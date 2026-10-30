package com.jcraftexpansion.client;

import com.jcraftexpansion.client.skill.SkillClient;
import com.jcraftexpansion.client.standstate.StandFlagsClient;

public class JCraftExpansionClient {
    public static void init() {
        // Client-side initialization logic goes here
        SkillClient.init();
        StandFlagsClient.init();
    }
}
