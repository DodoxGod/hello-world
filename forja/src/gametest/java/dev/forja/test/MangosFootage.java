package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.client.ForgeScreen;
import dev.forja.combat.Grip;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.menu.ForgeMenu;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.part.PartVariant;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Heavy and light handles and bindings (combat/Grip) on screen, for Andy to look at (FORJA_SOLO=mangos): the
 * parts on the parts table with their tooltips, the star forging a heavy and a light axe with the trade in its
 * panel, the finished pieces' tooltips, and the tools in hand, first and third person.
 */
final class MangosFootage {
	private static final int GRID_X = 7;
	private static final int GRID_Y = 21;
	private static final int GRID_ROW = 15;
	private static final int GRID_COLUMNS = 12;
	private static final int GRID_CELL = 16;

	private MangosFootage() {
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.takeScreenshot(TestScreenshotOptions.of("mangos_" + name).disableCounterPrefix());
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	/**
	 * A wall of item frames, the clearest way to see the three side by side at a size: plain, heavy and light
	 * in three columns, a row a kind (pickaxe, axe, mace, sword, scythe), and under them the loose handles and
	 * bindings.
	 */
	private static void wall(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int wx = x + 30;
		int wz = z + 30;
		int top = y + 7;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:smooth_stone", wx - 1, y, wz, wx + 3, top, wz));
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			List<ForgeType> kinds = List.of(ForgeType.PICO, ForgeType.HACHA, ForgeType.MAZO, ForgeType.ESPADA, ForgeType.GUADANA);
			for (int row = 0; row < kinds.size(); row++) {
				ForgeType type = kinds.get(row);
				for (int col = 0; col < 3; col++) {
					PartVariant variant = col == 0 ? PartVariant.NORMAL : col == 1 ? PartVariant.PESADO : PartVariant.LIGERO;
					List<ForgeMaterial> materials = new ArrayList<>();
					for (PartType slot : type.slots) {
						materials.add(slot.role == PartType.Role.HEAD ? ForgeMaterial.HIERRO
							: variant == PartVariant.PESADO ? ForgeMaterial.ACERO
							: slot == PartType.ATADURA ? ForgeMaterial.CUERO : ForgeMaterial.MADERA);
					}
					frame(level, new BlockPos(wx + col, top - row, wz - 1), forged(type, materials, variant, variant));
				}
			}
			PartType[][] loose = {{PartType.MANGO, PartType.MANGO_PESADO, PartType.MANGO_LIGERO},
				{PartType.ATADURA, PartType.ATADURA_PESADA, PartType.ATADURA_LIGERA}};
			for (int row = 0; row < 2; row++) {
				for (int col = 0; col < 3; col++) {
					PartType part = loose[row][col];
					ForgeMaterial material = part.variant == PartVariant.PESADO ? ForgeMaterial.ACERO
						: part.base() == PartType.ATADURA ? ForgeMaterial.CUERO : ForgeMaterial.MADERA;
					frame(level, new BlockPos(wx + col, top - 5 - row, wz - 1), Assembler.createPart(part, material));
				}
			}
		});
		server.runCommand("time set noon");
		// Stood on a block of barrier in the air, with an empty hand.
		server.runOnServer(s -> connection.getServerPlayer().getInventory().setItem(8, ItemStack.EMPTY));
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(8));
		double[][] views = {{top - 2.6, 4.4}, {top - 6.6, 2.8}};
		String[] names = {"09_pared_herramientas", "10_pared_piezas"};
		for (int v = 0; v < 2; v++) {
			int feet = (int) Math.floor(views[v][0]);
			int standZ = (int) Math.floor(wz - views[v][1]);
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:barrier", wx + 1, feet - 1, standZ));
			tp(server, wx + 1.5, feet, standZ + 0.5, 0.0F, 0.0F);
			context.waitTicks(20);
			shot(context, names[v]);
		}
	}

	private static void frame(ServerLevel level, BlockPos pos, ItemStack stack) {
		net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(level, pos, net.minecraft.core.Direction.NORTH);
		frame.setItem(stack, false);
		frame.setInvisible(true);
		level.addFreshEntity(frame);
	}

	static ItemStack forged(ForgeType type, List<ForgeMaterial> materials, PartVariant handle, PartVariant binding) {
		List<PartVariant> variants = new ArrayList<>();
		for (PartType slot : type.slots) {
			variants.add(slot == PartType.MANGO ? handle : slot == PartType.ATADURA ? binding : PartVariant.NORMAL);
		}
		return Assembler.create(new ForgedParts(type, materials, variants));
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		BlockPos parts = new BlockPos(x, y, z + 2);
		BlockPos forge = new BlockPos(x + 1, y, z + 2);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_piezas", parts.getX(), parts.getY(), parts.getZ()));
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d forja:mesa_de_forja", forge.getX(), forge.getY(), forge.getZ()));
		tp(server, x + 1.0, y, z + 0.3, 0.0F, 30.0F);
		context.waitTicks(5);

		// The parts table: the four new shapes in the grid, with a blank template so they can be picked.
		open(context, server, connection, parts);
		server.runOnServer(s -> menu(connection).getSlot(ForgeMenu.TEMPLATE_SLOT).set(new ItemStack(ModItems.PLANTILLA)));
		context.waitTicks(10);
		shot(context, "01_mesa_de_piezas");
		for (PartType part : List.of(PartType.MANGO_PESADO, PartType.MANGO_LIGERO, PartType.ATADURA_PESADA, PartType.ATADURA_LIGERA)) {
			hoverPart(context, part);
			context.waitTicks(5);
			shot(context, "02_plantilla_" + part.id());
		}
		context.getInput().setCursorPos(0, 0);
		// Cut a light handle out of bamboo planks: the bench makes it.
		server.runOnServer(s -> {
			ForgeMenu menu = menu(connection);
			check(menu.clickMenuButton(connection.getServerPlayer(), PartType.MANGO_LIGERO.ordinal()), "a blank template takes the light handle");
			menu.getSlot(ForgeMenu.MATERIAL_SLOT).set(new ItemStack(net.minecraft.world.item.Items.BAMBOO_PLANKS, 4));
		});
		context.waitTicks(10);
		server.runOnServer(s -> {
			ItemStack cut = menu(connection).getSlot(ForgeMenu.PART_RESULT_SLOT).getItem();
			check(cut.getItem() == ModItems.part(PartType.MANGO_LIGERO), "bamboo cuts a light handle, got " + cut);
		});
		shot(context, "03_corta_mango_ligero");
		server.runOnServer(s -> connection.getServerPlayer().closeContainer());
		context.waitTicks(5);

		// The star: a heavy axe (steel counterweight, iron bands) and a light one (bone handle, leather wrap).
		PartVariant[][] builds = {{PartVariant.PESADO, PartVariant.PESADO}, {PartVariant.LIGERO, PartVariant.LIGERO}};
		ForgeMaterial[][] materials = {{ForgeMaterial.ACERO, ForgeMaterial.HIERRO}, {ForgeMaterial.HUESO, ForgeMaterial.CUERO}};
		for (int b = 0; b < builds.length; b++) {
			int which = b;
			open(context, server, connection, forge);
			server.runOnServer(s -> {
				ForgeMenu menu = menu(connection);
				menu.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.CABEZA_HACHA, ForgeMaterial.HIERRO));
				menu.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.of(PartType.MANGO, builds[which][0]), materials[which][0]));
				menu.getSlot(ForgeMenu.STAR_FIRST + 4).set(Assembler.createPart(PartType.of(PartType.ATADURA, builds[which][1]), materials[which][1]));
				check(menu.action() == ForgeMenu.Action.FORGE && Grip.handle(menu.forgePreview()) == builds[which][0],
					"the star previews the axe with its variants");
			});
			context.waitTicks(10);
			String name = builds[b][0].id();
			shot(context, "04_estrella_hacha_" + name);
			server.runOnServer(s -> check(menu(connection).clickMenuButton(connection.getServerPlayer(), ForgeMenu.BUTTON_FORGE), "it forges"));
			context.waitTicks(10);
			hover(context, ForgeMenu.CENTER_X + 8, ForgeMenu.CENTER_Y + 8);
			context.waitTicks(5);
			shot(context, "05_tooltip_hacha_" + name);
			context.getInput().setCursorPos(0, 0);
			server.runOnServer(s -> {
				ItemStack axe = menu(connection).getSlot(ForgeMenu.CENTER_SLOT).getItem();
				check(axe.has(ModComponents.PARTS) && Grip.binding(axe) == builds[which][1], "the forged axe keeps its binding: " + axe);
				menu(connection).getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
				connection.getServerPlayer().closeContainer();
			});
			context.waitTicks(5);
		}

		// In hand: the three maces and the three pickaxes, first and third person.
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			List<ForgeMaterial> heavyMace = List.of(ForgeMaterial.HIERRO, ForgeMaterial.ACERO, ForgeMaterial.HIERRO);
			List<ForgeMaterial> plainMace = List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.MADERA);
			List<ForgeMaterial> lightMace = List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO);
			player.getInventory().setItem(0, forged(ForgeType.MAZO, plainMace, PartVariant.NORMAL, PartVariant.NORMAL));
			player.getInventory().setItem(1, forged(ForgeType.MAZO, heavyMace, PartVariant.PESADO, PartVariant.PESADO));
			player.getInventory().setItem(2, forged(ForgeType.MAZO, lightMace, PartVariant.LIGERO, PartVariant.LIGERO));
			player.getInventory().setItem(3, forged(ForgeType.PICO, plainMace, PartVariant.NORMAL, PartVariant.NORMAL));
			player.getInventory().setItem(4, forged(ForgeType.PICO, heavyMace, PartVariant.PESADO, PartVariant.PESADO));
			player.getInventory().setItem(5, forged(ForgeType.PICO, lightMace, PartVariant.LIGERO, PartVariant.LIGERO));
			player.getInventory().setItem(6, forged(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.ACERO, ForgeMaterial.HIERRO),
				PartVariant.PESADO, PartVariant.NORMAL));
			player.getInventory().setItem(7, forged(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.HUESO, ForgeMaterial.HIERRO),
				PartVariant.LIGERO, PartVariant.NORMAL));
			player.getInventory().setItem(8, Assembler.createPart(PartType.MANGO_PESADO, ForgeMaterial.HIERRO));
			player.getInventory().setItem(9, Assembler.createPart(PartType.MANGO_LIGERO, ForgeMaterial.MADERA));
			player.getInventory().setItem(10, Assembler.createPart(PartType.ATADURA_PESADA, ForgeMaterial.HIERRO));
			player.getInventory().setItem(11, Assembler.createPart(PartType.ATADURA_LIGERA, ForgeMaterial.CUERO));
			player.getInventory().setItem(12, Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA));
			player.getInventory().setItem(13, Assembler.createPart(PartType.ATADURA, ForgeMaterial.CUERO));
			ItemStack mould = dev.forja.item.CastingMouldItem.of(PartType.MANGO_PESADO);
			player.getInventory().setItem(14, mould);
			ItemStack template = new ItemStack(ModItems.PLANTILLA);
			dev.forja.item.TemplateItem.engrave(template, PartType.ATADURA_PESADA);
			player.getInventory().setItem(15, template);
		});
		tp(server, x + 0.5, y, z - 3.5, 180.0F, 10.0F);
		String[] held = {"mazo_normal", "mazo_pesado", "mazo_ligero", "pico_normal", "pico_pesado", "pico_ligero", "espada_pesada", "espada_ligera"};
		for (int slot = 0; slot < held.length; slot++) {
			int selected = slot;
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(selected));
			context.waitTicks(12);
			shot(context, String.format(Locale.ROOT, "06_mano_%d_%s", slot, held[slot]));
		}
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		for (int slot : new int[] {0, 1, 2}) {
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(slot));
			context.waitTicks(12);
			shot(context, "07_tercera_" + held[slot]);
		}
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		wall(context, server, connection, x, y, z);
		// The inventory: every new part and piece side by side.
		context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
		context.waitTicks(10);
		shot(context, "08_inventario");
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(5);
	}
}
