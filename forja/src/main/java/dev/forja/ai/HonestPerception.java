package dev.forja.ai;

import dev.forja.combat.CombatConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Honest perception (docs/red_mob_v4_diseno.md §4.5, part of M4; Andy, 2026-09-29): a monster that has lost sight of
 * its player goes to where it last perceived them, not to where they really are. Vanilla's navigation paths to the
 * real position through walls, and a mob that walks round a corner straight to a player it has never seen there is
 * cheating. This only moves the goal of its walk: it never breaks or builds anything, so a closed base stays closed.
 *
 * <p>Only for a mob with a mind that has perceived its player at least once, or heard them (Perception.estimate: the
 * newer of the two): one sent after a player it never saw nor heard (a siege, a call for help) has no estimate and
 * keeps going as before. Once there it searches (TacticGoal.search, BUSCAR) or lies in wait (EMBOSCAR).
 */
public final class HonestPerception {
	/** Ticks without perceiving the player after which its movement follows the last known position instead. */
	public static final int LOST_AFTER = 20;
	/** How close to the last known position counts as there: it stops and waits. */
	public static final double ARRIVED = 2.0;
	/** Closer than this the player is felt, whatever the eyes say: an attack on a player it touches is never held back. */
	public static final double TOUCH = 1.5;

	private HonestPerception() {
	}

	/** Whether this mob's movement must follow the last known position of its player rather than the player. */
	public static boolean lost(MobMind mind, long now) {
		if (mind == null || !CombatConfig.get().iaPercepcionHonesta) {
			return false;
		}
		Player target = mind.target;
		if (target == null || Perception.estimate(mind) == null || now - mind.lastSeenAt < LOST_AFTER || mind.mob.getTarget() != target) {
			return false;
		}
		// A boss fights in its own arena by its own script: it is not hunting anybody down.
		return mind.mob.distanceTo(target) >= TOUCH && !dev.forja.difficulty.Bosses.isBoss(mind.mob);
	}

	public static boolean lost(Mob mob) {
		MobMind mind = MobAi.mind(mob);
		return mind != null && lost(mind, mob.level().getGameTime());
	}

	/** Whether it has got to the last known position (then it holds still there). */
	public static boolean arrived(MobMind mind) {
		net.minecraft.world.phys.Vec3 estimate = Perception.estimate(mind);
		return estimate != null && mind.mob.distanceToSqr(estimate.x, mind.mob.getY(), estimate.z) < ARRIVED * ARRIVED;
	}
}
