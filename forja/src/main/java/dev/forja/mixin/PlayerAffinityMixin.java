package dev.forja.mixin;

import dev.forja.forge.Quality;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Afinidad: a tool answers a little better in the hands of the smith who made it. */
@Mixin(Player.class)
public abstract class PlayerAffinityMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void forja$ownWorkMinesFaster(BlockState state, CallbackInfoReturnable<Float> cir) {
		Player self = (Player) (Object) this;
		float speed = cir.getReturnValueF();
		if (speed > 1.0F && Quality.ownWork(self.getMainHandItem(), self)) {
			cir.setReturnValue(speed * Quality.affinity(self.getMainHandItem(), self));
		}
	}
}
