package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ménsula estelar: one of the four brackets of the star portal's frame (docs/HERRERO_DIMENSION.md, 1.3).
 *
 * <p>The frame is a ring of 5 by 5 round a 3 by 3 hole, with a bracket in the middle of each side, facing
 * the hole ({@link #FACING}). Each bracket takes one oricalco pearl ({@link #PERLA}), set with a right
 * click, the way an eye goes into an end portal frame. With all four set the hole fills with the star
 * portal, and it stays lit for good: the pearls do not come out and the brackets cannot be broken.
 */
public class StarBracketBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<StarBracketBlock> CODEC = simpleCodec(StarBracketBlock::new);
	public static final BooleanProperty PERLA = BooleanProperty.create("perla");

	/** How far a bracket stands from the middle of its frame. */
	public static final int REACH = 2;

	private static final VoxelShape BASE = Block.box(0.0, 0.0, 0.0, 16.0, 13.0, 16.0);
	private static final VoxelShape WITH_PEARL = Shapes.or(BASE, Block.box(4.0, 13.0, 4.0, 12.0, 16.0, 12.0));

	public StarBracketBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PERLA, false));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PERLA);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(PERLA) ? WITH_PEARL : BASE;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(ModItems.PERLA_DE_ORICALCO) || state.getValue(PERLA)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		setPearl(server, pos);
		stack.consume(1, player);
		int set = pearlsIn(server, pos);
		if (set < 4) {
			player.sendOverlayMessage(Component.translatable("gui.forja.portal.faltan", 4 - set));
		}
		return InteractionResult.SUCCESS;
	}

	/** Sets a pearl in the bracket at this position, and lights the frame if it was the last. */
	public static void setPearl(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.MENSULA_ESTELAR) || state.getValue(PERLA)) {
			return;
		}
		level.setBlockAndUpdate(pos, state.setValue(PERLA, true));
		level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 0.8F);
		level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3F, 1.6F);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
			14, 0.2, 0.1, 0.2, 0.08);
		BlockPos centre = pos.relative(state.getValue(FACING), REACH);
		if (complete(level, centre)) {
			light(level, centre);
		}
	}

	/** How many pearls the frame this bracket belongs to has, this one included. */
	public static int pearlsIn(Level level, BlockPos bracket) {
		BlockState state = level.getBlockState(bracket);
		if (!state.is(ModBlocks.MENSULA_ESTELAR)) {
			return 0;
		}
		BlockPos centre = bracket.relative(state.getValue(FACING), REACH);
		int set = 0;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockState other = level.getBlockState(centre.relative(side, REACH));
			if (other.is(ModBlocks.MENSULA_ESTELAR) && other.getValue(PERLA)) {
				set++;
			}
		}
		return set;
	}

	/** Whether the frame round this centre has its four brackets, each facing in, each with its pearl. */
	public static boolean complete(Level level, BlockPos centre) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockState bracket = level.getBlockState(centre.relative(side, REACH));
			if (!bracket.is(ModBlocks.MENSULA_ESTELAR) || !bracket.getValue(PERLA) || bracket.getValue(FACING) != side.getOpposite()) {
				return false;
			}
		}
		return true;
	}

	/** Fills the 3 by 3 hole with the star portal. */
	public static void light(ServerLevel level, BlockPos centre) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos at = centre.offset(dx, 0, dz);
				if (level.getBlockState(at).canBeReplaced() || level.getBlockState(at).isAir()) {
					level.setBlock(at, ModBlocks.PORTAL_ESTELAR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		level.playSound(null, centre, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 0.9F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, centre.getX() + 0.5, centre.getY() + 0.8,
			centre.getZ() + 0.5, 60, 1.2, 0.3, 1.2, 0.05);
	}

	/**
	 * Builds an empty frame (no pearls) round this centre: the four brackets facing in, brick at the
	 * corners and between, the hole cleared. What an old fragua apagada turns into.
	 */
	public static void buildFrame(Level level, BlockPos centre) {
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				BlockPos at = centre.offset(dx, 0, dz);
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
				} else if (dx == 0 || dz == 0) {
					Direction side = dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
					level.setBlockAndUpdate(at, ModBlocks.MENSULA_ESTELAR.defaultBlockState().setValue(FACING, side.getOpposite()));
				} else {
					level.setBlockAndUpdate(at, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				}
			}
		}
	}
}
