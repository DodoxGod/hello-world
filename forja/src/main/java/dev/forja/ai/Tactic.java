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
	PARAPETARSE,

	// --- red_mob_v3: the network's head has these four more, after the nine of v2 (a v2 network never picks them)

	/** The bait: runs to the nearest ally fighting the same player and 2.5 past it, drawing the player into it. */
	CEBO,
	/** The relay: out of the player's reach, 5 blocks off, a little to one side, while it gets its wind back. */
	RELEVO,
	/** Out of sight: behind a block from the player's eyes if one is within 4, else in an ally's shadow, else away. */
	OCULTARSE,
	/** The push: round to the side of the player away from lava or a drop within 3 of them, then in to strike. */
	EMPUJAR;

	/** How many the v2 contract has: LIBRE to PARAPETARSE. */
	public static final int V2_COUNT = 9;

	private static final Tactic[] VALUES = values();

	public static Tactic of(int index) {
		return index >= 0 && index < VALUES.length ? VALUES[index] : LIBRE;
	}
}
