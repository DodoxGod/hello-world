package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.forja.ai.Decision;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsV4;
import dev.forja.ai.Reach;
import dev.forja.ai.Tactic;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Step M1 of the v4 mob network (docs/red_mob_v4_diseno.md §6.1; Andy, 2026-09-29): ObsV4 gives the simulator's
 * contract (docs/red_mob_v4_contrato.json) name for name, a mob's v4 observation is 468 finite numbers, a v4 file in
 * redes_v4 is loaded, and a v4 network drives a zombie with what the executor can do today, the rest shut.
 */
public class RedV4GameTests {
	/** The contract the simulator wrote, found by walking up from the game's folder (the tests run a few folders down). */
	private static Path contractFile() {
		List<Path> starts = List.of(FabricLoader.getInstance().getGameDir().toAbsolutePath(), Path.of("").toAbsolutePath());
		for (Path start : starts) {
			for (Path at = start; at != null; at = at.getParent()) {
				for (String relative : new String[] {"docs/red_mob_v4_contrato.json", "forja/docs/red_mob_v4_contrato.json"}) {
					Path file = at.resolve(relative);
					if (Files.exists(file)) {
						return file;
					}
				}
			}
		}
		return null;
	}

	/** ObsV4.names() is the contract's nombres_obs, name for name and in order; its sizes, blocks and heads match the mod's. */
	@GameTest
	public void v4NamesMatchTheContract(GameTestHelper helper) throws java.io.IOException {
		Path file = contractFile();
		helper.assertTrue(file != null, "no se encuentra docs/red_mob_v4_contrato.json subiendo desde " + FabricLoader.getInstance().getGameDir());
		JsonObject contract = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		helper.assertTrue(contract.get("n_obs").getAsInt() == 468 && ObsV4.SIZE == 468, "n_obs debería ser 468: " + contract.get("n_obs"));
		List<String> theirs = new ArrayList<>();
		contract.getAsJsonArray("nombres_obs").forEach(name -> theirs.add(name.getAsString()));
		List<String> ours = ObsV4.names();
		helper.assertTrue(theirs.size() == 468, "el contrato debería traer 468 nombres: " + theirs.size());
		helper.assertTrue(ours.size() == theirs.size(), "ObsV4 da " + ours.size() + " nombres y el contrato " + theirs.size());
		for (int i = 0; i < theirs.size(); i++) {
			helper.assertTrue(theirs.get(i).equals(ours.get(i)), "entrada " + i + ": el contrato dice '" + theirs.get(i) + "' y ObsV4 '" + ours.get(i) + "'");
		}
		helper.assertTrue(MobAi.V4_FORMAT.equals(contract.get("formato_red").getAsString()), "formato_red: " + contract.get("formato_red"));
		helper.assertTrue(contract.get("alcance_v").getAsInt() == ObsV4.REACH_VERSION, "alcance_v: " + contract.get("alcance_v"));
		helper.assertTrue(contract.get("n_salidas").getAsInt() == NetBrain.V4_OUTPUTS, "n_salidas: " + contract.get("n_salidas"));
		java.util.Map<String, Integer> starts = java.util.Map.ofEntries(java.util.Map.entry("V3B", 0), java.util.Map.entry("S", ObsV4.S_AT),
			java.util.Map.entry("R", ObsV4.R_AT), java.util.Map.entry("M", ObsV4.M_AT), java.util.Map.entry("Mo", ObsV4.MO_AT),
			java.util.Map.entry("P", ObsV4.P_AT), java.util.Map.entry("E", ObsV4.E_AT), java.util.Map.entry("A", ObsV4.A_AT),
			java.util.Map.entry("O", ObsV4.O_AT), java.util.Map.entry("C", ObsV4.C_AT), java.util.Map.entry("G", ObsV4.G_AT),
			java.util.Map.entry("L", ObsV4.L_AT), java.util.Map.entry("W", ObsV4.W_AT));
		for (var block : contract.getAsJsonArray("bloques")) {
			JsonObject b = block.getAsJsonObject();
			String name = b.get("bloque").getAsString();
			helper.assertTrue(starts.containsKey(name) && starts.get(name) == b.get("inicio").getAsInt(),
				"el bloque " + name + " empieza en " + b.get("inicio") + " en el contrato y en " + starts.get(name) + " en ObsV4");
		}
		java.util.Map<String, int[]> heads = new java.util.HashMap<>();
		heads.put("mover", range(0, 9));
		heads.put("saltar", new int[] {9});
		heads.put("usar", new int[] {10});
		heads.put("tactica", NetBrain.tacticLogits(NetBrain.V4_OUTPUTS));
		heads.put("especial", range(NetBrain.SPECIAL_AT, 5));
		heads.put("defensa", range(NetBrain.DEFENSE_AT, 3));
		heads.put("fintar", new int[] {NetBrain.FEINT_AT});
		heads.put("correr", new int[] {NetBrain.RUN_AT});
		heads.put("objeto", range(NetBrain.OBJECT_AT, NetBrain.OBJECTS));
		heads.put("furia", new int[] {NetBrain.FURY_AT});
		heads.put("golpe_escudo", new int[] {NetBrain.SHIELD_BASH_AT});
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
					+ java.util.Arrays.toString(heads.get(name)) + " en NetBrain");
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

