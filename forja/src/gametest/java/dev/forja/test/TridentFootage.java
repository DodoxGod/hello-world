package dev.forja.test;

import java.util.List;
import java.util.Locale;

import dev.forja.entity.ThrownHead;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.registry.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The thrown trident in flight (FORJA_SOLO=tridente): seen side on as it crosses the view tip first, turns
 * at a wall and comes back haft first, the way vanilla's loyal trident does. Then a dagger, also tip first,
 * and an axe, which still tumbles. Frames go to Forja_capturas_mejoras/tridente.
 */
final class TridentFootage {
	/** How far the flight line runs in front of the camera, and where the thrower and the wall stand on it. */
	private static final int DEPTH = 4;
	private static final int THROWER = -7;
	private static final int WALL = 6;

	private TridentFootage() {
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("tridente_" + name).disableCounterPrefix().withSize(960, 540));
	}

	/** Throws a weapon of this kind from an unseen thrower to the camera's left, flying right across the view. */
	private static void throwAcross(TestServerContext server, int x, int y, int z, ForgeType type) {
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			FakePlayer thrower = FakePlayer.get(level);
			thrower.snapTo(x + THROWER + 0.5, y, z - DEPTH + 0.5, -90.0F, 0.0F);
			thrower.setYHeadRot(-90.0F);
			ItemStack weapon = Assembler.create(type, Assembler.defaultMaterials(type));
			level.addFreshEntity(new ThrownHead(level, thrower, weapon, InteractionHand.MAIN_HAND, ThrownHead.Mode.WEAPON, true, 0));
		});
	}

	private static List<? extends ThrownHead> flying(TestServerContext server) {
		return server.computeOnServer(s -> s.overworld().getEntities(ModEntities.THROWN_HEAD, ThrownHead::isAlive));
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("gamemode spectator @a");
		server.runCommand("time set noon");
		// The camera looks north at the flight line, a little above it.
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 180.0 8.0", x + 0.5, y + 0.4, z + 0.5));
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int dy = 0; dy <= 3; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					level.setBlockAndUpdate(new BlockPos(x + WALL, y + dy, z - DEPTH + dz), Blocks.POLISHED_ANDESITE.defaultBlockState());
				}
			}
		});
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		context.waitTicks(30);

		throwAcross(server, x, y, z, ForgeType.TRIDENTE);
		boolean pointed = server.computeOnServer(s -> s.overworld().getEntities(ModEntities.THROWN_HEAD, ThrownHead::isPointFirst).size() == 1);
		check(pointed, "the thrown trident should fly point first");
		// A frame every two ticks while it is in the air: out to the wall, then back for home.
		for (int frame = 1; frame <= 12; frame++) {
			context.waitTicks(2);
			List<? extends ThrownHead> now = flying(server);
			if (now.isEmpty()) {
				break;
			}
			shot(context, String.format(Locale.ROOT, "%02d_%s", frame, now.getFirst().isReturning() ? "vuelta" : "ida"));
		}
		context.waitTicks(40);
		check(flying(server).isEmpty(), "the trident should be home by now");

		throwAcross(server, x, y, z, ForgeType.DAGA);
		context.waitTicks(6);
		shot(context, "10_daga");
		context.waitTicks(40);

		throwAcross(server, x, y, z, ForgeType.HACHA);
		context.waitTicks(5);
		shot(context, "11_hacha_gira");
		context.waitTicks(40);

		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}
}
