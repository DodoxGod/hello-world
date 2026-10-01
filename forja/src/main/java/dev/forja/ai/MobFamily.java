package dev.forja.ai;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;

/**
 * The families a brain is trained per (red_mob_v1 "grupo"): the mobs in a family fight the same way, so
 * one network serves all of them, with the exact type as an input. The order is the one of the
 * simulator's one-hot for allies (cuerpo, arquero, creeper, araña, otro).
 */
public enum MobFamily {
	CUERPO("cuerpo"),
	ARQUERO("arquero"),
	CREEPER("creeper"),
	ARANA("arana"),
	OTRO("otro"),
	/**
	 * The blaze: hovering, a burst of three fireballs, immune to fire (Andy, 2026-09-29: "too different" to share
	 * a network). Last, so the simulator's one-hot above keeps its order; an ally blaze is seen as "otro" there
	 * ({@link #slot}). Until a red_blaze.json is trained it fights by vanilla's own goals.
	 */
	BLAZE("blaze");

	/** The name the network files use: red_&lt;name&gt;.json. */
	public final String file;

	MobFamily(String file) {
		this.file = file;
	}

	public static MobFamily of(EntityType<?> type) {
		if (type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || type == EntityTypes.DROWNED
			|| type == EntityTypes.ZOMBIE_VILLAGER) {
			return CUERPO;
		}
		if (type == EntityTypes.SKELETON || type == EntityTypes.STRAY || type == EntityTypes.BOGGED) {
			return ARQUERO;
		}
		if (type == EntityTypes.CREEPER) {
			return CREEPER;
		}
		if (type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER) {
			return ARANA;
		}
		if (type == EntityTypes.BLAZE) {
			return BLAZE;
		}
		return OTRO;
	}

	/** Where this family sits in the observation's five-way ally one-hot: the blaze, newer than it, is "otro". */
	public int slot() {
		return this == BLAZE ? OTRO.ordinal() : this.ordinal();
	}

	/**
	 * Which network a vanilla mob fights with, by file name, or null for the rules only (Andy, 2026-09-29). Mobs
	 * that fight like a zombie borrow the zombie's; the ones with a crossbow borrow the archer's; the ravager, the
	 * hoglin and the zoglin the mod's tank's, and the little ones its swarm's. Witches, evokers, illusioners, creakings, guardians, the
	 * warden, slimes and magma cubes, ghasts and shulkers keep to the rules; flyers (phantom, vex), the enderman
	 * and the breeze will get networks of their own later.
	 */
	public static @org.jspecify.annotations.Nullable String network(LivingEntity entity) {
		EntityType<?> type = entity.getType();
		if (type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || type == EntityTypes.DROWNED || type == EntityTypes.ZOMBIE_VILLAGER
			|| type == EntityTypes.WITHER_SKELETON || type == EntityTypes.ZOMBIFIED_PIGLIN || type == EntityTypes.PIGLIN_BRUTE
			|| type == EntityTypes.VINDICATOR || type == EntityTypes.PIGLIN && !holdsCrossbow(entity)) {
			return CUERPO.file;
		}
		if (type == EntityTypes.SKELETON || type == EntityTypes.STRAY || type == EntityTypes.BOGGED) {
			// by what it fights with: a skeleton that put its bow away for a blade is a body (see of(LivingEntity))
			return of(entity).file;
		}
		if (type == EntityTypes.PILLAGER || type == EntityTypes.PIGLIN) {
			return holdsCrossbow(entity) ? ARQUERO.file : CUERPO.file;
		}
		if (type == EntityTypes.CREEPER) {
			return CREEPER.file;
		}
		if (type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER) {
			return ARANA.file;
		}
		if (type == EntityTypes.BLAZE) {
			return BLAZE.file;
		}
		if (type == EntityTypes.RAVAGER || type == EntityTypes.HOGLIN || type == EntityTypes.ZOGLIN) {
			return ForjaFamily.TANQUE.file;
		}
		if (type == EntityTypes.SILVERFISH || type == EntityTypes.ENDERMITE) {
			return ForjaFamily.ENJAMBRE.file;
		}
		return null;
	}

	/**
	 * The family whose executor (TacticGoal's "usar") a networked mob fights with: the one its network was
	 * trained for. A wither skeleton on the zombie's network strikes like one; a pillager on the archer's shoots.
	 */
	public static MobFamily executor(LivingEntity entity) {
		String net = network(entity);
		if (net == null) {
			return of(entity);
		}
		for (MobFamily family : values()) {
			if (family.file.equals(net)) {
				return family;
			}
		}
		return OTRO;
	}

	public static boolean holdsCrossbow(LivingEntity entity) {
		return entity.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem
			|| entity.getOffhandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem;
	}

	/**
	 * A mob's family by what it fights with, not only by what it is: a skeleton that has put its bow away
	 * for a sword walks up and swings, so it is a body. Without this an elite skeleton with a blade and a
	 * network never attacked at all (the network sent it to its bow, and it had none).
	 */
	public static MobFamily of(LivingEntity entity) {
		MobFamily family = of(entity.getType());
		if (family == ARQUERO && !(entity.getMainHandItem().getItem() instanceof BowItem)
			&& !(entity.getOffhandItem().getItem() instanceof BowItem)) {
			return CUERPO;
		}
		return family;
	}

	/**
	 * The index of the type in the simulator's one-hot (zombie, husk, drowned, skeleton, stray, creeper,
	 * spider), or -1 for any other type.
	 */
	public static int typeIndex(EntityType<?> type) {
		if (type == EntityTypes.ZOMBIE) return 0;
		if (type == EntityTypes.HUSK) return 1;
		if (type == EntityTypes.DROWNED) return 2;
		if (type == EntityTypes.SKELETON) return 3;
		if (type == EntityTypes.STRAY) return 4;
		if (type == EntityTypes.CREEPER) return 5;
		if (type == EntityTypes.SPIDER) return 6;
		return -1;
	}
}
