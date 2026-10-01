package dev.forja.item;

import java.util.function.Consumer;

import dev.forja.forge.RepairKits;
import dev.forja.material.ForgeMaterial;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A repair kit of one metal (forge/RepairKits): put it in a crafting grid with a forged piece whose main part
 * is of that metal and the piece comes back with three hundred more uses.
 */
public class RepairKitItem extends Item {
	public final ForgeMaterial material;

	public RepairKitItem(ForgeMaterial material, Item.Properties properties) {
		super(properties);
		this.material = material;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		lines.accept(Component.translatable("tooltip.forja.kit_de_reparacion", RepairKits.AMOUNT, this.material.displayName())
			.withStyle(ChatFormatting.GRAY));
		lines.accept(Component.translatable("tooltip.forja.kit_de_reparacion.uso").withStyle(ChatFormatting.DARK_GRAY));
	}
}
