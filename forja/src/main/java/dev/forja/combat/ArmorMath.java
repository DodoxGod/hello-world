package dev.forja.combat;

/** The armor formula with nothing from Minecraft in it, so it can be reasoned about on its own. */
public final class ArmorMath {
	private ArmorMath() {
	}

	/** Toughness blunts penetration: none at zero toughness, halved at {@code toughnessScale}. */
	public static double effectivePenetration(double penetration, double toughness, double toughnessScale) {
		double pen = clamp(penetration, 0.0, 1.0);
		if (toughnessScale <= 0) return pen;
		return pen * (toughnessScale / (toughnessScale + Math.max(0.0, toughness)));
	}

	/** The share of a blow armor stops: armor / (armor + K), no hard cap below {@code maxReduction}. */
	public static double reduction(double effectiveArmor, double curveK, double maxReduction) {
		if (effectiveArmor <= 0) return 0.0;
		double k = Math.max(curveK, 0.0001);
		return Math.min(clamp(maxReduction, 0.0, 1.0), effectiveArmor / (effectiveArmor + k));
	}

	/** Full protection new, {@code minFactor} of it on the verge of breaking. */
	public static double durabilityFactor(int damage, int maxDamage, double minFactor) {
		if (maxDamage <= 0) return 1.0;
		double remaining = clamp(1.0 - (double) damage / maxDamage, 0.0, 1.0);
		return minFactor + (1.0 - minFactor) * remaining;
	}

	public static double damageAfterArmor(double amount, double armor, double penetration, double toughness,
		double curveK, double maxReduction, double toughnessScale) {
		double pen = effectivePenetration(penetration, toughness, toughnessScale);
		return amount * (1.0 - reduction(armor * (1.0 - pen), curveK, maxReduction));
	}

	public static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}
}
