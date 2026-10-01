package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every item and block of the mod on screen, to judge their looks (FORJA_SOLO=objetos_visual): pages of inventory
 * icons drawn at twice their size on the slot grey (every registered item, the forged gear in five material sets,
 * the loose parts in four materials), then every block placed in a lit world, seen from two sides, with the foundry
 * and the heat line laid out connected as a player would build them.
 */
final class ObjetosVisualFootage {
	private ObjetosVisualFootage() {
	}

	/** A plain screen that draws a page of item icons in a grid, as large as the window allows. */
	static final class IconPage extends Screen {
		private final List<ItemStack> stacks;
		private final float scale;

		IconPage(List<ItemStack> stacks, float scale) {
			super(Component.literal("iconos"));
			this.stacks = stacks;
			this.scale = scale;
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			g.fill(0, 0, this.width, this.height, 0xFF8B8B8B);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
			super.extractRenderState(g, mouseX, mouseY, a);
			int cell = Math.round(18 * this.scale);
			int columns = Math.max(1, (this.width - 8) / cell);
			for (int i = 0; i < this.stacks.size(); i++) {
				int x = 4 + i % columns * cell;
				int y = 4 + i / columns * cell;
				g.fill(x, y, x + cell - 1, y + cell - 1, 0xFF9A9A9A);
				g.pose().pushMatrix();
				g.pose().translate(x + this.scale, y + this.scale);
				g.pose().scale(this.scale, this.scale);
				g.item(this.stacks.get(i), 0, 0);
				g.pose().popMatrix();
			}
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		shot(context, name, false);
	}

	/** A screenshot; `big` renders the world at twice the window's size (a screen keeps the window's layout). */
	private static void shot(ClientGameTestContext context, String name, boolean big) {
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		TestScreenshotOptions options = TestScreenshotOptions.of("objetos_visual_" + name).disableCounterPrefix();
		context.takeScreenshot(big ? options.withSize(1708, 960) : options);
	}

	/** Pages of icons: as many per page as fit at that scale, a screenshot each. */
	private static void pages(ClientGameTestContext context, String name, List<ItemStack> stacks, float scale) {
		int perPage = context.computeOnClient(mc -> {
			int width = mc.getWindow().getGuiScaledWidth();
			int height = mc.getWindow().getGuiScaledHeight();
			int cell = Math.round(18 * scale);
			return Math.max(1, (width - 8) / cell) * Math.max(1, (height - 8) / cell);
		});
		for (int page = 0; page * perPage < stacks.size(); page++) {
			List<ItemStack> slice = List.copyOf(stacks.subList(page * perPage, Math.min(stacks.size(), (page + 1) * perPage)));
			StringBuilder names = new StringBuilder();
			for (int i = 0; i < slice.size(); i++) {
				names.append(i).append('=').append(BuiltInRegistries.ITEM.getKey(slice.get(i).getItem()).getPath()).append(' ');
			}
			System.out.println("[objetos_visual] " + name + "_" + page + ": " + names);
			context.runOnClient(mc -> mc.gui.setScreen(new IconPage(slice, scale)));
			context.waitTicks(5);
			shot(context, String.format(Locale.ROOT, "%s_%d", name, page));
		}
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private static final List<List<ForgeMaterial>> SETS = List.of(
		// head or plate, handle, binding or extra
		List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO),
		List.of(ForgeMaterial.ORO, ForgeMaterial.HUESO, ForgeMaterial.HIERRO),
		List.of(ForgeMaterial.DIAMANTE, ForgeMaterial.OBSIDIANA, ForgeMaterial.ORO),
		List.of(ForgeMaterial.NETHERITA, ForgeMaterial.VARA_DE_BLAZE, ForgeMaterial.HIERRO),
		List.of(ForgeMaterial.COBRE, ForgeMaterial.MADERA, ForgeMaterial.CUERO));

	private static ForgeMaterial pick(PartType part, List<ForgeMaterial> set) {
		ForgeMaterial wanted = switch (part.role) {
			case HANDLE -> set.get(1);
			case EXTRA -> set.get(2);
			case LINING -> ForgeMaterial.CUERO;
			default -> set.get(0);
		};
		return part.accepts(wanted) ? wanted : part.showcase();
	}

	static List<ItemStack> forged() {
		List<ItemStack> out = new ArrayList<>();
		for (ForgeType type : ForgeType.values()) {
			for (List<ForgeMaterial> set : SETS) {
				List<ForgeMaterial> materials = new ArrayList<>();
				for (PartType slot : type.slots) {
					materials.add(pick(slot, set));
				}
				out.add(Assembler.create(type, materials));
			}
		}
		return out;
	}

	static List<ItemStack> parts() {
		List<ItemStack> out = new ArrayList<>();
		for (PartType part : PartType.values()) {
			for (ForgeMaterial material : List.of(ForgeMaterial.HIERRO, ForgeMaterial.ORO, ForgeMaterial.MADERA, ForgeMaterial.DIAMANTE)) {
				out.add(Assembler.createPart(part, part.accepts(material) ? material : part.showcase()));
			}
		}
		return out;
	}

	static List<ItemStack> registered() {
		List<ItemStack> out = new ArrayList<>();
		for (var item : BuiltInRegistries.ITEM) {
			if ("forja".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
				out.add(new ItemStack(item));
			}
		}
		return out;
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		pages(context, "01_registrados", registered(), 2.0F);
		pages(context, "02_forjados", forged(), 2.0F);
		pages(context, "03_piezas", parts(), 2.0F);
		// The blocks as the inventory draws them, large: their models seen from the usual three-quarter view.
		List<ItemStack> blocks = new ArrayList<>();
		for (ItemStack stack : registered()) {
			if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
				blocks.add(stack);
			}
		}
		pages(context, "05_bloques", blocks, 4.0F);
		world(context, server, connection, x, y, z);
	}

