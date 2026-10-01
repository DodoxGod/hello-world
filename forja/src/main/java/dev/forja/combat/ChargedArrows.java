package dev.forja.combat;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.entity.Entity;

/** Arrows from charged shots: they hit harder and go further through armor. */
public final class ChargedArrows {
	private static final Set<Entity> CHARGED = Collections.newSetFromMap(new WeakHashMap<>());

	private ChargedArrows() {
	}

	public static void mark(Entity arrow) {
		CHARGED.add(arrow);
	}

	public static boolean isCharged(Entity entity) {
		return entity != null && CHARGED.contains(entity);
	}
}
