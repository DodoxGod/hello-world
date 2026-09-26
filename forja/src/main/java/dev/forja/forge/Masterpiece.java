package dev.forja.forge;

import dev.forja.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Obra maestra: not something you make, something the star notices. A piece that came out of a perfect
 * press, was worn to Maestria ten, carries a gift and is signed by the smith holding it has had
 * everything a smith can give it, and from then on it says so and is worth a little more.
 */
public final class Masterpiece {
	/** What being a masterpiece adds to every stat, on top of the perfect forge. */
	public static final float BONUS = 0.03F;

	private Masterpiece() {
	}

	public static boolean is(ItemStack stack) {
		return stack.getOrDefault(ModComponents.OBRA_MAESTRA, false);
	}

	/** Whether the piece has everything: a clean birth, a full life, a gift and its maker's name. */
	public static boolean qualifies(ItemStack stack, Player smith) {
		return stack.has(ModComponents.PARTS)
			&& Quality.perfect(stack)
			&& Mastery.level(stack) >= Mastery.MAX_LEVEL
			&& Perk.of(stack) != null
			&& Quality.ownWork(stack, smith);
	}

	/**
	 * Checked wherever a piece changes in the hands of its smith. The moment it qualifies it is marked,
	 * and the mark is what raises its numbers and frames its tooltip.
	 */
	public static boolean crown(ItemStack stack, Player smith) {
		if (is(stack) || !qualifies(stack, smith)) {
			return false;
		}
		stack.set(ModComponents.OBRA_MAESTRA, true);
		stack.set(DataComponents.TOOLTIP_STYLE, dev.forja.Forja.id("obra_maestra"));
		Assembler.rewrite(stack, net.minecraft.core.registries.BuiltInRegistries.BLOCK, net.minecraft.core.registries.BuiltInRegistries.ITEM);
		if (smith instanceof ServerPlayer server && server.level() instanceof ServerLevel level) {
			server.sendSystemMessage(Component.translatable("gui.forja.obra_maestra", stack.getHoverName()).withColor(0xFFD770));
			level.playSound(null, server.getX(), server.getY(), server.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, server.getX(), server.getY(1.2), server.getZ(), 40, 0.5, 0.6, 0.5, 0.05);
			dev.forja.ForjaAdvancements.award(server, "obra_maestra");
		}
		return true;
	}
}
