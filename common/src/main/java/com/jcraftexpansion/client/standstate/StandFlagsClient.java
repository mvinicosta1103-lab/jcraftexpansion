package com.jcraftexpansion.client.standstate;

import com.jcraftexpansion.common.standstate.StandFlags;
import com.jcraftexpansion.common.standstate.TandemManager;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import io.netty.buffer.Unpooled;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.util.JUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** Teclas do modo Stand Off (X) e da Barragem de Chutes (J), e recebimento do estado dos outros jogadores. */
public final class StandFlagsClient {
    private static final String CATEGORY = "key.categories.jcraft_expansion";
    private static final KeyMapping STAND_OFF_KEY = new KeyMapping("key.jcraft_expansion.stand_off",
            InputConstants.Type.KEYSYM, 88 /* X */, CATEGORY);
    private static final KeyMapping KICK_KEY = new KeyMapping("key.jcraft_expansion.kick_barrage",
            InputConstants.Type.KEYSYM, 74 /* J */, CATEGORY);

    private static final KeyMapping TANDEM_KEY = new KeyMapping("key.jcraft_expansion.tandem",
            InputConstants.Type.KEYSYM, 89 /* Y */, CATEGORY);

    private static boolean kickSent = false;

    private StandFlagsClient() {
    }

    public static void init() {
        KeyMappingRegistry.register(STAND_OFF_KEY);
        KeyMappingRegistry.register(KICK_KEY);
        KeyMappingRegistry.register(TANDEM_KEY);
        ClientTickEvent.CLIENT_PRE.register(StandFlagsClient::tick);

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, StandFlags.SYNC_ID, (buf, context) -> {
            UUID player = buf.readUUID();
            boolean off = buf.readBoolean();
            boolean kick = buf.readBoolean();
            boolean tandem = buf.readBoolean();
            context.queue(() -> StandFlags.applyClient(player, off, kick, tandem));
        });
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) {
            kickSent = false;
            return;
        }
        StandEntity<?, ?> stand = JUtils.getStand(mc.player);

        while (STAND_OFF_KEY.consumeClick()) {
            if (stand != null && mc.screen == null) {
                NetworkManager.sendToServer(StandFlags.TOGGLE_OFF_ID, new FriendlyByteBuf(Unpooled.buffer()));
            }
        }

        while (TANDEM_KEY.consumeClick()) {
            if (stand != null && mc.screen == null) {
                NetworkManager.sendToServer(TandemManager.TOGGLE_ID, new FriendlyByteBuf(Unpooled.buffer()));
            }
        }

        // Só enviamos quando o estado da tecla muda (apertou / soltou).
        boolean down = stand != null && mc.screen == null && KICK_KEY.isDown();
        if (down != kickSent) {
            kickSent = down;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(down);
            NetworkManager.sendToServer(StandFlags.KICK_ID, buf);
        }
    }
}
