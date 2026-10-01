package dev.forja.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.MapCodec;
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
 * <p>It holds nothing. A pipe is a <em>connection</em>, not a container: everything joined by pipe is one
 * network (block/entity/MeltNetwork), a crucible pours into the deposits on it however far away they
 * are, and a casting table pulls out of them the same way. That is deliberate — a pipe with its own
 * little buffer is a pipe that has to be saved, synced, balanced and debugged, and all a foundry ever
 * wanted from one is to stop having to build the crucible directly against the glass.
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

	/**
	 * How far a run of pipe will carry. It used to be 64 because every pot walked its pipes every tick;
	 * the network is worked out once now and remembered (MeltNetwork), so a run can be as long as a
	 * foundry is ever going to be.
	 */
	public static final int REACH = dev.forja.block.entity.MeltNetwork.REACH;

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

	/**
	 * Whatever a pipe is willing to reach for: the members of the metal network and nothing else — more
	 * pipe, glass, a pot, a casting table, a casting box.
	 *
	 * <p>It used to reach for anything that held items, and a tank at the other end of the run then filled
	 * it with ingots: a hopper that happened to touch a pipe (the one feeding a crucible its ore, say) took
	 * the tank's metal as bars and fed it straight back into the pot, which melted it back into the tank,
	 * burning embers to go round in a circle. Metal goes where the network needs it — a deposit with room,
	 * a table that casts — and the one deliberate way out as items is a container right under a tank
	 * (MeltTankBlockEntity#serverTick), which you have to build on purpose.
	 */
	public static boolean joins(BlockGetter level, BlockPos pos) {
		net.minecraft.world.level.block.Block block = level.getBlockState(pos).getBlock();
		return block instanceof MeltPipeBlock || block instanceof MeltTankBlock || block instanceof CrucibleBlock
			|| block instanceof CastingTableBlock || block instanceof CastingBoxBlock;
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

	/**
	 * A neighbour changed. If that changes what this pipe is joined to — a chest set down at the end of a
	 * run, a tank taken away — the network around it is worked out again the next time it is asked.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
		BlockPos pos, Direction side, BlockPos neighbour, BlockState neighbourState, net.minecraft.util.RandomSource random) {
		boolean joined = joins(level, neighbour);
		if (joined != state.getValue(SIDES.get(side)) && level instanceof Level world) {
			dev.forja.block.entity.MeltNetwork.changed(world, pos);
		}
		return state.setValue(SIDES.get(side), joined);
	}

	/**
	 * Whether metal goes through this block of pipe. Every pipe and spout does; a valve only while it is
	 * open (MeltValveBlock).
	 */
	public static boolean passes(BlockState state) {
		return !(state.getBlock() instanceof MeltValveBlock) || MeltValveBlock.isOpen(state);
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
	 * <p>It falls straight down through open air — and through a strainer, see fallsThrough — until it
	 * finds something worth landing in. Anything else solid in the way stops it dead — metal does not pour through a floor, and a spout over a roof is
	 * a spout that is doing nothing, which is exactly what it will look like.
	 */
	public static @org.jspecify.annotations.Nullable BlockPos landing(BlockGetter level, BlockPos spout) {
		for (int down = 1; down <= DROP; down++) {
			BlockPos at = spout.below(down);
			if (joins(level, at)) {
				return at;
			}
			if (!fallsThrough(level.getBlockState(at))) {
				return null;
			}
		}
		return null;
	}

	/**
	 * What a falling stream goes straight through: open air, and a strainer.
	 *
	 * <p>The strainer is the one solid thing the metal is meant to cross. It stands on the casting table,
	 * right in the path of the pour, and the whole point of it is that the melt goes through it on the way
	 * down (Andy: "pasando primero por el colador antes de que llegue a la mesa con el molde"). Treated as
	 * a roof, it made the spout above it pour into nothing.
	 */
	public static boolean fallsThrough(BlockState state) {
		return state.isAir() || state.getBlock() instanceof StrainerBlock;
	}

	/** The strainer the stream from this spout falls through on its way down, if there is one. */
	public static @org.jspecify.annotations.Nullable BlockPos strainerUnder(BlockGetter level, BlockPos spout) {
		BlockPos lands = landing(level, spout);
		if (lands == null) {
			return null;
		}
		for (BlockPos at = spout.below(); at.getY() > lands.getY(); at = at.below()) {
			if (level.getBlockState(at).getBlock() instanceof StrainerBlock) {
				return at;
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
			if (!fallsThrough(level.getBlockState(at))) {
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
		// The walk itself lives in MeltNetwork now, where it is done once and remembered until a block of
		// the network changes. What is left here is the old question: the ends down a pipe or a spout (not
		// the ones merely touching), and what the way there costs.
		java.util.Map<BlockPos, Integer> ends = new java.util.LinkedHashMap<>();
		for (dev.forja.block.entity.MeltNetwork.End end : dev.forja.block.entity.MeltNetwork.reach(level, from).ends()) {
			if (!end.direct()) {
				ends.put(end.pos(), end.bleed());
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

	// There used to be a containersFrom here: every container at the end of a run, which is where a bank
	// emptied itself as ingots. It is gone with the containers themselves (see joins): the network carries
	// metal between its own members, and a tank's one way out as items is the container right under it.
}
