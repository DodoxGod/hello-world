package dev.forja.block;

import java.util.Map;

import com.mojang.serialization.MapCodec;
import dev.forja.forge.HeatConsumer;
import dev.forja.forge.HeatFluid;
import dev.forja.forge.HeatSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tubo de calor: the pipe of the heat line (docs/FUNDICION_V2.md, part B).
 *
 * <p>A second pipe system that has nothing to do with the metal one: it joins other heat pipes, boilers,
 * heat depots and the things that take heat ({@link HeatConsumer}: crucibles, casting tables, forge
 * tables), and <b>never</b> a metal pipe — a melt pipe does not reach for it either, since it is not a
 * container. Like a metal pipe it holds nothing: the fluid is in the vessels, and the pipe is drawn with
 * whatever its network carries ({@link #FLUIDO}, tinted by the fluid's colour through a slit in the casing).
 *
 * <p>What the network is and how it is cached is {@link HeatSources}; this block only tells the cache when
 * it has to forget (placed, broken, a neighbour changed) and keeps its own colour up to date by a
 * scheduled tick.
 */
public class HeatPipeBlock extends Block {
	public static final MapCodec<HeatPipeBlock> CODEC = simpleCodec(HeatPipeBlock::new);

	public static final BooleanProperty NORTH = BooleanProperty.create("north");
	public static final BooleanProperty SOUTH = BooleanProperty.create("south");
	public static final BooleanProperty EAST = BooleanProperty.create("east");
	public static final BooleanProperty WEST = BooleanProperty.create("west");
	public static final BooleanProperty UP = BooleanProperty.create("up");
	public static final BooleanProperty DOWN = BooleanProperty.create("down");
	/** What the pipe is carrying, for the eye: the network's fluid, or nothing. */
	public static final EnumProperty<HeatFluid.Shown> FLUIDO = EnumProperty.create("fluido", HeatFluid.Shown.class);

	public static final Map<Direction, BooleanProperty> SIDES = Map.of(
		Direction.NORTH, NORTH, Direction.SOUTH, SOUTH, Direction.EAST, EAST,
		Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN
	);

	/** The casing: an eight-texel square tube down the middle of the block. */
	private static final VoxelShape CORE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);
	private static final Map<Direction, VoxelShape> ARMS = Map.of(
		Direction.NORTH, Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 4.0),
		Direction.SOUTH, Block.box(4.0, 4.0, 12.0, 12.0, 12.0, 16.0),
		Direction.WEST, Block.box(0.0, 4.0, 4.0, 4.0, 12.0, 12.0),
		Direction.EAST, Block.box(12.0, 4.0, 4.0, 16.0, 12.0, 12.0),
		Direction.DOWN, Block.box(4.0, 0.0, 4.0, 12.0, 4.0, 12.0),
		Direction.UP, Block.box(4.0, 12.0, 4.0, 12.0, 16.0, 12.0)
	);

	/** Ticks between a change beside a pipe and the pipe re-reading what its network carries. */
	private static final int REFRESH_DELAY = 2;

	public HeatPipeBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false)
			.setValue(WEST, false).setValue(UP, false).setValue(DOWN, false)
			.setValue(FLUIDO, HeatFluid.Shown.VACIO));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, FLUIDO);
	}

	/**
	 * What a heat pipe reaches for: another heat pipe, a boiler or depot, or something that takes heat.
	 * Never a metal pipe, a tank or a spout: the two lines never meet.
	 */
	public static boolean joins(BlockGetter level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		return block instanceof HeatPipeBlock || block instanceof BoilerBlock
			|| (block instanceof HeatConsumer consumer && consumer.takesHeat(state));
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
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction side, BlockPos neighbour, BlockState neighbourState, RandomSource random) {
		boolean joined = joins(level, neighbour);
		if (joined != state.getValue(SIDES.get(side))) {
			// Something that matters to the network came or went beside it: forget, and look again shortly.
			if (level instanceof Level world) {
				HeatSources.invalidate(world);
			}
			ticks.scheduleTick(pos, this, REFRESH_DELAY);
		}
		return state.setValue(SIDES.get(side), joined);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		if (!old.is(this)) {
			HeatSources.invalidate(level);
			level.scheduleTick(pos, this, REFRESH_DELAY);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		HeatSources.invalidate(level);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		HeatSources.refreshPipes(level, pos);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int mask = 0;
		for (Direction side : Direction.values()) {
			if (state.getValue(SIDES.get(side))) {
				mask |= 1 << side.ordinal();
			}
		}
		return SHAPES[mask];
	}

	/** Every combination of arms, worked out once: a shape is asked for far too often to be built each time. */
	private static final VoxelShape[] SHAPES = new VoxelShape[64];

	static {
		for (int mask = 0; mask < SHAPES.length; mask++) {
			VoxelShape shape = CORE;
			for (Direction side : Direction.values()) {
				if ((mask & (1 << side.ordinal())) != 0) {
					shape = Shapes.or(shape, ARMS.get(side));
				}
			}
			SHAPES[mask] = shape.optimize();
		}
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}
}
