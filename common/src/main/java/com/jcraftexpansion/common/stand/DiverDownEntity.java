package com.jcraftexpansion.common.stand;

import com.jcraftexpansion.JCraftExpansion;
import com.jcraftexpansion.common.register.StandTypeRegistry;
import mod.azure.azurelib.animation.dispatch.command.AzCommand;
import mod.azure.azurelib.animation.play_behavior.AzPlayBehaviors;
import net.arna.jcraft.JCraft;
import net.arna.jcraft.api.attack.MoveMap;
import net.arna.jcraft.api.attack.MoveSet;
import net.arna.jcraft.api.attack.MoveSetManager;
import net.arna.jcraft.api.attack.enums.MoveClass;
import net.arna.jcraft.api.registry.JSoundRegistry;
import net.arna.jcraft.api.stand.StandData;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.api.stand.StandInfo;
import net.arna.jcraft.common.attack.moves.shared.MainBarrageAttack;
import net.arna.jcraft.common.attack.moves.shared.SimpleAttack;
import net.arna.jcraft.common.util.StandAnimationState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public class DiverDownEntity extends StandEntity<DiverDownEntity, DiverDownEntity.State> {
    public static final MoveSet<DiverDownEntity, State> MOVE_SET = MoveSetManager.create(StandTypeRegistry.DIVER_DOWN,
            DiverDownEntity::registerMoves, State.class);
    public static final StandData DATA = StandData.of(StandInfo.of(Component.literal("Diver Down")));

    public static final SimpleAttack<DiverDownEntity> LIGHT = new SimpleAttack<DiverDownEntity>(JCraft.LIGHT_COOLDOWN,
            5, 8, 0.75f, 5f, 16, 1.5f, 0.2f, -0.1f)
            .withImpactSound(JSoundRegistry.IMPACT_1)
            .withInfo(
                    Component.literal("Punch"),
                    Component.literal("A basic punch attack")
            );
    public static final MainBarrageAttack<DiverDownEntity> BARRAGE = new MainBarrageAttack<DiverDownEntity>(280,
            0, 40, 0.75f, 1f, 30, 2f, 0.25f, 0f, 3, Blocks.OBSIDIAN.defaultDestroyTime())
            .withSound(JSoundRegistry.STAR_PLATINUM_BARRAGE)
            .withInfo(
                    Component.translatable("jcraft.generic.barrage"),
                    Component.literal("A basic barrage attack")
            );

    public DiverDownEntity(Level world) {
        super(StandTypeRegistry.DIVER_DOWN.get(), world);
    }

    private static void registerMoves(MoveMap<DiverDownEntity, State> moveMap) {
        moveMap.register(MoveClass.LIGHT, LIGHT, State.LIGHT);
        moveMap.register(MoveClass.HEAVY, LIGHT, State.LIGHT);
        moveMap.register(MoveClass.BARRAGE, BARRAGE, State.BARRAGE);
    }

    @NotNull
    @Override
    public DiverDownEntity getThis() {
        return this;
    }

    @Override
    protected State[] getStateValues() {
        return State.values();
    }

    @Override
    public State getBlockState() {
        return State.BLOCK;
    }

    public enum State implements StandAnimationState<DiverDownEntity> {
        IDLE(AzCommand.create(JCraftExpansion.BASE_CONTROLLER, "idle", AzPlayBehaviors.LOOP)),
        LIGHT(AzCommand.create(JCraftExpansion.BASE_CONTROLLER, "light", AzPlayBehaviors.HOLD_ON_LAST_FRAME)),
        BLOCK(AzCommand.create(JCraftExpansion.BASE_CONTROLLER, "block", AzPlayBehaviors.LOOP)),
        BARRAGE(AzCommand.create(JCraftExpansion.BASE_CONTROLLER, "barrage", AzPlayBehaviors.LOOP));

        private final AzCommand animator;

        State(AzCommand animator) {
            this.animator = animator;
        }

        @Override
        public void playAnimation(DiverDownEntity attacker) {
            animator.sendForEntity(attacker);
        }
    }
}
