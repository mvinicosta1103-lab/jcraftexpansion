package com.jcraftexpansion.common.skill.cmoon;

import com.jcraftexpansion.common.skill.SimpleSkill;
import com.jcraftexpansion.common.skill.SkillContext;
import com.jcraftexpansion.common.skill.SkillTicker;
import com.jcraftexpansion.common.skill.StandSkill;
import com.jcraftexpansion.common.standstate.AutopilotManager;
import com.jcraftexpansion.common.util.TargetUtil;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.entity.stand.CMoonEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
 * As 9 skills extras do C-Moon (hotbar de skills, teclas 1-9): pilotos (guardião e manual) e habilidades de
 * gravidade e inversão. Reaproveitam as animações que o C-Moon já tem.
 */
public final class CMoonSkills {
    public static final List<StandSkill> SKILLS = List.of(
            new SimpleSkill("guardian_pilot", Items.SHIELD, 100, CMoonSkills::guardianPilot),               // 1
            new SimpleSkill("pilot_mode", Items.SPYGLASS, 40, CMoonPilot::toggle),                         // 2
            new SimpleSkill("gravity_well", Items.CRYING_OBSIDIAN, 600, CMoonSkills::gravityWell),         // 3
            new SimpleSkill("anti_gravity", Items.FEATHER, 500, CMoonSkills::antiGravity),                 // 4
            new SimpleSkill("inversion_wave", Items.ECHO_SHARD, 400, CMoonSkills::inversionWave),          // 5
            new SimpleSkill("lunar_leap", Items.RABBIT_FOOT, 200, CMoonSkills::lunarLeap),                 // 6
            new SimpleSkill("gravity_ward", Items.NAUTILUS_SHELL, 700, CMoonSkills::gravityWard),          // 7
            new SimpleSkill("gravity_crush", Items.ANVIL, 450, CMoonSkills::gravityCrush),                 // 8
            new SimpleSkill("heaven_drop", Items.NETHER_STAR, 500, CMoonSkills::heavenDrop)                // 9
    );

    private CMoonSkills() {
    }

    // ------------------------------------------------------------------------------------------
    // 1 - Piloto Guardião: o stand anda em volta de você e ataca quem atacar você. Usar de novo desliga.
    //     (Mesmo piloto automático do White Snake.)
    // ------------------------------------------------------------------------------------------
    private static boolean guardianPilot(SkillContext ctx) {
        return AutopilotManager.toggleGuard(ctx.player());
    }

