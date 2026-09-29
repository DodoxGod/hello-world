package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.BoilerBlockEntity;
import dev.forja.forge.HeatFluid;
import dev.forja.forge.HeatSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The two vessels of the heat line: the <b>caldera</b> (boiler), which boils what it is fed into steam,
 * blaze blood, forge breath or ice brine, and the <b>depósito de calor</b> (heat depot), which holds lava
 * poured in by the bucket. Either one hands its fluid to every heat pipe it touches, and to a consumer it
 * is built right against. See {@link HeatFluid} for what goes in and what comes out.
 *
 * <p>Fed by hand (a right click with what it takes) or by hopper (in from the top and the sides, the empty
 * buckets out of the bottom), and read by a comparator: the fuller it is, the stronger the signal.
 */
public class BoilerBlock extends BaseEntityBlock {
	public static final MapCodec<BoilerBlock> CODEC = simpleCodec(properties -> new BoilerBlock(properties, HeatFluid.Vessel.CALDERA));

	/** What it holds, for the colour in its window. */
	public static final EnumProperty<HeatFluid.Shown> FLUIDO = HeatPipeBlock.FLUIDO;
	/** How full the window looks: 0 empty, 4 full. */
	public static final IntegerProperty LEVEL = IntegerProperty.create("nivel", 0, 4);
	/** Boiling something right now (the fire in the boiler's mouth). */
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public final HeatFluid.Vessel vessel;

	public BoilerBlock(Properties properties, HeatFluid.Vessel vessel) {
		super(properties);
		this.vessel = vessel;
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(FLUIDO, HeatFluid.Shown.VACIO).setValue(LEVEL, 0).setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FLUIDO, LEVEL, LIT);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BoilerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null
			: createTickerHelper(type, dev.forja.block.entity.ModBlockEntities.CALDERA, BoilerBlockEntity::serverTick);
	}

	/** Something it takes goes in; anything else (a pipe, a block) is placed as usual. */
	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (HeatFluid.yield(held, this.vessel) == null) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		return level.getBlockEntity(pos) instanceof BoilerBlockEntity vessel && vessel.handIn(player, held)
			? InteractionResult.CONSUME : InteractionResult.PASS;
	}

	/** An empty hand takes out the empty buckets, or is told what is inside and what it is waiting for. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof BoilerBlockEntity vessel) {
			vessel.handOut(player);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		if (!old.is(this)) {
			HeatSources.invalidate(level);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		HeatSources.invalidate(level);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof BoilerBlockEntity vessel ? vessel.signal() : 0;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(LIT) && random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.12,
				pos.getZ() - 0.02, 0.0, 0.01, 0.0);
		}
		HeatFluid fluid = state.getValue(FLUIDO).fluid;
		if (fluid == null || state.getValue(LEVEL) == 0 || random.nextInt(3) != 0) {
			return;
		}
		// What is inside shows over the lid: steam puffs, a hot shimmer of sparks, a cold mist off the brine.
		double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
		double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
		switch (fluid) {
			case VAPOR -> level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 1.02, z, 0.0, 0.04, 0.0);
			case SALMUERA_HELADA -> level.addParticle(ParticleTypes.SNOWFLAKE, x, pos.getY() + 1.02, z, 0.0, 0.01, 0.0);
			case LAVA -> level.addParticle(ParticleTypes.LAVA, x, pos.getY() + 1.0, z, 0.0, 0.0, 0.0);
			case SANGRE_DE_BLAZE -> level.addParticle(ParticleTypes.SMALL_FLAME, x, pos.getY() + 1.02, z, 0.0, 0.02, 0.0);
			case ALIENTO_DE_FORJA -> level.addParticle(ParticleTypes.END_ROD, x, pos.getY() + 1.02, z, 0.0, 0.03, 0.0);
		}
	}
}
