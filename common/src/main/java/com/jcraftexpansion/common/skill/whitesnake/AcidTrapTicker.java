package com.jcraftexpansion.common.skill.whitesnake;

import com.jcraftexpansion.common.skill.SkillTicker;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.arna.jcraft.api.stand.StandEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Poças de ácido deixadas pela skill "Armadilha de Ácido": envenenam e atrasam quem pisar nelas. */
public final class AcidTrapTicker {
    private static final List<AcidZone> ZONES = new ArrayList<>();

    private AcidTrapTicker() {
    }

    public static void init() {
        TickEvent.SERVER_POST.register(AcidTrapTicker::tick);
        LifecycleEvent.SERVER_STOPPING.register(server -> ZONES.clear());
    }

    public static void addZone(ServerLevel level, Vec3 center, UUID owner, long endTick, double radius) {
        ZONES.add(new AcidZone(level, center, owner, endTick, radius));
    }

    private static void tick(MinecraftServer server) {
        Iterator<AcidZone> it = ZONES.iterator();
        while (it.hasNext()) {
            AcidZone z = it.next();
            long now = z.level.getGameTime();
            if (now >= z.endTick) {
                it.remove();
                continue;
            }

            if (now % 3 == 0) {
                double angle = z.level.random.nextDouble() * Math.PI * 2;
                double dist = Math.sqrt(z.level.random.nextDouble()) * z.radius;
                z.level.sendParticles(ParticleTypes.SNEEZE, z.center.x + Math.cos(angle) * dist, z.center.y + 0.1,
                        z.center.z + Math.sin(angle) * dist, 1, 0.05, 0.05, 0.05, 0.0);
            }

            if (now % 10 == 0) {
                Player owner = z.level.getPlayerByUUID(z.owner);
                AABB box = new AABB(z.center.x - z.radius, z.center.y - 1, z.center.z - z.radius,
                        z.center.x + z.radius, z.center.y + 2.5, z.center.z + z.radius);
                for (LivingEntity e : z.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                    if (e == owner || e instanceof StandEntity<?, ?> || SkillTicker.isAlly(e, owner)) {
                        continue;
                    }
                    if (e.position().distanceToSqr(z.center.x, e.getY(), z.center.z) <= z.radius * z.radius) {
                        e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                    }
                }
            }
        }
    }

    private record AcidZone(ServerLevel level, Vec3 center, UUID owner, long endTick, double radius) {
    }
}
