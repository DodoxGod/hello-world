package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.forja.Forja;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobKit;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsV4;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the monsters' AI costs with v4 networks of the contract's size (468 → 128 → 128 → GRU 96 → 53) and everything
 * M2/M3 brought in play: 30 mixed monsters (zombies, some with shields and kits, skeletons, spiders, creepers) round a
 * player walking a slow circle, torches about and things on the floor. The budget (Andy, 2026-09-29): the AI of 30
 * mobs at most 2.5 ms a tick, all of it: the brains' tick (observation, mask, network, the object and the bash) and the
 * goals that carry the decisions out. The same horde on the rules is measured after, for comparison. The numbers go to
 * the log ("rendimiento v4").
 */
public class RedV4PerfGameTests {
	/** The AI's budget for 30 mobs, ms per tick. */
	public static final double BUDGET_MS = 2.5;
	private static final int MOBS = 30;
	/**
	 * Ticks of the horde under v4 before the window opens. It was 60, and whether the test ran first in the suite
	 * (the batches come in a different order each run) or after everything else decided how much of the AI the JIT
	 * had compiled yet: the first pieces of a window came out slower than the last, and a cold run measured up to
	 * 2.7 ms where a warm one measured 1.9.
	 */
	private static final int WARMUP = 300;
	/** Ticks between the v4 window and the rules' one (the JIT is warm by then). */
	private static final int BETWEEN = 60;
	private static final int WINDOW = 200;

