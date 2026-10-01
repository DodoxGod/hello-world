package dev.forja.item;

import dev.forja.part.ForgedParts;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * What a forged thing is made of, as something a tooltip can draw rather than write.
 *
 * <p>The tooltip used to spend a line of text on every part — "Pickaxe head: diamond", "Handle: stone",
 * "Binding: gold" — and a five-part greatsword spent five before it got to a single number. The parts
 * are items with icons of their own, already in the colour of their material; this hands them to the
 * client to be drawn in a row under the name. See {@code client/PartsStripTooltip}.
 *
 * <p>The piece itself comes along so the same strip can draw what it carries: its load of upgrades
 * against what its potential lets it hold (forge/Potential, client/LoadBar).
 */
public record PartsStrip(ForgedParts parts, ItemStack stack) implements TooltipComponent {
}
