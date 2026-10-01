package dev.forja.item;

import dev.forja.menu.ForgeMenu;
import dev.forja.menu.Station;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Yunque portatil: the parts table folded into something you can carry. It cuts parts and takes gear
 * apart anywhere, but it wears out doing it, and it is not the star: nothing is forged on it, and it
 * never counts towards a whole workshop.
 */
public class PortableAnvilItem extends Item {
	public PortableAnvilItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer smith) {
			smith.openMenu(new SimpleMenuProvider(
				(containerId, inventory, opener) -> new ForgeMenu(Station.PIEZAS, containerId, inventory),
				Station.PIEZAS.title()
			));
			stack.hurtAndBreak(1, smith, hand.asEquipmentSlot());
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5F, 1.4F);
		}
		return InteractionResult.SUCCESS;
	}
}
