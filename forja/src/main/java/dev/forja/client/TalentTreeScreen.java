package dev.forja.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.forja.clase.ActiveSkill;
import dev.forja.clase.ClassData;
import dev.forja.clase.ClassNetwork;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.ClassSkills;
import dev.forja.clase.ClassStat;
import dev.forja.clase.ClassTree;
import dev.forja.clase.PlayerClass;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The big tree of the player's class (key K; docs/ARBOLES.md, "La pantalla del árbol"). The tree is a canvas:
 * dragged with the mouse (left on empty space, right anywhere) and zoomed with the wheel towards the cursor;
 * Home or a double click on empty space brings it back. What is learned and the lines between it are gold; what
 * can be learned now pulses. Every tooltip reads its numbers from the tree's file (clase/ClassTree).
 *
 * <p>"Probar" lets nodes be picked without spending anything (they show in blue, with what they would add); "Aplicar"
 * asks the server for all of them in order, "Descartar" drops them. The plan is kept while the game runs. The search
 * box lights the nodes whose name or text holds what is typed. "Hitos" lists the milestones. Opened by the Vela del
 * olvido, the screen is in its "forget" form: learned nodes at the edge are picked in red and "Quitar" takes them off.
 */
public class TalentTreeScreen extends Screen {
	static final int HEADER = 30;
	static final int SIDE = 128;
	static final int MARGIN = 6;
	/** Pixels per tree unit at zoom 1. */
	static final float UNIT = 22.0F;
	static final float ZOOM_MIN = 0.4F;
	static final float ZOOM_MAX = 2.0F;
	static final int BLUE = 0xFF5AA8F0;
	static final int GOLD_LINE = 0xFFF0C24A;

	/** The plans of "Probar", one per class, kept while the game runs. */
	private static final Map<PlayerClass, List<String>> PLANS = new EnumMap<>(PlayerClass.class);

	private final boolean forgetting;
	private final List<String> forget = new ArrayList<>();
	private float zoom = 1.0F;
	private float panX;
	private float panY;
	private boolean trying;
	private boolean milestonesOpen;
	private String search = "";
	private int searchHit = -1;
	private @Nullable EditBox searchBox;
	private boolean dragging;
	private double dragFromX;
	private double dragFromY;
	private boolean dragMoved;

	public TalentTreeScreen() {
		this(false);
	}

	public TalentTreeScreen(boolean forgetting) {
		super(Component.translatable("gui.forja.clase.arbol"));
		this.forgetting = forgetting;
	}

	@Override
	protected void init() {
		this.searchBox = new EditBox(this.font, this.canvasRight() + MARGIN + 3, this.canvasTop() + 3, SIDE - 12, 14,
			Component.translatable("gui.forja.arbol.buscar"));
		this.searchBox.setHint(Component.translatable("gui.forja.arbol.buscar").withColor(ClassGui.INK_DIM));
		this.searchBox.setMaxLength(40);
		this.searchBox.setValue(this.search);
		this.searchBox.setResponder(text -> {
			this.search = text.trim().toLowerCase(Locale.ROOT);
			this.searchHit = -1;
		});
		this.addRenderableWidget(this.searchBox);
		if (this.zoom == 1.0F && this.panX == 0.0F && this.panY == 0.0F) {
			this.home();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private @Nullable Player player() {
		return this.minecraft == null ? null : this.minecraft.player;
	}

	// ------------------------------------------------------------------ the canvas

	private int canvasLeft() {
		return MARGIN;
	}

	private int canvasTop() {
		return HEADER + MARGIN;
	}

	private int canvasRight() {
		return this.width - SIDE - MARGIN;
	}

	private int canvasBottom() {
		return this.height - MARGIN;
	}

	private float centreX() {
		return (this.canvasLeft() + this.canvasRight()) / 2.0F + this.panX;
	}

	private float centreY() {
		return (this.canvasTop() + this.canvasBottom()) / 2.0F + this.panY;
	}

	/** Back to the origin, zoomed so the core and the trunks fit. */
	public void home() {
		float half = Math.min(this.canvasRight() - this.canvasLeft(), this.canvasBottom() - this.canvasTop()) / 2.0F;
		this.zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, half / (6.6F * UNIT)));
		this.panX = 0.0F;
		this.panY = 0.0F;
	}

	public float zoom() {
		return this.zoom;
	}

