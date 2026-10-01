package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.ForjaAdvancements;
import dev.forja.GuideBooks;
import dev.forja.item.GuideBookItem;
import dev.forja.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Atril del Herrero: a lectern with book VII open on it, El Cementerio entre Estrellas. It appears beside the star
 * portal in the Bastion's Deep Forge the moment the portal is lit (StarBracketBlock.light), and anyone can read the
 * book there: a click opens it. The book itself stays on the lectern: copies are crafted, like every book of the guide.
 */
public class SmithLecternBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<SmithLecternBlock> CODEC = simpleCodec(SmithLecternBlock::new);
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0);

	public SmithLecternBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			GuideBookItem.opener.accept(GuideBooks.Book.CEMENTERIO);
		} else {
			ForjaAdvancements.award(player, "guia");
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Puts the lectern beside a star portal just lit, outside its frame, on the floor and facing the portal: the first
	 * of the places round the frame where there is room. Nothing if there is none.
	 */
	public static void placeBeside(ServerLevel level, BlockPos centre) {
		int[][] around = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}};
		for (int dy : new int[] {0, -1, 1}) {
			for (int[] offset : around) {
				BlockPos at = centre.offset(offset[0], dy, offset[1]);
				if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
					&& level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
					Direction facing = Math.abs(offset[0]) >= Math.abs(offset[1])
						? (offset[0] > 0 ? Direction.WEST : Direction.EAST)
						: (offset[1] > 0 ? Direction.NORTH : Direction.SOUTH);
					// The lectern's front, where the reader stands, looks away from the portal: it faces the one reading.
					level.setBlockAndUpdate(at, ModBlocks.ATRIL_DEL_HERRERO.defaultBlockState().setValue(FACING, facing.getOpposite()));
					return;
				}
			}
		}
	}
}
