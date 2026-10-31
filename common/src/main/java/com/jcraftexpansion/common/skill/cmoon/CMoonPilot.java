package com.jcraftexpansion.common.skill.cmoon;

import com.jcraftexpansion.common.skill.SkillContext;
import com.jcraftexpansion.common.standstate.StandFlags;
import dev.architectury.event.events.common.TickEvent;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.entity.stand.CMoonEntity;
import net.arna.jcraft.common.util.JUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Modo piloto do C-Moon, com o mesmo alcance do piloto do White Snake (64 blocos).
 * <p>
 * O JCraft só tem movimento de piloto pronto para alguns stands (como o White Snake). Para o C-Moon usamos a mesma
 * base do JCraft (stand "remoto": solto do usuário, controlado pelas teclas de movimento e pela mira) e fazemos
 * aqui o movimento, copiando a lógica do White Snake (igual ao {@code KingCrimsonPilot}).
 */
public final class CMoonPilot {
    /** Distância máxima (em blocos) entre o usuário e o C-Moon pilotado. Igual ao White Snake; JCraft original: 20.
     * Não passe de ~70: o stand só é enviado aos clientes dentro de ~80 blocos. */
    public static final double MAX_DISTANCE = 64.0;
    public static final double MAX_DISTANCE_SQR = MAX_DISTANCE * MAX_DISTANCE;

    private CMoonPilot() {
    }

    public static void init() {
        TickEvent.SERVER_POST.register(CMoonPilot::tick);
    }

    /** Skill: liga/desliga o modo piloto. */
    public static boolean toggle(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        StandEntity<?, ?> stand = ctx.stand();

        if (StandFlags.isTandem(stand)) {
            player.displayClientMessage(Component.translatable("skill.jcraft_expansion.msg.pilot_busy"), true);
            return false;
        }

        stand.cancelMove();
        boolean turnOn = !stand.isRemote();
        stand.setRemote(turnOn);
        player.displayClientMessage(Component.translatable(turnOn
                ? "skill.jcraft_expansion.msg.cmoon_pilot_on" : "skill.jcraft_expansion.msg.pilot_off"), true);
        return true;
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            StandEntity<?, ?> stand = JUtils.getStand(player);
            if (stand instanceof CMoonEntity cm && cm.isRemoteAndControllable() && !StandFlags.isTandem(cm)) {
                moveRemote(cm, player);
            }
        }
    }

    /** Mesma física do {@code tickRemoteMovement} do White Snake, com o limite de distância do C-Moon. */
    private static void moveRemote(CMoonEntity stand, LivingEntity user) {
        double f = stand.getRemoteForwardInput();
        double s = stand.getRemoteSideInput();
        boolean jump = stand.getRemoteJumpInput();

        if (stand.lastRemoteInputTime - stand.tickCount > 2) {
            stand.updateRemoteInputs(0, 0, false, false);
        }

        Vec3 pos = stand.position();
        Vec3 look = stand.getLookAngle();
        Vec3 rotVec = new Vec3(look.x, 0.0, look.z).normalize();
        double dragMult = stand.getMoveStun() > 0 ? 0.2 : 0.4;
        double moveSpeed = 0.24;
        boolean onGround = stand.onGround();
        boolean climbing = stand.getFeetBlockState().is(BlockTags.CLIMBABLE);
        boolean swimming = !stand.level().getFluidState(stand.blockPosition()).isEmpty();
        if (climbing || swimming) {
            dragMult *= 0.5;
        }

        if ((climbing || swimming) && jump) {
            stand.push(0.0, 0.1, 0.0);
        } else if (onGround) {
            if (jump) {
                stand.push(0.0, 0.75, 0.0);
                stand.setRemoteJumpInput(false);
            }
        } else {
            moveSpeed = 0.024; // pouco controle no ar
            dragMult = 0.4;
        }

        Vec3 speed = stand.remoteSpeed
                .add(rotVec.scale(f * moveSpeed))
                .add(rotVec.yRot(1.5707963f).scale(s * moveSpeed))
                .scale(dragMult);

        // Passou do alcance: puxa o stand de volta na direção do usuário.
        Vec3 userPos = user.position();
        if (pos.add(speed).distanceToSqr(userPos) > MAX_DISTANCE_SQR) {
            speed = userPos.subtract(pos).scale(0.025);
        }
        stand.remoteSpeed = speed;

        if (f == 0.0 && s == 0.0 && !jump) {
            Vec3 dm = stand.getDeltaMovement();
            stand.push(-dm.x * 0.4, -dm.y * 0.4, -dm.z * 0.4);
        }
        stand.push(speed.x, speed.y, speed.z);
        stand.hasImpulse = true;
    }
}
