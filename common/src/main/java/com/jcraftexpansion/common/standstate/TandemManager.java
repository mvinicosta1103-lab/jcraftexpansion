package com.jcraftexpansion.common.standstate;

import com.jcraftexpansion.JCraftExpansion;
import com.jcraftexpansion.common.barrage.BarrageHoldHandler;
import com.jcraftexpansion.common.util.TargetUtil;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import net.arna.jcraft.api.attack.enums.MoveClass;
import net.arna.jcraft.api.attack.moves.AbstractBarrageAttack;
import net.arna.jcraft.api.component.living.CommonCooldownsComponent;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.util.CooldownType;
import net.arna.jcraft.common.util.JUtils;
import net.arna.jcraft.platform.JComponentPlatformUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tandem: o stand se solta do usuário e ataca sozinho a entidade para a qual o usuário estava olhando.
 * <p>
 * Usa o modo "remoto" do JCraft (stand solto do dono). A IA daqui move o stand até o alvo, gira o stand
 * (de forma independente da direção do dono) até olhar para ele e só então dispara golpes leves, pesados e barragens. Apertar a tecla de novo (ou perder o alvo) encerra.
 */
public final class TandemManager {
    /** Cliente -> Servidor: alternar o Tandem. */
    public static final ResourceLocation TOGGLE_ID = JCraftExpansion.id("tandem_toggle");

    private static final double LOOK_RANGE = 32.0;
    private static final double MAX_DISTANCE = 64.0;
    private static final double STAND_DISTANCE = 1.7;
    private static final double ATTACK_RANGE = 4.0;
    /** Velocidade máxima de giro do stand (graus por tick): ele gira por conta própria, independente do dono. */
    private static final float TURN_SPEED_YAW = 24.0f;
    private static final float TURN_SPEED_PITCH = 16.0f;
    /** O stand só ataca se estiver olhando para o alvo, com no máximo esta diferença (graus). */
    private static final float ATTACK_YAW_TOLERANCE = 30.0f;
    private static final float ATTACK_PITCH_TOLERANCE = 45.0f;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final UUID target;
        long nextAttack;
        long barrageEnd;

