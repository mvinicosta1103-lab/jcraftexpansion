package com.jcraftexpansion.common.skill;

import com.jcraftexpansion.common.skill.gold.GoldExperienceSkills;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.entity.stand.GoldExperienceEntity;

import java.util.List;

/** Lista de skills extras por stand. Para adicionar um stand novo, adicione um "if" aqui. */
public final class SkillRegistry {
    public static final int MAX_SLOTS = 9;

    private SkillRegistry() {
    }

    public static List<StandSkill> getSkills(StandEntity<?, ?> stand) {
        if (stand instanceof GoldExperienceEntity) {
            return GoldExperienceSkills.SKILLS;
        }
        return List.of();
    }
}
