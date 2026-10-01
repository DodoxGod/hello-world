package dev.forja.entity.ai;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A run-up and a jump, and whatever is standing where it lands pays for it.
 *
 * <p>This is bolted onto vanilla monsters at spawn time rather than written into a class of our own,
 * because the mobs that use it (an elite, a raider captain) are ordinary zombies and vindicators that
 * we dressed up. A goal is the only way to give one of those a move without a mixin.
 */
public class LeapStrikeGoal extends Goal {
	/** How long it stays in the air before the goal gives up on it landing. */
	private static final int FLIGHT = 24;

	private final Mob mob;
	private final double min;
	private final double max;
	private final int cooldown;
	private final float damage;
	private final double radius;
	private final ParticleOptions trail;
	private final SoundEvent call;
	private final Set<UUID> hit = new HashSet<>();

	private int wait;
	private int flying;

	public LeapStrikeGoal(Mob mob, double min, double max, int cooldown, float damage, double radius,
		ParticleOptions trail, SoundEvent call) {
		this.mob = mob;
		this.min = min;
		this.max = max;
		this.cooldown = cooldown;
		this.damage = damage;
		this.radius = radius;
		this.trail = trail;
		this.call = call;
		this.wait = cooldown / 2;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		// The wait has to come down here: a goal that is not running never gets its tick called.
		if (this.wait > 0) {
			this.wait--;
			return false;
		}
		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive() || !this.mob.onGround() || this.mob.isInWater()) {
			return false;
		}
		double distance = this.mob.distanceTo(target);
		return distance >= this.min && distance <= this.max && this.mob.hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		return this.flying > 0;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}
		this.hit.clear();
		this.flying = FLIGHT;
		Vec3 aim = target.position().subtract(this.mob.position());
		double reach = Math.max(1.0, aim.horizontalDistance());
		// Enough lift to clear a fence and enough push to cover the gap it decided to jump.
		this.mob.setDeltaMovement(aim.x / reach * 0.62, 0.48, aim.z / reach * 0.62);
		this.mob.hurtMarked = true;
		if (this.mob.level() instanceof ServerLevel level) {
			level.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(), this.call, SoundSource.HOSTILE, 1.6F, 0.8F);
		}
	}

	@Override
	public void tick() {
		this.flying--;
		if (!(this.mob.level() instanceof ServerLevel level)) {
			return;
		}
		level.sendParticles(this.trail, this.mob.getX(), this.mob.getY(0.6), this.mob.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
		// It counts as landed once it is back on the floor, but not on the first tick, when it still is.
		if (this.mob.onGround() && this.flying < FLIGHT - 3) {
			this.land(level);
			this.flying = 0;
		}
	}

	private void land(ServerLevel level) {
		level.sendParticles(this.trail, this.mob.getX(), this.mob.getY(0.1), this.mob.getZ(), 30, this.radius * 0.4, 0.1, this.radius * 0.4, 0.06);
		level.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
			net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 1.6F);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, this.mob.getBoundingBox().inflate(this.radius),
			other -> other != this.mob && other.isAlive() && !this.mob.isAlliedTo(other))) {
			if (!this.hit.add(victim.getUUID())) {
				continue;
			}
			victim.invulnerableTime = 0;
			victim.hurtServer(level, this.mob.damageSources().mobAttack(this.mob), this.damage);
			Vec3 away = victim.position().subtract(this.mob.position()).normalize();
			double hold = 1.0 - dev.forja.upgrade.Upgrades.anchor(victim);
			victim.push(away.x * 0.5 * hold, 0.42 * hold, away.z * 0.5 * hold);
			victim.hurtMarked = true;
		}
	}

	@Override
	public void stop() {
		this.flying = 0;
		this.wait = this.cooldown;
	}
}
