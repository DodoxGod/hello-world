package dev.forja.test;

import java.util.Locale;

import dev.forja.client.PressureHud;
import dev.forja.difficulty.Pressure;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The armour indicator of the HUD (client/PressureHud) on screen (FORJA_SOLO=presion): whole, half, broken just
 * now and broken a while, coming back, and gone with F1, in creative and with no armour. The shots are laid out
 * by tools/hoja_presion.py.
 */
final class PressureFootage {
	private PressureFootage() {
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.takeScreenshot(TestScreenshotOptions.of("presion_" + name).disableCounterPrefix().withSize(960, 540));
	}

	private static void hits(TestServerContext server, TestServerConnection connection, int count) {
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			for (int i = 0; i < count; i++) {
				Pressure.onHit(player);
			}
		});
	}

	private static float shown(ClientGameTestContext context) {
		return context.computeOnClient(mc -> PressureHud.shownIntegrity());
	}

	private static boolean broken(ClientGameTestContext context) {
		return context.computeOnClient(mc -> PressureHud.isBroken());
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		// The test world is peaceful, and pressure is armour penetration, which the ladder only turns on from
		// Normal up (difficulty/Ladder, Pressure.add): on peaceful no blow adds any and the shield stays whole.
		server.runCommand("difficulty normal");
		try {
			filmOnNormal(context, server, connection, x, y, z);
		} finally {
			server.runCommand("difficulty peaceful");
		}
	}

	private static void filmOnNormal(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x,
		int y, int z) {
		check(server.computeOnServer(s -> dev.forja.difficulty.Ladder.current().penetration),
			"the pressure needs a level with armour penetration, at " + server.computeOnServer(s -> dev.forja.difficulty.Ladder.current()));
		server.runCommand("gamemode survival @a");
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0.0 20.0", x + 0.5, (double) y, z + 0.5));
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		});
		context.waitTicks(30);
		check(shown(context) > 0.99F && !broken(context), "with no pressure the shield is whole: " + shown(context));
		shot(context, "01_entero");

		// Five blows: 0.325 of 0.60, a little under half.
		hits(server, connection, 5);
		context.waitTicks(4);
		float half = shown(context);
		check(half > 0.35F && half < 0.6F && !broken(context), "five blows leave about half of it: " + half);
		shot(context, "02_medio");

		// Five more: the cap. It breaks, with its shards flying a moment later.
		hits(server, connection, 5);
		context.waitTicks(1);
		shot(context, "03_se_rompe");
		context.waitTicks(10);
		check(broken(context), "at the cap the shield is broken");
		shot(context, "04_roto");

		// Three seconds of waiting leave it broken; then the pressure drains and it comes back gradually.
		context.waitTicks(70);
		float early = shown(context);
		check(!broken(context) && early > 0.0F && early < 0.5F, "starting to drain it has left the broken state: " + early + " " + broken(context));
		shot(context, "05_recuperando_poco");
		context.waitTicks(30);
		float later = shown(context);
		check(later > early + 0.1F, "it fills gradually as the pressure drains: " + early + " -> " + later);
		shot(context, "06_recuperando_mas");
		context.waitTicks(60);
		check(shown(context) > 0.99F, "and is whole again: " + shown(context));
		shot(context, "07_de_nuevo_entero");

		// Hidden: with the interface off (F1), in creative, and with no armour and no pressure.
		hits(server, connection, 6);
		context.waitTicks(4);
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		context.waitTicks(3);
		shot(context, "08_oculto_f1");
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		server.runCommand("gamemode creative @a");
		context.waitTicks(10);
		shot(context, "09_oculto_creativo");
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		});
		context.waitTicks(200);
		shot(context, "10_sin_armadura_ni_presion");
	}
}
