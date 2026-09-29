package dev.forja.client;

import java.util.ArrayList;
import java.util.List;

import dev.forja.clase.ActiveSkill;
import dev.forja.clase.ClassData;
import dev.forja.clase.ClassNetwork;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.ClassSkills;
import dev.forja.clase.ClassStat;
import dev.forja.clase.PlayerClass;
import dev.forja.clase.Talent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The talent tree of the player's class (key K): three branches down the panel, the skill node under them,
 * and on the right the class's base numbers and its two skills. Every number in a tooltip comes from the
 * enum the game reads (clase/Talent, clase/ActiveSkill). A click on a node that can be learned asks the
 * server, which checks it again; the screen redraws from the synced attachment when the answer comes.
 */
public class TalentTreeScreen extends Screen {
	/** Where the three branches sit across the panel, and the rows of their nodes. */
	static final int[] COLUMNS = {48, 124, 200};
	static final int[] ROWS = {40, 80, 120};
	static final int SKILL_Y = 168;
	static final int SIDE_X = 252;

	private int left;
	private int top;

	public TalentTreeScreen() {
		super(Component.translatable("gui.forja.clase.arbol"));
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

	private @Nullable Player player() {
		return this.minecraft == null ? null : this.minecraft.player;
	}

	/** Where a node's frame is, relative to the panel. */
	static int[] nodeAt(Talent talent) {
		if (talent.isSkill()) {
			return new int[] {COLUMNS[1] - ClassGui.NODE / 2, SKILL_Y};
		}
		return new int[] {COLUMNS[talent.branch] - ClassGui.NODE / 2, ROWS[talent.tier - 1]};
	}

	/** The middle of a node's frame on screen, for the client test's clicks. */
	public int[] nodeCentre(Talent talent) {
		int[] at = nodeAt(talent);
		return new int[] {this.left + at[0] + ClassGui.NODE / 2, this.top + at[1] + ClassGui.NODE / 2};
	}

	private ClassGui.NodeState state(ClassData data, Talent talent) {
		if (data.has(talent)) {
			return ClassGui.NodeState.LEARNED;
		}
		Player player = this.player();
		return player != null && ClassProgress.check(player, talent) == ClassProgress.Refusal.NONE ? ClassGui.NodeState.AVAILABLE : ClassGui.NodeState.LOCKED;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		ClassGui.panel(g, this.left, this.top, ClassGui.TREE_V);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		Player player = this.player();
		ClassData data = ClassProgress.data(player);
		PlayerClass clazz = data.playerClass();
		if (player == null || clazz == null) {
			return;
		}
		int x = this.left;
		int y = this.top;
		// The header: the class, its level and how far to the next, and the points to spend.
		g.item(clazz.icon(), x + 8, y + 4);
		g.text(this.font, clazz.displayName(), x + 28, y + 4, 0xFFFFFFFF, true);
		boolean top = data.level() >= ClassProgress.MAX_LEVEL;
		int from = ClassProgress.totalFor(data.level());
		int to = ClassProgress.totalFor(Math.min(ClassProgress.MAX_LEVEL, data.level() + 1));
		ClassGui.small(g, this.font, top ? Component.translatable("gui.forja.clase.nivel_maximo", data.level())
			: Component.translatable("gui.forja.clase.nivel", data.level(), data.xp() - from, to - from), x + 28, y + 14, ClassGui.INK_SOFT);
		int barX = x + 150;
		ClassGui.line(g, barX, y + 9, barX + 89, y + 9, 0xFF2A2320);
		g.fill(barX, y + 8, barX + 90, y + 13, 0xFF2A2320);
		float share = top ? 1.0F : (data.xp() - from) / (float) Math.max(1, to - from);
		g.fill(barX + 1, y + 9, barX + 1 + Math.round(88 * Math.max(0.0F, Math.min(1.0F, share))), y + 12, 0xFF000000 | clazz.color);
		Component points = Component.translatable("gui.forja.clase.puntos", data.points());
		g.text(this.font, points, x + ClassGui.PANEL_W - 10 - this.font.width(points), y + 7, data.points() > 0 ? ClassGui.GOLD : ClassGui.INK_DIM, true);

		// The branches: their names, the lines between the nodes, the nodes.
		for (int branch = 0; branch < 3; branch++) {
			Component name = Component.translatable("gui.forja.clase.rama." + clazz.id() + "." + branch);
			int width = Math.round(this.font.width(name) * 0.75F);
			ClassGui.small(g, this.font, name, x + COLUMNS[branch] - width / 2, y + 29, ClassGui.GOLD);
		}
		Talent skillNode = null;
		for (Talent talent : clazz.talents()) {
			if (talent.isSkill()) {
				skillNode = talent;
				continue;
			}
			Talent parent = talent.parent();
			if (parent != null) {
				int[] at = nodeAt(talent);
				int cx = x + at[0] + ClassGui.NODE / 2;
				int colour = data.has(talent) ? ClassGui.GOLD : data.has(parent) ? 0xFFB08A50 : 0xFF4A4038;
				g.fill(cx - 1, y + at[1] - 14, cx + 1, y + at[1], colour);
			}
		}
		if (skillNode != null) {
			boolean open = skillNode.prerequisitesMet(data.mask());
			int colour = data.has(skillNode) ? ClassGui.GOLD : open ? 0xFFB08A50 : 0xFF4A4038;
			int below = ROWS[2] + ClassGui.NODE;
			for (int column : COLUMNS) {
				g.fill(x + column - 1, y + below, x + column + 1, y + 157, colour);
			}
			g.fill(x + COLUMNS[0] - 1, y + 156, x + COLUMNS[2] + 1, y + 158, colour);
			g.fill(x + COLUMNS[1] - 1, y + 156, x + COLUMNS[1] + 1, y + SKILL_Y, colour);
		}
		Talent pointed = null;
		for (Talent talent : clazz.talents()) {
			int[] at = nodeAt(talent);
			ClassGui.NodeState state = this.state(data, talent);
			ClassGui.node(g, x + at[0], y + at[1], state, talent.isSkill());
			g.item(talent.icon(), x + at[0] + 5, y + at[1] + 5);
			if (state == ClassGui.NodeState.LOCKED) {
				g.fill(x + at[0] + 5, y + at[1] + 5, x + at[0] + 21, y + at[1] + 21, 0x90201A16);
			}
			// The cost in the corner, while it is still to be learned.
			if (state != ClassGui.NodeState.LEARNED) {
				String cost = Integer.toString(talent.cost());
				g.text(this.font, cost, x + at[0] + ClassGui.NODE - 2 - this.font.width(cost), y + at[1] + ClassGui.NODE - 8,
					state == ClassGui.NodeState.AVAILABLE ? ClassGui.GOLD : ClassGui.INK_DIM, true);
			}
			if (ClassGui.over(mouseX, mouseY, x + at[0], y + at[1], ClassGui.NODE, ClassGui.NODE)) {
				pointed = talent;
			}
		}

		// The side: the class's base, then its two skills and their keys.
		int sideY = y + 28;
		ClassGui.small(g, this.font, Component.translatable("gui.forja.clase.base"), x + SIDE_X, sideY, ClassGui.GOLD);
		sideY += 9;
		for (ClassStat.Mod mod : clazz.base) {
			ClassGui.small(g, this.font, mod.line(), x + SIDE_X, sideY, 0xFFFFFFFF);
			sideY += 7;
		}
		int skillsY = y + 128;
		ClassGui.small(g, this.font, Component.translatable("gui.forja.clase.habilidades"), x + SIDE_X, skillsY, ClassGui.GOLD);
		ActiveSkill pointedSkill = null;
		for (int slot = 1; slot <= 2; slot++) {
			ActiveSkill skill = slot == 1 ? clazz.firstSkill : clazz.secondSkill();
			boolean have = ClassSkills.skill(player, slot) == skill;
			int rowY = skillsY + 10 + (slot - 1) * 30;
			ClassGui.hudSlot(g, x + SIDE_X - 2, rowY, have && ClassSkills.waiting(player, slot) == 0);
			g.item(skill.icon(), x + SIDE_X + 1, rowY + 3);
			if (!have) {
				g.fill(x + SIDE_X + 1, rowY + 3, x + SIDE_X + 17, rowY + 19, 0x90201A16);
			}
			ClassGui.small(g, this.font, skill.displayName(), x + SIDE_X + 23, rowY + 2, have ? 0xFFFFFFFF : ClassGui.INK_DIM);
			ClassGui.small(g, this.font, ClassClient.keyName(slot), x + SIDE_X + 23, rowY + 11, ClassGui.INK_SOFT);
			if (ClassGui.over(mouseX, mouseY, x + SIDE_X - 2, rowY, 120, ClassGui.HUD_SIZE)) {
				pointedSkill = skill;
			}
		}

		if (pointed != null) {
			g.setTooltipForNextFrame(GuideText.wrap(this.font, this.tooltip(player, data, pointed)), mouseX, mouseY);
		} else if (pointedSkill != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(pointedSkill.displayName().copy().withColor(0xFF000000 | clazz.color));
			lines.addAll(pointedSkill.lines());
			g.setTooltipForNextFrame(GuideText.wrap(this.font, lines), mouseX, mouseY);
		}
	}

	/** Its name, where it sits and what it costs, what it does, what it needs, and whether it can be had now. */
	private List<Component> tooltip(Player player, ClassData data, Talent talent) {
		List<Component> lines = new ArrayList<>();
		lines.add(talent.displayName().copy().withColor(0xFF000000 | talent.owner.color));
		lines.add(talent.isSkill()
			? Component.translatable("gui.forja.talento.posicion_habilidad", talent.cost()).withColor(ClassGui.INK_SOFT)
			: Component.translatable("gui.forja.talento.posicion", talent.branchName(), talent.tier, talent.cost()).withColor(ClassGui.INK_SOFT));
		lines.addAll(talent.effectLines());
		Talent parent = talent.parent();
		if (parent != null) {
			lines.add(Component.translatable("gui.forja.talento.necesita", parent.displayName())
				.withColor(data.has(parent) ? ClassGui.INK_SOFT : ClassGui.RED));
		} else if (talent.isSkill()) {
			lines.add(Component.translatable("gui.forja.talento.necesita_rama", Talent.SKILL_NEEDS_TIER)
				.withColor(talent.prerequisitesMet(data.mask()) ? ClassGui.INK_SOFT : ClassGui.RED));
		}
		ClassProgress.Refusal why = ClassProgress.check(player, talent);
		lines.add(switch (why) {
			case NONE -> Component.translatable("gui.forja.talento.aprender").withColor(ClassGui.GOLD);
			case ALREADY -> Component.translatable("gui.forja.talento.no.already").withColor(ClassGui.GREEN);
			case POINTS -> Component.translatable("gui.forja.talento.no.points", data.points()).withColor(ClassGui.RED);
			default -> Component.translatable("gui.forja.talento.no." + why.name().toLowerCase(java.util.Locale.ROOT)).withColor(ClassGui.RED);
		});
		return lines;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Player player = this.player();
		PlayerClass clazz = ClassProgress.clazz(player);
		if (event.button() == 0 && player != null && clazz != null) {
			for (Talent talent : clazz.talents()) {
				int[] at = nodeAt(talent);
				if (ClassGui.over(event.x(), event.y(), this.left + at[0], this.top + at[1], ClassGui.NODE, ClassGui.NODE)) {
					if (ClassProgress.check(player, talent) == ClassProgress.Refusal.NONE && ClientPlayNetworking.canSend(ClassNetwork.Action.TYPE)) {
						ClientPlayNetworking.send(new ClassNetwork.Action(ClassNetwork.Action.LEARN, talent.id()));
						this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (ClassClient.TREE != null && ClassClient.TREE.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
