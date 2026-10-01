package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.forge.Alloys;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.RepairKits;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * FORJA_SOLO=kits: the new ingots (tools/lingotes.py, variant A) and the repair kits (variant B, forge/RepairKits) as a
 * player sees them, in a vanilla crafting table: every ingot in the inventory with a kit's own recipe in the grid; every kit
 * in the inventory with a kit and a worn steel pickaxe in the grid and the mended pickaxe out; the tooltips of a kit and
 * of the pickaxe before and after; and a kit of the wrong metal, which gives nothing.
 */
final class KitsFootage {
	private KitsFootage() {
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		BlockPos table = new BlockPos(x + 2, y, z);
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runOnServer(s -> connection.getServerLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState()));
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});

		List<ItemStack> ingots = new ArrayList<>();
		for (Alloys.Recipe recipe : Alloys.ALL) {
			ingots.add(new ItemStack(ModItems.alloy(recipe.id())));
		}
		ingots.add(new ItemStack(ModItems.ORICALCO));
		ingots.add(new ItemStack(ModItems.LINGOTE_DE_TEMPLE));
		List<ItemStack> kits = new ArrayList<>();
		for (ForgeMaterial material : RepairKits.materials()) {
			kits.add(new ItemStack(RepairKits.kit(material)));
		}
		check(kits.size() >= 19, "deberia haber al menos 19 kits, hay " + kits.size());

		// 1. Every ingot, and a steel kit's recipe: two steel ingots, a leather and a string.
		open(context, server, connection, table, ingots, List.of(
			new ItemStack(ModItems.alloy("acero")), new ItemStack(ModItems.alloy("acero")), ItemStack.EMPTY,
			new ItemStack(Items.LEATHER), new ItemStack(Items.STRING)));
		ItemStack made = result(server, connection);
		check(made.is(RepairKits.kit(ForgeMaterial.ACERO)), "dos lingotes de acero, cuero y cuerda deberian dar el kit de acero, dan " + made);
		shot(context, "kits_01_lingotes_y_receta");
		hover(context, 124, 35);
		shot(context, "kits_02_kit_de_acero_tooltip");

		// 2. Every kit, and a steel kit with a worn steel pickaxe: the pickaxe comes out with 300 more.
		ItemStack pick = server.computeOnServer(s -> Assembler.create(ForgeType.PICO,
			List.of(ForgeMaterial.ACERO, ForgeMaterial.MADERA, ForgeMaterial.CUERO), s.registryAccess()));
		int max = pick.getMaxDamage();
		pick.setDamageValue(max - 40);
		open(context, server, connection, table, kits, List.of(new ItemStack(RepairKits.kit(ForgeMaterial.ACERO)), ItemStack.EMPTY,
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, pick));
		ItemStack mended = result(server, connection);
		check(mended.getMaxDamage() - mended.getDamageValue() == 340, "el pico deberia salir con 340 de uso, sale con "
			+ (mended.getMaxDamage() - mended.getDamageValue()));
		shot(context, "kits_03_kits_y_reparacion");
		hover(context, 30 + 2 * 18, 17 + 18);
		shot(context, "kits_04_pico_antes");
		hover(context, 124, 35);
		shot(context, "kits_05_pico_despues");
		hover(context, 30, 17);
		shot(context, "kits_06_kit_tooltip");

		// 3. The iron kit on the steel pickaxe: its main part is steel, so nothing.
		open(context, server, connection, table, kits, List.of(new ItemStack(RepairKits.kit(ForgeMaterial.HIERRO)), ItemStack.EMPTY,
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, pick));
		check(result(server, connection).isEmpty(), "el kit de hierro no deberia reparar un pico de acero");
		shot(context, "kits_07_otro_metal_nada");

		context.getInput().setCursorPos(0, 0);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		server.runOnServer(s -> connection.getServerPlayer().closeContainer());
	}

	/** The crafting table opened for the player, the inventory filled with {@code inventory} and the grid with {@code grid}. */
	private static void open(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos table,
		List<ItemStack> inventory, List<ItemStack> grid) {
		context.getInput().setCursorPos(0, 0);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.closeContainer();
			player.getInventory().clearContent();
			// The hotbar last, so the first nine fill the main rows top-left first, as a chest would show them.
			for (int i = 0; i < inventory.size() && i < 36; i++) {
				player.getInventory().setItem(i < 27 ? 9 + i : i - 27, inventory.get(i).copy());
			}
			var level = connection.getServerLevel();
			player.teleportTo(table.getX() + 0.5, table.getY(), table.getZ() - 1.5);
			player.openMenu(level.getBlockState(table).getMenuProvider(level, table));
		});
		context.waitTicks(5);
		server.runOnServer(s -> {
			if (connection.getServerPlayer().containerMenu instanceof CraftingMenu menu) {
				for (int i = 0; i < grid.size(); i++) {
					menu.getSlot(1 + i).set(grid.get(i).copy());
				}
				menu.slotsChanged(menu.getSlot(1).container);
				menu.broadcastChanges();
			}
		});
		context.waitTicks(10);
		// A screen that opens puts the mouse in the middle of the window, over some slot: move it off the panel.
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen),
			"la mesa de crafteo deberia estar abierta");
	}

	private static ItemStack result(TestServerContext server, TestServerConnection connection) {
		return server.computeOnServer(s -> connection.getServerPlayer().containerMenu.getSlot(0).getItem().copy());
	}

	/** The mouse over the slot whose top-left corner is (u, v) inside the crafting screen (176 by 166, centred). */
	private static void hover(ClientGameTestContext context, int u, int v) {
		double[] point = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			double left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
			double top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			return new double[] {(left + u + 8) * scale, (top + v + 8) * scale};
		});
		context.getInput().setCursorPos(point[0], point[1]);
		context.waitTicks(5);
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(960, 540));
		log("kits: captura " + name);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void log(String message) {
		System.out.println("[forja-test] " + String.format(Locale.ROOT, "%s", message));
	}
}
