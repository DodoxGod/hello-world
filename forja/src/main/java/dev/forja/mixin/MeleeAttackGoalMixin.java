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
 * Melee monsters warn a player before they strike: they stop, flash and wait a few ticks, and a player
 * who steps back, dodges or raises a shield in time is not hit. Only a few of them swing at the same
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
			forja$reset();
			return;
		}
		if (forja$windup > 0 && target != forja$target) {
			// Turned on somebody else mid-warning (another player struck it): the turn the blow was wound
			// up under goes back, and the blow carries on only if the new target has a turn free as well.
			AttackTokens.release(forja$target, mob);
			forja$target = null;
			if (!AttackTokens.tryAcquire(target, mob, dev.forja.ai.Aggression.maxAttackers(mob, target))) {
				forja$reset();
				return;
			}
			forja$target = target;
		}
		if (forja$windup > 0) {
			forja$holdStill(target);
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (forja$feint && forja$windup <= Math.max(1, forja$windupTotal / 2)) {
				// The fake: it wound up, the player raised the shield for it, and nothing comes.
				forja$feint = false;
				dev.forja.combat.CombatStats.record(mob, dev.forja.combat.CombatStats.FEINT);
				resetAttackCooldown();
				forja$reset();
				return;
			}
			if (--forja$windup > 0) return;
			mob.swing(InteractionHand.MAIN_HAND);
			// Lands as far off as the weapon reaches (Reach): a flail's or a lance's blow further than a fist's.
			if (mob.distanceTo(target) <= dev.forja.ai.Reach.landing(mob, target) && mob.getSensing().hasLineOfSight(target) && mob.level() instanceof ServerLevel level
				&& mob.doHurtTarget(level, target)) {
				dev.forja.ai.HopBack.afterHit(mob, target);
			}
			resetAttackCooldown();
			dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
			if (mind != null) {
				mind.lastStrike = mob.level().getGameTime();
			}
			forja$reset();
			return;
		}
		if (!canPerformAttack(target) || !AttackTokens.tryAcquire(target, mob, dev.forja.ai.Aggression.maxAttackers(mob, target))) return;
		forja$windup = dev.forja.ai.MobDefense.windup(mob);
		forja$windupTotal = forja$windup;
		dev.forja.ai.MobDefense.spendCounter(mob);
		forja$target = target;
		forja$feint = mob.getRandom().nextDouble() < (mob instanceof dev.forja.entity.ForgeAutomaton
			? dev.forja.ai.Aggression.adaptiveFeintChance(target) : dev.forja.ai.Aggression.feintChance(mob, target));
		forja$holdStill(target);
		CombatFeedback.telegraph(mob, forja$windup);
	}

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

	/** Peso (combat/Weight): the more it carries, the longer it waits before the next blow. */
	@Inject(method = "resetAttackCooldown", at = @At("TAIL"))
	private void forja$heavyWait(CallbackInfo ci) {
		if (CombatConfig.get().enabled) {
			ticksUntilNextAttack = Math.round(ticksUntilNextAttack * (1.0F + dev.forja.combat.Weight.INTERVAL_PER_KG * dev.forja.combat.Weight.carried(mob)));
		}
	}

	@Inject(method = "stop", at = @At("TAIL"))
	private void forja$onStop(CallbackInfo ci) {
		forja$reset();
	}

	@Unique
	private void forja$reset() {
		AttackTokens.release(forja$target, mob);
		forja$target = null;
		forja$windup = 0;
	}
}
