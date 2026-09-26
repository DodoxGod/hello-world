package dev.forja.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The anvil the fallen smith leaves behind.
 *
 * <p>It was a full cube with a warm top until now, which is the one shape an anvil is not. It is the
 * trophy of the hardest fight in the mod and it stands on its own in a workshop, so it is worth the
 * four boxes it takes to make it look like the thing it is: a foot, a waist, and a face wide enough
 * to work on, laid along whichever way you put it down.
 */
public class SmithAnvilBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<SmithAnvilBlock> CODEC = simpleCodec(SmithAnvilBlock::new);

	private static final VoxelShape FOOT = Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);
	/** Laid along Z, the way it is drawn: the face runs north to south. */
	private static final VoxelShape ALONG_Z = Shapes.or(FOOT,
		Block.box(4.0, 4.0, 3.0, 12.0, 5.0, 13.0),
		Block.box(6.0, 5.0, 4.0, 10.0, 10.0, 12.0),
		Block.box(3.0, 10.0, 0.0, 13.0, 16.0, 16.0));
	private static final VoxelShape ALONG_X = Shapes.or(FOOT,
		Block.box(3.0, 4.0, 4.0, 13.0, 5.0, 12.0),
		Block.box(4.0, 5.0, 6.0, 12.0, 10.0, 10.0),
		Block.box(0.0, 10.0, 3.0, 16.0, 16.0, 13.0));

	public SmithAnvilBlock(Properties properties) {
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

	/**
	 * Still warm.
	 *
	 * <p>This is the fallen smith's anvil, and it turns up in his barrow and in the ruined forges —
	 * places where the fire went out a long time ago and something of it did not. So: no flame, no
	 * smoke, just soot lifting off the face of it and, very occasionally, a spark from a blow nobody
	 * struck. Rare on purpose. It is scenery, and scenery that insists on itself stops being scenery.
	 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.02;
		double z = pos.getZ() + 0.5;
		if (random.nextInt(14) == 0) {
			level.addParticle(dev.forja.registry.ModParticles.CENIZA,
				x + random.nextDouble() * 0.7 - 0.35, y, z + random.nextDouble() * 0.7 - 0.35,
				(random.nextDouble() - 0.5) * 0.01, 0.01, (random.nextDouble() - 0.5) * 0.01);
		}
		if (random.nextInt(70) == 0) {
			level.addParticle(dev.forja.registry.ModParticles.CHISPA,
				x + random.nextDouble() * 0.4 - 0.2, y, z + random.nextDouble() * 0.4 - 0.2,
				(random.nextDouble() - 0.5) * 0.08, 0.1 + random.nextDouble() * 0.06, (random.nextDouble() - 0.5) * 0.08);
		}
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// Turned across the smith, the way a real one is: you stand at the side of an anvil, not at
		// the end of it.
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.X ? ALONG_X : ALONG_Z;
	}

	/**
	 * Clicking it says what it is doing, because otherwise nothing ever does.
	 *
	 * <p>The anvil has no screen and never will — its whole job is to stand near a forge table and
	 * count as the parts table and the saddlery at once. That is invisible: you set it down, nothing
	 * happens, and there is no way to learn whether it is close enough to the table to be working.
	 * So it answers when you ask it, and it says which of the two cases you are in.
	 */
	@Override
	protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
		BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
		if (level.isClientSide()) {
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		int range = dev.forja.menu.ForgeMenu.WORKSHOP_RANGE;
		int tables = 0;
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-range, -2, -range), pos.offset(range, 2, range))) {
			if (level.getBlockState(at).getBlock() instanceof ForgeTableBlock) {
				tables++;
			}
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer smith) {
			smith.sendOverlayMessage(tables > 0
				? net.minecraft.network.chat.Component.translatable("gui.forja.yunque.taller", tables)
				: net.minecraft.network.chat.Component.translatable("gui.forja.yunque.solo", range));
		}
		level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_LAND,
			net.minecraft.sounds.SoundSource.BLOCKS, 0.3F, 1.8F);
		return net.minecraft.world.InteractionResult.SUCCESS;
	}
}
