package dev.forja.entity.ai;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * Walk toward the nearest one of your own side, and stay near it.
 *
 * <p>Written for the Yunque Andante, which is a healer with no reason to be anywhere. It has no target
 * goal at all — on purpose, it is not supposed to come after you — so what it actually did was wander
 * off on its own and mend nothing, which made the one mob in the mod whose whole job is other mobs the
 * one least likely to be near any.
 *
 * <p>So it follows. Not a player and not the nearest thing that moves: <b>the nearest one it would
 * mend</b>, preferring one that is already hurt. That puts it where it is useful without giving it any
 * interest in the fight, and it means killing the thing it is following is a way of dealing with it.
 */
public class FollowOursGoal extends Goal {
	private final Mob mob;
	private final Predicate<Mob> ours;
	private final double speed;
	private final double range;
	private final double keep;

	private Mob following;
	private int recheck;

	/**
	 * @param range how far it looks
	 * @param keep how close it tries to stay, so a group does not end up standing inside each other
	 */
	public FollowOursGoal(Mob mob, Predicate<Mob> ours, double speed, double range, double keep) {
		this.mob = mob;
		this.ours = ours;
		this.speed = speed;
		this.range = range;
		this.keep = keep;
		this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (this.recheck-- > 0) {
			return false;
		}
		this.recheck = 20;
		this.following = this.pick();
		return this.following != null && this.mob.distanceTo(this.following) > this.keep;
	}

	@Override
	public boolean canContinueToUse() {
		return this.following != null && this.following.isAlive()
			&& this.mob.distanceTo(this.following) > this.keep * 0.6
			&& this.mob.distanceTo(this.following) < this.range * 1.5;
	}

	@Override
	public void stop() {
		this.following = null;
		this.mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (this.following == null) {
			return;
		}
		this.mob.getLookControl().setLookAt(this.following, 10.0F, this.mob.getMaxHeadXRot());
		if (this.mob.tickCount % 10 == 0) {
			this.mob.getNavigation().moveTo(this.following, this.speed);
		}
	}

	/** The nearest one of ours, with the hurt ones first — that is where the work is. */
	private Mob pick() {
		AABB near = new AABB(this.mob.position(), this.mob.position()).inflate(this.range);
		List<Mob> ours = this.mob.level().getEntitiesOfClass(Mob.class, near,
			other -> other != this.mob && other.isAlive() && this.ours.test(other));
		if (ours.isEmpty()) {
			return null;
		}
		return ours.stream()
			.min(Comparator
				.comparingInt((Mob other) -> other.getHealth() < other.getMaxHealth() ? 0 : 1)
				.thenComparingDouble(this.mob::distanceToSqr))
			.orElse(null);
	}
}
