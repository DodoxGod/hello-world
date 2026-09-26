package dev.forja.block;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.forge.Alloys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The crucible: where a smith actually melts things.
 *
 * <p>The forge table alloys by holding ingredients over whatever heat happens to be under it, which is
 * fine for one bar and hopeless for a hundred. The crucible is the other half of that: it has no screen
 * at all, it takes what a hopper puts in and gives back what a hopper takes out, and the only thing that
 * decides what it can do is which of the three you built. A clay one will make bronze all day and will
 * never touch netherite; an obsidian one melts anything, pays back a broken tool whole, and throws in a
 * bar for the trouble.
 */
public class CrucibleBlock extends BaseEntityBlock {
	public static final MapCodec<CrucibleBlock> CODEC = simpleCodec(properties -> new CrucibleBlock(properties, Tier.BARRO));
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** What a crucible is built out of, and therefore what it can do. */
	public enum Tier {
		/** Clay and brick: warm enough for bronze, and that is the whole of it. */
		BARRO(Alloys.Heat.TEMPLADA, 8, 200, 0.5F, 0),
		/** Iron over the clay: hot enough for steel, and it gives most of a tool back. */
		HIERRO(Alloys.Heat.CALIENTE, 16, 120, 0.75F, 0),
		/**
		 * Obsidian and scrap: it melts anything, pays a tool back whole, and adds a bar of its own.
		 *
		 * <p>And it is the only thing in the mod that burns at white heat, which is the whole reason to
		 * build one: three alloys exist that no table over lava will ever give you.
		 */
		OBSIDIANA(Alloys.Heat.FORJA_BLANCA, 32, 60, 1.0F, 1);

		public final Alloys.Heat heat;
		/** How many items it will hold across its two input slots. */
		public final int capacity;
		/** Ticks per pour. */
		public final int cook;
		/** The share of a forged part that comes back out as its material. */
		public final float recovery;
		/** Extra bars on every alloy, which is what makes the dear one worth building. */
		public final int bonus;

		Tier(Alloys.Heat heat, int capacity, int cook, float recovery, int bonus) {
			this.heat = heat;
			this.capacity = capacity;
			this.cook = cook;
			this.recovery = recovery;
			this.bonus = bonus;
		}

		public String id() {
			return "crisol_de_" + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public final Tier tier;

	public CrucibleBlock(Properties properties, Tier tier) {
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
		return new CrucibleBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, dev.forja.block.entity.ModBlockEntities.CRISOL, CrucibleBlockEntity::serverTick);
	}

	/**
	 * Right-click opens it. The hopper convention underneath is what a workshop uses at scale, and this
	 * is what a smith uses to see what the pot is actually doing.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible) {
			player.openMenu(crucible);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean moved) {
		if (level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible) {
			Containers.dropContents(level, pos, crucible);
		}
		super.affectNeighborsAfterRemoval(state, level, pos, moved);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		// A working crucible is meant to be findable across a room full of them.
		level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + random.nextDouble() * 0.4 - 0.2,
			pos.getY() + 1.0, pos.getZ() + 0.5 + random.nextDouble() * 0.4 - 0.2, 0.0, 0.02, 0.0);
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + random.nextDouble() * 0.3 - 0.15,
				pos.getY() + 0.95, pos.getZ() + 0.5 + random.nextDouble() * 0.3 - 0.15, 0.0, 0.01, 0.0);
		}
		// And the metal itself, showing over the rim. A crucible of gold and a crucible of iron burned
		// with exactly the same flame, which is the one thing you would want to tell across a workshop
		// with four of them going.
		if (level.getBlockEntity(pos) instanceof dev.forja.block.entity.CrucibleBlockEntity crucible) {
			int colour = crucible.meltColour();
			if (colour >= 0 && random.nextInt(2) == 0) {
				level.addParticle(new net.minecraft.core.particles.DustParticleOptions(colour, 1.0F),
					pos.getX() + 0.35 + random.nextDouble() * 0.3, pos.getY() + 0.92,
					pos.getZ() + 0.35 + random.nextDouble() * 0.3, 0.0, 0.02, 0.0);
			}
		}
	}
}
