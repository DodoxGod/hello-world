package dev.forja.test;

import dev.forja.world.BastionGround;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where the great castle may stand (world/BastionGround), and what it costs to ask: /locate asks it of thousands of
 * candidates on the server thread, and the first version froze the game (Andy, 2026-09-29).
 */
public class SitioGameTests {
	/** Over the sea every sample is wet: turned down after three columns' worth, not the whole grid. */
	@GameTest
	public void aSeaIsTurnedDownAtOnce(GameTestHelper helper) {
		boolean ok = BastionGround.suitable((x, z, floor) -> floor ? 40 : 63, 0, 0);
		helper.assertFalse(ok, "sobre el mar no");
		helper.assertTrue(BastionGround.lastCost() <= 6, "lo descarta en seguida, preguntó " + BastionGround.lastCost() + " columnas");
		helper.succeed();
	}

	/** A mountainside: the centre and one corner already rise too much. */
	@GameTest
	public void aSlopeIsTurnedDownAtOnce(GameTestHelper helper) {
		boolean ok = BastionGround.suitable((x, z, floor) -> 70 + x / 3, 0, 0);
		helper.assertFalse(ok, "en una ladera no");
		helper.assertTrue(BastionGround.lastCost() <= 4, "lo descarta en seguida, preguntó " + BastionGround.lastCost() + " columnas");
		helper.succeed();
	}

	/** Dry, level ground passes, and even a site that passes asks for a few dozen columns, not hundreds. */
	@GameTest
	public void levelDryGroundPasses(GameTestHelper helper) {
		boolean ok = BastionGround.suitable((x, z, floor) -> 70 + ((x * 7 + z * 13) & 3), 0, 0);
		helper.assertTrue(ok, "en llano y seco sí");
		helper.assertTrue(BastionGround.lastCost() <= 60, "preguntó " + BastionGround.lastCost() + " columnas");
		helper.succeed();
	}

	/** A pond is allowed; a lake is not. */
	@GameTest
	public void aPondIsFineALakeIsNot(GameTestHelper helper) {
		helper.assertTrue(BastionGround.suitable((x, z, floor) -> floor ? 70 : (x == 0 && z == 0 ? 72 : 70), 0, 0), "un charco en el centro vale");
		helper.assertFalse(BastionGround.suitable((x, z, floor) -> floor ? 70 : (x < 0 ? 72 : 70), 0, 0), "media llanura bajo el agua no");
		helper.succeed();
	}

	/** Five hundred candidates on the real generator of the test world, on this thread, in well under a few seconds. */
	@GameTest(maxTicks = 20)
	public void fiveHundredCandidatesAreCheap(GameTestHelper helper) {
		var level = helper.getLevel();
		var generator = level.getChunkSource().getGenerator();
		var random = level.getChunkSource().randomState();
		long started = System.nanoTime();
		for (int i = 0; i < 500; i++) {
			BastionGround.suitable((x, z, floor) -> generator.getBaseHeight(x, z,
				floor ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG, level, random), i * 1712, i * 928);
		}
		long ms = (System.nanoTime() - started) / 1_000_000L;
		helper.assertTrue(ms < 5000, "500 candidatos costaron " + ms + " ms");
		helper.succeed();
	}
}
