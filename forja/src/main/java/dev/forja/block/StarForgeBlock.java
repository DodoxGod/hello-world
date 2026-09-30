package dev.forja.block;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import dev.forja.registry.ModItems;
import dev.forja.world.StarFight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Fragua fría estelar: the cold forge that is left in the middle of the arena when the Fallen Smith dies
 * (docs/HERRERO_DIMENSION.md, 3.10). Revived with a right click and everything below in the bag, it calls
 * him down out of the sky again. Nothing in the price is something only he gives or something that runs
 * out (Andy): lose it all and you can still make it again.
 */
public class StarForgeBlock extends Block {
	public static final MapCodec<StarForgeBlock> CODEC = simpleCodec(StarForgeBlock::new);

	public record Cost(Supplier<Item> item, int count) {
	}

	public static final List<Cost> COST = List.of(
		new Cost(() -> ModItems.PERLA_DE_ORICALCO, 1),
		new Cost(() -> ModItems.ORICALCO, 3),
		new Cost(() -> Items.NETHER_STAR, 1),
		new Cost(() -> ModItems.HIERRO_ESTELAR, 16)
	);

	public StarForgeBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	public static int count(Player player, Item item) {
		return player.getInventory().countItem(item);
	}

	public static boolean pays(Player player) {
		return player.isCreative() || COST.stream().allMatch(cost -> count(player, cost.item().get()) >= cost.count());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer smith)) {
			return InteractionResult.SUCCESS;
		}
		if (!pays(player)) {
			for (Cost cost : COST) {
				smith.sendSystemMessage(Component.translatable("gui.forja.fragua.pide",
					new ItemStack(cost.item().get()).getHoverName(), count(player, cost.item().get()), cost.count()));
			}
			return InteractionResult.CONSUME;
		}
		if (!player.isCreative()) {
			for (Cost cost : COST) {
				take(player, cost.item().get(), cost.count());
			}
		}
		StarFight.rematch(server, smith);
		return InteractionResult.SUCCESS;
	}

	private static void take(Player player, Item item, int count) {
		int left = count;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
				pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02, 0.0);
		}
		if (random.nextInt(4) == 0) {
			level.addParticle(dev.forja.registry.ModParticles.CENIZA, pos.getX() + random.nextDouble(), pos.getY() + 1.0,
				pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
		}
	}
}
