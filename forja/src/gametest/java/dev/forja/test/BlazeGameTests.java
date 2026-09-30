package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.forja.Forja;
import dev.forja.ai.BlazeBrain;
import dev.forja.ai.BlazeDecision;
import dev.forja.ai.BlazePilot;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsBlaze;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The blaze's own network, red_blaze_v1 (docs/red_blaze_contrato.json, the simulator's contract): ObsBlaze gives the
 * contract's 324 names in order and finite values, the trained network (redes_entrenadas/red_blaze.json) loads and
 * passes the check, and with it a blaze hovers in its band, fires telegraphed bursts of three, leads a moving player
 * and keeps to its range, fast enough; without it the blaze is vanilla's.
 */
public class BlazeGameTests {
	/**
	 * The flight tests run in a batch of their own: they open their box and lay 29 × 29 of ground round it, and a blaze
	 * flies well outside the box, which the other tests' layout does not expect.
	 */
	private static final String ARENA = "forja-test:blaze";

	/** A file of the repo, found by walking up from the game's folder (the tests run a few folders down). */
	private static Path repoFile(String relative) {
		List<Path> starts = List.of(FabricLoader.getInstance().getGameDir().toAbsolutePath(), Path.of("").toAbsolutePath());
		for (Path start : starts) {
			for (Path at = start; at != null; at = at.getParent()) {
				for (String candidate : new String[] {relative, "forja/" + relative}) {
					Path file = at.resolve(candidate);
					if (Files.exists(file)) {
						return file;
					}
				}
			}
		}
		return null;
	}

	private static NetBrain trained;

	/** The trained network, redes_entrenadas/red_blaze.json, read once. */
	static NetBrain trainedNet(GameTestHelper helper) {
		if (trained == null) {
			Path file = repoFile("redes_entrenadas/red_blaze.json");
			helper.assertTrue(file != null, "no se encuentra redes_entrenadas/red_blaze.json subiendo desde " + FabricLoader.getInstance().getGameDir());
			try {
				trained = NetBrain.load(file);
			} catch (java.io.IOException failure) {
				throw new IllegalStateException(failure);
			}
		}
		return trained;
	}

	/** ObsBlaze.names() is the contract's nombres_obs in order; its formato, outputs and heads are the mod's. */
	@GameTest
	public void blazeNamesMatchTheContract(GameTestHelper helper) throws java.io.IOException {
		Path file = repoFile("docs/red_blaze_contrato.json");
		helper.assertTrue(file != null, "no se encuentra docs/red_blaze_contrato.json subiendo desde " + FabricLoader.getInstance().getGameDir());
		JsonObject contract = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		helper.assertTrue(ObsBlaze.FORMAT.equals(contract.get("formato").getAsString()), "formato: " + contract.get("formato"));
		helper.assertTrue(contract.get("version").getAsInt() == 1, "versión: " + contract.get("version"));
		List<String> theirs = new ArrayList<>();
		contract.getAsJsonArray("nombres_obs").forEach(name -> theirs.add(name.getAsString()));
		List<String> ours = ObsBlaze.names();
		helper.assertTrue(contract.get("n_obs").getAsInt() == theirs.size() && theirs.size() == ObsBlaze.SIZE,
			"n_obs debería ser " + ObsBlaze.SIZE + ": " + contract.get("n_obs") + " con " + theirs.size() + " nombres");
		helper.assertTrue(contract.get("n_obs_base").getAsInt() == ObsBlaze.BASE, "n_obs_base: " + contract.get("n_obs_base"));
		helper.assertTrue(ours.size() == theirs.size(), "ObsBlaze da " + ours.size() + " nombres y el contrato " + theirs.size());
		for (int i = 0; i < theirs.size(); i++) {
			helper.assertTrue(theirs.get(i).equals(ours.get(i)), "entrada " + i + ": el contrato dice '" + theirs.get(i) + "' y ObsBlaze '" + ours.get(i) + "'");
		}
		helper.assertTrue(contract.get("n_salidas").getAsInt() == BlazeBrain.OUTPUTS, "n_salidas: " + contract.get("n_salidas"));
		helper.assertTrue(contract.getAsJsonArray("salidas").size() == BlazeBrain.OUTPUTS, "salidas: " + contract.get("salidas"));
		helper.assertTrue(contract.get("ticks_por_decision").getAsInt() == 2, "ticks_por_decision: " + contract.get("ticks_por_decision"));
		Map<String, int[]> heads = new HashMap<>();
		heads.put("mover", new int[] {BlazeBrain.MOVE_AT, BlazeBrain.MOVES, 0});
		heads.put("vertical", new int[] {BlazeBrain.VERTICAL_AT, BlazeBrain.VERTICALS, 0});
		heads.put("usar", new int[] {BlazeBrain.USE_AT, 1, 1});
		heads.put("adelanto", new int[] {BlazeBrain.LEAD_AT, BlazeBrain.LEADS, 0});
		heads.put("retirarse", new int[] {BlazeBrain.RETREAT_AT, 1, 1});
		int seen = 0;
		for (var head : contract.getAsJsonArray("cabezas")) {
			JsonObject h = head.getAsJsonObject();
			String name = h.get("nombre").getAsString();
			int[] ours2 = heads.get(name);
			helper.assertTrue(ours2 != null, "cabeza desconocida: " + name);
			String type = h.get("tipo").getAsString();
			helper.assertTrue(ours2[0] == h.get("inicio").getAsInt() && ours2[1] == h.get("n").getAsInt()
					&& (ours2[2] == 1 ? "bernoulli" : "softmax").equals(type),
				"la cabeza " + name + " es " + h + " en el contrato y [" + ours2[0] + ", " + ours2[1] + "] en BlazeBrain");
			seen++;
		}
		helper.assertTrue(seen == heads.size(), "el contrato debería traer " + heads.size() + " cabezas: " + seen);
		JsonArray outputs = contract.getAsJsonArray("salidas");
		helper.assertTrue("usar".equals(outputs.get(BlazeBrain.USE_AT).getAsString())
			&& "retirarse".equals(outputs.get(BlazeBrain.RETREAT_AT).getAsString())
			&& "vertical_subir".equals(outputs.get(BlazeBrain.VERTICAL_AT + BlazeDecision.RISE).getAsString())
			&& "adelanto_1.5".equals(outputs.get(BlazeBrain.LEAD_AT + 3).getAsString()), "orden de las salidas: " + outputs);
		helper.succeed();
	}

