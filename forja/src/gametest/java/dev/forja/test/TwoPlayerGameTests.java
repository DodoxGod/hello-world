package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.forja.Forja;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsForja;
import dev.forja.ai.ObsM1;
import dev.forja.ai.ObsNames;
import dev.forja.ai.SquadRole;
import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.ParryRhythm;
import dev.forja.combat.Stamina;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModEntities;
import dev.forja.upgrade.Frenzy;
import dev.forja.world.OilPools;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Two players at once (Andy, 2026-09-26: "probar con dos jugadores"). Most of the combat and the mob AI
 * is worked out against one player at a time — turns, squads, observations, per-player bars and states —
 * and with a second player about, state meant for one leaked onto the other. Each test here failed
 * before the fix it names, except {@link #aSquadSharesItselfOut} and {@link #perPlayerStateStaysPerPlayer},
 * which only measure what already behaved (the first also logs a question for Andy).
 */
public class TwoPlayerGameTests {
	/** A simulated player that also keeps what it was told in chat, so a test can ask. */
	public static class Listener extends CombatGameTests.TestPlayer {
		final List<Component> heard = new ArrayList<>();

		public Listener(ServerLevel level) {
			super(level);
		}

		@Override
		public void sendSystemMessage(Component message, boolean overlay) {
			this.heard.add(message);
		}

		boolean heard(String key) {
			return this.heard.stream().anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
		}
	}

	/** A player standing at a relative position, looking along +X, with nothing in hand and no spawn grace. */
	private static Listener player(GameTestHelper helper, BlockPos relative) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Listener player = new Listener(helper.getLevel());
		clearSpawnGrace(player);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
		player.setPos(pos.x, pos.y, pos.z);
		player.setYRot(-90.0F);
		player.setYHeadRot(-90.0F);
		player.setXRot(0.0F);
		player.setHealth(player.getMaxHealth());
		Stamina.forget(player);
		return player;
	}

	/** As in {@link CombatGameTests}: a fake player never ticks its spawn grace away, so it is zeroed. */
	private static void clearSpawnGrace(ServerPlayer player) {
		for (java.lang.reflect.Field field : ServerPlayer.class.getDeclaredFields()) {
			if (field.getType() == int.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
				try {
					field.setAccessible(true);
					if (field.getInt(player) == 60) {
						field.setInt(player, 0);
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
					// Not the field we are after.
				}
			}
		}
	}

	/** A plain zombie, never a veteran, elite or shield bearer, with a helmet against the sun. */
	private static Zombie zombie(GameTestHelper helper, BlockPos pos, boolean still) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		CombatConfig.get().shieldChance = 0.0;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, pos);
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setNoAi(still);
		return zombie;
	}

	/** Fills every turn the player has with still zombies, so nobody else can get one. */
	private static void takeEveryTurn(GameTestHelper helper, Mob asking, ServerPlayer player, BlockPos from) {
		int max = dev.forja.ai.Aggression.maxAttackers(asking, player);
		for (int i = 0; i < max; i++) {
			Zombie blocker = zombie(helper, from.offset(i, 0, 0), true);
			blocker.setTarget(player);
			helper.assertTrue(AttackTokens.tryAcquire(player, blocker, max), "el zombi " + i + " debería coger un turno de B");
		}
		helper.assertFalse(AttackTokens.free(player, max), "B debería tener todos sus turnos ocupados");
	}

	// --- Turns ---------------------------------------------------------------------------------------

	/**
	 * Turns are per player, and a mob that turns on the other player lets go of the first one's turn at
	 * once: in what the others count and in what the network is fed.
	 */
	@GameTest
	public void aTurnGoesWithTheTarget(GameTestHelper helper) {
		Listener a = player(helper, new BlockPos(1, 1, 1));
		Listener b = player(helper, new BlockPos(1, 1, 6));
		Zombie first = zombie(helper, new BlockPos(3, 1, 1), true);
		Zombie second = zombie(helper, new BlockPos(3, 1, 2), true);
		Zombie third = zombie(helper, new BlockPos(3, 1, 6), true);
		first.setTarget(a);
		second.setTarget(a);
		third.setTarget(b);
		helper.assertTrue(AttackTokens.tryAcquire(a, first, 2) && AttackTokens.tryAcquire(a, second, 2), "dos turnos de A");
		helper.assertFalse(AttackTokens.free(a, 2), "A no tiene más turnos");
		helper.assertTrue(AttackTokens.tryAcquire(b, third, 2), "los turnos de B son aparte de los de A");
		helper.assertTrue(AttackTokens.held(a) == 2 && AttackTokens.held(b) == 1, "A " + AttackTokens.held(a) + ", B " + AttackTokens.held(b));

		first.setTarget(b);
		helper.assertFalse(AttackTokens.holds(a, first), "al cambiar a B, el zombi suelta el turno de A");
		helper.assertTrue(AttackTokens.held(a) == 1, "a A le queda un turno ocupado, no " + AttackTokens.held(a));
		helper.assertFalse(AttackTokens.holds(b, first), "y no se lleva uno de B sin pedirlo");
		helper.assertTrue(AttackTokens.held(b) == 1, "B sigue con uno: " + AttackTokens.held(b));
		int n = ObsNames.M1.size() + ObsForja.size();
		float[] obs = ObsForja.full(second, a, MobAi.mind(second), ObsM1.of(second, a, 0, 0), n);
		float busy = obs[ObsNames.M1.size() + ObsForja.names().indexOf("turnos_ocupados/4")];
		helper.assertTrue(Math.abs(busy - 0.25F) < 1.0E-6F, "la red del otro zombi ve 1 turno ocupado en A (0,25), vio " + busy);
		helper.succeed();
	}

	/** A network that stands still and strikes, every time: the executor's side of a blow and nothing else. */
	private static NetBrain standAndStrike() {
		JsonObject json = new JsonObject();
		json.addProperty("formato", "red_mob_v1");
		json.addProperty("grupo", "cuerpo");
		json.addProperty("ticks_por_decision", 1);
		JsonArray names = new JsonArray();
		ObsNames.M1.forEach(names::add);
		json.add("nombres_obs", names);
		json.add("w1", zeros(ObsNames.M1.size(), 8));
		json.add("b1", zeros(8));
		json.add("w2", zeros(8, 8));
		json.add("b2", zeros(8));
		json.add("gru_ih", zeros(12, 8));
		json.add("gru_hh", zeros(12, 4));
		json.add("gru_bih", zeros(12));
		json.add("gru_bhh", zeros(12));
		json.add("w_out", zeros(12, NetBrain.V1_OUTPUTS));
		JsonArray bias = new JsonArray();
		for (int k = 0; k < NetBrain.V1_OUTPUTS; k++) {
			// Mover 0 (still) and usar; never a jump.
			bias.add(k == 0 || k == 10 ? 20.0F : k == 9 ? -20.0F : 0.0F);
		}
		json.add("b_out", bias);
		return NetBrain.fromJson(json);
	}

	private static JsonArray zeros(int n) {
		JsonArray a = new JsonArray();
		for (int i = 0; i < n; i++) {
			a.add(0.0F);
		}
		return a;
	}

	private static JsonArray zeros(int rows, int cols) {
		JsonArray a = new JsonArray();
		for (int i = 0; i < rows; i++) {
			a.add(zeros(cols));
		}
		return a;
	}

	/**
	 * A mob turns on the other player halfway through a blow wound up under the first one's turn. The blow
	 * does not come down on the second player, whose turns are all taken, and the first player's turn goes
	 * back. The mob is set on A; the test switches it to B the tick {@code windingUp} says its blow on A is
	 * under way.
	 */
	private static void blowDoesNotCross(GameTestHelper helper, Mob mob, Listener a, Listener b, java.util.function.BooleanSupplier windingUp) {
		mob.setTarget(a);
		takeEveryTurn(helper, mob, b, new BlockPos(5, 1, 5));
		boolean[] switched = {false};
		helper.onEachTick(() -> {
			if (!switched[0] && windingUp.getAsBoolean()) {
				mob.setTarget(b);
				switched[0] = true;
			}
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(switched[0], "el mob nunca empezó un golpe contra A");
			helper.assertTrue(b.getHealth() == b.getMaxHealth(),
				"el golpe avisado contra A cayó sobre B sin turno libre: B tiene " + b.getHealth() + " de " + b.getMaxHealth());
			helper.assertFalse(AttackTokens.holds(a, mob), "el mob no debería seguir con el turno de A");
			helper.assertTrue(a.getHealth() == a.getMaxHealth(), "A no debería recibir el golpe tras el cambio: " + a.getHealth());
			helper.succeed();
		});
	}

	/** Under a network: the executor's warned blow. */
	@GameTest(maxTicks = 100)
	public void networkBlowStaysWithinTurns(GameTestHelper helper) {
		Listener a = player(helper, new BlockPos(1, 1, 1));
		Listener b = player(helper, new BlockPos(2, 1, 2));
		Zombie zombie = zombie(helper, new BlockPos(2, 1, 1), false);
		MobMind mind = MobAi.mind(zombie);
		helper.assertTrue(mind != null, "el zombi debería tener cerebro");
		mind.override = standAndStrike();
		// The executor's own warning, not the vanilla goal's first swing before the network takes over.
		blowDoesNotCross(helper, zombie, a, b, () -> mind.networked && mind.windup > 0 && AttackTokens.holds(a, zombie));
	}

	/**
	 * Under a network, the mob loses its player halfway through a warned blow: the blow is dropped, not
	 * kept paused for whoever it takes on next (who would get the rest of it with no warning of their own).
	 */
	@GameTest(maxTicks = 100)
	public void aLostTargetTakesThePausedBlowWithIt(GameTestHelper helper) {
		Listener a = player(helper, new BlockPos(1, 1, 1));
		Zombie zombie = zombie(helper, new BlockPos(2, 1, 1), false);
		MobMind mind = MobAi.mind(zombie);
		helper.assertTrue(mind != null, "el zombi debería tener cerebro");
		mind.override = standAndStrike();
		zombie.setTarget(a);
		long[] lostAt = {-1};
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			if (lostAt[0] < 0 && mind.networked && mind.windup > 0 && AttackTokens.holds(a, zombie)) {
				zombie.setTarget(null);
				lostAt[0] = now;
			} else if (lostAt[0] >= 0 && now == lostAt[0] + 4) {
				helper.assertTrue(mind.windup == 0, "el golpe sigue en pausa esperando al siguiente: le quedan " + mind.windup + " ticks");
				helper.assertTrue(a.getHealth() == a.getMaxHealth(), "sin objetivo no debería golpear a A");
				helper.succeed();
			}
		});
	}

	/** Under the rules: vanilla's melee goal with Forja's warning, on one of the mod's own (it keeps to it). */
	@GameTest(maxTicks = 100)
	public void vanillaBlowStaysWithinTurns(GameTestHelper helper) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Listener a = player(helper, new BlockPos(1, 1, 1));
		Listener b = player(helper, new BlockPos(2, 1, 3));
		var mould = helper.spawn(ModEntities.MOLDE_ROTO, new BlockPos(2, 1, 1));
		blowDoesNotCross(helper, mould, a, b, () -> AttackTokens.holds(a, mould));
	}

	/** A special that took a turn gives it back however it ends, not only when it runs its course. */
	@GameTest
	public void anEndedLungeLetsGoOfItsTurn(GameTestHelper helper) {
		Listener a = player(helper, new BlockPos(6, 1, 1));
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1), true);
		zombie.setPos(zombie.getX(), ObsM1.ground(helper.getLevel(), zombie.getX(), zombie.getZ(), zombie.getY(), 0.3), zombie.getZ());
		zombie.setOnGround(true);
		zombie.setTarget(a);
		helper.runAfterDelay(3, () -> {
			MobMind mind = MobAi.mind(zombie);
			zombie.setOnGround(true);
			helper.assertTrue(mind != null && mind.specials != null && mind.specials.moveset().get(0) == dev.forja.ai.VanillaSpecials.LUNGE,
				"el zombi tiene embestida");
			helper.assertTrue(mind.specials.start(0, a), "la embestida debería poder empezar a " + zombie.distanceTo(a));
			helper.assertTrue(AttackTokens.holds(a, zombie), "la embestida coge turno");
			// Cut short, as when the goal driving it is taken over.
			mind.specials.end();
			helper.assertFalse(AttackTokens.holds(a, zombie), "cortada la embestida, el turno debería volver");
			helper.succeed();
		});
	}

	// --- Squads and the brain ------------------------------------------------------------------------

	/**
	 * The flanker of A's squad turns on B: until the squads are next worked out it is nothing in B's, not
	 * A's flanker still, and nobody is "waiting" behind it.
	 */
	@GameTest(maxTicks = 100)
	public void aNewTargetStartsWithoutTheOldSquad(GameTestHelper helper) {
		Listener a = player(helper, new BlockPos(4, 1, 4));
		Listener b = player(helper, new BlockPos(1, 1, 7));
		List<Zombie> squad = new ArrayList<>();
		// In front of A, to its side, and behind it: the one behind is the flanker.
		for (BlockPos at : List.of(new BlockPos(6, 1, 4), new BlockPos(4, 1, 6), new BlockPos(2, 1, 4))) {
			Zombie z = zombie(helper, at, true);
			z.setTarget(a);
			squad.add(z);
		}
		Zombie[] turned = {null};
		long[] at = {0};
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			if (turned[0] == null) {
				// Half-way between two squad updates, so the next one cannot have tidied up yet.
				if (now % dev.forja.ai.Squad.PERIOD != 5) {
					return;
				}
				for (Zombie z : squad) {
					MobMind mind = MobAi.mind(z);
					if (mind != null && mind.role == SquadRole.FLANCO) {
						helper.assertTrue(mind.othersWaiting, "tres contra A con dos turnos: hay quien espera");
						z.setTarget(b);
						turned[0] = z;
						at[0] = now;
					}
				}
				return;
			}
			if (now == at[0] + 1) {
				MobMind mind = MobAi.mind(turned[0]);
				helper.assertTrue(mind.target == b, "debería ir ya a por B");
				helper.assertTrue(mind.role == SquadRole.RESERVA, "no debería seguir de flanco de A contra B: " + mind.role);
				helper.assertFalse(mind.othersWaiting, "contra B nadie espera detrás de él");
				helper.succeed();
			}
		});
	}

	/**
	 * A mob that last saw anybody half a minute ago (a player it lost, or killed) still fights the next
	 * player it takes on. It used to drop them the moment it took them.
	 */
	@GameTest
	public void aNewTargetStartsANewBoredomClock(GameTestHelper helper) {
		Listener b = player(helper, new BlockPos(1, 1, 4));
		Zombie zombie = zombie(helper, new BlockPos(1, 1, 1), true);
		MobMind mind = MobAi.mind(zombie);
		helper.assertTrue(mind != null, "el zombi debería tener cerebro");
		// The last player it saw, 35 seconds ago.
		mind.lastSeen = zombie.position().add(10.0, 0.0, 0.0);
		mind.lastSeenAt = helper.getLevel().getGameTime() - dev.forja.ai.Personality.BORED_TICKS - 100;
		zombie.setTarget(b);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(zombie.getTarget() == b, "el zombi soltó a B en cuanto lo cogió: " + zombie.getTarget());
			helper.assertTrue(mind.lastSeen == null || mind.lastSeen.distanceTo(b.position()) < 1.0, "el rastro es de B, no del de antes");
			helper.succeed();
		});
	}

	/**
	 * A split squad (ideas 11 to 20): nothing breaks with nobody, one or two players about, and one of
	 * A's crowd goes to B once B has a fight of their own. What happens when B has nobody at all is only
	 * measured here and written to the log: it is a question for Andy.
	 */
	@GameTest(maxTicks = 100)
	public void aSquadSharesItselfOut(GameTestHelper helper) throws ReflectiveOperationException {
		java.lang.reflect.Method update = dev.forja.ai.Squad.class.getDeclaredMethod("update", ServerLevel.class, List.class, long.class);
		update.setAccessible(true);
		Listener a = player(helper, new BlockPos(1, 1, 1));
		Listener b = player(helper, new BlockPos(1, 1, 7));
		List<Zombie> crowd = new ArrayList<>();
		for (BlockPos at : List.of(new BlockPos(3, 1, 1), new BlockPos(3, 1, 2), new BlockPos(2, 1, 3), new BlockPos(3, 1, 3),
			new BlockPos(1, 1, 5))) {
			Zombie z = zombie(helper, at, true);
			z.setTarget(a);
			crowd.add(z);
		}
		Zombie idle = zombie(helper, new BlockPos(3, 1, 7), true);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			long now = level.getGameTime();
			List<MobMind> minds = new ArrayList<>();
			crowd.forEach(z -> minds.add(MobAi.mind(z)));
			// Nobody to fight, and nobody at all.
			squadUpdate(update, level, List.of(), now);
			squadUpdate(update, level, List.of(MobAi.mind(idle)), now);
			// One player: every one a slot, and one flanker.
			squadUpdate(update, level, minds, now);
			helper.assertTrue(minds.stream().noneMatch(m -> Double.isNaN(m.ringAngle)), "cada zombi de A con su hueco");
			helper.assertTrue(minds.stream().filter(m -> m.role == SquadRole.FLANCO).count() == 1, "un flanco contra A");
			long onB = crowd.stream().filter(z -> z.getTarget() == b).count();
			Forja.LOGGER.info("[dos jugadores] 5 contra A y 0 contra B a 6 bloques: pasan a B {}", onB);
			// B gets a fight of their own.
			idle.setTarget(b);
			helper.runAfterDelay(2, () -> {
				List<MobMind> all = new ArrayList<>(minds);
				all.add(MobAi.mind(idle));
				squadUpdate(update, level, all, level.getGameTime());
				helper.runAfterDelay(2, () -> {
					squadUpdate(update, level, all, level.getGameTime());
					long moved = crowd.stream().filter(z -> z.getTarget() == b).count();
					Forja.LOGGER.info("[dos jugadores] 5 contra A y 1 contra B: pasan a B {}", moved);
					helper.assertTrue(moved == 1, "con 5 contra 1 debería pasar uno a B (y solo uno), pasaron " + moved);
					helper.assertTrue(crowd.get(4).getTarget() == b, "pasa el que está más cerca de B");
					helper.succeed();
				});
			});
		});
	}

	/** Runs the squads over just these minds, sharing on whatever another test has set the option to. */
	private static void squadUpdate(java.lang.reflect.Method update, ServerLevel level, List<MobMind> minds, long now) {
		boolean share = CombatConfig.get().iaRepartirObjetivos;
		CombatConfig.get().iaRepartirObjetivos = true;
		try {
			update.invoke(null, level, minds, now);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		} finally {
			CombatConfig.get().iaRepartirObjetivos = share;
		}
	}

	// --- The mod's own monsters ----------------------------------------------------------------------

	/** The hollow armor gets up behind the one it was fighting, even with the other player nearer. */
	@GameTest(maxTicks = 120)
	public void hollowArmorGetsUpBehindItsOwnFoe(GameTestHelper helper) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Listener a = player(helper, new BlockPos(7, 1, 5));
		Listener b = player(helper, new BlockPos(2, 1, 1));
		var hollow = helper.spawn(ModEntities.CORAZA, new BlockPos(1, 1, 1));
		hollow.setNoAi(true);
		hollow.setTarget(a);
		// B, standing right next to it, is the one who knocks it down.
		hollow.invulnerableTime = 0;
		hollow.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(b), 1000.0F);
		helper.assertTrue(hollow.isAlive() && hollow.isNoAi(), "debería hacerse el muerto");
		helper.runAfterDelay(dev.forja.ai.ForjaTraits.HOLLOW_FEIGN_TICKS + 3, () -> {
			helper.assertFalse(hollow.isNoAi(), "debería levantarse");
			helper.assertTrue(hollow.getTarget() == a, "debería seguir con A: " + hollow.getTarget());
			helper.assertTrue(hollow.distanceTo(a) < 3.0,
				"debería levantarse detrás de A (a " + hollow.distanceTo(a) + " de A, a " + hollow.distanceTo(b) + " de B)");
			hollow.discard();
			helper.succeed();
		});
	}

	private static ServerBossEvent bar(Entity boss) {
		try {
			java.lang.reflect.Field field = boss.getClass().getDeclaredField("bar");
			field.setAccessible(true);
			return (ServerBossEvent) field.get(boss);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
	}

	/**
	 * A boss bar leaves whoever is no longer in the boss's world: the player who went through a portal,
	 * and the body a respawn leaves behind. Somebody still there keeps it.
	 */
	@GameTest(maxTicks = 100)
	public void bossBarsLetGoOfPlayersWhoLeft(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "el servidor de pruebas debería tener el Nether");
		var cune = helper.spawn(ModEntities.GUARDIAN_DE_CUNO, new BlockPos(2, 1, 2));
		var smith = helper.spawn(ModEntities.HERRERO_CAIDO, new BlockPos(5, 1, 5));
		cune.setNoAi(true);
		smith.setNoAi(true);
		Listener present = player(helper, new BlockPos(1, 1, 5));
		Listener died = player(helper, new BlockPos(1, 1, 6));
		Listener travelled = new Listener(nether);
		for (Entity boss : List.<Entity>of(cune, smith)) {
			bar(boss).addPlayer(present);
			bar(boss).addPlayer(died);
			bar(boss).addPlayer(travelled);
		}
		died.setRemoved(Entity.RemovalReason.KILLED);
		helper.runAfterDelay(45, () -> {
			for (Entity boss : List.<Entity>of(cune, smith)) {
				var shown = bar(boss).getPlayers();
				String name = boss.getClass().getSimpleName();
				helper.assertFalse(shown.contains(died), name + ": la barra sigue con el cuerpo que dejó un reaparecer");
				helper.assertFalse(shown.contains(travelled), name + ": la barra sigue con quien se fue al Nether");
				helper.assertTrue(shown.contains(present), name + ": quien sigue allí conserva la barra");
			}
			cune.discard();
			smith.discard();
			helper.succeed();
		});
	}

	/** The cune guardian tells each player who strikes it that it is sealed, not only the first. */
	@GameTest(maxTicks = 60)
	public void cuneTellsEachPlayerItIsSealed(GameTestHelper helper) {
		helper.setBlock(new BlockPos(5, 1, 5), ModBlocks.FAROL_DE_PAVESA);
		var cune = helper.spawn(ModEntities.GUARDIAN_DE_CUNO, new BlockPos(2, 1, 2));
		cune.setNoAi(true);
		Listener a = player(helper, new BlockPos(1, 1, 5));
		Listener b = player(helper, new BlockPos(5, 1, 1));
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(cune.sealed(), "con un farol encendido en su sala debería estar sellado");
			float health = cune.getHealth();
			for (Listener striker : List.of(a, b)) {
				cune.invulnerableTime = 0;
				cune.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(striker), 4.0F);
			}
			helper.assertTrue(cune.getHealth() == health, "sellado no recibe daño");
			helper.assertTrue(a.heard("gui.forja.cuno_sellado"), "A debería saber que está sellado");
			helper.assertTrue(b.heard("gui.forja.cuno_sellado"), "B también debería saberlo, no solo el primero en pegarle");
			cune.discard();
			helper.succeed();
		});
	}

	// --- What was already per player (measured, nothing changed) ------------------------------------

	/**
	 * The frenzy, the oil that puts it out, stamina, the dodge and the parry rhythm, and the tongs' grip
	 * each belong to one player: what happens to B leaves A as it was.
	 */
	@GameTest(maxTicks = 60)
	public void perPlayerStateStaysPerPlayer(GameTestHelper helper) {
		Listener a = player(helper, new BlockPos(1, 1, 1));
		Listener b = player(helper, new BlockPos(1, 1, 5));
		for (int i = 0; i < 3; i++) {
			Frenzy.onHit(a);
		}
		Frenzy.onHit(b);
		helper.assertTrue(Math.abs(Frenzy.level(a) - 0.6F) < 1.0E-6F && Math.abs(Frenzy.level(b) - 0.2F) < 1.0E-6F,
			"frenesí A " + Frenzy.level(a) + ", B " + Frenzy.level(b));
		OilPools.quench(b);
		helper.assertTrue(Frenzy.level(b) == 0.0F && Math.abs(Frenzy.level(a) - 0.6F) < 1.0E-6F,
			"el aceite sobre B solo apaga a B: A " + Frenzy.level(a) + ", B " + Frenzy.level(b));

		float max = CombatConfig.get().staminaMax;
		Stamina.trySpend(a, 40.0F);
		helper.assertTrue(Stamina.value(a) == max - 40.0F && Stamina.value(b) == max, "estamina A " + Stamina.value(a) + ", B " + Stamina.value(b));
		Stamina.onDodge(a, 1.0F, 0.0F);
		helper.assertTrue(Stamina.dodgeCooldown(a) > 0 && Stamina.dodgeCooldown(b) == 0, "la esquiva de A no enfría la de B");
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(Stamina.isDodging(a, now) && !Stamina.isDodging(b, now), "solo A esquiva");
		ParryRhythm.onRaise(a, now - 2);
		ParryRhythm.onRaise(a, now);
		ParryRhythm.onRaise(b, now);
		helper.assertTrue(ParryRhythm.rushed(a) && !ParryRhythm.rushed(b), "el escudo machacado de A no castiga a B");

		var tongs = helper.spawn(ModEntities.TENAZA, new BlockPos(5, 1, 1));
		tongs.setNoAi(true);
		tongs.grab(helper.getLevel(), a);
		helper.runAfterDelay(dev.forja.entity.Tongs.GRAB_WINDUP + 2, () -> {
			helper.assertTrue(tongs.holding() == a, "la tenaza sujeta a A: " + tongs.holding());
			tongs.discard();
			helper.succeed();
		});
	}
}
