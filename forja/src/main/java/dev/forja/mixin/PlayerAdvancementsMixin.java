package dev.forja.mixin;

import dev.forja.GuideBooks;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An advancement of the forja tab done, however it was done (from code, or by a trigger in its json): the book
 * recipes it teaches are given (GuideBooks). One place for all of them, so no advancement can be earned without
 * its book following.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Shadow
	public abstract AdvancementProgress getOrStartProgress(AdvancementHolder holder);

	@Inject(method = "award", at = @At("RETURN"))
	private void forja$teachBooks(AdvancementHolder holder, String criterion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && this.getOrStartProgress(holder).isDone()) {
			GuideBooks.completed(this.player, holder);
		}
	}
}
