package com.jcraftexpansion.common.skill;

import net.minecraft.world.item.Item;

import java.util.function.Predicate;

/** Implementação simples de {@link StandSkill} a partir de uma função. */
public record SimpleSkill(String id, Item icon, int cooldownTicks, Predicate<SkillContext> action) implements StandSkill {
    @Override
    public boolean cast(SkillContext ctx) {
        return action.test(ctx);
    }
}
