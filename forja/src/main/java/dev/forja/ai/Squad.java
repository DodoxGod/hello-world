package dev.forja.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.AttackTokens;
import dev.forja.combat.Posture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The monsters fighting the same player, as a group (ideas 11 to 20 of the plan). Every 10 ticks it
 * hands out, by rules:
 * <ul>
 *   <li>a slot on the ring around the player to each, evenly spaced, nearest first (so they surround
 *   instead of queueing);</li>
 *   <li>a role: the ones with a turn attack; with three or more, the one furthest round becomes the
 *   flanker and the nearest in front the distraction; archers cover; the rest wait on the ring;</li>
 *   <li>whether the group is falling back (half of it lost in the last ten seconds, or its leader);</li>
 *   <li>whether an ally lies staggered near the player (the rest close round it);</li>
 *   <li>and, with several players about, it shares the monsters out between them.</li>
 * </ul>
 * The roles and slots reach a network as inputs; they are never its decision.
 */
public final class Squad {
	/** How often the squads are worked out, in ticks. */
	public static final int PERIOD = 10;
	/** How long a group keeps falling back once it breaks, in ticks. */
	public static final int ROUT_TICKS = 60;
	/** Deaths remembered for a rout, in ticks. */
	public static final int LOSS_WINDOW = 200;
	/**
	 * The least room between two neighbours on the ring. Evenly spaced at 3.5 blocks, seven or more stood
	 * shoulder to shoulder, and anything that hits an area took them all; past that the ring widens instead.
	 */
	public static final double MIN_GAP = 3.0;

	/** What each player's group has lost lately: [time of loss, ...] and its biggest size. */
	private static final Map<Player, List<Long>> LOSSES = new WeakHashMap<>();
	private static final Map<Player, Integer> PEAK = new WeakHashMap<>();
	private static final Map<Player, Long> ROUTED_UNTIL = new WeakHashMap<>();
	private static final Map<Player, Mob> LEADER = new WeakHashMap<>();
	/** Where each player's ring starts, and when that was last used: kept while the fight goes on. */
	private static final Map<Player, double[]> START = new WeakHashMap<>();
	/** A ring start not used for this long is forgotten, and the next fight starts from its own side. */
	static final int START_MEMORY = 3 * PERIOD;

	private Squad() {
	}

	/** A member of the group died (called from the death event). */
	public static void onDeath(Mob mob, long now) {
		if (!(mob.getTarget() instanceof Player player)) {
			return;
		}
		List<Long> losses = LOSSES.computeIfAbsent(player, p -> new ArrayList<>());
		losses.add(now);
		losses.removeIf(t -> now - t > LOSS_WINDOW);
		int peak = PEAK.getOrDefault(player, 1);
		boolean leaderFell = LEADER.get(player) == mob;
		if (leaderFell || losses.size() * 2 >= peak && peak >= 3) {
			ROUTED_UNTIL.put(player, now + ROUT_TICKS);
			losses.clear();
			PEAK.put(player, 0);
		}
	}

	/** The ring's radius for n on it: 3.5, or wider so that neighbours are at least {@link #MIN_GAP} apart. */
	public static double ringRadius(int n) {
		return n < 2 ? TacticGoal.RING_RADIUS : Math.max(TacticGoal.RING_RADIUS, MIN_GAP / (2.0 * Math.sin(Math.PI / n)));
	}

	public static boolean routed(Player player, long now) {
		return ROUTED_UNTIL.getOrDefault(player, Long.MIN_VALUE) > now;
	}

	static void update(ServerLevel level, List<MobMind> minds, long now) {
		Map<Player, List<MobMind>> groups = new HashMap<>();
		for (MobMind mind : minds) {
			if (mind.target != null) {
				groups.computeIfAbsent(mind.target, p -> new ArrayList<>()).add(mind);
			}
		}
		share(level, groups);
		for (Map.Entry<Player, List<MobMind>> group : groups.entrySet()) {
			assign(group.getKey(), group.getValue(), now);
		}
	}

