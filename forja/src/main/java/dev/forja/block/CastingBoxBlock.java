package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.CastingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
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
 * Caja de moldeo: where a part stops being something you cut and becomes something you pour.
 *
 * <p>It does two jobs, and the first one costs you something. Put a finished part in it with refractory
 * steel and the steel is poured over the part: the part is destroyed and what comes out is a
 * <b>mould</b> of it. From then on that mould, fed from the tanks, casts the same part in any metal the
 * box can stand — which is what the tiers are for. A clay box will pour bronze all day and crack on
 * diamond; a damascus one takes anything the mod has.
 *
 * <p>So the line finishes here: ore into the crucible, molten metal into the tanks, tanks down a pipe
 * into this, and parts out of the bottom without a table in sight.
 */
public class CastingBoxBlock extends BaseEntityBlock {
	public static final MapCodec<CastingBoxBlock> CODEC =
		simpleCodec(properties -> new CastingBoxBlock(properties, Tier.BARRO));
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/**
	 * What a box is built of, and so the hardest metal it will hold without cracking.
	 *
	 * <p>The limit is read off the material's own durability, which the mod already knows, so a metal
	 * added tomorrow lands in the right tier by itself.
	 */
	public enum Tier {
		/** Clay and pewter: the everyday metals, up to bronze. */
		BARRO(320, 120),
		/** Refractory steel: everything a smith works with before the hard stuff. */
		ACERO(700, 80),
		/** Damascus over refractory steel: there is nothing it will not take. */
		DAMASCO(Integer.MAX_VALUE, 40);

		/** The highest material durability this box will pour. */
		public final int holds;
		/** Ticks per cast. */
		public final int cook;

		Tier(int holds, int cook) {
			this.holds = holds;
			this.cook = cook;
		}

		public String id() {
			return this == BARRO ? "caja_de_moldeo" : "caja_de_moldeo_de_" + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public final Tier tier;

	public CastingBoxBlock(Properties properties, Tier tier) {
		super(properties);
		this.tier = tier;
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
		return new CastingBoxBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null
			: createTickerHelper(type, dev.forja.block.entity.ModBlockEntities.CAJA, CastingBoxBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof CastingBoxBlockEntity box) {
			player.openMenu(box);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moved) {
		if (level.getBlockEntity(pos) instanceof CastingBoxBlockEntity box) {
			Containers.dropContents(level, pos, box);
		}
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
			pos.getX() + 0.5 + random.nextDouble() * 0.5 - 0.25, pos.getY() + 0.9,
			pos.getZ() + 0.5 + random.nextDouble() * 0.5 - 0.25, 0.0, 0.015, 0.0);
	}
}
