package dev.forja.mixin;

import java.util.function.Consumer;

import dev.forja.forge.BrokenGear;
import dev.forja.registry.ModComponents;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forged gear breaks without vanishing and does nothing useful while broken. */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@org.spongepowered.asm.mixin.Unique
	private static final net.minecraft.util.RandomSource forja$wear = net.minecraft.util.RandomSource.create();

	/**
	 * Every forged thing draws its parts under its name. Done here rather than in the items because
	 * there are a dozen forged item classes, each extending a different vanilla one, and what they have
	 * in common is not a superclass but this component.
	 */
	@Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
	private void forja$partsStrip(CallbackInfoReturnable<java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent>> cir) {
		if (cir.getReturnValue().isPresent()) {
			return;
		}
		dev.forja.part.ForgedParts parts = ((ItemStack) (Object) this).get(ModComponents.PARTS);
		if (parts != null) {
			cir.setReturnValue(java.util.Optional.of(new dev.forja.item.PartsStrip(parts, (ItemStack) (Object) this)));
		}
	}

	/**
	 * Nothing of the mod's wears the enchantment glint. Andy: "quiero que nada del mod tenga el brillo por
	 * encantamiento". The enchantments some upgrades and trait materials hide on a piece are how those work,
	 * not something to advertise, and the purple sheen covers the one thing a forged piece has to show: what
	 * it is made of. Asked of the stack rather than written on it, so gear forged before this loses it too.
	 */
	@Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
	private void forja$noGlint(CallbackInfoReturnable<Boolean> cir) {
		ItemStack self = (ItemStack) (Object) this;
		if (dev.forja.Forja.MOD_ID.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(self.getItem()).getNamespace())) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "applyDamage", at = @At("HEAD"), cancellable = true)
	private void forja$breakWithoutVanishing(int newDamage, @Nullable ServerPlayer player, Consumer<Item> onBreak, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (!self.has(ModComponents.PARTS) || newDamage < self.getMaxDamage()) {
			return;
		}
		if (player != null) {
			CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(player, self, newDamage);
		}
		boolean wasBroken = self.isBroken();
		BrokenGear.breakWithoutVanishing(self, player);
		if (!wasBroken) {
			// The break sound, particles and dropped attribute modifiers, without shrinking the stack.
			onBreak.accept(self.getItem());
		}
		ci.cancel();
	}

	@Inject(method = "applyDamage", at = @At("HEAD"), cancellable = true)
	private void forja$eternalEdge(int newDamage, @Nullable ServerPlayer player, Consumer<Item> onBreak, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (newDamage <= self.getDamageValue()) {
			return;
		}
		// Filo eterno: a quarter of the wear never lands. A water quench skips a tenth of it.
		float skipped = dev.forja.forge.Perk.has(self, dev.forja.forge.Perk.FILO_ETERNO) ? 0.25F : 0.0F;
		if (dev.forja.forge.Temple.has(self, dev.forja.forge.Temple.AGUA)) {
			skipped += dev.forja.forge.Temple.WATER_WEAR_SKIP;
		}
		if (skipped > 0.0F && forja$wear.nextFloat() < skipped) {
			// A gift that works by something **not** happening is invisible by construction, which is
			// why this one gets drawn and Duelista does not: you can count a combo, you cannot count
			// the durability you still have.
			if (player != null && player.level() instanceof ServerLevel world
				&& dev.forja.forge.Perk.has(self, dev.forja.forge.Perk.FILO_ETERNO)) {
				dev.forja.forge.Perk.FILO_ETERNO.spark(world, player, 3);
			}
			ci.cancel();
		}
	}

	@Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
	private void forja$brokenMinesSlowly(BlockState state, CallbackInfoReturnable<Float> cir) {
		if (BrokenGear.isBroken((ItemStack) (Object) this)) {
			cir.setReturnValue(1.0F);
		}
	}

	@Inject(method = "isCorrectToolForDrops", at = @At("HEAD"), cancellable = true)
	private void forja$brokenDropsNothing(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (BrokenGear.isBroken((ItemStack) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void forja$brokenCannotBeUsed(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (BrokenGear.isBroken((ItemStack) (Object) this)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void forja$brokenCannotBeUsedOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (BrokenGear.isBroken((ItemStack) (Object) this)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
