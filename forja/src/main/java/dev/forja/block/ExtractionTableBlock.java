package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.menu.ExtractionMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The extraction table: where one upgrade is taken off a piece, and kept in an orb if you brought one.
 * It holds nothing between visits — like the forge tables, whatever is on it goes back to whoever
 * walks away — so it is a plain block with a menu and no block entity. See {@link ExtractionMenu}.
 */
public class ExtractionTableBlock extends Block {
	public static final MapCodec<ExtractionTableBlock> CODEC = simpleCodec(ExtractionTableBlock::new);

	public ExtractionTableBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide()) {
			player.openMenu(state.getMenuProvider(level, pos));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return new SimpleMenuProvider(
			(containerId, inventory, player) -> new ExtractionMenu(containerId, inventory, ContainerLevelAccess.create(level, pos)),
			Component.translatable("container.forja.mesa_de_extraccion")
		);
	}
}
