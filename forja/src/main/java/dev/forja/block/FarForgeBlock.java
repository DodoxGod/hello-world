package dev.forja.block;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import dev.forja.block.entity.FarForgeBlockEntity;
import dev.forja.forge.Alloys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The far forges (docs/ALEACIONES_NETHER_END.md): the soul forge of the Fragua caída in the Nether, and the void
 * forge of the End ruin. Each one makes its own alloys and nothing else does.
 *
 * <p>They are found cold. The right thing in the hand lights one (a blaze rod, an eye of ender), and lighting it
 * wakes everything that guards it. Lit, it is a furnace for one thing: the ingredients go into its hearth with a
 * right click, its own fuel goes in the same way, and a batch comes out every {@link FarForgeBlockEntity#BATCH_TICKS}
 * while there is enough of both. It only burns in its own dimension, and nothing breaks it: the forge is the ruin's.
 */
public class FarForgeBlock extends BaseEntityBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** Which far forge: what lights it, what feeds it, where it burns and who it wakes. */
	public enum Kind {
		ALMAS(Level.NETHER, () -> Items.BLAZE_ROD, () -> Items.BLAZE_POWDER, Alloys.Place.ALMAS, 2),
		VACIO(Level.END, () -> Items.ENDER_EYE, () -> Items.ENDER_PEARL, Alloys.Place.VACIO, 2);

		/** The only dimension it burns in. */
		public final ResourceKey<Level> dimension;
		/** What lights it, spent on lighting. */
		public final Supplier<Item> lighter;
		/** What it burns: one per batch. */
		public final Supplier<Item> fuel;
		/** Which alloys it makes. */
		public final Alloys.Place place;
		/** How many guards rise when it is lit. */
		public final int risen;

		Kind(ResourceKey<Level> dimension, Supplier<Item> lighter, Supplier<Item> fuel, Alloys.Place place, int risen) {
			this.dimension = dimension;
			this.lighter = lighter;
			this.fuel = fuel;
			this.place = place;
			this.risen = risen;
		}

		public String id() {
			return this.place.id();
		}

		/** The alloys this forge makes. */
		public List<Alloys.Recipe> recipes() {
			return Alloys.at(this.place);
		}

		/** The guard that rises when it is lit: an ember wisp at the soul forge, an empty suit at the void forge. */
		public EntityType<? extends Mob> guard() {
			return this == ALMAS ? dev.forja.registry.ModEntities.PAVESA : dev.forja.registry.ModEntities.CORAZA;
		}
	}

	/** How far lighting it carries: every monster this close turns on whoever lit it. */
	public static final double WAKE_RADIUS = 24.0;

	public final Kind kind;

	public FarForgeBlock(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
		this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return this.kind == Kind.ALMAS
			? simpleCodec(properties -> new FarForgeBlock(properties, Kind.ALMAS))
			: simpleCodec(properties -> new FarForgeBlock(properties, Kind.VACIO));
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
		return new FarForgeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null
			: createTickerHelper(type, dev.forja.block.entity.ModBlockEntities.FRAGUA_LEJANA, FarForgeBlockEntity::serverTick);
	}

	/** Whether it burns here: lit, and in its own dimension. */
	public boolean burns(Level level, BlockState state) {
		return state.getValue(LIT) && level.dimension() == this.kind.dimension;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (held.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!state.getValue(LIT)) {
			if (held.is(this.kind.lighter.get())) {
				held.consume(1, player);
				light((ServerLevel) level, pos, player);
			} else {
				player.sendSystemMessage(Component.translatable("gui.forja.fragua_lejana.fria." + this.kind.id(),
					new ItemStack(this.kind.lighter.get()).getHoverName()));
			}
			return InteractionResult.CONSUME;
		}
		if (level.getBlockEntity(pos) instanceof FarForgeBlockEntity forge) {
			forge.handIn(player, held);
		}
		return InteractionResult.CONSUME;
	}

	/** An empty hand is told what is in the hearth and what it is waiting for; crouching, it takes the hearth back. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!state.getValue(LIT)) {
			player.sendSystemMessage(Component.translatable("gui.forja.fragua_lejana.fria." + this.kind.id(),
				new ItemStack(this.kind.lighter.get()).getHoverName()));
			return InteractionResult.CONSUME;
		}
		if (level.getBlockEntity(pos) instanceof FarForgeBlockEntity forge) {
			if (player.isShiftKeyDown()) {
				forge.handOut(player);
			} else {
				forge.report(player);
			}
		}
		return InteractionResult.CONSUME;
	}

	/**
	 * Lights the forge and wakes its guards: every monster within {@link #WAKE_RADIUS} turns on whoever lit it, and
	 * {@link Kind#risen} more rise beside the altar. Public so a test or a command can light one without a player.
	 */
	public static void light(ServerLevel level, BlockPos pos, @Nullable Player who) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof FarForgeBlock forge) || state.getValue(LIT)) {
			return;
		}
		level.setBlockAndUpdate(pos, state.setValue(LIT, true));
		Kind kind = forge.kind;
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		if (kind == Kind.ALMAS) {
			level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.0F, 0.6F);
			level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.5F, 0.7F);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 60, 0.6, 0.8, 0.6, 0.05);
			level.sendParticles(dev.forja.registry.ModParticles.ALMA, x, y + 0.5, z, 20, 1.5, 1.0, 1.5, 0.02);
		} else {
			level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 0.6F);
			level.playSound(null, pos, SoundEvents.ENDERMAN_STARE, SoundSource.BLOCKS, 0.8F, 0.8F);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 80, 0.6, 0.8, 0.6, 0.1);
			level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z, 20, 1.5, 1.0, 1.5, 0.02);
		}
		// A creative builder is not left out: the monsters' own targeting lets go of anyone they cannot attack.
		Player target = who != null && !who.isSpectator() ? who : null;
		if (target != null) {
			for (Monster monster : level.getEntitiesOfClass(Monster.class, new AABB(pos).inflate(WAKE_RADIUS), Monster::isAlive)) {
				monster.setTarget(target);
			}
		}
		RandomSource random = level.getRandom();
		for (int i = 0; i < kind.risen; i++) {
			Mob guard = kind.guard().create(level, EntitySpawnReason.EVENT);
			if (guard == null) {
				continue;
			}
			BlockPos spot = standing(level, pos, random);
			guard.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			guard.setPersistenceRequired();
			if (target != null) {
				guard.setTarget(target);
			}
			level.addFreshEntity(guard);
			level.sendParticles(kind == Kind.ALMAS ? ParticleTypes.SOUL : ParticleTypes.PORTAL,
				spot.getX() + 0.5, spot.getY() + 0.8, spot.getZ() + 0.5, 16, 0.3, 0.6, 0.3, 0.05);
		}
		if (who != null) {
			who.sendSystemMessage(Component.translatable("gui.forja.fragua_lejana.enciende." + kind.id()).withColor(kind == Kind.ALMAS ? 0x5FD3E0 : 0xB98AE6));
		}
	}

	/** A spot beside the altar with room to stand, or the block over the forge if the ruin has none. */
	private static BlockPos standing(ServerLevel level, BlockPos forge, RandomSource random) {
		for (int attempt = 0; attempt < 16; attempt++) {
			BlockPos spot = forge.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
			if (level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir()
				&& !level.getBlockState(spot.below()).isAir()) {
				return spot;
			}
		}
		return forge.above();
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		if (!state.getValue(LIT)) {
			// Cold, but the fire it burned with is still in the stone.
			if (random.nextInt(4) == 0) {
				level.addParticle(this.kind == Kind.ALMAS ? ParticleTypes.SMOKE : ParticleTypes.PORTAL,
					x + (random.nextDouble() - 0.5) * 0.6, y + 0.02, z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.02, 0.0);
			}
			return;
		}
		if (this.kind == Kind.ALMAS) {
			for (int i = 0; i < 2; i++) {
				level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
					x + (random.nextDouble() - 0.5) * 0.6, y + 0.05, z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.03, 0.0);
			}
			if (random.nextInt(4) == 0) {
				level.addParticle(dev.forja.registry.ModParticles.ALMA, x, y + 0.3, z,
					(random.nextDouble() - 0.5) * 0.02, 0.03, (random.nextDouble() - 0.5) * 0.02);
			}
			if (random.nextInt(24) == 0) {
				level.playLocalSound(pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.3F, 0.6F, false);
				level.playLocalSound(pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.5F, 0.5F, false);
			}
		} else {
			for (int i = 0; i < 3; i++) {
				level.addParticle(ParticleTypes.REVERSE_PORTAL,
					x + (random.nextDouble() - 0.5) * 0.7, y + 0.05, z + (random.nextDouble() - 0.5) * 0.7, 0.0, 0.04, 0.0);
			}
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.END_ROD, x, y + 0.2, z, (random.nextDouble() - 0.5) * 0.03, 0.04,
					(random.nextDouble() - 0.5) * 0.03);
			}
			if (random.nextInt(30) == 0) {
				level.playLocalSound(pos, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.2F, 1.4F, false);
			}
		}
	}
}
