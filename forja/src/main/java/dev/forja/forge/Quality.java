package dev.forja.forge;

import dev.forja.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What the hand that made a piece leaves on it. A press of the hammer caught at the right moment is a
 * perfect forge, worth five percent of every number; the name of the smith stays on the item, and in
 * their own hands it answers a little better than in anyone else.
 */
public final class Quality {
	/** What a perfect forge adds to every stat. */
	public static final float PERFECT_BONUS = 0.05F;

	/** What a piece adds in the hands of the one who forged it. */
	public static final float AFFINITY_BONUS = 0.02F;

	private Quality() {
	}

	public static boolean perfect(ItemStack stack) {
		return stack.getOrDefault(ModComponents.PERFECTA, false);
	}

	/** The share every stat of this item is raised by, before anyone picks it up. */
	/** What a rough casting takes off a piece: the same scale the perfect press adds on. */
	public static final float ROUGH_PENALTY = -0.15F;

	public static float bonus(ItemStack stack) {
		return (perfect(stack) ? PERFECT_BONUS : 0.0F)
			+ (Masterpiece.is(stack) ? Masterpiece.BONUS : 0.0F)
			// Poured through a strainer that did not hold. It is the opposite of a perfect press, and it
			// belongs here rather than in the stats so that every path that rewrites a piece keeps it.
			+ (stack.getOrDefault(ModComponents.ROUGH, false) ? ROUGH_PENALTY : 0.0F);
	}

	/** Marks the piece that came out of a well timed press. */
	public static void markPerfect(ItemStack stack) {
		stack.set(ModComponents.PERFECTA, true);
	}

	/** Writes who made it, so the piece carries their name from then on. */
	public static void sign(ItemStack stack, Player smith) {
		stack.set(ModComponents.HERRERO, smith.getName().getString());
		stack.set(ModComponents.HERRERO_ID, smith.getUUID().toString());
	}

	public static @Nullable String smith(ItemStack stack) {
		return stack.get(ModComponents.HERRERO);
	}

	/** Whether this player is the one who forged the piece. */
	public static boolean ownWork(ItemStack stack, @Nullable Player holder) {
		String id = stack.get(ModComponents.HERRERO_ID);
		return holder != null && id != null && id.equals(holder.getUUID().toString());
	}

	/** Afinidad: the small edge a smith has with their own work, as a plain multiplier. */
	public static float affinity(ItemStack stack, @Nullable Player holder) {
		if (!ownWork(stack, holder)) {
			return 1.0F;
		}
		// Firma del maestro: their own work answers to them twice as well.
		return 1.0F + (Techniques.has(holder, Technique.FIRMA_DEL_MAESTRO) ? Technique.AFFINITY : AFFINITY_BONUS);
	}

	public static Component signatureLine(ItemStack stack, @Nullable Player holder) {
		String name = smith(stack);
		return ownWork(stack, holder)
			? Component.translatable("tooltip.forja.firma.propia", name)
			: Component.translatable("tooltip.forja.firma", name);
	}
}
