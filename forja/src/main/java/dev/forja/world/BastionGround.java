package dev.forja.world;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import dev.forja.mixin.NoiseColumnAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
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
 *
 * <p>So strict a test leaves most worlds without a castle anywhere near spawn (Andy found his nearest one 26,000
 * blocks out): every world also gets one "home" castle, chosen by {@link BastionHome} with the same test and slightly
 * wider {@link Limits}. The home chunk has already been surveyed, so {@link #suitable(Structure.GenerationContext)}
 * lets it through without asking again.
 */
public final class BastionGround {
	/** Half the width of the ground looked at, around the start piece's corner (the plan is about 250 across). */
	static final int REACH = 120;
	/** Samples along each side of the full grid. */
	static final int GRID = 5;
	/** Candidates remembered (by seed and chunk) before the memory is emptied. */
	static final int CACHE = 8192;

	/** How much a site may fall short of flat and dry. */
	public record Limits(int maxRise, int maxOffMedian, int maxWet) {
	}

	/**
	 * Every castle left to the dice: at most 14 blocks between the highest and lowest ground sampled, the start column
	 * (which sets the courtyard's height) within 4 of the ground's median, and at most 3 samples of 25 with water over
	 * the ground (a pond, not a lake).
	 */
	public static final Limits STRICT = new Limits(14, 4, 3);

	/** Why a site was turned down, or that it was not. */
	public enum Verdict {
		OK, BIOME, EDGE, WET, RISE, OFF_MEDIAN
	}

	/** What the survey of a site found: the verdict and, when it got that far, how far from flat it is. */
	public record Survey(Verdict verdict, int rise, int offMedian, int wet) {
		public boolean ok() {
			return this.verdict == Verdict.OK;
		}

		/** Lower is better: every block of rise, two per block the courtyard sits off the ground, three per wet sample. */
		public int score() {
			return this.rise + 2 * this.offMedian + 3 * this.wet;
		}
	}

	private static final Map<Long, Verdict> KNOWN = new ConcurrentHashMap<>();

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

	/** How many columns the last survey asked for (the tests watch the cost with it). */
	private static final ThreadLocal<int[]> ASKED = ThreadLocal.withInitial(() -> new int[1]);
	/** The verdict of the last check on this thread, for the measurements. */
	private static final ThreadLocal<Verdict[]> LAST = ThreadLocal.withInitial(() -> new Verdict[] {Verdict.OK});

	private BastionGround() {
	}

	/** Whether a jigsaw structure starting from this pool is the great castle (and not its clipped test twin). */
	public static boolean isBastion(Holder<StructureTemplatePool> startPool) {
		return startPool.unwrapKey()
			.map(key -> key.identifier().getNamespace().equals("forja") && key.identifier().getPath().startsWith("bastion/"))
			.orElse(false);
	}

	public static boolean suitable(Structure.GenerationContext context) {
		if (BastionHome.isHome(context.seed(), context.chunkPos())) {
			// surveyed already, with the home limits, when the home was chosen
			LAST.get()[0] = Verdict.OK;
			return true;
		}
		int x0 = context.chunkPos().getMinBlockX();
		int z0 = context.chunkPos().getMinBlockZ();
		long key = context.seed() * 31L + (((long) x0 << 32) ^ (z0 & 0xffffffffL));
		Verdict known = KNOWN.get(key);
		if (known != null) {
			LAST.get()[0] = known;
			return known == Verdict.OK;
		}
		long started = System.nanoTime();
		ASKED_SITES.incrementAndGet();
		int sea = context.chunkGenerator().getSeaLevel();
		Verdict result;
		Verdict biomes = biomes(context.biomeSource(), context.randomState().sampler(), context.validBiome(), x0, z0, sea);
		if (biomes != Verdict.OK) {
			BY_BIOME.incrementAndGet();
			LAST.get()[0] = biomes;
			result = biomes;
		} else {
			result = survey(ground(context.chunkGenerator(), context.heightAccessor(), context.randomState()), x0, z0, STRICT).verdict();
			(result == Verdict.OK ? PASSED : BY_GROUND).incrementAndGet();
		}
		NANOS.addAndGet(System.nanoTime() - started);
		if (KNOWN.size() > CACHE) {
			KNOWN.clear();
		}
		KNOWN.put(key, result);
		return result == Verdict.OK;
	}

	/**
	 * The heights of a world. On the noise generator every world uses, one walk down the column gives both the ground
	 * and whether anything (water, lava) stands over it; elsewhere the floor is asked for only where water could be.
	 */
	public static Ground ground(ChunkGenerator generator, LevelHeightAccessor heights, RandomState random) {
		int sea = generator.getSeaLevel();
		if (generator instanceof NoiseColumnAccess column) {
			Predicate<BlockState> solid = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
			return new Ground() {
				private int lastX = Integer.MIN_VALUE;
				private int lastZ;
				private int lastFloor;

				@Override
				public int height(int x, int z, boolean floor) {
					if (floor && x == this.lastX && z == this.lastZ) {
						return this.lastFloor;
					}
					boolean[] over = {false};
					int ground = column.forja$iterateNoiseColumn(heights, random, x, z, null, state -> {
						if (solid.test(state)) {
							return true;
						}
						over[0] |= !state.isAir();
						return false;
					}).orElse(heights.getMinY());
					this.lastX = x;
					this.lastZ = z;
					this.lastFloor = ground;
					// the survey only asks whether something stands over the ground, not how deep it is
					return over[0] ? ground + 1 : ground;
				}

				@Override
				public boolean mayBeWet(int surface) {
					// the floor is known already, asking for it again is free
					return true;
				}
			};
		}
		return new Ground() {
			@Override
			public int height(int x, int z, boolean floor) {
				return generator.getBaseHeight(x, z, floor ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG, heights, random);
			}

			@Override
			public boolean mayBeWet(int surface) {
				return surface <= sea + 1;
			}
		};
	}

	/**
	 * The biomes, before any column: the start's must be one the castle may stand in (the test vanilla makes after
	 * us, at sea level rather than at the surface), and no corner may be sea, river or mountain.
	 */
	static Verdict biomes(BiomeSource source, Climate.Sampler sampler, Predicate<Holder<Biome>> valid, int x0, int z0, int sea) {
		if (!valid.test(biome(source, sampler, x0, z0, sea))) {
			return Verdict.BIOME;
		}
		for (int[] corner : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
			Holder<Biome> at = biome(source, sampler, x0 + corner[0] * REACH, z0 + corner[1] * REACH, sea);
			if (at.is(BiomeTags.IS_OCEAN) || at.is(BiomeTags.IS_RIVER) || at.is(BiomeTags.IS_MOUNTAIN)) {
				return Verdict.EDGE;
			}
		}
		return Verdict.OK;
	}

	private static Holder<Biome> biome(BiomeSource source, Climate.Sampler sampler, int x, int z, int y) {
		return source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
	}

	/** Where the candidates went since the game started: asked, turned down by biome and by ground, passed, and the time. */
	public static String report() {
		return ASKED_SITES.get() + " candidatos: " + BY_BIOME.get() + " por bioma, " + BY_GROUND.get() + " por el terreno, " + PASSED.get()
			+ " valen; " + NANOS.get() / 1_000_000L + " ms";
	}

	/** The ground test itself with the strict limits, on any source of heights. */
	public static boolean suitable(Ground ground, int x0, int z0) {
		return survey(ground, x0, z0, STRICT).ok();
	}

	/** The ground test on any source of heights; leaves at the first sample that rules the site out. */
	public static Survey survey(Ground ground, int x0, int z0, Limits limits) {
		int[] asked = ASKED.get();
		asked[0] = 0;
		Ground counted = (x, z, floor) -> {
			asked[0]++;
			return ground.height(x, z, floor);
		};
		int[] heights = new int[GRID * GRID];
		int wet = 0;
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
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
			if (surface > floor && ++wet > limits.maxWet()) {
				return verdict(new Survey(Verdict.WET, 0, 0, wet));
			}
			low = Math.min(low, floor);
			high = Math.max(high, floor);
			if (high - low > limits.maxRise()) {
				return verdict(new Survey(Verdict.RISE, high - low, 0, wet));
			}
		}
		int[] sorted = heights.clone();
		Arrays.sort(sorted);
		int median = sorted[sorted.length / 2];
		// the start column is the centre sample, the first one asked
		int off = Math.abs(heights[mid * GRID + mid] - median);
		return verdict(new Survey(off <= limits.maxOffMedian() ? Verdict.OK : Verdict.OFF_MEDIAN, high - low, off, wet));
	}

	private static Survey verdict(Survey survey) {
		LAST.get()[0] = survey.verdict();
		return survey;
	}

	/** Why the last check on this thread turned its site down ({@link Verdict#OK} if it did not). */
	public static Verdict lastVerdict() {
		return LAST.get()[0];
	}

	/** Columns the last ground test on this thread asked for. */
	public static int lastCost() {
		return ASKED.get()[0];
	}
}
