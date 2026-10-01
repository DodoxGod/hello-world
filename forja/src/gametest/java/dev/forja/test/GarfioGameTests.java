package dev.forja.test;

import dev.forja.combat.Stamina;
import dev.forja.entity.FallenSmith;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * The Garfio can be answered: the claw only lands if the line to the target is clear when it is thrown
 * and the target is not rolling through it (docs/HERRERO_DIMENSION.md, 3.1).
 */
public class GarfioGameTests {
	private static FallenSmith smith(GameTestHelper helper) {
		FallenSmith smith = helper.spawn(ModEntities.HERRERO_CAIDO, new BlockPos(1, 1, 3));
		smith.setNoAi(true);
		smith.setYRot(-90.0F);
		smith.setYHeadRot(-90.0F);
		return smith;
	}

	/** A clear line: it hurts and drags the target toward him. */
	@GameTest(maxTicks = 80)
	public void garfioHitsAClearLine(GameTestHelper helper) {
		FallenSmith smith = smith(helper);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(7, 1, 3));
		float health = player.getHealth();
		smith.hookIn(helper.getLevel(), player);
		helper.runAfterDelay(FallenSmith.HOOK_WINDUP + 4, () -> {
			helper.assertTrue(player.getHealth() < health, "una línea limpia hiere: vida " + player.getHealth() + " de " + health);
			net.minecraft.world.phys.Vec3 toward = smith.position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize();
			helper.assertTrue(player.getDeltaMovement().dot(toward) > 0.3, "y lo arrastra hacia él: " + player.getDeltaMovement());
			smith.discard();
			helper.succeed();
		});
	}

	/** A wall put up during the windup: the claw stops on it, with no damage and no pull. */
	@GameTest(maxTicks = 80)
	public void garfioMissesBehindAWall(GameTestHelper helper) {
		FallenSmith smith = smith(helper);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(7, 1, 3));
		float health = player.getHealth();
		smith.hookIn(helper.getLevel(), player);
		helper.runAfterDelay(3, () -> {
			for (int y = 1; y <= 4; y++) {
				for (int z = 1; z <= 5; z++) {
					helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
				}
			}
		});
		helper.runAfterDelay(FallenSmith.HOOK_WINDUP + 4, () -> {
			helper.assertTrue(player.getHealth() == health, "tras un muro no hiere: vida " + player.getHealth() + " de " + health);
			helper.assertTrue(player.getDeltaMovement().horizontalDistance() < 0.01, "ni arrastra: " + player.getDeltaMovement());
			smith.discard();
			helper.succeed();
		});
	}

	/** A target in the i-frames of a roll when the claw leaves is missed the same way. */
	@GameTest(maxTicks = 80)
	public void garfioMissesARollingTarget(GameTestHelper helper) {
		FallenSmith smith = smith(helper);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(7, 1, 3));
		float health = player.getHealth();
		smith.hookIn(helper.getLevel(), player);
		helper.runAfterDelay(FallenSmith.HOOK_WINDUP - 3, () -> {
			Stamina.onDodge(player, 0.0F, 1.0F);
			helper.assertTrue(Stamina.isDodging(player, helper.getLevel().getGameTime() + 3), "debería estar esquivando");
		});
		helper.runAfterDelay(FallenSmith.HOOK_WINDUP + 4, () -> {
			helper.assertTrue(player.getHealth() == health, "rodando no hiere: vida " + player.getHealth() + " de " + health);
			helper.assertTrue(player.getDeltaMovement().horizontalDistance() < 0.01, "ni arrastra: " + player.getDeltaMovement());
			smith.discard();
			helper.succeed();
		});
	}
}
