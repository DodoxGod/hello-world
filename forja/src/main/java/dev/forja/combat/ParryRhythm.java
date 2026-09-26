package dev.forja.combat;

import java.util.Map;
import java.util.WeakHashMap;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Timing over mashing: a shield raised again too soon after the last raise gets no parry window, so
 * tapping the shield over and over stops being a way to live inside the window. A parry that lands
 * clears the penalty, so chaining parries against a combo is still rewarded.
 */
public final class ParryRhythm {
	private static final Map<LivingEntity, long[]> RAISES = new WeakHashMap<>();

	private ParryRhythm() {
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide() && player.getItemInHand(hand).has(DataComponents.BLOCKS_ATTACKS)) {
				onRaise(player, level.getGameTime());
			}
			return InteractionResult.PASS;
		});
	}

	public static void onRaise(Player player, long now) {
		long[] state = RAISES.computeIfAbsent(player, p -> new long[] {Long.MIN_VALUE / 2, 0});
		state[1] = now - state[0] < CombatConfig.get().parrySpamTicks ? 1 : 0;
		state[0] = now;
	}

	/** Whether the shield now up was raised too soon after the last one to parry with. */
	public static boolean rushed(LivingEntity entity) {
		if (!CombatConfig.get().enabled) return false;
		long[] state = RAISES.get(entity);
		return state != null && state[1] == 1;
	}

	/** A parry landed: the next raise is free, whenever it comes. */
	public static void landed(LivingEntity entity) {
		long[] state = RAISES.get(entity);
		if (state != null) {
			state[0] = Long.MIN_VALUE / 2;
			state[1] = 0;
		}
	}
}
