package dev.forja.combat;

/**
 * One blow, described.
 *
 * @param precise whether the zone was aimed (a player's swing, a projectile): only precise blows to
 *                the head get the head multiplier
 */
public record AttackProfile(DamageKind kind, double penetration, HitZone zone, boolean precise) {
	public static final AttackProfile NEUTRAL = new AttackProfile(DamageKind.OTHER, 0.0, HitZone.WHOLE, false);
}
