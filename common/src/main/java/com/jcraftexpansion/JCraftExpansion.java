package com.jcraftexpansion;

import com.jcraftexpansion.common.barrage.BarrageHoldHandler;
import com.jcraftexpansion.common.register.EntityTypeRegistry;
import com.jcraftexpansion.common.register.StandTypeRegistry;
import com.jcraftexpansion.common.skill.SkillNetworking;
import com.jcraftexpansion.common.skill.SkillTicker;
import com.jcraftexpansion.common.standstate.StandFlags;
import com.jcraftexpansion.common.standstate.TandemManager;
import net.minecraft.resources.ResourceLocation;

public final class JCraftExpansion {
    public static final String MOD_ID = "jcraft_expansion";
    public static final String BASE_CONTROLLER = "base_controller";

    public static void init() {
        EntityTypeRegistry.REGISTRY.register();
        StandTypeRegistry.REGISTRY.register();

        EntityTypeRegistry.registerAttributes();

        BarrageHoldHandler.init();

        SkillNetworking.init();
        SkillTicker.init();

        StandFlags.init();
        TandemManager.init();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
