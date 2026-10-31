package com.jcraftexpansion.common.skill.whitesnake;

import com.jcraftexpansion.common.disguise.DisguiseManager;
import com.jcraftexpansion.common.skill.InputSkill;
import com.jcraftexpansion.common.skill.SimpleSkill;
import com.jcraftexpansion.common.skill.SkillContext;
import com.jcraftexpansion.common.skill.SkillTicker;
import com.jcraftexpansion.common.skill.StandSkill;
import com.jcraftexpansion.common.standstate.AutopilotManager;
import com.jcraftexpansion.common.util.TargetUtil;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.entity.stand.WhiteSnakeEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * As 9 skills extras do White Snake (hotbar de skills, teclas 1-9).
 * <p>
 * As skills reaproveitam as animações que o White Snake já tem (disco, spew etc.).
 */
public final class WhiteSnakeSkills {
    /** Alcance para apontar uma criatura ou um ponto para o stand. */
    private static final double COMMAND_RANGE = 48.0;

    public static final List<StandSkill> SKILLS = List.of(
            new SimpleSkill("guardian_pilot", Items.SHIELD, 100, WhiteSnakeSkills::guardianPilot),            // 1
            new SimpleSkill("stand_command", Items.COMPASS, 40, WhiteSnakeSkills::standCommand),             // 2
            new SimpleSkill("recall_stand", Items.ENDER_PEARL, 20, WhiteSnakeSkills::recallStand),           // 3
            new SimpleSkill("memory_wipe", Items.MUSIC_DISC_13, 400, WhiteSnakeSkills::memoryWipe),          // 4
            new SimpleSkill("sleep_disc", Items.MUSIC_DISC_11, 600, WhiteSnakeSkills::sleepDisc),            // 5
            new SimpleSkill("toxic_mist", Items.FERMENTED_SPIDER_EYE, 700, WhiteSnakeSkills::toxicMist),     // 6
            new SimpleSkill("acid_trap", Items.SLIME_BALL, 800, WhiteSnakeSkills::acidTrap),                 // 7
            new SimpleSkill("disc_ward", Items.AMETHYST_SHARD, 900, WhiteSnakeSkills::discWard),             // 8
            new InputSkill("disguise", Items.PLAYER_HEAD, 100, WhiteSnakeSkills::disguise)                 // 9
    );

    private WhiteSnakeSkills() {
    }

    // ------------------------------------------------------------------------------------------
    // 9 - Disfarce: o jogador digita o nome de um jogador (skin) ou de um mob e o stand vira uma cópia dele
    //     (aparência, tamanho e golpes), continuando a ser o White Snake. Digitar vazio ou repetir remove.
    // ------------------------------------------------------------------------------------------
    private static boolean disguise(SkillContext ctx, String text) {
        return DisguiseManager.apply(ctx.player(), text);
    }

    // ------------------------------------------------------------------------------------------
    // 1 - Piloto Guardião: o stand entra no modo piloto SEM você controlar; ele acompanha você e ataca
    //     qualquer entidade que ataque você. Usar de novo desliga.
    // ------------------------------------------------------------------------------------------
    private static boolean guardianPilot(SkillContext ctx) {
        return AutopilotManager.toggleGuard(ctx.player());
    }

    // ------------------------------------------------------------------------------------------
    // 2 - Ordem ao Stand: olhando para uma criatura, o stand vai atacá-la; olhando para um ponto
    //     (bloco), o stand vai até lá e fica parado, vigiando o local.
    // ------------------------------------------------------------------------------------------
    private static boolean standCommand(SkillContext ctx) {
        ServerPlayer player = ctx.player();

        LivingEntity target = TargetUtil.lookEntity(player, COMMAND_RANGE);
        if (target != null) {
            return AutopilotManager.orderAttack(player, target);
        }

        BlockHitResult hit = lookBlock(player, COMMAND_RANGE);
        if (hit == null) {
            return fail(player, "no_target");
        }
        Vec3 point = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        return AutopilotManager.orderHold(player, point);
    }

