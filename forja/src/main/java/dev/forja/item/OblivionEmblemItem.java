package dev.forja.item;

import dev.forja.clase.ClassNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Emblema del olvido (docs/CLASES.md): what changing class costs. Used, it opens the class choice in its
 * "change" form; the emblem is spent only when a class is actually chosen there (clase/ClassNetwork), so
 * closing the screen costs nothing. Choosing the class you already have empties its tree.
 */
public class OblivionEmblemItem extends Item {
	public OblivionEmblemItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer server) {
			ClassNetwork.openChange(server);
		}
		return InteractionResult.SUCCESS;
	}
}
