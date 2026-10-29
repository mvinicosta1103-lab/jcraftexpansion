package com.jcraftexpansion.common.register;

import com.jcraftexpansion.JCraftExpansion;
import com.jcraftexpansion.common.stand.DiverDownEntity;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public interface EntityTypeRegistry {
    DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(JCraftExpansion.MOD_ID, Registries.ENTITY_TYPE);

    RegistrySupplier<EntityType<DiverDownEntity>> DIVER_DOWN = REGISTRY.register("diver_down", () ->
            EntityType.Builder.of((EntityType.EntityFactory<DiverDownEntity>)
                            (t, level) -> new DiverDownEntity(level),
                            MobCategory.CREATURE)
                    .sized(0.6f, 1.8f)
                    .build("diver_down"));

    static void registerAttributes() {
        EntityAttributeRegistry.register(DIVER_DOWN, DiverDownEntity::createMobAttributes);
    }
}
