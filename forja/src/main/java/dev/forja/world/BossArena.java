package dev.forja.world;

import dev.forja.entity.FallenSmith;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * While the Fallen Smith is up, his forge is his: nobody breaks or builds within reach of him. A fight
 * won by walling him in or tunnelling out of the rotunda was not a fight (Andy, 2026-09-29). Creative
 * players are left alone, and so is everything once he is dead.
 */
public final class BossArena {
	/** How far from the boss the ground is his. The rotunda is 25 across; this covers it and its doors. */
	public static final double RADIUS = 32.0;

	private BossArena() {
	}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (locked(level, player)) {
				refuse(player);
				return false;
			}
			return true;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if ((held.getItem() instanceof BlockItem || held.getItem() instanceof BucketItem) && locked(level, player)) {
				refuse(player);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	/** Whether a live Fallen Smith is close enough that this player's building is off. */
	public static boolean locked(Level level, Player player) {
		if (level.isClientSide() || player.isCreative() || player.isSpectator()) {
			return false;
		}
		return !level.getEntitiesOfClass(FallenSmith.class, new AABB(player.blockPosition()).inflate(RADIUS),
			FallenSmith::isAlive).isEmpty();
	}

	private static void refuse(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(Component.translatable("gui.forja.arena_bloqueada"));
		}
	}
}