	/** The trained red_blaze.json passes MobAi.checkBlaze, and MobAi loads it from a redes folder for the blaze. */
	@GameTest
	public void trainedBlazeNetLoadsAndPassesTheCheck(GameTestHelper helper) throws java.io.IOException {
		NetBrain net = trainedNet(helper);
		helper.assertTrue(ObsBlaze.FORMAT.equals(net.format), "formato de la red entrenada: " + net.format);
		helper.assertTrue(MobAi.checkBlaze(net) == null, "la red entrenada debería pasar la comprobación: " + MobAi.checkBlaze(net));
		helper.assertTrue(net.inputs() == ObsBlaze.SIZE && net.outputs() == BlazeBrain.OUTPUTS && net.ticksPerDecision == 2,
			"324 entradas, 18 salidas y 2 ticks: " + net.inputs() + ", " + net.outputs() + ", " + net.ticksPerDecision);
		helper.assertTrue(MobAi.check(net) != null, "la red del blaze no debería valer como red v3 de otra familia");
		CombatConfig cfg = CombatConfig.get();
		String savedFolder = cfg.iaCarpetaRedes;
		Path root = Files.createTempDirectory("forja_redes_blaze_entrenada");
		Path redes = Files.createDirectories(root.resolve("redes"));
		try {
			Files.copy(repoFile("redes_entrenadas/red_blaze.json"), redes.resolve("red_blaze.json"));
			cfg.iaCarpetaRedes = redes.toAbsolutePath().toString();
			MobAi.reload();
			NetBrain loaded = MobAi.net("blaze");
			helper.assertTrue(loaded != null && ObsBlaze.FORMAT.equals(loaded.format), "MobAi debería cargar la red del blaze: " + MobAi.problems());
			helper.assertTrue(!MobAi.problems().containsKey("blaze"), "sin problemas: " + MobAi.problems());
		} finally {
			cfg.iaCarpetaRedes = savedFolder;
			MobAi.reload();
		}
		helper.succeed();
	}

