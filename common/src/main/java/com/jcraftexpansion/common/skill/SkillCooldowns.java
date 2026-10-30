package com.jcraftexpansion.common.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cooldowns por jogador e por slot (em tempo de jogo do servidor). */
public final class SkillCooldowns {
    private static final Map<UUID, long[]> END_TICKS = new HashMap<>();

    private SkillCooldowns() {
    }

    public static long getEnd(UUID player, int slot) {
        long[] arr = END_TICKS.get(player);
        return arr == null ? 0L : arr[slot];
    }

    public static void set(UUID player, int slot, long endTick) {
        END_TICKS.computeIfAbsent(player, k -> new long[SkillRegistry.MAX_SLOTS])[slot] = endTick;
    }

    public static void clearAll() {
        END_TICKS.clear();
    }
}
