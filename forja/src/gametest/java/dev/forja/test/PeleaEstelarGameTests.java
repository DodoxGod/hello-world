package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.forja.entity.FallenSmith;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.forge.ForgedStar;
import dev.forja.forge.Potential;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEntities;
import dev.forja.registry.ModItems;
import dev.forja.world.Apprentices;
import dev.forja.world.Formation;
import dev.forja.world.StarChart;
import dev.forja.world.StarFight;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Fallen Smith's fight in the Cementerio entre Estrellas (docs/HERRERO_DIMENSION.md, sections 3 and 4).
 *
 * <p>The gametest server has no datapack dimensions, so the tests that need the graveyard's fight ask the
 * smith for it ({@link FallenSmith#yardForTests}): its braseros and forge fires then stand at the graveyard's
 * own coordinates round (0, 81, 0) of the test world, which is empty air up there.
 */
public class PeleaEstelarGameTests {
	/** 4 apprentices and 3 more a player; rings of a square, then one vertex more each; 1 or 2 left over join the ring inside. */
	@GameTest(maxTicks = 20)
	public void apprenticeCountsAndFormations(GameTestHelper helper) {
		int[] expected = {4, 7, 10, 13};
		String[] rings = {"[4]", "[4, 3]", "[4, 6]", "[4, 5, 4]"};
		for (int players = 1; players <= 4; players++) {
			int count = Formation.count(players);
			helper.assertTrue(count == expected[players - 1], players + " jugadores: " + count + " aprendices");
			helper.assertTrue(Formation.rings(count).toString().equals(rings[players - 1]), players + " jugadores, anillos " + Formation.rings(count));
			List<Vec3> offsets = Formation.offsets(count);
			List<Integer> of = Formation.ringOf(count);
			helper.assertTrue(offsets.size() == count, "tantos puestos como aprendices");
			for (int i = 0; i < count; i++) {
				double r = offsets.get(i).horizontalDistance();
				helper.assertTrue(Math.abs(r - Formation.radius(of.get(i))) < 1.0E-6, "cada uno en su anillo: " + r);
			}
			// Regular: in each ring, every neighbour the same distance apart.
			int start = 0;
			for (int take : Formation.rings(count)) {
				if (take > 1) {
					double side = offsets.get(start).distanceTo(offsets.get(start + 1));
					for (int k = 0; k < take; k++) {
						double d = offsets.get(start + k).distanceTo(offsets.get(start + (k + 1) % take));
						helper.assertTrue(Math.abs(d - side) < 1.0E-6, "polígono regular: " + d + " frente a " + side);
					}
				}
				start += take;
			}
		}
		// The square points south first: (0, 3).
		Vec3 first = Formation.offsets(4).get(0);
		helper.assertTrue(Math.abs(first.x) < 1.0E-6 && Math.abs(first.z - 3.0) < 1.0E-6, "el primero, al sur: " + first);
		helper.succeed();
	}

	/**
	 * Apprentices come only at two thirds and at one third, and each only once; they come up out of the
	 * ground and cannot be hurt until they are out; he cannot be hurt while they come.
	 */
	@GameTest(maxTicks = 260)
	public void phasesAtTwoThirdsAndOneThirdOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FallenSmith smith = helper.spawn(ModEntities.HERRERO_CAIDO, new BlockPos(4, 1, 4));
		smith.setNoAi(true);
		smith.setHealth(smith.getMaxHealth() * 0.7F);
		List<Integer> seen = new ArrayList<>();
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(smith.wavesCalled() == 0, "a 70 % no hay aprendices");
			helper.assertTrue(smith.phase() == 1, "fase 1 a 70 %");
			smith.setHealth(smith.getMaxHealth() * 0.65F);
		});
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(smith.wavesCalled() == 1, "a 65 % viene la primera oleada");
			helper.assertTrue(smith.phase() == 2, "fase 2");
			List<Mob> risen = apprentices(helper, smith);
			seen.add(risen.size());
			helper.assertTrue(risen.size() == 4, "cuatro aprendices: " + risen.size());
			for (Mob apprentice : risen) {
				helper.assertTrue(Apprentices.rising(apprentice) && apprentice.isInvulnerable(), "salen de la tierra, intocables");
				helper.assertTrue(apprentice.getY() < smith.getY() - 1.0, "empiezan bajo el suelo: " + apprentice.getY());
			}
			float before = smith.getHealth();
			smith.hurtServer(level, level.damageSources().magic(), 10.0F);
			helper.assertTrue(smith.getHealth() == before, "el Herrero no recibe daño mientras salen");
		});
		helper.runAfterDelay(6 + FallenSmith.RISE_TICKS + 20, () -> {
			for (Mob apprentice : apprentices(helper, smith)) {
				helper.assertFalse(Apprentices.rising(apprentice) || apprentice.isInvulnerable(), "ya han salido");
				helper.assertTrue(Math.abs(apprentice.getY() - smith.getY()) < 1.6, "fuera del suelo: " + apprentice.getY());
			}
			smith.setHealth(smith.getMaxHealth() * 0.6F);
		});
		helper.runAfterDelay(6 + FallenSmith.RISE_TICKS + 24, () -> {
			helper.assertTrue(smith.wavesCalled() == 1, "a 60 % no vuelven a venir");
			smith.setHealth(smith.getMaxHealth() * 0.3F);
		});
		helper.runAfterDelay(6 + FallenSmith.RISE_TICKS + 28, () -> {
			helper.assertTrue(smith.wavesCalled() == 2 && smith.phase() == 3, "a 30 % la segunda oleada y la fase 3");
			smith.setHealth(smith.getMaxHealth() * 0.1F);
		});
		helper.runAfterDelay(6 + FallenSmith.RISE_TICKS + 32, () -> {
			helper.assertTrue(smith.wavesCalled() == 2, "no hay una tercera");
			level.getEntitiesOfClass(Mob.class, ownGround(helper), Apprentices::isApprentice).forEach(Mob::discard);
			level.getEntitiesOfClass(Mob.class, ownGround(helper), mob -> mob instanceof dev.forja.entity.WalkingAnvil).forEach(Mob::discard);
			smith.discard();
			helper.succeed();
		});
	}

	/**
	 * Where this test's apprentices can be: its box and four blocks round it, their rings being at most 7 from the
	 * smith. It was 12 or 16 blocks round the smith, and the tests of a batch stand 13 apart: a test tidying up took the
	 * apprentices of the one next door, which then found none of its four ("cuatro aprendices: 0").
	 */
	private static AABB ownGround(GameTestHelper helper) {
		return helper.getBounds().inflate(4.0);
	}

	private static List<Mob> apprentices(GameTestHelper helper, FallenSmith smith) {
		return helper.getLevel().getEntitiesOfClass(Mob.class, smith.getBoundingBox().inflate(9.0, 4.0, 9.0), Apprentices::isApprentice);
	}

	/**
	 * The Reforjado estelar: at half he lights his forge fires and nothing touches him, with no timer; a
	 * tipped brasero's metal runs to the middle and puts out the fires on its line; the last one out leaves
	 * him stunned and taking half again; a brasero is refilled with four star iron.
	 */
	@GameTest(maxTicks = 400)
	public void theReforgeEndsOnlyWhenTheBraserosPutItOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FallenSmith smith = helper.spawn(ModEntities.HERRERO_CAIDO, new BlockPos(4, 1, 4));
		smith.yardForTests = true;
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		player.teleportTo(smith.getX() + 3.0, smith.getY(), smith.getZ());
		smith.setHealth(smith.getMaxHealth() * 0.6F);
		helper.runAfterDelay(FallenSmith.PHASE_GUARD + 10, () -> {
			helper.assertTrue(smith.wavesCalled() == 1, "primera oleada llamada");
			smith.setHealth(smith.getMaxHealth() * 0.45F);
		});
		helper.runAfterDelay(FallenSmith.PHASE_GUARD + 14, () -> {
			helper.assertTrue(smith.isReforging() && smith.starReforge() == 1, "a la mitad, el Reforjado");
			List<BlockPos> embers = smith.embersAt();
			helper.assertTrue(embers.size() == 3, "tres brasas con un jugador: " + embers.size());
			for (BlockPos at : embers) {
				helper.assertTrue(level.getBlockState(at).is(ModBlocks.BRASA_ESTELAR), "brasa encendida en " + at);
			}
			for (int i = 0; i < 4; i++) {
				helper.assertTrue(StarFight.full(level, i), "braseros llenos al empezar");
			}
			float before = smith.getHealth();
			smith.hurtServer(level, level.damageSources().playerAttack(player), 20.0F);
			helper.assertTrue(smith.getHealth() == before, "inmortal mientras arde");
			// Tip three braseros: the ones on the three fires' diagonals.
			for (int i = 0; i < 3; i++) {
				helper.assertTrue(StarFight.tip(level, i), "se vuelca el brasero " + i);
			}
			helper.assertFalse(StarFight.full(level, 0), "y queda vacío");
		});
		// Run the flows by hand: the graveyard's tick does it there.
		helper.onEachTick(() -> StarFight.flowsForTests(level));
		helper.runAfterDelay(FallenSmith.PHASE_GUARD + 14 + StarFight.FLOW_TICKS + 20, () -> {
			for (BlockPos at : smith.embersAt()) {
				helper.assertFalse(level.getBlockState(at).is(ModBlocks.BRASA_ESTELAR), "la colada apagó la brasa de " + at);
			}
			helper.assertTrue(smith.starReforge() == 2 && smith.isStunned(), "apagada la última, queda aturdido");
			float before = smith.getHealth();
			smith.invulnerableTime = 0;
			smith.hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
			float taken = before - smith.getHealth();
			helper.assertTrue(taken > 0.0F, "aturdido, ya recibe daño: " + taken);
			// Refill with four star iron.
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.HIERRO_ESTELAR, 4));
			helper.assertTrue(StarFight.refill(level, 0) && StarFight.full(level, 0), "el brasero se rellena");
			for (int i = 0; i < 4; i++) {
				level.setBlockAndUpdate(StarFight.brazier(i), Blocks.AIR.defaultBlockState());
			}
			smith.discard();
			level.getEntitiesOfClass(Mob.class, ownGround(helper), Apprentices::isApprentice).forEach(Mob::discard);
			helper.succeed();
		});
	}

	/** Half and half: four constellations help him and four the players, and each phase's colours add to 100. */
	@GameTest(maxTicks = 20)
	public void theConstellationsAreHalfAndHalf(GameTestHelper helper) {
		int forBoss = 0;
		for (boolean side : StarFight.FOR_BOSS) {
			forBoss += side ? 1 : 0;
		}
		helper.assertTrue(StarFight.FOR_BOSS.length == StarChart.CONSTELLATIONS.size() && forBoss == 4, "cuatro para cada lado: " + forBoss);
		helper.assertTrue(StarFight.FOR_BOSS[StarFight.ESCUDO], "la Égida es del Herrero");
		for (int[] weights : StarFight.WEIGHTS) {
			int sum = 0;
			for (int w : weights) {
				sum += w;
			}
			helper.assertTrue(sum == 100, "los colores de una fase suman 100: " + sum);
		}
		helper.assertTrue(StarFight.WEIGHTS[0][4] == 0 && StarFight.WEIGHTS[2][4] > 0, "el carmesí solo en la fase 3");
		for (int i = 1; i < StarFight.STRENGTH.length; i++) {
			helper.assertTrue(StarFight.STRENGTH[i] > StarFight.STRENGTH[i - 1], "cada color más fuerte que el anterior");
		}
		helper.succeed();
	}

	/** The fight is kept on him through a save and a load: his phase, the waves called, the reforge and its fires. */
	@GameTest(maxTicks = 20)
	public void theFightOutlivesASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FallenSmith smith = helper.spawn(ModEntities.HERRERO_CAIDO, new BlockPos(4, 1, 4));
		smith.setNoAi(true);
		smith.setHealth(smith.getMaxHealth() * 0.6F);
		smith.tick();
		helper.assertTrue(smith.wavesCalled() == 1, "primera oleada");
		var saved = net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING);
		smith.saveWithoutId(saved);
		FallenSmith loaded = ModEntities.HERRERO_CAIDO.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
		loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
		helper.assertTrue(loaded.wavesCalled() == 1, "la oleada sigue contada tras cargar");
		helper.assertTrue(Math.abs(loaded.getHealth() - smith.getHealth()) < 0.01F, "y su vida: " + loaded.getHealth());
		helper.assertTrue(loaded.phase() == 2, "y su fase");
		level.getEntitiesOfClass(Mob.class, ownGround(helper), Apprentices::isApprentice).forEach(Mob::discard);
		smith.discard();
		// And the fight's own record on the level.
		UUID someone = UUID.randomUUID();
		StarFight.State state = new StarFight.State(StarFight.Stage.FIGHTING, Optional.of(UUID.randomUUID()), List.of(someone), List.of(), 5L, 9L, 1);
		var encoded = StarFight.State.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, state).getOrThrow();
		StarFight.State decoded = StarFight.State.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
		helper.assertTrue(decoded.equals(state), "el estado de la pelea se guarda entero: " + decoded);
		helper.succeed();
	}

	/**
	 * When he dies the star lands (the way home and the cold forge of a rematch), every participant is owed
	 * one Estrella forjada, and is paid it once.
	 */
	@GameTest(maxTicks = 40)
	public void hisDeathLeavesTheStarAndOneRewardEach(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FallenSmith smith = helper.spawn(ModEntities.HERRERO_CAIDO, new BlockPos(4, 1, 4));
		smith.setNoAi(true);
		ServerPlayer one = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		UUID absent = UUID.randomUUID();
		StarFight.setStateForTests(level, new StarFight.State(StarFight.Stage.FIGHTING, Optional.of(smith.getUUID()),
			List.of(one.getUUID(), absent), List.of(), 0L, 0L, 0));
		StarFight.winForTests(level, smith);
		StarFight.State after = StarFight.state(level);
		helper.assertTrue(after.stage() == StarFight.Stage.WON, "la pelea queda ganada");
		helper.assertTrue(after.owed().contains(absent), "al que no está se le guarda su estrella");
		StarFight.payForTests(level, List.of(one));
		StarFight.payForTests(level, List.of(one));
		int paid = StarFight.paidForTests(one.getUUID());
		helper.assertTrue(paid == 1, "una Estrella, una sola vez: " + paid);
		level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, one.getBoundingBox().inflate(3.0)).forEach(net.minecraft.world.entity.Entity::discard);
		helper.assertTrue(StarFight.state(level).owed().equals(List.of(absent)), "y solo queda la del ausente");
		StarFight.landForTests(level);
		helper.assertTrue(level.getBlockState(StarFight.returnStarAt()).is(ModBlocks.ESTRELLA_DE_VUELTA), "cae la estrella de vuelta");
		helper.assertTrue(level.getBlockState(StarFight.forgeAt()).is(ModBlocks.FRAGUA_FRIA_ESTELAR), "y la fragua fría de la revancha");
		// The rematch's price has nothing only he gives in it.
		for (dev.forja.block.StarForgeBlock.Cost cost : dev.forja.block.StarForgeBlock.COST) {
			helper.assertFalse(cost.item().get() == ModItems.CORAZON_DE_FORJA, "la revancha no pide el corazón de forja");
		}
		level.setBlockAndUpdate(StarFight.returnStarAt(), Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(StarFight.forgeAt(), Blocks.AIR.defaultBlockState());
		StarFight.setStateForTests(level, new StarFight.State(StarFight.Stage.IDLE, Optional.empty(), List.of(), List.of(), 0L, 0L, 0));
		smith.discard();
		helper.succeed();
	}

	/**
	 * The Estrella forjada: potential up to 125 and 25 more at once (26 points of upgrades), damage and
	 * mining ×1.12, durability ×1.5, and a piece of armour +1 armour and +0.5 toughness.
	 */
	@GameTest(maxTicks = 20)
	public void theForgedStarMakesAPieceStronger(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.CORAZON, ForgeMaterial.CORAZON, ForgeMaterial.CORAZON), registries);
		sword.set(ModComponents.POTENCIAL, 100);
		ItemStack starred = ForgedStar.star(sword);
		helper.assertTrue(ForgedStar.starred(starred) && !ForgedStar.takes(starred), "estrellada, y no admite otra");
		helper.assertTrue(Potential.of(sword) == Potential.MOST && Potential.of(starred) == ForgedStar.MOST,
			"potencial " + Potential.of(sword) + " → " + Potential.of(starred));
		helper.assertTrue(Potential.capacity(starred) == 26 && Potential.capacity(sword) == 20, "carga 20 → " + Potential.capacity(starred));
		var plain = ForgeStats.sheet(sword, sword.get(ModComponents.PARTS));
		var bright = ForgeStats.sheet(starred, starred.get(ModComponents.PARTS));
		helper.assertTrue(Math.abs(bright.attackDamage - plain.attackDamage * ForgedStar.DAMAGE) < 0.01F, "daño ×1,12: " + plain.attackDamage + " → " + bright.attackDamage);
		helper.assertTrue(starred.getMaxDamage() == Math.round(sword.getMaxDamage() * ForgedStar.DURABILITY), "durabilidad ×1,5: " + sword.getMaxDamage() + " → " + starred.getMaxDamage());
		ItemStack chest = Assembler.create(ForgeType.PECHERA, List.of(ForgeMaterial.CORAZON, ForgeMaterial.ESCAMA), registries);
		var chestPlain = ForgeStats.sheet(chest, chest.get(ModComponents.PARTS));
		ItemStack chestStar = ForgedStar.star(chest);
		var chestBright = ForgeStats.sheet(chestStar, chestStar.get(ModComponents.PARTS));
		helper.assertTrue(chestBright.armor == chestPlain.armor + ForgedStar.ARMOR, "armadura +1: " + chestPlain.armor + " → " + chestBright.armor);
		helper.assertTrue(Math.abs(chestBright.toughness - chestPlain.toughness - ForgedStar.TOUGHNESS) < 0.01F, "dureza +0,5");
		helper.succeed();
	}
}
