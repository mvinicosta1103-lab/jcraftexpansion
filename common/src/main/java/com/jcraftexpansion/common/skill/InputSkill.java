package com.jcraftexpansion.common.skill;

import net.minecraft.world.item.Item;

import java.util.function.BiPredicate;

/** Skill que pede um texto ao jogador (o cliente abre uma tela para digitar) antes de ser usada. */
public record InputSkill(String id, Item icon, int cooldownTicks, BiPredicate<SkillContext, String> action)
        implements StandSkill {
    @Override
    public boolean needsTextInput() {
        return true;
    }

    @Override
    public boolean cast(SkillContext ctx) {
        return false; // sem texto não há o que fazer
    }

    @Override
    public boolean cast(SkillContext ctx, String text) {
        return action.test(ctx, text);
    }
}
