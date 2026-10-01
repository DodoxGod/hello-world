package dev.forja.block.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import dev.forja.block.MeltPipeBlock;
import dev.forja.block.MeltSpoutBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The foundry as one network, worked out once and remembered.
 *
 * <p>Andy, 2026-09-28: "sistema que te permite unir todos los contenedores" — crucibles, tanks, pipes,
 * spouts and casting tables joined by pipe are ONE network, and metal goes from where it comes out to
 * where it is wanted however far apart they are. Two things live here:
 * <ul>
 * <li><b>Reaches.</b> What a block standing at some position is connected to: every tank, pot and
 * container at the end of the pipe runs touching it (and the ones it touches directly, and the spout
 * pouring onto it from above), each with how far away it is along the pipe and how much heat the way
 * there costs. It is a flood fill along the pipes, and it is the only walk of the network there is.</li>
 * <li><b>Deposits.</b> Tanks of the same metal touching each other are one {@link MeltDeposit}: one
 * capacity, one metal, one level.</li>
 * </ul>
 *
 * <p>Neither is ever recomputed on a timer. Both are cached per level and thrown away only when a block
 * they were built from changes: every reach remembers each position it looked at, and a change at a
 * position (or next to it) drops exactly the reaches and deposits that looked there. The hooks are the
 * foundry's own block entities appearing and disappearing (placed, broken, loaded, unloaded), a pipe whose
 * connections change because something was set down beside it, a valve turned, and a spout whose stream
 * lands somewhere new. Nothing walks the network every tick: a crucible asking "where does my metal go?"
 * forty times a second gets the same answer out of a map until somebody builds something.
 *
 * <p>Everything here is server side. A client never asks; it is only ever told what is in each tank.
 */
public final class MeltNetwork {
	/**
	 * How many blocks of pipe one walk follows before it gives up, so a mistake with a stack of pipe can
	 * never cost more than this once. It used to be 64 and walked every tick; it is walked once now, so
	 * it can be long enough that no foundry anybody builds will ever meet it.
	 */
	public static final int REACH = 1024;

	private MeltNetwork() {
	}

	/** One thing at the end of the network, seen from where the walk started. */
	public record End(BlockPos pos, int distance, int bleed, boolean direct) {
	}

	/** One deposit a position can reach, the nearest of its tanks that was reached, and how. */
	public record Fed(MeltDeposit deposit, MeltTankBlockEntity via, End end) {
	}

	/** What one position is connected to. Kept until something it looked at changes. */
	public static final class Reach {
		final BlockPos origin;
		/** Every end, nearest first (the walk is breadth first, so this is its own order). */
		final List<End> ends;
		final Map<BlockPos, End> byPos;
		/** Every position the walk looked at: pipes, valves, ends, the air under the spouts, the origin. */
		final Set<BlockPos> watched;
		boolean valid = true;
		/** The deposits the ends belong to, resolved once per change of any deposit in the level. */
		private @Nullable List<Fed> fed;
		private int fedEpoch = -1;

		Reach(BlockPos origin, List<End> ends, Set<BlockPos> watched) {
			this.origin = origin;
			this.ends = ends;
			this.watched = watched;
			this.byPos = new HashMap<>();
			for (End end : ends) {
				this.byPos.put(end.pos(), end);
			}
		}

		public List<End> ends() {
			return this.ends;
		}

		public @Nullable End end(BlockPos pos) {
			return this.byPos.get(pos);
		}
	}

	/** Everything remembered about one level. */
	private static final class Cache {
		final Map<BlockPos, Reach> reaches = new HashMap<>();
		final Map<BlockPos, Set<Reach>> watchers = new HashMap<>();
		final Map<BlockPos, MeltDeposit> deposits = new HashMap<>();
		/** Goes up every time any deposit is thrown away, so a reach knows its list of deposits is stale. */
		int depositEpoch;
		/** How many walks and how many deposits were built, for the tests that prove nothing rebuilds per tick. */
		int walks;
		int formed;
	}

	private static final Map<Level, Cache> CACHES = new WeakHashMap<>();

