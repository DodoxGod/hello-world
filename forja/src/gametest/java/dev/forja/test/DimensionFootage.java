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
		// FORJA_PELEA_SOLO=1: only the fight, for looking at it again without the whole plateau.
		if (System.getenv("FORJA_PELEA_SOLO") == null) {
			world(context, "cementerio-uno", "a", true);
		}
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
			// World a films the plateau with no fight in it; world b has the fight, which wants monsters.
			server.runCommand(full ? "difficulty peaceful" : "difficulty normal");
			server.runOnServer(s -> dev.forja.world.StarFight.peaceForFootage = full);
			long worldSeed = server.computeOnServer(s -> s.overworld().getSeed());
			StarYardLayout layout = StarYardLayout.of(worldSeed);
			log("dimension " + tag + ": seed '" + seed + "' = " + worldSeed + ", " + layout.rivers.length + " rios, "
				+ layout.islets.length + " islotes, " + StarYardGenerator.forges(layout).size() + " forjas");
			if (full) {
				portalTrip(context, server, connection, tag);
				enter(context, server, connection, tag);
				film(context, server, connection, layout, tag);
				leave(context, server, connection);
			} else {
				fight(context, server, connection, tag);
				enter(context, server, connection, tag);
				overview(context, server, connection, layout, tag);
				leave(context, server, connection);
			}
			server.runOnServer(s -> dev.forja.world.StarFight.peaceForFootage = false);
		}
	}

	/**
	 * Delivery 2 with real clicks: an old dead forge on its dais, right-clicked, opens into the empty frame;
	 * four oricalco pearls set one by one light it; stepping in takes the player to the graveyard's arrival
	 * platform; the lit well behind the platform brings them back to stand just outside the frame.
	 */
	private static void portalTrip(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String tag) {
		String p = "dimension_" + tag + "_p";
		server.runCommand("gamemode survival @a");
		BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
		int cx = spawn.getX() + 8;
		int cy = spawn.getY() + 1;
		int cz = spawn.getZ();
		// Level ground, a dais top of chiseled blackstone, and the dead forge on it, as in the castle.
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d polished_blackstone_bricks", cx - 6, cy - 2, cz - 6, cx + 6, cy - 1, cz + 6));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", cx - 6, cy, cz - 6, cx + 6, cy + 6, cz + 6));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d chiseled_polished_blackstone", cx - 1, cy, cz - 1, cx + 1, cy, cz + 1));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:fragua_apagada", cx, cy + 1, cz));
		server.runCommand("time set 13000");
		standAndLook(context, server, cx + 0.5, cy, cz + 4.5, cx + 0.5, cy + 1.5, cz + 0.5);
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		shot(context, p + "1_fragua_apagada");
		use(context);
		String opened = server.computeOnServer(s -> connection.getServerLevel().getBlockState(new BlockPos(cx, cy, cz + 2)).toString());
		log("dimension: after right-clicking the dead forge, south of it there is " + opened);
		check(opened.contains("mensula_estelar"), "the dead forge should open into the frame: " + opened);
		context.waitTicks(10);
		shot(context, p + "2_marco_vacio");
		// Four pearls, set by hand, the last from the south where the player will look from.
		server.runCommand("item replace entity @a weapon.mainhand with forja:perla_de_oricalco 4");
		int[][] sides = {{0, -1}, {1, 0}, {-1, 0}, {0, 1}};
		for (int i = 0; i < sides.length; i++) {
			int bx = cx + sides[i][0] * 2;
			int bz = cz + sides[i][1] * 2;
			standAndLook(context, server, cx + sides[i][0] * 4.5 + 0.5, cy, cz + sides[i][1] * 4.5 + 0.5, bx + 0.5, cy + 0.8, bz + 0.5);
			use(context);
			if (i == 2) {
				check(!server.computeOnServer(s -> connection.getServerLevel().getBlockState(new BlockPos(cx, cy, cz)).is(dev.forja.registry.ModBlocks.PORTAL_ESTELAR)),
					"three pearls should not light the portal");
				shot(context, p + "3_tres_perlas");
			}
		}
		boolean lit = server.computeOnServer(s -> connection.getServerLevel().getBlockState(new BlockPos(cx, cy, cz)).is(dev.forja.registry.ModBlocks.PORTAL_ESTELAR));
		check(lit, "four pearls should light the portal");
		standAndLook(context, server, cx + 0.5, cy + 2, cz + 5.5, cx + 0.5, cy, cz + 0.5);
		context.waitTicks(30);
		shot(context, p + "4_portal_encendido");
		// In: step onto the lit hole.
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 20", cx + 0.5, cy + 1.2, cz + 0.5));
		context.waitTicks(60);
		boolean there = context.computeOnClient(mc -> StarYardSky.here());
		check(there, "the star portal should take the player to the Cementerio entre Estrellas");
		context.waitTicks(60);
		shot(context, p + "5_llegada_por_el_portal");
		// And back, through the lit well behind the arrival platform.
		standAndLook(context, server, StarYard.RETURN_WELL.getX() + 0.5, S + 1, StarYard.RETURN_WELL.getZ() + 4.5,
			StarYard.RETURN_WELL.getX() + 0.5, S, StarYard.RETURN_WELL.getZ() + 0.5);
		shot(context, p + "6_pozo_de_vuelta");
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f 180 10", DIM,
			StarYard.RETURN_WELL.getX() + 0.5, S + 1.2, StarYard.RETURN_WELL.getZ() + 0.5));
		context.waitTicks(80);
		boolean back = context.computeOnClient(mc -> !StarYardSky.here());
		check(back, "the lit well should take the player back");
		String where = server.computeOnServer(s -> connection.getServerPlayer().position().toString());
		double off = server.computeOnServer(s -> connection.getServerPlayer().position().distanceTo(new Vec3(cx + 0.5, cy, cz + 0.5)));
		log("dimension: back from the graveyard at " + where + ", " + String.format(Locale.ROOT, "%.1f", off) + " from the portal");
		check(off < 8.0, "back beside the frame: " + where);
		context.waitTicks(30);
		shot(context, p + "7_vuelta_junto_al_marco");
		server.runCommand("time set noon");
	}

	/** Puts the (survival) player at a spot looking at a point, for a few ticks so it holds. */
	private static void standAndLook(ClientGameTestContext context, TestServerContext server, double x, double y, double z, double lx, double ly, double lz) {
		// The heading worked out here, from the eyes: "facing" on a survival player left the crosshair a block
		// or so above what it was told to look at, and the right click went into the air.
		double dx = lx - x;
		double dy = ly - (y + 1.62);
		double dz = lz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		String dim = context.computeOnClient(mc -> mc.level.dimension().identifier().toString());
		for (int tick = 0; tick < 5; tick++) {
			server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f %.2f %.2f", dim, x, y, z, yaw, pitch));
			context.waitTicks(1);
		}
		context.waitTicks(20);
	}

	/** One real right click: the use key held for a moment. */
	private static void use(ClientGameTestContext context) {
		String aim = context.computeOnClient(mc -> mc.player.position() + " looking at " + (mc.hitResult == null ? "nothing"
			: mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult block ? block.getBlockPos().toShortString() + " " + mc.level.getBlockState(block.getBlockPos())
			: mc.hitResult.getType().toString()));
		log("dimension: right click from " + aim);
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(3);
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(10);
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

		// The sun's colour does not jump with what is in view (Andy, 2026-09-29): a wall across half the
		// view from the edge, then the same view without it.
		double wx = lx - ox * 3.0;
		double wz = lz - oz * 3.0;
		view(context, server, wx, S + 3, wz, lx + ox * 30, S - 45, lz + oz * 30, p + "29_sol_antes_del_muro");
		server.runCommand(String.format(Locale.ROOT, "execute in %s run fill %d %d %d %d %d %d obsidian", DIM,
			(int) Math.floor(lx - 1), S + 1, (int) Math.floor(lz - 1), (int) Math.floor(lx), S + 5, (int) Math.floor(lz)));
		context.waitTicks(20);
		shot(context, p + "30_sol_muro_puesto");
		server.runCommand(String.format(Locale.ROOT, "execute in %s run fill %d %d %d %d %d %d air", DIM,
			(int) Math.floor(lx - 1), S + 1, (int) Math.floor(lz - 1), (int) Math.floor(lx), S + 5, (int) Math.floor(lz)));
		context.waitTicks(20);
		shot(context, p + "31_sol_muro_quitado");
		// A dead vent.
		int[] vent = layout.vents[0];
		double vx = vent[0] + 0.5;
		double vz = vent[1] + 0.5;
		Vec3 away = new Vec3(vx, 0, vz).normalize();
		view(context, server, vx - away.x * (vent[2] + 14), S + 8, vz - away.z * (vent[2] + 14), vx, S + vent[3] * 0.6, vz, p + "32_respiradero");
		view(context, server, vx + 3.5, S + vent[3] + 6, vz + 3.5, vx, S + vent[3] - 2, vz, p + "33_respiradero_crater");
		// The sky minutes apart: the moulds are spread round the whole turn, so some are always up.
		context.runOnClient(mc -> StarYardSky.skewSeconds = 0.0F);
		view(context, server, 0.5, S + 2, 0.5, 0.5, S + 60, 1.5, p + "34_cielo_girando_0min");
		context.runOnClient(mc -> StarYardSky.skewSeconds = 180.0F);
		context.waitTicks(5);
		shot(context, p + "35_cielo_girando_3min");
		context.runOnClient(mc -> StarYardSky.skewSeconds = 360.0F);
		context.waitTicks(5);
		shot(context, p + "36_cielo_girando_6min");
		context.runOnClient(mc -> StarYardSky.skewSeconds = 0.0F);
		// A world event in the graveyard's sky: its colours, stars and particles, never its moon.
		for (String event : new String[] {"aurora", "luna_de_sangre", "meteoritos"}) {
			server.runCommand("execute as @a run forja evento " + event);
			context.waitTicks(120);
			view(context, server, 0.5, S + 2, -20.5, 0.5, S + 30, 20.5, p + "37_evento_" + event);
			server.runOnServer(s -> dev.forja.world.WorldEvents.stop(s.getLevel(StarYard.LEVEL)));
			context.waitTicks(120);
		}

		// Without the ash haze.
		context.runOnClient(mc -> StarYardSky.fogOff = true);
		view(context, server, 0.5, S + 9, -27.5, 0.5, S, 4.5, p + "26_arena_sin_niebla");
		view(context, server, graves.x, graves.y, graves.z, 0.5, S + 3, 0.5, p + "27_tumbas_sin_niebla");
		view(context, server, high[0] + 0.5, high[2] + 8, high[1] + 0.5, 0.5, S, 0.5, p + "28_lejos_sin_niebla");
		context.runOnClient(mc -> StarYardSky.fogOff = false);
	}

	// ------------------------------------------------------------------ the fight (delivery 3)

	private static net.minecraft.server.level.ServerLevel yard(net.minecraft.server.MinecraftServer server) {
		return server.getLevel(StarYard.LEVEL);
	}

	/** A creative player flying at a spot, looking at a point: out of his reach, and still in the graveyard's count. */
	private static void hover(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
		double x, double y, double z, double lx, double ly, double lz) {
		server.runOnServer(s -> {
			var player = connection.getServerPlayer();
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		});
		double dx = lx - x;
		double dy = ly - (y + 1.62);
		double dz = lz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		for (int tick = 0; tick < 4; tick++) {
			server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f %.2f %.2f", DIM, x, y, z, yaw, pitch));
			context.waitTicks(1);
		}
	}

	private static String smith(TestServerContext server) {
		return server.computeOnServer(s -> {
			var boss = dev.forja.world.StarFight.boss(yard(s));
			return boss == null ? "none" : String.format(Locale.ROOT, "health %.0f, phase %d, waves %d, reforge %d, stunned %b, falling %b",
				boss.getHealth(), boss.phase(), boss.wavesCalled(), boss.starReforge(), boss.isStunned(), boss.isFalling());
		});
	}

	private static void setHealth(TestServerContext server, float share) {
		server.runOnServer(s -> {
			var boss = dev.forja.world.StarFight.boss(yard(s));
			if (boss != null) {
				boss.setHealth(boss.getMaxHealth() * share);
			}
		});
	}

	/**
	 * Delivery 3, the whole fight, sped up with commands: in, the star falls and he stands up; the sky
	 * casts one constellation for him and one for the players; the second phase and his apprentices coming
	 * up out of the ground; the Reforjado estelar, put out by tipping three braseros with real clicks; the
	 * stun; the third phase; his death, the star home and the cold forge; the Estrella forjada in the bag;
	 * and home with a real click on the star.
	 */
	private static void fight(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String tag) {
		String p = "dimension_" + tag + "_f";
		server.runCommand("gamemode creative @a");
		server.runCommand("execute as @a run forja dimension");
		context.runOnClient(mc -> {
			mc.options.fov().set(80);
			mc.options.renderDistance().set(12);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		// The fall: from where you arrive, looking up over the arena.
		hover(context, server, connection, 0.5, StarYard.SURFACE + 6, -34.5, 0.5, StarYard.SURFACE + 60, 0.5);
		long start = context.computeOnClient(mc -> mc.level.getGameTime());
		context.waitTicks(dev.forja.world.StarFight.ARRIVAL_DELAY + 12);
		shot(context, p + "01_cae_la_estrella");
		hover(context, server, connection, 0.5, StarYard.SURFACE + 6, -34.5, 0.5, StarYard.SURFACE + 25, 0.5);
		context.waitTicks(14);
		shot(context, p + "02_cae_cerca");
		hover(context, server, connection, 0.5, StarYard.SURFACE + 5, -20.5, 0.5, StarYard.SURFACE + 1, 0.5);
		context.waitTicks(18);
		shot(context, p + "03_impacto");
		context.waitTicks(30);
		log("dimension: after the fall, " + smith(server));
		check(smith(server).contains("falling false"), "he should be on his feet after the fall: " + smith(server));
		shot(context, p + "04_en_pie");
		// Two constellations: one for him (the Espada's burning line) and one for the players (the Lanza).
		server.runOnServer(s -> dev.forja.world.StarFight.cast(yard(s), dev.forja.world.StarFight.boss(yard(s)), dev.forja.world.StarFight.ESPADA, 3));
		hover(context, server, connection, 0.5, StarYard.SURFACE + 14, -18.5, 0.5, StarYard.SURFACE + 1, 0.5);
		context.waitTicks(30);
		shot(context, p + "05_espada_aviso_suelo");
		long now = context.computeOnClient(mc -> mc.level.getGameTime());
		Vec3 up = StarYardSky.constellationAt(dev.forja.world.StarFight.ESPADA, now);
		hover(context, server, connection, 0.5, StarYard.SURFACE + 3, -18.5, 0.5 + up.x * 40, StarYard.SURFACE + 3 + up.y * 40, -18.5 + up.z * 40);
		context.waitTicks(8);
		shot(context, p + "06_espada_cielo_herrero");
		hover(context, server, connection, 0.5, StarYard.SURFACE + 14, -18.5, 0.5, StarYard.SURFACE + 1, 0.5);
		context.waitTicks(30);
		shot(context, p + "07_espada_arde");
		context.waitTicks(40);
		server.runOnServer(s -> dev.forja.world.StarFight.cast(yard(s), dev.forja.world.StarFight.boss(yard(s)), dev.forja.world.StarFight.LANZA, 2));
		now = context.computeOnClient(mc -> mc.level.getGameTime());
		up = StarYardSky.constellationAt(dev.forja.world.StarFight.LANZA, now);
		hover(context, server, connection, 0.5, StarYard.SURFACE + 3, -18.5, 0.5 + up.x * 40, StarYard.SURFACE + 3 + up.y * 40, -18.5 + up.z * 40);
		context.waitTicks(30);
		shot(context, p + "08_lanza_cielo_jugadores");
		hover(context, server, connection, 0.5, StarYard.SURFACE + 10, -14.5, 0.5, StarYard.SURFACE + 1, 0.5);
		context.waitTicks(32);
		shot(context, p + "09_lanzas_caen");
		// The second phase: the apprentices come up out of the ground.
		context.waitTicks(20);
		setHealth(server, 0.62F);
		hover(context, server, connection, 0.5, StarYard.SURFACE + 5, -9.5, 0.5, StarYard.SURFACE + 1, 0.5);
		context.waitTicks(12);
		shot(context, p + "10_salen_de_la_tierra");
		context.waitTicks(18);
		shot(context, p + "11_medio_fuera");
		context.waitTicks(40);
		shot(context, p + "12_aprendices_fuera");
		log("dimension: after two thirds, " + smith(server));
		check(smith(server).contains("waves 1"), "the first wave should have come at two thirds: " + smith(server));
		// The Reforjado estelar.
		setHealth(server, 0.45F);
		context.waitTicks(10);
		hover(context, server, connection, 0.5, StarYard.SURFACE + 18, -16.5, 0.5, StarYard.SURFACE + 1, 0.5);
		context.waitTicks(20);
		shot(context, p + "13_reforjado_brasas");
		check(smith(server).contains("reforge 1"), "the Reforjado estelar should burn at half: " + smith(server));
		// Three braseros tipped by hand: a click on each bowl from beside it.
		for (int i = 0; i < 3; i++) {
			net.minecraft.core.BlockPos bowl = dev.forja.world.StarFight.brazier(i);
			Vec3 out = new Vec3(bowl.getX(), 0, bowl.getZ()).normalize();
			hover(context, server, connection, bowl.getX() + 0.5 + out.x * 2.5, bowl.getY() - 0.5, bowl.getZ() + 0.5 + out.z * 2.5,
				bowl.getX() + 0.5, bowl.getY() + 0.5, bowl.getZ() + 0.5);
			context.waitTicks(10);
			context.getInput().holdKey(options -> options.keyAttack);
			context.waitTicks(2);
			context.getInput().releaseKey(options -> options.keyAttack);
			context.waitTicks(4);
			if (i == 0) {
				hover(context, server, connection, 0.5, StarYard.SURFACE + 18, -16.5, 0.5, StarYard.SURFACE + 1, 0.5);
				context.waitTicks(28);
				shot(context, p + "14_colada_del_brasero");
			}
		}
		hover(context, server, connection, 0.5, StarYard.SURFACE + 18, -16.5, 0.5, StarYard.SURFACE + 1, 0.5);
		context.waitTicks(dev.forja.world.StarFight.FLOW_TICKS + 10);
		log("dimension: after the braseros, " + smith(server));
		check(smith(server).contains("reforge 2") && smith(server).contains("stunned true"), "the braseros should put out his forge and stun him: " + smith(server));
		hover(context, server, connection, 0.5, StarYard.SURFACE + 5, -8.5, 0.5, StarYard.SURFACE + 1.5, 0.5);
		context.waitTicks(5);
		shot(context, p + "15_aturdido");
		// The third phase, and the end.
		context.waitTicks(100);
		setHealth(server, 0.3F);
		context.waitTicks(30);
		shot(context, p + "16_fase_tres");
		context.waitTicks(80);
		server.runOnServer(s -> {
			var boss = dev.forja.world.StarFight.boss(yard(s));
			if (boss != null) {
				boss.setHealth(1.0F);
				boss.invulnerableTime = 0;
				boss.hurtServer(yard(s), yard(s).damageSources().playerAttack(connection.getServerPlayer()), 50.0F);
			}
		});
		context.waitTicks(20);
		hover(context, server, connection, 0.5, StarYard.SURFACE + 8, -16.5, 2.5, StarYard.SURFACE + 30, 0.5);
		context.waitTicks(20);
		shot(context, p + "17_cae_la_estrella_de_vuelta");
		context.waitTicks(40);
		hover(context, server, connection, 0.5, StarYard.SURFACE + 3, -6.5, 1.5, StarYard.SURFACE + 1.5, 0.5);
		context.waitTicks(20);
		shot(context, p + "18_estrella_y_fragua");
		String after = server.computeOnServer(s -> {
			var level = yard(s);
			return dev.forja.world.StarFight.state(level).stage() + " " + level.getBlockState(dev.forja.world.StarFight.returnStarAt())
				+ " " + level.getBlockState(dev.forja.world.StarFight.forgeAt()) + " bolsa "
				+ connection.getServerPlayer().getInventory().countItem(dev.forja.registry.ModItems.ESTRELLA_FORJADA)
				+ " debidas " + dev.forja.world.StarFight.state(level).owed().size()
				+ " estrellas " + dev.forja.world.StarFight.paidForTests(connection.getServerPlayer().getUUID());
		});
		log("dimension: after his death, " + after);
		check(after.startsWith("WON") && after.contains("estrella_de_vuelta") && after.contains("fragua_fria_estelar") && after.endsWith("estrellas 1"),
			"his death should leave the star home, the cold forge and one Estrella forjada: " + after);
		// Home, with a click on the star.
		server.runCommand("gamemode survival @a");
		standAndLook(context, server, 2.5, StarYard.SURFACE + 1, -1.5, 2.5, StarYard.SURFACE + 1.4, 0.5);
		use(context);
		context.waitTicks(40);
		boolean home = context.computeOnClient(mc -> !StarYardSky.here());
		check(home, "the star home should take the player back");
		shot(context, p + "19_de_vuelta");
		log("dimension: the fight took " + (context.computeOnClient(mc -> mc.level.getGameTime()) - start) + " ticks of footage");
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
