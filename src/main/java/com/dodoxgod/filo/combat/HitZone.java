package com.dodoxgod.filo.combat;

/**
 * Zona del cuerpo que recibe el golpe. Cada zona reparte la protección entre las piezas
 * de armadura en el orden casco, peto, pantalones, botas.
 */
public enum HitZone {
	HEAD(0.70, 0.30, 0.00, 0.00),
	TORSO(0.10, 0.70, 0.20, 0.00),
	LEGS(0.00, 0.15, 0.70, 0.15),
	FEET(0.00, 0.00, 0.30, 0.70),
	/** Daño que no viene de un punto concreto (explosiones, etc.): todas las piezas por igual. */
	WHOLE(0.25, 0.25, 0.25, 0.25);

	private final double[] weights;

	HitZone(double head, double chest, double legs, double feet) {
		this.weights = new double[] {head, chest, legs, feet};
	}

	/** @param slot 0 = casco, 1 = peto, 2 = pantalones, 3 = botas */
	public double weight(int slot) {
		return weights[slot];
	}

	/** Zona según la altura relativa del impacto (0 = pies, 1 = coronilla). */
	public static HitZone fromRelativeHeight(double rel) {
		if (rel >= 0.78) return HEAD;
		if (rel >= 0.45) return TORSO;
		if (rel >= 0.15) return LEGS;
		return FEET;
	}
}
