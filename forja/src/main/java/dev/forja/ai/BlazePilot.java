package dev.forja.ai;

import java.util.ArrayList;
import java.util.List;

import dev.forja.combat.Posture;
import dev.forja.mixin.BlazeAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/**
 * The blaze's executor while its own network drives it (red_blaze_v1, docs/red_blaze_contrato.json "ejecutor"; the
 * simulator's blaze.rs {@code tick_blaze_mod} and {@code mover_blaze}): carries out a {@link BlazeDecision} every tick,
 * from TacticGoal. Every number here is the contract's.
 *
 * <p>Flight: it hovers 2 to 5 blocks over the ground under its box. Its vertical speed eases towards a target,
 * vy += (target − vy) · 0.3, with +0.12 to rise, −0.10 to descend, 0 to hold; under its band it rises, over it (no
 * ground near) it sinks at −0.05, and over the same ground it does not leave the band. Vanilla's gravity and the
 * Blaze's own lift towards a player above it do not act on it: the speed is set here every tick (and
 * BlazeMixin skips Blaze.customServerAiStep). Across, it has vanilla's air physics: v += dir · speed · 0.02 · k (k 1,
 * or 0.5 while charging or firing), then vanilla's travel moves it and keeps 0.91 of it.
 *
 * <p>The burst: with usar and the burst ready (idle, rested, under 16 blocks and in sight), 20 ticks of warning, the
 * blaze glowing and flaming as vanilla's charged blaze does, with a rising crackle, then three small fireballs six
 * ticks apart (the first as the warning ends), aimed at the player's middle plus lead × their flat speed × the ball's
 * flight ticks, then 60 ticks of rest. A burst started goes on even if the player is lost from sight; a staggered
 * blaze's burst stops where it is.
 */
public final class BlazePilot {
	/** The flight band over the ground, the vertical speeds and how much of the gap closes each tick. */
	public static final double MIN_HEIGHT = 2.0;
	public static final double MAX_HEIGHT = 5.0;
	public static final double RISE = 0.12;
	public static final double DESCEND = 0.10;
	public static final double SINK = 0.05;
	public static final double VERTICAL_EASE = 0.3;
	/** Vanilla's flying speed (LivingEntity.getFlyingSpeed), times the movement speed attribute (0.23). */
	public static final double AIR_ACCEL = 0.02;
	/** How fast it moves across while charging or firing. */
	public static final double BURST_SPEED = 0.5;
	/** The burst: warning, shots, ticks between them, rest after, and how near and in sight it must be to start. */
	public static final int CHARGE_TICKS = 20;
	public static final int BURST = TacticGoal.BLAZE_BURST;
	public static final int GAP = TacticGoal.BLAZE_BURST_GAP;
	public static final int COOLDOWN = TacticGoal.BLAZE_COOLDOWN;
	public static final double FIRE_RANGE = 16.0;
	/** A small fireball: its starting speed and push per tick (AbstractHurtingProjectile.accelerationPower), inertia. */
	public static final double BALL_ACCEL = 0.1;
	public static final double BALL_INERTIA = 0.95;
	/** The longest flight {@link #flightTicks} counts. */
	public static final int MAX_FLIGHT = 60;
	/** The phases of the burst. */
	public static final int IDLE = 0;
	public static final int CHARGING = 1;
	public static final int BURSTING = 2;

	/** A blaze's burst and flight while its network drives it. The wait after a burst is the mind's cooldown. */
	public static final class State {
		public int phase;
		public int chargeLeft;
		public int chargeTotal;
		/** Ticks into the burst (1 is the first shot's) and shots fired. */
		public int burstTick;
		public int shots;
		/** The vertical speed it flies with (the simulator's vy, before any gravity). */
		public double vy;
		/** Whether vanilla's charged flag (the flames round it) was set by this executor. */
		boolean charged;
		/** Its fireballs, to count the ones still flying (mis_bolas_vuelo). */
		final List<SmallFireball> balls = new ArrayList<>();
	}

	private BlazePilot() {
	}

	public static State state(MobMind mind) {
		if (mind.blazeState == null) {
			mind.blazeState = new State();
		}
		return mind.blazeState;
	}

	/** Whether a blaze network is driving this mob now (and vanilla's blaze fire and lift must keep out). */
	public static boolean drives(Mob mob) {
		MobMind mind = MobAi.mind(mob);
		return mind != null && mind.networked && mind.blaze != null;
	}

	/** rafaga_lista, and the mask of usar: idle, rested, under 16 blocks of the player and seeing them. */
	public static boolean ready(Mob mob, MobMind mind, Player target) {
		State state = state(mind);
		return state.phase == IDLE && mind.cooldown <= 0 && target.isAlive() && mob.distanceTo(target) < FIRE_RANGE
			&& ObsM1.sees(mob, target.getX(), target.getEyeY(), target.getZ());
	}

	/** Where its fireballs leave from: its middle, half a block up (vanilla's BlazeAttackGoal). */
	public static Vec3 muzzle(Mob mob) {
		return new Vec3(mob.getX(), mob.getY(0.5) + 0.5, mob.getZ());
	}