	/**
	 * Forgets every level when the server stops. The map is weak on the level, but what it remembers holds
	 * the level's own block entities, which hold the level: without this a singleplayer world closed and
	 * another one opened would keep the first one's foundry in memory.
	 */
	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> CACHES.clear());
	}

	private static @Nullable Cache cache(@Nullable Level level) {
		if (level == null || level.isClientSide()) {
			return null;
		}
		return CACHES.computeIfAbsent(level, ignored -> new Cache());
	}

	// ------------------------------------------------------------------ reaches

	/** What a block at this position is connected to, from the cache when nothing has changed. */
	public static Reach reach(Level level, BlockPos origin) {
		Cache cache = cache(level);
		if (cache == null) {
			return walk(level, origin);
		}
		Reach known = cache.reaches.get(origin);
		if (known != null && known.valid) {
			return known;
		}
		BlockPos key = origin.immutable();
		Reach made = walk(level, key);
		cache.walks++;
		cache.reaches.put(key, made);
		for (BlockPos at : made.watched) {
			cache.watchers.computeIfAbsent(at, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(made);
		}
		return made;
	}

	/**
	 * The deposits a position can reach, nearest first, one entry per deposit.
	 *
	 * <p>"Nearest" is by the network: how many blocks of pipe (and fall) there are between here and the
	 * closest of that deposit's tanks, which is what "el vacío más cercano" means for a smith looking at
	 * a line of tanks down one long channel.
	 */
	public static List<Fed> deposits(Level level, BlockPos origin) {
		Reach reach = reach(level, origin);
		Cache cache = cache(level);
		int epoch = cache == null ? -2 : cache.depositEpoch;
		if (reach.fed != null && reach.fedEpoch == epoch) {
			// A deposit thrown away since would have moved the epoch on; one still valid is still right.
			boolean stale = false;
			for (Fed fed : reach.fed) {
				if (!fed.deposit().valid) {
					stale = true;
					break;
				}
			}
			if (!stale) {
				return reach.fed;
			}
		}
		List<Fed> found = new ArrayList<>();
		Set<MeltDeposit> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (End end : reach.ends) {
			if (!level.isLoaded(end.pos()) || !(level.getBlockEntity(end.pos()) instanceof MeltTankBlockEntity tank) || tank.isRemoved()) {
				continue;
			}
			MeltDeposit deposit = deposit(tank);
			if (seen.add(deposit)) {
				found.add(new Fed(deposit, tank, end));
			}
		}
		List<Fed> fixed = List.copyOf(found);
		reach.fed = fixed;
		// Read again: forming a deposit on the way may have thrown another one away and moved the epoch.
		reach.fedEpoch = cache == null ? -2 : cache.depositEpoch;
		return fixed;
	}

	/**
	 * The walk itself: breadth first along pipes, from one position, up to {@link #REACH} blocks.
	 *
	 * <p>Only pipes are road. A tank, a pot or a container at the end of a run is a destination: two banks
	 * joined only through a crucible are still two banks, and a deposit does not pass metal from the pipe
	 * on one side of it to the pipe on the other. A closed valve is not road either — "abierto deja pasar y
	 * cerrado corta la red en dos" — but it is watched, so opening it throws this away.
	 */
	static Reach walk(Level level, BlockPos origin) {
		Map<BlockPos, End> ends = new LinkedHashMap<>();
		Set<BlockPos> watched = new HashSet<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Map<BlockPos, int[]> cost = new HashMap<>();
		watched.add(origin);
		seen.add(origin);
		for (Direction side : Direction.values()) {
			BlockPos next = origin.relative(side);
			if (!level.isLoaded(next)) {
				continue;
			}
			BlockState state = level.getBlockState(next);
			if (state.getBlock() instanceof MeltPipeBlock pipe) {
				seen.add(next);
				watched.add(next);
				if (MeltPipeBlock.passes(state)) {
					cost.put(next, new int[] {1, pipe.grade.bleeds});
					queue.add(next);
				}
			} else if (MeltPipeBlock.joins(level, next)) {
				seen.add(next);
				watched.add(next);
				ends.put(next, new End(next, 1, 0, true));
			}
		}
		// The spout pouring onto this position from above, across open air (and through a strainer).
		for (int up = 1; up <= MeltPipeBlock.DROP; up++) {
			BlockPos at = origin.above(up);
			watched.add(at);
			BlockState state = level.getBlockState(at);
			if (state.getBlock() instanceof MeltSpoutBlock) {
				if (seen.add(at)) {
					cost.put(at, new int[] {up, MeltPipeBlock.FALL_BLEED * up});
					queue.add(at);
				}
				break;
			}
			if (!MeltPipeBlock.fallsThrough(state)) {
				break;
			}
		}
		int walked = 0;
		while (!queue.isEmpty() && walked < REACH) {
			BlockPos at = queue.poll();
			walked++;
			int[] here = cost.getOrDefault(at, new int[] {0, 0});
			if (level.getBlockState(at).getBlock() instanceof MeltSpoutBlock) {
				// What the stream falls through is part of what this depends on: block it and the spout
				// pours nowhere (the spout's own tick notices, see MeltFlowBlockEntity).
				for (int down = 1; down <= MeltPipeBlock.DROP; down++) {
					BlockPos below = at.below(down);
					watched.add(below);
					if (MeltPipeBlock.joins(level, below) || !MeltPipeBlock.fallsThrough(level.getBlockState(below))) {
						break;
					}
				}
				BlockPos lands = MeltPipeBlock.landing(level, at);
				if (lands != null && seen.add(lands)) {
					int drop = at.getY() - lands.getY();
					ends.put(lands, new End(lands.immutable(), here[0] + drop, here[1] + MeltPipeBlock.FALL_BLEED * drop, false));
				}
			}
			for (Direction side : Direction.values()) {
				BlockPos next = at.relative(side);
				if (!seen.add(next) || !level.isLoaded(next)) {
					continue;
				}
				BlockState state = level.getBlockState(next);
				if (state.getBlock() instanceof MeltPipeBlock pipe) {
					watched.add(next);
					if (MeltPipeBlock.passes(state)) {
						cost.put(next, new int[] {here[0] + 1, here[1] + pipe.grade.bleeds});
						queue.add(next);
					}
				} else if (MeltPipeBlock.joins(level, next)) {
					watched.add(next);
					ends.put(next, new End(next, here[0] + 1, here[1], false));
				}
			}
			watched.add(at);
		}
		List<End> list = new ArrayList<>(ends.values());
		// Breadth first is nearest first along the pipes, except that a spout's landing comes in at the
		// spout's own step; sort, stably, so "nearest" is always the real distance.
		list.sort(Comparator.comparingInt(End::distance));
		return new Reach(origin, List.copyOf(list), watched);
	}

	// ------------------------------------------------------------------ deposits

	/** The deposit this tank belongs to, formed on the spot if nothing is remembered for it. */
	public static MeltDeposit deposit(MeltTankBlockEntity tank) {
		MeltDeposit known = tank.cachedDeposit();
		if (known != null && known.valid) {
			return known;
		}
		Level level = tank.getLevel();
		Cache cache = cache(level);
		if (cache == null || tank.isRemoved()) {
			// No level (a copy loaded for a test) or a client: the tank on its own.
			MeltDeposit alone = MeltDeposit.alone(tank);
			return alone;
		}
		form(level, cache, tank);
		MeltDeposit made = tank.cachedDeposit();
		return made != null ? made : MeltDeposit.alone(tank);
	}

	/**
	 * Works out the deposits of every tank touching this one.
	 *
	 * <p>The cluster is every tank touching, directly or through others. Within it, tanks of the same metal
	 * are one deposit and an empty tank goes with the metal next to it (it is only empty because the level
	 * has not reached it yet). Two metals touching are two deposits side by side — which an older world
	 * could have, and which should not be able to mix them. Worked out in a fixed order, so the same tanks
	 * always come out the same deposits whichever of them asked first.
	 */
	private static void form(Level level, Cache cache, MeltTankBlockEntity seed) {
		Map<BlockPos, MeltTankBlockEntity> cluster = new LinkedHashMap<>();
		ArrayDeque<MeltTankBlockEntity> queue = new ArrayDeque<>();
		cluster.put(seed.getBlockPos(), seed);
		queue.add(seed);
		while (!queue.isEmpty() && cluster.size() < MeltTankBlockEntity.MAX_TANKS) {
			MeltTankBlockEntity at = queue.poll();
			for (Direction side : Direction.values()) {
				BlockPos next = at.getBlockPos().relative(side);
				if (cluster.containsKey(next) || !level.isLoaded(next)) {
					continue;
				}
				if (level.getBlockEntity(next) instanceof MeltTankBlockEntity tank && !tank.isRemoved()) {
					MeltDeposit other = tank.cachedDeposit();
					// Already part of a deposit that was formed without this tank: that one is kept apart on
					// purpose (a different metal, or a wall past the size limit).
					if (other != null && other.valid) {
						continue;
					}
					cluster.put(next, tank);
					queue.add(tank);
					if (cluster.size() >= MeltTankBlockEntity.MAX_TANKS) {
						break;
					}
				}
			}
		}
		List<MeltTankBlockEntity> members = new ArrayList<>(cluster.values());
		members.sort(MeltDeposit.ORDER);

		// Every tank with metal in it starts a group; groups spread into empty tanks next to them, and two
		// groups of the same metal that meet become one.
		Map<MeltTankBlockEntity, Integer> group = new IdentityHashMap<>();
		List<Integer> parent = new ArrayList<>();
		List<@Nullable Item> metalOf = new ArrayList<>();
		ArrayDeque<MeltTankBlockEntity> spread = new ArrayDeque<>();
		for (MeltTankBlockEntity tank : members) {
			if (tank.metal() != null && tank.amount() > 0) {
				group.put(tank, parent.size());
				parent.add(parent.size());
				metalOf.add(tank.metal());
				spread.add(tank);
			}
		}
		while (!spread.isEmpty()) {
			MeltTankBlockEntity tank = spread.poll();
			int mine = find(parent, group.get(tank));
			for (Direction side : Direction.values()) {
				MeltTankBlockEntity next = cluster.get(tank.getBlockPos().relative(side));
				if (next == null) {
					continue;
				}
				Integer theirs = group.get(next);
				if (theirs == null) {
					if (next.metal() == null || next.amount() <= 0) {
						group.put(next, mine);
						spread.add(next);
					}
				} else {
					int root = find(parent, theirs);
					if (root != mine && metalOf.get(root) == metalOf.get(mine)) {
						parent.set(root, mine);
					}
				}
			}
		}
		Map<Integer, List<MeltTankBlockEntity>> byGroup = new LinkedHashMap<>();
		for (MeltTankBlockEntity tank : members) {
			Integer id = group.get(tank);
			int root = id == null ? -1 : find(parent, id);
			byGroup.computeIfAbsent(root, ignored -> new ArrayList<>()).add(tank);
		}
		List<MeltDeposit> made = new ArrayList<>();
		for (List<MeltTankBlockEntity> part : byGroup.values()) {
			made.add(new MeltDeposit(level, part));
		}
		for (MeltDeposit deposit : made) {
			if (made.size() > 1) {
				deposit.cluster = made;
			}
			for (MeltTankBlockEntity tank : deposit.members) {
				cache.deposits.put(tank.getBlockPos(), deposit);
				tank.setDeposit(deposit);
			}
		}
		cache.formed += made.size();
		// Only now that every tank knows its deposit: spreading the metal writes block states, and those
		// must not find a tank that belongs to nothing.
		for (MeltDeposit deposit : made) {
			deposit.settleIn();
		}
	}

	private static int find(List<Integer> parent, int id) {
		int at = id;
		while (parent.get(at) != at) {
			parent.set(at, parent.get(parent.get(at)));
			at = parent.get(at);
		}
		return at;
	}

	// ------------------------------------------------------------------ invalidation

	/**
	 * Something at this position changed: forget every reach that looked at it or next to it, and every
	 * deposit with a tank there or next to it.
	 *
	 * <p>Next to it as well, because what is new is often something set down beside the network — a pipe
	 * against a pipe, a tank against a tank, a chest at the end of a run — at a position nobody had looked
	 * at yet.
	 */
	public static void changed(@Nullable Level level, BlockPos pos) {
		if (level == null || level.isClientSide()) {
			return;
		}
		if (level.getServer() != null && !level.getServer().isSameThread()) {
			// Chunks can come in off the main thread; the caches are only ever touched on it.
			BlockPos at = pos.immutable();
			level.getServer().execute(() -> changed(level, at));
			return;
		}
		Cache cache = CACHES.get(level);
		if (cache == null) {
			return;
		}
		forget(cache, pos);
		for (Direction side : Direction.values()) {
			forget(cache, pos.relative(side));
		}
	}

	private static void forget(Cache cache, BlockPos at) {
		Set<Reach> watching = cache.watchers.get(at);
		if (watching != null && !watching.isEmpty()) {
			for (Reach reach : List.copyOf(watching)) {
				drop(cache, reach);
			}
		}
		MeltDeposit deposit = cache.deposits.get(at);
		if (deposit != null) {
			drop(cache, deposit);
		}
	}

	private static void drop(Cache cache, Reach reach) {
		reach.valid = false;
		cache.reaches.remove(reach.origin, reach);
		for (BlockPos at : reach.watched) {
			Set<Reach> set = cache.watchers.get(at);
			if (set != null) {
				set.remove(reach);
				if (set.isEmpty()) {
					cache.watchers.remove(at);
				}
			}
		}
	}

	private static void drop(Cache cache, MeltDeposit deposit) {
		List<MeltDeposit> all = deposit.cluster != null ? deposit.cluster : List.of(deposit);
		for (MeltDeposit each : all) {
			if (!each.valid) {
				continue;
			}
			each.valid = false;
			for (MeltTankBlockEntity tank : each.members) {
				cache.deposits.remove(tank.getBlockPos(), each);
				tank.forgetDeposit(each);
			}
		}
		cache.depositEpoch++;
	}

	/**
	 * A deposit that went from empty to holding a metal, or back: in a cluster shared with another metal
	 * the empty tanks may now belong elsewhere, so the cluster is worked out again next time it is asked.
	 */
	static void regroup(Level level, MeltDeposit deposit) {
		Cache cache = cache(level);
		if (cache != null && deposit.cluster != null) {
			drop(cache, deposit);
		}
	}

	/** How many walks and deposits this level has built so far, for the tests. */
	public static int[] builds(Level level) {
		Cache cache = cache(level);
		return cache == null ? new int[] {0, 0} : new int[] {cache.walks, cache.formed};
	}
}
