package dev.forja.block;

import java.util.ArrayList;
import java.util.List;

import dev.forja.entity.FallenSmith;
import dev.forja.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * La fragua apagada: the forge the fallen smith was working at, cold since the sky came down on it.
 * It is only found in his own fortress, and putting your bare hand on it with the whole offering in
 * your bag wakes him up. Everything it asks for is something you had to go somewhere to get.
 */
public class DeadForgeBlock extends Block {
	/** What waking him costs: the sky, his own steel, a star and a memory. */
	public record Offering(java.util.function.Supplier<Item> item, int count) {
	}

	public static final List<Offering> OFFERING = List.of(
		new Offering(() -> ModItems.HIERRO_ESTELAR, 8),
		new Offering(() -> ModItems.alloy("damasco"), 4),
		new Offering(() -> Items.NETHER_STAR, 1),
		new Offering(() -> Items.ECHO_SHARD, 1)
	);

	public DeadForgeBlock(Properties properties) {
		super(properties);
	}

	/** Whether this player is carrying the whole offering. */
	public static boolean hasOffering(Player player) {
		for (Offering offering : OFFERING) {
			if (count(player, offering) < offering.count()) {
				return false;
			}
		}
		return true;
	}

	private static int count(Player player, Offering offering) {
		int found = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(offering.item().get())) {
				found += stack.getCount();
			}
		}
		return found;
	}

	/** The offering, written out, so the block can say what it is missing. */
	public static List<Component> describe(Player player) {
		List<Component> lines = new ArrayList<>();
		for (Offering offering : OFFERING) {
			lines.add(Component.translatable(
				"gui.forja.fragua.pide", new ItemStack(offering.item().get()).getHoverName(), count(player, offering), offering.count()
			));
		}
		return lines;
	}

	/** Cold, but not out: a little smoke and the odd ember still come off the hearth. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(
				net.minecraft.core.particles.ParticleTypes.SMOKE,
				pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
				0.0, 0.02, 0.0
			);
		}
		if (random.nextInt(12) == 0) {
			level.addParticle(
				net.minecraft.core.particles.ParticleTypes.LAVA,
				pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0, 0.0, 0.0
			);
			level.playLocalSound(pos, net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS, 0.3F, 0.6F, false);
		}
		// And the ash of it, which is the mod's own and is what a forge that went out actually leaves.
		// Smoke says "still burning"; ash says "burned, once, a long time ago", which is the whole
		// story of every ruin this block turns up in.
		if (random.nextInt(5) == 0) {
			level.addParticle(dev.forja.registry.ModParticles.CENIZA,
				pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.0,
				pos.getZ() + 0.2 + random.nextDouble() * 0.6,
				(random.nextDouble() - 0.5) * 0.02, 0.012, (random.nextDouble() - 0.5) * 0.02);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer smith)) {
			return InteractionResult.SUCCESS;
		}
		if (!hasOffering(player)) {
			for (Component line : describe(player)) {
				smith.sendSystemMessage(line);
			}
			return InteractionResult.CONSUME;
		}
		if (!server.getEntitiesOfClass(FallenSmith.class, new net.minecraft.world.phys.AABB(pos).inflate(48.0)).isEmpty()) {
			smith.sendSystemMessage(Component.translatable("gui.forja.fragua.ya"));
			return InteractionResult.CONSUME;
		}
		for (Offering offering : OFFERING) {
			take(player, offering);
		}
		FallenSmith.summon(server, pos.above());
		smith.sendSystemMessage(Component.translatable("gui.forja.fragua.despierta"));
		return InteractionResult.SUCCESS;
	}

	private static void take(Player player, Offering offering) {
		int left = offering.count();
		for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(offering.item().get())) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}
	}
}
