package com.dodoxgod.filo.combat;

import net.minecraft.entity.Entity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Flechas de disparos cargados: hacen más daño y atraviesan más armadura. */
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
