package dev.forja.mixin.client;

import dev.forja.client.CombatPoses;
import dev.forja.client.MobGaits;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zombie-like arms are posed after the swing and would wipe it out; see {@link CombatPoses#reapplyAfterZombieArms}.
 * Their arms-out run and leap go on first ({@link MobGaits#zombieArms}), so a weapon's blow still wins.
 */
@Mixin(AnimationUtils.class)
abstract class AnimationUtilsMixin {
	@Inject(method = "animateZombieArms", at = @At("TAIL"))
	private static void forja$weaponSwing(ModelPart leftArm, ModelPart rightArm, boolean aggressive, UndeadRenderState state, CallbackInfo ci) {
		MobGaits.zombieArms(leftArm, rightArm, state);
		CombatPoses.reapplyAfterZombieArms(leftArm, rightArm, aggressive, state);
	}
}
