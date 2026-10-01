package dev.forja.difficulty;

import java.util.Locale;

/**
 * The four sets of multipliers ("presets") of Forja. Each one scales everything else in this package: how tough
 * and how dangerous monsters are, how often they come stronger, how hard one blow can hit them, how sure their
 * decisions are (the sampling temperature of a trained brain), and how much beating them pays.
 *
 * <p>Since the difficulty ladder (Andy, 2026-09-30) the preset is no longer chosen on its own: each level of
 * {@link Ladder} names one (Fácil Aprendiz, Normal and Difícil Herrero, Extremo Maestro). The config's
 * {@code dificultad} still forces one for the whole server, as an admin override; "auto" (the default) leaves it to
 * the ladder. Leyenda is only reached that way. The four stay as they are: the networks see them one-hot (dif_*).
 */
public enum ForjaDifficulty {
	//          health damage posture threat  cap   temperature loot  feint
	APRENDIZ(   0.8,   0.7,   0.8,    0.5,    1.4,  1.3,        0.8,  0.05),
	HERRERO(    1.0,   1.0,   1.0,    1.0,    1.0,  1.0,        1.0,  0.10),
	MAESTRO(    1.3,   1.25,  1.2,    1.5,    0.85, 0.8,        1.3,  0.15),
	LEYENDA(    1.7,   1.5,   1.4,    2.2,    0.7,  0.6,        1.7,  0.20);

	/** Multiplier on a hostile mob's max health. */
	public final double health;
	/** Multiplier on the damage hostile mobs deal to players. */
	public final double damage;
	/** Multiplier on a mob's max posture. */
	public final double posture;
	/** Multiplier on the chance of a veteran or elite. */
	public final double threat;
	/** Multiplier on the per-hit damage cap (higher is easier). */
	public final double cap;
	/** Multiplier on the sampling temperature of trained brains (lower is sharper). */
	public final double temperature;
	/** Multiplier on the rewards for beating stronger foes. */
	public final double loot;
	/** The least chance any mob that warns its blow fakes it, whatever the player does (ai/Aggression.feintChance). */
	public final double feint;

	ForjaDifficulty(double health, double damage, double posture, double threat, double cap, double temperature, double loot, double feint) {
		this.health = health;
		this.damage = damage;
		this.posture = posture;
		this.threat = threat;
		this.cap = cap;
		this.temperature = temperature;
		this.loot = loot;
		this.feint = feint;
	}

	/** The preset in force: the config's override when it names one, else the current level's (Ladder). */
	public static ForjaDifficulty current() {
		return Ladder.current().preset();
	}

	/** The preset the config's {@code dificultad} forces, or null for "auto" (or anything that names none). */
	public static ForjaDifficulty forced(String name) {
		if (name == null || name.isBlank() || "auto".equalsIgnoreCase(name.trim())) {
			return null;
		}
		for (ForjaDifficulty difficulty : values()) {
			if (difficulty.name().equalsIgnoreCase(name.trim())) {
				return difficulty;
			}
		}
		return null;
	}

	public static ForjaDifficulty parse(String name) {
		if (name != null) {
			for (ForjaDifficulty difficulty : values()) {
				if (difficulty.name().equalsIgnoreCase(name.trim())) {
					return difficulty;
				}
			}
		}
		return HERRERO;
	}

	public String key() {
		return "dificultad.forja." + name().toLowerCase(Locale.ROOT);
	}
}
