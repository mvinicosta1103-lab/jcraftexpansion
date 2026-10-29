package com.jcraftexpansion.mixin;

import net.arna.jcraft.api.attack.enums.MoveInputType;
import net.arna.jcraft.api.stand.StandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Por padrão o JCraft só trata a tecla de barragem como "segurável" em alguns stands.
 * Aqui forçamos para todos, para o servidor saber que o B continua pressionado.
 */
@Mixin(StandEntity.class)
public abstract class StandEntityMixin {
    @Inject(method = "canHoldMove", at = @At("HEAD"), cancellable = true, remap = false)
    private void jcraftexpansion$barrageIsHoldable(MoveInputType type, CallbackInfoReturnable<Boolean> cir) {
        if (type == MoveInputType.BARRAGE) {
            cir.setReturnValue(true);
        }
    }
}
