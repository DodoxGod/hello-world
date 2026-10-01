package com.dodoxgod.filo.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorMathTest {
	private static final double K = 20.0;
	private static final double MAX = 0.8;
	private static final double TOUGH_SCALE = 10.0;

	@Test
	void zombieHitOnFullIron() {
		// 6 de daño, armadura 15, sin penetración: 15 / 35 = 42.9 % bloqueado.
		double dealt = ArmorMath.damageAfterArmor(6, 15, 0, 0, K, MAX, TOUGH_SCALE);
		assertEquals(6 * (1 - 15.0 / 35.0), dealt, 1e-9);
	}

	@Test
	void chargedArrowPiercesIron() {
		// 9 de daño, 40 % de penetración: armadura efectiva 9, 9 / 29 bloqueado.
		double dealt = ArmorMath.damageAfterArmor(9, 15, 0.4, 0, K, MAX, TOUGH_SCALE);
		assertEquals(9 * (1 - 9.0 / 29.0), dealt, 1e-9);
	}

	@Test
	void toughnessHalvesPenetrationAtScale() {
		assertEquals(0.2, ArmorMath.effectivePenetration(0.4, 10, TOUGH_SCALE), 1e-9);
		assertEquals(0.4, ArmorMath.effectivePenetration(0.4, 0, TOUGH_SCALE), 1e-9);
	}

	@Test
	void reductionIsCappedAndNeverTotal() {
		assertEquals(MAX, ArmorMath.reduction(10_000, K, MAX), 1e-9);
		assertEquals(0.0, ArmorMath.reduction(0, K, MAX), 1e-9);
		assertTrue(ArmorMath.reduction(30, K, 1.0) < 1.0);
	}

	@Test
	void worseArmorBlocksLess() {
		assertTrue(ArmorMath.reduction(10, K, MAX) < ArmorMath.reduction(20, K, MAX));
	}

	@Test
	void brokenArmorProtectsLess() {
		assertEquals(1.0, ArmorMath.durabilityFactor(0, 100, 0.6), 1e-9);
		assertEquals(0.6, ArmorMath.durabilityFactor(100, 100, 0.6), 1e-9);
		assertEquals(0.8, ArmorMath.durabilityFactor(50, 100, 0.6), 1e-9);
	}
}
