package dev.forja.difficulty;

/** Starts everything in this package; called once from Forja's initializer. */
public final class DifficultyRules {
	private DifficultyRules() {
	}

	public static void register() {
		java.util.Objects.requireNonNull(Adaptive.VALUE);
		java.util.Objects.requireNonNull(Nights.COUNT);
		java.util.Objects.requireNonNull(Nights.LAST_DAY);
		Scaling.register();
		Adaptive.register();
		Nights.register();
		Rewards.register();
	}
}
