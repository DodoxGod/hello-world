package dev.forja.mixin;

import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.Perception;
import dev.forja.ai.Personality;
import dev.forja.combat.CombatConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hunting (docs/red_mob_v4_diseno.md §4.5): vanilla lets go of a player it has not seen for 3 seconds, or who is beyond
 * its follow range. A monster that thinks (it has a mind) and has seen or heard its player within the boredom time
 * (Personality.BORED_TICKS, 30 s) keeps them as its target up to 48 blocks: it goes on looking for them where it thinks
 * they are (the estimate), not where they really are. Everything else vanilla checks (alive, not creative, can be
 * attacked) still applies.
 */
@Mixin(TargetGoal.class)
abstract class TargetGoalMixin {
	@Shadow
	@Final
	protected Mob mob;

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void forja$keepHunting(CallbackInfoReturnable<Boolean> cir) {
		if (!CombatConfig.get().enabled || !CombatConfig.get().iaPercepcionHonesta || !dev.forja.difficulty.Ladder.thinks(this.mob)) {
			return;
		}
		LivingEntity target = this.mob.getTarget();
		MobMind mind = MobAi.mind(this.mob);
		if (mind == null || !(target instanceof Player player) || mind.hunted != player || !player.isAlive() || player.isCreative()
			|| player.isSpectator() || player.level() != this.mob.level() || !this.mob.canAttack(player)) {
			return;
		}
		long now = this.mob.level().getGameTime();
		if (now - Perception.estimateAt(mind) <= Personality.BORED_TICKS && this.mob.distanceTo(player) <= Perception.HUNT_RANGE) {
			cir.setReturnValue(true);
		}
	}
}
