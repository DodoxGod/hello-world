package dev.forja.test;

import java.util.List;

import dev.forja.clase.ClassProgress;
import dev.forja.clase.ClassTree;
import dev.forja.clase.PlayerClass;
import dev.forja.client.TalentTreeScreen;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The big class tree (docs/ARBOLES.md) played with the mouse, for Andy to look at (FORJA_SOLO=arbol): the tree as
 * it opens, nodes learned by clicking, the wheel zooming out, a drag moving it, "Probar" planning in blue and
 * "Aplicar" learning the plan, a keystone's tooltip, the search, the milestones, the Mago's tree, the Vela del
 * olvido, and the three skills beside the hotbar; then the three ultimates as a "choose one" set (shots "ultimas_*"):
 * none chosen, one tried with "Probar", one learned with the other two locked, the candle swapping it, and every class.
 * Every step that changes something is checked on the server.
 */
final class ArbolFootage {
	private ArbolFootage() {
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of("arbol_" + name).disableCounterPrefix());
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void log(String message) {
		System.out.println("[forja-test] " + message);
	}

	private static TalentTreeScreen screen(net.minecraft.client.Minecraft mc) {
		return (TalentTreeScreen) mc.gui.screen();
	}

	/** Puts the cursor on a node, in window pixels. */
	private static void onNode(ClientGameTestContext context, String id) {
		double[] point = context.computeOnClient(mc -> {
			int[] gui = screen(mc).nodeCentre(id);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(point[0], point[1]);
		context.waitTicks(1);
	}

	private static void clickNode(ClientGameTestContext context, String id) {
		onNode(context, id);
		context.getInput().pressMouse(0);
		context.waitTicks(3);
	}

	private static boolean has(TestServerContext server, TestServerConnection connection, String id) {
		return server.computeOnServer(s -> ClassProgress.data(connection.getServerPlayer()).has(id));
	}

	public static void film(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runCommand("gamemode survival @a");
		server.runCommand("time set noon");
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ClassProgress.choose(player, PlayerClass.GUERRERO);
			ClassProgress.setLevel(player, 30);
			for (String id : List.of("campeon", "herrero_caido", "nether", "golpe_limpio", "dragon")) {
				ClassProgress.reach(player, id);
			}
		});
		context.waitTicks(5);
		context.runOnClient(mc -> mc.gui.setScreen(new TalentTreeScreen()));
		context.waitForScreen(TalentTreeScreen.class);
		context.getInput().setCursorPos(0, 0);
		shot(context, "01_inicio");

		// Learned by clicking: the Guardia door, its trunk, and Réplica at the end of it.
		for (String id : List.of("guerrero.nucleo_3", "guerrero.b.tronco_1", "guerrero.b.tronco_2", "guerrero.b.tronco_3", "guerrero.b.tronco_4")) {
			clickNode(context, id);
			check(has(server, connection, id), "a click should learn " + id);
		}
		log("arbol: five nodes learned by clicking, the gold path up the Guardia trunk");
		onNode(context, "guerrero.b.tronco_4");
		shot(context, "02_aprendidos");

		// The wheel zooms towards the cursor; a drag on empty space moves the tree.
		float zoomBefore = context.computeOnClient(mc -> screen(mc).zoom());
		double[] middle = context.computeOnClient(mc -> new double[] {mc.getWindow().getScreenWidth() * 0.35, mc.getWindow().getScreenHeight() * 0.55});
		context.getInput().setCursorPos(middle[0], middle[1]);
		for (int i = 0; i < 4; i++) {
			context.getInput().scroll(-1.0);
			context.waitTicks(1);
		}
		float zoomAfter = context.computeOnClient(mc -> screen(mc).zoom());
		check(zoomAfter < zoomBefore * 0.7F, "the wheel should zoom out: " + zoomBefore + " -> " + zoomAfter);
		shot(context, "03_lejos");
		int[] originBefore = context.computeOnClient(mc -> screen(mc).nodeCentre("guerrero.origen"));
		context.getInput().setCursorPos(middle[0] * 0.4, middle[1] * 1.5);
		context.getInput().holdMouse(1);
		context.waitTicks(1);
		for (int i = 0; i < 6; i++) {
			context.getInput().moveCursor(30, -12);
			context.waitTicks(1);
		}
		context.getInput().releaseMouse(1);
		context.waitTicks(2);
		int[] originAfter = context.computeOnClient(mc -> screen(mc).nodeCentre("guerrero.origen"));
		check(originAfter[0] > originBefore[0] && originAfter[1] < originBefore[1], "a drag should move the tree: "
			+ originBefore[0] + "," + originBefore[1] + " -> " + originAfter[0] + "," + originAfter[1]);
		context.getInput().setCursorPos(0, 0);
		shot(context, "04_arrastrado");
		context.runOnClient(mc -> screen(mc).home());
		for (int i = 0; i < 2; i++) {
			context.getInput().setCursorPos(middle[0], middle[1]);
			context.getInput().scroll(-1.0);
			context.waitTicks(1);
		}

		// "Probar": the Contragolpe path planned in blue, nothing spent until "Aplicar".
		int pointsBefore = server.computeOnServer(s -> ClassProgress.data(connection.getServerPlayer()).points());
		context.runOnClient(mc -> screen(mc).press("probar"));
		for (String id : List.of("guerrero.b2.1", "guerrero.b2.2", "guerrero.b2.3", "guerrero.b2.4", "guerrero.b2.5")) {
			clickNode(context, id);
		}
		int planned = context.computeOnClient(mc -> screen(mc).plan().size());
		check(planned == 5, "the plan should hold five nodes: " + planned);
		check(!has(server, connection, "guerrero.b2.1"), "trying spends nothing");
		onNode(context, "guerrero.b2.5");
		shot(context, "05_probar");
		context.runOnClient(mc -> screen(mc).press("aplicar"));
		context.waitTicks(5);
		check(has(server, connection, "guerrero.b2.5"), "Aplicar should learn the plan, Duelista included");
		int pointsAfter = server.computeOnServer(s -> ClassProgress.data(connection.getServerPlayer()).points());
		check(pointsAfter == pointsBefore - 6, "the plan cost six points: " + pointsBefore + " -> " + pointsAfter);
		log("arbol: Probar planned five nodes, Aplicar learned them for six points");

		// A keystone's tooltip: what it gains, what it costs, the other one it rules out.
		onNode(context, "guerrero.b1.5");
		shot(context, "06_clave_tooltip");
		check(server.computeOnServer(s -> ClassProgress.check(connection.getServerPlayer(), "guerrero.b1.5"))
			!= ClassProgress.Refusal.NONE, "Fortaleza is ruled out by Duelista");

		// The search lights what matches.
		context.runOnClient(mc -> screen(mc).search("parada"));
		context.getInput().setCursorPos(0, 0);
		shot(context, "07_buscar");
		context.runOnClient(mc -> screen(mc).search(""));

		// The milestones.
		context.runOnClient(mc -> screen(mc).press("hitos"));
		shot(context, "08_hitos");
		context.runOnClient(mc -> screen(mc).press("hitos"));

		// The Mago's tree, at the top, with a build of its own.
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ClassProgress.choose(player, PlayerClass.MAGO);
			ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
			for (ClassTree.Milestone milestone : ClassTree.milestones()) {
				ClassProgress.reach(player, milestone.id());
			}
			for (String id : List.of("mago.a1.5", "mago.b2.3", "mago.b.habilidad_b2", "mago.s3.ultima_ii", "mago.c.tronco_4", "forja.mano_firme",
				"forja.alma_del_metal")) {
				ArbolGameTests.learnTo(player, id);
			}
		});
		context.waitTicks(5);
		context.runOnClient(mc -> mc.gui.setScreen(new TalentTreeScreen()));
		context.waitForScreen(TalentTreeScreen.class);
		context.getInput().setCursorPos(0, 0);
		shot(context, "09_mago");
		onNode(context, "mago.s3.ultima");
		shot(context, "10_meteoro_tooltip");

		// The Vela del olvido: two nodes at the edge marked in red, and taken off.
		server.runOnServer(s -> {
			connection.getServerPlayer().getInventory().clearContent();
			connection.getServerPlayer().getInventory().add(new ItemStack(ModItems.VELA_DEL_OLVIDO));
		});
		context.runOnClient(mc -> mc.gui.setScreen(new TalentTreeScreen(true)));
		context.waitForScreen(TalentTreeScreen.class);
		clickNode(context, "forja.alma_del_metal");
		int marked = context.computeOnClient(mc -> screen(mc).forgetting().size());
		check(marked == 1, "the candle should mark Alma del metal: " + marked);
		onNode(context, "forja.alma_del_metal");
		shot(context, "11_vela");
		context.runOnClient(mc -> screen(mc).press("quitar"));
		context.waitTicks(5);
		check(!has(server, connection, "forja.alma_del_metal"), "the candle should take Alma del metal off");
		check(server.computeOnServer(s -> connection.getServerPlayer().getInventory().countItem(ModItems.VELA_DEL_OLVIDO)) == 0, "and be spent");

		// The three skills beside the hotbar, and the third one used.
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(5);
		context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_N);
		context.waitTicks(10);
		check(server.computeOnServer(s -> ClassProgress.data(connection.getServerPlayer()).thirdReady() > s.overworld().getGameTime()),
			"N should cast the Meteoro and start its wait");
		shot(context, "12_hud_tres_habilidades");
		server.runOnServer(s -> ClassProgress.clear(connection.getServerPlayer()));
		icons(context, server, connection);
	}

	/** Brings a node to the middle of the canvas, then zooms {@code factor} times in on it. */
	private static void zoomOn(ClientGameTestContext context, String id, float factor) {
		context.runOnClient(mc -> {
			TalentTreeScreen screen = screen(mc);
			float x = mc.getWindow().getGuiScaledWidth() * 0.35F;
			float y = mc.getWindow().getGuiScaledHeight() * 0.52F;
			int[] at = screen.nodeCentre(id);
			screen.pan(x - at[0], y - at[1]);
			screen.zoomAt(x, y, factor);
		});
		context.waitTicks(2);
	}

	/** The icons (docs/ARBOLES.md, "Iconos"): far, near, and the tooltip of each sort of node; shots "iconos_*". */
	private static void icons(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ClassProgress.choose(player, PlayerClass.GUERRERO);
			ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
			for (ClassTree.Milestone milestone : ClassTree.milestones()) {
				ClassProgress.reach(player, milestone.id());
			}
			for (String id : List.of("guerrero.a1.5", "guerrero.b2.4", "guerrero.a.habilidad_v2", "guerrero.s3.ultima_ii", "guerrero.c.tronco_4", "forja.mano_firme",
				"forja.alma_del_metal", "forja.temple_de_campana")) {
				ArbolGameTests.learnTo(player, id);
			}
		});
		context.waitTicks(5);
		context.runOnClient(mc -> mc.gui.setScreen(new TalentTreeScreen()));
		context.waitForScreen(TalentTreeScreen.class);
		context.getInput().setCursorPos(0, 0);
		shot(context, "iconos_01_inicio");
		// Zoomed out: the icons shrink with the nodes and give way to colour when they are too small to read.
		context.runOnClient(mc -> screen(mc).zoomAt(mc.getWindow().getGuiScaledWidth() * 0.4F, mc.getWindow().getGuiScaledHeight() * 0.5F, 0.55F));
		shot(context, "iconos_02_lejos");
		context.runOnClient(mc -> screen(mc).home());
		// Zoomed in on the core, the Guardia branch's learned keystone and a path.
		zoomOn(context, "guerrero.origen", 1.9F);
		shot(context, "iconos_03_cerca_nucleo");
		context.runOnClient(mc -> screen(mc).home());
		zoomOn(context, "guerrero.a1.4", 2.0F);
		shot(context, "iconos_04_cerca_clave");
		context.runOnClient(mc -> screen(mc).home());
		zoomOn(context, "guerrero.s1.3", 2.0F);
		shot(context, "iconos_05_cerca_senda_puente");
		onNode(context, "guerrero.s1.puente_1");
		shot(context, "iconos_06_tooltip_puente");
		context.runOnClient(mc -> screen(mc).home());
		zoomOn(context, "guerrero.a.habilidad_v2", 2.0F);
		onNode(context, "guerrero.a.habilidad_v2");
		shot(context, "iconos_07_tooltip_habilidad_ii");
		context.runOnClient(mc -> screen(mc).home());
		zoomOn(context, "guerrero.nucleo_4", 2.0F);
		shot(context, "iconos_08_cerca_forja");
		onNode(context, "forja.alma_del_metal");
		shot(context, "iconos_09_tooltip_forja");
		context.runOnClient(mc -> screen(mc).home());
		zoomOn(context, "guerrero.a1.5", 1.8F);
		onNode(context, "guerrero.a1.5");
		shot(context, "iconos_10_tooltip_clave");
		onNode(context, "guerrero.a2.1");
		shot(context, "iconos_11_tooltip_menor");
		onNode(context, "guerrero.a2.3");
		shot(context, "iconos_12_tooltip_notable");
		// The other five classes, as they open: their keystones and bridges.
		for (PlayerClass clazz : PlayerClass.values()) {
			if (clazz == PlayerClass.GUERRERO) {
				continue;
			}
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				ClassProgress.choose(player, clazz);
				ClassProgress.setLevel(player, 20);
			});
			context.waitTicks(3);
			context.runOnClient(mc -> mc.gui.setScreen(new TalentTreeScreen()));
			context.waitForScreen(TalentTreeScreen.class);
			context.getInput().setCursorPos(0, 0);
			context.runOnClient(mc -> screen(mc).zoomAt(mc.getWindow().getGuiScaledWidth() * 0.4F, mc.getWindow().getGuiScaledHeight() * 0.5F, 0.8F));
			shot(context, "iconos_clase_" + clazz.id());
		}
		context.runOnClient(mc -> mc.gui.setScreen(null));
		server.runOnServer(s -> ClassProgress.clear(connection.getServerPlayer()));
		ultimates(context, server, connection);
	}

	private static int ultimate(TestServerContext server, TestServerConnection connection) {
		return server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			return ClassProgress.clazz(player).tree().chosenUltimate(ClassProgress.data(player).nodes());
		});
	}

	/**
	 * The three ultimates (docs/ARBOLES.md, "Las habilidades finales"): the set to choose from at a normal zoom, with its
	 * icons; one picked in "Probar" and the other two locked in blue; one learned by a click and the others locked, with
	 * the tooltip that says which was chosen; the candle taking it off; every class with one chosen. Shots "ultimas_*".
	 */
	private static void ultimates(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ClassProgress.choose(player, PlayerClass.GUERRERO);
			ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
			for (String id : List.of("guerrero.s1.3", "guerrero.s2.1", "guerrero.s3.2", "guerrero.a.tronco_2", "guerrero.b.habilidad_b2")) {
				ArbolGameTests.learnTo(player, id);
			}
		});
		context.waitTicks(3);
		context.runOnClient(mc -> mc.gui.setScreen(new TalentTreeScreen()));
		context.waitForScreen(TalentTreeScreen.class);
		context.getInput().setCursorPos(0, 0);
		// As it opens: the icons at the zoom the screen starts at, and the three finals with "Final · elige una".
		shot(context, "ultimas_01_iconos_zoom_normal");
		context.runOnClient(mc -> screen(mc).zoomAt(mc.getWindow().getGuiScaledWidth() * 0.4F, mc.getWindow().getGuiScaledHeight() * 0.53F, 0.82F));
		shot(context, "ultimas_02_elige_una");
		check(ultimate(server, connection) == 0, "no ultimate chosen yet");

		// "Probar": the Hendedura planned, the other two locked in the preview, and the tooltip that says why.
		context.runOnClient(mc -> screen(mc).press("probar"));
		for (String id : List.of("guerrero.s2.2", "guerrero.s2.3", "guerrero.s2.ultima")) {
			clickNode(context, id);
		}
		check(context.computeOnClient(mc -> screen(mc).plan().contains("guerrero.s2.ultima")), "the plan should hold the Hendedura");
		clickNode(context, "guerrero.s1.ultima");
		check(!context.computeOnClient(mc -> screen(mc).plan().contains("guerrero.s1.ultima")), "a second ultimate cannot go in the plan");
		context.getInput().setCursorPos(0, 0);
		shot(context, "ultimas_03_probar_una");
		onNode(context, "guerrero.s1.ultima");
		shot(context, "ultimas_04_probar_tooltip_cerrada");
		check(ultimate(server, connection) == 0, "trying spends nothing");
		context.runOnClient(mc -> {
			screen(mc).press("descartar");
			screen(mc).press("probar");
		});

		// One learned with a click: the Bramido. The other two lock, their II too.
		clickNode(context, "guerrero.s1.ultima");
		check(ultimate(server, connection) == 1, "a click should choose the Bramido");
		clickNode(context, "guerrero.s3.3");
		clickNode(context, "guerrero.s3.ultima");
		check(ultimate(server, connection) == 1 && !has(server, connection, "guerrero.s3.ultima"), "the Torbellino stays locked");
		check(has(server, connection, "guerrero.s3.3"), "but the rest of its senda can still be learned");
		context.getInput().setCursorPos(0, 0);
		shot(context, "ultimas_05_elegida");
		onNode(context, "guerrero.s3.ultima");
		shot(context, "ultimas_06_tooltip_ya_elegiste");
		onNode(context, "guerrero.s1.ultima");
		shot(context, "ultimas_07_tooltip_elegida");
		// Zoomed out to the edge: still icons, not squares.
		context.runOnClient(mc -> screen(mc).zoomAt(mc.getWindow().getGuiScaledWidth() * 0.4F, mc.getWindow().getGuiScaledHeight() * 0.53F, 0.7F));
		context.getInput().setCursorPos(0, 0);
		shot(context, "ultimas_08_iconos_lejos");

		// The candle takes the ultimate off, II and all, and the three are open again.
		server.runOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			ArbolGameTests.learnTo(player, "guerrero.s1.ultima_ii");
			player.getInventory().add(new ItemStack(ModItems.VELA_DEL_OLVIDO));
		});
		context.waitTicks(3);
		context.runOnClient(mc -> mc.gui.setScreen(new TalentTreeScreen(true)));
		context.waitForScreen(TalentTreeScreen.class);
		clickNode(context, "guerrero.s1.ultima_ii");
		clickNode(context, "guerrero.s1.ultima");
		int marked = context.computeOnClient(mc -> screen(mc).forgetting().size());
		check(marked == 2, "the candle should mark the Bramido and its II: " + marked);
		context.getInput().setCursorPos(0, 0);
		shot(context, "ultimas_09_vela");
		context.runOnClient(mc -> screen(mc).press("quitar"));
		context.waitTicks(5);
		check(ultimate(server, connection) == 0, "the candle should free the choice");
		context.runOnClient(mc -> mc.gui.setScreen(new TalentTreeScreen()));
		context.waitForScreen(TalentTreeScreen.class);
		context.getInput().setCursorPos(0, 0);
		shot(context, "ultimas_10_libre_otra_vez");

		// Every class, one ultimate chosen (a different senda each time) and the other two locked.
		int k = 0;
		for (PlayerClass clazz : PlayerClass.values()) {
			int which = 1 + (k++ % 3);
			server.runOnServer(s -> {
				ServerPlayer player = connection.getServerPlayer();
				ClassProgress.choose(player, clazz);
				ClassProgress.setLevel(player, ClassProgress.MAX_LEVEL);
				ArbolGameTests.learnTo(player, clazz.tree().ultimateNode(which, true).id);
			});
			context.waitTicks(3);
			context.runOnClient(mc -> mc.gui.setScreen(new TalentTreeScreen()));
			context.waitForScreen(TalentTreeScreen.class);
			context.getInput().setCursorPos(0, 0);
			context.runOnClient(mc -> screen(mc).zoomAt(mc.getWindow().getGuiScaledWidth() * 0.4F, mc.getWindow().getGuiScaledHeight() * 0.53F, 0.82F));
			shot(context, "ultimas_clase_" + clazz.id());
			check(ultimate(server, connection) == which, clazz + ": the ultimate " + which + " should be chosen");
		}
		context.runOnClient(mc -> mc.gui.setScreen(null));
		server.runOnServer(s -> ClassProgress.clear(connection.getServerPlayer()));
	}
}
