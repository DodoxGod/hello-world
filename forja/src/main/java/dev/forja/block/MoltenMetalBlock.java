package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

/**
 * Metal fundido: the molten metal of the Cementerio entre Estrellas (docs/HERRERO_DIMENSION.md, 2.6).
 *
 * <p>Scenery and nothing else. It lies in the rivers' beds and pours off the plateau's edge into the void
 * ({@link #CAE}: a falling column rather than a still pool), it lights the plain round it, and it cannot
 * be broken or taken. It is not a fluid: no bucket picks it up and it never spreads, so a river is
 * exactly as wide as the generator made it. Walls keep anybody from falling in by accident; somebody who
 * climbs in anyway burns as in lava and wades, and can walk out.
 */
public class MoltenMetalBlock extends Block {
	public static final MapCodec<MoltenMetalBlock> CODEC = simpleCodec(MoltenMetalBlock::new);

	/** A column of a fall rather than the surface of a river. */
	public static final BooleanProperty CAE = BooleanProperty.create("cae");

	/** The colour its drops fall with, read by the gota particle as red, green and blue. */
	private static final double RED = 1.0;
	private static final double GREEN = 0.72;
	private static final double BLUE = 0.28;

	public MoltenMetalBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CAE, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CAE);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
		// Heavy going, and it burns like lava does.
		entity.makeStuckInBlock(state, new Vec3(0.5, 0.6, 0.5));
		if (entity instanceof LivingEntity) {
			effects.apply(InsideBlockEffectType.LAVA_IGNITE);
			entity.lavaHurt();
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		boolean falls = state.getValue(CAE);
		boolean open = level.getBlockState(pos.above()).isAir();
		if (falls) {
			// Only the lowest block of a fall throws anything: the beads it loses to the void.
			if (level.getBlockState(pos.below()).isAir() && random.nextInt(2) == 0) {
				level.addParticle(ModParticles.GOTA, pos.getX() + random.nextDouble(), pos.getY() - 0.05,
					pos.getZ() + random.nextDouble(), RED, GREEN, BLUE);
			}
			return;
		}
		if (!open) {
			return;
		}
		if (random.nextInt(40) == 0) {
			level.addParticle(ParticleTypes.LAVA, pos.getX() + random.nextDouble(), pos.getY() + 1.0, pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(ModParticles.CHISPA, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(),
				(random.nextDouble() - 0.5) * 0.04, 0.06 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.04);
		}
		if (random.nextInt(120) == 0) {
			level.playLocalSound(pos, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.25F, 0.7F + random.nextFloat() * 0.2F, false);
		}
	}
}
