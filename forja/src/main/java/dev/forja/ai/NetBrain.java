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
	public final List<String> names;
	private final float[][] w1;
	private final float[] b1;
	private final float[][] w2;
	private final float[] b2;
	private final float[][] gruIh;
	private final float[][] gruHh;
	private final float[] gruBih;
	private final float[] gruBhh;
	private final float[][] wOut;
	private final float[] bOut;
	/** Mean and spread per input, when the file carries them (v2); null otherwise. */
	private final float[] mean;
	private final float[] std;
	public final int memory;

	private NetBrain(JsonObject json) {
		this.group = json.get("grupo").getAsString();
		this.ticksPerDecision = json.has("ticks_por_decision") ? json.get("ticks_por_decision").getAsInt() : 2;
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
		this.wOut = matrix(json, "w_out");
		this.bOut = vector(json, "b_out");
		this.mean = json.has("media") ? vector(json, "media") : null;
		this.std = json.has("desviacion") ? vector(json, "desviacion") : null;
		this.memory = this.gruHh[0].length;
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
		float[] gi = new float[3 * h];
		float[] gh = new float[3 * h];
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

	/** Samples a decision from the logits (v1: 11 of them; v2 adds Forja's heads after). */
	public static Decision sample(float[] logits, double temperature, RandomSource random, boolean[] mask) {
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
