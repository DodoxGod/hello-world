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

	private static final Map<Mob, MobMind> MINDS = new WeakHashMap<>();
	/** Networks by family file name ("cuerpo", "forja_tanque"...), and why the ones that were refused were. */
	private static final Map<String, NetBrain> NETS = new java.util.LinkedHashMap<>();
	private static final Map<String, String> NET_PROBLEMS = new java.util.LinkedHashMap<>();
	private static boolean loaded;

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
				if (MovementGoals.Builder.builds(mob)) {
					goals.addGoal(2, new MovementGoals.Builder(mob));
				}
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(MobAi::tick);
		TemporaryBlocks.register();
		ForjaTraits.register();
		Personality.register();
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

	/** (Re)reads every family's network, checking each against the observation it will be fed. */
	public static synchronized void reload() {
		NETS.clear();
		NET_PROBLEMS.clear();
		for (String family : families()) {
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

	/** Why a network cannot be used here, or null: its inputs must be ours, in our order. */
	public static String check(NetBrain net) {
		List<String> ours = new ArrayList<>(ObsNames.M1);
		ours.addAll(ObsForja.names());
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

	/** The family file name a mob's network would come from. */
	public static String familyOf(Mob mob) {
		ForjaFamily forja = ForjaFamily.of(mob);
		return forja != null ? forja.file : MobFamily.of(mob).file;
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
		}
		AiDebug.tick(level, now);
	}

	private static void think(MobMind mind, long now) {
		Mob mob = mind.mob;
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
		if (target != null && ObsM1.sees(mob, target.getX(), target.getEyeY(), target.getZ())) {
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
		}
		if (target == null) {
			mind.networked = false;
			mind.decision = Decision.APPROACH;
			return;
		}
		NetBrain net = mind.override != null ? mind.override : mode() == Mode.REGLAS ? null : net(familyOf(mob));
		int every = net != null ? Math.max(1, net.ticksPerDecision) : 1;
		if (now - mind.decidedAt < every) {
			return;
		}
		mind.decidedAt = now;
		if (net == null) {
			mind.networked = false;
			mind.decision = RuleBrain.decide(mind, target);
			AiStats.count(mob, mind.decision);
			return;
		}
		if (mind.memory == null || mind.memory.length != net.memory) {
			mind.resetMemory(net.memory);
		}
		float[] obs = ObsM1.of(mob, target, mind.cooldown, mind.draw);
		if (net.inputs() > obs.length) {
			obs = ObsForja.full(mob, target, mind, obs, net.inputs());
		}
		boolean[] mask = mask(mob);
		float[] logits = net.forward(obs, mind.memory);
		double temperature = CombatConfig.get().iaTemperatura * ForjaDifficulty.current().temperature * Threat.of(mob).temperature();
		mind.decision = NetBrain.sample(logits, temperature, mind.random, mask);
		mind.networked = true;
		mind.lastObs = obs;
		mind.lastLogits = logits;
		AiStats.count(mob, mind.decision);
		AiRecorder.record(mind, target, obs, mask, now);
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
