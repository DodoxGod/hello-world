package dev.forja.world;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Where the great castle may stand. It is a single 250-block plan laid flat at the height of the one column under
 * its start piece, and it used to come down wherever the dice said: over the sea with its gate in the water,
 * half inside a mesa, across a valley (Andy, 2026-09-29). Before anything is assembled, the ground under the whole
 * plan is sampled and the site is turned down unless it is dry, fairly level, and the start column sits at the
 * ground's usual height.
 *
 * <p>It has to be cheap: {@code /locate} asks it of every candidate in a hundred rings of regions, on the server
 * thread, and the first version (81 columns of noise each, before even the biome was checked) froze the game
 * (Andy, 2026-09-29). So, in order, each step leaving at the first failure: the biome at the start column (one
 * sample, the same test vanilla makes after us, made first); the centre and the four corners; then a 5 x 5 grid.
 * A candidate is worked out once per chunk and remembered.
 */
public final class BastionGround {
	/** Half the width of the ground looked at, around the start piece's corner (the plan is about 250 across). */
	static final int REACH = 120;
	/** Samples along each side of the full grid. */
	static final int GRID = 5;
	/** The highest and the lowest sampled ground may differ by this much, and no more. */
	static final int MAX_RISE = 18;
	/** The start column (which sets the courtyard's height) may be this far from the ground's median. */
	static final int MAX_OFF_MEDIAN = 4;
	/** Samples with water over the ground allowed: a pond, not a lake. */
	static final int MAX_WET = 2;
	/** Candidates remembered (by seed and chunk) before the memory is emptied. */
	static final int CACHE = 8192;

	private static final Map<Long, Boolean> KNOWN = new ConcurrentHashMap<>();

	/** Where the height of a column comes from: the chunk generator in the game, anything in the tests. */
	@FunctionalInterface
	public interface Ground {
		/** The column's height: the ocean floor when {@code floor}, the surface (water included) otherwise. */
		int height(int x, int z, boolean floor);
	}

	/** How many columns the last {@link #suitable(Ground, int, int)} asked for (the tests watch the cost with it). */
	private static final ThreadLocal<int[]> ASKED = ThreadLocal.withInitial(() -> new int[1]);

	private BastionGround() {
	}

	/** Whether a jigsaw structure starting from this pool is the great castle (and not its clipped test twin). */
	public static boolean isBastion(Holder<StructureTemplatePool> startPool) {
		return startPool.unwrapKey()
			.map(key -> key.identifier().getNamespace().equals("forja") && key.identifier().getPath().startsWith("bastion/"))
			.orElse(false);
	}

	public static boolean suitable(Structure.GenerationContext context) {
		int x0 = context.chunkPos().getMinBlockX();
		int z0 = context.chunkPos().getMinBlockZ();
		long key = context.seed() * 31L + (((long) x0 << 32) ^ (z0 & 0xffffffffL));
		Boolean known = KNOWN.get(key);
		if (known != null) {
			return known;
		}
		Ground ground = (x, z, floor) -> context.chunkGenerator().getBaseHeight(x, z,
			floor ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		// the biome first: most candidates fail here, for the price of one column and one biome sample
		int y = ground.height(x0, z0, false);
		boolean result = context.validBiome().test(context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x0), QuartPos.fromBlock(y),
			QuartPos.fromBlock(z0), context.randomState().sampler())) && suitable(ground, x0, z0);
		if (KNOWN.size() > CACHE) {
			KNOWN.clear();
		}
		KNOWN.put(key, result);
		return result;
	}

	/** The ground test itself, on any source of heights; leaves at the first sample that rules the site out. */
	public static boolean suitable(Ground ground, int x0, int z0) {
		int[] asked = ASKED.get();
		asked[0] = 0;
		Ground counted = (x, z, floor) -> {
			asked[0]++;
			return ground.height(x, z, floor);
		};
		int[] heights = new int[GRID * GRID];
		int[] wet = {0};
		int[] range = {Integer.MAX_VALUE, Integer.MIN_VALUE};
		// the centre and the four corners, then the rest of the grid
		int mid = GRID / 2;
		int[][] order = new int[GRID * GRID][];
		int n = 0;
		for (int[] first : new int[][] {{mid, mid}, {0, 0}, {GRID - 1, 0}, {0, GRID - 1}, {GRID - 1, GRID - 1}}) {
			order[n++] = first;
		}
		for (int i = 0; i < GRID; i++) {
			for (int k = 0; k < GRID; k++) {
				boolean corner = (i == 0 || i == GRID - 1) && (k == 0 || k == GRID - 1);
				if (!corner && !(i == mid && k == mid)) {
					order[n++] = new int[] {i, k};
				}
			}
		}
		for (int[] at : order) {
			int x = x0 - REACH + at[0] * 2 * REACH / (GRID - 1);
			int z = z0 - REACH + at[1] * 2 * REACH / (GRID - 1);
			int floor = counted.height(x, z, true);
			int surface = counted.height(x, z, false);
			heights[at[0] * GRID + at[1]] = floor;
			if (surface > floor && ++wet[0] > MAX_WET) {
				return false;
			}
			range[0] = Math.min(range[0], floor);
			range[1] = Math.max(range[1], floor);
			if (range[1] - range[0] > MAX_RISE) {
				return false;
			}
		}
		int[] sorted = heights.clone();
		Arrays.sort(sorted);
		int median = sorted[sorted.length / 2];
		int start = counted.height(x0, z0, false);
		return Math.abs(start - median) <= MAX_OFF_MEDIAN;
	}

	/** Columns the last ground test on this thread asked for. */
	public static int lastCost() {
		return ASKED.get()[0];
	}
}
