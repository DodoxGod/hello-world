package dev.forja.ai;

import java.util.EnumSet;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Under the rules, the specials start on their own: each tick a ready one that can start has a small
 * chance to. Above vanilla's attack goals and below the tactic executor. Under a network this goal stands
 * aside: the executor drives the same runner when the network asks.
 */
public final class SpecialGoal extends Goal {
	private final Mob mob;
	private final MobMind mind;

	public SpecialGoal(Mob mob, MobMind mind) {
		this.mob = mob;
		this.mind = mind;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		SpecialRunner runner = this.mind.specials;
		if (runner == null || this.mind.networked || this.mind.target == null || !dev.forja.combat.CombatConfig.get().enabled) {
			return false;
		}
		return runner.active() || runner.ruleStart(this.mind.target);
	}

	@Override
	public boolean canContinueToUse() {
		return this.mind.specials != null && this.mind.specials.active() && !this.mind.networked;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.mind.specials.tick();
	}

	@Override
	public void stop() {
		if (this.mind.specials != null && !this.mind.networked) {
			this.mind.specials.end();
		}
	}
}
