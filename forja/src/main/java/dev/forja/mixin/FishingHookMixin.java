package dev.forja.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.world.entity.player.Player;

/** A cast hook only stays out while its owner holds Items.FISHING_ROD; a forged rod counts too. */
@Mixin(FishingHook.class)
abstract class FishingHookMixin {
	/** A rod that lands a catch learns from it, the way a pickaxe learns from ore. */
	@Inject(method = "retrieve", at = @At("RETURN"))
	private void forja$masteryFromCatch(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
		FishingHook self = (FishingHook) (Object) this;
		Player owner = self.getPlayerOwner();
		if (owner == null || cir.getReturnValueI() <= 0 || !rod.has(dev.forja.registry.ModComponents.PARTS)) {
			return;
		}
		dev.forja.forge.Mastery.addExperience(owner, rod, 3);
		dev.forja.forge.ItemHistory.addFish(rod);
		// Banco de peces: Cebo and Suerte del mar together sometimes pull up a second catch.
		if (dev.forja.upgrade.Synergy.BANCO_DE_PECES.active(rod) && self.level() instanceof net.minecraft.server.level.ServerLevel level
			&& level.getRandom().nextFloat() < 0.25F) {
			var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, self.position())
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, rod)
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, self)
				.withLuck(owner.getLuck())
				.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.FISHING);
			var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.FISHING);
			for (ItemStack extra : table.getRandomItems(params)) {
				var entity = new net.minecraft.world.entity.item.ItemEntity(level, self.getX(), self.getY(), self.getZ(), extra);
				entity.setDeltaMovement((owner.getX() - self.getX()) * 0.1, (owner.getY() - self.getY()) * 0.1 + 0.2, (owner.getZ() - self.getZ()) * 0.1);
				level.addFreshEntity(entity);
			}
		}
	}

	@WrapOperation(
		method = "shouldStopFishing",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
	)
	private boolean forja$forgedRodKeepsFishing(ItemStack stack, Object item, Operation<Boolean> original) {
		return original.call(stack, item) || item == Items.FISHING_ROD && stack.getItem() instanceof FishingRodItem;
	}
}
