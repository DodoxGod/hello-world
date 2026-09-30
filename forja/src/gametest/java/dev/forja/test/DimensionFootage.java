package dev.forja.test;

import java.util.List;
import java.util.Locale;

import dev.forja.client.StarYardSky;
import dev.forja.world.StarYard;
import dev.forja.world.StarYardGenerator;
import dev.forja.world.StarYardLayout;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * FORJA_SOLO=dimension: the Cementerio entre Estrellas as a player first sees it (docs/HERRERO_DIMENSION.md,
 * section 2), in two worlds with different seeds.
 *
 * <p>World "a", in full: in with the test command as the player would type it, then the camera round the
 * plateau — the arena, the graves, the constellations being poured, the sky seen to turn, shooting stars,
 * a river, a spring, a fall, a cold forge, the sun under the void from the edge and from below, the far
 * views, the plateau from straight above, every grave weapon from the four sides, a grave that goes with
 * its ground, and views with the ash haze put out. Back out at the end.
 *
 * <p>World "b", the same few views, to set beside world a's: the seed draws a different plateau round the
 * same arena.
 */
final class DimensionFootage {
	private static final String DIM = StarYard.LEVEL.identifier().toString();
	private static final int S = StarYard.SURFACE;

	private DimensionFootage() {
	}

	static void run(ClientGameTestContext context) {
		world(context, "cementerio-uno", "a", true);
		world(context, "cementerio-dos", "b", false);
	}

