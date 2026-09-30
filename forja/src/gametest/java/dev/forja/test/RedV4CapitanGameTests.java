package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.forja.ai.Aggression;
import dev.forja.ai.Captain;
import dev.forja.ai.CaptainBrain;
import dev.forja.ai.Fury;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import dev.forja.ai.ObsV4;
import dev.forja.ai.Tactic;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Step M5 of the v4 mob network (docs/red_mob_v4_diseno.md §4.1–§4.3; Andy's decisions 3 and 4): the captain (only an
 * elite or a champion), its orders and formations, the synchronized charge's extra turn, a leaderless group when it
 * dies, morale, fury (a tenth of each group, chosen at spawn), leaving the fight, the captain's network and its
 * contract (docs/red_capitan_v4_contrato.json).
 */
public class RedV4CapitanGameTests {
	/**
	 * A stone floor of size × size. Bigger than a test's 8×8 box, its barriers come down and its chunks are forced
	 * (entities only tick in chunks that are, and a test's own reach little past its box); {@link #clear} lets them go.
	 */
	private static void floor(GameTestHelper helper, int size) {
		if (size > 8) {
			TestChunks.force(helper, size);
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

	private static void clear(GameTestHelper helper, int size) {
		AABB box = new AABB(helper.absoluteVec(new Vec3(-4, -2, -4)), helper.absoluteVec(new Vec3(size + 4, 10, size + 4)));
		helper.getLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player)).forEach(Entity::discard);
		if (size > 8) {
			TestChunks.release(helper);
		}
	}


	private static Zombie zombie(GameTestHelper helper, BlockPos at) {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		return zombie;
	}

	/** An elite zombie, three zombies (one with a shield) and a skeleton, all on a still player in the middle. */
	private static List<Mob> group(GameTestHelper helper, CombatGameTests.TestPlayer player) {
		return group(helper, player, 0);
	}

