package com.jcraftexpansion.common.standstate;

import com.jcraftexpansion.common.barrage.BarrageHoldHandler;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.arna.jcraft.api.attack.enums.MoveClass;
import net.arna.jcraft.api.attack.moves.AbstractBarrageAttack;
import net.arna.jcraft.api.component.living.CommonCooldownsComponent;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.util.CooldownType;
import net.arna.jcraft.common.util.JUtils;
import net.arna.jcraft.platform.JComponentPlatformUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Piloto Automático: o stand fica no modo "piloto" (remoto) do JCraft, mas quem o move é a nossa IA,
 * então o jogador continua livre para andar e lutar.
 * <p>
 * Tem dois modos de base (só um por vez) e uma ordem de ataque que pode ser dada por cima deles:
 * <ul>
 *     <li>{@link Mode#GUARD}: o stand anda em volta do dono (pelo chão, sem flutuar e sem precisar olhar para onde o dono
 *     olha) e ataca quem o atacar (jogadores, mobs, projéteis de mobs...).</li>
 *     <li>{@link Mode#HOLD}: o stand vai até um ponto e fica parado lá, atacando inimigos que chegarem perto.</li>
 *     <li>Ordem de ataque: o stand vai atrás de uma criatura específica até derrotá-la e depois volta ao modo de base
 *     (ou encerra, se não havia modo de base).</li>
 * </ul>
 * Reaproveita o flag "Tandem" do {@link StandFlags} (= "stand autônomo") para o mixin e o cliente saberem que o stand
 * não está sendo pilotado manualmente.
 */
public final class AutopilotManager {
    public enum Mode {GUARD, HOLD}

    /** Distância máxima entre dono e stand antes do piloto automático encerrar (o stand some dos clientes a ~80). */
    private static final double MAX_OWNER_DISTANCE = 96.0;
    /** Agressor mais longe que isso do dono é ignorado no modo Guarda. */
    private static final double GUARD_LEASH = 32.0;
    /** Raio em volta do dono onde procuramos mobs que estejam mirando nele. */
    private static final double THREAT_SCAN = 24.0;
    /** Quanto tempo (ticks) lembramos de quem bateu no dono por último. */
    private static final int THREAT_MEMORY_TICKS = 100;
    /** Raio de vigia do modo Ponto Fixo. */
    private static final double HOLD_RADIUS = 5.0;
    private static final double STAND_DISTANCE = 1.7;
    private static final double ATTACK_RANGE = 4.0;
    /** Modo Guarda sem ameaça: o stand anda em círculo em volta do dono, com os pés no chão. */
    private static final double ORBIT_RADIUS = 2.6;
    /** Quanto o ponto do círculo avança por tick (radianos): ~1 volta a cada 6 s. */
    private static final double ORBIT_SPEED = 0.05;
    private static final double WALK_SPEED = 0.25;
    private static final double RUN_SPEED = 0.6;
    /** Acima desta distância ao ponto do círculo o stand corre em vez de andar. */
    private static final double RUN_DISTANCE = 3.5;
    /** Longe demais (ou em outro nível) o stand reaparece junto do dono. */
    private static final double TELEPORT_DISTANCE = 20.0;
    private static final double TELEPORT_HEIGHT = 6.0;
    private static final float WALK_TURN_SPEED = 25.0f;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        Mode base;
        Vec3 holdPoint;
        UUID attackTarget;
        UUID threat;
        long nextAttack;
        long barrageEnd;
        /** Ângulo atual do stand no círculo em volta do dono (NaN = ainda não começou). */
        double orbitAngle = Double.NaN;
        /** Velocidade vertical do stand quando ele anda pelo chão (gravidade própria). */
        double fallSpeed;
    }

    private AutopilotManager() {
    }

    public static void init() {
        TickEvent.SERVER_POST.register(AutopilotManager::tick);
        PlayerEvent.PLAYER_QUIT.register(player -> {
            if (SESSIONS.containsKey(player.getUUID())) {
                stop(player, null);
            }
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> SESSIONS.clear());
    }

    // ------------------------------------------------------------------ API usada pelas skills

    public static boolean isActive(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /**
     * Liga o modo Guarda (se já estava em Guarda, desliga tudo).
     *
     * @return true se o modo Guarda foi ligado
     */
    public static boolean toggleGuard(ServerPlayer player) {
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null && existing.base == Mode.GUARD) {
            stop(player, "autopilot.jcraft_expansion.off");
            return false;
        }
        Session s = open(player);
        if (s == null) {
            return false;
        }
        s.base = Mode.GUARD;
        s.holdPoint = null;
        s.threat = null;
        player.displayClientMessage(Component.translatable("autopilot.jcraft_expansion.guard_on"), true);
        return true;
    }

    /** Manda o stand ficar parado num ponto (vigiando em volta). */
    public static boolean orderHold(ServerPlayer player, Vec3 point) {
        Session s = open(player);
        if (s == null) {
            return false;
        }
        s.base = Mode.HOLD;
        s.holdPoint = point;
        s.attackTarget = null;
        s.threat = null;
        player.displayClientMessage(Component.translatable("autopilot.jcraft_expansion.hold_on"), true);
        return true;
    }

    /** Manda o stand atacar uma criatura específica. */
    public static boolean orderAttack(ServerPlayer player, LivingEntity target) {
        if (target == player || !target.isAlive()) {
            return false;
        }
        Session s = open(player);
        if (s == null) {
            return false;
        }
        s.attackTarget = target.getUUID();
        player.displayClientMessage(Component.translatable("autopilot.jcraft_expansion.attack_on"), true);
        return true;
    }

    /** Encerra qualquer piloto automático e o stand volta a acompanhar o dono. */
    public static void stop(ServerPlayer player, String messageKey) {
        SESSIONS.remove(player.getUUID());
        StandFlags.setTandemFlag(player, false);

        StandEntity<?, ?> stand = JUtils.getStand(player);
        if (stand != null && stand.isRemote()) {
            stand.cancelMove();
            stand.setRemote(false);
            stand.setNoGravity(false);
        }
        if (messageKey != null) {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    // ------------------------------------------------------------------ abrir sessão

    private static Session open(ServerPlayer player) {
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null) {
            return existing;
        }
        StandEntity<?, ?> stand = JUtils.getStand(player);
        if (stand == null || !stand.hasUser()) {
            return null;
        }
        if (StandFlags.isTandem(stand)) {
            // O Tandem usa o mesmo flag: se não é uma sessão nossa, é o Tandem em andamento.
            player.displayClientMessage(Component.translatable("autopilot.jcraft_expansion.busy"), true);
            return null;
        }

        Session s = new Session();
        SESSIONS.put(player.getUUID(), s);
        StandFlags.setTandemFlag(player, true); // antes do setRemote: o mixin já precisa saber que é autônomo
        if (!stand.isRemote()) {
            stand.cancelMove();
            stand.setRemote(true);
        }
        stand.resetAlphaOverride(); // o modo remoto deixa o stand quase invisível; aqui ele deve aparecer
        stand.setNoGravity(true);
        return s;
    }

    // ------------------------------------------------------------------ tick

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
            if (!owner.isAlive() || stand == null || !stand.isAlive() || !stand.isRemote()
                    || stand.level() != owner.level()) {
                // Dono morreu, stand guardado/destruído, piloto manual ligado por cima ou mudou de dimensão.
                it.remove();
                StandFlags.setTandemFlag(owner, false);
                continue;
            }
            if (stand.distanceTo(owner) > MAX_OWNER_DISTANCE) {
                it.remove();
                finish(owner, stand, "autopilot.jcraft_expansion.too_far");
                continue;
            }

            String endKey = drive(owner, stand, entry.getValue());
            if (endKey != null) {
                it.remove();
                finish(owner, stand, endKey);
            }
        }
    }

    /** Termina sem mexer no mapa de sessões (quem chama já removeu). */
    private static void finish(ServerPlayer owner, StandEntity<?, ?> stand, String messageKey) {
        StandFlags.setTandemFlag(owner, false);
        stand.cancelMove();
        stand.setRemote(false);
        stand.setNoGravity(false);
        owner.displayClientMessage(Component.translatable(messageKey), true);
    }

    /** @return chave de mensagem se o piloto automático deve encerrar, ou null para continuar. */
    private static String drive(ServerPlayer owner, StandEntity<?, ?> stand, Session s) {
        ServerLevel level = owner.serverLevel();
        long now = level.getGameTime();

        // 1) Escolhe o alvo: ordem explícita > ameaça do modo de base.
        LivingEntity target = null;
        if (s.attackTarget != null) {
            Entity e = level.getEntity(s.attackTarget);
            if (e instanceof LivingEntity le && le.isAlive() && le.distanceTo(owner) <= MAX_OWNER_DISTANCE) {
                target = le;
            } else {
                s.attackTarget = null;
                if (s.base == null) {
                    return "autopilot.jcraft_expansion.target_done";
                }
            }
        }
        if (target == null && s.base != null) {
            target = currentThreat(owner, stand, s, level, now);
        }

        // Guarda sem ameaça: o stand anda em volta do dono em vez de flutuar atrás dele.
        if (target == null && s.base == Mode.GUARD) {
            if (stand.getCurrentMove() instanceof AbstractBarrageAttack<?, ?>) {
                stand.cancelMove();
            }
            walkAround(owner, stand, s);
            return null;
        }
        s.fallSpeed = 0.0; // voando atrás de um alvo / parado num ponto: sem gravidade própria

        // 2) Posição e rotação desejadas.
        Vec3 desired;
        float yaw;
        float pitch = 0.0f;
        Vec3 current = stand.position();

        if (target != null) {
            Vec3 targetPos = target.position();
            Vec3 side = owner.position().subtract(targetPos);
            side = new Vec3(side.x, 0.0, side.z);
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            desired = targetPos.add(side.scale(STAND_DISTANCE)).add(0.0, 0.1, 0.0);
        } else if (s.base == Mode.HOLD && s.holdPoint != null) {
            desired = s.holdPoint;
        } else {
            // (Não deve ocorrer: a Guarda sem ameaça é tratada em walkAround.) Fica atrás e à direita do dono.
            Vec3 offset = new Vec3(-0.9, 0.2, -1.2).yRot(-owner.getYRot() * Mth.DEG_TO_RAD);
            desired = owner.position().add(offset);
        }

        Vec3 delta = desired.subtract(current);
        double length = delta.length();
        Vec3 next = current;
        if (length > 1.0E-3) {
            double step = Math.min(length, Math.max(0.2, length * 0.3));
            step = Math.min(step, 1.4); // limite de velocidade por tick
            next = current.add(delta.scale(step / length));
        }

        if (target != null) {
            double dx = target.getX() - next.x;
            double dz = target.getZ() - next.z;
            double dy = target.getY(0.5) - (next.y + stand.getBbHeight() * 0.5);
            yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
        } else if (s.base == Mode.HOLD) {
            yaw = stand.getYRot(); // parado: mantém a direção
            pitch = 0.0f;
        } else {
            yaw = owner.getYRot();
        }

        stand.setPos(next.x, next.y, next.z);
        stand.setYRot(yaw);
        stand.setXRot(pitch);
        stand.setYHeadRot(yaw);
        stand.setYBodyRot(yaw);
        stand.setDeltaMovement(Vec3.ZERO);
        stand.fallDistance = 0.0f;

        // 3) Ataques.
        if (target == null || !stand.canAttack() || stand.position().distanceTo(target.position()) > ATTACK_RANGE) {
            // Sem alvo ao alcance: não deixa uma barragem antiga rodando no vazio.
            if (stand.getCurrentMove() instanceof AbstractBarrageAttack<?, ?> && target == null) {
                stand.cancelMove();
            }
            return null;
        }

        if (stand.getCurrentMove() instanceof AbstractBarrageAttack<?, ?> barrage) {
            if (now >= s.barrageEnd) {
                stand.cancelMove();
                s.nextAttack = now + 8;
            } else {
                BarrageHoldHandler.extend(stand, barrage);
            }
            return null;
        }

        if (stand.getCurrentMove() == null && stand.getMoveStun() <= 0 && now >= s.nextAttack) {
            clearAttackCooldowns(owner);
            float roll = level.random.nextFloat();
            MoveClass moveClass = roll < 0.55f ? MoveClass.LIGHT : roll < 0.75f ? MoveClass.HEAVY : MoveClass.BARRAGE;
            if (stand.handleMove(moveClass)) {
                if (moveClass == MoveClass.BARRAGE) {
                    s.barrageEnd = now + 50 + level.random.nextInt(40);
                }
                s.nextAttack = now + (moveClass == MoveClass.LIGHT ? 6 : 14);
            } else {
                s.nextAttack = now + 10;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ andar em volta do dono

    /**
     * Move o stand pelo chão em direção a um ponto que gira em volta do dono. O stand vira para onde está andando
     * (não copia a direção do dono), tem gravidade própria, sobe degraus/pula obstáculos e, se ficar longe demais,
     * reaparece perto do dono.
     */
    private static void walkAround(ServerPlayer owner, StandEntity<?, ?> stand, Session s) {
        Vec3 ownerPos = owner.position();
        Vec3 pos = stand.position();

        if (Double.isNaN(s.orbitAngle)) {
            s.orbitAngle = Math.atan2(pos.z - ownerPos.z, pos.x - ownerPos.x);
        }
        s.orbitAngle += ORBIT_SPEED;

        double slotX = ownerPos.x + Math.cos(s.orbitAngle) * ORBIT_RADIUS;
        double slotZ = ownerPos.z + Math.sin(s.orbitAngle) * ORBIT_RADIUS;
        double dx = slotX - pos.x;
        double dz = slotZ - pos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist > TELEPORT_DISTANCE || Math.abs(ownerPos.y - pos.y) > TELEPORT_HEIGHT) {
            stand.setPos(ownerPos.x, ownerPos.y, ownerPos.z);
            s.fallSpeed = 0.0;
            stand.setDeltaMovement(Vec3.ZERO);
            stand.fallDistance = 0.0f;
            return;
        }

        // Horizontal: anda até o ponto do círculo (corre se estiver ficando para trás).
        double speed = Math.min(dist, dist > RUN_DISTANCE ? RUN_SPEED : WALK_SPEED);
        double mx = dist > 1.0E-3 ? dx / dist * speed : 0.0;
        double mz = dist > 1.0E-3 ? dz / dist * speed : 0.0;

        // Vertical: gravidade própria; pula se bateu num obstáculo andando pelo chão.
        if (stand.onGround()) {
            s.fallSpeed = stand.horizontalCollision ? 0.42 : -0.08;
        } else {
            s.fallSpeed = Math.max((s.fallSpeed - 0.08) * 0.98, -3.0);
        }

        stand.move(MoverType.SELF, new Vec3(mx, s.fallSpeed, mz));
        stand.fallDistance = 0.0f;
        stand.setDeltaMovement(Vec3.ZERO);

        // Rotação: olha para onde está andando; parado, mantém a direção.
        float yaw = stand.getYRot();
        if (speed > 0.02) {
            float wanted = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            yaw = Mth.approachDegrees(yaw, wanted, WALK_TURN_SPEED);
        }
        stand.setYRot(yaw);
        stand.setXRot(0.0f);
        stand.setYHeadRot(yaw);
        stand.setYBodyRot(yaw);
    }

    // ------------------------------------------------------------------ ameaças

    /** Mantém o alvo atual enquanto ele for válido e reavalia de tempos em tempos (quem bateu por último tem prioridade). */
    private static LivingEntity currentThreat(ServerPlayer owner, StandEntity<?, ?> stand, Session s,
                                              ServerLevel level, long now) {
        LivingEntity threat = null;
        if (s.threat != null) {
            Entity e = level.getEntity(s.threat);
            if (e instanceof LivingEntity le && isValidThreat(le, owner, stand) && withinLeash(le, owner, s)) {
                threat = le;
            } else {
                s.threat = null;
            }
        }

        if (threat == null || now % 10 == 0) {
            LivingEntity found = s.base == Mode.HOLD
                    ? findHoldThreat(owner, stand, s, level)
                    : findGuardThreat(owner, stand, level);
            if (found != null) {
                threat = found;
            }
        }
        s.threat = threat == null ? null : threat.getUUID();
        return threat;
    }

    private static boolean withinLeash(LivingEntity e, ServerPlayer owner, Session s) {
        if (s.base == Mode.HOLD && s.holdPoint != null) {
            return e.position().distanceTo(s.holdPoint) <= HOLD_RADIUS * 1.6;
        }
        return e.distanceTo(owner) <= GUARD_LEASH;
    }

    /** Quem bateu no dono recentemente; senão, o mob mais próximo que esteja mirando nele. */
    private static LivingEntity findGuardThreat(ServerPlayer owner, StandEntity<?, ?> stand, ServerLevel level) {
        LivingEntity attacker = recentAttacker(owner);
        if (attacker != null && isValidThreat(attacker, owner, stand) && attacker.distanceTo(owner) <= GUARD_LEASH) {
            return attacker;
        }

        AABB box = owner.getBoundingBox().inflate(THREAT_SCAN);
        return level.getEntitiesOfClass(Mob.class, box, m -> m.getTarget() == owner && isValidThreat(m, owner, stand))
                .stream()
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(owner)))
                .orElse(null);
    }

    /** Ponto Fixo: monstros (ou quem esteja atacando o dono) dentro do raio de vigia do ponto. */
    private static LivingEntity findHoldThreat(ServerPlayer owner, StandEntity<?, ?> stand, Session s, ServerLevel level) {
        if (s.holdPoint == null) {
            return null;
        }
        Vec3 point = s.holdPoint;
        LivingEntity attacker = recentAttacker(owner);
        AABB box = new AABB(point, point).inflate(HOLD_RADIUS);
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> isValidThreat(e, owner, stand)
                        && e.position().distanceToSqr(point) <= HOLD_RADIUS * HOLD_RADIUS
                        && (e instanceof Enemy || e == attacker || (e instanceof Mob m && m.getTarget() == owner)))
                .stream()
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(point)))
                .orElse(null);
    }

    /** Última entidade que feriu o dono (o JCraft/Minecraft esquece sozinho após ~5 s). Se foi um stand, vale o usuário. */
    private static LivingEntity recentAttacker(ServerPlayer owner) {
        LivingEntity last = owner.getLastHurtByMob();
        if (last == null || owner.tickCount - owner.getLastHurtByMobTimestamp() > THREAT_MEMORY_TICKS) {
            return null;
        }
        if (last instanceof StandEntity<?, ?> se) {
            return se.getUser();
        }
        return last;
    }

    private static boolean isValidThreat(LivingEntity e, ServerPlayer owner, StandEntity<?, ?> stand) {
        return e != owner && e != stand && e.isAlive() && !e.isSpectator()
                && !(e instanceof StandEntity<?, ?>)
                && !e.isAlliedTo(owner)
                && !(e instanceof TamableAnimal t && t.isOwnedBy(owner));
    }

    /** O piloto automático não deve ficar preso nos cooldowns do usuário. */
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