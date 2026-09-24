package dev.forja.combat;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The lunge: at middle range the mob crouches (the warning), leaps at the player and, if it lands,
 * hits and holds them back. Step aside during the crouch and it sails past. Elites keep their own
 * leap strike and never get this one.
 */
public class LungeGoal extends Goal {
	private static final int MAX_FLIGHT_TICKS = 25;

	private final Monster mob;
	private LivingEntity target;
	private int windup;
	private int flight;
	private boolean hit;
	private long nextLunge;

	public LungeGoal(Monster mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.enabled || !cfg.zombieLunge || !mob.onGround() || dev.forja.world.Elites.isElite(mob)) return false;
		long now = mob.level().getGameTime();
		if (now < nextLunge || Posture.isStaggered(mob, now)) return false;
		if (!(mob.getTarget() instanceof Player player) || !player.isAlive() || player.isCreative() || player.isSpectator()) {
			return false;
		}
		double distance = mob.distanceTo(player);
		if (distance < cfg.lungeMinDistance || distance > cfg.lungeMaxDistance) return false;
		if (!mob.getSensing().hasLineOfSight(player)) return false;
		if (!AttackTokens.tryAcquire(player, mob, cfg.maxSimultaneousAttackers)) return false;
		target = player;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return target != null && target.isAlive() && (windup > 0 || flight > 0);
	}

	@Override
	public void start() {
		windup = Math.max(1, CombatConfig.get().lungeWindupTicks);
		flight = 0;
		hit = false;
		mob.getNavigation().stop();
		CombatFeedback.lungeTelegraph(mob);
	}

	@Override
	public void stop() {
		CombatConfig cfg = CombatConfig.get();
		AttackTokens.release(target, mob);
		target = null;
		windup = 0;
		flight = 0;
		int spread = Math.max(0, cfg.lungeCooldownMaxTicks - cfg.lungeCooldownMinTicks);
		nextLunge = mob.level().getGameTime() + cfg.lungeCooldownMinTicks + mob.getRandom().nextInt(spread + 1);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		CombatConfig cfg = CombatConfig.get();
		if (Posture.isStaggered(mob, mob.level().getGameTime())) {
			windup = 0;
			flight = 0;
			return;
		}
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (windup > 0) {
			mob.getNavigation().stop();
			if (--windup == 0) leap(cfg);
			return;
		}
		flight++;
		if (!hit && mob.getBoundingBox().inflate(0.4).intersects(target.getBoundingBox()) && mob.level() instanceof ServerLevel level) {
			hit = true;
			mob.swing(InteractionHand.MAIN_HAND);
			if (mob.doHurtTarget(level, target)) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.lungeGrabTicks, 1));
			}
		}
		if ((flight > 3 && mob.onGround()) || flight > MAX_FLIGHT_TICKS) {
			flight = 0;
		}
	}

	private void leap(CombatConfig cfg) {
		Vec3 toTarget = target.position().subtract(mob.position());
		Vec3 dir = new Vec3(toTarget.x, 0.0, toTarget.z).normalize();
		mob.setDeltaMovement(dir.x * cfg.lungeSpeed, cfg.lungeLift, dir.z * cfg.lungeSpeed);
		mob.hurtMarked = true;
		flight = 1;
		CombatStats.record(mob, CombatStats.LUNGE);
	}
}
