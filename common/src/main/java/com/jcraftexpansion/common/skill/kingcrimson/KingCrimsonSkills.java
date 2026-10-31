package com.jcraftexpansion.common.skill.kingcrimson;

import com.jcraftexpansion.common.skill.SimpleSkill;
import com.jcraftexpansion.common.skill.SkillContext;
import com.jcraftexpansion.common.skill.SkillTicker;
import com.jcraftexpansion.common.skill.StandSkill;
import com.jcraftexpansion.common.util.TargetUtil;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.entity.stand.KingCrimsonEntity;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * As 9 skills extras do King Crimson (hotbar de skills, teclas 1-9): modo piloto, golpes corpo a corpo
 * e ataques que apagam o tempo. Reaproveitam as animações que o King Crimson já tem.
 */
public final class KingCrimsonSkills {
    public static final List<StandSkill> SKILLS = List.of(
            new SimpleSkill("pilot_mode", Items.ENDER_EYE, 40, KingCrimsonPilot::toggle),                  // 1
            new SimpleSkill("crimson_dash", Items.FIRE_CHARGE, 300, KingCrimsonSkills::crimsonDash),       // 2
            new SimpleSkill("erased_cut", Items.IRON_SWORD, 400, KingCrimsonSkills::erasedCut),            // 3
            new SimpleSkill("time_freeze", Items.CLOCK, 700, KingCrimsonSkills::timeFreeze),               // 4
            new SimpleSkill("phantom_flurry", Items.BLAZE_POWDER, 500, KingCrimsonSkills::phantomFlurry),  // 5
            new SimpleSkill("crimson_pulse", Items.REDSTONE, 350, KingCrimsonSkills::crimsonPulse),        // 6
            new SimpleSkill("time_rewind", Items.ENDER_PEARL, 900, KingCrimsonSkills::timeRewind),         // 7
            new SimpleSkill("phantom_dodge", Items.FEATHER, 600, KingCrimsonSkills::phantomDodge),         // 8
            new SimpleSkill("crimson_thrust", Items.NETHERITE_SWORD, 300, KingCrimsonSkills::crimsonThrust) // 9
    );

    private KingCrimsonSkills() {
    }

    // ------------------------------------------------------------------------------------------
    // 2 - Investida Carmesim: o tempo é pulado e você aparece colado no alvo com um golpe forte.
    // ------------------------------------------------------------------------------------------
    private static boolean crimsonDash(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        LivingEntity target = TargetUtil.lookEntity(player, 14.0);
        if (target == null) {
            return fail(player, "no_target");
        }

        Vec3 from = player.position();
        Vec3 flat = target.position().subtract(from).multiply(1, 0, 1);
        Vec3 dir = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
        Vec3 dest = new Vec3(target.getX() - dir.x * 1.6, target.getY(), target.getZ() - dir.z * 1.6);

        if (level.noCollision(player, player.getBoundingBox().move(dest.subtract(from)))) {
            trail(level, from, dest, ParticleTypes.CRIMSON_SPORE);
            player.teleportTo(dest.x, dest.y, dest.z);
        }
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());

        target.invulnerableTime = 0;
        target.hurt(level.damageSources().playerAttack(player), 8.0f);
        target.knockback(0.8, -dir.x, -dir.z);

