package dev.forja.test;

import dev.forja.difficulty.Ladder;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.WorldOptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;

/**
 * FORJA_SOLO=dificultad: the difficulty ladder (difficulty/Ladder) on Minecraft's own button, played with real clicks.
 *
 * <ul>
 *   <li>The world creation screen: Normal → Difícil → <b>Extremo</b>; then Hardcore, where the same button stays open
 *   as the Difícil ⇄ Extremo toggle;</li>
 *   <li>a hardcore world created on Extremo is Extremo on the server, and still is when it is saved, closed and opened
 *   again; its world options show "Extremo" (locked, as hardcore locks it);</li>
 *   <li>an ordinary world: the world options' button (pause menu → Options → World options) clicked from Normal to
 *   Difícil to Extremo puts the server on Extremo.</li>
 * </ul>
 * The pictures (dificultad_*.png) go to the run's screenshots; Andy's copies are in Forja_capturas_mejoras/dificultad.
 */
final class DificultadFootage {
	private DificultadFootage() {
	}

	static void run(ClientGameTestContext context) {
		creationScreen(context);
		hardcoreWorld(context);
		ordinaryWorld(context);
	}

	// ------------------------------------------------------------------ the world creation screen

	private static void creationScreen(ClientGameTestContext context) {
		// the resource reload's overlay still fading out swallows every click (MouseHandler.onButton)
		context.waitFor(mc -> mc.gui.overlay() == null, 1200);
		context.runOnClient(mc -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen())));
		context.waitForScreen(CreateWorldScreen.class);
		context.waitTicks(10);
		expect(context, "Normal", "al abrir, la de siempre");
		clickDifficulty(context);
		expect(context, "Difícil", "un clic: Difícil");
		clickDifficulty(context);
		expect(context, "Extremo", "dos clics: Extremo");
		check(context.computeOnClient(mc -> state(mc).getGameRules().get(Ladder.EXTREMO_RULE) && state(mc).getDifficulty() == Difficulty.HARD),
			"el mundo por crear lleva HARD y la bandera");
		hoverDifficulty(context);
		shot(context, "dificultad_01_crear_extremo");
		// Hardcore: vanilla shuts the difficulty button; ours stays open, Difícil or Extremo
		clickWidget(context, w -> w instanceof CycleButton<?> b && b.getValue() instanceof WorldCreationUiState.SelectedGameMode);
		context.waitTicks(2);
		check(context.computeOnClient(mc -> state(mc).isHardcore()), "modo extremo (hardcore) elegido");
		expect(context, "Extremo", "en hardcore sigue en Extremo");
		check(context.computeOnClient(mc -> difficultyButton(mc).active), "en hardcore el botón sigue abierto");
		hoverDifficulty(context);
		shot(context, "dificultad_02_hardcore_extremo");
		clickDifficulty(context);
		expect(context, "Difícil", "en hardcore, un clic: Difícil");
		check(context.computeOnClient(mc -> !state(mc).getGameRules().get(Ladder.EXTREMO_RULE)), "Difícil quita la bandera");
		hoverDifficulty(context);
		shot(context, "dificultad_03_hardcore_dificil");
		clickDifficulty(context);
		expect(context, "Extremo", "en hardcore, otro clic: Extremo otra vez");
		context.setScreen(TitleScreen::new);
	}

	private static WorldCreationUiState state(Minecraft mc) {
		return ((CreateWorldScreen) mc.gui.screen()).getUiState();
	}

	// ------------------------------------------------------------------ a hardcore world on Extremo, saved and opened again

	private static void hardcoreWorld(ClientGameTestContext context) {
		TestWorldSave save;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().adjustSettings(settings -> {
			settings.setGameMode(WorldCreationUiState.SelectedGameMode.HARDCORE);
			settings.getGameRules().set(Ladder.EXTREMO_RULE, true, null);
		}).create()) {
			save = singleplayer.getWorldSave();
			TestServerContext server = singleplayer.getServer();
			context.waitTicks(20);
			check(server.computeOnServer(s -> s.isHardcore() && Ladder.world(s) == Ladder.EXTREMO), "el mundo hardcore nace en Extremo");
			check(context.computeOnClient(mc -> dev.forja.client.LadderClient.extremo()), "el cliente sabe que es Extremo");
			context.waitFor(mc -> mc.gui.overlay() == null, 600);
			openWorldOptions(context);
			expect(context, "Extremo", "las opciones del mundo dicen Extremo");
			check(context.computeOnClient(mc -> !difficultyButton(mc).active), "en hardcore, bloqueado como en vanilla");
			shot(context, "dificultad_04_opciones_hardcore_extremo");
			context.setScreen(() -> null);
		}
		try (TestSingleplayerContext again = save.open()) {
			context.waitTicks(20);
			check(again.getServer().computeOnServer(s -> s.isHardcore() && Ladder.world(s) == Ladder.EXTREMO),
				"guardado, cerrado y abierto de nuevo, sigue en Extremo");
			log("dificultad: el mundo hardcore vuelve en Extremo");
		}
	}

	// ------------------------------------------------------------------ an ordinary world, by its world options

	private static void ordinaryWorld(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			context.waitTicks(20);
			server.runCommand("difficulty normal");
			context.waitFor(mc -> mc.gui.overlay() == null, 600);
			openWorldOptions(context);
			expect(context, "Normal", "las opciones del mundo, en Normal");
			clickDifficulty(context);
			context.waitTicks(5);
			expect(context, "Difícil", "un clic: Difícil");
			check(server.computeOnServer(s -> Ladder.world(s) == Ladder.DIFICIL), "el servidor, en Difícil");
			clickDifficulty(context);
			context.waitTicks(5);
			expect(context, "Extremo", "otro clic: Extremo");
			check(server.computeOnServer(s -> s.getWorldData().getDifficulty() == Difficulty.HARD && Ladder.world(s) == Ladder.EXTREMO),
				"el servidor, en Extremo (HARD con la bandera)");
			shot(context, "dificultad_05_opciones_extremo");
			clickDifficulty(context);
			context.waitTicks(5);
			expect(context, "Pacífico", "y tras Extremo vuelve a Pacífico");
			check(server.computeOnServer(s -> Ladder.world(s) == Ladder.PACIFICO), "el servidor, en Pacífico, sin bandera");
			context.setScreen(() -> null);
		}
	}

	private static void openWorldOptions(ClientGameTestContext context) {
		context.setScreen(() -> new PauseScreen(true));
		context.clickScreenButton("menu.options");
		context.clickScreenButton("options.worldOptions.button");
		context.waitForScreen(WorldOptionsScreen.class);
		context.waitTicks(5);
	}

	// ------------------------------------------------------------------ the button

	private static CycleButton<?> difficultyButton(Minecraft mc) {
		for (GuiEventListener child : mc.gui.screen().children()) {
			CycleButton<?> found = find(child);
			if (found != null) {
				return found;
			}
		}
		throw new AssertionError("no hay botón de dificultad en " + mc.gui.screen());
	}

	private static CycleButton<?> find(GuiEventListener child) {
		if (child instanceof CycleButton<?> button && button.getValue() instanceof Difficulty) {
			return button;
		}
		if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container) {
			for (GuiEventListener inner : container.children()) {
				CycleButton<?> found = find(inner);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** A real click: the cursor on the button's middle, the left button pressed and let go. */
	private static void clickDifficulty(ClientGameTestContext context) {
		clickWidget(context, w -> w instanceof CycleButton<?> b && b.getValue() instanceof Difficulty);
	}

	private static void clickWidget(ClientGameTestContext context, java.util.function.Predicate<AbstractWidget> which) {
		double[] point = context.computeOnClient(mc -> {
			AbstractWidget widget = null;
			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof AbstractWidget candidate && which.test(candidate)) {
					widget = candidate;
				}
			}
			if (widget == null && which.test(difficultyButton(mc))) {
				widget = difficultyButton(mc);
			}
			if (widget == null) {
				throw new AssertionError("no está el botón buscado en " + mc.gui.screen());
			}
			// the cursor is in the window's screen units, which the GUI is scaled from (MouseHandler.getScaledXPos)
			var window = mc.getWindow();
			double sx = (double) window.getScreenWidth() / window.getGuiScaledWidth();
			double sy = (double) window.getScreenHeight() / window.getGuiScaledHeight();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * sx, (widget.getY() + widget.getHeight() / 2.0) * sy};
		});
		context.getInput().setCursorPos(point[0], point[1]);
		context.waitTick();
		context.getInput().pressMouse(0);
		context.waitTicks(2);
	}

	private static void expect(ClientGameTestContext context, String label, String what) {
		String shown = context.computeOnClient(mc -> difficultyButton(mc).getMessage().getString());
		log("dificultad: " + what + " → \"" + shown + "\"");
		check(shown.endsWith(label), what + ": el botón dice \"" + shown + "\", se esperaba " + label);
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.waitTicks(2);
		context.takeScreenshot(name);
	}

	/** The cursor resting on the difficulty button long enough for its hint to show. */
	private static void hoverDifficulty(ClientGameTestContext context) {
		double[] point = context.computeOnClient(mc -> {
			CycleButton<?> button = difficultyButton(mc);
			var window = mc.getWindow();
			double sx = (double) window.getScreenWidth() / window.getGuiScaledWidth();
			double sy = (double) window.getScreenHeight() / window.getGuiScaledHeight();
			return new double[] {(button.getX() + button.getWidth() / 2.0) * sx, (button.getY() + button.getHeight() / 2.0) * sy};
		});
		context.getInput().setCursorPos(point[0], point[1]);
		context.waitTicks(25);
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
	}

	private static void log(String message) {
		System.out.println("[forja-test] " + message);
	}
}
