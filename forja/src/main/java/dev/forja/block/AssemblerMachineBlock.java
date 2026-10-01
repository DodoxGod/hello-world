package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.AssemblerMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * La montadora: a steel frame round a forge star, with a piston where the smith's arm would be.
 *
 * <p>It assembles what the forge table assembles, from the same parts by the same rule, and does nothing
 * else a table does (block/entity/AssemblerMachineBlockEntity). Right-click opens it; hoppers feed it
 * parts from the top and the sides and take the finished piece from underneath; a comparator reads
 * whether it is working and whether something is waiting in it. It lights up while it works.
 */
public class AssemblerMachineBlock extends BaseEntityBlock implements dev.forja.forge.HeatConsumer {
	public static final MapCodec<AssemblerMachineBlock> CODEC = simpleCodec(AssemblerMachineBlock::new);

	/** Set while it is assembling: the model glows and the sparks fly. */
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public AssemblerMachineBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AssemblerMachineBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null
			: createTickerHelper(type, dev.forja.block.entity.ModBlockEntities.MONTADORA, AssemblerMachineBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof AssemblerMachineBlockEntity machine) {
			player.openMenu(machine);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	/** 15 with a finished piece waiting, 1 to 14 as one is assembled, 0 idle. */
	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof AssemblerMachineBlockEntity machine ? machine.signal() : 0;
	}

	// What it holds is dropped by the block entity (preRemoveSideEffects), for the reason the crucible's is.

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		// The piston comes down on the star: sparks off the top, and the smoke of hot metal.
		if (random.nextInt(3) == 0) {
			level.addParticle(dev.forja.registry.ModParticles.CHISPA,
				pos.getX() + 0.4 + random.nextDouble() * 0.2, pos.getY() + 1.02, pos.getZ() + 0.4 + random.nextDouble() * 0.2,
				(random.nextDouble() - 0.5) * 0.08, 0.12, (random.nextDouble() - 0.5) * 0.08);
		}
		level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
			pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02, 0.0);
	}
}