	/**
	 * A zombie with a sword and a skeleton behind it, both on a player: each one's v4 observation is 468 finite numbers,
	 * the blocks not brought in yet (M to W) are 0, and yo_arma_alcance/6 is the full reach ("alcance_v": 2).
	 */
	@GameTest(maxTicks = 60)
	public void v4ObservationHas468FiniteInputs(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(helper.getLevel());
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(6, 1, 3)));
		player.setPos(at.x, at.y, at.z);
		helper.onEachTick(() -> {
			player.setHealth(player.getMaxHealth());
			player.setDeltaMovement(Vec3.ZERO);
		});
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		Mob skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 3));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setNoAi(true);
		skeleton.setNoAi(true);
		zombie.setTarget(player);
		skeleton.setTarget(player);
		int reachAt = ObsV4.names().indexOf("yo_arma_alcance/6");
		helper.assertTrue(reachAt >= 0 && reachAt < ObsV4.BASE, "yo_arma_alcance/6 debería estar entre las 280 de v3b: " + reachAt);
		helper.runAfterDelay(12, () -> {
			for (Mob mob : new Mob[] {zombie, skeleton}) {
				MobMind mind = MobAi.mind(mob);
				helper.assertTrue(mind != null, "el mob debería tener cerebro");
				mind.target = player;
				float[] obs = ObsV4.build(mob, player, mind);
				helper.assertTrue(obs.length == 468, "la observación v4 debería tener 468 entradas: " + obs.length);
				for (int i = 0; i < obs.length; i++) {
					helper.assertTrue(Float.isFinite(obs[i]), "la entrada " + i + " (" + ObsV4.names().get(i) + ") no es finita: " + obs[i]);
				}
				// The blocks still to come (M, Mo, W) are 0; P, E, A, O, C, G and L came with M2 to M4.
				for (int i = ObsV4.M_AT; i < obs.length; i++) {
					boolean live = i >= ObsV4.P_AT && i < ObsV4.W_AT;
					helper.assertTrue(live || obs[i] == 0.0F, "la entrada " + i + " (" + ObsV4.names().get(i) + ") debería ser 0 aún: " + obs[i]);
				}
				helper.assertTrue(obs[ObsV4.G_AT + 3] == 1.0F, "bloqueo_hace/20 sin bloqueo es 1: " + obs[ObsV4.G_AT + 3]);
				helper.assertTrue(obs[ObsV4.A_AT + 3] == 2.0F, "jug_borde/2 en llano es el tope, 2: " + obs[ObsV4.A_AT + 3]);
				float sectors = obs[ObsV4.S_AT] + obs[ObsV4.S_AT + 1] + obs[ObsV4.S_AT + 2] + obs[ObsV4.S_AT + 3];
				helper.assertTrue(sectors == 0.0F || sectors == 1.0F, "los sectores son un one-hot (o nada sin hueco): " + sectors);
				helper.assertTrue(mind.reachVersion == ObsV4.REACH_VERSION, "una observación v4 lee el alcance con alcance_v 2");
			}
			float[] obs = ObsV4.build(zombie, player, MobAi.mind(zombie));
			double full = Reach.actionOf(new ItemStack(Items.IRON_SWORD)) / 6.0;
			helper.assertTrue(Math.abs(obs[reachAt] - full) < 1.0E-4, "yo_arma_alcance/6 con espada debería ser el alcance real " + full + ": " + obs[reachAt]);
			helper.assertTrue(obs[ObsV4.R_AT] == 0.0F, "sin lanza no hay alcance mínimo: " + obs[ObsV4.R_AT]);
			zombie.discard();
			skeleton.discard();
			helper.succeed();
		});
	}

	/**
	 * A v4 network with small random weights (seeded), the contract's 468 inputs and 53 outputs, and its output biases
	 * as given.
	 */
	static JsonObject fakeV4(long seed, float[] outBias) {
		List<String> names = ObsV4.names();
		int n = names.size();
		int hidden = 8;
		int memory = 6;
		Random random = new Random(seed);
		JsonObject json = new JsonObject();
		json.addProperty("formato", MobAi.V4_FORMAT);
		json.addProperty("grupo", "cuerpo");
		json.addProperty("ticks_por_decision", 2);
		json.addProperty("alcance_v", ObsV4.REACH_VERSION);
		JsonArray namesJson = new JsonArray();
		names.forEach(namesJson::add);
		json.add("nombres_obs", namesJson);
		json.add("w1", noise(random, n, hidden));
		json.add("b1", noise(random, 1, hidden).get(0));
		json.add("w2", noise(random, hidden, hidden));
		json.add("b2", noise(random, 1, hidden).get(0));
		json.add("gru_ih", noise(random, 3 * memory, hidden));
		json.add("gru_hh", noise(random, 3 * memory, memory));
		json.add("gru_bih", noise(random, 1, 3 * memory).get(0));
		json.add("gru_bhh", noise(random, 1, 3 * memory).get(0));
		json.add("w_out", noise(random, hidden + memory, NetBrain.V4_OUTPUTS));
		JsonArray bias = new JsonArray();
		for (int k = 0; k < NetBrain.V4_OUTPUTS; k++) {
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

	/**
	 * A v4 file in redes_v4 takes the family over with iaContrato "auto" or "v4", ahead of its v3 file; with "v3" the
	 * v3 file is used and redes_v4 is not looked at.
	 */
	@GameTest
	public void v4NetworkInRedesV4IsLoaded(GameTestHelper helper) throws java.io.IOException {
		CombatConfig cfg = CombatConfig.get();
		String savedFolder = cfg.iaCarpetaRedes;
		String savedContract = cfg.iaContrato;
		Path root = Files.createTempDirectory("forja_redes");
		Path v3 = Files.createDirectories(root.resolve("redes"));
		Path v4 = Files.createDirectories(root.resolve("redes_v4"));
		com.google.gson.Gson gson = new com.google.gson.Gson();
		Files.writeString(v3.resolve("red_cuerpo.json"), gson.toJson(AiGameTests.fakeV2(new float[NetBrain.V2_OUTPUTS])));
		Files.writeString(v4.resolve("red_cuerpo.json"), gson.toJson(fakeV4(7L, new float[0])));
		try {
			cfg.iaCarpetaRedes = v3.toAbsolutePath().toString();
			for (String contract : new String[] {"auto", "v4"}) {
				cfg.iaContrato = contract;
				MobAi.reload();
				NetBrain cuerpo = MobAi.net("cuerpo");
				helper.assertTrue(cuerpo != null && MobAi.V4_FORMAT.equals(cuerpo.format) && cuerpo.inputs() == 468 && cuerpo.outputs() == 53,
					"con iaContrato " + contract + " el cuerpo debería usar su red v4: " + (cuerpo == null ? null : cuerpo.format)
						+ " " + MobAi.problems());
				helper.assertTrue(!MobAi.problems().containsKey("cuerpo"), "la v4 buena no debería dejar problemas: " + MobAi.problems());
			}
			cfg.iaContrato = "v3";
			MobAi.reload();
			NetBrain cuerpo = MobAi.net("cuerpo");
			helper.assertTrue(cuerpo != null && "red_mob_v2".equals(cuerpo.format),
				"con iaContrato v3 el cuerpo debería usar su red de redes: " + (cuerpo == null ? null : cuerpo.format));
		} finally {
			cfg.iaCarpetaRedes = savedFolder;
			cfg.iaContrato = savedContract;
			MobAi.reload();
		}
		helper.succeed();
	}

	/**
	 * A fake v4 network drives a zombie for 40 ticks: it decides every 2 ticks without an exception, each time from 468
	 * finite inputs into 53 finite logits. FORMACION, a tactic that needs a post from the captain (M5), is given a huge
	 * bias and is never run: the mask shuts it, as it shuts "curarse" with nothing in the kit.
	 */
	@GameTest(maxTicks = 80)
	public void fakeV4NetworkDrivesAZombie(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		float[] bias = new float[NetBrain.V4_OUTPUTS];
		// FORMACION (a post from the captain, M5) is still shut, and "curarse" without a potion in the kit
		bias[NetBrain.V4_TACTICS_AT + dev.forja.ai.Tactic.FORMACION.ordinal() - dev.forja.ai.Tactic.V3_COUNT] = 20.0F;
		bias[NetBrain.OBJECT_AT + 1] = 20.0F;
		NetBrain net = NetBrain.fromJson(fakeV4(11L, bias));
		helper.assertTrue(MobAi.checkV4(net) == null, "la red v4 falsa debería encajar: " + MobAi.checkV4(net));
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
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		MobMind mind = MobAi.mind(zombie);
		helper.assertTrue(mind != null, "el zombi debería tener cerebro");
		mind.override = net;
		zombie.setTarget(player);
		long[] last = {Long.MIN_VALUE};
		int[] decisions = {0};
		int[] notFree = {0};
		helper.onEachTick(() -> {
			if (!zombie.isAlive() || !mind.networked || mind.decidedAt == last[0]) {
				return;
			}
			last[0] = mind.decidedAt;
			decisions[0]++;
			helper.assertTrue(mind.lastObs != null && mind.lastObs.length == 468, "la red v4 debería recibir 468 entradas: "
				+ (mind.lastObs == null ? null : mind.lastObs.length));
			for (float v : mind.lastObs) {
				helper.assertTrue(Float.isFinite(v), "entrada no finita en la observación v4");
			}
			helper.assertTrue(mind.lastLogits != null && mind.lastLogits.length == NetBrain.V4_OUTPUTS, "la red v4 debería dar 53 salidas");
			for (float v : mind.lastLogits) {
				helper.assertTrue(Float.isFinite(v), "salida no finita de la red v4");
			}
			Decision decision = mind.decision;
			helper.assertTrue(decision != null && decision.tactic() != Tactic.FORMACION && decision.tactic() != Tactic.EMBOSCAR
				&& decision.tactic() != Tactic.BUSCAR, "FORMACION, EMBOSCAR y BUSCAR siguen enmascaradas: " + (decision == null ? null : decision.tactic()));
			helper.assertTrue(decision.item() == 0, "sin nada en la mochila la cabeza de objeto sale 'nada': " + decision.item());
			notFree[0] += decision.tactic() != Tactic.LIBRE ? 1 : 0;
		});
		helper.runAfterDelay(41, () -> {
			helper.assertTrue(decisions[0] >= 15, "en 40 ticks la red v4 debería decidir unas 20 veces: " + decisions[0]);
			helper.assertTrue(notFree[0] > 0, "FORMACION está enmascarada: las tácticas deberían repartirse entre las abiertas, no caer siempre en LIBRE");
			zombie.discard();
			helper.succeed();
		});
	}
}
