package com.jcraftexpansion.common.attack;

import com.mojang.datafixers.kinds.App;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.arna.jcraft.api.attack.IAttacker;
import net.arna.jcraft.api.attack.MoveType;
import net.arna.jcraft.api.attack.enums.MoveInputType;
import net.arna.jcraft.api.attack.moves.AbstractBarrageAttack;
import net.arna.jcraft.common.network.c2s.PlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Barragem que dura enquanto a tecla de barragem (B por padrão) estiver pressionada.
 * <p>
 * Funciona assim: a cada tick no servidor, se o jogador ainda está segurando a tecla e a barragem
 * está perto de acabar, o moveStun é "rebobinado" por um múltiplo exato do intervalo entre golpes.
 * Assim o ritmo dos golpes (shouldPerform) continua o mesmo e a barragem nunca termina sozinha.
 * Ao soltar a tecla, a barragem é cancelada.
 */
public final class HeldBarrageAttack<A extends IAttacker<? extends A, ?>>
        extends AbstractBarrageAttack<HeldBarrageAttack<A>, A> {

    public HeldBarrageAttack(int cooldown, int windup, int duration, float moveDistance, float damage, int stun,
                             float hitboxSize, float knockback, float offset, int interval) {
        super(cooldown, windup, duration, moveDistance, damage, stun, hitboxSize, knockback, offset, interval);
        this.withShockwaves();
        this.withHoldable();
        // O stun "crescente" da barragem normal depende do moveStun; com o loop ele ficaria oscilando.
        this.withNoStunWindup();
    }

    @Override
    public MoveType<HeldBarrageAttack<A>> getMoveType() {
        return Type.INSTANCE.cast();
    }

    @Override
    protected HeldBarrageAttack<A> getThis() {
        return this;
    }

    @Override
    public void activeTick(A attacker, int moveStun) {
        super.activeTick(attacker, moveStun);

        // O servidor é a autoridade do moveStun (ele é sincronizado com o client).
        if (attacker.getEntityWorld().isClientSide()) {
            return;
        }
        if (!isBarrageHeld(attacker)) {
            return;
        }

        int interval = Math.max(1, getInterval());
        // Quanto o moveStun pode ser rebobinado sem sair da fase "windup já passou" e mantendo a fase dos golpes.
        int loopLength = interval * Math.max(1, (getDuration() - getWindup() - interval) / interval);

        if (moveStun <= interval) {
            attacker.setMoveStun(moveStun + loopLength);
        }
    }

    @Override
    public void onUserMoveInput(A attacker, MoveInputType type, boolean pressed, boolean moveInitiated) {
        super.onUserMoveInput(attacker, type, pressed, moveInitiated);
        // Soltou a tecla de barragem: encerra a barragem.
        if (!pressed && type.getMoveClass() == this.getMoveClass()) {
            attacker.cancelMove();
        }
    }

    private static boolean isBarrageHeld(IAttacker<?, ?> attacker) {
        LivingEntity user = attacker.getUser();
        if (user instanceof ServerPlayer player) {
            return PlayerInputPacket.getInputStateManager(player).heldInputs.containsKey(MoveInputType.BARRAGE);
        }
        // Mobs/IA não "seguram" tecla: usam a barragem normal, com duração fixa.
        return false;
    }

    @Override
    public HeldBarrageAttack<A> copy() {
        return this.copyExtras(new HeldBarrageAttack<A>(getCooldown(), getWindup(), getDuration(), getMoveDistance(),
                getDamage(), getStun(), getHitboxSize(), getKnockback(), getOffset(), getInterval()));
    }

    public static class Type extends AbstractBarrageAttack.Type<HeldBarrageAttack<?>> {
        public static final Type INSTANCE = new Type();

        @Override
        protected App<RecordCodecBuilder.Mu<HeldBarrageAttack<?>>, HeldBarrageAttack<?>> buildCodec(
                RecordCodecBuilder.Instance<HeldBarrageAttack<?>> instance) {
            return this.barrageDefault(instance, HeldBarrageAttack::new);
        }
    }
}