package com.jcraftexpansion.client.standstate;

import com.jcraftexpansion.common.standstate.StandFlags;
import mod.azure.azurelib.model.AzBakedModel;
import mod.azure.azurelib.model.AzBone;
import net.arna.jcraft.api.stand.StandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Ajusta os ossos do modelo do stand depois que o JCraft termina de animá-lo:
 * - Stand Off: esconde tudo menos os braços.
 * - Barragem de Chutes: esconde os braços da barragem e balança as pernas.
 */
public final class BonePoser {
    /** Ossos que escondemos em cada stand, para restaurar quando o estado acabar. */
    private static final Map<StandEntity<?, ?>, Set<String>> HIDDEN = new WeakHashMap<>();

    private BonePoser() {
    }

    public static void apply(AzBakedModel model, StandEntity<?, ?> stand) {
        boolean off = StandFlags.isStandOff(stand);
        boolean kick = StandFlags.isKicking(stand);
        Set<String> previous = HIDDEN.get(stand);
        if (!off && !kick && previous == null) {
            return; // caminho comum: nada a fazer
        }

        Set<String> wanted = new HashSet<>();
        for (String name : model.getBonesByName().keySet()) {
            if (off && !isArmBone(name) && !name.equals("base") && !name.equals("Stand")) {
                wanted.add(name);
            } else if (kick && (name.startsWith("barrage") || name.equals("BAM"))) {
                wanted.add(name);
            }
        }

        // Restaura o que não precisa mais ficar escondido.
        if (previous != null) {
            for (String name : previous) {
                if (!wanted.contains(name)) {
                    AzBone bone = model.getBoneOrNull(name);
                    if (bone != null) {
                        bone.setHidden(false);
                    }
                }
            }
        }

        // Esconde o osso mas mantém os filhos visíveis (os braços ficam dentro do tronco no modelo).
        for (String name : wanted) {
            AzBone bone = model.getBoneOrNull(name);
            if (bone != null) {
                bone.setHidden(true);
                bone.setChildrenHidden(false);
            }
        }

        if (wanted.isEmpty()) {
            HIDDEN.remove(stand);
        } else {
            HIDDEN.put(stand, wanted);
        }

        if (kick) {
            poseKick(model, stand);
        }
    }

    private static boolean isArmBone(String name) {
        String n = name.toLowerCase();
        return n.contains("arm") || n.contains("hand") || n.contains("shoulder") || n.startsWith("pad");
    }

    private static void poseKick(AzBakedModel model, StandEntity<?, ?> stand) {
        // Braços relaxados (pose padrão do modelo).
        for (String name : new String[]{"armLeft", "armRight", "lower_armLeft", "lower_armRight",
                "leftArm", "rightArm"}) {
            AzBone arm = model.getBoneOrNull(name);
            if (arm != null) {
                arm.setRotX(0f);
                arm.setRotY(0f);
                arm.setRotZ(0f);
            }
        }

        float t = stand.tickCount + Minecraft.getInstance().getFrameTime();
        float swing = Mth.sin(t * 1.7f);
        kickLeg(model, "legRight", "lower_legRight", swing);
        kickLeg(model, "legLeft", "lower_legLeft", -swing);
        kickLeg(model, "rightLeg", "lowerRight", swing);
        kickLeg(model, "leftLeg", "lowerLeft", -swing);
    }

    private static void kickLeg(AzBakedModel model, String upper, String lower, float swing) {
        AzBone leg = model.getBoneOrNull(upper);
        if (leg != null) {
            leg.setRotX(leg.getRotX() + swing * 1.1f);
        }
        AzBone shin = model.getBoneOrNull(lower);
        if (shin != null) {
            // O joelho dobra quando a perna volta e estica no chute.
            shin.setRotX(shin.getRotX() + Math.max(0f, -swing) * 1.3f);
        }
    }
}
