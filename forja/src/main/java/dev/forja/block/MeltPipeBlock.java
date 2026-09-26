package dev.forja.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.block.entity.MeltTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Conducto de colada: the pipe that joins a foundry together.
 *
 * <p>It holds nothing. A pipe is a <em>connection</em>, not a container: a crucible looks for its tanks
 * through pipes as though they were touching, and a bank of tanks pushes its metal out to whatever
 * container a pipe run ends at. That is deliberate — a pipe with its own little buffer is a pipe that
 * has to be saved, synced, balanced and debugged, and all a foundry ever wanted from one is to stop
 * having to build the crucible directly against the glass.
 *
 * <p>It is shaped like <b>floor</b>, not like plumbing: a slab you walk over with the metal sunk flush
 * into a channel down the middle, open to the sky. A foundry is worth looking at, and a tube hid the
 * only part of it that moves. A run that has to climb falls back to a closed tube for that one block,
 * because a channel cannot carry anything up a wall.
 */
public class MeltPipeBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
	public static final MapCodec<MeltPipeBlock> CODEC = simpleCodec(properties -> new MeltPipeBlock(properties, Grade.BRONCE));

	/**
	 * What a run of pipe costs the metal that goes through it.
	 *
	 * <p>Bronze is what you can afford first and it bleeds heat: a long bronze run will deliver metal
	 * that has already set. Damascus barely takes anything, which is what makes a foundry that sprawls
	 * possible at all. You cannot craft the better two — they are cast in the box from a mould of a pipe,
	 * out of the very metal they are made of.
	 */
	public enum Grade {
		BRONCE(6),
		ACERO(3),
		DAMASCO(1);

		/** Heat the melt loses per block of this pipe it travels. */
		public final int bleeds;

		Grade(int bleeds) {
			this.bleeds = bleeds;
		}

		public String id() {
			return this == BRONCE ? "conducto_de_colada" : "conducto_de_" + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public final Grade grade;

	public static final BooleanProperty NORTH = BooleanProperty.create("north");
	public static final BooleanProperty SOUTH = BooleanProperty.create("south");
	public static final BooleanProperty EAST = BooleanProperty.create("east");
	public static final BooleanProperty WEST = BooleanProperty.create("west");
	public static final BooleanProperty UP = BooleanProperty.create("up");
	public static final BooleanProperty DOWN = BooleanProperty.create("down");

	public static final Map<Direction, BooleanProperty> SIDES = Map.of(
		Direction.NORTH, NORTH, Direction.SOUTH, SOUTH, Direction.EAST, EAST,
		Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN
	);

	/** How far a run of pipe will carry, so a mistake with a stack of them cannot cost a tick. */
	public static final int REACH = 64;

	/** How far metal poured out of a spout falls before there is nothing left to catch it. */
	public static final int DROP = 5;

	/** What every block of that fall costs the metal. Open air is the coldest thing it ever crosses. */
	public static final int FALL_BLEED = 4;

	/**
	 * A channel is floor. The whole footprint is solid up to the walking surface, so a run of it is
	 * something you cross without thinking about it rather than something you trip over.
	 */
	private static final VoxelShape SLAB = Block.box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0);

	/** And the one piece that is not floor: the closed tube a run climbs with. */
	private static final VoxelShape RISER = Block.box(5.0, 6.0, 5.0, 11.0, 16.0, 11.0);

	public MeltPipeBlock(Properties properties) {
		this(properties, Grade.BRONCE);
	}

	public MeltPipeBlock(Properties properties, Grade grade) {
		super(properties);
		this.grade = grade;
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false)
			.setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
	}

	/** Whatever a pipe is willing to reach for: more pipe, glass, a pot, or something that holds items. */
	public static boolean joins(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof MeltPipeBlock || state.getBlock() instanceof MeltTankBlock
			|| state.getBlock() instanceof CrucibleBlock) {
			return true;
		}
		return level.getBlockEntity(pos) instanceof net.minecraft.world.Container;
	}

	private BlockState shaped(LevelReader level, BlockPos pos, BlockState state) {
		BlockState out = state;
		for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
			out = out.setValue(side.getValue(), joins(level, pos.relative(side.getKey())));
		}
		return out;
	}

	@Override
	public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
		return this.shaped(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
		BlockPos pos, Direction side, BlockPos neighbour, BlockState neighbourState, net.minecraft.util.RandomSource random) {
		return state.setValue(SIDES.get(side), joins(level, neighbour));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(UP) ? Shapes.or(SLAB, RISER) : SLAB;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	/**
	 * It still holds nothing. The block entity is there so the channel can be <b>drawn honestly</b>:
	 * which metal is going past, and whether anything is going past at all. See MeltFlowBlockEntity.
	 */
	@Override
	public net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity newBlockEntity(
		BlockPos pos, BlockState state) {
		return new dev.forja.block.entity.MeltFlowBlockEntity(pos, state);
	}

	@Override
	public <T extends net.minecraft.world.level.block.entity.BlockEntity>
		net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntityTicker<T> getTicker(
			Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
		if (level.isClientSide() || type != dev.forja.block.entity.ModBlockEntities.COLADA) {
			return null;
		}
		return (world, pos, blockState, entity) -> dev.forja.block.entity.MeltFlowBlockEntity.serverTick(
			world, pos, blockState, (dev.forja.block.entity.MeltFlowBlockEntity) entity);
	}

	// ------------------------------------------------------------------ the network

	/**
	 * Everything of interest a run of pipe touches, starting from one block.
	 *
	 * <p>The walk only ever goes through pipes: a tank or a pot at the end of a run is a destination,
	 * not a road, so two banks joined only by a crucible are still two banks.
	 */
	public static List<BlockPos> reachable(Level level, BlockPos from) {
		return new ArrayList<>(walk(level, from).keySet());
	}

	/**
	 * Where the metal a spout pours lands, or null if it falls into nothing.
	 *
	 * <p>It falls straight down through open air until it finds something worth landing in. Anything
	 * solid in the way stops it dead — metal does not pour through a floor, and a spout over a roof is
	 * a spout that is doing nothing, which is exactly what it will look like.
	 */
	public static @org.jspecify.annotations.Nullable BlockPos landing(BlockGetter level, BlockPos spout) {
		for (int down = 1; down <= DROP; down++) {
			BlockPos at = spout.below(down);
			if (joins(level, at)) {
				return at;
			}
			if (!level.getBlockState(at).isAir()) {
				return null;
			}
		}
		return null;
	}

	/**
	 * The spout pouring into this block, if there is one within reach and nothing in between.
	 *
	 * <p>The drop has to work both ways round. Walking down from the pot, a spout finds what it is
	 * pouring into; but a casting table pulls metal by walking out from <em>itself</em>, and if it only
	 * ever looked sideways it would never notice the spout on the gantry above it.
	 */
	public static @org.jspecify.annotations.Nullable BlockPos spoutAbove(BlockGetter level, BlockPos pos) {
		for (int up = 1; up <= DROP; up++) {
			BlockPos at = pos.above(up);
			if (level.getBlockState(at).getBlock() instanceof MeltSpoutBlock) {
				return at;
			}
			if (!level.getBlockState(at).isAir()) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Every end a run of pipe reaches, and what the metal lost getting there.
	 *
	 * <p>The cost is summed over the pipes actually walked, so a cheap bronze detour is paid for even if
	 * the last block before the tank is damascus.
	 */
	public static java.util.Map<BlockPos, Integer> walk(Level level, BlockPos from) {
		java.util.Map<BlockPos, Integer> ends = new java.util.LinkedHashMap<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		java.util.Map<BlockPos, Integer> cost = new java.util.HashMap<>();
		seen.add(from);
		for (Direction side : Direction.values()) {
			BlockPos next = from.relative(side);
			if (seen.add(next) && level.getBlockState(next).getBlock() instanceof MeltPipeBlock pipe) {
				cost.put(next, pipe.grade.bleeds);
				queue.add(next);
			}
		}
		// And the spout overhead, if this is what one has been pouring into all along.
		BlockPos over = spoutAbove(level, from);
		if (over != null && seen.add(over)) {
			cost.put(over, FALL_BLEED * (over.getY() - from.getY()));
			queue.add(over);
		}
		int walked = 0;
		while (!queue.isEmpty() && walked < REACH) {
			BlockPos at = queue.poll();
			walked++;
			int here = cost.getOrDefault(at, 0);
			// A spout reaches whatever is under it across open air, and the fall costs the metal.
			if (level.getBlockState(at).getBlock() instanceof MeltSpoutBlock) {
				BlockPos lands = landing(level, at);
				if (lands != null) {
					ends.putIfAbsent(lands, here + FALL_BLEED * (at.getY() - lands.getY()));
					seen.add(lands);
				}
			}
			for (Direction side : Direction.values()) {
				BlockPos next = at.relative(side);
				if (!seen.add(next)) {
					continue;
				}
				if (level.getBlockState(next).getBlock() instanceof MeltPipeBlock pipe) {
					cost.put(next, here + pipe.grade.bleeds);
					queue.add(next);
				} else if (joins(level, next)) {
					ends.put(next, here);
				}
			}
		}
		return ends;
	}

	/** What a run of pipe between these two blocks costs the metal, or 0 if they are touching. */
	public static int bleedBetween(Level level, BlockPos from, BlockPos to) {
		return walk(level, from).getOrDefault(to, 0);
	}

	/** The first tank a run of pipe out of this block reaches, if any. */
	public static @org.jspecify.annotations.Nullable MeltTankBlockEntity tankFrom(Level level, BlockPos from) {
		for (BlockPos end : reachable(level, from)) {
			if (level.getBlockEntity(end) instanceof MeltTankBlockEntity tank) {
				return tank;
			}
		}
		return null;
	}

	/**
	 * Every container a run of pipe out of this block reaches, which is where a bank empties itself.
	 *
	 * <p>Tanks and crucibles are left out on purpose even though both hold items. A tank emptying into
	 * another tank would be metal going round in circles, and a tank emptying into a crucible would fill
	 * the pot's own slots with the very metal it is about to take back out of the glass — the crucible
	 * pulls what it needs by itself, and being on the same pipe run must not mean being force-fed.
	 */
	public static List<net.minecraft.world.Container> containersFrom(Level level, BlockPos from) {
		List<net.minecraft.world.Container> found = new ArrayList<>();
		for (BlockPos end : reachable(level, from)) {
			var at = level.getBlockEntity(end);
			if (at instanceof net.minecraft.world.Container container
				&& !(at instanceof MeltTankBlockEntity) && !(at instanceof CrucibleBlockEntity)) {
				found.add(container);
			}
		}
		return found;
	}
}
