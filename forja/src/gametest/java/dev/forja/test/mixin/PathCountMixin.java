package dev.forja.test.mixin;

import dev.forja.ai.TacticGoal;
import dev.forja.test.Perf;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * How often the executor asks for a path, and how often that turns into a new search: every tenth
 * tick by design, or sooner when the last path has ended. Only reads its state.
 */
@Mixin(value = TacticGoal.class, remap = false)
abstract class PathCountMixin {
	@Shadow
	@Final
	private Mob mob;

	@Shadow
	private int repath;

	@Inject(method = "pathTo", at = @At("HEAD"))
	private void bench$asked(double x, double y, double z, double speed, CallbackInfoReturnable<Boolean> cir) {
		Perf.count(Perf.C.TACTIC_PATH);
		if (Perf.measuring() && this.repath - 1 > 0 && this.mob.getNavigation().isDone()) {
			Perf.count(Perf.C.TACTIC_MOVETO_DONE);
		}
	}

	@Inject(method = "pathTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;moveTo(DDDD)Z"))
	private void bench$searched(double x, double y, double z, double speed, CallbackInfoReturnable<Boolean> cir) {
		Perf.count(Perf.C.TACTIC_MOVETO);
	}
}
