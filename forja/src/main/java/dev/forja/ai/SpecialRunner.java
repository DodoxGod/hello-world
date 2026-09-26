package dev.forja.ai;

import java.util.List;

import dev.forja.combat.CombatStats;
import dev.forja.combat.Posture;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Runs one mob's specials: which is under way and in which phase, and when each is ready again. Driven
 * by {@link SpecialGoal} under the rules and by {@link TacticGoal} under a network, so a special behaves
 * the same whoever asked for it.
 */
public final class SpecialRunner {
	/** A special under way: the move, its target, where it will land (if it marks a spot), its phase. */
	public static final class Run {
		public final Special special;
		public final Player target;
		public Vec3 mark;
		public int left;
		public int after;
		public boolean hit;

		Run(Special special, Player target) {
			this.special = special;
			this.target = target;
		}
	}

	private final Mob mob;
	private final List<Special> moveset;
	private final long[] readyAt;
	private Run run;

	public SpecialRunner(Mob mob, List<Special> moveset) {
		this.mob = mob;
		this.moveset = moveset;
		this.readyAt = new long[moveset.size()];
	}

	public List<Special> moveset() {
		return this.moveset;
	}

	public boolean active() {
		return this.run != null;
	}

	/** Whether the special in slot k (0-based) is ready: off cooldown. */
	public boolean ready(int k) {
		return k >= 0 && k < this.moveset.size() && this.mob.level().getGameTime() >= this.readyAt[k];
	}

	/** Share of its cooldown still to run, 0 (ready) to 1. */
	public double cooldownLeft(int k) {
		if (k < 0 || k >= this.moveset.size()) {
			return 1.0;
		}
		Special special = this.moveset.get(k);
		long left = this.readyAt[k] - this.mob.level().getGameTime();
		return left <= 0 ? 0.0 : Math.min(1.0, left / (double) Math.max(1, special.cooldownMax));
	}

	/** Whether special k could start now. */
	public boolean available(int k, Player target) {
		return this.ready(k) && this.moveset.get(k).canStart(this.mob, target);
	}

	/** The warning under way, as progress 0 to 1; -1 when none. */
	public double warningProgress() {
		if (this.run == null || this.run.left <= 0) {
			return -1.0;
		}
		return 1.0 - this.run.left / (double) Math.max(1, this.run.special.windup);
	}

	/** Starts special k if it can; returns whether it did. */
	public boolean start(int k, Player target) {
		if (this.run != null || !this.available(k, target) || Posture.isStaggered(this.mob, this.mob.level().getGameTime())) {
			return false;
		}
		Special special = this.moveset.get(k);
		this.run = new Run(special, target);
		this.run.left = Math.max(0, special.windup);
		this.mob.getNavigation().stop();
		special.warn(this.mob, target, this.run);
		CombatStats.record(this.mob, special.id);
		if (this.run.left == 0) {
			this.release();
		}
		return true;
	}

	/** Under the rules: maybe start one of the ready specials that can start. */
	public boolean ruleStart(Player target) {
		for (int k = 0; k < this.moveset.size(); k++) {
			if (this.available(k, target) && this.mob.getRandom().nextDouble() < this.moveset.get(k).ruleChance) {
				return this.start(k, target);
			}
		}
		return false;
	}

	/** One tick of the special under way. */
	public void tick() {
		Run current = this.run;
		if (current == null) {
			return;
		}
		if (!current.target.isAlive() || Posture.isStaggered(this.mob, this.mob.level().getGameTime())) {
			this.end();
			return;
		}
		this.mob.getLookControl().setLookAt(current.target, 30.0F, 30.0F);
		if (current.left > 0) {
			current.special.warning(this.mob, current.target, current, current.left);
			if (--current.left == 0) {
				this.release();
			}
			return;
		}
		if (!current.special.follow(this.mob, current.target, current, ++current.after)) {
			this.end();
		}
	}

	private void release() {
		this.run.special.release(this.mob, this.run.target, this.run);
		if (!this.run.special.follow(this.mob, this.run.target, this.run, 0)) {
			this.end();
		}
	}

	/** Drops whatever is under way (the goal stopped, the mob was staggered) and starts its cooldown. */
	public void end() {
		if (this.run == null) {
			return;
		}
		int k = this.moveset.indexOf(this.run.special);
		Special special = this.run.special;
		int spread = Math.max(0, special.cooldownMax - special.cooldownMin);
		this.readyAt[k] = this.mob.level().getGameTime() + special.cooldownMin + (spread > 0 ? this.mob.getRandom().nextInt(spread + 1) : 0);
		this.run = null;
	}
}
