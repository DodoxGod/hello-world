package dev.forja.test;

import dev.forja.ai.Ambush;
import dev.forja.ai.Hearing;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsV4;
import dev.forja.ai.Perception;
import dev.forja.ai.Tactic;
import dev.forja.combat.CombatConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Step M4 of the v4 mob network (docs/red_mob_v4_diseno.md §4.4 and §4.5): what monsters hear, the estimate of where a
 * lost player is (and that a v4 network never sees the real position), BUSCAR's search, hunting beyond vanilla's 3
 * seconds, the hiding spot and EMBOSCAR, and the player's surroundings in block E.
 */
public class RedV4PercepcionGameTests {
	private static void floor(GameTestHelper helper, int size) {
		if (size > 8) {
			force(helper, size, true);
			for (int x = -1; x <= size; x++) {
				for (int z = -1; z <= size; z++) {
					for (int y = 0; y <= 10; y++) {
						BlockPos at = new BlockPos(x, y, z);
						if (helper.getBlockState(at).is(Blocks.BARRIER)) {
							helper.setBlock(at, Blocks.AIR);
						}
					}
				}
			}
		}
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static void clear(GameTestHelper helper, int size, int height) {
		for (int x = -1; x <= size; x++) {
			for (int z = -1; z <= size; z++) {
				for (int y = 1; y <= height; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		AABB box = new AABB(helper.absoluteVec(new Vec3(-4, -2, -4)), helper.absoluteVec(new Vec3(size + 4, height + 4, size + 4)));
		helper.getLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player)).forEach(Entity::discard);
		if (size > 8) {
			force(helper, size, false);
		}
	}

	/** Forces (or lets go of) the chunks under a floor bigger than a test's box: entities only tick in those that are. */
	private static void force(GameTestHelper helper, int size, boolean on) {
		BlockPos a = helper.absolutePos(new BlockPos(-1, 0, -1));
		BlockPos c = helper.absolutePos(new BlockPos(size, 0, size));
		for (int cx = Math.min(a.getX(), c.getX()) >> 4; cx <= Math.max(a.getX(), c.getX()) >> 4; cx++) {
			for (int cz = Math.min(a.getZ(), c.getZ()) >> 4; cz <= Math.max(a.getZ(), c.getZ()) >> 4; cz++) {
				helper.getLevel().setChunkForced(cx, cz, on);
			}
		}
	}


	/** A stone wall along x = {@code x}, from z0 to z1, 3 high. */
	private static void wall(GameTestHelper helper, int x, int z0, int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
			}
		}
	}

