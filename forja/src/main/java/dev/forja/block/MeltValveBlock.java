package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.MeltNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Llave de paso: a length of channel with a sluice gate in it and a lever on top.
 *
 * <p>Andy, 2026-09-28: "llave de paso". Open, it is a pipe like any other; closed, the gate drops into the
 * channel and the network is cut in two right there — the metal on one side cannot reach anything on the
 * other (MeltNetwork treats a closed valve as a pipe that is not there, see MeltPipeBlock#passes). It is
 * turned with a right click, and a redstone signal holds it shut whatever the lever says, so a comparator
 * reading a deposit can close the line into it when it is full.
 *
 * <p>It has to read at a glance from across a foundry, so the two states do not share a silhouette: open,
 * the gate hangs high in its frame with the metal running under it and the lever thrown back; closed, the
 * red-painted gate sits down in the channel and the lever is thrown forward.
 */
public class MeltValveBlock extends MeltPipeBlock {
	public static final MapCodec<MeltValveBlock> CODEC = simpleCodec(MeltValveBlock::new);

	/** What the lever says. */
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	/** Whether a redstone signal is holding it shut. */
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	/** Which way the channel runs through it: the gate stands across it. */
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

	/** The frame the gate hangs in, across a channel running east-west, and across one running north-south. */
	private static final VoxelShape FRAME_X = Block.box(7.0, 6.0, 3.0, 9.0, 15.0, 13.0);
	private static final VoxelShape FRAME_Z = Block.box(3.0, 6.0, 7.0, 13.0, 15.0, 9.0);
	private static final VoxelShape FLOOR = Block.box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0);

	public MeltValveBlock(Properties properties) {
		// A steel fitting: it costs the metal what a steel pipe does.
		super(properties, Grade.ACERO);
		this.registerDefaultState(this.defaultBlockState()
			.setValue(OPEN, true).setValue(POWERED, false).setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(OPEN, POWERED, AXIS);
	}

	/** Whether metal goes through: the lever open and no signal holding it shut. */
	public static boolean isOpen(BlockState state) {
		return state.getValue(OPEN) && !state.getValue(POWERED);
	}

	/**
	 * The gate goes across the channel it is set into: along the pipes it joins if they run one way, and
	 * otherwise along the way the smith is looking, which is the way a line is laid.
	 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		boolean eastWest = state.getValue(EAST) || state.getValue(WEST);
		boolean northSouth = state.getValue(NORTH) || state.getValue(SOUTH);
		Direction.Axis axis = eastWest && !northSouth ? Direction.Axis.X
			: northSouth && !eastWest ? Direction.Axis.Z
			: context.getHorizontalDirection().getAxis();
		return state.setValue(AXIS, axis).setValue(OPEN, true)
			.setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.or(FLOOR, state.getValue(AXIS) == Direction.Axis.X ? FRAME_X : FRAME_Z);
	}

	/**
	 * With a block in hand a click is building (the next length of pipe goes against it); with anything
	 * else, or nothing, it throws the lever.
	 */
	@Override
	protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack held, BlockState state, Level level, BlockPos pos,
		Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		return held.getItem() instanceof net.minecraft.world.item.BlockItem ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/** A right click throws the lever. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		BlockState turned = state.cycle(OPEN);
		level.setBlock(pos, turned, Block.UPDATE_ALL);
		MeltNetwork.changed(level, pos);
		level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, turned.getValue(OPEN) ? 0.6F : 0.5F);
		level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5F, turned.getValue(OPEN) ? 1.2F : 0.8F);
		String said = turned.getValue(OPEN) && turned.getValue(POWERED) ? "gui.forja.llave.forzada"
			: turned.getValue(OPEN) ? "gui.forja.llave.abierta" : "gui.forja.llave.cerrada";
		player.sendOverlayMessage(Component.translatable(said));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** A redstone signal shuts it; taking the signal away gives it back to its lever. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour,
		net.minecraft.world.level.redstone.@Nullable Orientation orientation, boolean moved) {
		if (level.isClientSide()) {
			return;
		}
		boolean powered = level.hasNeighborSignal(pos);
		if (powered != state.getValue(POWERED)) {
			level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
			MeltNetwork.changed(level, pos);
			if (state.getValue(OPEN)) {
				level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5F, powered ? 0.8F : 1.2F);
			}
		}
	}
}
