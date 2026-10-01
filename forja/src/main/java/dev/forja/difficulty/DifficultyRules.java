package dev.forja.difficulty;

/** Starts everything in this package; called once from Forja's initializer. */
public final class DifficultyRules {
	private DifficultyRules() {
	}

	public static void register() {
		// The ladder first: every rule below asks it what the level turns on.
		Ladder.register();
		java.util.Objects.requireNonNull(Adaptive.VALUE);
		java.util.Objects.requireNonNull(Nights.COUNT);
		java.util.Objects.requireNonNull(Nights.LAST_DAY);
		Scaling.register();
		// After Scaling: a mob loaded for the first time has its threat rolled by then.
		Threat.register();
		Adaptive.register();
		Nights.register();
		Rewards.register();
	}
}
