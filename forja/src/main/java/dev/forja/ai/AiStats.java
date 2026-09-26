package dev.forja.ai;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.world.entity.Mob;

/**
 * What the brains have been doing, per family: how often each tactic was chosen, and how many decisions
 * came from a network against the rules. Printed by /forja ia estadisticas; reset with the server.
 */
public final class AiStats {
	private static final Map<MobFamily, long[]> TACTICS = new EnumMap<>(MobFamily.class);
	private static final Map<MobFamily, long[]> SOURCES = new EnumMap<>(MobFamily.class);

	private AiStats() {
	}

	static void count(Mob mob, Decision decision) {
		MobFamily family = MobFamily.of(mob);
		TACTICS.computeIfAbsent(family, f -> new long[Tactic.values().length])[decision.tactic().ordinal()]++;
		MobMind mind = MobAi.mind(mob);
		SOURCES.computeIfAbsent(family, f -> new long[2])[mind != null && mind.networked ? 1 : 0]++;
	}

	/** One line per family: "cuerpo: red 1200, reglas 300 · LIBRE 1200, RODEAR 180, ...". */
	public static String summary() {
		StringBuilder out = new StringBuilder();
		for (MobFamily family : MobFamily.values()) {
			long[] tactics = TACTICS.get(family);
			long[] sources = SOURCES.get(family);
			if (tactics == null) {
				continue;
			}
			out.append(family.file).append(": red ").append(sources[1]).append(", reglas ").append(sources[0]).append(" ·");
			for (Tactic tactic : Tactic.values()) {
				if (tactics[tactic.ordinal()] > 0) {
					out.append(' ').append(tactic.name()).append(' ').append(tactics[tactic.ordinal()]);
				}
			}
			out.append('\n');
		}
		return out.isEmpty() ? "sin decisiones todavía" : out.toString().trim();
	}
}
