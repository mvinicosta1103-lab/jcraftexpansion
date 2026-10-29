package com.jcraftexpansion.fabric.datagen;

import com.jcraftexpansion.common.register.StandTypeRegistry;
import com.jcraftexpansion.common.stand.DiverDownEntity;
import net.arna.jcraft.api.datagen.JCraftStandDataProvider;
import net.arna.jcraft.api.stand.StandData;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;

public class JCraftExpansionStandDataProvider extends JCraftStandDataProvider {
    public JCraftExpansionStandDataProvider(FabricDataOutput dataOutput) {
        super(dataOutput);
    }

    @Override
    protected void configure(BiConsumer<ResourceLocation, StandData> provider) {
        provider.accept(StandTypeRegistry.DIVER_DOWN.getId(), DiverDownEntity.DATA);
    }
}
