package com.jcraftexpansion.common.skill;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Efeitos de skills que duram vários ticks (Sobrecarga de Dor e Flor Curativa). */
public final class SkillTicker {
    private static final List<PainMark> PAIN = new ArrayList<>();
    private static final List<FlowerField> FIELDS = new ArrayList<>();

    private SkillTicker() {
    }

    public static void init() {
        TickEvent.SERVER_POST.register(SkillTicker::tick);
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            PAIN.clear();
            FIELDS.clear();
            SkillCooldowns.clearAll();
        });
    }

    public static void markPain(LivingEntity target, ServerPlayer owner, long endTick) {
        PAIN.removeIf(m -> m.target == target);
        PAIN.add(new PainMark(target, owner, endTick, target.getHealth()));
    }

    public static void addField(ServerLevel level, Vec3 center, UUID owner, long endTick, double radius) {
        FIELDS.add(new FlowerField(level, center, owner, endTick, radius));
    }

    private static void tick(MinecraftServer server) {
        tickPain();
        tickFields();
    }

    private static void tickPain() {
        Iterator<PainMark> it = PAIN.iterator();
        while (it.hasNext()) {
            PainMark m = it.next();
            if (m.target.isRemoved() || !m.target.isAlive() || !(m.target.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }

            float cur = m.target.getHealth();
            if (cur < m.lastHealth) {
                // 50% de todo dano recebido durante a marca fica "guardado" para estourar no final.
                m.stored = Math.min(20f, m.stored + (m.lastHealth - cur) * 0.5f);
            }
            m.lastHealth = cur;

            if (level.getGameTime() % 5 == 0) {
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, m.target.getX(), m.target.getY(0.6), m.target.getZ(),
                        2, 0.25, 0.3, 0.25, 0.0);
            }

            if (level.getGameTime() >= m.endTick) {
                if (m.stored > 0f) {
                    m.target.hurt(level.damageSources().indirectMagic(m.owner, m.owner), m.stored);
                    level.sendParticles(ParticleTypes.CRIT, m.target.getX(), m.target.getY(0.6), m.target.getZ(),
                            20, 0.4, 0.5, 0.4, 0.2);
                }
                it.remove();
            }
        }
    }

    private static void tickFields() {
        Iterator<FlowerField> it = FIELDS.iterator();
        while (it.hasNext()) {
            FlowerField f = it.next();
            long now = f.level.getGameTime();
            if (now >= f.endTick) {
                it.remove();
                continue;
            }

            if (now % 4 == 0) {
                double angle = f.level.random.nextDouble() * Math.PI * 2;
                double dist = Math.sqrt(f.level.random.nextDouble()) * f.radius;
                f.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, f.center.x + Math.cos(angle) * dist, f.center.y + 0.2,
                        f.center.z + Math.sin(angle) * dist, 2, 0.1, 0.2, 0.1, 0.0);
            }

            if (now % 20 == 0) {
                Player owner = f.level.getPlayerByUUID(f.owner);
                AABB box = new AABB(f.center.x - f.radius, f.center.y - 1, f.center.z - f.radius,
                        f.center.x + f.radius, f.center.y + 2.5, f.center.z + f.radius);
                for (LivingEntity e : f.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                    if (isAlly(e, owner) && e.position().distanceToSqr(f.center.x, e.getY(), f.center.z) <= f.radius * f.radius) {
                        e.heal(2.0f);
                        e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, true, false));
                        f.level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY(1.0), e.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
                    }
                }
            }
        }
    }

    /** Aliado = o próprio dono, outros jogadores ou animais domados pelo dono. */
    public static boolean isAlly(LivingEntity e, Player owner) {
        if (e instanceof Player) {
            return true;
        }
        return owner != null && e instanceof TamableAnimal t && t.isOwnedBy(owner);
    }

    private static final class PainMark {
        final LivingEntity target;
        final ServerPlayer owner;
        final long endTick;
        float lastHealth;
        float stored;

        PainMark(LivingEntity target, ServerPlayer owner, long endTick, float lastHealth) {
            this.target = target;
            this.owner = owner;
            this.endTick = endTick;
            this.lastHealth = lastHealth;
        }
    }

    private record FlowerField(ServerLevel level, Vec3 center, UUID owner, long endTick, double radius) {
    }
}
