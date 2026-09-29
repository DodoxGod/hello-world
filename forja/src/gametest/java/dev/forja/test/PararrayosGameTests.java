package dev.forja.test;

import dev.forja.block.StarRodBlock;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModItems;
import dev.forja.world.WorldEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Pararrayos de estrellas (block/StarRodBlock): a meteorite of the shower that would have landed near one
 * lands on it instead, wears it and leaves its star iron at its foot; the last one breaks it; and one out of
 * reach is left alone. Padded: a meteorite that misses digs a crater three blocks round.
 */
public class PararrayosGameTests {
	/** A meteorite aimed seven blocks off comes down on the rod: no crater where it was aimed, one wear, the iron at the rod. */
	@GameTest(padding = 16, maxTicks = 120)
	public void aRodDrawsTheMeteoriteOntoItself(GameTestHelper helper) {
		var level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		BlockPos rod = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos aim = helper.absolutePos(new BlockPos(6, 1, 6));
		BlockPos floorUnderAim = helper.absolutePos(new BlockPos(6, 0, 6));
		level.setBlockAndUpdate(rod, ModBlocks.PARARRAYOS.defaultBlockState());
		helper.assertTrue(rod.equals(StarRodBlock.attract(level, aim)), "el pararrayos está a menos de " + StarRodBlock.RADIUS + " bloques");
		helper.assertTrue(rod.equals(WorldEvents.drawnTo(level, aim)), "el meteorito debería ir al pararrayos");
		WorldEvents.meteorForTest(level, aim);
		helper.succeedWhen(() -> {
			BlockState state = level.getBlockState(rod);
			helper.assertTrue(state.is(ModBlocks.PARARRAYOS), "el pararrayos debería seguir en pie: " + state);
			helper.assertTrue(state.getValue(StarRodBlock.DESGASTE) == 1, "y con un meteorito encima: " + state);
			helper.assertTrue(level.getBlockState(floorUnderAim).is(Blocks.STONE), "donde apuntaba no hay cráter");
			var iron = level.getEntitiesOfClass(ItemEntity.class, new AABB(rod).inflate(2.0),
				item -> item.getItem().is(ModItems.HIERRO_ESTELAR));
			helper.assertFalse(iron.isEmpty(), "el hierro estelar queda al pie del pararrayos");
			iron.forEach(Entity::discard);
		});
	}

	/** Each meteorite wears it by one; the STRIKES-th breaks it, and a broken rod takes nothing. */
	@GameTest(maxTicks = 20)
	public void aRodWearsAndBreaksOnItsLast(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos rod = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlockAndUpdate(rod, ModBlocks.PARARRAYOS.defaultBlockState());
		for (int strike = 1; strike < StarRodBlock.STRIKES; strike++) {
			helper.assertTrue(StarRodBlock.takeStrike(level, rod), "el pararrayos debería aguantar el golpe " + strike);
			helper.assertTrue(level.getBlockState(rod).getValue(StarRodBlock.DESGASTE) == strike,
				"desgaste tras " + strike + ": " + level.getBlockState(rod));
		}
		helper.assertTrue(StarRodBlock.takeStrike(level, rod), "el último también lo aguanta");
		helper.assertTrue(level.getBlockState(rod).isAir(), "y se rompe: " + level.getBlockState(rod));
		helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(rod).inflate(1.5)).isEmpty(), "gastado no suelta nada");
		helper.assertFalse(StarRodBlock.takeStrike(level, rod), "sin pararrayos, nada que lo tome");
		helper.succeed();
	}

	/** Out of reach across the ground, the meteorite goes where it was going and the rod is not touched. */
	@GameTest(padding = 16, maxTicks = 20)
	public void aRodOutOfReachDoesNothing(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos rod = helper.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(rod, ModBlocks.PARARRAYOS.defaultBlockState());
		BlockPos far = rod.offset(StarRodBlock.RADIUS + 1, 0, 0);
		helper.assertTrue(StarRodBlock.attract(level, far) == null, "a " + (StarRodBlock.RADIUS + 1) + " bloques no llega");
		helper.assertTrue(WorldEvents.drawnTo(level, far).equals(far), "el meteorito cae donde iba");
		BlockPos near = rod.offset(StarRodBlock.RADIUS, 0, 0);
		helper.assertTrue(rod.equals(StarRodBlock.attract(level, near)), "a " + StarRodBlock.RADIUS + " bloques sí llega");
		helper.assertTrue(level.getBlockState(rod).getValue(StarRodBlock.DESGASTE) == 0, "y nada lo ha gastado");
		helper.succeed();
	}
}