	private static void place(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	private static BlockState block(String id) {
		return BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("forja", id)).defaultBlockState();
	}

	/** The blocks redrawn by the visual pass (tools/visual_objetos.py), placed in a row of their own for a close look. */
	private static final List<String> FOCUS = List.of("mesa_de_forja", "montadora", "yunque_del_herrero", "mesa_de_losa", "mesa_de_forja_mayor",
		"caja_de_moldeo", "crisol_de_hierro", "mesa_de_extraccion");

	/** Every block in a lit yard, a block every second space; then the foundry and the heat line, connected. */
	private static void world(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int ox = x + 40;
		int oz = z + 40;
		int floor = y - 1;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:polished_andesite", ox - 4, floor, oz - 4, ox + 26, floor, oz + 44));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", ox - 4, floor + 1, oz - 4, ox + 26, floor + 8, oz + 44));
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		List<String> skip = List.of("metal_fundido", "portal_estelar");
		List<String> ids = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			var key = BuiltInRegistries.BLOCK.getKey(block);
			if ("forja".equals(key.getNamespace()) && !skip.contains(key.getPath())) {
				ids.add(key.getPath());
			}
		}
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			for (int i = 0; i < ids.size(); i++) {
				BlockPos pos = new BlockPos(ox + i % 8 * 3, floor + 1, oz + i / 8 * 3);
				place(level, pos, block(ids.get(i)));
			}
			// The foundry in a row: crucible, pipes, a valve, a casting box, a tank; and the heat line beside it.
			int row = oz + 24;
			String[] foundry = {"crisol_de_hierro", "conducto_de_colada", "conducto_de_colada", "llave_de_paso", "conducto_de_acero",
				"caja_de_moldeo", "conducto_de_damasco", "cuba_de_colada", "cuba_de_colada"};
			for (int i = 0; i < foundry.length; i++) {
				place(level, new BlockPos(ox + i, floor + 1, row), block(foundry[i]));
			}
			String[] heat = {"caldera", "tubo_de_calor", "tubo_de_calor", "tubo_de_calor", "deposito_de_calor", "tubo_de_calor", "crisol_de_barro"};
			for (int i = 0; i < heat.length; i++) {
				place(level, new BlockPos(ox + i, floor + 1, row + 3), block(heat[i]));
			}
			place(level, new BlockPos(ox + 3, floor + 2, row + 3), block("tubo_de_calor"));
			// The blocks the visual pass redrew, side by side for a close look (FOCUS).
			for (int i = 0; i < FOCUS.size(); i++) {
				place(level, new BlockPos(ox + i * 2, floor + 1, oz + 40), block(FOCUS.get(i)));
			}
			place(level, new BlockPos(ox + 3, floor + 3, row + 3), block("tubo_de_calor"));
			// Shapes that depend on the neighbours (pipes and their arms) are worked out again now that all are down.
			for (BlockPos pos : BlockPos.betweenClosed(ox - 1, floor + 1, row - 1, ox + 10, floor + 4, row + 4)) {
				BlockState state = level.getBlockState(pos);
				if (!state.isAir()) {
					BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
					if (updated != state) {
						level.setBlock(pos, updated, Block.UPDATE_ALL);
					}
				}
			}
			level.setBlock(new BlockPos(ox - 3, floor + 1, oz - 3), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		});
		server.runCommand("gamemode spectator @a");
		context.waitTicks(20);
		double cx = ox + 10.5;
		String[][] views = {
			{String.valueOf(cx), String.valueOf(floor + 6.0), String.valueOf(oz - 6.0), "0", "32", "a_frente"},
			{String.valueOf(cx), String.valueOf(floor + 5.0), String.valueOf(oz + 3.0), "0", "35", "b_frente_cerca"},
			{String.valueOf(cx), String.valueOf(floor + 5.0), String.valueOf(oz + 9.0), "0", "35", "c_medio"},
			{String.valueOf(cx), String.valueOf(floor + 5.0), String.valueOf(oz + 15.0), "0", "35", "d_fondo"},
			{String.valueOf(cx + 14.0), String.valueOf(floor + 7.0), String.valueOf(oz + 30.0), "135", "30", "e_detras"},
			{String.valueOf(ox + 4.5), String.valueOf(floor + 4.0), String.valueOf(oz + 19.5), "0", "30", "f_fundicion"},
			{String.valueOf(ox + 4.5), String.valueOf(floor + 3.5), String.valueOf(oz + 31.5), "180", "25", "g_calor"},
			{String.valueOf(ox + 3.5), String.valueOf(floor + 3.4), String.valueOf(oz + 37.0), "0", "35", "h_cerca_1"},
			{String.valueOf(ox + 11.5), String.valueOf(floor + 3.4), String.valueOf(oz + 37.0), "0", "35", "h_cerca_2"},
			{String.valueOf(ox + 3.5), String.valueOf(floor + 3.4), String.valueOf(oz + 44.0), "180", "35", "i_detras_1"},
			{String.valueOf(ox + 11.5), String.valueOf(floor + 3.4), String.valueOf(oz + 44.0), "180", "35", "i_detras_2"},
		};
		for (String[] view : views) {
			server.runCommand(String.format(Locale.ROOT, "tp @a %s %s %s %s %s", view[0], view[1], view[2], view[3], view[4]));
			context.waitTicks(15);
			connection.waitForChunksRender();
			shot(context, "04_mundo_" + view[5], true);
		}
		server.runCommand("gamemode survival @a");
	}
}
