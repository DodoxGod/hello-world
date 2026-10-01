package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.world.StarYard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Estrella de vuelta: the star that falls on the arena when the Fallen Smith dies (docs/HERRERO_DIMENSION.md,
 * 3.9). A light that stands like a beacon; a right click on it takes you back where you came in.
 */
public class ReturnStarBlock extends Block {
	public static final MapCodec<ReturnStarBlock> CODEC = simpleCodec(ReturnStarBlock::new);
	private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 12.0, 12.0);

	public ReturnStarBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer traveller) {
			traveller.teleport(StarYard.departure(traveller, TeleportTransition.PLAY_PORTAL_SOUND));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// A column of light up out of it, the way a beacon's beam says "here" from across the plain.
		for (int i = 0; i < 3; i++) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2,
				pos.getY() + 0.8 + random.nextDouble() * 12.0, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2, 0.0, 0.08, 0.0);
		}
		level.addParticle(ParticleTypes.GLOW, pos.getX() + random.nextDouble(), pos.getY() + 0.5, pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
	}
}
