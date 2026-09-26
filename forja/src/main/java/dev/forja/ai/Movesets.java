package dev.forja.ai;

import java.util.List;

import net.minecraft.world.entity.Mob;

/** Which specials each mob has, in slot order (at most 4): vanilla's here, the mod's own in phase 6. */
public final class Movesets {
	public static final int SLOTS = 4;

	private Movesets() {
	}

	public static List<Special> of(Mob mob) {
		List<Special> forja = ForjaSpecials.of(mob);
		if (!forja.isEmpty()) {
			return forja;
		}
		return VanillaSpecials.of(mob.getType(), mob);
	}
}
