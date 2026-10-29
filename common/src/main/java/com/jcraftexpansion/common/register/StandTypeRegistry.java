package com.jcraftexpansion.common.register;

import com.jcraftexpansion.JCraftExpansion;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.arna.jcraft.api.JRegistries;
import net.arna.jcraft.api.stand.StandType;

public interface StandTypeRegistry {
    DeferredRegister<StandType> REGISTRY = DeferredRegister.create(JCraftExpansion.MOD_ID, JRegistries.STAND_TYPE_REGISTRY_KEY);

    RegistrySupplier<StandType> DIVER_DOWN = REGISTRY.register(JCraftExpansion.id("diver_down"), () ->
            StandType.of(JCraftExpansion.id("diver_down"), EntityTypeRegistry.DIVER_DOWN));
}
