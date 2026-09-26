package dev.forja.mixin;

import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forged armor with gold plates keeps piglins calm, the same as vanilla golden armor. */
@Mixin(PiglinAi.class)
abstract class PiglinAiMixin {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	@Inject(method = "isWearingSafeArmor", at = @At("HEAD"), cancellable = true)
	private static void forja$goldenPlates(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		for (EquipmentSlot slot : ARMOR) {
			ItemStack worn = entity.getItemBySlot(slot);
			ForgedParts parts = worn.get(ModComponents.PARTS);
			if (parts != null && parts.type().kind == ForgeType.Kind.ARMOR && parts.primary() == ForgeMaterial.ORO && !worn.isBroken()) {
				cir.setReturnValue(true);
				return;
			}
		}
	}
}
