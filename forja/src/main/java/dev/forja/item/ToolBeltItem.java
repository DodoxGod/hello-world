package dev.forja.item;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.ForgeType;
import dev.forja.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cinturon de herramientas: it holds up to four digging tools and puts the right one in your hand as
 * you start on a block. It is deliberately not free: only picks, axes, shovels and hoes go in, every
 * swap wears the belt by one and it will not do it again for half a second, and it never swaps while
 * something is in the middle of hitting you. Carry it for the walking, not for the fighting.
 */
public class ToolBeltItem extends Item {
	/** How many tools the belt holds. */
	public static final int SLOTS = 4;

	/** Ticks between two swaps, so it cannot be used as a swing-speed trick. */
	public static final int SWAP_COOLDOWN = 10;

	/** Ticks after taking or dealing a hit in which the belt stays shut. */
	public static final int COMBAT_LOCK = 100;

	public ToolBeltItem(Item.Properties properties) {
		super(properties);
	}

	/** Whether this is a tool the belt will take at all. */
	public static boolean fits(ItemStack stack) {
		ForgeType type = stack.getItem() instanceof ForgedItems.Forged forged ? forged.forgeType() : null;
		if (type != null) {
			return type == ForgeType.PICO || type == ForgeType.HACHA || type == ForgeType.PALA
				|| type == ForgeType.AZADA || type == ForgeType.PICAHACHA;
		}
		return stack.is(net.minecraft.tags.ItemTags.PICKAXES) || stack.is(net.minecraft.tags.ItemTags.AXES)
			|| stack.is(net.minecraft.tags.ItemTags.SHOVELS) || stack.is(net.minecraft.tags.ItemTags.HOES);
	}

	public static List<ItemStack> tools(ItemStack belt) {
		List<ItemStack> tools = new ArrayList<>();
		belt.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().forEach(tools::add);
		return tools;
	}

	private static void store(ItemStack belt, List<ItemStack> tools) {
		belt.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(tools));
	}

	/** Right-click a tool onto the belt to hang it there, or onto an empty belt slot to take it back. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack belt, ItemStack other, Slot slot, ClickAction action, Player player, net.minecraft.world.entity.SlotAccess access) {
		if (action != ClickAction.SECONDARY) {
			return false;
		}
		List<ItemStack> tools = tools(belt);
		if (other.isEmpty()) {
			if (tools.isEmpty()) {
				return false;
			}
			// Nothing in hand: the last tool hung on the belt comes off.
			ItemStack taken = tools.removeLast();
			store(belt, tools);
			access.set(taken);
			player.playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.4F);
			return true;
		}
		if (!fits(other) || tools.size() >= SLOTS) {
			return false;
		}
		tools.add(other.copyWithCount(1));
		store(belt, tools);
		access.set(ItemStack.EMPTY);
		player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.6F, 1.2F);
		return true;
	}

	/**
	 * Puts the right tool in the main hand for this block, if the belt has one and nothing is stopping
	 * it. The tool that was in your hand goes on the belt in its place.
	 */
	public static boolean swapFor(ServerPlayer player, BlockState state) {
		ItemStack belt = find(player);
		if (belt.isEmpty() || belt.isBroken()) {
			return false;
		}
		Long lock = belt.get(ModComponents.CINTURON);
		long now = player.level().getGameTime();
		if (lock != null && now < lock) {
			return false;
		}
		if (now - player.getLastHurtByMobTimestamp() < COMBAT_LOCK && player.getLastHurtByMob() != null) {
			return false;
		}
		ItemStack held = player.getMainHandItem();
		if (held.isCorrectToolForDrops(state) && held.getDestroySpeed(state) > 1.0F) {
			return false;
		}
		List<ItemStack> tools = tools(belt);
		int best = -1;
		float bestSpeed = Math.max(1.0F, held.getDestroySpeed(state));
		for (int i = 0; i < tools.size(); i++) {
			float speed = tools.get(i).getDestroySpeed(state);
			if (speed > bestSpeed && (tools.get(i).isCorrectToolForDrops(state) || !state.requiresCorrectToolForDrops())) {
				best = i;
				bestSpeed = speed;
			}
		}
		if (best < 0) {
			return false;
		}
		ItemStack tool = tools.remove(best);
		if (!held.isEmpty() && fits(held)) {
			tools.add(held);
		} else if (!held.isEmpty()) {
			// Whatever you were holding is not a digging tool, so it goes to the bag, not to the belt.
			if (!player.getInventory().add(held)) {
				player.drop(held, false);
			}
		}
		store(belt, tools);
		belt.set(ModComponents.CINTURON, now + SWAP_COOLDOWN);
		belt.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.5F, 1.6F);
		return true;
	}

	/** The belt the player is carrying, if any. */
	public static ItemStack find(Player player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).getItem() instanceof ToolBeltItem) {
				return inventory.getItem(slot);
			}
		}
		return ItemStack.EMPTY;
	}
}
