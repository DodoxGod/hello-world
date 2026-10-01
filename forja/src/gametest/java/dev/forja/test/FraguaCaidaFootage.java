package dev.forja.test;

import java.util.Locale;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * FORJA_SOLO=fragua_caida: the Nether ruin forja:fragua_caida/fragua, placed on a cleared pocket of the real Nether
 * (its own light, no forced daytime) with the guards it comes with standing still, and photographed from a spectator
 * camera with the HUD hidden: a wide shot, two 3/4 views, the dead forge with the tables, and the guards up close.
 */
final class FraguaCaidaFootage {
	private static final int SIZE = 15;

	private FraguaCaidaFootage() {
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int ox = x + 500;
		int oz = z + 500;
		int oy = 50;
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("difficulty easy");
		server.runCommand("gamemode spectator @a");
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp @a %d %d %d", ox + 7, oy + 8, oz + 40));
		context.waitTicks(60);
		// A netherrack floor and an air pocket around the template, so the walls stand out and nothing from the cave hides them.
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run fill %d %d %d %d %d %d minecraft:netherrack",
			ox - 20, oy - 6, oz - 20, ox + 36, oy - 1, oz + 50));
		// fill is capped at 32768 blocks, so the air goes in slices.
		for (int zz = oz - 20; zz <= oz + 50; zz += 10) {
			server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run fill %d %d %d %d %d %d minecraft:air",
				ox - 20, oy, zz, ox + 36, oy + 24, Math.min(zz + 9, oz + 50)));
		}
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run place template forja:fragua_caida/fragua %d %d %d", ox, oy, oz));
		context.waitTicks(20);
		int[] counts = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level.NETHER);
			int forges = 0;
			int tables = 0;
			for (BlockPos pos : BlockPos.betweenClosed(ox, oy, oz, ox + SIZE - 1, oy + 9, oz + SIZE - 1)) {
				forges += level.getBlockState(pos).is(dev.forja.registry.ModBlocks.FRAGUA_APAGADA) ? 1 : 0;
				tables += level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_FORJA)
					|| level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_PIEZAS) ? 1 : 0;
			}
			int guards = 0;
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(ox - 2, oy - 1, oz - 2, ox + SIZE + 2, oy + 12, oz + SIZE + 2))) {
				mob.setNoAi(true);
				guards++;
			}
			return new int[] {forges, tables, guards};
		});
		log("fragua caida: fraguas apagadas " + counts[0] + ", mesas " + counts[1] + ", guardias " + counts[2]);
		check(counts[0] == 1 && counts[1] == 2, "la fragua caida deberia traer una fragua apagada y dos mesas");
		check(counts[2] == 7, "y sus siete guardias (2 automatas, 2 corazas, 3 pavesas), hay " + counts[2]);

		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		// The template's origin is its corner; its middle is (7.5, 7.5) and its gap in the wall is on the south side.
		double cx = ox + 7.5;
		double cz = oz + 7.5;
		look(context, server, cx, oy + 9, oz + 38, cx, oy + 2, cz, "fragua_caida_01_desde_fuera");
		look(context, server, ox - 14, oy + 11, oz - 14, cx, oy + 2, cz, "fragua_caida_02_tres_cuartos_noroeste");
		look(context, server, ox + 29, oy + 11, oz + 29, cx, oy + 2, cz, "fragua_caida_03_tres_cuartos_sureste");
		look(context, server, ox + 11.5, oy + 4.5, oz + 12.0, cx, oy + 2.5, cz, "fragua_caida_04_fragua_apagada_y_mesas");
		look(context, server, ox + 4.5, oy + 4.5, oz + 2.5, cx, oy + 2.5, cz, "fragua_caida_05_fragua_y_yunque");
		look(context, server, ox + 3.5, oy + 2.6, oz + 9.0, ox + 3.5, oy + 1.8, oz + 3.5, "fragua_caida_06_guardia_automata");
		look(context, server, ox + 11.5, oy + 2.6, oz + 6.0, ox + 11.5, oy + 1.8, oz + 11.5, "fragua_caida_07_guardia_automata_este");

		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		server.runCommand("gamemode survival @a");
	}

	/** The spectator camera at (x, y, z) looking at (tx, ty, tz), a few ticks to let the chunks and light settle, then the shot. */
	private static void look(ClientGameTestContext context, TestServerContext server, double x, double y, double z,
		double tx, double ty, double tz, String name) {
		double dx = tx - x;
		double dy = ty - y;
		double dz = tz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		context.waitTicks(40);
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
		log("fragua caida: captura " + name);
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
