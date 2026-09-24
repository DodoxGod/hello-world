package com.dodoxgod.filo.mixin;

import com.dodoxgod.filo.combat.AttackTokens;
import com.dodoxgod.filo.combat.CombatFeedback;
import com.dodoxgod.filo.combat.PostureManager;
import com.dodoxgod.filo.config.FiloConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ataques con aviso contra jugadores: antes de golpear, el mob se para, suelta partículas y un
 * sonido, y espera unos ticks. Si en ese tiempo te apartas, esquivas o levantas el escudo, el golpe
 * falla o se bloquea. Además, solo unos pocos mobs pueden atacar a la vez al mismo jugador.
 * Contra otros mobs (aldeanos, gólems...) atacan como en vanilla.
 */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackGoalMixin {
	@Shadow
	@Final
	protected PathAwareEntity mob;

	@Shadow
	protected abstract boolean canAttack(LivingEntity target);

	@Shadow
	protected abstract void resetCooldown();

	@Unique
	private int filo$windup;

	@Unique
	private LivingEntity filo$target;

	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void filo$telegraphedAttack(LivingEntity target, CallbackInfo ci) {
		FiloConfig cfg = FiloConfig.get();
		if (!cfg.mobs.telegraph || !(target instanceof PlayerEntity) && filo$windup == 0) return;
		ci.cancel();

		long now = mob.getWorld().getTime();
		if (PostureManager.isStaggered(mob, now)) {
			filo$reset();
			return;
		}

		if (filo$windup > 0) {
			mob.getNavigation().stop();
			mob.getLookControl().lookAt(target, 30.0f, 30.0f);
			if (--filo$windup > 0) return;

			mob.swingHand(Hand.MAIN_HAND);
			double allowed = mob.getWidth() * 2.0 + target.getWidth() * 0.5 + cfg.mobs.strikeReachBonus;
			if (mob.distanceTo(target) <= allowed && mob.getVisibilityCache().canSee(target)) {
				mob.tryAttack(target);
			}
			resetCooldown();
			filo$reset();
			return;
		}

		if (!canAttack(target)) return;
		if (!AttackTokens.tryAcquire(target, mob, cfg.mobs.maxSimultaneousAttackers)) return;
		filo$windup = Math.max(1, cfg.mobs.windupTicks);
		filo$target = target;
		mob.getNavigation().stop();
		CombatFeedback.telegraph(mob);
	}

	@Inject(method = "stop", at = @At("TAIL"))
	private void filo$onStop(CallbackInfo ci) {
		filo$reset();
	}

	@Unique
	private void filo$reset() {
		AttackTokens.release(filo$target, mob);
		filo$target = null;
		filo$windup = 0;
	}
}
