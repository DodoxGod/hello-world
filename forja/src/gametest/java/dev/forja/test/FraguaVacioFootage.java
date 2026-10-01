package dev.forja.test;

import java.util.List;
import java.util.Locale;

import dev.forja.block.FarForgeBlock;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * FORJA_SOLO=fragua_vacio: the End ruin forja:fragua_del_vacio/fragua (docs/ALEACIONES_NETHER_END.md), placed on an
 * island of end stone in the real End, its guards standing still, photographed from a spectator camera with the HUD
 * hidden: a wide shot, a 3/4 view, the cold void forge, the forge lit and working, and a suit and a sword of aetherium.
 */
final class FraguaVacioFootage {
	private static final int SIZE = 11;

	private FraguaVacioFootage() {
	}

	static void film(ClientGameTestContext context, TestServerContext server) {
		int ox = 1200;
		int oz = 1200;
		int oy = 62;
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("difficulty easy");
		server.runCommand("gamemode spectator @a");
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_end run tp @a %d %d %d", ox + 5, oy + 8, oz + 30));
		context.waitTicks(60);
		// An island to stand it on: a slab of end stone with a rough edge, and air over it.
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_end run fill %d %d %d %d %d %d minecraft:end_stone",
			ox - 12, oy - 5, oz - 12, ox + 22, oy - 1, oz + 22));
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_end run fill %d %d %d %d %d %d minecraft:air",
			ox - 12, oy, oz - 12, ox + 22, oy + 20, oz + 22));
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_end run place template forja:fragua_del_vacio/fragua %d %d %d", ox, oy, oz));
		context.waitTicks(20);
		int[] counts = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level.END);
			int forges = 0;
			for (BlockPos pos : BlockPos.betweenClosed(ox, oy, oz, ox + SIZE - 1, oy + 7, oz + SIZE - 1)) {
				forges += level.getBlockState(pos).is(dev.forja.registry.ModBlocks.FRAGUA_DEL_VACIO) ? 1 : 0;
			}
			int guards = 0;
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(ox - 2, oy - 1, oz - 2, ox + SIZE + 2, oy + 10, oz + SIZE + 2))) {
				mob.setNoAi(true);
				guards++;
			}
			return new int[] {forges, guards};
		});
		log("fragua del vacio: fraguas " + counts[0] + ", guardias " + counts[1]);
		check(counts[0] == 1, "la fragua del vacio deberia traer su fragua");
		check(counts[1] == 3, "y sus tres guardias (2 corazas y un shulker), hay " + counts[1]);

		context.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		double cx = ox + 5.5;
		double cz = oz + 5.5;
		look(context, server, cx, oy + 7, oz + 24, cx, oy + 2, cz, "fragua_vacio_01_desde_fuera");
		look(context, server, ox - 9, oy + 9, oz - 9, cx, oy + 2, cz, "fragua_vacio_02_tres_cuartos");
		look(context, server, ox + 5.5, oy + 3.6, oz + 8.6, ox + 5.5, oy + 2.4, oz + 5.5, "fragua_vacio_03_fragua_fria");

		BlockPos forge = new BlockPos(ox + 5, oy + 2, oz + 5);
		boolean lit = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level.END);
			FarForgeBlock.light(level, forge, null);
			if (level.getBlockEntity(forge) instanceof dev.forja.block.entity.FarForgeBlockEntity hearth) {
				for (dev.forja.forge.Alloys.Recipe recipe : FarForgeBlock.Kind.VACIO.recipes()) {
					for (dev.forja.forge.Alloys.Part part : recipe.inputs()) {
						hearth.insert(new net.minecraft.world.item.ItemStack(part.item().get(), part.count()));
					}
				}
				hearth.addFuel(4);
			}
			stand(level, ox + 5.5, oy + 2.0, oz + 6.5);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(ox - 2, oy - 1, oz - 2, ox + SIZE + 2, oy + 10, oz + SIZE + 2))) {
				mob.setNoAi(true);
			}
			return level.getBlockState(forge).getValue(FarForgeBlock.LIT);
		});
		log("fragua del vacio: encendida " + lit);
		check(lit, "la fragua del vacio deberia estar encendida");
		look(context, server, cx, oy + 6, oz + 16, cx, oy + 2.2, cz, "fragua_vacio_04_encendida");
		look(context, server, ox + 5.5, oy + 4.2, oz + 2.6, ox + 5.5, oy + 2.6, oz + 5.5, "fragua_vacio_05_hogar_cerca");
		// The suits that stood up had their shots; out of the way of the gear's.
		server.runOnServer(s -> s.getLevel(Level.END).getEntitiesOfClass(dev.forja.entity.HollowArmor.class,
			new AABB(ox - 2, oy - 1, oz - 2, ox + SIZE + 2, oy + 10, oz + SIZE + 2)).forEach(net.minecraft.world.entity.Entity::discard));
		look(context, server, ox + 5.5, oy + 3.4, oz + 10.0, ox + 5.5, oy + 2.9, oz + 6.5, "fragua_vacio_06_equipo_de_eterio");
		look(context, server, ox + 9.0, oy + 3.2, oz + 7.5, ox + 5.5, oy + 2.9, oz + 6.5, "fragua_vacio_07_equipo_de_lado");

		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		server.runCommand("gamemode survival @a");
	}

	/** An armour stand in a full suit of aetherium, with a sword of it in the hand and a pickaxe in the other. */
	private static void stand(ServerLevel level, double x, double y, double z) {
		var registries = level.registryAccess();
		ArmorStand stand = new ArmorStand(level, x, y, z);
		stand.setShowArms(true);
		stand.setNoBasePlate(true);
		List<ForgeMaterial> plate = List.of(ForgeMaterial.ETERIO, ForgeMaterial.CUERO);
		List<ForgeMaterial> all = List.of(ForgeMaterial.ETERIO, ForgeMaterial.ETERIO, ForgeMaterial.ETERIO);
		stand.setItemSlot(EquipmentSlot.HEAD, Assembler.create(ForgeType.CASCO, plate, registries));
		stand.setItemSlot(EquipmentSlot.CHEST, Assembler.create(ForgeType.PECHERA, plate, registries));
		stand.setItemSlot(EquipmentSlot.LEGS, Assembler.create(ForgeType.GREBAS, plate, registries));
		stand.setItemSlot(EquipmentSlot.FEET, Assembler.create(ForgeType.BOTAS, plate, registries));
		stand.setItemSlot(EquipmentSlot.MAINHAND, Assembler.create(ForgeType.ESPADA, all, registries));
		stand.setItemSlot(EquipmentSlot.OFFHAND, Assembler.create(ForgeType.PICO, all, registries));
		level.addFreshEntity(stand);
	}

	/** The spectator camera at (x, y, z) looking at (tx, ty, tz), a few ticks to settle, then the shot. */
	private static void look(ClientGameTestContext context, TestServerContext server, double x, double y, double z,
		double tx, double ty, double tz, String name) {
		double dx = tx - x;
		double dy = ty - y;
		double dz = tz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_end run tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		context.waitTicks(40);
		context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
		log("fragua del vacio: captura " + name);
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
