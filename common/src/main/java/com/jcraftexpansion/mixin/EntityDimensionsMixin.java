package com.jcraftexpansion.mixin;

import com.jcraftexpansion.common.disguise.DisguiseManager;
import net.arna.jcraft.api.stand.StandEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stand disfarçado tem o tamanho (hitbox) da entidade copiada. */
@Mixin(Entity.class)
public abstract class EntityDimensionsMixin {
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void jcraftexpansion$disguiseDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object) this instanceof StandEntity<?, ?> stand) {
            EntityDimensions dims = DisguiseManager.dimensionsOf(stand);
            if (dims != null) {
                cir.setReturnValue(dims);
            }
        }
    }
}