	/** Several players about: one with more than their share gives some of theirs to one with fewer. */
	private static void share(ServerLevel level, Map<Player, List<MobMind>> groups) {
		if (groups.size() < 2 || !dev.forja.combat.CombatConfig.get().iaRepartirObjetivos) {
			return;
		}
		for (Map.Entry<Player, List<MobMind>> crowded : groups.entrySet()) {
			for (Map.Entry<Player, List<MobMind>> quiet : groups.entrySet()) {
				if (crowded == quiet || crowded.getValue().size() - quiet.getValue().size() < 3
					|| crowded.getKey().distanceTo(quiet.getKey()) > 16.0) {
					continue;
				}
				// The member nearest the quieter player changes target.
				MobMind moving = crowded.getValue().stream()
					.min(java.util.Comparator.comparingDouble(m -> m.mob.distanceToSqr(quiet.getKey()))).orElse(null);
				if (moving != null && !AttackTokens.holds(crowded.getKey(), moving.mob)
					&& moving.mob.distanceTo(quiet.getKey()) < moving.mob.distanceTo(crowded.getKey())) {
					moving.mob.setTarget(quiet.getKey());
				}
				return;
			}
		}
	}

	private static void assign(Player player, List<MobMind> members, long now) {
		PEAK.merge(player, members.size(), Math::max);
		members.sort(java.util.Comparator.comparingDouble(m -> m.mob.distanceToSqr(player)));
		LEADER.put(player, leader(members));
		boolean routed = routed(player, now);

		// Slots on the ring, evenly spaced all the way round from the side the group comes from, and filled
		// outwards from there: the nearest takes the slot on its own side, the next the free one either side of
		// it (whichever side it is already on), and the last to arrive the ones round the back. Andy wants them
		// to surround ("que rodeen"), and taking the free slot nearest to each one's own angle, as this did,
		// let everyone who came the same way settle on the same side of the player.
		//
		// Only the ones that fight up close stand on it: an archer or a creeper holding a slot left a gap in the
		// ring where it stood off, and pushed the rest round it (Andy, 2026-09-29: "no rodean de verdad").
		// Those two keep a slot where they already are, so what they read of the ring says they are on it.
		//
		// The start is kept from one pass to the next while the fight lasts. Taken afresh from whoever was
		// nearest, it swung round each time another one got closer, and every slot moved with it: the group
		// chased slots that kept going away and bunched up behind them instead of taking the sides and back.
		List<MobMind> ring = new ArrayList<>();
		for (MobMind mind : members) {
			if (onRing(mind.mob)) {
				ring.add(mind);
			} else {
				mind.ringAngle = angle(mind.mob, player);
				mind.ringRadius = mind.mob.distanceTo(player);
			}
		}
		int n = ring.size();
		double radius = ringRadius(n);
		double[] kept = START.get(player);
		double start = n == 0 ? 0.0 : kept != null && now - (long) kept[1] <= START_MEMORY ? kept[0] : angle(ring.get(0).mob, player);
		START.put(player, new double[] {start, now});
		boolean[] taken = new boolean[n];
		for (MobMind mind : ring) {
			double own = angle(mind.mob, player);
			int slot = -1;
			for (int step = 0; step <= n / 2 && slot < 0; step++) {
				int left = Math.floorMod(step, n);
				int right = Math.floorMod(-step, n);
				boolean leftFree = !taken[left];
				boolean rightFree = right != left && !taken[right];
				if (leftFree && rightFree) {
					slot = Math.abs(wrap(start + left * 2.0 * Math.PI / n - own)) <= Math.abs(wrap(start + right * 2.0 * Math.PI / n - own)) ? left : right;
				} else if (leftFree) {
					slot = left;
				} else if (rightFree) {
					slot = right;
				}
			}
			slot = Math.max(0, slot);
			taken[slot] = true;
			mind.ringAngle = start + slot * 2.0 * Math.PI / n;
			mind.ringRadius = radius;
		}

		// A staggered ally near the player: the others close round it, between it and the player.
		MobMind down = null;
		for (MobMind mind : members) {
			if (Posture.isStaggered(mind.mob, now) && mind.mob.distanceTo(player) < 5.0) {
				down = mind;
				break;
			}
		}

		MobMind flanker = null;
		if (members.size() >= 3) {
			double facing = facing(player);
			double far = -1.0;
			for (MobMind mind : members) {
				if (MobFamily.of(mind.mob) == MobFamily.ARQUERO || MobFamily.of(mind.mob) == MobFamily.CREEPER) {
					continue;
				}
				double off = Math.abs(wrap(angle(mind.mob, player) - facing));
				if (off > far) {
					far = off;
					flanker = mind;
				}
			}
		}
		for (MobMind mind : members) {
			mind.routed = routed;
			mind.othersWaiting = members.size() > Aggression.maxAttackers(player);
			mind.guarding = down != null && down != mind;
			if (mind.guarding) {
				mind.ringAngle = angle(down.mob, player);
			}
			if (MobFamily.of(mind.mob) == MobFamily.ARQUERO) {
				mind.role = SquadRole.COBERTURA;
			} else if (mind == flanker) {
				mind.role = SquadRole.FLANCO;
			} else if (AttackTokens.holds(player, mind.mob)) {
				mind.role = SquadRole.ATACANTE;
			} else if (flanker != null && mind == firstMelee(members, flanker)) {
				mind.role = SquadRole.DISTRACTOR;
			} else {
				mind.role = SquadRole.RESERVA;
			}
		}
	}

