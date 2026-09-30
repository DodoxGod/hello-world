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
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
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
 *   {@link #LEADERLESS} ticks), with no orders even if another elite is there; after that another may take over.</li>
 *   <li><b>Orders</b>: from red_capitan.json (CaptainNet) when there is one, else by the rules ({@link #rules}): an
 *   order, a formation, a sector, a countdown, a focus and a post for each member. The Squad turns the formation and
 *   the posts into a point per member ({@link #place}).</li>
 *   <li><b>The synchronized charge</b>: CARGA with a countdown; at 0 the captain shouts (and at half of it) and the
 *   player allows one more turn for {@link #CHARGE_TURN_TICKS} ticks (2 s).</li>
 *   <li><b>Morale</b>: the group's losses, its captain's death, fear, the player's health, being at home (§4.3).</li>
 * </ul>
 * The members see all of it as inputs (blocks M and Mo) and decide; the rule brain obeys by rules.
 */
public final class Captain {
	public enum Order { NINGUNA, CERCAR, CARGA, HOSTIGAR, RETIRADA, REAGRUPAR, EMBOSCADA, ASEDIO, ESCOLTA }

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

		Command copyOrder() {
			Command c = new Command();
			c.order = this.order;
			c.formation = this.formation;
			c.sector = this.sector;
			c.count = this.count;
			c.focus = this.focus;
			return c;
		}
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
		/** The captain network's memory (GRU), from nothing when a captain takes over. */
		public float[] memory;
	}

	private static final Map<Player, Group> GROUPS = new WeakHashMap<>();
	private static final DustParticleOptions BANNER = new DustParticleOptions(0xE0B020, 1.4F);

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
			g.captain = null;
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
		g.peak = Math.max(g.peak, sorted.size());
		Mob leading = g.captain;
		if (leading != null && (!leading.isAlive() || sorted.stream().noneMatch(m -> m.mob == leading))) {
			g.captain = null;
			g.memory = null;
		}
		if (g.captain == null && now - g.captainDiedAt >= LEADERLESS && CaptainBrain.enabled()) {
			g.captain = strongest(sorted);
			g.memory = null;
		}
		if (g.captain == null) {
			g.command = new Command();
			for (MobMind mind : sorted) {
				mind.post = -1;
				mind.postPoint = null;
			}
			return;
		}
		Command previous = g.command;
		Command next = CaptainBrain.decide(g, player, now);
		// An order carries on (its age, its countdown) while it stays the same; a new one starts from now.
		if (next.order == previous.order && next.formation == previous.formation && next.sector == previous.sector
			&& (next.order != Order.CARGA || now < previous.chargeAt + CHARGE_TURN_TICKS)) {
			next.givenAt = previous.givenAt;
			next.chargeAt = previous.chargeAt;
			next.count = previous.count;
			next.shoutedHalf = previous.shoutedHalf;
			next.shoutedGo = previous.shoutedGo;
		} else {
			next.givenAt = now;
			next.chargeAt = next.order == Order.CARGA ? now + COUNTS[Math.max(0, Math.min(3, next.count))] : Long.MIN_VALUE / 2;
		}
		g.command = next;
		place(g, player, now);
		if (next.focus == 1) {
			focus(g, player);
		}
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

	/**
	 * The rules captain, in this order: the player up a pillar or in a tower → ASEDIO; the group's morale under 0.3 →
	 * RETIRADA; the player with a shield and a bow beyond 10 → HOSTIGAR; 60 % or more in their posts and the player busy
	 * (using an item, charging a blow, or someone holds a turn on them) or with one of them behind (within 8, more than 120
	 * degrees off their look) → CARGA (countdown 10); at night, the player in a light under 7 and nobody seeing
	 * them → EMBOSCADA; otherwise CERCAR, in a wall (MURO) with at least one shield and one archer, else a pincer.
	 */
	public static Command rules(Group g, Player player, long now) {
		Command c = new Command();
		int shields = 0;
		int archers = 0;
		int inPost = 0;
		int withPost = 0;
		boolean seen = false;
		double cx = 0.0;
		double cz = 0.0;
		for (MobMind mind : g.members) {
			Mob mob = mind.mob;
			shields += MobDefense.hasShield(mob) ? 1 : 0;
			archers += MobFamily.of(mob) == MobFamily.ARQUERO ? 1 : 0;
			if (mind.postPoint != null) {
				withPost++;
				inPost += mob.distanceToSqr(mind.postPoint.x, mob.getY(), mind.postPoint.z) < IN_POST * IN_POST ? 1 : 0;
			}
			seen |= Perception.perceived(mind, now) || now - mind.perceivedAt < 20;
			cx += mob.getX();
			cz += mob.getZ();
		}
		int n = Math.max(1, g.members.size());
		cx /= n;
		cz /= n;
		double spread = Math.hypot(player.getX() - cx, player.getZ() - cz);
		MobMind captainMind = MobAi.mind(g.captain);
		double morale = captainMind == null ? 1.0 : groupMorale(captainMind, player, now);
		boolean shieldAndBow = player.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)
			&& (player.getMainHandItem().getItem() instanceof BowItem || player.getMainHandItem().getItem() instanceof CrossbowItem);
		boolean busy = player.isUsingItem() || dev.forja.combat.ChargedStrike.isCharging(player) || AttackTokens.held(player) > 0;
		// Its back to the group: some member within 8 behind the player (more than 120 degrees off their look).
		double look = Squad.facing(player);
		boolean backTurned = false;
		for (MobMind mind : g.members) {
			backTurned |= mind.mob.distanceTo(player) < 8.0 && Math.abs(Squad.wrap(look - Squad.angle(mind.mob, player))) > Math.toRadians(120.0);
		}
		boolean night = player.level().isDarkOutside();
		if (Heights.besieged(player)) {
			c.order = Order.ASEDIO;
		} else if (morale < 0.3) {
			c.order = Order.RETIRADA;
		} else if (shieldAndBow && spread > 10.0) {
			c.order = Order.HOSTIGAR;
			c.formation = shields > 0 ? Formation.MURO : Formation.PINZA;
		} else if (withPost > 0 && inPost >= 0.6 * withPost && (busy || backTurned)) {
			c.order = Order.CARGA;
			c.count = 1;
			c.formation = g.command.formation == Formation.LIBRE ? Formation.PINZA : g.command.formation;
		} else if (night && Lights.playerLight(player) < 7 && !seen) {
			c.order = Order.EMBOSCADA;
		} else {
			c.order = Order.CERCAR;
			c.formation = shields >= 1 && archers >= 1 ? Formation.MURO : Formation.PINZA;
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
		for (int post = 0; post < 4; post++) {
			List<Integer> these = byPost.get(post);
			these.sort(java.util.Comparator.comparingDouble(i -> Squad.wrap(Squad.angle(g.members.get(i).mob, player) - front)));
			for (int k = 0; k < these.size(); k++) {
				MobMind mind = g.members.get(these.get(k));
				mind.post = post;
				mind.postPoint = point(g, c, mind, player, p, front, post, k, these.size(), now);
			}
		}
	}

	private static Vec3 point(Group g, Command c, MobMind mind, Player player, Vec3 p, double front, int post, int k, int n, long now) {
		Mob mob = mind.mob;
		switch (c.order) {
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

	/** Each tick (MobAi): the captain's banner, and its shouts in a charge (at half the countdown, and at 0). */
	static void tick(ServerLevel level, long now) {
		for (Map.Entry<Player, Group> entry : GROUPS.entrySet()) {
			Group g = entry.getValue();
			Mob captain = g.captain;
			if (captain == null || !captain.isAlive() || captain.level() != level) {
				continue;
			}
			Command c = g.command;
			if (now % 20 == 0) {
				level.sendParticles(BANNER, captain.getX(), captain.getY() + captain.getBbHeight() + 0.6, captain.getZ(), 4, 0.15, 0.2, 0.15, 0.0);
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

	// ---------------------------------------------------------------- morale (§4.3)

	/**
	 * moral_grupo = clamp(1 − 0.8·bajas_frac − 0.4·[captain dead less than 200 ticks ago] − 0.2·miedo + 0.2·[player under
	 * 30 % health] + 0.1·en_casa, 0, 1); miedo and en_casa are the mob's own.
	 */
	public static double groupMorale(MobMind mind, Player player, long now) {
		Group g = group(player);
		double lost = g == null || g.peak <= 0 ? 0.0 : Math.min(1.0, g.dead / (double) g.peak);
		boolean leaderless = g != null && now - g.captainDiedAt < LEADERLESS;
		Player real = Perception.real(player);
		double m = 1.0 - 0.8 * lost - (leaderless ? 0.4 : 0.0) - (Personality.afraid(mind.mob) ? 0.2 : 0.0)
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
		out[at + 3 + (c == null ? 0 : c.order.ordinal())] = 1.0F;
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
