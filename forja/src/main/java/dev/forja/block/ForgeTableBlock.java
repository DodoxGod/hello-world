package dev.forja.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.forja.menu.ForgeMenu;
import dev.forja.menu.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
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
 * The parts table and the forge table. Like the crafting table they keep nothing: whatever is left
 * inside goes back to the player on close.
 */
public class ForgeTableBlock extends Block {
	public static final MapCodec<ForgeTableBlock> CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(Station.CODEC.fieldOf("station").forGetter(block -> block.station), propertiesCodec()).apply(i, ForgeTableBlock::new)
	);
	/** Which of the three tables this block is; read by the menu, and by the Jade tooltip. */
	public final Station station;

	public ForgeTableBlock(Station station, BlockBehaviour.Properties properties) {
		super(properties);
		this.station = station;
	}

	@Override
	public MapCodec<? extends ForgeTableBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide()) {
			player.openMenu(state.getMenuProvider(level, pos));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * The hearth, breathing.
	 *
	 * <p>The central block of the whole mod did not move at all: a crafting table that happened to be
	 * hot. The crucible next to it smokes, the tank runs, the spout pours — and the thing the mod is
	 * named after just sat there. Now it draws like a banked fire, which is what it is between jobs.
	 *
	 * <p>The big forge gets more of everything, because a player who has built one has earned a block
	 * that looks like the upgrade it was.
	 */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		boolean greater = this.station == Station.FORJA_MAYOR;
		if (this.station != Station.FORJA && !greater) {
			// The parts table and the saddlery are cold work: no fire in either of them.
			return;
		}
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		// Smoke, always, drifting off the top.
		if (random.nextInt(greater ? 2 : 3) == 0) {
			level.addParticle(ParticleTypes.SMOKE,
				x + random.nextDouble() * 0.6 - 0.3, y, z + random.nextDouble() * 0.6 - 0.3,
				0.0, 0.015 + random.nextDouble() * 0.02, 0.0);
		}
		// The coals underneath, seen through the grate.
		if (random.nextInt(greater ? 3 : 6) == 0) {
			level.addParticle(ParticleTypes.FLAME,
				x + random.nextDouble() * 0.4 - 0.2, y - 0.08, z + random.nextDouble() * 0.4 - 0.2,
				0.0, 0.01, 0.0);
		}
		// And every so often a spark comes off it, which is the mod's own and says whose forge it is.
		if (random.nextInt(greater ? 8 : 16) == 0) {
			level.addParticle(dev.forja.registry.ModParticles.CHISPA,
				x + random.nextDouble() * 0.5 - 0.25, y - 0.02, z + random.nextDouble() * 0.5 - 0.25,
				(random.nextDouble() - 0.5) * 0.06, 0.12 + random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.06);
		}
		// A working forge is loud as well as bright, but quietly and not often.
		if (greater && random.nextInt(90) == 0) {
			level.playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,
				net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 0.7F + random.nextFloat() * 0.3F, false);
		}
	}

	@Override
	protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return new SimpleMenuProvider(
			(containerId, inventory, player) -> new ForgeMenu(this.station, containerId, inventory, ContainerLevelAccess.create(level, pos)), this.station.title()
		);
	}
}
