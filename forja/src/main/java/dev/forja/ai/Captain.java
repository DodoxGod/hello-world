package dev.forja.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.AttackTokens;
import dev.forja.difficulty.Threat;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The captain (docs/red_mob_v4_diseno.md §1.3, §3.2–§3.4, §4.1–§4.3; Andy's decision 4, 2026-09-29): one mind per group
 * (the monsters fighting the same player) that gives discrete orders every Squad pass (10 ticks), and the group's
 * morale.
 *
 * <ul>
 *   <li><b>Who</b>: the strongest elite or champion of the group, never a veteran; a group without one has no captain
 *   and goes by its old rules. When the captain dies the group is leaderless (sin_mando 1 → 0 over
 *   {@link #LEADERLESS} ticks), with no orders even if another elite is there; after that another may take over. The one
 *   exception (captain 2, Andy 2026-09-30): {@link #SUCCESSION} ticks after a captain fell, a veteran leads as a weaker
 *   acting captain until an elite takes over.</li>
 *   <li><b>Orders</b>: from red_capitan.json (CaptainNet) when there is one, else by the rules ({@link #rules}): an
 *   order, a formation, a sector, a countdown, a focus and a post for each member. The Squad turns the formation and
 *   the posts into a point per member ({@link #place}).</li>
 *   <li><b>The synchronized charge</b>: CARGA with a countdown; at 0 the captain shouts (and at half of it) and the
 *   player allows one more turn for {@link #CHARGE_TURN_TICKS} ticks (2 s).</li>
 *   <li><b>Morale</b>: the group's losses, its captain's death, fear, the player's health, being at home (§4.3).</li>
 * </ul>
 * The members see all of it as inputs (blocks M and Mo) and decide; the rule brain obeys by rules.
 *
 * <p><b>Captain 2</b> (docs/mod_spec_capitan2.md; docs/red_mob_v4_mod_estado.md, "Capitán 2"), each piece with its switch
 * (CaptainBrain.piece): shared vision ({@link #share}), the acting veteran (succession), the captain's protection and its
 * escorts ({@link #protect}), the new orders CERRAR_SALIDAS, FOCO_HERIDO and RETIRADA_FALSA (only a network of contract
 * revision 2 gives them; the members see them as older ones, {@link #seenAs}) and the visible captain (a shout with each
 * new order).
 */
public final class Captain {
	/**
	 * The orders. The first nine are contract v1's; the last three (captain 2, docs/mod_spec_capitan2.md §4) only a captain
	 * network of contract revision 2 gives, and the members see each as one of the first nine ({@link #seenAs}): the mob's
	 * contract (red_mob_v4) does not change.
	 */
	public enum Order {
		NINGUNA, CERCAR, CARGA, HOSTIGAR, RETIRADA, REAGRUPAR, EMBOSCADA, ASEDIO, ESCOLTA, CERRAR_SALIDAS, FOCO_HERIDO, RETIRADA_FALSA;

		/** How many orders contract v1 has (and the members' block M sees). */
		public static final int V1 = 9;
	}

	public enum Formation { LIBRE, MURO, PINZA, CUNA }

	public static final int FRENTE = 0;
	public static final int SEGUNDA = 1;
	public static final int FLANCO = 2;
	public static final int RESERVA = 3;
	/** The countdowns a CARGA can have, in ticks (cuenta 0..3). */
	public static final int[] COUNTS = {0, 10, 20, 40};
	/** Ticks with no orders after the captain dies (sin_mando). */
	public static final int LEADERLESS = 200;
	/** One more turn on the player for this long after the charge (Andy: 2 s). */
	public static final int CHARGE_TURN_TICKS = 40;
	/** A blow to morale counts this long (furia_disponible, and the morale formula). */
	public static final int MORALE_BLOW = 200;
	/** Deaths counted as recent (bajas_recientes/5). */
	public static final int RECENT = 200;
	/** Members the captain gives a post to by name (its network sees 8); the rest take theirs by kind. */
	public static final int MEMBERS = 8;
	/** A group that has had nobody for this long is a new fight next time. */
	public static final int FORGET = 60;
	/** How close to its post a member counts as in it. */
	public static final double IN_POST = 2.0;

	// ---- captain 2 (docs/mod_spec_capitan2.md), each piece with its switch (CaptainBrain.piece)
	/** Shared vision: the members within this of the captain (3D) get what the group saw. */
	public static final double SHARED_RANGE = 32.0;
	/** Shared vision: a member that perceived the player less than this many ticks ago gives what it saw. */
	public static final int SHARED_FRESH = 10;
	/** Succession: this long after the captain died, a veteran takes command as an acting captain (once a fight). */
	public static final int SUCCESSION = 60;
	/** An acting captain decides only on the passes with now % this == 0. */
	public static final int INTERIM_EVERY = 20;
	/** The group's morale is this much lower while an acting captain leads. */
	public static final double INTERIM_MORALE = 0.15;
	/** Protection: behind the front at this from the player, back to this under {@link #GUARD_HEALTH} of its health. */
	public static final double GUARD_BEHIND = 7.0;
	public static final double GUARD_RETREAT = 14.0;
	public static final double GUARD_HEALTH = 0.35;
	/** Protection: behind only with at least this many others that fight up close (and it fights up close itself). */
	public static final int GUARD_MELEE = 3;
	/** Protection: a captain behind with a turn and the player this close fights as ever. */
	public static final double GUARD_FIGHT = 3.5;
	/** Escorts: while the player is within this of the captain, up to {@link #ESCORTS}, 2 ahead of it and 1 to a side. */
	public static final double ESCORT_RANGE = 10.0;
	public static final int ESCORTS = 2;
	public static final double ESCORT_AHEAD = 2.0;
	public static final double ESCORT_SIDE = 1.0;
	/** The captain's guard: none, behind the front, or retreating. */
	public static final int GUARD_NONE = 0;
	public static final int GUARD_BEHIND_FRONT = 1;
	public static final int GUARD_RETREATING = 2;
	/** The protection head of a v2 network: 0 by the rules, 1 none, 2 retreat now. */
	public static final int PROTECT_RULES = 0;
	public static final int PROTECT_NONE = 1;
	public static final int PROTECT_RETREAT = 2;
	/** CERRAR_SALIDAS: the bodies spread over ±60° of the way out, 5 from the player; the way out is the player's motion over 0.05. */
	public static final double EXITS_RADIUS = 5.0;
	public static final double EXITS_HALF = Math.toRadians(60.0);
	public static final double EXITS_MOVING = 0.05;
	/** RETIRADA_FALSA: 10 from the player, going away, for 40 ticks or until the player comes 4 on towards the group. */
	public static final double FALSE_RADIUS = 10.0;
	public static final int FALSE_TICKS = 40;
	public static final double FALSE_ADVANCE = 4.0;
	/** FOCO_HERIDO (and foco 1): the other player within this of the captain. */
	public static final double FOCUS_RANGE = 16.0;

	/** The captain's orders as they stand. */
	public static final class Command {
		public Order order = Order.NINGUNA;
		public Formation formation = Formation.LIBRE;
		/** 0 none, 1..8 the side it wants the attack from, from the player's slow look, 45° apart turning right. */
		public int sector;
		/** 0..3: 0, 10, 20 or 40 ticks (COUNTS). */
		public int count;
		/** 0 its own player, 1 the other player nearest (with two about). */
		public int focus;
		/** Each member's post (FRENTE..RESERVA), in the order of Group.members; null for the rules' by kind. */
		public int[] posts;
		public long givenAt = Long.MIN_VALUE / 2;
		/** When the charge goes (CARGA): givenAt + its countdown. */
		public long chargeAt = Long.MIN_VALUE / 2;
		boolean shoutedHalf;
		boolean shoutedGo;
		/** RETIRADA_FALSA: whether it has turned to the attack (phase 2), and where the player stood and the way to the group then. */
		public boolean falseAttack;
		Vec3 falseFrom;
		Vec3 falseToGroup;

		/** No order and no formation: the members fight free, as without a captain (the ring and the turns). */
		public boolean free() {
			return this.order == Order.NINGUNA && this.formation == Formation.LIBRE;
		}

		Command copyOrder() {
			Command c = new Command();
			c.order = this.order;
			c.formation = this.formation;
			c.sector = this.sector;
			c.count = this.count;
			c.focus = this.focus;
			return c;
		}

		/** Whether it is the same order as another, for the tests: order, formation, sector, countdown and posts. */
		public boolean sameAs(Command other) {
			return other != null && this.order == other.order && this.formation == other.formation && this.sector == other.sector
				&& this.count == other.count && this.focus == other.focus && java.util.Arrays.equals(this.posts, other.posts);
		}

		@Override
		public String toString() {
			return this.order + "/" + this.formation + " sector " + this.sector + " cuenta " + this.count + " foco " + this.focus;
		}
	}

	/** The order as the members see it (block M, and block O of a v1 captain): the new ones as one of the first nine. */
	public static Order seenAs(Command c) {
		return switch (c.order) {
			case CERRAR_SALIDAS -> Order.CERCAR;
			case FOCO_HERIDO -> Order.NINGUNA;
			case RETIRADA_FALSA -> c.falseAttack ? Order.CARGA : Order.RETIRADA;
			default -> c.order;
		};
	}

	/** One group: the monsters fighting one player. */
	public static final class Group {
		public Mob captain;
		public long captainDiedAt = Long.MIN_VALUE / 2;
		public Command command = new Command();
		public int dead;
		public int peak;
		public final ArrayDeque<Long> deaths = new ArrayDeque<>();
		public long blowAt = Long.MIN_VALUE / 2;
		public long chargeUntil = Long.MIN_VALUE / 2;
		public long startedAt;
		public long seenAt = Long.MIN_VALUE / 2;
		public List<MobMind> members = List.of();
		/** When its last charge was ordered (the rules rest between charges, CHARGE_REST). */
		public long lastChargeAt = Long.MIN_VALUE / 2;
		/** The captain network's memory (GRU), from nothing when a captain takes over. */
		public float[] memory;
		/** For the world's memory (M6): whether the player went up, or ran or shut themselves in, in this fight. */
		public boolean wentUp;
		public boolean ranOff;
		public boolean over;
		/** Captain 2: an acting veteran leads (succession), and whether this fight has had its one succession. */
		public boolean interim;
		public boolean succeeded;
		/** The protection in force: the v2 network's head (PROTECT_*), and the captain's guard this pass (GUARD_*). */
		public int protection;
		public int guard;
		/** How many members got the group's estimate at the last pass (compartida/8). */
		public int shared;
		/** The rules' order at the last decision of a v2 network (its inputs 213..232), and whether the network took command. */
		public Command rulesCommand;
		public boolean mando;
		/** Orders shouted by the visible captain in this fight. */
		public int shouts;
	}

	private static final Map<Player, Group> GROUPS = new WeakHashMap<>();
	private static final DustParticleOptions BANNER = new DustParticleOptions(0xE0B020, 1.4F);
	/** An acting captain's banner (succession): silver, not gold. */
	private static final DustParticleOptions ACTING = new DustParticleOptions(0xC8D0DC, 1.4F);

	private Captain() {
	}

	/** The group fighting this player, or null for none. */
	public static Group group(Player player) {
		return player == null ? null : GROUPS.get(Perception.real(player));
	}

	/** Forgets the group fighting this player (tests). */
	public static void forget(Player player) {
		GROUPS.remove(player);
	}

	/** Whether the captain of this mob's group gives it orders now: a captain, not leaderless, and it has a post. */
	public static Command commandFor(MobMind mind) {
		Group g = group(mind.target);
		return g != null && g.captain != null && g.captain.isAlive() && mind.post >= 0 ? g.command : null;
	}

	/** A blow to the group's morale in the last 200 ticks: its captain fell, or half of it is down. */
	public static boolean moraleBlow(Player player, long now) {
		Group g = group(player);
		return g != null && now - g.blowAt <= MORALE_BLOW;
	}

	/**
	 * How many fight this player, as the last Squad pass counted them (captain or not); 0 when no group has been seen
	 * fighting them lately.
	 */
	public static int groupSize(Player player, long now) {
		Group g = group(player);
		return g == null || now - g.seenAt > FORGET ? 0 : g.members.size();
	}

	/**
	 * The player reeling from a blow. Players have no balance bar (Posture leaves them out: stamina plays that part), so
	 * "staggered" never held for one; what staggers a player in the game is slowness II or more from a monster: a mob's
	 * shield parry (slowness II and weakness I, 30 ticks, shoved back 0.6), a zombie's lunge grab (slowness II, 30), a
	 * brute's charge (slowness III, 40), the Broken Mould's shield shove (slowness VI, weakness III, 30).
	 */
	public static boolean reeling(Player player) {
		net.minecraft.world.effect.MobEffectInstance slow = player.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
		return slow != null && slow.getAmplifier() >= 1;
	}

	/** Whether the synchronized charge's extra turn is on for this player (Aggression.maxAttackers). */
	public static boolean chargeTurn(Player player, long now) {
		Group g = group(player);
		return g != null && now < g.chargeUntil;
	}

	/** A member of a group died (MobAi's death event). */
	public static void onDeath(Mob mob, long now) {
		if (!(mob.getTarget() instanceof Player player)) {
			return;
		}
		Group g = GROUPS.get(player);
		if (g == null) {
			return;
		}
		g.dead++;
		g.deaths.addLast(now);
		if (mob == g.captain) {
			// an acting captain's fall is a captain's fall: leaderless again and a blow to morale (no second succession)
			g.captain = null;
			g.interim = false;
			g.guard = GUARD_NONE;
			g.captainDiedAt = now;
			g.blowAt = now;
			g.command = new Command();
			g.memory = null;
		}
		if (g.peak >= 2 && g.dead * 2 >= g.peak) {
			g.blowAt = now;
		}
	}

	// ---------------------------------------------------------------- the Squad pass

	/** Each Squad pass (10 ticks), for one player's group: its captain, its orders, its members' posts and points. */
	static void update(Player player, List<MobMind> members, long now) {
		Group g = GROUPS.computeIfAbsent(player, p -> new Group());
		if (now - g.seenAt > FORGET) {
			finish(player, g);
			Group fresh = new Group();
			fresh.startedAt = now;
			GROUPS.put(player, fresh);
			g = fresh;
			for (MobMind mind : members) {
				mind.furyUsed = false;
			}
		}
		g.seenAt = now;
		while (!g.deaths.isEmpty() && now - g.deaths.peekFirst() > RECENT) {
			g.deaths.removeFirst();
		}
		List<MobMind> sorted = new ArrayList<>(members);
		sorted.sort(java.util.Comparator.comparingDouble(m -> m.mob.distanceToSqr(player)));
		g.members = sorted;
		// the player's style in this fight (WorldMemory): up high 40 ticks, or away from all of them / unseen for 200
		g.wentUp |= Heights.upTicks(Heights.of(player), now) >= 40;
		long seen = Long.MIN_VALUE / 2;
		boolean near = false;
		for (MobMind mind : sorted) {
			seen = Math.max(seen, mind.lastSeenAt);
			near |= mind.mob.distanceTo(player) <= 24.0;
		}
		g.ranOff |= !near || now - seen >= 200 && now - g.startedAt >= 200;
		g.peak = Math.max(g.peak, sorted.size());
		Mob leading = g.captain;
		if (leading != null && (!leading.isAlive() || sorted.stream().noneMatch(m -> m.mob == leading))) {
			g.captain = null;
			g.interim = false;
			g.memory = null;
		}
		// An elite or champion takes over once the group has been leaderless LEADERLESS ticks: from nobody, or from an
		// acting veteran (the relief).
		if ((g.captain == null || g.interim) && now - g.captainDiedAt >= LEADERLESS && CaptainBrain.enabled(player)) {
			Mob best = strongest(sorted);
			if (best != null || g.captain == null) {
				g.captain = best;
				g.interim = false;
				g.memory = null;
			}
		}
		// Captain 2, succession (Andy, 2026-09-30): SUCCESSION ticks after a captain fell, the group's veteran with the most
		// health leads as an acting captain. Once a fight: when it falls too, nobody but an elite takes over.
		if (g.captain == null && !g.succeeded && g.captainDiedAt > Long.MIN_VALUE / 4 && now - g.captainDiedAt >= SUCCESSION
			&& CaptainBrain.enabled(player) && CaptainBrain.piece(player, CaptainBrain.Piece.SUCESION)) {
			Mob veteran = veteran(sorted);
			if (veteran != null) {
				g.captain = veteran;
				g.interim = true;
				g.succeeded = true;
				g.memory = null;
			}
		}
		if (g.captain == null) {
			g.command = new Command();
			g.guard = GUARD_NONE;
			g.shared = 0;
			for (MobMind mind : sorted) {
				mind.post = -1;
				mind.postPoint = null;
				mind.escort = false;
			}
			return;
		}
		// Captain 2, shared vision: what one of them saw, the group knows (before the decision: the captain reads compartida).
		if (CaptainBrain.piece(player, CaptainBrain.Piece.VISION)) {
			share(g, now);
		} else {
			g.shared = 0;
		}
		Command previous = g.command;
		// An acting captain decides on every other pass (now % 20 == 0); in between its order carries on, and the posts are
		// placed afresh.
		boolean decides = !g.interim || now % INTERIM_EVERY == 0;
		Command next = decides ? CaptainBrain.decide(g, player, now) : previous;
		if (decides) {
			// An order carries on (its age, its countdown) while it stays the same; a new one starts from now.
			if (next.order == previous.order && next.formation == previous.formation && next.sector == previous.sector
				&& (next.order != Order.CARGA || now < previous.chargeAt + CHARGE_TURN_TICKS)) {
				next.givenAt = previous.givenAt;
				next.chargeAt = previous.chargeAt;
				next.count = previous.count;
				next.shoutedHalf = previous.shoutedHalf;
				next.shoutedGo = previous.shoutedGo;
				next.falseAttack = previous.falseAttack;
				next.falseFrom = previous.falseFrom;
				next.falseToGroup = previous.falseToGroup;
			} else {
				next.givenAt = now;
				next.chargeAt = next.order == Order.CARGA ? now + COUNTS[Math.max(0, Math.min(3, next.count))] : Long.MIN_VALUE / 2;
				if (next.order == Order.CARGA) {
					g.lastChargeAt = now;
				}
				if (next.order == Order.RETIRADA_FALSA) {
					next.falseFrom = player.position();
					Vec3 middle = middle(g.members, null);
					Vec3 toGroup = middle.subtract(player.position()).multiply(1.0, 0.0, 1.0);
					next.falseToGroup = toGroup.lengthSqr() < 1.0E-6 ? Vec3.ZERO : toGroup.normalize();
				}
				// Captain 2, the visible captain: a shout with every new order but none-and-free (a charge shouts at its 0).
				if (!next.free() && next.order != Order.CARGA && CaptainBrain.piece(player, CaptainBrain.Piece.VISIBLE)
					&& g.captain.level() instanceof ServerLevel level) {
					g.shouts++;
					orderShout(level, g.captain, g.interim);
				}
			}
		}
		g.command = next;
		place(g, player, now);
		protect(g, player, now);
		if (next.focus == 1) {
			focus(g, player);
		}
		if (next.order == Order.FOCO_HERIDO) {
			focusHurt(g, player);
		}
	}

	/** The middle of the members (flat), leaving one out (or none). */
	static Vec3 middle(List<MobMind> members, Mob except) {
		double x = 0.0;
		double y = 0.0;
		double z = 0.0;
		int n = 0;
		for (MobMind mind : members) {
			if (mind.mob != except) {
				x += mind.mob.getX();
				y += mind.mob.getY();
				z += mind.mob.getZ();
				n++;
			}
		}
		return n == 0 ? Vec3.ZERO : new Vec3(x / n, y / n, z / n);
	}

	/** Whether it fights up close: not an archer, nor one that shoots or casts by nature (as the rules' count of them). */
	public static boolean melee(Mob mob) {
		MobFamily family = MobFamily.of(mob);
		return family != MobFamily.ARQUERO && !RuleBrain.shootsOrCasts(mob, family);
	}

	/** How many of the group fight up close, the captain included. */
	public static int meleeCount(Group g) {
		int n = 0;
		for (MobMind mind : g.members) {
			n += melee(mind.mob) ? 1 : 0;
		}
		return n;
	}

	/** The group's veteran with the most health (threat 1, not a boss), or null: the acting captain of a succession. */
	private static Mob veteran(List<MobMind> members) {
		Mob best = null;
		for (MobMind mind : members) {
			if (Threat.of(mind.mob) == Threat.VETERANO && !dev.forja.difficulty.Bosses.isBoss(mind.mob) && mind.mob.isAlive()
				&& (best == null || mind.mob.getMaxHealth() > best.getMaxHealth())) {
				best = mind.mob;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- captain 2: shared vision

	/**
	 * Shared vision (docs/mod_spec_capitan2.md §1): of the members that perceived the player less than SHARED_FRESH ticks
	 * ago, the one that did last gives where it saw them, and when. Each member that does not see them now (the pass runs
	 * before this tick's thinking, so "now" is the last tick) and stands within SHARED_RANGE (3D) of the captain takes it as a
	 * sound heard, if it is newer than its own estimate; one with no picture of the player takes the giver's too.
	 */
	static void share(Group g, long now) {
		g.shared = 0;
		MobMind giver = null;
		for (MobMind mind : g.members) {
			if (mind.lastSeen != null && now - mind.perceivedAt < SHARED_FRESH && (giver == null || mind.perceivedAt > giver.perceivedAt)) {
				giver = mind;
			}
		}
		if (giver == null) {
			return;
		}
		long at = giver.perceivedAt;
		Vec3 where = giver.lastSeen;
		for (MobMind mind : g.members) {
			if (mind == giver || now - mind.perceivedAt <= 1 || mind.mob.distanceTo(g.captain) > SHARED_RANGE || at <= Perception.estimateAt(mind)) {
				continue;
			}
			mind.lastHeard = where;
			mind.lastHeardAt = at;
			if (mind.snapshot == null && giver.snapshot != null) {
				mind.snapshot = giver.snapshot.copy();
			}
			g.shared++;
		}
	}

	// ---------------------------------------------------------------- captain 2: protection

	/**
	 * Protecting the captain (docs/mod_spec_capitan2.md §3), after the posts are placed: under GUARD_HEALTH of its health (or
	 * the network's "retreat now") its point is GUARD_RETREAT from the player, on the side it is on, and it drops its turn;
	 * otherwise, fighting up close itself with GUARD_MELEE or more others that do, GUARD_BEHIND from the player towards the
	 * middle of the others. Its post is then the reserve, and every brain goes there (TacticGoal, {@link #guarded}). With the
	 * player within ESCORT_RANGE of it, up to two that fight up close and hold no turn (shields first, then the nearest to
	 * it) stand 2 ahead of it towards the player, 1 to either side (the first on the right), in the front post.
	 */
	static void protect(Group g, Player player, long now) {
		g.guard = GUARD_NONE;
		for (MobMind mind : g.members) {
			mind.escort = false;
		}
		if (!CaptainBrain.piece(player, CaptainBrain.Piece.PROTECCION) || g.protection == PROTECT_NONE) {
			return;
		}
		Mob captain = g.captain;
		MobMind own = MobAi.mind(captain);
		if (own == null) {
			return;
		}
		Vec3 p = player.position();
		double front = ObsV4.front(player, now);
		Vec3 point = null;
		if (captain.getHealth() < captain.getMaxHealth() * GUARD_HEALTH || g.protection == PROTECT_RETREAT) {
			Vec3 away = captain.position().subtract(p).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-6 ? new Vec3(Math.cos(front + Math.PI), 0.0, Math.sin(front + Math.PI)) : away.normalize();
			point = p.add(away.scale(GUARD_RETREAT));
			g.guard = GUARD_RETREATING;
			AttackTokens.release(player, captain);
		} else if (melee(captain)) {
			int others = 0;
			for (MobMind mind : g.members) {
				others += mind.mob != captain && melee(mind.mob) ? 1 : 0;
			}
			if (others >= GUARD_MELEE) {
				Vec3 toGroup = middle(g.members, captain).subtract(p).multiply(1.0, 0.0, 1.0);
				toGroup = toGroup.lengthSqr() < 1.0E-6 ? new Vec3(Math.cos(front + Math.PI), 0.0, Math.sin(front + Math.PI)) : toGroup.normalize();
				point = p.add(toGroup.scale(GUARD_BEHIND));
				g.guard = GUARD_BEHIND_FRONT;
			}
		}
		if (point != null) {
			own.post = RESERVA;
			own.postPoint = new Vec3(point.x, p.y, point.z);
		}
		if (captain.distanceTo(player) >= ESCORT_RANGE) {
			return;
		}
		List<MobMind> guards = new ArrayList<>();
		for (MobMind mind : g.members) {
			if (mind.mob != captain && melee(mind.mob) && !AttackTokens.holds(player, mind.mob)) {
				guards.add(mind);
			}
		}
		guards.sort(java.util.Comparator.<MobMind>comparingInt(m -> MobDefense.hasShield(m.mob) ? 0 : 1)
			.thenComparingDouble(m -> m.mob.distanceToSqr(captain)));
		Vec3 ahead = p.subtract(captain.position()).multiply(1.0, 0.0, 1.0);
		ahead = ahead.lengthSqr() < 1.0E-6 ? new Vec3(Math.cos(front + Math.PI), 0.0, Math.sin(front + Math.PI)) : ahead.normalize();
		// the frame's "derecha" is (−delante_z, delante_x)
		Vec3 right = new Vec3(-ahead.z, 0.0, ahead.x);
		for (int k = 0; k < Math.min(ESCORTS, guards.size()); k++) {
			MobMind mind = guards.get(k);
			Vec3 at = captain.position().add(ahead.scale(ESCORT_AHEAD)).add(right.scale(k == 0 ? ESCORT_SIDE : -ESCORT_SIDE));
			mind.escort = true;
			mind.post = FRENTE;
			mind.postPoint = new Vec3(at.x, p.y, at.z);
		}
	}

	/**
	 * Whether this mob is its group's protected captain and goes to its point whatever its brain decided (TacticGoal, as
	 * BUSCAR with a player lost): not behind the front with a turn and the player within GUARD_FIGHT (it fights then).
	 */
	public static boolean guarded(MobMind mind, long now) {
		Player target = mind.target;
		Group g = target == null ? null : group(target);
		if (g == null || g.captain != mind.mob || g.guard == GUARD_NONE || mind.postPoint == null || now - g.seenAt > FORGET) {
			return false;
		}
		return !(g.guard == GUARD_BEHIND_FRONT && AttackTokens.holds(target, mind.mob) && mind.mob.distanceTo(target) < GUARD_FIGHT);
	}

	// ---------------------------------------------------------------- captain 2: the new orders

	/** FOCO_HERIDO: another player in the fight within FOCUS_RANGE of the captain and more hurt (share of health) than this one, the most hurt; or null. */
	public static Player hurtOther(Group g, Player player) {
		if (g.captain == null) {
			return null;
		}
		Player real = Perception.real(player);
		double mine = real.getHealth() / Math.max(1.0F, real.getMaxHealth());
		Player best = null;
		double lowest = mine;
		for (Player p : g.captain.level().players()) {
			if (p == real || !p.isAlive() || p.isCreative() || p.isSpectator() || p.distanceToSqr(g.captain) > FOCUS_RANGE * FOCUS_RANGE) {
				continue;
			}
			double share = p.getHealth() / Math.max(1.0F, p.getMaxHealth());
			if (share < lowest) {
				lowest = share;
				best = p;
			}
		}
		return best;
	}

	/** FOCO_HERIDO: the members without a turn, but the captain, go for the more hurt player (as foco 1, chosen by health). */
	private static void focusHurt(Group g, Player player) {
		Player other = hurtOther(g, player);
		if (other == null) {
			return;
		}
		for (MobMind mind : g.members) {
			if (mind.mob != g.captain && !AttackTokens.holds(player, mind.mob)) {
				mind.mob.setTarget(other);
			}
		}
	}

	/**
	 * CERRAR_SALIDAS: the way out is the player's motion when over EXITS_MOVING a tick, else away from the group's middle.
	 * The ones that fight up close, sorted by their angle off that way, are spread over ±60° of it at EXITS_RADIUS from the
	 * player (one alone right on it); the others keep their formation's point.
	 */
	private static Map<MobMind, Vec3> exits(Group g, Player player) {
		Map<MobMind, Vec3> points = new java.util.HashMap<>();
		Vec3 moving = MobSprint.motion(player);
		double way;
		if (Math.hypot(moving.x, moving.z) > EXITS_MOVING) {
			way = Math.atan2(moving.z, moving.x);
		} else {
			Vec3 middle = middle(g.members, null);
			way = Math.atan2(player.getZ() - middle.z, player.getX() - middle.x);
		}
		List<MobMind> bodies = new ArrayList<>();
		for (MobMind mind : g.members) {
			if (melee(mind.mob)) {
				bodies.add(mind);
			}
		}
		double at = way;
		bodies.sort(java.util.Comparator.comparingDouble(m -> Squad.wrap(Squad.angle(m.mob, player) - at)));
		int n = bodies.size();
		for (int k = 0; k < n; k++) {
			double a = n == 1 ? way : way - EXITS_HALF + 2.0 * EXITS_HALF * k / (n - 1);
			points.put(bodies.get(k), new Vec3(player.getX() + Math.cos(a) * EXITS_RADIUS, player.getY(), player.getZ() + Math.sin(a) * EXITS_RADIUS));
		}
		return points;
	}

	/** The strongest elite or champion of the group (Andy: never a veteran), or null. */
	private static Mob strongest(List<MobMind> members) {
		Mob best = null;
		double score = -1.0;
		for (MobMind mind : members) {
			Threat threat = Threat.of(mind.mob);
			if (threat != Threat.ELITE && threat != Threat.CAMPEON) {
				continue;
			}
			double s = threat.ordinal() * 1000.0 + mind.mob.getMaxHealth();
			if (s > score) {
				score = s;
				best = mind.mob;
			}
		}
		return best;
	}

	/** Focus: with another player about, the members without a turn go for the other one nearest the captain. */
	private static void focus(Group g, Player player) {
		Player other = null;
		double best = 16.0 * 16.0;
		for (Player p : g.captain.level().players()) {
			if (p != player && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(g.captain) < best) {
				best = p.distanceToSqr(g.captain);
				other = p;
			}
		}
		if (other == null) {
			return;
		}
		for (MobMind mind : g.members) {
			if (mind.mob != g.captain && !AttackTokens.holds(player, mind.mob)) {
				mind.mob.setTarget(other);
			}
		}
	}

	// ---------------------------------------------------------------- the rules captain (§3.4)

	/** The post a member takes by its kind: shields and tanks in front, archers behind, fast ones on the flanks. */
	public static int postByKind(Mob mob) {
		if (MobDefense.hasShield(mob) || ForjaFamily.of(mob) == ForjaFamily.TANQUE || "forja_tanque".equals(MobFamily.network(mob))) {
			return FRENTE;
		}
		MobFamily family = MobFamily.of(mob);
		if (family == MobFamily.ARQUERO || mob instanceof RangedAttackMob && family != MobFamily.CUERPO
			|| mob instanceof net.minecraft.world.entity.monster.Witch) {
			return SEGUNDA;
		}
		MobMind mind = MobAi.mind(mob);
		double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
		if (family == MobFamily.ARANA || speed >= 0.3 || mind != null && mind.running) {
			return FLANCO;
		}
		return RESERVA;
	}

	/** After a charge's extra turn, this long before the rules call another (a charge is a moment, not a way of life). */
	public static final int CHARGE_REST = 100;
	/** The player backing away from the group faster than this (blocks a tick) is worth a pincer. */
	static final double BACKING = 0.08;

	/**
	 * The rules captain (2026-09-30). The first one ordered CERCAR nearly all the time: its members held their posts at 6 or
	 * more and nobody went in, and a charge needed 60 % of them in their posts and the player "busy" (someone holding a
	 * turn on a player nobody attacked) or a member behind them (never, with everyone at their posts), so a group with a
	 * captain did less damage than one without (the simulator: 111 against 121 a minute; the mod: CapitanMedidaGameTests).
	 * Now it gives an order only when it helps, and otherwise none: its members fight free, with the ring and the turns,
	 * as without a captain. In this order:
	 * <ul>
	 *   <li>the player up a pillar or in a tower → ASEDIO;</li>
	 *   <li>the group's morale under 0.3 → RETIRADA;</li>
	 *   <li>the player exposed (using an item, charging a blow, reeling ({@link #reeling}), under 30 % health, or with one
	 *   of them behind,
	 *   within 6 and more than 120 degrees off their look) with at least two that fight up close within 10, and no charge
	 *   in the last {@link #CHARGE_REST} ticks after the last one's extra turn → CARGA at once (countdown 0): all in, with
	 *   one more turn for 2 s;</li>
	 *   <li>at night, the player in a light under 7 and nobody seeing them → EMBOSCADA;</li>
	 *   <li>the player backing away from the group → no order, in a pincer (PINZA): those without a turn go round both
	 *   sides, those with one strike as ever;</li>
	 *   <li>otherwise no order and no formation (free).</li>
	 * </ul>
	 * CERCAR and HOSTIGAR are left to a captain network (they mean the same to the members), never given by the rules.
	 */
	public static Command rules(Group g, Player player, long now) {
		Command c = new Command();
		boolean seen = false;
		int close = 0;
		double cx = 0.0;
		double cz = 0.0;
		double look = Squad.facing(player);
		boolean backTurned = false;
		for (MobMind mind : g.members) {
			Mob mob = mind.mob;
			seen |= Perception.perceived(mind, now) || now - mind.perceivedAt < 20;
			double d = mob.distanceTo(player);
			MobFamily family = MobFamily.of(mob);
			close += d < 10.0 && family != MobFamily.ARQUERO && !RuleBrain.shootsOrCasts(mob, family) ? 1 : 0;
			// its back to the group: one of them within 6 behind the player (more than 120 degrees off their look)
			backTurned |= d < 6.0 && Math.abs(Squad.wrap(look - Squad.angle(mob, player))) > Math.toRadians(120.0);
			cx += mob.getX();
			cz += mob.getZ();
		}
		int n = Math.max(1, g.members.size());
		cx /= n;
		cz /= n;
		MobMind captainMind = MobAi.mind(g.captain);
		double morale = captainMind == null ? 1.0 : groupMorale(captainMind, player, now);
		Player real = Perception.real(player);
		boolean exposed = player.isUsingItem() || dev.forja.combat.ChargedStrike.isCharging(player)
			|| reeling(real) || real.getHealth() < real.getMaxHealth() * 0.3F || backTurned;
		boolean rested = now >= g.lastChargeAt + CHARGE_TURN_TICKS + CHARGE_REST;
		// the player backing away from the group's middle
		Vec3 moving = MobSprint.motion(player);
		double ax = player.getX() - cx;
		double az = player.getZ() - cz;
		double ad = Math.max(1.0E-6, Math.hypot(ax, az));
		boolean backing = (moving.x * ax + moving.z * az) / ad > BACKING && ad < 16.0;
		boolean night = player.level().isDarkOutside();
		if (Heights.besieged(player)) {
			c.order = Order.ASEDIO;
		} else if (morale < 0.3) {
			c.order = Order.RETIRADA;
		} else if (exposed && close >= 2 && rested) {
			c.order = Order.CARGA;
			c.count = 0;
		} else if (night && Lights.playerLight(player) < 7 && !seen) {
			c.order = Order.EMBOSCADA;
		} else if (backing && dev.forja.combat.CombatConfig.get().iaCapitanPinza) {
			c.formation = Formation.PINZA;
		}
		// A charge under way goes through: the rules do not call it off before its extra turn is over.
		if (g.command.order == Order.CARGA && now < g.command.chargeAt + CHARGE_TURN_TICKS && c.order != Order.ASEDIO && c.order != Order.RETIRADA) {
			c = g.command.copyOrder();
		}
		return c;
	}

	/** The posts of the rules: each member's by its kind (the captain's network gives its own to the first 8). */
	public static int[] rulesPosts(Group g) {
		int[] posts = new int[g.members.size()];
		for (int i = 0; i < posts.length; i++) {
			posts[i] = postByKind(g.members.get(i).mob);
		}
		return posts;
	}

	// ---------------------------------------------------------------- geometry (§3.2)

	/** The point of each member's post, from the formation, the order and the player's slow look. */
	static void place(Group g, Player player, long now) {
		Command c = g.command;
		double front = ObsV4.front(player, now);
		int[] posts = c.posts != null && c.posts.length == g.members.size() ? c.posts : rulesPosts(g);
		// Within a post, the places are handed out by where each one already stands round the player (by angle off the
		// front): handed out by distance, two of the same post swapped places whenever one got nearer, and both turned back.
		List<List<Integer>> byPost = new ArrayList<>();
		for (int post = 0; post < 4; post++) {
			byPost.add(new ArrayList<>());
		}
		for (int i = 0; i < g.members.size(); i++) {
			byPost.get(Math.max(0, Math.min(3, posts[i]))).add(i);
		}
		Vec3 p = player.position();
		Map<MobMind, Vec3> exits = c.order == Order.CERRAR_SALIDAS ? exits(g, player) : Map.of();
		for (int post = 0; post < 4; post++) {
			List<Integer> these = byPost.get(post);
			these.sort(java.util.Comparator.comparingDouble(i -> Squad.wrap(Squad.angle(g.members.get(i).mob, player) - front)));
			for (int k = 0; k < these.size(); k++) {
				MobMind mind = g.members.get(these.get(k));
				mind.post = post;
				Vec3 exit = exits.get(mind);
				mind.postPoint = exit != null ? exit : point(g, c, mind, player, p, front, post, k, these.size(), now);
			}
		}
	}

	private static Vec3 point(Group g, Command c, MobMind mind, Player player, Vec3 p, double front, int post, int k, int n, long now) {
		Mob mob = mind.mob;
		switch (c.order) {
			case RETIRADA_FALSA -> {
				// phase 1: 10 from the player, going away (FORMACION for the rules, not RETIRARSE); phase 2: on the player, as CARGA at 0
				if (c.falseAttack) {
					return p;
				}
				Vec3 away = mob.position().subtract(p).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-6 ? new Vec3(Math.cos(front + Math.PI), 0.0, Math.sin(front + Math.PI)) : away.normalize();
				return p.add(away.scale(FALSE_RADIUS));
			}
			case RETIRADA -> {
				Vec3 away = mob.position().subtract(p).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-6 ? new Vec3(Math.cos(front + Math.PI), 0.0, Math.sin(front + Math.PI)) : away.normalize();
				return p.add(away.scale(12.0));
			}
			case REAGRUPAR -> {
				double a = 2.0 * Math.PI * (g.members.indexOf(mind)) / Math.max(1, g.members.size());
				return g.captain.position().add(Math.cos(a) * 2.0, 0.0, Math.sin(a) * 2.0);
			}
			case ESCOLTA -> {
				Vec3 toPlayer = p.subtract(g.captain.position()).multiply(1.0, 0.0, 1.0);
				toPlayer = toPlayer.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : toPlayer.normalize();
				Vec3 side = new Vec3(-toPlayer.z, 0.0, toPlayer.x);
				int i = g.members.indexOf(mind);
				double lateral = (i % 2 == 0 ? 1.0 : -1.0) * (0.75 + 0.75 * (i / 2));
				return mob == g.captain ? mob.position() : g.captain.position().add(toPlayer.scale(2.5)).add(side.scale(lateral));
			}
			case EMBOSCADA -> {
				Ambush.Spot spot = Ambush.spot(mob, mind, player, now);
				if (spot != null) {
					return spot.pos();
				}
			}
			case ASEDIO -> {
				Vec3 spot = Siege.spot(mob, mind, player);
				if (spot != null) {
					return spot;
				}
			}
			case CARGA -> {
				if (now >= c.chargeAt) {
					return p;
				}
			}
			default -> {
			}
		}
		double[] polar = formation(c.formation, post, k, n, front, mind);
		double radius = polar[1];
		if (c.order == Order.CERCAR && (post == FRENTE || post == FLANCO)) {
			radius = Math.max(radius, 6.0);
		}
		return new Vec3(p.x + Math.cos(polar[0]) * radius, p.y, p.z + Math.sin(polar[0]) * radius);
	}

	/** A post's place in a formation, as {angle, radius} round the player (angles as Squad.angle, front = their slow look). */
	static double[] formation(Formation formation, int post, int k, int n, double front, MobMind mind) {
		double deg = Math.PI / 180.0;
		// The members of a post come sorted by their angle off the front (Captain.place): the first half takes the
		// left-hand side (negative angles), the rest the right, one alone the side it is on.
		double side = side(k, n, mind, front);
		int row = n <= 1 ? 0 : (side < 0.0 ? k : k - (n + 1) / 2);
		switch (formation) {
			case MURO -> {
				return switch (post) {
					case FRENTE -> new double[] {front + spread(k, n, 40.0 * deg), 3.5};
					case SEGUNDA -> new double[] {front + spread(k, n, 30.0 * deg), 8.5};
					case FLANCO -> new double[] {front + side * (100.0 * deg + row * 15.0 * deg), 4.0};
					default -> new double[] {front + spread(k, n, 20.0 * deg), 12.0};
				};
			}
			case PINZA -> {
				double fan = side * row * 12.0 * deg;
				return switch (post) {
					case FRENTE, FLANCO -> new double[] {front + side * 120.0 * deg + fan, 3.5};
					case SEGUNDA -> new double[] {front + side * 120.0 * deg + fan, 8.5};
					default -> new double[] {front + Math.PI + spread(k, n, 20.0 * deg), 10.0};
				};
			}
			case CUNA -> {
				return switch (post) {
					case FRENTE -> new double[] {front + spread(k, n, 20.0 * deg), 3.5};
					case SEGUNDA -> new double[] {front + side * 35.0 * deg, 6.0 + row};
					case FLANCO -> new double[] {front + side * 60.0 * deg, 5.0 + row};
					default -> new double[] {front + spread(k, n, 25.0 * deg), 9.0};
				};
			}
			default -> {
				double a = !Double.isNaN(mind.ringAngle) ? mind.ringAngle : front;
				return new double[] {a, mind.ringRadius};
			}
		}
	}

	/** -1 for the left-hand side of the front (negative angles), +1 for the right (see {@link #formation}). */
	private static double side(int k, int n, MobMind mind, double front) {
		if (n <= 1) {
			Player player = mind.target;
			double off = player == null ? 0.0 : Squad.wrap(Squad.angle(mind.mob, player) - front);
			return off < 0.0 ? -1.0 : 1.0;
		}
		return k < (n + 1) / 2 ? -1.0 : 1.0;
	}

	/** The k-th of n spread evenly over ±half (0 for one). */
	private static double spread(int k, int n, double half) {
		return n <= 1 ? 0.0 : -half + 2.0 * half * k / (n - 1);
	}

	// ---------------------------------------------------------------- each tick: the shouts and the banner

	/** A fight is over (its group gone for FORGET ticks): the player's style in it goes to the world's memory, once. */
	private static void finish(Player player, Group g) {
		if (!g.over && g.seenAt > Long.MIN_VALUE / 4) {
			g.over = true;
			WorldMemory.onFightEnd(player, g.wentUp, g.ranOff);
		}
	}

	/** Each tick (MobAi): the captain's banner, and its shouts in a charge (at half the countdown, and at 0). */
	static void tick(ServerLevel level, long now) {
		for (Map.Entry<Player, Group> entry : GROUPS.entrySet()) {
			Group g = entry.getValue();
			if (now - g.seenAt > FORGET && entry.getKey().level() == level) {
				finish(entry.getKey(), g);
			}
			Mob captain = g.captain;
			if (captain == null || !captain.isAlive() || captain.level() != level) {
				continue;
			}
			Command c = g.command;
			if (now % 20 == 0) {
				level.sendParticles(g.interim ? ACTING : BANNER, captain.getX(), captain.getY() + captain.getBbHeight() + 0.6, captain.getZ(), 4, 0.15, 0.2, 0.15, 0.0);
			}
			// RETIRADA_FALSA turns to the attack after FALSE_TICKS, or sooner when the player comes FALSE_ADVANCE on towards
			// the group (from where they stood when it was given): a shout, the charge's 0 and one more turn for 40 ticks.
			if (c.order == Order.RETIRADA_FALSA && !c.falseAttack) {
				Player player = entry.getKey();
				double advanced = c.falseFrom == null || c.falseToGroup == null ? 0.0
					: (player.getX() - c.falseFrom.x) * c.falseToGroup.x + (player.getZ() - c.falseFrom.z) * c.falseToGroup.z;
				if (now - c.givenAt >= FALSE_TICKS || advanced >= FALSE_ADVANCE) {
					c.falseAttack = true;
					c.chargeAt = now;
					c.shoutedGo = true;
					g.chargeUntil = now + CHARGE_TURN_TICKS;
					shout(level, captain, 1.2F);
					level.sendParticles(ParticleTypes.ANGRY_VILLAGER, captain.getX(), captain.getEyeY() + 0.5, captain.getZ(), 6, 0.4, 0.2, 0.4, 0.0);
					place(g, player, now);
					protect(g, player, now);
				}
				continue;
			}
			if (c.order != Order.CARGA) {
				continue;
			}
			long count = COUNTS[Math.max(0, Math.min(3, c.count))];
			if (!c.shoutedHalf && count >= 20 && now >= c.chargeAt - count / 2 && now < c.chargeAt) {
				c.shoutedHalf = true;
				shout(level, captain, 0.9F);
			}
			if (!c.shoutedGo && now >= c.chargeAt) {
				c.shoutedGo = true;
				g.chargeUntil = now + CHARGE_TURN_TICKS;
				shout(level, captain, 1.2F);
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, captain.getX(), captain.getEyeY() + 0.5, captain.getZ(), 6, 0.4, 0.2, 0.4, 0.0);
			}
		}
	}

	private static void shout(ServerLevel level, Mob captain, float pitch) {
		level.playSound(null, captain.getX(), captain.getY(), captain.getZ(), SoundEvents.PILLAGER_CELEBRATE, SoundSource.HOSTILE, 2.0F, pitch);
		level.playSound(null, captain.getX(), captain.getY(), captain.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6F, pitch);
	}

	/**
	 * Captain 2, the visible captain: a new order (not none-and-free) is a shout the player can place, and a burst of the
	 * banner's dust over its head (silver for an acting captain), so they can read who leads.
	 */
	static void orderShout(ServerLevel level, Mob captain, boolean acting) {
		level.playSound(null, captain.getX(), captain.getY(), captain.getZ(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.6F, acting ? 1.25F : 0.9F);
		level.sendParticles(acting ? ACTING : BANNER, captain.getX(), captain.getY() + captain.getBbHeight() + 0.6, captain.getZ(), 14, 0.35, 0.3, 0.35, 0.0);
	}

	// ---------------------------------------------------------------- morale (§4.3)

	/**
	 * moral_grupo = clamp(1 − 0.8·bajas_frac − 0.4·[captain dead less than 200 ticks ago] − 0.2·miedo + 0.2·[player under
	 * 30 % health] + 0.1·en_casa, 0, 1); miedo and en_casa are the mob's own. Captain 2: 0.15 less while an acting veteran
	 * leads (INTERIM_MORALE), on top of the 0.4 of the 200 leaderless ticks.
	 */
	public static double groupMorale(MobMind mind, Player player, long now) {
		Group g = group(player);
		double lost = g == null || g.peak <= 0 ? 0.0 : Math.min(1.0, g.dead / (double) g.peak);
		boolean leaderless = g != null && now - g.captainDiedAt < LEADERLESS;
		boolean acting = g != null && g.interim && g.captain != null && g.captain.isAlive();
		Player real = Perception.real(player);
		double m = 1.0 - 0.8 * lost - (leaderless ? 0.4 : 0.0) - (acting ? INTERIM_MORALE : 0.0) - (Personality.afraid(mind.mob) ? 0.2 : 0.0)
			+ (real.getHealth() < real.getMaxHealth() * 0.3F ? 0.2 : 0.0) + (Personality.atHome(mind.mob) ? 0.1 : 0.0);
		return Math.max(0.0, Math.min(1.0, m));
	}

	/**
	 * moral_propia = clamp(moral_grupo + {aggressive +0.2, careful 0, cunning 0, cowardly −0.2} − 0.3·(1 − its health
	 * share), 0, 1); elites, champions and bosses never under 0.8.
	 */
	public static double ownMorale(MobMind mind, Player player, long now) {
		Mob mob = mind.mob;
		double trait = switch (Personality.trait(mob)) {
			case AGRESIVO -> 0.2;
			case COBARDE -> -0.2;
			default -> 0.0;
		};
		double m = groupMorale(mind, player, now) + trait - 0.3 * (1.0 - mob.getHealth() / Math.max(1.0F, mob.getMaxHealth()));
		m = Math.max(0.0, Math.min(1.0, m));
		return Personality.fearless(mob) ? Math.max(0.8, m) : m;
	}

	// ---------------------------------------------------------------- blocks M and Mo of the mob's observation

	/** Block M (298–327); {@code player} is the player as the observation sees them. */
	static void observe(Mob mob, MobMind mind, Player player, long now, float[] out, int at) {
		Group g = group(player);
		double dx = player.getX() - mob.getX();
		double dz = player.getZ() - mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double fx = dx / d;
		double fz = dz / d;
		boolean led = g != null && g.captain != null && g.captain.isAlive();
		out[at] = led ? 1.0F : 0.0F;
		out[at + 1] = led && g.captain == mob ? 1.0F : 0.0F;
		if (g != null && now - g.captainDiedAt < LEADERLESS) {
			out[at + 2] = (float) (1.0 - (now - g.captainDiedAt) / (double) LEADERLESS);
		}
		Command c = led ? g.command : null;
		// the new orders of contract revision 2 as one of the first nine (seenAs): the mob's contract does not change
		out[at + 3 + (c == null ? 0 : seenAs(c).ordinal())] = 1.0F;
		if (c != null) {
			out[at + 12] = (float) ObsM1.clip((now - c.givenAt) / 40.0, 0.0, 2.0);
			if (c.order == Order.CARGA && now < c.chargeAt) {
				out[at + 13] = (float) ObsM1.clip((c.chargeAt - now) / 40.0, 0.0, 2.0);
			}
			if (c.sector > 0) {
				double a = ObsV4.front(Perception.real(player), now) + (c.sector - 1) * Math.PI / 4.0;
				double vx = Math.cos(a);
				double vz = Math.sin(a);
				out[at + 14] = (float) (vx * fx + vz * fz);
				out[at + 15] = (float) (vx * -fz + vz * fx);
			}
		}
		out[at + 16 + (c == null ? 0 : c.formation.ordinal())] = 1.0F;
		if (c != null && mind.post >= 0) {
			out[at + 20 + mind.post] = 1.0F;
			if (mind.postPoint != null) {
				double ox = mind.postPoint.x - mob.getX();
				double oz = mind.postPoint.z - mob.getZ();
				out[at + 24] = (float) ObsM1.clip((ox * fx + oz * fz) / 8.0, -2.0, 2.0);
				out[at + 25] = (float) ObsM1.clip((ox * -fz + oz * fx) / 8.0, -2.0, 2.0);
			}
		}
		out[at + 26] = covered(mob, player) ? 1.0F : 0.0F;
		if (led) {
			double ox = g.captain.getX() - mob.getX();
			double oz = g.captain.getZ() - mob.getZ();
			out[at + 27] = (float) ObsM1.clip((ox * fx + oz * fz) / 16.0, -2.0, 2.0);
			out[at + 28] = (float) ObsM1.clip((ox * -fz + oz * fx) / 16.0, -2.0, 2.0);
			out[at + 29] = g.captain.getHealth() / Math.max(1.0F, g.captain.getMaxHealth());
		}
	}

	/**
	 * cubierto: an ally with a shield (or a tank) stands across the line from the player's eyes to its own (Squad's
	 * line of fire, the other way round).
	 */
	public static boolean covered(Mob mob, Player player) {
		Vec3 from = player.getEyePosition();
		Vec3 to = mob.getEyePosition();
		for (Mob other : mob.level().getEntitiesOfClass(Mob.class, new AABB(from, to).inflate(1.0),
			m -> m != mob && m.isAlive() && m instanceof Enemy)) {
			if ((MobDefense.hasShield(other) || postByKind(other) == FRENTE) && other.getBoundingBox().inflate(0.3).clip(from, to).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/** Block Mo (328–337). */
	static void observeMorale(Mob mob, MobMind mind, Player player, long now, float[] out, int at) {
		Group g = group(player);
		out[at] = (float) groupMorale(mind, player, now);
		out[at + 1] = (float) ownMorale(mind, player, now);
		if (g != null) {
			out[at + 2] = g.peak <= 0 ? 0.0F : (float) Math.min(1.0, g.dead / (double) g.peak);
			int recent = 0;
			for (long t : g.deaths) {
				recent += now - t <= RECENT ? 1 : 0;
			}
			out[at + 3] = (float) ObsM1.clip(recent / 5.0, 0.0, 2.0);
			int running = 0;
			int raging = 0;
			for (MobMind other : g.members) {
				if (other == mind || !other.mob.isAlive()) {
					continue;
				}
				running += other.retreatSince > Long.MIN_VALUE / 4 && now - other.retreatSince >= 40 ? 1 : 0;
				raging += other.furyActive(now) ? 1 : 0;
			}
			out[at + 4] = (float) ObsM1.clip(running / 5.0, 0.0, 2.0);
			out[at + 5] = (float) ObsM1.clip(raging / 5.0, 0.0, 2.0);
		}
		out[at + 6] = Fury.available(mind, now) ? 1.0F : 0.0F;
		out[at + 7] = mind.furyActive(now) ? (float) ((mind.furyUntil - now) / 200.0) : 0.0F;
		out[at + 8] = !mind.furyActive(now) && now < mind.exhaustedUntil ? (float) ((mind.exhaustedUntil - now) / 100.0) : 0.0F;
		out[at + 9] = mind.retreatSince > Long.MIN_VALUE / 4 ? (float) ObsM1.clip((now - mind.retreatSince) / 100.0, 0.0, 2.0) : 0.0F;
	}
}