	private static Zombie zombie(GameTestHelper helper, BlockPos at) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		return zombie;
	}

	private static MobMind mind(GameTestHelper helper, Zombie zombie) {
		MobMind mind = MobAi.mind(zombie);
		helper.assertTrue(mind != null, "el zombi debería tener cerebro");
		return mind;
	}

	private static void place(GameTestHelper helper, Player player, Vec3 relative) {
		Vec3 at = helper.absoluteVec(relative);
		player.setPos(at.x, at.y, at.z);
	}

	/**
	 * Through a wall a sound carries half as far: mining (16) behind a wall 5 blocks off is heard (8), a step (6) is not
	 * (3). What it heard becomes where it thinks the player is, with its kind (work).
	 */
	@GameTest(maxTicks = 40)
	public void soundsCarryHalfAsFarThroughAWall(GameTestHelper helper) {
		floor(helper, 8);
		wall(helper, 4, 0, 7);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(6, 1, 3));
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 3));
		zombie.setNoAi(true);
		zombie.setTarget(player);
		MobMind mind = mind(helper, zombie);
		helper.onEachTick(() -> {
			zombie.setTarget(player);
			player.setHealth(player.getMaxHealth());
		});
		helper.runAfterDelay(3, () -> helper.getLevel().gameEvent(GameEvent.STEP, player.position(), GameEvent.Context.of(player)));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(mind.lastHeard == null, "un paso tras la pared a 5 bloques no se oye (6 / 2 = 3)");
			helper.getLevel().gameEvent(GameEvent.BLOCK_DESTROY, player.position(), GameEvent.Context.of(player));
		});
		helper.runAfterDelay(9, () -> {
			helper.assertTrue(mind.lastHeard != null && mind.lastHeard.distanceTo(player.position()) < 0.5,
				"picar tras la pared a 5 bloques sí se oye (16 / 2 = 8): " + mind.lastHeard);
			helper.assertTrue(mind.sound0 != null && mind.sound0.kind() == Hearing.WORK && mind.sound0.radius() == 16.0, "el sonido es de trabajo: " + mind.sound0);
			helper.assertTrue(player.position().equals(Perception.estimate(mind)), "lo oído es dónde cree que está");
			float[] obs = ObsV4.build(zombie, player, mind);
			helper.assertTrue(obs[ObsV4.P_AT + 1] == 1.0F && obs[ObsV4.P_AT + 7] == 1.0F && obs[ObsV4.P_AT + 7 + 7] == 1.0F,
				"obj_oido, sonido0_presente y sonido0_trabajo: " + obs[ObsV4.P_AT + 1] + " " + obs[ObsV4.P_AT + 7] + " " + obs[ObsV4.P_AT + 14]);
			zombie.discard();
			clear(helper, 8, 3);
			helper.succeed();
		});
	}

	/**
	 * A v4 network on a zombie: while it sees the player, obj_distancia/16 is their real distance; once they are behind
	 * a wall it is the distance to where it last saw them, and it stays so however the real player moves (they are
	 * silent): the real position never reaches the network. obj_percibido goes to 0 and obj_edad counts up.
	 */
	@GameTest(maxTicks = 80)
	public void v4EstimateNeverLeaksTheRealPosition(GameTestHelper helper) {
		floor(helper, 8);
		wall(helper, 4, 0, 5);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(2, 1, 6));
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		MobMind mind = mind(helper, zombie);
		mind.override = NetBrain.fromJson(RedV4GameTests.fakeV4(13L, new float[0]));
		zombie.setTarget(player);
		Vec3 seenAt = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(2, 1, 6)));
		helper.onEachTick(() -> {
			zombie.setTarget(player);
			player.setHealth(player.getMaxHealth());
		});
		float[] seen = new float[2];
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(mind.lastObs != null && mind.lastObs[ObsV4.P_AT] == 1.0F, "al principio lo percibe");
			seen[0] = mind.lastObs[0];
			// behind the wall, and silent (a test player makes no sounds by itself)
			place(helper, player, new Vec3(6.5, 1.0, 2.5));
		});
		helper.runAfterDelay(30, () -> {
			float[] obs = mind.lastObs;
			double toSeen = zombie.position().distanceTo(seenAt) / 16.0;
			double toReal = zombie.position().distanceTo(player.position()) / 16.0;
			helper.assertTrue(obs[ObsV4.P_AT] == 0.0F, "tras la pared no lo percibe");
			helper.assertTrue(Math.abs(obs[0] - toSeen) < 0.02, "obj_distancia/16 debería ser la de donde lo vio (" + toSeen + "), no la real ("
				+ toReal + "): " + obs[0]);
			helper.assertTrue(obs[ObsV4.P_AT + 2] > 0.1F, "obj_edad/100 cuenta: " + obs[ObsV4.P_AT + 2]);
			seen[1] = obs[0];
			place(helper, player, new Vec3(6.5, 1.0, 0.5));
		});
		helper.runAfterDelay(50, () -> {
			float[] obs = mind.lastObs;
			helper.assertTrue(Math.abs(obs[0] - seen[1]) < 1.0E-4, "aunque el jugador real se mueva, lo que ve la red no cambia: " + seen[1] + " -> " + obs[0]);
			zombie.discard();
			clear(helper, 8, 3);
			helper.succeed();
		});
	}

	/**
	 * A zombie on the rules sees the player at one spot; they vanish far behind a wall. It goes to where it saw them
	 * (BUSCAR) and then on to the points of its fan beyond, and never comes within 3 blocks of where they really are.
	 * Then a sound (mining) at a new spot sends it there.
	 */
	@GameTest(padding = 16, maxTicks = 360)
	public void zombieSearchesThenFollowsTheSound(GameTestHelper helper) {
		floor(helper, 16);
		wall(helper, 8, 0, 15);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(4, 1, 8));
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1));
		zombie.setTarget(player);
		MobMind mind = mind(helper, zombie);
		Vec3 hidden = helper.absoluteVec(new Vec3(13.5, 1.0, 13.5));
		Vec3 noise = helper.absoluteVec(new Vec3(4.5, 1.0, 14.5));
		boolean[] vanished = {false};
		int[] searching = {0};
		double[] far = {0.0};
		Vec3[] seenAt = {null};
		helper.onEachTick(() -> {
			zombie.setTarget(player);
			player.setHealth(player.getMaxHealth());
			if (vanished[0]) {
				player.setPos(hidden.x, hidden.y, hidden.z);
				helper.assertTrue(zombie.distanceToSqr(hidden.x, zombie.getY(), hidden.z) > 3.0 * 3.0,
					"el zombi llegó a 3 bloques de donde está el jugador, sin percibirlo: " + zombie.position());
				if (mind.decision.tactic() == Tactic.BUSCAR) {
					searching[0]++;
				}
				if (seenAt[0] != null) {
					far[0] = Math.max(far[0], zombie.position().distanceTo(seenAt[0]));
				}
			}
		});
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(mind.lastSeen != null, "a los 6 ticks ya lo ha visto");
			seenAt[0] = mind.lastSeen;
			vanished[0] = true;
		});
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(searching[0] > 60, "sin percibirlo, las reglas buscan (BUSCAR): " + searching[0]);
			helper.assertTrue(mind.searchStage >= 1, "llegó a donde lo vio y siguió por el abanico: etapa " + mind.searchStage);
			helper.assertTrue(far[0] > 3.0, "el abanico lo lleva más allá de donde lo vio: " + far[0]);
			// the player, unseen behind the wall, digs: the zombie goes there
			helper.getLevel().gameEvent(GameEvent.BLOCK_DESTROY, noise, GameEvent.Context.of(player));
		});
		helper.runAfterDelay(340, () -> {
			helper.assertTrue(mind.lastHeard != null && mind.lastHeard.distanceTo(noise) < 0.5, "debería haber oído picar: " + mind.lastHeard);
			double d = Math.sqrt(zombie.distanceToSqr(noise.x, zombie.getY(), noise.z));
			helper.assertTrue(d < 7.0, "el zombi debería ir hacia el ruido y buscar allí: está a " + d);
			zombie.discard();
			clear(helper, 16, 3);
			helper.succeed();
		});
	}

	/**
	 * Vanilla lets go of a player unseen for 3 seconds (a target goal's unseen memory). A zombie hunting a player it
	 * saw keeps them as its target while it saw or heard them within 30 s; with honest perception off, vanilla's rule.
	 */
	@GameTest(maxTicks = 140)
	public void huntingOutlastsVanillasThreeSeconds(GameTestHelper helper) {
		floor(helper, 8);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(6, 1, 3));
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 3));
		zombie.setNoAi(true);
		zombie.setTarget(player);
		MobMind mind = mind(helper, zombie);
		NearestAttackableTargetGoal<Player> goal = new NearestAttackableTargetGoal<>(zombie, Player.class, true);
		helper.onEachTick(() -> player.setHealth(player.getMaxHealth()));
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(mind.lastSeen != null, "ya lo ha visto");
			goal.start();
			wall(helper, 4, 0, 7);
		});
		boolean[] kept = {true};
		helper.onEachTick(() -> {
			if (helper.getLevel().getGameTime() > 0 && mind.lastSeen != null && zombie.getTarget() == player) {
				kept[0] &= goal.canContinueToUse();
			}
		});
		helper.runAfterDelay(110, () -> {
			helper.assertTrue(kept[0], "cazando, no lo suelta a los 3 s sin verlo");
			boolean honest = CombatConfig.get().iaPercepcionHonesta;
			CombatConfig.get().iaPercepcionHonesta = false;
			boolean vanilla = true;
			for (int i = 0; i < 200 && vanilla; i++) {
				vanilla = goal.canContinueToUse();
			}
			CombatConfig.get().iaPercepcionHonesta = honest;
			helper.assertFalse(vanilla, "sin percepción honesta, la regla vanilla lo suelta");
			zombie.discard();
			clear(helper, 8, 3);
			helper.succeed();
		});
	}

	/** Block E's view of the player's surroundings: a 1-wide corridor under a roof, a doorway, and the open. */
	@GameTest(maxTicks = 20)
	public void corridorDoorAndRoof(GameTestHelper helper) {
		floor(helper, 8);
		// a corridor along x at z = 1, walls at z = 0 and z = 2, roof at y = 3
		for (int x = 0; x <= 4; x++) {
			for (int y = 1; y <= 2; y++) {
				helper.setBlock(new BlockPos(x, y, 0), Blocks.STONE);
				helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
			}
			helper.setBlock(new BlockPos(x, 3, 1), Blocks.STONE);
		}
		helper.setBlock(new BlockPos(6, 1, 6), Blocks.OAK_DOOR);
		CombatGameTests.TestPlayer inside = CombatGameTests.player(helper, new BlockPos(2, 1, 1));
		inside.setYRot(-90.0F);
		CombatGameTests.TestPlayer door = CombatGameTests.player(helper, new BlockPos(6, 1, 6));
		CombatGameTests.TestPlayer open = CombatGameTests.player(helper, new BlockPos(6, 1, 3));
		helper.assertTrue(Ambush.corridor(inside) && Ambush.roofed(inside), "en el pasillo con techo: pasillo " + Ambush.corridor(inside)
			+ ", techo " + Ambush.roofed(inside));
		helper.assertTrue(Ambush.doorway(door), "en la puerta");
		helper.assertFalse(Ambush.corridor(open) || Ambush.roofed(open) || Ambush.doorway(open), "al aire libre nada");
		clear(helper, 8, 3);
		helper.succeed();
	}

	/**
	 * A wall between the player and the zombie's side: the zombie has a hiding spot out of the player's sight. A v4
	 * network that wants EMBOSCAR goes there, stays still and its yo_emboscado/200 counts up; the player never has a
	 * line to its hiding spot.
	 */
	@GameTest(maxTicks = 500)
	public void zombieLiesInWaitOutOfSight(GameTestHelper helper) {
		floor(helper, 8);
		wall(helper, 3, 2, 6);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(6, 1, 4));
		Zombie zombie = zombie(helper, new BlockPos(4, 1, 1));
		MobMind mind = mind(helper, zombie);
		float[] bias = new float[NetBrain.V4_OUTPUTS];
		bias[NetBrain.V4_TACTICS_AT + Tactic.EMBOSCAR.ordinal() - Tactic.V3_COUNT] = 20.0F;
		mind.override = NetBrain.fromJson(RedV4GameTests.fakeV4(17L, bias));
		zombie.setTarget(player);
		Vec3 at = player.position();
		helper.onEachTick(() -> {
			zombie.setTarget(player);
			player.setPos(at.x, at.y, at.z);
			player.setHealth(player.getMaxHealth());
		});
		helper.runAfterDelay(3, () -> {
			Ambush.Spot spot = Ambush.spot(zombie, mind, player, helper.getLevel().getGameTime());
			helper.assertTrue(spot != null, "debería haber un escondite tras la pared");
			helper.assertTrue(Ambush.hidden(zombie, player, spot.pos()), "el escondite queda fuera de la vista del jugador");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(mind.decision.tactic() == Tactic.EMBOSCAR, "la red elige EMBOSCAR: " + mind.decision.tactic());
			helper.assertTrue(mind.ambushSince > Long.MIN_VALUE / 4 && helper.getLevel().getGameTime() - mind.ambushSince > 20,
				"lleva un rato emboscado en su escondite: desde " + mind.ambushSince + " (ahora " + helper.getLevel().getGameTime() + "), en "
					+ zombie.position() + ", escondite " + mind.hideSpot + ", perdido " + dev.forja.ai.HonestPerception.lost(mind, helper.getLevel().getGameTime()));
			helper.assertTrue(Ambush.hidden(zombie, player, zombie.position()), "y el jugador no lo ve: " + zombie.position());
			float[] obs = mind.lastObs;
			helper.assertTrue(obs[ObsV4.E_AT + 20] > 0.05F && obs[ObsV4.E_AT + 13] == 0.0F,
				"yo_emboscado/200 > 0 y me_ve_jugador = 0: " + obs[ObsV4.E_AT + 20] + " " + obs[ObsV4.E_AT + 13]);
			zombie.discard();
			clear(helper, 8, 3);
		});
	}
}
