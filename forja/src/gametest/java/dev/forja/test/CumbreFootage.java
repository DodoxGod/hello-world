package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.GuideBooks;
import dev.forja.block.CrucibleBlock;
import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.block.entity.MeltTankBlockEntity;
import dev.forja.client.GuideBookScreen;
import dev.forja.forge.Alloys;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.RepairKits;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.ArmorSets;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * FORJA_SOLO=cumbre: the middle tier and the peak alloys (docs/ALEACIONES_CUMBRE.md, 4.3) as a player sees them. The
 * seven ingots and their repair kits in a crafting table; the obsidian crucible with two tanks pouring the aegis; the
 * four middle-tier sets on armour stands in a row, each with its sword in a frame; each peak set, from the front and from
 * behind, with its sword and pickaxe in frames; a sword of wrathsteel and a shield of aegis in first person with their
 * particles; and book III open at "Aleaciones cumbre". JEI is compile-only in the test run, so its picture is left out
 * (said in the log). tools/hoja_cumbre.py makes the contact sheet.
 */
final class CumbreFootage {
	private static final String[] MIDDLE = {"espectracero", "corazon_de_volcan", "eclipse", "astralita"};
	private static final String[] PEAK = {"iracero", "egida", "arcanio"};
	private static final ForgeType[] PIECES = {ForgeType.CASCO, ForgeType.PECHERA, ForgeType.GREBAS, ForgeType.BOTAS};
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private CumbreFootage() {
	}

	private static ForgeMaterial material(String id) {
		return ForgeMaterial.valueOf(id.toUpperCase(Locale.ROOT));
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runCommand("weather clear 1000000");
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("gamerule spawn_monsters false");
		quiet(context);

		// 1. The seven ingots, and their repair kits, in a crafting table.
		BlockPos table = new BlockPos(x + 2, y, z);
		server.runOnServer(s -> connection.getServerLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState()));
		List<ItemStack> bag = new ArrayList<>();
		for (String id : MIDDLE) {
			bag.add(new ItemStack(ModItems.alloy(id), 16));
		}
		for (String id : PEAK) {
			bag.add(new ItemStack(ModItems.alloy(id), 8));
		}
		for (String id : MIDDLE) {
			bag.add(new ItemStack(RepairKits.kit(material(id))));
		}
		for (String id : PEAK) {
			bag.add(new ItemStack(RepairKits.kit(material(id))));
		}
		bag.add(new ItemStack(ModItems.CORAZON_DE_FORJA));
		open(context, server, connection, table, bag, List.of(new ItemStack(ModItems.alloy("iracero")), new ItemStack(ModItems.alloy("iracero")),
			ItemStack.EMPTY, new ItemStack(Items.LEATHER), new ItemStack(Items.STRING)));
		ItemStack kit = server.computeOnServer(s -> connection.getServerPlayer().containerMenu.getSlot(0).getItem().copy());
		check(kit.is(RepairKits.kit(ForgeMaterial.IRACERO)), "dos lingotes de iracero, cuero y cuerda deberian dar su kit, dan " + kit);
		shot(context, "cumbre_01_lingotes");
		close(context, server, connection);

