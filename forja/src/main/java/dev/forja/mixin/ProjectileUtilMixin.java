package dev.forja.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.forja.item.ForgedItems;
import dev.forja.world.ForjaMobs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The two places every ranged mob goes through, taught about forged bows and crossbows.
 *
 * <p>Vanilla asks which hand holds {@code Items.BOW} (or {@code Items.CROSSBOW}) when a skeleton picks
 * its goal, draws, and lets go, and a pillager loads and fires; ours is a different item, so the answer
 * was always the empty off hand. Only mob code calls this: the player's own bow never asks.
 */
@Mixin(ProjectileUtil.class)
abstract class ProjectileUtilMixin {
	@ModifyReturnValue(method = "getWeaponHoldingHand", at = @At("RETURN"))
	private static InteractionHand forja$forgedLauncher(InteractionHand hand, LivingEntity mob, Item weapon) {
		return hand == InteractionHand.OFF_HAND && ForjaMobs.forgedLauncher(mob.getMainHandItem(), weapon) ? InteractionHand.MAIN_HAND : hand;
	}

	/**
	 * A mob's arrow from a forged bow bites the way a player's does: the limbs add the same bonus the
	 * player's arrow gets (ForgedBowItem.createProjectile), on top of what the draw was worth. The rest —
	 * Poder, Llama, Retroceso, the material's traits — rides on the arrow already, as enchantments of the
	 * weapon it remembers being fired from.
	 */
	@ModifyReturnValue(method = "getMobArrow", at = @At("RETURN"))
	private static AbstractArrow forja$forgedArrow(AbstractArrow arrow, LivingEntity mob, ItemStack projectile, float power, @Nullable ItemStack weapon) {
		if (weapon != null && weapon.getItem() instanceof ForgedItems.ForgedBowItem) {
			arrow.setBaseDamage(((AbstractArrowAccess) arrow).forjaBaseDamage() + ForgedItems.ForgedBowItem.arrowDamageBonus(weapon));
		}
		return arrow;
	}
}
