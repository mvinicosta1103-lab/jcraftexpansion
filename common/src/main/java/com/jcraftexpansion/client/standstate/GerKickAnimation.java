package com.jcraftexpansion.client.standstate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import mod.azure.azurelib.model.AzBakedModel;
import mod.azure.azurelib.model.AzBone;
import mod.azure.azurelib.model.AzBoneSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lê a animação "animation.ger.airbarrage" (a kick barrage aérea do Gold Experience Requiem) direto do arquivo
 * de animações do JCraft e aplica os mesmos keyframes nos ossos de QUALQUER stand humanoide
 * (torso, braços, pernas, cabeça...), repetindo o trecho central em loop enquanto a barragem durar.
 */
public final class GerKickAnimation {
    private static final ResourceLocation FILE = new ResourceLocation("jcraft", "animations/gold_experience_requiem.animation.json");
    private static final String ANIMATION = "animation.ger.airbarrage";

    /** Depois da introdução, o chute se repete a cada 0,12 s. Repetimos 0,48 s -> 1,44 s (8 ciclos). */
    private static final float LOOP_START = 0.48f;
    private static final float LOOP_LENGTH = 0.96f;

    private static final Map<String, BoneTracks> TRACKS = new HashMap<>();
    private static boolean loaded = false;
    private static boolean failed = false;

    private record Key(float time, float x, float y, float z, String easing) {
    }

    private record BoneTracks(List<Key> rotation, List<Key> position) {
    }

    private GerKickAnimation() {
    }

