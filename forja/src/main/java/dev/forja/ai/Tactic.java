package dev.forja.ai;

/**
 * What a mob's brain can choose, besides the simulator's low-level controls. Kept few and exactly defined
 * (docs/COMBATE_ESPECIFICACION.md, "Tácticas"), because the simulator runs the same executor: a tactic that
 * does something different there than here teaches a network the wrong thing.
 */
public enum Tactic {
	/** The low-level controls (mover, saltar, usar) drive the mob, as in the simulator's M1. */
	LIBRE,
	/** Vanilla's own approach and attack (the rule brain's default). */
	ACERCARSE,
	/** To its slot on a ring around the target, radius 3.5, looking at it. */
	RODEAR,
	/** Behind the target: 150° to 210° off where it faces, radius 2.5, a little faster. */
	FLANQUEAR,
	/** Waiting its turn: keeps 4 to 6 blocks from the target, looking at it. */
	ESPERAR,
	/** Away from the target, 6 blocks further out, faster. */
	RETIRARSE,
	/** To the middle of the other mobs fighting the same target. */
	REAGRUPARSE,
	/** Shield up (if it has one) while closing in at a walk. */
	CUBRIRSE,
	/** To a spot within 4 blocks where a block stands between it and the player's eyes (cover from arrows). */
	PARAPETARSE;

	private static final Tactic[] VALUES = values();

	public static Tactic of(int index) {
		return index >= 0 && index < VALUES.length ? VALUES[index] : LIBRE;
	}
}
