package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.PartsCabinetBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The parts cabinet: a chest with a filter, so a workshop's drawers stay a workshop's drawers. */
public class PartsCabinetBlock extends BaseEntityBlock {
	public static final MapCodec<PartsCabinetBlock> CODEC = simpleCodec(PartsCabinetBlock::new);

	public PartsCabinetBlock(Properties properties) {
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
		return new PartsCabinetBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof PartsCabinetBlockEntity cabinet) {
			player.openMenu(cabinet);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moved) {
		// Whatever was inside falls out, the same as a chest that is broken.
		if (level.getBlockEntity(pos) instanceof PartsCabinetBlockEntity cabinet) {
			Containers.dropContents(level, pos, cabinet);
			level.updateNeighbourForOutputSignal(pos, this);
		}
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}
}
