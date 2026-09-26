package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.CastingTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Mesas de colada: dark stone tables that pour a whole tool at once.
 *
 * <p>The casting box fills a mould and gives back a part. These fill a <b>frame</b> and give back the
 * finished thing. You set the frame down on the table, the reachable tanks drop in exactly the metal
 * that tool is worth — not a drop more — and what you pick up is a pickaxe, not a pick head.
 *
 * <p>All three do the same work. They differ in one thing only, and it is the thing dark stone is good
 * at: <b>holding heat</b>. A table that is not being warmed bleeds heat every second, and the better
 * the stone the slower it goes. A cold table will not start a pour at all, and a pour that was started
 * on the last of the heat comes out <b>rough</b> — the metal set in the frame before it was done. The
 * better stone also has the steadier hand: a good pour has a chance of coming out <b>perfect</b>, the
 * same +5% a smith gets for stopping the hammer dead centre, and the chance is what you are really
 * paying for when you build the third one.
 */
public class CastingTableBlock extends BaseEntityBlock {
	public static final MapCodec<CastingTableBlock> CODEC =
		simpleCodec(properties -> new CastingTableBlock(properties, Tier.LOSA));

	/** Set while there is metal in the frame, which is what the model and the particles read. */
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** What a table is cut from, and therefore how long its heat lasts. */
	public enum Tier {
		/** Polished deepslate and iron: a cold stone that works, and asks for a fire close by. */
		LOSA(4, 0.10F),
		/** Blackstone and gold, off the fallen smith's own floor: it remembers the fire longer. */
		BRASA(2, 0.25F),
		/** Basalt and obsidian, with something in the seams: it barely lets go of the heat at all. */
		ALMAS(1, 0.50F);

		/** Heat lost every second with nothing warming it. */
		public final int cools;
		/** The chance a finished pour comes out perfect, worth +5% on every stat. */
		public final float luck;

		Tier(int cools, float luck) {
			this.cools = cools;
			this.luck = luck;
		}

		public String id() {
			return "mesa_de_" + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public final Tier tier;

	public CastingTableBlock(Properties properties, Tier tier) {
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
		return new CastingTableBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null
			: createTickerHelper(type, dev.forja.block.entity.ModBlockEntities.MESA_DE_COLADA, CastingTableBlockEntity::serverTick);
	}

	/**
	 * There is no screen. You put the frame down on the table and you pick the tool up off it, which is
	 * the whole interface and the reason the frame and the melt are drawn where you can see them.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
		net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		return level.getBlockEntity(pos) instanceof CastingTableBlockEntity table && table.hand(player, held)
			? InteractionResult.CONSUME
			: InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof CastingTableBlockEntity table) {
			table.hand(player, ItemStack.EMPTY);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moved) {
		if (level.getBlockEntity(pos) instanceof CastingTableBlockEntity table) {
			Containers.dropContents(level, pos, table);
		}
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		// The pour smokes where it lands, and spits now and then, like the crucible does.
		level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
			pos.getX() + 0.35 + random.nextDouble() * 0.3, pos.getY() + 1.05,
			pos.getZ() + 0.35 + random.nextDouble() * 0.3, 0.0, 0.02, 0.0);
		// And it glows the colour of the metal that is in it. A casting table full of gold and one full
		// of iron looked identical while they cooled, which is the one moment you would want to tell.
		if (level.getBlockEntity(pos) instanceof dev.forja.block.entity.CastingTableBlockEntity table) {
			int colour = table.metalColour();
			if (colour >= 0 && random.nextInt(2) == 0) {
				level.addParticle(new net.minecraft.core.particles.DustParticleOptions(colour, 1.0F),
					pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.01, 0.0);
			}
			if (colour >= 0 && random.nextInt(10) == 0) {
				level.addParticle(dev.forja.registry.ModParticles.CHISPA,
					pos.getX() + 0.4 + random.nextDouble() * 0.2, pos.getY() + 1.02,
					pos.getZ() + 0.4 + random.nextDouble() * 0.2,
					(random.nextDouble() - 0.5) * 0.05, 0.1, (random.nextDouble() - 0.5) * 0.05);
			}
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA,
				pos.getX() + 0.4 + random.nextDouble() * 0.2, pos.getY() + 1.02,
				pos.getZ() + 0.4 + random.nextDouble() * 0.2, 0.0, 0.0, 0.0);
		}
	}
}
