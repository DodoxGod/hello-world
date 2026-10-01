package dev.forja.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import dev.forja.Forja;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.jspecify.annotations.Nullable;

/**
 * The great castle every world gets near spawn. Left to its grid (one candidate every 110 chunks) and to the strict
 * site test of {@link BastionGround}, almost no candidate stands: over sixty seeds, of the 1,943 candidates within
 * 5,000 blocks of the origin half were the wrong biome, a third were inside a village's exclusion zone, and one
 * passed. That left Andy's world (2026-09-30) with its nearest castle 26,000 blocks away, and he wants one within
 * 5,000 of the origin in every world.
 *
 * <p>So once per world the ground around spawn is searched for the best site: every fifth chunk between 1,500 and
 * 4,500 blocks from the origin. The cheap tests go first: the biomes (those of {@link BastionGround}), then the
 * villages. What is left is ranked by the noise's rough surface level (a cheap estimate, no column walked), flattest
 * first, and only then is the ground itself surveyed, with the slightly wider {@link #LIMITS}. The search stops at a
 * very good site, or once it has three to choose from and the best is as level as the strict test wants, or after
 * sixty surveys; the flattest, driest site it saw is kept. It runs in the background from the moment the world's
 * structure state is made (0.3 to 1.9 s over sixty seeds), and anything that needs it first waits.
 *
 * <p>The castle's own grid cell hands that chunk out instead of its random one
 * ({@code RandomSpreadStructurePlacementMixin}), so {@code /locate}, the Forjador's map, and the other sets' exclusion
 * zones all see it as an ordinary castle of the set. Every other castle stays where the grid and the strict test put
 * it: far apart and rare.
 *
 * <p>Water and mountains still rule a site out (the biome test turns down any sea, river or mountain under a corner,
 * and at most three samples of twenty-five may be wet); a village's candidate may not be within 12 chunks; and a
 * castle of a neighbouring cell that really stands may not be within {@link #NEIGHBOUR} chunks.
 */
