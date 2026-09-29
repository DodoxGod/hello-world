package dev.forja.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.forja.Forja;
import dev.forja.clase.ActiveSkill;
import dev.forja.clase.ClassNetwork;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.ClassSkills;
import dev.forja.clase.PlayerClass;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The client half of the classes: the three keys (by default K the tree, or the choice with no class yet; V and
 * B the two skills; all rebindable in Controls under "Forja: clases"), what the server says (a toast, the
 * medallion's change screen), and the two skills beside the hotbar with their waits.
 */
public final class ClassClient {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Forja.id("clases"));
	public static @Nullable KeyMapping TREE;
	public static @Nullable KeyMapping SKILL_1;
	public static @Nullable KeyMapping SKILL_2;

	private ClassClient() {
	}

	public static void register() {
		// K, V and B are only the defaults (Andy, 2026-09-29): all three are KeyMappings in the Forja category, so
		// they are rebound in Controls, and nothing anywhere compares a key code with them. Texts name them with
		// ClassProgress.key, which follows the binding.
		TREE = KeyMappingHelper.registerKeyMapping(new KeyMapping(ClassProgress.KEY_TREE, InputConstants.Type.KEYSYM,
			org.lwjgl.glfw.GLFW.GLFW_KEY_K, CATEGORY));
		SKILL_1 = KeyMappingHelper.registerKeyMapping(new KeyMapping(ClassProgress.KEY_SKILL_1, InputConstants.Type.KEYSYM,
			org.lwjgl.glfw.GLFW.GLFW_KEY_V, CATEGORY));
		SKILL_2 = KeyMappingHelper.registerKeyMapping(new KeyMapping(ClassProgress.KEY_SKILL_2, InputConstants.Type.KEYSYM,
			org.lwjgl.glfw.GLFW.GLFW_KEY_B, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TREE.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					openClassScreen(client);
				}
			}
			while (SKILL_1.consumeClick()) {
				useSkill(client, 1);
			}
			while (SKILL_2.consumeClick()) {
				useSkill(client, 2);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ClassNetwork.Notice.TYPE, (notice, context) -> {
			Minecraft client = context.client();
			switch (notice.toast()) {
				case OPEN_CHANGE -> client.gui.setScreen(new ClassChoiceScreen(true));
				case LEVEL, CHOSEN -> {
					PlayerClass clazz = PlayerClass.byId(notice.clazz());
					if (clazz != null) {
						client.gui.toastManager().addToast(new ClassToast(clazz, notice.toast(), notice.level()));
					}
					// A class just taken: straight on to its tree, where the first point is waiting.
					if (notice.toast() == ClassNetwork.Toast.CHOSEN && client.gui.screen() instanceof ClassChoiceScreen) {
						client.gui.setScreen(new TalentTreeScreen());
					}
				}
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Forja.id("habilidades_de_clase"), new SkillHud());
	}

	/** The tree, or the choice for a player without a class yet. */
	public static void openClassScreen(Minecraft client) {
		client.gui.setScreen(ClassProgress.clazz(client.player) == null ? new ClassChoiceScreen(false) : new TalentTreeScreen());
	}

	private static void useSkill(Minecraft client, int slot) {
		if (client.player == null || client.gui.screen() != null) {
			return;
		}
		if (ClassProgress.clazz(client.player) == null) {
			client.gui.hud.setOverlayMessage(Component.translatable("gui.forja.habilidad.sin_clase", ClassProgress.key(ClassProgress.KEY_TREE)), false);
			return;
		}
		if (ClientPlayNetworking.canSend(ClassNetwork.Action.TYPE)) {
			ClientPlayNetworking.send(new ClassNetwork.Action(ClassNetwork.Action.SKILL, Integer.toString(slot)));
		}
	}

	/** The key a skill is on, as the controls screen names it. */
	public static Component keyName(int slot) {
		KeyMapping key = slot == 1 ? SKILL_1 : SKILL_2;
		return key == null ? Component.empty() : Component.translatable("gui.forja.clase.tecla", key.getTranslatedKeyMessage());
	}

	/**
	 * The two skills, left of the off-hand slot: the icon, the wait running down over it like a cooldown, and
	 * the key under it. Only with a class; the second only once learned.
	 */
	private static final class SkillHud implements HudElement {
		@Override
		public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
			Minecraft client = Minecraft.getInstance();
			LocalPlayer player = client.player;
			if (player == null || player.isSpectator() || ClassProgress.clazz(player) == null) {
				return;
			}
			int x = g.guiWidth() / 2 - 91 - 29 - 2 * (ClassGui.HUD_SIZE + 2) - 4;
			int y = g.guiHeight() - ClassGui.HUD_SIZE - 1;
			for (int slot = 1; slot <= 2; slot++) {
				ActiveSkill skill = ClassSkills.skill(player, slot);
				int at = x + (slot - 1) * (ClassGui.HUD_SIZE + 2);
				if (skill == null) {
					continue;
				}
				int waiting = ClassSkills.waiting(player, slot);
				ClassGui.hudSlot(g, at, y, waiting == 0);
				g.item(skill.icon(), at + 3, y + 3);
				if (waiting > 0) {
					float share = Math.min(1.0F, waiting / (float) skill.cooldownTicks());
					int cover = Math.round(16 * share);
					g.fill(at + 3, y + 3 + 16 - cover, at + 19, y + 19, 0xA0101010);
					String seconds = Integer.toString((waiting + 19) / 20);
					g.text(client.font, seconds, at + 11 - client.font.width(seconds) / 2, y + 7, 0xFFFFFFFF, true);
				}
			}
		}
	}
}
