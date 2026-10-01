package com.dodoxgod.filo.ai;

import com.dodoxgod.filo.combat.AttackTokens;
import com.dodoxgod.filo.combat.CombatFeedback;
import com.dodoxgod.filo.combat.CombatStats;
import com.dodoxgod.filo.combat.PostureManager;
import com.dodoxgod.filo.config.FiloConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Embestida: a media distancia, el mob se agacha unos ticks (aviso), salta hacia el jugador y,
 * si le alcanza, le golpea y le frena ("agarre"). Si te apartas durante el aviso, falla.
 */
public class LungeGoal extends Goal {
	private static final int MAX_FLIGHT_TICKS = 25;

	private final HostileEntity mob;
	private LivingEntity target;
	private int windup;
	private int flight;
	private boolean hit;
	private long nextLunge;

	public LungeGoal(HostileEntity mob) {
		this.mob = mob;
		setControls(EnumSet.of(Control.MOVE, Control.JUMP, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		FiloConfig.Mobs cfg = FiloConfig.get().mobs;
		if (!cfg.zombieLunge || !mob.isOnGround()) return false;
		long now = mob.getWorld().getTime();
		if (now < nextLunge || PostureManager.isStaggered(mob, now)) return false;
		if (!(mob.getTarget() instanceof PlayerEntity player) || !player.isAlive()
				|| player.isCreative() || player.isSpectator()) {
			return false;
		}
		double distance = mob.distanceTo(player);
		if (distance < cfg.lungeMinDistance || distance > cfg.lungeMaxDistance) return false;
		if (!mob.getVisibilityCache().canSee(player)) return false;
		if (!AttackTokens.tryAcquire(player, mob, cfg.maxSimultaneousAttackers)) return false;
		target = player;
		return true;
	}

	@Override
	public boolean shouldContinue() {
		return target != null && target.isAlive() && (windup > 0 || flight > 0);
	}

	@Override
	public void start() {
		windup = Math.max(1, FiloConfig.get().mobs.lungeWindupTicks);
		flight = 0;
		hit = false;
		mob.getNavigation().stop();
		CombatFeedback.lungeTelegraph(mob);
	}

	@Override
	public void stop() {
		FiloConfig.Mobs cfg = FiloConfig.get().mobs;
		AttackTokens.release(target, mob);
		target = null;
		windup = 0;
		flight = 0;
		int spread = Math.max(0, cfg.lungeCooldownMaxTicks - cfg.lungeCooldownMinTicks);
		nextLunge = mob.getWorld().getTime() + cfg.lungeCooldownMinTicks + mob.getRandom().nextInt(spread + 1);
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		FiloConfig.Mobs cfg = FiloConfig.get().mobs;
		if (PostureManager.isStaggered(mob, mob.getWorld().getTime())) {
			windup = 0;
			flight = 0;
			return;
		}
		mob.getLookControl().lookAt(target, 30.0f, 30.0f);

		if (windup > 0) {
			mob.getNavigation().stop();
			if (--windup == 0) leap(cfg);
			return;
		}

		flight++;
		if (!hit && mob.getBoundingBox().expand(0.4).intersects(target.getBoundingBox())) {
			hit = true;
			mob.swingHand(Hand.MAIN_HAND);
			if (mob.tryAttack(target)) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, cfg.lungeGrabTicks, 1));
			}
		}
		if ((flight > 3 && mob.isOnGround()) || flight > MAX_FLIGHT_TICKS) {
			flight = 0;
		}
	}

	private void leap(FiloConfig.Mobs cfg) {
		Vec3d toTarget = target.getPos().subtract(mob.getPos());
		Vec3d dir = new Vec3d(toTarget.x, 0.0, toTarget.z).normalize();
		mob.setVelocity(dir.x * cfg.lungeSpeed, cfg.lungeLift, dir.z * cfg.lungeSpeed);
		mob.velocityModified = true;
		flight = 1;
		CombatStats.record(mob, CombatStats.LUNGE);
	}
}
