package dev.forja.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.item.TemplateItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.Upgrade;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The forge guide book: a two-page spread with an index and one chapter each for the tables, gear
 * recipes, parts, materials, traits, upgrades and the stat colors. Hovering an entry shows its full
 * tooltip, the index entries jump to their chapter, and the arrows, mouse wheel or arrow keys turn pages.
 *
 * <p>It grew to a hundred and eighty pages while still being five flat rectangles with an index button,
 * and at that length "turn the page" is not a way to get anywhere. So it is a book now, drawn on
 * {@code textures/gui/libro.png}, and it has the things a long book has: <b>tabs</b> down the side for
 * its five sections, a <b>strip along the foot</b> of the pages showing where you are in the whole of
 * it (and taking you anywhere in it with a click), and a <b>way back</b> — right click, or backspace —
 * to wherever you jumped from.
 */
public class GuideBookScreen extends Screen {
	private static final int PAGE_W = 150;
	private static final int PAGE_H = 186;
	private static final int SPINE = 6;
	private static final int BOOK_W = PAGE_W * 2 + SPINE + 8;
	private static final int BOOK_H = PAGE_H + 8;
	private static final int MARGIN = 10;
	private static final int CONTENT_W = PAGE_W - 2 * MARGIN;
	private static final int CONTENT_H = PAGE_H - 34;
	private static final float SMALL = 0.75F;
	private static final int WRAP = Math.round(CONTENT_W / SMALL);

	private static final net.minecraft.resources.Identifier BOOK = dev.forja.Forja.id("textures/gui/libro.png");
	/** How far the cover shows beyond the rectangle the pages are laid out in. */
	private static final int OVERHANG = 6;
	private static final int TAB_H = 20;
	private static final int TAB_GAP = 3;
	/** One per section, in the order of {@link #SECTIONS}: the same five colours the tabs are painted in. */
	private static final int[] SECTION_COLOURS = {0xFFE2782C, 0xFF966CDC, 0xFFCC3E38, 0xFF46AAA8, 0xFF788CB0};
	/** How long a page takes to settle after it is turned, in milliseconds. */
	private static final long TURN_MS = 170L;
	private static final int PAPER = 0xFFF4E9CC;
	private static final int PAPER_SHADE = 0xFFE3D2A8;
	private static final int INK = 0xFF3B2A1A;
	private static final int INK_SOFT = 0xFF7A6448;
	private static final int BAND = 0xFFD9C394;

	private final List<List<Element>> pages = new ArrayList<>();
	/** Where laid-out pages go while the book is being built; see build(). */
	private List<List<Element>> sink = this.pages;
	/** Where the index starts. The button used to say page one and hope the cover was one page long. */
	private int indexPage = 1;
	private final Map<String, Chapter> chapters = new LinkedHashMap<>();
	private int spread;
	/** Where the reader has jumped from, newest last: what right click and backspace walk back through. */
	private final java.util.ArrayDeque<Integer> history = new java.util.ArrayDeque<>();
	private long turnedAt;
	private int turnedWay;
	private PageButton backButton;
	private PageButton forwardButton;

	public GuideBookScreen() {
		super(Component.translatable("item.forja.guia_de_forja"));
	}

	// ------------------------------------------------------------------ layout

	private int bookLeft() {
		return (this.width - BOOK_W) / 2;
	}

	private int bookTop() {
		return Math.max(2, (this.height - BOOK_H - 24) / 2);
	}

	private int pageX(int side) {
		return this.bookLeft() + 4 + side * (PAGE_W + SPINE);
	}

	@Override
	protected void init() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		int top = this.bookTop();
		this.backButton = this.addRenderableWidget(new PageButton(this.pageX(0) + 8, top + BOOK_H - 22, false, button -> this.turn(-1), true));
		this.forwardButton = this.addRenderableWidget(new PageButton(this.pageX(1) + PAGE_W - 31, top + BOOK_H - 22, true, button -> this.turn(1), true));
		this.addRenderableWidget(Button.builder(Component.translatable("gui.forja.libro.indice"), button -> this.jumpTo(this.indexPage))
			.bounds(this.width / 2 - 104, top + BOOK_H + 3, 100, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
			.bounds(this.width / 2 + 4, top + BOOK_H + 3, 100, 20).build());
		this.updateButtons();
	}

