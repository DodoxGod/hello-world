package dev.forja.test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.forja.ai.Aim;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsV4;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.Ladder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Contract revision 4.1, block J: the player aims at me (docs/mod_spec_mira.md). The names and sizes of both
 * revisions, a 4.0 net still loading with 468 inputs, a 4.1 net getting 472, the crosshair ray (box, blocks and
 * other mobs in the way), the strike being ready, the threat, the neutrals, the ladder (Extremo only) and the single
 * cached ray per player and tick.
 */
public class MiraGameTests {
	private static final int AIMED = ObsV4.J_AT;
	private static final int DISTANCE = ObsV4.J_AT + 1;
	private static final int READY = ObsV4.J_AT + 2;
	private static final int THREAT = ObsV4.J_AT + 3;

	/** Revision 4.1's file in docs: 472 names, the 468 of 4.0 first and unchanged, block J last. */
	@GameTest
	public void contract41MatchesTheMod(GameTestHelper helper) throws java.io.IOException {
		Path file41 = RedV4GameTests.contractFile("red_mob_v4_contrato.json");
		Path file40 = RedV4GameTests.contractFile("red_mob_v4_contrato_v40.json");
		helper.assertTrue(file41 != null && file40 != null, "faltan docs/red_mob_v4_contrato.json o su _v40");
		JsonObject c41 = JsonParser.parseString(Files.readString(file41)).getAsJsonObject();
		JsonObject c40 = JsonParser.parseString(Files.readString(file40)).getAsJsonObject();
		helper.assertTrue("4.1".equals(c41.get("revision").getAsString()), "revision: " + c41.get("revision"));
		helper.assertTrue(!c40.has("revision"), "la 4.0 no lleva revision");
		helper.assertTrue(c41.get("n_obs").getAsInt() == 472 && ObsV4.SIZE_41 == 472, "n_obs: " + c41.get("n_obs"));
		helper.assertTrue(c40.get("n_obs").getAsInt() == 468 && ObsV4.SIZE == 468, "n_obs 4.0: " + c40.get("n_obs"));
		List<String> theirs = new ArrayList<>();
		c41.getAsJsonArray("nombres_obs").forEach(name -> theirs.add(name.getAsString()));
		List<String> old = new ArrayList<>();
		c40.getAsJsonArray("nombres_obs").forEach(name -> old.add(name.getAsString()));
		List<String> ours = ObsV4.names41();
		helper.assertTrue(theirs.size() == 472 && ours.size() == 472, "472 nombres: " + theirs.size() + " / " + ours.size());
		for (int i = 0; i < 472; i++) {
			helper.assertTrue(theirs.get(i).equals(ours.get(i)), "entrada " + i + ": el contrato dice '" + theirs.get(i) + "' y ObsV4 '" + ours.get(i) + "'");
			helper.assertTrue(i >= 468 || old.get(i).equals(theirs.get(i)), "la entrada " + i + " de la 4.0 no debe cambiar");
		}
		helper.assertTrue(ObsV4.names().equals(old), "ObsV4.names() sigue siendo la 4.0");
		boolean block = false;
		for (var b : c41.getAsJsonArray("bloques")) {
			JsonObject j = b.getAsJsonObject();
			if ("J".equals(j.get("bloque").getAsString())) {
				block = j.get("inicio").getAsInt() == ObsV4.J_AT && j.get("n").getAsInt() == 4;
			}
		}
		helper.assertTrue(block, "el bloque J empieza en 468 y tiene 4");
		helper.assertTrue(c41.get("n_salidas").getAsInt() == NetBrain.V4_OUTPUTS, "las salidas no cambian");
		helper.succeed();
	}