        anim(ctx, KingCrimsonEntity.State.DUAL_CHOP, 14);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 3 - Corte Apagado: um corte em arco; o dano "aconteceu no tempo apagado" e só aparece 1 s depois.
    // ------------------------------------------------------------------------------------------
    private static boolean erasedCut(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        AABB box = player.getBoundingBox().inflate(3.5, 1.0, 3.5);

        int hit = 0;
        long end = level.getServer().getTickCount() + 20;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (!isEnemy(e, player)) {
                continue;
            }
            Vec3 to = e.position().subtract(player.position()).multiply(1, 0, 1);
            if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(look) < 0.5) { // cone de ~60 graus
                continue;
            }
            KingCrimsonTicker.addDelayedHit(e, player, 10.0f, end);
            hit++;
        }
        if (hit == 0) {
            return fail(player, "no_target");
        }

        anim(ctx, KingCrimsonEntity.State.HEAVY, 18);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + look.x * 1.5, player.getY(0.6),
                player.getZ() + look.z * 1.5, 1, 0, 0, 0, 0);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 4 - Apagar Tempo: todos em volta ficam parados por 3 segundos; você ganha velocidade.
    // ------------------------------------------------------------------------------------------
    private static boolean timeFreeze(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        long end = level.getServer().getTickCount() + 60;
        double radius = 7.0;

        int count = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                LivingEntity::isAlive)) {
            if (!isEnemy(e, player) || e.distanceToSqr(player) > radius * radius) {
                continue;
            }
            KingCrimsonTicker.freeze(e, end);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 10));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 10));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 10));
            count++;
        }
        if (count == 0) {
            return fail(player, "no_target");
        }

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2));
        anim(ctx, KingCrimsonEntity.State.TIME_ERASE, 20);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(1.0), player.getZ(),
                60, radius / 3, 1.0, radius / 3, 0.2);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 5 - Rajada Fantasma: você some e reaparece em volta do alvo, acertando várias vezes.
    // ------------------------------------------------------------------------------------------
    private static boolean phantomFlurry(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        LivingEntity target = TargetUtil.lookEntity(player, 12.0);
        if (target == null) {
            return fail(player, "no_target");
        }

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 4)); // intocável durante a rajada
        KingCrimsonTicker.addFlurry(player, target, 6, 3, level.getServer().getTickCount() + 1);

        anim(ctx, KingCrimsonEntity.State.BARRAGE, 22);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 6 - Pulso Carmesim: onda que lança os inimigos próximos para cima e para longe.
    // ------------------------------------------------------------------------------------------
    private static boolean crimsonPulse(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        double radius = 4.5;

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                LivingEntity::isAlive)) {
            if (!isEnemy(e, player) || e.distanceToSqr(player) > radius * radius) {
                continue;
            }
            Vec3 dir = e.position().subtract(player.position()).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(player), 6.0f);
            e.setDeltaMovement(dir.x * 1.0, 0.9, dir.z * 1.0);
            e.hurtMarked = true;
        }

        anim(ctx, KingCrimsonEntity.State.DONUT, 25);
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32.0;
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX() + Math.cos(a) * radius * 0.8, player.getY() + 0.3,
                    player.getZ() + Math.sin(a) * radius * 0.8, 1, 0.0, 0.1, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0, 0, 0, 0);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 7 - Retrocesso: o tempo dos últimos 5 segundos é apagado; você volta ao ponto marcado e à vida que tinha.
    // ------------------------------------------------------------------------------------------
    private static boolean timeRewind(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        KingCrimsonTicker.addRewind(player, level.getServer().getTickCount() + 100);

        player.displayClientMessage(Component.translatable("skill.jcraft_expansion.msg.rewind_marked"), true);
        anim(ctx, KingCrimsonEntity.State.TIME_SKIP, 15);
        burst(level, player, ParticleTypes.PORTAL, 25);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 8 - Esquiva Fantasma: por 2 segundos o primeiro dano que você sofrer é apagado e o atacante leva um contra-golpe.
    // ------------------------------------------------------------------------------------------
    private static boolean phantomDodge(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        KingCrimsonTicker.addDodge(player, level.getServer().getTickCount() + 40);

        player.displayClientMessage(Component.translatable("skill.jcraft_expansion.msg.dodge_ready"), true);
        anim(ctx, KingCrimsonEntity.State.TIME_SKIP, 10);
        burst(level, player, ParticleTypes.SMOKE, 15);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 9 - Estocada Carmesim: golpe pesado de perto que lança o alvo para cima.
    // ------------------------------------------------------------------------------------------
    private static boolean crimsonThrust(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        LivingEntity target = TargetUtil.lookEntity(player, 3.8);
        if (target == null) {
            return fail(player, "no_target");
        }

        target.invulnerableTime = 0;
        target.hurt(level.damageSources().playerAttack(player), 10.0f);
        Vec3 dm = target.getDeltaMovement();
        target.setDeltaMovement(dm.x, 0.9, dm.z);
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));

        anim(ctx, KingCrimsonEntity.State.HEAVY, 18);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 20, 0.3, 0.4, 0.3, 0.3);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------------------------------

    private static void anim(SkillContext ctx, KingCrimsonEntity.State state, int ticks) {
        if (ctx.stand() instanceof KingCrimsonEntity kc && !kc.isRemote()) {
            kc.setState(state);
            kc.setMoveStun(ticks);
        }
    }

    private static boolean fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("skill.jcraft_expansion.msg." + key), true);
        return false;
    }

    private static boolean isEnemy(LivingEntity e, ServerPlayer owner) {
        return e != owner && !(e instanceof StandEntity<?, ?>) && !SkillTicker.isAlly(e, owner) && !e.isSpectator();
    }

    private static void burst(ServerLevel level, LivingEntity e, ParticleOptions particle, int count) {
        level.sendParticles(particle, e.getX(), e.getY(1.0), e.getZ(), count, 0.6, 0.7, 0.6, 0.0);
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle) {
        int steps = (int) Math.max(2, from.distanceTo(to) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(particle, p.x, p.y + 1.0, p.z, 2, 0.1, 0.4, 0.1, 0.01);
        }
    }

    private static void sound(ServerLevel level, LivingEntity e) {
        level.playSound(null, e.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.6f);
    }
}
