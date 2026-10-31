package com.jcraftexpansion.mixin;

import com.jcraftexpansion.client.disguise.DisguiseRenderer;
import com.jcraftexpansion.common.disguise.DisguiseManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.util.JUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    /**
     * Stands só são visíveis para quem também tem um stand (ou está em modo espectador).
     * Exceção: um stand disfarçado (White Snake) aparece para todos, senão o disfarce não enganaria ninguém.
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void jcraftexpansion$standsOnlyVisibleToStandUsers(Entity entity, Frustum frustum, double camX,
                                                               double camY, double camZ,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof StandEntity<?, ?> stand)) {
            return;
        }
        if (DisguiseManager.isDisguised(stand)) {
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

    /** Stand disfarçado: desenha a cópia (jogador ou mob) no lugar do stand. */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void jcraftexpansion$renderDisguise(E entity, double x, double y, double z,
                                                                   float yaw, float partialTicks, PoseStack poseStack,
                                                                   MultiBufferSource buffer, int light,
                                                                   CallbackInfo ci) {
        if (!(entity instanceof StandEntity<?, ?> stand)) {
            return;
        }
        Entity copy = DisguiseRenderer.getCopy(stand);
        if (copy == null) {
            return;
        }
        ((EntityRenderDispatcher) (Object) this).render(copy, x, y, z, yaw, partialTicks, poseStack, buffer, light);
        ci.cancel();
    }
}
