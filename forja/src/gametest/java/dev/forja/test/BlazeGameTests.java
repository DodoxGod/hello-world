package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The blaze's own network, red_blaze_v1 (docs/red_blaze_contrato.json; Andy, 2026-09-29): ObsBlaze gives the contract
 * name for name, a blaze's observation is 65 finite numbers, a blaze network drives a blaze in 3D (and shoots only
 * what it sees), and any other formato for the blaze is refused, so the blaze keeps to its rules.
 */
public class BlazeGameTests {
	/** The contract, found by walking up from the game's folder (the tests run a few folders down), as RedV4GameTests does. */
	private static Path contractFile() {
		List<Path> starts = List.of(FabricLoader.getInstance().getGameDir().toAbsolutePath(), Path.of("").toAbsolutePath());
		for (Path start : starts) {
			for (Path at = start; at != null; at = at.getParent()) {
				for (String relative : new String[] {"docs/red_blaze_contrato.json", "forja/docs/red_blaze_contrato.json"}) {
					Path file = at.resolve(relative);
					if (Files.exists(file)) {
						return file;
					}
				}
			}
		}
		return null;
	}

	/** ObsBlaze.names() is the contract's nombres_obs in order; its formato, blocks and heads are the mod's. */
	@GameTest
	public void blazeNamesMatchTheContract(GameTestHelper helper) throws java.io.IOException {
		Path file = contractFile();
		helper.assertTrue(file != null, "no se encuentra docs/red_blaze_contrato.json subiendo desde " + FabricLoader.getInstance().getGameDir());
		JsonObject contract = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		helper.assertTrue(ObsBlaze.FORMAT.equals(contract.get("formato").getAsString()), "formato: " + contract.get("formato"));
		helper.assertTrue(contract.get("version").getAsInt() == 1, "versión: " + contract.get("version"));
		List<String> theirs = new ArrayList<>();
		contract.getAsJsonArray("nombres_obs").forEach(name -> theirs.add(name.getAsString()));
		List<String> ours = ObsBlaze.names();
		helper.assertTrue(contract.get("n_obs").getAsInt() == theirs.size() && theirs.size() == ObsBlaze.SIZE,
			"n_obs debería ser " + ObsBlaze.SIZE + ": " + contract.get("n_obs") + " con " + theirs.size() + " nombres");
		helper.assertTrue(ours.size() == theirs.size(), "ObsBlaze da " + ours.size() + " nombres y el contrato " + theirs.size());
		for (int i = 0; i < theirs.size(); i++) {
			helper.assertTrue(theirs.get(i).equals(ours.get(i)), "entrada " + i + ": el contrato dice '" + theirs.get(i) + "' y ObsBlaze '" + ours.get(i) + "'");
		}
		java.util.Map<String, Integer> starts = java.util.Map.of("YO", ObsBlaze.YO_AT, "OBJ", ObsBlaze.OBJ_AT, "BOLAS", ObsBlaze.BOLAS_AT,
			"ALIADOS", ObsBlaze.ALIADOS_AT, "ENTORNO", ObsBlaze.ENTORNO_AT);
		for (var block : contract.getAsJsonArray("bloques")) {
			JsonObject b = block.getAsJsonObject();
			String name = b.get("bloque").getAsString();
			helper.assertTrue(starts.containsKey(name) && starts.get(name) == b.get("inicio").getAsInt(),
				"el bloque " + name + " empieza en " + b.get("inicio") + " en el contrato y en " + starts.get(name) + " en ObsBlaze");
		}
		helper.assertTrue(contract.get("n_salidas").getAsInt() == BlazeBrain.OUTPUTS, "n_salidas: " + contract.get("n_salidas"));
		java.util.Map<String, int[]> heads = java.util.Map.of("mover", range(BlazeBrain.MOVE_AT, BlazeBrain.MOVES),
			"vertical", range(BlazeBrain.VERTICAL_AT, 3), "fuego", range(BlazeBrain.FIRE_AT, 3), "distancia", range(BlazeBrain.RANGE_AT, 3),
			"tactica", range(BlazeBrain.TACTIC_AT, BlazeBrain.TACTICS));
		int seen = 0;
		for (var head : contract.getAsJsonArray("cabezas")) {
			JsonObject h = head.getAsJsonObject();
			String name = h.get("nombre").getAsString();
			JsonArray indices = h.getAsJsonArray("indices");
			int[] theirIndices = new int[indices.size()];
			for (int k = 0; k < theirIndices.length; k++) {
				theirIndices[k] = indices.get(k).getAsInt();
			}
			helper.assertTrue(heads.containsKey(name) && java.util.Arrays.equals(heads.get(name), theirIndices),
				"la cabeza " + name + " es " + java.util.Arrays.toString(theirIndices) + " en el contrato y "
					+ java.util.Arrays.toString(heads.get(name)) + " en BlazeBrain");
			seen++;
		}
		helper.assertTrue(seen == heads.size(), "el contrato debería traer " + heads.size() + " cabezas: " + seen);
		JsonArray tactics = contract.getAsJsonArray("tacticas");
		BlazeDecision.Plan[] plans = BlazeDecision.Plan.values();
		helper.assertTrue(tactics.size() == plans.length, "tácticas: " + tactics);
		for (int k = 0; k < plans.length; k++) {
			helper.assertTrue(plans[k].name().toLowerCase(java.util.Locale.ROOT).equals(tactics.get(k).getAsString()),
				"táctica " + k + ": el contrato dice " + tactics.get(k) + " y el mod " + plans[k]);
		}
		helper.succeed();
	}