public final class BastionHome {
	/** The castle's structure set. */
	public static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, Forja.id("bastion_del_gremio"));
	private static final ResourceKey<StructureSet> VILLAGES = ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("villages"));

	/** The nearest and the farthest a home castle may be from the origin, in blocks. */
	public static final int MIN_BLOCKS = 1500;
	public static final int MAX_BLOCKS = 4500;
	/** Chunks between the candidates looked at. */
	static final int STEP = 5;
	/** A village's candidate may not be this close, in chunks (the grid's own zone is 10). */
	static final int VILLAGE_CLEARANCE = 12;
	/** Nor may a standing castle of a neighbouring cell (the plan is about 16 chunks across). */
	static final int NEIGHBOUR = 24;
	/** Sites that pass before the best of them is kept. */
	static final int WANT = 3;
	/** A site this good (a score of {@link BastionGround.Survey#score()}) is kept at once. */
	static final int GOOD_ENOUGH = 8;
	/** Once a site has passed, the ground is surveyed at most this many times; then the best so far is kept. */
	static final int MAX_SURVEYS = 60;
	/** A little wider than {@link BastionGround#STRICT} on the ground, no wetter. */
	public static final BastionGround.Limits LIMITS = new BastionGround.Limits(20, 6, 3);

	/** The heights the home's ground is walked between (whole cells of noise, eight blocks each). */
	static final int HOME_LOW = 0;
	static final int HOME_HEIGHT = 200;

	/** Homes by seed, for the site test (which knows the seed and the chunk, not the placement). */
	private static final Map<Long, BastionHome> BY_SEED = new ConcurrentHashMap<>();
	/** Off only while a measurement looks at the grid on its own. */
	private static volatile boolean enabled = true;

	/** The castle placement carries its world's home (see {@code RandomSpreadStructurePlacementMixin}). */
	public interface Carrier {
		@Nullable BastionHome forja$home();

		void forja$setHome(BastionHome home);
	}

	private final long seed;
	private final ChunkGenerator generator;
	private final RandomState random;
	private final ChunkGeneratorStructureState state;
	private final RandomSpreadStructurePlacement placement;
	private final @Nullable Holder<StructureSet> villages;
	private final Predicate<Holder<Biome>> validBiome;

	private volatile boolean done;
	private boolean searching;
	private volatile @Nullable ChunkPos chunk;
	private BastionGround.@Nullable Survey survey;
	private int surveys;
	private int sites;
	private long nanos;

	private BastionHome(long seed, ChunkGenerator generator, RandomState random, ChunkGeneratorStructureState state,
		RandomSpreadStructurePlacement placement, @Nullable Holder<StructureSet> villages, Predicate<Holder<Biome>> validBiome) {
		this.seed = seed;
		this.generator = generator;
		this.random = random;
		this.state = state;
		this.placement = placement;
		this.villages = villages;
		this.validBiome = validBiome;
	}

	/**
	 * A world's generator has just worked out its structure state: if the castle can generate in it, its placement
	 * learns about this world's home (found the first time it is asked for).
	 */
	public static void attach(ChunkGenerator generator, ChunkGeneratorStructureState state, RandomState random, long seed) {
		Holder<StructureSet> set = null;
		Holder<StructureSet> villages = null;
		for (Holder<StructureSet> holder : state.possibleStructureSets()) {
			if (holder.is(SET)) {
				set = holder;
			} else if (holder.is(VILLAGES)) {
				villages = holder;
			}
		}
		if (set == null || !(set.value().placement() instanceof RandomSpreadStructurePlacement placement) || set.value().structures().isEmpty()) {
			return;
		}
		var biomes = set.value().structures().getFirst().structure().value().biomes();
		BastionHome home = new BastionHome(seed, generator, random, state, placement, villages, biomes::contains);
		((Carrier) placement).forja$setHome(home);
		if (BY_SEED.size() > 8) {
			BY_SEED.clear();
		}
		BY_SEED.put(seed, home);
		// searched in the background from now on, so that the first chunks of the world rarely wait for it
		CompletableFuture.runAsync(home::chunk, Util.backgroundExecutor());
	}

	/** Whether this chunk is its world's home castle (the site test does not ask about it again). */
	public static boolean isHome(long seed, ChunkPos at) {
		BastionHome home = BY_SEED.get(seed);
		if (home == null || !enabled) {
			return false;
		}
		ChunkPos chunk = home.chunk();
		return chunk != null && chunk.equals(at);
	}

	/** The home of the last world with this seed, if any; for the tests and the log. */
	public static @Nullable BastionHome forSeed(long seed) {
		return BY_SEED.get(seed);
	}

	/** Measurements only: whether the home castle takes its cell's place. */
	public static void setEnabled(boolean on) {
		enabled = on;
	}

	public long seed() {
		return this.seed;
	}

	/**
	 * What the grid hands out for a cell: the home chunk for the home's own cell, nothing (the grid's own candidate)
	 * otherwise. Called for every chunk of every world by the placement, so the answer is one volatile read once found.
	 */
	public @Nullable ChunkPos replace(long seed, int sourceX, int sourceZ) {
		if (seed != this.seed || !enabled) {
			return null;
		}
		ChunkPos home = this.chunk();
		if (home == null) {
			return null;
		}
		int spacing = this.placement.spacing();
		if (Math.floorDiv(sourceX, spacing) == Math.floorDiv(home.x(), spacing) && Math.floorDiv(sourceZ, spacing) == Math.floorDiv(home.z(), spacing)) {
			return home;
		}
		return null;
	}

	/** The home chunk, searched for the first time it is asked (null while the search itself asks the grid). */
	public @Nullable ChunkPos chunk() {
		if (this.done) {
			return this.chunk;
		}
		synchronized (this) {
			if (this.done) {
				return this.chunk;
			}
			if (this.searching) {
				// the search itself asking the grid, on this thread: the grid's own candidates
				return null;
			}
			this.searching = true;
			long started = System.nanoTime();
			try {
				this.search();
			} finally {
				this.nanos = System.nanoTime() - started;
				this.searching = false;
				this.done = true;
			}
			return this.chunk;
		}
	}

	/** How the chosen site measured, or null if there was none. */
	public BastionGround.@Nullable Survey survey() {
		this.chunk();
		return this.survey;
	}

	/** Ground surveys the search made. */
	public int surveys() {
		this.chunk();
		return this.surveys;
	}

	/** Sites of the right biomes clear of villages that the search found around spawn. */
	public int sites() {
		this.chunk();
		return this.sites;
	}

	/** What the search cost, in milliseconds. */
	public long millis() {
		this.chunk();
		return this.nanos / 1_000_000L;
	}

	private void search() {
		int sea = this.generator.getSeaLevel();
		// the home's own ground is only walked between y 0 and 199 (half the column, half the cost): a castle biome's
		// ground is never that high or that low, and a column cut off there reads as far too high or too low anyway
		BastionGround.Ground ground = BastionGround.ground(this.generator, LevelHeightAccessor.create(HOME_LOW, HOME_HEIGHT), this.random);
		BastionGround.Ground whole = BastionGround.ground(this.generator, LevelHeightAccessor.create(this.generator.getMinY(), this.generator.getGenDepth()),
			this.random);
		List<ChunkPos> candidates = new ArrayList<>();
		int reach = Math.ceilDiv(MAX_BLOCKS, 16);
		for (int cx = -reach; cx <= reach; cx += STEP) {
			for (int cz = -reach; cz <= reach; cz += STEP) {
				double blocks = Math.sqrt((double) cx * cx + (double) cz * cz) * 16.0;
				if (blocks >= MIN_BLOCKS && blocks <= MAX_BLOCKS) {
					candidates.add(new ChunkPos(cx, cz));
				}
			}
		}
		Collections.shuffle(candidates, new Random(this.seed ^ 0x6A09E667F3BCC908L));
		var sampler = this.random.sampler();
		var source = this.generator.getBiomeSource();
		// every site of the right biomes clear of villages, the ones the noise's rough surface says are flattest first:
		// the ground is costly to survey (a column of noise per sample), the rough surface is not
		var rough = this.random.router().preliminarySurfaceLevel();
		List<ChunkPos> sites = new ArrayList<>();
		List<Double> rise = new ArrayList<>();
		for (ChunkPos at : candidates) {
			int x0 = at.getMinBlockX();
			int z0 = at.getMinBlockZ();
			if (BastionGround.biomes(source, sampler, this.validBiome, x0, z0, sea) != BastionGround.Verdict.OK) {
				continue;
			}
			if (this.villages != null && this.state.hasStructureChunkInRange(this.villages, at.x(), at.z(), VILLAGE_CLEARANCE)) {
				continue;
			}
			double low = Double.MAX_VALUE;
			double high = -Double.MAX_VALUE;
			for (int i = -1; i <= 1; i++) {
				for (int k = -1; k <= 1; k++) {
					double y = rough.compute(new DensityFunction.SinglePointContext(x0 + i * BastionGround.REACH, 0, z0 + k * BastionGround.REACH));
					low = Math.min(low, y);
					high = Math.max(high, y);
				}
			}
			sites.add(at);
			rise.add(high - low);
		}
		this.sites = sites.size();
		Integer[] order = new Integer[sites.size()];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Double.compare(rise.get(a), rise.get(b)));
		int passed = 0;
		for (int i : order) {
			if (this.surveys >= MAX_SURVEYS && this.chunk != null) {
				break;
			}
			ChunkPos at = sites.get(i);
			this.surveys++;
			BastionGround.Survey found = BastionGround.survey(ground, at.getMinBlockX(), at.getMinBlockZ(), LIMITS);
			if (!found.ok() || this.crowded(at, whole, sea)) {
				continue;
			}
			if (this.survey == null || found.score() < this.survey.score()) {
				this.survey = found;
				this.chunk = at;
			}
			// enough: a very good site, or a few to choose from of which the best is as level as the strict test wants
			if (this.survey.score() <= GOOD_ENOUGH || ++passed >= WANT && this.survey.rise() <= BastionGround.STRICT.maxRise()) {
				break;
			}
		}
	}

	/** Whether a castle of a neighbouring cell really stands too close to this site. */
	private boolean crowded(ChunkPos at, BastionGround.Ground ground, int sea) {
		int spacing = this.placement.spacing();
		int cellX = Math.floorDiv(at.x(), spacing);
		int cellZ = Math.floorDiv(at.z(), spacing);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				// the search is under way, so the grid answers with its own candidate here
				ChunkPos other = this.placement.getPotentialStructureChunk(this.seed, (cellX + dx) * spacing, (cellZ + dz) * spacing);
				if (Math.max(Math.abs(other.x() - at.x()), Math.abs(other.z() - at.z())) >= NEIGHBOUR) {
					continue;
				}
				if (!this.placement.isStructureChunk(this.state, other.x(), other.z())) {
					continue;
				}
				int ox = other.getMinBlockX();
				int oz = other.getMinBlockZ();
				if (BastionGround.biomes(this.generator.getBiomeSource(), this.random.sampler(), this.validBiome, ox, oz, sea) == BastionGround.Verdict.OK
					&& BastionGround.survey(ground, ox, oz, BastionGround.STRICT).ok()) {
					return true;
				}
			}
		}
		return false;
	}
}
