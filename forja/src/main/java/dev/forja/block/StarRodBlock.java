package dev.forja.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Pararrayos de estrellas: a lightning rod for the meteor shower (world/WorldEvents#METEORITOS).
 *
 * <p>Andy, 2026-09-29: a meteorite of the shower digs a crater of basalt and magma wherever it likes, a
 * house included. A rod within {@link #RADIUS} blocks of where one was going to land draws it onto itself
 * instead: the warning ring on the floor and the chat line move to the rod, and when it arrives the rod
 * takes it. No crater, and the star iron it carried is left at the rod's foot. Each one wears it
 * ({@link #DESGASTE}); the {@link #STRIKES}th breaks it, and it drops nothing then.
 *
 * <p>Broken by hand it keeps its wear (the loot table copies the state onto the item), so taking it down
 * and putting it back up is not a way of mending it.
 */
public class StarRodBlock extends Block {
	public static final MapCodec<StarRodBlock> CODEC = simpleCodec(StarRodBlock::new);

	/** How many meteorites it has taken: 0 new, up to STRIKES - 1. */
	public static final IntegerProperty DESGASTE = IntegerProperty.create("desgaste", 0, 3);

	/** How many meteorites one rod takes; the last one breaks it. */
	public static final int STRIKES = 4;

	/** How far, across the ground, a rod reaches for a meteorite: from where it would have landed to the rod. */
	public static final int RADIUS = 12;

	/** How far below and above the landing spot a rod may stand and still reach it: on a roof, down a slope. */
	public static final int BELOW = 6;
	public static final int ABOVE = 24;

	/** A copper foot, a brass collar, a steel shaft and the star glass at the top. */
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0),
		Block.box(6.0, 2.0, 6.0, 10.0, 3.0, 10.0),
		Block.box(7.0, 3.0, 7.0, 9.0, 12.0, 9.0),
		Block.box(6.0, 12.0, 6.0, 10.0, 16.0, 10.0)
	);

	public StarRodBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(DESGASTE, 0));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(DESGASTE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	/**
	 * The rod that takes a meteorite meant for {@code ground}, if one is near enough: the closest across the
	 * ground within {@link #RADIUS}, from {@link #BELOW} under the spot to {@link #ABOVE} over it. Null when
	 * there is none, and the meteorite lands where it was going to.
	 */
	public static @Nullable BlockPos attract(ServerLevel level, BlockPos ground) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-RADIUS, -BELOW, -RADIUS), ground.offset(RADIUS, ABOVE, RADIUS))) {
			double dx = pos.getX() - ground.getX();
			double dz = pos.getZ() - ground.getZ();
			double across = dx * dx + dz * dz;
			if (across > RADIUS * RADIUS || across >= bestDistance) {
				continue;
			}
			if (level.getBlockState(pos).getBlock() instanceof StarRodBlock) {
				best = pos.immutable();
				bestDistance = across;
			}
		}
		return best;
	}

	/**
	 * A meteorite arriving at {@code pos}: if the rod is still standing there it takes it, wears by one, or
	 * breaks on its last, and this says true. False when there is no rod there any more (somebody took it
	 * down while it was falling), and the meteorite digs its crater like any other.
	 */
	public static boolean takeStrike(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof StarRodBlock)) {
			return false;
		}
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 40, 0.3, 1.2, 0.3, 0.12);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, 0.4, 0.6, 0.4, 0.4);
		level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 3.0F, 1.4F);
		int wear = state.getValue(DESGASTE) + 1;
		if (wear >= STRIKES) {
			// Spent: the glass goes and the rest of it with it, with nothing left to pick up.
			level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3.0F, 0.5F);
			level.destroyBlock(pos, false);
		} else {
			level.setBlockAndUpdate(pos, state.setValue(DESGASTE, wear));
		}
		return true;
	}
}
