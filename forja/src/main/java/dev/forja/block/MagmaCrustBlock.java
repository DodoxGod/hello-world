package dev.forja.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Costra de magma (docs/ALEACIONES_NETHER_END.md): what lava cools into under the feet of someone wearing
 * magmasteel (the Volcánico trait). It holds while anyone stands on it and goes back to lava a few seconds after,
 * the way frosted ice goes back to water; it drops nothing and is never an item.
 */
public class MagmaCrustBlock extends Block {
	/** How long a crust lasts once nobody is on it, at the least and on top of that at random. */
	public static final int HOLDS = 80;
	public static final int HOLDS_SPREAD = 40;
	/** How soon it looks again while someone is standing on it. */
	private static final int RECHECK = 20;

	public MagmaCrustBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		if (!old.is(this)) {
			level.scheduleTick(pos, this, HOLDS + level.getRandom().nextInt(HOLDS_SPREAD));
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos.above()).inflate(0.3, 0.0, 0.3), LivingEntity::isAlive).isEmpty()) {
			level.scheduleTick(pos, this, RECHECK);
			return;
		}
		melt(level, pos);
	}

	/** Back to lava, with the hiss of it. */
	public static void melt(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, Blocks.LAVA.defaultBlockState());
		level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 0.6F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.01);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
		}
	}
}
