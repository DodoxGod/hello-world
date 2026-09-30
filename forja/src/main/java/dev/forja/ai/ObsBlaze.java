package dev.forja.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The blaze's observation, red_blaze_v1 (docs/red_blaze_contrato.json, written by the simulator; its blaze.rs
 * {@code obs_blaze}): 324 inputs, the 280 of red_mob_v3b (with correr) seen from the blaze, then 44 of its own.
 *
 * <p>The first 280 are worked out exactly as for a v3b mob (ObsM1, ObsForja, ObsV3), with three things the contract
 * spells out for the blaze: yo_recarga/40 is the wait after its burst (the mind's cooldown), yo_arco/20 is 0 (its
 * burst is not a drawn bow) and yo_fuego is 0 (a blaze never burns; vanilla's Blaze.isOnFire is its "charged" flag).
 * yo_vy*5 is the vertical speed its executor flies with (BlazePilot), not the delta after vanilla's gravity, which
 * the simulator does not have.
 *
 * <p>The frame of the new ones is v3's: "delante" is the flat direction from the blaze to the player, "derecha" is
 * (−delante_z, delante_x).
 */
public final class ObsBlaze {
	/** The "formato" of a blaze network (docs/red_blaze_contrato.json). */
	public static final String FORMAT = "red_blaze_v1";
	/** red_mob_v3b's inputs, the blaze's own, and all of them. */
	public static final int BASE = 280;
	public static final int EXTRA = 44;
	public static final int SIZE = BASE + EXTRA;
	/** Where the three nearest fireballs start among the 324, and how many inputs each has. */
	public static final int BALLS_AT = BASE + 20;
	public static final int PER_BALL = 8;
	public static final int BALLS = 3;
	/** How far down the ground under a box is looked for; with none that deep, the height reads this. */
	public static final int GROUND_DEPTH = 16;
	/** A ground ally this close to the player (flat) is beside them (aliados_junto_jug). */
	public static final double BESIDE_PLAYER = 3.0;
	/** How far round the player ground allies and fireballs are looked for. */
	public static final double ALLY_RANGE = 24.0;
	public static final double BALL_RANGE = 48.0;
	/** A fireball older than this has left the fight (the simulator drops its own at 100 ticks). */
	public static final int BALL_LIFE = 100;
	/** How far ahead a fireball's path is followed for its closest point to the player, in ticks. */
	public static final int BALL_LOOKAHEAD = 40;
	/** How much higher the player's eyes are when they jump, for jug_dist_golpe_salto. */
	public static final double JUMP_EYES = 1.25;
	/** ObsM1's indices the blaze overrides. */
	private static final int YO_VY = 26;
	private static final int YO_ARCO = 28;
	private static final int YO_FUEGO = 30;

	private static final List<String> NAMES;

	static {
		List<String> names = new ArrayList<>(ObsNames.M1);
		names.addAll(ObsForja.names());
		names.addAll(ObsV3.names());
		names = new ArrayList<>(names.subList(0, BASE));
		Collections.addAll(names, "yo_altura_suelo/5", "jug_altura_suelo/5", "jug_dy_ojos/4", "jug_dist_golpe/6",
			"jug_dist_golpe_salto/6", "jug_me_alcanza", "jug_me_alcanza_salto", "rafaga_cargando", "rafaga_carga_progreso",
			"rafaga_en_curso", "rafaga_quedan/3", "rafaga_enfriamiento/60", "rafaga_lista", "jug_ardiendo", "jug_fuego/100",
			"aliados_junto_jug/5", "aliado_suelo_dist_jug/8", "bola_ticks_al_jug/20", "yo_mojado", "mis_bolas_vuelo/3");
		for (int k = 0; k < BALLS; k++) {
			for (String s : new String[] {"presente", "delante/16", "derecha/16", "dy/4", "t_cercano/20", "fallo/2", "mia", "desviada"}) {
				names.add("bola" + k + "_" + s);
			}
		}
		NAMES = Collections.unmodifiableList(names);
	}

	private ObsBlaze() {
	}

	/** The 324 names, in the contract's order. */
	public static List<String> names() {
		return NAMES;
	}

	/** The observation a blaze network is fed (see the class comment). {@code mind} may not be null. */
	public static float[] build(Mob mob, Player target, MobMind mind) {
		BlazePilot.State state = BlazePilot.state(mind);
		float[] m1 = ObsM1.of(mob, target, mind.cooldown, 0);
		float[] base = ObsForja.full(mob, target, mind, m1, BASE);
		float[] out = new float[SIZE];
		System.arraycopy(base, 0, out, 0, BASE);
		out[YO_ARCO] = 0.0F;
		out[YO_FUEGO] = 0.0F;
		// On the ground and not moving up or down, Minecraft keeps the tick's gravity in the delta (−0.0784), and the
		// simulator observes it so; in the air it is the executor's own vertical speed.
		double vy = mob.onGround() && state.vy == 0.0 ? -0.0784 : state.vy;
		out[YO_VY] = (float) ObsM1.clip(vy * 5.0, -3.0, 3.0);

		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double ux = dx / d;
		double uz = dz / d;
		double rx = -uz;
		double rz = ux;
		double reach = Reach.player(target);
		int i = BASE;
		out[i++] = f(ObsM1.clip(heightOverGround(mob) / 5.0, 0.0, 2.0));
		out[i++] = f(ObsM1.clip(heightOverGround(target) / 5.0, 0.0, 2.0));
		out[i++] = f(ObsM1.clip((target.getEyeY() - mob.getY(0.5)) / 4.0, -2.0, 2.0));
		double blow = hitDistance(target, mob, 0.0);
		double jumping = hitDistance(target, mob, JUMP_EYES);
		out[i++] = f(ObsM1.clip(blow / 6.0, 0.0, 2.0));
		out[i++] = f(ObsM1.clip(jumping / 6.0, 0.0, 2.0));
		out[i++] = blow <= reach ? 1.0F : 0.0F;
		out[i++] = jumping <= reach ? 1.0F : 0.0F;
		out[i++] = state.phase == BlazePilot.CHARGING ? 1.0F : 0.0F;
		out[i++] = state.phase == BlazePilot.CHARGING && state.chargeTotal > 0
			? f(1.0 - (double) state.chargeLeft / state.chargeTotal) : 0.0F;
		out[i++] = state.phase == BlazePilot.BURSTING ? 1.0F : 0.0F;
		out[i++] = state.phase == BlazePilot.BURSTING
			? f((BlazePilot.BURST - Math.min(BlazePilot.BURST, state.shots)) / 3.0) : 0.0F;
		out[i++] = f(ObsM1.clip(Math.max(0, mind.cooldown) / 60.0, 0.0, 2.0));
		out[i++] = BlazePilot.ready(mob, mind, target) ? 1.0F : 0.0F;
		int fire = target.getRemainingFireTicks();
		out[i++] = fire > 0 ? 1.0F : 0.0F;
		out[i++] = f(ObsM1.clip(Math.max(0, fire) / 100.0, 0.0, 2.0));
		int beside = 0;
		double nearest = Double.MAX_VALUE;
		for (Mob ally : groundAllies(mob, target)) {
			double flat = Math.hypot(ally.getX() - target.getX(), ally.getZ() - target.getZ());
			nearest = Math.min(nearest, flat);
			if (flat <= BESIDE_PLAYER) {
				beside++;
			}
		}
		out[i++] = f(ObsM1.clip(beside / 5.0, 0.0, 2.0));
		out[i++] = nearest == Double.MAX_VALUE ? 2.0F : f(ObsM1.clip(nearest / 8.0, 0.0, 2.0));
		Vec3 muzzle = BlazePilot.muzzle(mob);
		double toPlayer = muzzle.distanceTo(new Vec3(target.getX(), target.getY(0.5), target.getZ()));
		out[i++] = f(ObsM1.clip(BlazePilot.flightTicks(toPlayer) / 20.0, 0.0, 3.0));
		out[i++] = mob.isInWaterOrRain() ? 1.0F : 0.0F;
		out[i++] = f(ObsM1.clip(BlazePilot.ownBallsInFlight(state, mob) / 3.0, 0.0, 2.0));
		List<Fireball> balls = nearestBalls(target);
		double cx = target.getX();
		double cy = target.getY(0.5);
		double cz = target.getZ();
		AABB body = target.getBoundingBox();
		for (int k = 0; k < BALLS; k++) {
			if (k >= balls.size()) {
				i += PER_BALL;
				continue;
			}
			Fireball ball = balls.get(k);
			double qx = ball.getX() - cx;
			double qy = ball.getY() - cy;
			double qz = ball.getZ() - cz;
			double[] closest = closestApproach(ball, body);
			Entity owner = ball.getOwner();
			out[i++] = 1.0F;
			out[i++] = f(ObsM1.clip((qx * ux + qz * uz) / 16.0, -2.0, 2.0));
			out[i++] = f(ObsM1.clip((qx * rx + qz * rz) / 16.0, -2.0, 2.0));
			out[i++] = f(ObsM1.clip(qy / 4.0, -2.0, 2.0));
			out[i++] = f(ObsM1.clip(closest[0] / 20.0, 0.0, 2.0));
			out[i++] = f(ObsM1.clip(closest[1] / 2.0, 0.0, 1.0));
			out[i++] = owner == mob ? 1.0F : 0.0F;
			out[i++] = owner == target ? 1.0F : 0.0F;
		}
		return out;
	}

	private static float f(double v) {
		return (float) v;
	}

	/**
	 * How high an entity's feet are over the ground under its box: the highest collision top at or below its feet in
	 * any block column its box stands over (the simulator's suelo(x, z, half width)), {@link #GROUND_DEPTH} with none.
	 */
	public static double heightOverGround(Entity entity) {
		double y = entity.getY();
		return y - groundBelow(entity.level(), entity.getX(), entity.getZ(), entity.getBbWidth() / 2.0, y);
	}

	/** The ground under a box of half width {@code hw} at (x, z) whose feet are at {@code y}; y − GROUND_DEPTH with none. */
	public static double groundBelow(Level level, double x, double z, double hw, double y) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = (int) Math.floor(y + 1.0E-3);
		double best = y - GROUND_DEPTH;
		for (int bx = (int) Math.floor(x - hw); bx <= (int) Math.floor(x + hw - 1.0E-6); bx++) {
			for (int bz = (int) Math.floor(z - hw); bz <= (int) Math.floor(z + hw - 1.0E-6); bz++) {
				for (int k = top; k >= top - GROUND_DEPTH && k + 1 > best; k--) {
					pos.set(bx, k, bz);
					var shape = level.getBlockState(pos).getCollisionShape(level, pos);
					if (shape.isEmpty()) {
						continue;
					}
					double surface = k + shape.max(Direction.Axis.Y);
					if (surface <= y + 1.0E-3) {
						best = Math.max(best, surface);
						break;
					}
				}
			}
		}
		return best;
	}

	/**
	 * The simulator's distancia_golpe of the player to the blaze: from the player's eyes ({@code extra} higher) along
	 * the ray to the blaze's middle at the eyes' height (kept 0.1 inside its box), how far until it enters the box.
	 */
	public static double hitDistance(Player player, Mob blaze, double extra) {
		AABB box = blaze.getBoundingBox();
		double ex = player.getX();
		double ey = player.getEyeY() + extra;
		double ez = player.getZ();
		double ay = Math.max(box.minY + 0.1, Math.min(box.maxY - 0.1, ey));
		double dx = blaze.getX() - ex;
		double dy = ay - ey;
		double dz = blaze.getZ() - ez;
		double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (n < 1.0E-6) {
			return 0.0;
		}
		double entry = rayEntry(ex, ey, ez, dx / n, dy / n, dz / n, box);
		return entry < 0.0 ? n : entry;
	}

	/** Where a ray (unit direction) enters a box: 0 from inside, −1 when it misses. */
	static double rayEntry(double ox, double oy, double oz, double dx, double dy, double dz, AABB box) {
		double near = 0.0;
		double far = Double.MAX_VALUE;
		double[] o = {ox, oy, oz};
		double[] d = {dx, dy, dz};
		double[] lo = {box.minX, box.minY, box.minZ};
		double[] hi = {box.maxX, box.maxY, box.maxZ};
		for (int a = 0; a < 3; a++) {
			if (Math.abs(d[a]) < 1.0E-12) {
				if (o[a] < lo[a] || o[a] > hi[a]) {
					return -1.0;
				}
				continue;
			}
			double t1 = (lo[a] - o[a]) / d[a];
			double t2 = (hi[a] - o[a]) / d[a];
			near = Math.max(near, Math.min(t1, t2));
			far = Math.min(far, Math.max(t1, t2));
			if (near > far) {
				return -1.0;
			}
		}
		return near;
	}

	/** Hostiles on the ground (no blaze) fighting this player, within {@link #ALLY_RANGE} of them. */
	static List<Mob> groundAllies(Mob self, Player target) {
		return target.level().getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(ALLY_RANGE),
			other -> other != self && other.isAlive() && other instanceof Enemy && !(other instanceof Blaze) && other.getTarget() == target);
	}

	/** The fireballs (anyone's) still in the fight, nearest the player's middle first, at most three. */
	static List<Fireball> nearestBalls(Player target) {
		Vec3 centre = new Vec3(target.getX(), target.getY(0.5), target.getZ());
		List<Fireball> balls = target.level().getEntitiesOfClass(Fireball.class, target.getBoundingBox().inflate(BALL_RANGE),
			ball -> ball.isAlive() && ball.tickCount <= BALL_LIFE);
		balls.sort(java.util.Comparator.comparingDouble(ball -> ball.position().distanceToSqr(centre)));
		return balls.size() > BALLS ? balls.subList(0, BALLS) : balls;
	}

	/**
	 * A fireball's closest point to the (still) player's box over the next {@link #BALL_LOOKAHEAD} ticks, with its own
	 * acceleration: {ticks until then, distance there}. As vanilla moves it (AbstractHurtingProjectile.tick): the
	 * speed takes its step first, then the ball moves by it.
	 */
	static double[] closestApproach(AbstractHurtingProjectile ball, AABB body) {
		double x = ball.getX();
		double y = ball.getY();
		double z = ball.getZ();
		Vec3 v = ball.getDeltaMovement();
		double vx = v.x;
		double vy = v.y;
		double vz = v.z;
		double power = ball.accelerationPower;
		double best = boxDistance(body, x, y, z);
		int when = 0;
		for (int k = 1; k <= BALL_LOOKAHEAD; k++) {
			double n = Math.max(1.0E-9, Math.sqrt(vx * vx + vy * vy + vz * vz));
			vx = (vx + vx / n * power) * BlazePilot.BALL_INERTIA;
			vy = (vy + vy / n * power) * BlazePilot.BALL_INERTIA;
			vz = (vz + vz / n * power) * BlazePilot.BALL_INERTIA;
			x += vx;
			y += vy;
			z += vz;
			double dist = boxDistance(body, x, y, z);
			if (dist < best) {
				best = dist;
				when = k;
			}
		}
		return new double[] {when, best};
	}

	static double boxDistance(AABB box, double x, double y, double z) {
		double dx = Math.max(0.0, Math.max(box.minX - x, x - box.maxX));
		double dy = Math.max(0.0, Math.max(box.minY - y, y - box.maxY));
		double dz = Math.max(0.0, Math.max(box.minZ - z, z - box.maxZ));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
