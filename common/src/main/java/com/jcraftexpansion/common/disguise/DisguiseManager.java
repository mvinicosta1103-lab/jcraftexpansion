package com.jcraftexpansion.common.disguise;

import com.jcraftexpansion.JCraftExpansion;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.entity.stand.WhiteSnakeEntity;
import net.arna.jcraft.common.util.JUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Disfarce do White Snake: o stand passa a ter a aparência (e o tamanho) de um jogador ou de um mob.
 * <p>
 * O disfarce é guardado como texto: "player:NomeDoJogador" ou "mob:minecraft:zombie".
 * O servidor é a autoridade; os clientes recebem uma cópia por pacote para desenhar o stand disfarçado.
 */
public final class DisguiseManager {
    /** Servidor -> Cliente: disfarce de um jogador (UUID, texto; texto vazio = sem disfarce). */
    public static final ResourceLocation SYNC_ID = JCraftExpansion.id("disguise_sync");

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Map<UUID, String> DISGUISES = new ConcurrentHashMap<>();

    private DisguiseManager() {
    }

    // ---------------------------------------------------------------- consultas (cliente e servidor)

    /** Disfarce do stand (ou null). Seguro de chamar durante a construção da entidade. */
    public static String getSpec(StandEntity<?, ?> stand) {
        if (DISGUISES.isEmpty()) {
            return null;
        }
        try {
            LivingEntity user = stand.getUser();
            return user == null ? null : DISGUISES.get(user.getUUID());
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isDisguised(StandEntity<?, ?> stand) {
        return getSpec(stand) != null;
    }

    public static EntityType<?> typeOf(String spec) {
        if (spec.startsWith("player:")) {
            return EntityType.PLAYER;
        }
        if (spec.startsWith("mob:")) {
            ResourceLocation id = ResourceLocation.tryParse(spec.substring(4));
            return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        }
        return null;
    }

    /** Tamanho do corpo da entidade copiada (usado pelo mixin que muda a hitbox do stand). */
    public static EntityDimensions dimensionsOf(StandEntity<?, ?> stand) {
        String spec = getSpec(stand);
        if (spec == null) {
            return null;
        }
        EntityType<?> type = typeOf(spec);
        return type == null ? null : type.getDimensions();
    }

    /** Usado pelo cliente ao receber o pacote de sincronização. */
    public static void applyClient(UUID user, String spec) {
        if (spec.isEmpty()) {
            DISGUISES.remove(user);
        } else {
            DISGUISES.put(user, spec);
        }
    }

    // ---------------------------------------------------------------- servidor

    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(joiner -> {
            for (Map.Entry<UUID, String> e : DISGUISES.entrySet()) {
                NetworkManager.sendToPlayer(joiner, SYNC_ID, buf(e.getKey(), e.getValue()));
            }
        });
        PlayerEvent.PLAYER_QUIT.register(player -> {
            if (DISGUISES.remove(player.getUUID()) != null) {
                broadcast(player.getServer(), player.getUUID(), "");
            }
        });
        // Uma vez por segundo: quem perdeu o White Snake perde o disfarce.
        TickEvent.SERVER_POST.register(server -> {
            if (DISGUISES.isEmpty() || server.getTickCount() % 20 != 0) {
                return;
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (DISGUISES.containsKey(p.getUUID()) && !(JUtils.getStand(p) instanceof WhiteSnakeEntity)) {
                    clear(p);
                }
            }
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> DISGUISES.clear());
    }

    /**
     * Aplica (ou remove) o disfarce. Texto vazio remove; repetir o mesmo disfarce também remove.
     *
     * @return true se a skill foi usada (aplica o cooldown).
     */
    public static boolean apply(ServerPlayer player, String input) {
        String spec = parse(input == null ? "" : input);
        if (spec == null) {
            player.displayClientMessage(Component.translatable("disguise.jcraft_expansion.invalid"), true);
            return false;
        }

        StandEntity<?, ?> stand = JUtils.getStand(player);
        if (stand == null) {
            return false;
        }
        if (spec.isEmpty() || spec.equals(DISGUISES.get(player.getUUID()))) {
            clear(player);
            player.displayClientMessage(Component.translatable("disguise.jcraft_expansion.off"), true);
            return true;
        }

        DISGUISES.put(player.getUUID(), spec);
        stand.refreshDimensions();
        broadcast(player.getServer(), player.getUUID(), spec);

        Component name = spec.startsWith("player:")
                ? Component.literal(spec.substring(7))
                : typeOf(spec).getDescription();
        player.displayClientMessage(Component.translatable("disguise.jcraft_expansion.on", name), true);
        return true;
    }

    /**
     * Chamado quando o White Snake lança qualquer golpe ou skill: o disfarce cai automaticamente.
     * Só faz algo no servidor e só se o dono do stand estiver disfarçado.
     */
    public static void breakOnUse(StandEntity<?, ?> stand) {
        if (DISGUISES.isEmpty() || stand == null || stand.level().isClientSide) {
            return;
        }
        LivingEntity user = stand.getUser();
        if (user instanceof ServerPlayer player) {
            breakOnUse(player);
        }
    }

    public static void breakOnUse(ServerPlayer player) {
        if (DISGUISES.containsKey(player.getUUID())) {
            clear(player);
            player.displayClientMessage(Component.translatable("disguise.jcraft_expansion.off"), true);
        }
    }

    private static void clear(ServerPlayer player) {
        DISGUISES.remove(player.getUUID());
        StandEntity<?, ?> stand = JUtils.getStand(player);
        if (stand != null) {
            stand.refreshDimensions();
        }
        broadcast(player.getServer(), player.getUUID(), "");
    }

    /**
     * Interpreta o que o jogador digitou.
     *
     * @return "" (remover), "mob:..." / "player:..." ou null se for inválido.
     */
    private static String parse(String input) {
        String raw = input.trim();
        if (raw.isEmpty()) {
            return "";
        }
        // Prefixo explícito: "player:Nome" (útil para jogadores com nome igual ao de um mob).
        if (raw.toLowerCase(Locale.ROOT).startsWith("player:")) {
            String name = raw.substring(7).trim();
            return PLAYER_NAME.matcher(name).matches() ? "player:" + name : null;
        }

        String key = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
        ResourceLocation id = ResourceLocation.tryParse(key.contains(":") ? key : "minecraft:" + key);
        if (id != null) {
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            if (type.isPresent() && type.get() != EntityType.PLAYER && type.get().canSummon()) {
                return "mob:" + id;
            }
        }
        return PLAYER_NAME.matcher(raw).matches() ? "player:" + raw : null;
    }

    private static void broadcast(MinecraftServer server, UUID user, String spec) {
        if (server == null) {
            return;
        }
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), SYNC_ID, buf(user, spec));
    }

    private static FriendlyByteBuf buf(UUID user, String spec) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUUID(user);
        buf.writeUtf(spec, 128);
        return buf;
    }
}
