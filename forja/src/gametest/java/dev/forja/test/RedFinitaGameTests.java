package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.forja.ai.BlazeBrain;
import dev.forja.ai.CaptainBrain;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Networks with non-finite numbers: a file with a NaN or infinite weight is refused at load (every contract's check), and
 * a network whose forward pass gives NaN logits at runtime is not sampled: the rules decide that tick, and after 20 such
 * ticks the network is switched off.
 */
public class RedFinitaGameTests {
	/** How many copies of nanFileIsRefusedAndMobUsesRules have MobAi on their folder now, and what was there before the first. */
	private static int swapped;
	private static String savedFolder;
	private static String savedContract;

	/** Puts a non-finite value in the first number of the named matrix or vector. */
	private static JsonObject poison(JsonObject json, String key, float value) {
		JsonArray array = json.getAsJsonArray(key);
		if (array.get(0).isJsonArray()) {
			array.get(0).getAsJsonArray().set(0, new JsonPrimitive(value));
		} else {
			array.set(0, new JsonPrimitive(value));
		}
		return json;
	}

	/** A normal v2 network as the simulator would write it, ticking every tick so the tests are short. */
	private static JsonObject good() {
		JsonObject json = AiGameTests.fakeV2(new float[NetBrain.V2_OUTPUTS]);
		json.addProperty("ticks_por_decision", 1);
		return json;
	}

	private static Zombie zombieWith(GameTestHelper helper, NetBrain net) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(helper.getLevel());
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(6, 1, 6)));
		player.setPos(at.x, at.y, at.z);
		helper.onEachTick(() -> {
			player.setHealth(player.getMaxHealth());
			player.setDeltaMovement(Vec3.ZERO);
		});
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
		MobMind mind = MobAi.mind(zombie);
		helper.assertTrue(mind != null, "el zombi debería tener cerebro");
		if (net != null) {
			mind.override = net;
		}
		zombie.setTarget(player);
		return zombie;
	}

	/** A NaN or infinite weight, bias or normalisation value is refused by every contract's check; a normal net passes. */
	@GameTest
	public void nonFiniteNetworksFailEveryCheck(GameTestHelper helper) {
		helper.assertTrue(MobAi.check(NetBrain.fromJson(good())) == null, "una red normal debería pasar la comprobación");
		String[] keys = {"w1", "b1", "w2", "b2", "gru_ih", "gru_hh", "gru_bih", "gru_bhh", "w_out", "b_out"};
		for (String key : keys) {
			for (float bad : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
				String problem = MobAi.check(NetBrain.fromJson(poison(good(), key, bad)));
				helper.assertTrue(problem != null && problem.contains("pesos no finitos"), key + " = " + bad + " debería rechazarse: " + problem);
			}
		}
		JsonObject normalised = good();
		JsonArray std = new JsonArray();
		for (int i = 0; i < normalised.getAsJsonArray("nombres_obs").size(); i++) {
			std.add(1.0F);
		}
		normalised.add("desviacion", std);
		helper.assertTrue(MobAi.check(NetBrain.fromJson(normalised)) == null, "una red con desviación finita pasa");
		poison(normalised, "desviacion", Float.NaN);
		helper.assertTrue(MobAi.check(NetBrain.fromJson(normalised)) != null, "una desviación NaN debería rechazarse");
		String v4 = MobAi.checkV4(NetBrain.fromJson(poison(RedV4GameTests.fakeV4(3L, new float[0]), "b_out", Float.NaN)));
		helper.assertTrue(v4 != null && v4.contains("pesos no finitos"), "v4: " + v4);
		helper.assertTrue(MobAi.checkV4(NetBrain.fromJson(RedV4GameTests.fakeV4(3L, new float[0]))) == null, "una v4 normal pasa");
		String blaze = BlazeBrain.check(NetBrain.fromJson(poison(BlazeGameTests.fakeBlaze(3L, new float[0]), "w_out", Float.POSITIVE_INFINITY)));
		helper.assertTrue(blaze != null && blaze.contains("pesos no finitos"), "blaze: " + blaze);
		String captain = CaptainBrain.check(NetBrain.fromJson(poison(RedV4CapitanGameTests.fakeCaptain(3L, new float[0]), "w1", Float.NaN)));
		helper.assertTrue(captain != null && captain.contains("pesos no finitos"), "capitán: " + captain);
		helper.succeed();
	}

	/**
	 * A file with a NaN weight is refused at load, listed in the problems, and its mob fights by the rules; a good one loads.
	 *
	 * <p>In a batch of its own (its environment, like RedV4ModGameTests.aChangeOfFamilySwitchesNetworkAndMemory): the
	 * folder it points MobAi at is every mob's for the 40 ticks it watches its zombie, so for those ticks no zombie
	 * anywhere has a body network and the contract is v3. In the common batch that took the networks away from the tests
	 * running beside it (the apprentices among them, once). And the folder and contract go back whatever happens: on
	 * a failed check now or in the delayed one, not only on success. Run side by side with itself (FORJA_VERIFICAR),
	 * each copy found the folder the one before had set and put that back; the count below has the first copy in
	 * keep the world's own folder and the last one out put it back.
	 */
	@GameTest(environment = "forja-test:red_finita", maxTicks = 80)
	public void nanFileIsRefusedAndMobUsesRules(GameTestHelper helper) throws java.io.IOException {
		CombatConfig cfg = CombatConfig.get();
		if (swapped++ == 0) {
			savedFolder = cfg.iaCarpetaRedes;
			savedContract = cfg.iaContrato;
		}
		boolean[] restored = {false};
		Runnable restore = () -> {
			if (restored[0]) {
				return;
			}
			restored[0] = true;
			if (--swapped == 0) {
				cfg.iaCarpetaRedes = savedFolder;
				cfg.iaContrato = savedContract;
				MobAi.reload();
			}
		};
		Path root = Files.createTempDirectory("forja_finita");
		Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
		try {
			cfg.iaCarpetaRedes = root.toAbsolutePath().toString();
			cfg.iaContrato = "v3";
			Files.writeString(root.resolve("red_cuerpo.json"), gson.toJson(good()));
			MobAi.reload();
			helper.assertTrue(MobAi.net("cuerpo") != null && !MobAi.problems().containsKey("cuerpo"),
				"una red normal debería cargarse: " + MobAi.problems());
			Files.writeString(root.resolve("red_cuerpo.json"), gson.toJson(poison(good(), "w1", Float.NaN)));
			MobAi.reload();
			helper.assertTrue(MobAi.net("cuerpo") == null, "la red con NaN no debería cargarse");
			String problem = MobAi.problems().get("cuerpo");
			helper.assertTrue(problem != null && problem.contains("pesos no finitos"), "debería figurar en los problemas: " + problem);
			Zombie zombie = zombieWith(helper, null);
			MobMind mind = MobAi.mind(zombie);
			helper.runAfterDelay(40, () -> {
				try {
					helper.assertTrue(!mind.networked, "sin red válida el zombi usa las reglas");
				} finally {
					restore.run();
				}
				helper.succeed();
			});
		} catch (RuntimeException | java.io.IOException failure) {
			restore.run();
			throw failure;
		}
	}

	/** Non-finite logits at runtime are never sampled; 20 of those ticks switch the network off, and the rules take over. */
	@GameTest(maxTicks = 160)
	public void runtimeNanFallsBackAndDisablesAfter20(GameTestHelper helper) {
		float[] bias = new float[NetBrain.V2_OUTPUTS];
		bias[0] = Float.NaN;
		JsonObject json = AiGameTests.fakeV2(bias);
		json.addProperty("ticks_por_decision", 1);
		NetBrain net = NetBrain.fromJson(json);
		helper.assertTrue(!NetBrain.finiteLogits(net.forward(new float[net.inputs()], new float[net.memory])), "la red falsa debería dar NaN");
		Zombie zombie = zombieWith(helper, net);
		MobMind mind = MobAi.mind(zombie);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(!mind.networked, "con salidas NaN decide las reglas, no la red");
			helper.assertTrue(net.badTicks() > 0 && !net.disabled(), "cuenta los ticks malos sin apagarla todavía: " + net.badTicks());
			helper.assertTrue(mind.decision != null && mind.decision.move() >= 0 && mind.decision.move() <= 8, "decisión válida de las reglas");
		});
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(net.disabled() && net.badTicks() == NetBrain.MAX_BAD_TICKS,
				"a los 20 ticks malos se apaga y deja de contar: " + net.badTicks() + " " + net.disabled());
			helper.assertTrue(!mind.networked, "apagada, las reglas");
			helper.succeed();
		});
	}
}
