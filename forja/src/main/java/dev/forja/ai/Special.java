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
	/**
	 * A special of red_mob_v4's (the knockback arrow, the spider's swipe, the hook: docs/red_mob_v4_diseno.md §4.6):
	 * a v1..v3 network never trained with it, so for a mob on one of those it is not there at all (SpecialRunner.hideV4).
	 * The rules and a v4 network have it.
	 */
	public final boolean v4;

	protected Special(String id, int windup, int cooldownMin, int cooldownMax, double ruleChance) {
		this(id, windup, cooldownMin, cooldownMax, ruleChance, false);
	}

	protected Special(String id, int windup, int cooldownMin, int cooldownMax, double ruleChance, boolean v4) {
		this.id = id;
		this.windup = windup;
		this.cooldownMin = cooldownMin;
		this.cooldownMax = cooldownMax;
		this.ruleChance = ruleChance;
		this.v4 = v4;
	}

	/**
	 * An empty slot, to put a v4 special at the slot the contract gives it (the third: especial 3) in a moveset with
	 * fewer before it. It never starts and reads as no special at all.
	 */
	public static final Special NONE = new Special("ninguno", 0, 0, 0, 0.0) {
		@Override
		public boolean canStart(Mob mob, Player target) {
			return false;
		}

		@Override
		public void release(Mob mob, Player target, SpecialRunner.Run run) {
		}
	};

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
