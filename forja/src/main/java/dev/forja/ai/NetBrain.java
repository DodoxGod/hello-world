package dev.forja.ai;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.RandomSource;

/**
 * A network trained in the simulator, in the red_mob_v1 format (and, when it comes, v2):
 *
 * <pre>
 * h1 = tanh(x·w1 + b1); h2 = tanh(h1·w2 + b2); mem = GRUCell(h2, mem)  (gates r, z, n as torch.GRUCell)
 * logits = [h2, mem]·w_out + b_out
 * mover ~ softmax(logits[0..8] / T); saltar ~ Bernoulli(σ(logits[9] / T)); usar ~ Bernoulli(σ(logits[10] / T))
 * </pre>
 *
 * Sampled, never the most likely: that is how it was trained, and taking the maximum made the real bot
 * stop jumping and landing crits. The memory starts at zero when the mob appears.
 */
public final class NetBrain {
	public final String group;
	public final int ticksPerDecision;
	/**
	 * Which yo_arma_alcance the network was trained on (contract v3.1, docs/red_mob_v3_1.md): 1, the contract's
	 * body-plus-attributes; 2 ("alcance_v": 2 in its file), the full reach the monster really strikes with.
	 */
	public final int reachVersion;
	public final List<String> names;
	/**
	 * The file's "formato" ("red_mob_v2", "red_mob_v3", "red_mob_v4"...), or null when it has none (the first v1
	 * files). MobAi.check goes by this before the input names: v4's first 280 names are v3b's, but not their meaning.
	 */
	public final String format;
	private final float[][] w1;
	private final float[] b1;
	private final float[][] w2;
	private final float[] b2;
	private final float[][] gruIh;
	private final float[][] gruHh;
	private final float[] gruBih;
	private final float[] gruBhh;
	/**
	 * The same GRU weights laid out by input rather than by gate, so the gate sums can run the way
	 * {@link #dense} does: each gate still adds its terms in the same order, so the result is the same to
	 * the last bit, but the gates no longer wait on each other. Null when the file's rows are uneven.
	 */
	private final float[][] gruIhByInput;
	private final float[][] gruHhByInput;
	private final float[][] wOut;
	private final float[] bOut;
	/** Mean and spread per input, when the file carries them (v2); null otherwise. */
	private final float[] mean;
	private final float[] std;
	public final int memory;

	private NetBrain(JsonObject json) {
		this.group = json.get("grupo").getAsString();
		this.ticksPerDecision = json.has("ticks_por_decision") ? json.get("ticks_por_decision").getAsInt() : 2;
		this.reachVersion = json.has("alcance_v") ? json.get("alcance_v").getAsInt() : 1;
		this.format = json.has("formato") ? json.get("formato").getAsString() : null;
		this.names = new ArrayList<>();
		for (var name : json.getAsJsonArray("nombres_obs")) {
			this.names.add(name.getAsString());
		}
		this.w1 = matrix(json, "w1");
		this.b1 = vector(json, "b1");
		this.w2 = matrix(json, "w2");
		this.b2 = vector(json, "b2");
		this.gruIh = matrix(json, "gru_ih");
		this.gruHh = matrix(json, "gru_hh");
		this.gruBih = vector(json, "gru_bih");
		this.gruBhh = vector(json, "gru_bhh");
		this.gruIhByInput = byInput(this.gruIh);
		this.gruHhByInput = byInput(this.gruHh);
		this.wOut = matrix(json, "w_out");
		this.bOut = vector(json, "b_out");
		this.mean = json.has("media") ? vector(json, "media") : null;
		this.std = json.has("desviacion") ? vector(json, "desviacion") : null;
		this.memory = this.gruHh[0].length;
	}

	/** A network from its JSON, already parsed (tests, tools). */
	public static NetBrain fromJson(JsonObject json) {
		return new NetBrain(json);
	}

	public static NetBrain load(Path file) throws java.io.IOException {
		try (Reader reader = Files.newBufferedReader(file)) {
			return new NetBrain(JsonParser.parseReader(reader).getAsJsonObject());
		}
	}

	public int inputs() {
		return this.w1.length;
	}

