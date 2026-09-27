package dev.forja.test;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsM1;
import dev.forja.ai.Personality;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The speed-ups of docs/RENDIMIENTO.md change nothing a mob decides. Each one is checked here against a
 * copy of the code it replaced, kept below exactly as it was: same floats to the last bit, same lists in
 * the same order, same ground heights.
 */
public class OptimizationGameTests {
	/** Networks trained on this machine, when they are here: the v1 ones the other tests read, and FORJA_REDES. */
	private static final Path TRAINED = Path.of("E:/IA/agente/minecraft/combate/mobs");

	// --- The network -------------------------------------------------------------------------------------

	/** A network of the trained ones' size, with random weights; with or without the input scaling. */
	private static JsonObject randomNet(long seed, int inputs, int hidden, int memory, int outputs, boolean scaled) {
		Random random = new Random(seed);
		JsonObject json = new JsonObject();
		json.addProperty("formato", "red_mob_v2");
		json.addProperty("grupo", "cuerpo");
		json.addProperty("ticks_por_decision", 2);
		JsonArray names = new JsonArray();
		for (int i = 0; i < inputs; i++) {
			names.add("x" + i);
		}
		json.add("nombres_obs", names);
		json.add("w1", matrix(random, inputs, hidden));
		json.add("b1", vector(random, hidden));
		json.add("w2", matrix(random, hidden, hidden));
		json.add("b2", vector(random, hidden));
		json.add("gru_ih", matrix(random, 3 * memory, hidden));
		json.add("gru_hh", matrix(random, 3 * memory, memory));
		json.add("gru_bih", vector(random, 3 * memory));
		json.add("gru_bhh", vector(random, 3 * memory));
		json.add("w_out", matrix(random, hidden + memory, outputs));
		json.add("b_out", vector(random, outputs));
		if (scaled) {
			json.add("media", vector(random, inputs));
			JsonArray spread = new JsonArray();
			for (int i = 0; i < inputs; i++) {
				spread.add(i % 17 == 0 ? 0.0F : 0.1F + random.nextFloat());
			}
			json.add("desviacion", spread);
		}
		return json;
	}

	private static JsonArray vector(Random random, int n) {
		JsonArray a = new JsonArray();
		for (int i = 0; i < n; i++) {
			a.add((float) (random.nextGaussian() * 0.3));
		}
		return a;
	}

	private static JsonArray matrix(Random random, int rows, int cols) {
		JsonArray a = new JsonArray();
		for (int i = 0; i < rows; i++) {
			a.add(vector(random, cols));
		}
		return a;
	}

	/** An observation as the mod makes them: many exact zeros, some negative zeros, ones, and the odd large value. */
	private static float[] input(Random random, int n) {
		float[] x = new float[n];
		for (int i = 0; i < n; i++) {
			int kind = random.nextInt(10);
			x[i] = switch (kind) {
				case 0, 1, 2 -> 0.0F;
				case 3 -> -0.0F;
				case 4 -> 1.0F;
				case 5 -> (float) (random.nextGaussian() * 40.0);
				default -> (float) random.nextGaussian();
			};
		}
		return x;
	}

	/** Runs both brains side by side for a few hundred decisions, memory carried over; any bit apart fails. */
	private static void sameForward(GameTestHelper helper, JsonObject json, String label) {
		NetBrain net = NetBrain.fromJson(json);
		ReferenceNet reference = new ReferenceNet(json);
		float[] memory = new float[net.memory];
		float[] memoryRef = new float[net.memory];
		Random random = new Random(label.hashCode());
		for (int step = 0; step < 300; step++) {
			// Now and then an observation shorter or longer than the network's inputs, as a v1 net fed v2 inputs.
			int n = step % 50 == 7 ? net.inputs() - 5 : step % 50 == 8 ? net.inputs() + 5 : net.inputs();
			float[] x = input(random, Math.max(1, n));
			float[] logits = net.forward(x, memory);
			float[] logitsRef = reference.forward(x, memoryRef);
			helper.assertTrue(Arrays.equals(logits, logitsRef), label + ": las salidas difieren en el paso " + step);
			helper.assertTrue(Arrays.equals(memory, memoryRef), label + ": la memoria difiere en el paso " + step);
		}
	}

