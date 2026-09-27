package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.Scaling;
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
		// Other tests run beside this one; their mobs are not this spawn's crowd.
		cfg.nightCompanionMax = 0.0;
		cfg.packChance = 1.0;
		cfg.packCrowd = 1000;
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
}
