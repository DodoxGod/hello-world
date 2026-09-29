package dev.forja.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.Forja;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.ForjaDifficulty;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Gives every hostile mob a brain and runs it: a network trained in the simulator when there is one for
 * its family (config/forja/redes/red_&lt;familia&gt;.json) and the mode allows it, the rules otherwise. Only
 * mobs with a player to fight think at all; the rest cost nothing.
 *
 * <p>A network decides every {@code ticks_por_decision} ticks, the rate it was trained at, never slower.
 */
public final class MobAi {
	public enum Mode { AUTO, REGLAS, RED }

	/** The network contract to run (CombatConfig.iaContrato): v1..v3.1 from redes, v4 from redes_v4, or v4 where there is one. */
	public enum Contract { V3, V4, AUTO }

	/** The "formato" of a v4 mob network, and the file name reserved for the v4 captain in redes_v4. */
	public static final String V4_FORMAT = ObsV4.FORMAT;
	public static final String V4_CAPTAIN = "capitan";
	/** The "formato" of the blaze's own network (docs/red_blaze_contrato.json): the only one a blaze accepts. */
	public static final String BLAZE_FORMAT = ObsBlaze.FORMAT;

	private static final Map<Mob, MobMind> MINDS = new WeakHashMap<>();
	/** Networks by family file name ("cuerpo", "forja_tanque"...), and why the ones that were refused were. */
	private static final Map<String, NetBrain> NETS = new java.util.LinkedHashMap<>();
	private static final Map<String, String> NET_PROBLEMS = new java.util.LinkedHashMap<>();
	private static boolean loaded;
	/**
	 * v4 files found in redes_v4 that this mod cannot run yet, by family: since M1 only the captain's
	 * (red_capitan.json, "capitan"), which waits for M5. And what was already logged, so a reload does not say it again.
	 */
	private static final Map<String, Path> V4_WAITING = new java.util.LinkedHashMap<>();
	private static final java.util.Set<String> V4_LOGGED = new java.util.HashSet<>();

	private MobAi() {
	}

	public static void register() {
		java.util.Objects.requireNonNull(PlayerHabits.VALUES);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && entity instanceof Enemy && !MINDS.containsKey(mob)) {
				MobMind mind = new MobMind(mob);
				MINDS.put(mob, mind);
				var goals = ((dev.forja.mixin.MobGoalsAccess) mob).forjaGoals();
				goals.addGoal(0, new TacticGoal(mob, mind));
				// Ahead of the specials at the same priority, so a caster's lunge never cuts into its casting.
				goals.addGoal(1, new dev.forja.entity.ai.CasterGoal(mob));
				List<Special> moveset = Movesets.of(mob);
				if (!moveset.isEmpty()) {
					mind.specials = new SpecialRunner(mob, moveset);
					goals.addGoal(1, new SpecialGoal(mob, mind));
				}
				goals.addGoal(3, new MovementGoals.Track(mob, mind));
				goals.addGoal(4, new MovementGoals.Home(mob));
				goals.addGoal(4, new MovementGoals.Curious(mob));
				if (MobFamily.of(mob) == MobFamily.ARQUERO) {
					goals.addGoal(3, new MovementGoals.HighGround(mob, mind));
				}
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(MobAi::tick);
		// the spider's web, the one block a monster puts down, and only for a moment (Andy, 2026-09-29: webs stay)
		TemporaryBlocks.register();
		ForjaTraits.register();
		Personality.register();
		WorldFights.register();
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob mob && entity instanceof Enemy) {
				Squad.onDeath(mob, mob.level().getGameTime());
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(MobAi::callForHelp);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(MobAi::pushToDanger);
	}

