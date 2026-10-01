package dev.forja.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import dev.forja.Forja;
import dev.forja.ai.MobAi;
import dev.forja.combat.CombatConfig;
import dev.forja.entity.FallenSmith;
import dev.forja.entity.Shockwave;
import dev.forja.entity.WalkingAnvil;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The server-side benchmark (docs/RENDIMIENTO.md): hordes of 30 and 50 hostiles of mixed kinds round one
 * player, with the rules and with the trained networks, the same horde with Forja's combat switched off
 * for reference, and a fight with the fallen smith through his three stages. Every window records the
 * length of each server tick, clocks round Forja's own paths and a JFR recording (see {@link Perf}).
 *
 * <p>Only with FORJA_RENDIMIENTO set to the folder the report goes to; otherwise it passes at once. It
 * has a test environment of its own, so it runs in a batch of its own and never shares the server with
 * another test. The networks come from the folder in FORJA_REDES, when it is set.
 */
public class BenchmarkGameTests {
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** Half the side of the flat floor the fights happen on, and how high the air above it is cleared. */
	private static final int ARENA = 22;
	private static final int HEADROOM = 8;
	/** Where the player walks: a slow circle round the middle, so the monsters keep having to follow. */
	private static final double WALK_RADIUS = 3.0;
	private static final int WALK_PERIOD = 400;
	/** The player strikes the nearest monster in reach this often, this hard. */
	private static final int STRIKE_EVERY = 10;
	private static final float STRIKE_DAMAGE = 5.0F;
	private static final double STRIKE_REACH = 3.5;
	/** Ticks measured per horde, after 200 to let the fight settle in. */
	private static final int WINDOW = 1200;

