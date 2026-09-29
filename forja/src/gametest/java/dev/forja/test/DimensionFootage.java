package dev.forja.test;

import java.util.List;
import java.util.Locale;

import dev.forja.client.StarYardSky;
import dev.forja.world.StarYard;
import dev.forja.world.StarYardGenerator;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * FORJA_SOLO=dimension: the Cementerio entre Estrellas as a player first sees it (docs/HERRERO_DIMENSION.md,
 * section 2). In with the test command as the player would type it, then the camera round the plateau:
 * the arena, the ground among the graves, the constellations and one of them being poured, a river and its
 * bridge, a fall off the edge, a cold forge, the burning void looked down into from the edge, the far view
 * from an islet, and the same views with the ash haze put out. Back out at the end.
 */
final class DimensionFootage {
	private static final String DIM = StarYard.LEVEL.identifier().toString();

	private DimensionFootage() {
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runCommand("gamemode survival @a");
		server.runCommand("execute as @a run forja dimension");
		context.waitTicks(40);
		connection.waitForChunksRender();
		boolean there = context.computeOnClient(mc -> StarYardSky.here());
		check(there, "/forja dimension should take the player to the Cementerio entre Estrellas");
		String at = server.computeOnServer(s -> connection.getServerPlayer().blockPosition().toShortString()
			+ " in " + connection.getServerPlayer().level().dimension().identifier());
		log("dimension: player after /forja dimension at " + at);
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
		shot(context, "dimension_01_llegada");

		server.runCommand("gamemode spectator @a");
		int s = StarYard.SURFACE;
		// The arena from above its northern wall, and from high over it.
		view(context, server, connection, 0.5, s + 9, -27.5, 0.5, s, 4.5, "dimension_02_arena");
		view(context, server, connection, 30.5, s + 40, -30.5, 0.5, s, 0.5, "dimension_03_arena_alto");
		// Among the graves, at eye height, looking back at the arena and its braziers.
		double gx = -8.5;
		double gz = 58.5;
		view(context, server, connection, gx, s + 1.7, gz, 0.5, s + 3, 0.5, "dimension_04_tumbas");
		view(context, server, connection, gx + 3, s + 1.2, gz + 3, gx + 12, s + 0.5, gz + 20, "dimension_05_tumbas_suelo");
		// Up at the sky: the constellation being poured just now, then straight up.
		long now = context.computeOnClient(mc -> mc.level.getGameTime());
		float into = StarYardSky.pourSecondsAt(now);
		int wait = into < 2.0F ? (int) ((2.0F - into) * 20) : (int) ((40.0F - into + 2.0F) * 20);
		context.waitTicks(Math.max(1, wait));
		long pourTime = now + wait + 60;
		int poured = StarYardSky.pouredAt(pourTime);
		Vec3 up = StarYardSky.constellationAt(poured, pourTime);
		log("dimension: pouring " + StarYardSky.constellations().get(poured) + " towards " + up);
		double ex = 0.5;
		double ey = s + 2.0;
		double ez = -20.5;
		teleport(server, ex, ey, ez, ex + up.x * 40, ey + up.y * 40, ez + up.z * 40);
		context.waitTicks(60);
		shot(context, "dimension_06_colada_" + StarYardSky.constellations().get(poured));
		context.waitTicks(60);
		shot(context, "dimension_07_colada_llena");
		teleport(server, ex, ey, ez, ex + 1, ey + 40, ez + 12);
		context.waitTicks(10);
		shot(context, "dimension_08_constelaciones");
		// A river, the bridge over it, and where it rises.
		Vec3 bridge = StarYard.riverPoint(0, StarYard.BRIDGES[0]);
		view(context, server, connection, bridge.x - 9, s + 6, bridge.z - 9, bridge.x, s, bridge.z, "dimension_09_rio_puente");
		Vec3 spring = StarYard.spring(2);
		view(context, server, connection, spring.x + 9, s + 5, spring.z - 6, spring.x, s + 2, spring.z, "dimension_10_manantial");
		// The fall off the edge, from out in the void.
		double edgeR = StarYard.edge(Math.atan2(StarYard.riverPoint(0, 150).z, StarYard.riverPoint(0, 150).x));
		Vec3 lip = StarYard.riverPoint(0, edgeR);
		Vec3 out = lip.normalize().scale(1.0);
		view(context, server, connection, lip.x + out.x * 26 + out.z * 14, s - 10, lip.z + out.z * 26 - out.x * 14, lip.x, s - 20, lip.z, "dimension_11_cascada");
		// A cold forge.
		List<BlockPos> forges = StarYardGenerator.forges();
		check(!forges.isEmpty(), "there should be cold forges on the plain");
		BlockPos forge = forges.stream().min((a, b) -> Double.compare(a.distSqr(BlockPos.ZERO), b.distSqr(BlockPos.ZERO))).orElseThrow();
		view(context, server, connection, forge.getX() + 7.5, forge.getY() + 4, forge.getZ() + 6.5, forge.getX() + 0.5, forge.getY() + 1, forge.getZ() + 0.5, "dimension_12_forja_fria");
		// Down into the void from just past the edge, between two rivers: the glow of its floor, the ash and the embers.
		double bearing = Math.toRadians(210.0);
		double rim = StarYard.edge(bearing) + 3.0;
		double lx = Math.cos(bearing) * rim;
		double lz = Math.sin(bearing) * rim;
		double ox = Math.cos(bearing);
		double oz = Math.sin(bearing);
		view(context, server, connection, lx, s + 3, lz, lx + ox * 30, s - 45, lz + oz * 30, "dimension_13_borde_abajo");
		view(context, server, connection, lx, s + 3, lz, lx + ox * 3, s - 80, lz + oz * 3, "dimension_14_vacio_recto_abajo");
		// Under the plateau, out in the void below its northern edge, looking up at its underside.
		view(context, server, connection, 0.5, 40, -205.5, 0.5, 62, -110.5, "dimension_15_bajo_la_meseta");
		// The far view from the high islet, and from the low one where the ash is thick.
		int[] high = StarYard.ISLETS[3];
		view(context, server, connection, high[0] + 0.5, high[2] + 8, high[1] + 0.5, 0.5, s, 0.5, "dimension_16_lejos_islote");
		int[] low = StarYard.ISLETS[2];
		view(context, server, connection, low[0] + 0.5, low[2] + 2.5, low[1] + 0.5, 0.5, s - 10, 0.5, "dimension_17_islote_bajo");
		view(context, server, connection, -120.5, s + 90, -120.5, 0.5, s - 10, 0.5, "dimension_18_lejos_alto");
		// The same without the ash haze.
		context.runOnClient(mc -> StarYardSky.fogOff = true);
		view(context, server, connection, 0.5, s + 9, -27.5, 0.5, s, 4.5, "dimension_19_arena_sin_niebla");
		view(context, server, connection, gx, s + 1.7, gz, 0.5, s + 3, 0.5, "dimension_20_tumbas_sin_niebla");
		view(context, server, connection, high[0] + 0.5, high[2] + 8, high[1] + 0.5, 0.5, s, 0.5, "dimension_21_lejos_sin_niebla");
		view(context, server, connection, -120.5, s + 90, -120.5, 0.5, s - 10, 0.5, "dimension_22_lejos_alto_sin_niebla");
		context.runOnClient(mc -> StarYardSky.fogOff = false);

		// And back, the way the command says.
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

	private static void teleport(TestServerContext server, double x, double y, double z, double lx, double ly, double lz) {
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", DIM, x, y, z, lx, ly, lz));
	}

	private static void view(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
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