    // ------------------------------------------------------------------------------------------
    // 3 - Poço Gravitacional: cria um poço no ponto olhado que puxa os inimigos por 4 s e implode no final.
    // ------------------------------------------------------------------------------------------
    private static boolean gravityWell(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();

        Vec3 center;
        LivingEntity target = TargetUtil.lookEntity(player, 24.0);
        if (target != null) {
            center = target.position();
        } else {
            BlockHitResult hit = lookBlock(player, 24.0);
            if (hit == null) {
                return fail(player, "no_target");
            }
            center = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        }

        CMoonTicker.addWell(level, center, player, level.getServer().getTickCount() + 80, 7.0);

        anim(ctx, CMoonEntity.State.GRAV_SHIFT, 20);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.5, center.z, 30, 0.5, 0.5, 0.5, 0.2);
        sound(level, player, SoundEvents.BEACON_ACTIVATE, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 4 - Antigravidade: inimigos num raio de 6 blocos flutuam por 3 s e depois despencam.
    // ------------------------------------------------------------------------------------------
    private static boolean antiGravity(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        double radius = 6.0;

        int count = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                LivingEntity::isAlive)) {
            if (!isEnemy(e, player) || e.distanceToSqr(player) > radius * radius) {
                continue;
            }
            e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 1));
            burst(level, e, ParticleTypes.END_ROD, 8);
            count++;
        }
        if (count == 0) {
            return fail(player, "no_target");
        }

        anim(ctx, CMoonEntity.State.DIRECTIONAL_SHIFT, 20);
        sound(level, player, SoundEvents.BEACON_POWER_SELECT, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 5 - Onda de Inversão: inimigos próximos são virados de costas, têm o movimento invertido e ficam confusos.
    // ------------------------------------------------------------------------------------------
    private static boolean inversionWave(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        double radius = 5.0;

        int count = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                LivingEntity::isAlive)) {
            if (!isEnemy(e, player) || e.distanceToSqr(player) > radius * radius) {
                continue;
            }
            float yaw = e.getYRot() + 180.0f;
            if (e instanceof ServerPlayer sp) {
                sp.connection.teleport(sp.getX(), sp.getY(), sp.getZ(), yaw, sp.getXRot());
            } else {
                e.setYRot(yaw);
                e.setYHeadRot(yaw);
                e.setYBodyRot(yaw);
            }
            if (e instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            Vec3 dm = e.getDeltaMovement();
            e.setDeltaMovement(-dm.x, Math.max(dm.y, 0.2), -dm.z);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            burst(level, e, ParticleTypes.PORTAL, 15);
            count++;
        }
        if (count == 0) {
            return fail(player, "no_target");
        }

        anim(ctx, CMoonEntity.State.GRAV_SHIFT, 20);
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32.0;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX() + Math.cos(a) * radius * 0.8,
                    player.getY() + 0.3, player.getZ() + Math.sin(a) * radius * 0.8, 1, 0.0, 0.1, 0.0, 0.0);
        }
        sound(level, player, SoundEvents.ENDERMAN_TELEPORT, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 6 - Salto Lunar: você é lançado para cima e para frente e cai devagar (sem dano de queda).
    // ------------------------------------------------------------------------------------------
    private static boolean lunarLeap(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();

        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
        player.setDeltaMovement(look.x * 0.9, 1.1, look.z * 0.9);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0, true, false));

        anim(ctx, CMoonEntity.State.GROUND_SHOOT, 12);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 15, 0.4, 0.1, 0.4, 0.05);
        sound(level, player, SoundEvents.ENDERMAN_TELEPORT, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 7 - Escudo Gravitacional: por 5 s projéteis que chegam perto de você são desviados e você resiste mais.
    // ------------------------------------------------------------------------------------------
    private static boolean gravityWard(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();

        CMoonTicker.addWard(player, level.getServer().getTickCount() + 100);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));

        anim(ctx, CMoonEntity.State.BLOCK, 15);
        burst(level, player, ParticleTypes.REVERSE_PORTAL, 25);
        sound(level, player, SoundEvents.BEACON_ACTIVATE, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 8 - Esmagamento Gravitacional: a gravidade do alvo aumenta por 3 s; ele é pressionado ao chão e fica lento.
    // ------------------------------------------------------------------------------------------
    private static boolean gravityCrush(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        LivingEntity target = TargetUtil.lookEntity(player, 18.0);
        if (target == null) {
            return fail(player, "no_target");
        }

        CMoonTicker.addCrush(target, player, level.getServer().getTickCount() + 60);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 1));
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }

        anim(ctx, CMoonEntity.State.GRAV_PUNCH, 18);
        burst(level, target, ParticleTypes.CRIT, 20);
        sound(level, player, SoundEvents.ANVIL_LAND, 0.6f);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 9 - Queda Celestial: o alvo é lançado ao céu e, em seguida, jogado de volta ao chão com um impacto em área.
    // ------------------------------------------------------------------------------------------
    private static boolean heavenDrop(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        LivingEntity target = TargetUtil.lookEntity(player, 16.0);
        if (target == null) {
            return fail(player, "no_target");
        }

        long now = level.getServer().getTickCount();
        target.setDeltaMovement(0.0, 1.4, 0.0);
        target.hurtMarked = true;
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        CMoonTicker.addDrop(target, player, now + 22, now + 22 + 80);

        anim(ctx, CMoonEntity.State.GROUND_SLAM, 22);
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.5), target.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
        sound(level, player, SoundEvents.BEACON_POWER_SELECT, 1.0f);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------------------------------

    /** Toca uma animação que o C-Moon já tem (não interrompe o stand no modo piloto nem em outra ação). */
    private static void anim(SkillContext ctx, CMoonEntity.State state, int ticks) {
        if (ctx.stand() instanceof CMoonEntity cm && !cm.isRemote() && cm.getCurrentMove() == null
                && cm.getMoveStun() <= 0) {
            cm.setState(state);
            cm.setMoveStun(ticks);
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

    private static void sound(ServerLevel level, LivingEntity e, SoundEvent sound, float pitch) {
        level.playSound(null, e.blockPosition(), sound, SoundSource.PLAYERS, 0.8f, pitch);
    }

    private static BlockHitResult lookBlock(ServerPlayer player, double range) {
        HitResult hit = player.pick(range, 1.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit;
        }
        return null;
    }
}