	/** The vanilla share of every ten monsters, and the mod's monsters taken in turn for the rest. */
	private static final List<EntityType<? extends Mob>> VANILLA = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER,
		EntityTypes.ZOMBIE, EntityTypes.CREEPER, EntityTypes.SKELETON);
	private static final List<EntityType<? extends Mob>> FORJA = List.of(ModEntities.CORAZA, ModEntities.PERCUTOR, ModEntities.TENAZA,
		ModEntities.AUTOMATA, ModEntities.TEMPLADOR, ModEntities.ESCORIA, ModEntities.HERRUMBRE, ModEntities.GUARDIAN_DE_CUNO,
		ModEntities.PAVESA, ModEntities.ASCUA_MAYOR);

	/** The kind of monster number {@code i} of a horde is: six vanilla in every ten, four of the mod's. */
	static EntityType<? extends Mob> kind(int i) {
		int inTen = i % 10;
		if (inTen < VANILLA.size()) {
			return VANILLA.get(inTen);
		}
		return FORJA.get(((i / 10) * 4 + inTen - VANILLA.size()) % FORJA.size());
	}

	/** The benchmark under way, driven from the server's own tick (see {@link #hordesAndSmith}). */
	private static Bench running;
	private static boolean driven;

	@GameTest(environment = "forja-test:rendimiento", maxTicks = 40000)
	public void hordesAndSmith(GameTestHelper helper) {
		String out = System.getenv("FORJA_RENDIMIENTO");
		if (out == null || out.isBlank()) {
			helper.succeed();
			return;
		}
		// Not helper.onEachTick: that books one callback for every tick up to the timeout, and the test
		// runner walks all forty thousand of them every tick, which it would be measuring as the server's.
		if (!driven) {
			driven = true;
			ServerTickEvents.END_SERVER_TICK.register(server -> {
				Bench bench = running;
				if (bench != null) {
					bench.tick();
				}
			});
		}
		running = new Bench(helper, Path.of(out.trim()), System.getenv("FORJA_REDES"));
	}

	/** One thing measured: how many of what, under which brain, for how long. */
	private record Scenario(String name, String description, int mobs, boolean nets, boolean enabled, boolean smith, int settle, int measure) {
	}

	/** The benchmark as a little machine that moves on one server tick at a time. */
	private static final class Bench {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final Path folder;
		private final Path nets;
		private final List<Scenario> scenarios = new ArrayList<>();
		private final List<Perf.Result> results = new ArrayList<>();
		private final BlockPos centre;
		private final CombatGameTests.TestPlayer player;
		private final List<Mob> horde = new ArrayList<>();

		// Configuration as the benchmark found it, put back at the end.
		private final String savedMode;
		private final String savedFolder;
		private final boolean savedEnabled;
		private final double savedVeteran;
		private final double savedElite;

		private int index = -1;
		private int step;
		private int spawned;
		private int respawns;
		private long mobSamples;
		private long mobSum;
		private long waveSum;
		private int waveMax;
		private long arrowSum;
		private FallenSmith smith;
		private boolean finished;

		Bench(GameTestHelper helper, Path folder, String nets) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.folder = folder;
			this.nets = nets == null || nets.isBlank() ? null : Path.of(nets.trim());
			this.centre = helper.absolutePos(new BlockPos(4, 1, 4));
			CombatConfig cfg = CombatConfig.get();
			this.savedMode = cfg.iaModo;
			this.savedFolder = cfg.iaCarpetaRedes;
			this.savedEnabled = cfg.enabled;
			this.savedVeteran = cfg.veteranChance;
			this.savedElite = cfg.eliteChance;
			// Every monster comes plain: a veteran or an elite by chance would make two runs differ.
			cfg.veteranChance = 0.0;
			cfg.eliteChance = 0.0;
			this.level.getServer().setDifficulty(Difficulty.NORMAL, true);

			this.player = new CombatGameTests.TestPlayer(this.level);
			clearSpawnGrace(this.player);
			this.player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
			this.player.setHealth(this.player.getMaxHealth());
			this.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			this.walk(0);

			boolean haveNets = this.nets != null && Files.isDirectory(this.nets);
			this.scenarios.add(new Scenario("calentamiento", "50 mobs con reglas, solo para que el JIT compile (no cuenta).", 50, false, true, false, 300, 300));
			this.scenarios.add(new Scenario("reglas_30", "30 mobs (18 vanilla + 12 de Forja) con el cerebro de reglas.", 30, false, true, false, 200, WINDOW));
			this.scenarios.add(new Scenario("reglas_50", "50 mobs (30 vanilla + 20 de Forja) con el cerebro de reglas.", 50, false, true, false, 200, WINDOW));
			if (haveNets) {
				this.scenarios.add(new Scenario("redes_30", "Los mismos 30 mobs, cada uno con la red entrenada de su familia.", 30, true, true, false, 200, WINDOW));
				this.scenarios.add(new Scenario("redes_50", "Los mismos 50 mobs, cada uno con la red entrenada de su familia.", 50, true, true, false, 200, WINDOW));
			}
			this.scenarios.add(new Scenario("apagado_50", "Referencia: los mismos 50 mobs con el combate y la IA de Forja apagados (enabled = false)."
				+ " Los mobs de Forja siguen con su propia IA; los vanilla vuelven a la suya.", 50, false, false, false, 200, WINDOW));
			this.scenarios.add(new Scenario("jefe", "El Herrero Caído contra el jugador, con reglas: onda, revés y garfio; a los 250 ticks baja al 70 %"
				+ " (aprendices), a los 500 al 45 % (reforja) y a los 750 al 20 % (anillo, yunques y lluvia de estrellas).", 0, false, true, true, 40, 900));
			if (haveNets) {
				this.scenarios.add(new Scenario("jefe_redes", "El mismo combate con las redes entrenadas (la del jefe, forja_jefe, y las de sus aprendices).",
					0, true, true, true, 40, 900));
			}
			this.forceChunks(true);
		}

		void tick() {
			if (this.finished) {
				return;
			}
			if (this.index < 0 || this.step >= this.current().settle() + this.current().measure()) {
				if (this.index >= 0) {
					this.closeScenario();
				}
				this.index++;
				if (this.index >= this.scenarios.size()) {
					this.finish();
					return;
				}
				this.openScenario();
				this.step = 0;
				return;
			}
			Scenario s = this.current();
			if (this.step == s.settle()) {
				Perf.begin(s.name());
				this.mobSamples = 0;
				this.mobSum = 0;
				this.waveSum = 0;
				this.waveMax = 0;
				this.arrowSum = 0;
				this.respawns = 0;
			}
			this.upkeep(s);
			this.step++;
		}

		private Scenario current() {
			return this.scenarios.get(this.index);
		}

		// --- Setting up and taking down ---------------------------------------------------------------------

		private void openScenario() {
			Scenario s = this.current();
			CombatConfig cfg = CombatConfig.get();
			cfg.enabled = s.enabled();
			cfg.iaModo = s.nets() ? "auto" : "reglas";
			cfg.iaCarpetaRedes = s.nets() ? this.nets.toAbsolutePath().toString() : this.savedFolder;
			MobAi.reload();
			this.clearArena();
			this.buildArena();
			this.horde.clear();
			this.spawned = 0;
			this.smith = null;
			if (s.smith()) {
				this.smith = FallenSmith.summon(this.level, this.centre.offset(6, -1, 0));
				this.smith.setTarget(this.player);
				this.horde.add(this.smith);
			} else {
				for (int i = 0; i < s.mobs(); i++) {
					this.spawnOne();
				}
			}
			Forja.LOGGER.info("[rendimiento] {}: {} mobs, redes {}, problemas de redes: {}", s.name(), this.horde.size(), s.nets(), MobAi.problems());
		}

		private void closeScenario() {
			Scenario s = this.current();
			Perf.Result result = Perf.end(this.folder);
			result.description = s.description();
			double samples = Math.max(1, this.mobSamples);
			result.notes.put("Mobs vivos en la arena (media por tick)", String.format(Locale.ROOT, "%.1f", this.mobSum / samples));
			result.notes.put("Reapariciones para mantener la horda", String.valueOf(this.respawns));
			result.notes.put("Ondas (Shockwave) vivas", String.format(Locale.ROOT, "media %.2f, máximo %d", this.waveSum / samples, this.waveMax));
			result.notes.put("Proyectiles vivos (media)", String.format(Locale.ROOT, "%.1f", this.arrowSum / samples));
			if (s.nets()) {
				result.notes.put("Redes", "de " + this.nets + (MobAi.problems().isEmpty() ? ", todas aceptadas" : ", rechazadas: " + MobAi.problems()));
			}
			if (this.smith != null) {
				result.notes.put("Jefe al final", String.format(Locale.ROOT, "%s, vida %.0f / %.0f, fase %d",
					this.smith.isAlive() ? "vivo" : "muerto", this.smith.getHealth(), this.smith.getMaxHealth(), this.smith.phase()));
			}
			this.results.add(result);
			Forja.LOGGER.info("[rendimiento]\n{}", Perf.markdown(result));
			this.clearArena();
		}

		private void finish() {
			this.finished = true;
			running = null;
			CombatConfig cfg = CombatConfig.get();
			cfg.iaModo = this.savedMode;
			cfg.iaCarpetaRedes = this.savedFolder;
			cfg.enabled = this.savedEnabled;
			cfg.veteranChance = this.savedVeteran;
			cfg.eliteChance = this.savedElite;
			MobAi.reload();
			this.forceChunks(false);
			try {
				this.write();
			} catch (IOException failure) {
				throw new IllegalStateException("no se pudo escribir el informe en " + this.folder, failure);
			}
			this.helper.succeed();
		}

		private void write() throws IOException {
			Files.createDirectories(this.folder);
			StringBuilder report = new StringBuilder("# Rendimiento de Forja: resultado de una pasada\n\n");
			report.append("- Fecha: ").append(LocalDateTime.now().format(STAMP)).append('\n');
			report.append("- Java ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name"))
				.append(", ").append(Runtime.getRuntime().availableProcessors()).append(" hilos, memoria máxima ")
				.append(Runtime.getRuntime().maxMemory() / (1024 * 1024)).append(" MB\n");
			report.append("- Servidor de gametest: sin jugadores reales (no se mandan paquetes); el jugador es un FakePlayer que no se mueve solo.\n\n");
			for (Perf.Result result : this.results) {
				report.append(Perf.markdown(result));
			}
			Files.writeString(this.folder.resolve("informe.md"), report.toString());
			Path csv = this.folder.resolve("resumen.csv");
			if (!Files.exists(csv)) {
				Files.writeString(csv, "fecha;" + Perf.csvHeader());
			}
			StringBuilder lines = new StringBuilder();
			String stamp = LocalDateTime.now().format(STAMP);
			for (Perf.Result result : this.results) {
				lines.append(stamp).append(';').append(Perf.csvLine(result));
			}
			Files.writeString(csv, lines.toString(), StandardOpenOption.APPEND);
		}

		// --- Each tick of a scenario ---------------------------------------------------------------------

		private void upkeep(Scenario s) {
			this.walk(this.step);
			// The player never dies: the point is the monsters' side of the fight, as long as it lasts.
			this.player.setHealth(this.player.getMaxHealth());
			this.player.clearFire();
			this.player.setDeltaMovement(Vec3.ZERO);
			if (this.step % STRIKE_EVERY == 0) {
				this.strike();
			}
			for (Iterator<Mob> it = this.horde.iterator(); it.hasNext();) {
				Mob mob = it.next();
				if (!mob.isAlive() || mob.isRemoved()) {
					it.remove();
				}
			}
			if (!s.smith()) {
				while (this.horde.size() < s.mobs()) {
					this.spawnOne();
					if (this.step >= s.settle()) {
						this.respawns++;
					}
				}
			} else {
				this.smithStages(s);
			}
			if (this.step % 20 == 0) {
				// Monsters that dropped their target come back to it: a horde that stands about measures nothing.
				for (Mob mob : this.level.getEntitiesOfClass(Mob.class, this.arenaBox(), m -> m.isAlive() && m instanceof Enemy
					&& !(m instanceof WalkingAnvil) && m.getTarget() == null)) {
					mob.setTarget(this.player);
				}
			}
			// Counting what is about takes three searches of the arena: every fifth tick is plenty for a mean.
			if (this.step >= s.settle() && this.step % 5 == 0) {
				List<Mob> alive = this.level.getEntitiesOfClass(Mob.class, this.arenaBox(), m -> m.isAlive() && m instanceof Enemy);
				int waves = this.level.getEntitiesOfClass(Shockwave.class, this.arenaBox().inflate(16.0)).size();
				this.mobSamples++;
				this.mobSum += alive.size();
				this.waveSum += waves;
				this.waveMax = Math.max(this.waveMax, waves);
				this.arrowSum += this.level.getEntitiesOfClass(Projectile.class, this.arenaBox()).size();
			}
		}

		/** The smith's stages, brought on by the clock rather than by a player who can hit that hard. */
		private void smithStages(Scenario s) {
			if (this.smith == null || !this.smith.isAlive()) {
				return;
			}
			int t = this.step - s.settle();
			float share = t == 250 ? 0.70F : t == 500 ? 0.45F : t == 750 ? 0.20F : -1.0F;
			if (share > 0.0F) {
				this.smith.setHealth(this.smith.getMaxHealth() * share);
			}
			if (this.smith.getTarget() == null) {
				this.smith.setTarget(this.player);
			}
		}

		private void strike() {
			Mob nearest = null;
			double best = STRIKE_REACH * STRIKE_REACH;
			for (Mob mob : this.level.getEntitiesOfClass(Mob.class, this.player.getBoundingBox().inflate(STRIKE_REACH), m -> m.isAlive() && m instanceof Enemy)) {
				double d = mob.distanceToSqr(this.player);
				if (d < best) {
					best = d;
					nearest = mob;
				}
			}
			if (nearest != null) {
				nearest.hurtServer(this.level, this.level.damageSources().playerAttack(this.player), STRIKE_DAMAGE);
			}
		}

		private void walk(int t) {
			double a = 2.0 * Math.PI * t / WALK_PERIOD;
			double radius = this.index >= 0 && this.current().smith() ? WALK_RADIUS * 2.0 : WALK_RADIUS;
			double x = this.centre.getX() + 0.5 + Math.cos(a) * radius;
			double z = this.centre.getZ() + 0.5 + Math.sin(a) * radius;
			this.player.setPos(x, this.centre.getY(), z);
			this.player.setOnGround(true);
			float yaw = (float) Math.toDegrees(a) + 180.0F;
			this.player.setYRot(yaw);
			this.player.setYHeadRot(yaw);
		}

		private void spawnOne() {
			int i = this.spawned++;
			EntityType<? extends Mob> type = kind(i);
			double angle = i * 2.399963;
			double radius = 8.0 + 6.0 * ((i * 0.618034) % 1.0);
			double x = this.centre.getX() + 0.5 + Math.cos(angle) * radius;
			double z = this.centre.getZ() + 0.5 + Math.sin(angle) * radius;
			Mob mob = type.create(this.level, EntitySpawnReason.EVENT);
			if (mob == null) {
				return;
			}
			mob.snapTo(x, this.centre.getY(), z, (float) Math.toDegrees(angle), 0.0F);
			mob.finalizeSpawn(this.level, this.level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, null);
			if (mob instanceof Zombie || mob instanceof AbstractSkeleton) {
				// A carved pumpkin never wears out: the undead fight instead of burning in the test world's daylight.
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
			}
			mob.setPersistenceRequired();
			if (this.level.addFreshEntity(mob)) {
				mob.setTarget(this.player);
				this.horde.add(mob);
			}
		}

		// --- The ground ---------------------------------------------------------------------------------

		private AABB arenaBox() {
			return new AABB(this.centre.getX() - ARENA, this.centre.getY() - 4, this.centre.getZ() - ARENA,
				this.centre.getX() + ARENA + 1, this.centre.getY() + HEADROOM + 8, this.centre.getZ() + ARENA + 1);
		}

		/** Everything a fight leaves behind goes: monsters, arrows, drops, rings. The player is not in the world. */
		private void clearArena() {
			for (Entity entity : this.level.getEntitiesOfClass(Entity.class, this.arenaBox().inflate(24.0), e -> !(e instanceof Player))) {
				entity.discard();
			}
		}

		/** A flat stone floor with clear air over it: craters, pillars, webs and embers from the last scenario go. */
		private void buildArena() {
			BlockState stone = Blocks.STONE.defaultBlockState();
			BlockState air = Blocks.AIR.defaultBlockState();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int floor = this.centre.getY() - 1;
			for (int dx = -ARENA; dx <= ARENA; dx++) {
				for (int dz = -ARENA; dz <= ARENA; dz++) {
					for (int y = floor - 2; y <= floor + HEADROOM; y++) {
						pos.set(this.centre.getX() + dx, y, this.centre.getZ() + dz);
						this.level.setBlock(pos, y <= floor ? stone : air, 2);
					}
				}
			}
		}

		private void forceChunks(boolean on) {
			int x0 = (this.centre.getX() - ARENA - 32) >> 4;
			int x1 = (this.centre.getX() + ARENA + 32) >> 4;
			int z0 = (this.centre.getZ() - ARENA - 32) >> 4;
			int z1 = (this.centre.getZ() + ARENA + 32) >> 4;
			for (int cx = x0; cx <= x1; cx++) {
				for (int cz = z0; cz <= z1; cz++) {
					this.level.setChunkForced(cx, cz, on);
				}
			}
		}

		/** The same trick as {@link CombatGameTests}: a fake player never ticks its spawn grace away. */
		private static void clearSpawnGrace(ServerPlayer player) {
			for (java.lang.reflect.Field field : ServerPlayer.class.getDeclaredFields()) {
				if (field.getType() == int.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
					try {
						field.setAccessible(true);
						if (field.getInt(player) == 60) {
							field.setInt(player, 0);
						}
					} catch (ReflectiveOperationException | RuntimeException ignored) {
						// Not the field we are after.
					}
				}
			}
		}
	}
}
