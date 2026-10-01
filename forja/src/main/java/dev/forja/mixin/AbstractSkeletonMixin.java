package dev.forja.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.forja.item.ForgedItems;
import dev.forja.world.ForjaMobs;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A skeleton with a forged bow is an archer. Vanilla picks its attack goal by asking whether what it
 * holds is {@code Items.BOW}, so one of ours put it on the sword goal and it walked up and clubbed you
 * with the bow — which is why the mod used to leave every skeleton its plain vanilla one.
 */
@Mixin(AbstractSkeleton.class)
abstract class AbstractSkeletonMixin {
	@ModifyExpressionValue(method = "reassessWeaponGoal",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
	private boolean forja$forgedBowIsABow(boolean bow) {
		return bow || ForjaMobs.holdsForged((AbstractSkeleton) (Object) this, Items.BOW);
	}

	/** And it treats one as a bow when it looks at what is lying on the ground. */
	@ModifyReturnValue(method = "canUseNonMeleeWeapon", at = @At("RETURN"))
	private boolean forja$usesForgedBow(boolean usable, ItemStack item) {
		return usable || item.getItem() instanceof ForgedItems.ForgedBowItem;
	}
}
