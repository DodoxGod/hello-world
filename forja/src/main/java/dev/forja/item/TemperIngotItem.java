package dev.forja.item;

import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Lingote de temple: a bar poured in the crucible that has not decided what metal it is yet.
 *
 * <p>Right-click it onto damaged forged gear in any inventory slot and it mends it — and it mends it
 * <b>in the gear's own metal</b>, because that is what a blank bar is for. So what one bar is worth
 * depends entirely on what it is mending: a quarter of what the head's material is worth on its own.
 * A wooden head gets the little a wooden head is worth; a heart of the forge gets back six hundred
 * points from the same bar.
 *
 * <p>That is the whole design. It replaces the old repair kit, which mended a flat fifteen percent of
 * anything and therefore made good materials feel <em>worse</em> to own: the better the gear, the more
 * of its life one kit gave back. This is the other way round on purpose.
 */
public class TemperIngotItem extends Item {
	/** How much of the head material's own durability one bar is worth. */
	public static final int SHARE = 4;
	/** And a floor, so mending a wooden handle is not an insult. */
	public static final int MINIMUM = 25;

	public TemperIngotItem(Item.Properties properties) {
		super(properties);
	}

	/** What one bar gives back to this piece of gear, or 0 if it is not forged gear at all. */
	public static int repairAmount(ItemStack gear) {
		ForgedParts parts = gear.get(ModComponents.PARTS);
		if (parts == null) {
			return 0;
		}
		ForgeMaterial head = parts.primary();
		return Math.max(MINIMUM, head.durability / SHARE);
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack bar, Slot slot, ClickAction action, Player player) {
		ItemStack gear = slot.getItem();
		if (action != ClickAction.SECONDARY || !gear.has(ModComponents.PARTS) || !gear.isDamaged() || !slot.allowModification(player)) {
			return false;
		}
		gear.setDamageValue(Math.max(0, gear.getDamageValue() - repairAmount(gear)));
		slot.setChanged();
		bar.shrink(1);
		// The click is also predicted on the client, so push the real stacks back to it.
		if (player.containerMenu != null) {
			player.containerMenu.broadcastChanges();
		}
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
			SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
		return true;
	}
}
