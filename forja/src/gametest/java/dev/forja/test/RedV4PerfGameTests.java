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
	private static final int WARMUP = 60;
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

	@GameTest(padding = 24, maxTicks = WARMUP * 2 + WINDOW * 2 + 40)
	public void v4AiOf30MobsFitsTheBudget(GameTestHelper helper) throws java.io.IOException {
		int size = 22;
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
				}
			}
		});
		Path folder = Files.createTempDirectory("forja_rendimiento_v4");
		Perf.Result[] v4 = {null};
		helper.runAfterDelay(1, () -> {
			for (Mob mob : mobs) {
				MobMind mind = MobAi.mind(mob);
				if (mind != null && mob.getType() != EntityTypes.CREEPER) {
					mind.override = net;
				}
			}
		});
		helper.runAfterDelay(WARMUP, () -> Perf.begin("v4_30"));
		helper.runAfterDelay(WARMUP + WINDOW, () -> {
			v4[0] = Perf.end(folder);
			for (Mob mob : mobs) {
				MobMind mind = MobAi.mind(mob);
				if (mind != null) {
					mind.override = null;
				}
			}
		});
		helper.runAfterDelay(WARMUP * 2 + WINDOW, () -> Perf.begin("reglas_30"));
		helper.runAfterDelay(WARMUP * 2 + WINDOW * 2, () -> {
			Perf.Result rules = Perf.end(folder);
			String report = line("rendimiento v4 (30 mobs, redes v4 del tamaño del contrato)", v4[0]) + "\n"
				+ line("rendimiento v4 (30 mobs, reglas)", rules);
			Forja.LOGGER.info(report);
			try {
				Files.writeString(folder.resolve("resumen.txt"), report);
			} catch (java.io.IOException ignored) {
				// the log has it
			}
			double ai = aiMs(v4[0]);
			AABB box = new AABB(helper.absoluteVec(new Vec3(-4, -2, -4)), helper.absoluteVec(new Vec3(size + 4, 12, size + 4)));
			helper.getLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(Entity::discard);
			helper.assertTrue(ai <= BUDGET_MS, String.format(Locale.ROOT, "la IA de 30 mobs con redes v4 cuesta %.3f ms/tick (tope %.1f)", ai, BUDGET_MS));
			helper.succeed();
		});
	}
}
