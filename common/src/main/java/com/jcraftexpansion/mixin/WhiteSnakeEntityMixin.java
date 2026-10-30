package com.jcraftexpansion.mixin;

import com.jcraftexpansion.common.skill.whitesnake.WhiteSnakePilotRange;
import net.arna.jcraft.common.entity.stand.WhiteSnakeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(WhiteSnakeEntity.class)
public abstract class WhiteSnakeEntityMixin {
    /**
     * Em {@code tickRemoteMovement}, o JCraft compara {@code distanceToSqr(usuário) > 400} (20 blocos) e,
     * se passar, puxa o stand de volta. Trocamos o 400 pelo nosso limite maior.
     * (Esse método roda no cliente e no servidor, então o mixin fica em "mixins", não só em "client".)
     */
    @ModifyConstant(method = "tickRemoteMovement", constant = @Constant(doubleValue = 400.0), remap = false)
    private double jcraftexpansion$longerPilotRange(double original) {
        return WhiteSnakePilotRange.MAX_DISTANCE_SQR;
    }
}
