package dev.forja.test;

import java.util.List;
import java.util.Locale;

import dev.forja.client.ForgeScreen;
import dev.forja.combat.Grip;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.menu.ForgeMenu;
import dev.forja.part.PartType;
import dev.forja.part.PartVariant;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Every material makes every handle and binding (Andy, 2026-09-30), on screen (FORJA_SOLO=mangos_todos): the
 * parts table previewing the heavy and light shapes in oak and in bone, the star with a light netherite handle and a
 * heavy oak one with their tooltips, a wall of loose handles and bindings a material a row, and a few of them in hand.
 */
final class MangosTodosFootage {
	private static final int GRID_X = 7;
	private static final int GRID_Y = 21;
	private static final int GRID_ROW = 15;
	private static final int GRID_COLUMNS = 12;
	private static final int GRID_CELL = 16;

	private MangosTodosFootage() {
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.takeScreenshot(TestScreenshotOptions.of("mangos_todos_" + name).disableCounterPrefix());
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private static ForgeMenu menu(TestServerConnection connection) {
		return (ForgeMenu) connection.getServerPlayer().containerMenu;
	}

	private static void open(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos table) {
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ServerLevel level = connection.getServerLevel();
			player.openMenu(level.getBlockState(table).getMenuProvider(level, table));
		});
		context.waitForScreen(ForgeScreen.class);
	}

	private static void hover(ClientGameTestContext context, int panelX, int panelY) {
		double[] point = context.computeOnClient(mc -> {
			double[] gui = ((ForgeScreen) mc.gui.screen()).guiPoint(panelX, panelY);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(point[0], point[1]);
	}

	private static void hoverPart(ClientGameTestContext context, PartType part) {
		int i = part.ordinal();
		hover(context, GRID_X + i % GRID_COLUMNS * GRID_CELL + 8, GRID_Y + i / GRID_COLUMNS * GRID_ROW + 8);
	}

	/** Points at a slot of the player's own inventory, as the forge screen shows it. */
	private static void hoverInventory(ClientGameTestContext context, int inventorySlot) {
		int[] at = context.computeOnClient(mc -> {
			for (var slot : ((ForgeScreen) mc.gui.screen()).getMenu().slots) {
				if (slot.container instanceof net.minecraft.world.entity.player.Inventory && slot.getContainerSlot() == inventorySlot) {
					return new int[] {slot.x, slot.y};
				}
			}
			throw new AssertionError("no inventory slot " + inventorySlot);
		});
		hover(context, at[0] + 8, at[1] + 8);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	/** The materials of the wall, a row each: woods and bone, the metals light to heavy, a crystal and glass steel. */
	private static final List<ForgeMaterial> WALL = List.of(ForgeMaterial.MADERA, ForgeMaterial.HUESO, ForgeMaterial.ORO, ForgeMaterial.HIERRO,
		ForgeMaterial.NETHERITA, ForgeMaterial.AMATISTA, ForgeMaterial.VIDRIACERO);
	private static final List<PartType> WALL_PARTS = List.of(PartType.MANGO, PartType.MANGO_PESADO, PartType.MANGO_LIGERO,
		PartType.ATADURA, PartType.ATADURA_PESADA, PartType.ATADURA_LIGERA);

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		BlockPos parts = new BlockPos(x, y, z + 2);
		BlockPos forge = new BlockPos(x + 1, y, z + 2);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_piezas", parts.getX(), parts.getY(), parts.getZ()));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_forja", forge.getX(), forge.getY(), forge.getZ()));
		tp(server, x + 1.0, y, z + 0.3, 0.0F, 30.0F);
		context.waitTicks(5);

		// The parts table: oak in the material slot, so the grid shows every part in oak, the heavy handle too;
		// then bone, and the heavy binding's tooltip says what it weighs in bone.
		open(context, server, connection, parts);
		Object[][] cuts = {{Items.OAK_PLANKS, PartType.MANGO_PESADO, ForgeMaterial.MADERA}, {Items.BONE, PartType.ATADURA_PESADA, ForgeMaterial.HUESO},
			{Items.AMETHYST_SHARD, PartType.MANGO_LIGERO, ForgeMaterial.AMATISTA}};
		for (Object[] cut : cuts) {
			PartType part = (PartType) cut[1];
			server.runOnServer(s -> {
				ForgeMenu menu = menu(connection);
				menu.getSlot(ForgeMenu.TEMPLATE_SLOT).set(new ItemStack(ModItems.PLANTILLA));
				menu.getSlot(ForgeMenu.MATERIAL_SLOT).set(new ItemStack((net.minecraft.world.item.Item) cut[0], 8));
				check(menu.clickMenuButton(connection.getServerPlayer(), part.ordinal()), "a blank template takes " + part);
			});
			context.waitTicks(10);
			server.runOnServer(s -> {
				ItemStack made = menu(connection).getSlot(ForgeMenu.PART_RESULT_SLOT).getItem();
				check(made.getItem() == ModItems.part(part) && made.get(ModComponents.MATERIAL) == cut[2], "the bench cuts " + part + " of " + cut[2] + ", got " + made);
			});
			hoverPart(context, part);
			context.waitTicks(5);
			shot(context, "01_mesa_" + part.id() + "_" + ((ForgeMaterial) cut[2]).getSerializedName());
			context.getInput().setCursorPos(0, 0);
			server.runOnServer(s -> {
				ForgeMenu menu = menu(connection);
				menu.getSlot(ForgeMenu.MATERIAL_SLOT).set(ItemStack.EMPTY);
				menu.getSlot(ForgeMenu.TEMPLATE_SLOT).set(ItemStack.EMPTY);
			});
			context.waitTicks(2);
		}
		server.runOnServer(s -> connection.getServerPlayer().closeContainer());
		context.waitTicks(5);

		// The star: a sword with a light netherite handle, and an axe with a heavy oak handle and a light gold binding.
		Object[][] builds = {
			{ForgeType.ESPADA, List.of(Assembler.createPart(PartType.HOJA, ForgeMaterial.HIERRO), Assembler.createPart(PartType.MANGO_LIGERO, ForgeMaterial.NETHERITA),
				Assembler.createPart(PartType.GUARDA, ForgeMaterial.HIERRO)), "espada_ligera_netherita"},
			{ForgeType.HACHA, List.of(Assembler.createPart(PartType.CABEZA_HACHA, ForgeMaterial.HIERRO), Assembler.createPart(PartType.MANGO_PESADO, ForgeMaterial.MADERA),
				Assembler.createPart(PartType.ATADURA_LIGERA, ForgeMaterial.ORO)), "hacha_pesada_roble"}};
		for (Object[] build : builds) {
			@SuppressWarnings("unchecked")
			List<ItemStack> loose = (List<ItemStack>) build[1];
			String name = (String) build[2];
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				player.getInventory().clearContent();
				player.getInventory().setItem(9, loose.get(1).copy());
			});
			open(context, server, connection, forge);
			server.runOnServer(s -> {
				ForgeMenu menu = menu(connection);
				menu.getSlot(ForgeMenu.STAR_FIRST).set(loose.get(0).copy());
				menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(loose.get(1).copy());
				menu.getSlot(ForgeMenu.STAR_FIRST + 4).set(loose.get(2).copy());
				check(menu.action() == ForgeMenu.Action.FORGE && menu.forgePreview().get(ModComponents.PARTS).type() == build[0],
					"the star previews the " + build[0]);
			});
			context.waitTicks(10);
			// The loose handle's own tooltip (a copy in the inventory): its material, what it weighs, its trade.
			hoverInventory(context, 9);
			context.waitTicks(5);
			shot(context, "02_estrella_pieza_" + name);
			context.getInput().setCursorPos(0, 0);
			server.runOnServer(s -> check(menu(connection).clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE), "it forges"));
			context.waitTicks(10);
			hover(context, ForgeMenu.CENTER_X + 8, ForgeMenu.CENTER_Y + 8);
			context.waitTicks(5);
			shot(context, "03_estrella_arma_" + name);
			context.getInput().setCursorPos(0, 0);
			server.runOnServer(s -> {
				ItemStack made = menu(connection).getSlot(ForgeMenu.CENTER_SLOT).getItem();
				check(made.has(ModComponents.PARTS) && Grip.handle(made) != PartVariant.NORMAL, "the forged piece keeps its handle: " + made);
				menu(connection).getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
				connection.getServerPlayer().closeContainer();
			});
			context.waitTicks(5);
		}

		// In hand: a few pieces of mixed materials and shapes.
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			player.getInventory().setItem(0, MangosFootage.forged(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.NETHERITA, ForgeMaterial.HIERRO),
				PartVariant.LIGERO, PartVariant.NORMAL));
			player.getInventory().setItem(1, MangosFootage.forged(ForgeType.HACHA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.ORO),
				PartVariant.PESADO, PartVariant.LIGERO));
			player.getInventory().setItem(2, MangosFootage.forged(ForgeType.MAZO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.AMATISTA, ForgeMaterial.NETHERITA),
				PartVariant.PESADO, PartVariant.PESADO));
			player.getInventory().setItem(3, MangosFootage.forged(ForgeType.PICO, List.of(ForgeMaterial.DIAMANTE, ForgeMaterial.ORO, ForgeMaterial.HUESO),
				PartVariant.LIGERO, PartVariant.LIGERO));
		});
		tp(server, x + 0.5, y, z - 3.5, 180.0F, 10.0F);
		String[] held = {"espada_ligera_netherita", "hacha_pesada_roble_atadura_oro", "mazo_pesado_amatista_netherita", "pico_ligero_oro_hueso"};
		// Seen from the front, where the handle and the binding show at a size; first person shows little more than a corner.
		context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
		for (int slot = 0; slot < held.length; slot++) {
			int selected = slot;
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(selected));
			context.waitTicks(12);
			shot(context, String.format(Locale.ROOT, "04_mano_%d_%s", slot, held[slot]));
		}
		context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		wall(context, server, connection, x, y, z);
	}

	/** A wall of item frames: a row a material (WALL), a column a part (WALL_PARTS), the colour of each its material's. */
	private static void wall(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int wx = x + 30;
		int wz = z + 30;
		int top = y + 8;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:smooth_stone", wx - 1, y, wz, wx + WALL_PARTS.size(), top, wz));
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (int row = 0; row < WALL.size(); row++) {
				for (int col = 0; col < WALL_PARTS.size(); col++) {
					net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(level,
						new BlockPos(wx + WALL_PARTS.size() - 1 - col, top - row, wz - 1), net.minecraft.core.Direction.NORTH);
					frame.setItem(Assembler.createPart(WALL_PARTS.get(col), WALL.get(row)), false);
					frame.setInvisible(true);
					level.addFreshEntity(frame);
				}
			}
		});
		server.runCommand("time set noon");
		server.runOnServer(s -> connection.getServerPlayer().getInventory().setItem(8, ItemStack.EMPTY));
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(8));
		int feet = top - 6;
		int standZ = wz - 11;
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:barrier", wx + 2, feet - 1, standZ));
		tp(server, wx + 3.0, feet, standZ + 0.5, 0.0F, -10.0F);
		// The frames face north, so the columns are laid right to left and the plain handle reads first. Stood back and
		// looking a little up, so the bottom row clears the hotbar.
		context.waitTicks(20);
		shot(context, "05_pared_materiales");
	}
}
