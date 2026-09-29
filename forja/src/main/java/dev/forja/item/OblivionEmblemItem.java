package dev.forja.item;

import dev.forja.clase.ClassNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * What changing class costs (docs/CLASES.md): the Medallón del olvido, forged at the star from parts
 * (forge/Relic), and the old crafted Emblema del olvido, which has no recipe any more but still works. Used,
 * it opens the class choice in its "change" form; it is spent only when a class is actually chosen there
 * (clase/ClassNetwork), so closing the screen costs nothing. Choosing the class you already have empties its
 * tree and keeps the level; any other class starts again at level 1.
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