	/** A 4.0 net (no revision) loads with its 468; a 4.1 net with its 472; a mixed-up one does not. */
	@GameTest
	public void revisionsLoadByTheirOwnNames(GameTestHelper helper) {
		NetBrain v40 = NetBrain.fromJson(RedV4GameTests.fakeV4(3L, new float[0]));
		helper.assertTrue(v40.inputs() == 468 && MobAi.checkV4(v40) == null, "la 4.0 carga con 468: " + MobAi.checkV4(v40));
		NetBrain v41 = NetBrain.fromJson(RedV4GameTests.fakeV4(3L, new float[0], true));
		helper.assertTrue(v41.inputs() == 472 && "4.1".equals(v41.revision) && MobAi.checkV4(v41) == null, "la 4.1 carga con 472: " + MobAi.checkV4(v41));
		JsonObject lie = RedV4GameTests.fakeV4(3L, new float[0]);
		lie.addProperty("revision", "4.1");
		helper.assertTrue(MobAi.checkV4(NetBrain.fromJson(lie)) != null, "una red de 468 que dice ser 4.1 se rechaza");
		JsonObject odd = RedV4GameTests.fakeV4(3L, new float[0], true);
		odd.addProperty("revision", "9.9");
		helper.assertTrue(MobAi.checkV4(NetBrain.fromJson(odd)) != null, "una revisión desconocida se rechaza");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the ray

	/** A rested player at (x, 1, 3.5), looking along +X, on a stone floor. */
	private static CombatGameTests.TestPlayer playerAt(GameTestHelper helper, double x) {
		for (int xx = 0; xx < 8; xx++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(xx, 0, z), Blocks.STONE);
			}
		}
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, 3.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(-90.0F);
		player.setYHeadRot(-90.0F);
		player.setXRot(0.0F);
		strength(player, 1000);
		return player;
	}

	private static void strength(LivingEntity player, int ticks) {
		try {
			Field ticker = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			ticker.setAccessible(true);
			ticker.setInt(player, ticks);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("attackStrengthTicker", failure);
		}
	}

	/** A zombie without AI standing {@code ahead} blocks in front of (+X) the player, centre to centre. */
	private static Zombie zombieAhead(GameTestHelper helper, CombatGameTests.TestPlayer player, double ahead) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 3));
		zombie.setNoAi(true);
		zombie.setPos(player.getX() + ahead, player.getY(), player.getZ());
		return zombie;
	}

	/** The 472 inputs of a mob as a 4.1 net sees them at a ladder level, with the mob perceiving the player (or not). */
	private static float[] observe(Mob mob, CombatGameTests.TestPlayer player, boolean perceives, Ladder level) {
		MobMind mind = MobAi.mind(mob);
		mind.target = player;
		mind.perceivedAt = perceives ? mob.level().getGameTime() : Long.MIN_VALUE / 4;
		CombatConfig cfg = CombatConfig.get();
		String saved = cfg.nivel;
		try {
			cfg.nivel = level.name();
			return ObsV4.build(mob, player, mind, true);
		} finally {
			cfg.nivel = saved;
		}
	}

	private static boolean close(float a, double b) {
		return Math.abs(a - b) < 0.01;
	}

	/** A zombie at 2.5 on the crosshair, strike ready: aimed, entry at 2.2, threat; the one behind it is covered. */
	@GameTest
	public void aimedAtWithReadyStrikeIsAThreat(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = playerAt(helper, 0.5);
		Zombie front = zombieAhead(helper, player, 2.5);
		Zombie behind = zombieAhead(helper, player, 4.5);
		helper.runAfterDelay(12, () -> {
			long before = Aim.casts();
			float[] a = observe(front, player, true, Ladder.EXTREMO);
			float[] b = observe(behind, player, true, Ladder.EXTREMO);
			helper.assertTrue(a.length == 472, "472 entradas: " + a.length);
			for (float v : a) {
				helper.assertTrue(Float.isFinite(v), "entrada no finita");
			}
			helper.assertTrue(a[AIMED] == 1.0F && close(a[DISTANCE], 2.2 / 6.0), "apunta al de delante a 2,2: " + a[AIMED] + " " + a[DISTANCE]);
			helper.assertTrue(close(a[READY], 1.0) && a[THREAT] == 1.0F, "golpe lleno y a su alcance: amenaza " + a[THREAT] + " listo " + a[READY]);
			helper.assertTrue(b[AIMED] == 0.0F && b[DISTANCE] == 0.0F && b[THREAT] == 0.0F, "el de detrás está tapado: " + b[AIMED] + " " + b[THREAT]);
			helper.assertTrue(close(b[READY], 1.0), "jug_golpe_listo se da aunque no apunte: " + b[READY]);
			helper.assertTrue(Aim.casts() - before == 1, "un rayo por jugador y tick, aunque pregunten dos mobs: " + (Aim.casts() - before));
			front.discard();
			behind.discard();
			helper.succeed();
		});
	}

	/** Out of reach (4): aimed, no threat. */
	@GameTest
	public void outOfReachIsNoThreat(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = playerAt(helper, 0.5);
		Zombie near = zombieAhead(helper, player, 4.0);
		helper.runAfterDelay(12, () -> {
			float[] a = observe(near, player, true, Ladder.EXTREMO);
			helper.assertTrue(a[AIMED] == 1.0F && a[THREAT] == 0.0F, "a 4 apunta pero fuera de alcance 3: " + a[AIMED] + " " + a[THREAT]);
			helper.assertTrue(close(a[DISTANCE], 3.7 / 6.0), "distancia 3,7/6: " + a[DISTANCE]);
			near.discard();
			helper.succeed();
		});
	}

	/** Beyond the ray's 6 blocks (7.7 centre to centre): nothing. */
	@GameTest
	public void beyondTheRayIsNothing(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = playerAt(helper, 0.2);
		Zombie far = zombieAhead(helper, player, 7.7);
		helper.runAfterDelay(12, () -> {
			float[] a = observe(far, player, true, Ladder.EXTREMO);
			helper.assertTrue(a[AIMED] == 0.0F && a[DISTANCE] == 0.0F && a[THREAT] == 0.0F, "a 7,7 el rayo no llega: " + a[AIMED]);
			far.discard();
			helper.succeed();
		});
	}

	/** Right after a swing the strike is far from ready (0.5 over the recharge) and there is no threat. */
	@GameTest
	public void justSwungIsNoThreat(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = playerAt(helper, 0.5);
		Zombie zombie = zombieAhead(helper, player, 2.5);
		helper.runAfterDelay(12, () -> {
			strength(player, 0);
			float expected = player.getAttackStrengthScale(0.5F);
			float[] a = observe(zombie, player, true, Ladder.EXTREMO);
			helper.assertTrue(expected < 0.2F && close(a[READY], expected), "jug_golpe_listo es getAttackStrengthScale(0,5): " + a[READY] + " / " + expected);
			helper.assertTrue(a[AIMED] == 1.0F && a[THREAT] == 0.0F, "apunta pero sin golpe listo no amenaza: " + a[THREAT]);
			zombie.discard();
			helper.succeed();
		});
	}

	/** A wall between the two: the ray stops at the block. */
	@GameTest
	public void aWallCoversTheMob(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = playerAt(helper, 0.5);
		Zombie zombie = zombieAhead(helper, player, 3.5);
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(new BlockPos(2, y, 3), Blocks.STONE);
		}
		helper.runAfterDelay(12, () -> {
			float[] a = observe(zombie, player, true, Ladder.EXTREMO);
			helper.assertTrue(a[AIMED] == 0.0F && a[DISTANCE] == 0.0F && a[THREAT] == 0.0F, "el muro tapa: " + a[AIMED] + " " + a[THREAT]);
			zombie.discard();
			helper.succeed();
		});
	}

	/** The neutrals: not perceived, or a level below Extremo, or a 4.0 observation: block J is 0 (or not there). */
	@GameTest
	public void neutralsAreZero(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = playerAt(helper, 0.5);
		Zombie zombie = zombieAhead(helper, player, 2.5);
		helper.runAfterDelay(12, () -> {
			float[] unseen = observe(zombie, player, false, Ladder.EXTREMO);
			for (int i = AIMED; i <= THREAT; i++) {
				helper.assertTrue(unseen[i] == 0.0F, "sin percibir, " + ObsV4.names41().get(i) + " es 0: " + unseen[i]);
			}
			long before = Aim.casts();
			for (Ladder level : new Ladder[] {Ladder.PACIFICO, Ladder.FACIL, Ladder.NORMAL, Ladder.DIFICIL}) {
				float[] a = observe(zombie, player, true, level);
				helper.assertTrue(a.length == 472, "una red 4.1 recibe 472 en " + level);
				for (int i = AIMED; i <= THREAT; i++) {
					helper.assertTrue(a[i] == 0.0F, "en " + level + " " + ObsV4.names41().get(i) + " no se calcula: " + a[i]);
				}
			}
			helper.assertTrue(Aim.casts() == before, "fuera de Extremo no se lanza ningún rayo");
			MobMind mind = MobAi.mind(zombie);
			mind.target = player;
			mind.perceivedAt = zombie.level().getGameTime();
			float[] v40 = ObsV4.build(zombie, player, mind);
			helper.assertTrue(v40.length == 468, "la 4.0 sigue dando 468: " + v40.length);
			helper.assertTrue(Aim.casts() == before, "la 4.0 no lanza rayo");
			zombie.discard();
			helper.succeed();
		});
	}

	/** A 4.1 net drives a zombie: 472 finite inputs at every decision. */
	@GameTest(maxTicks = 80)
	public void v41NetworkDrivesAZombie(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = playerAt(helper, 5.5);
		helper.onEachTick(() -> {
			player.setHealth(player.getMaxHealth());
			player.setDeltaMovement(Vec3.ZERO);
		});
		NetBrain net = NetBrain.fromJson(RedV4GameTests.fakeV4(11L, new float[0], true));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 3));
		MobMind mind = MobAi.mind(zombie);
		helper.assertTrue(mind != null, "el zombi tiene cerebro");
		mind.override = net;
		zombie.setTarget(player);
		int[] decisions = {0};
		long[] last = {Long.MIN_VALUE};
		helper.onEachTick(() -> {
			if (!zombie.isAlive() || !mind.networked || mind.decidedAt == last[0]) {
				return;
			}
			last[0] = mind.decidedAt;
			decisions[0]++;
			helper.assertTrue(mind.lastObs != null && mind.lastObs.length == 472, "la red 4.1 recibe 472: " + (mind.lastObs == null ? null : mind.lastObs.length));
			for (float v : mind.lastObs) {
				helper.assertTrue(Float.isFinite(v), "entrada no finita");
			}
		});
		helper.runAfterDelay(41, () -> {
			helper.assertTrue(decisions[0] >= 10, "decide unas 20 veces en 40 ticks: " + decisions[0]);
			zombie.discard();
			helper.succeed();
		});
	}
}