	/** Runs the network; {@code memory} is updated in place. Returns the raw logits. */
	public float[] forward(float[] input, float[] memory) {
		int n = this.w1.length;
		float[] x = new float[n];
		for (int i = 0; i < n; i++) {
			float v = i < input.length ? input[i] : 0.0F;
			if (this.mean != null && this.std != null) {
				v = (v - this.mean[i]) / Math.max(1.0E-6F, this.std[i]);
			}
			x[i] = v;
		}
		float[] h1 = dense(x, this.w1, this.b1, true);
		float[] h2 = dense(h1, this.w2, this.b2, true);
		int h = this.memory;
		float[] gi;
		float[] gh;
		if (this.gruIhByInput != null && this.gruHhByInput != null && this.gruIh.length >= 3 * h && this.gruHh.length >= 3 * h
			&& this.gruIhByInput.length >= h2.length && this.gruHhByInput.length >= h) {
			gi = gates(this.gruBih, this.gruIhByInput, h2, h2.length, 3 * h);
			gh = gates(this.gruBhh, this.gruHhByInput, memory, h, 3 * h);
		} else {
			gi = new float[3 * h];
			gh = new float[3 * h];
			for (int g = 0; g < 3 * h; g++) {
				float si = this.gruBih[g];
				float[] rowI = this.gruIh[g];
				for (int k = 0; k < h2.length; k++) {
					si += rowI[k] * h2[k];
				}
				gi[g] = si;
				float sh = this.gruBhh[g];
				float[] rowH = this.gruHh[g];
				for (int k = 0; k < h; k++) {
					sh += rowH[k] * memory[k];
				}
				gh[g] = sh;
			}
		}
		float[] next = new float[h];
		for (int k = 0; k < h; k++) {
			float r = sigmoid(gi[k] + gh[k]);
			float z = sigmoid(gi[h + k] + gh[h + k]);
			float c = (float) Math.tanh(gi[2 * h + k] + r * gh[2 * h + k]);
			next[k] = (1.0F - z) * c + z * memory[k];
		}
		System.arraycopy(next, 0, memory, 0, h);
		float[] f = new float[h2.length + h];
		System.arraycopy(h2, 0, f, 0, h2.length);
		System.arraycopy(memory, 0, f, h2.length, h);
		return dense(f, this.wOut, this.bOut, false);
	}

	/** Where each head's logits start in a v2 network's output, and how many it has. */
	public static final int V1_OUTPUTS = 11;
	public static final int TACTIC_AT = 11;
	public static final int SPECIAL_AT = TACTIC_AT + 9;
	public static final int DEFENSE_AT = SPECIAL_AT + 5;
	public static final int FEINT_AT = DEFENSE_AT + 3;
	public static final int V2_OUTPUTS = FEINT_AT + 1;
	/**
	 * red_mob_v3: the four new tactics (CEBO, RELEVO, OCULTARSE, EMPUJAR) come after everything v2 has, so no
	 * head of v2 moves; the tactic is then one choice among the nine at TACTIC_AT and these four.
	 */
	public static final int NEW_TACTICS_AT = V2_OUTPUTS;
	public static final int V3_OUTPUTS = NEW_TACTICS_AT + 4;
	/** v3, widened at the end (2026-09-27): correr, a Bernoulli like fintar. A 33-output network does without it. */
	public static final int RUN_AT = V3_OUTPUTS;
	public static final int V3_RUN_OUTPUTS = RUN_AT + 1;

	/** The run head, sampled like the feint; false for a network that has none. */
	public static boolean sampleRun(float[] logits, double temperature, RandomSource random, boolean[] mask) {
		if (logits.length <= RUN_AT || mask != null && mask.length > RUN_AT && !mask[RUN_AT]) {
			return false;
		}
		return random.nextDouble() < sigmoid((float) (logits[RUN_AT] / Math.max(0.05, temperature)));
	}

	/** The logits the tactic head is read from, in the order of {@link Tactic}. */
	static int[] tacticLogits(int outputs) {
		int n = outputs >= V3_OUTPUTS ? Tactic.values().length : Tactic.V2_COUNT;
		int[] at = new int[n];
		for (int k = 0; k < n; k++) {
			at[k] = k < Tactic.V2_COUNT ? TACTIC_AT + k : NEW_TACTICS_AT + (k - Tactic.V2_COUNT);
		}
		return at;
	}

	public int outputs() {
		return this.bOut.length;
	}

	/**
	 * Samples a decision. A v1 network (11 outputs) gives the three heads of the simulator and LIBRE; a v2
	 * network (29) adds tactica (9), especial (5), defensa (3) and fintar (1), each sampled on its own with
	 * the same temperature and its part of the mask.
	 */
	public static Decision sample(float[] logits, double temperature, RandomSource random, boolean[] mask) {
		Decision base = sampleV1(logits, temperature, random, mask);
		if (logits.length < V2_OUTPUTS) {
			return base;
		}
		int tactic = categorical(logits, tacticLogits(logits.length), temperature, random, mask);
		int special = categorical(logits, SPECIAL_AT, 5, temperature, random, mask);
		int defense = categorical(logits, DEFENSE_AT, 3, temperature, random, mask);
		boolean feint = (mask == null || mask.length <= FEINT_AT || mask[FEINT_AT])
			&& random.nextDouble() < sigmoid((float) (logits[FEINT_AT] / Math.max(0.05, temperature)));
		return new Decision(base.move(), base.jump(), base.use(), Tactic.of(tactic), special, defense, feint);
	}

