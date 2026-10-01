package dev.forja.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.forja.item.ForgedItems;
import dev.forja.world.ForjaMobs;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The rest of a pillager's crossbow checks: see RangedCrossbowAttackGoalMixin. */
@Mixin(Pillager.class)
abstract class PillagerMixin {
	/** The pose: arms up round the stock rather than hanging at its sides. */
	@ModifyExpressionValue(method = "getArmPose",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/illager/Pillager;isHolding(Lnet/minecraft/world/item/Item;)Z"))
	private boolean forja$holdsForgedCrossbow(boolean holding) {
		return holding || ForjaMobs.holdsForged((Pillager) (Object) this, Items.CROSSBOW);
	}

	/** And it treats one as a crossbow when it looks at what is lying on the ground. */
	@ModifyReturnValue(method = "canUseNonMeleeWeapon", at = @At("RETURN"))
	private boolean forja$usesForgedCrossbow(boolean usable, ItemStack item) {
		return usable || item.getItem() instanceof ForgedItems.ForgedCrossbowItem;
	}
}
