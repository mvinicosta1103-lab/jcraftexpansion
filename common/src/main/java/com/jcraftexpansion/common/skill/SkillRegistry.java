package com.jcraftexpansion.common.skill;

import com.jcraftexpansion.common.skill.gold.GoldExperienceSkills;
import com.jcraftexpansion.common.skill.kingcrimson.KingCrimsonSkills;
import com.jcraftexpansion.common.skill.whitesnake.WhiteSnakeSkills;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.entity.stand.GoldExperienceEntity;
import net.arna.jcraft.common.entity.stand.KingCrimsonEntity;
import net.arna.jcraft.common.entity.stand.WhiteSnakeEntity;

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
        if (stand instanceof KingCrimsonEntity) {
            return KingCrimsonSkills.SKILLS;
        }
        if (stand instanceof WhiteSnakeEntity) {
            return WhiteSnakeSkills.SKILLS;
        }
        return List.of();
    }
}