	/** The network's answers are the same to the last bit after the GRU gates were laid out by input. */
	@GameTest
	public void networkForwardIsBitIdentical(GameTestHelper helper) throws java.io.IOException {
		sameForward(helper, randomNet(11L, 200, 128, 64, 29, false), "aleatoria 200-128-64-29");
		sameForward(helper, randomNet(12L, 200, 128, 64, 29, true), "aleatoria con escala");
		sameForward(helper, randomNet(13L, 102, 16, 8, 11, false), "aleatoria v1 pequeña");
		List<Path> files = new ArrayList<>();
		String folder = System.getenv("FORJA_REDES");
		for (Path dir : new Path[] {TRAINED, folder == null || folder.isBlank() ? null : Path.of(folder.trim())}) {
			if (dir != null && Files.isDirectory(dir)) {
				try (var list = Files.list(dir)) {
					list.filter(p -> p.getFileName().toString().matches("red_.*\\.json")).sorted().forEach(files::add);
				}
			}
		}
		for (Path file : files) {
			try (Reader reader = Files.newBufferedReader(file)) {
				sameForward(helper, JsonParser.parseReader(reader).getAsJsonObject(), file.toString());
			}
		}
		helper.succeed();
	}

	/** NetBrain.forward as it was before the gates were laid out by input, word for word. */
	private static final class ReferenceNet {
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
		private final float[] mean;
		private final float[] std;
		private final int memory;

