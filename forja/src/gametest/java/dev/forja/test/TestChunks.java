package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Chunks forced for a test that builds past its 8×8 box: entities only tick in chunks that are loaded for it, and a
 * test's own reach little past its box. Only the chunks this test forced are let go afterwards: the framework forces the
 * chunks of every test running, and letting one of those go stopped that test for good.
 */
final class TestChunks {
	private static final Map<GameTestHelper, List<ChunkPos>> FORCED = new WeakHashMap<>();

	private TestChunks() {
	}

	/** Forces the chunks under (-1, -1) to (size, size) that were not already forced. */
	static void force(GameTestHelper helper, int size) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(new BlockPos(-1, 0, -1));
		BlockPos c = helper.absolutePos(new BlockPos(size, 0, size));
		List<ChunkPos> mine = FORCED.computeIfAbsent(helper, h -> new ArrayList<>());
		for (int cx = Math.min(a.getX(), c.getX()) >> 4; cx <= Math.max(a.getX(), c.getX()) >> 4; cx++) {
			for (int cz = Math.min(a.getZ(), c.getZ()) >> 4; cz <= Math.max(a.getZ(), c.getZ()) >> 4; cz++) {
				ChunkPos pos = new ChunkPos(cx, cz);
				if (!level.getForceLoadedChunks().contains(pos.pack()) && level.setChunkForced(cx, cz, true)) {
					mine.add(pos);
				}
			}
		}
	}

	/** Lets go of the chunks this test forced, and only those. */
	static void release(GameTestHelper helper) {
		List<ChunkPos> mine = FORCED.remove(helper);
		if (mine != null) {
			for (ChunkPos pos : mine) {
				helper.getLevel().setChunkForced(pos.x(), pos.z(), false);
			}
		}
	}
}
