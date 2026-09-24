package com.dodoxgod.filo.combat;

/**
 * Descripción de un ataque concreto.
 *
 * @param kind        tipo de daño
 * @param penetration fracción de armadura que ignora (0 a 1), antes de aplicar la dureza
 * @param zone        zona del cuerpo golpeada
 * @param precise     si la zona se calculó con precisión (jugador apuntando o proyectil);
 *                    solo los golpes precisos reciben el multiplicador de cabeza
 */
public record AttackProfile(DamageKind kind, double penetration, HitZone zone, boolean precise) {
	public static final AttackProfile NEUTRAL = new AttackProfile(DamageKind.OTHER, 0.0, HitZone.WHOLE, false);
}