    // ------------------------------------------------------------------------------------------
    // 3 - Chamar de Volta: encerra o piloto automático; o stand volta para você.
    // ------------------------------------------------------------------------------------------
    private static boolean recallStand(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        if (!AutopilotManager.isActive(player)) {
            return fail(player, "no_autopilot");
        }
        AutopilotManager.stop(player, "autopilot.jcraft_expansion.off");
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 4 - Disco do Esquecimento: mob alvo esquece quem estava perseguindo; qualquer alvo fica fraco,
    //     cego e atordoado por alguns segundos.
    // ------------------------------------------------------------------------------------------
    private static boolean memoryWipe(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        LivingEntity target = TargetUtil.lookEntity(player, 6.0);
        if (target == null) {
            return fail(player, "no_target");
        }

        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
            mob.getNavigation().stop();
        }
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));

        anim(ctx, WhiteSnakeEntity.State.DISC_TAKE, 20);
        burst(ctx.level(), target, ParticleTypes.ENCHANT, 25);
        sound(ctx.level(), target);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 5 - Disco do Sono: o alvo fica praticamente paralisado e cego por 5 segundos.
    // ------------------------------------------------------------------------------------------
    private static boolean sleepDisc(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        LivingEntity target = TargetUtil.lookEntity(player, 6.0);
        if (target == null) {
            return fail(player, "no_target");
        }

        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6));
        target.addEffect(new MobEffectInstance(MobEffects.JUMP, 100, 128));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 2));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));

        anim(ctx, WhiteSnakeEntity.State.DISC_TAKE, 20);
        burst(ctx.level(), target, ParticleTypes.NOTE, 12);
        sound(ctx.level(), target);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 6 - Névoa Tóxica: inimigos num raio de 6 blocos ficam envenenados e lentos.
    // ------------------------------------------------------------------------------------------
    private static boolean toxicMist(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();

        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6.0),
                e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof StandEntity<?, ?>)
                        && !SkillTicker.isAlly(e, player)
                        && e.distanceToSqr(player) <= 36.0);
        if (enemies.isEmpty()) {
            return fail(player, "no_target");
        }

        for (LivingEntity e : enemies) {
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
            burst(level, e, ParticleTypes.SNEEZE, 8);
        }
        level.sendParticles(ParticleTypes.SNEEZE, player.getX(), player.getY(0.5), player.getZ(),
                40, 3.0, 0.8, 3.0, 0.02);

        anim(ctx, WhiteSnakeEntity.State.ACID_SPEW, 20);
        sound(ctx.level(), player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 7 - Armadilha de Ácido: cria uma poça no bloco olhado que envenena e atrasa inimigos por 15 s.
    // ------------------------------------------------------------------------------------------
    private static boolean acidTrap(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();

        BlockHitResult hit = lookBlock(player, 20.0);
        if (hit == null) {
            return fail(player, "no_target");
        }
        Vec3 center = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        AcidTrapTicker.addZone(level, center, player.getUUID(), level.getGameTime() + 300, 3.5);

        anim(ctx, WhiteSnakeEntity.State.ACID_SPEW, 20);
        level.sendParticles(ParticleTypes.SNEEZE, center.x, center.y + 0.2, center.z, 30, 1.5, 0.1, 1.5, 0.02);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 8 - Disco Protetor: um disco blinda você com absorção e resistência.
    // ------------------------------------------------------------------------------------------
    private static boolean discWard(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));

        anim(ctx, WhiteSnakeEntity.State.DISC_GIVE, 20);
        burst(ctx.level(), player, ParticleTypes.ENCHANT, 25);
        sound(ctx.level(), player);
        return true;
    }

    // ------------------------------------------------------------------------------------------

    /** Toca uma animação que o White Snake já tem (não interrompe o stand se ele estiver em piloto automático lutando). */
    private static void anim(SkillContext ctx, WhiteSnakeEntity.State state, int ticks) {
        if (ctx.stand() instanceof WhiteSnakeEntity ws && ws.getCurrentMove() == null && ws.getMoveStun() <= 0) {
            ws.setState(state);
            ws.setMoveStun(ticks);
        }
    }

    private static boolean fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("skill.jcraft_expansion.msg." + key), true);
        return false;
    }

    private static void burst(ServerLevel level, LivingEntity e, ParticleOptions particle, int count) {
        level.sendParticles(particle, e.getX(), e.getY(1.0), e.getZ(), count, 0.6, 0.7, 0.6, 0.0);
    }

    private static void sound(ServerLevel level, LivingEntity e) {
        level.playSound(null, e.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    private static BlockHitResult lookBlock(ServerPlayer player, double range) {
        HitResult hit = player.pick(range, 1.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit;
        }
        return null;
    }
}
