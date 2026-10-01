package dev.forja.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import dev.forja.ai.BlazePilot;
import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.NetBrain;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;

/**
 * FORJA_SOLO=blaze: a blaze flown by its trained network (redes_entrenadas/red_blaze.json, red_blaze_v1) against the
 * player on open ground, seen from the side: hovering in its band, the 20-tick warning of its burst (charged and
 * flaming, as vanilla's charged blaze), the three fireballs, and the warning up close.
 */
final class BlazeFootage {
	private BlazeFootage() {
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int sx = x + 600;
		int sz = z + 200;
		server.runCommand("time set noon");
		server.runCommand("weather clear 1000000");
		server.runCommand("difficulty normal");
		server.runCommand("gamemode survival @a");
		server.runCommand("effect give @a resistance infinite 4 true");
		server.runCommand("effect give @a fire_resistance infinite 0 true");
		server.runCommand("effect give @a regeneration infinite 4 true");
		server.runCommand("effect give @a saturation infinite 0 true");
		server.runCommand("attribute @a minecraft:knockback_resistance base set 1");
		tp(server, sx + 0.5, y, sz + 0.5, 180.0F, 0.0F);
		context.waitTicks(20);
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d smooth_stone", sx - 20, y - 1, sz - 30, sx + 20, y - 1, sz + 10));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", sx - 20, y, sz - 30, sx + 20, y + 20, sz + 10));
		tp(server, sx + 0.5, y, sz + 0.5, 180.0F, 0.0F);
		context.waitTicks(10);
		Path netFile = find("redes_entrenadas/red_blaze.json");
		check(netFile != null, "no se encuentra redes_entrenadas/red_blaze.json");
		int[] ids = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			NetBrain net;
			try {
				net = NetBrain.load(netFile);
			} catch (java.io.IOException failure) {
				throw new IllegalStateException(failure);
			}
			dev.forja.combat.CombatConfig.get().veteranChance = 0.0;
			dev.forja.combat.CombatConfig.get().eliteChance = 0.0;
			Blaze blaze = EntityTypes.BLAZE.create(level, EntitySpawnReason.EVENT);
			blaze.snapTo(sx + 0.5, y + 1.0, sz - 11.5, 0.0F, 0.0F);
			blaze.setPersistenceRequired();
			level.addFreshEntity(blaze);
			MobMind mind = MobAi.mind(blaze);
			mind.override = net;
			blaze.setTarget(connection.getServerPlayer());
			ArmorStand stand = new ArmorStand(level, sx + 12.5, y + 4.0, sz - 5.5);
			stand.setInvisible(true);
			stand.setNoGravity(true);
			level.addFreshEntity(stand);
			return new int[] {blaze.getId(), stand.getId()};
		});
		int blazeId = ids[0];
		int eye = ids[1];
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		context.waitFor(mc -> mc.level.getEntity(eye) != null && mc.level.getEntity(blazeId) != null, 100);

		// 1. Hovering: it rises from the ground into its band, and holds there.
		for (int t = 0; t < 60; t++) {
			frame(context, server, connection, blazeId, eye, LOW);
		}
		double height = server.computeOnServer(s -> dev.forja.ai.ObsBlaze.heightOverGround(connection.getServerLevel().getEntity(blazeId)));
		log("blaze: a " + String.format(Locale.ROOT, "%.2f", height) + " bloques del suelo");
		check(height >= 1.9 && height <= 5.1, "el blaze debería flotar entre 2 y 5 bloques: " + height);
		shot(context, "blaze_red_01_flotando");

		// 2. The warning: waits for a burst to start, then shoots its charge twice, and the fireballs.
		int charging = waitForPhase(context, server, connection, blazeId, eye, BlazePilot.CHARGING, 200, SIDE);
		check(charging >= 0, "el blaze debería empezar una ráfaga en 200 ticks");
		for (int t = 0; t < 8; t++) {
			frame(context, server, connection, blazeId, eye, SIDE);
		}
		boolean lit = context.computeOnClient(mc -> mc.level.getEntity(blazeId) != null && mc.level.getEntity(blazeId).isOnFire());
		check(lit, "mientras carga, el blaze debería verse encendido en el cliente");
		shot(context, "blaze_red_02_aviso");
		for (int t = 0; t < 8; t++) {
			frame(context, server, connection, blazeId, eye, SIDE);
		}
		shot(context, "blaze_red_03_aviso_final");
		int bursting = waitForPhase(context, server, connection, blazeId, eye, BlazePilot.BURSTING, 10, WIDE);
		check(bursting >= 0, "tras el aviso, la ráfaga");
		for (int t = 0; t < 8; t++) {
			frame(context, server, connection, blazeId, eye, WIDE);
		}
		shot(context, "blaze_red_04_rafaga");
		for (int t = 0; t < 6; t++) {
			frame(context, server, connection, blazeId, eye, WIDE);
		}
		shot(context, "blaze_red_05_rafaga_tercera");

		// 3. The next warning up close.
		charging = waitForPhase(context, server, connection, blazeId, eye, BlazePilot.CHARGING, 200, CLOSE);
		check(charging >= 0, "una segunda ráfaga");
		for (int t = 0; t < 10; t++) {
			frame(context, server, connection, blazeId, eye, CLOSE);
		}
		shot(context, "blaze_red_06_aviso_cerca");
		int phase = server.computeOnServer(s -> {
			MobMind mind = MobAi.mind((net.minecraft.world.entity.Mob) connection.getServerLevel().getEntity(blazeId));
			return mind.blazeState == null ? -1 : mind.blazeState.phase;
		});
		log("blaze: fase al final " + phase);
		server.runOnServer(s -> {
			var level = connection.getServerLevel();
			var blaze = level.getEntity(blazeId);
			if (blaze != null) {
				blaze.discard();
			}
			var stand = level.getEntity(eye);
			if (stand != null) {
				stand.discard();
			}
		});
		context.runOnClient(mc -> mc.setCameraEntity(mc.player));
	}

	/**
	 * One tick, with the camera moved to see the blaze side on: {@code distance} from the point looked at (the blaze,
	 * or {@code toward} of the way from it to the player), {@code height} over the player's feet.
	 */
	private static void frame(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int blazeId, int eye,
		View view) {
		server.runOnServer(s -> {
			var level = connection.getServerLevel();
			var blaze = level.getEntity(blazeId);
			var stand = level.getEntity(eye);
			var player = connection.getServerPlayer();
			if (blaze == null || stand == null) {
				return;
			}
			Vec3 b = blaze.position().add(0.0, 0.9, 0.0);
			Vec3 p = player.position().add(0.0, 0.9, 0.0);
			Vec3 look = b.add(p.subtract(b).scale(view.toward()));
			Vec3 along = new Vec3(p.x - b.x, 0.0, p.z - b.z);
			along = along.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : along.normalize();
			Vec3 side = new Vec3(-along.z, 0.0, along.x);
			Vec3 at = look.add(side.scale(view.distance()));
			at = new Vec3(at.x, player.getY() + view.height(), at.z);
			Vec3 d = look.subtract(at);
			float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
			float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
			stand.snapTo(at.x, at.y - stand.getEyeHeight(), at.z, yaw, pitch);
			stand.setYHeadRot(yaw);
		});
		context.waitTicks(1);
		context.runOnClient(mc -> {
			var stand = mc.level.getEntity(eye);
			if (stand != null && mc.getCameraEntity() != stand) {
				mc.setCameraEntity(stand);
			}
		});
	}

	/** Where the camera stands (see {@link #frame}). */
	private record View(double distance, double toward, double height) {
	}

	/** Low, to show the air under it; level with it for the warning; back and between the two for the burst; up close. */
	private static final View LOW = new View(7.0, 0.0, 1.0);
	private static final View SIDE = new View(7.0, 0.0, 3.0);
	private static final View WIDE = new View(9.0, 0.45, 3.0);
	private static final View CLOSE = new View(3.5, 0.0, 4.0);

	/** Ticks (framing all the while) until the blaze's burst is in {@code phase}; the ticks it took, or −1. */
	private static int waitForPhase(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int blazeId, int eye,
		int phase, int limit, View view) {
		for (int t = 0; t < limit; t++) {
			int now = server.computeOnServer(s -> {
				var mob = connection.getServerLevel().getEntity(blazeId);
				MobMind mind = mob instanceof net.minecraft.world.entity.Mob m ? MobAi.mind(m) : null;
				return mind == null || mind.blazeState == null ? -1 : mind.blazeState.phase;
			});
			if (now == phase) {
				return t;
			}
			frame(context, server, connection, blazeId, eye, view);
		}
		return -1;
	}

	private static void shot(ClientGameTestContext context, String name) {
		String camera = context.computeOnClient(mc -> mc.getCameraEntity() == null ? "none"
			: mc.getCameraEntity().getType() + " " + mc.getCameraEntity().position() + " yaw " + mc.getCameraEntity().getYRot()
				+ " pitch " + mc.getCameraEntity().getXRot() + "; jugador " + mc.player.position() + " visible " + !mc.player.isInvisible());
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
		log("blaze: captura " + name + " desde " + camera);
	}

	/** A file of the repo, walking up from the game's folder. */
	private static Path find(String relative) {
		for (Path at = FabricLoader.getInstance().getGameDir().toAbsolutePath(); at != null; at = at.getParent()) {
			for (String candidate : new String[] {relative, "forja/" + relative}) {
				if (Files.exists(at.resolve(candidate))) {
					return at.resolve(candidate);
				}
			}
		}
		return null;
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
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
