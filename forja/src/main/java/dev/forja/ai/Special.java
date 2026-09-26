package dev.forja.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * One special attack of a mob's moveset: always warned, then released, then (for some) carried through
 * for a few ticks. The same object serves the rules (which start it on their own) and a network (whose
 * {@code especial} head asks for it); its numbers are in docs/COMBATE_ESPECIFICACION.md, "Movesets", so
 * the simulator can reproduce it.
 */
public abstract class Special {
	/** Its name in the specification and the network files. */
	public final String id;
	/** Ticks of warning before the release. */
	public final int windup;
	/** Cooldown after it ends, a random number of ticks in [min, max]. */
	public final int cooldownMin;
	public final int cooldownMax;
	/** Chance per tick the rules start it when it is ready and can start. */
	public final double ruleChance;

	protected Special(String id, int windup, int cooldownMin, int cooldownMax, double ruleChance) {
		this.id = id;
		this.windup = windup;
		this.cooldownMin = cooldownMin;
		this.cooldownMax = cooldownMax;
		this.ruleChance = ruleChance;
	}

	/** Whether it can start now against this target (range, sight, footing...). */
	public abstract boolean canStart(Mob mob, Player target);

	/** The start of the warning: particles, sound, the pose; it may note where it will land. */
	public void warn(Mob mob, Player target, SpecialRunner.Run run) {
	}

	/** Each tick of the warning (the mob holds still unless this moves it). */
	public void warning(Mob mob, Player target, SpecialRunner.Run run, int left) {
	}

	/** The end of the warning: the move itself. */
	public abstract void release(Mob mob, Player target, SpecialRunner.Run run);

	/**
	 * Each tick after the release, for moves that carry on (a leap in flight, a charge).
	 *
	 * @return false when it is over
	 */
	public boolean follow(Mob mob, Player target, SpecialRunner.Run run, int tick) {
		return false;
	}
}
