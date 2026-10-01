package dev.forja.ai;

import dev.forja.combat.CombatConfig;
import dev.forja.combat.Weight;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

/**
 * "Aviso en movimiento" (Andy, 2026-09-30: "cuando los mobs preparan un ataque ya no se pueden mover, por lo que es muy
 * fácil esquivarlos"). A monster warning a melee blow used to stand still for the whole warning, and the blow lands only
 * if the target is still within {@link Reach#landing} at its end: one step back dodged every blow. Now it keeps following
 * its target through the warning, turning to face it, at its approach speed times {@link #factor} and never running
 * (MobSprint). The warning is as long as ever and lands by the same rule, so what dodges it now is getting further away
 * than it can follow (running, a dodge roll), or a shield, a parry or a block.
 *
 * <p>It stops following, and stands where it is, once it is within {@link #pressAt} of the target (it does not shove
 * into the player's face); it starts again as soon as the target moves off. Used by both warned-blow paths: the melee
 * goal of the rules and of Forja's own monsters (MeleeAttackGoalMixin) and the executor's blow (TacticGoal), and by the
 * enderman's blow after its blink. Specials with a wind-up of their own (the lunge, the charge, the bosses' moves) keep
 * theirs.
 *
 * <p>The training simulator mirrors this: docs/red_mob_v4_mod_estado.md, "Aviso en movimiento".
 */
public final class WindupChase {
	/** Centre to centre, it stops pressing in this far inside the distance its blow lands from. */
	public static final double PRESS_MARGIN = 0.8;
	/** Never closer than the two half widths and this, whatever the reach. */
	public static final double PRESS_MIN_GAP = 0.1;
	/** A path whose end is this far from the target is stale... */
	public static final double STALE = 1.0;
	/** ...and is made again on the next tick that is a multiple of this (per mob): at most one path in 3 ticks. */
	public static final int REPATH_TICKS = 3;
	/** Within this, flat, and this much up or down, it walks straight at the target instead of along a path. */
	public static final double STRAIGHT = 4.0;
	public static final double STRAIGHT_RISE = 0.6;

	private WindupChase() {
	}

	/** Whether warning monsters follow at all (CombatConfig windupChase). */
	public static boolean on() {
		CombatConfig cfg = CombatConfig.get();
		return cfg.enabled && cfg.windupChase;
	}

	/** The share of its approach speed modifier it keeps while warning: windupChaseSpeed × Weight.chaseFactor. */
	public static double factor(Mob mob) {
		return CombatConfig.get().windupChaseSpeed * Weight.chaseFactor(mob);
	}

	/** Centre to centre, how close it presses in: its landing distance less {@link #PRESS_MARGIN}. */
	public static double pressAt(Mob mob, LivingEntity target) {
		return Math.max((mob.getBbWidth() + target.getBbWidth()) * 0.5 + PRESS_MIN_GAP, Reach.landing(mob, target) - PRESS_MARGIN);
	}

	/**
	 * One tick of a warning: follow the target at {@code approach} × {@link #factor}, or not.
	 *
	 * @param approach the speed modifier it walks in at (the melee goal's own, 1 for the executor)
	 * @return whether it is following; false when it should stand still (close enough, no path, or following is off)
	 */
	public static boolean follow(Mob mob, LivingEntity target, double approach) {
		if (!on() || mob.distanceTo(target) <= pressAt(mob, target) || ownMoveCharging(mob)) {
			return false;
		}
		double speed = approach * factor(mob);
		PathNavigation navigation = mob.getNavigation();
		// Close, on the same level and in sight: straight at it, as a path to a block would stop up to a block short of a
		// player moving off and be made again only once they are a block from its end. Never a step into lava or off a drop.
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double flat = Math.hypot(dx, dz);
		if (flat <= STRAIGHT && Math.abs(target.getY() - mob.getY()) <= STRAIGHT_RISE && mob.onGround()
			&& mob.getSensing().hasLineOfSight(target)
			&& !Terrain.danger(mob.level(), mob.getX() + dx / flat, mob.getZ() + dz / flat, mob.getY())) {
			navigation.stop();
			mob.getMoveControl().setWantedPosition(target.getX(), mob.getY(), target.getZ(), speed);
			return true;
		}
		Path path = navigation.getPath();
		boolean stale = navigation.isDone() || path == null
			|| Math.hypot(path.getTarget().getX() + 0.5 - target.getX(), path.getTarget().getZ() + 0.5 - target.getZ()) > STALE;
		if (navigation.isDone() || stale && (mob.tickCount + mob.getId()) % REPATH_TICKS == 0) {
			if (!navigation.moveTo(target, speed)) {
				return false;
			}
		}
		navigation.setSpeedModifier(speed);
		return true;
	}

	/**
	 * The {@link dev.forja.entity.ai.Windup} field of each of Forja's own monsters (the smith, the guardian, the striker...),
	 * or null for a class without one. Their special moves plant their feet while they charge (each stops its navigation
	 * then); a melee warning running at the same time must not walk them out of it.
	 */
	private static final ClassValue<java.lang.reflect.Field> OWN_WINDUP = new ClassValue<>() {
		@Override
		protected java.lang.reflect.Field computeValue(Class<?> type) {
			for (Class<?> c = type; c != null && c != Mob.class; c = c.getSuperclass()) {
				for (java.lang.reflect.Field field : c.getDeclaredFields()) {
					if (field.getType() == dev.forja.entity.ai.Windup.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
						field.setAccessible(true);
						return field;
					}
				}
			}
			return null;
		}
	};

	/** Whether one of Forja's own monsters is charging a special move of its own (its Windup). */
	static boolean ownMoveCharging(Mob mob) {
		java.lang.reflect.Field field = OWN_WINDUP.get(mob.getClass());
		if (field == null) {
			return false;
		}
		try {
			return field.get(mob) instanceof dev.forja.entity.ai.Windup windup && windup.charging();
		} catch (IllegalAccessException failure) {
			return false;
		}
	}
}
