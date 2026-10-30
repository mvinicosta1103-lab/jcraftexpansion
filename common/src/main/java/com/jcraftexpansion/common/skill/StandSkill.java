package com.jcraftexpansion.common.skill;

import net.minecraft.world.item.Item;

/** Uma skill extra de stand, acessada pela hotbar de skills (teclas 1-9). */
public interface StandSkill {
    /** Id curto da skill (usado nas traduções: skill.jcraft_expansion.<id>). */
    String id();

    /** Item usado como ícone na HUD. */
    Item icon();

    /** Cooldown em ticks (20 ticks = 1 segundo). */
    int cooldownTicks();

    /**
     * Executa a skill no servidor.
     *
     * @return true se a skill foi usada (aplica o cooldown); false se falhou (sem cooldown).
     */
    boolean cast(SkillContext ctx);

    default String translationKey() {
        return "skill.jcraft_expansion." + id();
    }
}
