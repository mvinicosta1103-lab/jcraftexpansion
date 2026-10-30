package com.jcraftexpansion.mixin;

import com.jcraftexpansion.common.standstate.StandFlags;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.client.renderer.entity.stands.StandEntityRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Em Stand Off os braços ficam levemente translúcidos. */
@Mixin(StandEntityRenderer.class)
public abstract class StandEntityRendererMixin {
    private static final float STAND_OFF_ALPHA = 0.55f;

    @Inject(method = "getAlpha", at = @At("HEAD"), cancellable = true, remap = false)
    private static void jcraftexpansion$standOffAlpha(StandEntity<?, ?> stand, float tickDelta,
                                                      CallbackInfoReturnable<Float> cir) {
        if (StandFlags.isStandOff(stand)) {
            cir.setReturnValue(STAND_OFF_ALPHA);
        }
    }

    @Inject(method = "renderTypeOf", at = @At("HEAD"), cancellable = true, remap = false)
    private static void jcraftexpansion$standOffRenderType(StandEntity<?, ?> stand, ResourceLocation texture,
                                                           CallbackInfoReturnable<RenderType> cir) {
        if (StandFlags.isStandOff(stand)) {
            cir.setReturnValue(RenderType.entityTranslucent(texture));
        }
    }
}
