package com.jcraftexpansion.common.util;

import net.arna.jcraft.api.stand.StandEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class TargetUtil {
    private TargetUtil() {
    }

    /** Entidade viva (que não seja stand) para onde o jogador está olhando, até {@code range} blocos. */
    public static LivingEntity lookEntity(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof LivingEntity le && le != player && le.isAlive() && !le.isSpectator()
                        && !(le instanceof StandEntity<?, ?>),
                range * range);
        if (result != null && result.getEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }
}
