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
 * The warning is counted on the world's clock, not in the goal's own ticks: vanilla runs this goal every
 * other tick, and counting its ticks made the warning twice as long as anyone else's. A player who steps
 * out of reach before the charge comes has escaped it: the warning is off, the turn goes back, and the
 * next approach warns again. The turn also goes back as soon as a charge is spent, not when the mob has
 * finished falling back to charge again.
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

	/** The game tick the warning ends at, once one has been given. */
	@Unique
	private long forja$releaseAt;

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
		long now = this.mob.level().getGameTime();
		if (Posture.isStaggered(this.mob, now)) {
			forja$reset();
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
		boolean inReach = this.mob.distanceToSqr(target) <= this.approachDistanceSq;
		if (this.forja$warned) {
			if (!inReach) {
				// Out of reach before the charge came: escaped. No chasing it down with the turn in hand.
				forja$reset();
				return;
			}
			if (now < this.forja$releaseAt) {
				this.mob.getNavigation().stop();
				this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
				ci.cancel();
			}
			// the warning is over: vanilla goes on this very tick, the spear comes down and it runs
			return;
		}
		if (!inReach) {
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
		this.forja$releaseAt = now + dev.forja.ai.MobDefense.windup(this.mob);
		this.mob.getNavigation().stop();
		CombatFeedback.telegraph(this.mob, (int) (this.forja$releaseAt - now));
		CombatStats.record(this.mob, WARNED);
		ci.cancel();
	}

	@Inject(method = "stop", at = @At("TAIL"))
	private void forja$onStop(CallbackInfo ci) {
		forja$reset();
	}

	@Unique
	private void forja$reset() {
		forja$release();
		this.forja$warned = false;
		this.forja$releaseAt = 0L;
	}

	@Unique
	private void forja$release() {
		if (this.forja$target != null) {
			AttackTokens.release(this.forja$target, this.mob);
			this.forja$target = null;
		}
	}
}