	private static CombatGameTests.TestPlayer player(GameTestHelper helper, BlockPos relative) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		floor(helper, net.minecraft.world.level.block.Blocks.STONE);
		openBox(helper);
		return CombatGameTests.player(helper, relative);
	}

	/** Takes down the barrier walls and ceiling round the test's box: a blaze keeps its distance, outside the box. */
	private static void openBox(GameTestHelper helper) {
		AABB box = helper.getBounds().inflate(1.0);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (helper.getLevel().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BARRIER)) {
				helper.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			}
		}
	}

	/**
	 * Level ground for the flight: y 0 from −10 to 18 (the test's padding keeps its neighbours further off), its
	 * chunks kept ticking while it lasts (outside the test's own box they are not, and a blaze there stands still).
	 * Taking it up lets go of the chunks this test forced and only those (TestChunks): it let go of every chunk under
	 * the ground, and one of them could be a chunk the framework keeps for the test next door.
	 */
	private static void floor(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
		boolean laying = block != net.minecraft.world.level.block.Blocks.AIR;
		if (laying) {
			TestChunks.force(helper, FLOOR_FROM, FLOOR_TO);
		} else {
			TestChunks.release(helper);
		}
		for (int x = FLOOR_FROM; x <= FLOOR_TO; x++) {
			for (int z = FLOOR_FROM; z <= FLOOR_TO; z++) {
				helper.setBlock(new BlockPos(x, 0, z), block);
			}
		}
	}

	private static final int FLOOR_FROM = -10;
	private static final int FLOOR_TO = 18;

	/** Keeps a still test player standing: full health, no fire, no knockback, hits count again each tick. */
	private static float[] keepAlive(GameTestHelper helper, CombatGameTests.TestPlayer player) {
		float[] taken = {0.0F};
		helper.onEachTick(() -> {
			taken[0] += Math.max(0.0F, player.getMaxHealth() - player.getHealth());
			player.setHealth(player.getMaxHealth());
			player.setDeltaMovement(Vec3.ZERO);
			player.setRemainingFireTicks(0);
			player.invulnerableTime = 0;
		});
		return taken;
	}

	private static Blaze blaze(GameTestHelper helper, BlockPos relative, CombatGameTests.TestPlayer player, NetBrain net) {
		Blaze blaze = helper.spawn(EntityTypes.BLAZE, relative);
		blaze.setPersistenceRequired();
		MobMind mind = MobAi.mind(blaze);
		helper.assertTrue(mind != null, "el blaze debería tener cerebro");
		mind.override = net;
		blaze.setTarget(player);
		helper.onEachTick(() -> {
			if (blaze.isAlive() && blaze.getTarget() != player) {
				blaze.setTarget(player);
			}
		});
		return blaze;
	}

	private static List<SmallFireball> fireballsOf(GameTestHelper helper, Mob blaze) {
		return helper.getLevel().getEntitiesOfClass(SmallFireball.class, blaze.getBoundingBox().inflate(64.0), ball -> ball.getOwner() == blaze);
	}

	private static void cleanUp(GameTestHelper helper, Mob... mobs) {
		for (Mob mob : mobs) {
			fireballsOf(helper, mob).forEach(SmallFireball::discard);
			mob.discard();
		}
		floor(helper, net.minecraft.world.level.block.Blocks.AIR);
	}

	/**
	 * Two blazes, a zombie beside the player and a fireball in the air: each blaze's observation is 324 finite numbers
	 * within the contract's scales, and the blaze's own inputs read what is there.
	 */
	@GameTest(environment = ARENA, padding = 16, maxTicks = 60)
	public void blazeObservationHas324FiniteInputs(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(6, 1, 3));
		Blaze blaze = helper.spawn(EntityTypes.BLAZE, new BlockPos(1, 4, 3));
		Blaze other = helper.spawn(EntityTypes.BLAZE, new BlockPos(1, 4, 6));
		Mob zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(7, 1, 3));
		for (Mob mob : new Mob[] {blaze, other, zombie}) {
			mob.setNoAi(true);
		}
		helper.runAfterDelay(10, () -> {
			zombie.setTarget(player);
			SmallFireball ball = new SmallFireball(helper.getLevel(), other, new Vec3(1.0, 0.0, 0.0));
			ball.setPos(player.getX() - 3.0, player.getY(0.5), player.getZ());
			ball.setNoGravity(true);
			helper.getLevel().addFreshEntity(ball);
			List<String> names = ObsBlaze.names();
			for (Mob mob : new Mob[] {blaze, other}) {
				MobMind mind = MobAi.mind(mob);
				helper.assertTrue(mind != null, "el blaze debería tener cerebro");
				float[] obs = ObsBlaze.build(mob, player, mind);
				helper.assertTrue(obs.length == ObsBlaze.SIZE, "la observación del blaze debería tener 324 entradas: " + obs.length);
				for (int i = 0; i < obs.length; i++) {
					helper.assertTrue(Float.isFinite(obs[i]), "la entrada " + i + " (" + names.get(i) + ") no es finita: " + obs[i]);
					helper.assertTrue(Math.abs(obs[i]) <= 3.0F, "la entrada " + i + " (" + names.get(i) + ") se sale de su escala: " + obs[i]);
				}
				double height = ObsBlaze.heightOverGround(mob);
				helper.assertTrue(height > 2.0 && height < 4.5, "el blaze está a unos 3 bloques del suelo: " + height);
				helper.assertTrue(Math.abs(obs[names.indexOf("yo_altura_suelo/5")] - height / 5.0) < 1.0E-4, "yo_altura_suelo/5");
				helper.assertTrue(Math.abs(obs[names.indexOf("jug_altura_suelo/5")]) < 1.0E-4, "el jugador pisa el suelo");
				helper.assertTrue(obs[names.indexOf("aliados_junto_jug/5")] == 0.2F, "un zombi junto al jugador: " + obs[names.indexOf("aliados_junto_jug/5")]);
				helper.assertTrue(obs[names.indexOf("aliado_suelo_dist_jug/8")] < 0.2F, "el zombi está a un bloque del jugador");
				helper.assertTrue(obs[names.indexOf("bola0_presente")] == 1.0F, "la bola en el aire debería verse");
				helper.assertTrue(obs[names.indexOf("bola0_mia")] == (mob == other ? 1.0F : 0.0F), "bola0_mia es de quien la tiró");
				helper.assertTrue(obs[names.indexOf("bola0_t_cercano/20")] > 0.0F && obs[names.indexOf("bola0_fallo/2")] < 0.1F,
					"la bola va derecha al jugador: " + obs[names.indexOf("bola0_t_cercano/20")] + ", " + obs[names.indexOf("bola0_fallo/2")]);
				helper.assertTrue(obs[names.indexOf("bola1_presente")] == 0.0F, "solo hay una bola");
				helper.assertTrue(obs[names.indexOf("rafaga_lista")] == 1.0F && obs[names.indexOf("rafaga_cargando")] == 0.0F,
					"quieto, sin espera y viéndolo, la ráfaga está lista");
				helper.assertTrue(obs[names.indexOf("tipo_zombie")] == 0.0F && obs[names.indexOf("yo_fuego")] == 0.0F
					&& obs[names.indexOf("yo_arco/20")] == 0.0F, "el blaze no está en los tipos, ni arde, ni tensa un arco");
				helper.assertTrue(obs[names.indexOf("obj_distancia/16")] > 0.0F && obs[names.indexOf("yo_ve_obj")] == 1.0F,
					"el jugador está a la vista");
			}
			ball.discard();
			cleanUp(helper, blaze, other, zombie);
			helper.succeed();
		});
	}

	/** A blaze network with small random weights (seeded), the contract's 324 inputs and 18 outputs, and its output biases as given. */
	static JsonObject fakeBlaze(long seed, float[] outBias) {
		List<String> names = ObsBlaze.names();
		int hidden = 8;
		int memory = 6;
		Random random = new Random(seed);
		JsonObject json = new JsonObject();
		json.addProperty("formato", ObsBlaze.FORMAT);
		json.addProperty("grupo", "blaze");
		json.addProperty("ticks_por_decision", 2);
		JsonArray namesJson = new JsonArray();
		names.forEach(namesJson::add);
		json.add("nombres_obs", namesJson);
		json.add("w1", noise(random, names.size(), hidden));
		json.add("b1", noise(random, 1, hidden).get(0));
		json.add("w2", noise(random, hidden, hidden));
		json.add("b2", noise(random, 1, hidden).get(0));
		json.add("gru_ih", noise(random, 3 * memory, hidden));
		json.add("gru_hh", noise(random, 3 * memory, memory));
		json.add("gru_bih", noise(random, 1, 3 * memory).get(0));
		json.add("gru_bhh", noise(random, 1, 3 * memory).get(0));
		json.add("w_out", noise(random, hidden + memory, BlazeBrain.OUTPUTS));
		JsonArray bias = new JsonArray();
		for (int k = 0; k < BlazeBrain.OUTPUTS; k++) {
			bias.add(k < outBias.length ? outBias[k] : 0.0F);
		}
		json.add("b_out", bias);
		return json;
	}

	private static JsonArray noise(Random random, int rows, int cols) {
		JsonArray m = new JsonArray();
		for (int i = 0; i < rows; i++) {
			JsonArray row = new JsonArray();
			for (int j = 0; j < cols; j++) {
				row.add((float) ((random.nextDouble() * 2.0 - 1.0) * 0.1));
			}
			m.add(row);
		}
		return m;
	}

	/** Per blaze: when it began to charge, and when each of its fireballs left, in test ticks. */
	private static final class Bursts {
		final List<Long> charges = new ArrayList<>();
		final List<Long> shots = new ArrayList<>();
		final List<Double> leads = new ArrayList<>();
		final Set<Integer> seen = new HashSet<>();
		int chargedTicks;
		int phase;
	}

	private static Bursts watchBursts(GameTestHelper helper, Blaze blaze) {
		Bursts bursts = new Bursts();
		MobMind mind = MobAi.mind(blaze);
		helper.onEachTick(() -> {
			if (!blaze.isAlive()) {
				return;
			}
			BlazePilot.State state = mind.blazeState;
			int phase = state == null ? 0 : state.phase;
			if (phase == BlazePilot.CHARGING && bursts.phase != BlazePilot.CHARGING) {
				bursts.charges.add(helper.getTick());
			}
			if (phase == BlazePilot.CHARGING && blaze.isOnFire()) {
				bursts.chargedTicks++;
			}
			bursts.phase = phase;
			for (SmallFireball ball : fireballsOf(helper, blaze)) {
				if (bursts.seen.add(ball.getId())) {
					bursts.shots.add(helper.getTick());
					bursts.leads.add(mind.blaze == null ? -1.0 : mind.blaze.leadFactor());
				}
			}
		});
		return bursts;
	}

	/**
	 * The trained network flies a blaze that starts a block off the ground: it rises into its band (2 to 5 over the
	 * ground) and stays there.
	 */
	@GameTest(environment = ARENA, padding = 16, maxTicks = 320)
	public void trainedBlazeHoversInItsBand(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 4));
		keepAlive(helper, player);
		Blaze blaze = blaze(helper, new BlockPos(4, 1, 14), player, trainedNet(helper));
		MobMind mind = MobAi.mind(blaze);
		int[] counted = {0, 0, 0};
		double[] extremes = {Double.MAX_VALUE, -Double.MAX_VALUE};
		helper.onEachTick(() -> {
			if (helper.getTick() < 60 || !blaze.isAlive()) {
				return;
			}
			double height = ObsBlaze.heightOverGround(blaze);
			counted[0]++;
			if (height >= BlazePilot.MIN_HEIGHT - 0.25 && height <= BlazePilot.MAX_HEIGHT + 0.25) {
				counted[1]++;
			}
			if (BlazePilot.drives(blaze)) {
				counted[2]++;
			}
			extremes[0] = Math.min(extremes[0], height);
			extremes[1] = Math.max(extremes[1], height);
		});
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(counted[2] > counted[0] * 0.9, "la red debería llevar al blaze: " + counted[2] + " de " + counted[0]);
			double share = counted[1] / (double) Math.max(1, counted[0]);
			Forja.LOGGER.info("[blaze] banda de vuelo: {} de {} ticks entre 2 y 5 (alturas {} a {})", counted[1], counted[0],
				String.format(Locale.ROOT, "%.2f", extremes[0]), String.format(Locale.ROOT, "%.2f", extremes[1]));
			helper.assertTrue(share >= 0.9, "el blaze debería flotar entre 2 y 5 bloques del suelo: " + counted[1] + " de " + counted[0]
				+ " ticks (alturas de " + extremes[0] + " a " + extremes[1] + ")");
			cleanUp(helper, blaze);
			helper.succeed();
		});
	}

	/**
	 * The trained network fires bursts of three: each starts with 20 ticks of warning (the blaze visibly charged, as
	 * vanilla's is), then three fireballs six ticks apart, then its rest.
	 */
	@GameTest(environment = ARENA, padding = 16, maxTicks = 420)
	public void trainedBlazeFiresTelegraphedBursts(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 4));
		keepAlive(helper, player);
		Blaze blaze = blaze(helper, new BlockPos(4, 3, 14), player, trainedNet(helper));
		Bursts bursts = watchBursts(helper, blaze);
		helper.runAfterDelay(400, () -> {
			Forja.LOGGER.info("[blaze] ráfagas: cargas en {}, bolas en {}", bursts.charges, bursts.shots);
			helper.assertTrue(bursts.charges.size() >= 2, "en 400 ticks debería cargar al menos dos ráfagas: " + bursts.charges);
			helper.assertTrue(bursts.shots.size() >= 6, "y soltar al menos 6 bolas: " + bursts.shots);
			helper.assertTrue(bursts.chargedTicks >= 15, "mientras carga debería verse encendido (el aviso): " + bursts.chargedTicks + " ticks");
			// every shot belongs to the last charge before it: the first 20 ticks after it, then 6 and 6 more
			for (int k = 0; k < bursts.shots.size(); k++) {
				long shot = bursts.shots.get(k);
				long charge = Long.MIN_VALUE;
				for (long c : bursts.charges) {
					if (c < shot) {
						charge = c;
					}
				}
				helper.assertTrue(charge != Long.MIN_VALUE, "la bola del tick " + shot + " salió sin aviso");
				long after = shot - charge;
				helper.assertTrue(Math.abs(after - 20) <= 1 || Math.abs(after - 26) <= 1 || Math.abs(after - 32) <= 1,
					"las bolas salen 20, 26 y 32 ticks después de empezar a cargar: " + after + " (cargas " + bursts.charges + ", bolas " + bursts.shots + ")");
			}
			int complete = 0;
			for (long c : bursts.charges) {
				int n = 0;
				for (long shot : bursts.shots) {
					if (shot > c && shot - c <= 33) {
						n++;
					}
				}
				if (n == 3) {
					complete++;
				}
			}
			helper.assertTrue(complete >= 1, "al menos una ráfaga completa de 3: " + bursts.charges + " / " + bursts.shots);
			cleanUp(helper, blaze);
			helper.succeed();
		});
	}

	/** Where a fireball was aimed, across the player's path: + ahead of where they are going, − behind. */
	private static double aheadOf(SmallFireball ball, Vec3 playerCentre, Vec3 playerVelocity) {
		Vec3 dir = ball.getDeltaMovement().normalize();
		Vec3 to = playerCentre.subtract(ball.position());
		// the point of the ball's line nearest the player, and how far along the player's way it is
		Vec3 nearest = ball.position().add(dir.scale(to.dot(dir)));
		Vec3 way = new Vec3(playerVelocity.x, 0.0, playerVelocity.z).normalize();
		return nearest.subtract(playerCentre).dot(way);
	}

	/**
	 * A blaze told to fire with a lead of 1.5 at a player walking to and fro across its line aims ahead of them every
	 * time; the trained network, against the same player, aims ahead on average.
	 */
	@GameTest(environment = ARENA, padding = 16, maxTicks = 420)
	public void blazeLeadsAMovingTarget(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 4));
		keepAlive(helper, player);
		float[] bias = new float[BlazeBrain.OUTPUTS];
		bias[BlazeBrain.MOVE_AT] = 8.0F;
		bias[BlazeBrain.VERTICAL_AT] = 8.0F;
		bias[BlazeBrain.USE_AT] = 8.0F;
		bias[BlazeBrain.LEAD_AT + 3] = 8.0F;
		bias[BlazeBrain.RETREAT_AT] = -8.0F;
		Blaze told = blaze(helper, new BlockPos(4, 3, 14), player, NetBrain.fromJson(fakeBlaze(21L, bias)));
		Blaze own = blaze(helper, new BlockPos(4, 3, -6), player, trainedNet(helper));
		Vec3 start = player.position();
		double speed = 0.2;
		Vec3[] velocity = {Vec3.ZERO};
		Map<Blaze, List<Double>> ahead = new HashMap<>();
		Map<Blaze, List<Double>> leads = new HashMap<>();
		Set<Integer> seen = new HashSet<>();
		// Which way the player was walking, tick by tick: a ball is judged against the way they went when it was thrown.
		Map<Long, Double> phases = new HashMap<>();
		helper.onEachTick(() -> {
			long t = helper.getTick();
			// to and fro along x, 12 blocks each way: across the told blaze's line (it is to the south, +z)
			double phase = (t % 120) < 60 ? 1.0 : -1.0;
			phases.put(t, phase);
			double offset = (t % 120) < 60 ? (t % 60) * speed : (60 - t % 60) * speed;
			Vec3 at = start.add(offset - 6.0, 0.0, 0.0);
			velocity[0] = new Vec3(phase * speed, 0.0, 0.0);
			player.setPos(at.x, at.y, at.z);
			player.setKnownMovement(velocity[0]);
			for (Blaze blaze : new Blaze[] {told, own}) {
				MobMind mind = MobAi.mind(blaze);
				for (SmallFireball ball : fireballsOf(helper, blaze)) {
					// A ball thrown about the tick the player turned round was aimed ahead of where they were going
					// before the turn, and read against the new way it came out behind them (-2.5 among 3 to 4.6).
					// Which way the blaze saw them go is not known then, so that ball is left out.
					long thrown = t - ball.tickCount;
					boolean sameWay = phases.getOrDefault(thrown - 1, phase) == phase && phases.getOrDefault(thrown, phase) == phase;
					if (seen.add(ball.getId()) && sameWay) {
						ahead.computeIfAbsent(blaze, b -> new ArrayList<>()).add(aheadOf(ball, new Vec3(at.x, player.getY(0.5), at.z), velocity[0]));
						leads.computeIfAbsent(blaze, b -> new ArrayList<>()).add(mind.blaze == null ? -1.0 : mind.blaze.leadFactor());
					}
				}
			}
		});
		helper.runAfterDelay(400, () -> {
			List<Double> toldAhead = ahead.getOrDefault(told, List.of());
			List<Double> ownAhead = ahead.getOrDefault(own, List.of());
			Forja.LOGGER.info("[blaze] adelanto: mandado a 1,5 {} ; la red {} con factores {}", fmt(toldAhead), fmt(ownAhead), leads.get(own));
			helper.assertTrue(toldAhead.size() >= 3, "el blaze mandado debería disparar al menos una ráfaga: " + toldAhead);
			for (double a : toldAhead) {
				helper.assertTrue(a > 0.3, "con adelanto 1,5 cada bola debería ir por delante del jugador: " + fmt(toldAhead));
			}
			helper.assertTrue(ownAhead.size() >= 3, "la red debería disparar al jugador que se mueve: " + ownAhead);
			double mean = ownAhead.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
			helper.assertTrue(mean > 0.0, "la red debería apuntar por delante del jugador, de media: " + fmt(ownAhead));
			cleanUp(helper, told, own);
			helper.succeed();
		});
	}

	private static String fmt(List<Double> values) {
		StringBuilder out = new StringBuilder("[");
		for (double v : values) {
			out.append(out.length() > 1 ? ", " : "").append(String.format(Locale.ROOT, "%.2f", v));
		}
		return out.append("]").toString();
	}

	/**
	 * The trained network keeps a blaze in its fight: within the 16 blocks its burst needs most of the time, and out of
	 * the player's reach almost always.
	 */
	@GameTest(environment = ARENA, padding = 16, maxTicks = 420)
	public void trainedBlazeKeepsItsEngagementRange(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 4));
		keepAlive(helper, player);
		Blaze blaze = blaze(helper, new BlockPos(4, 3, 13), player, trainedNet(helper));
		int[] counted = {0, 0, 0};
		double[] sum = {0.0};
		helper.onEachTick(() -> {
			if (helper.getTick() < 40 || !blaze.isAlive()) {
				return;
			}
			double d = blaze.distanceTo(player);
			counted[0]++;
			sum[0] += d;
			if (d < BlazePilot.FIRE_RANGE) {
				counted[1]++;
			}
			if (ObsBlaze.hitDistance(player, blaze, 0.0) <= dev.forja.ai.Reach.player(player)) {
				counted[2]++;
			}
		});
		helper.runAfterDelay(400, () -> {
			double within = counted[1] / (double) Math.max(1, counted[0]);
			double reached = counted[2] / (double) Math.max(1, counted[0]);
			double mean = sum[0] / Math.max(1, counted[0]);
			Forja.LOGGER.info("[blaze] distancia: media {}, {} de los ticks a < 16, {} al alcance de la espada",
				String.format(Locale.ROOT, "%.2f", mean), String.format(Locale.ROOT, "%.2f", within), String.format(Locale.ROOT, "%.2f", reached));
			helper.assertTrue(within >= 0.85, "debería pasar casi todo el combate a menos de 16: " + within + " (media " + mean + ")");
			helper.assertTrue(reached <= 0.1, "casi nunca al alcance de la espada: " + reached);
			cleanUp(helper, blaze);
			helper.succeed();
		});
	}

	/** Without a network the blaze is vanilla's: no executor, vanilla's own fireballs. */
	@GameTest(environment = ARENA, padding = 16, maxTicks = 200)
	public void blazeWithoutNetworkStaysVanilla(GameTestHelper helper) {
		helper.assertTrue(MobAi.net("blaze") == null, "las pruebas no llevan red del blaze en la carpeta: " + MobAi.net("blaze"));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 4));
		keepAlive(helper, player);
		Blaze blaze = blaze(helper, new BlockPos(4, 3, 12), player, null);
		MobMind mind = MobAi.mind(blaze);
		Set<Integer> balls = new HashSet<>();
		helper.onEachTick(() -> {
			fireballsOf(helper, blaze).forEach(ball -> balls.add(ball.getId()));
			helper.assertTrue(!BlazePilot.drives(blaze) && mind.blaze == null, "sin red, el ejecutor del blaze no debería actuar");
		});
		helper.runAfterDelay(180, () -> {
			helper.assertTrue(mind.blazeState == null, "sin red no hay estado de ráfaga del mod");
			helper.assertTrue(!balls.isEmpty(), "el blaze de vanilla debería haber disparado en 180 ticks");
			cleanUp(helper, blaze);
			helper.succeed();
		});
	}

	/**
	 * What it costs: a decision (ObsBlaze, mask, forward, sample) every 2 ticks plus the executor every tick, per
	 * blaze, at most 0.1 ms a tick.
	 */
	@GameTest(environment = ARENA, padding = 16, maxTicks = 60)
	public void blazeNetworkCostsLittle(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 4));
		keepAlive(helper, player);
		NetBrain net = trainedNet(helper);
		Blaze blaze = blaze(helper, new BlockPos(4, 3, 13), player, net);
		Mob zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(5, 1, 4));
		zombie.setNoAi(true);
		helper.runAfterDelay(20, () -> {
			MobMind mind = MobAi.mind(blaze);
			float[] memory = new float[net.memory];
			RandomSource random = RandomSource.create(7L);
			int rounds = 7;
			int per = 300;
			double[] decisions = new double[rounds];
			double[] pilots = new double[rounds];
			for (int warm = 0; warm < 400; warm++) {
				float[] obs = ObsBlaze.build(blaze, player, mind);
				boolean[] mask = BlazeBrain.mask(blaze, mind, player);
				BlazeBrain.sample(net.forward(obs, memory), 1.0, random, mask);
			}
			BlazeDecision still = new BlazeDecision(3, BlazeDecision.HOLD, false, 0, false);
			for (int r = 0; r < rounds; r++) {
				long t0 = System.nanoTime();
				for (int k = 0; k < per; k++) {
					float[] obs = ObsBlaze.build(blaze, player, mind);
					boolean[] mask = BlazeBrain.mask(blaze, mind, player);
					BlazeBrain.sample(net.forward(obs, memory), 1.0, random, mask);
				}
				decisions[r] = (System.nanoTime() - t0) / (double) per;
				BlazeDecision saved = mind.blaze;
				mind.blaze = still;
				Vec3 at = blaze.position();
				Vec3 moving = blaze.getDeltaMovement();
				t0 = System.nanoTime();
				for (int k = 0; k < per; k++) {
					BlazePilot.tick(blaze, mind, player);
				}
				pilots[r] = (System.nanoTime() - t0) / (double) per;
				blaze.setPos(at);
				blaze.setDeltaMovement(moving);
				mind.blaze = saved;
			}
			java.util.Arrays.sort(decisions);
			java.util.Arrays.sort(pilots);
			double decision = decisions[rounds / 2] / 1.0E6;
			double pilot = pilots[rounds / 2] / 1.0E6;
			double perTick = decision / net.ticksPerDecision + pilot;
			Forja.LOGGER.info("[blaze] coste: decisión {} ms, ejecutor {} ms, por tick {} ms",
				String.format(Locale.ROOT, "%.4f", decision), String.format(Locale.ROOT, "%.4f", pilot), String.format(Locale.ROOT, "%.4f", perTick));
			helper.assertTrue(perTick <= 0.1, "un blaze con red debería costar ≤ 0,1 ms por tick: " + perTick + " ms (decisión "
				+ decision + ", ejecutor " + pilot + ")");
			cleanUp(helper, blaze, zombie);
			helper.succeed();
		});
	}

	/**
	 * A rough comparison, logged: how many fireballs of one blaze in 600 ticks, at about 10 blocks, go into a still
	 * player's box, with the trained network and with vanilla's goal. The test player is not in the level's entity
	 * lists (a fake player), so a ball flies through it: the hit is worked out from the ball's path each tick, and the
	 * ball is taken out there. The network must land some.
	 */
	@GameTest(environment = ARENA, padding = 16, maxTicks = 620)
	public void blazeDamageWithTheNetwork(GameTestHelper helper) {
		hitsOverTime(helper, trainedNet(helper), "red");
	}

	@GameTest(environment = ARENA, padding = 16, maxTicks = 620)
	public void blazeDamageVanilla(GameTestHelper helper) {
		hitsOverTime(helper, null, "vanilla");
	}

	private static void hitsOverTime(GameTestHelper helper, NetBrain net, String label) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(4, 1, 4));
		keepAlive(helper, player);
		Blaze blaze = blaze(helper, new BlockPos(4, 3, 14), player, net);
		Map<Integer, Vec3> last = new HashMap<>();
		int[] counted = {0, 0};
		helper.onEachTick(() -> {
			AABB body = player.getBoundingBox().inflate(0.16);
			for (SmallFireball ball : fireballsOf(helper, blaze)) {
				Vec3 now = ball.position();
				Vec3 before = last.put(ball.getId(), now);
				if (before == null) {
					counted[0]++;
					before = now;
				}
				if (body.contains(now) || body.clip(before, now).isPresent()) {
					counted[1]++;
					ball.discard();
				}
			}
		});
		helper.runAfterDelay(600, () -> {
			Forja.LOGGER.info("[blaze] impactos en 600 ticks ({}): {} de {} bolas, unos {} de daño a 5 por bola", label, counted[1], counted[0],
				counted[1] * 5);
			if (net != null) {
				helper.assertTrue(counted[1] > 0, "la red del blaze debería acertar alguna bola a un jugador quieto en 30 s: " + counted[0] + " bolas");
			}
			cleanUp(helper, blaze);
			helper.succeed();
		});
	}

	/**
	 * red_blaze.json with any formato but red_blaze_v1 is refused (a v2 or v3 mob network, a v4 one) and the blaze keeps
	 * its rules; a red_blaze_v1 file is loaded, from redes_v4 or from redes.
	 */
	@GameTest
	public void wrongFormatBlazeNetIsRefused(GameTestHelper helper) throws java.io.IOException {
		CombatConfig cfg = CombatConfig.get();
		String savedFolder = cfg.iaCarpetaRedes;
		Path root = Files.createTempDirectory("forja_redes_blaze");
		Path v3 = Files.createDirectories(root.resolve("redes"));
		Path v4 = Files.createDirectories(root.resolve("redes_v4"));
		com.google.gson.Gson gson = new com.google.gson.Gson();
		helper.assertTrue(MobAi.checkBlaze(NetBrain.fromJson(RedV4GameTests.fakeV4(3L, new float[0]))) != null, "una red v4 no vale para el blaze");
		JsonObject mislabelled = fakeBlaze(5L, new float[0]);
		mislabelled.addProperty("formato", "red_mob_v3");
		helper.assertTrue(MobAi.checkBlaze(NetBrain.fromJson(mislabelled)) != null, "con otro formato no vale aunque las entradas sean las del blaze");
		try {
			cfg.iaCarpetaRedes = v3.toAbsolutePath().toString();
			Files.writeString(v3.resolve("red_blaze.json"), gson.toJson(AiGameTests.fakeV2(new float[NetBrain.V2_OUTPUTS])));
			MobAi.reload();
			helper.assertTrue(MobAi.net("blaze") == null, "una red v2 para el blaze debería rechazarse");
			helper.assertTrue(MobAi.problems().containsKey("blaze"), "el rechazo debería quedar en los problemas: " + MobAi.problems());
			Files.writeString(v3.resolve("red_blaze.json"), gson.toJson(mislabelled));
			MobAi.reload();
			helper.assertTrue(MobAi.net("blaze") == null, "una red con formato red_mob_v3 para el blaze debería rechazarse");
			Files.writeString(v3.resolve("red_blaze.json"), gson.toJson(fakeBlaze(7L, new float[0])));
			MobAi.reload();
			NetBrain fromRedes = MobAi.net("blaze");
			helper.assertTrue(fromRedes != null && ObsBlaze.FORMAT.equals(fromRedes.format), "una red_blaze_v1 en redes debería cargarse: " + MobAi.problems());
			helper.assertTrue(!MobAi.problems().containsKey("blaze"), "la buena no deja problemas: " + MobAi.problems());
			Files.writeString(v3.resolve("red_blaze.json"), gson.toJson(mislabelled));
			Files.writeString(v4.resolve("red_blaze.json"), gson.toJson(fakeBlaze(9L, new float[0])));
			MobAi.reload();
			NetBrain fromV4 = MobAi.net("blaze");
			helper.assertTrue(fromV4 != null && ObsBlaze.FORMAT.equals(fromV4.format), "una red_blaze_v1 en redes_v4 debería cargarse: " + MobAi.problems());
		} finally {
			cfg.iaCarpetaRedes = savedFolder;
			MobAi.reload();
		}
		helper.succeed();
	}
}