	/** The same, shifted {@code shift} blocks along x and z (for a bigger floor). */
	private static List<Mob> group(GameTestHelper helper, CombatGameTests.TestPlayer player, int shift) {
		List<Mob> mobs = new ArrayList<>();
		// the elite starts far (a duel is offered within 10 blocks, idea 96), the rest round the player
		Zombie elite = zombie(helper, new BlockPos(2, 1, 2));
		Threat.ELITE.mark(elite);
		mobs.add(elite);
		Zombie shield = zombie(helper, new BlockPos(13 + shift, 1, 2 + shift));
		shield.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		mobs.add(shield);
		mobs.add(zombie(helper, new BlockPos(2 + shift, 1, 13 + shift)));
		mobs.add(zombie(helper, new BlockPos(13 + shift, 1, 13 + shift)));
		var skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(7 + shift, 1, 1 + shift));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		mobs.add(skeleton);
		for (Mob mob : mobs) {
			mob.setTarget(player);
		}
		return mobs;
	}

	private static void hold(GameTestHelper helper, CombatGameTests.TestPlayer player, List<Mob> mobs) {
		Vec3 at = player.position();
		helper.onEachTick(() -> {
			player.setPos(at.x, at.y, at.z);
			player.setHealth(player.getMaxHealth());
			for (Mob mob : mobs) {
				if (mob.isAlive()) {
					mob.setTarget(player);
					mob.setHealth(mob.getMaxHealth());
				}
			}
		});
	}

	/**
	 * The elite leads (never a veteran), and by default gives no order while nothing calls for one (2026-09-30: the rules
	 * captain that ordered CERCAR all the time made groups worse): orden_ninguna and formacion_libre, the posts by kind
	 * (the shield in front, the archer behind) only for block M, and the archer shoots from where it is instead of
	 * walking to a post. Block M says so: tengo_capitan, soy_capitan for the elite, capitan_vida_frac.
	 */
	@GameTest(padding = 24, maxTicks = 120)
	public void eliteLeadsFreeUntilItHelps(GameTestHelper helper) {
		floor(helper, 32);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(16, 1, 16));
		CaptainBrain.override(player, CaptainBrain.Mode.REGLAS);
		List<Mob> mobs = group(helper, player, 8);
		hold(helper, player, mobs);
		Mob elite = mobs.get(0);
		Mob shield = mobs.get(1);
		Mob skeleton = mobs.get(4);
		boolean[] walked = {false};
		boolean[] free = {false};
		String[] wrong = {null};
		helper.onEachTick(() -> {
			Captain.Group g = Captain.group(player);
			if (g == null || g.captain == null) {
				return;
			}
			// the rules never hold the group back: no CERCAR, no wall; only a charge (someone behind the player) or nothing
			if (g.command.order != Captain.Order.NINGUNA && g.command.order != Captain.Order.CARGA
				|| g.command.formation != Captain.Formation.LIBRE && g.command.formation != Captain.Formation.PINZA) {
				wrong[0] = g.command.order + " " + g.command.formation;
			}
			free[0] |= g.command.free();
			MobMind mind = MobAi.mind(skeleton);
			walked[0] |= g.command.free() && mind != null && mind.decision.tactic() == Tactic.FORMACION;
		});
		helper.runAfterDelay(13, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertTrue(g != null && g.captain == elite, "el élite es el capitán: " + (g == null ? null : g.captain));
			helper.assertTrue(free[0], "sin nada que lo pida, sin orden y libre: da " + g.command.order + " " + g.command.formation);
			helper.assertTrue(MobAi.mind(shield).post == Captain.FRENTE, "el del escudo, de puesto frente: " + MobAi.mind(shield).post);
			helper.assertTrue(MobAi.mind(skeleton).post == Captain.SEGUNDA, "el arquero, en segunda línea: " + MobAi.mind(skeleton).post);
			float[] obs = ObsV4.build(elite, player, MobAi.mind(elite));
			int m = ObsV4.M_AT;
			helper.assertTrue(obs[m] == 1.0F && obs[m + 1] == 1.0F, "tengo_capitan y soy_capitan para el élite");
			helper.assertTrue(obs[m + 3 + g.command.order.ordinal()] == 1.0F && obs[m + 16 + g.command.formation.ordinal()] == 1.0F,
				"la orden y la formación, en el bloque M");
			float[] other = ObsV4.build(shield, player, MobAi.mind(shield));
			helper.assertTrue(other[m] == 1.0F && other[m + 1] == 0.0F && other[m + 20 + Captain.FRENTE] == 1.0F, "el del escudo: tengo capitán, puesto frente");
			helper.assertTrue(other[m + 29] > 0.9F, "capitan_vida_frac: " + other[m + 29]);
		});
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(wrong[0] == null, "las reglas nunca frenan al grupo: dieron " + wrong[0]);
			helper.assertFalse(walked[0], "sin orden, el arquero no va a ningún puesto (tira desde donde está)");
			CaptainBrain.override(player, null);
			clear(helper, 32);
			helper.succeed();
		});
	}

	/**
	 * The rules captain's charge: a zombie behind the player (within 6, their back to it) and another up close make the
	 * player exposed, and the captain orders CARGA at once (countdown 0): one more turn for 2 s, and every member goes in.
	 * Then it rests: no second charge until CHARGE_REST ticks after the first one's extra turn, though the back is still
	 * turned.
	 */
	@GameTest(padding = 16, maxTicks = 200)
	public void theRulesChargeWhenTheBackIsTurned(GameTestHelper helper) {
		floor(helper, 16);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		CaptainBrain.override(player, CaptainBrain.Mode.REGLAS);
		List<Mob> mobs = new ArrayList<>();
		Zombie elite = zombie(helper, new BlockPos(12, 1, 8));
		Threat.ELITE.mark(elite);
		mobs.add(elite);
		// the player looks along +x: this one is right behind them
		mobs.add(zombie(helper, new BlockPos(4, 1, 8)));
		mobs.add(zombie(helper, new BlockPos(11, 1, 11)));
		// four: a captain leading more than three never offers a duel (its watchers would stand back)
		mobs.add(zombie(helper, new BlockPos(11, 1, 5)));
		for (Mob mob : mobs) {
			mob.setTarget(player);
		}
		hold(helper, player, mobs);
		long start = helper.getLevel().getGameTime();
		long[] first = {-1};
		int[] charges = {0};
		boolean[] extraTurn = {false};
		boolean[] allIn = {false};
		long[] last = {Long.MIN_VALUE};
		helper.onEachTick(() -> {
			Captain.Group g = Captain.group(player);
			long now = helper.getLevel().getGameTime();
			if (g == null || g.captain == null) {
				return;
			}
			if (g.command.order == Captain.Order.CARGA && g.lastChargeAt != last[0]) {
				last[0] = g.lastChargeAt;
				charges[0]++;
				if (first[0] < 0) {
					first[0] = now;
				}
			}
			if (first[0] >= 0 && now - first[0] <= Captain.CHARGE_TURN_TICKS) {
				extraTurn[0] |= Captain.chargeTurn(player, now);
				int in = 0;
				for (Mob mob : mobs) {
					MobMind mind = MobAi.mind(mob);
					in += mind != null && (mind.decision == dev.forja.ai.Decision.APPROACH || mind.decision.tactic() == Tactic.ACERCARSE) ? 1 : 0;
				}
				// all in (one may be busy with something of its own: a blow on its way, a step back from a charged one)
				allIn[0] |= in >= mobs.size() - 1;
			}
		});
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(first[0] >= 0 && first[0] - start <= 22, "con uno a su espalda, CARGA en la primera o segunda pasada: " + first[0]);
		});
		// the first charge came by tick 22; the next may not before 40 + CHARGE_REST ticks after it (140)
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(extraTurn[0], "la carga da un turno más");
			helper.assertTrue(allIn[0], "en la carga todos van al ataque");
			helper.assertTrue(charges[0] == 1, "entre carga y carga, descanso: " + charges[0] + " cargas");
			CaptainBrain.override(player, null);
			clear(helper, 16);
			helper.succeed();
		});
	}

	/** The player backing away from the group (facing it): no order, in a pincer (PINZA). */
	@GameTest(padding = 24, maxTicks = 120)
	public void theRulesPincerAPlayerBackingAway(GameTestHelper helper) {
		floor(helper, 32);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(10, 1, 16));
		CaptainBrain.override(player, CaptainBrain.Mode.REGLAS);
		// facing the group, on the -x side
		player.setYRot(90.0F);
		player.setYHeadRot(90.0F);
		List<Mob> mobs = new ArrayList<>();
		Zombie elite = zombie(helper, new BlockPos(2, 1, 16));
		Threat.ELITE.mark(elite);
		mobs.add(elite);
		mobs.add(zombie(helper, new BlockPos(2, 1, 13)));
		mobs.add(zombie(helper, new BlockPos(2, 1, 19)));
		for (Mob mob : mobs) {
			mob.setTarget(player);
		}
		Vec3[] at = {player.position()};
		boolean[] pincer = {false};
		helper.onEachTick(() -> {
			at[0] = at[0].add(0.15, 0.0, 0.0);
			player.setPos(at[0].x, at[0].y, at[0].z);
			player.setYRot(90.0F);
			player.setYHeadRot(90.0F);
			player.setHealth(player.getMaxHealth());
			for (Mob mob : mobs) {
				mob.setTarget(player);
				mob.setHealth(mob.getMaxHealth());
			}
			Captain.Group g = Captain.group(player);
			pincer[0] |= g != null && g.command.order == Captain.Order.NINGUNA && g.command.formation == Captain.Formation.PINZA;
		});
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(pincer[0], "echándose atrás, una pinza: " + (Captain.group(player) == null ? null : Captain.group(player).command.formation));
			CaptainBrain.override(player, null);
			clear(helper, 32);
			helper.succeed();
		});
	}

	/**
	 * With the rules captain off (iaCapitanReglas, or the mode LIBRE) a group keeps its captain but never gets an order,
	 * even with the player's back turned; with the captain off (SIN_CAPITAN) it has none at all.
	 */
	@GameTest(padding = 16, maxTicks = 100)
	public void aCaptainWithoutRulesGivesNoOrders(GameTestHelper helper) {
		floor(helper, 16);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		CaptainBrain.override(player, CaptainBrain.Mode.LIBRE);
		CombatGameTests.TestPlayer other = CombatGameTests.player(helper, new BlockPos(8, 1, 3));
		CaptainBrain.override(other, CaptainBrain.Mode.SIN_CAPITAN);
		List<Mob> mobs = new ArrayList<>();
		Zombie elite = zombie(helper, new BlockPos(12, 1, 8));
		Threat.ELITE.mark(elite);
		mobs.add(elite);
		mobs.add(zombie(helper, new BlockPos(4, 1, 8)));
		mobs.add(zombie(helper, new BlockPos(11, 1, 11)));
		List<Mob> others = new ArrayList<>();
		Zombie otherElite = zombie(helper, new BlockPos(12, 1, 2));
		Threat.ELITE.mark(otherElite);
		others.add(otherElite);
		others.add(zombie(helper, new BlockPos(4, 1, 2)));
		hold(helper, player, mobs);
		hold(helper, other, others);
		boolean[] ordered = {false};
		boolean[] led = {false};
		boolean[] otherLed = {false};
		helper.onEachTick(() -> {
			Captain.Group g = Captain.group(player);
			led[0] |= g != null && g.captain == elite;
			ordered[0] |= g != null && !g.command.free();
			Captain.Group o = Captain.group(other);
			otherLed[0] |= o != null && o.captain != null;
		});
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(led[0], "sin reglas el élite sigue siendo capitán");
			helper.assertFalse(ordered[0], "pero no da órdenes, aunque le den la espalda");
			helper.assertFalse(otherLed[0], "sin capitán, nadie manda");
			CaptainBrain.override(player, null);
			CaptainBrain.override(other, null);
			clear(helper, 16);
			helper.succeed();
		});
	}

	/**
	 * A free turn is used (2026-09-30): a zombie behind a player who walks away from it, away from its ring slot (the
	 * surround mode, MobSprint.rodeo), goes in while a turn on them is free, and only goes round to its slot when every turn is
	 * taken. Against a player on the move the whole group used to go round and round and hardly strike.
	 */
	@GameTest(padding = 24, maxTicks = 80)
	public void aFreeTurnIsUsedAgainstAPlayerOnTheMove(GameTestHelper helper) {
		floor(helper, 32);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(12, 1, 16));
		List<Mob> mobs = new ArrayList<>();
		for (int k = 0; k < 5; k++) {
			Zombie zombie = zombie(helper, new BlockPos(6, 1, 13 + k));
			for (dev.forja.ai.Personality.Trait trait : dev.forja.ai.Personality.Trait.values()) {
				zombie.removeTag(dev.forja.ai.Personality.TRAIT_TAG + trait.name().toLowerCase(java.util.Locale.ROOT));
			}
			zombie.addTag(dev.forja.ai.Personality.TRAIT_TAG + "agresivo");
			mobs.add(zombie);
		}
		Vec3[] at = {player.position()};
		helper.onEachTick(() -> {
			// walking away along +x
			at[0] = at[0].add(0.15, 0.0, 0.0);
			player.setPos(at[0].x, at[0].y, at[0].z);
			player.setHealth(player.getMaxHealth());
			dev.forja.ai.MobSprint.motion(player);
			for (Mob mob : mobs) {
				mob.setTarget(player);
				mob.setHealth(mob.getMaxHealth());
			}
		});
		helper.runAfterDelay(40, () -> {
			Mob behind = mobs.stream().min(java.util.Comparator.comparingDouble(Mob::getX)).orElseThrow();
			MobMind mind = MobAi.mind(behind);
			for (Mob mob : mobs) {
				dev.forja.combat.AttackTokens.release(player, mob);
			}
			// its slot on its own side (no going round first) but well out, 3 beyond it: the player walking away from it,
			// the surround mode wants it there
			double side = Math.atan2(behind.getZ() - player.getZ(), behind.getX() - player.getX());
			double radius = behind.distanceTo(player) + 3.0;
			mind.ringAngle = side;
			mind.ringRadius = radius;
			mind.role = dev.forja.ai.SquadRole.RESERVA;
			mind.lastStrike = Long.MIN_VALUE / 2;
			helper.assertTrue(dev.forja.ai.MobSprint.rodeo(mind), "el modo rodeo lo pide (jugador alejándose, hueco por delante)");
			dev.forja.ai.Decision free = dev.forja.ai.RuleBrain.decide(mind, player);
			helper.assertTrue(free.tactic() == Tactic.ACERCARSE, "con un turno libre, va a por él: " + free.tactic());
			int max = Aggression.maxAttackers(behind, player);
			int taken = 0;
			for (Mob mob : mobs) {
				if (mob != behind && taken < max && dev.forja.combat.AttackTokens.tryAcquire(player, mob, max)) {
					taken++;
				}
			}
			helper.assertTrue(taken == max, "los turnos, todos ocupados: " + taken + " de " + max);
			mind.ringAngle = side;
			mind.ringRadius = radius;
			dev.forja.ai.Decision busy = dev.forja.ai.RuleBrain.decide(mind, player);
			helper.assertTrue(busy.tactic() == Tactic.RODEAR, "sin turno libre, rodea hasta su hueco: " + busy.tactic());
			for (Mob mob : mobs) {
				dev.forja.combat.AttackTokens.release(player, mob);
			}
			clear(helper, 32);
			helper.succeed();
		});
	}

	/**
	 * A big group (iaTurnoGrupoGrande, on): with 6 or more fighting one player (the Squad's count) the player allows one
	 * more turn; with 5, not.
	 */
	@GameTest(padding = 16, maxTicks = 60)
	public void aBigGroupGetsOneMoreTurn(GameTestHelper helper) {
		floor(helper, 16);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		List<Mob> mobs = new ArrayList<>();
		for (int k = 0; k < 6; k++) {
			mobs.add(zombie(helper, new BlockPos(2 + 2 * k, 1, 2)));
		}
		hold(helper, player, mobs);
		int[] six = {0};
		helper.runAfterDelay(22, () -> {
			long now = helper.getLevel().getGameTime();
			helper.assertTrue(Captain.groupSize(player, now) == 6, "el grupo cuenta 6: " + Captain.groupSize(player, now));
			six[0] = Aggression.maxAttackers(player);
			mobs.get(5).discard();
		});
		helper.runAfterDelay(45, () -> {
			long now = helper.getLevel().getGameTime();
			helper.assertTrue(Captain.groupSize(player, now) == 5, "ahora 5: " + Captain.groupSize(player, now));
			int five = Aggression.maxAttackers(player);
			boolean on = CombatConfig.get().iaTurnoGrupoGrande;
			helper.assertTrue(six[0] == five + (on ? 1 : 0), "con 6, un turno más (" + on + "): " + six[0] + " / " + five);
			clear(helper, 16);
			helper.succeed();
		});
	}

	/** What staggers a player (they have no balance bar): slowness II or more from a monster, as a mob's parry leaves. */
	@GameTest(maxTicks = 10)
	public void aPlayerReelsFromSlownessTwo(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(2, 1, 2));
		helper.assertFalse(Captain.reeling(player), "sin nada, no");
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 30, 0));
		helper.assertFalse(Captain.reeling(player), "lentitud I, no");
		player.removeAllEffects();
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 30, 1));
		helper.assertTrue(Captain.reeling(player), "lentitud II (la parada de un mob), sí");
		helper.assertFalse(dev.forja.combat.Posture.isStaggered(player, helper.getLevel().getGameTime()), "la postura nunca aturde a un jugador");
		helper.succeed();
	}

	/** A group led by nobody but a veteran has no captain (Andy's decision 4). */
	@GameTest(padding = 16, maxTicks = 40)
	public void aVeteranNeverLeads(GameTestHelper helper) {
		floor(helper, 16);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		List<Mob> mobs = group(helper, player);
		Threat.VETERANO.mark(mobs.get(0));
		mobs.get(0).removeTag("forja_amenaza_elite");
		hold(helper, player, mobs);
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(Threat.of(mobs.get(0)) == Threat.VETERANO, "ahora es veterano: " + Threat.of(mobs.get(0)));
			Captain.Group g = Captain.group(player);
			helper.assertTrue(g == null || g.captain == null, "un veterano no es capitán");
			float[] obs = ObsV4.build(mobs.get(1), player, MobAi.mind(mobs.get(1)));
			helper.assertTrue(obs[ObsV4.M_AT] == 0.0F && obs[ObsV4.M_AT + 3] == 1.0F && obs[ObsV4.M_AT + 16] == 1.0F,
				"sin capitán: orden_ninguna y formacion_libre a 1");
			clear(helper, 16);
			helper.succeed();
		});
	}

	/**
	 * The captain dies: the group is leaderless (no orders, sin_mando 1 going down), its morale drops by 0.4, and a mob
	 * chosen for fury can go into one (a blow to morale). Its fury: +20 % speed and +25 % damage for 10 s, then 5 s spent
	 * with no turn.
	 */
	@GameTest(padding = 16, maxTicks = 400)
	public void theCaptainFallsAndFuryComes(GameTestHelper helper) {
		floor(helper, 16);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		List<Mob> mobs = group(helper, player);
		Mob elite = mobs.get(0);
		Mob raging = mobs.get(2);
		raging.addTag(Fury.TAG);
		for (dev.forja.ai.Personality.Trait trait : dev.forja.ai.Personality.Trait.values()) {
			raging.removeTag(dev.forja.ai.Personality.TRAIT_TAG + trait.name().toLowerCase(java.util.Locale.ROOT));
		}
		raging.addTag(dev.forja.ai.Personality.TRAIT_TAG + "agresivo");
		List<Mob> alive = new ArrayList<>(mobs.subList(1, mobs.size()));
		Vec3 at = player.position();
		helper.onEachTick(() -> {
			player.setPos(at.x, at.y, at.z);
			player.setHealth(player.getMaxHealth());
			for (Mob mob : alive) {
				mob.setTarget(player);
			}
		});
		float[] before = {0.0F};
		long[] died = {0};
		helper.runAfterDelay(25, () -> {
			MobMind mind = MobAi.mind(raging);
			before[0] = ObsV4.build(raging, player, mind)[ObsV4.MO_AT];
			helper.assertFalse(Fury.available(mind, helper.getLevel().getGameTime()), "sin golpe de moral no hay furia");
			elite.setTarget(player);
			elite.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 10000.0F);
			died[0] = helper.getLevel().getGameTime();
		});
		helper.runAfterDelay(35, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertTrue(g.captain == null && g.command.order == Captain.Order.NINGUNA, "muerto el capitán no hay órdenes");
			MobMind mind = MobAi.mind(raging);
			float[] obs = ObsV4.build(raging, player, mind);
			helper.assertTrue(obs[ObsV4.M_AT + 2] > 0.8F && obs[ObsV4.M_AT] == 0.0F, "sin_mando cerca de 1: " + obs[ObsV4.M_AT + 2]);
			helper.assertTrue(obs[ObsV4.MO_AT] < before[0] - 0.3F, "la moral cae: " + before[0] + " -> " + obs[ObsV4.MO_AT]);
			helper.assertTrue(obs[ObsV4.MO_AT + 2] > 0.1F, "bajas_frac: " + obs[ObsV4.MO_AT + 2]);
		});
		long[] raged = {-1};
		helper.onEachTick(() -> {
			MobMind mind = MobAi.mind(raging);
			if (raged[0] < 0 && mind != null && mind.furyActive(helper.getLevel().getGameTime())) {
				raged[0] = helper.getLevel().getGameTime();
				var speed = raging.getAttribute(Attributes.MOVEMENT_SPEED);
				helper.assertTrue(speed.getModifier(dev.forja.Forja.id("furia")) != null, "en furia va más rápido");
				helper.assertTrue(Fury.damage(raging) == 1.0 + Fury.DAMAGE, "y pega un 25 % más");
			}
		});
		helper.runAfterDelay(260, () -> {
			helper.assertTrue(raged[0] > 0 && raged[0] - died[0] < 40, "el agresivo elegido entra en furia al morir el capitán: " + raged[0]);
			long now = helper.getLevel().getGameTime();
			helper.assertTrue(Fury.spent(raging), "tras 10 s de furia, 5 s agotado");
			helper.assertFalse(dev.forja.combat.AttackTokens.tryAcquire(player, raging, 99), "agotado no toma turno");
			helper.assertFalse(Fury.available(MobAi.mind(raging), now), "una furia por pelea");
			float[] obs = ObsV4.build(raging, player, MobAi.mind(raging));
			helper.assertTrue(obs[ObsV4.MO_AT + 8] > 0.0F && obs[ObsV4.MO_AT + 7] == 0.0F, "yo_agotado > 0 y yo_furia = 0");
			clear(helper, 16);
			helper.succeed();
		});
	}

	/** Fury is for a tenth of a group chosen at spawn: one at least from five, none in four. */
	@GameTest(maxTicks = 20)
	public void aTenthCanRage(GameTestHelper helper) {
		RandomSource random = RandomSource.create(5);
		int[][] cases = {{4, 0}, {5, 1}, {10, 1}, {13, 1}, {20, 2}};
		for (int[] c : cases) {
			List<Mob> group = new ArrayList<>();
			for (int i = 0; i < c[0]; i++) {
				Mob mob = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1 + i % 6, 1, 1 + i / 6));
				mob.setNoAi(true);
				group.add(mob);
			}
			Fury.choose(group, random);
			long able = group.stream().filter(Fury::able).count();
			helper.assertTrue(able == c[1], "en un grupo de " + c[0] + ", " + c[1] + " con furia; salen " + able);
			group.forEach(Entity::discard);
		}
		helper.succeed();
	}

	/**
	 * The synchronized charge: CARGA with a countdown of 10; at 0 the captain shouts and the player allows one more turn
	 * for 2 s. cuenta_atras/40 counts down in block M.
	 */
	@GameTest(padding = 16, maxTicks = 120)
	public void theChargeGivesOneMoreTurn(GameTestHelper helper) {
		floor(helper, 16);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		// the rules captain keeps a charge under way (and it may have called one of its own before: counted out below)
		CaptainBrain.override(player, CaptainBrain.Mode.REGLAS);
		List<Mob> mobs = group(helper, player);
		hold(helper, player, mobs);
		int[] base = {0};
		long[] ordered = {0};
		helper.runAfterDelay(25, () -> {
			Captain.Group g = Captain.group(player);
			helper.assertTrue(g != null && g.captain != null, "hay capitán");
			long now = helper.getLevel().getGameTime();
			base[0] = Aggression.maxAttackers(player) - (Captain.chargeTurn(player, now) ? 1 : 0);
			// and none of its own for a while after this one (it rests between charges)
			g.lastChargeAt = now;
			Captain.Command c = new Captain.Command();
			c.order = Captain.Order.CARGA;
			c.formation = Captain.Formation.PINZA;
			c.count = 1;
			c.givenAt = now;
			c.chargeAt = now + Captain.COUNTS[1];
			g.command = c;
			ordered[0] = now;
			float[] obs = ObsV4.build(mobs.get(1), player, MobAi.mind(mobs.get(1)));
			helper.assertTrue(Math.abs(obs[ObsV4.M_AT + 13] - 10.0F / 40.0F) < 1.0E-4 && obs[ObsV4.M_AT + 3 + Captain.Order.CARGA.ordinal()] == 1.0F,
				"cuenta_atras/40 = 0,25 y orden_carga: " + obs[ObsV4.M_AT + 13]);
		});
		helper.runAfterDelay(37, () -> {
			helper.assertTrue(Captain.chargeTurn(player, helper.getLevel().getGameTime()), "tras la cuenta, un turno más durante 2 s");
			helper.assertTrue(Aggression.maxAttackers(player) == base[0] + 1, "turnos: " + base[0] + " -> " + Aggression.maxAttackers(player));
		});
		helper.runAfterDelay(25 + 10 + 45, () -> {
			helper.assertFalse(Captain.chargeTurn(player, helper.getLevel().getGameTime()), "pasados los 2 s, el turno extra se va");
			CaptainBrain.override(player, null);
			clear(helper, 16);
			helper.succeed();
		});
	}

	/** A monster running away (RETIRARSE) for 100 ticks and more than 20 blocks off lets the player go. */
	@GameTest(padding = 24, maxTicks = 200)
	public void aRunawayLeavesTheFight(GameTestHelper helper) {
		floor(helper, 8);
		TestChunks.force(helper, 28);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(2, 1, 2));
		Vec3 at = player.position();
		// a pen 22 blocks off, where it runs in circles: far enough, and it cannot get further
		for (int x = 22; x <= 26; x++) {
			for (int z = 22; z <= 26; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				if (x == 22 || x == 26 || z == 22 || z == 26) {
					helper.setBlock(new BlockPos(x, 1, z), Blocks.GLASS);
					helper.setBlock(new BlockPos(x, 2, z), Blocks.GLASS);
				}
			}
		}
		Zombie zombie = zombie(helper, new BlockPos(24, 1, 24));
		float[] bias = new float[NetBrain.V4_OUTPUTS];
		bias[NetBrain.TACTIC_AT + Tactic.RETIRARSE.ordinal()] = 20.0F;
		MobAi.mind(zombie).override = NetBrain.fromJson(RedV4GameTests.fakeV4(23L, bias));
		zombie.setTarget(player);
		boolean[] gone = {false};
		helper.onEachTick(() -> {
			player.setPos(at.x, at.y, at.z);
			player.setHealth(player.getMaxHealth());
			gone[0] |= zombie.getTarget() == null;
		});
		helper.succeedWhen(() -> {
			MobMind mind = MobAi.mind(zombie);
			for (int x = 22; x <= 26; x++) {
				for (int z = 22; z <= 26; z++) {
					for (int y = 0; y <= 2; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
					}
				}
			}
			TestChunks.release(helper);
			helper.assertTrue(gone[0], "huyendo 5 s a más de 20 bloques suelta al jugador: táctica " + mind.decision.tactic() + ", a "
				+ zombie.distanceTo(player) + ", huyendo desde " + mind.retreatSince + " (ahora " + helper.getLevel().getGameTime() + "), red "
				+ mind.networked + ", camino " + zombie.getNavigation().getPath() + ", metas "
				+ ((dev.forja.mixin.MobGoalsAccess) zombie).forjaGoals().getAvailableGoals().stream().filter(w -> w.isRunning()).map(w -> w.getGoal().getClass().getSimpleName()).toList()
				+ ", en " + zombie.position() + ", noAI " + zombie.isNoAi());
			clear(helper, 30);
		});
	}

	/** A v4 captain network with its size and names, and output biases as given. */
	static JsonObject fakeCaptain(long seed, float[] outBias) {
		List<String> names = CaptainBrain.names();
		Random random = new Random(seed);
		JsonObject json = new JsonObject();
		json.addProperty("formato", CaptainBrain.FORMAT);
		json.addProperty("grupo", "capitan");
		json.addProperty("ticks_por_decision", 10);
		JsonArray namesJson = new JsonArray();
		names.forEach(namesJson::add);
		json.add("nombres_obs", namesJson);
		int hidden = 8;
		int memory = 6;
		json.add("w1", noise(random, names.size(), hidden));
		json.add("b1", noise(random, 1, hidden).get(0));
		json.add("w2", noise(random, hidden, hidden));
		json.add("b2", noise(random, 1, hidden).get(0));
		json.add("gru_ih", noise(random, 3 * memory, hidden));
		json.add("gru_hh", noise(random, 3 * memory, memory));
		json.add("gru_bih", noise(random, 1, 3 * memory).get(0));
		json.add("gru_bhh", noise(random, 1, 3 * memory).get(0));
		json.add("w_out", noise(random, hidden + memory, CaptainBrain.OUTPUTS));
		JsonArray bias = new JsonArray();
		for (int k = 0; k < CaptainBrain.OUTPUTS; k++) {
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
	 * A captain network in redes_v4 (red_capitan.json) is loaded and gives the orders: one that wants HOSTIGAR in a wedge
	 * (CUÑA) gets them; its 213 inputs are finite. A captain file with the wrong names is refused and noted. In a batch of its
	 * own (its environment): the network it loads is every group's while it lasts.
	 */
	@GameTest(environment = "forja-test:capitan_red", padding = 16, maxTicks = 60)
	public void aCaptainNetworkGivesTheOrders(GameTestHelper helper) throws java.io.IOException {
		floor(helper, 16);
		CombatConfig cfg = CombatConfig.get();
		String savedFolder = cfg.iaCarpetaRedes;
		String savedContract = cfg.iaContrato;
		Path root = Files.createTempDirectory("forja_redes");
		Path v3 = Files.createDirectories(root.resolve("redes"));
		Path v4 = Files.createDirectories(root.resolve("redes_v4"));
		float[] bias = new float[CaptainBrain.OUTPUTS];
		bias[CaptainBrain.ORDER_AT + Captain.Order.HOSTIGAR.ordinal()] = 30.0F;
		bias[CaptainBrain.FORMATION_AT + Captain.Formation.CUNA.ordinal()] = 30.0F;
		Files.writeString(v4.resolve("red_" + MobAi.V4_CAPTAIN + ".json"), new com.google.gson.Gson().toJson(fakeCaptain(3L, bias)));
		cfg.iaCarpetaRedes = v3.toAbsolutePath().toString();
		cfg.iaContrato = "auto";
		MobAi.reload();
		helper.assertTrue(MobAi.captainNet() != null && CaptainBrain.check(MobAi.captainNet()) == null, "la red de capitán se carga: " + MobAi.problems());
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(8, 1, 8));
		// the rules captain off for this group: a loaded network gives the orders all the same
		CaptainBrain.override(player, CaptainBrain.Mode.LIBRE);
		List<Mob> mobs = group(helper, player);
		hold(helper, player, mobs);
		helper.runAfterDelay(25, () -> {
			try {
				Captain.Group g = Captain.group(player);
				helper.assertTrue(g != null && g.captain != null, "hay capitán");
				helper.assertTrue(g.command.order == Captain.Order.HOSTIGAR && g.command.formation == Captain.Formation.CUNA,
					"la red manda HOSTIGAR en CUÑA: " + g.command.order + " " + g.command.formation);
				float[] obs = CaptainBrain.observe(g, player, helper.getLevel().getGameTime());
				helper.assertTrue(obs.length == 213, "213 entradas: " + obs.length);
				for (int i = 0; i < obs.length; i++) {
					helper.assertTrue(Float.isFinite(obs[i]), "entrada " + i + " (" + CaptainBrain.names().get(i) + ") no finita");
				}
				JsonObject wrong = fakeCaptain(4L, new float[0]);
				wrong.getAsJsonArray("nombres_obs").set(0, new com.google.gson.JsonPrimitive("otra_cosa"));
				Files.writeString(v4.resolve("red_" + MobAi.V4_CAPTAIN + ".json"), new com.google.gson.Gson().toJson(wrong));
				MobAi.reload();
				helper.assertTrue(MobAi.captainNet() == null && String.valueOf(MobAi.problems().get(MobAi.V4_CAPTAIN)).startsWith("v4:"),
					"un capitán con otros nombres se rechaza: " + MobAi.problems());
			} catch (java.io.IOException failure) {
				throw new RuntimeException(failure);
			} finally {
				cfg.iaCarpetaRedes = savedFolder;
				cfg.iaContrato = savedContract;
				CaptainBrain.override(player, null);
				MobAi.reload();
			}
			clear(helper, 16);
			helper.succeed();
		});
	}

	/** The captain's contract file for the simulator (docs/red_capitan_v4_contrato.json), found walking up from the game. */
	private static Path contractFile() {
		for (Path start : List.of(FabricLoader.getInstance().getGameDir().toAbsolutePath(), Path.of("").toAbsolutePath())) {
			for (Path at = start; at != null; at = at.getParent()) {
				for (String relative : new String[] {"docs/red_capitan_v4_contrato.json", "forja/docs/red_capitan_v4_contrato.json"}) {
					if (Files.exists(at.resolve(relative))) {
						return at.resolve(relative);
					}
				}
			}
		}
		return null;
	}

	/**
	 * Writes the captain's contract when FORJA_CONTRATO_CAPITAN names a file; otherwise checks that the one in docs is
	 * CaptainBrain's, name for name (inputs and outputs).
	 */
	@GameTest
	public void captainContractMatches(GameTestHelper helper) throws java.io.IOException {
		String target = System.getenv("FORJA_CONTRATO_CAPITAN");
		if (target != null && !target.isBlank()) {
			JsonObject json = new JsonObject();
			json.addProperty("formato", "red_capitan_v4_contrato");
			json.addProperty("version", 1);
			json.addProperty("formato_red", CaptainBrain.FORMAT);
			json.addProperty("carpeta_mod", "config/forja/redes_v4/red_capitan.json");
			json.addProperty("ticks_por_decision", 10);
			json.addProperty("diseno", "red_mob_v4_diseno.md §3.2-§3.4; red_mob_v4_mod_estado.md (M5)");
			json.addProperty("n_obs", CaptainBrain.SIZE);
			JsonArray obs = new JsonArray();
			CaptainBrain.names().forEach(obs::add);
			json.add("nombres_obs", obs);
			JsonArray blocks = new JsonArray();
			String[][] parts = {{"J", "0", "38", "el jugador (visto desde el capitán; con el sustituto si no lo percibe)"},
				{"G", "38", "25", "el grupo"}, {"K", "63", "120", "8 miembros × 15, del más cercano al jugador al más lejano, en el marco capitán → jugador"},
				{"W", "183", "16", "el vector de mundo, el mismo que el del mob"}, {"O", "199", "14", "la orden vigente (9), su edad/40 y la formación (4)"}};
			for (String[] p : parts) {
				JsonObject b = new JsonObject();
				b.addProperty("bloque", p[0]);
				b.addProperty("inicio", Integer.parseInt(p[1]));
				b.addProperty("n", Integer.parseInt(p[2]));
				b.addProperty("que", p[3]);
				blocks.add(b);
			}
			json.add("bloques", blocks);
			json.addProperty("n_salidas", CaptainBrain.OUTPUTS);
			JsonArray outs = new JsonArray();
			CaptainBrain.outputs().forEach(outs::add);
			json.add("salidas", outs);
			JsonArray heads = new JsonArray();
			Object[][] h = {{"orden", CaptainBrain.ORDER_AT, 9}, {"formacion", CaptainBrain.FORMATION_AT, 4}, {"sector", CaptainBrain.SECTOR_AT, 9},
				{"cuenta", CaptainBrain.COUNT_AT, 4}, {"foco", CaptainBrain.FOCUS_AT, 2}};
			for (Object[] head : h) {
				JsonObject o = new JsonObject();
				o.addProperty("nombre", (String) head[0]);
				o.addProperty("tipo", "softmax");
				JsonArray idx = new JsonArray();
				for (int k = 0; k < (int) head[2]; k++) {
					idx.add((int) head[1] + k);
				}
				o.add("indices", idx);
				heads.add(o);
			}
			for (int k = 0; k < Captain.MEMBERS; k++) {
				JsonObject o = new JsonObject();
				o.addProperty("nombre", "puesto" + k);
				o.addProperty("tipo", "softmax");
				JsonArray idx = new JsonArray();
				for (int j = 0; j < 4; j++) {
					idx.add(CaptainBrain.POSTS_AT + 4 * k + j);
				}
				o.add("indices", idx);
				heads.add(o);
			}
			json.add("cabezas", heads);
			JsonObject mask = new JsonObject();
			mask.addProperty("orden_asedio", "jugador con sobre el suelo ≥ 2 o en torre (como ASEDIAR del mob)");
			mask.addProperty("foco_otro", "otro jugador vivo a ≤ 16 del capitán");
			mask.addProperty("puestoK_1..3", "miembro K presente (el 0 de cada softmax nunca se prohíbe)");
			json.add("mascara", mask);
			JsonObject values = new JsonObject();
			values.addProperty("orden", "0 NINGUNA, 1 CERCAR, 2 CARGA, 3 HOSTIGAR, 4 RETIRADA, 5 REAGRUPAR, 6 EMBOSCADA, 7 ASEDIO, 8 ESCOLTA");
			values.addProperty("formacion", "0 LIBRE, 1 MURO, 2 PINZA, 3 CUNA");
			values.addProperty("sector", "0 ninguno; k = 1..8: dirección desde el jugador = su mirada lenta + (k − 1)·45°, girando a la derecha");
			values.addProperty("cuenta", "0, 10, 20 o 40 ticks hasta la carga (solo con CARGA)");
			values.addProperty("foco", "0 su jugador, 1 el otro jugador más cercano al capitán");
			values.addProperty("puesto", "0 frente, 1 segunda línea, 2 flanco, 3 reserva; los miembros 9 y siguientes, por su tipo (reglas)");
			json.add("valores", values);
			json.addProperty("red", "213 → 128 → 128 (tanh) → GRUCell 64 → 60, el cálculo de NetBrain.forward (el tamaño lo da el archivo)");
			Files.writeString(Path.of(target), new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json));
			helper.succeed();
			return;
		}
		Path file = contractFile();
		helper.assertTrue(file != null, "no se encuentra docs/red_capitan_v4_contrato.json");
		JsonObject contract = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		List<String> theirs = new ArrayList<>();
		contract.getAsJsonArray("nombres_obs").forEach(n -> theirs.add(n.getAsString()));
		helper.assertTrue(theirs.equals(CaptainBrain.names()), "los 213 nombres del contrato del capitán son los de CaptainBrain");
		List<String> outs = new ArrayList<>();
		contract.getAsJsonArray("salidas").forEach(n -> outs.add(n.getAsString()));
		helper.assertTrue(outs.equals(CaptainBrain.outputs()) && contract.get("n_salidas").getAsInt() == CaptainBrain.OUTPUTS, "y las 60 salidas");
		helper.succeed();
	}
}
