package com.jcraftexpansion.common.skill.gold;

import com.jcraftexpansion.common.skill.SimpleSkill;
import com.jcraftexpansion.common.skill.SkillContext;
import com.jcraftexpansion.common.skill.SkillTicker;
import com.jcraftexpansion.common.skill.StandSkill;
import net.arna.jcraft.JCraft;
import net.arna.jcraft.api.registry.JEntityTypeRegistry;
import net.arna.jcraft.api.stand.StandEntity;
import net.arna.jcraft.common.entity.GESnakeEntity;
import net.arna.jcraft.common.entity.stand.GoldExperienceEntity;
import net.arna.jcraft.common.util.JUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * As 9 skills extras do Gold Experience (hotbar de skills, teclas 1-9).
 * <p>
 * As skills reaproveitam as animações que o Gold Experience já tem (Life Giver, cura, árvore, etc.).
 */
public final class GoldExperienceSkills {
    private static final Block[] FLOWERS = {
            Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.ALLIUM
    };

    public static final List<StandSkill> SKILLS = List.of(
            new SimpleSkill("life_swarm", Items.FEATHER, 600, GoldExperienceSkills::lifeSwarm),          // 1
            new SimpleSkill("life_transplant", Items.GOLDEN_APPLE, 400, GoldExperienceSkills::lifeTransplant), // 2
            new SimpleSkill("living_sapling", Items.OAK_SAPLING, 900, GoldExperienceSkills::livingSapling),  // 3
            new SimpleSkill("block_reanimate", Items.BONE_MEAL, 300, GoldExperienceSkills::blockReanimate),  // 4
            new SimpleSkill("living_bark", Items.VINE, 600, GoldExperienceSkills::livingBark),               // 5
            new SimpleSkill("vital_pulse", Items.GLOW_BERRIES, 500, GoldExperienceSkills::vitalPulse),       // 6
            new SimpleSkill("pain_overload", Items.SPIDER_EYE, 500, GoldExperienceSkills::painOverload),     // 7
            new SimpleSkill("guided_viper", Items.LEAD, 400, GoldExperienceSkills::guidedViper),             // 8
            new SimpleSkill("healing_flower", Items.POPPY, 900, GoldExperienceSkills::healingFlower)         // 9
    );

    private GoldExperienceSkills() {
    }

    // ------------------------------------------------------------------------------------------
    // 1 - Enxame de Vida: transforma até 3 itens em cobras domadas que atacam por alguns segundos.
    // ------------------------------------------------------------------------------------------
    private static boolean lifeSwarm(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        ItemStack stack = heldStack(player);
        if (stack.isEmpty()) {
            return fail(player, "need_item");
        }
        if (stack.getMaxStackSize() <= 1) {
            return fail(player, "need_stackable");
        }

        int count = Math.min(3, stack.getCount());
        ItemStack single = stack.copy();
        single.setCount(1);
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            spawnSnake(level, player, single.copy(), Math.cos(angle) * 1.2, Math.sin(angle) * 1.2);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(count);
        }

