package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.RepairKits;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

/**
 * FORJA_SOLO=oricalco: oricalco as a forge material (docs/HERRERO_DIMENSION.md, 1.4) as a player sees it. Every part
 * cast in it, in a large chest, with one part's tooltip; every forged item in it, with the pickaxe's and the
 * chestplate's tooltips (the Astral trait, the set bonus); and a full suit worn with an oricalco sword in hand, seen
 * from the front and from behind, and sword and shield in first person. tools/hoja_oricalco.py makes the contact sheet.
 */
final class OricalcoFootage {
	private static final int PANEL_W = 176;
	private static final int PANEL_H = 222;

	private OricalcoFootage() {
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		ForgeMaterial oricalco = ForgeMaterial.ORICALCO;

		// 1. Every part, cast in oricalco.
		List<ItemStack> parts = new ArrayList<>();
		for (PartType part : PartType.values()) {
			if (part.accepts(oricalco)) {
				parts.add(Assembler.createPart(part, oricalco));
			}
		}
		check(parts.size() == PartType.values().length, "todas las piezas deberian aceptar oricalco: " + parts.size() + " de " + PartType.values().length);
		chest(context, server, connection, "Oricalco: piezas", parts);
		shot(context, "oricalco_01_piezas");
		hover(context, 0);
		shot(context, "oricalco_02_cabeza_de_pico_tooltip");
		hover(context, PartType.PLACA_PECHERA.ordinal());
		shot(context, "oricalco_03_placa_tooltip");

		// 2. Every forged item, all in oricalco, with its kit, its ingot and its pearl.
		List<ItemStack> forged = server.computeOnServer(s -> {
			List<ItemStack> made = new ArrayList<>();
			for (ForgeType type : ForgeType.values()) {
				List<ForgeMaterial> all = new ArrayList<>();
				for (int slot = 0; slot < type.slots.size(); slot++) {
					all.add(oricalco);
				}
				made.add(Assembler.create(type, all, s.registryAccess()));
			}
			// A pickaxe with a star iron handle: the Astral trait doubled.
			made.add(Assembler.create(ForgeType.PICO, List.of(oricalco, ForgeMaterial.ESTELAR, oricalco), s.registryAccess()));
			made.add(new ItemStack(RepairKits.kit(oricalco)));
			made.add(new ItemStack(ModItems.ORICALCO, 16));
			made.add(new ItemStack(ModItems.PERLA_DE_ORICALCO));
			return made;
		});
		chest(context, server, connection, "Oricalco: forjado", forged);
		shot(context, "oricalco_04_objetos");
		hover(context, ForgeType.PICO.ordinal());
		shot(context, "oricalco_05_pico_tooltip");
		hover(context, ForgeType.PECHERA.ordinal());
		shot(context, "oricalco_06_pechera_tooltip");
		hover(context, ForgeType.values().length);
		shot(context, "oricalco_07_pico_con_hierro_estelar_tooltip");
		hover(context, ForgeType.values().length + 1);
		shot(context, "oricalco_08_kit_tooltip");

		// 3. Worn: a full suit and an oricalco sword, from the front and from behind; then the shield too, in first person.
		context.getInput().setCursorPos(0, 0);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.closeContainer();
			player.getInventory().clearContent();
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ForgeType type = switch (slot) {
					case HEAD -> ForgeType.CASCO;
					case CHEST -> ForgeType.PECHERA;
					case LEGS -> ForgeType.GREBAS;
					default -> ForgeType.BOTAS;
				};
				player.setItemSlot(slot, Assembler.create(type, List.of(oricalco, oricalco), s.registryAccess()));
			}
			player.setItemInHand(InteractionHand.MAIN_HAND, Assembler.create(ForgeType.ESPADA, List.of(oricalco, oricalco, oricalco), s.registryAccess()));
		});
		check(server.computeOnServer(s -> dev.forja.upgrade.ArmorSets.fullSet(connection.getServerPlayer()) == oricalco),
			"el conjunto puesto deberia ser de oricalco");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		tp(server, x + 0.5, y, z + 0.5, 180.0F, 15.0F);
		context.waitTicks(30);
		shot(context, "oricalco_09_puesto_de_frente");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(20);
		shot(context, "oricalco_10_puesto_de_espaldas");
		// First person, with an oricalco shield in the other hand.
		server.runOnServer(s -> connection.getServerPlayer().setItemInHand(InteractionHand.OFF_HAND,
			Assembler.create(ForgeType.ESCUDO, List.of(oricalco, oricalco, oricalco), s.registryAccess())));
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		tp(server, x + 0.5, y, z + 0.5, 180.0F, 20.0F);
		context.waitTicks(20);
		shot(context, "oricalco_11_espada_en_mano");
	}

	/** A large chest's screen opened for the player with these stacks in it, the inventory empty. */
	private static void chest(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String title,
		List<ItemStack> stacks) {
		context.getInput().setCursorPos(0, 0);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.closeContainer();
			player.getInventory().clearContent();
			SimpleContainer box = new SimpleContainer(54);
			for (int i = 0; i < stacks.size() && i < 54; i++) {
				box.setItem(i, stacks.get(i).copy());
			}
			player.openMenu(new SimpleMenuProvider((id, inventory, who) -> ChestMenu.sixRows(id, inventory, box), Component.literal(title)));
		});
		context.waitTicks(10);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen),
			"el cofre deberia estar abierto");
	}

	/** The mouse over chest slot {@code index} (nine to a row, from the panel's (8, 18)). */
	private static void hover(ClientGameTestContext context, int index) {
		int u = 8 + index % 9 * 18;
		int v = 18 + index / 9 * 18;
		double[] point = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			double left = (mc.getWindow().getGuiScaledWidth() - PANEL_W) / 2;
			double top = (mc.getWindow().getGuiScaledHeight() - PANEL_H) / 2;
			return new double[] {(left + u + 8) * scale, (top + v + 8) * scale};
		});
		context.getInput().setCursorPos(point[0], point[1]);
		context.waitTicks(5);
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
		log("oricalco: captura " + name);
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
