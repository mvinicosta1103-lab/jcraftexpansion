package com.jcraftexpansion.client.skill;

import com.jcraftexpansion.common.skill.SkillNetworking;
import com.jcraftexpansion.common.skill.SkillRegistry;
import com.jcraftexpansion.common.skill.StandSkill;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import io.netty.buffer.Unpooled;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.util.JUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Lado cliente da hotbar de skills extras: tecla para ligar/desligar (Z por padrão),
 * teclas 1-9 para usar as skills e a HUD com ícones e cooldowns.
 */
public final class SkillClient {
    private static final KeyMapping TOGGLE_KEY = new KeyMapping("key.jcraft_expansion.skill_toggle",
            InputConstants.Type.KEYSYM, 90 /* Z */, "key.categories.jcraft_expansion");

    private static final long[] COOLDOWN_END = new long[SkillRegistry.MAX_SLOTS];
    private static final int[] COOLDOWN_TOTAL = new int[SkillRegistry.MAX_SLOTS];
    private static boolean active = false;

    private SkillClient() {
    }

    public static void init() {
        KeyMappingRegistry.register(TOGGLE_KEY);
        ClientTickEvent.CLIENT_PRE.register(SkillClient::tick);
        ClientGuiEvent.RENDER_HUD.register(SkillClient::renderHud);
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, SkillNetworking.COOLDOWN_ID, (buf, context) -> {
            int slot = buf.readByte();
            int ticks = buf.readInt();
            context.queue(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null && slot >= 0 && slot < SkillRegistry.MAX_SLOTS) {
                    COOLDOWN_END[slot] = mc.level.getGameTime() + ticks;
                    COOLDOWN_TOTAL[slot] = ticks;
                }
            });
        });
    }

    private static List<StandSkill> currentSkills(Minecraft mc) {
        if (mc.player == null) {
            return List.of();
        }
        StandEntity<?, ?> stand = JUtils.getStand(mc.player);
        return stand == null ? List.of() : SkillRegistry.getSkills(stand);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            active = false;
            return;
        }

        List<StandSkill> skills = currentSkills(mc);
        while (TOGGLE_KEY.consumeClick()) {
            if (!skills.isEmpty() && mc.screen == null) {
                active = !active;
            }
        }
        if (skills.isEmpty()) {
            active = false;
            return;
        }

        // Com o modo ligado, as teclas 1-9 viram skills em vez de trocar o item da hotbar.
        // Consumimos os cliques aqui (antes do Minecraft tratar as teclas) para a hotbar não mudar.
        if (active && mc.screen == null) {
            for (int i = 0; i < SkillRegistry.MAX_SLOTS; i++) {
                KeyMapping key = mc.options.keyHotbarSlots[i];
                while (key.consumeClick()) {
                    if (i < skills.size()) {
                        sendCast(i);
                    }
                }
            }
        }
    }

    private static void sendCast(int slot) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(slot);
        NetworkManager.sendToServer(SkillNetworking.CAST_ID, buf);
    }

    private static void renderHud(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        List<StandSkill> skills = currentSkills(mc);
        if (skills.isEmpty()) {
            return;
        }

        int size = 20;
        int gap = 2;
        int count = Math.min(skills.size(), SkillRegistry.MAX_SLOTS);
        int totalWidth = count * (size + gap) - gap;
        int x0 = (g.guiWidth() - totalWidth) / 2;
        int y = g.guiHeight() - 72;
        long now = mc.level.getGameTime();

        for (int i = 0; i < count; i++) {
            int x = x0 + i * (size + gap);
            g.fill(x, y, x + size, y + size, active ? 0xAA101010 : 0x55101010);
            g.renderOutline(x, y, size, size, active ? 0xFFE8C040 : 0x66FFFFFF);
            g.renderItem(new ItemStack(skills.get(i).icon()), x + 2, y + 2);

            long remaining = COOLDOWN_END[i] - now;
            if (remaining > 0 && COOLDOWN_TOTAL[i] > 0) {
                int h = (int) Math.min(size, Math.ceil(size * (remaining / (double) COOLDOWN_TOTAL[i])));
                g.fill(x, y, x + size, y + h, 0xAA000000);
                String secs = String.valueOf((int) Math.ceil(remaining / 20.0));
                g.drawString(mc.font, secs, x + (size - mc.font.width(secs)) / 2, y + 6, 0xFFFFFF, true);
            }
            g.drawString(mc.font, String.valueOf(i + 1), x + 2, y + 11, active ? 0xFFE8C040 : 0xAAAAAA, true);
        }

        if (active) {
            g.drawCenteredString(mc.font, Component.translatable("hud.jcraft_expansion.skills_active"),
                    g.guiWidth() / 2, y - 12, 0xFFE8C040);

            // Legenda com o nome de cada skill.
            int lx = 6;
            int ly = g.guiHeight() / 2 - count * 5;
            for (int i = 0; i < count; i++) {
                Component name = Component.literal((i + 1) + "  ").append(Component.translatable(skills.get(i).translationKey()));
                g.drawString(mc.font, name, lx, ly + i * 11, COOLDOWN_END[i] > now ? 0x888888 : 0xFFFFFF, true);
            }
        } else {
            g.drawCenteredString(mc.font,
                    Component.translatable("hud.jcraft_expansion.skills_hint", TOGGLE_KEY.getTranslatedKeyMessage()),
                    g.guiWidth() / 2, y - 12, 0xAAAAAA);
        }
    }
}