        Session(UUID target) {
            this.target = target;
        }
    }

    private TandemManager() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, TOGGLE_ID, (buf, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                context.queue(() -> toggle(player));
            }
        });
        TickEvent.SERVER_POST.register(TandemManager::tick);
        PlayerEvent.PLAYER_QUIT.register(player -> {
            if (SESSIONS.containsKey(player.getUUID())) {
                stop(player, null);
            }
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> SESSIONS.clear());
    }

    // ------------------------------------------------------------------ iniciar / parar

    private static void toggle(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) {
            stop(player, "tandem.jcraft_expansion.off");
            return;
        }

        StandEntity<?, ?> stand = JUtils.getStand(player);
        if (stand == null || !stand.hasUser()) {
            return;
        }
        if (stand.isRemote()) {
            player.displayClientMessage(Component.translatable("tandem.jcraft_expansion.busy"), true);
            return;
        }
        LivingEntity target = TargetUtil.lookEntity(player, LOOK_RANGE);
        if (target == null) {
            player.displayClientMessage(Component.translatable("tandem.jcraft_expansion.no_target"), true);
            return;
        }

        SESSIONS.put(player.getUUID(), new Session(target.getUUID()));
        StandFlags.setTandemFlag(player, true); // antes do setRemote: o mixin já precisa saber que é autônomo
        stand.cancelMove();
        stand.setRemote(true);
        stand.resetAlphaOverride(); // o modo remoto deixa o stand quase invisível; no Tandem ele deve aparecer
        stand.setNoGravity(true);
        player.displayClientMessage(Component.translatable("tandem.jcraft_expansion.on"), true);
    }

    private static void stop(ServerPlayer player, String messageKey) {
        SESSIONS.remove(player.getUUID());
        StandFlags.setTandemFlag(player, false);

        StandEntity<?, ?> stand = JUtils.getStand(player);
        if (stand != null && stand.isRemote()) {
            stand.cancelMove();
            stand.setRemote(false); // o stand volta a acompanhar o usuário
            stand.setNoGravity(false);
        }
        if (messageKey != null) {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    // ------------------------------------------------------------------ IA

    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null) {
                it.remove();
                continue;
            }

            StandEntity<?, ?> stand = JUtils.getStand(owner);
            Entity targetEntity = owner.serverLevel().getEntity(entry.getValue().target);
            if (stand == null || !stand.isAlive() || !stand.isRemote()) {
                it.remove();
                StandFlags.setTandemFlag(owner, false);
                continue;
            }
            if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()
                    || target.distanceTo(owner) > MAX_DISTANCE) {
                it.remove();
                finish(owner, stand, "tandem.jcraft_expansion.target_lost");
                continue;
            }

            drive(owner, stand, target, entry.getValue());
        }
    }

    /** Termina o Tandem sem mexer no mapa de sessões (quem chama já removeu). */
    private static void finish(ServerPlayer owner, StandEntity<?, ?> stand, String messageKey) {
        StandFlags.setTandemFlag(owner, false);
        stand.cancelMove();
        stand.setRemote(false);
        stand.setNoGravity(false);
        owner.displayClientMessage(Component.translatable(messageKey), true);
    }

    private static void drive(ServerPlayer owner, StandEntity<?, ?> stand, LivingEntity target, Session session) {
        ServerLevel level = owner.serverLevel();
        long now = level.getGameTime();

        // 1) Posição: fica a ~1,7 blocos do alvo, do lado do dono, com movimento suave.
        Vec3 targetPos = target.position();
        Vec3 side = owner.position().subtract(targetPos);
        side = new Vec3(side.x, 0.0, side.z);
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Vec3 desired = targetPos.add(side.scale(STAND_DISTANCE)).add(0.0, 0.1, 0.0);

        Vec3 current = stand.position();
        Vec3 delta = desired.subtract(current);
        double length = delta.length();
        Vec3 next = current;
        if (length > 1.0E-3) {
            double step = Math.min(length, Math.max(0.2, length * 0.3));
            step = Math.min(step, 1.4); // limite de velocidade por tick
            next = current.add(delta.scale(step / length));
        }

        // 2) Rotação: o stand gira sozinho em direção ao alvo (não copia a direção do dono).
        double dx = target.getX() - next.x;
        double dz = target.getZ() - next.z;
        double dy = target.getY(0.5) - (next.y + stand.getBbHeight() * 0.5);
        float wantedYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float wantedPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));

        float yaw = Mth.approachDegrees(stand.getYRot(), wantedYaw, TURN_SPEED_YAW);
        float pitch = Mth.approachDegrees(stand.getXRot(), wantedPitch, TURN_SPEED_PITCH);

        stand.setPos(next.x, next.y, next.z);
        stand.setYRot(yaw);
        stand.setXRot(pitch);
        stand.setYHeadRot(yaw);
        stand.setYBodyRot(yaw);
        stand.setDeltaMovement(Vec3.ZERO);
        stand.fallDistance = 0.0f;

        // Só ataca enquanto estiver olhando para o alvo.
        boolean facingTarget = Math.abs(Mth.degreesDifference(yaw, wantedYaw)) <= ATTACK_YAW_TOLERANCE
                && Math.abs(Mth.degreesDifference(pitch, wantedPitch)) <= ATTACK_PITCH_TOLERANCE;

        // 3) Ataques.
        if (!stand.canAttack() || stand.position().distanceTo(target.position()) > ATTACK_RANGE) {
            return;
        }

        if (!facingTarget) {
            // Virou para longe do alvo: interrompe a barragem e espera olhar de novo antes de golpear.
            if (stand.getCurrentMove() instanceof AbstractBarrageAttack<?, ?>) {
                stand.cancelMove();
                session.nextAttack = now + 4;
            }
            return;
        }

        if (stand.getCurrentMove() instanceof AbstractBarrageAttack<?, ?> barrage) {
            if (now >= session.barrageEnd) {
                stand.cancelMove();
                session.nextAttack = now + 8;
            } else {
                BarrageHoldHandler.extend(stand, barrage);
            }
            return;
        }

        if (stand.getCurrentMove() == null && stand.getMoveStun() <= 0 && now >= session.nextAttack) {
            clearAttackCooldowns(owner);
            float roll = level.random.nextFloat();
            MoveClass moveClass = roll < 0.55f ? MoveClass.LIGHT : roll < 0.75f ? MoveClass.HEAVY : MoveClass.BARRAGE;
            if (stand.handleMove(moveClass)) {
                if (moveClass == MoveClass.BARRAGE) {
                    session.barrageEnd = now + 50 + level.random.nextInt(40);
                }
                session.nextAttack = now + (moveClass == MoveClass.LIGHT ? 6 : 14);
            } else {
                session.nextAttack = now + 10;
            }
        }
    }

    /** O Tandem não deve ficar preso nos cooldowns do usuário. */
    private static void clearAttackCooldowns(ServerPlayer owner) {
        CommonCooldownsComponent cooldowns = JComponentPlatformUtils.getCooldowns(owner);
        for (CooldownType type : new CooldownType[]{CooldownType.LIGHT, CooldownType.HEAVY,
                CooldownType.BARRAGE, CooldownType.STAND_BARRAGE}) {
            if (cooldowns.getCooldown(type) > 0) {
                cooldowns.clear(type);
            }
        }
    }
}