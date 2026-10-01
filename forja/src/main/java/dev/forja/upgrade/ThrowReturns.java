package dev.forja.upgrade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import dev.forja.entity.ThrownHead;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Where a thrown weapon goes when it cannot go back to the hand that threw it.
 *
 * <p>A thrown weapon is the <b>only</b> copy of the item: the hand was emptied when it left. So when
 * its flight ends it must end up in exactly one place. Back in the hand or the inventory if the thrower
 * is alive and has room; otherwise it waits here, kept on the overworld under the thrower's id, and is
 * handed over as soon as they are alive, online and have a free slot. That covers a full inventory, a
 * thrower who died, left, or changed dimension mid-flight, and a weapon whose chunk unloaded under it.
 */
public final class ThrowReturns {
	/** What is waiting for whom: thrower id to the weapons it is owed. Saved with the world. */
	public static final AttachmentType<Map<String, List<ItemStack>>> HELD = AttachmentRegistry.create(Forja.id("armas_en_espera"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, ItemStack.CODEC.listOf())));

	/** How often waiting weapons try to get home, and how often the thrower is reminded to make room. */
	private static final int DELIVERY_EVERY = 10;
	private static final int REMINDER_EVERY = 200;

	private ThrowReturns() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % DELIVERY_EVERY == 0) {
				deliverAll(server);
			}
		});
		// Someone leaving mid-throw: whatever is in the air is theirs, so it comes off the world now
		// rather than being lost when the chunk it flies over unloads behind them.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			for (ServerLevel level : server.getAllLevels()) {
				for (ThrownHead flying : level.getEntities(ModEntities.THROWN_HEAD, head -> id.equals(head.ownerId()))) {
					flying.recall();
				}
			}
		});
	}

	/** Whether a player can take an item into their inventory right now. */
	private static boolean receives(@Nullable ServerPlayer player) {
		return player != null && player.isAlive() && !player.isRemoved();
	}

	/**
	 * Gives a thrown weapon back to its thrower, or keeps it for them. Never drops it and never copies
	 * it: {@code stack} is the weapon itself, and the caller must not hold on to it.
	 *
	 * @param owner the thrower as the projectile last saw them, used when they are not on the player list
	 *              (a fake player in a test); the list wins when both are there
	 * @param hand  the hand it left, refilled first when it is still empty
	 */
	public static void giveBack(MinecraftServer server, @Nullable ServerPlayer owner, UUID ownerId, ItemStack stack, @Nullable InteractionHand hand) {
		if (stack.isEmpty()) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
		if (player == null) {
			player = owner;
		}
		if (receives(player)) {
			if (hand != null && player.getItemInHand(hand).isEmpty()) {
				player.setItemInHand(hand, stack);
				return;
			}
			if (player.getInventory().add(stack) && stack.isEmpty()) {
				return;
			}
		}
		hold(server, ownerId, stack);
		if (receives(player)) {
			remind(player, stack);
		}
	}

	/** Keeps a weapon for its thrower until it can be handed over. */
	public static void hold(MinecraftServer server, UUID ownerId, ItemStack stack) {
		ServerLevel overworld = server.overworld();
		Map<String, List<ItemStack>> held = new HashMap<>(overworld.getAttachedOrElse(HELD, Map.of()));
		List<ItemStack> mine = new ArrayList<>(held.getOrDefault(ownerId.toString(), List.of()));
		mine.add(stack);
		held.put(ownerId.toString(), List.copyOf(mine));
		overworld.setAttached(HELD, Map.copyOf(held));
	}

	/** What is being kept for this thrower, for tests and the curious. */
	public static List<ItemStack> heldFor(MinecraftServer server, UUID ownerId) {
		return server.overworld().getAttachedOrElse(HELD, Map.<String, List<ItemStack>>of()).getOrDefault(ownerId.toString(), List.of());
	}

	/** Hands this player whatever is being kept for them that fits. Returns how many weapons got home. */
	public static int deliver(MinecraftServer server, ServerPlayer player) {
		if (!receives(player)) {
			return 0;
		}
		ServerLevel overworld = server.overworld();
		Map<String, List<ItemStack>> held = overworld.getAttached(HELD);
		String key = player.getUUID().toString();
		if (held == null || !held.containsKey(key)) {
			return 0;
		}
		List<ItemStack> left = new ArrayList<>();
		int delivered = 0;
		for (ItemStack waiting : held.get(key)) {
			ItemStack stack = waiting.copy();
			if (player.getInventory().add(stack) && stack.isEmpty()) {
				delivered++;
			} else {
				left.add(stack);
			}
		}
		if (delivered == 0) {
			return 0;
		}
		Map<String, List<ItemStack>> updated = new HashMap<>(held);
		if (left.isEmpty()) {
			updated.remove(key);
		} else {
			updated.put(key, List.copyOf(left));
		}
		if (updated.isEmpty()) {
			overworld.removeAttached(HELD);
		} else {
			overworld.setAttached(HELD, Map.copyOf(updated));
		}
		return delivered;
	}

	private static void deliverAll(MinecraftServer server) {
		Map<String, List<ItemStack>> held = server.overworld().getAttached(HELD);
		if (held == null || held.isEmpty()) {
			return;
		}
		for (String key : List.copyOf(held.keySet())) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException badKey) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (!receives(player)) {
				continue;
			}
			deliver(server, player);
			List<ItemStack> still = heldFor(server, id);
			if (!still.isEmpty() && server.getTickCount() % REMINDER_EVERY == 0) {
				remind(player, still.getFirst());
			}
		}
	}

	/** The weapon came back to a full inventory: say so, or it looks lost. */
	private static void remind(ServerPlayer player, ItemStack waiting) {
		player.sendOverlayMessage(Component.translatable("gui.forja.lanzada.espera", waiting.getHoverName()));
	}
}
