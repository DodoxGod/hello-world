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

	/** What each player's group has lost lately: [time of loss, ...] and its biggest size. */
	private static final Map<Player, List<Long>> LOSSES = new WeakHashMap<>();
	private static final Map<Player, Integer> PEAK = new WeakHashMap<>();
	private static final Map<Player, Long> ROUTED_UNTIL = new WeakHashMap<>();
	private static final Map<Player, Mob> LEADER = new WeakHashMap<>();

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

		// Slots on the ring: evenly spaced from where the nearest stands, each taken by the closest free member.
		int n = members.size();
		double start = angle(members.get(0).mob, player);
		boolean[] taken = new boolean[n];
		for (MobMind mind : members) {
			double best = Double.MAX_VALUE;
			int slot = 0;
			for (int i = 0; i < n; i++) {
				if (taken[i]) {
					continue;
				}
				double gap = Math.abs(wrap(start + i * 2.0 * Math.PI / n - angle(mind.mob, player)));
				if (gap < best) {
					best = gap;
					slot = i;
				}
			}
			taken[slot] = true;
			mind.ringAngle = start + slot * 2.0 * Math.PI / n;
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
		if (n >= 3) {
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
			mind.othersWaiting = n > Aggression.maxAttackers(player);
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

	/** Whether any of the squad's other hostiles stands in the way of a shot from the archer at the target. */
	public static boolean allyInLine(Mob archer, Player target) {
		var from = archer.getEyePosition();
		var to = target.getEyePosition();
		for (Mob other : archer.level().getEntitiesOfClass(Mob.class, archer.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0),
			m -> m != archer && m.isAlive() && m instanceof Enemy)) {
			if (other.getBoundingBox().inflate(0.3).clip(from, to).isPresent()) {
				return true;
			}
		}
		return false;
	}
}
