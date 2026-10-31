package com.jcraftexpansion.common.skill;

import com.jcraftexpansion.JCraftExpansion;
import com.jcraftexpansion.common.disguise.DisguiseManager;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.util.JUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class SkillNetworking {
    /** Cliente -> Servidor: usar a skill do slot (byte). */
    public static final ResourceLocation CAST_ID = JCraftExpansion.id("skill_cast");
    /** Servidor -> Cliente: cooldown de um slot (byte slot, int ticks). */
    public static final ResourceLocation COOLDOWN_ID = JCraftExpansion.id("skill_cooldown");

    private SkillNetworking() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, CAST_ID, SkillNetworking::onCast);
    }

    private static void onCast(FriendlyByteBuf buf, NetworkManager.PacketContext context) {
        int slot = buf.readByte();
        String text = buf.readBoolean() ? buf.readUtf(64) : null;
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        context.queue(() -> handleCast(player, slot, text));
    }

    private static void handleCast(ServerPlayer player, int slot, String text) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return;
        }
        StandEntity<?, ?> stand = JUtils.getStand(player);
        if (stand == null || !stand.hasUser() || !stand.canAttack()) {
            return;
        }
        List<StandSkill> skills = SkillRegistry.getSkills(stand);
        if (slot < 0 || slot >= skills.size() || slot >= SkillRegistry.MAX_SLOTS) {
            return;
        }

        long now = level.getGameTime();
        long end = SkillCooldowns.getEnd(player.getUUID(), slot);
        if (end > now) {
            int seconds = (int) Math.ceil((end - now) / 20.0);
            player.displayClientMessage(Component.translatable("skill.jcraft_expansion.msg.cooldown", seconds), true);
            return;
        }

        StandSkill skill = skills.get(slot);
        if (skill.needsTextInput() && text == null) {
            return;
        }
        SkillContext ctx = new SkillContext(player, level, stand);
        boolean used = skill.needsTextInput() ? skill.cast(ctx, text) : skill.cast(ctx);
        if (used && !"disguise".equals(skill.id())) {
            // Lançar qualquer outra skill derruba o disfarce do White Snake.
            DisguiseManager.breakOnUse(player);
        }
        if (used) {
            SkillCooldowns.set(player.getUUID(), slot, now + skill.cooldownTicks());
            sendCooldown(player, slot, skill.cooldownTicks());
        }
    }

    public static void sendCooldown(ServerPlayer player, int slot, int ticks) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(slot);
        buf.writeInt(ticks);
        NetworkManager.sendToPlayer(player, COOLDOWN_ID, buf);
    }
}
