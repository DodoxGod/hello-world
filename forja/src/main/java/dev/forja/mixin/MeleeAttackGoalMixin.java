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
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla melee monsters warn a player before they strike: they stop, flash and wait a few ticks, and
 * a player who steps back, dodges or raises a shield in time is not hit. Only a few of them swing at
 * the same player at once. Forja's own monsters keep their Windup telegraphs; against anything that
 * is not a player, mobs fight as they always have (a villager running away would never be caught).
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

	@Unique
	private LivingEntity forja$target;

	/** This blow is a fake: dropped halfway through its warning (against players who parry a lot). */
	@Unique
	private boolean forja$feint;

	@Inject(method = "checkAndPerformAttack", at = @At("HEAD"), cancellable = true)
	private void forja$telegraphedAttack(LivingEntity target, CallbackInfo ci) {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.enabled || !cfg.telegraph || forja$windup == 0 && !(target instanceof Player)
			|| !"minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace())
				&& !(mob instanceof dev.forja.entity.ForgeAutomaton)) {
			return;
		}
		ci.cancel();
		if (Posture.isStaggered(mob, mob.level().getGameTime())) {
			forja$reset();
			return;
		}
		if (forja$windup > 0) {
			forja$holdStill(target);
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (forja$feint && forja$windup <= Math.max(1, cfg.windupTicks) / 2) {
				// The fake: it wound up, the player raised the shield for it, and nothing comes.
				forja$feint = false;
				dev.forja.combat.CombatStats.record(mob, dev.forja.combat.CombatStats.FEINT);
				resetAttackCooldown();
				forja$reset();
				return;
			}
			if (--forja$windup > 0) return;
			mob.swing(InteractionHand.MAIN_HAND);
			double allowed = mob.getBbWidth() * 2.0 + target.getBbWidth() * 0.5 + cfg.strikeReachBonus;
			if (mob.distanceTo(target) <= allowed && mob.getSensing().hasLineOfSight(target) && mob.level() instanceof ServerLevel level) {
				mob.doHurtTarget(level, target);
			}
			resetAttackCooldown();
			dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
			if (mind != null) {
				mind.lastStrike = mob.level().getGameTime();
			}
			forja$reset();
			return;
		}
		if (!canPerformAttack(target) || !AttackTokens.tryAcquire(target, mob, dev.forja.ai.Aggression.maxAttackers(target))) return;
		forja$windup = dev.forja.ai.MobDefense.windup(mob);
		dev.forja.ai.MobDefense.spendCounter(mob);
		forja$target = target;
		forja$feint = mob.getRandom().nextDouble() < (mob instanceof dev.forja.entity.ForgeAutomaton
			? dev.forja.ai.Aggression.adaptiveFeintChance(target) : dev.forja.ai.Aggression.feintChance(mob, target));
		forja$holdStill(target);
		CombatFeedback.telegraph(mob);
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
