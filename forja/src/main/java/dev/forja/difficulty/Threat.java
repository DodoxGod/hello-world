package dev.forja.difficulty;

import dev.forja.combat.CombatConfig;
import net.minecraft.world.entity.LivingEntity;

/**
 * How dangerous one monster is, beyond its kind: normal, veteran, elite or champion. The champion is
 * Forja's old elite (a legend in its hands, see {@link dev.forja.world.Elites}); veterans and elites are
 * ordinary monsters that simply came stronger. Kept as an entity tag, so it survives a reload.
 */
public enum Threat {
	//        health armor posture damage tag
	NORMAL(   1.0,   0.0,  1.0,    1.0,   null),
	VETERANO( 1.5,   2.0,  1.3,    1.15,  "forja_veterano"),
	ELITE(    2.5,   4.0,  1.8,    1.3,   "forja_amenaza_elite"),
	CAMPEON(  1.0,   0.0,  2.5,    1.0,   "forja_elite");

	/** Multiplier on max health (the champion already has its own, from Elites). */
	public final double health;
	/** Armor added. */
	public final double armor;
	/** Multiplier on max posture. */
	public final double posture;
	/** Multiplier on the damage it deals. */
	public final double damage;
	private final String tag;

	Threat(double health, double armor, double posture, double damage, String tag) {
		this.health = health;
		this.armor = armor;
		this.posture = posture;
		this.damage = damage;
		this.tag = tag;
	}

	public static Threat of(LivingEntity entity) {
		var tags = entity.entityTags();
		if (tags.contains(CAMPEON.tag)) return CAMPEON;
		if (tags.contains(ELITE.tag)) return ELITE;
		if (tags.contains(VETERANO.tag)) return VETERANO;
		return NORMAL;
	}

	public void mark(LivingEntity entity) {
		if (this.tag != null) {
			entity.addTag(this.tag);
		}
	}

	/** Elites, champions and bosses keep a guard that has to break before their health really suffers. */
	public boolean guarded() {
		return this == ELITE || this == CAMPEON;
	}

	/** The most of its max health one ordinary blow can take from a mob of this threat. */
	public double hitCap() {
		CombatConfig cfg = CombatConfig.get();
		return switch (this) {
			case NORMAL -> cfg.hitCapNormal;
			case VETERANO -> cfg.hitCapVeteran;
			case ELITE -> cfg.hitCapElite;
			case CAMPEON -> cfg.hitCapChampion;
		};
	}

	/** Multiplier on the sampling temperature of a trained brain: stronger foes decide more surely. */
	public double temperature() {
		return switch (this) {
			case NORMAL -> 1.0;
			case VETERANO -> 0.9;
			case ELITE -> 0.75;
			case CAMPEON -> 0.6;
		};
	}
}
