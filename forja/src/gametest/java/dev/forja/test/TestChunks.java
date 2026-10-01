package dev.forja.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Chunks forced for a test that builds past its 8×8 box: entities only tick in chunks that are loaded for it, and a
 * test's own reach little past its box. The framework forces the chunks of every test's box and lets them all go when
 * the batch ends; letting one of those go sooner stopped that test for good, so only the chunks forced here are let go,
 * and each only when no test that asked for it is still running: tests side by side share chunks, and the first to end
 * let go of the ones its neighbour still stood on (that neighbour had found them forced and asked nothing).
 */
final class TestChunks {
	private static final Map<GameTestHelper, List<ChunkPos>> FORCED = new WeakHashMap<>();
	/** The chunks forced here, and the tests running that asked for each. */
	private static final Map<ChunkPos, Set<GameTestHelper>> HOLDERS = new HashMap<>();

	private TestChunks() {
	}

	/** Forces the chunks under (-1, -1) to (size, size) that were not already forced. */
	static void force(GameTestHelper helper, int size) {
		force(helper, -1, size);
	}

	/** Forces the chunks under (from, from) to (to, to) that were not already forced, or holds those forced here. */
	static void force(GameTestHelper helper, int from, int to) {
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(new BlockPos(from, 0, from));
		BlockPos c = helper.absolutePos(new BlockPos(to, 0, to));
		List<ChunkPos> mine = FORCED.computeIfAbsent(helper, h -> new ArrayList<>());
		for (int cx = Math.min(a.getX(), c.getX()) >> 4; cx <= Math.max(a.getX(), c.getX()) >> 4; cx++) {
			for (int cz = Math.min(a.getZ(), c.getZ()) >> 4; cz <= Math.max(a.getZ(), c.getZ()) >> 4; cz++) {
				ChunkPos pos = new ChunkPos(cx, cz);
				boolean forced = level.getForceLoadedChunks().contains(pos.pack());
				Set<GameTestHelper> holders = HOLDERS.get(pos);
				if (holders != null && !forced) {
					// let go by the framework at the end of a batch: forced here again from nothing
					HOLDERS.remove(pos);
					holders = null;
				}
				if (holders == null && !forced && level.setChunkForced(cx, cz, true)) {
					holders = new HashSet<>();
					HOLDERS.put(pos, holders);
				}
				if (holders != null && holders.add(helper)) {
					mine.add(pos);
				}
			}
		}
	}

	/**
	 * Whether every chunk under (from, from) to (to, to) ticks its entities by now: a chunk just forced takes some ticks
	 * to come up, more under load.
	 */
	static boolean ticking(GameTestHelper helper, int from, int to) {
		BlockPos a = helper.absolutePos(new BlockPos(from, 0, from));
		BlockPos c = helper.absolutePos(new BlockPos(to, 0, to));
		for (int cx = Math.min(a.getX(), c.getX()) >> 4; cx <= Math.max(a.getX(), c.getX()) >> 4; cx++) {
			for (int cz = Math.min(a.getZ(), c.getZ()) >> 4; cz <= Math.max(a.getZ(), c.getZ()) >> 4; cz++) {
				if (!helper.getLevel().areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Lets go of the chunks forced here that this test held and no other running test still holds. */
	static void release(GameTestHelper helper) {
		List<ChunkPos> mine = FORCED.remove(helper);
		if (mine != null) {
			for (ChunkPos pos : mine) {
				Set<GameTestHelper> holders = HOLDERS.get(pos);
				if (holders != null && holders.remove(helper) && holders.isEmpty()) {
					HOLDERS.remove(pos);
					helper.getLevel().setChunkForced(pos.x(), pos.z(), false);
				}
			}
		}
	}
}
