package dev.forja.mixin;

import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatFeedback;
import dev.forja.combat.CombatStats;
import dev.forja.combat.Posture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A monster with a spear charges the way every other blow in this mod comes: after a warning, and only
 * when it is its turn. Vanilla's spear goal walks up to the edge of its charge, lowers the point and runs
 * in; it had neither the warning nor the turn (MeleeAttackGoalMixin never sees it), so a zombie with a
 * spear was the one monster that hit without saying so and on top of everybody else.
 *
 * <p>Now at the edge of its charge it asks for a turn, and waits there until it has one; then it stands
 * and shows the warning for {@link dev.forja.ai.MobDefense#windup} ticks, and only then lowers the spear.
 * The turn goes back as soon as the charge is spent, not when it has finished falling back to charge again.
 */
@Mixin(SpearUseGoal.class)
abstract class SpearUseGoalMixin {
	@Shadow
	@Final
	private Monster mob;

	@Shadow
	private SpearUseGoal.SpearUseState state;

	@Shadow
	@Final
	private float approachDistanceSq;

	@Unique
	private int forja$windup;

	@Unique
	private boolean forja$warned;

	@Unique
	private LivingEntity forja$target;

	/** What it did, for the tests to count. */
	@Unique
	private static final String WARNED = "spear_warned";

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void forja$warnedCharge(CallbackInfo ci) {
		CombatConfig cfg = CombatConfig.get();
		LivingEntity target = this.mob.getTarget();
		if (this.state == null || target == null || !cfg.enabled || !cfg.telegraph || !(target instanceof Player)
			|| !AttackTokens.warns(this.mob)) {
			return;
		}
		if (Posture.isStaggered(this.mob, this.mob.level().getGameTime())) {
			forja$release();
			this.mob.getNavigation().stop();
			ci.cancel();
			return;
		}
		if (!this.state.notEngagedYet()) {
			// Charging, or falling back: vanilla's. Once the point is up again the turn is somebody else's.
			if (!this.mob.isUsingItem()) {
				forja$release();
			}
			return;
		}
		if (this.forja$windup > 0) {
			this.mob.getNavigation().stop();
			this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (--this.forja$windup > 0) {
				ci.cancel();
			}
			// at nought vanilla goes on this very tick: the spear comes down and it runs
			return;
		}
		if (this.forja$warned || this.mob.distanceToSqr(target) > this.approachDistanceSq) {
			return;
		}
		if (!AttackTokens.tryAcquire(target, this.mob, dev.forja.ai.Aggression.maxAttackers(this.mob, target))) {
			// No turn: it waits at the edge of its charge, point up, until one comes free.
			this.mob.getNavigation().stop();
			this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			ci.cancel();
			return;
		}
		this.forja$target = target;
		this.forja$warned = true;
		this.forja$windup = dev.forja.ai.MobDefense.windup(this.mob);
		this.mob.getNavigation().stop();
		CombatFeedback.telegraph(this.mob);
		CombatStats.record(this.mob, WARNED);
		ci.cancel();
	}

	@Inject(method = "stop", at = @At("TAIL"))
	private void forja$onStop(CallbackInfo ci) {
		forja$release();
		this.forja$warned = false;
		this.forja$windup = 0;
	}

	@Unique
	private void forja$release() {
		if (this.forja$target != null) {
			AttackTokens.release(this.forja$target, this.mob);
			this.forja$target = null;
		}
	}
}