        anim(ctx, GoldExperienceEntity.State.LIFE_GIVER, 20);
        burst(level, player, ParticleTypes.HAPPY_VILLAGER, 12);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 2 - Transplante de Vida: cura forte (alvo olhado ou você) e remove efeitos negativos.
    // ------------------------------------------------------------------------------------------
    private static boolean lifeTransplant(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        LivingEntity target = lookEntity(player, 5.0);
        boolean self = target == null;
        if (self) {
            target = player;
        }

        target.heal(10.0f);
        target.clearFire();
        for (MobEffectInstance effect : new ArrayList<>(target.getActiveEffects())) {
            if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                target.removeEffect(effect.getEffect());
            }
        }
        target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));

        anim(ctx, self ? GoldExperienceEntity.State.HEAL_SELF : GoldExperienceEntity.State.HEAL, 20);
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY(1.0), target.getZ(), 8, 0.4, 0.5, 0.4, 0.0);
        sound(level, target);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 3 - Muda Viva: faz uma árvore crescer na hora onde você olha e solta maçãs curativas.
    // ------------------------------------------------------------------------------------------
    private static boolean livingSapling(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        BlockHitResult hit = lookBlock(player, 8.0);
        if (hit == null) {
            return fail(player, "no_target");
        }

        BlockPos place = hit.getBlockPos().relative(hit.getDirection());
        BlockState sapling = Blocks.OAK_SAPLING.defaultBlockState();
        if (!level.getBlockState(place).isAir() || !sapling.canSurvive(level, place)) {
            return fail(player, "no_soil");
        }
        if (!canAlter(level, player, place)) {
            return fail(player, "griefing_off");
        }

        BlockState stage1 = sapling.setValue(SaplingBlock.STAGE, 1);
        level.setBlock(place, stage1, 3);
        ((SaplingBlock) Blocks.OAK_SAPLING).advanceTree(level, place, stage1, level.getRandom());

        if (level.getBlockState(place).is(BlockTags.SAPLINGS)) {
            // Sem espaço para a árvore: desfaz a muda.
            level.setBlock(place, Blocks.AIR.defaultBlockState(), 3);
            return fail(player, "no_space");
        }

        Block.popResource(level, place, new ItemStack(Items.APPLE, 3));
        anim(ctx, GoldExperienceEntity.State.TREE, 25);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, place.getX() + 0.5, place.getY() + 1.0, place.getZ() + 0.5,
                20, 1.0, 1.5, 1.0, 0.0);
        level.playSound(null, place, SoundEvents.MOSS_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 4 - Reanimar Bloco: blocos naturais perto de onde você olha viram musgo com flores.
    // ------------------------------------------------------------------------------------------
    private static boolean blockReanimate(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        BlockHitResult hit = lookBlock(player, 8.0);
        if (hit == null) {
            return fail(player, "no_target");
        }
        if (!level.getGameRules().getBoolean(JCraft.STAND_GRIEFING)) {
            return fail(player, "griefing_off");
        }

        BlockPos center = hit.getBlockPos();
        int changed = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx * dx + dy * dy + dz * dz > 3) {
                        continue;
                    }
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!isReanimatable(level, pos, state) || !JUtils.mayAlter(level, player, pos, null)) {
                        continue;
                    }

                    level.setBlock(pos, Blocks.MOSS_BLOCK.defaultBlockState(), 3);
                    changed++;

                    BlockPos above = pos.above();
                    if (level.getBlockState(above).isAir() && level.random.nextFloat() < 0.6f) {
                        BlockState flower = FLOWERS[level.random.nextInt(FLOWERS.length)].defaultBlockState();
                        if (flower.canSurvive(level, above)) {
                            level.setBlock(above, flower, 3);
                        }
                    }
                }
            }
        }

        if (changed == 0) {
            return fail(player, "nothing_changed");
        }
        anim(ctx, GoldExperienceEntity.State.LIFE_GIVER, 20);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.getX() + 0.5, center.getY() + 0.8, center.getZ() + 0.5,
                25, 0.8, 0.8, 0.8, 0.0);
        level.playSound(null, center, SoundEvents.MOSS_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    private static boolean isReanimatable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0f || hardness > 3.0f) {
            return false;
        }
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE);
    }

    // ------------------------------------------------------------------------------------------
    // 5 - Casca Viva: vinhas protegem você (resistência + absorção).
    // ------------------------------------------------------------------------------------------
    private static boolean livingBark(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 1));

        anim(ctx, GoldExperienceEntity.State.HEAL_SELF, 20);
        burst(level, player, ParticleTypes.COMPOSTER, 25);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 6 - Pulso Vital: cura aliados em volta e empurra os inimigos.
    // ------------------------------------------------------------------------------------------
    private static boolean vitalPulse(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        double radius = 6.0;
        AABB box = player.getBoundingBox().inflate(radius);

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e instanceof StandEntity<?, ?>) {
                continue;
            }
            if (e.distanceToSqr(player) > radius * radius) {
                continue;
            }
            if (e == player || SkillTicker.isAlly(e, player)) {
                e.heal(6.0f);
                level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY(1.0), e.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
            } else {
                Vec3 dir = e.position().subtract(player.position());
                dir = new Vec3(dir.x, 0, dir.z).normalize();
                e.push(dir.x * 1.2, 0.4, dir.z * 1.2);
                e.hurtMarked = true;
            }
        }

        anim(ctx, GoldExperienceEntity.State.HEAL, 20);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24.0;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX() + Math.cos(a) * 3.0, player.getY() + 0.5,
                    player.getZ() + Math.sin(a) * 3.0, 1, 0.0, 0.1, 0.0, 0.0);
        }
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 7 - Sobrecarga de Dor: golpe que marca o alvo; metade do dano recebido estoura depois.
    // ------------------------------------------------------------------------------------------
    private static boolean painOverload(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        LivingEntity target = lookEntity(player, 5.0);
        if (target == null) {
            return fail(player, "no_target");
        }

        target.hurt(level.damageSources().playerAttack(player), 3.0f);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
        SkillTicker.markPain(target, player, level.getGameTime() + 80);

        anim(ctx, GoldExperienceEntity.State.HEAL, 15);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.4, 0.3, 0.1);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 8 - Víbora Teleguiada: uma cobra rápida que persegue o alvo.
    // ------------------------------------------------------------------------------------------
    private static boolean guidedViper(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        ItemStack stack = heldStack(player);
        if (stack.isEmpty()) {
            return fail(player, "need_item");
        }
        if (stack.getMaxStackSize() <= 1) {
            return fail(player, "need_stackable");
        }

        LivingEntity target = lookEntity(player, 24.0);
        if (target == null || SkillTicker.isAlly(target, player)) {
            target = nearestEnemy(level, player, 16.0);
        }
        if (target == null) {
            return fail(player, "no_target");
        }

        ItemStack single = stack.copy();
        single.setCount(1);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        GESnakeEntity viper = spawnSnake(level, player, single, 0.0, 0.0);
        viper.setTarget(target);
        viper.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 500, 2));
        viper.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 500, 1));

        anim(ctx, GoldExperienceEntity.State.LIFE_GIVER, 20);
        burst(level, player, ParticleTypes.HAPPY_VILLAGER, 8);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // 9 - Flor Curativa: campo de cura no chão por 10 segundos.
    // ------------------------------------------------------------------------------------------
    private static boolean healingFlower(SkillContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        Vec3 center = player.position();
        SkillTicker.addField(level, center, player.getUUID(), level.getGameTime() + 200, 3.5);

        anim(ctx, GoldExperienceEntity.State.HEAL_SELF, 20);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 0.3, center.z, 30, 1.5, 0.1, 1.5, 0.0);
        sound(level, player);
        return true;
    }

    // ------------------------------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------------------------------

    /** Toca uma animação que o Gold Experience já tem. */
    private static void anim(SkillContext ctx, GoldExperienceEntity.State state, int ticks) {
        if (ctx.stand() instanceof GoldExperienceEntity ge) {
            ge.setState(state);
            ge.setMoveStun(ticks);
        }
    }

    private static boolean fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("skill.jcraft_expansion.msg." + key), true);
        return false;
    }

    private static void burst(ServerLevel level, LivingEntity e, ParticleOptions particle, int count) {
        level.sendParticles(particle, e.getX(), e.getY(1.0), e.getZ(), count, 0.6, 0.7, 0.6, 0.0);
    }

    private static void sound(ServerLevel level, LivingEntity e) {
        level.playSound(null, e.blockPosition(), SoundEvents.MOSS_PLACE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static boolean canAlter(ServerLevel level, ServerPlayer player, BlockPos pos) {
        return level.getGameRules().getBoolean(JCraft.STAND_GRIEFING) && JUtils.mayAlter(level, player, pos, null);
    }

    /** Item da mão secundária (se houver) ou da principal, como no Life Giver do JCraft. */
    private static ItemStack heldStack(Player player) {
        ItemStack off = player.getOffhandItem();
        return off.isEmpty() ? player.getMainHandItem() : off;
    }

    private static GESnakeEntity spawnSnake(ServerLevel level, ServerPlayer player, ItemStack held, double ox, double oz) {
        GESnakeEntity snake = new GESnakeEntity(JEntityTypeRegistry.GE_SNAKE.get(), level);
        snake.tame(player);
        snake.moveTo(player.getX() + ox, player.getY() + 0.5, player.getZ() + oz, player.getYRot(), 0.0f);
        snake.setItemInHand(InteractionHand.MAIN_HAND, held);
        level.addFreshEntity(snake);
        return snake;
    }

    private static LivingEntity lookEntity(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof LivingEntity le && le != player && le.isAlive() && !le.isSpectator()
                        && !(le instanceof StandEntity<?, ?>),
                range * range);
        if (result != null && result.getEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private static BlockHitResult lookBlock(ServerPlayer player, double range) {
        HitResult hit = player.pick(range, 1.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit;
        }
        return null;
    }

    private static LivingEntity nearestEnemy(ServerLevel level, ServerPlayer player, double range) {
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                        e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof StandEntity<?, ?>)
                                && !SkillTicker.isAlly(e, player))
                .stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }
}
