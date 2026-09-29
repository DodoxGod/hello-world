package dev.forja.entity.ai;

import java.util.EnumSet;

import dev.forja.entity.FallenSmith;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

/**
 * The smith's apprentices stand by their master.
 *
 * <p>Andy, 2026-09-28: "los aprendices no atacan a lo que lo llegan a atacar". They were four elite
 * wither skeletons with nothing tying them to him: a warden could take him apart in front of them and
 * they would carry on looking for a player. Now:
 *
 * <ul>
 *   <li>Whoever hurts him — a player, a golem, a warden, a wolf — becomes their target at once, over
 *       whatever they were doing. This is the goal's first priority.
 *   <li>With nothing to fight of their own — nothing at all, or only something they spotted by
 *       themselves, such as a golem across the room — they join in on whatever he is fighting. A
 *       player they are after, or whoever last hit them, they keep.
 *   <li>If one of them is hit by somebody else while it is defending him, it lets go, so its own
 *       retaliation (the vanilla hurt-by goal, one step lower) can answer: a player who turns on an
 *       apprentice gets that apprentice back.
 * </ul>
 *
 * <p>Nothing of Forja's side is ever picked ({@link dev.forja.world.Truce}), so they never go for each
 * other or for him. The master is found rather than remembered — the nearest smith within
 * {@link #REACH} — so the link survives the world being saved and loaded, which a stored reference
 * would not.
 */
public class DefendMasterGoal extends TargetGoal {
	/** How far an apprentice looks for its master. */
	public static final double REACH = 32.0;

	private static final TargetingConditions DEFENDING = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

	private FallenSmith master;
	private int lookAgain;
	/** The master's record of the last blow on him that this goal has already answered. */
	private int answeredAt = Integer.MIN_VALUE;
	/** The apprentice's own last-hurt stamp when it took the job, so a blow on itself afterwards shows. */
	private int ownHurtAt;
	private LivingEntity picked;
	private int pickedAt;

	public DefendMasterGoal(Mob apprentice) {
		super(apprentice, false);
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	/** The smith this apprentice is serving, if he is still about. */
	public FallenSmith master() {
		if (this.master != null && (!this.master.isAlive() || this.master.level() != this.mob.level()
			|| this.master.distanceToSqr(this.mob) > REACH * REACH)) {
			this.master = null;
		}
		if (this.master == null && --this.lookAgain <= 0) {
			this.lookAgain = 20;
			this.master = this.mob.level().getEntitiesOfClass(FallenSmith.class, this.mob.getBoundingBox().inflate(REACH),
				FallenSmith::isAlive).stream().min(java.util.Comparator.comparingDouble(smith -> smith.distanceToSqr(this.mob))).orElse(null);
		}
		return this.master;
	}

	@Override
	public boolean canUse() {
		FallenSmith smith = this.master();
		if (smith == null) {
			return false;
		}
		LivingEntity attacker = smith.lastAttacker();
		int at = smith.lastAttackedAt();
		if (attacker != null && at != this.answeredAt && smith.tickCount - at <= FallenSmith.FOCUS_TICKS && this.fair(attacker)) {
			this.picked = attacker;
			this.pickedAt = at;
			return true;
		}
		LivingEntity his = smith.getTarget();
		LivingEntity mine = this.mob.getTarget();
		if (his != null && his != mine && (mine == null || this.ownPick(mine)) && this.fair(his)) {
			this.picked = his;
			this.pickedAt = this.answeredAt;
			return true;
		}
		return false;
	}

	/**
	 * Whether what it is fighting is only something it spotted by itself — not a player, and not
	 * whoever last hit it. That gives way to the master's fight; a player or its own attacker does not.
	 */
	private boolean ownPick(LivingEntity mine) {
		return !(mine instanceof net.minecraft.world.entity.player.Player) && mine != this.mob.getLastHurtByMob();
	}

	/** Something an apprentice may go for: alive, attackable, and not one of Forja's own. */
	private boolean fair(LivingEntity target) {
		return target != this.mob && target.isAlive() && !dev.forja.world.Truce.ours(target) && this.canAttack(target, DEFENDING);
	}

	@Override
	public void start() {
		this.mob.setTarget(this.picked);
		this.targetMob = this.picked;
		this.answeredAt = this.pickedAt;
		this.ownHurtAt = this.mob.getLastHurtByMobTimestamp();
		this.unseenMemoryTicks = 300;
		super.start();
	}

	@Override
	public boolean canContinueToUse() {
		// Struck by somebody else while it is busy with its master's fight: let its own retaliation have it.
		LivingEntity hurtBy = this.mob.getLastHurtByMob();
		if (hurtBy != null && this.mob.getLastHurtByMobTimestamp() != this.ownHurtAt && hurtBy != this.mob.getTarget()) {
			return false;
		}
		// A fresh blow on the master outranks the one it is answering.
		FallenSmith smith = this.master();
		if (smith != null && smith.lastAttacker() != null && smith.lastAttackedAt() != this.answeredAt
			&& smith.lastAttacker() != this.mob.getTarget() && this.fair(smith.lastAttacker())) {
			return false;
		}
		return super.canContinueToUse();
	}

	@Override
	public void stop() {
		super.stop();
		this.picked = null;
	}
}
