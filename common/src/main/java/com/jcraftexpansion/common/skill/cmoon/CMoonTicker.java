package com.jcraftexpansion.common.skill.cmoon;

import com.jcraftexpansion.common.skill.SkillTicker;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Efeitos de gravidade do C-Moon que duram vários ticks: Poço Gravitacional, Esmagamento, Queda Celestial e Escudo. */
public final class CMoonTicker {
    private static final List<Well> WELLS = new ArrayList<>();
    private static final List<Crush> CRUSHES = new ArrayList<>();
    private static final List<Drop> DROPS = new ArrayList<>();
    private static final List<Ward> WARDS = new ArrayList<>();

    private CMoonTicker() {
    }

    public static void init() {
        TickEvent.SERVER_POST.register(CMoonTicker::tick);
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            WELLS.clear();
            CRUSHES.clear();
            DROPS.clear();
            WARDS.clear();
        });
    }

    // ------------------------------------------------------------------ registrar efeitos

    /** Poço Gravitacional: puxa inimigos para o centro até {@code endTick}; no fim, implode. */
    public static void addWell(ServerLevel level, Vec3 center, ServerPlayer owner, long endTick, double radius) {
        WELLS.add(new Well(level, center, owner.getUUID(), endTick, radius));
    }

    /** Esmagamento: a gravidade do alvo aumenta (ele é pressionado para baixo) até {@code endTick}. */
    public static void addCrush(LivingEntity target, ServerPlayer owner, long endTick) {
        CRUSHES.removeIf(c -> c.target == target);
        CRUSHES.add(new Crush(target, owner.getUUID(), endTick));
    }

    /** Queda Celestial: o alvo já foi lançado para cima; em {@code slamTick} é arremessado de volta ao chão. */
    public static void addDrop(LivingEntity target, ServerPlayer owner, long slamTick, long giveUpTick) {
        DROPS.removeIf(d -> d.target == target);
        DROPS.add(new Drop(target, owner.getUUID(), slamTick, giveUpTick));
    }

    /** Escudo Gravitacional: até {@code endTick} projéteis que chegarem perto do dono são rebatidos. */
    public static void addWard(ServerPlayer owner, long endTick) {
        WARDS.removeIf(w -> w.owner == owner);
        WARDS.add(new Ward(owner, endTick));
    }

    // ------------------------------------------------------------------ tick

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        tickWells(now);
        tickCrushes(now);
        tickDrops(now);
        tickWards(now);
    }

    private static void tickWells(long now) {
        Iterator<Well> it = WELLS.iterator();
        while (it.hasNext()) {
            Well w = it.next();
            ServerLevel level = w.level;
            Player owner = level.getPlayerByUUID(w.owner);

            if (now % 3 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double r = w.radius * (0.4 + level.random.nextDouble() * 0.6);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, w.center.x + Math.cos(a) * r, w.center.y + 0.3,
                            w.center.z + Math.sin(a) * r, 1, 0.0, 0.1, 0.0, 0.0);
                }
                level.sendParticles(ParticleTypes.PORTAL, w.center.x, w.center.y + 0.5, w.center.z, 4, 0.2, 0.3, 0.2, 0.3);
            }

            boolean ending = now >= w.endTick;
            double pullRadiusSqr = w.radius * w.radius;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(w.center, w.center).inflate(w.radius, 3.0, w.radius),
                    e -> isEnemy(e, owner))) {
                double dSqr = e.position().distanceToSqr(w.center);
                if (dSqr > pullRadiusSqr) {
                    continue;
                }
                if (ending) {
                    if (dSqr <= 9.0) { // implosão: só quem ficou perto do centro
                        e.invulnerableTime = 0;
                        e.hurt(magic(level, owner), 5.0f);
                    }
                    continue;
                }
                Vec3 toCenter = w.center.subtract(e.position());
                double len = toCenter.length();
                if (len < 0.6) {
                    continue;
                }
                Vec3 pull = toCenter.scale(0.13 / len);
                Vec3 dm = e.getDeltaMovement();
                e.setDeltaMovement(dm.x * 0.6 + pull.x, dm.y, dm.z * 0.6 + pull.z);
                e.hurtMarked = true;
            }

            if (ending) {
                level.sendParticles(ParticleTypes.EXPLOSION, w.center.x, w.center.y + 0.5, w.center.z, 1, 0, 0, 0, 0);
                level.playSound(null, net.minecraft.core.BlockPos.containing(w.center), SoundEvents.GENERIC_EXPLODE,
                        SoundSource.PLAYERS, 0.8f, 1.4f);
                it.remove();
            }
        }
    }

    private static void tickCrushes(long now) {
        Iterator<Crush> it = CRUSHES.iterator();
        while (it.hasNext()) {
            Crush c = it.next();
            if (c.target.isRemoved() || !c.target.isAlive() || !(c.target.level() instanceof ServerLevel level)
                    || now >= c.endTick) {
                it.remove();
                continue;
            }

            Vec3 dm = c.target.getDeltaMovement();
            c.target.setDeltaMovement(dm.x * 0.3, Math.min(dm.y, -0.7), dm.z * 0.3);
            c.target.hurtMarked = true;

            if (now % 5 == 0) {
                level.sendParticles(ParticleTypes.CRIT, c.target.getX(), c.target.getY(1.0), c.target.getZ(),
                        3, 0.25, 0.1, 0.25, 0.0);
            }
            if (now % 20 == 0) {
                c.target.invulnerableTime = 0;
                c.target.hurt(magic(level, level.getPlayerByUUID(c.owner)), 2.0f);
            }
        }
    }

    private static void tickDrops(long now) {
        Iterator<Drop> it = DROPS.iterator();
        while (it.hasNext()) {
            Drop d = it.next();
            if (d.target.isRemoved() || !d.target.isAlive() || !(d.target.level() instanceof ServerLevel level)
                    || now >= d.giveUpTick) {
                it.remove();
                continue;
            }

            if (!d.slammed) {
                if (now < d.slamTick) {
                    continue;
                }
                d.slammed = true;
                d.target.setDeltaMovement(0.0, -2.6, 0.0);
                d.target.hurtMarked = true;
                level.playSound(null, d.target.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                        0.8f, 0.5f);
                continue;
            }

            // Já foi arremessado para baixo: mantém a velocidade até tocar o chão.
            if (!d.target.onGround()) {
                Vec3 dm = d.target.getDeltaMovement();
                d.target.setDeltaMovement(dm.x * 0.2, Math.min(dm.y, -2.0), dm.z * 0.2);
                d.target.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, d.target.getX(), d.target.getY(0.5), d.target.getZ(),
                        2, 0.1, 0.3, 0.1, 0.02);
                continue;
            }

            Player owner = level.getPlayerByUUID(d.owner);
            d.target.invulnerableTime = 0;
            d.target.hurt(magic(level, owner), 10.0f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, d.target.getBoundingBox().inflate(3.0),
                    e -> e != d.target && isEnemy(e, owner))) {
                Vec3 dir = e.position().subtract(d.target.position()).multiply(1, 0, 1);
                dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
                e.invulnerableTime = 0;
                e.hurt(magic(level, owner), 4.0f);
                e.setDeltaMovement(dir.x * 0.8, 0.5, dir.z * 0.8);
                e.hurtMarked = true;
            }
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24.0;
                level.sendParticles(ParticleTypes.CLOUD, d.target.getX() + Math.cos(a) * 2.2, d.target.getY() + 0.2,
                        d.target.getZ() + Math.sin(a) * 2.2, 1, 0.0, 0.05, 0.0, 0.0);
            }
            level.sendParticles(ParticleTypes.EXPLOSION, d.target.getX(), d.target.getY() + 0.3, d.target.getZ(),
                    1, 0, 0, 0, 0);
            level.playSound(null, d.target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.7f);
            it.remove();
        }
    }

    private static void tickWards(long now) {
        Iterator<Ward> it = WARDS.iterator();
        while (it.hasNext()) {
            Ward w = it.next();
            if (w.owner.isRemoved() || !w.owner.isAlive() || now >= w.endTick
                    || !(w.owner.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }

            if (now % 4 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6.0 + now * 0.2;
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, w.owner.getX() + Math.cos(a) * 1.6,
                            w.owner.getY(0.5), w.owner.getZ() + Math.sin(a) * 1.6, 1, 0.0, 0.1, 0.0, 0.0);
                }
            }

            for (Projectile p : level.getEntitiesOfClass(Projectile.class, w.owner.getBoundingBox().inflate(3.5),
                    p -> p.isAlive() && p.getOwner() != w.owner)) {
                Vec3 away = p.position().subtract(w.owner.position());
                away = away.lengthSqr() < 1.0E-4 ? w.owner.getLookAngle() : away.normalize();
                double speed = Math.max(0.8, p.getDeltaMovement().length());
                p.setDeltaMovement(away.scale(speed));
                p.setOwner(w.owner); // passa a ser "do dono": não é rebatido de novo
                p.hurtMarked = true;
                level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY(), p.getZ(), 6, 0.1, 0.1, 0.1, 0.2);
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static boolean isEnemy(LivingEntity e, Player owner) {
        return e.isAlive() && e != owner && !e.isSpectator()
                && !(e instanceof net.arna.jcraft.api.stand.StandEntity<?, ?>)
                && !SkillTicker.isAlly(e, owner);
    }

    private static DamageSource magic(ServerLevel level, Player owner) {
        return owner == null ? level.damageSources().magic() : level.damageSources().indirectMagic(owner, owner);
    }

    private static final class Well {
        final ServerLevel level;
        final Vec3 center;
        final UUID owner;
        final long endTick;
        final double radius;

        Well(ServerLevel level, Vec3 center, UUID owner, long endTick, double radius) {
            this.level = level;
            this.center = center;
            this.owner = owner;
            this.endTick = endTick;
            this.radius = radius;
        }
    }

    private record Crush(LivingEntity target, UUID owner, long endTick) {
    }

    private static final class Drop {
        final LivingEntity target;
        final UUID owner;
        final long slamTick;
        final long giveUpTick;
        boolean slammed;

        Drop(LivingEntity target, UUID owner, long slamTick, long giveUpTick) {
            this.target = target;
            this.owner = owner;
            this.slamTick = slamTick;
            this.giveUpTick = giveUpTick;
        }
    }

    private record Ward(ServerPlayer owner, long endTick) {
    }
}
