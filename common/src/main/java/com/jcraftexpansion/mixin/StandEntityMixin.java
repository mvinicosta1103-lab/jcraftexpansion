package com.jcraftexpansion.mixin;

import com.jcraftexpansion.common.standstate.StandFlags;
import net.arna.jcraft.api.attack.enums.MoveClass;
import net.arna.jcraft.api.attack.enums.MoveInputType;
import net.arna.jcraft.api.stand.StandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StandEntity.class)
public abstract class StandEntityMixin {
    /**
     * Por padrão o JCraft só trata a tecla de barragem como "segurável" em alguns stands.
     * Aqui forçamos para todos, para o servidor saber que o B continua pressionado.
     */
    @Inject(method = "canHoldMove", at = @At("HEAD"), cancellable = true, remap = false)
    private void jcraftexpansion$barrageIsHoldable(MoveInputType type, CallbackInfoReturnable<Boolean> cir) {
        if (type == MoveInputType.BARRAGE) {
            cir.setReturnValue(true);
        }
    }

    /** Modo Stand Off: só golpes de braço (leve, pesado e barragem) podem ser usados. */
    @Inject(method = "handleMove(Lnet/arna/jcraft/api/attack/enums/MoveClass;)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void jcraftexpansion$standOffRestrictsMoves(MoveClass moveClass, CallbackInfoReturnable<Boolean> cir) {
        StandEntity<?, ?> self = (StandEntity<?, ?>) (Object) this;
        if (StandFlags.isStandOff(self)
                && moveClass != MoveClass.LIGHT && moveClass != MoveClass.HEAVY && moveClass != MoveClass.BARRAGE) {
            cir.setReturnValue(false);
        }
    }
}