	/** Zooms by a factor, keeping the point under (x, y) where it is. */
	public void zoomAt(double x, double y, float factor) {
		float before = this.zoom;
		this.zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, this.zoom * factor));
		float k = this.zoom / before;
		float cx = this.centreX();
		float cy = this.centreY();
		this.panX += (float) ((cx - x) * (k - 1.0F));
		this.panY += (float) ((cy - y) * (k - 1.0F));
	}

	public void pan(float dx, float dy) {
		this.panX += dx;
		this.panY += dy;
	}

	/** Where a node is drawn, for the clicks and for the client test. */
	public int[] nodeCentre(String id) {
		ClassTree.Tree tree = this.tree();
		ClassTree.Node node = tree == null ? null : tree.node(id);
		if (node == null) {
			return new int[] {-1000, -1000};
		}
		return new int[] {Math.round(this.centreX() + node.x * UNIT * this.zoom), Math.round(this.centreY() - node.y * UNIT * this.zoom)};
	}

	private float radius(ClassTree.Node node) {
		float r = switch (node.kind) {
			case ORIGEN -> 13.0F;
			case CLAVE -> 11.0F;
			case HABILIDAD -> 10.0F;
			case NOTABLE, PUENTE -> 9.0F;
			case FORJA -> 8.0F;
			default -> 6.0F;
		};
		return r * this.zoom;
	}

	private ClassTree.@Nullable Tree tree() {
		PlayerClass clazz = ClassProgress.clazz(this.player());
		return clazz == null ? null : clazz.tree();
	}

	// ------------------------------------------------------------------ the plan

	public List<String> plan() {
		PlayerClass clazz = ClassProgress.clazz(this.player());
		return clazz == null ? new ArrayList<>() : PLANS.computeIfAbsent(clazz, c -> new ArrayList<>());
	}

	public boolean trying() {
		return this.trying;
	}

	public void setTrying(boolean trying) {
		this.trying = trying;
	}

	/** The player's data with the plan learned on top: what "Probar" shows. */
	private ClassData planned(ClassData data) {
		List<String> nodes = new ArrayList<>(data.nodes());
		for (String id : this.plan()) {
			if (!nodes.contains(id)) {
				nodes.add(id);
			}
		}
		return data.withNodes(nodes);
	}

	private int planCost(ClassData data) {
		return this.planned(data).spent() - data.spent();
	}

	/** Adds a node to the plan if it can go there (reachable from what is learned or planned, not excluded). */
	public boolean planAdd(String id) {
		ClassData data = ClassProgress.data(this.player());
		ClassData planned = this.planned(data);
		ClassTree.Tree tree = planned.tree();
		if (tree == null || tree.node(id) == null || planned.has(id)) {
			return false;
		}
		ClassTree.Node node = tree.node(id);
		if (node.kind == ClassTree.Kind.ORIGEN || node.excludes != null && planned.has(node.excludes) || !ClassProgress.reachable(tree, planned, node)) {
			return false;
		}
		this.plan().add(id);
		return true;
	}

	/** Takes a node off the plan, and whatever in the plan hung only from it. */
	public void planRemove(String id) {
		List<String> plan = this.plan();
		plan.remove(id);
		ClassData data = ClassProgress.data(this.player());
		ClassTree.Tree tree = data.tree();
		if (tree == null) {
			return;
		}
		boolean changed = true;
		while (changed) {
			changed = false;
			Set<String> owned = new HashSet<>(data.nodes());
			owned.addAll(plan);
			for (String other : new ArrayList<>(plan)) {
				Set<String> without = new HashSet<>(owned);
				without.remove(other);
				ClassData test = data.withNodes(new ArrayList<>(without));
				if (!ClassProgress.reachable(tree, test, tree.node(other))) {
					plan.remove(other);
					changed = true;
					break;
				}
			}
		}
	}

	public void apply() {
		List<String> plan = this.plan();
		if (!plan.isEmpty() && ClientPlayNetworking.canSend(ClassNetwork.Action.TYPE)) {
			ClientPlayNetworking.send(new ClassNetwork.Action(ClassNetwork.Action.LEARN_PLAN, String.join(",", plan)));
			plan.clear();
			this.trying = false;
			this.click();
		}
	}

	public void discard() {
		this.plan().clear();
		this.click();
	}

	public List<String> forgetting() {
		return this.forget;
	}

	private int forgetCost(ClassTree.Tree tree) {
		int total = 0;
		for (String id : this.forget) {
			total += tree.node(id).cost;
		}
		return total;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		g.fill(0, 0, this.width, this.height, 0xE0141218);
		g.fill(this.canvasLeft() - 1, this.canvasTop() - 1, this.canvasRight() + 1, this.canvasBottom() + 1, 0xFF3A3328);
		g.fill(this.canvasLeft(), this.canvasTop(), this.canvasRight(), this.canvasBottom(), 0xFF1A171C);
		g.fill(this.canvasRight() + MARGIN - 1, this.canvasTop() - 1, this.width - MARGIN + 1, this.canvasBottom() + 1, 0xFF3A3328);
		g.fill(this.canvasRight() + MARGIN, this.canvasTop(), this.width - MARGIN, this.canvasBottom(), 0xFF201C1A);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Player player = this.player();
		ClassData data = ClassProgress.data(player);
		PlayerClass clazz = data.playerClass();
		if (player == null || clazz == null) {
			super.extractRenderState(g, mouseX, mouseY, a);
			return;
		}
		ClassTree.Tree tree = clazz.tree();
		ClassData planned = this.planned(data);
		Set<String> matches = this.matches(tree, clazz);
		long time = player.level().getGameTime();
		boolean pulse = (time / 10) % 2 == 0;

		g.enableScissor(this.canvasLeft(), this.canvasTop(), this.canvasRight(), this.canvasBottom());
		this.regionLabels(g, clazz, tree);
		// The lines first: gold where both ends are learned, blue where the plan reaches, grey elsewhere.
		Set<String> drawn = new HashSet<>();
		for (ClassTree.Node node : tree.nodes) {
			for (String link : node.links) {
				String key = node.id.compareTo(link) < 0 ? node.id + "|" + link : link + "|" + node.id;
				ClassTree.Node other = tree.node(link);
				if (other == null || !drawn.add(key)) {
					continue;
				}
				boolean have = owned(data, node) && owned(data, other);
				boolean plan = !have && owned(planned, node) && owned(planned, other);
				int colour = have ? GOLD_LINE : plan ? BLUE : 0xFF4A4452;
				float width = (have || plan ? 3.0F : 1.5F) * Math.max(0.7F, this.zoom);
				int[] p = this.nodeCentre(node.id);
				int[] q = this.nodeCentre(other.id);
				line(g, p[0], p[1], q[0], q[1], width, colour);
			}
		}
		ClassTree.Node pointed = null;
		for (ClassTree.Node node : tree.nodes) {
			int[] at = this.nodeCentre(node.id);
			float r = this.radius(node);
			if (at[0] + r < this.canvasLeft() || at[0] - r > this.canvasRight() || at[1] + r < this.canvasTop() || at[1] - r > this.canvasBottom()) {
				continue;
			}
			boolean have = owned(data, node);
			boolean inPlan = !have && planned.has(node.id);
			boolean canNow = !have && !inPlan && ClassProgress.check(data, node.id) == ClassProgress.Refusal.NONE;
			ClassGui.NodeState state = have || inPlan ? ClassGui.NodeState.LEARNED : canNow ? ClassGui.NodeState.AVAILABLE : ClassGui.NodeState.LOCKED;
			boolean dim = !this.search.isEmpty() && !matches.contains(node.id);
			this.node(g, clazz, node, at[0], at[1], r, state, inPlan, canNow && pulse, dim, this.forget.contains(node.id));
			if (Math.hypot(mouseX - at[0], mouseY - at[1]) <= r + 1 && this.overCanvas(mouseX, mouseY) && !this.milestonesOpen) {
				pointed = node;
			}
		}
		g.disableScissor();

		this.header(g, clazz, data, planned, tree, mouseX, mouseY);
		this.side(g, clazz, player, data, planned, mouseX, mouseY);
		if (this.milestonesOpen) {
			this.milestones(g, data);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
		if (pointed != null) {
			this.drawTooltip(g, pointed, GuideText.wrap(this.font, this.tooltip(clazz, data, planned, pointed)), mouseX, mouseY);
		}
	}

	/** The tooltip of a node: its icon beside its name, then the rest of the lines. */
	private void drawTooltip(GuiGraphicsExtractor g, ClassTree.Node node, List<FormattedCharSequence> lines, int mouseX, int mouseY) {
		List<ClientTooltipComponent> parts = new ArrayList<>();
		parts.add(new IconHeader(node.icon, node.slot != null && node.slot.upgrade, lines.get(0)));
		for (int i = 1; i < lines.size(); i++) {
			parts.add(ClientTooltipComponent.create(lines.get(i)));
		}
		g.tooltip(this.font, parts, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
	}

	/** The first line of a node's tooltip: the icon, 16 pixels, and the name. */
	private record IconHeader(String icon, boolean upgrade, FormattedCharSequence name) implements ClientTooltipComponent {
		@Override
		public int getHeight(Font font) {
			return 18;
		}

		@Override
		public int getWidth(Font font) {
			return 20 + font.width(this.name);
		}

		@Override
		public void extractText(GuiGraphicsExtractor g, Font font, int x, int y) {
			g.text(font, this.name, x + 20, y + 4, 0xFFFFFFFF, true);
		}

		@Override
		public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
			ClassGui.treeIcon(g, font, this.icon, x + 8, y + 8, 16.0F, this.upgrade);
		}
	}

	private static boolean owned(ClassData data, ClassTree.Node node) {
		return node.kind == ClassTree.Kind.ORIGEN || data.has(node.id);
	}

	/** A line of any slope: a thin rectangle, turned. */
	static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, float width, int colour) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 0.5F) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x0, y0);
		g.pose().rotate((float) Math.atan2(dy, dx));
		int half = Math.max(1, Math.round(width / 2.0F));
		g.fill(0, -half, Math.round(length), half, colour);
		g.pose().popMatrix();
	}

	private void node(GuiGraphicsExtractor g, PlayerClass clazz, ClassTree.Node node, int x, int y, float r, ClassGui.NodeState state, boolean plan,
		boolean pulse, boolean dim, boolean forgetting) {
		boolean octagon = node.kind == ClassTree.Kind.CLAVE || node.kind == ClassTree.Kind.HABILIDAD || node.kind == ClassTree.Kind.ORIGEN;
		float scale = 2.0F * r / ClassGui.NODE;
		g.pose().pushMatrix();
		g.pose().translate(x - r, y - r);
		g.pose().scale(scale, scale);
		ClassGui.node(g, 0, 0, state, octagon);
		g.pose().popMatrix();
		int inner = Math.max(2, Math.round(r * 0.45F));
		// What it is, in the middle: its icon (clase/TreeIcon), scaled to the node, dimmed while it is out of reach.
		boolean big = node.kind == ClassTree.Kind.ORIGEN || node.kind == ClassTree.Kind.HABILIDAD;
		float size = r * (big ? 1.3F : 1.5F);
		if (size >= 6.0F) {
			ClassGui.treeIcon(g, this.font, node.icon, x, y, size, node.slot != null && node.slot.upgrade);
			if (state != ClassGui.NodeState.LEARNED) {
				int half = Math.round(size / 2.0F);
				g.fill(x - half, y - half, x + half, y + half, state == ClassGui.NodeState.LOCKED ? 0xA0262226 : 0x40262226);
			}
		} else {
			int colour = switch (node.kind) {
				case CLAVE -> 0xFFE0533D;
				case FORJA -> 0xFFE8923A;
				case PUENTE, CRUZADO -> 0xFF000000 | PlayerClass.byId(node.target).color;
				case NOTABLE -> 0xFF000000 | clazz.color;
				default -> 0xFF8A8070;
			};
			if (node.kind != ClassTree.Kind.MENOR && node.kind != ClassTree.Kind.NUCLEO || state == ClassGui.NodeState.LEARNED) {
				g.fill(x - inner, y - inner, x + inner, y + inner, colour);
			}
		}
		if (plan) {
			ring(g, x, y, r + 1.5F, BLUE);
		}
		if (pulse) {
			ring(g, x, y, r + 1.5F, 0xFFFFFFFF);
		}
		if (forgetting) {
			ring(g, x, y, r + 2.0F, 0xFFE0533D);
		}
		if (dim) {
			g.fill(Math.round(x - r), Math.round(y - r), Math.round(x + r), Math.round(y + r), 0xB0141218);
		}
	}

	private static void ring(GuiGraphicsExtractor g, int x, int y, float r, int colour) {
		int steps = 16;
		for (int i = 0; i < steps; i++) {
			double a0 = i * Math.PI * 2 / steps;
			double a1 = (i + 1) * Math.PI * 2 / steps;
			line(g, (float) (x + Math.cos(a0) * r), (float) (y + Math.sin(a0) * r), (float) (x + Math.cos(a1) * r), (float) (y + Math.sin(a1) * r), 1.5F, colour);
		}
	}

	/** The names of the three branches and the three paths, out where they are. */
	private void regionLabels(GuiGraphicsExtractor g, PlayerClass clazz, ClassTree.Tree tree) {
		String[] regions = {"A", "S1", "B", "S2", "C", "S3"};
		float[] angles = {90, 30, -30, -90, -150, 150};
		for (int i = 0; i < regions.length; i++) {
			float r = regions[i].startsWith("S") ? 10.6F : 6.6F;
			double angle = Math.toRadians(angles[i] + (regions[i].startsWith("S") ? 9 : 22));
			int x = Math.round(this.centreX() + (float) Math.cos(angle) * r * UNIT * this.zoom);
			int y = Math.round(this.centreY() - (float) Math.sin(angle) * r * UNIT * this.zoom);
			Component name = ClassTree.regionName(clazz, regions[i]);
			if (regions[i].startsWith("S")) {
				PlayerClass to = PlayerClass.byId(tree.bridges.get(Integer.parseInt(regions[i].substring(1)) - 1));
				name = Component.translatable("gui.forja.arbol.senda_a", name, to == null ? Component.empty() : to.displayName());
			}
			g.centeredText(this.font, name, x, y, 0xFFB8A8C0);
		}
	}

	private void header(GuiGraphicsExtractor g, PlayerClass clazz, ClassData data, ClassData planned, ClassTree.Tree tree, int mouseX, int mouseY) {
		g.item(clazz.icon(), 8, 7);
		g.text(this.font, clazz.displayName(), 28, 5, 0xFFFFFFFF, true);
		boolean top = data.level() >= ClassProgress.MAX_LEVEL;
		Component level = top ? Component.translatable("gui.forja.clase.nivel_maximo", data.level())
			: Component.translatable("gui.forja.clase.nivel", data.level(), data.xp(), ClassProgress.totalFor(data.level() + 1));
		ClassGui.small(g, this.font, level, 28, 16, ClassGui.INK_SOFT);
		int from = ClassProgress.totalFor(data.level());
		int to = ClassProgress.totalFor(Math.min(ClassProgress.MAX_LEVEL, data.level() + 1));
		float share = top || to <= from ? 1.0F : Math.max(0.0F, Math.min(1.0F, (data.xp() - from) / (float) (to - from)));
		g.fill(28, 24, 128, 26, 0xFF3A3328);
		g.fill(28, 24, 28 + Math.round(100 * share), 26, 0xFF000000 | clazz.color);
		// The points, after the level line, in the room the buttons leave.
		int x = 28 + Math.max(100, Math.round(this.font.width(level) * 0.75F)) + 8;
		Component points = Component.translatable("gui.forja.arbol.puntos", data.points(), data.earned());
		ClassGui.small(g, this.font, points, x, 7, data.points() > 0 ? ClassGui.GOLD : ClassGui.INK_SOFT);
		ClassGui.small(g, this.font, data.waitingMilestonePoints() > 0
			? Component.translatable("gui.forja.arbol.hitos_espera", data.usableMilestonePoints(), data.waitingMilestonePoints())
			: Component.translatable("gui.forja.arbol.hitos_puntos", data.usableMilestonePoints()), x, 16, ClassGui.INK_SOFT);
		// The buttons on the right of the header.
		int bx = this.width - MARGIN;
		for (HeaderButton button : this.buttons()) {
			bx -= button.width;
			boolean over = ClassGui.over(mouseX, mouseY, bx, 6, button.width - 3, 18);
			g.fill(bx, 6, bx + button.width - 3, 24, button.active ? (over ? 0xFF6A5838 : 0xFF4A3E2A) : 0xFF2A2420);
			g.fill(bx, 6, bx + button.width - 3, 7, 0xFF8A7448);
			int labelWidth = Math.round(this.font.width(button.label) * 0.75F);
			ClassGui.small(g, this.font, button.label, bx + (button.width - 3 - labelWidth) / 2, 12, button.active ? 0xFFFFFFFF : 0xFF7A6E5E);
		}
		// What "Probar" or the candle has picked.
		if (this.trying || !this.plan().isEmpty()) {
			int cost = this.planCost(data);
			g.text(this.font, Component.translatable("gui.forja.arbol.plan", cost, data.points() - cost),
				this.canvasLeft() + 4, this.canvasTop() + 4, cost > data.points() ? ClassGui.RED : BLUE, true);
		}
		if (this.forgetting) {
			g.text(this.font, Component.translatable("gui.forja.arbol.olvidar", this.forgetCost(tree), dev.forja.item.OblivionCandleItem.POINTS),
				this.canvasLeft() + 4, this.canvasTop() + 4, 0xFFE0533D, true);
		}
	}

	/** One of the header's buttons: what it says, how wide, whether it does anything now. */
	private record HeaderButton(String id, Component label, int width, boolean active) {
	}

	private List<HeaderButton> buttons() {
		List<HeaderButton> out = new ArrayList<>();
		out.add(new HeaderButton("hitos", Component.translatable("gui.forja.arbol.boton_hitos"), 42, true));
		if (this.forgetting) {
			out.add(new HeaderButton("quitar", Component.translatable("gui.forja.arbol.boton_quitar"), 42, !this.forget.isEmpty()));
			return out;
		}
		out.add(new HeaderButton("descartar", Component.translatable("gui.forja.arbol.boton_descartar"), 46, !this.plan().isEmpty()));
		out.add(new HeaderButton("aplicar", Component.translatable("gui.forja.arbol.boton_aplicar"), 42, !this.plan().isEmpty()));
		out.add(new HeaderButton("probar", Component.translatable(this.trying ? "gui.forja.arbol.boton_probando" : "gui.forja.arbol.boton_probar"), 46, true));
		return out;
	}

	/** The side: the totals the tree gives (and what the plan would change), and the three skills with their keys. */
	private void side(GuiGraphicsExtractor g, PlayerClass clazz, Player player, ClassData data, ClassData planned, int mouseX, int mouseY) {
		int x = this.canvasRight() + MARGIN + 4;
		int y = this.canvasTop() + 22;
		int width = SIDE - 12;
		ClassGui.small(g, this.font, Component.translatable("gui.forja.clase.habilidades"), x, y, ClassGui.GOLD);
		y += 9;
		ActiveSkill pointedSkill = null;
		for (int slot = 1; slot <= 3; slot++) {
			ActiveSkill skill = clazz.skill(slot);
			boolean have = ClassSkills.skill(player, slot) == skill;
			ClassGui.hudSlot(g, x - 2, y, have && ClassSkills.waiting(player, slot) == 0);
			g.item(skill.icon(), x + 1, y + 3);
			if (!have) {
				g.fill(x + 1, y + 3, x + 17, y + 19, 0x90201A16);
			}
			ClassGui.small(g, this.font, skill.displayName(player), x + 23, y + 3, have ? 0xFFFFFFFF : ClassGui.INK_DIM);
			ClassGui.small(g, this.font, ClassClient.keyName(slot), x + 23, y + 12, ClassGui.INK_SOFT);
			if (ClassGui.over(mouseX, mouseY, x - 2, y, width, ClassGui.HUD_SIZE)) {
				pointedSkill = skill;
			}
			y += 25;
		}
		y += 3;
		ClassGui.small(g, this.font, Component.translatable("gui.forja.arbol.totales"), x, y, ClassGui.GOLD);
		y += 9;
		for (ClassStat stat : ClassStat.values()) {
			float now = total(clazz, data, stat);
			float then = total(clazz, planned, stat);
			if (Math.abs(now) < 1.0E-4F && Math.abs(then) < 1.0E-4F) {
				continue;
			}
			if (y > this.canvasBottom() - 8) {
				break;
			}
			Component line = stat.line(then);
			if (Math.abs(then - now) > 1.0E-4F) {
				line = Component.empty().append(line).append(Component.literal(" ◂ " + stat.format(now)).withColor(BLUE));
			}
			ClassGui.small(g, this.font, line, x, y, 0xFFFFFFFF);
			y += 7;
		}
		if (pointedSkill != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(pointedSkill.displayName(player).copy().withColor(0xFF000000 | clazz.color));
			boolean upgraded = pointedSkill.upgraded(player);
			lines.add(pointedSkill.def().effect(upgraded));
			lines.add(Component.translatable("gui.forja.habilidad.espera", pointedSkill.cooldownSeconds(player)).withColor(0xFF9A9A9A));
			if (!upgraded) {
				lines.add(Component.translatable("gui.forja.arbol.con_ii").withColor(ClassGui.GOLD));
				lines.add(pointedSkill.def().effect(true));
			}
			g.setTooltipForNextFrame(GuideText.wrap(this.font, lines), mouseX, mouseY);
		}
	}

	/** A stat's total for this data: the class's base and its learned nodes. */
	static float total(PlayerClass clazz, ClassData data, ClassStat stat) {
		float total = clazz.base(stat);
		ClassTree.Tree tree = clazz.tree();
		for (String id : data.nodes()) {
			ClassTree.Node node = tree.node(id);
			if (node != null) {
				for (ClassStat.Mod mod : node.mods) {
					if (mod.stat() == stat) {
						total += mod.value();
					}
				}
			}
		}
		return total;
	}

	private void milestones(GuiGraphicsExtractor g, ClassData data) {
		int w = Math.min(320, this.canvasRight() - this.canvasLeft() - 12);
		int lines = ClassTree.milestones().size();
		int h = 34 + lines * 8;
		int x = (this.canvasLeft() + this.canvasRight()) / 2 - w / 2;
		int y = Math.max(this.canvasTop() + 4, (this.canvasTop() + this.canvasBottom()) / 2 - h / 2);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF8A7448);
		g.fill(x, y, x + w, y + h, 0xF0201C1A);
		g.text(this.font, Component.translatable("gui.forja.arbol.hitos_titulo", data.milestonePoints(), ClassTree.allMilestonePoints()), x + 6, y + 5, ClassGui.GOLD, true);
		ClassGui.smallWrapped(g, this.font, Component.translatable("gui.forja.arbol.hitos_tope", ClassTree.milestoneCapPerLevel()), x + 6, y + 16,
			w - 12, ClassGui.INK_SOFT);
		int row = y + 32;
		for (ClassTree.Milestone milestone : ClassTree.milestones()) {
			boolean done = data.hasMilestone(milestone.id());
			Component text = Component.literal(done ? "✔ " : "· ").append(milestone.displayName()).append(" (+" + milestone.points() + ") — ")
				.append(milestone.description());
			ClassGui.small(g, this.font, text, x + 6, row, done ? ClassGui.GREEN : ClassGui.INK_SOFT);
			row += 8;
		}
	}

	/** Its name, kind and cost, what it does, what it rules out, and whether it can be had now. */
	private List<Component> tooltip(PlayerClass clazz, ClassData data, ClassData planned, ClassTree.Node node) {
		List<Component> lines = new ArrayList<>();
		lines.add(node.displayName(clazz).copy().withColor(node.kind == ClassTree.Kind.CLAVE ? 0xFFF0C070 : 0xFF000000 | clazz.color));
		Component kind = Component.translatable("gui.forja.arbol.tipo." + node.kind.name().toLowerCase(Locale.ROOT));
		if (node.target != null && PlayerClass.byId(node.target) != null) {
			kind = Component.translatable("gui.forja.arbol.de_clase", kind, PlayerClass.byId(node.target).displayName());
		}
		lines.add(Component.translatable("gui.forja.arbol.tipo_coste", kind, node.cost).withColor(ClassGui.INK_SOFT));
		lines.addAll(node.effectLines(clazz));
		// What it would do to the totals.
		for (ClassStat.Mod mod : node.mods) {
			float now = total(clazz, planned, mod.stat());
			float then = planned.has(node.id) ? now : now + mod.value();
			if (!planned.has(node.id)) {
				lines.add(Component.translatable("gui.forja.arbol.de_a", Component.translatable("gui.forja.clase.stat." + mod.stat().id(), ""),
					mod.stat().format(now), mod.stat().format(then)).withColor(ClassGui.INK_DIM));
			}
		}
		if (node.excludes != null) {
			ClassTree.Node other = clazz.tree().node(node.excludes);
			lines.add(Component.translatable("gui.forja.arbol.excluye", other.displayName(clazz)).withColor(data.has(node.excludes) ? ClassGui.RED : ClassGui.INK_SOFT));
		}
		if (this.forgetting) {
			return lines;
		}
		ClassProgress.Refusal why = ClassProgress.check(data, node.id);
		lines.add(switch (why) {
			case NONE -> Component.translatable(this.trying ? "gui.forja.arbol.clic_probar" : "gui.forja.arbol.clic_aprender").withColor(ClassGui.GOLD);
			case ALREADY -> Component.translatable("gui.forja.talento.no.already").withColor(ClassGui.GREEN);
			case POINTS -> Component.translatable("gui.forja.talento.no.points", data.points()).withColor(ClassGui.RED);
			default -> Component.translatable("gui.forja.talento.no." + why.name().toLowerCase(Locale.ROOT)).withColor(ClassGui.RED);
		});
		if (why != ClassProgress.Refusal.ALREADY && why != ClassProgress.Refusal.NONE && !this.trying) {
			lines.add(Component.translatable("gui.forja.arbol.mayus_probar").withColor(ClassGui.INK_DIM));
		}
		return lines;
	}

	/** The nodes whose name or text holds the search, in the tree's order. */
	private Set<String> matches(ClassTree.Tree tree, PlayerClass clazz) {
		Set<String> out = new LinkedHashSet<>();
		if (this.search.isEmpty()) {
			return out;
		}
		for (ClassTree.Node node : tree.nodes) {
			if (node.searchText(clazz).contains(this.search)) {
				out.add(node.id);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ input

	private boolean overCanvas(double x, double y) {
		return x >= this.canvasLeft() && x < this.canvasRight() && y >= this.canvasTop() && y < this.canvasBottom();
	}

	private ClassTree.@Nullable Node nodeAt(double x, double y) {
		ClassTree.Tree tree = this.tree();
		if (tree == null || !this.overCanvas(x, y)) {
			return null;
		}
		for (ClassTree.Node node : tree.nodes) {
			int[] at = this.nodeCentre(node.id);
			if (Math.hypot(x - at[0], y - at[1]) <= this.radius(node) + 1) {
				return node;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (this.searchBox != null) {
			this.searchBox.setFocused(false);
		}
		Player player = this.player();
		PlayerClass clazz = ClassProgress.clazz(player);
		if (player == null || clazz == null) {
			return false;
		}
		// The header's buttons.
		int bx = this.width - MARGIN;
		for (HeaderButton button : this.buttons()) {
			bx -= button.width;
			if (event.button() == 0 && ClassGui.over(event.x(), event.y(), bx, 6, button.width - 3, 18)) {
				if (button.active) {
					this.press(button.id);
				}
				return true;
			}
		}
		if (this.milestonesOpen) {
			this.milestonesOpen = false;
			return true;
		}
		ClassTree.Node node = event.button() == 0 ? this.nodeAt(event.x(), event.y()) : null;
		if (node != null) {
			this.clickNode(node, (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0);
			return true;
		}
		if (this.overCanvas(event.x(), event.y())) {
			if (doubleClick && event.button() == 0) {
				this.home();
				return true;
			}
			this.dragging = true;
			this.dragMoved = false;
			this.dragFromX = event.x();
			this.dragFromY = event.y();
			return true;
		}
		return false;
	}

	/** A button of the header, by name (the client test presses them this way too). */
	public void press(String id) {
		switch (id) {
			case "probar" -> {
				this.trying = !this.trying;
				this.click();
			}
			case "aplicar" -> this.apply();
			case "descartar" -> this.discard();
			case "hitos" -> {
				this.milestonesOpen = !this.milestonesOpen;
				this.click();
			}
			case "quitar" -> {
				if (!this.forget.isEmpty() && ClientPlayNetworking.canSend(ClassNetwork.Action.TYPE)) {
					ClientPlayNetworking.send(new ClassNetwork.Action(ClassNetwork.Action.FORGET, String.join(",", this.forget)));
					this.forget.clear();
					this.click();
					this.onClose();
				}
			}
			default -> {
			}
		}
	}

	/** A click on a node: learn it, or (trying, or with Shift) put it in the plan or take it out; forgetting, mark it. */
	public void clickNode(ClassTree.Node node, boolean shift) {
		ClassData data = ClassProgress.data(this.player());
		if (this.forgetting) {
			if (this.forget.contains(node.id)) {
				this.forget.remove(node.id);
			} else if (data.has(node.id)) {
				List<String> next = new ArrayList<>(this.forget);
				next.add(node.id);
				if (ClassProgress.canForget(data, next, dev.forja.item.OblivionCandleItem.POINTS)) {
					this.forget.add(node.id);
				}
			}
			this.click();
			return;
		}
		if (this.trying || shift) {
			if (this.plan().contains(node.id)) {
				this.planRemove(node.id);
			} else {
				this.planAdd(node.id);
			}
			this.click();
			return;
		}
		if (ClassProgress.check(data, node.id) == ClassProgress.Refusal.NONE && ClientPlayNetworking.canSend(ClassNetwork.Action.TYPE)) {
			ClientPlayNetworking.send(new ClassNetwork.Action(ClassNetwork.Action.LEARN, node.id));
			this.plan().remove(node.id);
			this.click();
		}
	}

	private void click() {
		if (this.minecraft != null) {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging) {
			// From where the cursor last was, not the event's delta: a cursor that jumped before the press would
			// otherwise throw the tree across the screen on the first move.
			this.pan((float) (event.x() - this.dragFromX), (float) (event.y() - this.dragFromY));
			this.dragFromX = event.x();
			this.dragFromY = event.y();
			this.dragMoved = true;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.overCanvas(mouseX, mouseY) && scrollY != 0.0) {
			this.zoomAt(mouseX, mouseY, scrollY > 0 ? 1.15F : 1.0F / 1.15F);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean typing = this.searchBox != null && this.searchBox.isFocused();
		if (!typing && ClassClient.TREE != null && ClassClient.TREE.matches(event)) {
			this.onClose();
			return true;
		}
		if (typing && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
			this.nextMatch();
			return true;
		}
		if (!typing) {
			switch (event.key()) {
				case GLFW.GLFW_KEY_HOME -> {
					this.home();
					return true;
				}
				case GLFW.GLFW_KEY_LEFT -> {
					this.pan(30, 0);
					return true;
				}
				case GLFW.GLFW_KEY_RIGHT -> {
					this.pan(-30, 0);
					return true;
				}
				case GLFW.GLFW_KEY_UP -> {
					this.pan(0, 30);
					return true;
				}
				case GLFW.GLFW_KEY_DOWN -> {
					this.pan(0, -30);
					return true;
				}
				case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
					this.zoomAt((this.canvasLeft() + this.canvasRight()) / 2.0, (this.canvasTop() + this.canvasBottom()) / 2.0, 1.15F);
					return true;
				}
				case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
					this.zoomAt((this.canvasLeft() + this.canvasRight()) / 2.0, (this.canvasTop() + this.canvasBottom()) / 2.0, 1.0F / 1.15F);
					return true;
				}
				default -> {
				}
			}
		}
		return super.keyPressed(event);
	}

	/** Enter in the search: centres the next node that matches. */
	public void nextMatch() {
		ClassTree.Tree tree = this.tree();
		PlayerClass clazz = ClassProgress.clazz(this.player());
		if (tree == null || clazz == null) {
			return;
		}
		List<String> found = new ArrayList<>(this.matches(tree, clazz));
		if (found.isEmpty()) {
			return;
		}
		this.searchHit = (this.searchHit + 1) % found.size();
		ClassTree.Node node = tree.node(found.get(this.searchHit));
		this.panX = -node.x * UNIT * this.zoom;
		this.panY = node.y * UNIT * this.zoom;
	}

	/** For the client test: types into the search. */
	public void search(String text) {
		if (this.searchBox != null) {
			this.searchBox.setValue(text);
		}
		this.search = text.trim().toLowerCase(Locale.ROOT);
	}

	static ItemStack candle() {
		return dev.forja.registry.ModItems.VELA_DEL_OLVIDO == null ? new ItemStack(Items.CANDLE) : new ItemStack(dev.forja.registry.ModItems.VELA_DEL_OLVIDO);
	}
}
