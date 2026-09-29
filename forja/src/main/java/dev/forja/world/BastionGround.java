package dev.forja.world;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
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
 *
 * <p>The second version still took 20 s to find a castle 45,000 blocks away: one candidate in two thousand passed,
 * and every one cost two columns of noise per sample. Now the biome is sampled (cheap: a few noises at one point, no
 * column) at the start and the four corners first, a column asks for the water under it only when its surface is
 * at sea level. Andy (2026-09-29) wants the site flatter even if there are fewer: at most 14 blocks between the
 * highest and lowest ground sampled, and the start within 4 of the median. {@link #report()} says where the candidates went.
 */
public final class BastionGround {
	/** Half the width of the ground looked at, around the start piece's corner (the plan is about 250 across). */
	static final int REACH = 120;
	/** Samples along each side of the full grid. */
	static final int GRID = 5;
	/** The highest and the lowest sampled ground may differ by this much, and no more. */
	static final int MAX_RISE = 14;
	/** The start column (which sets the courtyard's height) may be this far from the ground's median. */
	static final int MAX_OFF_MEDIAN = 4;
	/** Samples with water over the ground allowed: a pond, not a lake. */
	static final int MAX_WET = 3;
	/** Candidates remembered (by seed and chunk) before the memory is emptied. */
	static final int CACHE = 8192;

	private static final Map<Long, Boolean> KNOWN = new ConcurrentHashMap<>();

	/** Candidates asked about, and where they were turned down, since the game started: for the log and the tests. */
	private static final AtomicLong ASKED_SITES = new AtomicLong();
	private static final AtomicLong BY_BIOME = new AtomicLong();
	private static final AtomicLong BY_GROUND = new AtomicLong();
	private static final AtomicLong PASSED = new AtomicLong();
	private static final AtomicLong NANOS = new AtomicLong();

	/** Where the height of a column comes from: the chunk generator in the game, anything in the tests. */
	@FunctionalInterface
	public interface Ground {
		/** The column's height: the ocean floor when {@code floor}, the surface (water included) otherwise. */
		int height(int x, int z, boolean floor);

		/** Whether water could stand over a column with this surface; when not, its floor is not asked for. */
		default boolean mayBeWet(int surface) {
			return true;
		}
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
		long started = System.nanoTime();
		ASKED_SITES.incrementAndGet();
		int sea = context.chunkGenerator().getSeaLevel();
		boolean result;
		if (!biomes(context, x0, z0, sea)) {
			BY_BIOME.incrementAndGet();
			result = false;
		} else {
			Ground ground = new Ground() {
				@Override
				public int height(int x, int z, boolean floor) {
					return context.chunkGenerator().getBaseHeight(x, z, floor ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG,
						context.heightAccessor(), context.randomState());
				}

				@Override
				public boolean mayBeWet(int surface) {
					return surface <= sea + 1;
				}
			};
			result = suitable(ground, x0, z0);
			(result ? PASSED : BY_GROUND).incrementAndGet();
		}
		NANOS.addAndGet(System.nanoTime() - started);
		if (KNOWN.size() > CACHE) {
			KNOWN.clear();
		}
		KNOWN.put(key, result);
		return result;
	}

	/**
	 * The biomes, before any column: the start's must be one the castle may stand in (the test vanilla makes after
	 * us, at sea level rather than at the surface), and no corner may be sea, river or mountain.
	 */
	private static boolean biomes(Structure.GenerationContext context, int x0, int z0, int sea) {
		if (!context.validBiome().test(biome(context, x0, z0, sea))) {
			return false;
		}
		for (int[] corner : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			Holder<Biome> at = biome(context, x0 + corner[0] * REACH, z0 + corner[1] * REACH, sea);
			if (at.is(BiomeTags.IS_OCEAN) || at.is(BiomeTags.IS_RIVER) || at.is(BiomeTags.IS_MOUNTAIN)) {
				return false;
			}
		}
		return true;
	}

	private static Holder<Biome> biome(Structure.GenerationContext context, int x, int z, int y) {
		return context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
			context.randomState().sampler());
	}

	/** Where the candidates went since the game started: asked, turned down by biome and by ground, passed, and the time. */
	public static String report() {
		return ASKED_SITES.get() + " candidatos: " + BY_BIOME.get() + " por bioma, " + BY_GROUND.get() + " por el terreno, " + PASSED.get()
			+ " valen; " + NANOS.get() / 1_000_000L + " ms";
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
			int surface = counted.height(x, z, false);
			int floor = ground.mayBeWet(surface) ? counted.height(x, z, true) : surface;
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
		// the start column is the centre sample, the first one asked
		int start = heights[mid * GRID + mid];
		return Math.abs(start - median) <= MAX_OFF_MEDIAN;
	}

	/** Columns the last ground test on this thread asked for. */
	public static int lastCost() {
		return ASKED.get()[0];
	}
}
