package dev.forja.client;

import java.util.List;

import dev.forja.clase.ActiveSkill;
import dev.forja.clase.ClassNetwork;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.ClassStat;
import dev.forja.clase.PlayerClass;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The class choice (docs/CLASES.md): the seven classes down the left, and on the right the one pointed at —
 * what it is, its base numbers and its two skills, all read from the enums the game plays by. The first time
 * it comes from the guide or the K key and costs nothing; opened by the Emblema del olvido it is a change,
 * and says what it costs before anything is spent.
 */
public class ClassChoiceScreen extends Screen {
	static final int LIST_X = 8;
	static final int LIST_Y = 28;
	static final int LIST_STEP = 25;
	static final int PAGE_X = 122;
	static final int CONFIRM_X = 290;
	static final int CONFIRM_Y = 184;

	private final boolean change;
	private PlayerClass selected;
	private int left;
	private int top;

	public ClassChoiceScreen(boolean change) {
		super(Component.translatable(change ? "gui.forja.clase.cambiar" : "gui.forja.clase.elegir"));
		this.change = change;
		PlayerClass current = ClassProgress.clazz(net.minecraft.client.Minecraft.getInstance().player);
		this.selected = current != null ? current : PlayerClass.values()[0];
	}

	public boolean change() {
		return this.change;
	}

	public PlayerClass selected() {
		return this.selected;
	}

	@Override
	protected void init() {
		this.left = (this.width - ClassGui.PANEL_W) / 2;
		this.top = (this.height - ClassGui.PANEL_H) / 2;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The middle of a class's card on screen, for the client test's clicks. */
	public int[] cardCentre(PlayerClass clazz) {
		return new int[] {this.left + LIST_X + ClassGui.CARD_W / 2, this.top + LIST_Y + clazz.ordinal() * LIST_STEP + ClassGui.CARD_H / 2};
	}

	public int[] confirmCentre() {
		return new int[] {this.left + CONFIRM_X + ClassGui.BUTTON_W / 2, this.top + CONFIRM_Y + ClassGui.BUTTON_H / 2};
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		ClassGui.panel(g, this.left, this.top, ClassGui.CHOICE_V);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int x = this.left;
		int y = this.top;
		g.text(this.font, this.title, x + 10, y + 7, 0xFFFFFFFF, true);
		PlayerClass current = ClassProgress.clazz(this.minecraft.player);
		for (PlayerClass clazz : PlayerClass.values()) {
			int cardY = y + LIST_Y + clazz.ordinal() * LIST_STEP;
			boolean pointed = ClassGui.over(mouseX, mouseY, x + LIST_X, cardY, ClassGui.CARD_W, ClassGui.CARD_H);
			ClassGui.card(g, x + LIST_X, cardY, clazz == this.selected ? 2 : pointed ? 1 : 0);
			g.item(clazz.icon(), x + LIST_X + 3, cardY + 3);
			g.text(this.font, clazz.displayName(), x + LIST_X + 22, cardY + 7, 0xFFFFFFFF, true);
			if (clazz == current) {
				ClassGui.small(g, this.font, Component.literal("●"), x + LIST_X + ClassGui.CARD_W - 9, cardY + 8, ClassGui.GOLD);
			}
		}

		// The page of the class pointed at: what it is, its numbers, its skills.
		PlayerClass clazz = this.selected;
		int pageY = y + 26;
		ClassGui.item(g, clazz.icon(), x + PAGE_X, pageY, 1.5F);
		g.text(this.font, clazz.displayName(), x + PAGE_X + 30, pageY + 3, 0xFFFFFFFF, true);
		ClassGui.small(g, this.font, Component.translatable("gui.forja.clase." + clazz.id() + ".lema"), x + PAGE_X + 30, pageY + 14, ClassGui.INK_SOFT);
		int width = ClassGui.PANEL_W - PAGE_X - 10;
		int lineY = ClassGui.smallWrapped(g, this.font, clazz.description(), x + PAGE_X, pageY + 28, width, ClassGui.INK) + 4;
		ClassGui.small(g, this.font, Component.translatable("gui.forja.clase.base"), x + PAGE_X, lineY, ClassGui.GOLD);
		lineY += 9;
		List<ClassStat.Mod> base = clazz.base;
		int half = (base.size() + 1) / 2;
		for (int i = 0; i < base.size(); i++) {
			int column = i < half ? 0 : 1;
			int row = i < half ? i : i - half;
			ClassGui.small(g, this.font, base.get(i).line(), x + PAGE_X + column * (width / 2), lineY + row * 7, 0xFFFFFFFF);
		}
		lineY += half * 7 + 5;
		ClassGui.small(g, this.font, Component.translatable("gui.forja.clase.habilidades"), x + PAGE_X, lineY, ClassGui.GOLD);
		lineY += 9;
		for (int slot = 1; slot <= 2; slot++) {
			ActiveSkill skill = slot == 1 ? clazz.firstSkill : clazz.secondSkill();
			ClassGui.item(g, skill.icon(), x + PAGE_X, lineY, 0.75F);
			ClassGui.small(g, this.font, Component.translatable(slot == 1 ? "gui.forja.clase.habilidad_1" : "gui.forja.clase.habilidad_2",
				skill.displayName(), ClassClient.keyName(slot)), x + PAGE_X + 15, lineY + 1, 0xFFFFFFFF);
			lineY = ClassGui.smallWrapped(g, this.font, skill.lines().get(0), x + PAGE_X + 15, lineY + 9, width - 15, ClassGui.INK_SOFT) + 3;
		}

		// What it costs, and the button.
		if (this.change) {
			ClassGui.smallWrapped(g, this.font, Component.translatable("gui.forja.clase.coste_cambio"), x + PAGE_X, y + CONFIRM_Y + 1,
				CONFIRM_X - PAGE_X - 6, ClassGui.INK_SOFT);
		}
		boolean pointed = ClassGui.over(mouseX, mouseY, x + CONFIRM_X, y + CONFIRM_Y, ClassGui.BUTTON_W, ClassGui.BUTTON_H);
		ClassGui.button(g, x + CONFIRM_X, y + CONFIRM_Y, pointed ? 1 : 0);
		Component label = Component.translatable(this.change ? "gui.forja.clase.boton_cambiar" : "gui.forja.clase.boton_elegir");
		g.text(this.font, label, x + CONFIRM_X + (ClassGui.BUTTON_W - this.font.width(label)) / 2, y + CONFIRM_Y + 6, 0xFFFFFFFF, true);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (PlayerClass clazz : PlayerClass.values()) {
				int cardY = this.top + LIST_Y + clazz.ordinal() * LIST_STEP;
				if (ClassGui.over(event.x(), event.y(), this.left + LIST_X, cardY, ClassGui.CARD_W, ClassGui.CARD_H)) {
					this.selected = clazz;
					this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					return true;
				}
			}
			if (ClassGui.over(event.x(), event.y(), this.left + CONFIRM_X, this.top + CONFIRM_Y, ClassGui.BUTTON_W, ClassGui.BUTTON_H)) {
				this.confirm();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Asks the server for the class; it answers with a toast, and the tree opens then (client/ClassClient). */
	private void confirm() {
		if (ClientPlayNetworking.canSend(ClassNetwork.Action.TYPE)) {
			ClientPlayNetworking.send(new ClassNetwork.Action(ClassNetwork.Action.CHOOSE, this.selected.id()));
		}
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
