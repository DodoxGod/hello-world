package dev.forja.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Arma clavada: a weapon driven point first into the ash, the grave of a smith who fell with the Fallen
 * Smith (docs/HERRERO_DIMENSION.md, 2.5). Thousands of them stand on the plain.
 *
 * <p>Nothing to walk into: a field of three and a half thousand things that stop you would be a maze, not
 * a graveyard. The model is the weapon's own sprite stood up and half buried, in {@link #INCLINACION} one
 * of three leans. Broken, it leaves nothing: what is left of these is rust.
 */
public class GraveWeaponBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<GraveWeaponBlock> CODEC = simpleCodec(GraveWeaponBlock::new);

	/** The six kinds of weapon in the ground. */
	public enum Weapon implements StringRepresentable {
		ESPADA, ESPADA_NEGRA, HACHA, TRIDENTE, MAZA, PICO;

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Weapon> ARMA = EnumProperty.create("arma", Weapon.class);
	/** 0 straight up, 1 leaning one way, 2 the other. */
	public static final IntegerProperty INCLINACION = IntegerProperty.create("inclinacion", 0, 2);

	private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 14.0, 12.0);

	public GraveWeaponBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(FACING, Direction.NORTH).setValue(ARMA, Weapon.ESPADA).setValue(INCLINACION, 0));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ARMA, INCLINACION);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** It stands in the ground: with nothing solid under it, it has nothing to stand in. */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	/**
	 * Dig the ash out from under a grave and the weapon goes with it (Andy, 2026-09-29: it used to hang in
	 * the air). It breaks like a torch whose wall is gone, and like a grave broken by hand it leaves nothing.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return true;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
