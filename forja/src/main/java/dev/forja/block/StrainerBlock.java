package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.CastingTableBlockEntity;
import dev.forja.block.entity.StrainerBlockEntity;
import dev.forja.item.CastingFrameItem;
import dev.forja.item.CastingMouldItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Colador puesto: the strainer as a thing in the world rather than an item in a slot.
 *
 * <p>Andy, 2026-09-28: "¿se podría hacer que el líquido tenga que caer en la herramienta? pasando primero
 * por el colador antes de que llegue a la mesa con el molde". So the strainer is a grate on four short
 * legs, set down on top of a casting table, and the metal falling out of a spout overhead goes <b>through
 * it</b> on its way into the mould. It is what decides whether the pour is clean: a strainer that holds the
 * metal gives a clean casting, one that cannot take it breaks the moment the pour starts, and a table with
 * no strainer over it pours rough (see CastingTableBlockEntity#start).
 *
 * <p>It does not get in the way of anything else. A spout's stream passes through it as if it were air
 * (MeltPipeBlock#landing), it is not a container so no pipe reaches for it, and clicking it with a mould,
 * a frame or an empty hand is the same as clicking the table under it — which is where the player's cursor
 * lands when they look down at a table with a strainer on it.
 */
public class StrainerBlock extends BaseEntityBlock {
	public static final MapCodec<StrainerBlock> CODEC = simpleCodec(StrainerBlock::new);

	/** Where the grate lies: a plate eight pixels up, so the mould on the table shows underneath it. */
	public static final float GRATE_TOP = 10.0F / 16.0F;

	/** The whole of it, grate and legs, for the outline and for walking into. */
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(2.0, 8.0, 2.0, 14.0, 10.0, 14.0),
		Block.box(2.0, 0.0, 2.0, 4.0, 8.0, 4.0),
		Block.box(12.0, 0.0, 2.0, 14.0, 8.0, 4.0),
		Block.box(2.0, 0.0, 12.0, 4.0, 8.0, 14.0),
		Block.box(12.0, 0.0, 12.0, 14.0, 8.0, 14.0)
	);

	public StrainerBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StrainerBlockEntity(pos, state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	/** It stands on anything with a top to stand on, a casting table above all. */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return Block.canSupportCenter(level, pos.below(), Direction.UP)
			|| level.getBlockState(pos.below()).getBlock() instanceof CastingTableBlock;
	}

	/**
	 * Knocked off when what it stands on goes. Returning air here is how a torch comes off a wall: the
	 * level destroys the block with its drops, so the strainer lands on the floor as the same colador.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
		BlockPos pos, Direction side, BlockPos neighbour, BlockState neighbourState, net.minecraft.util.RandomSource random) {
		if (side == Direction.DOWN && !this.canSurvive(state, level, pos)) {
			return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, side, neighbour, neighbourState, random);
	}

	/**
	 * A mould, a frame or an empty hand on the strainer is meant for the table under it.
	 *
	 * <p>Looking down at a table with a strainer on it, the cursor is on the grate, not on the table; without
	 * this the only way to set a mould down would be to break the strainer first. Anything else passes, so a
	 * spout held against the grate is placed on top of it like on any other block.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
		net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (held.isEmpty()) {
			// An empty hand is answered by useWithoutItem, but only if this says so: a plain PASS here meant
			// the empty-handed click that should have taken the finished piece off the table did nothing.
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		boolean forTable = CastingMouldItem.partOf(held) != null || CastingFrameItem.typeOf(held) != null;
		if (!forTable || !(level.getBlockEntity(pos.below()) instanceof CastingTableBlockEntity table)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		return table.hand(player, held) ? InteractionResult.CONSUME : InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos.below()) instanceof CastingTableBlockEntity table)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		table.hand(player, ItemStack.EMPTY);
		return InteractionResult.CONSUME;
	}
}
