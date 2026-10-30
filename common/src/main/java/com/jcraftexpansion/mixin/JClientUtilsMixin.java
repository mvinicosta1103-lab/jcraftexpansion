package com.jcraftexpansion.mixin;

import com.jcraftexpansion.client.standstate.BonePoser;
import mod.azure.azurelib.animation.AzAnimationContext;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.client.util.JClientUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JClientUtils.class)
public abstract class JClientUtilsMixin {
    /** Roda depois da animação genérica de stands humanoides: aplica Stand Off e pose de chutes. */
    @Inject(method = "animateGenericHumanoid", at = @At("RETURN"), remap = false)
    private static void jcraftexpansion$poseBones(AzAnimationContext<?> context, StandEntity<?, ?> entity,
                                                  boolean flipBody, boolean flipHead, float tPO, float hPO,
                                                  float velInfluence, CallbackInfo ci) {
        BonePoser.apply(context.boneCache().getBakedModel(), entity);
    }
}