	public static Mode mode() {
		try {
			return Mode.valueOf(CombatConfig.get().iaModo.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException ignored) {
			return Mode.AUTO;
		}
	}

	public static Contract contract() {
		try {
			return Contract.valueOf(CombatConfig.get().iaContrato.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException ignored) {
			return Contract.AUTO;
		}
	}

	/**
	 * Whether this mod can feed a v4 mob network its observation: yes since M1 (ObsV4, Andy, 2026-09-29). Its heads are
	 * carried out as far as the executor goes today (see {@link #mask(Mob, MobMind, Player, int)}). The v4 captain
	 * (red_capitan.json) is still only noted: it comes with M5.
	 */
	public static boolean supportsV4() {
		return true;
	}

	public static MobMind mind(Mob mob) {
		return MINDS.get(mob);
	}

	/** The folder the networks are read from. */
	public static Path netFolder() {
		String folder = CombatConfig.get().iaCarpetaRedes;
		if (folder != null && !folder.isBlank()) {
			return Path.of(folder.trim());
		}
		return FabricLoader.getInstance().getConfigDir().resolve("forja").resolve("redes");
	}

	/**
	 * The folder the v4 networks are read from: redes_v4 beside {@link #netFolder()} (config/forja/redes_v4 by
	 * default). A folder of its own, so a v4 file is never mistaken for a v3b one with the same first 280 names.
	 */
	public static Path netFolderV4() {
		return netFolder().toAbsolutePath().resolveSibling("redes_v4");
	}

	/** The v4 files waiting in redes_v4 for a mod that can run them, by family. */
	public static Map<String, Path> v4Waiting() {
		return V4_WAITING;
	}

	/** (Re)reads every family's network, checking each against the observation it will be fed. */
	public static synchronized void reload() {
		NETS.clear();
		NET_PROBLEMS.clear();
		V4_WAITING.clear();
		if (contract() != Contract.V3) {
			readV4(V4_CAPTAIN);
		}
		for (String family : families()) {
			// The blaze has a contract of its own (red_blaze_v1), in either folder, whatever iaContrato says.
			if (MobFamily.BLAZE.file.equals(family)) {
				loadBlaze();
				continue;
			}
			// v4 first (docs/red_mob_v4_diseno.md §1.6): with "auto" or "v4", a v4 file that fits takes the family over.
			// One that does not fit is logged and the family carries on with its v3 file below, or the rules.
			if (contract() != Contract.V3) {
				NetBrain four = loadV4(family);
				if (four != null) {
					NETS.put(family, four);
					continue;
				}
			}
			Path file = netFolder().resolve("red_" + family + ".json");
			if (!Files.exists(file)) {
				continue;
			}
			try {
				NetBrain net = NetBrain.load(file);
				String problem = check(net);
				if (problem != null) {
					NET_PROBLEMS.put(family, problem);
					Forja.LOGGER.warn("Red {} descartada, se usan las reglas: {}", file.getFileName(), problem);
				} else {
					NETS.put(family, net);
					Forja.LOGGER.info("Red cargada para {}: {} ({} ticks por decisión)", family, file.getFileName(), net.ticksPerDecision);
				}
			} catch (Exception failure) {
				NET_PROBLEMS.put(family, failure.getMessage());
				Forja.LOGGER.warn("No se pudo leer la red {}", file, failure);
			}
		}
		loaded = true;
	}

	/**
	 * The blaze's network (Andy, 2026-09-29): red_blaze.json from redes_v4, or else from redes, with "formato":
	 * "red_blaze_v1" and ObsBlaze's inputs (see {@link #checkBlaze}). Any other file there (a v3 or v4 mob network) is
	 * refused with its reason in {@link #problems()}, and the blaze keeps to its rules: vanilla's own goals.
	 */
	private static void loadBlaze() {
		String family = MobFamily.BLAZE.file;
		for (Path folder : new Path[] {netFolderV4(), netFolder()}) {
			Path file = folder.resolve("red_" + family + ".json");
			if (!Files.exists(file)) {
				continue;
			}
			try {
				NetBrain net = NetBrain.load(file);
				String problem = checkBlaze(net);
				if (problem == null) {
					NETS.put(family, net);
					NET_PROBLEMS.remove(family);
					Forja.LOGGER.info("Red del blaze cargada: {} ({} ticks por decisión)", file, net.ticksPerDecision);
					return;
				}
				NET_PROBLEMS.put(family, problem);
				Forja.LOGGER.warn("Red del blaze {} descartada, el blaze sigue con las reglas: {}", file, problem);
			} catch (Exception failure) {
				NET_PROBLEMS.put(family, String.valueOf(failure.getMessage()));
				Forja.LOGGER.warn("No se pudo leer la red del blaze {}", file, failure);
			}
		}
	}

	/** Why a network cannot drive a blaze, or null: the red_blaze_v1 contract, exactly (BlazeBrain.check). */
	public static String checkBlaze(NetBrain net) {
		return BlazeBrain.check(net);
	}

	/**
	 * The v4 file redes_v4 holds for this family, parsed, or null: none, unreadable, or with a formato that is not v4
	 * (that one belongs in redes, and is not used). One the mod cannot run yet (the captain's, until M5) is kept in
	 * {@link #v4Waiting()}, logged once, and also comes back null.
	 */
	private static com.google.gson.JsonObject readV4(String family) {
		Path file = netFolderV4().resolve("red_" + family + ".json");
		if (!Files.exists(file)) {
			return null;
		}
		com.google.gson.JsonObject json;
		String format;
		try (java.io.Reader reader = Files.newBufferedReader(file)) {
			json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
			format = json.has("formato") ? json.get("formato").getAsString() : null;
		} catch (Exception failure) {
			NET_PROBLEMS.put(family, "v4: " + failure.getMessage());
			Forja.LOGGER.warn("No se pudo leer la red v4 {}", file, failure);
			return null;
		}
		boolean captain = V4_CAPTAIN.equals(family);
		boolean v4 = captain ? "red_capitan_v4".equals(format) : V4_FORMAT.equals(format);
		if (!v4) {
			if (V4_LOGGED.add(family + ":" + format)) {
				Forja.LOGGER.warn("{} está en redes_v4 pero su formato es '{}', no v4: no se usa", file.getFileName(), format);
			}
			return null;
		}
		if (captain || !supportsV4()) {
			V4_WAITING.put(family, file);
			if (V4_LOGGED.add(family)) {
				Forja.LOGGER.info(captain ? "red de capitán v4 encontrada, aún no soportada (llega en M5): los grupos siguen con sus reglas"
					: "red v4 encontrada para {}, aún no soportada: se usa v3", family);
			}
			return null;
		}
		return json;
	}

	/**
	 * The family's v4 network from redes_v4, if there is one and it fits ObsV4 and the v4 outputs; null otherwise, with
	 * the reason in {@link #problems()} ("v4: ...") when there was a file that did not fit.
	 */
	private static NetBrain loadV4(String family) {
		com.google.gson.JsonObject json = readV4(family);
		if (json == null) {
			return null;
		}
		Path file = netFolderV4().resolve("red_" + family + ".json");
		try {
			NetBrain net = NetBrain.fromJson(json);
			String problem = checkV4(net);
			if (problem != null) {
				NET_PROBLEMS.put(family, "v4: " + problem);
				Forja.LOGGER.warn("Red v4 {} descartada, se usa la v3 o las reglas: {}", file, problem);
				return null;
			}
			Forja.LOGGER.info("Red v4 cargada para {}: {} ({} ticks por decisión)", family, file, net.ticksPerDecision);
			return net;
		} catch (Exception failure) {
			NET_PROBLEMS.put(family, "v4: " + failure.getMessage());
			Forja.LOGGER.warn("No se pudo leer la red v4 {}", file, failure);
			return null;
		}
	}

	/**
	 * Why a v4 network cannot be used here, or null: its formato must be red_mob_v4, its inputs exactly ObsV4's 468 in
	 * the same order, and its outputs the contract's 53. Unlike v1..v3, a shorter prefix is not accepted: the contract
	 * is one size.
	 */
	public static String checkV4(NetBrain net) {
		if (!V4_FORMAT.equals(net.format)) {
			return "formato '" + net.format + "': no es " + V4_FORMAT;
		}
		List<String> ours = ObsV4.names();
		if (net.inputs() != ours.size() || net.names.size() != ours.size()) {
			return "espera " + net.inputs() + " entradas con " + net.names.size() + " nombres; la v4 tiene " + ours.size();
		}
		for (int i = 0; i < ours.size(); i++) {
			if (!ours.get(i).equals(net.names.get(i))) {
				return "la entrada " + i + " es '" + net.names.get(i) + "' y el mod da '" + ours.get(i) + "'";
			}
		}
		if (net.outputs() != NetBrain.V4_OUTPUTS) {
			return "da " + net.outputs() + " salidas y la v4 tiene " + NetBrain.V4_OUTPUTS;
		}
		return null;
	}

	/**
	 * Whether a network's formato is one of the contracts this loader runs: none (the first v1 files), or red_mob_v1,
	 * v2 or v3 with anything after that is not another digit (v3b, v3_1...). A v4 or a captain's file is not.
	 */
	static boolean v3Format(String format) {
		if (format == null) {
			return true;
		}
		for (String ok : new String[] {"red_mob_v1", "red_mob_v2", "red_mob_v3"}) {
			if (format.startsWith(ok) && (format.length() == ok.length() || !Character.isDigit(format.charAt(ok.length())))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Why a network cannot be used here, or null: its formato must be one we run (docs/red_mob_v4_diseno.md §1.6: by
	 * formato, not by the names, since v4's first 280 are v3b's), and its inputs ours, in our order.
	 */
	public static String check(NetBrain net) {
		if (!v3Format(net.format)) {
			return "formato '" + net.format + "': no es una red v1..v3" + (V4_FORMAT.equals(net.format) ? " (las v4 van en redes_v4)" : "");
		}
		List<String> ours = new ArrayList<>(ObsNames.M1);
		ours.addAll(ObsForja.names());
		ours.addAll(ObsV3.names());
		if (net.inputs() < ObsNames.M1.size() || net.names.size() != net.inputs()) {
			return "espera " + net.inputs() + " entradas con " + net.names.size() + " nombres; el mod da al menos " + ObsNames.M1.size();
		}
		if (net.inputs() > ours.size()) {
			return "pide " + net.inputs() + " entradas y este mod da " + ours.size() + " (las del bloque Forja que faltan son de una versión más nueva)";
		}
		for (int i = 0; i < net.inputs(); i++) {
			if (!ours.get(i).equals(net.names.get(i))) {
				return "la entrada " + i + " es '" + net.names.get(i) + "' y el mod da '" + ours.get(i) + "'";
			}
		}
		return null;
	}

	/** Every family a network can be given for, by file name: vanilla's and the mod's. */
	public static List<String> families() {
		List<String> all = new ArrayList<>();
		for (MobFamily family : MobFamily.values()) {
			if (family != MobFamily.OTRO) {
				all.add(family.file);
			}
		}
		for (ForjaFamily family : ForjaFamily.values()) {
			all.add(family.file);
		}
		return all;
	}

	/** The family file name a mob's network would come from ("otro", which is never loaded, for the rules only). */
	public static String familyOf(Mob mob) {
		ForjaFamily forja = ForjaFamily.of(mob);
		if (forja != null) {
			return forja.file;
		}
		String net = MobFamily.network(mob);
		return net != null ? net : MobFamily.OTRO.file;
	}

	public static NetBrain net(String family) {
		if (!loaded) {
			reload();
		}
		return NETS.get(family);
	}

	public static NetBrain net(MobFamily family) {
		return net(family.file);
	}

	public static Map<String, String> problems() {
		return NET_PROBLEMS;
	}

	private static void tick(ServerLevel level) {
		if (!CombatConfig.get().enabled) {
			return;
		}
		long now = level.getGameTime();
		List<MobMind> minds = new ArrayList<>();
		for (MobMind mind : MINDS.values()) {
			if (mind.mob.level() == level && mind.mob.isAlive()) {
				minds.add(mind);
			}
		}
		if (now % Squad.PERIOD == 0) {
			Squad.update(level, minds, now);
		}
		for (MobMind mind : minds) {
			think(mind, now);
			MobSprint.tick(mind, now);
		}
		AiDebug.tick(level, now);
	}

	private static void think(MobMind mind, long now) {
		Mob mob = mind.mob;
		// The trail and the boredom clock belong to one player: taking on a new one (or the same one again,
		// after losing them) starts both afresh. They used to carry over, so a mob that had last seen
		// anybody half a minute ago dropped the next player it took on the moment it took them on, and
		// once it had lost or killed one player it would never fight another.
		Player hunting = mob.getTarget() instanceof Player chased ? chased : null;
		if (hunting != mind.hunted) {
			mind.hunted = hunting;
			if (hunting != null) {
				mind.lastSeen = null;
				mind.lastSeenAt = now;
			}
		}
		// Boredom (69): a player it has not seen for 30 seconds is given up.
		if (mob.getTarget() instanceof Player hunted && mind.lastSeenAt > Long.MIN_VALUE / 2 && now - mind.lastSeenAt > Personality.BORED_TICKS) {
			mob.setTarget(null);
			mind.lastSeen = null;
		}
		// A grudge (62): with nothing to do, it goes for the player it holds one against, if it sees them.
		if (mob.getTarget() == null && now % 20 == 0) {
			java.util.UUID grudge = Personality.grudgeTarget(mob);
			Player held = grudge == null ? null : mob.level().getPlayerByUUID(grudge);
			if (held != null && !held.isCreative() && !held.isSpectator() && mob.distanceTo(held) < 16.0
				&& ObsM1.sees(mob, held.getX(), held.getEyeY(), held.getZ())) {
				mob.setTarget(held);
			}
		}
		Player target = mob.getTarget() instanceof Player player && player.isAlive() && !player.isCreative() && !player.isSpectator()
			&& mob.distanceTo(player) <= CombatConfig.get().iaAlcance ? player : null;
		if (target != null && ObsM1.sees(mob, target.getX(), target.getEyeY(), target.getZ()) && !WorldFights.hiddenByNight(mob, target)) {
			mind.lastSeen = target.position();
			mind.lastSeenAt = now;
		}
		if (target != mind.target) {
			if (mind.target != null && target == null && mob.isAlive()) {
				Personality.survived(mob, mind.target);
			}
			mind.target = target;
			mind.memory = null;
			mind.ringAngle = Double.NaN;
			mind.ringRadius = TacticGoal.RING_RADIUS;
			// Its part in the old player's squad is no part in the new one: until the squads are next
			// worked out it is a plain reserve, not the other group's flanker, rout or guard, and the cover
			// it found was cover from the other player's arrows.
			mind.role = SquadRole.RESERVA;
			mind.routed = false;
			mind.guarding = false;
			mind.othersWaiting = false;
			mind.cover = null;
			mind.coverAt = Long.MIN_VALUE / 2;
		}
		if (target == null) {
			mind.networked = false;
			mind.blaze = null;
			mind.decision = Decision.APPROACH;
			mind.wantsRun = false;
			return;
		}
		// A caster fights by the rules whatever the mode: no network has ever seen a staff or a tome, and
		// the observation it would be fed has nothing in it that says one is in the hand.
		NetBrain net = mind.override != null ? mind.override
			: mode() == Mode.REGLAS || dev.forja.entity.ai.CasterGoal.casts(mob) ? null : net(familyOf(mob));
		int every = net != null ? Math.max(1, net.ticksPerDecision) : 1;
		if (now - mind.decidedAt < every) {
			return;
		}
		mind.decidedAt = now;
		if (net != null && BLAZE_FORMAT.equals(net.format)) {
			thinkBlaze(mind, target, net);
			return;
		}
		mind.blaze = null;
		if (net == null) {
			mind.networked = false;
			mind.decision = RuleBrain.decide(mind, target);
			mind.wantsRun = MobSprint.rules(mind, target);
			AiStats.count(mob, mind.decision);
			return;
		}
		if (mind.memory == null || mind.memory.length != net.memory) {
			mind.resetMemory(net.memory);
		}
		float[] obs;
		if (V4_FORMAT.equals(net.format)) {
			// v4: its own 468 (ObsV4 also sets reachVersion to 2, the only one the v4 contract has)
			obs = ObsV4.build(mob, target, mind);
		} else {
			mind.reachVersion = net.reachVersion;
			obs = ObsM1.of(mob, target, mind.cooldown, mind.draw);
			if (net.inputs() > obs.length) {
				obs = ObsForja.full(mob, target, mind, obs, net.inputs());
			}
		}
		boolean[] mask = mask(mob, mind, target, net.outputs());
		float[] logits = net.forward(obs, mind.memory);
		double temperature = CombatConfig.get().iaTemperatura * ForjaDifficulty.current().temperature * Threat.of(mob).temperature();
		mind.decision = NetBrain.sample(logits, temperature, mind.random, mask);
		// A network without the run head leaves running to the rules.
		mind.wantsRun = logits.length > NetBrain.RUN_AT ? NetBrain.sampleRun(logits, temperature, mind.random, mask)
			: MobSprint.rules(mind, target);
		mind.networked = true;
		mind.lastObs = obs;
		mind.lastLogits = logits;
		AiStats.count(mob, mind.decision);
		AiRecorder.record(mind, target, obs, mask, now);
	}

	/**
	 * A blaze network's decision (red_blaze_v1): ObsBlaze's 65 inputs, its own mask and heads, carried out by BlazePilot
	 * (from TacticGoal). mind.decision gets the same decision in the ground mobs' terms, for what counts and shows them.
	 */
	private static void thinkBlaze(MobMind mind, Player target, NetBrain net) {
		Mob mob = mind.mob;
		if (mind.memory == null || mind.memory.length != net.memory) {
			mind.resetMemory(net.memory);
		}
		float[] obs = ObsBlaze.build(mob, target, mind);
		boolean[] mask = BlazeBrain.mask(mob, mind, target);
		float[] logits = net.forward(obs, mind.memory);
		double temperature = CombatConfig.get().iaTemperatura * ForjaDifficulty.current().temperature * Threat.of(mob).temperature();
		if (mind.blaze == null) {
			// taking over from the rules: its vertical speed starts from the one it has
			mind.blazeVy = mob.getDeltaMovement().y;
		}
		mind.blaze = BlazeBrain.sample(logits, temperature, mind.random, mask);
		mind.decision = mind.blaze.asDecision();
		mind.wantsRun = false;
		mind.networked = true;
		mind.lastObs = obs;
		mind.lastLogits = logits;
		AiStats.count(mob, mind.decision);
	}

	/** When each mob last called for help, so a fight does not turn into a shouting match. */
	private static final Map<Mob, Long> CALLED = new WeakHashMap<>();
	private static final double HELP_RANGE = 24.0;

	/**
	 * Idea 18: a monster badly hurt by a player calls for help; hostiles nearby with nothing to do take
	 * that player as their target. Once every five seconds per monster.
	 */
	private static void callForHelp(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
		float base, float taken, boolean blocked) {
		if (!(entity instanceof Mob mob) || !(entity instanceof Enemy) || !(source.getEntity() instanceof Player player)
			|| player.isCreative() || player.isSpectator() || mob.getHealth() > mob.getMaxHealth() * 0.5F || !mob.isAlive()) {
			return;
		}
		long now = mob.level().getGameTime();
		if (now - CALLED.getOrDefault(mob, Long.MIN_VALUE / 2) < 100) {
			return;
		}
		CALLED.put(mob, now);
		for (Mob other : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(HELP_RANGE),
			m -> m != mob && m.isAlive() && m instanceof Enemy && m.getTarget() == null && Personality.sameSide(mob, m))) {
			other.setTarget(player);
		}
	}

	/**
	 * Idea 53: a monster's blow sends a player towards lava or a drop, if there is one within 3 blocks of
	 * them: an extra push of 0,35 that way.
	 */
	private static void pushToDanger(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source,
		float base, float taken, boolean blocked) {
		if (blocked || taken <= 0.0F || !(entity instanceof Player player) || !(source.getEntity() instanceof Mob)
			|| source.getDirectEntity() != source.getEntity() || !CombatConfig.get().enabled) {
			return;
		}
		net.minecraft.world.phys.Vec3 danger = Terrain.dangerNear(player);
		if (danger != null) {
			player.push(danger.x * 0.35, 0.05, danger.z * 0.35);
			player.hurtMarked = true;
		}
	}

	/**
	 * What the network may not choose right now. The simulator's part: a lit creeper holds still, a jump
	 * needs the ground or water. Forja's (v2): a special only if it is available, the shield only if it has
	 * one and its guard holds, a dodge only when ready and on the ground, a feint only in the first half of
	 * a warning.
	 */
	static boolean[] mask(Mob mob, MobMind mind, Player target, int outputs) {
		boolean[] mask = new boolean[Math.max(11, outputs)];
		java.util.Arrays.fill(mask, true);
		boolean[] v1 = mask(mob);
		System.arraycopy(v1, 0, mask, 0, v1.length);
		if (outputs >= NetBrain.V2_OUTPUTS) {
			for (int k = 1; k <= 4; k++) {
				mask[NetBrain.SPECIAL_AT + k] = mind.specials != null && mind.specials.available(k - 1, target);
			}
			mask[NetBrain.DEFENSE_AT + 1] = MobDefense.hasShield(mob) && !MobDefense.guardBroken(mob);
			mask[NetBrain.DEFENSE_AT + 2] = MobDefense.dodgeReady(mob) && mob.onGround();
			mask[NetBrain.FEINT_AT] = mind.windup > mind.windupTotal / 2;
		}
		if (outputs >= NetBrain.V3_OUTPUTS) {
			// v3's tactics, only where they can be carried out: bait and hide need somebody to run to (hide
			// also takes a block), the push needs lava or a drop by the player.
			boolean ally = false;
			for (Mob other : ObsM1.allies(mob)) {
				if (other.getTarget() == target) {
					ally = true;
					break;
				}
			}
			int at = NetBrain.NEW_TACTICS_AT;
			mask[at + Tactic.CEBO.ordinal() - Tactic.V2_COUNT] = ally;
			mask[at + Tactic.RELEVO.ordinal() - Tactic.V2_COUNT] = true;
			mask[at + Tactic.OCULTARSE.ordinal() - Tactic.V2_COUNT] = ally || Terrain.cover(mob, target) != null;
			mask[at + Tactic.EMPUJAR.ordinal() - Tactic.V2_COUNT] = Terrain.dangerNear(target) != null;
		}
		if (outputs > NetBrain.RUN_AT) {
			mask[NetBrain.RUN_AT] = MobSprint.able(mind, mob.level().getGameTime());
		}
		if (outputs >= NetBrain.V4_OUTPUTS) {
			// v4 (M1): what the executor cannot carry out yet is shut, as the simulator's step S1 shuts it (the contract's
			// "estado_S1"): the eight new tactics (34..41), every object but "nada" (42 stays open: index 0 of a softmax
			// is never shut), furia (51) and golpe_escudo (52). Each opens with the step that brings it (M2 to M5).
			for (int k = NetBrain.V4_TACTICS_AT; k < NetBrain.V4_TACTICS_AT + NetBrain.V4_NEW_TACTICS; k++) {
				mask[k] = false;
			}
			mask[NetBrain.OBJECT_AT] = true;
			for (int k = 1; k < NetBrain.OBJECTS; k++) {
				mask[NetBrain.OBJECT_AT + k] = false;
			}
			mask[NetBrain.FURY_AT] = false;
			mask[NetBrain.SHIELD_BASH_AT] = false;
		}
		return mask;
	}

	/** What the network may not choose right now, as in the simulator: a lit creeper holds still. */
	static boolean[] mask(Mob mob) {
		boolean[] mask = new boolean[11];
		java.util.Arrays.fill(mask, true);
		if (mob instanceof Creeper creeper && creeper.getSwellDir() > 0) {
			for (int k = 1; k <= 8; k++) {
				mask[k] = false;
			}
			mask[10] = false;
		}
		if (!mob.onGround() && !mob.isInWater()) {
			mask[9] = false;
		}
		return mask;
	}

	public static Iterable<MobMind> minds() {
		return MINDS.values();
	}
}
