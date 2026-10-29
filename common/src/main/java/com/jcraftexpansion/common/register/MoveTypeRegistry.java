package com.jcraftexpansion.common.register;

import com.jcraftexpansion.JCraftExpansion;
import com.jcraftexpansion.common.attack.HeldBarrageAttack;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.arna.jcraft.api.JRegistries;
import net.arna.jcraft.api.attack.MoveType;

public interface MoveTypeRegistry {
    DeferredRegister<MoveType<?>> REGISTRY = DeferredRegister.create(JCraftExpansion.MOD_ID, JRegistries.MOVE_TYPE_REGISTRY_KEY);

    RegistrySupplier<MoveType<?>> HELD_BARRAGE_ATTACK = REGISTRY.register("held_barrage_attack", () -> HeldBarrageAttack.Type.INSTANCE);
}