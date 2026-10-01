package dev.forja.test;

import java.util.List;
import java.util.Locale;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The second visual pass's audit (FORJA_SOLO=visual_2): what is still weak in the world rather than in an inventory.
 * The Cementerio entre Estrellas' ground laid in wide patches (a texture that tiles badly shows it only there), the
 * Bastión's blocks of the mod on its own deepslate, the star channels of the Deep Forge set into a floor, and the
 * forged armour worn by stands in a row of materials, front and back.
 */
final class Visual2Footage {
	private Visual2Footage() {
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.takeScreenshot(TestScreenshotOptions.of("visual2_" + name).disableCounterPrefix().withSize(1708, 960));
	}

	private static BlockState block(String id) {
		String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "forja";
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(namespace, path)).defaultBlockState();
	}

	/** Armour materials worn side by side: the vanilla metals, then the mod's own. */
	private static final List<ForgeMaterial> ARMOUR = List.of(ForgeMaterial.CUERO, ForgeMaterial.COBRE, ForgeMaterial.HIERRO,
		ForgeMaterial.ORO, ForgeMaterial.DIAMANTE, ForgeMaterial.NETHERITA, ForgeMaterial.BRONCE, ForgeMaterial.ACERO,
		ForgeMaterial.DAMASCO, ForgeMaterial.OBSIDIANA);

	/** The Bastión's blocks of the mod, in a row on its floor. */
	private static final List<String> BASTION = List.of("farol_de_pavesa", "fragua_apagada", "mesa_de_losa", "mesa_de_brasa",
		"mesa_de_almas", "crisol_de_obsidiana", "cuba_de_colada", "estanteria_del_herrero", "atril_del_herrero", "mensula_estelar");

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int ox = x + 60;
		int oz = z + 60;
		int floor = y - 1;
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", ox - 6, floor + 1, oz - 6, ox + 40, floor + 10, oz + 40));
		server.runCommand("time set noon");
		server.runCommand("weather clear");
		server.runOnServer(s -> {
			ServerLevel level = connection.getServerLevel();
			// The dimension's ground: ash and trodden ash, each 12 x 12, with what lies on it.
			for (int dx = 0; dx < 24; dx++) {
				for (int dz = 0; dz < 12; dz++) {
					level.setBlock(new BlockPos(ox + dx, floor, oz + dz), block(dx < 12 ? "ceniza" : "ceniza_prensada"), Block.UPDATE_ALL);
				}
			}
			level.setBlock(new BlockPos(ox + 5, floor + 1, oz + 6), block("arma_clavada"), Block.UPDATE_ALL);
			level.setBlock(new BlockPos(ox + 17, floor + 1, oz + 5), block("brasa_estelar"), Block.UPDATE_ALL);
			// The Bastión: a deepslate-brick floor and wall, its blocks against the wall, the star channels in the floor.
			for (int dx = 0; dx < 24; dx++) {
				for (int dz = 16; dz < 28; dz++) {
					level.setBlock(new BlockPos(ox + dx, floor, oz + dz), block("minecraft:polished_blackstone_bricks"), Block.UPDATE_ALL);
				}
				for (int dy = 1; dy < 6; dy++) {
					level.setBlock(new BlockPos(ox + dx, floor + dy, oz + 28), block("minecraft:deepslate_bricks"), Block.UPDATE_ALL);
				}
			}
			for (int i = 0; i < BASTION.size(); i++) {
				level.setBlock(new BlockPos(ox + 2 + i * 2, floor + 1, oz + 27), block(BASTION.get(i)), Block.UPDATE_ALL);
			}
			for (int dx = 2; dx < 22; dx++) {
				level.setBlock(new BlockPos(ox + dx, floor, oz + 20), block("conducto_de_damasco"), Block.UPDATE_ALL);
			}
			for (int dz = 17; dz < 26; dz++) {
				level.setBlock(new BlockPos(ox + 12, floor, oz + dz), block("conducto_de_damasco"), Block.UPDATE_ALL);
			}
			for (BlockPos pos : BlockPos.betweenClosed(ox, floor, oz + 16, ox + 23, floor, oz + 27)) {
				BlockState state = level.getBlockState(pos);
				BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
				if (updated != state) {
					level.setBlock(pos, updated, Block.UPDATE_ALL);
				}
			}
			// The armour stands, on a plain floor.
			for (int dx = 0; dx < 24; dx++) {
				for (int dz = 32; dz < 38; dz++) {
					level.setBlock(new BlockPos(ox + dx, floor, oz + dz), block("minecraft:smooth_stone"), Block.UPDATE_ALL);
				}
			}
			for (int i = 0; i < ARMOUR.size(); i++) {
				ForgeMaterial material = ARMOUR.get(i);
				ArmorStand stand = new ArmorStand(level, ox + 1.5 + i * 2.2, floor + 1.0, oz + 34.5);
				stand.setYRot(180.0F);
				stand.setYBodyRot(180.0F);
				stand.setYHeadRot(180.0F);
				stand.setNoGravity(true);
				stand.setItemSlot(EquipmentSlot.HEAD, piece(ForgeType.CASCO, material));
				stand.setItemSlot(EquipmentSlot.CHEST, piece(ForgeType.PECHERA, material));
				stand.setItemSlot(EquipmentSlot.LEGS, piece(ForgeType.GREBAS, material));
				stand.setItemSlot(EquipmentSlot.FEET, piece(ForgeType.BOTAS, material));
				level.addFreshEntity(stand);
			}
		});
		server.runCommand("gamemode spectator @a");
		context.waitTicks(20);
		String[][] views = {
			{String.valueOf(ox + 12.0), String.valueOf(floor + 9.0), String.valueOf(oz - 7.0), "0", "38", "a_ceniza"},
			{String.valueOf(ox + 6.0), String.valueOf(floor + 2.6), String.valueOf(oz - 1.0), "0", "18", "b_ceniza_baja"},
			{String.valueOf(ox + 12.0), String.valueOf(floor + 6.0), String.valueOf(oz + 15.0), "0", "28", "c_bastion"},
			{String.valueOf(ox + 12.5), String.valueOf(floor + 4.0), String.valueOf(oz + 18.5), "0", "50", "d_canales"},
			{String.valueOf(ox + 11.5), String.valueOf(floor + 2.8), String.valueOf(oz + 29.5), "0", "8", "e_armaduras_frente"},
			{String.valueOf(ox + 11.5), String.valueOf(floor + 2.8), String.valueOf(oz + 39.5), "180", "8", "f_armaduras_espalda"},
			{String.valueOf(ox + 4.5), String.valueOf(floor + 2.2), String.valueOf(oz + 31.6), "0", "8", "g_armaduras_cerca"},
		};
		for (String[] view : views) {
			server.runCommand(String.format(Locale.ROOT, "tp @a %s %s %s %s %s", view[0], view[1], view[2], view[3], view[4]));
			context.waitTicks(15);
			connection.waitForChunksRender();
			shot(context, view[5]);
		}
		server.runCommand("gamemode survival @a");
	}

	private static ItemStack piece(ForgeType type, ForgeMaterial material) {
		return Assembler.create(type, List.of(material, ForgeMaterial.CUERO));
	}
}
