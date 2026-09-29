package dev.forja.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/**
 * The blaze's executor in 3D (red_blaze_v1, docs/red_blaze_contrato.json "ejecutor"; Andy, 2026-09-29): carries out a
 * {@link BlazeDecision} every tick, from TacticGoal, while a blaze network drives the blaze. Every number is in the
 * contract, because the simulator has to copy it.
 *
 * <p>Height: a blaze floats. Vanilla lets it sink (Blaze.aiStep slows its fall) and lifts it when the player's eyes are
 * above its own (Blaze.customServerAiStep). While the network drives, this sets the vertical speed itself every tick,
 * easing towards what the head asked (0, rise or descend), so gravity never builds up and it holds its height when
 * told to. Vanilla's lift still runs after it: with the player above its eyes, "descend" cannot beat it, and the
 * simulator has to copy that too.
 *
 * <p>Across: the move control points it the way it was told (that turns it and walks it on the ground), and in the
 * air, where a mob barely pushes itself, its flat speed is also eased towards that way.
 */
public final class BlazePilot {
	/** The charge before a burst (the visible warning), in ticks. */
	public static final int CHARGE_TICKS = 20;
	/** Vertical speeds in blocks per tick, and how much of the gap the speed closes each tick. */
	public static final double RISE = 0.12;
	public static final double DESCEND = -0.10;
	public static final double VERTICAL_EASE = 0.35;
	/** Flat speed in the air, blocks per tick, and how much of the gap the speed closes each tick. */
	public static final double AIR_SPEED = 0.1;
	public static final double AIR_EASE = 0.25;
	/** No rising with a ceiling this close over its head, or this high over the ground; no descending this near it. */
	public static final double CEILING_MIN = 1.5;
	public static final double MAX_HEIGHT = 10.0;
	public static final double MIN_HEIGHT = 1.0;
	/** RODEAR_ALTO: how far above the player's feet it wants to be, and how fast the gap is closed (per block). */
	public static final double HIGH_ABOVE = 4.0;
	public static final double HEIGHT_GAIN = 0.1;
	/** How far a shot goes (as vanilla's blaze and the rules' burst), and how far RETIRARSE aims. */
	public static final double FIRE_RANGE = 16.0;
	public static final double RETREAT = 6.0;
	/** The distance bands (flat) of the distancia head: middle, near, far. */
	public static final double[][] BANDS = {{8.0, 12.0}, {5.0, 8.0}, {12.0, 15.0}};

	private BlazePilot() {
	}

	/** One tick of the decision in {@code mind.blaze}. */
	public static void tick(Mob mob, MobMind mind, Player target) {
		BlazeDecision decision = mind.blaze;
		if (decision == null) {
			return;
		}
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double d = Math.hypot(dx, dz);
		double ux = d > 1.0E-6 ? dx / d : 1.0;
		double uz = d > 1.0E-6 ? dz / d : 0.0;
		double[] band = BANDS[Math.max(0, Math.min(BANDS.length - 1, decision.range()))];
		double[] across = null;
		double vertical = verticalSpeed(decision.vertical());
		switch (decision.tactic()) {
			case ACOSAR -> across = decision.move() == 0 ? null : ObsM1.direction(decision.move(), ux, uz);
			case RODEAR_ALTO -> {
				double angle = Math.atan2(mob.getZ() - target.getZ(), mob.getX() - target.getX()) + side(mob) * TacticGoal.ARC_STEP;
				double radius = (band[0] + band[1]) / 2.0;
				across = towards(mob, target.getX() + Math.cos(angle) * radius, target.getZ() + Math.sin(angle) * radius);
				double gap = target.getY() + HIGH_ABOVE - mob.getY();
				vertical = Math.max(DESCEND, Math.min(RISE, gap * HEIGHT_GAIN));
			}
			case RETIRARSE -> across = new double[] {-ux, -uz};
			case ESPERAR -> across = d < band[0] ? new double[] {-ux, -uz} : d > band[1] ? new double[] {ux, uz} : null;
		}
		fly(mob, mind, across, vertical);
		fire(mob, mind, target, decision.fire());
	}

	private static double verticalSpeed(int vertical) {
		return vertical == BlazeDecision.RISE ? RISE : vertical == BlazeDecision.DESCEND ? DESCEND : 0.0;
	}

	/** Which way this one circles: fixed per blaze, as a relay's side, so it does not dither. */
	private static double side(Mob mob) {
		return (mob.getId() & 1) == 0 ? 1.0 : -1.0;
	}

	/** The flat unit direction to a point, or null when it is there already. */
	private static double[] towards(Mob mob, double x, double z) {
		double dx = x - mob.getX();
		double dz = z - mob.getZ();
		double d = Math.hypot(dx, dz);
		return d < 0.5 ? null : new double[] {dx / d, dz / d};
	}

