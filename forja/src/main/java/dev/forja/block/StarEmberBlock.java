package dev.forja.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Brasa estelar: one of the Fallen Smith's forge fires in the Reforjado estelar (docs/HERRERO_DIMENSION.md,
 * 3.7). While one burns he cannot be hurt. Nothing a player does puts it out: only the molten metal of a
 * tipped brasero, running over it, does. It cannot be broken and leaves nothing.
 */
public class StarEmberBlock extends Block {
	public static final MapCodec<StarEmberBlock> CODEC = simpleCodec(StarEmberBlock::new);
	private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 14.0, 13.0);

	public StarEmberBlock(Properties properties) {
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
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5,
			pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.04, 0.0);
		if (random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
				(random.nextDouble() - 0.5) * 0.02, 0.05, (random.nextDouble() - 0.5) * 0.02);
		}
		if (random.nextInt(3) == 0) {
			level.addParticle(dev.forja.registry.ModParticles.BRASA, pos.getX() + random.nextDouble(), pos.getY() + 0.7,
				pos.getZ() + random.nextDouble(), 0.0, 0.03, 0.0);
		}
	}
}
