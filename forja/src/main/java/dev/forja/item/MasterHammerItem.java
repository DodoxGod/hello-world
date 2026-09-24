package dev.forja.item;

import dev.forja.forge.Technique;
import dev.forja.forge.Techniques;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * El martillo del maestro: what the Fallen Smith was holding. A technique is a choice for good, and this
 * is the one thing in the world that takes the choice back. Using it forgets every technique you took,
 * and the hammer is spent doing it.
 */
public class MasterHammerItem extends Item {
	public MasterHammerItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack hammer = player.getItemInHand(hand);
		if (Techniques.mask(player) == 0) {
			if (player instanceof ServerPlayer smith) {
				smith.sendSystemMessage(Component.translatable("gui.forja.martillo.nada").withColor(0xB0A890));
			}
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer smith && level instanceof ServerLevel server) {
			int forgotten = Integer.bitCount(Techniques.mask(smith));
			smith.setAttached(Techniques.LEARNED, 0);
			hammer.shrink(1);
			smith.sendSystemMessage(Component.translatable("gui.forja.martillo.olvidas", forgotten).withColor(0xF0C070));
			server.playSound(null, smith.getX(), smith.getY(), smith.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
			server.sendParticles(ParticleTypes.ENCHANT, smith.getX(), smith.getY(1.2), smith.getZ(), 60, 0.6, 0.8, 0.6, 0.4);
			dev.forja.ForjaAdvancements.award(smith, "martillo");
		}
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}

	/** How many choices it gives back, for the book. */
	public static int tiers() {
		return Technique.TIERS;
	}
}
