package com.jcraftexpansion.common.barrage;

import dev.architectury.event.events.common.TickEvent;
import net.arna.jcraft.api.attack.enums.MoveInputType;
import net.arna.jcraft.api.attack.moves.AbstractBarrageAttack;
import net.arna.jcraft.api.component.living.CommonCooldownsComponent;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.network.c2s.PlayerInputPacket;
import net.arna.jcraft.common.util.CooldownType;
import net.arna.jcraft.common.util.JUtils;
import net.arna.jcraft.platform.JComponentPlatformUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Faz a barragem de QUALQUER stand durar enquanto a tecla de barragem (B) estiver pressionada,
 * ignorando o cooldown da barragem. Ao soltar a tecla, a barragem é cancelada.
 * <p>
 * Funciona em conjunto com o StandEntityMixin (que faz o JCraft tratar a tecla de barragem como "segurável").
 */
public final class BarrageHoldHandler {
    /** Jogadores cuja barragem estamos mantendo ativa (para cancelar quando soltarem a tecla). */
    private static final Set<UUID> LOOPING = new HashSet<>();

    private BarrageHoldHandler() {
    }

    public static void init() {
        // POST: roda depois do tick de input do JCraft (que roda em SERVER_PRE).
        TickEvent.SERVER_POST.register(BarrageHoldHandler::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            StandEntity<?, ?> stand = JUtils.getStand(player);
            if (stand == null) {
                LOOPING.remove(player.getUUID());
                continue;
            }

            boolean held = PlayerInputPacket.getInputStateManager(player).heldInputs.containsKey(MoveInputType.BARRAGE);

            if (held) {
                // Barragem ignora o cooldown enquanto B estiver pressionado.
                CommonCooldownsComponent cooldowns = JComponentPlatformUtils.getCooldowns(player);
                if (cooldowns.getCooldown(CooldownType.STAND_BARRAGE) > 0) {
                    cooldowns.clear(CooldownType.STAND_BARRAGE);
                }

                if (stand.getCurrentMove() instanceof AbstractBarrageAttack<?, ?> barrage) {
                    int moveStun = stand.getMoveStun();
                    int interval = Math.max(1, barrage.getInterval());
                    int span = barrage.getDuration() - barrage.getWindup();

                    // Perto do fim: "rebobina" o moveStun por um múltiplo exato do intervalo entre golpes,
                    // mantendo o ritmo dos golpes e impedindo a barragem de terminar.
                    if (moveStun > 0 && moveStun <= interval && span > interval) {
                        int loopLength = interval * Math.max(1, (span - interval) / interval);
                        stand.setMoveStun(moveStun + loopLength);
                        LOOPING.add(player.getUUID());
                    }
                }
            } else if (LOOPING.remove(player.getUUID())) {
                // Soltou a tecla: encerra a barragem que estávamos mantendo.
                if (stand.getCurrentMove() instanceof AbstractBarrageAttack<?, ?>) {
                    stand.cancelMove();
                }
            }
        }
    }
}
