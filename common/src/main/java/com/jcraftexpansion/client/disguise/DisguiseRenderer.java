package com.jcraftexpansion.client.disguise;

import com.jcraftexpansion.common.disguise.DisguiseManager;
import com.mojang.authlib.GameProfile;
import net.arna.jcraft.api.stand.StandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cuida das "cópias" desenhadas no lugar de um stand disfarçado. Cada stand disfarçado tem uma entidade
 * de mentira (jogador ou mob) que segue a posição, a rotação e os golpes do stand a cada tick do cliente.
 */
public final class DisguiseRenderer {
    private static final Map<StandEntity<?, ?>, Copy> COPIES = new WeakHashMap<>();

    private static final class Copy {
        final String spec;
        final Entity entity;
        boolean broken;
        int lastMoveStun;

        Copy(String spec, Entity entity, boolean broken) {
            this.spec = spec;
            this.entity = entity;
            this.broken = broken;
        }
    }

    private DisguiseRenderer() {
    }

    /** A entidade que deve ser desenhada no lugar do stand (ou null para desenhar o stand normalmente). */
    public static Entity getCopy(StandEntity<?, ?> stand) {
        String spec = DisguiseManager.getSpec(stand);
        if (spec == null) {
            COPIES.remove(stand);
            return null;
        }
        Copy copy = COPIES.get(stand);
        if (copy == null || !copy.spec.equals(spec)) {
            copy = create(spec);
            COPIES.put(stand, copy);
        }
        return copy.broken ? null : copy.entity;
    }

    private static Copy create(String spec) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return new Copy(spec, null, true);
        }
        try {
            if (spec.startsWith("player:")) {
                DisguisePlayer player = new DisguisePlayer(level, new GameProfile(null, spec.substring(7)));
                player.loadSkin();
                return new Copy(spec, player, false);
            }
            EntityType<?> type = DisguiseManager.typeOf(spec);
            Entity entity = type == null ? null : type.create(level);
            return new Copy(spec, entity, entity == null);
        } catch (Throwable t) {
            return new Copy(spec, null, true);
        }
    }

    /** Roda a cada tick do cliente: faz as cópias imitarem os stands. */
    public static void tick(Minecraft mc) {
        if (COPIES.isEmpty()) {
            return;
        }
        for (Map.Entry<StandEntity<?, ?>, Copy> entry : new ArrayList<>(COPIES.entrySet())) {
            StandEntity<?, ?> stand = entry.getKey();
            Copy copy = entry.getValue();
            if (stand == null || stand.isRemoved()) {
                COPIES.remove(stand);
                continue;
            }
            if (copy.broken || copy.entity == null) {
                continue;
            }
            try {
                follow(stand, copy);
            } catch (Throwable t) {
                copy.broken = true; // se algo der errado, volta a desenhar o stand normal
            }
        }
    }

    private static void follow(StandEntity<?, ?> stand, Copy copy) {
        Entity e = copy.entity;
        e.setOldPosAndRot();
        e.setPos(stand.getX(), stand.getY(), stand.getZ());
        e.setYRot(stand.getYRot());
        e.setXRot(stand.getXRot());
        e.setDeltaMovement(Vec3.ZERO);
        e.setOnGround(stand.onGround());
        e.tickCount = stand.tickCount;

        if (e instanceof LivingEntity le) {
            // Golpe do stand = o corpo copiado também balança o braço.
            int moveStun = stand.getMoveStun();
            if (moveStun > 0 && copy.lastMoveStun <= 0) {
                le.swing(InteractionHand.MAIN_HAND);
            }
            copy.lastMoveStun = moveStun;

            e.tick(); // anima as pernas, o balançar de braço, etc.

            // O tick acima recalcula o giro do corpo; aqui voltamos a seguir exatamente o stand.
            le.yBodyRotO = stand.yBodyRotO;
            le.yBodyRot = stand.yBodyRot;
            le.yHeadRotO = stand.yHeadRotO;
            le.yHeadRot = stand.yHeadRot;
        }
    }
}
