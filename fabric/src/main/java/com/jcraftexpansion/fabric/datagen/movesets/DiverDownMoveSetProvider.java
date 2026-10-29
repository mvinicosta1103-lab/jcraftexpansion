package com.jcraftexpansion.fabric.datagen.movesets;

import com.jcraftexpansion.common.register.StandTypeRegistry;
import com.jcraftexpansion.common.stand.DiverDownEntity;
import net.arna.jcraft.api.attack.MoveMap;
import net.arna.jcraft.api.attack.MoveSet;
import net.arna.jcraft.api.datagen.JCraftMoveSetProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;

import java.util.function.Consumer;

public class DiverDownMoveSetProvider extends JCraftMoveSetProvider<DiverDownEntity, DiverDownEntity.State> {
    public DiverDownMoveSetProvider(FabricDataOutput dataOutput) {
        super(dataOutput, MoveMap.Entry.codecFor(DiverDownEntity.State.class), StandTypeRegistry.DIVER_DOWN.getId());
    }

    @Override
    protected void configureMoveSets(Consumer<MoveSet<DiverDownEntity, DiverDownEntity.State>> provider) {
        provider.accept(DiverDownEntity.MOVE_SET);
    }
}
