package com.jcraftexpansion.common.skill;

import net.arna.jcraft.api.stand.StandEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record SkillContext(ServerPlayer player, ServerLevel level, StandEntity<?, ?> stand) {
}