	/** Whether it takes a slot on the ring: everything but the ones that fight from off it. */
	static boolean onRing(Mob mob) {
		MobFamily family = MobFamily.of(mob);
		return family != MobFamily.ARQUERO && family != MobFamily.CREEPER;
	}

	private static MobMind firstMelee(List<MobMind> members, MobMind except) {
		for (MobMind mind : members) {
			if (mind != except && MobFamily.of(mind.mob) != MobFamily.ARQUERO && MobFamily.of(mind.mob) != MobFamily.CREEPER) {
				return mind;
			}
		}
		return null;
	}

	/** The leader: the strongest by threat, then by max health. */
	private static Mob leader(List<MobMind> members) {
		Mob best = null;
		double score = -1.0;
		for (MobMind mind : members) {
			double s = dev.forja.difficulty.Threat.of(mind.mob).ordinal() * 1000.0 + mind.mob.getMaxHealth();
			if (s > score) {
				score = s;
				best = mind.mob;
			}
		}
		return best;
	}

	/** Angle of the mob seen from the player, atan2(z, x). */
	static double angle(Mob mob, Player player) {
		return Math.atan2(mob.getZ() - player.getZ(), mob.getX() - player.getX());
	}

	/** Angle the player faces, in the same convention. */
	static double facing(Player player) {
		double yaw = Math.toRadians(player.getYRot());
		return Math.atan2(Math.cos(yaw), -Math.sin(yaw));
	}

	static double wrap(double a) {
		while (a > Math.PI) a -= 2.0 * Math.PI;
		while (a < -Math.PI) a += 2.0 * Math.PI;
		return a;
	}

	/**
	 * The archer's answer to a friend in its line of fire: a step to the side away from that friend, two
	 * blocks, where the line is clear. Holding the arrow and standing there, it waited for a zombie that was
	 * busy fighting to move out of the way, and never shot (Andy, 2026-09-29). Nothing when the line is
	 * clear, or when the step would be into lava or off a drop.
	 */
	public static boolean stepToClearLine(Mob archer, Player target) {
		Vec3 step = clearLineStep(archer, target);
		if (step == null) {
			return false;
		}
		archer.getMoveControl().setWantedPosition(step.x, archer.getY(), step.z, 1.0);
		return true;
	}

	/** Where {@link #stepToClearLine} would send the archer, or null for nowhere. */
	public static @Nullable Vec3 clearLineStep(Mob archer, Player target) {
		Mob ally = allyInLineOf(archer, target);
		if (ally == null) {
			return null;
		}
		double dx = target.getX() - archer.getX();
		double dz = target.getZ() - archer.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		// The side the friend leans to, from the archer's line: the step goes the other way.
		double cross = dx * (ally.getZ() - archer.getZ()) - dz * (ally.getX() - archer.getX());
		double side = cross >= 0.0 ? -1.0 : 1.0;
		double x = archer.getX() - dz / d * side * 2.0;
		double z = archer.getZ() + dx / d * side * 2.0;
		if (Terrain.danger(archer.level(), x, z, archer.getY())) {
			return null;
		}
		return new Vec3(x, archer.getY(), z);
	}

	/** Whether any of the squad's other hostiles stands in the way of a shot from the archer at the target. */
	public static boolean allyInLine(Mob archer, Player target) {
		return allyInLineOf(archer, target) != null;
	}

	/** The nearest of its own side standing in its line of fire at the target, or null (red_mob_v3's aliado_en_linea). */
	public static Mob allyInLineOf(Mob archer, Player target) {
		var from = archer.getEyePosition();
		var to = target.getEyePosition();
		Mob nearest = null;
		for (Mob other : archer.level().getEntitiesOfClass(Mob.class, archer.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
			m -> m != archer && m.isAlive() && m instanceof Enemy)) {
			if (other.getBoundingBox().inflate(0.3).clip(from, to).isPresent()
				&& (nearest == null || archer.distanceToSqr(other) < archer.distanceToSqr(nearest))) {
				nearest = other;
			}
		}
		return nearest;
	}
}
