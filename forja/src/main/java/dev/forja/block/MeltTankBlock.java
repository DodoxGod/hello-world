package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.MeltTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Cuba de colada: a glass and iron tank for molten metal, which is only worth anything in a bank.
 *
 * <p>One of these holds a wall's worth of one metal and shows the level through the glass. Set two of
 * them touching and they are one tank; set a crucible against the bank and it pours into it instead of
 * into its own slot, and takes its ingredients back out of it twice as fast, because metal that is
 * already molten does not have to be melted again. That last part is the reason to build a foundry
 * rather than a row of pots: the tanks are not denser than a chest and were never meant to be.
 */
public class MeltTankBlock extends BaseEntityBlock {
	public static final MapCodec<MeltTankBlock> CODEC = simpleCodec(MeltTankBlock::new);

	/** How full the glass looks, in quarters. The block entity holds the real number. */
	public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);

	public MeltTankBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 0));
	}

	/** Which quarter of the glass a given amount lights up. Anything at all shows as at least one. */
	public static int levelFor(int amount, int capacity) {
		if (amount <= 0) {
			return 0;
		}
		return Math.max(1, Math.min(4, Math.round(4.0F * amount / capacity)));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MeltTankBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null
			: createTickerHelper(type, dev.forja.block.entity.ModBlockEntities.CUBA, MeltTankBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof MeltTankBlockEntity tank) {
			return tank.hand(player, held) ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof MeltTankBlockEntity tank) {
			tank.hand(player, ItemStack.EMPTY);
		}
		return InteractionResult.CONSUME;
	}

	/**
	 * Breaking one spills what was in that tank, not what was in the bank.
	 *
	 * <p>The rest of the wall keeps its metal, which is what makes taking a tank out of the middle of a
	 * bank a cheap mistake instead of an expensive one.
	 */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moved) {
		if (level.getBlockEntity(pos) instanceof MeltTankBlockEntity tank && tank.metal() != null && tank.amount() > 0) {
			ItemStack spilled = new ItemStack(tank.metal(), tank.amount());
			while (!spilled.isEmpty()) {
				int moving = Math.min(spilled.getCount(), spilled.getMaxStackSize());
				net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					spilled.copyWithCount(moving));
				spilled.shrink(moving);
			}
		}
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}

	// Glass: the faces between two tanks are not drawn, so a bank reads as one body of metal.
	@Override
	protected boolean skipRendering(BlockState state, BlockState neighbour, net.minecraft.core.Direction side) {
		return neighbour.getBlock() instanceof MeltTankBlock || super.skipRendering(state, neighbour, side);
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
		return 1.0F;
	}
}