    /** @return true se a animação foi aplicada (false se o arquivo não pôde ser lido). */
    public static boolean apply(AzBakedModel model, float seconds) {
        ensureLoaded();
        if (failed || TRACKS.isEmpty()) {
            return false;
        }

        float t = seconds;
        if (t >= LOOP_START + LOOP_LENGTH) {
            t = LOOP_START + ((t - LOOP_START) % LOOP_LENGTH);
        }

        for (Map.Entry<String, BoneTracks> entry : TRACKS.entrySet()) {
            String name = entry.getKey();
            if (name.equals("BAM") || name.toLowerCase().startsWith("eye")) {
                continue;
            }
            AzBone bone = model.getBoneOrNull(name);
            if (bone == null) {
                continue;
            }
            BoneTracks tracks = entry.getValue();

            float[] rot = sample(tracks.rotation(), t);
            if (rot != null) {
                // Mesma convenção do AzureLib: -X, -Y, +Z em radianos, somado à pose inicial do osso.
                AzBoneSnapshot initial = bone.getInitialAzSnapshot();
                float ix = initial == null ? 0f : initial.getRotX();
                float iy = initial == null ? 0f : initial.getRotY();
                float iz = initial == null ? 0f : initial.getRotZ();
                bone.setRotX((float) Math.toRadians(-rot[0]) + ix);
                bone.setRotY((float) Math.toRadians(-rot[1]) + iy);
                bone.setRotZ((float) Math.toRadians(rot[2]) + iz);
            }
            float[] pos = sample(tracks.position(), t);
            if (pos != null) {
                bone.setPosX(pos[0]);
                bone.setPosY(pos[1]);
                bone.setPosZ(pos[2]);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ interpolação

    private static float[] sample(List<Key> keys, float t) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        Key first = keys.get(0);
        if (t <= first.time() || keys.size() == 1) {
            return new float[]{first.x(), first.y(), first.z()};
        }
        Key last = keys.get(keys.size() - 1);
        if (t >= last.time()) {
            return new float[]{last.x(), last.y(), last.z()};
        }
        for (int i = 0; i < keys.size() - 1; i++) {
            Key a = keys.get(i);
            Key b = keys.get(i + 1);
            if (t >= a.time() && t < b.time()) {
                float span = b.time() - a.time();
                float f = span <= 0f ? 1f : ease(b.easing(), (t - a.time()) / span);
                return new float[]{
                        a.x() + (b.x() - a.x()) * f,
                        a.y() + (b.y() - a.y()) * f,
                        a.z() + (b.z() - a.z()) * f};
            }
        }
        return new float[]{last.x(), last.y(), last.z()};
    }

    /** O easing de um keyframe vale para a transição que CHEGA nele (mesma regra do AzureLib). */
    private static float ease(String type, float x) {
        switch (type) {
            case "easeInSine":
                return 1f - (float) Math.cos(x * Math.PI / 2.0);
            case "easeOutSine":
                return (float) Math.sin(x * Math.PI / 2.0);
            case "easeInOutSine":
                return -((float) Math.cos(Math.PI * x) - 1f) / 2f;
            case "easeInQuad":
                return x * x;
            case "easeOutQuad":
                return 1f - (1f - x) * (1f - x);
            case "easeInOutQuad":
                return x < 0.5f ? 2f * x * x : 1f - (float) Math.pow(-2f * x + 2f, 2) / 2f;
            case "easeInCubic":
                return x * x * x;
            case "easeOutCubic":
                return 1f - (float) Math.pow(1f - x, 3);
            case "easeInOutCubic":
                return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
            case "easeOutElastic": {
                if (x <= 0f) {
                    return 0f;
                }
                if (x >= 1f) {
                    return 1f;
                }
                double c4 = (2.0 * Math.PI) / 3.0;
                return (float) (Math.pow(2.0, -10.0 * x) * Math.sin((x * 10.0 - 0.75) * c4) + 1.0);
            }
            default:
                return x; // linear
        }
    }

    // ------------------------------------------------------------------ leitura do arquivo

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FILE);
            if (resource.isEmpty()) {
                failed = true;
                return;
            }
            try (InputStream in = resource.get().open();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject bones = root.getAsJsonObject("animations").getAsJsonObject(ANIMATION).getAsJsonObject("bones");
                for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
                    JsonObject channels = bone.getValue().getAsJsonObject();
                    List<Key> rotation = parseChannel(channels.get("rotation"));
                    List<Key> position = parseChannel(channels.get("position"));
                    if (!rotation.isEmpty() || !position.isEmpty()) {
                        TRACKS.put(bone.getKey(), new BoneTracks(rotation, position));
                    }
                }
            }
        } catch (Exception e) {
            failed = true;
            TRACKS.clear();
        }
    }

    private static List<Key> parseChannel(JsonElement element) {
        List<Key> keys = new ArrayList<>();
        if (element == null) {
            return keys;
        }
        if (element.isJsonArray()) {
            keys.add(vector(0f, element.getAsJsonArray(), "linear"));
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("vector")) {
                keys.add(vector(0f, obj.getAsJsonArray("vector"), easingOf(obj)));
            } else {
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    float time;
                    try {
                        time = Float.parseFloat(e.getKey());
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    JsonElement v = e.getValue();
                    if (v.isJsonArray()) {
                        keys.add(vector(time, v.getAsJsonArray(), "linear"));
                    } else if (v.isJsonObject() && v.getAsJsonObject().has("vector")) {
                        keys.add(vector(time, v.getAsJsonObject().getAsJsonArray("vector"), easingOf(v.getAsJsonObject())));
                    }
                }
            }
        }
        keys.sort((a, b) -> Float.compare(a.time(), b.time()));
        return keys;
    }

    private static String easingOf(JsonObject obj) {
        return obj.has("easing") && obj.get("easing").isJsonPrimitive() ? obj.get("easing").getAsString() : "linear";
    }

    private static Key vector(float time, JsonArray array, String easing) {
        return new Key(time, number(array, 0), number(array, 1), number(array, 2), easing);
    }

    private static float number(JsonArray array, int index) {
        if (index >= array.size() || !array.get(index).isJsonPrimitive()) {
            return 0f;
        }
        JsonPrimitive p = array.get(index).getAsJsonPrimitive();
        if (p.isNumber()) {
            return p.getAsFloat();
        }
        try {
            return Float.parseFloat(p.getAsString()); // expressões Molang não numéricas viram 0
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
}
