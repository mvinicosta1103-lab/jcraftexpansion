package com.jcraftexpansion.common.standstate;

import com.jcraftexpansion.JCraftExpansion;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.arna.jcraft.api.attack.enums.MoveInputType;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.network.c2s.PlayerInputPacket;
import net.arna.jcraft.common.util.JUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estados por jogador ligados ao stand: modo "Stand Off" e "Barragem de Chutes".
 * <p>
 * O servidor é a autoridade; os clientes recebem uma cópia por pacote (SYNC_ID) só para renderização.
 * (Em mundo single-player os dois lados compartilham estes conjuntos, o que é inofensivo.)
 */
public final class StandFlags {
    /** Cliente -> Servidor: alternar o modo Stand Off. */
    public static final ResourceLocation TOGGLE_OFF_ID = JCraftExpansion.id("stand_off_toggle");
    /** Cliente -> Servidor: tecla de chutes pressionada (true) ou solta (false). */
    public static final ResourceLocation KICK_ID = JCraftExpansion.id("kick_barrage");
    /** Servidor -> Cliente: flags de um jogador (UUID, standOff, kicking). */
    public static final ResourceLocation SYNC_ID = JCraftExpansion.id("stand_flags");

    private static final Set<UUID> STAND_OFF = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> KICKING = ConcurrentHashMap.newKeySet();

    private StandFlags() {
    }

    // ---------------------------------------------------------------- consultas (cliente e servidor)

    public static boolean isStandOff(StandEntity<?, ?> stand) {
        LivingEntity user = stand.getUser();
        return user != null && STAND_OFF.contains(user.getUUID());
    }

    public static boolean isKicking(StandEntity<?, ?> stand) {
        LivingEntity user = stand.getUser();
        return user != null && KICKING.contains(user.getUUID());
    }

    /** Usado pelo cliente ao receber o pacote de sincronização. */
    public static void applyClient(UUID player, boolean off, boolean kick) {
        setIn(STAND_OFF, player, off);
        setIn(KICKING, player, kick);
    }

    private static void setIn(Set<UUID> set, UUID id, boolean value) {
        if (value) {
            set.add(id);
        } else {
            set.remove(id);
        }
    }

    // ---------------------------------------------------------------- servidor

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, TOGGLE_OFF_ID, (buf, context) -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                context.queue(() -> toggleStandOff(player));
            }
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, KICK_ID, (buf, context) -> {
            boolean held = buf.readBoolean();
            if (context.getPlayer() instanceof ServerPlayer player) {
                context.queue(() -> setKick(player, held));
            }
        });

        TickEvent.SERVER_POST.register(StandFlags::tick);

        // Quem entra no servidor recebe o estado de todo mundo.
        PlayerEvent.PLAYER_JOIN.register(joiner -> {
            MinecraftServer server = joiner.getServer();
            if (server == null) {
                return;
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (STAND_OFF.contains(p.getUUID()) || KICKING.contains(p.getUUID())) {
                    NetworkManager.sendToPlayer(joiner, SYNC_ID, syncBuf(p.getUUID()));
                }
            }
        });
        PlayerEvent.PLAYER_QUIT.register(player -> {
            STAND_OFF.remove(player.getUUID());
            KICKING.remove(player.getUUID());
            broadcast(player);
        });
    }

    private static void toggleStandOff(ServerPlayer player) {
        StandEntity<?, ?> stand = JUtils.getStand(player);
        if (stand == null || !stand.hasUser()) {
            return;
        }
        UUID id = player.getUUID();
        boolean nowOff = !STAND_OFF.contains(id);
        setIn(STAND_OFF, id, nowOff);
        if (nowOff) {
            releaseKick(player);
        }
        broadcast(player);
        player.displayClientMessage(Component.translatable(nowOff
                ? "standflags.jcraft_expansion.stand_off_on" : "standflags.jcraft_expansion.stand_off_off"), true);
    }

    private static void setKick(ServerPlayer player, boolean held) {
        UUID id = player.getUUID();
        if (held) {
            StandEntity<?, ?> stand = JUtils.getStand(player);
            // Em Stand Off só os braços aparecem, então não há chutes.
            if (stand == null || !stand.hasUser() || STAND_OFF.contains(id)) {
                return;
            }
            if (KICKING.add(id)) {
                broadcast(player);
            }
        } else if (KICKING.contains(id)) {
            releaseKick(player);
            broadcast(player);
        }
    }

    /** Solta a "tecla" de barragem virtual, como se o jogador tivesse soltado o B. */
    private static void releaseKick(ServerPlayer player) {
        if (KICKING.remove(player.getUUID())) {
            PlayerInputPacket.getInputStateManager(player).heldInputs.put(MoveInputType.BARRAGE, 0);
        }
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            boolean off = STAND_OFF.contains(id);
            boolean kick = KICKING.contains(id);
            if (!off && !kick) {
                continue;
            }

            StandEntity<?, ?> stand = JUtils.getStand(player);
            if (stand == null) {
                // Stand guardado: limpa os estados.
                STAND_OFF.remove(id);
                releaseKick(player);
                broadcast(player);
                continue;
            }

            if (kick) {
                // Mantém a barragem "pressionada" no servidor (o JCraft re-inicia golpes segurados a cada tick;
                // o BarrageHoldHandler mantém a barragem viva e a cancela quando a tecla é solta).
                PlayerInputPacket.getInputStateManager(player).heldInputs.put(MoveInputType.BARRAGE, 3);
            }
        }
    }

    private static void broadcast(ServerPlayer about) {
        MinecraftServer server = about.getServer();
        if (server == null) {
            return;
        }
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), SYNC_ID, syncBuf(about.getUUID()));
    }

    private static FriendlyByteBuf syncBuf(UUID player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(player);
        buf.writeBoolean(STAND_OFF.contains(player));
        buf.writeBoolean(KICKING.contains(player));
        return buf;
    }
}
