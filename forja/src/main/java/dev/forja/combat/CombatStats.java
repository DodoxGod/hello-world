package dev.forja.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.Entity;

/** Counts the special moves each mob has made. The game tests read it. */
public final class CombatStats {
	public static final String LUNGE = "lunge";
	public static final String CHARGED_SHOT = "charged_shot";
	public static final String FEINT = "feint";

	private static final Map<Entity, Map<String, Integer>> COUNTS = new WeakHashMap<>();

	private CombatStats() {
	}

	public static void record(Entity entity, String what) {
		COUNTS.computeIfAbsent(entity, e -> new HashMap<>()).merge(what, 1, Integer::sum);
	}

	public static int count(Entity entity, String what) {
		Map<String, Integer> counts = COUNTS.get(entity);
		return counts == null ? 0 : counts.getOrDefault(what, 0);
	}
}
