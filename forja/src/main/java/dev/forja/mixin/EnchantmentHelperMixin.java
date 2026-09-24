package dev.forja.mixin;

import dev.forja.forge.BrokenGear;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Broken forged gear loses the enchantments behind its upgrades and traits until it is repaired. */
@Mixin(EnchantmentHelper.class)
abstract class EnchantmentHelperMixin {
	@Inject(
		method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentVisitor;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void forja$skipBroken(ItemStack piece, EnchantmentHelper.EnchantmentVisitor method, CallbackInfo ci) {
		if (BrokenGear.isBroken(piece)) {
			ci.cancel();
		}
	}

	@Inject(
		method = "runIterationOnItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/enchantment/EnchantmentHelper$EnchantmentInSlotVisitor;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void forja$skipBrokenInSlot(ItemStack piece, EquipmentSlot slot, LivingEntity owner, EnchantmentHelper.EnchantmentInSlotVisitor method, CallbackInfo ci) {
		if (BrokenGear.isBroken(piece)) {
			ci.cancel();
		}
	}
}
