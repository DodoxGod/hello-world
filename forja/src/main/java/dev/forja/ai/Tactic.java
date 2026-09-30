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
	EMPUJAR,

	// --- red_mob_v4 (docs/red_mob_v4_diseno.md §3.1): eight more, after v3's thirteen (a v3 network never picks them)

	/** To its own slot on the ring, by the outer ring when it has to cross in front of the player (its sector). */
	SECTOR,
	/** An archer with a friend in its line: to the nearest spot with a clear shot (Squad.clearLineStep), then it looses. */
	TIRO_LIBRE,
	/** To the point of its post in the captain's formation (M5); without a post, as RODEAR. */
	FORMACION,
	/** To a hiding spot out of the player's sight and still there, facing where it will come out (M4). */
	EMBOSCAR,
	/** To the last known position, then the latest sound, then a fan of three points ahead (M4). */
	BUSCAR,
	/** To the most useful thing on the floor, and it takes it (docs/red_mob_v4_diseno.md §4.7). */
	RECOGER,
	/** To the torch that lights the player most, and it puts it out: the one block a monster breaks (§4.10). */
	APAGAR_LUZ,
	/** The siege ring round a player on a pillar or a tower: out of sight of the top where it can, cutting the way down (§4.6). */
	ASEDIAR;

	/** How many the v2 contract has: LIBRE to PARAPETARSE. */
	public static final int V2_COUNT = 9;
	/** How many the v3 contract has: v2's and CEBO, RELEVO, OCULTARSE, EMPUJAR. */
	public static final int V3_COUNT = 13;

	private static final Tactic[] VALUES = values();

	public static Tactic of(int index) {
		return index >= 0 && index < VALUES.length ? VALUES[index] : LIBRE;
	}
}