	/**
	 * Ticks a new small fireball takes to cover {@code distance}, moving as vanilla's does: it starts at 0.1 and each
	 * tick its speed takes the push and the inertia before it moves (at most {@link #MAX_FLIGHT}).
	 */
	public static int flightTicks(double distance) {
		double covered = 0.0;
		double v = BALL_ACCEL;
		int n = 0;
		while (covered < distance && n < MAX_FLIGHT) {
			v = (v + BALL_ACCEL) * BALL_INERTIA;
			covered += v;
			n++;
		}
		return n;
	}

	/** Its own fireballs still flying (not turned back by the player, not older than ObsBlaze.BALL_LIFE). */
	static int ownBallsInFlight(State state, Mob mob) {
		state.balls.removeIf(ball -> !ball.isAlive() || ball.tickCount > ObsBlaze.BALL_LIFE);
		int n = 0;
		for (SmallFireball ball : state.balls) {
			if (ball.getOwner() == mob) {
				n++;
			}
		}
		return n;
	}

	/** One tick of the decision in {@code mind.blaze}. */
	public static void tick(Mob mob, MobMind mind, Player target) {
		BlazeDecision decision = mind.blaze;
		State state = state(mind);
		if (decision == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (!target.isAlive() || Posture.isStaggered(mob, level.getGameTime())) {
			fly(mob, state, null, 0.0, BlazeDecision.HOLD);
			return;
		}
		if (mind.cooldown > 0) {
			mind.cooldown--;
		}
		if (state.phase == IDLE && decision.use() && ready(mob, mind, target)) {
			state.phase = CHARGING;
			state.chargeLeft = CHARGE_TICKS;
			state.chargeTotal = CHARGE_TICKS;
			setCharged(mob, state, true);
			warn(level, mob, 0.0, true);
		} else if (state.phase == CHARGING) {
			state.chargeLeft--;
			if (state.chargeLeft <= 0) {
				state.phase = BURSTING;
				state.burstTick = 0;
				state.shots = 0;
			} else {
				warn(level, mob, 1.0 - (double) state.chargeLeft / state.chargeTotal, false);
			}
		}
		if (state.phase == BURSTING) {
			state.burstTick++;
			if ((state.burstTick - 1) % GAP == 0) {
				shoot(level, mob, state, target, decision.leadFactor());
			}
			if (state.burstTick >= 1 + (BURST - 1) * GAP) {
				state.phase = IDLE;
				mind.cooldown = COOLDOWN;
				setCharged(mob, state, false);
			}
		}
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double ux = dx / d;
		double uz = dz / d;
		double speed = state.phase != IDLE ? BURST_SPEED : 1.0;
		if (decision.retreat()) {
			fly(mob, state, ObsM1.direction(BlazeDecision.AWAY, ux, uz), speed, BlazeDecision.RISE);
		} else {
			fly(mob, state, decision.move() > 0 ? ObsM1.direction(decision.move(), ux, uz) : null, speed, decision.vertical());
		}
	}

	/**
	 * Sets this tick's movement (the simulator's mover_blaze): {@code across} the flat unit direction to push (null
	 * none), {@code speed} its factor, {@code vertical} 0 hold, 1 rise, 2 descend. Vanilla's travel then moves it by
	 * exactly that, with its collisions, and keeps 0.91 of the flat speed for the next tick.
	 */
	static void fly(Mob mob, State state, double[] across, double speed, int vertical) {
		double x = mob.getX();
		double y = mob.getY();
		double z = mob.getZ();
		double hw = mob.getBbWidth() / 2.0;
		double ground = ObsBlaze.groundBelow(mob.level(), x, z, hw, y);
		double height = y - ground;
		boolean inside = height >= MIN_HEIGHT - 1.0E-6 && height <= MAX_HEIGHT + 1.0E-6;
		double wanted = vertical == BlazeDecision.RISE && height < MAX_HEIGHT ? RISE
			: vertical == BlazeDecision.DESCEND && height > MIN_HEIGHT ? -DESCEND : 0.0;
		if (height < MIN_HEIGHT - 1.0E-6) {
			wanted = RISE;
		} else if (height > MAX_HEIGHT + 1.0E-6) {
			wanted = -SINK;
		}
		state.vy += (wanted - state.vy) * VERTICAL_EASE;
		Vec3 v = mob.getDeltaMovement();
		double vx = v.x;
		double vz = v.z;
		if (across != null) {
			double push = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * AIR_ACCEL * speed;
			vx += across[0] * push;
			vz += across[1] * push;
		}
		double next = ObsBlaze.groundBelow(mob.level(), x + vx, z + vz, hw, y);
		double ny = y + state.vy;
		// over the same ground it does not leave its band: it stops at the edge
		if (inside && Math.abs(next - ground) < 1.0E-6) {
			if (ny - next > MAX_HEIGHT) {
				ny = next + MAX_HEIGHT;
				state.vy = 0.0;
			}
			if (ny - next < MIN_HEIGHT) {
				ny = next + MIN_HEIGHT;
				state.vy = 0.0;
			}
		}
		if (ny < next) {
			ny = next;
			state.vy = 0.0;
		}
		mob.getMoveControl().setWait();
		mob.setZza(0.0F);
		mob.setXxa(0.0F);
		mob.setDeltaMovement(vx, ny - y, vz);
		mob.resetFallDistance();
	}

	/** One small fireball at the player's middle, led by {@code lead} × their flat speed × its flight ticks (twice worked out). */
	static void shoot(ServerLevel level, Mob mob, State state, Player target, double lead) {
		Vec3 from = muzzle(mob);
		double tx = target.getX();
		double ty = target.getY(0.5);
		double tz = target.getZ();
		if (lead > 0.0) {
			Vec3 moving = target.getKnownMovement();
			double ticks = flightTicks(from.distanceTo(new Vec3(tx, ty, tz)));
			for (int k = 0; k < 2; k++) {
				double px = target.getX() + moving.x * ticks * lead;
				double pz = target.getZ() + moving.z * ticks * lead;
				ticks = flightTicks(from.distanceTo(new Vec3(px, ty, pz)));
				tx = px;
				tz = pz;
			}
		}
		Vec3 aim = new Vec3(tx - from.x, ty - from.y, tz - from.z);
		if (aim.lengthSqr() < 1.0E-12) {
			return;
		}
		SmallFireball ball = new SmallFireball(level, mob, aim.normalize());
		ball.setPos(from.x, from.y, from.z);
		level.addFreshEntity(ball);
		state.balls.add(ball);
		state.shots++;
		if (!mob.isSilent()) {
			level.levelEvent(null, 1018, mob.blockPosition(), 0);
		}
	}

	/**
	 * The warning while it charges (progress 0 to 1): flames drawn in towards it, tighter and more of them as it
	 * fills, and a crackle that rises in pitch; at the start, a deep hiss the player can place.
	 */
	private static void warn(ServerLevel level, Mob mob, double progress, boolean start) {
		double cy = mob.getY(0.5);
		if (start) {
			level.playSound(null, mob.getX(), cy, mob.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 1.6F, 0.6F);
			level.sendParticles(ParticleTypes.LAVA, mob.getX(), cy, mob.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
		}
		int count = 4 + (int) Math.round(progress * 8.0);
		double radius = 1.3 - progress;
		for (int k = 0; k < count; k++) {
			double angle = (mob.tickCount * 0.6) + k * (Math.PI * 2.0 / count);
			double px = mob.getX() + Math.cos(angle) * radius;
			double pz = mob.getZ() + Math.sin(angle) * radius;
			level.sendParticles(ParticleTypes.FLAME, px, cy + (k % 3 - 1) * 0.4, pz, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (!start && Math.round(progress * CHARGE_TICKS) % 5 == 0) {
			level.playSound(null, mob.getX(), cy, mob.getZ(), SoundEvents.BLAZE_BURN, SoundSource.HOSTILE, 1.4F,
				(float) (0.7 + progress * 0.9));
		}
	}

	private static void setCharged(Mob mob, State state, boolean charged) {
		state.charged = charged;
		if (mob instanceof Blaze) {
			((BlazeAccess) mob).forja$setCharged(charged);
		}
	}

	/** When its network lets go of it (TacticGoal.stop): the burst is dropped and vanilla's flag cleared. */
	public static void release(Mob mob, MobMind mind) {
		State state = mind.blazeState;
		if (state == null) {
			return;
		}
		state.phase = IDLE;
		state.vy = 0.0;
		if (state.charged) {
			setCharged(mob, state, false);
		}
	}

	/** Shots of a rules burst still to come after burst tick {@code draw} (1 is the first shot's tick). */
	public static int shotsLeft(int draw) {
		if (draw <= 0) {
			return 0;
		}
		int fired = Math.min(BURST, (draw - 1) / GAP + 1);
		return BURST - fired;
	}

	/**
	 * One tick of the rules' burst (TacticGoal's "usar" for a blaze sent into a tactic by the rules): {@code mind.draw}
	 * counts its ticks, a small fireball goes on 1, 1 + GAP and 1 + 2 GAP, and then the cooldown. The network's burst
	 * is {@link #tick}'s, with its warning; this one is left as it always was.
	 *
	 * @param eachInSight a shot only when the player is in sight that tick
	 * @param lead        aim ahead of the player: their flat speed × the distance (at most 16) in ticks
	 */
	public static void burst(Mob mob, MobMind mind, Player target, boolean eachInSight, boolean lead) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		int tick = ++mind.draw;
		if ((tick - 1) % GAP == 0 && (!eachInSight || ObsM1.sees(mob, target.getX(), target.getY(0.5), target.getZ()))) {
			Vec3 from = muzzle(mob);
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
		if (tick >= 1 + (BURST - 1) * GAP) {
			mind.draw = 0;
			mind.cooldown = COOLDOWN;
		}
	}
}
