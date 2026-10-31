package com.jcraftexpansion.common.skill.kingcrimson;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Efeitos de tempo das skills do King Crimson: dano atrasado, tempo apagado, rajada fantasma, retrocesso e esquiva. */
public final class KingCrimsonTicker {
    private static final List<DelayedHit> DELAYED = new ArrayList<>();
    private static final List<Frozen> FROZEN = new ArrayList<>();
    private static final List<Flurry> FLURRIES = new ArrayList<>();
    private static final List<Rewind> REWINDS = new ArrayList<>();
    private static final List<Dodge> DODGES = new ArrayList<>();

    private KingCrimsonTicker() {
    }

    public static void init() {
        TickEvent.SERVER_POST.register(KingCrimsonTicker::tick);
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            // Devolve a IA dos mobs congelados antes de limpar.
            for (Frozen f : FROZEN) {
                f.release();
            }
            DELAYED.clear();
            FROZEN.clear();
            FLURRIES.clear();
            REWINDS.clear();
            DODGES.clear();
        });
    }

    // ------------------------------------------------------------------ registrar efeitos

    /** Corte Apagado: o dano "já aconteceu" no tempo apagado e só aparece depois. */
    public static void addDelayedHit(LivingEntity target, ServerPlayer owner, float damage, long endTick) {
        DELAYED.add(new DelayedHit(target, owner, damage, endTick));
    }

    /** Apagar Tempo: o alvo fica parado até o tick {@code endTick}. */
    public static void freeze(LivingEntity target, long endTick) {
        for (Frozen f : FROZEN) {
            if (f.target == target) {
                f.endTick = Math.max(f.endTick, endTick);
                return;
            }
        }
        boolean freezeAi = target instanceof Mob mob && !mob.isNoAi();
        if (freezeAi) {
            ((Mob) target).setNoAi(true);
        }
        FROZEN.add(new Frozen(target, endTick, freezeAi));
    }

    /** Rajada Fantasma: {@code hits} golpes teletransportados, um a cada {@code interval} ticks. */
    public static void addFlurry(ServerPlayer owner, LivingEntity target, int hits, int interval, long startTick) {
        FLURRIES.add(new Flurry(owner, target, hits, interval, startTick));
    }

    /** Retrocesso: depois de {@code endTick} o jogador volta para onde estava e recupera a vida que tinha. */
    public static void addRewind(ServerPlayer player, long endTick) {
        REWINDS.removeIf(r -> r.player == player);
        REWINDS.add(new Rewind(player, player.serverLevel().dimension(), player.position(), player.getHealth(), endTick));
    }

    /** Esquiva Fantasma: durante a janela o dano recebido é apagado e o atacante leva um contra-ataque. */
    public static void addDodge(ServerPlayer player, long endTick) {
        DODGES.removeIf(d -> d.player == player);
        DODGES.add(new Dodge(player, endTick, player.getHealth()));
    }

    // ------------------------------------------------------------------ tick

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        tickDelayed(now);
        tickFrozen(now);
        tickFlurries(now);
        tickRewinds(server, now);
        tickDodges(now);
    }

    private static void tickDelayed(long now) {
        Iterator<DelayedHit> it = DELAYED.iterator();
        while (it.hasNext()) {
            DelayedHit h = it.next();
            if (h.target.isRemoved() || !h.target.isAlive()) {
                it.remove();
                continue;
            }
            if (now >= h.endTick) {
                if (h.target.level() instanceof ServerLevel level) {
                    h.target.invulnerableTime = 0;
                    h.target.hurt(level.damageSources().playerAttack(h.owner), h.damage);
                    level.sendParticles(ParticleTypes.CRIT, h.target.getX(), h.target.getY(0.6), h.target.getZ(),
                            25, 0.4, 0.5, 0.4, 0.3);
                    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, h.target.getX(), h.target.getY(0.6),
                            h.target.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
                }
                it.remove();
            }
        }
    }

    private static void tickFrozen(long now) {
        Iterator<Frozen> it = FROZEN.iterator();
        while (it.hasNext()) {
            Frozen f = it.next();
            boolean gone = f.target.isRemoved() || !f.target.isAlive();
            if (gone || now >= f.endTick) {
                f.release();
                it.remove();
                continue;
            }
            // Parado no tempo: sem movimento próprio e sem cair/avançar.
            f.target.setDeltaMovement(Vec3.ZERO);
            f.target.hurtMarked = true;
            if (f.target.level() instanceof ServerLevel level && now % 10 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, f.target.getX(), f.target.getY(0.5), f.target.getZ(),
                        6, 0.3, 0.5, 0.3, 0.5);
            }
        }
    }

    private static void tickFlurries(long now) {
        Iterator<Flurry> it = FLURRIES.iterator();
        while (it.hasNext()) {
            Flurry f = it.next();
            if (!f.owner.isAlive() || f.owner.hasDisconnected() || f.target.isRemoved() || !f.target.isAlive()
                    || f.owner.level() != f.target.level()) {
                it.remove();
                continue;
            }
            if (now < f.nextTick) {
                continue;
            }
            f.nextTick = now + f.interval;
            f.hitsLeft--;

            ServerLevel level = f.owner.serverLevel();
            Vec3 from = f.owner.position();
            double angle = level.random.nextDouble() * Math.PI * 2;
            Vec3 dest = f.target.position().add(Math.cos(angle) * 1.6, 0.0, Math.sin(angle) * 1.6);
            if (level.noCollision(f.owner, f.owner.getBoundingBox().move(dest.subtract(from)))) {
                level.sendParticles(ParticleTypes.SMOKE, from.x, from.y + 1.0, from.z, 12, 0.3, 0.6, 0.3, 0.02);
                f.owner.teleportTo(dest.x, dest.y, dest.z);
                level.sendParticles(ParticleTypes.CRIMSON_SPORE, dest.x, dest.y + 1.0, dest.z, 12, 0.3, 0.6, 0.3, 0.02);
            }
            f.owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    f.target.getEyePosition());

            f.target.invulnerableTime = 0;
            boolean last = f.hitsLeft <= 0;
            f.target.hurt(level.damageSources().playerAttack(f.owner), last ? 4.0f : 2.0f);
            if (last) {
                Vec3 dir = f.target.position().subtract(f.owner.position()).multiply(1, 0, 1).normalize();
                f.target.knockback(1.1, -dir.x, -dir.z);
                it.remove();
            }
        }
    }

    private static void tickRewinds(MinecraftServer server, long now) {
        Iterator<Rewind> it = REWINDS.iterator();
        while (it.hasNext()) {
            Rewind r = it.next();
            if (!r.player.isAlive() || r.player.hasDisconnected()) {
                it.remove();
                continue;
            }
            if (now < r.endTick) {
                continue;
            }
            ServerLevel saved = server.getLevel(r.dimension);
            if (saved != null && r.player.serverLevel() == saved) {
                saved.sendParticles(ParticleTypes.PORTAL, r.player.getX(), r.player.getY(1.0), r.player.getZ(),
                        30, 0.4, 0.8, 0.4, 0.5);
                r.player.teleportTo(r.pos.x, r.pos.y, r.pos.z);
                saved.sendParticles(ParticleTypes.PORTAL, r.pos.x, r.pos.y + 1.0, r.pos.z, 30, 0.4, 0.8, 0.4, 0.5);
            }
            if (r.player.getHealth() < r.health) {
                r.player.setHealth(r.health);
            }
            r.player.clearFire();
            r.player.fallDistance = 0.0f;
            r.player.displayClientMessage(Component.translatable("skill.jcraft_expansion.msg.rewind_done"), true);
            it.remove();
        }
    }

    private static void tickDodges(long now) {
        Iterator<Dodge> it = DODGES.iterator();
        while (it.hasNext()) {
            Dodge d = it.next();
            if (!d.player.isAlive() || d.player.hasDisconnected() || now >= d.endTick) {
                it.remove();
                continue;
            }
            float current = d.player.getHealth();
            if (current < d.lastHealth) {
                // O dano foi apagado do tempo: devolvemos a vida e punimos quem atacou.
                d.player.setHealth(d.lastHealth);
                ServerLevel level = d.player.serverLevel();
                LivingEntity attacker = d.player.getLastHurtByMob();
                level.sendParticles(ParticleTypes.SMOKE, d.player.getX(), d.player.getY(1.0), d.player.getZ(),
                        20, 0.3, 0.6, 0.3, 0.03);
                if (attacker != null && attacker.isAlive() && attacker.level() == d.player.level()) {
                    Vec3 behind = attacker.position().subtract(
                            attacker.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
                    if (level.noCollision(d.player, d.player.getBoundingBox().move(behind.subtract(d.player.position())))) {
                        d.player.teleportTo(behind.x, behind.y, behind.z);
                    }
                    attacker.invulnerableTime = 0;
                    attacker.hurt(level.damageSources().playerAttack(d.player), 5.0f);
                }
                d.player.displayClientMessage(Component.translatable("skill.jcraft_expansion.msg.dodge_used"), true);
                it.remove();
                continue;
            }
            d.lastHealth = current;
        }
    }

    // ------------------------------------------------------------------ dados

    private record DelayedHit(LivingEntity target, ServerPlayer owner, float damage, long endTick) {
    }

    private static final class Frozen {
        final LivingEntity target;
        long endTick;
        final boolean restoreAi;

        Frozen(LivingEntity target, long endTick, boolean restoreAi) {
            this.target = target;
            this.endTick = endTick;
            this.restoreAi = restoreAi;
        }

        void release() {
            if (restoreAi && target instanceof Mob mob) {
                mob.setNoAi(false);
            }
        }
    }

    private static final class Flurry {
        final ServerPlayer owner;
        final LivingEntity target;
        int hitsLeft;
        final int interval;
        long nextTick;

        Flurry(ServerPlayer owner, LivingEntity target, int hits, int interval, long startTick) {
            this.owner = owner;
            this.target = target;
            this.hitsLeft = hits;
            this.interval = interval;
            this.nextTick = startTick;
        }
    }

    private record Rewind(ServerPlayer player, ResourceKey<Level> dimension, Vec3 pos, float health, long endTick) {
    }

    private static final class Dodge {
        final ServerPlayer player;
        final long endTick;
        float lastHealth;

        Dodge(ServerPlayer player, long endTick, float lastHealth) {
            this.player = player;
            this.endTick = endTick;
            this.lastHealth = lastHealth;
        }
    }
}
