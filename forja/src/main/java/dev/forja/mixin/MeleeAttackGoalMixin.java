package dev.forja.mixin;

import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatFeedback;
import dev.forja.combat.Posture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Melee monsters warn a player before they strike: they flash and wait a few ticks, following the player all the
 * while (ai.WindupChase, 2026-09-30; they used to stop), and a player who gets out of reach, dodges or raises a
 * shield in time is not hit. Only a few of them swing at the same
 * player at once. That covers vanilla's monsters and Forja's own (see {@link AttackTokens#warns}):
 * Forja's keep their Windup telegraphs for their special moves on top of this. Since 2026-09-29 (Andy) the
 * same holds against anything, not only players: the warning, the turns and, for the ones waiting, the ring
 * (ai.MobRing).
 */
@Mixin(MeleeAttackGoal.class)
abstract class MeleeAttackGoalMixin {
	@Shadow
	@Final
	protected PathfinderMob mob;

	@Shadow
	protected abstract boolean canPerformAttack(LivingEntity target);

	@Shadow
	protected abstract void resetAttackCooldown();

	@Unique
	private int forja$windup;

	/** The whole of this warning, which weight and counters make longer or shorter than the base. */
	@Unique
	private int forja$windupTotal;

	@Unique
	private LivingEntity forja$target;

	/** This blow is a fake: dropped halfway through its warning (against players who parry a lot). */
	@Unique
	private boolean forja$feint;

	@Inject(method = "checkAndPerformAttack", at = @At("HEAD"), cancellable = true)
	private void forja$telegraphedAttack(LivingEntity target, CallbackInfo ci) {
		CombatConfig cfg = CombatConfig.get();
		// A creeper has no blow: its attack is the fuse (SwellGoal). Given the warning, it stopped two blocks
		// short, flashed, took a turn from the ones that do strike and hopped back, and hardly ever blew up
		// (Andy, 2026-09-29).
		if (mob instanceof net.minecraft.world.entity.monster.Creeper) {
			return;
		}
		// A duel's watchers do not swing (idea 96).
		if (forja$windup == 0 && dev.forja.ai.Duels.watching(mob)) {
			ci.cancel();
			return;
		}
		// Against anything, not only players (Andy, 2026-09-29): a golem or another monster gets the same warning
		// and the same turns. Only the networks stay for players; this is the rules' path.
		if (!cfg.enabled || !cfg.telegraph || !AttackTokens.warns(mob)) {
			return;
		}
		ci.cancel();
		if (Posture.isStaggered(mob, mob.level().getGameTime())) {
			forja$cut("aturdido");
			forja$reset();
			return;
		}
		if (forja$windup > 0 && target != forja$target) {
			// Turned on somebody else mid-warning (another player struck it): the turn the blow was wound
			// up under goes back, and the blow carries on only if the new target has a turn free as well.
			AttackTokens.release(forja$target, mob);
			forja$target = null;
			if (!AttackTokens.tryAcquire(target, mob, dev.forja.ai.Aggression.maxAttackers(mob, target))) {
				forja$cut("objetivo");
				forja$reset();
				return;
			}
			forja$target = target;
		}
		if (forja$windup > 0) {
			forja$followOrHold(target);
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (forja$feint && forja$windup <= Math.max(1, forja$windupTotal / 2)) {
				// The fake: it wound up, the player raised the shield for it, and nothing comes.
				forja$feint = false;
				dev.forja.combat.CombatStats.record(mob, dev.forja.combat.CombatStats.FEINT);
				dev.forja.combat.CombatStats.warnEnded(mob, "finta");
				resetAttackCooldown();
				forja$reset();
				return;
			}
			if (--forja$windup > 0) return;
			mob.swing(InteractionHand.MAIN_HAND);
			// Lands as far off as the weapon reaches (Reach): a flail's or a lance's blow further than a fist's.
			boolean inReach = mob.distanceTo(target) <= dev.forja.ai.Reach.landing(mob, target);
			boolean seen = inReach && mob.getSensing().hasLineOfSight(target);
			boolean landed = seen && mob.level() instanceof ServerLevel level && mob.doHurtTarget(level, target);
			if (landed) {
				dev.forja.ai.HopBack.afterHit(mob, target);
			}
			// how it ended, for the tests (CapitanMedidaGameTests): struck during the warning is knocked back
			dev.forja.combat.CombatStats.warnEnded(mob, landed ? "llega" : "falla");
			dev.forja.combat.CombatStats.record(mob, landed ? dev.forja.combat.CombatStats.WARNED_LANDED
				: !inReach ? (mob.hurtTime > 0 ? dev.forja.combat.CombatStats.WARNED_KNOCKED : dev.forja.combat.CombatStats.WARNED_MOVED)
				: !seen ? dev.forja.combat.CombatStats.WARNED_UNSEEN : dev.forja.combat.CombatStats.WARNED_NO_DAMAGE);
			resetAttackCooldown();
			dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
			if (mind != null) {
				mind.lastStrike = mob.level().getGameTime();
			}
			// walked straight in without a path (WindupChase): one again, or the goal of a mob that does not follow unseen
			// targets ends on the next tick and stands it idle until it is looked at again
			if (!landed && dev.forja.ai.WindupChase.on() && mob.getNavigation().isDone()) {
				mob.getNavigation().moveTo(target, speedModifier);
			}
			forja$reset();
			return;
		}
		// the one wait after its last blow, whichever path struck it (MobMind.nextBlowAt)
		dev.forja.ai.MobMind waiting = dev.forja.ai.MobAi.mind(mob);
		if (waiting != null && mob.level().getGameTime() < waiting.nextBlowAt) return;
		if (!canPerformAttack(target) || !AttackTokens.tryAcquire(target, mob, dev.forja.ai.Aggression.maxAttackers(mob, target))) return;
		forja$windup = dev.forja.ai.MobDefense.windup(mob);
		forja$windupTotal = forja$windup;
		dev.forja.ai.MobDefense.spendCounter(mob);
		forja$target = target;
		forja$feint = mob.getRandom().nextDouble() < (mob instanceof dev.forja.entity.ForgeAutomaton
			? dev.forja.ai.Aggression.adaptiveFeintChance(target) : dev.forja.ai.Aggression.feintChance(mob, target));
		forja$followOrHold(target);
		CombatFeedback.telegraph(mob, forja$windup);
		dev.forja.combat.CombatStats.record(mob, dev.forja.combat.CombatStats.WARNED);
		dev.forja.combat.CombatStats.warnStarted(mob, target, "vanilla");
		dev.forja.ai.MobMind warned = dev.forja.ai.MobAi.mind(mob);
		if (warned != null) {
			warned.warning = true;
		}
	}

	/** A warning dropped before its end (counted for the tests; a feint is counted as one). */
	@Unique
	private void forja$cut(String why) {
		if (forja$windup > 0) {
			dev.forja.combat.CombatStats.record(mob, dev.forja.combat.CombatStats.WARNED_CUT);
			dev.forja.combat.CombatStats.record(mob, dev.forja.combat.CombatStats.WARNED_CUT + "_" + why);
			dev.forja.combat.CombatStats.warnEnded(mob, "cortado");
		}
	}

	/** Why the goal stopped mid-warning, for the tests: no target, dead, or what the brain decided instead. */
	@Unique
	private String forja$stopReason() {
		if (!mob.isAlive()) {
			return "muerto";
		}
		if (mob.getTarget() == null || mob.getTarget() != forja$target) {
			return "sin_objetivo";
		}
		dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
		return mind == null ? "parada" : "parada_" + mind.decision.tactic().name().toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * Through the warning it keeps following its target, at its own approach speed times WindupChase.factor (Andy,
	 * 2026-09-30); close enough, with no way to it, or with windupChase off, it stands still.
	 */
	@Unique
	private void forja$followOrHold(LivingEntity target) {
		if (!dev.forja.ai.WindupChase.follow(mob, target, speedModifier)) {
			forja$holdStill(target);
		}
	}

	@Shadow
	@Final
	private double speedModifier;

	/**
	 * Still during the warning without dropping the path: a melee goal whose navigation is done ends
	 * itself, and the warning would start over forever without the blow ever landing.
	 */
	@Unique
	private void forja$holdStill(LivingEntity target) {
		PathNavigation navigation = mob.getNavigation();
		if (navigation.isDone()) navigation.moveTo(target, 0.0);
		navigation.setSpeedModifier(0.0);
	}

	@Shadow
	private int ticksUntilNextAttack;

	/**
	 * A mob whose weapon reaches further than its body (a flail, a lance, a scythe...) stops walking in once
	 * its target is within that reach, and strikes from there, instead of walking up into the player's face.
	 * The path is kept, only its speed is taken away, as during a warning; a mob holding anything that adds
	 * no reach walks in as it always did.
	 */
	@Inject(method = "tick", at = @At("TAIL"))
	private void forja$holdAtReach(CallbackInfo ci) {
		LivingEntity target = mob.getTarget();
		if (target != null && forja$windup == 0 && CombatConfig.get().enabled && !(mob instanceof net.minecraft.world.entity.monster.Creeper) && dev.forja.ai.Reach.closeEnough(mob, target)
			&& mob.getSensing().hasLineOfSight(target)) {
			forja$holdStill(target);
		}
	}

	/**
	 * The ring against what is not a player (ai.MobRing): with every turn on the target taken, it waits round it
	 * at its own slot instead of pushing in behind the ones striking. Every few ticks, not every tick: vanilla
	 * re-paths to the target on its own clock, and a path a tick is what a crowd cannot afford.
	 */
	@Inject(method = "tick", at = @At("TAIL"))
	private void forja$waitOnTheRing(CallbackInfo ci) {
		LivingEntity target = mob.getTarget();
		if (forja$windup > 0 || !dev.forja.ai.MobRing.applies(mob, target) || !dev.forja.ai.MobRing.waits(mob, target)) {
			return;
		}
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if ((mob.tickCount + mob.getId()) % 4 == 0) {
			net.minecraft.world.phys.Vec3 slot = dev.forja.ai.MobRing.place(mob, target);
			if (mob.distanceToSqr(slot.x, mob.getY(), slot.z) > 1.0) {
				mob.getNavigation().moveTo(slot.x, slot.y, slot.z, 1.0);
			} else {
				mob.getNavigation().stop();
			}
		}
	}

	/**
	 * Honest perception (ai.HonestPerception, Andy 2026-09-29): a monster that has lost sight of its player does not
	 * chase them by vanilla's path to the real position; the executor (TacticGoal) walks it to where it last saw them.
	 * A player it perceives, or touches, is attacked as always.
	 */
	@Shadow
	private long lastCanUseCheck;

	/**
	 * Vanilla looks at whether to start the melee goal once every 20 ticks. A monster our rules drive stops it whenever
	 * it turns to a tactic (to wait, to go round) or its path runs out next to a moving player, and then stood with
	 * nothing moving it for up to a second when it went in again: 12 % of its "going in" ticks against the test player
	 * (CapitanMedidaGameTests, 2026-09-30). For those, the look comes every {@link #FORJA_RECHECK} ticks.
	 */
	@Unique
	private static final long FORJA_RECHECK = 4L;

	@Inject(method = "canUse", at = @At("HEAD"))
	private void forja$recheckSooner(CallbackInfoReturnable<Boolean> cir) {
		long now = mob.level().getGameTime();
		if (now - lastCanUseCheck >= FORJA_RECHECK && now - lastCanUseCheck < 20L && CombatConfig.get().enabled) {
			dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
			if (mind != null && mind.target != null && !mind.networked && mind.decision.tactic() == dev.forja.ai.Tactic.ACERCARSE) {
				lastCanUseCheck = now - 20L;
			}
		}
	}

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void forja$honestStart(CallbackInfoReturnable<Boolean> cir) {
		if (forja$windup == 0 && dev.forja.ai.HonestPerception.lost(mob)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void forja$honestGoOn(CallbackInfoReturnable<Boolean> cir) {
		if (forja$windup == 0 && dev.forja.ai.HonestPerception.lost(mob)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * A blow being warned is seen through while its target lives. Vanilla ends the goal of a mob that does not
	 * follow unseen targets (a zombie's) as soon as its path is done, and {@link #forja$holdStill} keeps a path only
	 * when one can be made: a mob standing in its target's own block, as close as it can get, has none, so its goal
	 * stopped the tick after it started, dropped the warning with it, and started again twenty ticks later only to
	 * drop it again. After a feint a zombie walked into a still test player that way and never struck again.
	 */
	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void forja$finishTheWarning(CallbackInfoReturnable<Boolean> cir) {
		if (forja$windup > 0 && forja$target != null && forja$target.isAlive() && forja$target == mob.getTarget()) {
			cir.setReturnValue(true);
		}
	}

	/** Peso (combat/Weight): the more it carries, the longer it waits before the next blow. */
	@Inject(method = "resetAttackCooldown", at = @At("TAIL"))
	private void forja$heavyWait(CallbackInfo ci) {
		if (CombatConfig.get().enabled) {
			ticksUntilNextAttack = Math.round(ticksUntilNextAttack * (1.0F + dev.forja.combat.Weight.INTERVAL_PER_KG * dev.forja.combat.Weight.carried(mob)));
		}
		forja$nextAttackAt = mob.level().getGameTime() + ticksUntilNextAttack;
		dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
		if (mind != null) {
			mind.nextBlowAt = Math.max(mind.nextBlowAt, forja$nextAttackAt);
		}
	}

	/** When the wait after its last blow is over (resetAttackCooldown): a restart of the goal does not cut it short. */
	@Unique
	private long forja$nextAttackAt = Long.MIN_VALUE / 2;

	/**
	 * Vanilla's start() puts the wait before the next blow back to 0. Our rules stop and start the melee goal far more
	 * often than vanilla does (a tactic for a moment, a path run out next to a moving player, the goal looked at again
	 * every 4 ticks), and 1 warning in 5 came less than 20 ticks after the same mob's last blow (2026-09-30): the
	 * wait after a blow, and a heavy mob's longer one (Weight), were skipped. The wait left over is kept.
	 */
	@Inject(method = "start", at = @At("TAIL"))
	private void forja$keepTheWait(CallbackInfo ci) {
		if (CombatConfig.get().enabled) {
			ticksUntilNextAttack = (int) Math.max(ticksUntilNextAttack, forja$nextAttackAt - mob.level().getGameTime());
		}
	}

	@Inject(method = "stop", at = @At("TAIL"))
	private void forja$onStop(CallbackInfo ci) {
		forja$cut(forja$stopReason());
		forja$reset();
	}

	@Unique
	private void forja$reset() {
		AttackTokens.release(forja$target, mob);
		forja$target = null;
		forja$windup = 0;
		dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
		if (mind != null) {
			mind.warning = false;
		}
	}
}
