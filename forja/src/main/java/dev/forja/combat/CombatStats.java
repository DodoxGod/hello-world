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
	/** An enderman blinked away from a blow (dev.forja.ai.EnderDodge). */
	public static final String TELEPORT_DODGE = "teleport_dodge";
	/** An enderman rolled its dodge but had nowhere to blink to, and took the blow. */
	public static final String TELEPORT_DODGE_FAILED = "teleport_dodge_failed";

	/** A warned blow (MeleeAttackGoalMixin): started, and how it ended. */
	public static final String WARNED = "aviso";
	public static final String WARNED_LANDED = "aviso_llega";
	/** Out of reach at the end, having been struck during the warning (knocked back), or not (the target moved). */
	public static final String WARNED_KNOCKED = "aviso_fuera_empujado";
	public static final String WARNED_MOVED = "aviso_fuera_movido";
	public static final String WARNED_UNSEEN = "aviso_sin_vista";
	/** In reach and in sight, and the blow did nothing (blocked, i-frames). */
	public static final String WARNED_NO_DAMAGE = "aviso_sin_dano";
	/** Dropped before the end: staggered, its goal stopped, or a new target with no turn. */
	public static final String WARNED_CUT = "aviso_cortado";
	/** A zombie's lunge (VanillaSpecials.LUNGE) that reached its target. */
	public static final String LUNGE_HIT = "lunge_toca";

	private static final Map<Entity, Map<String, Integer>> COUNTS = new WeakHashMap<>();

	private CombatStats() {
	}

	public static void record(Entity entity, String what) {
		COUNTS.computeIfAbsent(entity, e -> new HashMap<>()).merge(what, 1, Integer::sum);
	}

	/** Everything counted for this entity (a copy). */
	public static Map<String, Integer> counts(Entity entity) {
		Map<String, Integer> counts = COUNTS.get(entity);
		return counts == null ? Map.of() : new HashMap<>(counts);
	}

	public static int count(Entity entity, String what) {
		Map<String, Integer> counts = COUNTS.get(entity);
		return counts == null ? 0 : counts.getOrDefault(what, 0);
	}
}