	private static void world(ClientGameTestContext context, String seed, String tag, boolean full) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().setUseConsistentSettings(false)
			.adjustSettings(settings -> settings.setSeed(seed)).create()) {
			TestServerConnection connection = singleplayer.getConnection();
			TestServerContext server = singleplayer.getServer();
			// A fixed wait: waiting for every chunk to render timed out on the second world.
			context.waitTicks(100);
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("difficulty peaceful");
			long worldSeed = server.computeOnServer(s -> s.overworld().getSeed());
			StarYardLayout layout = StarYardLayout.of(worldSeed);
			log("dimension " + tag + ": seed '" + seed + "' = " + worldSeed + ", " + layout.rivers.length + " rios, "
				+ layout.islets.length + " islotes, " + StarYardGenerator.forges(layout).size() + " forjas");
			enter(context, server, connection, tag);
			if (full) {
				film(context, server, connection, layout, tag);
			} else {
				overview(context, server, connection, layout, tag);
			}
			leave(context, server, connection);
		}
	}

	private static void enter(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String tag) {
		server.runCommand("gamemode survival @a");
		server.runCommand("execute as @a run forja dimension");
		context.waitTicks(40);
		boolean there = context.computeOnClient(mc -> StarYardSky.here());
		check(there, "/forja dimension should take the player to the Cementerio entre Estrellas");
		String at = server.computeOnServer(s -> connection.getServerPlayer().blockPosition().toShortString()
			+ " in " + connection.getServerPlayer().level().dimension().identifier());
		log("dimension " + tag + ": player after /forja dimension at " + at);
		check(at.startsWith(StarYard.ARRIVAL.toShortString()), "on the arrival platform: " + at);
		context.runOnClient(mc -> {
			mc.options.fov().set(80);
			mc.options.renderDistance().set(12);
			mc.gui.hud.getChat().clearMessages(false);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		context.waitTicks(60);
		shot(context, "dimension_" + tag + "_01_llegada");
		server.runCommand("gamemode spectator @a");
	}

	private static void leave(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		context.runOnClient(mc -> StarYardSky.fogOff = false);
		server.runCommand("gamemode survival @a");
		server.runCommand("execute as @a run forja dimension volver");
		context.waitTicks(40);
		boolean back = context.computeOnClient(mc -> !StarYardSky.here());
		check(back, "/forja dimension volver should bring the player back");
		context.runOnClient(mc -> {
			mc.options.fov().set(70);
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		log("dimension: back in " + server.computeOnServer(sv -> connection.getServerPlayer().level().dimension().identifier().toString()));
	}

	/** Somewhere among the graves on the ash, about sixty out and clear of every river. */
	private static Vec3 amongGraves(StarYardLayout layout) {
		for (int step = 0; step < 36; step++) {
			double angle = Math.toRadians(100.0 + step * 10.0);
			double x = Math.cos(angle) * 58.0;
			double z = Math.sin(angle) * 58.0;
			if (layout.riverDistance(x, z) > 14.0) {
				return new Vec3(x, S + 1.7, z);
			}
		}
		return new Vec3(0.5, S + 1.7, 58.5);
	}

	private static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, StarYardLayout layout, String tag) {
		String p = "dimension_" + tag + "_";
		view(context, server, 0.5, S + 9, -27.5, 0.5, S, 4.5, p + "02_arena");
		view(context, server, 30.5, S + 40, -30.5, 0.5, S, 0.5, p + "03_arena_alto");
		Vec3 graves = amongGraves(layout);
		view(context, server, graves.x, graves.y, graves.z, 0.5, S + 3, 0.5, p + "04_tumbas");
		view(context, server, graves.x, S + 1.2, graves.z, graves.x * 1.4, S + 0.5, graves.z * 1.4, p + "05_tumbas_suelo");

		// The sky: a constellation being poured, then the same patch of sky ten seconds apart, then shooting stars.
		long now = context.computeOnClient(mc -> mc.level.getGameTime());
		float into = StarYardSky.pourSecondsAt(now);
		int wait = into < 2.0F ? (int) ((2.0F - into) * 20) : (int) ((40.0F - into + 2.0F) * 20);
		context.waitTicks(Math.max(1, wait));
		long pourTime = now + wait + 60;
		int poured = StarYardSky.pouredAt(pourTime);
		Vec3 up = StarYardSky.constellationAt(poured, pourTime);
		double ex = 0.5;
		double ey = S + 2.0;
		double ez = -20.5;
		teleport(server, ex, ey, ez, ex + up.x * 40, ey + up.y * 40, ez + up.z * 40);
		context.waitTicks(60);
		shot(context, p + "06_colada_" + StarYardSky.constellations().get(poured));
		teleport(server, ex, ey, ez, ex + 1, ey + 40, ez + 12);
		context.waitTicks(10);
		shot(context, p + "07_cielo_t0");
		context.waitTicks(200);
		shot(context, p + "08_cielo_t10s");
		context.runOnClient(mc -> {
			RandomSource random = RandomSource.create(7L);
			for (int i = 0; i < 4; i++) {
				StarYardSky.shoot(random);
			}
		});
		context.waitTicks(3);
		shot(context, p + "09_estrellas_fugaces");

		// The rivers, where one rises, and where one pours off the edge.
		Vec3 bridge = layout.riverPoint(0, layout.bridges[0]);
		view(context, server, bridge.x - 9, S + 6, bridge.z - 9, bridge.x, S, bridge.z, p + "10_rio_puente");
		Vec3 spring = layout.spring(layout.rivers.length - 1);
		view(context, server, spring.x + 9, S + 5, spring.z - 6, spring.x, S + 2, spring.z, p + "11_manantial");
		Vec3 far = layout.riverPoint(0, 150);
		double edge = layout.edge(Math.atan2(far.z, far.x));
		Vec3 lip = layout.riverPoint(0, edge);
		Vec3 out = new Vec3(lip.x, 0, lip.z).normalize();
		view(context, server, lip.x + out.x * 26 + out.z * 14, S - 10, lip.z + out.z * 26 - out.x * 14, lip.x, S - 20, lip.z, p + "12_cascada");
		// A cold forge.
		List<BlockPos> forges = StarYardGenerator.forges(layout);
		check(!forges.isEmpty(), "there should be cold forges on the plain");
		BlockPos forge = forges.stream().min((a, b) -> Double.compare(a.distSqr(BlockPos.ZERO), b.distSqr(BlockPos.ZERO))).orElseThrow();
		view(context, server, forge.getX() + 7.5, forge.getY() + 4, forge.getZ() + 6.5, forge.getX() + 0.5, forge.getY() + 1, forge.getZ() + 0.5, p + "13_forja_fria");

		// The sun under the void: from just past the edge, straight down, and from under the plateau.
		double bearing = layout.rivers[0] + Math.PI / layout.rivers.length;
		double rim = layout.edge(bearing) + 3.0;
		double lx = Math.cos(bearing) * rim;
		double lz = Math.sin(bearing) * rim;
		double ox = Math.cos(bearing);
		double oz = Math.sin(bearing);
		view(context, server, lx, S + 3, lz, lx + ox * 30, S - 45, lz + oz * 30, p + "14_sol_desde_el_borde");
		view(context, server, lx, S + 3, lz, lx + ox * 3, S - 80, lz + oz * 3, p + "15_sol_recto_abajo");
		view(context, server, ox * (rim + 60), 40, oz * (rim + 60), 0.5, 55, 0.5, p + "16_sol_bajo_la_meseta");

		// Far off: from the highest islet, from high over the edge, and straight down from far above.
		int[] high = layout.islets[0];
		for (int[] islet : layout.islets) {
			if (islet[2] > high[2]) {
				high = islet;
			}
		}
		view(context, server, high[0] + 0.5, high[2] + 8, high[1] + 0.5, 0.5, S, 0.5, p + "17_lejos_islote");
		view(context, server, -120.5, S + 90, -120.5, 0.5, S - 10, 0.5, p + "18_lejos_alto");
		overhead(context, server, p + "19_cenital");

		// Every grave weapon, every lean, from the four sides; and one that goes with its ground.
		gravesFromFourSides(context, server, p);
		graveGoesWithItsGround(context, server, connection, p);

		// Without the ash haze.
		context.runOnClient(mc -> StarYardSky.fogOff = true);
		view(context, server, 0.5, S + 9, -27.5, 0.5, S, 4.5, p + "26_arena_sin_niebla");
		view(context, server, graves.x, graves.y, graves.z, 0.5, S + 3, 0.5, p + "27_tumbas_sin_niebla");
		view(context, server, high[0] + 0.5, high[2] + 8, high[1] + 0.5, 0.5, S, 0.5, p + "28_lejos_sin_niebla");
		context.runOnClient(mc -> StarYardSky.fogOff = false);
	}

	/** World b: the views to set beside world a's. */
	private static void overview(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, StarYardLayout layout, String tag) {
		String p = "dimension_" + tag + "_";
		view(context, server, 30.5, S + 40, -30.5, 0.5, S, 0.5, p + "03_arena_alto");
		Vec3 graves = amongGraves(layout);
		view(context, server, graves.x, graves.y, graves.z, 0.5, S + 3, 0.5, p + "04_tumbas");
		Vec3 far = layout.riverPoint(0, 150);
		double edge = layout.edge(Math.atan2(far.z, far.x));
		Vec3 lip = layout.riverPoint(0, edge);
		Vec3 out = new Vec3(lip.x, 0, lip.z).normalize();
		view(context, server, lip.x + out.x * 26 + out.z * 14, S - 10, lip.z + out.z * 26 - out.x * 14, lip.x, S - 20, lip.z, p + "12_cascada");
		view(context, server, -120.5, S + 90, -120.5, 0.5, S - 10, 0.5, p + "18_lejos_alto");
		overhead(context, server, p + "19_cenital");
	}

	/** The plateau from 170 above the arena, looking straight down with the widest lens. */
	private static void overhead(ClientGameTestContext context, TestServerContext server, String name) {
		context.runOnClient(mc -> mc.options.fov().set(110));
		for (int tick = 0; tick < 10; tick++) {
			server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a 0.5 %d 0.5 0 90", DIM, S + 170));
			context.waitTicks(1);
		}
		context.waitTicks(160);
		shot(context, name);
		context.runOnClient(mc -> mc.options.fov().set(80));
	}

	/**
	 * Every grave weapon in every facing and lean (72) on the arena floor, seen from the north, the south,
	 * the east and the west. Andy saw them wrong from one side.
	 */
	private static void gravesFromFourSides(ClientGameTestContext context, TestServerContext server, String p) {
		String[] facings = {"north", "east", "south", "west"};
		String[] weapons = {"espada", "espada_negra", "hacha", "tridente", "maza", "pico"};
		server.runCommand("effect give @a night_vision infinite 0 true");
		teleport(server, 0.5, S + 7, -22.5, 0.5, S, -2.5);
		context.waitTicks(20);
		// Six columns (the weapons) by twelve rows (four facings, each in its three leans), two blocks apart.
		for (int facing = 0; facing < 4; facing++) {
			for (int tilt = 0; tilt < 3; tilt++) {
				int z = -14 + (facing * 3 + tilt) * 2;
				for (int w = 0; w < 6; w++) {
					int x = -5 + w * 2;
					server.runCommand(String.format(Locale.ROOT, "execute in %s run setblock %d %d %d forja:arma_clavada[arma=%s,facing=%s,inclinacion=%d]",
						DIM, x, S + 1, z, weapons[w], facings[facing], tilt));
				}
			}
		}
		context.runOnClient(mc -> mc.options.fov().set(60));
		view(context, server, 0.5, S + 7, -24.5, 0.5, S + 1, -3.5, p + "20_tumbas_desde_el_norte");
		view(context, server, 0.5, S + 7, 17.5, 0.5, S + 1, -3.5, p + "21_tumbas_desde_el_sur");
		view(context, server, 18.5, S + 7, -3.5, 0.5, S + 1, -3.5, p + "22_tumbas_desde_el_este");
		view(context, server, -17.5, S + 7, -3.5, 0.5, S + 1, -3.5, p + "23_tumbas_desde_el_oeste");
		context.runOnClient(mc -> mc.options.fov().set(80));
		server.runCommand(String.format(Locale.ROOT, "execute in %s run fill -5 %d -14 5 %d 8 air", DIM, S + 1, S + 1));
	}

	/** A grave on a block of ash on the arena floor; the ash goes, and the grave with it, leaving nothing. */
	private static void graveGoesWithItsGround(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String p) {
		server.runCommand(String.format(Locale.ROOT, "execute in %s run setblock 0 %d 12 forja:ceniza", DIM, S + 1));
		server.runCommand(String.format(Locale.ROOT, "execute in %s run setblock 0 %d 12 forja:arma_clavada[arma=espada,facing=north,inclinacion=0]", DIM, S + 2));
		view(context, server, 0.5, S + 3.0, 9.5, 0.5, S + 2.2, 12.5, p + "24_tumba_con_suelo");
		server.runCommand(String.format(Locale.ROOT, "execute in %s run setblock 0 %d 12 air", DIM, S + 1));
		context.waitTicks(10);
		shot(context, p + "25_tumba_sin_suelo");
		String left = server.computeOnServer(s -> {
			var level = s.getLevel(StarYard.LEVEL);
			var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(new BlockPos(0, S + 2, 12)).inflate(4.0));
			return level.getBlockState(new BlockPos(0, S + 2, 12)) + " / " + drops.size() + " objetos";
		});
		log("dimension: grave after its ground went: " + left);
		check(left.startsWith("Block{minecraft:air}") && left.endsWith(" 0 objetos"), "the grave should go with its ground and drop nothing: " + left);
		server.runCommand("effect clear @a night_vision");
	}

	private static void teleport(TestServerContext server, double x, double y, double z, double lx, double ly, double lz) {
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", DIM, x, y, z, lx, ly, lz));
	}

	private static void view(ClientGameTestContext context, TestServerContext server,
		double x, double y, double z, double lx, double ly, double lz, String name) {
		for (int tick = 0; tick < 10; tick++) {
			teleport(server, x, y, z, lx, ly, lz);
			context.waitTicks(1);
		}
		// Chunks far off in the void render slowly under the test's renderer: a fixed wait, not the
		// all-chunks-rendered wait, which timed out on the far views.
		context.waitTicks(100);
		shot(context, name);
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void log(String message) {
		System.out.println("[forja-test] " + message);
	}
}
