package com.jcraftexpansion.mixin;

import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.util.JUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    /** Stands só são visíveis para quem também tem um stand (ou está em modo espectador). */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void jcraftexpansion$standsOnlyVisibleToStandUsers(Entity entity, Frustum frustum, double camX,
                                                               double camY, double camZ,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof StandEntity<?, ?>)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) {
            return;
        }
        if (JUtils.getStand(mc.player) == null) {
            cir.setReturnValue(false);
        }
    }
}
