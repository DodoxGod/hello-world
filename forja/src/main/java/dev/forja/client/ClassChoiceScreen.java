package dev.forja.client;

import java.util.List;

import dev.forja.clase.ActiveSkill;
import dev.forja.clase.ClassDamage;
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
 * it comes from the guide or the class key (K by default) and costs nothing; opened by the Medallón del olvido
 * it is a change, and says what it costs before anything is spent.
 */
public class ClassChoiceScreen extends Screen {
	static final int LIST_X = 8;
	static final int LIST_Y = 28;
	static final int LIST_STEP = 25;
	static final int PAGE_X = 122;
	/**
	 * The button sits under the list of classes, in the left column's free foot. It used to sit in the page's
	 * bottom right corner, where the third skill had to wrap short of it and a change's cost was written over
	 * that skill's last lines and out through the frame.
	 */
	static final int CONFIRM_X = LIST_X + (ClassGui.CARD_W - ClassGui.BUTTON_W) / 2;
	static final int CONFIRM_Y = LIST_Y + 6 * LIST_STEP + 5;

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

	/** "Daño: cuerpo a cuerpo ×0,7 · magia ×1/3", with only the kinds of blow the class changes; null if none. */
	static @org.jspecify.annotations.Nullable Component damageFactors(PlayerClass clazz) {
		net.minecraft.network.chat.MutableComponent line = null;
		for (ClassDamage.Blow blow : ClassDamage.Blow.values()) {
			float factor = clazz.damageFactor(blow);
			if (Math.abs(factor - 1.0F) < 1.0E-4F) {
				continue;
			}
			Component part = Component.translatable("gui.forja.clase.golpe", blow.displayName(), ClassDamage.format(factor));
			if (line == null) {
				line = Component.translatable("gui.forja.clase.dano").append(" ").append(part);
			} else {
				line.append(" · ").append(part);
			}
		}
		return line;
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
		lineY += half * 7;
		// Andy's damage factors (clase/ClassDamage), only the ones that are not x1.
		Component factors = ClassChoiceScreen.damageFactors(clazz);
		if (factors != null) {
			ClassGui.small(g, this.font, factors, x + PAGE_X, lineY, 0xFFE0533D);
			lineY += 7;
		}
		lineY += 5;
		ClassGui.small(g, this.font, Component.translatable("gui.forja.clase.habilidades"), x + PAGE_X, lineY, ClassGui.GOLD);
		lineY += 9;
		for (int slot = 1; slot <= 2; slot++) {
			ActiveSkill skill = clazz.skill(slot);
			ClassGui.item(g, skill.icon(), x + PAGE_X, lineY, 0.75F);
			ClassGui.small(g, this.font, Component.translatable(slot == 1 ? "gui.forja.clase.habilidad_1" : "gui.forja.clase.habilidad_2",
				skill.displayName(), ClassClient.keyName(slot)), x + PAGE_X + 15, lineY + 1, 0xFFFFFFFF);
			lineY = ClassGui.smallWrapped(g, this.font, skill.def().effect(false), x + PAGE_X + 15, lineY + 7, width - 15, ClassGui.INK_SOFT) + 1;
		}
		// The third key: one of three ultimates, one at the end of each senda. It runs down beside the button.
		List<ActiveSkill> ultimates = clazz.ultimates();
		for (int i = 0; i < ultimates.size(); i++) {
			ClassGui.item(g, ultimates.get(i).icon(), x + PAGE_X + (i % 2) * 6, lineY + (i / 2) * 6, 0.45F);
		}
		ClassGui.small(g, this.font, Component.translatable("gui.forja.clase.habilidad_3", ClassClient.keyName(3)), x + PAGE_X + 15, lineY + 1, 0xFFFFFFFF);
		ClassGui.smallWrapped(g, this.font, Component.translatable("gui.forja.clase.habilidad_3_cuales", ultimates.get(0).displayName(),
			ultimates.get(1).displayName(), ultimates.get(2).displayName()), x + PAGE_X + 15, lineY + 7, CONFIRM_X - PAGE_X - 21, ClassGui.INK_SOFT);

		// The button, under the list. A change carries its price on its face — the Medallón del olvido it spends —
		// and says the whole of what it costs under the mouse, before anything is spent; and, when the page has
		// room left under the skills, on the page as well.
		boolean pointed = ClassGui.over(mouseX, mouseY, x + CONFIRM_X, y + CONFIRM_Y, ClassGui.BUTTON_W, ClassGui.BUTTON_H);
		ClassGui.button(g, x + CONFIRM_X, y + CONFIRM_Y, pointed ? 1 : 0);
		Component label = Component.translatable(this.change ? "gui.forja.clase.boton_cambiar" : "gui.forja.clase.boton_elegir");
		int labelX = x + CONFIRM_X + (ClassGui.BUTTON_W - this.font.width(label)) / 2;
		if (this.change) {
			labelX += 6;
			ClassGui.item(g, new net.minecraft.world.item.ItemStack(dev.forja.registry.ModItems.MEDALLON_DEL_OLVIDO), labelX - 15, y + CONFIRM_Y + 4, 0.75F);
			Component cost = Component.translatable("gui.forja.clase.coste_cambio");
			int room = y + ClassGui.PANEL_H - 9 - (lineY + 3);
			if (this.font.split(cost, Math.round(width / 0.75F)).size() * 7 <= room) {
				ClassGui.smallWrapped(g, this.font, cost, x + PAGE_X, lineY + 3, width, ClassGui.GOLD);
			}
			if (pointed) {
				g.setTooltipForNextFrame(this.font, this.font.split(cost, 220), mouseX, mouseY);
			}
		}
		g.text(this.font, label, labelX, y + CONFIRM_Y + 6, 0xFFFFFFFF, true);
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
