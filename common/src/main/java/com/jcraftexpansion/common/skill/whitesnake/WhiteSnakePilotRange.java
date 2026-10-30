package com.jcraftexpansion.common.skill.whitesnake;

/**
 * Alcance do modo piloto do White Snake.
 * <p>
 * O JCraft original puxa o stand de volta quando ele passa de 20 blocos do usuário. Aqui o limite é maior.
 * Não passe de ~70: o stand (entidade "CREATURE") só é enviado aos clientes dentro de ~80 blocos.
 */
public final class WhiteSnakePilotRange {
    /** Distância máxima (em blocos) entre o usuário e o White Snake pilotado. O original era 20. */
    public static final double MAX_DISTANCE = 64.0;

    /** Mesmo valor ao quadrado (o JCraft compara distância ao quadrado). */
    public static final double MAX_DISTANCE_SQR = MAX_DISTANCE * MAX_DISTANCE;

    private WhiteSnakePilotRange() {
    }
}