	/**
	 * Pages whose content is taller than the page they are on.
	 *
	 * <p>There should never be any, and for a long time there were: nothing clips a page when it is
	 * drawn, so anything that does not fit is simply drawn outside the book, over the world and over
	 * the buttons. A list rather than a boolean so the test can say <em>which</em>.
	 */
	public List<Integer> overflowingPages() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		List<Integer> over = new ArrayList<>();
		for (int i = 0; i < this.pages.size(); i++) {
			int used = 0;
			for (Element element : this.pages.get(i)) {
				used += element.height();
			}
			if (used > CONTENT_H) {
				over.add(i);
			}
		}
		return over;
	}

	/**
	 * Elements that draw wider than the page, as "page:element:width".
	 *
	 * <p>The sibling of {@link #overflowingPages()}, and the one that was missing. A page can be the
	 * right height and still throw its text off the right-hand edge, which is exactly what the parts
	 * chapter was doing: one line per part, none of them measured, the long ones running across the
	 * spine and over the other page.
	 */
	public List<String> wideElements() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		List<String> over = new ArrayList<>();
		for (int i = 0; i < this.pages.size(); i++) {
			List<Element> page = this.pages.get(i);
			for (int j = 0; j < page.size(); j++) {
				int widest = page.get(j).widest(this.font);
				if (widest > CONTENT_W) {
					over.add(i + ":" + page.get(j).getClass().getSimpleName() + ":" + widest);
				}
			}
		}
		return over;
	}

	/** Elements that had to cut their text to fit, as "page:element". */
	public List<String> elidedElements() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		List<String> cut = new ArrayList<>();
		for (int i = 0; i < this.pages.size(); i++) {
			for (Element element : this.pages.get(i)) {
				if (element.elided()) {
					cut.add(i + ":" + element.getClass().getSimpleName());
				}
			}
		}
		return cut;
	}

	/**
	 * Text in the book that is a translation key and not a translation, as "page:key".
	 *
	 * <p>A key the language file does not have is drawn as itself, and the generator's own check cannot
	 * see the ones the code builds at run time — {@code "gui.forja.libro.evento." + event.id()} passes as
	 * long as any one event has its text. Three of the nine did not, and the events chapter printed
	 * "gui.forja.libro.evento.ventisca" at whoever opened it. So this reads what every element is
	 * actually holding — a Component, or lines already split for the page — and looks for anything
	 * shaped like one of the mod's keys.
	 */
	public List<String> rawKeys() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		java.util.regex.Pattern key = java.util.regex.Pattern.compile("[a-z_]+\\.forja\\.[a-z0-9_.]+");
		List<String> raw = new ArrayList<>();
		for (int i = 0; i < this.pages.size(); i++) {
			for (Element element : this.pages.get(i)) {
				StringBuilder said = new StringBuilder();
				for (Class<?> type = element.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
					for (java.lang.reflect.Field field : type.getDeclaredFields()) {
						if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
							continue;
						}
						try {
							field.setAccessible(true);
							spell(field.get(element), said, 0);
						} catch (ReflectiveOperationException | RuntimeException ignored) {
							// A field that cannot be read holds no text this could have checked anyway.
						}
					}
				}
				java.util.regex.Matcher found = key.matcher(said);
				while (found.find()) {
					raw.add(i + ":" + found.group());
				}
			}
		}
		return raw;
	}

	/** Everything textual under a value, written out: components, split lines, and lists of either. */
	private static void spell(@Nullable Object value, StringBuilder into, int depth) {
		if (value == null || depth > 3) {
			return;
		}
		if (value instanceof Component component) {
			into.append(component.getString()).append(' ');
		} else if (value instanceof FormattedCharSequence line) {
			line.accept((index, style, codePoint) -> {
				into.appendCodePoint(codePoint);
				return true;
			});
			// No space: a key too long for the page is split across lines, and should join back up.
		} else if (value instanceof Iterable<?> many) {
			for (Object one : many) {
				spell(one, into, depth + 1);
			}
			into.append(' ');
		}
	}

	/** Chapters whose page number in the index does not land on their own first page. */
	public List<String> misplacedChapters() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, Chapter> entry : this.chapters.entrySet()) {
			Chapter chapter = entry.getValue();
			List<Element> page = chapter.page >= 0 && chapter.page < this.pages.size() ? this.pages.get(chapter.page) : List.of();
			boolean right = !page.isEmpty() && page.getFirst() instanceof Header header
				&& header.text.getString().equals(chapter.name.getString());
			if (!right) {
				wrong.add(entry.getKey() + "@" + chapter.page);
			}
		}
		return wrong;
	}

	/** How many pages the book ended up with. */
	public int pageCount() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		return this.pages.size();
	}

	private void turn(int direction) {
		int last = (this.pages.size() - 1) / 2;
		int before = this.spread;
		this.spread = Math.max(0, Math.min(last, this.spread + direction));
		if (this.spread != before) {
			this.flip(direction);
		}
		this.updateButtons();
	}

	private void flip(int direction) {
		this.turnedAt = net.minecraft.util.Util.getMillis();
		this.turnedWay = direction < 0 ? -1 : 1;
	}

	/** A jump rather than a turn: it remembers where it left from, so that there is a way back. */
	private void jumpTo(int page) {
		int target = Math.max(0, page) / 2;
		if (target == this.spread) {
			return;
		}
		this.history.addLast(this.spread);
		while (this.history.size() > 32) {
			this.history.removeFirst();
		}
		this.flip(target - this.spread);
		this.goToPage(page);
	}

	private boolean goBack() {
		Integer last = this.history.pollLast();
		if (last == null) {
			return false;
		}
		this.flip(last - this.spread);
		this.goToPage(last * 2);
		return true;
	}

	/** Which of the five sections the open spread belongs to, or -1 on the cover and the index. */
	private int currentSection() {
		String found = null;
		// The chapters are laid out in the order they were written, which is not section order, so
		// "the last one that starts before here" is only right if they are walked by page.
		int best = -1;
		for (Map.Entry<String, Chapter> entry : this.chapters.entrySet()) {
			if (entry.getValue().page <= this.spread * 2 + 1 && entry.getValue().page > best) {
				best = entry.getValue().page;
				found = entry.getKey();
			}
		}
		if (found == null) {
			return -1;
		}
		int index = 0;
		for (List<String> keys : SECTIONS.values()) {
			if (keys.contains(found)) {
				return index;
			}
			index++;
		}
		return -1;
	}

	private int sectionOf(String chapterKey) {
		int index = 0;
		for (List<String> keys : SECTIONS.values()) {
			if (keys.contains(chapterKey)) {
				return index;
			}
			index++;
		}
		return -1;
	}

	/** The first page of a section: of whichever of its chapters comes first in the book. */
	private int sectionPage(int section) {
		int index = 0;
		for (List<String> keys : SECTIONS.values()) {
			if (index++ == section) {
				int first = Integer.MAX_VALUE;
				for (String key : keys) {
					Chapter chapter = this.chapters.get(key);
					if (chapter != null) {
						first = Math.min(first, chapter.page);
					}
				}
				return first == Integer.MAX_VALUE ? 0 : first;
			}
		}
		return 0;
	}

	private String sectionKey(int section) {
		int index = 0;
		for (String key : SECTIONS.keySet()) {
			if (index++ == section) {
				return key;
			}
		}
		return "";
	}

	private int tabX() {
		return this.bookLeft() + BOOK_W + OVERHANG - 5;
	}

	private int tabY(int section) {
		return this.bookTop() + 12 + section * (TAB_H + TAB_GAP);
	}

	/** The section whose tab is under the mouse, or -1. */
	private int tabAt(double mouseX, double mouseY) {
		for (int section = 0; section < SECTIONS.size(); section++) {
			if (mouseX >= this.tabX() && mouseX < this.tabX() + 22 && mouseY >= this.tabY(section) && mouseY < this.tabY(section) + TAB_H) {
				return section;
			}
		}
		return -1;
	}

	/** Clear of the two page-turning arrows, which sit in the bottom corners and would otherwise be on top of it. */
	private int stripLeft() {
		return this.pageX(0) + MARGIN + 26;
	}

	private int stripRight() {
		return this.pageX(1) + PAGE_W - MARGIN - 26;
	}

	private int stripY() {
		return this.bookTop() + 4 + PAGE_H - 6;
	}

	/** The page the strip along the foot points at under the mouse, or -1 when the mouse is not on it. */
	private int stripPageAt(double mouseX, double mouseY) {
		if (mouseY < this.stripY() - 2 || mouseY > this.stripY() + 5 || mouseX < this.stripLeft() || mouseX > this.stripRight()) {
			return -1;
		}
		double share = (mouseX - this.stripLeft()) / (double) (this.stripRight() - this.stripLeft());
		return Math.max(0, Math.min(this.pages.size() - 1, (int) Math.round(share * (this.pages.size() - 1))));
	}

	/** The chapter a page falls in, for the strip's tooltip. */
	private @Nullable Chapter chapterAt(int page) {
		Chapter found = null;
		for (Chapter chapter : this.chapters.values()) {
			if (chapter.page <= page && (found == null || chapter.page > found.page)) {
				found = chapter;
			}
		}
		return found;
	}

	private void updateButtons() {
		this.backButton.visible = this.spread > 0;
		this.forwardButton.visible = this.spread < (this.pages.size() - 1) / 2;
	}

	/** Opens the spread holding a page; also used by the client test. */
	public void goToPage(int page) {
		this.spread = Math.max(0, page) / 2;
		if (this.backButton != null) {
			this.updateButtons();
		}
	}

	/** The pages with a creature drawn on them, for the client test to go and look at. */
	public List<Integer> portraitPages() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		List<Integer> found = new ArrayList<>();
		for (int i = 0; i < this.pages.size(); i++) {
			for (Element element : this.pages.get(i)) {
				if (element instanceof Portrait) {
					found.add(i);
					break;
				}
			}
		}
		return found;
	}

	/** The spread that is open, for the client test. */
	public int openSpread() {
		return this.spread;
	}

	/** The middle of a section's tab, for the client test. */
	public double[] tabPoint(int section) {
		return new double[] {this.tabX() + 12, this.tabY(section) + TAB_H / 2.0};
	}

	/** A point along the strip at the foot of the pages, 0 at the front of the book and 1 at the back. */
	public double[] stripPoint(double share) {
		return new double[] {this.stripLeft() + (this.stripRight() - this.stripLeft()) * share, this.stripY() + 1};
	}

	/** A left click at a point, exactly as the mouse would deliver it; for the client test. */
	public boolean clickAt(double[] point) {
		return this.mouseClicked(new MouseButtonEvent(point[0], point[1], new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
	}

	/** The way back, for the client test. */
	public boolean back() {
		return this.goBack();
	}

	/** GUI coordinates of a point inside a page's content area (side 0 left, 1 right), for the client test. */
	public double[] contentPoint(int side, int dx, int dy) {
		return new double[] {this.pageX(side) + MARGIN + dx, this.bookTop() + 4 + MARGIN + dy};
	}

	/** First page of a chapter by key (mesas, objetos, piezas, materiales, rasgos, mejoras, estadisticas). */
	public int chapterPage(String key) {
		Chapter chapter = this.chapters.get(key);
		return chapter == null ? 0 : chapter.page;
	}

	// ------------------------------------------------------------------ content

	private void build() {
		List<Element> cover = new ArrayList<>();
		cover.add(new Emblem(
			Assembler.create(ForgeType.MARTILLO, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.MADERA, ForgeMaterial.ORO)),
			new ItemStack(ModItems.CORAZON_DE_FORJA)));
		cover.add(new Title(Component.translatable("item.forja.guia_de_forja")));
		cover.add(new IconRow(List.of(
			new ItemStack(ModItems.MESA_DE_PIEZAS),
			Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.DIAMANTE),
			new ItemStack(ModItems.MESA_DE_FORJA),
			Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.DIAMANTE, ForgeMaterial.PIEDRA, ForgeMaterial.ORO))
		)));
		cover.add(new IconRow(List.of(
			Assembler.create(ForgeType.MANGUAL, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.DAMASCO, ForgeMaterial.OBSIDIACERO)),
			Assembler.create(ForgeType.ALAS, List.of(ForgeMaterial.ORO, ForgeMaterial.CUERO)),
			dev.forja.item.Talisman.CUARZO.create(),
			new ItemStack(ModItems.CORAZON_DE_FORJA)
		)));
		cover.add(new Divider());
		cover.add(new Text(Component.translatable("gui.forja.libro.intro"), INK));
		cover.add(new Spacer(4));
		cover.add(new Text(Component.translatable("gui.forja.libro.pasos"), INK_SOFT));
		// The chapters are laid out first, into their own list, because until the index has been broken
		// into pages nobody knows how many pages come before them.
		List<List<Element>> body = new ArrayList<>();
		this.sink = body;

		this.chapter("primeros_pasos", this.firstStepsChapter());
		List<Element> tables = new ArrayList<>(this.tablesChapter());
		tables.add(new Divider());
		tables.addAll(this.cabinetEntry());
		this.chapter("mesas", tables);
		this.chapter("objetos", this.recipesChapter());
		this.chapter("piezas", this.partsChapter());
		this.chapter("materiales", this.materialsChapter());
		this.chapter("rasgos", this.traitsChapter());
		this.chapter("mejoras", this.upgradesChapter());
		this.chapter("potencial", this.potentialChapter());
		this.chapter("maestria", this.masteryChapter());
		this.chapter("aleaciones", this.alloysChapter());
		this.chapter("fundicion", this.foundryChapter());
		this.chapter("temple", this.quenchChapter());
		this.chapter("herrero", this.smithChapter());
		this.chapter("tecnicas", this.techniquesChapter());
		this.chapter("mi_taller", this.myWorkshopChapter());
		this.chapter("sinergias", this.synergiesChapter());
		this.chapter("pactos", this.pactsChapter());
		this.chapter("combate", this.combatChapter());
		this.chapter("accesorios", this.trinketsChapter());
		this.chapter("eventos", this.eventsChapter());
		this.chapter("encargos", this.commissionsChapter());
		this.chapter("amenazas", this.threatsChapter());
		this.chapter("bestiario", this.bestiaryChapter());
		this.chapter("mundo", this.worldChapter());
		this.chapter("estadisticas", this.statsChapter());

		// Twenty chapters is too many for a flat list, so the index is grouped.
		List<Element> index = new ArrayList<>();
		index.add(new Header(Component.translatable("gui.forja.libro.indice")));
		for (Map.Entry<String, List<String>> section : SECTIONS.entrySet()) {
			index.add(new SubHeader(Component.translatable("gui.forja.libro.seccion." + section.getKey())));
			for (String key : section.getValue()) {
				Chapter chapter = this.chapters.get(key);
				if (chapter != null) {
					index.add(new IndexEntry(chapter, chapterIcon(key)));
				}
			}
		}

		// And now the book is put together. The cover and the index used to be added as single raw
		// pages while only the chapters were flowed, so the index — five headings and twenty-four
		// entries — simply ran off the bottom of the page and drew over the world and the buttons.
		this.sink = this.pages;
		this.pages.addAll(this.flow(cover));
		this.indexPage = this.pages.size();
		this.pages.addAll(this.flow(index));
		// The chapters recorded where they started relative to themselves; now they know what is before.
		int before = this.pages.size();
		for (Chapter chapter : this.chapters.values()) {
			chapter.page += before;
		}
		this.pages.addAll(body);
	}

	/**
	 * Break a run of elements into pages that fit.
	 *
	 * <p>Nothing clips a page when it is drawn, so this is the only thing standing between a long
	 * chapter and text spilling out of the book. Everything goes through it — cover, index, chapters.
	 */
	private List<List<Element>> flow(List<Element> body) {
		List<List<Element>> out = new ArrayList<>();
		List<Element> page = new ArrayList<>();
		int used = 0;
		for (int i = 0; i < body.size(); i++) {
			Element element = body.get(i);
			// A section title keeps company with the element under it.
			int needed = element.height() + (element instanceof SubHeader && i + 1 < body.size() ? body.get(i + 1).height() : 0);
			if (used + needed > CONTENT_H && !page.isEmpty()) {
				out.add(page);
				page = new ArrayList<>();
				used = 0;
			}
			page.add(element);
			used += element.height();
		}
		out.add(page);
		return out;
	}

	/** The index, in sections: what the chapters are about rather than the order they were written in. */
	private static final Map<String, List<String>> SECTIONS = new LinkedHashMap<>(Map.of());

	static {
		SECTIONS.put("taller", List.of("primeros_pasos", "mesas", "objetos", "piezas", "materiales", "rasgos", "aleaciones", "fundicion", "temple", "herrero", "tecnicas"));
		SECTIONS.put("mejoras", List.of("mejoras", "potencial", "maestria", "sinergias", "pactos"));
		SECTIONS.put("pelear", List.of("combate", "accesorios"));
		SECTIONS.put("mundo", List.of("eventos", "encargos", "amenazas", "bestiario", "mundo"));
		SECTIONS.put("referencia", List.of("mi_taller", "estadisticas"));
	}

	/** Something to put beside each chapter in the index, so the page reads at a glance. */
	private static ItemStack chapterIcon(String key) {
		return switch (key) {
			case "primeros_pasos" -> new ItemStack(ModItems.GUIA_DE_FORJA);
			case "mesas" -> new ItemStack(ModItems.MESA_DE_FORJA);
			case "objetos" -> Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO));
			case "piezas" -> Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO);
			case "materiales" -> new ItemStack(Items.IRON_INGOT);
			case "rasgos" -> new ItemStack(Items.ECHO_SHARD);
			case "aleaciones" -> new ItemStack(ModItems.alloy("acero"));
			case "fundicion" -> new ItemStack(ModItems.CRISOL_DE_OBSIDIANA);
			case "temple" -> new ItemStack(Items.WATER_BUCKET);
			case "herrero" -> new ItemStack(Items.ANVIL);
			case "tecnicas" -> new ItemStack(Items.BLAST_FURNACE);
			case "mi_taller" -> new ItemStack(Items.WRITABLE_BOOK);
			case "mejoras" -> dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 60);
			case "maestria" -> new ItemStack(ModItems.SELLO);
			case "sinergias" -> new ItemStack(Items.AMETHYST_CLUSTER);
			case "pactos" -> new ItemStack(Items.ROTTEN_FLESH);
			case "potencial" -> new ItemStack(dev.forja.registry.ModItems.FUNDENTE_MAESTRO);
			case "combate" -> Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO));
			case "accesorios" -> new ItemStack(ModItems.CINTURON);
			case "eventos" -> new ItemStack(ModItems.JARRA);
			case "encargos" -> new ItemStack(Items.EMERALD);
			case "amenazas" -> new ItemStack(Items.CROSSBOW);
			case "bestiario" -> new ItemStack(ModItems.CORAZON_DE_FORJA);
			case "mundo" -> new ItemStack(Items.FILLED_MAP);
			case "estadisticas" -> new ItemStack(Items.PAPER);
			default -> ItemStack.EMPTY;
		};
	}

	/** The cabinet, tucked into the chapter about the tables: it is workshop furniture, not a mechanic. */
	private List<Element> cabinetEntry() {
		List<Element> body = new ArrayList<>();
		body.add(new SubHeader(Component.translatable("block.forja.armario_de_piezas")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.ARMARIO_DE_PIEZAS))));
		body.add(new Crafting(new Item[] {Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS,
			Items.IRON_INGOT, Items.CHEST, Items.IRON_INGOT,
			Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS}, new ItemStack(ModItems.ARMARIO_DE_PIEZAS)));
		body.add(new Text(Component.translatable("gui.forja.libro.armario"), INK));
		return body;
	}

	/** Maestria: what the levels give, the gifts that wait at ten, and what a masterpiece is. */
	private List<Element> masteryChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.maestria_intro"), INK));
		body.add(new Spacer(3));
		body.add(new Text(Component.translatable("gui.forja.libro.maestria_bonos"), INK_SOFT));
		body.add(new Spacer(3));
		body.add(new Text(Component.translatable("gui.forja.libro.maestria_niveles"), INK_SOFT));
		body.add(new Spacer(3));
		body.add(new Text(Component.translatable("gui.forja.libro.conjunto"), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("gui.forja.libro.dones.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.dones"), INK));
		// One line per gift, straight off the enum, so the list cannot fall behind the code.
		for (dev.forja.forge.Perk perk : dev.forja.forge.Perk.values()) {
			body.add(new IconRow(List.of(dev.forja.item.SealItem.create(perk))));
			body.add(new Text(Component.translatable("gui.forja.libro.don_linea", perk.displayName(), perk.description()), INK_SOFT));
		}
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("tooltip.forja.obra_maestra")));
		body.add(new Text(Component.translatable("gui.forja.libro.obra_maestra",
			Math.round(dev.forja.forge.Masterpiece.BONUS * 100)), INK));
		return body;
	}

	/**
	 * The first chapter anyone reads: the whole loop in six steps, each one with the things it is done
	 * with, so the book opens on something you can follow rather than on a wall of rules.
	 */
	private List<Element> firstStepsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.pasos_intro"), INK));
		body.add(new Divider());

		body.add(new SubHeader(Component.translatable("gui.forja.libro.paso1.titulo")));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.MESA_DE_PIEZAS), new ItemStack(ModItems.MESA_DE_FORJA), new ItemStack(ModItems.PLANTILLA)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.paso1"), INK_SOFT));

		body.add(new SubHeader(Component.translatable("gui.forja.libro.paso2.titulo")));
		body.add(new IconRow(List.of(
			engravedTemplate(PartType.CABEZA_PICO),
			new ItemStack(Items.IRON_INGOT),
			Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.paso2"), INK_SOFT));

		body.add(new SubHeader(Component.translatable("gui.forja.libro.paso3.titulo")));
		body.add(new IconRow(List.of(
			Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO),
			Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA),
			Assembler.createPart(PartType.ATADURA, ForgeMaterial.CUERO),
			Assembler.create(dev.forja.forge.ForgeType.PICO,
				List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO))
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.paso3"), INK_SOFT));

		body.add(new SubHeader(Component.translatable("gui.forja.libro.paso4.titulo")));
		body.add(new IconRow(List.of(
			new ItemStack(Items.DIAMOND), dev.forja.item.UpgradeOrbItem.create(Upgrade.EFICIENCIA, 60), new ItemStack(Items.ENCHANTED_BOOK)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.paso4"), INK_SOFT));

		body.add(new SubHeader(Component.translatable("gui.forja.libro.paso5.titulo")));
		body.add(new IconRow(List.of(
			new ItemStack(Items.ANVIL), new ItemStack(Items.CAMPFIRE), new ItemStack(Items.WATER_BUCKET)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.paso5"), INK_SOFT));

		body.add(new SubHeader(Component.translatable("gui.forja.libro.paso6.titulo")));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.JARRA), new ItemStack(ModItems.TALISMAN), new ItemStack(ModItems.CORAZON_DE_FORJA)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.paso6"), INK_SOFT));
		return body;
	}

	/** The three choices a smith makes on the way up, and what each one changes. */
	private List<Element> techniquesChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.tecnicas_intro"), INK_SOFT));
		body.add(new IconRow(List.of(new ItemStack(ModItems.MARTILLO_DEL_MAESTRO))));
		body.add(new Text(Component.translatable("gui.forja.libro.martillo_maestro"), INK));
		body.add(new Divider());
		for (int tier = 1; tier <= dev.forja.forge.Technique.TIERS; tier++) {
			List<dev.forja.forge.Technique> options = dev.forja.forge.Technique.ofTier(tier);
			body.add(new SubHeader(Component.translatable("gui.forja.libro.tecnicas_nivel", dev.forja.forge.Technique.levelFor(tier))));
			body.add(new IconRow(options.stream().map(dev.forja.forge.Technique::icon).toList()));
			for (dev.forja.forge.Technique technique : options) {
				body.add(new Text(Component.translatable("gui.forja.libro.tecnica_linea",
					technique.displayName(), technique.description()), INK_SOFT));
			}
			if (tier < dev.forja.forge.Technique.TIERS) {
				body.add(new Divider());
			}
		}
		return body;
	}

	/**
	 * The one page that is about the reader rather than the mod: where their Maestria stands, which
	 * techniques they took, and the tally of what they have actually made.
	 */
	private List<Element> myWorkshopChapter() {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		List<Element> body = new ArrayList<>();
		int level = dev.forja.forge.SmithLevel.level(player);
		body.add(new Text(Component.translatable("gui.forja.libro.taller_propio_intro"), INK_SOFT));
		// Text, not a SubHeader: the line about experience left is too long to fit on one.
		if (player != null) {
			body.add(new Text(dev.forja.forge.SmithLevel.describe(player), INK));
		}
		body.add(new Bars(List.of(new Bar(
			Component.translatable("gui.forja.libro.taller_maestria"),
			level, 0xF0C070,
			Component.literal(level + " / " + dev.forja.forge.SmithLevel.MAX_LEVEL)
		))));
		body.add(new Divider());

		body.add(new SubHeader(Component.translatable("gui.forja.libro.cap.tecnicas")));
		List<ItemStack> taken = new ArrayList<>();
		for (int tier = 1; tier <= dev.forja.forge.Technique.TIERS; tier++) {
			dev.forja.forge.Technique chosen = dev.forja.forge.Techniques.chosen(player, tier);
			body.add(new Text(chosen != null
				? Component.translatable("gui.forja.libro.taller_tecnica", dev.forja.forge.Technique.levelFor(tier), chosen.displayName())
				: Component.translatable("gui.forja.libro.taller_sin_tecnica", dev.forja.forge.Technique.levelFor(tier)),
				chosen != null ? INK : INK_SOFT));
			if (chosen != null) {
				taken.add(chosen.icon());
			}
		}
		if (!taken.isEmpty()) {
			body.add(new IconRow(taken));
		}
		body.add(new Divider());

		body.add(new SubHeader(Component.translatable("gui.forja.libro.taller_cuenta")));
		int forged = dev.forja.forge.SmithRecord.count(player, dev.forja.forge.SmithRecord.FORGED);
		int perfect = dev.forja.forge.SmithRecord.count(player, dev.forja.forge.SmithRecord.PERFECT);
		int upgraded = dev.forja.forge.SmithRecord.count(player, dev.forja.forge.SmithRecord.UPGRADED);
		body.add(new Text(Component.translatable("gui.forja.libro.taller_forjadas", forged), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.taller_perfectas", perfect,
			forged == 0 ? 0 : Math.round(perfect * 100.0F / forged)), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.taller_mejoradas", upgraded), INK));
		return body;
	}

	/** Alloys: the ingredients, and the heat the table needs under it. */
	private List<Element> alloysChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.aleaciones_intro"), INK));
		// Four to a row, however many alloys there happen to be.
		List<ItemStack> ingots = dev.forja.forge.Alloys.ALL.stream().map(dev.forja.forge.Alloys.Recipe::result).toList();
		for (int from = 0; from < ingots.size(); from += 4) {
			body.add(new IconRow(ingots.subList(from, Math.min(from + 4, ingots.size()))));
		}
		body.add(new Divider());
		for (dev.forja.forge.Alloys.Heat heat : dev.forja.forge.Alloys.Heat.values()) {
			if (heat == dev.forja.forge.Alloys.Heat.FRIA) {
				continue;
			}
			body.add(new SubHeader(Component.translatable("gui.forja.libro.calor." + heat.id())));
			body.add(new Text(Component.translatable("gui.forja.libro.calor." + heat.id() + ".desc"), INK_SOFT));
			for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.ALL) {
				if (recipe.heat() != heat) {
					continue;
				}
				List<ItemStack> row = new ArrayList<>();
				for (dev.forja.forge.Alloys.Part part : recipe.inputs()) {
					row.add(new ItemStack(part.item().get(), part.count()));
				}
				row.add(recipe.result());
				body.add(new IconRow(row));
				body.add(new Text(Component.translatable("gui.forja.libro.aleacion_linea", recipe.displayName(), recipe.output()), INK));
			}
			body.add(new Spacer(3));
		}
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("gui.forja.fundir.titulo")));
		body.add(new IconRow(List.of(
			Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO),
			new ItemStack(Items.LAVA_BUCKET),
			new ItemStack(Items.IRON_INGOT)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.fundir_piezas"), INK));
		body.add(new Divider());

		body.add(new SubHeader(Component.translatable("gui.forja.libro.crisol.titulo")));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.CRISOL_DE_BARRO), new ItemStack(ModItems.CRISOL_DE_HIERRO),
			new ItemStack(ModItems.CRISOL_DE_OBSIDIANA), new ItemStack(Items.HOPPER)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.crisol"), INK));
		var barro = dev.forja.block.CrucibleBlock.Tier.BARRO;
		var hierro = dev.forja.block.CrucibleBlock.Tier.HIERRO;
		var obsidiana = dev.forja.block.CrucibleBlock.Tier.OBSIDIANA;
		body.add(new IconRow(List.of(new ItemStack(ModItems.CUBA_DE_COLADA), new ItemStack(ModItems.ASCUA))));
		body.add(new Text(Component.translatable("gui.forja.libro.cuba",
			dev.forja.block.entity.MeltTankBlockEntity.CAPACITY), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.caja.titulo")));
		body.add(new IconRow(List.of(
			Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO),
			new ItemStack(ModItems.alloy("acero_refractario")),
			dev.forja.item.CastingMouldItem.of(PartType.CABEZA_PICO),
			new ItemStack(ModItems.CAJA_DE_MOLDEO_DE_ACERO)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.caja"), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.caja.colar"), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.caja.niveles",
			dev.forja.block.CastingBoxBlock.Tier.BARRO.holds,
			dev.forja.block.CastingBoxBlock.Tier.ACERO.holds), INK_SOFT));
		body.add(new Divider());

		body.add(new Text(Component.translatable("gui.forja.libro.crisol.niveles",
			barro.heat.displayName(), barro.capacity, barro.cook / 20, Math.round(barro.recovery * 100),
			hierro.heat.displayName(), hierro.capacity, hierro.cook / 20, Math.round(hierro.recovery * 100),
			obsidiana.heat.displayName(), obsidiana.capacity, obsidiana.cook / 20, Math.round(obsidiana.recovery * 100)), INK_SOFT));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("gui.forja.libro.aleaciones_comparar")));
		List<Bar> durability = new ArrayList<>();
		for (ForgeMaterial material : List.of(ForgeMaterial.HIERRO, ForgeMaterial.BRONCE, ForgeMaterial.ACERO,
			ForgeMaterial.DAMASCO, ForgeMaterial.ACERO_ESTELAR, ForgeMaterial.OBSIDIACERO, ForgeMaterial.CORAZON)) {
			ItemStack sword = Assembler.create(ForgeType.ESPADA, List.of(material, material, material));
			durability.add(new Bar(material.displayName(), sword.getMaxDamage(), material.color, Component.literal(String.valueOf(sword.getMaxDamage()))));
		}
		body.add(new Bars(durability));
		return body;
	}

	/** The quench: one minute of heat, and what you put it out in. */
	private List<Element> quenchChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.temple_intro", dev.forja.forge.Temple.HOT_TICKS / 20), INK));
		body.add(new IconRow(List.of(
			new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.LAVA_BUCKET),
			new ItemStack(Items.POWDER_SNOW_BUCKET), new ItemStack(Items.HONEY_BLOCK)
		)));
		for (dev.forja.forge.Temple temple : dev.forja.forge.Temple.values()) {
			body.add(new SubHeader(temple.displayName()));
			body.add(new Text(temple.description(), INK_SOFT));
		}
		return body;
	}

	/**
	 * Montar una fundicion: the one chapter that is a set of instructions rather than a reference.
	 *
	 * <p>The foundry is eight blocks that only make sense together, and a smith opening the book has no
	 * way of knowing which to build first. So this reads in the order you build it: pot, tank, pipe,
	 * box, and what each one wants from the one before. Every number in it comes out of the code, so it
	 * cannot drift away from what the blocks actually do.
	 */
	private List<Element> foundryChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion_intro"), INK));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.CRISOL_DE_BARRO), new ItemStack(ModItems.CUBA_DE_COLADA),
			new ItemStack(ModItems.CONDUCTO_DE_COLADA), new ItemStack(ModItems.CAJA_DE_MOLDEO)
		)));
		body.add(new Divider());

		// 1. The pot.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.paso1")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso1.desc",
			dev.forja.block.entity.CrucibleBlockEntity.EMBER_TICKS / 20), INK));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.CRISOL_DE_BARRO), new ItemStack(ModItems.CRISOL_DE_HIERRO),
			new ItemStack(ModItems.CRISOL_DE_OBSIDIANA), new ItemStack(ModItems.ASCUA)
		)));
		for (dev.forja.block.CrucibleBlock.Tier tier : dev.forja.block.CrucibleBlock.Tier.values()) {
			body.add(new Text(Component.translatable("gui.forja.libro.fundicion.crisol_linea",
				Component.translatable("block.forja." + tier.id()), tier.heat.displayName(),
				tier.capacity, tier.cook / 20.0F), INK_SOFT));
		}
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso1.farol"), INK_SOFT));

		// 2. The tanks.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.paso2")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso2.desc",
			dev.forja.block.entity.MeltTankBlockEntity.CAPACITY,
			dev.forja.block.entity.MeltTankBlockEntity.MAX_TANKS), INK));
		body.add(new IconRow(List.of(new ItemStack(ModItems.CUBA_DE_COLADA, 4))));

		// 3. The pipes.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.paso3")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso3.desc",
			dev.forja.block.MeltPipeBlock.REACH), INK));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.CONDUCTO_DE_COLADA), new ItemStack(ModItems.CONDUCTO_DE_ACERO),
			new ItemStack(ModItems.CONDUCTO_DE_DAMASCO)
		)));
		for (dev.forja.block.MeltPipeBlock.Grade grade : dev.forja.block.MeltPipeBlock.Grade.values()) {
			body.add(new Text(Component.translatable("gui.forja.libro.fundicion.conducto_linea",
				Component.translatable("block.forja." + grade.id()), grade.bleeds), INK_SOFT));
		}
		body.add(new IconRow(List.of(new ItemStack(ModItems.CANO_DE_COLADA))));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.cano",
			dev.forja.block.MeltPipeBlock.DROP, dev.forja.block.MeltPipeBlock.FALL_BLEED), INK));

		// 4. Heat, which is the thing that catches people out.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.paso4")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso4.desc",
			dev.forja.block.entity.MeltTankBlockEntity.HOT / dev.forja.block.entity.MeltTankBlockEntity.COOLS
				* dev.forja.block.entity.MeltTankBlockEntity.PUSH_EVERY / 20,
			Math.round(dev.forja.block.entity.MeltTankBlockEntity.REMELT_LOSS * 100)), INK));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.FAROL_DE_PAVESA), new ItemStack(Items.MAGMA_BLOCK), new ItemStack(Items.LAVA_BUCKET)
		)));

		// 5. The box.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.paso5")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso5.desc",
			dev.forja.block.entity.CastingBoxBlockEntity.MOULD_COST), INK));
		body.add(new IconRow(List.of(
			Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO),
			new ItemStack(ModItems.alloy("acero_refractario"), dev.forja.block.entity.CastingBoxBlockEntity.MOULD_COST),
			dev.forja.item.CastingMouldItem.of(PartType.CABEZA_PICO)
		)));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.CAJA_DE_MOLDEO), new ItemStack(ModItems.CAJA_DE_MOLDEO_DE_ACERO),
			new ItemStack(ModItems.CAJA_DE_MOLDEO_DE_DAMASCO)
		)));

		// 6. The strainers, and the one way this whole thing can go wrong.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.paso6")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso6.desc",
			dev.forja.item.StrainerItem.CLAY_HOLDS,
			dev.forja.block.entity.CastingBoxBlockEntity.BATH_COST), INK));
		body.add(new IconRow(List.of(
			dev.forja.item.StrainerItem.of(null),
			dev.forja.item.StrainerItem.of(ForgeMaterial.ACERO),
			dev.forja.item.StrainerItem.of(ForgeMaterial.DAMASCO)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso6.basta",
			Math.round(-dev.forja.forge.Quality.ROUGH_PENALTY * 100)), 0xFF9A3412));

		// 7. The tables: where the line stops making parts and starts making tools.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.paso7")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso7.desc",
			dev.forja.block.entity.CastingBoxBlockEntity.FRAME_COST,
			dev.forja.block.entity.CastingTableBlockEntity.COOK / 20), INK));
		body.add(new IconRow(List.of(
			Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO)),
			new ItemStack(ModItems.alloy("acero_refractario"), dev.forja.block.entity.CastingBoxBlockEntity.FRAME_COST),
			dev.forja.item.CastingFrameItem.of(ForgeType.PICO)
		)));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.MESA_DE_LOSA), new ItemStack(ModItems.MESA_DE_BRASA), new ItemStack(ModItems.MESA_DE_ALMAS)
		)));
		for (dev.forja.block.CastingTableBlock.Tier tier : dev.forja.block.CastingTableBlock.Tier.values()) {
			body.add(new Text(Component.translatable("gui.forja.libro.fundicion.mesa_linea",
				Component.translatable("block.forja." + tier.id()), tier.cools,
				Math.round(tier.luck * 100.0F)), INK_SOFT));
		}
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso7.frio",
			dev.forja.block.entity.CastingTableBlockEntity.SPEND,
			Math.round(-dev.forja.forge.Quality.ROUGH_PENALTY * 100)), 0xFF9A3412));

		// 8. And what the whole thing is for at the top end.
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.blanca")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.blanca.desc"), INK));
		body.add(new IconRow(dev.forja.forge.Alloys.ALL.stream()
			.filter(recipe -> dev.forja.forge.Alloys.WHITE_HEAT_ONLY.contains(recipe.id()))
			.map(dev.forja.forge.Alloys.Recipe::result).toList()));
		return body;
	}

	/** What the smith learns, as opposed to what a piece learns. */
	private List<Element> smithChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.herrero_intro", dev.forja.forge.SmithLevel.MAX_LEVEL), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("gui.forja.libro.perfecta.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.perfecta"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.herencia.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.herencia", dev.forja.menu.ForgeMenu.INHERIT_LEVEL,
			Math.round(dev.forja.menu.ForgeMenu.INHERIT_SHARE * 100)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.firma.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.firma", Math.round(dev.forja.forge.Quality.AFFINITY_BONUS * 100)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.historia.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.historia"), INK_SOFT));
		return body;
	}

	/** The fighting chapter: the parry, the combo, the wounds and the moves. */
	private List<Element> combatChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new SubHeader(Component.translatable("gui.forja.libro.armas_comparadas")));
		body.add(new Text(Component.translatable("gui.forja.libro.armas_comparadas.desc"), INK_SOFT));
		List<Bar> weapons = new ArrayList<>();
		for (ForgeType type : ForgeType.values()) {
			if (type.kind != ForgeType.Kind.WEAPON) {
				continue;
			}
			List<ForgeMaterial> materials = new ArrayList<>();
			for (int slot = 0; slot < type.slots.size(); slot++) {
				materials.add(type.slots.get(slot).role == PartType.Role.HANDLE ? ForgeMaterial.MADERA : ForgeMaterial.HIERRO);
			}
			ForgeStats.Sheet sheet = ForgeStats.sheet(type, materials, dev.forja.upgrade.Upgrades.EMPTY);
			// What the weapon is worth over a second: the blow it lands and how often it lands it.
			float damage = 1.0F + sheet.attackDamage;
			float speed = Math.max(0.2F, 4.0F + sheet.attackSpeed);
			float dps = damage * speed;
			weapons.add(new Bar(Component.translatable("item.forja." + type.id()), dps, 0xC98A45,
				Component.literal(String.format(java.util.Locale.ROOT, "%.1f", dps))));
		}
		body.add(new Bars(weapons));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("gui.forja.libro.parada.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.parada"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.frenesi.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.frenesi", dev.forja.upgrade.Frenzy.MAX_HITS,
			dev.forja.upgrade.Frenzy.WINDOW_TICKS / 20), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.sangrado.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.sangrado"), INK));
		body.add(new IconRow(List.of(
			Assembler.create(ForgeType.DAGA, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.HUESO)),
			Assembler.create(ForgeType.GUADANA, List.of(ForgeMaterial.ACERO, ForgeMaterial.VARA_DE_BLAZE, ForgeMaterial.HIERRO))
		)));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.especiales.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.especiales"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.lanzar.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.lanzar"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.caballo.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.caballo"), INK_SOFT));
		return body;
	}

	/**
	 * Potencial: how far a piece's upgrades can go, where that comes from, and the table that takes one
	 * off again. Every number on the page is read out of forge/Potential, so the book cannot go on saying
	 * fifty after the bench has been moved to sixty.
	 */
	private List<Element> potentialChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.intro"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.potencial.de_donde")));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.fuentes",
			dev.forja.forge.Potential.FLOOR, dev.forja.forge.Potential.CAST_PARTS, dev.forja.forge.Potential.PER_QUALITY,
			dev.forja.forge.Potential.PER_QUALITY * 2, dev.forja.forge.Potential.GREATER_TABLE, dev.forja.forge.Potential.WHOLE_WORKSHOP), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.despues",
			dev.forja.forge.Potential.PER_PACT, dev.forja.forge.Potential.ANNEAL), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.potencial.topes")));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.mesas",
			dev.forja.menu.Station.FORJA.capacity(), dev.forja.menu.Station.FORJA_MAYOR.capacity()), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.tramos",
			dev.forja.forge.Potential.HALF_FROM, dev.forja.forge.Potential.QUARTER_FROM), INK_SOFT));
		body.add(new IconRow(List.of(new ItemStack(dev.forja.registry.ModItems.FUNDENTE_MAESTRO))));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.fundente", dev.forja.forge.Potential.WITHOUT_FLUX), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.fuera"), INK_SOFT));
		// The load: how much a piece carries, what everything weighs, and the seven that are all or nothing.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.potencial.carga.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.carga", dev.forja.forge.Potential.POINTS_PER_LOAD,
			dev.forja.forge.Potential.CAPACITY_FROM, dev.forja.forge.Potential.capacity(dev.forja.forge.Potential.FLOOR),
			dev.forja.forge.Potential.MOST_CAPACITY), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.carga.libres", dev.forja.upgrade.Synergy.THRESHOLD), INK_SOFT));
		for (int weight : new int[] {4, 3, 1}) {
			body.add(new Text(Component.translatable("gui.forja.libro.potencial.pesan", weight, namesOf(upgrade ->
				dev.forja.forge.Potential.weight(upgrade) == weight)), INK_SOFT));
		}
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.pesan.resto", 2), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.todo_o_nada",
			namesOf(dev.forja.forge.Potential::allOrNothing), dev.forja.forge.Potential.HEAVY), INK_SOFT));
		body.add(new SubHeader(Component.translatable("block.forja.mesa_de_extraccion")));
		body.add(new IconRow(List.of(new ItemStack(dev.forja.registry.ModItems.MESA_DE_EXTRACCION), new ItemStack(dev.forja.registry.ModItems.ORBE_VACIO))));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.extraccion", dev.forja.menu.ExtractionMenu.PERCENT_PER_STEP), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.orbes"), INK_SOFT));
		return body;
	}

	/** The names of the upgrades that pass a test, in the order the game lists them, with commas between. */
	private static Component namesOf(java.util.function.Predicate<Upgrade> test) {
		net.minecraft.network.chat.MutableComponent names = Component.empty();
		boolean first = true;
		for (Upgrade upgrade : Upgrade.values()) {
			if (test.test(upgrade)) {
				if (!first) {
					names.append(", ");
				}
				names.append(upgrade.displayName());
				first = false;
			}
		}
		return names;
	}

	/** The pacts, kept apart from the ordinary upgrades on purpose. */
	private List<Element> pactsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.pactos_intro"), INK));
		for (Upgrade pact : List.of(Upgrade.PACTO_DE_SED, Upgrade.PACTO_DE_VIDRIO, Upgrade.PACTO_DE_SOMBRA,
			Upgrade.PACTO_DE_LA_PRISA)) {
			body.add(new SubHeader(pact.displayName()));
			body.add(new Text(pact.effect(100), INK_SOFT));
		}
		body.add(new Spacer(3));
		body.add(new Text(Component.translatable("gui.forja.libro.pactos_aviso"), INK));
		return body;
	}

	/** The four events, the flask and the star iron they bring down. */
	private List<Element> eventsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.eventos_intro"), INK));
		body.add(new IconRow(List.of(new ItemStack(ModItems.JARRA), new ItemStack(ModItems.HIERRO_ESTELAR))));
		body.add(new Crafting(new Item[] {Items.GLASS, Items.NETHER_STAR, Items.GLASS, Items.GLASS, Items.ECHO_SHARD, Items.GLASS,
			Items.GLASS, Items.GLASS, Items.GLASS}, new ItemStack(ModItems.JARRA)));
		body.add(new Divider());
		for (dev.forja.world.WorldEvents event : dev.forja.world.WorldEvents.values()) {
			body.add(new SubHeader(event.displayName()));
			body.add(new IconRow(List.of(new ItemStack(eventIcon(event)), dev.forja.item.UpgradeOrbItem.create(event.upgrade, 100))));
			body.add(new Text(Component.translatable("gui.forja.libro.evento." + event.id()), INK_SOFT));
			body.add(new Text(Component.translatable("gui.forja.libro.evento_mejora", event.upgrade.displayName(), event.upgrade.effect(100)), INK));
		}
		return body;
	}

	/** A template already engraved with a shape, for the pictures in the book. */
	private static ItemStack engravedTemplate(PartType part) {
		ItemStack template = new ItemStack(ModItems.PLANTILLA);
		dev.forja.item.TemplateItem.engrave(template, part);
		return template;
	}

	/** Something to put next to each event's name, so the chapter reads at a glance. */
	private static Item eventIcon(dev.forja.world.WorldEvents event) {
		return switch (event) {
			case METEORITOS -> ModItems.HIERRO_ESTELAR;
			case TORMENTA_ARCANA -> Items.LIGHTNING_ROD.weathering().unaffected();
			case NIEBLA_DE_ALMAS -> Items.SOUL_LANTERN;
			case AURORA -> Items.END_ROD;
			case LUNA_DE_SANGRE -> Items.REDSTONE;
			case ECLIPSE -> Items.INK_SAC;
			case VENTISCA -> Items.POWDER_SNOW_BUCKET;
			case MAREA_VIVA -> Items.PRISMARINE_SHARD;
			case LLUVIA_DE_PAVESAS -> ModItems.FAROL_DE_PAVESA;
		};
	}

	/** Commissions: what the Forjador actually wants and what it pays. */
	private List<Element> commissionsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.encargos_intro", dev.forja.world.Commissions.UPGRADE_PERCENT), INK));
		body.add(new IconRow(List.of(new ItemStack(Items.EMERALD), dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 80), new ItemStack(ModItems.PLANTILLA))));
		body.add(new Text(Component.translatable("gui.forja.libro.encargos_como"), INK_SOFT));
		return body;
	}

	/** What comes looking for you once you have something worth taking. */
	private List<Element> threatsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new SubHeader(Component.translatable("gui.forja.libro.elites.titulo")));
		body.add(new IconRow(List.of(new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.ROTTEN_FLESH), new ItemStack(Items.BONE))));
		body.add(new Text(Component.translatable("gui.forja.libro.elites", Math.round(dev.forja.ForjaConfig.get().elites * 100),
			Math.round(dev.forja.world.Elites.HEALTH)), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.ataques.elite",
			Math.round(dev.forja.world.Elites.WIND_MEND * 100)), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.automata.titulo")));
		body.add(new IconRow(List.of(
			Assembler.createPart(PartType.CABEZA_MARTILLO, ForgeMaterial.HIERRO),
			Assembler.createPart(PartType.PLACA_ESCUDO, ForgeMaterial.PIEDRA),
			Assembler.createPart(PartType.GARFIO, ForgeMaterial.HIERRO)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.automata", Math.round(dev.forja.entity.ForgeAutomaton.HEALTH)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.saqueadores.titulo")));
		body.add(new IconRow(List.of(new ItemStack(Items.CROSSBOW), new ItemStack(Items.IRON_AXE), new ItemStack(Items.BELL))));
		body.add(new Text(Component.translatable("gui.forja.libro.saqueadores", dev.forja.world.ForgeRaiders.BAND), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("gui.forja.libro.herrero_caido.titulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.FRAGUA_APAGADA), new ItemStack(ModItems.CORAZON_DE_FORJA))));
		body.add(new Text(Component.translatable("gui.forja.libro.herrero_caido"), INK));
		List<ItemStack> offering = new ArrayList<>();
		for (dev.forja.block.DeadForgeBlock.Offering piece : dev.forja.block.DeadForgeBlock.OFFERING) {
			offering.add(new ItemStack(piece.item().get(), piece.count()));
		}
		body.add(new IconRow(offering));
		body.add(new Text(Component.translatable("gui.forja.libro.herrero_caido_fases", Math.round(dev.forja.entity.FallenSmith.HEALTH),
			dev.forja.entity.FallenSmith.EMBERS), INK_SOFT));
		return body;
	}

	/**
	 * The three things the mod puts in the world to fight, with what each one leaves behind. Short on
	 * purpose: the chapter on threats says how they behave, this one says what they are worth.
	 */
	private List<Element> bestiaryChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario_intro"), INK_SOFT));

		body.add(new SubHeader(Component.translatable("entity.forja.automata_de_forja")));
		body.add(new Portrait(level -> new dev.forja.entity.ForgeAutomaton(dev.forja.registry.ModEntities.AUTOMATA, level)));
		body.add(new IconRow(List.of(
			Assembler.createPart(PartType.CABEZA_MARTILLO, ForgeMaterial.HIERRO),
			Assembler.createPart(PartType.BOLA, ForgeMaterial.PIEDRA),
			new ItemStack(Items.IRON_NUGGET)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.automata", Math.round(dev.forja.entity.ForgeAutomaton.HEALTH)), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.ataques.automata",
			Math.round(dev.forja.entity.ForgeAutomaton.STEAM_DAMAGE)), INK_SOFT));
		body.add(new Divider());

		body.add(new SubHeader(Component.translatable("entity.forja.coraza_vacia")));
		body.add(new Portrait(level -> new dev.forja.entity.HollowArmor(dev.forja.registry.ModEntities.CORAZA, level)));
		body.add(new IconRow(List.of(
			Assembler.createPart(PartType.PLACA_PECHERA, ForgeMaterial.HIERRO),
			Assembler.createPart(PartType.PLACA_CASCO, ForgeMaterial.HIERRO),
			dev.forja.item.UpgradeOrbItem.create(Upgrade.PROTECCION, 25)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.coraza",
			Math.round(dev.forja.entity.HollowArmor.HEALTH),
			Math.round(dev.forja.entity.HollowArmor.UNFORGED_SHARE * 100)), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.coraza_alma",
			Math.round(dev.forja.entity.HollowArmor.SOUL_REACH),
			Math.round(dev.forja.entity.HollowArmor.SOUL_HEAL),
			dev.forja.entity.HollowArmor.SOUL_RAGE / 20), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.ataques.coraza",
			Math.round(dev.forja.entity.HollowArmor.DASH_DAMAGE)), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.coraza_visita",
			Math.round(dev.forja.ForjaConfig.get().corazas * 100)), INK_SOFT));
		body.add(new Divider());

		body.add(new SubHeader(Component.translatable("entity.forja.pavesa")));
		body.add(new Portrait(level -> new dev.forja.entity.EmberWisp(dev.forja.registry.ModEntities.PAVESA, level)));
		body.add(new IconRow(List.of(
			new ItemStack(Items.COAL), new ItemStack(Items.BLAZE_POWDER), new ItemStack(ModItems.HUEVO_PAVESA)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.pavesa",
			Math.round(dev.forja.entity.EmberWisp.HEALTH),
			Math.round(dev.forja.entity.EmberWisp.DIVE_DAMAGE),
			Math.round(dev.forja.entity.EmberWisp.FLARE_DAMAGE)), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.farol"), INK));
		body.add(new IconRow(List.of(new ItemStack(Items.LANTERN), new ItemStack(ModItems.FAROL_DE_PAVESA))));
		body.add(new Text(Component.translatable("gui.forja.libro.farol_precio"), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.pavesa_visita",
			Math.round(dev.forja.ForjaConfig.get().pavesas * 100), dev.forja.world.WispWatch.limit()), INK_SOFT));
		body.add(new Divider());

		body.add(new SubHeader(Component.translatable("entity.forja.capitan_saqueador")));
		body.add(new IconRow(List.of(
			dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 50),
			dev.forja.item.UpgradeOrbItem.create(Upgrade.PROTECCION, 75)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.capitan", dev.forja.world.ForgeRaiders.BAND), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.ataques.capitan",
			dev.forja.world.ForgeRaiders.RALLY_TICKS / 20), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.tumulo")));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.YUNQUE_DEL_HERRERO), new ItemStack(Items.SOUL_LANTERN), new ItemStack(ModItems.SELLO)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.tumulo_desc"), INK_SOFT));
		body.add(new Divider());

		body.add(new SubHeader(Component.translatable("gui.forja.libro.campamento")));
		body.add(new IconRow(List.of(
			new ItemStack(Items.SPRUCE_LOG), new ItemStack(Items.CAMPFIRE), new ItemStack(Items.BELL), new ItemStack(Items.FILLED_MAP)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.campamento_desc"), INK_SOFT));
		body.add(new Divider());

		body.add(new SubHeader(Component.translatable("entity.forja.herrero_caido")));
		body.add(new Portrait(level -> new dev.forja.entity.FallenSmith(dev.forja.registry.ModEntities.HERRERO_CAIDO, level)));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.CORAZON_DE_FORJA), new ItemStack(ModItems.YUNQUE_DEL_HERRERO), new ItemStack(ModItems.FRAGUA_APAGADA)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.herrero", Math.round(dev.forja.entity.FallenSmith.HEALTH)), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.ataques.herrero",
			Math.round(dev.forja.entity.FallenSmith.WAVE_DAMAGE)), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.yunque"), INK_SOFT));
		// The eleven that came after the book was written. They were in the world for days with no page:
		// the only way to learn that the Herrumbre eats armour, or that the Nucleo is better left
		// alone, was to find out. One page each — what it is, what it costs you, and the way round it.
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.herrumbre")));
		body.add(new Portrait(level -> new dev.forja.entity.RustSwarm(dev.forja.registry.ModEntities.HERRUMBRE, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.herrumbre", Math.round(dev.forja.entity.RustSwarm.HEALTH)), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.ascua_mayor")));
		body.add(new Portrait(level -> new dev.forja.entity.GreaterEmber(dev.forja.registry.ModEntities.ASCUA_MAYOR, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.ascua_mayor", Math.round(dev.forja.entity.GreaterEmber.HEALTH)), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.escoria_viviente")));
		body.add(new Portrait(level -> new dev.forja.entity.LivingSlag(dev.forja.registry.ModEntities.ESCORIA, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.escoria_viviente"), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.yunque_andante")));
		body.add(new Portrait(level -> new dev.forja.entity.WalkingAnvil(dev.forja.registry.ModEntities.YUNQUE_ANDANTE, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.yunque_andante", Math.round(dev.forja.entity.WalkingAnvil.HEALTH)), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.percutor")));
		body.add(new Portrait(level -> new dev.forja.entity.Striker(dev.forja.registry.ModEntities.PERCUTOR, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.percutor", Math.round(dev.forja.entity.Striker.HEALTH)), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.tenaza")));
		body.add(new Portrait(level -> new dev.forja.entity.Tongs(dev.forja.registry.ModEntities.TENAZA, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.tenaza", Math.round(dev.forja.entity.Tongs.HEALTH)), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.cargador_de_carbon")));
		body.add(new Portrait(level -> new dev.forja.entity.CoalHauler(dev.forja.registry.ModEntities.CARGADOR_DE_CARBON, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.cargador_de_carbon", Math.round(dev.forja.entity.CoalHauler.HEALTH)), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.templador")));
		body.add(new Portrait(level -> new dev.forja.entity.Quencher(dev.forja.registry.ModEntities.TEMPLADOR, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.templador", Math.round(dev.forja.entity.Quencher.HEALTH)), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.nucleo_estelar")));
		body.add(new Portrait(level -> new dev.forja.entity.StarCore(dev.forja.registry.ModEntities.NUCLEO_ESTELAR, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.nucleo_estelar", Math.round(dev.forja.entity.StarCore.HEALTH)), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.molde_roto")));
		body.add(new Portrait(level -> new dev.forja.entity.BrokenMould(dev.forja.registry.ModEntities.MOLDE_ROTO, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.molde_roto", Math.round(dev.forja.entity.BrokenMould.HEALTH)), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("entity.forja.guardian_de_cuno")));
		body.add(new Portrait(level -> new dev.forja.entity.CuneGuardian(dev.forja.registry.ModEntities.GUARDIAN_DE_CUNO, level)));
		body.add(new Text(Component.translatable("gui.forja.libro.bestiario.guardian_de_cuno", Math.round(dev.forja.entity.CuneGuardian.HEALTH)), INK));
		return body;
	}

	/** Talismans and the tool belt: what you carry rather than what you swing. */
	private List<Element> trinketsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new SubHeader(Component.translatable("gui.forja.libro.talismanes.titulo")));
		body.add(new IconRow(java.util.Arrays.stream(dev.forja.item.Talisman.values()).map(dev.forja.item.Talisman::create).toList()));
		body.add(new Text(Component.translatable("gui.forja.libro.talismanes"), INK));
		for (dev.forja.item.Talisman talisman : dev.forja.item.Talisman.values()) {
			body.add(new Text(Component.translatable("gui.forja.libro.talisman_linea", talisman.displayName(), talisman.description()), INK_SOFT));
		}
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("item.forja.yunque_portatil")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.YUNQUE_PORTATIL))));
		body.add(new Crafting(new Item[] {Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT,
			Items.LEATHER, Items.IRON_BLOCK, Items.LEATHER, null, Items.STICK, null},
			new ItemStack(ModItems.YUNQUE_PORTATIL)));
		body.add(new Text(Component.translatable("gui.forja.libro.yunque_portatil"), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("gui.forja.libro.cinturon.titulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.CINTURON))));
		body.add(new Crafting(new Item[] {Items.LEATHER, Items.LEATHER, Items.LEATHER, Items.IRON_INGOT, Items.STRING, Items.IRON_INGOT,
			Items.LEATHER, Items.LEATHER, Items.LEATHER}, new ItemStack(ModItems.CINTURON)));
		body.add(new Text(Component.translatable("gui.forja.libro.cinturon", dev.forja.item.ToolBeltItem.SLOTS), INK));
		return body;
	}

	/** The upgrade pairs that do something extra together. */
	private List<Element> synergiesChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.sinergias_intro", dev.forja.upgrade.Synergy.THRESHOLD), INK_SOFT));
		for (dev.forja.upgrade.Synergy synergy : dev.forja.upgrade.Synergy.values()) {
			body.add(new Spacer(2));
			body.add(new SubHeader(synergy.displayName()));
			body.add(new Text(Component.translatable("gui.forja.libro.sinergia_par", synergy.first.displayName(), synergy.second.displayName()), INK_SOFT));
			body.add(new Text(synergy.description(), INK));
		}
		return body;
	}

	/** Orbs, broken gear, trims, and where forged things turn up in the world. */
	private List<Element> worldChapter() {
		ItemStack broken = Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO));
		broken.setDamageValue(broken.getMaxDamage());
		List<Element> body = new ArrayList<>();
		for (String topic : List.of("orbes", "rotos", "botin", "monstruos", "aldeanos", "taller", "adornos")) {
			body.add(new SubHeader(Component.translatable("gui.forja.libro.mundo." + topic + ".titulo")));
			if (topic.equals("orbes")) {
				body.add(new IconRow(List.of(
					dev.forja.item.UpgradeOrbItem.create(dev.forja.upgrade.Upgrade.FILO, 40),
					dev.forja.item.UpgradeOrbItem.create(dev.forja.upgrade.Upgrade.FORTUNA, 25),
					dev.forja.item.UpgradeOrbItem.create(dev.forja.upgrade.Upgrade.PROTECCION, 30)
				)));
			} else if (topic.equals("rotos")) {
				body.add(new IconRow(List.of(broken)));
			}
			body.add(new Text(Component.translatable("gui.forja.libro.mundo." + topic), INK));
			if (topic.equals("rotos")) {
				// Poured rather than crafted: the bar comes out of a crucible like every other ingot.
				body.add(new IconRow(List.of(new ItemStack(ModItems.alloy("acero_refractario")),
					new ItemStack(Items.CLAY_BALL, 2), new ItemStack(ModItems.LINGOTE_DE_TEMPLE, 2))));
			}
			body.add(new Spacer(3));
		}
		return body;
	}

	private void chapter(String key, List<Element> body) {
		Chapter chapter = new Chapter(Component.translatable("gui.forja.libro.cap." + key));
		// Relative to the chapters, not to the book: build() adds the cover and the index in front
		// afterwards and shifts every chapter by however many pages those turned out to be.
		chapter.page = this.sink.size();
		this.chapters.put(key, chapter);
		List<Element> whole = new ArrayList<>();
		whole.add(new Header(chapter.name));
		whole.addAll(body);
		this.sink.addAll(this.flow(whole));
	}

	private List<Element> tablesChapter() {
		Item planks = Items.OAK_PLANKS;
		Item iron = Items.IRON_INGOT;
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.mesa_piezas"), INK));
		body.add(new Crafting(new Item[] {iron, iron, iron, planks, Items.GRINDSTONE, planks, planks, null, planks}, new ItemStack(ModItems.MESA_DE_PIEZAS)));
		body.add(new Text(Component.translatable("gui.forja.libro.plantilla"), INK));
		ItemStack engraved = new ItemStack(ModItems.PLANTILLA);
		TemplateItem.engrave(engraved, PartType.CABEZA_PICO);
		body.add(new Crafting(new Item[] {Items.STICK, planks, null, planks, Items.STICK, null, null, null, null}, new ItemStack(ModItems.PLANTILLA, 2)));
		body.add(new IconRow(List.of(new ItemStack(ModItems.PLANTILLA), engraved, Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO))));
		body.add(new Text(Component.translatable("gui.forja.libro.mesa_forja"), INK));
		body.add(new Crafting(new Item[] {iron, iron, iron, planks, Items.CRAFTING_TABLE, planks, planks, null, planks}, new ItemStack(ModItems.MESA_DE_FORJA)));
		body.add(new Text(Component.translatable("gui.forja.libro.receta_libro"), INK));
		body.add(new Crafting(new Item[] {Items.BOOK, iron, null, null, null, null, null, null, null}, new ItemStack(ModItems.GUIA_DE_FORJA)));
		return body;
	}

	private List<Element> recipesChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.objetos_intro"), INK_SOFT));
		for (ForgeType type : ForgeType.values()) {
			body.add(new Recipe(type));
		}
		// What the special weapons do beyond their stats.
		for (String weapon : List.of("lanza", "tridente", "cincel", "mazo", "espadon", "guadana", "mangual", "guanteletes", "arcos", "flecha", "gancho", "montura", "cana", "alas", "escudo", "baculo", "grimorio")) {
			body.add(new Spacer(2));
			body.add(new SubHeader(Component.translatable("gui.forja.libro.especial." + weapon + ".titulo")));
			body.add(new Text(Component.translatable("gui.forja.libro.especial." + weapon), INK));
		}
		return body;
	}

	private List<Element> partsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.piezas_intro"), INK_SOFT));
		for (PartType part : PartType.values()) {
			body.add(new Part(part));
		}
		return body;
	}

	private List<Element> materialsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.materiales_intro"), INK_SOFT));
		for (ForgeMaterial material : ForgeMaterial.values()) {
			body.add(new Material(material));
		}
		return body;
	}

	private List<Element> traitsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.rasgos_intro"), INK_SOFT));
		for (ForgeMaterial.Trait trait : ForgeMaterial.Trait.values()) {
			if (trait != ForgeMaterial.Trait.NONE) {
				body.add(new Trait(trait));
			}
		}
		return body;
	}

	private List<Element> upgradesChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.mejoras_intro"), INK_SOFT));
		for (String section : GuideText.UPGRADE_SECTIONS) {
			List<Upgrade> listed = new ArrayList<>();
			for (Upgrade upgrade : Upgrade.values()) {
				if (GuideText.section(upgrade).equals(section)) {
					listed.add(upgrade);
				}
			}
			if (listed.isEmpty()) {
				continue;
			}
			body.add(new SubHeader(Component.translatable("gui.forja.libro.seccion." + section)));
			for (Upgrade upgrade : listed) {
				body.add(new UpgradeEntry(upgrade));
			}
		}
		return body;
	}

	private List<Element> statsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.estadisticas_intro"), INK));
		body.add(new ColorScale());
		body.add(new Text(Component.translatable("gui.forja.libro.estadisticas_invertidas"), INK_SOFT));
		body.add(new Spacer(4));
		body.add(new Text(Component.translatable("gui.forja.libro.estadisticas_donde"), INK_SOFT));
		return body;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int left = this.bookLeft();
		int top = this.bookTop();
		// The tabs first, so that the cover lies over the end of each and they come out from under it.
		int open = this.currentSection();
		int pointed = this.tabAt(mouseX, mouseY);
		for (int section = 0; section < SECTIONS.size(); section++) {
			boolean lit = section == open || section == pointed;
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BOOK, this.tabX(), this.tabY(section),
				section * 24.0F, lit ? 234.0F : 212.0F, lit ? 22 : 18, TAB_H, 512, 256);
		}
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BOOK, left - OVERHANG, top - OVERHANG,
			0.0F, 0.0F, BOOK_W + OVERHANG * 2, BOOK_H + OVERHANG * 2, 512, 256);
		// The ribbon, out of the foot of the spine, in the gap between the two buttons.
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BOOK, left + 4 + PAGE_W + 1, top + BOOK_H + OVERHANG - 2,
			130.0F, 212.0F, 4, 24, 512, 256);
		for (int section = 0; section < SECTIONS.size(); section++) {
			ItemStack icon = chapterIcon(SECTIONS.get(this.sectionKey(section)).get(0));
			boolean lit = section == open || section == pointed;
			g.pose().pushMatrix();
			g.pose().translate(this.tabX() + (lit ? 8 : 5), this.tabY(section) + 5);
			g.pose().scale(0.62F, 0.62F);
			g.item(icon, 0, 0);
			g.pose().popMatrix();
		}

		for (int side = 0; side < 2; side++) {
			int page = this.spread * 2 + side;
			if (page >= this.pages.size()) {
				continue;
			}
			int x = this.pageX(side) + MARGIN;
			int y = top + 4 + MARGIN;
			for (Element element : this.pages.get(page)) {
				element.draw(this, g, x, y, mouseX, mouseY);
				y += element.height();
			}
			String number = String.valueOf(page + 1);
			this.small(g, Component.literal(number), this.pageX(side) + PAGE_W / 2 - Math.round(this.font.width(number) * SMALL / 2), top + BOOK_H - 19, INK_SOFT);
		}
		this.strip(g, mouseX, mouseY);
		this.turning(g);
	}

	/**
	 * Where you are in the whole book, along the foot of both pages: one run of colour per section, a
	 * mark for the open spread, and a lighter mark under the mouse for where a click would take you.
	 */
	private void strip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int from = this.stripLeft();
		int to = this.stripRight();
		int y = this.stripY();
		int total = Math.max(1, this.pages.size() - 1);
		List<Chapter> ordered = new ArrayList<>(this.chapters.values());
		ordered.sort(java.util.Comparator.comparingInt(chapter -> chapter.page));
		List<String> keys = new ArrayList<>(this.chapters.keySet());
		keys.sort(java.util.Comparator.comparingInt(key -> this.chapters.get(key).page));
		g.fill(from, y, to, y + 3, 0x50806848);
		for (int i = 0; i < keys.size(); i++) {
			int start = this.chapters.get(keys.get(i)).page;
			int end = i + 1 < keys.size() ? this.chapters.get(keys.get(i + 1)).page : this.pages.size();
			int x0 = from + Math.round((to - from) * (start / (float) total));
			int x1 = Math.min(to, from + Math.round((to - from) * (end / (float) total)));
			int section = this.sectionOf(keys.get(i));
			int colour = section < 0 ? 0xFF9A8868 : SECTION_COLOURS[section];
			// A pixel of paper between chapters, so the strip also says how many there are and how long.
			g.fill(x0, y, Math.max(x0 + 1, x1 - 1), y + 3, colour & 0x00FFFFFF | 0xC0000000);
		}
		// It runs straight across the gutter; the spine is simply in front of it.
		int spineX = this.bookLeft() + 4 + PAGE_W;
		int here = from + Math.round((to - from) * (this.spread * 2 / (float) total));
		if (here >= spineX - 1 && here < spineX + SPINE) {
			here = spineX - 2;
		}
		g.fill(here - 1, y - 2, here + 2, y + 5, INK);
		g.fill(here, y - 1, here + 1, y + 4, 0xFFFFE9B0);
		int pointed = this.stripPageAt(mouseX, mouseY);
		if (pointed >= 0) {
			int there = from + Math.round((to - from) * (pointed / (float) total));
			g.fill(there - 1, y - 2, there + 2, y + 5, 0xC06B2A0E);
		}
	}

	/**
	 * The page settling after a turn: the new spread comes up out of the paper, and the shadow of the
	 * leaf that just went over crosses it the way the leaf did.
	 */
	private void turning(GuiGraphicsExtractor g) {
		long since = net.minecraft.util.Util.getMillis() - this.turnedAt;
		if (this.turnedWay == 0 || since >= TURN_MS) {
			return;
		}
		float through = since / (float) TURN_MS;
		float left = (1.0F - through) * (1.0F - through);
		int top = this.bookTop() + 4;
		int veil = Math.round(left * 215.0F) << 24 | (PAPER & 0xFFFFFF);
		for (int side = 0; side < 2; side++) {
			g.fill(this.pageX(side), top, this.pageX(side) + PAGE_W, top + PAGE_H - 9, veil);
		}
		int span = PAGE_W * 2 + SPINE;
		float sweep = this.turnedWay > 0 ? 1.0F - through : through;
		int x = this.pageX(0) + Math.round(span * sweep);
		int dark = Math.round(left * 70.0F) << 24;
		g.fillGradient(Math.max(this.pageX(0), x - 10), top, Math.min(this.pageX(1) + PAGE_W, x + 10), top + PAGE_H, dark, dark);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int tab = this.tabAt(mouseX, mouseY);
		if (tab >= 0) {
			g.setTooltipForNextFrame(this.font, Component.translatable("gui.forja.libro.seccion." + this.sectionKey(tab)), mouseX, mouseY);
			return;
		}
		int stripPage = this.stripPageAt(mouseX, mouseY);
		if (stripPage >= 0) {
			Chapter chapter = this.chapterAt(stripPage);
			Component where = chapter == null ? this.title : chapter.name;
			g.setTooltipForNextFrame(this.font, Component.translatable("gui.forja.libro.pagina", where, stripPage + 1), mouseX, mouseY);
			return;
		}
		Element hovered = null;
		int hoveredX = 0;
		int hoveredY = 0;
		for (int side = 0; side < 2; side++) {
			int page = this.spread * 2 + side;
			if (page >= this.pages.size()) {
				continue;
			}
			int x = this.pageX(side) + MARGIN;
			int y = this.bookTop() + 4 + MARGIN;
			for (Element element : this.pages.get(page)) {
				if (mouseX >= x && mouseX < x + CONTENT_W && mouseY >= y && mouseY < y + element.height()) {
					hovered = element;
					hoveredX = x;
					hoveredY = y;
				}
				y += element.height();
			}
		}
		if (hovered == null) {
			return;
		}
		Object tooltip = hovered.tooltip(hoveredX, hoveredY, mouseX, mouseY);
		if (tooltip instanceof ItemStack stack) {
			g.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
		} else if (tooltip instanceof List<?> list) {
			List<Component> lines = new ArrayList<>();
			for (Object line : list) {
				lines.add((Component) line);
			}
			g.setTooltipForNextFrame(GuideText.wrap(this.font, lines), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 1 && this.goBack()) {
			return true;
		}
		if (event.button() == 0) {
			int tab = this.tabAt(event.x(), event.y());
			if (tab >= 0) {
				this.jumpTo(this.sectionPage(tab));
				return true;
			}
			int stripPage = this.stripPageAt(event.x(), event.y());
			if (stripPage >= 0) {
				this.jumpTo(stripPage);
				return true;
			}
		}
		if (event.button() == 0) {
			for (int side = 0; side < 2; side++) {
				int page = this.spread * 2 + side;
				if (page >= this.pages.size()) {
					continue;
				}
				int x = this.pageX(side) + MARGIN;
				int y = this.bookTop() + 4 + MARGIN;
				for (Element element : this.pages.get(page)) {
					if (event.x() >= x && event.x() < x + CONTENT_W && event.y() >= y && event.y() < y + element.height() && element.click(this)) {
						return true;
					}
					y += element.height();
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0.0) {
			this.turn(scrollY < 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		// Page up / page down and the arrow keys turn pages.
		return switch (event.key()) {
			// Backspace goes back to wherever the last jump was made from; Home goes to the index.
			case 259 -> this.goBack();
			case 268 -> {
				this.jumpTo(this.indexPage);
				yield true;
			}
			case 266, 263 -> {
				this.turn(-1);
				yield true;
			}
			case 267, 262 -> {
				this.turn(1);
				yield true;
			}
			default -> false;
		};
	}

	/**
	 * Cut a line down to what fits, with an ellipsis if it had to be cut.
	 *
	 * <p>The room is given in page pixels and converted here, because every caller thinks in page
	 * pixels and none of them should have to remember that small text is drawn at three quarters.
	 */
	private FormattedCharSequence fit(Component text, int room) {
		int width = Math.round(room / SMALL);
		if (this.font.width(text) <= width) {
			return text.getVisualOrderText();
		}
		return Component.literal(this.font.plainSubstrByWidth(text.getString(), width - this.font.width("…")) + "…")
			.setStyle(text.getStyle()).getVisualOrderText();
	}

	/** How wide that line will actually be drawn, in page pixels. */
	private int widthOf(Component text, int room) {
		return Math.min(room, Math.round(this.font.width(text) * SMALL));
	}

	private void small(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(SMALL, SMALL);
		g.text(this.font, text, 0, 0, color, false);
		g.pose().popMatrix();
	}

	private void small(GuiGraphicsExtractor g, FormattedCharSequence text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(SMALL, SMALL);
		g.text(this.font, text, 0, 0, color, false);
		g.pose().popMatrix();
	}

	private static void smallItem(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(SMALL, SMALL);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	private static boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
	}

	// ------------------------------------------------------------------ elements

	private static final class Chapter {
		final Component name;
		int page;

		Chapter(Component name) {
			this.name = name;
		}
	}

	private abstract static class Element {
		abstract int height();

		/**
		 * How far to the right this element draws, in page pixels from its own left edge.
		 *
		 * <p>Zero means "nothing worth measuring". The page is never clipped, so anything wider than
		 * {@link #CONTENT_W} is drawn outside the book — over the other page, over the frame, over the
		 * world. The height check alone never caught that: a line can be one pixel tall and run off
		 * the edge of the screen.
		 */
		int widest(Font font) {
			return 0;
		}

		/**
		 * Whether this element had to drop or cut text to fit.
		 *
		 * <p>Fitting the page is half the job. The parts chapter fit perfectly and every entry read
		 * "Cuesta 3 · Da nivel, velocid…", which is the same nothing twenty-odd times over. Text that
		 * has been cut is a layout bug too, so the test asks about it.
		 */
		boolean elided() {
			return false;
		}

		abstract void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY);

		/** An ItemStack or a list of Components to show for the mouse, or null. */
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			return null;
		}

		boolean click(GuideBookScreen screen) {
			return false;
		}
	}

	/**
	 * The mark on the title page: the hammer, three times its size, with the forge's heart under it.
	 *
	 * <p>Drawn from the mod's own items rather than from a picture of them, so it is always the
	 * hammer the game actually has, in whatever the materials look like this week.
	 */
	private static final class Emblem extends Element {
		private final ItemStack over;
		private final ItemStack under;

		Emblem(ItemStack over, ItemStack under) {
			this.over = over;
			this.under = under;
		}

		@Override
		int height() {
			return 58;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int centre = x + CONTENT_W / 2;
			// A plate for it to sit on: two rules and a lozenge of the band colour.
			for (int i = 0; i < 22; i++) {
				g.fill(centre - 22 + i, y + 27 - i / 2, centre + 22 - i, y + 28 - i / 2 + 1, BAND);
				g.fill(centre - 22 + i, y + 28 + i / 2, centre + 22 - i, y + 29 + i / 2 + 1, BAND);
			}
			g.fill(x + 8, y + 28, centre - 26, y + 29, INK_SOFT);
			g.fill(centre + 26, y + 28, x + CONTENT_W - 8, y + 29, INK_SOFT);
			g.pose().pushMatrix();
			g.pose().translate(centre - 10, y + 30);
			g.pose().scale(1.25F, 1.25F);
			g.item(this.under, 0, 0);
			g.pose().popMatrix();
			g.pose().pushMatrix();
			g.pose().translate(centre - 24, y + 2);
			g.pose().scale(3.0F, 3.0F);
			g.item(this.over, 0, 0);
			g.pose().popMatrix();
		}
	}

	/**
	 * The creature itself, standing on the page and looking at the mouse.
	 *
	 * <p>The bestiary described things nobody had seen yet with a row of what they drop, which is a
	 * shopping list and not a picture. This is the real model through the real renderer — the same
	 * call the inventory makes for the player — so it is always what the thing looks like now, glow
	 * and all, and it never needs redrawing when a model changes.
	 *
	 * <p>It is built the first time it is drawn and by hand rather than through its entity type,
	 * because on peaceful a monster's type refuses to make one, and a book that is blank on peaceful
	 * is a worse book. It never goes into the world: nothing ticks it, and nothing can see it but this.
	 */
	private static final class Portrait extends Element {
		private static final int HEIGHT = 74;
		private static int nextSitter = -7000;
		private final java.util.function.Function<net.minecraft.world.level.Level, net.minecraft.world.entity.LivingEntity> maker;
		private net.minecraft.world.entity.@Nullable LivingEntity sitter;
		private boolean failed;

		Portrait(java.util.function.Function<net.minecraft.world.level.Level, net.minecraft.world.entity.LivingEntity> maker) {
			this.maker = maker;
		}

		@Override
		int height() {
			return HEIGHT;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int left = x + 14;
			int right = x + CONTENT_W - 14;
			int top = y + 2;
			int bottom = y + HEIGHT - 4;
			// A plate to stand on: a darker leaf of the page with a ruled edge, and a floor line.
			g.fill(left, top, right, bottom, 0x30806848);
			g.outline(left, top, right - left, bottom - top, 0x90806848);
			g.fill(left + 6, bottom - 7, right - 6, bottom - 6, 0x70806848);
			net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
			if (this.sitter == null && !this.failed && level != null) {
				try {
					this.sitter = this.maker.apply(level);
					// An entity gets its number on the way into the world, and this one never goes in:
					// the renderer asks for it and the game throws if nobody has given it one. Negative,
					// and counting down, so that it can never be a number the server has handed out.
					this.sitter.setId(nextSitter--);
				} catch (RuntimeException problem) {
					this.failed = true;
				}
			}
			if (this.sitter == null) {
				return;
			}
			// As tall as the plate allows, whatever it is: the smith is five blocks and the wisp is one.
			float tall = Math.max(0.6F, this.sitter.getBbHeight());
			float wide = Math.max(0.6F, this.sitter.getBbWidth());
			int size = Math.round(Math.min((bottom - top - 14) / tall, (right - left - 16) / (wide * 1.5F)));
			net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(
				g, left + 1, top + 1, right - 1, bottom - 1, Math.max(6, Math.min(34, size)), 0.0625F, mouseX, mouseY, this.sitter);
		}
	}

	/** A rule across the page with a diamond in the middle, to break a chapter into parts. */
	private static final class Divider extends Element {
		@Override
		int height() {
			return 8;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int middle = y + 4;
			g.fill(x + 6, middle, x + CONTENT_W - 6, middle + 1, BAND);
			int centre = x + CONTENT_W / 2;
			for (int i = 0; i < 3; i++) {
				g.fill(centre - i, middle - 2 + i, centre + i + 1, middle - 1 + i, INK_SOFT);
				g.fill(centre - i, middle + 2 - i, centre + i + 1, middle + 3 - i, INK_SOFT);
			}
		}
	}

	/** One row of a bar chart: a name, a bar as long as its share, and the number at the end. */
	private record Bar(Component name, float value, int color, Component shown) {
	}

	/**
	 * A small chart. The book uses it wherever a list of numbers says less than a picture of them:
	 * how far each membrane flies, how long each alloy lasts, how much armor a plate is worth.
	 */
	private static final class Bars extends Element {
		private final List<Bar> rows;
		private final float max;

		Bars(List<Bar> rows) {
			this.rows = rows;
			float top = 0.0F;
			for (Bar row : rows) {
				top = Math.max(top, row.value());
			}
			this.max = top <= 0.0F ? 1.0F : top;
		}

		@Override
		int height() {
			return this.rows.size() * 11 + 2;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int labelWidth = 52;
			int barWidth = CONTENT_W - labelWidth - 26;
			int row = y + 1;
			for (Bar bar : this.rows) {
				g.pose().pushMatrix();
				g.pose().translate(x, row + 1.0F);
				g.pose().scale(SMALL, SMALL);
				g.text(screen.font, bar.name(), 0, 0, INK, false);
				g.pose().popMatrix();
				int filled = Math.max(2, Math.round(barWidth * (bar.value() / this.max)));
				g.fill(x + labelWidth, row, x + labelWidth + barWidth, row + 8, PAPER_SHADE);
				g.fill(x + labelWidth, row, x + labelWidth + filled, row + 8, 0xFF000000 | bar.color());
				g.fill(x + labelWidth, row + 7, x + labelWidth + filled, row + 8, 0x40000000);
				g.pose().pushMatrix();
				g.pose().translate(x + labelWidth + barWidth + 3, row + 1.0F);
				g.pose().scale(SMALL, SMALL);
				g.text(screen.font, bar.shown(), 0, 0, INK_SOFT, false);
				g.pose().popMatrix();
				row += 11;
			}
		}
	}

	/** A strip of material colours with their names, so a chapter can show a palette at a glance. */
	private static final class Swatches extends Element {
		private final List<ForgeMaterial> materials;

		Swatches(List<ForgeMaterial> materials) {
			this.materials = materials;
		}

		@Override
		int height() {
			return (this.materials.size() + 3) / 4 * 13 + 2;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int column = 0;
			int row = y + 1;
			for (ForgeMaterial material : this.materials) {
				int left = x + column * (CONTENT_W / 4);
				g.fill(left, row, left + 8, row + 8, 0xFF000000 | material.color);
				g.fill(left, row + 7, left + 8, row + 8, 0x50000000);
				g.pose().pushMatrix();
				g.pose().translate(left + 10, row + 1.0F);
				g.pose().scale(0.6F, 0.6F);
				g.text(screen.font, material.displayName(), 0, 0, INK, false);
				g.pose().popMatrix();
				column++;
				if (column == 4) {
					column = 0;
					row += 13;
				}
			}
		}
	}

	private static final class Spacer extends Element {
		private final int size;

		Spacer(int size) {
			this.size = size;
		}

		@Override
		int height() {
			return this.size;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
		}
	}

	private static final class Title extends Element {
		@Override
		int widest(Font font) {
			return font.width(this.text);
		}

		private final Component text;

		Title(Component text) {
			this.text = text;
		}

		@Override
		int height() {
			return 22;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			float scale = 1.3F;
			int width = Math.round(screen.font.width(this.text) * scale);
			g.pose().pushMatrix();
			g.pose().translate(x + (CONTENT_W - width) / 2.0F, y + 3);
			g.pose().scale(scale, scale);
			g.text(screen.font, this.text, 0, 0, 0xFF6B2A0E, false);
			g.pose().popMatrix();
		}
	}

	private static final class Header extends Element {
		@Override
		int widest(Font font) {
			return font.width(this.text);
		}

		private final Component text;

		Header(Component text) {
			this.text = text;
		}

		@Override
		int height() {
			return 17;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			g.fill(x - 2, y, x + CONTENT_W + 2, y + 13, BAND);
			g.fill(x - 2, y, x + CONTENT_W + 2, y + 1, 0xFFEBDCB4);
			g.fill(x - 2, y + 12, x + CONTENT_W + 2, y + 13, INK_SOFT);
			// A notch out of each end and a stud beside it, which is the difference between a
			// coloured bar and a banner.
			for (int i = 0; i < 4; i++) {
				g.fill(x - 2, y + 6 - i, x + 2 - i, y + 7 + i, PAPER);
				g.fill(x + CONTENT_W - 2 + i, y + 6 - i, x + CONTENT_W + 2, y + 7 + i, PAPER);
			}
			g.fill(x + 6, y + 5, x + 9, y + 8, INK_SOFT);
			g.fill(x + CONTENT_W - 9, y + 5, x + CONTENT_W - 6, y + 8, INK_SOFT);
			int width = screen.font.width(this.text);
			g.text(screen.font, this.text, x + (CONTENT_W - width) / 2, y + 3, INK, false);
		}
	}

	private static final class SubHeader extends Element {
		private final Component text;

		SubHeader(Component text) {
			this.text = text;
		}

		@Override
		int height() {
			return 13;
		}

		/**
		 * A section title is written smaller when it does not fit, rather than wrapped or cut.
		 *
		 * <p>Four of them ran off the page — one at a hundred and seventy-eight pixels on a page a
		 * hundred and thirty wide. A title is one line by nature: wrapping it would break the rhythm
		 * of the page and cutting it would lose the word that says what the section is.
		 */
		private float scale(Font font) {
			int width = font.width(this.text);
			return width <= CONTENT_W ? 1.0F : CONTENT_W / (float) width;
		}

		@Override
		int widest(Font font) {
			return Math.round(font.width(this.text) * this.scale(font));
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			float scale = this.scale(screen.font);
			if (scale >= 1.0F) {
				g.text(screen.font, this.text, x, y + 2, 0xFF6B2A0E, false);
			} else {
				g.pose().pushMatrix();
				g.pose().translate(x, y + 2 + (1.0F - scale) * 4.0F);
				g.pose().scale(scale, scale);
				g.text(screen.font, this.text, 0, 0, 0xFF6B2A0E, false);
				g.pose().popMatrix();
			}
			g.fill(x, y + 11, x + CONTENT_W, y + 12, PAPER_SHADE);
		}
	}

	private final class Text extends Element {
		@Override
		int widest(Font font) {
			int widest = 0;
			for (FormattedCharSequence line : this.lines) {
				widest = Math.max(widest, Math.round(font.width(line) * SMALL));
			}
			return widest;
		}

		private final List<FormattedCharSequence> lines;
		private final int color;

		Text(Component text, int color) {
			this.lines = GuideBookScreen.this.font.split(GuideText.rubric(text), WRAP);
			this.color = color;
		}

		@Override
		int height() {
			return this.lines.size() * 8 + 3;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			for (int i = 0; i < this.lines.size(); i++) {
				screen.small(g, this.lines.get(i), x, y + i * 8, this.color);
			}
		}
	}

	private static final class IconRow extends Element {
		private final List<ItemStack> icons;

		IconRow(List<ItemStack> icons) {
			this.icons = icons;
		}

		@Override
		int height() {
			return 24;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int total = this.icons.size() * 22 - 6;
			int iconX = x + (CONTENT_W - total) / 2;
			for (ItemStack icon : this.icons) {
				g.item(icon, iconX, y + 3);
				iconX += 22;
			}
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			int total = this.icons.size() * 22 - 6;
			int iconX = x + (CONTENT_W - total) / 2;
			for (ItemStack icon : this.icons) {
				if (over(mouseX, mouseY, iconX, y + 3, 16, 16)) {
					return icon;
				}
				iconX += 22;
			}
			return null;
		}
	}

	private static final class IndexEntry extends Element {
		private final Chapter chapter;
		private final ItemStack icon;

		IndexEntry(Chapter chapter, ItemStack icon) {
			this.chapter = chapter;
			this.icon = icon;
		}

		@Override
		int height() {
			return 14;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			boolean hovered = over(mouseX, mouseY, x, y, CONTENT_W, 14);
			if (hovered) {
				g.fill(x - 2, y, x + CONTENT_W + 2, y + 13, PAPER_SHADE);
			}
			if (!this.icon.isEmpty()) {
				// Half size, the way the parts and materials are drawn elsewhere in the book.
				g.pose().pushMatrix();
				g.pose().translate(x + 1, y);
				g.pose().scale(0.7F, 0.7F);
				g.item(this.icon, 0, 0);
				g.pose().popMatrix();
			}
			String number = String.valueOf(this.chapter.page + 1);
			int room = CONTENT_W - 16 - screen.font.width(number) - 4;
			int colour = hovered ? 0xFF6B2A0E : INK;
			if (screen.font.width(this.chapter.name) > room) {
				// Long titles would run into the page number, so they are written a little smaller.
				g.pose().pushMatrix();
				g.pose().translate(x + 14, y + 4);
				g.pose().scale(0.8F, 0.8F);
				g.text(screen.font, this.chapter.name, 0, 0, colour, false);
				g.pose().popMatrix();
			} else {
				g.text(screen.font, this.chapter.name, x + 14, y + 3, colour, false);
			}
			g.text(screen.font, number, x + CONTENT_W - 2 - screen.font.width(number), y + 3, INK_SOFT, false);
		}

		@Override
		boolean click(GuideBookScreen screen) {
			screen.jumpTo(this.chapter.page);
			return true;
		}
	}

	private final class Crafting extends Element {
		private final ItemStack[] grid = new ItemStack[9];
		private final ItemStack result;

		Crafting(Item[] items, ItemStack result) {
			for (int i = 0; i < 9; i++) {
				this.grid[i] = items[i] == null ? ItemStack.EMPTY : new ItemStack(items[i]);
			}
			this.result = result;
		}

		@Override
		int height() {
			return 44;
		}

		private int gridX(int x) {
			return x + (CONTENT_W - 88) / 2;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int left = this.gridX(x);
			for (int i = 0; i < 9; i++) {
				int cellX = left + i % 3 * 13;
				int cellY = y + 1 + i / 3 * 13;
				g.fill(cellX, cellY, cellX + 12, cellY + 12, PAPER_SHADE);
				if (!this.grid[i].isEmpty()) {
					smallItem(g, this.grid[i], cellX, cellY);
				}
			}
			int arrowX = left + 44;
			g.fill(arrowX, y + 19, arrowX + 14, y + 21, INK_SOFT);
			for (int i = 0; i < 4; i++) {
				g.fill(arrowX + 14 + i, y + 16 + i, arrowX + 15 + i, y + 24 - i, INK_SOFT);
			}
			g.fill(left + 66, y + 10, left + 88, y + 32, PAPER_SHADE);
			g.item(this.result, left + 69, y + 13);
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			int left = this.gridX(x);
			for (int i = 0; i < 9; i++) {
				if (!this.grid[i].isEmpty() && over(mouseX, mouseY, left + i % 3 * 13, y + 1 + i / 3 * 13, 12, 12)) {
					return this.grid[i];
				}
			}
			return over(mouseX, mouseY, left + 66, y + 10, 22, 22) ? this.result : null;
		}
	}

	private final class Recipe extends Element {
		@Override
		int widest(Font font) {
			return 20 + GuideBookScreen.this.widthOf(Component.translatable("item.forja." + this.type.id()), CONTENT_W - 20);
		}

		private final ForgeType type;
		private final ItemStack item;
		private final List<ItemStack> parts = new ArrayList<>();

		Recipe(ForgeType type) {
			this.type = type;
			this.item = Assembler.create(type, Assembler.defaultMaterials(type));
			for (PartType part : type.slots) {
				this.parts.add(Assembler.createPart(part, GuideText.sampleMaterial(part)));
			}
		}

		@Override
		int height() {
			return 23;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			g.item(this.item, x, y + 3);
			screen.small(g, screen.fit(Component.translatable("item.forja." + this.type.id()), CONTENT_W - 20), x + 20, y + 2, INK);
			for (int i = 0; i < this.parts.size(); i++) {
				int partX = x + 20 + i * 18;
				if (i > 0) {
					screen.small(g, Component.literal("+"), partX - 5, y + 13, INK_SOFT);
				}
				smallItem(g, this.parts.get(i), partX, y + 10);
			}
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			for (int i = 0; i < this.parts.size(); i++) {
				if (over(mouseX, mouseY, x + 20 + i * 18, y + 10, 12, 12)) {
					return this.parts.get(i);
				}
			}
			return this.item;
		}
	}

	private final class Part extends Element {
		/** The room left beside the icon. Everything this element draws has to live inside it. */
		private static final int ROOM = CONTENT_W - 20;

		private final PartType part;
		private final ItemStack icon;
		/**
		 * What the part does, broken to fit beside the icon.
		 *
		 * <p>Wrapped rather than cut. Cutting it kept the text inside the page and made it useless —
		 * every part in the chapter ended up saying "Cuesta 3 · Da nivel, velocid…", which is the
		 * same nothing twenty-odd times over. A line that has to be short is a line that should be on
		 * two lines.
		 */
		private final List<FormattedCharSequence> line;

		Part(PartType part) {
			this.part = part;
			this.icon = Assembler.createPart(part, GuideText.sampleMaterial(part));
			Component role = Component.translatable("gui.forja.rol." + part.role.name().toLowerCase(java.util.Locale.ROOT));
			this.line = GuideBookScreen.this.font.split(
				Component.translatable("gui.forja.libro.pieza_linea", part.cost, role), Math.round(ROOM / SMALL));
			this.cut = GuideBookScreen.this.font.width(part.displayName()) > Math.round(ROOM / SMALL);
		}

		private final boolean cut;

		@Override
		boolean elided() {
			return this.cut;
		}

		@Override
		int height() {
			return 14 + this.line.size() * 8;
		}

		@Override
		int widest(Font font) {
			int widest = GuideBookScreen.this.widthOf(this.part.displayName(), ROOM);
			for (FormattedCharSequence part : this.line) {
				widest = Math.max(widest, Math.round(font.width(part) * SMALL));
			}
			return 20 + widest;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			g.item(this.icon, x, y + 2);
			screen.small(g, screen.fit(this.part.displayName(), ROOM), x + 20, y + 2, INK);
			for (int i = 0; i < this.line.size(); i++) {
				screen.small(g, this.line.get(i), x + 20, y + 10 + i * 8, INK_SOFT);
			}
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			return this.icon;
		}
	}

	private final class Material extends Element {
		private final ForgeMaterial material;
		/**
		 * The trait line, already broken to fit beside the icon.
		 *
		 * <p>It used to be drawn in one go with no width at all, so a long trait ran straight off the
		 * right-hand edge of the page and carried on over the world. Nothing clips a page here, so the
		 * only thing that keeps text inside the book is having measured it first.
		 */
		private final List<FormattedCharSequence> trait;

		Material(ForgeMaterial material) {
			this.material = material;
			Component line = material.trait == ForgeMaterial.Trait.NONE
				? Component.translatable("gui.forja.guia.sin_rasgo")
				: material.trait.displayName().copy().append(": ").append(material.trait.description());
			// As many lines as it takes. Capping it at two was the same mistake as cutting it: three
			// materials have a trait that needs a third line, and they lost the end of the sentence
			// to keep a number tidy. The element simply gets taller and the page flow deals with it.
			this.trait = GuideBookScreen.this.font.split(line, Math.round((CONTENT_W - 20) / SMALL));
			this.cut = false;
		}

		private final boolean cut;

		@Override
		boolean elided() {
			return this.cut;
		}

		@Override
		int height() {
			return 14 + this.trait.size() * 8;
		}

		@Override
		int widest(Font font) {
			int widest = GuideBookScreen.this.widthOf(this.material.displayName(), CONTENT_W - 20);
			for (FormattedCharSequence line : this.trait) {
				widest = Math.max(widest, Math.round(font.width(line) * SMALL));
			}
			return 20 + widest;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			g.item(this.material.displayStack(), x, y + 2);
			screen.small(g, this.material.displayName(), x + 20, y + 2, 0xFF000000 | GuideText.darken(this.material.color));
			int colour = this.material.trait == ForgeMaterial.Trait.NONE ? INK_SOFT : 0xFF8A4F00;
			for (int i = 0; i < this.trait.size(); i++) {
				screen.small(g, this.trait.get(i), x + 20, y + 10 + i * 8, colour);
			}
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			return GuideText.materialTooltip(this.material);
		}
	}

	private final class Trait extends Element {
		@Override
		int widest(Font font) {
			int widest = 0;
			for (FormattedCharSequence line : this.lines) {
				widest = Math.max(widest, Math.round(font.width(line) * SMALL));
			}
			return widest;
		}

		private final ForgeMaterial.Trait trait;
		private final List<FormattedCharSequence> lines;
		private final List<ForgeMaterial> materials = new ArrayList<>();

		Trait(ForgeMaterial.Trait trait) {
			this.trait = trait;
			this.lines = GuideBookScreen.this.font.split(Component.translatable("trait.forja." + trait.id() + ".largo"), WRAP);
			for (ForgeMaterial material : ForgeMaterial.values()) {
				if (material.trait == trait) {
					this.materials.add(material);
				}
			}
		}

		@Override
		int height() {
			return 18 + this.lines.size() * 8 + 4;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int iconX = x;
			for (ForgeMaterial material : this.materials) {
				g.item(material.displayStack(), iconX, y);
				iconX += 18;
			}
			g.text(screen.font, this.trait.displayName(), iconX + 2, y + 4, 0xFF8A4F00, false);
			for (int i = 0; i < this.lines.size(); i++) {
				screen.small(g, this.lines.get(i), x, y + 18 + i * 8, INK);
			}
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			return this.materials.isEmpty() ? null : GuideText.materialTooltip(this.materials.getFirst());
		}
	}

	private final class UpgradeEntry extends Element {
		@Override
		int widest(Font font) {
			return GuideBookScreen.this.widthOf(this.upgrade.displayName(), CONTENT_W);
		}

		private final Upgrade upgrade;

		UpgradeEntry(Upgrade upgrade) {
			this.upgrade = upgrade;
		}

		@Override
		int height() {
			return 23;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			screen.small(g, this.upgrade.displayName(), x, y + 1, 0xFF000000 | GuideText.darken(this.upgrade.color));
			int iconX = x;
			boolean first = true;
			for (Upgrade.Option option : this.upgrade.options) {
				if (!first) {
					screen.small(g, Component.literal("/"), iconX - 1, y + 12, INK_SOFT);
					iconX += 5;
				}
				first = false;
				for (int i = 0; i < option.requirements().size(); i++) {
					if (i > 0) {
						screen.small(g, Component.literal("+"), iconX - 1, y + 12, INK_SOFT);
						iconX += 4;
					}
					smallItem(g, option.requirements().get(i).displayStack(), iconX, y + 9);
					iconX += 13;
				}
				String percent = "+" + option.percent() + "%";
				screen.small(g, Component.literal(percent), iconX, y + 12, INK_SOFT);
				iconX += Math.round(screen.font.width(percent) * SMALL) + 3;
			}
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			return GuideText.upgradeTooltip(this.upgrade);
		}
	}

	private static final class ColorScale extends Element {
		@Override
		int height() {
			return 24;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			for (int i = 0; i < CONTENT_W; i++) {
				double rank = i / (double) (CONTENT_W - 1) * 2.0 - 1.0;
				g.fill(x + i, y + 2, x + i + 1, y + 10, 0xFF000000 | ForgeStats.color(rank));
			}
			g.fill(x, y + 10, x + CONTENT_W, y + 11, INK_SOFT);
			Component worst = Component.translatable("gui.forja.libro.peor");
			Component average = Component.translatable("gui.forja.libro.promedio");
			Component best = Component.translatable("gui.forja.libro.mejor");
			screen.small(g, worst, x, y + 13, INK);
			screen.small(g, average, x + CONTENT_W / 2 - Math.round(screen.font.width(average) * SMALL / 2), y + 13, INK);
			screen.small(g, best, x + CONTENT_W - Math.round(screen.font.width(best) * SMALL), y + 13, INK);
		}
	}
}
