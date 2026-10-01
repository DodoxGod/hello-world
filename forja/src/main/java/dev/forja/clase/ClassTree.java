package dev.forja.clase;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * The big class trees (docs/ARBOLES.md), read from {@code forja_arboles.json} — which tools/arboles.py writes
 * from tools/arboles_datos.py. Every number of a tree lives there: the nodes and what they do, their costs, the
 * three skills of each class and their II, the level cap, the points a level gives, the experience curve and
 * the milestones. Changing any of them is changing that file, not this code.
 *
 * <p>A node's plain numbers are its {@link Node#mods} (ClassStat, summed with the class's base). What a number
 * cannot say is its {@link Node#hook}, a name the code that does it asks for ({@link ClassEffects#hook}), with
 * the node's {@link Node#numbers}: the same numbers its tooltip prints.
 */
public final class ClassTree {
	public enum Kind {
		ORIGEN,
		NUCLEO,
		FORJA,
		MENOR,
		NOTABLE,
		CLAVE,
		HABILIDAD,
		PUENTE,
		CRUZADO;

		static Kind of(String name) {
			return valueOf(name.toUpperCase(Locale.ROOT));
		}

		public boolean named() {
			return this == NOTABLE || this == CLAVE || this == HABILIDAD || this == PUENTE || this == FORJA || this == ORIGEN;
		}
	}

	/** Which skill a skill node is, or what it upgrades. */
	public enum Slot {
		B(2, false),
		N(3, false),
		V2(1, true),
		B2(2, true),
		N2(3, true);

		/** The key: 1 V, 2 B, 3 N. */
		public final int key;
		public final boolean upgrade;

		Slot(int key, boolean upgrade) {
			this.key = key;
			this.upgrade = upgrade;
		}
	}

	public static final class Node {
		public final String id;
		public final Kind kind;
		public final int cost;
		public final String region;
		public final List<ClassStat.Mod> mods;
		public final float[] numbers;
		/** Per number: whether the text shows it as a percentage. */
		public final boolean[] percent;
		/** The name the code looks this node's special effect up by; null for a node that is only numbers. */
		public final @Nullable String hook;
		public final @Nullable Slot slot;
		public final List<String> links;
		public final float x;
		public final float y;
		/** A bridge's class. */
		public final @Nullable String target;
		/** The keystone this one rules out (the other one of its branch). */
		public final @Nullable String excludes;
		/** The path of a branch it is on ("a1", "c2"...). */
		public final @Nullable String path;
		final boolean hasText;
		final String nameEs;

		Node(JsonObject json) {
			this.id = json.get("id").getAsString();
			this.kind = Kind.of(json.get("tipo").getAsString());
			this.cost = json.get("coste").getAsInt();
			this.region = json.get("region").getAsString();
			List<ClassStat.Mod> mods = new ArrayList<>();
			for (JsonElement element : json.getAsJsonArray("mods")) {
				JsonArray pair = element.getAsJsonArray();
				mods.add(ClassStat.valueOf(pair.get(0).getAsString().toUpperCase(Locale.ROOT)).of(pair.get(1).getAsFloat()));
			}
			this.mods = List.copyOf(mods);
			this.numbers = floats(json.getAsJsonArray("numeros"));
			this.percent = percents(json.getAsJsonArray("formatos"));
			this.hook = json.has("gancho") && !json.get("gancho").isJsonNull() ? json.get("gancho").getAsString() : null;
			this.slot = json.has("habilidad") ? Slot.valueOf(json.get("habilidad").getAsString()) : null;
			List<String> links = new ArrayList<>();
			for (JsonElement element : json.getAsJsonArray("conexiones")) {
				links.add(element.getAsString());
			}
			this.links = links;
			this.x = json.get("x").getAsFloat();
			this.y = json.get("y").getAsFloat();
			this.target = json.has("destino") ? json.get("destino").getAsString() : null;
			this.excludes = json.has("excluye") ? json.get("excluye").getAsString() : null;
			this.path = json.has("camino") ? json.get("camino").getAsString() : null;
			this.hasText = json.has("plantilla") && !json.get("plantilla").isJsonNull();
			this.nameEs = json.getAsJsonObject("nombre").get("es").getAsString();
		}

		/** A copy with links of its own (a forge node as one tree sees it). */
		Node(Node other) {
			this.id = other.id;
			this.kind = other.kind;
			this.cost = other.cost;
			this.region = other.region;
			this.mods = other.mods;
			this.numbers = other.numbers;
			this.percent = other.percent;
			this.hook = other.hook;
			this.slot = other.slot;
			this.links = new ArrayList<>(other.links);
			this.x = other.x;
			this.y = other.y;
			this.target = other.target;
			this.excludes = other.excludes;
			this.path = other.path;
			this.hasText = other.hasText;
			this.nameEs = other.nameEs;
		}

		/** Whether it has a text of its own (something its numbers cannot say), besides its mods. */
		public boolean hasText() {
			return this.hasText;
		}

		/** The node's name: its own, its skill's, or (a small node) its stat's line. */
		public Component displayName(PlayerClass owner) {
			if (this.slot != null) {
				SkillDef skill = tree(owner).skill(this.slot);
				return Component.translatable("gui.forja.habilidad." + skill.id + (this.slot.upgrade ? ".ii" : ""));
			}
			if (this.kind == Kind.ORIGEN) {
				return owner.displayName();
			}
			if (this.hook != null) {
				return Component.translatable("gui.forja.nodo." + this.hook);
			}
			return this.mods.isEmpty() ? Component.literal(this.id) : this.mods.getFirst().line();
		}

		/** What it does, a line each: its numbers, then its text. */
		public List<Component> effectLines(PlayerClass owner) {
			List<Component> lines = new ArrayList<>();
			if (this.slot != null) {
				SkillDef skill = tree(owner).skill(this.slot);
				lines.add(skill.effect(this.slot.upgrade));
				lines.add(Component.translatable("gui.forja.habilidad.espera", skill.cooldown(this.slot.upgrade)).withColor(0xFF9A9A9A));
				return lines;
			}
			for (ClassStat.Mod mod : this.mods) {
				lines.add(mod.line());
			}
			if (this.hasText && this.hook != null) {
				lines.add(Component.translatable("gui.forja.nodo." + this.hook + ".efecto", args(this.numbers, this.percent)).withColor(0xFF7FD34E));
			}
			return lines;
		}

		/** Every text of it in one string, lower case, for the tree's search. */
		public String searchText(PlayerClass owner) {
			StringBuilder text = new StringBuilder(this.displayName(owner).getString());
			for (Component line : this.effectLines(owner)) {
				text.append(' ').append(line.getString());
			}
			return text.toString().toLowerCase(Locale.ROOT);
		}

		@Override
		public String toString() {
			return this.id;
		}
	}

	/** One of the three skills of a class: its numbers and its II's. */
	public static final class SkillDef {
		public final String id;
		public final float[] numbers;
		final boolean[] percent;
		public final int cooldown;
		public final float[] upgradeNumbers;
		final boolean[] upgradePercent;
		public final int upgradeCooldown;

		SkillDef(JsonObject json) {
			this.id = json.get("id").getAsString();
			this.numbers = floats(json.getAsJsonArray("numeros"));
			this.percent = percents(json.getAsJsonArray("formatos"));
			this.cooldown = json.get("espera").getAsInt();
			JsonObject upgrade = json.getAsJsonObject("mejora");
			this.upgradeNumbers = floats(upgrade.getAsJsonArray("numeros"));
			this.upgradePercent = percents(upgrade.getAsJsonArray("formatos"));
			this.upgradeCooldown = upgrade.get("espera").getAsInt();
		}

		public float[] numbers(boolean upgraded) {
			return upgraded ? this.upgradeNumbers : this.numbers;
		}

		public int cooldown(boolean upgraded) {
			return upgraded ? this.upgradeCooldown : this.cooldown;
		}

		public Component effect(boolean upgraded) {
			return Component.translatable("gui.forja.habilidad." + this.id + (upgraded ? ".ii" : "") + ".efecto",
				args(this.numbers(upgraded), upgraded ? this.upgradePercent : this.percent)).withColor(0xFF7FD34E);
		}
	}

	/** One class's tree: its nodes (the forge's among them), its skills, the names of its regions. */
	public static final class Tree {
		public final PlayerClass owner;
		/** In the file's order: the origin first. */
		public final List<Node> nodes;
		private final Map<String, Node> byId;
		private final Map<String, SkillDef> skills;
		/** A, B, C, S1, S2, S3, a1, a2, b1... */
		public final List<String> regions;
		/** The three classes its bridges lead to. */
		public final List<String> bridges;

		Tree(PlayerClass owner, List<Node> nodes, Map<String, SkillDef> skills, List<String> regions, List<String> bridges) {
			this.owner = owner;
			this.nodes = List.copyOf(nodes);
			Map<String, Node> byId = new LinkedHashMap<>();
			for (Node node : nodes) {
				byId.put(node.id, node);
			}
			this.byId = byId;
			this.skills = skills;
			this.regions = List.copyOf(regions);
			this.bridges = List.copyOf(bridges);
		}

		public @Nullable Node node(String id) {
			return this.byId.get(id);
		}

		public Node origin() {
			return this.nodes.getFirst();
		}

		/** The skill on a key: "V", "B" or "N". */
		public SkillDef skill(String key) {
			return this.skills.get(key);
		}

		public SkillDef skill(Slot slot) {
			return this.skills.get(slot.name().substring(0, 1));
		}

		public SkillDef skill(int key) {
			return this.skills.get(key == 1 ? "V" : key == 2 ? "B" : "N");
		}

		/** Every node with this hook (bridges and forge nodes can share one across trees, never within one). */
		public @Nullable Node byHook(String hook) {
			for (Node node : this.nodes) {
				if (hook.equals(node.hook)) {
					return node;
				}
			}
			return null;
		}

		/** The node that unlocks or upgrades this skill slot. */
		public @Nullable Node bySlot(Slot slot) {
			for (Node node : this.nodes) {
				if (node.slot == slot) {
					return node;
				}
			}
			return null;
		}

		/** Points to own every node at once (both keystones of a branch counted). */
		public int totalCost() {
			int total = 0;
			for (Node node : this.nodes) {
				total += node.cost;
			}
			return total;
		}
	}

	public record Milestone(String id, int points) {
		public Component displayName() {
			return Component.translatable("gui.forja.hito." + this.id);
		}

		public Component description() {
			return Component.translatable("gui.forja.hito." + this.id + ".desc");
		}
	}

	// ------------------------------------------------------------------ the file

	public static final String RESOURCE = "/forja_arboles.json";
	private static final Map<PlayerClass, Tree> TREES = new EnumMap<>(PlayerClass.class);
	private static final List<Milestone> MILESTONES = new ArrayList<>();
	private static final Map<String, Milestone> MILESTONES_BY_ID = new HashMap<>();
	private static int maxLevel;
	private static int pointsNum;
	private static int pointsDen;
	private static int milestoneCapPerLevel;
	private static int firstStep;
	private static int stepGrowth;
	private static final Map<String, Integer> COSTS = new HashMap<>();

	static {
		load();
	}

	private ClassTree() {
	}

	private static void load() {
		InputStream in = ClassTree.class.getResourceAsStream(RESOURCE);
		if (in == null) {
			throw new IllegalStateException("Missing " + RESOURCE + ": run tools/arboles.py");
		}
		JsonObject root;
		try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			root = JsonParser.parseReader(reader).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Cannot read " + RESOURCE, e);
		}
		maxLevel = root.get("nivel_max").getAsInt();
		JsonObject points = root.getAsJsonObject("puntos_nivel");
		pointsNum = points.get("numerador").getAsInt();
		pointsDen = points.get("denominador").getAsInt();
		milestoneCapPerLevel = root.get("tope_hitos_por_nivel").getAsInt();
		JsonObject xp = root.getAsJsonObject("experiencia");
		firstStep = xp.get("primer_paso").getAsInt();
		stepGrowth = xp.get("crecimiento").getAsInt();
		for (Map.Entry<String, JsonElement> cost : root.getAsJsonObject("costes").entrySet()) {
			COSTS.put(cost.getKey(), cost.getValue().getAsInt());
		}
		for (JsonElement element : root.getAsJsonArray("hitos")) {
			JsonObject milestone = element.getAsJsonObject();
			Milestone m = new Milestone(milestone.get("id").getAsString(), milestone.get("puntos").getAsInt());
			MILESTONES.add(m);
			MILESTONES_BY_ID.put(m.id(), m);
		}
		List<Node> forge = new ArrayList<>();
		for (JsonElement element : root.getAsJsonArray("nodos_forja")) {
			forge.add(new Node(element.getAsJsonObject()));
		}
		JsonObject classes = root.getAsJsonObject("clases");
		for (PlayerClass clazz : PlayerClass.values()) {
			JsonObject json = classes.getAsJsonObject(clazz.id());
			if (json == null) {
				throw new IllegalStateException("No tree for " + clazz.id() + " in " + RESOURCE);
			}
			List<Node> nodes = new ArrayList<>();
			for (JsonElement element : json.getAsJsonArray("nodos")) {
				nodes.add(new Node(element.getAsJsonObject()));
			}
			// The forge nodes are the same in every tree; each tree links its doors to them.
			for (Node shared : forge) {
				Node copy = copyWithLinks(shared, nodes);
				nodes.add(copy);
			}
			Map<String, SkillDef> skills = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> skill : json.getAsJsonObject("habilidades").entrySet()) {
				skills.put(skill.getKey(), new SkillDef(skill.getValue().getAsJsonObject()));
			}
			List<String> regions = new ArrayList<>(json.getAsJsonObject("regiones").keySet());
			List<String> bridges = new ArrayList<>();
			for (JsonElement element : json.getAsJsonArray("puentes")) {
				bridges.add(element.getAsString());
			}
			TREES.put(clazz, new Tree(clazz, nodes, skills, regions, bridges));
		}
	}

	/** A forge node as one tree sees it: its own links plus the doors of this tree that touch it. */
	private static Node copyWithLinks(Node shared, List<Node> classNodes) {
		Node copy = shared;
		for (Node node : classNodes) {
			if (node.links.contains(shared.id) && !copy.links.contains(node.id)) {
				if (copy == shared) {
					copy = new Node(shared);
				}
				copy.links.add(node.id);
			}
		}
		return copy;
	}

	public static Tree tree(PlayerClass clazz) {
		return TREES.get(clazz);
	}

	public static int maxLevel() {
		return maxLevel;
	}

	/** Points the levels give at this level: 1, 2, 4, 5, 7... (seven every five levels). */
	public static int levelPoints(int level) {
		return pointsNum * Math.max(0, Math.min(maxLevel, level)) / pointsDen;
	}

	public static int milestoneCapPerLevel() {
		return milestoneCapPerLevel;
	}

	public static int firstStep() {
		return firstStep;
	}

	public static int stepGrowth() {
		return stepGrowth;
	}

	/** What a node of this kind costs ("menor", "clave"...), as the file says. */
	public static int cost(String kind) {
		return COSTS.getOrDefault(kind, 0);
	}

	public static List<Milestone> milestones() {
		return Collections.unmodifiableList(MILESTONES);
	}

	public static @Nullable Milestone milestone(String id) {
		return MILESTONES_BY_ID.get(id);
	}

	/** Every milestone point there is. */
	public static int allMilestonePoints() {
		int total = 0;
		for (Milestone milestone : MILESTONES) {
			total += milestone.points();
		}
		return total;
	}

	// ------------------------------------------------------------------ helpers

	private static float[] floats(JsonArray array) {
		float[] out = new float[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).getAsFloat();
		}
		return out;
	}

	private static boolean[] percents(JsonArray array) {
		boolean[] out = new boolean[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = "pct".equals(array.get(i).getAsString());
		}
		return out;
	}

	/** Numbers as the texts print them: "30 %" for a percentage, "1,5" or "8" otherwise. */
	static Object[] args(float[] numbers, boolean[] percent) {
		Object[] out = new Object[numbers.length];
		for (int i = 0; i < numbers.length; i++) {
			out[i] = i < percent.length && percent[i] ? number(numbers[i] * 100.0F) + " %" : number(numbers[i]);
		}
		return out;
	}

	public static String number(float value) {
		if (Math.abs(value - Math.round(value)) < 1.0E-3F) {
			return Integer.toString(Math.round(value));
		}
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		return text.replace('.', ',');
	}

	/** "Rama A · Aguante" and the like, for the screen. */
	public static MutableComponent regionName(PlayerClass clazz, String region) {
		return Component.translatable("gui.forja.arbol." + clazz.id() + "." + region.toLowerCase(Locale.ROOT));
	}
}
