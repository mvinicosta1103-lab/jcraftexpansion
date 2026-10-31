package com.jcraftexpansion.client.disguise;

import com.jcraftexpansion.common.disguise.DisguiseManager;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.util.JUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class DisguiseClient {
    private DisguiseClient() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DisguiseManager.SYNC_ID, (buf, context) -> {
            UUID user = buf.readUUID();
            String spec = buf.readUtf(128);
            context.queue(() -> {
                DisguiseManager.applyClient(user, spec);
                // O tamanho do stand muda junto com o disfarce.
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    Player player = mc.level.getPlayerByUUID(user);
                    StandEntity<?, ?> stand = player == null ? null : JUtils.getStand(player);
                    if (stand != null) {
                        stand.refreshDimensions();
                    }
                }
            });
        });
        ClientTickEvent.CLIENT_POST.register(DisguiseRenderer::tick);
    }
}
