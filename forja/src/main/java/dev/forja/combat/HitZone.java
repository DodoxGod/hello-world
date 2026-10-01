package dev.forja.combat;

/** Where a blow lands, and how it spreads over helmet, chestplate, leggings and boots. */
public enum HitZone {
	HEAD(0.70, 0.30, 0.00, 0.00),
	TORSO(0.10, 0.70, 0.20, 0.00),
	LEGS(0.00, 0.15, 0.70, 0.15),
	FEET(0.00, 0.00, 0.30, 0.70),
	/** No single point (blasts and the like): every piece counts the same. */
	WHOLE(0.25, 0.25, 0.25, 0.25);

	private final double[] weights;

	HitZone(double head, double chest, double legs, double feet) {
		this.weights = new double[] {head, chest, legs, feet};
	}

	/** @param slot 0 helmet, 1 chestplate, 2 leggings, 3 boots */
	public double weight(int slot) {
		return weights[slot];
	}

	/** The zone at a relative height: 0 at the feet, 1 at the crown. */
	public static HitZone fromRelativeHeight(double rel) {
		if (rel >= 0.78) return HEAD;
		if (rel >= 0.45) return TORSO;
		if (rel >= 0.15) return LEGS;
		return FEET;
	}
}
