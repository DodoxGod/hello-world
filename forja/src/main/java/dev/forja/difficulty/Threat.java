package dev.forja.difficulty;

import dev.forja.Forja;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * How dangerous one monster is, beyond its kind: normal, veteran, elite or champion. The champion is
 * Forja's old elite (a legend in its hands, see {@link dev.forja.world.Elites}); veterans and elites are
 * ordinary monsters that simply came stronger. Kept as an entity tag, so it survives a reload.
 *
 * <p>The tag stays on the server, and "la única forma para identificar un veterano es viendo el nametag
 * que tiene": so the threat is also mirrored into {@link #SHOWN}, an attachment synced to every player
 * tracking the mob, which is what the badge over its head ({@code client.ThreatBadge}) reads. The tag is
 * the truth and the attachment only a copy: it is not saved, it is written again from the tags every
 * time the mob loads (a world or a chunk coming back), and every change of threat goes through
 * {@link #mark}, which writes it too.
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

	/**
	 * The threat as the clients see it: the ordinal, absent for a normal mob. Synced to everyone who can
	 * see the mob (the badge is for anybody looking), never saved (the tags are).
	 */
	public static final AttachmentType<Byte> SHOWN = AttachmentRegistry.<Byte>builder()
		.syncWith(ByteBufCodecs.BYTE.cast(), AttachmentSyncPredicate.all())
		.buildAndRegister(Forja.id("amenaza"));

	private static final Threat[] VALUES = values();

	Threat(double health, double armor, double posture, double damage, String tag) {
		this.health = health;
		this.armor = armor;
		this.posture = posture;
		this.damage = damage;
		this.tag = tag;
	}

	/** The armor penetration its blows carry (Andy, 2026-09-29): veteran 10 %, elite 20 %, champion 35 %. */
	public double penetration() {
		dev.forja.combat.CombatConfig cfg = dev.forja.combat.CombatConfig.get();
		return switch (this) {
			case NORMAL -> 0.0;
			case VETERANO -> cfg.penetrationVeteran;
			case ELITE -> cfg.penetrationElite;
			case CAMPEON -> cfg.penetrationChampion;
		};
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
		sync(entity);
	}

	/** Every mob that loads gets its synced copy back from its tags: a reloaded world, a chunk come back. */
	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living) {
				sync(living);
			}
		});
	}

	/** Writes the synced copy from the tags, and only when it changed, so a sync packet goes out only then. */
	public static void sync(LivingEntity entity) {
		if (entity.level().isClientSide()) {
			return;
		}
		Threat threat = of(entity);
		Byte now = entity.getAttached(SHOWN);
		if (threat == NORMAL) {
			if (now != null) {
				entity.removeAttached(SHOWN);
			}
		} else if (now == null || now != threat.ordinal()) {
			entity.setAttached(SHOWN, (byte) threat.ordinal());
		}
	}

	/** The threat as this side knows it: the synced copy on a client, the same on the server. */
	public static Threat shown(Entity entity) {
		Byte value = entity.getAttached(SHOWN);
		return value == null || value < 0 || value >= VALUES.length ? NORMAL : VALUES[value];
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
