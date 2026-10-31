package com.jcraftexpansion.client.disguise;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.util.Map;

/**
 * Jogador "de mentira" usado só para desenhar o disfarce de jogador: busca a skin de qualquer nome
 * (mesmo de quem não está no servidor) e usa o modelo certo (braço fino/normal).
 */
public class DisguisePlayer extends RemotePlayer {
    private ResourceLocation skin;
    private String model = "default";

    public DisguisePlayer(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    /** Baixa a skin em segundo plano; enquanto isso aparece a skin padrão. */
    public void loadSkin() {
        SkullBlockEntity.updateGameprofile(this.getGameProfile(),
                filled -> Minecraft.getInstance().execute(() -> applyProfile(filled)));
    }

    private void applyProfile(GameProfile filled) {
        if (filled == null) {
            return;
        }
        SkinManager skins = Minecraft.getInstance().getSkinManager();
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = skins.getInsecureSkinInformation(filled);
        MinecraftProfileTexture tex = textures.get(MinecraftProfileTexture.Type.SKIN);
        if (tex != null) {
            this.skin = skins.registerTexture(tex, MinecraftProfileTexture.Type.SKIN);
            this.model = "slim".equals(tex.getMetadata("model")) ? "slim" : "default";
        } else {
            this.skin = DefaultPlayerSkin.getDefaultSkin(UUIDUtil.getOrCreatePlayerUUID(filled));
        }
    }

    @Override
    public ResourceLocation getSkinTextureLocation() {
        return skin != null ? skin : super.getSkinTextureLocation();
    }

    @Override
    public String getModelName() {
        return model;
    }
}
