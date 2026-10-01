package dev.forja.test;

import java.util.Locale;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * FORJA_SOLO=fragua_caida: the Nether ruin forja:fragua_caida/fragua, placed on a cleared pocket of the real Nether
 * (its own light, no forced daytime) with the guards it comes with standing still, and photographed from a spectator
 * camera with the HUD hidden: a wide shot, two 3/4 views, the cold soul forge with the tables, and the guards up close.
 * Then the soul forge is lit (docs/ALEACIONES_NETHER_END.md) and fed a batch, and a suit and a sword of each Nether
 * alloy stand beside it on armour stands: the lit forge from afar and close, and the gear.
 */
final class FraguaCaidaFootage {
	private static final int SIZE = 15;

	private FraguaCaidaFootage() {
	}

	static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, int x, int y, int z) {
		int ox = x + 500;
		int oz = z + 500;
		int oy = 50;
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("difficulty easy");
		server.runCommand("gamemode spectator @a");
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp @a %d %d %d", ox + 7, oy + 8, oz + 40));
		context.waitTicks(60);
		// A netherrack floor and an air pocket around the template, so the walls stand out and nothing from the cave hides them.
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run fill %d %d %d %d %d %d minecraft:netherrack",
			ox - 20, oy - 6, oz - 20, ox + 36, oy - 1, oz + 50));
		// fill is capped at 32768 blocks, so the air goes in slices.
		for (int zz = oz - 20; zz <= oz + 50; zz += 10) {
			server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run fill %d %d %d %d %d %d minecraft:air",
				ox - 20, oy, zz, ox + 36, oy + 24, Math.min(zz + 9, oz + 50)));
		}
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run place template forja:fragua_caida/fragua %d %d %d", ox, oy, oz));
		context.waitTicks(20);
		int[] counts = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level.NETHER);
			int forges = 0;
			int tables = 0;
			for (BlockPos pos : BlockPos.betweenClosed(ox, oy, oz, ox + SIZE - 1, oy + 9, oz + SIZE - 1)) {
				forges += level.getBlockState(pos).is(dev.forja.registry.ModBlocks.FRAGUA_DE_ALMAS) ? 1 : 0;
				tables += level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_FORJA)
					|| level.getBlockState(pos).is(dev.forja.registry.ModBlocks.MESA_DE_PIEZAS) ? 1 : 0;
			}
			int guards = 0;
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(ox - 2, oy - 1, oz - 2, ox + SIZE + 2, oy + 12, oz + SIZE + 2))) {
				mob.setNoAi(true);
				guards++;
			}
			return new int[] {forges, tables, guards};
		});
		log("fragua caida: fraguas de almas " + counts[0] + ", mesas " + counts[1] + ", guardias " + counts[2]);
		check(counts[0] == 1 && counts[1] == 2, "la fragua caida deberia traer una fragua de almas y dos mesas");
		check(counts[2] == 7, "y sus siete guardias (2 automatas, 2 corazas, 3 pavesas), hay " + counts[2]);

		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		// The template's origin is its corner; its middle is (7.5, 7.5) and its gap in the wall is on the south side.
		double cx = ox + 7.5;
		double cz = oz + 7.5;
		look(context, server, cx, oy + 9, oz + 38, cx, oy + 2, cz, "fragua_caida_01_desde_fuera");
		look(context, server, ox - 14, oy + 11, oz - 14, cx, oy + 2, cz, "fragua_caida_02_tres_cuartos_noroeste");
		look(context, server, ox + 29, oy + 11, oz + 29, cx, oy + 2, cz, "fragua_caida_03_tres_cuartos_sureste");
		look(context, server, ox + 11.5, oy + 4.5, oz + 12.0, cx, oy + 2.5, cz, "fragua_caida_04_fragua_de_almas_fria_y_mesas");
		look(context, server, ox + 4.5, oy + 4.5, oz + 2.5, cx, oy + 2.5, cz, "fragua_caida_05_fragua_y_yunque");
		look(context, server, ox + 3.5, oy + 2.6, oz + 9.0, ox + 3.5, oy + 1.8, oz + 3.5, "fragua_caida_06_guardia_automata");
		look(context, server, ox + 11.5, oy + 2.6, oz + 6.0, ox + 11.5, oy + 1.8, oz + 11.5, "fragua_caida_07_guardia_automata_este");

		// The soul forge lit and working, and the Nether alloys' gear beside it.
		BlockPos forge = new BlockPos(ox + 7, oy + 2, oz + 7);
		String made = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level.NETHER);
			dev.forja.block.FarForgeBlock.light(level, forge, null);
			if (level.getBlockEntity(forge) instanceof dev.forja.block.entity.FarForgeBlockEntity hearth) {
				for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.block.FarForgeBlock.Kind.ALMAS.recipes()) {
					for (dev.forja.forge.Alloys.Part part : recipe.inputs()) {
						hearth.insert(new net.minecraft.world.item.ItemStack(part.item().get(), part.count()));
					}
				}
				hearth.addFuel(4);
			}
			StringBuilder names = new StringBuilder();
			int i = 0;
			for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.block.FarForgeBlock.Kind.ALMAS.recipes()) {
				dev.forja.material.ForgeMaterial material = dev.forja.material.ForgeMaterial.fromInput(recipe.result());
				// On the altar, either side of the forge and a step south of it, facing the camera.
				stand(level, ox + 6.5 + 2.0 * i, oy + 2.0, oz + 8.5, material);
				names.append(material.getSerializedName()).append(' ');
				i++;
			}
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(ox - 2, oy - 1, oz - 2, ox + SIZE + 2, oy + 12, oz + SIZE + 2))) {
				// The wisps float right in front of the forge and the stands: they had their shots above.
				if (mob instanceof dev.forja.entity.EmberWisp) {
					mob.discard();
				} else {
					mob.setNoAi(true);
				}
			}
			return names.toString().trim();
		});
		boolean lit = server.computeOnServer(s -> s.getLevel(Level.NETHER).getBlockState(forge).getValue(dev.forja.block.FarForgeBlock.LIT));
		log("fragua caida: fragua de almas encendida " + lit + ", equipo de " + made);
		check(lit, "la fragua de almas deberia estar encendida");
		look(context, server, cx, oy + 5, oz + 15.5, cx, oy + 2.2, cz, "fragua_caida_08_fragua_de_almas_encendida");
		look(context, server, ox + 7.5, oy + 4.2, oz + 5.4, ox + 7.5, oy + 2.8, oz + 7.5, "fragua_caida_09_hogar_de_almas_cerca");
		look(context, server, ox + 7.5, oy + 4.3, oz + 10.8, ox + 7.5, oy + 2.8, oz + 8.5, "fragua_caida_10_equipo_de_las_aleaciones");
		look(context, server, ox + 11.6, oy + 3.4, oz + 7.0, ox + 7.5, oy + 2.9, oz + 8.5, "fragua_caida_11_equipo_de_lado");

		// Volcánico: a suit of magmasteel standing in the lava channel by the gap in the south wall, and the crust it
		// cools round itself.
		int cooled = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level.NETHER);
			var walker = stand(level, ox + 7.5, oy + 1.0, oz + 12.5, dev.forja.material.ForgeMaterial.MAGMACERO);
			walker.setNoGravity(true);
			return dev.forja.upgrade.TraitEffects.volcanicStep(level, walker, 4);
		});
		log("fragua caida: costra de magma, " + cooled + " bloques");
		check(cooled > 0, "el magmacero deberia enfriar la lava del canal");
		look(context, server, ox + 7.5, oy + 3.4, oz + 17.0, ox + 7.5, oy + 1.2, oz + 12.5, "fragua_caida_12_costra_de_magma");

		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		server.runCommand("gamemode survival @a");
	}

	/** An armour stand in a full suit of one material, with a sword of it in the hand and a pickaxe in the other. */
	private static net.minecraft.world.entity.decoration.ArmorStand stand(ServerLevel level, double x, double y, double z,
		dev.forja.material.ForgeMaterial material) {
		var registries = level.registryAccess();
		var stand = new net.minecraft.world.entity.decoration.ArmorStand(level, x, y, z);
		stand.setYRot(0.0F);
		stand.setYBodyRot(0.0F);
		stand.setShowArms(true);
		stand.setNoBasePlate(true);
		java.util.List<dev.forja.material.ForgeMaterial> plate = java.util.List.of(material, dev.forja.material.ForgeMaterial.CUERO);
		stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, dev.forja.forge.Assembler.create(dev.forja.forge.ForgeType.CASCO, plate, registries));
		stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, dev.forja.forge.Assembler.create(dev.forja.forge.ForgeType.PECHERA, plate, registries));
		stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, dev.forja.forge.Assembler.create(dev.forja.forge.ForgeType.GREBAS, plate, registries));
		stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, dev.forja.forge.Assembler.create(dev.forja.forge.ForgeType.BOTAS, plate, registries));
		stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, dev.forja.forge.Assembler.create(dev.forja.forge.ForgeType.ESPADA,
			java.util.List.of(material, material, material), registries));
		stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, dev.forja.forge.Assembler.create(dev.forja.forge.ForgeType.PICO,
			java.util.List.of(material, material, material), registries));
		level.addFreshEntity(stand);
		return stand;
	}

	/** The spectator camera at (x, y, z) looking at (tx, ty, tz), a few ticks to let the chunks and light settle, then the shot. */
	private static void look(ClientGameTestContext context, TestServerContext server, double x, double y, double z,
		double tx, double ty, double tz, String name) {
		double dx = tx - x;
		double dy = ty - y;
		double dz = tz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		context.waitTicks(40);
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
		log("fragua caida: captura " + name);
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
