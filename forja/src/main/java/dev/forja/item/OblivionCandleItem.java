package dev.forja.item;

import dev.forja.clase.ClassNetwork;
import dev.forja.clase.ClassProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Vela del olvido (docs/ARBOLES.md, "Reiniciar"): the cheap way back from a node or two. Used, it opens the
 * tree in its "forget" form, where up to {@link #POINTS} points of nodes at the edge of what you have can be
 * picked; it is spent only when they are actually taken off (clase/ClassNetwork.forget), so closing the screen
 * costs nothing. The whole tree, or another class, is still the Medallón del olvido's.
 */
public class OblivionCandleItem extends Item {
	/** Points of nodes one candle takes off. */
	public static final int POINTS = 4;

	public OblivionCandleItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer server) {
			if (ClassProgress.clazz(server) == null || ClassProgress.data(server).nodes().isEmpty()) {
				server.sendOverlayMessage(Component.translatable("gui.forja.vela.nada"));
				return InteractionResult.FAIL;
			}
			ClassNetwork.openForget(server);
		}
		return InteractionResult.SUCCESS;
	}
}
