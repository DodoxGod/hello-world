package dev.forja.ai;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;

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
	OTRO("otro");

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
		return OTRO;
	}

	public static MobFamily of(LivingEntity entity) {
		return of(entity.getType());
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
