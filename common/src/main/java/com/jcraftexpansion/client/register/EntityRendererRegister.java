package com.jcraftexpansion.client.register;

import com.jcraftexpansion.client.renderer.DiverDownRenderer;
import com.jcraftexpansion.common.register.EntityTypeRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

public interface EntityRendererRegister {
    static void register(EntityRendererRegistrar registrar) {
        registrar.register(EntityTypeRegistry.DIVER_DOWN, DiverDownRenderer::new);
    }

    @FunctionalInterface
    interface EntityRendererRegistrar {
        <T extends Entity> void register(Supplier<? extends EntityType<? extends T>> type,
                                         EntityRendererProvider<T> rendererProvider);
    }
}