	private static final List<EntityType<? extends Mob>> KINDS = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER,
		EntityTypes.ZOMBIE, EntityTypes.CREEPER, EntityTypes.SKELETON);

	/** A v4 network of the contract's real size, with small random weights (seeded). */
	static NetBrain fullSizeV4(long seed) {
		List<String> names = ObsV4.names();
		Random random = new Random(seed);
		JsonObject json = new JsonObject();
		json.addProperty("formato", MobAi.V4_FORMAT);
		json.addProperty("grupo", "cuerpo");
		json.addProperty("ticks_por_decision", 2);
		json.addProperty("alcance_v", ObsV4.REACH_VERSION);
		JsonArray namesJson = new JsonArray();
		names.forEach(namesJson::add);
		json.add("nombres_obs", namesJson);
		json.add("w1", noise(random, names.size(), 128));
		json.add("b1", noise(random, 1, 128).get(0));
		json.add("w2", noise(random, 128, 128));
		json.add("b2", noise(random, 1, 128).get(0));
		json.add("gru_ih", noise(random, 3 * 96, 128));
		json.add("gru_hh", noise(random, 3 * 96, 96));
		json.add("gru_bih", noise(random, 1, 3 * 96).get(0));
		json.add("gru_bhh", noise(random, 1, 3 * 96).get(0));
		json.add("w_out", noise(random, 128 + 96, NetBrain.V4_OUTPUTS));
		json.add("b_out", noise(random, 1, NetBrain.V4_OUTPUTS).get(0));
		return NetBrain.fromJson(json);
	}

	private static JsonArray noise(Random random, int rows, int cols) {
		JsonArray m = new JsonArray();
		for (int i = 0; i < rows; i++) {
			JsonArray row = new JsonArray();
			for (int j = 0; j < cols; j++) {
				row.add((float) ((random.nextDouble() * 2.0 - 1.0) * 0.05));
			}
			m.add(row);
		}
		return m;
	}

	/** The AI's share of a window, ms per tick: the brains' tick and the goals that carry it out. */
	private static double aiMs(Perf.Result r) {
		return r.perTickMs(Perf.T.MOBAI_TICK) + r.perTickMs(Perf.T.TACTIC_TICK) + r.perTickMs(Perf.T.TACTIC_USE)
			+ r.perTickMs(Perf.T.SPECIAL_USE) + r.perTickMs(Perf.T.SPECIAL_TICK) + r.perTickMs(Perf.T.MOVEMENT);
	}

	private static String line(String name, Perf.Result r) {
		return String.format(Locale.ROOT, "%s: IA %.3f ms/tick (MobAi.tick %.3f: pensar %.3f, máscara %.3f, red %.3f; TacticGoal %.3f; especiales %.3f;"
				+ " movimiento %.3f), tick entero %.3f ms de media",
			name, aiMs(r), r.perTickMs(Perf.T.MOBAI_TICK), r.perTickMs(Perf.T.THINK), r.perTickMs(Perf.T.MASK), r.perTickMs(Perf.T.FORWARD),
			r.perTickMs(Perf.T.TACTIC_TICK) + r.perTickMs(Perf.T.TACTIC_USE), r.perTickMs(Perf.T.SPECIAL_USE) + r.perTickMs(Perf.T.SPECIAL_TICK),
			r.perTickMs(Perf.T.MOVEMENT), r.meanMs());
	}

	/**
	 * Puts a creeper's fuse out a tick before it would blow. The budget is for 30 mobs, and the horde's five creepers
	 * blew up and took others with them: a window began with 30 and ended with 19, at a pace that changed from run to
	 * run, and what was measured was the AI of however many were left (a whole horde costs more than the old number
	 * said). The creepers still think, close in and light their fuse; they only never go off.
	 */
	private static void defuse(net.minecraft.world.entity.monster.Creeper creeper) {
		try {
			java.lang.reflect.Field swell = net.minecraft.world.entity.monster.Creeper.class.getDeclaredField("swell");
			java.lang.reflect.Field maxSwell = net.minecraft.world.entity.monster.Creeper.class.getDeclaredField("maxSwell");
			swell.setAccessible(true);
			maxSwell.setAccessible(true);
			if (swell.getInt(creeper) >= maxSwell.getInt(creeper) - 2) {
				swell.setInt(creeper, 0);
			}
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Creeper.swell / maxSwell", failure);
		}
	}

	/** The timers {@link #aiMs} adds up. */
	private static final Perf.T[] AI = {Perf.T.MOBAI_TICK, Perf.T.TACTIC_TICK, Perf.T.TACTIC_USE, Perf.T.SPECIAL_USE, Perf.T.SPECIAL_TICK,
		Perf.T.MOVEMENT};

	/** The AI's time so far in the open window, ns. */
	private static long aiNanos() {
		long sum = 0L;
		for (Perf.T timer : AI) {
			sum += Perf.nanos(timer);
		}
		return sum;
	}

	/**
	 * The window cut in pieces of 20 ticks: each has ten decisions of every mob and two of the squad's updates, so
	 * each piece is the same work.
	 */
	static final int PIECES = 10;

	/**
	 * The calibration, timed once a tick on the server thread beside the AI: a fixed sum of products of the network's
	 * own shape (468 by 128, then 128 by 128, then 224 by 53), written here and not the mod's, so that a slower AI never
	 * slows the yardstick it is measured with. How long it takes against {@link #CALIBRATION_NS} is how much slower the
	 * machine is running than Andy's PC with nothing else on it.
	 */
	static final class Calibration {
		private final float[] w1 = new float[468 * 128];
		private final float[] w2 = new float[128 * 128];
		private final float[] w3 = new float[224 * 53];
		private final float[] in = new float[468];
		private final float[] h1 = new float[128];
		private final float[] h2 = new float[224];
		private final float[] out = new float[53];
		private float sink;

		Calibration(long seed) {
			Random random = new Random(seed);
			for (float[] a : new float[][] {this.w1, this.w2, this.w3, this.in}) {
				for (int i = 0; i < a.length; i++) {
					a[i] = (float) (random.nextDouble() * 2.0 - 1.0) * 0.05F;
				}
			}
		}

		/** One round, ns. */
		long run() {
			long t0 = System.nanoTime();
			for (int round = 0; round < 2; round++) {
				layer(this.w1, this.in, 468, this.h1, 128);
				layer(this.w2, this.h1, 128, this.h2, 128);
				System.arraycopy(this.h1, 0, this.h2, 128, 96);
				layer(this.w3, this.h2, 224, this.out, 53);
				this.in[round] += this.out[round] * 1.0E-3F;
			}
			this.sink += this.out[0];
			return System.nanoTime() - t0;
		}

		private static void layer(float[] w, float[] x, int n, float[] y, int m) {
			for (int j = 0; j < m; j++) {
				float acc = 0.0F;
				int row = j * n;
				for (int i = 0; i < n; i++) {
					acc += w[row + i] * x[i];
				}
				y[j] = acc > 0.0F ? acc : 0.0F;
			}
		}
	}

	/**
	 * One {@link Calibration#run} on Andy's PC (9800X3D) as fast as it went: the least seen over the test's runs while
	 * this was worked out (2026-09-30), with the machine training networks alongside, so if anything a little slow,
	 * which only makes the budget stricter.
	 */
	static final long CALIBRATION_NS = 65_000L;

	private static double median(double[] values) {
		double[] sorted = values.clone();
		java.util.Arrays.sort(sorted);
		int n = sorted.length;
		return n == 0 ? 0.0 : n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
	}

	/**
	 * The AI's cost that the budget is held to, ms per tick, robust to what else the machine is doing (networks
	 * training, other builds). Each piece's time is divided by how much slower than {@link #CALIBRATION_NS} the
	 * calibration ran in that piece (never by less than 1: on a quiet or faster machine it is the time as measured,
	 * and a slower machine than Andy's is judged by what his would take), and the budget holds the median piece, so a
	 * piece with a pause in it (a collection, the thread put aside) does not decide. Returns that, the median piece as
	 * measured and the median slowdown.
	 */
	static double[] steadyAiMs(List<Long> aiNanos, List<Long> calibrationNanos) {
		int per = Math.max(1, Math.min(aiNanos.size(), calibrationNanos.size()) / PIECES);
		double[] pieces = new double[PIECES];
		double[] raw = new double[PIECES];
		double[] slow = new double[PIECES];
		for (int p = 0; p < PIECES; p++) {
			long ai = 0L;
			double[] calibration = new double[per];
			for (int k = 0; k < per; k++) {
				ai += aiNanos.get(p * per + k);
				calibration[k] = calibrationNanos.get(p * per + k);
			}
			raw[p] = ai / 1.0E6 / per;
			slow[p] = Math.max(1.0, median(calibration) / CALIBRATION_NS);
			pieces[p] = raw[p] / slow[p];
		}
		return new double[] {median(pieces), median(raw), median(slow)};
	}

	@GameTest(environment = "forja-test:rendimiento_v4", padding = 24, maxTicks = WARMUP + BETWEEN + WINDOW * 2 + 40)
	public void v4AiOf30MobsFitsTheBudget(GameTestHelper helper) throws java.io.IOException {
		int size = 22;
		// every mob in a chunk that ticks entities, so all 30 are measured (TestChunks)
		TestChunks.force(helper, size);
		for (int x = -1; x <= size; x++) {
			for (int z = -1; z <= size; z++) {
				for (int y = 0; y <= 10; y++) {
					BlockPos at = new BlockPos(x, y, z);
					if (helper.getBlockState(at).is(Blocks.BARRIER)) {
						helper.setBlock(at, Blocks.AIR);
					}
				}
				if (x >= 0 && z >= 0 && x < size && z < size) {
					helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				} else {
					// A rim round the floor. It stands 24 blocks up (the padding), and without one the skeletons backing
					// off as they shoot and the spiders fell off the edge and died: the horde shrank as it was measured.
					for (int y = 0; y <= 3; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.BARRIER);
					}
				}
			}
		}
		// torches round the middle, and things on the floor
		for (int k = 0; k < 6; k++) {
			double a = k * Math.PI / 3.0;
			helper.setBlock(new BlockPos(11 + (int) Math.round(Math.cos(a) * 6), 1, 11 + (int) Math.round(Math.sin(a) * 6)), Blocks.TORCH);
		}
		for (int k = 0; k < 4; k++) {
			Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(3 + k * 5, 1, 2)));
			ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(k % 2 == 0 ? Items.IRON_SWORD : Items.BREAD));
			item.setDeltaMovement(Vec3.ZERO);
			helper.getLevel().addFreshEntity(item);
		}
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(11, 1, 11));
		Vec3 centre = helper.absoluteVec(new Vec3(11.5, 1.0, 11.5));
		NetBrain net = fullSizeV4(21L);
		List<Mob> mobs = new ArrayList<>();
		for (int i = 0; i < MOBS; i++) {
			double a = i * 2.0 * Math.PI / MOBS;
			double r = 8.0 + (i % 3);
			BlockPos at = new BlockPos((int) Math.floor(11.5 + Math.cos(a) * r), 1, (int) Math.floor(11.5 + Math.sin(a) * r));
			Mob mob = helper.spawn(KINDS.get(i % KINDS.size()), at);
			if (mob.getType() == EntityTypes.SKELETON) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			}
			if (mob.getType() == EntityTypes.ZOMBIE && i % 4 == 0) {
				mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				MobKit.add(mob, MobKit.Kind.HEAL, "minecraft:healing", 1);
				MobKit.add(mob, MobKit.Kind.WIND, "", 2);
				MobKit.add(mob, MobKit.Kind.SPLASH, "minecraft:slowness", 1);
			}
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			if (mob instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
				zombie.setBaby(false);
			}
			// The same fight every run as far as chance goes (what the AI costs depends on what it decides: a ring, a
			// path round, a shot): its random numbers, its brain's and its trait from fixed seeds.
			TestSeeds.seed(mob, 1000L + i);
			mobs.add(mob);
		}
		long[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			double a = tick[0] * 2.0 * Math.PI / 400.0;
			player.setPos(centre.x + Math.cos(a) * 3.0, centre.y, centre.z + Math.sin(a) * 3.0);
			player.setHealth(player.getMaxHealth());
			for (Mob mob : mobs) {
				if (mob.isAlive()) {
					mob.setHealth(mob.getMaxHealth());
					mob.setTarget(player);
					if (mob instanceof net.minecraft.world.entity.monster.Creeper creeper) {
						defuse(creeper);
					}
				}
			}
		});
		Path folder = Files.createTempDirectory("forja_rendimiento_v4");
		Perf.Result[] v4 = {null};
		// Tick by tick in the v4 window: the AI's time and one round of the calibration (run from the start, so the JIT
		// has compiled it by then).
		Calibration calibration = new Calibration(7L);
		List<Long> aiTicks = new ArrayList<>();
		List<Long> calibrationTicks = new ArrayList<>();
		// The fewest of the horde alive at any tick of the window: the budget is for all 30.
		int[] fewest = {MOBS};
		boolean[] recording = {false};
		long[] lastAi = {0L};
		helper.onEachTick(() -> {
			long round = calibration.run();
			if (recording[0] && Perf.measuring()) {
				long ai = aiNanos();
				aiTicks.add(ai - lastAi[0]);
				lastAi[0] = ai;
				calibrationTicks.add(round);
				fewest[0] = Math.min(fewest[0], (int) mobs.stream().filter(Mob::isAlive).count());
			}
		});
		helper.runAfterDelay(1, () -> {
			for (Mob mob : mobs) {
				MobMind mind = MobAi.mind(mob);
				if (mind != null && mob.getType() != EntityTypes.CREEPER) {
					mind.override = net;
				}
			}
		});
		helper.runAfterDelay(WARMUP, () -> {
			Perf.begin("v4_30");
			lastAi[0] = 0L;
			recording[0] = true;
		});
		helper.runAfterDelay(WARMUP + WINDOW, () -> {
			recording[0] = false;
			v4[0] = Perf.end(folder);
			for (Mob mob : mobs) {
				MobMind mind = MobAi.mind(mob);
				if (mind != null) {
					mind.override = null;
				}
			}
		});
		helper.runAfterDelay(WARMUP + BETWEEN + WINDOW, () -> Perf.begin("reglas_30"));
		helper.runAfterDelay(WARMUP + BETWEEN + WINDOW * 2, () -> {
			Perf.Result rules = Perf.end(folder);
			String report = line("rendimiento v4 (30 mobs, redes v4 del tamaño del contrato)", v4[0]) + "\n"
				+ line("rendimiento v4 (30 mobs, reglas)", rules);
			Forja.LOGGER.info(report);
			try {
				String gap = System.lineSeparator() + System.lineSeparator();
				Files.writeString(folder.resolve("resumen.txt"), report + gap + Perf.markdown(v4[0]) + gap + Perf.markdown(rules));
				Forja.LOGGER.info("rendimiento v4, informe completo: {}", folder.resolve("resumen.txt"));
			} catch (java.io.IOException ignored) {
				// the log has it
			}
			double[] steady = steadyAiMs(aiTicks, calibrationTicks);
			double ai = steady[0];
			long least = calibrationTicks.stream().mapToLong(Long::longValue).min().orElse(0L);
			Forja.LOGGER.info(String.format(Locale.ROOT, "rendimiento v4, lo que se compara con el tope: IA %.3f ms/tick (mediana de %d trozos"
				+ " de %d ticks: medida %.3f, máquina %.2f veces más lenta que la calibración de %d ns; la ventana entera daba %.3f)."
				+ " Calibración: la más rápida %d ns en %d ticks", ai, PIECES, aiTicks.size() / PIECES, steady[1], steady[2], CALIBRATION_NS,
				aiMs(v4[0]), least, calibrationTicks.size()));
			AABB box = new AABB(helper.absoluteVec(new Vec3(-4, -2, -4)), helper.absoluteVec(new Vec3(size + 4, 12, size + 4)));
			helper.getLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
			TestChunks.release(helper);
			helper.assertTrue(fewest[0] == MOBS, "se mide la horda entera: llegó a quedar con " + fewest[0] + " de " + MOBS);
			helper.assertTrue(ai <= BUDGET_MS, String.format(Locale.ROOT, "la IA de 30 mobs con redes v4 cuesta %.3f ms/tick (tope %.1f; medida %.3f,"
				+ " máquina %.2f veces más lenta que la calibración)", ai, BUDGET_MS, steady[1], steady[2]));
			helper.succeed();
		});
	}
}
