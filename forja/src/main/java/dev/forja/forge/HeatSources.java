package dev.forja.forge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import dev.forja.block.BoilerBlock;
import dev.forja.block.HeatPipeBlock;
import dev.forja.block.entity.BoilerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The heat line (docs/FUNDICION_V2.md, part B): heat that comes down a <b>heat pipe</b> from a boiler or a
 * heat depot, carrying one of five {@link HeatFluid fluids}. A pipe hands its fluid to any consumer it
 * touches, and a boiler or depot built right against a consumer does the same without a pipe.
 *
 * <h2>The API</h2>
 * The plain heat of a position is still read in ONE place, {@link Alloys#heatAt}: the block under it or a
 * registered {@link Alloys.HeatSource}, the hotter. The heat line registers itself there ({@link #register}),
 * so every block that reads {@code Alloys.heatAt} (forge tables, the assembler, crucibles, casting tables,
 * Jade) gets the pipes for free. This class adds what a plain heat cannot say:
 * <ul>
 * <li>{@link #at(Level, BlockPos)} — {@code Alloys.heatAt} plus the fluid's qualities ({@link Supply}):
 * which fluid it is, whether the pipe is what gave the heat, the melting speed, the steady-hand bonus,
 * brine for quenching, and a way to pay for what was used ({@link Supply#draw}).</li>
 * <li>{@link #piped(Level, BlockPos)} — only what heat pipes and vessels bring, ignoring the block under.</li>
 * </ul>
 * Everything works on both sides: on a client (Jade) the answer is read off the pipes' own block states
 * and costs nothing, on the server it is the real network with real amounts.
 *
 * <h2>Networks and performance</h2>
 * A network is every heat pipe joined to a pipe, and every boiler or depot touching one of them. It is
 * found by a flood fill of at most {@link #REACH} pipes and <b>cached</b> per level; every pipe of it maps
 * to the same {@link Network}. The cache is dropped whenever a pipe, a boiler or a depot is placed or
 * broken ({@link #invalidate}), and a network is never trusted for more than {@link #EXPIRY} ticks, so a
 * chunk that loaded after the walk is picked up. No consumer walks anything per tick: it looks at its six
 * neighbours and asks the cache.
 *
 * <p>A network carries <b>one</b> fluid: the one it has the most of across its vessels (so do not join a
 * brine boiler and a lava depot with the same pipe — lay a run for each; a casting table can touch both).
 */
public final class HeatSources {
	/** The most pipes one network walks, so a stack of them placed by mistake cannot cost a tick. */
	public static final int REACH = 128;

	/** How long a cached network is trusted without any block change, in ticks: a safety net only. */
	public static final int EXPIRY = 200;

	private static final Map<Level, Map<BlockPos, Network>> CACHE = new WeakHashMap<>();

	private HeatSources() {
	}

	// ------------------------------------------------------------------ what a consumer gets

	/**
	 * What reaches one consumer.
	 *
	 * @param heat the heat it works at
	 * @param fluid the hot fluid touching it, if any (it may be touching one that is no better than the
	 *     block under it: see {@code fromPipe})
	 * @param fromPipe whether that fluid is what gave {@code heat}, which is when its qualities apply and
	 *     it has to be paid for
	 * @param quench whether ice brine touches it too
	 * @param hotSources the vessels the hot fluid comes out of (server only)
	 * @param brineSources the vessels the brine comes out of (server only)
	 */
	public record Supply(Alloys.Heat heat, @Nullable HeatFluid fluid, boolean fromPipe, boolean quench,
		List<BlockPos> hotSources, List<BlockPos> brineSources) {
		public static final Supply NONE = new Supply(Alloys.Heat.FRIA, null, false, false, List.of(), List.of());

		/** Whether the heat came down a pipe (and so the fluid's qualities apply). */
		public boolean piped() {
			return this.fromPipe && this.fluid != null;
		}

		/** How fast a crucible works on it, in percent: 100 unless a piped fluid says otherwise. */
		public int meltPercent() {
			return this.piped() ? this.fluid.meltPercent : 100;
		}

		/** What a piped fluid adds to a casting table's chance of a perfect tool. */
		public float steadyBonus() {
			return this.piped() ? this.fluid.steadyBonus : 0.0F;
		}

		/**
		 * Pays for heat used: takes up to {@code amount} mB of the hot fluid out of its vessels and says how
		 * much it got. Nothing on a client, and nothing when the heat did not come down a pipe.
		 */
		public int draw(Level level, int amount) {
			if (!this.piped() || amount <= 0 || level.isClientSide()) {
				return 0;
			}
			return drain(level, this.hotSources, this.fluid, amount);
		}

		/** Spends one quench of brine ({@link HeatFluid#QUENCH_COST}); false if there is not that much. */
		public boolean spendQuench(Level level) {
			if (!this.quench || level.isClientSide()) {
				return this.quench;
			}
			int there = 0;
			for (BlockPos at : this.brineSources) {
				if (level.getBlockEntity(at) instanceof BoilerBlockEntity vessel && vessel.fluid() == HeatFluid.SALMUERA_HELADA) {
					there += vessel.amount();
				}
			}
			if (there < HeatFluid.QUENCH_COST) {
				return false;
			}
			drain(level, this.brineSources, HeatFluid.SALMUERA_HELADA, HeatFluid.QUENCH_COST);
			return true;
		}
	}

	private static int drain(Level level, List<BlockPos> sources, HeatFluid fluid, int amount) {
		int got = 0;
		for (BlockPos at : sources) {
			if (got >= amount) {
				break;
			}
			if (level.getBlockEntity(at) instanceof BoilerBlockEntity vessel) {
				got += vessel.drain(fluid, amount - got);
			}
		}
		return got;
	}

	/** Hooks the heat line into {@link Alloys#heatAt}, the one place plain heat is read. Called once, at startup. */
	public static void register() {
		Alloys.addHeatSource((level, pos) -> piped(level, pos).heat());
	}

	/**
	 * Everything a consumer at this position gets: {@link Alloys#heatAt} (the block under it, or every heat
	 * pipe or vessel touching it, whichever is better) and the qualities of the fluid when a pipe gave it.
	 *
	 * <p>A fluid only as hot as the block under is used when it brings something more (blaze blood's speed,
	 * forge breath's steady hand); otherwise the free heat under the table wins and nothing is spent.
	 */
	public static Supply at(Level level, BlockPos pos) {
		Alloys.Heat under = Alloys.heatUnder(level, pos);
		Alloys.Heat heat = Alloys.heatAt(level, pos);
		Supply piped = piped(level, pos);
		boolean pipeWins = piped.fluid() != null && (piped.heat().ordinal() > under.ordinal()
			|| (piped.heat() == under && (piped.fluid().meltPercent > 100 || piped.fluid().steadyBonus > 0.0F)));
		return new Supply(pipeWins ? piped.heat() : heat, piped.fluid(), pipeWins, piped.quench(),
			piped.hotSources(), piped.brineSources());
	}

	/** Only what the heat line brings to this position: the pipes and vessels on its six sides. */
	public static Supply piped(Level level, BlockPos pos) {
		HeatFluid best = null;
		boolean quench = false;
		List<BlockPos> hot = new ArrayList<>();
		List<BlockPos> brine = new ArrayList<>();
		for (Direction side : Direction.values()) {
			BlockPos at = pos.relative(side);
			if (!level.isLoaded(at)) {
				continue;
			}
			BlockState state = level.getBlockState(at);
			HeatFluid fluid;
			List<BlockPos> from;
			if (state.getBlock() instanceof HeatPipeBlock) {
				if (level.isClientSide()) {
					fluid = state.getValue(HeatPipeBlock.FLUIDO).fluid;
					from = List.of();
				} else {
					Network network = network(level, at);
					fluid = network.fluid(level);
					from = fluid == null ? List.of() : network.sourcesOf(level, fluid);
				}
			} else if (state.getBlock() instanceof BoilerBlock) {
				if (level.isClientSide()) {
					fluid = state.getValue(BoilerBlock.LEVEL) > 0 ? state.getValue(BoilerBlock.FLUIDO).fluid : null;
					from = List.of();
				} else {
					fluid = level.getBlockEntity(at) instanceof BoilerBlockEntity vessel && vessel.amount() > 0 ? vessel.fluid() : null;
					from = List.of(at.immutable());
				}
			} else {
				continue;
			}
			if (fluid == null) {
				continue;
			}
			if (fluid.cools()) {
				quench = true;
				brine.addAll(from);
			} else if (best == null || better(fluid, best)) {
				best = fluid;
				hot.clear();
				hot.addAll(from);
			} else if (fluid == best) {
				for (BlockPos source : from) {
					if (!hot.contains(source)) {
						hot.add(source);
					}
				}
			}
		}
		if (best == null && !quench) {
			return Supply.NONE;
		}
		return new Supply(best == null ? Alloys.Heat.FRIA : best.heat, best, best != null, quench, hot, brine);
	}

	/** Hotter first; as hot, faster; as fast, steadier. */
	private static boolean better(HeatFluid fluid, HeatFluid than) {
		if (fluid.heat != than.heat) {
			return fluid.heat.ordinal() > than.heat.ordinal();
		}
		if (fluid.meltPercent != than.meltPercent) {
			return fluid.meltPercent > than.meltPercent;
		}
		return fluid.steadyBonus > than.steadyBonus;
	}

	/**
	 * Whether a block right beside a casting table keeps it warm: a wisp lantern, lava, magma, fire, or a
	 * lit crucible. (The table is warmed from beside as well as from under; the forge table only from under,
	 * see {@link Alloys#heatUnder}.)
	 */
	public static boolean warmsBeside(BlockState beside) {
		return beside.is(dev.forja.registry.ModBlocks.FAROL_DE_PAVESA)
			|| beside.is(Blocks.LAVA)
			|| beside.is(Blocks.MAGMA_BLOCK)
			|| beside.is(Blocks.FIRE)
			|| (beside.getBlock() instanceof dev.forja.block.CrucibleBlock && beside.getValue(dev.forja.block.CrucibleBlock.LIT));
	}

	// ------------------------------------------------------------------ the networks

	/** One run of heat pipe and the vessels on it. Cached; see the class comment. */
	public static final class Network {
		private final List<BlockPos> pipes;
		private final List<BlockPos> sources;
		private final long built;
		/** What its pipes were last set to show, so a refresh that changes nothing touches nothing. */
		private HeatFluid.@Nullable Shown shown;

		private Network(List<BlockPos> pipes, List<BlockPos> sources, long built) {
			this.pipes = pipes;
			this.sources = sources;
			this.built = built;
		}

		public List<BlockPos> pipes() {
			return this.pipes;
		}

		public List<BlockPos> sources() {
			return this.sources;
		}

		/** The fluid it carries: the one it has the most of across its vessels, or null when they are all empty. */
		public @Nullable HeatFluid fluid(Level level) {
			Map<HeatFluid, Integer> totals = new EnumMap<>(HeatFluid.class);
			for (BlockPos at : this.sources) {
				if (level.getBlockEntity(at) instanceof BoilerBlockEntity vessel && vessel.amount() > 0 && vessel.fluid() != null) {
					totals.merge(vessel.fluid(), vessel.amount(), Integer::sum);
				}
			}
			HeatFluid best = null;
			int most = 0;
			for (Map.Entry<HeatFluid, Integer> entry : totals.entrySet()) {
				if (entry.getValue() > most) {
					best = entry.getKey();
					most = entry.getValue();
				}
			}
			return best;
		}

		/** The vessels holding this fluid. */
		public List<BlockPos> sourcesOf(Level level, HeatFluid fluid) {
			List<BlockPos> found = new ArrayList<>();
			for (BlockPos at : this.sources) {
				if (level.getBlockEntity(at) instanceof BoilerBlockEntity vessel && vessel.fluid() == fluid && vessel.amount() > 0) {
					found.add(at);
				}
			}
			return found;
		}
	}

	/** The network this pipe belongs to, from the cache or walked now. */
	public static Network network(Level level, BlockPos pipe) {
		Map<BlockPos, Network> byPipe = CACHE.computeIfAbsent(level, key -> new HashMap<>());
		Network cached = byPipe.get(pipe);
		if (cached != null && level.getGameTime() - cached.built < EXPIRY) {
			return cached;
		}
		Network walked = walk(level, pipe);
		for (BlockPos at : walked.pipes) {
			byPipe.put(at, walked);
		}
		return walked;
	}

	private static Network walk(Level level, BlockPos start) {
		List<BlockPos> pipes = new ArrayList<>();
		Set<BlockPos> sources = new LinkedHashSet<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.immutable());
		seen.add(start.immutable());
		while (!queue.isEmpty() && pipes.size() < REACH) {
			BlockPos at = queue.poll();
			pipes.add(at);
			for (Direction side : Direction.values()) {
				BlockPos next = at.relative(side);
				if (!seen.add(next) || !level.isLoaded(next)) {
					continue;
				}
				Block block = level.getBlockState(next).getBlock();
				if (block instanceof HeatPipeBlock) {
					queue.add(next);
				} else if (block instanceof BoilerBlock) {
					sources.add(next);
				}
			}
		}
		return new Network(List.copyOf(pipes), List.copyOf(sources), level.getGameTime());
	}

	/** Forgets every network of this level: a pipe, a boiler or a depot was placed or broken. */
	public static void invalidate(Level level) {
		CACHE.remove(level);
	}

	/**
	 * Sets every pipe of this pipe's network to show the fluid it now carries, if that changed. Called when
	 * a pipe is placed or a neighbour of it changes (by a scheduled tick), and by a vessel when what it
	 * offers changes (it ran dry, or took a first fluid).
	 */
	public static void refreshPipes(Level level, BlockPos pipe) {
		if (level.isClientSide() || !(level.getBlockState(pipe).getBlock() instanceof HeatPipeBlock)) {
			return;
		}
		Network network = network(level, pipe);
		HeatFluid.Shown want = HeatFluid.Shown.of(network.fluid(level));
		if (network.shown == want) {
			return;
		}
		for (BlockPos at : network.pipes) {
			BlockState state = level.getBlockState(at);
			if (state.getBlock() instanceof HeatPipeBlock && state.getValue(HeatPipeBlock.FLUIDO) != want) {
				// Clients only: a pipe changing colour is not a change any neighbour has to hear about.
				level.setBlock(at, state.setValue(HeatPipeBlock.FLUIDO, want), Block.UPDATE_CLIENTS);
			}
		}
		network.shown = want;
	}

	/** Refreshes every network touching this position (a vessel whose offer changed). */
	public static void refreshAround(Level level, BlockPos pos) {
		Set<Network> done = new HashSet<>();
		for (Direction side : Direction.values()) {
			BlockPos at = pos.relative(side);
			if (level.isLoaded(at) && level.getBlockState(at).getBlock() instanceof HeatPipeBlock) {
				Network network = network(level, at);
				if (done.add(network)) {
					network.shown = null;
					refreshPipes(level, at);
				}
			}
		}
	}
}
