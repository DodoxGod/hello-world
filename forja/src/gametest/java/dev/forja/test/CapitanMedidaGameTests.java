package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import dev.forja.ai.Captain;
import dev.forja.ai.CaptainBrain;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Does the captain help? A group of 8 (an elite and 7: zombies, one with a shield, a husk, a spider and two skeletons)
 * against a scripted player, the same seeded fights once per mode (CaptainBrain.Mode: no captain, a captain that gives no
 * orders, the rules captain). The player holds, backs away and strafes in seeded phases, faces the nearest monster and
 * hits whoever is within 3.5 for 5 every 16 ticks; its health is put back each tick and what it lost is added up.
 *
 * <p>Only with FORJA_CAPITAN_MEDIR=&lt;file&gt; (a line per fight goes to it; FORJA_CAPITAN_N fights per mode, 8 by default):
 * the fights are long and their outcome is a measurement, not a pass or a fail. The modes run at once, in a batch
 * of their own (environment capitan_medida), each on its own player (CaptainBrain.override).
 *
 * <p>Captain 2 (2026-09-30): a fourth mode, "reglas2", is the rules captain with every piece of captain 2 on
 * (CaptainBrain.overridePieces); the other three have them all off, as before. One zombie of the group is a veteran in
 * every mode (the one a succession hands command to; FORJA_CAPITAN_VETERANO=no leaves it out).
 */
public class CapitanMedidaGameTests {
	/** Ticks per fight. */
	static final int FIGHT = 600;
	/** Ticks between fights (clearing up, the group's memory forgotten). */
	static final int GAP = 80;
	static final int SIZE = 40;
	/** The player hits for this much every {@link #SWING} ticks, whoever is nearest within {@link #PLAYER_REACH}. */
	static final float PLAYER_DAMAGE = 5.0F;
	static final int SWING = 16;
	static final double PLAYER_REACH = 3.5;

	/** With FORJA_CAPITAN_TRAZA=<file>, every tick of the first fights of each mode, mob by mob, goes to it. */
	static final int TRACED = 2;
	static final int MAX_FIGHTS = 100;

	/** Whether FORJA_CAPITAN_ABLACION (a comma list) names this switch. */
	static boolean ablation(String name) {
		String list = System.getenv("FORJA_CAPITAN_ABLACION");
		return list != null && java.util.Arrays.asList(list.split(",")).contains(name);
	}

	/**
	 * How each fight's warned blows start (the fifth pass): by which path, from how far, how long after coming within
	 * reach, how long after the mob's last one, how many at once, and why a mob within reach was not warning.
	 */
	static final class WarnLog {
		final Map<String, Integer> counts = new java.util.TreeMap<>();
		final Map<Mob, Long> inRangeSince = new java.util.HashMap<>();
		final Map<Mob, long[]> lastEnd = new java.util.HashMap<>();
		final Map<Mob, String> lastOutcome = new java.util.HashMap<>();
		final Map<Mob, String> path = new java.util.HashMap<>();
		/** Since each mob's last blow (landed, missed or feinted), until its next warned blow: what it did (sixth pass). */
		final Map<Mob, GapTrack> gaps = new java.util.HashMap<>();
		Player player;
		long now;

		void add(String key, int n) {
			this.counts.merge(key, n, Integer::sum);
		}
	}

	static final Map<Entity, WarnLog> WARN_LOGS = new java.util.WeakHashMap<>();

	/** One mob between two warned blows (sixth pass). */
	static final class GapTrack {
		final long from;
		final String outcome;
		final String path;
		int waitMelee;
		int waitMeleeStopped;
		int waitTactic;
		int waitAny;
		int firstFree = -1;
		int cut;
		int cutRestart;
		int otherTactic;
		int inRangeFree;
		boolean special;
		boolean hop;
		int prevWait = -1;
		boolean prevRunning;
		boolean prevHopReady = true;

		GapTrack(long from, String outcome, String path) {
			this.from = from;
			this.outcome = outcome;
			this.path = path;
		}
	}

	static String gapBucket(long d) {
		return d < 20 ? "<20" : d < 30 ? "20-29" : d < 40 ? "30-39" : d < 60 ? "40-59" : d < 90 ? "60-89" : d < 150 ? "90-149" : "150+";
	}

	static final String[] DELAY_BUCKETS = {"0", "1-2", "3-5", "6-10", "11-20", "21+"};

	static String delayBucket(long d) {
		return d <= 0 ? "0" : d <= 2 ? "1-2" : d <= 5 ? "3-5" : d <= 10 ? "6-10" : d <= 20 ? "11-20" : "21+";
	}

