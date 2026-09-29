package dev.forja.ai;

import dev.forja.Forja;
import dev.forja.combat.Posture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Monsters that run (Andy, 2026-09-27, through the simulator's session): 35 % faster, paid for out of a
 * stamina of their own, so a run is a burst and not a way of life. Every monster with a mind runs except the
 * bosses, which have their own ways of closing a distance.
 *
 * <p>The run is vanilla's sprint (+30 %, the flag every client already sees and throws dust for) and a little
 * on top to make 35. By the rules a monster runs to close on a player getting away from it, to go round to
 * its slot of the ring when it is far round from it, and to get away when it is badly hurt; a network with
 * the {@code correr} output decides for itself. Never while stunned, never during the warning of a blow
 * (it stands still for that anyway), and not again until its breath is partly back.
 */
public final class MobSprint {
	public static final float MAX = 100.0F;
	/** Per tick of running: forty ticks of it, two seconds, from full. */
	public static final float COST = 2.0F;
	/** Per tick once it has not run for {@link #REST_TICKS}. */
	public static final float REGEN = 1.0F;
	public static final int REST_TICKS = 20;
	/** Out of breath, it runs again only from this much. */
	public static final float RESUME = 25.0F;
	public static final double BOOST = 0.35;
	/** The distance a player getting away is run down over. */
	public static final double CHASE_MIN = 4.0;
	public static final double CHASE_MAX = 12.0;
	/** How fast away counts as getting away, in blocks per tick along the line between them. */
	public static final double AWAY_SPEED = 0.05;
	public static final double RING_ANGLE = Math.toRadians(50.0);
	public static final float FLEE_HEALTH = 0.3F;
	/**
	 * Going to its place on the ring, and further from it than this, it runs: coming from afar to the far side
	 * is a long way however small the angle (Andy, 2026-09-29: "casi no esprintan para posicionarse").
	 */
	public static final double SLOT_FAR = 5.0;

	/** The player moving at least this fast (blocks a tick, across the ground) counts as backing away. */
	public static final double RODEO_PLAYER_SPEED = 0.08;
	/** Still this far from its slot, it is still going round to it. */
	public static final double RODEO_SLOT_NEAR = 2.0;
	/** How far behind the player's retreat the slot may be (cosine) and still count as ahead or beside it. */
	public static final double RODEO_BESIDE = -0.3;

	private static final Identifier EXTRA = Forja.id("carrera");
	/** On top of vanilla's sprint (×1.3) to make ×1.35. */
	private static final double EXTRA_AMOUNT = (1.0 + BOOST) / 1.3 - 1.0;

	private MobSprint() {
	}

	/** Whether this monster runs at all. */
	public static boolean runs(Mob mob) {
		return !dev.forja.difficulty.Bosses.isBoss(mob);
	}

	/** The rules' wish to run, from the decision just taken. */
	public static boolean rules(MobMind mind, Player target) {
		Mob mob = mind.mob;
		if (target == null) {
			return false;
		}
		Decision decision = mind.decision;
		if (decision.tactic() == Tactic.RETIRARSE && mob.getHealth() < mob.getMaxHealth() * FLEE_HEALTH) {
			return true;
		}
		if ((decision.tactic() == Tactic.RODEAR || decision.tactic() == Tactic.ESPERAR) && !Double.isNaN(mind.ringAngle)
			&& Math.abs(Squad.wrap(Squad.angle(mob, target) - mind.ringAngle)) > RING_ANGLE) {
			return true;
		}
		if ((decision.tactic() == Tactic.RODEAR || decision.tactic() == Tactic.FLANQUEAR) && !Double.isNaN(mind.ringAngle)
			&& mob.distanceToSqr(target.getX() + Math.cos(mind.ringAngle) * mind.ringRadius, mob.getY(),
				target.getZ() + Math.sin(mind.ringAngle) * mind.ringRadius) > SLOT_FAR * SLOT_FAR) {
			return true;
		}
		if ((decision.tactic() == Tactic.RODEAR || decision.tactic() == Tactic.FLANQUEAR || decision.tactic() == Tactic.ESPERAR) && rodeo(mind)) {
			return true;
		}
		double distance = mob.distanceTo(target);
		if (distance >= CHASE_MIN && distance <= CHASE_MAX) {
			Vec3 line = target.position().subtract(mob.position());
			Vec3 flat = new Vec3(line.x, 0.0, line.z);
			if (flat.lengthSqr() > 1.0E-6) {
				Vec3 moving = target.getKnownMovement();
				return moving.x * flat.x / flat.length() + moving.z * flat.z / flat.length() > AWAY_SPEED;
			}
		}
		return false;
	}

	/**
	 * Surround mode (Andy, 2026-09-29): a player who backs away never gave the mobs time to go round. A monster
	 * running to its ring slot, still away from it, while the player moves off (away from it, or with the slot
	 * ahead of or beside the way they are going) runs at {@code rodeoSpeed} (x2.3) instead of x1.35, and pays
	 * {@code rodeoCostPerTick} for it: about 3.5 s of it from full breath. The same for a network's run when its
	 * mob has a slot, since the slot is where the run takes it.
	 */
	public static boolean rodeo(MobMind mind) {
		Mob mob = mind.mob;
		Player target = mind.target;
		if (target == null || Double.isNaN(mind.ringAngle)) {
			return false;
		}
		double slotX = target.getX() + Math.cos(mind.ringAngle) * mind.ringRadius;
		double slotZ = target.getZ() + Math.sin(mind.ringAngle) * mind.ringRadius;
		if (mob.distanceToSqr(slotX, mob.getY(), slotZ) <= RODEO_SLOT_NEAR * RODEO_SLOT_NEAR) {
			return false;
		}
		Vec3 moving = target.getKnownMovement();
		double speed = Math.hypot(moving.x, moving.z);
		if (speed < RODEO_PLAYER_SPEED) {
			return false;
		}
		double mx = moving.x / speed;
		double mz = moving.z / speed;
		// away from this mob
		double ax = target.getX() - mob.getX();
		double az = target.getZ() - mob.getZ();
		double away = Math.hypot(ax, az);
		if (away > 1.0E-6 && (mx * ax + mz * az) / away > 0.0) {
			return true;
		}
		// or the slot ahead of or beside the retreat
		double sx = Math.cos(mind.ringAngle);
		double sz = Math.sin(mind.ringAngle);
		return mx * sx + mz * sz >= RODEO_BESIDE;
	}

	/** Whether it may run right now, wish aside: breath, feet and balance. */
	public static boolean able(MobMind mind, long now) {
		Mob mob = mind.mob;
		float needed = mind.winded ? RESUME : 0.0F;
		return mind.stamina > needed && mind.windup == 0 && !Posture.isStaggered(mob, now) && !mob.isInWater() && runs(mob);
	}

	/** Every tick, for every monster with a mind: run or not, spend or recover. */
	public static void tick(MobMind mind, long now) {
		Mob mob = mind.mob;
		boolean run = mind.target != null && mind.wantsRun && able(mind, now);
		boolean rodeo = run && rodeo(mind);
		if (run) {
			mind.stamina = Math.max(0.0F, mind.stamina - (rodeo ? dev.forja.combat.CombatConfig.get().rodeoCostPerTick : COST));
			mind.lastRun = now;
			if (mind.stamina <= 0.0F) {
				mind.winded = true;
				run = false;
				rodeo = false;
			}
		} else if (now - mind.lastRun >= REST_TICKS && mind.stamina < MAX) {
			mind.stamina = Math.min(MAX, mind.stamina + REGEN);
		}
		if (mind.winded && mind.stamina >= RESUME) {
			mind.winded = false;
		}
		if (run != mind.running || rodeo != mind.rodeo) {
			mind.running = run;
			mind.rodeo = rodeo;
			mob.setSprinting(run);
			AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(EXTRA);
				if (run) {
					// on top of vanilla's sprint (x1.3), to make x1.35, or the surround mode's x2.3
					double amount = rodeo ? dev.forja.combat.CombatConfig.get().rodeoSpeed / 1.3 - 1.0 : EXTRA_AMOUNT;
					speed.addTransientModifier(new AttributeModifier(EXTRA, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
		}
	}
}
