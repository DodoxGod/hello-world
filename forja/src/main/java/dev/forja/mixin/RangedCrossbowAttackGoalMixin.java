package dev.forja.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.forja.world.ForjaMobs;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A pillager with a forged crossbow loads and fires it like its own. The shot itself already goes
 * through ForgedCrossbowItem.createProjectile, the same as a player's; what was missing was vanilla
 * noticing the crossbow in its hand. The hand it is in is found in ProjectileUtilMixin.
 */
@Mixin(RangedCrossbowAttackGoal.class)
abstract class RangedCrossbowAttackGoalMixin {
	@Shadow
	@Final
	private Monster mob;

	@ModifyReturnValue(method = "isHoldingCrossbow", at = @At("RETURN"))
	private boolean forja$forgedCrossbow(boolean holding) {
		return holding || ForjaMobs.holdsForged(mob, Items.CROSSBOW);
	}
}