		// The arena for the rest: a flat stone floor, air over it, a good way off.
		int ax = x + 300;
		int az = z + 100;
		server.runCommand("gamemode spectator @a");
		tp(server, ax + 6.0, y + 4.0, az + 6.0, 0.0F, 30.0F);
		context.waitTicks(60);
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:smooth_stone", ax - 4, y - 1, az - 10, ax + 36, y - 1, az + 24));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", ax - 4, y, az - 10, ax + 36, y + 8, az + 24));
		context.waitTicks(10);

		// 2. The obsidian crucible, in line with two tanks: the heart and obsidian steel in the pot, spectresteel in a tank.
		BlockPos pot = new BlockPos(ax + 2, y, az + 8);
		boolean ready = server.computeOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			level.setBlockAndUpdate(pot, ModBlocks.CRISOL_DE_OBSIDIANA.defaultBlockState());
			level.setBlockAndUpdate(pot.east(), ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			level.setBlockAndUpdate(pot.west(), ModBlocks.CUBA_DE_COLADA.defaultBlockState());
			MeltTankBlockEntity spectre = (MeltTankBlockEntity) level.getBlockEntity(pot.east());
			spectre.fill(ModItems.alloy("espectracero"), 2);
			return level.getBlockEntity(pot) instanceof CrucibleBlockEntity && spectre != null;
		});
		check(ready, "el crisol y la cuba deberian existir");
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> connection.getServerPlayer().getInventory().clearContent());
		tp(server, pot.getX() + 0.5, y, pot.getZ() - 1.5, 0.0F, 30.0F);
		context.waitTicks(5);
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			connection.getServerPlayer().openMenu(level.getBlockState(pot).getMenuProvider(level, pot));
		});
		context.waitForScreen(dev.forja.client.CrucibleScreen.class);
		// Loaded with the screen open, and watched until the screen says it is pouring: that is the shot.
		server.runOnServer(s -> {
			CrucibleBlockEntity crucible = (CrucibleBlockEntity) connection.getServerLevel().getBlockEntity(pot);
			crucible.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(ModItems.CORAZON_DE_FORJA));
			crucible.setItem(CrucibleBlockEntity.SLOT_SECOND, new ItemStack(ModItems.alloy("obsidiacero"), 2));
			crucible.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 2));
		});
		int pouring = -1;
		for (int waited = 0; waited < CrucibleBlock.Tier.OBSIDIANA.cook + 40 && pouring < 0; waited += 2) {
			context.waitTicks(2);
			int job = context.computeOnClient(mc -> mc.player.containerMenu instanceof dev.forja.menu.CrucibleMenu menu ? menu.job() : -1);
			int[] held = server.computeOnServer(s -> {
				CrucibleBlockEntity crucible = (CrucibleBlockEntity) connection.getServerLevel().getBlockEntity(pot);
				return new int[] {crucible.getItem(CrucibleBlockEntity.SLOT_FIRST).getCount(), crucible.getItem(CrucibleBlockEntity.SLOT_SECOND).getCount()};
			});
			log("cumbre: crisol, " + waited + " ticks, la pantalla dice " + job + ", huecos " + held[0] + "/" + held[1]);
			if (job == CrucibleBlockEntity.JOB_ALLOY) {
				pouring = waited;
			}
		}
		check(pouring >= 0, "la pantalla del crisol deberia decir que cuela una aleacion");
		context.waitTicks(4);
		quiet(context);
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		shot(context, "cumbre_02_crisol");
		context.waitTicks(CrucibleBlock.Tier.OBSIDIANA.cook + 20);
		int[] poured = server.computeOnServer(s -> {
			MeltTankBlockEntity empty = (MeltTankBlockEntity) connection.getServerLevel().getBlockEntity(pot.west());
			return new int[] {empty.bankMetal() == ModItems.alloy("egida") ? empty.bankAmount() : -1};
		});
		check(poured[0] >= Alloys.ALL.stream().filter(recipe -> recipe.id().equals("egida")).findFirst().orElseThrow().output(),
			"el crisol deberia haber colado la egida en la cuba vacia: " + poured[0]);
		close(context, server, connection);

		// 3. The middle tier on stands in a row, a sword of each in a frame over it.
		server.runCommand("gamemode spectator @a");
		hideHud(context);
		int row = az - 6;
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (int i = 0; i < MIDDLE.length; i++) {
				int sx = ax + 3 * i;
				wall(level, sx - 1, y, row - 1, 3, 4);
				stand(level, material(MIDDLE[i]), sx + 0.5, y, row + 1.5, 0.0F, null);
				frame(level, new BlockPos(sx, y + 2, row), Direction.SOUTH, all(level, ForgeType.ESPADA, material(MIDDLE[i])));
			}
		});
		check(server.computeOnServer(s -> {
			for (ArmorStand stand : connection.getServerLevel().getEntitiesOfClass(ArmorStand.class,
				new net.minecraft.world.phys.AABB(ax - 2, y - 1, row - 2, ax + 14, y + 4, row + 4))) {
				if (ArmorSets.fullSet(stand) == null) {
					return false;
				}
			}
			return true;
		}), "cada soporte deberia llevar el conjunto entero de su metal");
		look(context, server, ax + 4.6, y + 1.7, row + 6.4, ax + 4.6, y + 1.4, row + 1.5, "cumbre_02b_intermedias");

		// 4. Each peak set, from the front and from behind, with its sword and pickaxe in frames beside it.
		String[] names = {"cumbre_03_iracero", "cumbre_04_egida", "cumbre_05_arcanio"};
		for (int i = 0; i < PEAK.length; i++) {
			int sx = ax + 18 + 7 * i;
			ForgeMaterial peak = material(PEAK[i]);
			float[] yaw = {0.0F};
			server.runOnServer(s -> {
				ServerLevel level = connection.getServerLevel();
				wall(level, sx - 2, y, row - 1, 5, 4);
				frame(level, new BlockPos(sx - 1, y + 2, row), Direction.SOUTH, all(level, ForgeType.ESPADA, peak));
				frame(level, new BlockPos(sx + 1, y + 2, row), Direction.SOUTH, all(level, ForgeType.PICO, peak));
				stand(level, peak, sx + 0.5, y, row + 1.5, 0.0F, null);
			});
			look(context, server, sx + 0.5, y + 1.7, row + 4.4, sx + 0.5, y + 1.5, row + 1.5, names[i]);
			// From behind: the stand turns its back (north is the wall, so the camera stays and the suit turns round).
			server.runOnServer(s -> {
				for (ArmorStand stand : connection.getServerLevel().getEntitiesOfClass(ArmorStand.class,
					new net.minecraft.world.phys.AABB(sx - 1, y - 1, row, sx + 2, y + 3, row + 3))) {
					stand.setYRot(180.0F);
					stand.setYBodyRot(180.0F);
					stand.setYHeadRot(180.0F);
					stand.yRotO = 180.0F;
					stand.yBodyRotO = 180.0F;
					stand.yHeadRotO = 180.0F;
				}
			});
			look(context, server, sx + 0.5, y + 1.7, row + 4.4, sx + 0.5, y + 1.5, row + 1.5, names[i] + "_espaldas");
		}

		// 5. First person: a sword of wrathsteel in one hand and a shield of aegis in the other, with their particles.
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.getInventory().clearContent();
			player.setItemInHand(InteractionHand.MAIN_HAND, all(connection.getServerLevel(), ForgeType.ESPADA, ForgeMaterial.IRACERO));
			player.setItemInHand(InteractionHand.OFF_HAND, all(connection.getServerLevel(), ForgeType.ESCUDO, ForgeMaterial.EGIDA));
		});
		server.runCommand("gamemode survival @a");
		showHud(context);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		tp(server, ax + 18.5, y, row + 3.2, 180.0F, 15.0F);
		context.waitTicks(40);
		shot(context, "cumbre_06_mano");

		// 6. Book III open at "Aleaciones cumbre".
		server.runOnServer(s -> connection.getServerPlayer().getInventory().clearContent());
		context.runOnClient(mc -> mc.gui.setScreen(new GuideBookScreen(GuideBooks.Book.FUNDICION, "aleaciones_cumbre")));
		context.waitForScreen(GuideBookScreen.class);
		context.waitTicks(10);
		int page = context.computeOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).chapterPage("aleaciones_cumbre"));
		check(page >= 0, "el libro III deberia tener el capitulo aleaciones_cumbre");
		context.runOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).goToPage(page));
		context.waitTicks(10);
		context.getInput().setCursorPos(0, 0);
		shot(context, "cumbre_07_libro");
		context.runOnClient(mc -> ((GuideBookScreen) mc.gui.screen()).goToPage(page + 2));
		context.waitTicks(10);
		context.getInput().setCursorPos(0, 0);
		shot(context, "cumbre_07b_libro_recetas");
		context.runOnClient(mc -> mc.gui.setScreen(null));

		// 7. JEI is only compile-time here: its recipe page ("Crisol de obsidiana con cubas") is not drawn in this run.
		boolean jei = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei");
		log("cumbre: JEI " + (jei ? "cargado, pero su pagina no se filma aqui" : "no esta cargado en la prueba de cliente: se omite cumbre_08_jei"));
		server.runCommand("gamemode survival @a");
	}

	/** A stand in a full set of this plate on a leather lining, with this in its hands (or nothing). */
	private static ArmorStand stand(ServerLevel level, ForgeMaterial plate, double x, double y, double z, float yaw, ItemStack hand) {
		var registries = level.registryAccess();
		ArmorStand stand = new ArmorStand(level, x, y, z);
		stand.setShowArms(true);
		stand.setNoBasePlate(true);
		stand.setYRot(yaw);
		for (int i = 0; i < SLOTS.length; i++) {
			stand.setItemSlot(SLOTS[i], Assembler.create(PIECES[i], List.of(plate, ForgeMaterial.CUERO), registries));
		}
		if (hand != null) {
			stand.setItemSlot(EquipmentSlot.MAINHAND, hand);
		}
		level.addFreshEntity(stand);
		return stand;
	}

	/** A forged item all of one material. */
	private static ItemStack all(ServerLevel level, ForgeType type, ForgeMaterial material) {
		List<ForgeMaterial> every = new ArrayList<>();
		for (int slot = 0; slot < type.slots.size(); slot++) {
			every.add(material);
		}
		return Assembler.create(type, every, level.registryAccess());
	}

	/** A wall of stone bricks, {@code width} blocks wide from x and {@code height} high from y, at z. */
	private static void wall(ServerLevel level, int x, int y, int z, int width, int height) {
		for (int dx = 0; dx < width; dx++) {
			for (int dy = 0; dy < height; dy++) {
				level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z), Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
	}

	private static void frame(ServerLevel level, BlockPos pos, Direction facing, ItemStack stack) {
		ItemFrame frame = new ItemFrame(level, pos, facing);
		frame.setItem(stack, false);
		level.addFreshEntity(frame);
	}

	/** The crafting table opened for the player, the inventory filled with {@code inventory} and the grid with {@code grid}. */
	private static void open(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, BlockPos table,
		List<ItemStack> inventory, List<ItemStack> grid) {
		context.getInput().setCursorPos(0, 0);
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.closeContainer();
			player.getInventory().clearContent();
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
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
		quiet(context);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen),
			"la mesa de crafteo deberia estar abierta");
	}

	private static void close(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		context.getInput().setCursorPos(0, 0);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		server.runOnServer(s -> connection.getServerPlayer().closeContainer());
		context.waitTicks(5);
	}

	private static void hideHud(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static void showHud(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static void quiet(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
	}

	private static void tp(TestServerContext server, double x, double y, double z, float yaw, float pitch) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	/** The spectator camera at (x, y, z) looking at (tx, ty, tz), a few ticks to settle, then the shot. */
	private static void look(ClientGameTestContext context, TestServerContext server, double x, double y, double z,
		double tx, double ty, double tz, String name) {
		double dx = tx - x;
		double dy = ty - y;
		double dz = tz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		// The camera is given as the eye, and a spectator's eyes are 1.62 above his feet.
		tp(server, x, y - 1.62, z, yaw, pitch);
		context.waitTicks(30);
		quiet(context);
		shot(context, name);
	}

	private static void shot(ClientGameTestContext context, String name) {
		quiet(context);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
		log("cumbre: captura " + name);
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