		ReferenceNet(JsonObject json) {
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

		float[] forward(float[] input, float[] memory) {
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

	// --- The allies ---------------------------------------------------------------------------------------

	/** ObsM1.allies as it was: the distance worked out inside the comparator. */
	private static List<Mob> referenceAllies(Mob mob) {
		List<Mob> found = new ArrayList<>(mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(32.0),
			other -> other != mob && other.isAlive() && other instanceof Enemy));
		found.sort(Comparator.comparingDouble(other -> {
			double ox = other.getX() - mob.getX();
			double oz = other.getZ() - mob.getZ();
			return ox * ox + oz * oz;
		}));
		return found;
	}

	/** A crowd with ties in it (a ring of equal distances, two on one spot): the same allies in the same order. */
	@GameTest(maxTicks = 40)
	public void alliesKeepTheirOrder(GameTestHelper helper) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		List<Mob> crowd = new ArrayList<>();
		Vec3 middle = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
			zombie.setNoAi(true);
			zombie.setPos(middle.x + Math.cos(a) * 3.0, middle.y, middle.z + Math.sin(a) * 3.0);
			crowd.add(zombie);
		}
		for (int i = 0; i < 6; i++) {
			var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1 + i, 1, i % 2 == 0 ? 1 : 7));
			skeleton.setNoAi(true);
			crowd.add(skeleton);
		}
		Zombie twin = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(7, 1, 4));
		twin.setNoAi(true);
		Zombie other = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(7, 1, 4));
		other.setNoAi(true);
		crowd.add(twin);
		crowd.add(other);
		Zombie centre = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
		centre.setNoAi(true);
		centre.setPos(middle.x, middle.y, middle.z);
		crowd.add(centre);
		// A crowd big enough to be sorted in pieces and merged, with every fifth one on another's exact spot.
		Random random = new Random(5L);
		for (int i = 0; i < 30; i++) {
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
			zombie.setNoAi(true);
			Mob copied = i % 5 == 4 ? crowd.get(crowd.size() - 3) : null;
			if (copied != null) {
				zombie.setPos(copied.getX(), copied.getY(), copied.getZ());
			} else {
				zombie.setPos(middle.x + random.nextDouble() * 12.0 - 6.0, middle.y, middle.z + random.nextDouble() * 12.0 - 6.0);
			}
			crowd.add(zombie);
		}
		Runnable same = () -> {
			for (Mob mob : crowd) {
				List<Mob> now = ObsM1.allies(mob);
				List<Mob> before = referenceAllies(mob);
				helper.assertTrue(now.equals(before), "aliados distintos para " + mob + ": " + now + " vs " + before);
			}
		};
		// At once, with the ties exactly as placed, and again once they have pushed each other about.
		same.run();
		// At least: the monsters of the tests next door are within 32 blocks too.
		helper.assertTrue(ObsM1.allies(centre).size() >= crowd.size() - 1, "el del centro ve a todos: " + ObsM1.allies(centre).size());
		helper.runAfterDelay(2, () -> {
			same.run();
			crowd.forEach(Mob::discard);
			helper.succeed();
		});
	}

	// --- The ground ---------------------------------------------------------------------------------------

	/** ObsM1.ground as it was: a fresh position and Level.getBlockState for every block. */
	private static double referenceGround(Level level, double x, double z, double y, double halfWidth) {
		double best = y - 8.0;
		int i0 = (int) Math.floor(x - halfWidth);
		int i1 = halfWidth <= 0.0 ? i0 : (int) Math.floor(x + halfWidth - 1.0E-6);
		int j0 = (int) Math.floor(z - halfWidth);
		int j1 = halfWidth <= 0.0 ? j0 : (int) Math.floor(z + halfWidth - 1.0E-6);
		for (int i = i0; i <= i1; i++) {
			for (int j = j0; j <= j1; j++) {
				for (int k = (int) Math.floor(y) + 4; k >= (int) Math.floor(y) - 8; k--) {
					BlockPos pos = new BlockPos(i, k, j);
					var shape = level.getBlockState(pos).getCollisionShape(level, pos);
					if (!shape.isEmpty()) {
						double top = k + shape.max(net.minecraft.core.Direction.Axis.Y);
						if (top > best) {
							best = top;
						}
						break;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Uneven ground (slabs, stairs, snow, carpet, a fence, mud, honey, a ledge, a roof, a hole) read from
	 * every angle and height, and at the top and bottom of the world: the same heights as before.
	 */
	@GameTest(maxTicks = 40)
	public void groundReadsTheSame(GameTestHelper helper) {
		var level = helper.getLevel();
		var blocks = new net.minecraft.world.level.block.Block[] {Blocks.STONE, Blocks.OAK_SLAB, Blocks.OAK_STAIRS, Blocks.SNOW,
			Blocks.MOSS_CARPET, Blocks.OAK_FENCE, Blocks.MUD, Blocks.HONEY_BLOCK, Blocks.AIR, Blocks.COBWEB, Blocks.SCAFFOLDING, Blocks.SOUL_SAND};
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				int height = (x * 3 + z * 5) % 4;
				for (int y = 0; y <= height; y++) {
					helper.setBlock(new BlockPos(x, y, z), y == height ? blocks[(x + z * 3) % blocks.length] : Blocks.STONE);
				}
			}
		}
		helper.setBlock(new BlockPos(3, 5, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 0, 5), Blocks.AIR);
		Vec3 origin = helper.absoluteVec(Vec3.ZERO);
		int checked = 0;
		for (double dx = -0.5; dx <= 8.5; dx += 0.37) {
			for (double dz = -0.5; dz <= 8.5; dz += 0.41) {
				for (double dy : new double[] {0.0, 1.0, 1.5, 2.25, 4.0, 9.0}) {
					for (double half : new double[] {0.0, 0.2, 0.3, 0.7}) {
						double x = origin.x + dx;
						double z = origin.z + dz;
						double y = origin.y + dy;
						double now = ObsM1.ground(level, x, z, y, half);
						double before = referenceGround(level, x, z, y, half);
						helper.assertTrue(Double.compare(now, before) == 0, "suelo distinto en " + x + ", " + y + ", " + z + ": " + now + " vs " + before);
						checked++;
					}
				}
			}
		}
		// The ends of the world, where the level answers with the air of the void.
		for (double y : new double[] {level.getMinY() - 2.0, level.getMinY() + 3.0, level.getMaxY() - 2.0, level.getMaxY() + 3.0}) {
			double now = ObsM1.ground(level, origin.x + 2.5, origin.z + 2.5, y, 0.3);
			helper.assertTrue(Double.compare(now, referenceGround(level, origin.x + 2.5, origin.z + 2.5, y, 0.3)) == 0, "suelo distinto a la altura " + y);
			checked++;
		}
		helper.assertTrue(checked > 3000, "comprobados " + checked);
		helper.succeed();
	}

	// --- Traits -----------------------------------------------------------------------------------------

	/** Personality.trait as it was: the tag names built on every call. */
	private static Personality.Trait referenceTrait(Mob mob) {
		for (Personality.Trait trait : Personality.Trait.values()) {
			if (mob.entityTags().contains(Personality.TRAIT_TAG + trait.name().toLowerCase(java.util.Locale.ROOT))) {
				return trait;
			}
		}
		return Personality.Trait.AGRESIVO;
	}

	/** Every trait, none, and two at once (the first in the enum's order wins, as before). */
	@GameTest
	public void traitsReadTheSame(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		List<String> all = new ArrayList<>();
		for (Personality.Trait trait : Personality.Trait.values()) {
			all.add(Personality.TRAIT_TAG + trait.name().toLowerCase(java.util.Locale.ROOT));
		}
		all.forEach(zombie::removeTag);
		helper.assertTrue(Personality.trait(zombie) == referenceTrait(zombie), "sin rasgo");
		for (String tag : all) {
			zombie.addTag(tag);
			helper.assertTrue(Personality.trait(zombie) == referenceTrait(zombie), "con " + tag);
			zombie.removeTag(tag);
		}
		zombie.addTag(all.get(3));
		zombie.addTag(all.get(1));
		helper.assertTrue(Personality.trait(zombie) == referenceTrait(zombie), "con dos rasgos");
		zombie.discard();
		helper.succeed();
	}
}
