package com.dodoxgod.filo.combat;

/** Fórmulas puras del nuevo cálculo de armadura, sin dependencias de Minecraft para poder probarlas. */
public final class ArmorMath {
	private ArmorMath() {
	}

	/**
	 * La dureza reduce la penetración del atacante.
	 * Con dureza 0 la penetración queda igual; con dureza = {@code toughnessScale} queda a la mitad.
	 */
	public static double effectivePenetration(double penetration, double toughness, double toughnessScale) {
		double pen = clamp(penetration, 0.0, 1.0);
		if (toughnessScale <= 0) return pen;
		return pen * (toughnessScale / (toughnessScale + Math.max(0.0, toughness)));
	}

	/**
	 * Fracción de daño que se bloquea: {@code armadura / (armadura + K)}, limitada por {@code maxReduction}.
	 * Sin tope duro: cada punto extra de armadura ayuda menos que el anterior.
	 */
	public static double reduction(double effectiveArmor, double curveK, double maxReduction) {
		if (effectiveArmor <= 0) return 0.0;
		double k = Math.max(curveK, 0.0001);
		return Math.min(clamp(maxReduction, 0.0, 1.0), effectiveArmor / (effectiveArmor + k));
	}

	/** Protección de una pieza según su durabilidad restante: 100 % nueva, {@code minFactor} rota. */
	public static double durabilityFactor(int damage, int maxDamage, double minFactor) {
		if (maxDamage <= 0) return 1.0;
		double remaining = clamp(1.0 - (double) damage / maxDamage, 0.0, 1.0);
		return minFactor + (1.0 - minFactor) * remaining;
	}

	/** Daño final tras armadura. */
	public static double damageAfterArmor(double amount, double armor, double penetration, double toughness,
			double curveK, double maxReduction, double toughnessScale) {
		double pen = effectivePenetration(penetration, toughness, toughnessScale);
		return amount * (1.0 - reduction(armor * (1.0 - pen), curveK, maxReduction));
	}

	public static double clamp(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}
}
