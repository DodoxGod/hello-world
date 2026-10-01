package dev.forja.test;

import java.util.List;
import java.util.Locale;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModComponents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The HUD with everything of the mod's on it at once (FORJA_SOLO=hud_carriles; the visual pass of 2026-10-01):
 * armour on and a second row of hearts from absorption, wings half spent, a full frenzy, mana and stamina short of
 * full, the integrity shield, and the class skills waiting: where bars that picked their own heights used to land
 * on each other and on vanilla's.
 *
 * <p>It checks, from the client, that no two of the mod's bars share a row and none is on vanilla's stack
 * (client/HudLayout's lanes), nor vanilla's held-item name and action-bar message, at the heights of GUI scales 2 to 4.
 */
final class VisualGuiFootage {
	private VisualGuiFootage() {
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runCommand("difficulty peaceful");
		server.runCommand("gamemode survival @a");
		int px = x + 140;
		int pz = z + 60;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d smooth_stone", px - 8, y - 1, pz - 8, px + 8, y - 1, pz + 8));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", px - 8, y, pz - 8, px + 8, y + 5, pz + 8));
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.teleportTo(px + 0.5, y, pz + 0.5);
			player.setYRot(0.0F);
			player.setXRot(20.0F);
		});
		context.waitTicks(20);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			var level = connection.getServerLevel();
			player.getInventory().clearContent();
			player.getInventory().setItem(0, Assembler.create(ForgeType.BACULO, List.of(ForgeMaterial.AMATISTA, ForgeMaterial.HIERRO, ForgeMaterial.MADERA), level.registryAccess()));
			player.getInventory().setSelectedSlot(0);
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			ItemStack wings = Assembler.create(ForgeType.ALAS, List.of(ForgeMaterial.ORO, ForgeMaterial.CUERO), level.registryAccess());
			wings.set(ModComponents.VUELO, Math.max(1, dev.forja.forge.Flight.maxTicks(wings) / 2));
			player.setItemSlot(EquipmentSlot.CHEST, wings);
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 20 * 120, 2, false, false));
			dev.forja.clase.ClassProgress.choose(player, dev.forja.clase.PlayerClass.GUERRERO);
			dev.forja.clase.ClassProgress.setLevel(player, 30);
			ArbolGameTests.learnTo(player, "guerrero.b.tronco_4");
			ArbolGameTests.learnTo(player, "guerrero.s1.2");
		});
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			for (int blow = 0; blow < dev.forja.upgrade.Frenzy.MAX_HITS; blow++) {
				dev.forja.upgrade.Frenzy.onHit(player);
			}
			dev.forja.magic.Mana.set(player, dev.forja.magic.Mana.max(player) * 0.55F);
			dev.forja.combat.Stamina.set(player, dev.forja.combat.Stamina.max(player) * 0.5F);
			dev.forja.clase.ClassSkills.use(player, 1, true);
		});
		context.waitTicks(6);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
			// Vanilla's two lines over the hotbar, both up: the action-bar message, and the held item's name.
			mc.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal("Grito de guerra II"), false);
			mc.player.getInventory().setSelectedSlot(1);
		});
		context.waitTicks(2);
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		context.waitTicks(3);
		// The lanes, checked at the heights a 1080p screen has at GUI scales 2, 3 and 4 and at the test window's own.
		// (The window itself is not resized: under Vulkan a resize in the test loses the device.)
		for (int height : new int[] {540, 360, 270, 240}) {
			String lanes = context.computeOnClient(mc -> laneProblems(height, mc.player, mc.gameMode.canHurtPlayer()));
			ClientLog.log("hud_carriles alto " + height + ": " + lanes);
			if (!lanes.startsWith("ok")) {
				throw new AssertionError("the HUD lanes should clear each other, vanilla's stack and vanilla's text at a GUI height of " + height + ": " + lanes);
			}
		}
		context.takeScreenshot(TestScreenshotOptions.of("vgui_hud_todo").disableCounterPrefix());
		// The second skill set going too, so two wait side by side.
		server.runOnServer(s -> dev.forja.clase.ClassSkills.use(connection.getServerPlayer(), 2, true));
		context.waitTicks(4);
		context.takeScreenshot(TestScreenshotOptions.of("vgui_hud_habilidades_esperando").disableCounterPrefix());
		// The event card, settled, for two events of very different colours.
		for (dev.forja.world.WorldEvents event : new dev.forja.world.WorldEvents[] {dev.forja.world.WorldEvents.METEORITOS, dev.forja.world.WorldEvents.LUNA_DE_SANGRE}) {
			context.runOnClient(mc -> dev.forja.client.EventBannerHud.show(event, true));
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("vgui_cartel_" + event.name().toLowerCase(Locale.ROOT)).disableCounterPrefix());
		}
		context.runOnClient(mc -> dev.forja.client.EventBannerHud.dismiss());
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.removeAllEffects();
			player.getInventory().clearContent();
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		});
	}

	/**
	 * "ok" and the lanes, or what is wrong with them: each bar's reach (its caps two pixels over its top and two
	 * under its foot) has to end above the next lane's, and the lowest above vanilla's stack — the armour row,
	 * ten pixels over the highest row of hearts, counted the way vanilla counts it. And vanilla's two lines of text
	 * over the hotbar, where vanilla writes them (the held item's name 59 up on a two-pixel backdrop, the action-bar
	 * message centred 68 up) and lifted by HudLayout.textLift, have to end above the top of the bars showing.
	 */
	private static String laneProblems(int height, net.minecraft.client.player.LocalPlayer player, boolean canBeHurt) {
		float maxHealth = Math.max((float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH), player.getHealth());
		int rows = net.minecraft.util.Mth.ceil((maxHealth + net.minecraft.util.Mth.ceil(player.getAbsorptionAmount())) / 20.0F);
		int armourRow = height - 39 - (rows - 1) * Math.max(10 - (rows - 2), 3) - 10;
		int side = dev.forja.client.HudLayout.sideY(height, player, 3);
		int flight = dev.forja.client.HudLayout.flightY(height, player, 5);
		int frenzy = dev.forja.client.HudLayout.frenzyY(height, player, 5);
		String lanes = String.format(Locale.ROOT, "alto %d, filas de corazones %d, fila de armadura %d, mana/estamina %d, alas %d, frenesi %d",
			height, rows, armourRow, side, flight, frenzy);
		StringBuilder wrong = new StringBuilder();
		if (side + 3 + 2 >= armourRow) {
			wrong.append("mana/estamina sobre la armadura; ");
		}
		if (flight + 5 + 2 >= side - 2) {
			wrong.append("alas sobre mana/estamina; ");
		}
		if (frenzy + 5 + 2 >= flight - 2) {
			wrong.append("frenesi sobre alas; ");
		}
		if (frenzy - 2 < 0) {
			wrong.append("frenesi fuera de la pantalla; ");
		}
		int top = dev.forja.client.HudLayout.lanesTop(height, player);
		int lift = dev.forja.client.HudLayout.textLift(height, player, canBeHurt);
		int itemTop = height - 59 + (canBeHurt ? 0 : 14) - lift - 2;
		int itemFoot = itemTop + 13;
		int messageTop = height - 68 - lift - 6;
		int messageFoot = messageTop + 13;
		lanes += String.format(Locale.ROOT, ", barras desde %d, texto subido %d: nombre %d-%d, mensaje %d-%d", top, lift, itemTop, itemFoot, messageTop, messageFoot);
		if (top < 0) {
			wrong.append("no se ve ninguna barra; ");
		} else {
			if (itemFoot > top - 1) {
				wrong.append("nombre del objeto sobre las barras; ");
			}
			if (messageFoot > top - 1) {
				wrong.append("mensaje de la barra de accion sobre las barras; ");
			}
		}
		if (messageTop < 0) {
			wrong.append("mensaje fuera de la pantalla; ");
		}
		return (wrong.length() == 0 ? "ok, " : "MAL: " + wrong) + lanes;
	}

	/** The client test's log line, under the same prefix as ForjaClientTest's. */
	private static final class ClientLog {
		static void log(String line) {
			System.out.println("[forja-test] " + line);
		}
	}
}