	private static int[] range(int from, int n) {
		int[] out = new int[n];
		for (int k = 0; k < n; k++) {
			out[k] = from + k;
		}
		return out;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static CombatGameTests.TestPlayer player(GameTestHelper helper, BlockPos relative) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(helper.getLevel());
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
		player.setPos(at.x, at.y, at.z);
		helper.onEachTick(() -> {
			player.setHealth(player.getMaxHealth());
			player.setDeltaMovement(Vec3.ZERO);
			player.setRemainingFireTicks(0);
		});
		return player;
	}

	/** Two blazes near a player: each one's observation is 65 finite numbers, and the other blaze is seen as one. */
	@GameTest(maxTicks = 60)
	public void blazeObservationHas65FiniteInputs(GameTestHelper helper) {
		floor(helper);
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(6, 1, 3));
		Mob blaze = helper.spawn(EntityTypes.BLAZE, new BlockPos(2, 3, 3));
		Mob other = helper.spawn(EntityTypes.BLAZE, new BlockPos(1, 3, 6));
		blaze.setNoAi(true);
		other.setNoAi(true);
		helper.runAfterDelay(12, () -> {
			for (Mob mob : new Mob[] {blaze, other}) {
				MobMind mind = MobAi.mind(mob);
				helper.assertTrue(mind != null, "el blaze debería tener cerebro");
				float[] obs = ObsBlaze.build(mob, player, mind);
				helper.assertTrue(obs.length == ObsBlaze.SIZE, "la observación del blaze debería tener 65 entradas: " + obs.length);
				for (int i = 0; i < obs.length; i++) {
					helper.assertTrue(Float.isFinite(obs[i]), "la entrada " + i + " (" + ObsBlaze.names().get(i) + ") no es finita: " + obs[i]);
					helper.assertTrue(Math.abs(obs[i]) <= 3.0F, "la entrada " + i + " (" + ObsBlaze.names().get(i) + ") se sale de su escala: " + obs[i]);
				}
				int present = ObsBlaze.names().indexOf("blaze0_presente");
				helper.assertTrue(obs[present] == 1.0F, "cada blaze debería ver al otro: " + obs[present]);
				int distance = ObsBlaze.names().indexOf("obj_distancia/16");
				helper.assertTrue(obs[distance] > 0.0F, "el jugador está a unos bloques: " + obs[distance]);
				int sight = ObsBlaze.names().indexOf("yo_ve_obj");
				helper.assertTrue(obs[sight] == 1.0F, "a campo abierto debería ver al jugador");
			}
			blaze.discard();
			other.discard();
			helper.succeed();
		});
	}

	/** A blaze network with small random weights (seeded), the contract's 65 inputs and 22 outputs, and its output biases as given. */
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

	private static List<SmallFireball> fireballsOf(GameTestHelper helper, Mob blaze) {
		return helper.getLevel().getEntitiesOfClass(SmallFireball.class, blaze.getBoundingBox().inflate(48.0), ball -> ball.getOwner() == blaze);
	}

	/**
	 * A fake blaze network drives a blaze for 40 ticks: it decides every 2 ticks without an exception, from 65 finite
	 * inputs into 22 finite logits; told to press in and rise, it goes up (the vertical control works); charged and told
	 * to fire, it fires, and every fireball leaves while it sees the player.
	 */
	@GameTest(maxTicks = 100)
	public void fakeBlazeNetworkDrivesABlaze(GameTestHelper helper) {
		floor(helper);
		float[] bias = new float[BlazeBrain.OUTPUTS];
		bias[BlazeBrain.MOVE_AT] = 3.0F;
		bias[BlazeBrain.VERTICAL_AT + BlazeDecision.RISE] = 6.0F;
		bias[BlazeBrain.FIRE_AT + BlazeDecision.FIRE] = 6.0F;
		bias[BlazeBrain.TACTIC_AT + BlazeDecision.Plan.ACOSAR.ordinal()] = 6.0F;
		NetBrain net = NetBrain.fromJson(fakeBlaze(13L, bias));
		helper.assertTrue(MobAi.checkBlaze(net) == null, "la red falsa del blaze debería encajar: " + MobAi.checkBlaze(net));
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(6, 1, 6));
		Mob blaze = helper.spawn(EntityTypes.BLAZE, new BlockPos(2, 2, 2));
		MobMind mind = MobAi.mind(blaze);
		helper.assertTrue(mind != null, "el blaze debería tener cerebro");
		mind.override = net;
		mind.blazeCharge = BlazePilot.CHARGE_TICKS;
		blaze.setTarget(player);
		long[] last = {Long.MIN_VALUE};
		int[] decisions = {0};
		double[] startY = {Double.NaN};
		Set<Integer> balls = new HashSet<>();
		helper.onEachTick(() -> {
			if (!blaze.isAlive()) {
				return;
			}
			for (SmallFireball ball : fireballsOf(helper, blaze)) {
				if (balls.add(ball.getId())) {
					helper.assertTrue(ObsBlaze.sees(blaze, player), "el blaze solo debería disparar viendo al jugador");
				}
			}
			if (!mind.networked || mind.decidedAt == last[0]) {
				return;
			}
			last[0] = mind.decidedAt;
			decisions[0]++;
			if (Double.isNaN(startY[0])) {
				startY[0] = blaze.getY();
			}
			helper.assertTrue(mind.blaze != null, "una red del blaze debería dejar una decisión del blaze");
			helper.assertTrue(mind.lastObs != null && mind.lastObs.length == ObsBlaze.SIZE, "la red del blaze debería recibir 65 entradas: "
				+ (mind.lastObs == null ? null : mind.lastObs.length));
			for (float v : mind.lastObs) {
				helper.assertTrue(Float.isFinite(v), "entrada no finita en la observación del blaze");
			}
			helper.assertTrue(mind.lastLogits != null && mind.lastLogits.length == BlazeBrain.OUTPUTS, "la red del blaze debería dar 22 salidas");
			for (float v : mind.lastLogits) {
				helper.assertTrue(Float.isFinite(v), "salida no finita de la red del blaze");
			}
		});
		helper.runAfterDelay(41, () -> {
			helper.assertTrue(decisions[0] >= 15, "en 40 ticks la red del blaze debería decidir unas 20 veces: " + decisions[0]);
			helper.assertTrue(!Double.isNaN(startY[0]) && blaze.getY() - startY[0] > 1.0,
				"con subir casi seguro, el blaze debería haber subido más de un bloque: de " + startY[0] + " a " + blaze.getY());
			helper.assertTrue(!balls.isEmpty(), "cargado, viéndolo y con disparar casi seguro, debería haber soltado alguna bola");
			fireballsOf(helper, blaze).forEach(SmallFireball::discard);
			blaze.discard();
			helper.succeed();
		});
	}

	/**
	 * Behind a wall a charged blaze may not fire (the mask shuts "disparar") and a burst sends nothing; with the wall
	 * gone the same burst sends its three fireballs.
	 */
	@GameTest(maxTicks = 40)
	public void blazeFiresOnlyWhatItSees(GameTestHelper helper) {
		floor(helper);
		for (int y = 1; y <= 5; y++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
			}
		}
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(6, 1, 3));
		Mob blaze = helper.spawn(EntityTypes.BLAZE, new BlockPos(2, 1, 3));
		blaze.setNoAi(true);
		MobMind mind = MobAi.mind(blaze);
		helper.assertTrue(mind != null, "el blaze debería tener cerebro");
		helper.runAfterDelay(2, () -> {
			mind.blazeCharge = BlazePilot.CHARGE_TICKS;
			mind.cooldown = 0;
			mind.draw = 0;
			helper.assertTrue(!ObsBlaze.sees(blaze, player), "el muro debería tapar al jugador");
			helper.assertTrue(!BlazePilot.canFire(blaze, mind, player), "tapado, no debería poder empezar la ráfaga");
			helper.assertTrue(!BlazeBrain.mask(blaze, mind, player)[BlazeBrain.FIRE_AT + BlazeDecision.FIRE], "tapado, disparar debería estar prohibido");
			int burst = 1 + (dev.forja.ai.TacticGoal.BLAZE_BURST - 1) * dev.forja.ai.TacticGoal.BLAZE_BURST_GAP;
			for (int k = 0; k < burst; k++) {
				BlazePilot.burst(blaze, mind, player, true, true);
			}
			helper.assertTrue(fireballsOf(helper, blaze).isEmpty(), "sin verlo no debería salir ninguna bola: " + fireballsOf(helper, blaze).size());
			for (int y = 1; y <= 5; y++) {
				for (int z = 0; z < 8; z++) {
					helper.setBlock(new BlockPos(4, y, z), Blocks.AIR);
				}
			}
			mind.cooldown = 0;
			mind.draw = 0;
			helper.assertTrue(ObsBlaze.sees(blaze, player), "sin el muro debería verlo");
			helper.assertTrue(BlazePilot.canFire(blaze, mind, player), "cargado y viéndolo debería poder disparar");
			for (int k = 0; k < burst; k++) {
				BlazePilot.burst(blaze, mind, player, true, true);
			}
			int sent = fireballsOf(helper, blaze).size();
			helper.assertTrue(sent == dev.forja.ai.TacticGoal.BLAZE_BURST, "viéndolo, la ráfaga debería soltar 3 bolas: " + sent);
			helper.assertTrue(mind.draw == 0 && mind.cooldown == dev.forja.ai.TacticGoal.BLAZE_COOLDOWN, "tras la ráfaga, la espera larga");
			fireballsOf(helper, blaze).forEach(SmallFireball::discard);
			blaze.discard();
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
