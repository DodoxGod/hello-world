package dev.forja.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Caño de colada: the end of a run, and the only block in the foundry that lets go of the metal.
 *
 * <p>Every other piece of the line keeps what it is carrying. A channel holds it in, a tank holds it
 * up, a crucible holds it over a fire. This one has a hole in its floor: what reaches it falls
 * straight down through open air, up to {@link MeltPipeBlock#DROP} blocks, into whatever is standing
 * underneath — a tank, a casting box, a table, another channel.
 *
 * <p>Which makes it two things at once. It is the only way to feed something on a <b>lower floor</b>
 * without stacking blocks down to it, and it is the thing that <b>shows you the line is live</b>: the
 * stream is drawn only when the metal has somewhere to land, so a spout pouring into nothing simply
 * does not pour. You can read a whole foundry off that one detail from across the room.
 *
 * <p>The fall is not free. Open air is the coldest thing the metal ever crosses, so a long drop
 * delivers it nearly set — {@link MeltPipeBlock#FALL_BLEED} a block, which is worse than the worst
 * pipe there is.
 */
public class MeltSpoutBlock extends MeltPipeBlock {
	public static final MapCodec<MeltSpoutBlock> CODEC = simpleCodec(MeltSpoutBlock::new);

	/** The block is floor with a hole in it, so what you walk on is the ring around the mouth. */
	private static final VoxelShape RING = net.minecraft.world.phys.shapes.Shapes.join(
		Block.box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0),
		Block.box(6.0, 0.0, 6.0, 10.0, 6.0, 10.0),
		net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST
	);

	public MeltSpoutBlock(Properties properties) {
		super(properties, Grade.BRONCE);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return RING;
	}

	@Override
	public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
		if (landing(level, pos) == null) {
			return;
		}
		// Beads of the metal itself coming off the stream, in the metal's own colour. This was vanilla's
		// lava pop, which jumps UP out of whatever it is on — under a spout, that is metal leaping back
		// into the hole it fell out of.
		if (random.nextInt(2) != 0) {
			return;
		}
		int colour = 0xFF8A2A;
		if (level.getBlockEntity(pos) instanceof dev.forja.block.entity.MeltFlowBlockEntity flow && flow.metal() != null) {
			dev.forja.material.ForgeMaterial material = dev.forja.material.ForgeMaterial.fromInput(new net.minecraft.world.item.ItemStack(flow.metal()));
			if (material != null) {
				colour = material.moltenColor();
			}
		}
		level.addParticle(dev.forja.registry.ModParticles.GOTA,
			pos.getX() + 0.42 + random.nextDouble() * 0.16, pos.getY() - 0.05,
			pos.getZ() + 0.42 + random.nextDouble() * 0.16,
			((colour >> 16) & 0xFF) / 255.0, ((colour >> 8) & 0xFF) / 255.0, (colour & 0xFF) / 255.0);
	}
}