	/** One categorical head: logits [at, at + n), masked where the mask says so (never all masked: 0 stays). */
	private static int categorical(float[] logits, int at, int n, double temperature, RandomSource random, boolean[] mask) {
		int[] indices = new int[n];
		for (int k = 0; k < n; k++) {
			indices[k] = at + k;
		}
		return categorical(logits, indices, temperature, random, mask);
	}

	/** One categorical head over the logits at {@code indices}; the answer is the position in that list. */
	private static int categorical(float[] logits, int[] indices, double temperature, RandomSource random, boolean[] mask) {
		int n = indices.length;
		double t = Math.max(0.05, temperature);
		double max = Double.NEGATIVE_INFINITY;
		for (int k = 0; k < n; k++) {
			if (allowed(mask, indices[k]) || k == 0) {
				max = Math.max(max, logits[indices[k]] / t);
			}
		}
		double[] p = new double[n];
		double sum = 0.0;
		for (int k = 0; k < n; k++) {
			p[k] = allowed(mask, indices[k]) || k == 0 ? Math.exp(logits[indices[k]] / t - max) : 0.0;
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

	private static boolean allowed(boolean[] mask, int index) {
		return mask == null || index >= mask.length || mask[index];
	}

	/** The simulator's three heads. */
	private static Decision sampleV1(float[] logits, double temperature, RandomSource random, boolean[] mask) {
		double t = Math.max(0.05, temperature);
		double max = Double.NEGATIVE_INFINITY;
		for (int k = 0; k < 9; k++) {
			if (mask == null || mask[k]) {
				max = Math.max(max, logits[k] / t);
			}
		}
		double[] p = new double[9];
		double sum = 0.0;
		for (int k = 0; k < 9; k++) {
			p[k] = mask == null || mask[k] ? Math.exp(logits[k] / t - max) : 0.0;
			sum += p[k];
		}
		double roll = random.nextDouble() * sum;
		int move = 0;
		for (int k = 0; k < 9; k++) {
			roll -= p[k];
			if (roll <= 0.0) {
				move = k;
				break;
			}
		}
		boolean jump = (mask == null || mask[9]) && random.nextDouble() < sigmoid((float) (logits[9] / t));
		boolean use = (mask == null || mask[10]) && random.nextDouble() < sigmoid((float) (logits[10] / t));
		return new Decision(move, jump, use, Tactic.LIBRE, 0, 0, false);
	}

	private static float[] dense(float[] x, float[][] w, float[] b, boolean tanh) {
		int out = b.length;
		float[] y = b.clone();
		for (int i = 0; i < x.length; i++) {
			float xi = x[i];
			if (xi == 0.0F) {
				continue;
			}
			float[] row = w[i];
			for (int j = 0; j < out; j++) {
				y[j] += xi * row[j];
			}
		}
		if (tanh) {
			for (int j = 0; j < out; j++) {
				y[j] = (float) Math.tanh(y[j]);
			}
		}
		return y;
	}

	/**
	 * {@code n} gate sums, bias[g] + Σ_k weight[g][k] · x[k] over the first {@code count} inputs, with the
	 * weights laid out by input. Every gate adds its terms in k order, exactly as a gate-by-gate loop does,
	 * so the floats come out identical; the inner loop is over independent gates, which the JIT vectorises.
	 */
	private static float[] gates(float[] bias, float[][] byInput, float[] x, int count, int n) {
		float[] out = new float[n];
		for (int g = 0; g < n; g++) {
			out[g] = bias[g];
		}
		for (int k = 0; k < count; k++) {
			float v = x[k];
			float[] row = byInput[k];
			for (int g = 0; g < n; g++) {
				out[g] += row[g] * v;
			}
		}
		return out;
	}

	/** A matrix by columns (m[i][j] at [j][i]), or null when its rows are not all the same length. */
	private static float[][] byInput(float[][] m) {
		if (m.length == 0) {
			return null;
		}
		int cols = m[0].length;
		for (float[] row : m) {
			if (row.length != cols) {
				return null;
			}
		}
		float[][] t = new float[cols][m.length];
		for (int i = 0; i < m.length; i++) {
			for (int j = 0; j < cols; j++) {
				t[j][i] = m[i][j];
			}
		}
		return t;
	}

	private static float sigmoid(float v) {
		return (float) (1.0 / (1.0 + Math.exp(-v)));
	}

	private static float[][] matrix(JsonObject json, String key) {
		JsonArray rows = json.getAsJsonArray(key);
		float[][] m = new float[rows.size()][];
		for (int i = 0; i < m.length; i++) {
			JsonArray row = rows.get(i).getAsJsonArray();
			m[i] = new float[row.size()];
			for (int j = 0; j < m[i].length; j++) {
				m[i][j] = row.get(j).getAsFloat();
			}
		}
		return m;
	}

	private static float[] vector(JsonObject json, String key) {
		JsonArray values = json.getAsJsonArray(key);
		float[] v = new float[values.size()];
		for (int i = 0; i < v.length; i++) {
			v[i] = values.get(i).getAsFloat();
		}
		return v;
	}
}
