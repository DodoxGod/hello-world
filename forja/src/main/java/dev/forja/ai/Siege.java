package dev.forja.ai;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Two of v4's tactics against a player who will not come down (docs/red_mob_v4_diseno.md §4.6 and §4.10), carried out
 * the same for a network and the rules:
 *
 * <ul>
 *   <li><b>ASEDIAR</b>: the siege ring. A body waits {@link #MELEE_RING} blocks (6 to 10) from the foot of the pillar or
 *   tower, round its own side (its ring slot) and out of sight of the top where it can find such a spot within
 *   ±40°, which cuts the ways down; an archer {@link #ARCHER_RING} (12 to 16) off with a line to them. It never builds or
 *   digs: against a closed bunker it only waits outside (Andy's decision 5).</li>
 *   <li><b>APAGAR_LUZ</b>: to the torch that lights the player most (Lights, luz0) and, in reach ({@link
 *   MobActions#LIGHT_REACH} from its eyes, nothing solid between), {@link #STRIKE_TICKS} ticks of striking at it, then
 *   it breaks and drops, as if a player broke it. A torch it finds no path to is left alone for a while.</li>
 * </ul>
 */
public final class Siege {
	public static final double[] MELEE_RING = {8.0, 6.0, 10.0};
	public static final double[] ARCHER_RING = {14.0, 12.0, 16.0};
	public static final double[] TURNS = {0.0, 0.35, -0.35, 0.7, -0.7};
	/** How often a mob looks for its siege spot again, in ticks. */
	public static final int RESPOT = 20;
	public static final int STRIKE_TICKS = 15;
	/** Ticks it may go on walking to a torch without getting there before the torch is marked unreachable. */
	public static final int LIGHT_PATIENCE = 200;

	private Siege() {
	}

	// ---------------------------------------------------------------- ASEDIAR

	/** Its spot in the siege ring round the player (see the class), or null for none found. */
	public static Vec3 spot(Mob mob, MobMind mind, Player player) {
		Heights.State h = Heights.of(player);
		boolean archer = MobFamily.executor(mob) == MobFamily.ARQUERO;
		double own = !Double.isNaN(mind.ringAngle) ? mind.ringAngle : Squad.angle(mob, player);
		Vec3 eyes = player.getEyePosition();
		Vec3 fallback = null;
		for (double r : archer ? ARCHER_RING : MELEE_RING) {
			for (double turn : TURNS) {
				double a = own + turn;
				Vec3 at = MobItems.floorNear(mob, player.getX() + Math.cos(a) * r, h.groundY, player.getZ() + Math.sin(a) * r);
				if (at == null) {
					continue;
				}
				boolean seen = clear(mob, eyes, at.add(0.0, 1.5, 0.0));
				if (archer ? seen : !seen) {
					return at;
				}
				if (fallback == null) {
					fallback = at;
				}
			}
		}
		return fallback;
	}

	private static boolean clear(Mob mob, Vec3 from, Vec3 to) {
		return mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
	}

	/** ASEDIAR, one tick: to its spot and still there, looking up at the player. Returns false when it has none. */
	public static boolean tick(Mob mob, MobMind mind, Player player, long now) {
		if (mind.siegeSpot == null || now - mind.siegeAt >= RESPOT) {
			mind.siegeSpot = spot(mob, mind, player);
			mind.siegeAt = now;
		}
		Vec3 spot = mind.siegeSpot;
		if (spot == null) {
			return false;
		}
		if (mob.distanceToSqr(spot.x, mob.getY(), spot.z) < 1.5 * 1.5) {
			mob.getNavigation().stop();
		} else if (mind.pathDue(mob.level().getGameTime())) {
			mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
		}
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		return true;
	}

	// ---------------------------------------------------------------- APAGAR_LUZ

	/** A torch taking off at least this much of the player's light is worth putting out, by the rules. */
	public static final int WORTH = 3;
	/** One mob of a player's group at a time goes for the torches, and holds the duty this long without renewing it. */
	public static final int DUTY_TICKS = 100;
	private static final java.util.Map<Player, Object[]> DUTY = new java.util.WeakHashMap<>();

	/**
	 * The rules' APAGAR_LUZ: torches may be put out here, the torch lighting the player most takes at least {@link #WORTH}
	 * off their light (by day in the open it takes nothing: the sky covers it) and can be reached, and no other mob of the
	 * group is on it already. Once on it, it stays on it while that torch stands.
	 */
	public static boolean torchDuty(Mob mob, MobMind mind, Player player, long now) {
		if (!Lights.allowed(mob.level())) {
			return false;
		}
		Object[] duty = DUTY.get(player);
		boolean mine = duty != null && duty[0] == mob && (long) duty[1] > now;
		if (mine && mind.decision.tactic() == Tactic.APAGAR_LUZ && mind.lightTarget != null
			&& Lights.breakable(mob.level().getBlockState(mind.lightTarget))) {
			duty[1] = now + DUTY_TICKS;
			return true;
		}
		if (!mine && duty != null && duty[0] instanceof Mob other && other.isAlive() && (long) duty[1] > now) {
			return false;
		}
		List<Lights.Torch> torches = Lights.forMob(mob, player);
		if (torches.isEmpty() || torches.get(0).share() < WORTH || !Lights.reachable(mob, torches.get(0).pos())) {
			if (mine) {
				DUTY.remove(player);
			}
			return false;
		}
		DUTY.put(player, new Object[] {mob, now + DUTY_TICKS});
		return true;
	}

	/** APAGAR_LUZ, one tick (see the class). Returns false when there is no torch it can go for. */
	public static boolean putOut(Mob mob, MobMind mind, Player player, long now) {
		if (!Lights.allowed(mob.level())) {
			mind.lightTarget = null;
			return false;
		}
		BlockPos torch = mind.lightTarget;
		if (torch == null || !Lights.breakable(mob.level().getBlockState(torch))) {
			List<Lights.Torch> torches = Lights.forMob(mob, player);
			torch = torches.isEmpty() || !Lights.reachable(mob, torches.get(0).pos()) ? null : torches.get(0).pos();
			mind.lightTarget = torch;
			mind.lightTicks = 0;
			mind.lightSince = now;
			if (torch == null) {
				return false;
			}
		}
		Vec3 centre = Vec3.atCenterOf(torch);
		boolean inReach = mob.getEyePosition().distanceTo(centre) <= MobActions.LIGHT_REACH
			&& mob.level().clip(new ClipContext(mob.getEyePosition(), centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
		if (!inReach) {
			mind.lightTicks = 0;
			if (now - mind.lightSince > LIGHT_PATIENCE) {
				Lights.unreachable(mob.level(), torch);
				mind.lightTarget = null;
				return false;
			}
			BlockPos stand = Lights.standing(mob.level(), torch);
			if (stand == null) {
				Lights.unreachable(mob.level(), torch);
				mind.lightTarget = null;
				return false;
			}
			if (mind.pathDue(mob.level().getGameTime())) {
				if (!mob.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 1.0) && mob.getNavigation().isDone()) {
					Lights.unreachable(mob.level(), torch);
					mind.lightTarget = null;
					return false;
				}
			}
			return true;
		}
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(centre.x, centre.y, centre.z);
		if (mind.lightTicks % 5 == 0) {
			mob.swing(InteractionHand.MAIN_HAND);
			if (mob.level() instanceof ServerLevel level) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(torch)), centre.x, centre.y, centre.z, 4, 0.1, 0.1, 0.1, 0.02);
				level.sendParticles(ParticleTypes.SMOKE, centre.x, centre.y + 0.2, centre.z, 2, 0.05, 0.05, 0.05, 0.01);
			}
		}
		if (++mind.lightTicks < STRIKE_TICKS) {
			return true;
		}
		MobActions.breakLight(mob, torch);
		mind.lightTarget = null;
		mind.lightTicks = 0;
		Lights.forget(player);
		return true;
	}
}
