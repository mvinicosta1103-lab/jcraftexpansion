package com.jcraftexpansion.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.jcraftexpansion.JCraftExpansion;

@Mod(JCraftExpansion.MOD_ID)
public final class JCraftExpansionForge {
    public JCraftExpansionForge(FMLJavaModLoadingContext ctx) {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(JCraftExpansion.MOD_ID, ctx.getModEventBus());

        // Run our common setup.
        JCraftExpansion.init();
    }
}
