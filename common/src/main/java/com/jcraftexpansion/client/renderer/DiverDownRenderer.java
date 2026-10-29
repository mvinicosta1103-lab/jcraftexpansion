package com.jcraftexpansion.client.renderer;

import com.jcraftexpansion.common.register.StandTypeRegistry;
import com.jcraftexpansion.common.stand.DiverDownEntity;
import net.arna.jcraft.client.renderer.entity.stands.StandEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class DiverDownRenderer extends StandEntityRenderer<DiverDownEntity> {
    public DiverDownRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, StandTypeRegistry.DIVER_DOWN.get());
    }
}
