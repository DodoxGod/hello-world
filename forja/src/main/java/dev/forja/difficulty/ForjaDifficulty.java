package dev.forja.difficulty;

import java.util.Locale;

import dev.forja.combat.CombatConfig;

/**
 * The four difficulties of Forja, on top of Minecraft's own. Each one scales everything else in this
 * package: how tough and how dangerous monsters are, how often they come stronger, how hard one blow can
 * hit them, how sure their decisions are (the sampling temperature of a trained brain), and how much
 * beating them pays.
 */
public enum ForjaDifficulty {
	//          health damage posture threat  cap   temperature loot
	APRENDIZ(   0.8,   0.7,   0.8,    0.5,    1.4,  1.3,        0.8),
	HERRERO(    1.0,   1.0,   1.0,    1.0,    1.0,  1.0,        1.0),
	MAESTRO(    1.3,   1.25,  1.2,    1.5,    0.85, 0.8,        1.3),
	LEYENDA(    1.7,   1.5,   1.4,    2.2,    0.7,  0.6,        1.7);

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

	ForjaDifficulty(double health, double damage, double posture, double threat, double cap, double temperature, double loot) {
		this.health = health;
		this.damage = damage;
		this.posture = posture;
		this.threat = threat;
		this.cap = cap;
		this.temperature = temperature;
		this.loot = loot;
	}

	/** The one in the config; HERRERO if the config names none that exists. */
	public static ForjaDifficulty current() {
		return parse(CombatConfig.get().dificultad);
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