	static {
		dev.forja.combat.CombatStats.watcher = new dev.forja.combat.CombatStats.WarnWatcher() {
			@Override
			public void started(Entity entity, Entity target, String path) {
				WarnLog log = WARN_LOGS.get(entity);
				if (log == null || !(entity instanceof Mob mob) || target != log.player) {
					return;
				}
				long now = mob.level().getGameTime();
				log.add("inicio_" + path, 1);
				log.path.put(mob, path.startsWith("especial:") ? "especial" : path);
				GapTrack gt = log.gaps.get(mob);
				if (path.startsWith("especial:")) {
					if (gt != null) {
						gt.special = true;
					}
					return;
				}
				if (gt != null) {
					// the gap since its last blow, and what filled it
					log.gaps.remove(mob);
					long gap = now - gt.from;
					String between = gt.special ? "especial" : gt.hop ? "salto_atras" : gt.otherTactic > 0 ? "otra_tactica" : "solo_acercarse";
					log.add("hueco_n", 1);
					log.add("hueco_suma", (int) gap);
					log.add("hueco_" + gapBucket(gap), 1);
					log.add("hueco_tras_" + gt.outcome + "_suma", (int) gap);
					log.add("hueco_tras_" + gt.outcome + "_n", 1);
					log.add("hueco_con_" + between + "_suma", (int) gap);
					log.add("hueco_con_" + between + "_n", 1);
					log.add("hueco_" + gt.path + "_a_" + path + "_n", 1);
					if (gap < 20) {
						log.add("corto_" + gt.path + "_a_" + path + "_n", 1);
					}
					log.add("hueco_espera_meta", gt.waitMelee);
					log.add("hueco_espera_meta_parada", gt.waitMeleeStopped);
					log.add("hueco_espera_tactica", gt.waitTactic);
					log.add("hueco_esperando", gt.waitAny);
					log.add("hueco_libre_a_su_alcance", gt.inRangeFree);
					log.add("hueco_hasta_libre_suma", gt.firstFree < 0 ? (int) gap : gt.firstFree);
					log.add("hueco_recortes", gt.cut);
					log.add("hueco_recortes_reinicio", gt.cutRestart);
				}
				// distance at the start: centre to centre (flat) and the gap between the two boxes
				double centre = Math.hypot(mob.getX() - log.player.getX(), mob.getZ() - log.player.getZ());
				net.minecraft.world.phys.AABB a = mob.getBoundingBox();
				net.minecraft.world.phys.AABB b = log.player.getBoundingBox();
				double gx = Math.max(0.0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
				double gz = Math.max(0.0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
				double gap = Math.hypot(gx, gz);
				log.add("dist_centro_x100", (int) Math.round(centre * 100));
				log.add("dist_hueco_x100", (int) Math.round(gap * 100));
				log.add("dist_n", 1);
				log.add("dist_centro_" + (centre < 1.0 ? "<1" : centre < 1.25 ? "1-1.25" : centre < 1.5 ? "1.25-1.5" : centre < 1.75 ? "1.5-1.75" : ">=1.75"), 1);
				// ticks since it came within reach (or since its last warning ended, if later)
				Long since = log.inRangeSince.get(mob);
				long[] end = log.lastEnd.get(mob);
				if (since != null) {
					long from = end != null ? Math.max(since, end[0]) : since;
					log.add("retraso_" + delayBucket(now - from), 1);
					log.add("retraso_suma", (int) (now - from));
					log.add("retraso_n", 1);
				} else {
					log.add("retraso_fuera_de_alcance", 1);
				}
				// its wait since its own last warning ended, by how that one ended
				if (end != null) {
					String outcome = log.lastOutcome.get(mob);
					log.add("espera_" + outcome + "_suma", (int) (now - end[0]));
					log.add("espera_" + outcome + "_n", 1);
					// sooner than the melee goal's 20-tick wait after a blow (vanilla's start() puts it back to 0)
					if (now - end[0] < 20 && "vanilla".equals(path) && !"cortado".equals(outcome)) {
						log.add("espera_menos_de_20_" + outcome, 1);
					}
				}
			}

			@Override
			public void ended(Entity entity, String outcome) {
				WarnLog log = WARN_LOGS.get(entity);
				if (log == null || !(entity instanceof Mob mob)) {
					return;
				}
				log.add("fin_" + outcome, 1);
				log.add("fin_" + log.path.getOrDefault(mob, "?") + "_" + outcome, 1);
				if (!"especial".equals(outcome)) {
					log.lastEnd.put(mob, new long[] {mob.level().getGameTime()});
					log.lastOutcome.put(mob, outcome);
				}
				if ("llega".equals(outcome) || "falla".equals(outcome) || "finta".equals(outcome)) {
					log.gaps.put(mob, new GapTrack(mob.level().getGameTime(), outcome, log.path.getOrDefault(mob, "?")));
				}
			}
		};
	}

	/** What each fight's player took, by kind, and how the chasers moved. */
	static final class Watch {
		double melee;
		double arrow;
		int arrows;
		double potion;
		double fire;
		double other;
		double walkDist;
		int walkTicks;
		double runDist;
		int runTicks;
		int goingIn;
		int idle;
		double drops;
		double lastDrop;
		int fired;
		int wouldHit;
		/** Captain 2: ticks with an acting captain, with the captain guarded (behind or back), escorts × ticks, estimates shared. */
		int interim;
		int guarded;
		int escorts;
		int shared;
		long sharedAt = Long.MIN_VALUE;
		/** When the elite (the first captain) fell, -1 for never; and the captain's distance to the player summed over ticks. */
		long eliteDied = -1;
		double captainDist;
		int captainTicks;
		final java.util.Set<Integer> arrowsSeen = new java.util.HashSet<>();
		final java.util.Set<Integer> arrowsHit = new java.util.HashSet<>();
	}

	static final Map<Player, Watch> DAMAGE = new java.util.WeakHashMap<>();
	/** Every drop of a watched player's health, by the calls that made it (HealthDropMixin): amount and count. */
	static final Map<String, double[]> DROPS = new java.util.TreeMap<>();

	/**
	 * Whether this is a measured fight's player (HealthDropMixin). setHealth also runs inside an entity's
	 * constructor, before it has an id, and a WeakHashMap lookup hashes by that id: it threw there, which crashed
	 * the client test on joining the world (creating the local player). So: nothing measured, nothing to look up;
	 * and an entity without an id yet is never one being watched.
	 */
	public static boolean watching(net.minecraft.world.entity.LivingEntity entity) {
		// by identity, not containsKey: that hashes the entity (its id), and a client's own player sets its health in its
		// constructor before it has an id, which crashed every client test at login
		if (!(entity instanceof Player) || DAMAGE.isEmpty()) {
			return false;
		}
		for (Player watched : DAMAGE.keySet()) {
			if (watched == entity) {
				return true;
			}
		}
		return false;
	}

	/** A watched player's health went down by {@code amount}: counted, and filed under the calls that did it. */
	public static void healthDrop(net.minecraft.world.entity.LivingEntity entity, float amount) {
		Watch w = DAMAGE.get((Player) entity);
		if (w == null) {
			return;
		}
		w.drops += amount;
		w.lastDrop = amount;
		StringBuilder key = new StringBuilder();
		int kept = 0;
		for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
			String c = frame.getClassName();
			if (c.startsWith("java.") || c.contains("HealthDropMixin") || c.contains("CapitanMedidaGameTests") || frame.getMethodName().equals("setHealth")) {
				continue;
			}
			key.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(frame.getMethodName()).append(" < ");
			if (++kept == 7) {
				break;
			}
		}
		double[] sum = DROPS.computeIfAbsent(key.toString(), k -> new double[2]);
		sum[0] += amount;
		sum[1]++;
	}

	static {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Watch w = entity instanceof Player p ? DAMAGE.get(p) : null;
			if (w == null || taken <= 0.0F) {
				return;
			}
			// What the hit really took off (HealthDropMixin, just before this event, in the same hurt): the event's figure is
			// the one handed to vanilla's armor step, before Forja's multipliers (CombatHooks.afterArmor: the difficulty, the
			// mob's threat, its personality, a head hit ×1.3...) and the i-frames' "only what is over the last blow".
			float real = w.lastDrop > 0.0 ? (float) w.lastDrop : taken;
			w.lastDrop = 0.0;
			taken = real;
			Entity direct = source.getDirectEntity();
			if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
				w.fire += taken;
			} else if (direct instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow) {
				w.arrow += taken;
				w.arrows++;
			} else if (direct instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion
				|| source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC) || source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)) {
				w.potion += taken;
			} else if (direct instanceof Mob) {
				w.melee += taken;
			} else {
				w.other += taken;
			}
		});
	}

	/**
	 * The test player is added to the level (2026-09-30), so arrows and thrown potions reach it: the FakePlayer of the
	 * other tests is never added, arrows flew through it and every table before counted no arrow at all.
	 * FORJA_CAPITAN_JUGADOR=fuera keeps it out, as before.
	 */
	static boolean inLevel() {
		return !"fuera".equals(System.getenv("FORJA_CAPITAN_JUGADOR"));
	}

	private static net.minecraft.server.level.ServerPlayer levelPlayer(GameTestHelper helper, BlockPos relative) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, relative);
		helper.getLevel().addNewPlayer(player);
		return player;
	}

	static int fights() {
		String n = System.getenv("FORJA_CAPITAN_N");
		return n == null || n.isBlank() ? 8 : Integer.parseInt(n.trim());
	}

	@GameTest(environment = "forja-test:capitan_medida", padding = 32, maxTicks = MAX_FIGHTS * (FIGHT + GAP) + 200)
	public void measureNoCaptain(GameTestHelper helper) {
		measure(helper, CaptainBrain.Mode.SIN_CAPITAN, false);
	}

	@GameTest(environment = "forja-test:capitan_medida", padding = 32, maxTicks = MAX_FIGHTS * (FIGHT + GAP) + 200)
	public void measureFreeCaptain(GameTestHelper helper) {
		measure(helper, CaptainBrain.Mode.LIBRE, false);
	}

	@GameTest(environment = "forja-test:capitan_medida", padding = 32, maxTicks = MAX_FIGHTS * (FIGHT + GAP) + 200)
	public void measureRulesCaptain(GameTestHelper helper) {
		measure(helper, CaptainBrain.Mode.REGLAS, false);
	}

	/** The rules captain with every piece of captain 2 (vision, succession, protection, new orders, visible): "reglas2". */
	@GameTest(environment = "forja-test:capitan_medida", padding = 32, maxTicks = MAX_FIGHTS * (FIGHT + GAP) + 200)
	public void measureRulesCaptain2(GameTestHelper helper) {
		measure(helper, CaptainBrain.Mode.REGLAS, true);
	}

	/**
	 * The pieces of captain 2 "reglas2" gets: all of them, or the ones FORJA_CAPITAN2_PIEZAS names (a comma list of
	 * vision, sucesion, proteccion, ordenes2, visible), to measure each on its own.
	 */
	static java.util.EnumSet<CaptainBrain.Piece> pieces() {
		String list = System.getenv("FORJA_CAPITAN2_PIEZAS");
		if (list == null || list.isBlank()) {
			return java.util.EnumSet.allOf(CaptainBrain.Piece.class);
		}
		java.util.EnumSet<CaptainBrain.Piece> on = java.util.EnumSet.noneOf(CaptainBrain.Piece.class);
		for (String name : list.split(",")) {
			if (!name.isBlank()) {
				on.add(CaptainBrain.Piece.valueOf(name.trim().toUpperCase(Locale.ROOT)));
			}
		}
		return on;
	}

	/** The mode's name in the measurement file: its CaptainBrain.Mode, or "reglas2" for the rules captain with captain 2. */
	static String label(CaptainBrain.Mode mode, boolean two) {
		return two ? "reglas2" : mode.name().toLowerCase(Locale.ROOT);
	}

	private static void measure(GameTestHelper helper, CaptainBrain.Mode mode, boolean two) {
		String out = System.getenv("FORJA_CAPITAN_MEDIR");
		if (out == null || out.isBlank()) {
			helper.succeed();
			return;
		}
		int fights = Math.min(MAX_FIGHTS, fights());
		CombatConfig.get().veteranChance = 0.0;
		// FORJA_CAPITAN_ABLACION: switches for looking for what slows the group down (they apply to every mode at once)
		if (ablation("sinaviso")) {
			CombatConfig.get().telegraph = false;
		}
		if (ablation("sinpostura")) {
			CombatConfig.get().posture = false;
		}
		// the variants measured for the defaults: set either way (the run's config file keeps whatever it had)
		CombatConfig.get().iaTurnoGrupoGrande = !ablation("sinturno");
		CombatConfig.get().iaCapitanPinza = !ablation("sinpinza");
		CombatConfig.get().eliteChance = 0.0;
		CombatConfig.get().shieldChance = 0.0;
		dev.forja.ForjaConfig.get().baculos = 0.0F;
		dev.forja.ForjaConfig.get().grimorios = 0.0F;
		floor(helper);
		Path file = Path.of(out.trim());
		// One fight after another, driven from a single tick listener (scheduling from inside a scheduled task broke the
		// framework's own iteration over its schedule).
		int[] next = {0};
		long[] startAt = {helper.getLevel().getGameTime() + 5};
		java.util.function.BooleanSupplier[] current = {null};
		boolean[] over = {false};
		helper.onEachTick(() -> {
			if (over[0]) {
				return;
			}
			long now = helper.getLevel().getGameTime();
			if (current[0] != null) {
				if (current[0].getAsBoolean()) {
					current[0] = null;
					startAt[0] = now + GAP;
				}
				return;
			}
			if (now < startAt[0]) {
				return;
			}
			if (next[0] >= fights) {
				over[0] = true;
				// where the health went, by the calls that took it, for every fight so far
				StringBuilder drops = new StringBuilder();
				DROPS.forEach((k, v) -> drops.append(String.format(Locale.ROOT, "%.1f\t%d\t%s%n", v[0], (long) v[1], k)));
				try {
					Files.writeString(Path.of(out.trim() + ".caidas"), drops);
				} catch (java.io.IOException failure) {
					throw new RuntimeException(failure);
				}
				TestChunks.release(helper);
				helper.succeed();
				return;
			}
			current[0] = fight(helper, mode, two, ++next[0], file);
		});
	}

	private static void floor(GameTestHelper helper) {
		TestChunks.force(helper, SIZE);
		for (int x = -1; x <= SIZE; x++) {
			for (int z = -1; z <= SIZE; z++) {
				for (int y = 0; y <= 10; y++) {
					BlockPos at = new BlockPos(x, y, z);
					if (helper.getBlockState(at).is(Blocks.BARRIER)) {
						helper.setBlock(at, Blocks.AIR);
					}
				}
			}
		}
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** One fight: the group comes in from a seeded side, the player follows its seeded script, for FIGHT ticks. */
	private static java.util.function.BooleanSupplier fight(GameTestHelper helper, CaptainBrain.Mode mode, boolean two, int seed, Path out) {
		Random dice = new Random(seed * 7919L);
		net.minecraft.server.level.ServerPlayer player = inLevel() ? levelPlayer(helper, new BlockPos(SIZE / 2, 1, SIZE / 2))
			: CombatGameTests.player(helper, new BlockPos(SIZE / 2, 1, SIZE / 2));
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		CaptainBrain.override(player, mode);
		// captain 2's pieces: all for "reglas2", none for the rest (the captain as it was before them)
		CaptainBrain.overridePieces(player, two ? pieces() : java.util.EnumSet.noneOf(CaptainBrain.Piece.class));
		double side = dice.nextDouble() * Math.PI * 2.0;
		List<Mob> mobs = new ArrayList<>();
		List<EntityType<? extends Mob>> kinds = List.of(EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.ZOMBIE,
			EntityTypes.HUSK, EntityTypes.SPIDER, EntityTypes.SKELETON, EntityTypes.SKELETON);
		for (int k = 0; k < kinds.size(); k++) {
			double a = side + (k - 3.5) * 0.18;
			double r = 11.0 + dice.nextDouble() * 3.0;
			BlockPos at = new BlockPos((int) Math.round(SIZE / 2.0 + Math.cos(a) * r), 1, (int) Math.round(SIZE / 2.0 + Math.sin(a) * r));
			Mob mob = helper.spawn(kinds.get(k), at);
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			if (kinds.get(k) == EntityTypes.SKELETON) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			}
			mobs.add(mob);
		}
		Mob elite = mobs.get(0);
		Threat.ELITE.mark(elite);
		elite.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		mobs.get(1).setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		// one veteran (captain 2): the one a succession hands command to when the elite falls; in every mode, so they compare
		// (FORJA_CAPITAN_VETERANO=no leaves the group as it was before)
		if (!"no".equals(System.getenv("FORJA_CAPITAN_VETERANO"))) {
			Threat.VETERANO.mark(mobs.get(2));
		}
		if (ablation("mochila")) {
			// the kit a monster spawning by itself rolls (helper.spawn skips it), the elite's as an elite's: potions,
			// food, pearls, wind charges (MobKit.roll)
			for (int k = 0; k < mobs.size(); k++) {
				dev.forja.ai.MobKit.roll(mobs.get(k), net.minecraft.util.RandomSource.create(seed * 131L + k), 1.0);
			}
		}
		for (Mob mob : mobs) {
			mob.setTarget(player);
		}
		long start = helper.getLevel().getGameTime();
		double[] taken = {0.0};
		long[] killedAt = {-1};
		int[] phase = {0};
		int[] phaseLeft = {40};
		double[] strafe = {1.0};
		Map<Captain.Order, Integer> orders = new EnumMap<>(Captain.Order.class);
		boolean[] done = {false};
		// ticks summed: mobs within reach, turns taken, ticks with a turn free, ticks
		double[] watch = new double[4];
		// what each kind of thing did to the player, and how the chasers moved (see Watch)
		Watch w = new Watch();
		DAMAGE.put(player, w);
		Map<Mob, Vec3> last = new java.util.HashMap<>();
		WarnLog warns = new WarnLog();
		warns.player = player;
		for (Mob mob : mobs) {
			WARN_LOGS.put(mob, warns);
		}
		String traced = System.getenv("FORJA_CAPITAN_TRAZA");
		Path trace = traced == null || traced.isBlank() ? null : Path.of(traced.trim());
		Vec3 low = helper.absoluteVec(new Vec3(4, 1, 4));
		Vec3 high = helper.absoluteVec(new Vec3(SIZE - 4, 1, SIZE - 4));
		return () -> {
			if (done[0]) {
				return true;
			}
			long t = helper.getLevel().getGameTime() - start;
			// what the monsters did to the player since the last tick
			float lost = player.getMaxHealth() - player.getHealth();
			if (lost > 0.0F) {
				taken[0] += lost;
				if (killedAt[0] < 0 && taken[0] >= 20.0) {
					killedAt[0] = t;
				}
			}
			player.setHealth(player.getMaxHealth());
			// neither player ticks (a FakePlayer is ticked by nobody, in the level or not): its i-frames are counted down here
			player.invulnerableTime = Math.max(0, player.invulnerableTime - 1);
			player.hurtTime = Math.max(0, player.hurtTime - 1);
			player.removeAllEffects();
			player.clearFire();
			List<Mob> alive = mobs.stream().filter(Entity::isAlive).toList();
			if (t >= FIGHT || alive.isEmpty()) {
				done[0] = true;
				finish(helper, mode, two, seed, out, mobs, taken[0], killedAt[0], t, orders, player, watch, w);
				return true;
			}
			Captain.Group g = Captain.group(player);
			Captain.Order order = g == null || g.captain == null ? Captain.Order.NINGUNA : g.command.order;
			orders.merge(order, 1, Integer::sum);
			// captain 2 at work
			if (g != null && g.captain != null) {
				w.interim += g.interim ? 1 : 0;
				w.guarded += g.guard != Captain.GUARD_NONE ? 1 : 0;
				w.captainDist += g.captain.distanceTo(player);
				w.captainTicks++;
				for (Mob mob : alive) {
					dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
					w.escorts += mind != null && mind.escort ? 1 : 0;
				}
				if (g.seenAt != w.sharedAt) {
					w.sharedAt = g.seenAt;
					w.shared += g.shared;
				}
			}
			if (w.eliteDied < 0 && !mobs.get(0).isAlive()) {
				w.eliteDied = t;
			}
			// where the free ring stalls: how many are within reach of the player, and how many turns are taken
			int near = 0;
			for (Mob mob : alive) {
				near += mob.distanceTo(player) <= PLAYER_REACH ? 1 : 0;
			}
			int held = dev.forja.combat.AttackTokens.held(player);
			int max = dev.forja.ai.Aggression.maxAttackers(player);
			watch[0] += near;
			watch[1] += held;
			watch[2] += held < max ? 1 : 0;
			watch[3]++;
			// the fifth pass: who is within reach, who is warning, and why one within reach is not
			{
				long gt = helper.getLevel().getGameTime();
				int warning = 0;
				int heldNow = dev.forja.combat.AttackTokens.held(player);
				int maxNow = dev.forja.ai.Aggression.maxAttackers(player);
				for (Mob mob : alive) {
					dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
					boolean busy = mind != null && (mind.warning || mind.windup > 0 || mind.specials != null && mind.specials.warningProgress() >= 0.0);
					warning += busy ? 1 : 0;
					boolean melee = mob instanceof net.minecraft.world.entity.monster.zombie.Zombie || mob instanceof net.minecraft.world.entity.monster.spider.Spider;
					if (!melee) {
						continue;
					}
					boolean inRange = mob.isWithinMeleeAttackRange(player);
					// sixth pass: between two warned blows, where the wait counts down
					GapTrack track = warns.gaps.get(mob);
					if (track != null && mind != null) {
						net.minecraft.world.entity.ai.goal.MeleeAttackGoal meleeGoal = null;
						boolean meleeRunning = false;
						boolean tacticRunning = false;
						for (var wrapped : ((dev.forja.mixin.MobGoalsAccess) mob).forjaGoals().getAvailableGoals()) {
							if (wrapped.getGoal() instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal m) {
								meleeGoal = m;
								meleeRunning = wrapped.isRunning();
							}
							tacticRunning |= wrapped.isRunning() && wrapped.getGoal() instanceof dev.forja.ai.TacticGoal;
						}
						int wait = meleeGoal == null ? 0 : ((dev.forja.test.mixin.MeleeGoalAccess) meleeGoal).forja$ticksUntilNextAttack();
						boolean waiting = wait > 0 || mind.cooldown > 0;
						if (wait > 0 && meleeRunning) {
							track.waitMelee++;
						} else if (wait > 0) {
							track.waitMeleeStopped++;
						}
						if (mind.cooldown > 0 && tacticRunning) {
							track.waitTactic++;
						}
						track.waitAny += waiting ? 1 : 0;
						if (!waiting && track.firstFree < 0) {
							track.firstFree = (int) (gt - track.from);
						}
						if (!waiting && inRange) {
							track.inRangeFree++;
						}
						// the wait cut short: more than the one tick a running goal counts down
						if (track.prevWait > 0 && wait < track.prevWait - 1) {
							track.cut += track.prevWait - 1 - wait;
							if (meleeRunning && !track.prevRunning) {
								track.cutRestart += track.prevWait - 1 - wait;
							}
						}
						track.prevWait = wait;
						track.prevRunning = meleeRunning;
						if (mind.decision.tactic() != dev.forja.ai.Tactic.ACERCARSE) {
							track.otherTactic++;
						}
						boolean hopReady = dev.forja.ai.HopBack.ready(mob);
						if (track.prevHopReady && !hopReady) {
							track.hop = true;
						}
						track.prevHopReady = hopReady;
					}
					if (!inRange) {
						warns.inRangeSince.remove(mob);
						continue;
					}
					warns.inRangeSince.putIfAbsent(mob, gt);
					if (busy) {
						warns.add("alcance_avisando", 1);
						continue;
					}
					net.minecraft.world.entity.ai.goal.MeleeAttackGoal goal = null;
					for (var wrapped : ((dev.forja.mixin.MobGoalsAccess) mob).forjaGoals().getAvailableGoals()) {
						if (wrapped.isRunning() && wrapped.getGoal() instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal meleeGoal) {
							goal = meleeGoal;
						}
					}
					String why;
					if (dev.forja.combat.Posture.isStaggered(mob, gt)) {
						why = "aturdido";
					} else if (goal == null) {
						why = "sin_meta_" + (mind == null ? "?" : mind.decision.tactic().name().toLowerCase(Locale.ROOT));
					} else if (((dev.forja.test.mixin.MeleeGoalAccess) goal).forja$ticksUntilNextAttack() > 0) {
						why = "recarga";
					} else if (!dev.forja.combat.AttackTokens.holds(player, mob) && heldNow >= dev.forja.ai.Aggression.maxAttackers(mob, player)) {
						why = "turnos_llenos";
					} else if (!mob.getSensing().hasLineOfSight(player)) {
						why = "sin_vista";
					} else {
						why = "otro";
					}
					warns.add("alcance_" + why, 1);
				}
				warns.add("a_la_vez_" + Math.min(4, warning), 1);
				warns.add("turnos_" + heldNow + "_de_" + maxNow, 1);
			}
			// arrows: the test player is not in the level (a FakePlayer never added), so an arrow flies through it; count
			// the ones fired at it and the ones whose path crosses its box (what would have hit a real player)
			for (net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow : helper.getLevel().getEntitiesOfClass(
				net.minecraft.world.entity.projectile.arrow.AbstractArrow.class, player.getBoundingBox().inflate(48.0), a -> mobs.contains(a.getOwner()))) {
				if (w.arrowsSeen.add(arrow.getId())) {
					w.fired++;
				}
				Vec3 from = arrow.position();
				Vec3 to = from.add(arrow.getDeltaMovement());
				if (!w.arrowsHit.contains(arrow.getId()) && player.getBoundingBox().inflate(0.3).clip(from, to).isPresent()) {
					w.arrowsHit.add(arrow.getId());
					w.wouldHit++;
				}
			}
			// the chasers' real speed (zombies and husks going in, more than 4 off, on the ground, not reeling), and the
			// ones that should be going in with nothing moving them (their melee goal waiting out vanilla's 20 ticks)
			for (Mob mob : alive) {
				dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
				Vec3 was = last.put(mob, mob.position());
				boolean melee = mob instanceof net.minecraft.world.entity.monster.zombie.Zombie || mob instanceof net.minecraft.world.entity.monster.spider.Spider;
				if (mind == null || !melee) {
					continue;
				}
				boolean goingIn = mind.decision.tactic() == dev.forja.ai.Tactic.ACERCARSE;
				boolean moving = ((dev.forja.mixin.MobGoalsAccess) mob).forjaGoals().getAvailableGoals().stream()
					.anyMatch(goal -> goal.isRunning() && goal.getFlags().contains(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE));
				if (goingIn) {
					w.goingIn++;
					w.idle += moving ? 0 : 1;
				}
				if (was != null && goingIn && mob instanceof net.minecraft.world.entity.monster.zombie.Zombie && mob.onGround()
					&& mob.distanceTo(player) > 4.0 && !dev.forja.combat.Posture.isStaggered(mob, helper.getLevel().getGameTime()) && mob.hurtTime == 0) {
					double step = Math.hypot(mob.getX() - was.x, mob.getZ() - was.z);
					if (mind.running) {
						w.runDist += step;
						w.runTicks++;
					} else {
						w.walkDist += step;
						w.walkTicks++;
					}
				}
			}
			if (trace != null && seed <= TRACED) {
				StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%s\t%d\t%d\t%d\t%d/%d\t%.2f,%.2f\t%s", label(mode, two),
					seed, t, near, held, max, player.getX() - low.x, player.getZ() - low.z, order.name().toLowerCase(Locale.ROOT)));
				for (Mob mob : alive) {
					dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(mob);
					StringBuilder goals = new StringBuilder();
					((dev.forja.mixin.MobGoalsAccess) mob).forjaGoals().getAvailableGoals().stream().filter(r -> r.isRunning())
						.forEach(r -> goals.append(r.getGoal().getClass().getSimpleName(), 0, Math.min(5, r.getGoal().getClass().getSimpleName().length())).append('+'));
					row.append(String.format(Locale.ROOT, "\t%s:%.1f:%s:%s%s%s%s:%s", mob.getType().toShortString().substring(0, 3), mob.distanceTo(player),
						mind == null ? "-" : mind.decision.tactic().name().toLowerCase(Locale.ROOT),
						dev.forja.combat.AttackTokens.holds(player, mob) ? "T" : "", mind != null && mind.windup > 0 ? "W" : "",
						mind != null && mind.running ? "R" : "", dev.forja.combat.Posture.isStaggered(mob, helper.getLevel().getGameTime()) ? "S" : "",
						goals));
				}
				row.append('\n');
				try {
					Files.writeString(trace, row, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				} catch (java.io.IOException failure) {
					throw new RuntimeException(failure);
				}
			}
			for (Mob mob : alive) {
				if (mob.getTarget() != player) {
					mob.setTarget(player);
				}
			}
			// the script: hold, back away from the group, or strafe round it
			Mob nearest = null;
			double best = Double.MAX_VALUE;
			double cx = 0.0;
			double cz = 0.0;
			for (Mob mob : alive) {
				double d = mob.distanceToSqr(player);
				if (d < best) {
					best = d;
					nearest = mob;
				}
				cx += mob.getX();
				cz += mob.getZ();
			}
			cx /= alive.size();
			cz /= alive.size();
			if (--phaseLeft[0] <= 0) {
				phase[0] = dice.nextInt(3);
				phaseLeft[0] = 25 + dice.nextInt(30);
				strafe[0] = dice.nextBoolean() ? 1.0 : -1.0;
			}
			double ax = player.getX() - cx;
			double az = player.getZ() - cz;
			double ad = Math.max(1.0E-6, Math.hypot(ax, az));
			ax /= ad;
			az /= ad;
			double vx = 0.0;
			double vz = 0.0;
			if (phase[0] == 1) {
				vx = ax * 0.18;
				vz = az * 0.18;
			} else if (phase[0] == 2) {
				vx = -az * strafe[0] * 0.15;
				vz = ax * strafe[0] * 0.15;
			}
			double nx = player.getX() + vx;
			double nz = player.getZ() + vz;
			if (nx < low.x || nx > high.x || nz < low.z || nz > high.z) {
				nx = Math.max(low.x, Math.min(high.x, nx));
				nz = Math.max(low.z, Math.min(high.z, nz));
				phaseLeft[0] = 0;
			}
			player.setPos(nx, player.getY(), nz);
			player.setDeltaMovement(vx, 0.0, vz);
			if (nearest != null) {
				float yaw = (float) (Math.toDegrees(Math.atan2(nearest.getZ() - player.getZ(), nearest.getX() - player.getX())) - 90.0);
				player.setYRot(yaw);
				player.setYHeadRot(yaw);
				if (t % SWING == 0 && Math.sqrt(best) <= PLAYER_REACH) {
					Vec3 before = nearest.getDeltaMovement();
					nearest.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), PLAYER_DAMAGE);
					if (ablation("sinempuje")) {
						nearest.setDeltaMovement(before);
					}
				}
			}
			return false;
		};
	}

	private static void finish(GameTestHelper helper, CaptainBrain.Mode mode, boolean two, int seed, Path out, List<Mob> mobs, double taken, long killedAt,
		long ticks, Map<Captain.Order, Integer> orders, Player player, double[] watch, Watch w) {
		int dead = (int) mobs.stream().filter(m -> !m.isAlive()).count();
		StringBuilder o = new StringBuilder();
		int total = orders.values().stream().mapToInt(Integer::intValue).sum();
		for (Map.Entry<Captain.Order, Integer> e : orders.entrySet()) {
			o.append(o.isEmpty() ? "" : ",").append(e.getKey().name().toLowerCase(Locale.ROOT)).append('=')
				.append(String.format(Locale.ROOT, "%.2f", e.getValue() / (double) Math.max(1, total)));
		}
		double n = Math.max(1.0, watch[3]);
		// per fight: the warned blows and how they ended, the lunges, damage by kind, the chasers' speed, idle share
		StringBuilder k = new StringBuilder();
		for (String key : new String[] {dev.forja.combat.CombatStats.WARNED, dev.forja.combat.CombatStats.WARNED_LANDED,
			dev.forja.combat.CombatStats.FEINT, dev.forja.combat.CombatStats.WARNED_CUT, dev.forja.combat.CombatStats.WARNED_KNOCKED,
			dev.forja.combat.CombatStats.WARNED_MOVED, dev.forja.combat.CombatStats.WARNED_UNSEEN, dev.forja.combat.CombatStats.WARNED_NO_DAMAGE,
			dev.forja.combat.CombatStats.LUNGE, dev.forja.combat.CombatStats.LUNGE_HIT}) {
			int sum = 0;
			for (Mob mob : mobs) {
				sum += dev.forja.combat.CombatStats.count(mob, key);
			}
			k.append(key).append('=').append(sum).append(';');
		}
		// why the warnings that were cut were cut (aviso_cortado_<why>)
		Map<String, Integer> cuts = new java.util.TreeMap<>();
		for (Mob mob : mobs) {
			dev.forja.combat.CombatStats.counts(mob).forEach((key, v) -> {
				if (key.startsWith(dev.forja.combat.CombatStats.WARNED_CUT + "_")) {
					cuts.merge(key, v, Integer::sum);
				}
			});
		}
		cuts.forEach((key, v) -> k.append(key).append('=').append(v).append(';'));
		k.append(String.format(Locale.ROOT, "dano_cuerpo=%.1f;dano_flecha=%.1f;golpes_flecha=%d;dano_pocion=%.1f;dano_fuego=%.1f;dano_otro=%.1f;"
			+ "vel_andando=%.4f;ticks_andando=%d;vel_corriendo=%.4f;ticks_corriendo=%d;ociosos=%.3f;flechas=%d;flechas_tocarian=%d;caidas=%.1f",
			w.melee, w.arrow, w.arrows, w.potion, w.fire, w.other, w.walkTicks == 0 ? 0.0 : w.walkDist / w.walkTicks, w.walkTicks,
			w.runTicks == 0 ? 0.0 : w.runDist / w.runTicks, w.runTicks, w.goingIn == 0 ? 0.0 : w.idle / (double) w.goingIn, w.fired, w.wouldHit, w.drops));
		// captain 2: acting-captain ticks, guarded ticks, escorts × ticks, estimates shared, orders shouted, when the elite fell,
		// and the captain's mean distance to the player
		Captain.Group g = Captain.group(player);
		k.append(String.format(Locale.ROOT, ";interino=%d;protegido=%d;escoltas=%d;compartida=%d;gritos=%d;elite_muere=%d;capitan_dist=%.2f",
			w.interim, w.guarded, w.escorts, w.shared, g == null ? 0 : g.shouts, w.eliteDied, w.captainTicks == 0 ? 0.0 : w.captainDist / w.captainTicks));
		DAMAGE.remove(player);
		WarnLog warns = null;
		for (Mob mob : mobs) {
			WarnLog l = WARN_LOGS.remove(mob);
			warns = l != null ? l : warns;
		}
		if (warns != null) {
			warns.counts.forEach((key, v) -> k.append(';').append(key).append('=').append(v));
		}
		String line = String.format(Locale.ROOT, "%s\t%d\t%.1f\t%d\t%d\t%d\t%s\t%.2f\t%.2f\t%.2f\t%s%n", label(mode, two), seed,
			taken * 60.0 * 20.0 / Math.max(1, ticks), killedAt, dead, ticks, o, watch[0] / n, watch[1] / n, watch[2] / n, k);
		try {
			Files.writeString(out, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (java.io.IOException failure) {
			throw new RuntimeException(failure);
		}
		CaptainBrain.override(player, null);
		CaptainBrain.overridePieces(player, null);
		Captain.forget(player);
		AABB box = new AABB(helper.absoluteVec(new Vec3(-4, -2, -4)), helper.absoluteVec(new Vec3(SIZE + 4, 10, SIZE + 4)));
		helper.getLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player)).forEach(Entity::discard);
		if (player instanceof net.minecraft.server.level.ServerPlayer sp && helper.getLevel().players().contains(sp)) {
			helper.getLevel().removePlayerImmediately(sp, Entity.RemovalReason.DISCARDED);
		} else {
			player.discard();
		}
	}
}