	/**
	 * Moves it: {@code across} the flat unit direction (null to stay), {@code vertical} the vertical speed it wants,
	 * cut to 0 where it may not rise or descend. The vertical speed eases towards it and is set every tick, so what it
	 * moves this tick is that speed (gravity is spent after the move, and set over again next tick).
	 */
	static void fly(Mob mob, MobMind mind, double[] across, double vertical) {
		if (vertical > 0.0 && !canRise(mob) || vertical < 0.0 && !canDescend(mob)) {
			vertical = 0.0;
		}
		mind.blazeVy += (vertical - mind.blazeVy) * VERTICAL_EASE;
		if (vertical == 0.0 && Math.abs(mind.blazeVy) < 0.005) {
			mind.blazeVy = 0.0;
		}
		mob.getNavigation().stop();
		Vec3 v = mob.getDeltaMovement();
		double vx = v.x;
		double vz = v.z;
		if (across != null) {
			mob.getMoveControl().setWantedPosition(mob.getX() + across[0] * 2.0, mob.getY(), mob.getZ() + across[1] * 2.0, 1.0);
		}
		if (!mob.onGround()) {
			double wx = across == null ? 0.0 : across[0] * AIR_SPEED;
			double wz = across == null ? 0.0 : across[1] * AIR_SPEED;
			vx += (wx - vx) * AIR_EASE;
			vz += (wz - vz) * AIR_EASE;
		}
		mob.setDeltaMovement(vx, mind.blazeVy, vz);
	}

	/** Whether it may rise: no ceiling within {@link #CEILING_MIN} over its head, and under {@link #MAX_HEIGHT} over the ground. */
	public static boolean canRise(Mob mob) {
		double ceiling = ObsBlaze.ceiling(mob.level(), mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ());
		return ceiling > CEILING_MIN && ObsBlaze.heightOverGround(mob) < MAX_HEIGHT;
	}

	/** Whether it may come down: off the ground, more than {@link #MIN_HEIGHT} over it. */
	public static boolean canDescend(Mob mob) {
		return !mob.onGround() && ObsBlaze.heightOverGround(mob) > MIN_HEIGHT;
	}

	/** Whether a burst can start now: charged, rested, within {@link #FIRE_RANGE} and in sight. */
	public static boolean canFire(Mob mob, MobMind mind, Player target) {
		return mind.blazeCharge >= CHARGE_TICKS && mind.cooldown <= 0 && mind.draw == 0 && mob.distanceTo(target) < FIRE_RANGE
			&& ObsBlaze.sees(mob, target);
	}

	/**
	 * The fire head: a burst under way goes on whatever was asked; "charge" adds a tick of charge (the first one warns,
	 * with flames and a sound, so the player sees it coming); "fire" with a full charge starts the burst and spends it;
	 * "wait" keeps the charge.
	 */
	static void fire(Mob mob, MobMind mind, Player target, int fire) {
		if (mind.draw > 0) {
			burst(mob, mind, target, true, true);
			return;
		}
		if (fire == BlazeDecision.CHARGE && mind.cooldown <= 0 && mind.blazeCharge < CHARGE_TICKS) {
			if (mind.blazeCharge == 0) {
				warn(mob);
			}
			mind.blazeCharge++;
		} else if (fire == BlazeDecision.FIRE && canFire(mob, mind, target)) {
			mind.blazeCharge = 0;
			burst(mob, mind, target, true, true);
		}
	}

	private static void warn(Mob mob) {
		if (mob.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY(0.5), mob.getZ(), 12, 0.4, 0.5, 0.4, 0.02);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 1.0F, 1.4F);
		}
	}

	/** Shots of a burst still to come after burst tick {@code draw} (1 is the first shot's tick). */
	public static int shotsLeft(int draw) {
		if (draw <= 0) {
			return 0;
		}
		int fired = Math.min(TacticGoal.BLAZE_BURST, (draw - 1) / TacticGoal.BLAZE_BURST_GAP + 1);
		return TacticGoal.BLAZE_BURST - fired;
	}

	/**
	 * One tick of a burst, shared by the rules' "usar" (TacticGoal) and the network: {@code mind.draw} counts its ticks,
	 * a small fireball goes on 1, 1 + GAP and 1 + 2 GAP, and then the cooldown. The network's own burst also wants the
	 * player in sight for each shot (one out of sight is lost) and leads them by their movement; the rules' burst is
	 * left as it always was.
	 *
	 * @param eachInSight a shot only when the player is in sight that tick
	 * @param lead        aim ahead of the player: their flat speed × the distance (at most 16) in ticks
	 */
	public static void burst(Mob mob, MobMind mind, Player target, boolean eachInSight, boolean lead) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		int tick = ++mind.draw;
		if ((tick - 1) % TacticGoal.BLAZE_BURST_GAP == 0 && (!eachInSight || ObsBlaze.sees(mob, target))) {
			Vec3 from = new Vec3(mob.getX(), mob.getY(0.5) + 0.5, mob.getZ());
			Vec3 at = new Vec3(target.getX(), target.getY(0.5), target.getZ());
			if (lead) {
				Vec3 moving = target.getKnownMovement();
				double ticks = Math.min(from.distanceTo(at), FIRE_RANGE);
				at = at.add(moving.x * ticks, 0.0, moving.z * ticks);
			}
			Vec3 aim = at.subtract(from).normalize();
			var ball = new SmallFireball(level, mob, aim);
			ball.snapTo(from.x, from.y, from.z, mob.getYRot(), mob.getXRot());
			level.addFreshEntity(ball);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
		}
		if (tick >= 1 + (TacticGoal.BLAZE_BURST - 1) * TacticGoal.BLAZE_BURST_GAP) {
			mind.draw = 0;
			mind.cooldown = TacticGoal.BLAZE_COOLDOWN;
		}
	}
}
