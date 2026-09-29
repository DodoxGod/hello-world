package dev.forja.ai;

import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The output side of a blaze network, red_blaze_v1 (docs/red_blaze_contrato.json; Andy, 2026-09-29): where each head's
 * logits sit, what the network may not choose right now, and the sampling. The network itself is a plain
 * {@link NetBrain}: the same layers and forward pass as the mobs' v3, only the inputs and the heads are the blaze's.
 */
public final class BlazeBrain {
	/** mover (9), vertical (3), fuego (3), distancia (3), tactica (4): 22 outputs. */
	public static final int MOVE_AT = 0;
	public static final int MOVES = 9;
	public static final int VERTICAL_AT = MOVE_AT + MOVES;
	public static final int FIRE_AT = VERTICAL_AT + 3;
	public static final int RANGE_AT = FIRE_AT + 3;
	public static final int TACTIC_AT = RANGE_AT + 3;
	public static final int TACTICS = BlazeDecision.Plan.values().length;
	public static final int OUTPUTS = TACTIC_AT + TACTICS;

	private BlazeBrain() {
	}

	/**
	 * Why a network cannot drive a blaze, or null: its formato must be red_blaze_v1, its inputs exactly ObsBlaze's 65 in
	 * the same order, and its outputs the contract's 22. Anything else (a v3 or v4 mob network among them) is refused,
	 * and the blaze keeps to its rules.
	 */
	public static String check(NetBrain net) {
		if (!ObsBlaze.FORMAT.equals(net.format)) {
			return "formato '" + net.format + "': el blaze solo acepta " + ObsBlaze.FORMAT;
		}
		List<String> ours = ObsBlaze.names();
		if (net.inputs() != ours.size() || net.names.size() != ours.size()) {
			return "espera " + net.inputs() + " entradas con " + net.names.size() + " nombres; la del blaze tiene " + ours.size();
		}
		for (int i = 0; i < ours.size(); i++) {
			if (!ours.get(i).equals(net.names.get(i))) {
				return "la entrada " + i + " es '" + net.names.get(i) + "' y el mod da '" + ours.get(i) + "'";
			}
		}
		if (net.outputs() != OUTPUTS) {
			return "da " + net.outputs() + " salidas y la del blaze tiene " + OUTPUTS;
		}
		return null;
	}

	/**
	 * What the network may not choose right now (the contract's "mascara"): rising under a ceiling or too high, coming
	 * down onto the ground, charging while it cannot or is charged already, firing without a full charge or without
	 * the player in sight within 16. Index 0 of each head is never shut.
	 */
	public static boolean[] mask(Mob mob, MobMind mind, Player target) {
		boolean[] mask = new boolean[OUTPUTS];
		java.util.Arrays.fill(mask, true);
		mask[VERTICAL_AT + BlazeDecision.RISE] = BlazePilot.canRise(mob);
		mask[VERTICAL_AT + BlazeDecision.DESCEND] = BlazePilot.canDescend(mob);
		boolean bursting = mind.draw > 0;
		mask[FIRE_AT + BlazeDecision.CHARGE] = !bursting && mind.cooldown <= 0 && mind.blazeCharge < BlazePilot.CHARGE_TICKS;
		mask[FIRE_AT + BlazeDecision.FIRE] = !bursting && BlazePilot.canFire(mob, mind, target);
		return mask;
	}

	/** Samples every head on its own, with the temperature and its part of the mask, as the mobs' heads are (never the maximum). */
	public static BlazeDecision sample(float[] logits, double temperature, RandomSource random, boolean[] mask) {
		int move = categorical(logits, MOVE_AT, MOVES, temperature, random, mask);
		int vertical = categorical(logits, VERTICAL_AT, 3, temperature, random, mask);
		int fire = categorical(logits, FIRE_AT, 3, temperature, random, mask);
		int range = categorical(logits, RANGE_AT, 3, temperature, random, mask);
		int tactic = categorical(logits, TACTIC_AT, TACTICS, temperature, random, mask);
		return new BlazeDecision(move, vertical, fire, range, BlazeDecision.Plan.values()[tactic]);
	}

	/** One softmax head over logits [at, at + n); the first of the head is always allowed. Returns the position in the head. */
	private static int categorical(float[] logits, int at, int n, double temperature, RandomSource random, boolean[] mask) {
		double t = Math.max(0.05, temperature);
		double max = Double.NEGATIVE_INFINITY;
		for (int k = 0; k < n; k++) {
			if (k == 0 || mask == null || mask[at + k]) {
				max = Math.max(max, logits[at + k] / t);
			}
		}
		double[] p = new double[n];
		double sum = 0.0;
		for (int k = 0; k < n; k++) {
			p[k] = k == 0 || mask == null || mask[at + k] ? Math.exp(logits[at + k] / t - max) : 0.0;
			sum += p[k];
		}
		double roll = random.nextDouble() * sum;
		for (int k = 0; k < n; k++) {
			roll -= p[k];
			if (roll <= 0.0) {
				return k;
			}
		}
		return 0;
	}
}
