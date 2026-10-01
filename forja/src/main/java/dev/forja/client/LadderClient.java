package dev.forja.client;

import dev.forja.difficulty.Ladder;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.HasDifficultyReaction;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;

/**
 * The difficulty ladder on the client (difficulty/Ladder): Minecraft's own difficulty button goes one step past Difícil,
 * to Extremo, wherever it is (the world options of the pause menu's Options, and the world creation screen), and in a
 * hardcore world being created it toggles Difícil and Extremo, the two a hardcore world may be. What the world's flag
 * is comes from the server ({@link Ladder.Sync}).
 */
public final class LadderClient {
	/** The label of the fifth step, and what it says on hover. */
	public static final Component EXTREMO = Component.translatable("options.difficulty.forja_extremo");
	public static final Component EXTREMO_INFO = Component.translatable("options.difficulty.forja_extremo.info");
	public static final Component HARDCORE_INFO = Component.translatable("options.difficulty.forja_extremo.hardcore");

	/** The world's Extremo flag, as the server last said. */
	private static volatile boolean extremo;

	private LadderClient() {
	}

	public static boolean extremo() {
		return extremo;
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Ladder.Sync.TYPE, (payload, context) -> {
			extremo = payload.extremo();
			if (context.client().gui.screen() instanceof HasDifficultyReaction screen) {
				screen.onDifficultyChanged();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> extremo = false);
		Ladder.clientView(() -> {
			Minecraft minecraft = Minecraft.getInstance();
			return minecraft.level == null ? Ladder.DIFICIL : Ladder.of(minecraft.level.getDifficulty(), extremo);
		});
	}

	/** What a difficulty button attached to the ladder does and knows: one for the world options, one for world creation. */
	public interface Hook {
		/** Whether the world (or the world being made) is on Extremo, given HARD. */
		boolean extremo();

		/** Puts the world on this level. */
		void choose(Ladder level);

		/** Whether only Difícil and Extremo may be chosen (a hardcore world). */
		default boolean hardcore() {
			return false;
		}
	}

	/** The duck interface CycleButtonMixin puts on every CycleButton. */
	public interface Attached {
		Hook forja$ladder();

		void forja$ladder(Hook hook);
	}

	/** The button's next step: one along the five (back with shift or the wheel up), or Difícil ⇄ Extremo in hardcore. */
	public static Ladder next(Ladder now, int delta, boolean hardcore) {
		if (hardcore) {
			return now == Ladder.EXTREMO ? Ladder.DIFICIL : Ladder.EXTREMO;
		}
		Ladder[] all = Ladder.values();
		return all[Math.floorMod(now.ordinal() + delta, all.length)];
	}

	/** A press on a ladder button: works out the next level, applies it and shows it. */
	@SuppressWarnings("unchecked")
	public static void cycle(CycleButton<?> button, Hook hook, int delta) {
		if (!(button.getValue() instanceof Difficulty difficulty)) {
			return;
		}
		Ladder chosen = next(Ladder.of(difficulty, hook.extremo()), delta, hook.hardcore());
		hook.choose(chosen);
		((CycleButton<Difficulty>) button).setValue(chosen.vanilla);
	}

	/** The world options' button (DifficultyButtons): the choice goes to the server, which says back what it did. */
	public static void attachInGame(CycleButton<Difficulty> button) {
		((Attached) button).forja$ladder(new Hook() {
			@Override
			public boolean extremo() {
				return extremo;
			}

			@Override
			public void choose(Ladder level) {
				// As vanilla's button does: shown at once, the server's answer puts it right if it refused.
				extremo = level == Ladder.EXTREMO;
				ClientPlayNetworking.send(new Ladder.Choose((byte) level.ordinal()));
			}
		});
		button.setValue(button.getValue());
	}

	/**
	 * The world creation screen's button: the choice is the screen's (its difficulty, and the flag as a game rule of the
	 * world to be, which it carries into level.dat). In hardcore, where vanilla shuts the button, it stays open as the
	 * Difícil ⇄ Extremo toggle.
	 */
	public static void attachCreation(CycleButton<Difficulty> button, WorldCreationUiState state) {
		((Attached) button).forja$ladder(new Hook() {
			@Override
			public boolean extremo() {
				return state.getGameRules().get(Ladder.EXTREMO_RULE);
			}

			@Override
			public void choose(Ladder level) {
				state.getGameRules().set(Ladder.EXTREMO_RULE, level == Ladder.EXTREMO, null);
				if (state.isHardcore()) {
					state.onChanged();
				} else {
					state.setDifficulty(level.vanilla);
				}
			}

			@Override
			public boolean hardcore() {
				return state.isHardcore();
			}
		});
		state.addListener(changed -> follow(button, changed));
		follow(button, state);
	}

	/** After every change on the creation screen: the hardcore toggle open, the flag only on HARD, the label and its hint. */
	private static void follow(CycleButton<Difficulty> button, WorldCreationUiState state) {
		boolean flag = state.getGameRules().get(Ladder.EXTREMO_RULE);
		if (flag && state.getDifficulty() != Difficulty.HARD) {
			// off HARD (out of hardcore onto an easier difficulty) the flag would mean nothing, and come back later
			state.getGameRules().set(Ladder.EXTREMO_RULE, false, null);
			flag = false;
		}
		button.active = !state.isDebug();
		button.setValue(state.getDifficulty());
		if (state.isHardcore()) {
			button.setTooltip(Tooltip.create(HARDCORE_INFO));
		} else if (flag) {
			button.setTooltip(Tooltip.create(EXTREMO_INFO));
		}
	}
}
