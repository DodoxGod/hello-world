package dev.forja.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.forja.combat.CombatConfig;
import dev.forja.entity.FallenSmith;
import dev.forja.registry.ModEntities;
import dev.forja.world.Apprentices;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * El jefe se defiende (Andy, 2026-09-28): "el jefe no se defiende si algo lo ataca, y los aprendices no
 * atacan a lo que lo llegan a atacar, por lo que si pones un warden al lado del jefe éste lo mata y ni
 * siquiera tienes que pelear".
 */
public class JefeGameTests {
	/** A smith that will not wander or swing, for the tests that only read whom he has chosen. */
	private static FallenSmith stillSmith(GameTestHelper helper, BlockPos pos) {
		FallenSmith smith = helper.spawn(ModEntities.HERRERO_CAIDO, pos);
		smith.setNoAi(true);
		return smith;
	}

	/** Keeps the natural-spawn dressing (veterans, elites) off the mobs a test spawns, and gives it back after. */
	private static Runnable plainSpawns() {
		CombatConfig cfg = CombatConfig.get();
		double veteran = cfg.veteranChance;
		double elite = cfg.eliteChance;
		cfg.veteranChance = 0.0;
		cfg.eliteChance = 0.0;
		return () -> {
			cfg.veteranChance = veteran;
			cfg.eliteChance = elite;
		};
	}

