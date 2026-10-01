package dev.forja.forge;

import dev.forja.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Forged gear never vanishes when its durability runs out: it stays at zero, broken. Vanilla already
 * ignores the attributes of broken equipment; Forja's mixins also turn off its mining speed, drops,
 * right-click use, enchantments and upgrades until it is repaired at the forge star, by Autorreparacion
 * or by Llanto.
 */
public final class BrokenGear {
	private BrokenGear() {
	}

	public static boolean isBroken(ItemStack stack) {
		return stack.has(ModComponents.PARTS) && stack.isBroken();
	}

	static void onBroken(ItemStack stack, @Nullable ServerPlayer player) {
		if (player == null) {
			return;
		}
		player.sendOverlayMessage(Component.translatable("gui.forja.rota.aviso", stack.getHoverName()));
		dev.forja.ForjaAdvancements.award(player, "roto");
		if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		// A piece of Forja gear does not vanish when it breaks, which is the whole point of it — so the
		// vanilla break has nothing to say here. What it looks like instead is the material giving up:
		// a shower of it, in its own colour, and the metal going cold.
		//
		// This matters more than a normal break. Losing a vanilla tool costs you the tool; losing one of
		// these costs you the materials, the upgrades and however many levels of Maestria it had, and
		// the only warning was one line of text in the corner of the screen.
		var parts = stack.get(dev.forja.registry.ModComponents.PARTS);
		int colour = parts == null || parts.primary() == null ? 0x9AA0A8 : parts.primary().color;
		level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(colour, 1.3F),
			player.getX(), player.getY(1.1), player.getZ(), 26, 0.35, 0.4, 0.35, 0.06);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
			player.getX(), player.getY(1.0), player.getZ(), 16, 0.3, 0.35, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			net.minecraft.sounds.SoundEvents.ITEM_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			net.minecraft.sounds.SoundEvents.ANVIL_DESTROY, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.4F);
	}

	/** Called by the ItemStack mixin in place of vanilla's break: keeps the item at zero durability. */
	public static void breakWithoutVanishing(ItemStack stack, @Nullable ServerPlayer player) {
		boolean wasBroken = stack.isBroken();
		stack.setDamageValue(stack.getMaxDamage());
		if (!wasBroken) {
			onBroken(stack, player);
		}
	}
}
