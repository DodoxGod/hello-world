package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.Scaling;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Grupos y rodear (Andy, 2026-09-27, through the simulator's session): "que sea difícil que aparezca un
 * zombi solo; si hay un veterano, que aparezcan en grupos de 3 a 6", and that they surround.
 */
public class GruposGameTests {
	/** A zombie as the natural spawner leaves it, of this threat, at a relative position. */
	private static Zombie natural(GameTestHelper helper, BlockPos relative, Threat threat) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
		zombie.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		zombie.addTag(Scaling.NATURAL);
		threat.mark(zombie);
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static int zombiesAround(GameTestHelper helper, Zombie centre) {
		return helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(centre.blockPosition()).inflate(4.0, 2.0, 4.0)).size();
	}

	/** A veteran comes with a pack of three to six; an ordinary one, when it brings any, with two or three in all. */
	@GameTest
	public void veteransComeInPacks(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		double night = cfg.nightCompanionMax;
		double chance = cfg.packChance;
		int crowd = cfg.packCrowd;
		double mix = cfg.packMixChance;
		double spacing = cfg.packLeaderSpacing;
		// Other tests run beside this one; their mobs are not this spawn's crowd. One kind, to count zombies.
		cfg.nightCompanionMax = 0.0;
		cfg.packChance = 1.0;
		cfg.packCrowd = 1000;
		cfg.packMixChance = 0.0;
		cfg.packLeaderSpacing = 0.0;
		// The empty test structure has no floor, and a companion needs something to stand on.
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		Zombie veteran = natural(helper, new BlockPos(4, 1, 4), Threat.VETERANO);
		int withVeteran = zombiesAround(helper, veteran);
		helper.runAfterDelay(1, () -> {
			veteran.discard();
			for (Zombie other : helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(veteran.blockPosition()).inflate(4.0, 2.0, 4.0))) {
				other.discard();
			}
			Zombie plain = natural(helper, new BlockPos(4, 1, 4), Threat.NORMAL);
			int withPlain = zombiesAround(helper, plain);
			cfg.nightCompanionMax = night;
			cfg.packChance = chance;
			cfg.packCrowd = crowd;
			cfg.packMixChance = mix;
			cfg.packLeaderSpacing = spacing;
			helper.assertTrue(withVeteran >= cfg.packVeteranMin && withVeteran <= cfg.packVeteranMax,
				"un veterano viene en grupo de " + cfg.packVeteranMin + " a " + cfg.packVeteranMax + ", vinieron " + withVeteran);
			helper.assertTrue(withPlain >= cfg.packMin && withPlain <= cfg.packMax,
				"uno corriente, con grupo, de " + cfg.packMin + " a " + cfg.packMax + ", vinieron " + withPlain);
			helper.succeed();
		});
	}

	/** Four arriving from one side get slots all the way round, the first on its own side and one at the back. */
	@GameTest
	public void theRingIsFilledFromTheSideTheyCome(GameTestHelper helper) throws ReflectiveOperationException {
		java.lang.reflect.Method update = dev.forja.ai.Squad.class.getDeclaredMethod("update", ServerLevel.class, List.class, long.class);
		update.setAccessible(true);
		TwoPlayerGameTests.Listener player = new TwoPlayerGameTests.Listener(helper.getLevel());
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 1, 4)));
		player.setPos(pos.x, pos.y, pos.z);
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		List<Zombie> crowd = new ArrayList<>();
		for (BlockPos at : List.of(new BlockPos(4, 1, 4), new BlockPos(5, 1, 3), new BlockPos(5, 1, 5), new BlockPos(6, 1, 4))) {
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, at);
			zombie.setNoAi(true);
			zombie.setTarget(player);
			crowd.add(zombie);
		}
		helper.runAfterDelay(2, () -> {
			List<MobMind> minds = new ArrayList<>();
			crowd.forEach(z -> minds.add(MobAi.mind(z)));
			try {
				update.invoke(null, helper.getLevel(), minds, helper.getLevel().getGameTime());
			} catch (ReflectiveOperationException failure) {
				throw new IllegalStateException(failure);
			}
			double arrival = Math.atan2(crowd.getFirst().getZ() - player.getZ(), crowd.getFirst().getX() - player.getX());
			List<Double> angles = new ArrayList<>();
			double furthest = 0.0;
			for (MobMind mind : minds) {
				double off = Math.abs(Math.IEEEremainder(mind.ringAngle - arrival, Math.PI * 2.0));
				furthest = Math.max(furthest, off);
				angles.add(((mind.ringAngle % (Math.PI * 2.0)) + Math.PI * 2.0) % (Math.PI * 2.0));
			}
			angles.sort(Double::compare);
			double widestGap = angles.getFirst() + Math.PI * 2.0 - angles.getLast();
			for (int i = 1; i < angles.size(); i++) {
				widestGap = Math.max(widestGap, angles.get(i) - angles.get(i - 1));
			}
			double first = Math.abs(Math.IEEEremainder(minds.getFirst().ringAngle - arrival, Math.PI * 2.0));
			helper.assertTrue(first < 0.01, "el primero se queda en su lado, se va a " + Math.toDegrees(first) + "°");
			helper.assertTrue(furthest > Math.toRadians(170.0), "alguno da la vuelta hasta la espalda, el más lejano a " + Math.toDegrees(furthest) + "°");
			helper.assertTrue(widestGap < Math.toRadians(91.0), "los cuatro repartidos cada 90°, hueco mayor " + Math.toDegrees(widestGap) + "°");
			helper.succeed();
		});
	}

	/** "Los campeones deben de dar solo la legendaria": killed by a player, a champion leaves its legend and nothing else. */
	@GameTest
	public void aChampionGivesOnlyItsLegend(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		ServerLevel level = helper.getLevel();
		Zombie champion = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
		dev.forja.world.Elites.makeElite(champion, level.getRandom());
		net.minecraft.world.item.ItemStack legend = champion.getMainHandItem().copy();
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(level);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(2, 1, 4)));
		player.setPos(at.x, at.y, at.z);
		champion.setLastHurtByPlayer(player, 100);
		// Straight to the death: a champion's guard and the cap on a single blow would turn any one blow away.
		champion.setHealth(0.0F);
		champion.die(level.damageSources().playerAttack(player));
		helper.runAfterDelay(2, () -> {
			var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(champion.blockPosition()).inflate(4.0));
			helper.assertTrue(drops.size() == 1, "el campeón suelta una cosa, soltó " + drops.stream().map(d -> d.getItem().toString()).toList());
			helper.assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(drops.getFirst().getItem(), legend),
				"y es su leyenda: " + drops.getFirst().getItem() + " / " + legend);
			helper.succeed();
		});
	}

	/** In, strike, out: after a blow it hops back out of reach, not again for six seconds, and never into a wall. */
	@GameTest(maxTicks = 120)
	public void aMonsterHopsBackAfterItsBlow(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		// A wall right behind the second one.
		for (int z = 4; z < 8; z++) {
			helper.setBlock(new BlockPos(4, 1, z), net.minecraft.world.level.block.Blocks.STONE);
			helper.setBlock(new BlockPos(4, 2, z), net.minecraft.world.level.block.Blocks.STONE);
		}
		ServerLevel level = helper.getLevel();
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie free = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
		Zombie walled = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 5));
		// Where they were put, not where an idle stroll took them in the ten ticks before the blow: a stroll that ended
		// by the box's edge left no room to land, and no hop was planned (3 runs of 2000). A helmet against the sun too.
		for (Zombie zombie : List.of(free, walled)) {
			AlcanceGameTests.noStroll(zombie);
			zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
		}
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(level);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		CombatGameTests.TestPlayer other = new CombatGameTests.TestPlayer(level);
		Vec3 there = helper.absoluteVec(new Vec3(1.5, 1.0, 5.5));
		other.setPos(there.x, there.y, there.z);
		helper.runAfterDelay(10, () -> {
			double before = free.getX();
			dev.forja.ai.HopBack.afterHit(free, player);
			dev.forja.ai.HopBack.afterHit(walled, other);
			helper.assertTrue(!dev.forja.ai.HopBack.ready(free), "tras golpear, el salto está planeado y en recarga");
			helper.assertTrue(dev.forja.ai.HopBack.ready(walled), "con un muro detrás no salta");
			helper.runAfterDelay(dev.forja.ai.HopBack.TELL + 6, () -> {
				double moved = Math.hypot(free.getX() - player.getX(), free.getZ() - player.getZ());
				double was = before - player.getX();
				helper.assertTrue(moved > was + 0.8, "salta hacia atrás: de " + was + " a " + moved);
				helper.succeed();
			});
		});
	}

	/** Andy: a jump costs a little stamina (twice at a run), and a special move stamina it will not go without. */
	@GameTest
	public void jumpsAndSpecialsCostStamina(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(level);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		dev.forja.combat.Stamina.forget(player);
		CombatConfig cfg = CombatConfig.get();
		float full = dev.forja.combat.Stamina.value(player);
		dev.forja.combat.Stamina.onJump(player);
		float afterJump = dev.forja.combat.Stamina.value(player);
		player.setSprinting(true);
		dev.forja.combat.Stamina.onJump(player);
		float afterRun = dev.forja.combat.Stamina.value(player);
		player.setSprinting(false);
		helper.assertTrue(Math.abs(full - afterJump - cfg.jumpCost) < 0.01F && Math.abs(afterJump - afterRun - cfg.sprintJumpCost) < 0.01F,
			"saltar cuesta " + cfg.jumpCost + " y corriendo " + cfg.sprintJumpCost + ": " + full + " -> " + afterJump + " -> " + afterRun);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
			Assembler.create(ForgeType.ESPADON, Assembler.defaultMaterials(ForgeType.ESPADON), level.registryAccess()));
		player.setShiftKeyDown(true);
		dev.forja.combat.Stamina.trySpend(player, dev.forja.combat.Stamina.value(player) - (cfg.whirlStamina - 5.0F));
		var refused = dev.forja.forge.SpecialAttacks.tryUse(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, ForgeType.ESPADON);
		helper.assertTrue(refused == net.minecraft.world.InteractionResult.FAIL, "sin estamina el torbellino no sale: " + refused);
		dev.forja.combat.Stamina.restore(player, 1000.0F);
		var done = dev.forja.forge.SpecialAttacks.tryUse(level, player, net.minecraft.world.InteractionHand.MAIN_HAND, ForgeType.ESPADON);
		helper.assertTrue(done == net.minecraft.world.InteractionResult.CONSUME
			&& Math.abs(cfg.staminaMax - dev.forja.combat.Stamina.value(player) - cfg.whirlStamina) < 0.01F,
			"con estamina sale y cuesta " + cfg.whirlStamina + ": " + done + ", queda " + dev.forja.combat.Stamina.value(player));
		helper.succeed();
	}

	/** Running: 35 % faster, two a tick of its own breath, out of it until 25 is back, and never a boss. */
	@GameTest(maxTicks = 200)
	public void monstersRunOnTheirOwnBreath(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(level);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		helper.runAfterDelay(2, () -> {
			MobMind mind = MobAi.mind(zombie);
			double walk = zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			long now = level.getGameTime();
			mind.target = player;
			mind.wantsRun = true;
			dev.forja.ai.MobSprint.tick(mind, now);
			double run = zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			helper.assertTrue(mind.running && Math.abs(run / walk - 1.35) < 0.01, "corre un 35 % más rápido: " + walk + " -> " + run);
			helper.assertTrue(Math.abs(mind.stamina - (dev.forja.ai.MobSprint.MAX - dev.forja.ai.MobSprint.COST)) < 0.01F, "y gasta 2: " + mind.stamina);
			for (int t = 1; t < 60; t++) {
				dev.forja.ai.MobSprint.tick(mind, now + t);
			}
			helper.assertTrue(!mind.running && mind.winded && mind.stamina == 0.0F, "sin aliento deja de correr: " + mind.stamina);
			helper.assertTrue(Math.abs(zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - walk) < 1.0E-6,
				"y vuelve a su paso");
			// Rests 20 ticks, then 1 a tick: at 24 still winded, at 25 it may run again.
			long rest = now + 59 + dev.forja.ai.MobSprint.REST_TICKS;
			for (int t = 0; t < 24; t++) {
				dev.forja.ai.MobSprint.tick(mind, rest + t);
			}
			helper.assertTrue(!mind.running, "no corre antes de recuperar 25: " + mind.stamina);
			dev.forja.ai.MobSprint.tick(mind, rest + 24);
			dev.forja.ai.MobSprint.tick(mind, rest + 25);
			helper.assertTrue(mind.running, "con 25 vuelve a correr: " + mind.stamina);
			mind.wantsRun = false;
			dev.forja.ai.MobSprint.tick(mind, rest + 26);
			helper.succeed();
		});
	}

	/** An elite loaded back from disk gets its leap and second wind again, and a used second wind stays used. */
	@GameTest
	public void anEliteKeepsItsMovesAfterAReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(zombie != null, "no se pudo crear el zombi");
		// What a reload looks like from the mob's side: the champion's tag is there, its goals are not.
		zombie.addTag("forja_elite");
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
		zombie.setPos(at.x, at.y, at.z);
		level.addFreshEntity(zombie);
		var goals = ((dev.forja.mixin.MobGoalsAccess) zombie).forjaGoals().getAvailableGoals();
		helper.assertTrue(goals.stream().anyMatch(g -> g.getGoal() instanceof dev.forja.entity.ai.LeapStrikeGoal),
			"el élite recargado no recuperó el salto");
		long winds = goals.stream().filter(g -> g.getGoal() instanceof dev.forja.entity.ai.SecondWindGoal).count();
		helper.assertTrue(winds == 1, "segundos alientos tras recargar: " + winds);
		// Loading it again must not stack a second copy of each move.
		dev.forja.world.Elites.giveMoves(zombie);
		long again = ((dev.forja.mixin.MobGoalsAccess) zombie).forjaGoals().getAvailableGoals().stream()
			.filter(g -> g.getGoal() instanceof dev.forja.entity.ai.SecondWindGoal).count();
		helper.assertTrue(again == 1, "los movimientos se duplicaron: " + again);
		zombie.discard();
		helper.succeed();
	}

	/**
	 * With every companion mixed, a pack is not all one kind, holds at most one creeper, and its companions
	 * are all ordinary: no second veteran, elite or champion beside the leader (Andy, 2026-09-29).
	 */
	@GameTest
	public void packsMixKindsAndHaveOneLeader(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		double night = cfg.nightCompanionMax;
		double chance = cfg.packChance;
		int crowd = cfg.packCrowd;
		double mix = cfg.packMixChance;
		double spacing = cfg.packLeaderSpacing;
		cfg.nightCompanionMax = 0.0;
		cfg.packChance = 1.0;
		cfg.packCrowd = 1000;
		cfg.packMixChance = 1.0;
		cfg.packLeaderSpacing = 0.0;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		Zombie veteran = natural(helper, new BlockPos(4, 1, 4), Threat.VETERANO);
		cfg.nightCompanionMax = night;
		cfg.packChance = chance;
		cfg.packCrowd = crowd;
		cfg.packMixChance = mix;
		cfg.packLeaderSpacing = spacing;
		List<net.minecraft.world.entity.Mob> pack = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
			new AABB(veteran.blockPosition()).inflate(4.0, 2.0, 4.0),
			m -> m != veteran && m.entityTags().contains(Scaling.COMPANION));
		helper.assertTrue(pack.size() >= cfg.packVeteranMin - 1, "el veterano vino con " + pack.size() + " compañeros");
		helper.assertTrue(pack.stream().anyMatch(m -> !(m instanceof Zombie)), "un grupo mezclado salió todo de zombis");
		long creepers = pack.stream().filter(m -> m instanceof net.minecraft.world.entity.monster.Creeper).count();
		helper.assertTrue(creepers <= 1, "creepers en un grupo: " + creepers);
		for (net.minecraft.world.entity.Mob m : pack) {
			helper.assertTrue(Threat.of(m) == Threat.NORMAL && !dev.forja.world.Elites.isElite(m),
				"un compañero salió " + Threat.of(m));
			m.discard();
		}
		veteran.discard();
		helper.succeed();
	}
}