	/** An iron golem hits him: he turns on it, and it gets hurt for it. */
	@GameTest(maxTicks = 200)
	public void smithFightsBackAgainstAGolem(GameTestHelper helper) {
		FallenSmith smith = helper.spawn(ModEntities.HERRERO_CAIDO, new BlockPos(2, 1, 3));
		var golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(4, 1, 3));
		// Right beside him, so he answers with his fists and the backhand rather than a ring of fire
		// that would reach the tests next door.
		golem.setNoAi(true);
		float golemHealth = golem.getHealth();
		smith.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(golem), 4.0F);
		helper.assertTrue(smith.getTarget() == golem, "debería volverse contra el gólem, apunta a " + smith.getTarget());
		helper.succeedWhen(() -> {
			helper.assertTrue(golem.getHealth() < golemHealth, "el gólem debería llevarse algo: " + golem.getHealth());
			golem.discard();
			smith.discard();
		});
	}

	/** Fighting a golem, a player hits him: he comes for the player, and the golem does not take him back. */
	@GameTest(maxTicks = 40)
	public void smithTurnsToThePlayerWhoHitsHim(GameTestHelper helper) {
		FallenSmith smith = stillSmith(helper, new BlockPos(2, 1, 2));
		var golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(5, 1, 2));
		golem.setNoAi(true);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(2, 1, 5));
		var level = helper.getLevel();
		smith.hurtServer(level, level.damageSources().mobAttack(golem), 3.0F);
		helper.assertTrue(smith.getTarget() == golem, "primero el gólem, apunta a " + smith.getTarget());
		smith.invulnerableTime = 0;
		smith.hurtServer(level, level.damageSources().playerAttack(player), 3.0F);
		helper.assertTrue(smith.getTarget() == player, "quien le pega es el jugador: debería ir a por él, apunta a " + smith.getTarget());
		// The golem keeps hitting, but the player has hit him within FOCUS_TICKS: he stays on the player.
		smith.invulnerableTime = 0;
		smith.hurtServer(level, level.damageSources().mobAttack(golem), 3.0F);
		helper.assertTrue(smith.getTarget() == player, "peleando con un jugador el gólem no se lo quita, apunta a " + smith.getTarget());
		helper.assertFalse(smith.heldByFoe(), "con el jugador, nada lo retiene");
		golem.discard();
		smith.discard();
		helper.succeed();
	}

	/** Whatever hurts the smith, his apprentices go for, and never for him or for each other. */
	@GameTest(maxTicks = 120)
	public void apprenticesGoForWhoeverHurtsTheSmith(GameTestHelper helper) {
		Runnable restore = plainSpawns();
		FallenSmith smith = stillSmith(helper, new BlockPos(4, 1, 4));
		// Under two thirds: his next tick calls them (docs/HERRERO_DIMENSION.md, 3.3).
		smith.setHealth(smith.getMaxHealth() * 0.6F);
		// A spider, because a skeleton never goes for one by itself: if they do, it is for him.
		LivingEntity spider = toughSpider(helper);
		List<Mob> apprentices = new ArrayList<>();
		Set<Mob> answered = new HashSet<>();
		// Everything is scheduled here, up front: a test's tick callbacks live in a map that is being walked
		// while they run, and one that books more of them can end up run twice.
		helper.runAfterDelay(3, () -> {
			apprentices.addAll(called(helper, smith));
			helper.assertTrue(apprentices.size() == 4, "deberían venir cuatro aprendices, hay " + apprentices.size());
			for (Mob apprentice : apprentices) {
				helper.assertTrue(apprentice.getTarget() != spider, "nadie le ha hecho nada todavía a la araña");
			}
			smith.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(spider), 2.0F);
		});
		helper.onEachTick(() -> {
			for (Mob apprentice : apprentices) {
				LivingEntity target = apprentice.getTarget();
				helper.assertFalse(target == smith, "un aprendiz apunta a su maestro");
				helper.assertFalse(target instanceof Mob other && Apprentices.isApprentice(other), "un aprendiz apunta a otro");
				if (target == spider) {
					answered.add(apprentice);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(apprentices.size() == 4 && answered.size() == 4, "los cuatro deberían ir a por la araña, van " + answered.size());
			apprentices.forEach(Mob::discard);
			spider.discard();
			smith.discard();
			restore.run();
		});
	}

	/** With nothing of their own to fight, the apprentices join whatever the smith is fighting. */
	@GameTest(maxTicks = 120)
	public void apprenticesJoinTheSmithsFight(GameTestHelper helper) {
		Runnable restore = plainSpawns();
		FallenSmith smith = stillSmith(helper, new BlockPos(4, 1, 4));
		smith.setHealth(smith.getMaxHealth() * 0.6F);
		LivingEntity spider = toughSpider(helper);
		List<Mob> apprentices = new ArrayList<>();
		Set<Mob> joined = new HashSet<>();
		helper.runAfterDelay(3, () -> {
			apprentices.addAll(called(helper, smith));
			helper.assertTrue(apprentices.size() == 4, "cuatro aprendices, hay " + apprentices.size());
		});
		// Nothing has hit him, so nothing holds him to the spider: set just after his twice-a-second look
		// round, so a mock player from a test next door cannot take him before they have joined.
		helper.runAfterDelay(3 + Math.floorMod(1 - (smith.tickCount + 3), 10), () -> smith.setTarget(spider));
		helper.onEachTick(() -> {
			for (Mob apprentice : apprentices) {
				if (smith.getTarget() == spider && apprentice.getTarget() == spider) {
					joined.add(apprentice);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(apprentices.size() == 4 && joined.size() == 4, "los cuatro, sin pelea propia, deberían ayudarle; van " + joined.size());
			apprentices.forEach(Mob::discard);
			spider.discard();
			smith.discard();
			restore.run();
		});
	}

	/** A spider with a thousand health that stands still: something to be gone for that does not die of it. */
	private static LivingEntity toughSpider(GameTestHelper helper) {
		var spider = helper.spawn(EntityTypes.SPIDER, new BlockPos(7, 1, 7));
		spider.setNoAi(true);
		spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		spider.setHealth(1000.0F);
		return spider;
	}

	/**
	 * His apprentices: the ones that came up round him (three blocks out), as opposed to the ones of a
	 * smith in the test next door. Read once, just after they arrive, and kept.
	 */
	private static List<Mob> called(GameTestHelper helper, FallenSmith smith) {
		return helper.getLevel().getEntitiesOfClass(Mob.class, smith.getBoundingBox().inflate(4.5),
			mob -> mob.isAlive() && Apprentices.isApprentice(mob));
	}

	/**
	 * A wild wolf's bite does jefeDanoAjeno of a tamed one's: the same blow, the same animal, and the only
	 * difference is that a player is behind the second.
	 */
	@GameTest
	public void othersOnlyDoPartOfTheirDamage(GameTestHelper helper) {
		Runnable restore = plainSpawns();
		var level = helper.getLevel();
		FallenSmith bitten = stillSmith(helper, new BlockPos(1, 1, 1));
		FallenSmith bittenByAPet = stillSmith(helper, new BlockPos(1, 1, 5));
		var wild = helper.spawn(EntityTypes.WOLF, new BlockPos(3, 1, 1));
		var pet = helper.spawn(EntityTypes.WOLF, new BlockPos(3, 1, 5));
		wild.setNoAi(true);
		pet.setNoAi(true);
		CombatGameTests.TestPlayer owner = CombatGameTests.player(helper, new BlockPos(6, 1, 5));
		pet.tame(owner);
		// Andy, 2026-09-29: a third, not a half.
		helper.assertTrue(new CombatConfig().jefeDanoAjeno == 0.333, "por defecto, un tercio: " + new CombatConfig().jefeDanoAjeno);
		helper.assertTrue(dev.forja.difficulty.Bosses.fromPlayer(pet), "un lobo domado cuenta como su dueño");
		helper.assertFalse(dev.forja.difficulty.Bosses.fromPlayer(wild), "uno salvaje no");
		var golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(6, 1, 1));
		golem.setNoAi(true);
		golem.setPlayerCreated(true);
		helper.assertFalse(dev.forja.difficulty.Bosses.fromPlayer(golem), "un gólem no es de nadie, ni uno hecho por un jugador");
		golem.discard();

		bitten.hurtServer(level, level.damageSources().mobAttack(wild), 10.0F);
		bittenByAPet.hurtServer(level, level.damageSources().mobAttack(pet), 10.0F);
		float wildLoss = bitten.getMaxHealth() - bitten.getHealth();
		float petLoss = bittenByAPet.getMaxHealth() - bittenByAPet.getHealth();
		double share = CombatConfig.get().jefeDanoAjeno;
		helper.assertTrue(petLoss > 0.0F, "el mordisco del lobo domado debería hacerle algo");
		helper.assertTrue(Math.abs(wildLoss - petLoss * share) <= petLoss * 0.03 + 0.01,
			"el lobo salvaje debería hacer " + share + " del domado: " + wildLoss + " contra " + petLoss);
		// Damage with nobody behind it (lava, an upgrade's lightning) is not the cheese and is left alone.
		helper.assertTrue(dev.forja.difficulty.Bosses.othersShare(level.damageSources().magic()) == 1.0F, "sin atacante no se toca");
		helper.assertTrue(dev.forja.difficulty.Bosses.othersShare(level.damageSources().playerAttack(owner)) == 1.0F, "el jugador pega entero");
		restore.run();
		helper.succeed();
	}

	/** The other big ones already answered whoever hit them; this keeps it that way. */
	@GameTest(maxTicks = 60)
	public void cuneGuardianAndChampionsFightBack(GameTestHelper helper) {
		Runnable restore = plainSpawns();
		var level = helper.getLevel();
		var cune = helper.spawn(ModEntities.GUARDIAN_DE_CUNO, new BlockPos(2, 1, 2));
		var champion = helper.spawn(EntityTypes.WITHER_SKELETON, new BlockPos(6, 1, 6));
		dev.forja.world.Elites.makeElite(champion, level.getRandom());
		var wolfA = helper.spawn(EntityTypes.WOLF, new BlockPos(2, 1, 5));
		var wolfB = helper.spawn(EntityTypes.WOLF, new BlockPos(6, 1, 3));
		wolfA.setNoAi(true);
		wolfB.setNoAi(true);
		wolfA.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		wolfA.setHealth(1000.0F);
		wolfB.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		wolfB.setHealth(1000.0F);
		// Not on the tick they appear: the hurt-by goal remembers the last blow by the victim's own tick
		// count, which starts at the same zero a blow on a mob's first tick is stamped with, and ignores it.
		helper.runAfterDelay(5, () -> {
			cune.hurtServer(level, level.damageSources().mobAttack(wolfA), 2.0F);
			champion.hurtServer(level, level.damageSources().mobAttack(wolfB), 2.0F);
		});
		Set<String> answered = new HashSet<>();
		helper.onEachTick(() -> {
			if (cune.getTarget() == wolfA) {
				answered.add("cuno");
			}
			if (champion.getTarget() == wolfB) {
				answered.add("campeon");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(answered.contains("cuno"), "el guardián del cuño debería volverse contra el lobo: apunta a "
				+ cune.getTarget() + ", le pegó " + cune.getLastHurtByMob() + ", vida " + cune.getHealth() + "/" + cune.getMaxHealth()
				+ ", sellado " + cune.sealed() + ", campeón " + answered.contains("campeon"));
			helper.assertTrue(answered.contains("campeon"), "un campeón debería volverse contra el lobo");
			cune.discard();
			champion.discard();
			wolfA.discard();
			wolfB.discard();
			restore.run();
		});
	}

	/** With the smith up nobody breaks or builds near him; once he is gone the ground is free again. */
	@GameTest(maxTicks = 40)
	public void theArenaIsLockedWhileTheSmithStands(GameTestHelper helper) {
		FallenSmith smith = stillSmith(helper, new BlockPos(2, 1, 2));
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(2, 1, 6));
		var level = helper.getLevel();
		helper.assertTrue(dev.forja.world.BossArena.locked(level, player), "con el herrero vivo, la arena debería estar cerrada");
		// Other tests' smiths may stand within reach of this one: only this smith's hold is checked once he is gone.
		smith.discard();
		helper.assertFalse(dev.forja.world.BossArena.lockers(level, player).contains(smith), "sin el herrero, se debería poder construir");
		helper.succeed();
	}

	// ------------------------------------------------------------------ La forja reclama

	/**
	 * Three golems hit him: he plays La forja reclama, and they are gone with nothing left behind, along with
	 * the spider that was only standing there. His apprentice, a player's wolf and the player are untouched.
	 * Padded, because it takes everything that is nobody's for twelve blocks round him.
	 */
	@GameTest(padding = 16, maxTicks = 120)
	public void aCrowdOfGolemsIsReclaimed(GameTestHelper helper) {
		Runnable restore = plainSpawns();
		var level = helper.getLevel();
		FallenSmith smith = stillSmith(helper, new BlockPos(4, 1, 4));
		List<Mob> golems = new ArrayList<>();
		for (BlockPos at : List.of(new BlockPos(1, 1, 4), new BlockPos(7, 1, 4), new BlockPos(4, 1, 1))) {
			Mob golem = helper.spawn(EntityTypes.IRON_GOLEM, at);
			golem.setNoAi(true);
			golems.add(golem);
		}
		Mob bystander = helper.spawn(EntityTypes.SPIDER, new BlockPos(1, 1, 1));
		bystander.setNoAi(true);
		Mob apprentice = helper.spawn(EntityTypes.WITHER_SKELETON, new BlockPos(4, 1, 7));
		apprentice.setNoAi(true);
		Apprentices.enlist(apprentice);
		CombatGameTests.TestPlayer owner = CombatGameTests.player(helper, new BlockPos(7, 1, 7));
		var pet = helper.spawn(EntityTypes.WOLF, new BlockPos(1, 1, 7));
		pet.setNoAi(true);
		pet.tame(owner);
		helper.assertFalse(FallenSmith.reclaimable(owner), "un jugador nunca");
		helper.assertFalse(FallenSmith.reclaimable(pet), "la mascota de un jugador nunca");
		helper.assertFalse(FallenSmith.reclaimable(apprentice), "sus aprendices nunca");
		helper.assertFalse(FallenSmith.reclaimable(smith), "él tampoco");
		boolean[] started = {false};
		helper.runAfterDelay(5, () -> {
			for (Mob golem : golems) {
				smith.invulnerableTime = 0;
				smith.hurtServer(level, level.damageSources().mobAttack(golem), 3.0F);
			}
		});
		helper.onEachTick(() -> started[0] |= smith.isReclaiming());
		helper.succeedWhen(() -> {
			helper.assertTrue(started[0], "tres gólems le han pegado: debería hacer La forja reclama");
			helper.assertFalse(smith.isReclaiming(), "todavía está en ello");
			for (Mob golem : golems) {
				helper.assertTrue(golem.isRemoved(), "el gólem debería haber desaparecido: " + golem);
			}
			helper.assertTrue(bystander.isRemoved(), "la araña que no es de nadie también se va");
			helper.assertTrue(apprentice.isAlive(), "a su aprendiz no lo toca");
			helper.assertTrue(pet.isAlive(), "al lobo de un jugador no lo toca");
			helper.assertTrue(owner.isAlive(), "al jugador no lo toca");
			AABB around = smith.getBoundingBox().inflate(FallenSmith.RECLAIM_RADIUS + 2.0);
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "no debería quedar nada en el suelo");
			helper.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class, around).isEmpty(), "ni experiencia");
			helper.assertTrue(smith.reclaimCooldown() > 0, "después, a esperar " + FallenSmith.RECLAIM_COOLDOWN + " ticks");
			apprentice.discard();
			pet.discard();
			smith.discard();
			restore.run();
		});
	}

	/** A player's dog biting him, however often, is the player fighting him: nothing is reclaimed. */
	@GameTest(padding = 16, maxTicks = 60)
	public void aPlayersWolvesAreNeverReclaimed(GameTestHelper helper) {
		Runnable restore = plainSpawns();
		var level = helper.getLevel();
		FallenSmith smith = stillSmith(helper, new BlockPos(4, 1, 4));
		CombatGameTests.TestPlayer owner = CombatGameTests.player(helper, new BlockPos(7, 1, 7));
		List<Mob> wolves = new ArrayList<>();
		for (BlockPos at : List.of(new BlockPos(2, 1, 4), new BlockPos(6, 1, 4), new BlockPos(4, 1, 2))) {
			var wolf = helper.spawn(EntityTypes.WOLF, at);
			wolf.setNoAi(true);
			wolf.tame(owner);
			wolves.add(wolf);
		}
		boolean[] started = {false};
		helper.runAfterDelay(5, () -> {
			// Three of them, three times each: more than would set it off if they were anybody's.
			for (int round = 0; round < 3; round++) {
				for (Mob wolf : wolves) {
					smith.invulnerableTime = 0;
					smith.hurtServer(level, level.damageSources().mobAttack(wolf), 4.0F);
				}
			}
		});
		helper.onEachTick(() -> started[0] |= smith.isReclaiming());
		helper.runAfterDelay(30, () -> {
			helper.assertFalse(started[0], "los lobos de un jugador no deberían hacerle reclamar");
			for (Mob wolf : wolves) {
				helper.assertTrue(wolf.isAlive(), "el lobo sigue ahí");
			}
			helper.assertTrue(smith.reclaimCooldown() == 0, "no ha gastado nada");
			wolves.forEach(Mob::discard);
			smith.discard();
			restore.run();
			helper.succeed();
		});
	}

	/** One golem alone does not set it off with a blow or two; one pounding him for FallenSmith.RECLAIM_HEAVY_DAMAGE does. */
	@GameTest(padding = 16, maxTicks = 100)
	public void aGolemPoundingHimAloneIsReclaimed(GameTestHelper helper) {
		Runnable restore = plainSpawns();
		var level = helper.getLevel();
		FallenSmith smith = stillSmith(helper, new BlockPos(4, 1, 4));
		Mob golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(2, 1, 4));
		golem.setNoAi(true);
		boolean[] early = {false};
		boolean[] started = {false};
		helper.runAfterDelay(5, () -> {
			smith.hurtServer(level, level.damageSources().mobAttack(golem), 10.0F);
		});
		helper.runAfterDelay(8, () -> {
			early[0] = smith.isReclaiming();
			// Ten of its hardest, at a third each: RECLAIM_HEAVY_DAMAGE and a little more.
			for (int blow = 0; blow < 10; blow++) {
				smith.invulnerableTime = 0;
				smith.hurtServer(level, level.damageSources().mobAttack(golem), 10.0F);
			}
		});
		helper.onEachTick(() -> started[0] |= smith.isReclaiming());
		helper.succeedWhen(() -> {
			helper.assertFalse(early[0], "un solo golpe de un gólem no basta");
			helper.assertTrue(started[0], "un gólem que le quita una décima parte sí");
			helper.assertTrue(golem.isRemoved(), "y el gólem desaparece");
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, smith.getBoundingBox().inflate(6.0)).isEmpty(), "sin hierro ni amapolas");
			smith.discard();
			restore.run();
		});
	}
}
