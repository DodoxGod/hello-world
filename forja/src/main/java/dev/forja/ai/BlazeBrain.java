package dev.forja.ai;

import java.util.Arrays;
import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The output side of a blaze network, red_blaze_v1 (docs/red_blaze_contrato.json, the simulator's contract): where
 * each head's logits sit, what the network may not choose right now ("mascara") and the sampling. The network itself
 * is a plain {@link NetBrain}: the same layers and forward pass as the mobs' v3, only the inputs and the heads differ.
 *
 * <pre>
 * mover     softmax  0..8   (quieto, adelante, adelante_derecha ... adelante_izquierda)
 * vertical  softmax  9..11  (mantener, subir, bajar)
 * usar      σ        12
 * adelanto  softmax  13..16 (0, 0.5, 1, 1.5)
 * retirarse σ        17
 * </pre>
 */
public final class BlazeBrain {
	public static final int MOVE_AT = 0;
	public static final int MOVES = 9;
	public static final int VERTICAL_AT = 9;
	public static final int VERTICALS = 3;
	public static final int USE_AT = 12;
	public static final int LEAD_AT = 13;
	public static final int LEADS = 4;
	public static final int RETREAT_AT = 17;
	public static final int OUTPUTS = 18;

	private BlazeBrain() {
	}

	/**
	 * Why a network cannot drive a blaze, or null: its formato must be red_blaze_v1, its inputs exactly ObsBlaze's 324
	 * in the same order, and its outputs the contract's 18. Anything else (a v3 or v4 mob network among them) is refused,
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
	 * The contract's mask: rise only under the top of its band (height &lt; 5 − 0.05), descend only over the bottom
	 * (&gt; 2 + 0.05), usar only when the burst can start now (= rafaga_lista). Index 0 of each softmax is never shut;
	 * a shut Bernoulli comes out 0.
	 */
	public static boolean[] mask(Mob mob, MobMind mind, Player target) {
		boolean[] mask = new boolean[OUTPUTS];
		Arrays.fill(mask, true);
		double height = ObsBlaze.heightOverGround(mob);
		mask[VERTICAL_AT + BlazeDecision.RISE] = height < BlazePilot.MAX_HEIGHT - 0.05;
		mask[VERTICAL_AT + BlazeDecision.DESCEND] = height > BlazePilot.MIN_HEIGHT + 0.05;
		mask[USE_AT] = BlazePilot.ready(mob, mind, target);
		return mask;
	}

	/** Samples every head on its own, with the temperature and its part of the mask (never the maximum: as it was trained). */
	public static BlazeDecision sample(float[] logits, double temperature, RandomSource random, boolean[] mask) {
		double t = Math.max(0.05, temperature);
		int move = categorical(logits, MOVE_AT, MOVES, t, random, mask);
		int vertical = categorical(logits, VERTICAL_AT, VERTICALS, t, random, mask);
		boolean use = bernoulli(logits, USE_AT, t, random, mask);
		int lead = categorical(logits, LEAD_AT, LEADS, t, random, mask);
		boolean retreat = bernoulli(logits, RETREAT_AT, t, random, mask);
		return new BlazeDecision(move, vertical, use, lead, retreat);
	}

	private static boolean bernoulli(float[] logits, int at, double t, RandomSource random, boolean[] mask) {
		if (mask != null && !mask[at]) {
			return false;
		}
		return random.nextDouble() < 1.0 / (1.0 + Math.exp(-logits[at] / t));
	}

	/** One softmax head over logits [at, at + n); the first of the head is always allowed. Returns the position in the head. */
	private static int categorical(float[] logits, int at, int n, double t, RandomSource random, boolean[] mask) {
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
			if (roll <= 0.0 && p[k] > 0.0) {
				return k;
			}
		}
		return 0;
	}
}
