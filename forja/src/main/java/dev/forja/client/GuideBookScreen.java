package dev.forja.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.forja.ForjaPath;
import dev.forja.GuideBooks;
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
 *
 * <p>And then it was cut up (docs/LIBROS_GUIA.md). Andy: "Ver un libro que tiene más de 200 páginas termina
 * asustando". The same screen now lays out whichever {@link GuideBooks.Book} it is given: a short book with its own
 * cover, tabs and chapters, the library G opens (the shelf, the path and the catalogue), or the old tome, kept for
 * creative. A link to a chapter in another book opens that book when the reader carries it.
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
	/** The forja/ advancements the reader has done, read once when the book is put together. */
	private Set<String> pathDone = Set.of();
	/** Set by the client test to lay the book out for a given progress instead of the player's own. */
	private @Nullable Set<String> forcedDone;
	/** Which book this is: its cover, its tabs and its chapters. */
	private final GuideBooks.Book book;
	/** A chapter to open at, when the book was opened from a link in another. */
	private @Nullable String openAt;

	/** The whole guide in one volume: the creative tome, and what the older client tests read. */
	public GuideBookScreen() {
		this(GuideBooks.Book.TOMO);
	}

	public GuideBookScreen(GuideBooks.Book book) {
		super(book.title());
		this.book = book;
	}

	/** A book opened at one of its chapters, for a link from another book. */
	public GuideBookScreen(GuideBooks.Book book, String chapter) {
		this(book);
		this.openAt = chapter;
	}

	/** Which book this screen is showing. */
	public GuideBooks.Book book() {
		return this.book;
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

	/** The leather of the guide's buttons: the cover's brown. */
	private static final int BOOK_LEATHER = 0xFF8A5530;

	@Override
	protected void init() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		int top = this.bookTop();
		this.backButton = this.addRenderableWidget(new PageButton(this.pageX(0) + 8, top + BOOK_H - 22, false, button -> this.turn(-1), true));
		this.forwardButton = this.addRenderableWidget(new PageButton(this.pageX(1) + PAGE_W - 31, top + BOOK_H - 22, true, button -> this.turn(1), true));
		// In the book's own leather, not vanilla's grey stone (client/ForjaButton).
		this.addRenderableWidget(new ForjaButton(this.width / 2 - 104, top + BOOK_H + 3, 100, 20,
			Component.translatable("gui.forja.libro.indice"), button -> this.jumpTo(this.indexPage), BOOK_LEATHER));
		this.addRenderableWidget(new ForjaButton(this.width / 2 + 4, top + BOOK_H + 3, 100, 20,
			CommonComponents.GUI_DONE, button -> this.onClose(), BOOK_LEATHER));
		if (this.openAt != null) {
			this.goToPage(this.chapterPage(this.openAt));
			this.openAt = null;
		}
		BookMemory.opened(this.book);
		this.updateButtons();
	}

	@Override
	public void removed() {
		super.removed();
		BookMemory.save();
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
		return this.sectionOf(found);
	}

	private int sectionOf(String chapterKey) {
		List<GuideBooks.Section> sections = this.book.sections;
		for (int index = 0; index < sections.size(); index++) {
			if (sections.get(index).chapters().contains(chapterKey)) {
				return index;
			}
		}
		return -1;
	}

	/** The tab colour of a section: one of the five painted on the book's texture, or -1 for none. */
	private int sectionColour(int section) {
		return section < 0 ? -1 : this.book.sections.get(section).colour();
	}

	/** The first page of a section: of whichever of its chapters comes first in the book. */
	private int sectionPage(int section) {
		if (section < 0 || section >= this.book.sections.size()) {
			return 0;
		}
		int first = Integer.MAX_VALUE;
		for (String key : this.book.sections.get(section).chapters()) {
			Chapter chapter = this.chapters.get(key);
			if (chapter != null) {
				first = Math.min(first, chapter.page);
			}
		}
		return first == Integer.MAX_VALUE ? 0 : first;
	}

	private String sectionKey(int section) {
		return section < 0 || section >= this.book.sections.size() ? "" : this.book.sections.get(section).key();
	}

	private int tabX() {
		return this.bookLeft() + BOOK_W + OVERHANG - 5;
	}

	private int tabY(int section) {
		return this.bookTop() + 12 + section * (TAB_H + TAB_GAP);
	}

	/** The section whose tab is under the mouse, or -1. */
	private int tabAt(double mouseX, double mouseY) {
		for (int section = 0; section < this.book.sections.size(); section++) {
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

	/** A book as a reader with exactly these forja/ advancements done would see it, for the client test. */
	public static GuideBookScreen showing(Set<String> done) {
		return showing(GuideBooks.Book.TOMO, done);
	}

	/** One of the books as a reader with exactly these forja/ advancements done would see it, for the client test. */
	public static GuideBookScreen showing(GuideBooks.Book which, Set<String> done) {
		GuideBookScreen book = new GuideBookScreen(which);
		book.forcedDone = done;
		return book;
	}

	/**
	 * Whether the head of the "Siguiente paso" page, what to do and the way to its chapter all landed on
	 * one page, for the client test: split over a turn, the link is on a page nobody is looking at.
	 */
	public boolean nextStepOnOnePage() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		for (List<Element> page : this.pages) {
			if (page.stream().anyMatch(element -> element instanceof StepCard)) {
				return page.stream().anyMatch(element -> element instanceof ChapterLink);
			}
		}
		return false;
	}

	/** Whether the list of the whole path is on one page, rather than its last line alone over the page. */
	public boolean pathListOnOnePage() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		for (List<Element> page : this.pages) {
			long rows = page.stream().filter(element -> element instanceof PathRow).count();
			if (rows > 0) {
				return rows == ForjaPath.STEPS.size();
			}
		}
		return false;
	}

	/**
	 * Where the sign on the cover is, for the client test to click: under the emblem and the title, whose height
	 * now depends on how many lines the book's name takes.
	 */
	public double[] pathSignPoint() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		int y = 0;
		for (Element element : this.pages.getFirst()) {
			if (element instanceof PathSign) {
				return this.contentPoint(0, 40, y + 8);
			}
			y += element.height();
		}
		return this.contentPoint(0, 40, 88);
	}

	/**
	 * Links that go nowhere, as "page:chapter": a chapter link or a step of the path whose chapter no book has.
	 * A link to another book is fine, and so is one to a book not written yet (it says so); a key nobody has is not.
	 */
	public List<String> brokenLinks() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		List<String> broken = new ArrayList<>();
		for (int i = 0; i < this.pages.size(); i++) {
			for (Element element : this.pages.get(i)) {
				String target = element instanceof ChapterLink link ? link.chapter
					: element instanceof PathRow row ? row.step.chapter : null;
				if (target != null && !this.chapters.containsKey(target) && GuideBooks.bookOf(target) == null) {
					broken.add(i + ":" + target);
				}
			}
		}
		return broken;
	}

	/** Every book card in this book as "BOOK:lit" or "BOOK:dark", for the client test. */
	public List<String> bookCards() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		List<String> cards = new ArrayList<>();
		for (List<Element> page : this.pages) {
			for (Element element : page) {
				if (element instanceof BookCard card) {
					cards.add(card.target + ":" + (card.lit() ? "lit" : "dark"));
				}
			}
		}
		return cards;
	}

	/** Pacts and synergies this book listed, as "pact:NAME:seen|shadow" and "synergy:NAME:...", for the client test. */
	private final List<String> shadowed = new ArrayList<>();

	public List<String> shadowedEntries() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		return List.copyOf(this.shadowed);
	}

	/**
	 * Whether the books show this pact: once the reader has opened it with its offering (Andy, 2026-09-30: pacts and
	 * synergies stay in shadow until opened or awakened). The creative tome shows everything.
	 */
	boolean pactKnown(Upgrade pact) {
		return this.book == GuideBooks.Book.TOMO || dev.forja.upgrade.Pacts.unlocked(this.minecraft.player, pact) && this.minecraft.player != null;
	}

	/** Whether the books show this synergy: once it has woken on something the reader carried (CreatureSightings). */
	boolean synergyKnown(dev.forja.upgrade.Synergy synergy) {
		return this.book == GuideBooks.Book.TOMO || BookMemory.knowsSynergy(synergy.name());
	}

	/** The chapters this book printed, in order, for the client test. */
	public List<String> chapterKeys() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		List<String> keys = new ArrayList<>(this.chapters.keySet());
		keys.sort(java.util.Comparator.comparingInt(key -> this.chapters.get(key).page));
		return keys;
	}

	/** First page of a chapter by key (mesas, objetos, piezas, materiales, rasgos, mejoras, estadisticas). */
	public int chapterPage(String key) {
		if (this.pages.isEmpty()) {
			this.build();
		}
		Chapter chapter = this.chapters.get(key);
		return chapter == null ? 0 : chapter.page;
	}

	/**
	 * What the "Siguiente paso" page and the sign on the cover are showing, for the client test: the
	 * step's advancement id, "completo" once the path is walked, and "portada" in front when the cover
	 * carries the sign.
	 */
	public String shownStep() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		String shown = "";
		boolean signed = false;
		for (List<Element> page : this.pages) {
			for (Element element : page) {
				if (element instanceof StepCard card) {
					shown = card.step == null ? "completo" : card.step.advancement();
				}
				signed |= element instanceof PathSign;
			}
		}
		return (signed ? "portada:" : "") + shown;
	}

	// ------------------------------------------------------------------ content

	private void build() {
		this.pathDone = this.forcedDone != null ? this.forcedDone : PathClient.done();
		ForjaPath.Step next = ForjaPath.next(this.pathDone::contains);
		List<Element> cover = this.book == GuideBooks.Book.TOMO ? this.tomeCover(next) : this.bookCover(next);
		// The chapters are laid out first, into their own list, because until the index has been broken
		// into pages nobody knows how many pages come before them.
		List<List<Element>> body = new ArrayList<>();
		this.sink = body;
		for (String key : this.book.chapters()) {
			this.chapter(key, this.body(key));
		}

		// Twenty chapters is too many for a flat list, so the index is grouped.
		List<Element> index = new ArrayList<>();
		index.add(new Header(Component.translatable("gui.forja.libro.indice")));
		for (GuideBooks.Section section : this.book.sections) {
			index.add(new SubHeader(Component.translatable("gui.forja.libro.seccion." + section.key())));
			for (String key : section.chapters()) {
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

	/** The old guide's title page, for the tome that keeps it whole. */
	private List<Element> tomeCover(ForjaPath.@Nullable Step next) {
		List<Element> cover = new ArrayList<>();
		cover.add(new Emblem(
			Assembler.create(ForgeType.MARTILLO, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.MADERA, ForgeMaterial.ORO)),
			new ItemStack(ModItems.CORAZON_DE_FORJA)));
		cover.add(new Title(this.font, this.book.title()));
		// Until the path is walked, the first thing under the title is where to go next.
		if (next != null) {
			cover.add(new PathSign(next));
		}
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
		return cover;
	}

	/** Every chapter's pages by its key: the same chapter can be printed in more than one book. */
	private List<Element> body(String key) {
		return switch (key) {
			case "primeros_pasos" -> this.firstStepsChapter();
			case "siguiente_paso" -> this.nextStepChapter();
			// Book I does not print the recipes again (the notebook has them): it says what each table is for.
			case "mesas" -> {
				if (this.book == GuideBooks.Book.YUNQUE) {
					yield this.workshopChapter();
				}
				List<Element> tables = new ArrayList<>(this.tablesChapter());
				tables.add(new Divider());
				tables.addAll(this.cabinetEntry());
				yield tables;
			}
			case "objetos" -> this.recipesChapter();
			case "piezas" -> this.partsChapter();
			case "materiales" -> this.materialsChapter();
			case "rasgos" -> this.traitsChapter();
			case "mejoras" -> this.upgradesChapter();
			case "potencial" -> this.potentialChapter();
			case "maestria" -> this.masteryChapter();
			case "aleaciones" -> this.alloysChapter();
			case "fundicion" -> this.foundryChapter();
			case "temple" -> this.quenchChapter();
			case "herrero" -> this.smithChapter();
			case "tecnicas" -> this.techniquesChapter();
			case "mi_taller" -> this.myWorkshopChapter();
			case "sinergias" -> this.synergiesChapter();
			case "pactos" -> this.pactsChapter();
			case "combate" -> this.combatChapter();
			case "mana" -> this.manaChapter();
			case "accesorios" -> this.trinketsChapter();
			case "clases" -> this.classesChapter();
			case "eventos" -> this.eventsChapter();
			case "encargos" -> this.commissionsChapter();
			case "amenazas" -> this.threatsChapter();
			case "bestiario" -> this.bestiaryChapter();
			case "mundo" -> this.worldChapter();
			case "cementerio" -> this.graveyardChapter();
			case "estadisticas" -> this.statsChapter();
			case "mesa_mayor" -> this.greaterTableChapter();
			// The notebook, book I and the library (docs/LIBROS_GUIA.md): further down, under "the books".
			case "bienvenida" -> this.welcomeChapter();
			case "primeras_mesas" -> this.firstTablesChapter();
			case "primer_objeto" -> this.firstPieceChapter();
			case "como_funciona" -> this.howItWorksChapter();
			case "teclas" -> this.keysChapter();
			case "estanteria" -> this.shelfChapter();
			case "yunque_sabes" -> this.anvilRecapChapter();
			case "cortar" -> this.cuttingChapter();
			case "estrella" -> this.starChapter();
			case "mejorar" -> this.improvingChapter();
			case "desarmar" -> this.salvageChapter();
			case "yunque_siguiente" -> this.anvilNextChapter();
			case "catalogo" -> this.catalogueChapter();
			// Book II.
			case "tu_cuerpo" -> this.bodyChapter();
			case "golpear" -> this.hittingChapter();
			case "armas" -> this.weaponsChapter(false);
			case "defenderse" -> this.defendingChapter();
			case "magia" -> this.magicChapter();
			case "como_pelean" -> this.enemiesChapter();
			case "rangos" -> this.ranksChapter();
			case "peleas_mundo" -> this.worldFightsChapter();
			case "dificultad" -> this.difficultyChapter();
			case "combate_siguiente" -> this.combatNextChapter();
			case "probador" -> this.probeChapter();
			// Book III.
			case "fundicion_sabes" -> this.foundryRecapChapter();
			case "primeras_aleaciones" -> this.firstAlloysChapter();
			case "catalogo_aleaciones" -> this.alloyListChapter();
			case "fundicion_siguiente" -> this.foundryNextChapter();
			case "aleaciones_lejanas" -> this.farAlloysChapter();
			case "aleaciones_cumbre" -> this.peakAlloysChapter();
			// Book IV.
			case "mayor_sabes" -> this.greaterRecapChapter();
			case "mayor_siguiente" -> this.greaterNextChapter();
			// Book V.
			case "clases_sabes" -> this.classesRecapChapter();
			case "clases_siguiente" -> this.classesNextChapter();
			// Book VI.
			case "bastion_sabes" -> this.bastionRecapChapter();
			case "ruinas" -> this.ruinsChapter();
			case "herrero_historia" -> this.smithStoryChapter();
			case "fraguas_lejanas" -> this.farForgesChapter();
			case "bastion" -> this.bastionChapter();
			case "portal_estelar" -> this.starPortalChapter();
			case "bastion_siguiente" -> this.bastionNextChapter();
			// Book VII.
			case "cementerio_viaje" -> this.graveyardJourneyChapter();
			case "cementerio_pelea" -> this.graveyardFightChapter();
			case "cementerio_recompensa" -> this.graveyardRewardChapter();
			case "cementerio_herrero" -> this.graveyardSmithChapter();
			case "cementerio_fin" -> this.graveyardEndChapter();
			default -> throw new IllegalArgumentException("no chapter " + key);
		};
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
		// A paragraph taller than a page is cut into page-sized pieces first: nothing else can carry it over a turn.
		List<Element> cut = new ArrayList<>();
		for (Element element : body) {
			if (element instanceof Text text && text.height() > CONTENT_H) {
				cut.addAll(text.pieces(CONTENT_H));
			} else {
				cut.add(element);
			}
		}
		body = cut;
		for (int i = 0; i < body.size(); i++) {
			Element element = body.get(i);
			if (element instanceof PageBreak) {
				if (!page.isEmpty()) {
					out.add(page);
					page = new ArrayList<>();
					used = 0;
				}
				continue;
			}
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

	/** Something to put beside each chapter in the index, so the page reads at a glance. */
	private static ItemStack chapterIcon(String key) {
		return switch (key) {
			case "primeros_pasos" -> new ItemStack(ModItems.GUIA_DE_FORJA);
			case "siguiente_paso" -> new ItemStack(Items.COMPASS);
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
			case "mana" -> new ItemStack(Items.LAPIS_LAZULI);
			case "accesorios" -> new ItemStack(ModItems.CINTURON);
			case "clases" -> dev.forja.forge.Relic.MEDALLON_DEL_OLVIDO.create(dev.forja.forge.Relic.MEDALLON_DEL_OLVIDO.defaultMaterials());
			case "eventos" -> new ItemStack(ModItems.JARRA);
			case "encargos" -> new ItemStack(Items.EMERALD);
			case "amenazas" -> new ItemStack(Items.CROSSBOW);
			case "bestiario" -> new ItemStack(ModItems.CORAZON_DE_FORJA);
			case "mundo" -> new ItemStack(Items.FILLED_MAP);
			case "cementerio" -> new ItemStack(ModItems.PERLA_DE_ORICALCO);
			case "estadisticas" -> new ItemStack(Items.PAPER);
			case "mesa_mayor" -> new ItemStack(ModItems.MESA_DE_FORJA_MAYOR);
			case "bienvenida" -> new ItemStack(ModItems.GUIA_DE_FORJA);
			case "primeras_mesas" -> new ItemStack(ModItems.MESA_DE_PIEZAS);
			case "primer_objeto" -> Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.PIEDRA, ForgeMaterial.MADERA, ForgeMaterial.MADERA));
			case "como_funciona" -> new ItemStack(Items.CLOCK);
			case "teclas" -> new ItemStack(Items.LEVER);
			case "estanteria" -> new ItemStack(Items.BOOKSHELF);
			case "yunque_sabes" -> new ItemStack(Items.WRITABLE_BOOK);
			case "cortar" -> Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.PIEDRA);
			case "estrella" -> new ItemStack(ModItems.MESA_DE_FORJA);
			case "mejorar" -> new ItemStack(Items.SUGAR);
			case "desarmar" -> new ItemStack(Items.GRINDSTONE);
			case "yunque_siguiente" -> new ItemStack(Items.COMPASS);
			case "catalogo" -> new ItemStack(Items.BOOK);
			case "tu_cuerpo" -> new ItemStack(Items.RABBIT_FOOT);
			case "golpear" -> Assembler.create(ForgeType.MARTILLO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO));
			case "armas" -> Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO));
			case "defenderse" -> Assembler.create(ForgeType.ESCUDO, Assembler.defaultMaterials(ForgeType.ESCUDO));
			case "magia" -> Assembler.create(ForgeType.BACULO, List.of(ForgeMaterial.AMATISTA, ForgeMaterial.HIERRO, ForgeMaterial.MADERA));
			case "como_pelean" -> new ItemStack(Items.ZOMBIE_HEAD);
			case "rangos" -> new ItemStack(Items.TOTEM_OF_UNDYING);
			case "peleas_mundo" -> new ItemStack(Items.BELL);
			case "dificultad" -> new ItemStack(Items.SKELETON_SKULL);
			case "combate_siguiente" -> new ItemStack(Items.COMPASS);
			case "probador" -> new ItemStack(Items.SPYGLASS);
			case "fundicion_sabes" -> new ItemStack(Items.WRITABLE_BOOK);
			case "primeras_aleaciones" -> new ItemStack(ModItems.alloy("bronce"));
			case "catalogo_aleaciones" -> new ItemStack(ModItems.alloy("acero"));
			case "fundicion_siguiente" -> new ItemStack(Items.COMPASS);
			case "aleaciones_lejanas" -> new ItemStack(ModItems.alloy("fatuo"));
			case "aleaciones_cumbre" -> new ItemStack(ModItems.alloy("astralita"));
			case "fraguas_lejanas" -> new ItemStack(ModItems.FRAGUA_DE_ALMAS);
			case "mayor_sabes" -> new ItemStack(Items.WRITABLE_BOOK);
			case "mayor_siguiente" -> new ItemStack(Items.COMPASS);
			case "clases_sabes" -> new ItemStack(Items.WRITABLE_BOOK);
			case "clases_siguiente" -> new ItemStack(Items.COMPASS);
			case "bastion_sabes" -> new ItemStack(Items.FILLED_MAP);
			case "ruinas" -> new ItemStack(Items.CRACKED_STONE_BRICKS);
			case "herrero_historia" -> new ItemStack(ModItems.FRAGUA_APAGADA);
			case "bastion" -> new ItemStack(Items.DEEPSLATE_BRICKS);
			case "portal_estelar" -> new ItemStack(ModItems.PERLA_DE_ORICALCO);
			case "bastion_siguiente" -> new ItemStack(Items.COMPASS);
			case "cementerio_viaje" -> new ItemStack(ModItems.PERLA_DE_ORICALCO);
			case "cementerio_pelea" -> new ItemStack(ModItems.MARTILLO_DEL_MAESTRO);
			case "cementerio_recompensa" -> new ItemStack(ModItems.ESTRELLA_FORJADA);
			case "cementerio_herrero" -> new ItemStack(ModItems.CORAZON_DE_FORJA);
			case "cementerio_fin" -> new ItemStack(ModItems.ESTANTERIA_DEL_HERRERO);
			default -> ItemStack.EMPTY;
		};
	}

	/**
	 * El Cementerio entre Estrellas (docs/HERRERO_DIMENSION.md): the way there. Oricalco out of every
	 * renewable metal, the pearl cast over an ender pearl, the frame in the Bastion's deep forge, and back.
	 */
	private List<Element> graveyardChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.cementerio.intro"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.cementerio.oricalco.titulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.HIERRO_ESTELAR), new ItemStack(ModItems.PLACA_HUECA), new ItemStack(ModItems.ESCORIA),
			new ItemStack(ModItems.alloy("acero_estelar")), new ItemStack(ModItems.alloy("almacero")), new ItemStack(ModItems.ORICALCO))));
		body.add(new Text(Component.translatable("gui.forja.libro.cementerio.oricalco"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.cementerio.perla.titulo")));
		body.add(new IconRow(List.of(new ItemStack(Items.ENDER_PEARL), new ItemStack(ModItems.MESA_DE_LOSA), new ItemStack(ModItems.PERLA_DE_ORICALCO))));
		body.add(new Text(Component.translatable("gui.forja.libro.cementerio.perla", dev.forja.block.entity.CastingTableBlockEntity.PEARL_COST), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.cementerio.portal.titulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.MENSULA_ESTELAR), new ItemStack(ModItems.PERLA_DE_ORICALCO))));
		body.add(new Text(Component.translatable("gui.forja.libro.cementerio.portal"), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.cementerio.vuelta"), INK_SOFT));
		return body;
	}

	// ------------------------------------------------------------------ the books (docs/LIBROS_GUIA.md)

	/**
	 * The cover of one of the short books, the notebook and the library: its mark, its name, where the path is
	 * when the book carries it, how long it is and how much of it has been read, its own steps of the path, a
	 * way back to where the reader left off, and a paragraph on what it is for.
	 */
	private List<Element> bookCover(ForjaPath.@Nullable Step next) {
		List<Element> cover = new ArrayList<>();
		cover.add(new Emblem(this.emblemOver(), this.emblemUnder()));
		cover.add(new Title(this.font, this.book.title()));
		if (next != null && this.book.chapters().contains("siguiente_paso")) {
			cover.add(new PathSign(next));
		}
		cover.add(new BookInfo());
		List<ForjaPath.Step> own = stepsOf(this.book);
		if (!own.isEmpty()) {
			cover.add(new BookSteps(own));
		}
		if (BookMemory.bookmark(this.book) > 1) {
			cover.add(new BookmarkLink(BookMemory.bookmark(this.book)));
		}
		cover.add(new Divider());
		cover.add(new Text(Component.translatable("gui.forja.libros." + this.book.key() + ".portada"), INK));
		return cover;
	}

	private ItemStack emblemOver() {
		return switch (this.book) {
			case CUADERNO -> Assembler.create(ForgeType.MARTILLO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO));
			case YUNQUE -> new ItemStack(ModItems.MESA_DE_FORJA);
			case COMBATE -> Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO));
			case FUNDICION -> new ItemStack(ModItems.CRISOL_DE_HIERRO);
			case MESA_MAYOR -> new ItemStack(ModItems.MESA_DE_FORJA_MAYOR);
			case CLASES -> dev.forja.forge.Relic.MEDALLON_DEL_OLVIDO.create(dev.forja.forge.Relic.MEDALLON_DEL_OLVIDO.defaultMaterials());
			case BASTION -> new ItemStack(ModItems.FRAGUA_APAGADA);
			case CEMENTERIO -> new ItemStack(ModItems.ESTRELLA_FORJADA);
			case BIBLIOTECA -> new ItemStack(Items.BOOKSHELF);
			default -> new ItemStack(Items.BOOK);
		};
	}

	private ItemStack emblemUnder() {
		return switch (this.book) {
			case CUADERNO -> new ItemStack(ModItems.PLANTILLA);
			case YUNQUE -> Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO));
			case COMBATE -> Assembler.create(ForgeType.ESCUDO, Assembler.defaultMaterials(ForgeType.ESCUDO));
			case FUNDICION -> new ItemStack(ModItems.alloy("bronce"));
			case MESA_MAYOR -> new ItemStack(ModItems.FUNDENTE_MAESTRO);
			case CLASES -> Assembler.create(ForgeType.FAROL, List.of(ForgeMaterial.ESMERALDA, ForgeMaterial.ORO, ForgeMaterial.MADERA));
			case BASTION -> new ItemStack(Items.FILLED_MAP);
			case CEMENTERIO -> new ItemStack(ModItems.PERLA_DE_ORICALCO);
			case BIBLIOTECA -> new ItemStack(ModItems.GUIA_DE_FORJA);
			default -> new ItemStack(ModItems.GUIA_DE_FORJA);
		};
	}

	/** The steps of the path whose chapter is in this book, in order. */
	private static List<ForjaPath.Step> stepsOf(GuideBooks.Book book) {
		List<ForjaPath.Step> own = new ArrayList<>();
		for (ForjaPath.Step step : ForjaPath.STEPS) {
			if (GuideBooks.bookOf(step.chapter) == book) {
				own.add(step);
			}
		}
		return own;
	}

	/**
	 * Whether the reader can open this book from here: they carry it (the library needs the notebook), or they
	 * are in creative, where every written book is on hand. A book not written yet cannot be opened by anyone.
	 */
	boolean canRead(GuideBooks.Book target) {
		if (!target.ready) {
			return false;
		}
		var player = this.minecraft.player;
		if (player == null) {
			return false;
		}
		if (player.isCreative()) {
			return true;
		}
		Item item = target == GuideBooks.Book.BIBLIOTECA ? ModItems.GUIA_DE_FORJA : target.item();
		if (item == null) {
			return false;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(item)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Go to a chapter: in this book, a jump; in another the reader has, that book opened at it. False, and
	 * nothing happens, when the chapter is in a book they do not carry or that is not written yet.
	 */
	public boolean openChapter(String key) {
		if (this.chapters.containsKey(key)) {
			this.jumpTo(this.chapterPage(key));
			return true;
		}
		GuideBooks.Book target = GuideBooks.bookOf(key);
		if (target != null && this.canRead(target)) {
			this.minecraft.gui.setScreen(new GuideBookScreen(target, key));
			return true;
		}
		return false;
	}

	/** Where a book the reader does not have comes from: its recipe and when it is learned, for a tooltip. */
	List<Component> whereToGet(GuideBooks.Book target) {
		List<Component> lines = new ArrayList<>();
		lines.add(target.title().copy().withColor(0xFFE9A8));
		if (!target.ready) {
			lines.add(Component.translatable("gui.forja.libros.carta.pronto", target.when()));
		} else if (target.unlock != null && !this.pathDone.contains(target.unlock)) {
			// Not learned yet: only when it opens, never the recipe (Andy, 2026-09-29).
			lines.add(Component.translatable("gui.forja.libros.carta.aprende", target.when()));
		} else {
			lines.add(Component.translatable("gui.forja.libros.receta", new ItemStack(Items.BOOK).getHoverName(),
				new ItemStack(target.ingredient.get()).getHoverName()));
			lines.add(Component.translatable("gui.forja.libros.carta.hazlo"));
		}
		return lines;
	}

	/** Pages of each written book, counted the first time a card asks, for the covers of the others. */
	private static final Map<GuideBooks.Book, Integer> PAGES = new java.util.EnumMap<>(GuideBooks.Book.class);
	private static final Set<GuideBooks.Book> COUNTING = java.util.EnumSet.noneOf(GuideBooks.Book.class);

	/** How many pages a book has, or -1 while it is not written or is being counted (a card on its own shelf). */
	static int pagesOf(GuideBooks.Book target) {
		if (!target.ready) {
			return -1;
		}
		Integer known = PAGES.get(target);
		if (known != null) {
			return known;
		}
		if (!COUNTING.add(target)) {
			return -1;
		}
		try {
			int pages = new GuideBookScreen(target).pageCount();
			PAGES.put(target, pages);
			return pages;
		} finally {
			COUNTING.remove(target);
		}
	}

	/** About twenty-five seconds a page, rounded up to whole minutes. */
	private static int minutes(int pages) {
		return Math.max(1, (pages * 25 + 59) / 60);
	}

	/** The stone pickaxe of the notebook's worked example: stone head, wooden handle, wooden binding. */
	private static ItemStack firstPickaxe() {
		return Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.PIEDRA, ForgeMaterial.MADERA, ForgeMaterial.MADERA));
	}

	/**
	 * The notebook's first page: what Forja is, and the five words the rest of the book is written in, each said once
	 * before it is used. Andy (2026-10-01) found the notebook did not explain how forging works; half of that was
	 * "plantilla", "pieza" and "estrella" turning up in the text before anything said what they were.
	 */
	private List<Element> welcomeChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.bienvenida"), INK));
		body.add(new Formula(List.of(Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.PIEDRA), PLUS,
			Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA), PLUS, Assembler.createPart(PartType.ATADURA, ForgeMaterial.MADERA), EQUALS,
			firstPickaxe())));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.palabras.titulo")));
		for (String word : List.of("mesa_piezas", "plantilla", "pieza", "mesa_forja", "objeto")) {
			body.add(new Text(Component.translatable("gui.forja.libros.palabras." + word), INK));
		}
		body.add(new Divider());
		body.add(new Text(Component.translatable("gui.forja.libros.bienvenida.libros"), INK_SOFT));
		return body;
	}

	/**
	 * Steps 1 to 5 of the first minute, in the order they are done: what to gather, the three recipes, setting the
	 * tables down, and the first template engraved, on a drawing of the parts table's own screen.
	 */
	private List<Element> firstTablesChapter() {
		Item planks = Items.OAK_PLANKS;
		Item iron = Items.IRON_INGOT;
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.primeras_mesas.necesitas"), INK));
		body.add(new Formula(List.of(new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.GRINDSTONE), new ItemStack(Items.CRAFTING_TABLE),
			new ItemStack(Items.OAK_PLANKS, 12), new ItemStack(Items.STICK, 4))));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.primeras_mesas.paso1")));
		body.add(new Crafting(new Item[] {iron, iron, iron, planks, Items.GRINDSTONE, planks, planks, null, planks}, new ItemStack(ModItems.MESA_DE_PIEZAS)));
		body.add(new Text(Component.translatable("gui.forja.libros.primeras_mesas.paso1.desc"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.primeras_mesas.paso2")));
		body.add(new Crafting(new Item[] {iron, iron, iron, planks, Items.CRAFTING_TABLE, planks, planks, null, planks}, new ItemStack(ModItems.MESA_DE_FORJA)));
		body.add(new Text(Component.translatable("gui.forja.libros.primeras_mesas.paso2.desc"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.primeras_mesas.paso3")));
		body.add(new Crafting(new Item[] {Items.STICK, planks, null, planks, Items.STICK, null, null, null, null}, new ItemStack(ModItems.PLANTILLA, 2)));
		body.add(new Text(Component.translatable("gui.forja.libros.primeras_mesas.paso3.desc"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.primeras_mesas.paso4")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.MESA_DE_PIEZAS), new ItemStack(ModItems.MESA_DE_FORJA))));
		body.add(new Text(Component.translatable("gui.forja.libros.primeras_mesas.paso4.desc"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.primeras_mesas.paso5")));
		body.add(TablePanel.engraving());
		body.add(new Text(Component.translatable("gui.forja.libros.primeras_mesas.paso5.desc"), INK));
		body.add(new Formula(List.of(new ItemStack(ModItems.PLANTILLA), ARROW, engravedTemplate(PartType.CABEZA_PICO))));
		body.add(new Text(Component.translatable("gui.forja.libros.primeras_mesas.paso5.fin"), INK_SOFT));
		return body;
	}

	/**
	 * The worked example, start to finish: a stone pickaxe, which is the first thing anybody can forge (the bench cuts
	 * only what is worked cold; metal is poured, and the foundry comes later). Every step says which table, which slot
	 * and what to click, on drawings of the two screens.
	 */
	private List<Element> firstPieceChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.intro"), INK));
		body.add(new Formula(List.of(new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.OAK_PLANKS, 2), PLUS,
			engravedTemplate(PartType.CABEZA_PICO), engravedTemplate(PartType.MANGO), engravedTemplate(PartType.ATADURA))));
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.metal"), INK_SOFT));

		body.add(new SubHeader(Component.translatable("gui.forja.libros.primer_objeto.paso1")));
		body.add(new IconRow(List.of(engravedTemplate(PartType.CABEZA_PICO), engravedTemplate(PartType.MANGO), engravedTemplate(PartType.ATADURA))));
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.paso1.desc"), INK));

		body.add(new SubHeader(Component.translatable("gui.forja.libros.primer_objeto.paso2")));
		body.add(TablePanel.cutting(engravedTemplate(PartType.CABEZA_PICO), new ItemStack(Items.COBBLESTONE, 3),
			Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.PIEDRA)));
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.paso2.desc", PartType.CABEZA_PICO.cost), INK));

		body.add(new SubHeader(Component.translatable("gui.forja.libros.primer_objeto.paso3")));
		body.add(TablePanel.cutting(engravedTemplate(PartType.MANGO), new ItemStack(Items.OAK_PLANKS, 1),
			Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA)));
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.paso3.desc"), INK));
		body.add(new Formula(List.of(Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.PIEDRA),
			Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA), Assembler.createPart(PartType.ATADURA, ForgeMaterial.MADERA))));

		body.add(new SubHeader(Component.translatable("gui.forja.libros.primer_objeto.paso4")));
		body.add(TablePanel.star(List.of(Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.PIEDRA),
			Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA), Assembler.createPart(PartType.ATADURA, ForgeMaterial.MADERA)), ItemStack.EMPTY));
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.paso4.desc"), INK));

		body.add(new SubHeader(Component.translatable("gui.forja.libros.primer_objeto.paso5")));
		body.add(TablePanel.star(List.of(), firstPickaxe()));
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.paso5.desc"), INK));
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.paso5.martillo"), INK_SOFT));

		body.add(new SubHeader(Component.translatable("gui.forja.libros.primer_objeto.paso6")));
		body.add(new IconRow(List.of(firstPickaxe(), new ItemStack(Items.WATER_BUCKET))));
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.paso6.desc", dev.forja.forge.Temple.HOT_TICKS / 20), INK));

		body.add(new Divider());
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.otros"), INK));
		// The same three cold materials, so that every one of them can be made today with what is on this page.
		for (ForgeType type : List.of(ForgeType.HACHA, ForgeType.PALA, ForgeType.ESPADA)) {
			List<ForgeMaterial> materials = List.of(ForgeMaterial.PIEDRA, ForgeMaterial.MADERA, ForgeMaterial.MADERA);
			List<Object> row = new ArrayList<>();
			for (int i = 0; i < type.slots.size(); i++) {
				if (i > 0) {
					row.add(PLUS);
				}
				row.add(Assembler.createPart(type.slots.get(i), materials.get(i)));
			}
			row.add(EQUALS);
			row.add(Assembler.create(type, materials));
			body.add(new Formula(row));
		}
		body.add(new Text(Component.translatable("gui.forja.libros.primer_objeto.otros.mas"), INK_SOFT));
		return body;
	}

	/**
	 * How it works, short: what the material of each part changes, what the hammer's press leaves on the piece, the
	 * potential, the quench, and where the long version is (book I).
	 */
	private List<Element> howItWorksChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new SubHeader(Component.translatable("gui.forja.libros.como_funciona.material.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.como_funciona.material"), INK));
		List<Bar> picks = new ArrayList<>();
		for (ForgeMaterial head : List.of(ForgeMaterial.MADERA, ForgeMaterial.PIEDRA, ForgeMaterial.HUESO, ForgeMaterial.HIERRO, ForgeMaterial.DIAMANTE)) {
			ItemStack pick = Assembler.create(ForgeType.PICO, List.of(head, ForgeMaterial.MADERA, ForgeMaterial.MADERA));
			picks.add(new Bar(head.displayName(), pick.getMaxDamage(), head.color, Component.literal(String.valueOf(pick.getMaxDamage()))));
		}
		// A heading rather than a line of text: the flow keeps a heading on the same page as what follows it.
		body.add(new SubHeader(Component.translatable("gui.forja.libros.como_funciona.barras")));
		body.add(new Bars(picks));
		body.add(new Text(Component.translatable("gui.forja.libros.como_funciona.mango"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.como_funciona.martillo.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.como_funciona.martillo",
			Math.round(dev.forja.forge.Quality.PERFECT_BONUS * 100)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.como_funciona.potencial.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.como_funciona.potencial", dev.forja.forge.Potential.FLOOR,
			dev.forja.forge.Potential.PER_QUALITY, dev.forja.forge.Potential.PER_QUALITY * 2, dev.forja.menu.Station.FORJA.capacity()), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.como_funciona.temple.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.como_funciona.temple"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.como_funciona.roto.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.como_funciona.roto"), INK));
		body.add(new Divider());
		body.add(new Text(Component.translatable("gui.forja.libros.primeras_mesas.yunque"), INK_SOFT));
		body.add(new BookCard(GuideBooks.Book.YUNQUE));
		return body;
	}

	/** The mod's keys, named the way the controls are bound now. */
	private List<Element> keysChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.teclas.intro"), INK_SOFT));
		body.add(new SubHeader(keyName(ForjaClient.GUIDE_KEY, "G")));
		body.add(new Text(Component.translatable("gui.forja.libros.teclas.g"), INK));
		body.add(new SubHeader(keyName(CombatClient.DODGE_KEY, "Alt")));
		body.add(new Text(Component.translatable("gui.forja.libros.teclas.esquivar"), INK));
		body.add(new SubHeader(keyName(ClassClient.TREE, "K")));
		body.add(new Text(Component.translatable("gui.forja.libros.teclas.clases",
			keyName(ClassClient.SKILL_1, "V"), keyName(ClassClient.SKILL_2, "B")), INK));
		body.add(new Text(Component.translatable("gui.forja.libros.teclas.cambiar"), INK_SOFT));
		return body;
	}

	private static Component keyName(net.minecraft.client.@Nullable KeyMapping key, String fallback) {
		return key == null ? Component.literal(fallback) : key.getTranslatedKeyMessage();
	}

	/** Every book on the shelf, with its recipe and when it is learned: in the notebook, and in the library. */
	private List<Element> shelfChapter() {
		List<Element> body = new ArrayList<>();
		boolean library = this.book == GuideBooks.Book.BIBLIOTECA;
		body.add(new Text(Component.translatable(library ? "gui.forja.libros.estanteria.biblioteca" : "gui.forja.libros.estanteria.intro"), INK));
		for (GuideBooks.Book target : GuideBooks.SHELF) {
			if (target == GuideBooks.Book.CUADERNO && !library) {
				continue;
			}
			body.add(new BookCard(target));
		}
		if (!library) {
			body.add(new Divider());
			body.add(new Text(Component.translatable("gui.forja.libros.estanteria.perdido"), INK_SOFT));
			body.add(new BookCard(GuideBooks.Book.CUADERNO));
		}
		return body;
	}

	/** Book I opens on what the notebook already said, for whoever opens it out of order. */
	private List<Element> anvilRecapChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.yunque_sabes"), INK));
		body.add(new Formula(List.of(new ItemStack(ModItems.MESA_DE_PIEZAS), new ItemStack(ModItems.MESA_DE_FORJA), engravedTemplate(PartType.CABEZA_PICO),
			ARROW, firstPickaxe())));
		body.add(new ChapterLink("primer_objeto"));
		body.add(new Text(Component.translatable("gui.forja.libros.yunque_sabes.ruta"), INK_SOFT));
		return body;
	}

	/**
	 * Book I's chapter on the tables: what each one is for and what its tabs do, rather than their recipes again (those
	 * are in the notebook); the workshop and its bonus, the saddlery that completes it, and the cabinet.
	 */
	private List<Element> workshopChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new SubHeader(Component.translatable("block.forja.mesa_de_piezas")));
		body.add(new Text(Component.translatable("gui.forja.libros.mesas.piezas"), INK));
		body.add(new SubHeader(Component.translatable("block.forja.mesa_de_forja")));
		body.add(new Text(Component.translatable("gui.forja.libros.mesas.forja"), INK));
		body.add(new Text(Component.translatable("gui.forja.libros.mesas.estrella"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.mesas.taller.titulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.MESA_DE_PIEZAS), new ItemStack(ModItems.MESA_DE_FORJA), new ItemStack(ModItems.MESA_DE_TALABARTERIA))));
		body.add(new Text(Component.translatable("gui.forja.libros.mesas.taller", dev.forja.forge.Potential.WHOLE_WORKSHOP,
			dev.forja.menu.ForgeMenu.WORKSHOP_RANGE), INK));
		Item planks = Items.OAK_PLANKS;
		Item leather = Items.LEATHER;
		body.add(new Crafting(new Item[] {leather, leather, leather, planks, Items.SADDLE, planks, planks, null, planks},
			new ItemStack(ModItems.MESA_DE_TALABARTERIA)));
		body.add(new SubHeader(Component.translatable("block.forja.mesa_de_forja_mayor")));
		body.add(new Text(Component.translatable("gui.forja.libros.mesas.mayor", dev.forja.menu.Station.BENCH.size(),
			dev.forja.forge.Potential.GREATER_TABLE), INK));
		body.add(new Divider());
		body.addAll(this.cabinetEntry());
		return body;
	}

	/** Cutting parts: what the bench cuts and what has to be poured, what a part costs and says, and the handle variants. */
	private List<Element> cuttingChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.cortar.intro"), INK));
		// The things themselves, six to a row, each naming itself under the mouse: read off the bench's own list.
		List<ItemStack> cut = ForgeMaterial.BASIC.stream().map(ForgeMaterial::displayStack).toList();
		for (int from = 0; from < cut.size(); from += 6) {
			body.add(new IconRow(cut.subList(from, Math.min(from + 6, cut.size()))));
		}
		body.add(new Text(Component.translatable("gui.forja.libros.cortar.colar"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.cortar.coste.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.piezas_intro"), INK));
		for (PartType part : List.of(PartType.CABEZA_PICO, PartType.HOJA, PartType.MANGO, PartType.ATADURA)) {
			body.add(new Part(part));
		}
		// Heavy and light handles and bindings (combat/Grip): a choice, not an upgrade.
		body.add(new SubHeader(Component.translatable("gui.forja.libros.variantes.titulo")));
		body.add(new Part(PartType.MANGO_PESADO));
		body.add(new Part(PartType.MANGO_LIGERO));
		body.add(new Text(Component.translatable("gui.forja.libros.variantes"), INK));
		body.add(new Text(Component.translatable("gui.forja.libros.variantes.hacer"), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libros.cortar.rasgo"), INK));
		body.add(new ChapterLink("piezas"));
		return body;
	}

	/** The star: forging, the hammer's centre, and the name every piece carries. */
	private List<Element> starChapter() {
		List<Element> body = new ArrayList<>();
		body.add(TablePanel.star(List.of(Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.PIEDRA),
			Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA), Assembler.createPart(PartType.ATADURA, ForgeMaterial.MADERA)), ItemStack.EMPTY));
		body.add(new Text(Component.translatable("gui.forja.libros.estrella.intro"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.perfecta.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.estrella.martillo", Math.round(dev.forja.forge.Quality.PERFECT_BONUS * 100),
			dev.forja.forge.Potential.PER_QUALITY * 2, dev.forja.forge.Potential.PER_QUALITY), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.estrella.potencial.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.estrella.potencial", dev.forja.forge.Potential.FLOOR,
			dev.forja.forge.Potential.PER_QUALITY * 2, dev.forja.forge.Potential.PER_SMITH_LEVEL, dev.forja.forge.Potential.WHOLE_WORKSHOP,
			dev.forja.forge.Potential.GREATER_TABLE, dev.forja.forge.Potential.CAST_PARTS), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.estrella.banco.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.estrella.banco", dev.forja.menu.Station.BENCH.size()), INK));
		for (ForgeType type : List.of(ForgeType.PICO, ForgeType.HACHA, ForgeType.ESPADA)) {
			body.add(new Recipe(type));
		}
		body.add(new ChapterLink("objetos"));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.firma.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.firma", Math.round(dev.forja.forge.Quality.AFFINITY_BONUS * 100)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.historia.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.historia"), INK_SOFT));
		return body;
	}

	/** Upgrading at the first table: what goes on the points, how far this table takes it, books and orbs. */
	private List<Element> improvingChapter() {
		List<Element> body = new ArrayList<>();
		body.add(TablePanel.star(List.of(new ItemStack(Items.SUGAR, 16)), firstPickaxe()));
		body.add(new Text(Component.translatable("gui.forja.libros.mejorar.pasos"), INK));
		for (Upgrade upgrade : List.of(Upgrade.FILO, Upgrade.EFICIENCIA, Upgrade.FORTUNA, Upgrade.PROTECCION)) {
			body.add(new UpgradeEntry(upgrade));
		}
		body.add(new Text(Component.translatable("gui.forja.libros.mejorar.tope", dev.forja.menu.Station.FORJA.capacity(),
			dev.forja.menu.Station.FORJA_MAYOR.capacity()), INK));
		body.add(new IconRow(List.of(new ItemStack(Items.ENCHANTED_BOOK), dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 40))));
		body.add(new Text(Component.translatable("gui.forja.libros.mejorar.libros"), INK));
		body.add(new ChapterLink("mejoras"));
		return body;
	}

	/** Taking apart, orbs, broken gear and repair: nothing forged is ever simply lost. */
	private List<Element> salvageChapter() {
		ItemStack broken = Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO));
		broken.setDamageValue(broken.getMaxDamage());
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.desarmar"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.mundo.orbes.titulo")));
		body.add(new IconRow(List.of(dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 40),
			dev.forja.item.UpgradeOrbItem.create(Upgrade.FORTUNA, 25), dev.forja.item.UpgradeOrbItem.create(Upgrade.PROTECCION, 30))));
		body.add(new Text(Component.translatable("gui.forja.libro.mundo.orbes"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.mundo.rotos.titulo")));
		body.add(new IconRow(List.of(broken)));
		body.add(new Text(Component.translatable("gui.forja.libro.mundo.rotos"), INK));
		body.add(new Text(Component.translatable("gui.forja.libros.reparar"), INK_SOFT));
		// The repair kits (forge/RepairKits): how one is made, and what it does in the grid, shown with iron.
		Item kit = dev.forja.forge.RepairKits.kit(ForgeMaterial.HIERRO);
		if (kit != null) {
			body.add(new SubHeader(Component.translatable("gui.forja.libros.kit_de_reparacion.titulo")));
			body.add(new Crafting(new Item[] {Items.IRON_INGOT, Items.IRON_INGOT, null, Items.LEATHER, Items.STRING, null, null, null, null},
				new ItemStack(kit)));
			body.add(new Text(Component.translatable("gui.forja.libros.kit_de_reparacion", dev.forja.forge.RepairKits.AMOUNT), INK));
			ItemStack mended = dev.forja.forge.RepairKits.repair(broken, ForgeMaterial.HIERRO);
			body.add(new Crafting(new ItemStack[] {new ItemStack(kit), broken.copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
				ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY}, mended));
		}
		return body;
	}

	/** The last page of book I: the two books that come after it, and the path. */
	private List<Element> anvilNextChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.yunque_siguiente"), INK));
		body.add(new BookCard(GuideBooks.Book.COMBATE));
		body.add(new BookCard(GuideBooks.Book.FUNDICION));
		body.add(new ChapterLink("siguiente_paso"));
		return body;
	}

	/** The library's separator: what follows is to look things up in, not to read. */
	private List<Element> catalogueChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.catalogo"), INK));
		body.add(new IconRow(List.of(Assembler.create(ForgeType.ESPADA, Assembler.defaultMaterials(ForgeType.ESPADA)),
			Assembler.createPart(PartType.HOJA, ForgeMaterial.HIERRO), new ItemStack(Items.IRON_INGOT),
			dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 60))));
		body.add(new Text(Component.translatable("gui.forja.libros.catalogo.como"), INK_SOFT));
		return body;
	}

	/** The greater table: what it makes that the first does not, and its recipe round damascus. */
	private List<Element> greaterTableChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.mesa_mayor", dev.forja.menu.Station.FORJA_MAYOR.capacity()), INK));
		Item stone = Items.POLISHED_BLACKSTONE;
		Item damascus = ModItems.alloy("damasco");
		body.add(new Crafting(new Item[] {stone, Items.GOLD_INGOT, stone, damascus, ModItems.MESA_DE_FORJA, damascus, stone, stone, stone},
			new ItemStack(ModItems.MESA_DE_FORJA_MAYOR)));
		body.add(new Text(ForjaPath.Step.MESA_MAYOR.description(), INK_SOFT));
		return body;
	}

	// ------------------------------------------------------------------ book II, El arte del combate

	private static String pct(double share) {
		return String.valueOf(Math.round(share * 100));
	}

	private static String secs(int ticks) {
		return number(ticks / 20.0F);
	}

	/** The body: stamina and what spends it, the dodge and its counter, jumping and running, and weight. */
	private List<Element> bodyChapter() {
		dev.forja.combat.CombatConfig cfg = dev.forja.combat.CombatConfig.get();
		List<Element> body = new ArrayList<>();
		body.add(new Summary(Component.translatable("gui.forja.libros.combate.cuerpo.resumen")));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.estamina.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.estamina", number(cfg.staminaMax), number(cfg.attackCost),
			number(cfg.dodgeCost), number(cfg.jumpCost), number(cfg.sprintJumpCost), number(cfg.staminaRegenPerTick * 20.0F),
			secs(cfg.staminaRegenDelayTicks), pct(cfg.tiredDamageMultiplier), number(cfg.blockCostPerDamage)), INK));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.especiales", number(cfg.whirlStamina), number(cfg.quakeStamina),
			number(cfg.reapStamina), number(cfg.chargeMoveStamina)), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.esquivar.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.esquivar", keyName(CombatClient.DODGE_KEY, "Alt"),
			cfg.dodgeIframeTicks, secs(cfg.counterWindowTicks), pct(cfg.counterDamage - 1.0), number(cfg.counterStaminaRefund)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.peso.titulo")));
		body.add(new IconRow(List.of(
			Assembler.create(ForgeType.DAGA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA)),
			Assembler.create(ForgeType.ESPADA, Assembler.defaultMaterials(ForgeType.ESPADA)),
			Assembler.create(ForgeType.MARTILLO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO)))));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.peso", pct(cfg.maxArmorSlow)), INK));
		return body;
	}

	/** Hitting: the charged blow, combos, posture and the finisher, where the blow lands and what it goes through. */
	private List<Element> hittingChapter() {
		dev.forja.combat.CombatConfig cfg = dev.forja.combat.CombatConfig.get();
		List<Element> body = new ArrayList<>();
		body.add(new Summary(Component.translatable("gui.forja.libros.combate.golpear.resumen")));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.cargado.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.cargado", secs(cfg.chargeDelayTicks + cfg.chargeFullTicks),
			pct(cfg.chargeDamageBonus), pct(cfg.chargePostureBonus), number(cfg.chargeStaminaCost)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.combo.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.combo", secs(cfg.comboWindowTicks),
			pct(cfg.comboFinisherDamage - 1.0), pct(cfg.comboFinisherPosture - 1.0)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.postura.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.postura", pct(cfg.staggerDamageMultiplier - 1.0),
			secs(cfg.staggerTicks), number((float) cfg.finisherMultiplier)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.zonas.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.zonas", pct(cfg.headMultiplier - 1.0)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.tipos.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.tipos", pct(cfg.penBlade), pct(cfg.penAxe), pct(cfg.penBlunt),
			pct(cfg.penSpear), pct(cfg.penArrowMax)), INK));
		// Every monster's own resistances, straight out of the config it plays by.
		body.add(new Text(Component.translatable("gui.forja.libros.combate.resisten"), INK_SOFT));
		for (Map.Entry<String, dev.forja.combat.CombatConfig.MobResistance> entry : cfg.resistenciasMobs.entrySet()) {
			net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(entry.getKey());
			if (id == null) {
				continue;
			}
			dev.forja.combat.CombatConfig.MobResistance resists = entry.getValue();
			// One of the mod's own not seen yet stays a shadow here too, as in the bestiary (vanilla's are known).
			boolean unseen = id.getNamespace().equals(dev.forja.Forja.MOD_ID) && this.book != GuideBooks.Book.TOMO
				&& !BookMemory.hasSeen(id.toString());
			body.add(new Text(Component.translatable("gui.forja.libros.combate.resiste",
				unseen ? Component.translatable("gui.forja.libros.bestiario.oculto") : Component.translatable("entity." + id.getNamespace() + "." + id.getPath()),
				times(resists.slash), times(resists.blunt), times(resists.pierce)), INK_SOFT));
		}
		return body;
	}

	private static String times(double factor) {
		return "×" + String.format(java.util.Locale.ROOT, "%.2f", factor).replaceAll("0+$", "").replaceAll("\\.$", "").replace('.', ',');
	}

	/** Defending: the parry, a weapon's own guard and its rhythm, and the shield's bash. */
	private List<Element> defendingChapter() {
		dev.forja.combat.CombatConfig cfg = dev.forja.combat.CombatConfig.get();
		List<Element> body = new ArrayList<>();
		body.add(new Summary(Component.translatable("gui.forja.libros.combate.defender.resumen")));
		body.add(new IconRow(List.of(Assembler.create(ForgeType.ESCUDO, Assembler.defaultMaterials(ForgeType.ESCUDO)),
			Assembler.create(ForgeType.ESPADA, Assembler.defaultMaterials(ForgeType.ESPADA)))));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.parada.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.parada"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.guardia.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.guardia", pct(cfg.weaponGuardBlock), cfg.weaponParryTicks,
			secs(cfg.parrySpamTicks), number(cfg.parryStaminaRefund)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.especial.escudo.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.especial.escudo"), INK));
		return body;
	}

	/** Magic, the plain way: the staff and the tome are weapons that spend mana, and the healing lantern. */
	private List<Element> magicChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Summary(Component.translatable("gui.forja.libros.combate.magia.resumen")));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.especial.baculo.titulo")));
		body.add(new IconRow(List.of(Assembler.create(ForgeType.BACULO, List.of(ForgeMaterial.AMATISTA, ForgeMaterial.HIERRO, ForgeMaterial.MADERA)))));
		body.add(new Text(Component.translatable("gui.forja.libro.especial.baculo"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.especial.grimorio.titulo")));
		body.add(new IconRow(List.of(Assembler.create(ForgeType.GRIMORIO, List.of(ForgeMaterial.AMATISTA, ForgeMaterial.HIERRO, ForgeMaterial.ORO)))));
		body.add(new Text(Component.translatable("gui.forja.libro.especial.grimorio"), INK));
		body.add(new SubHeader(Component.translatable("item.forja.farol")));
		body.add(new IconRow(List.of(Assembler.create(ForgeType.FAROL, List.of(ForgeMaterial.ESMERALDA, ForgeMaterial.ORO, ForgeMaterial.MADERA)))));
		body.add(new Text(Component.translatable("gui.forja.libro.clases.farol", Math.round(dev.forja.magic.Healing.BEAM_REACH),
			Math.round(dev.forja.magic.Healing.RING_REACH), Math.round(dev.forja.magic.Healing.RING_GROWTH),
			Math.round(dev.forja.magic.Healing.SELF_SHARE * 100)), INK));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.farol_curandero"), INK_SOFT));
		return body;
	}

	/** How the monsters fight: warnings and feints, turns and the ring, packs and captains, senses, and your gear. */
	private List<Element> enemiesChapter() {
		dev.forja.combat.CombatConfig cfg = dev.forja.combat.CombatConfig.get();
		List<Element> body = new ArrayList<>();
		body.add(new Summary(Component.translatable("gui.forja.libros.combate.enemigos.resumen")));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.aviso.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.aviso",
			pct(dev.forja.difficulty.Ladder.NORMAL.ownPreset().feint), pct(dev.forja.difficulty.Ladder.EXTREMO.ownPreset().feint)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.anillo.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.anillo", cfg.maxSimultaneousAttackers), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.grupos.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.grupos", cfg.packMin, cfg.packMax, cfg.packVeteranMin,
			cfg.packVeteranMax), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.sentidos.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.sentidos"), INK));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.sin_obras"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.equipo.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.equipo", pct(cfg.mobDamagePerGearTier), pct(cfg.pressureMax),
			pct(cfg.penetrationBaseMax), Math.round(Math.ceil(cfg.pressureMax / cfg.pressurePerHit)), secs(cfg.pressureDelayTicks),
			number((float) (cfg.pressureMax / cfg.pressureDrainPerTick / 20.0))), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.mundo.monstruos"), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.tregua"), INK_SOFT));
		return body;
	}

	/** Veterans, elites and champions: their badges, what they can take and go through, and what they drop. */
	private List<Element> ranksChapter() {
		dev.forja.combat.CombatConfig cfg = dev.forja.combat.CombatConfig.get();
		List<Element> body = new ArrayList<>();
		body.add(new Summary(Component.translatable("gui.forja.libros.combate.rangos.resumen")));
		body.add(new Text(Component.translatable("gui.forja.libro.insignias"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.tope.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.tope", pct(cfg.hitCapNormal), pct(cfg.hitCapVeteran),
			pct(cfg.hitCapElite), pct(cfg.hitCapChampion), pct(cfg.penetrationVeteran), pct(cfg.penetrationElite),
			pct(cfg.penetrationChampion), pct(cfg.penetrationBaseMax)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.elites.titulo")));
		body.add(new IconRow(List.of(new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.ROTTEN_FLESH), new ItemStack(Items.BONE))));
		body.add(new Text(Component.translatable("gui.forja.libro.elites", Math.round(dev.forja.ForjaConfig.get().elites * 100),
			Math.round(dev.forja.world.Elites.HEALTH)), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.ataques.elite",
			Math.round(dev.forja.world.Elites.WIND_MEND * 100)), INK_SOFT));
		return body;
	}

	/** The fights the world starts on its own: sieges, thieves, the ones who come back, duels and raiders. */
	private List<Element> worldFightsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Summary(Component.translatable("gui.forja.libros.combate.mundo.resumen")));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.asedio.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.asedio", dev.forja.ai.WorldFights.SIEGE_MIN,
			dev.forja.ai.WorldFights.SIEGE_MAX), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.ladrones.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.ladrones"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.duelo.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.duelo"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.saqueadores.titulo")));
		body.add(new IconRow(List.of(new ItemStack(Items.CROSSBOW), new ItemStack(Items.IRON_AXE), new ItemStack(Items.BELL))));
		body.add(new Text(Component.translatable("gui.forja.libro.saqueadores", dev.forja.world.ForgeRaiders.BAND), INK));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.noche_lluvia"), INK_SOFT));
		return body;
	}

	/** The difficulty ladder (difficulty/Ladder): what each step of Minecraft's button turns on, then the adaptive one and the nights. */
	private List<Element> difficultyChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Summary(Component.translatable("gui.forja.libros.combate.dificultad.resumen")));
		for (dev.forja.difficulty.Ladder level : dev.forja.difficulty.Ladder.values()) {
			dev.forja.difficulty.ForjaDifficulty preset = level.ownPreset();
			body.add(new Text(Component.translatable("gui.forja.libros.combate.dificultad.nivel." + level.name().toLowerCase(java.util.Locale.ROOT),
				Component.translatable(level.key()), times(preset.health), times(preset.damage), times(preset.threat), times(preset.loot),
				String.format(java.util.Locale.ROOT, "%.1f", dev.forja.difficulty.Ladder.SPRINT_BONUS * level.sprint * 100.0).replace(".0", "").replace('.', ','),
				pct(1.0 - level.stamina)), INK));
		}
		body.add(new Text(Component.translatable("gui.forja.libros.combate.dificultad.boton"), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.dificultad_actual",
			Component.translatable(dev.forja.difficulty.Ladder.current().key())), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.adaptativa.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.adaptativa"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.combate.noches.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.combate.noches"), INK));
		return body;
	}

	/** The last page of book II: the books that come next, and the path. */
	private List<Element> combatNextChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.combate.siguiente"), INK));
		body.add(new BookCard(GuideBooks.Book.FUNDICION));
		body.add(new BookCard(GuideBooks.Book.CLASES));
		body.add(new ChapterLink("siguiente_paso"));
		return body;
	}

	// ------------------------------------------------------------------ the upgrade probe (Andy, 2026-09-30)

	/** The piece in the probe's slot: remembered while the game runs, never taken from the bag, only copied. */
	private static ItemStack probe = ItemStack.EMPTY;
	/** Whether the picker of the bag's pieces is open over the book. */
	private boolean picking;
	/** What the probe listed last, as "fits:ID" and "no:ID:REASON", for the client test. */
	private final List<String> probeReport = new ArrayList<>();

	/**
	 * "¿Qué le cabe?": a slot for any forged tool, weapon or armour piece from the reader's bag, and the upgrades that
	 * go on it — those that fit now, and those that are compatible but do not fit, with why (UpgradeFit, the star's
	 * own rules). It starts on the piece in the reader's hand.
	 */
	private List<Element> probeChapter() {
		List<Element> body = new ArrayList<>();
		var player = this.minecraft.player;
		if (probe.isEmpty() && player != null && dev.forja.upgrade.UpgradeFit.upgradable(player.getMainHandItem())) {
			probe = player.getMainHandItem().copy();
		}
		body.add(new Text(Component.translatable("gui.forja.libros.probador.intro"), INK));
		body.add(new ItemProbe());
		this.probeReport.clear();
		if (probe.isEmpty()) {
			body.add(new Text(Component.translatable("gui.forja.libros.probador.vacio"), INK_SOFT));
			return body;
		}
		if (!dev.forja.upgrade.UpgradeFit.upgradable(probe)) {
			body.add(new Text(Component.translatable("gui.forja.libros.probador.no_forjado"), INK_SOFT));
			return body;
		}
		dev.forja.upgrade.UpgradeFit.Summary state = dev.forja.upgrade.UpgradeFit.summary(probe);
		body.add(new Text(Component.translatable("gui.forja.libros.probador.estado", state.potential(), state.load(), state.capacity(),
			state.pacts(), dev.forja.upgrade.Pacts.MOST, state.synergies(), dev.forja.upgrade.Synergy.MOST), INK_SOFT));
		List<dev.forja.upgrade.UpgradeFit.Fit> fits = dev.forja.upgrade.UpgradeFit.of(probe, player);
		List<dev.forja.upgrade.UpgradeFit.Fit> now = fits.stream().filter(dev.forja.upgrade.UpgradeFit.Fit::fits).toList();
		List<dev.forja.upgrade.UpgradeFit.Fit> not = fits.stream().filter(fit -> !fit.fits()).toList();
		// Once, not on every line: how far the first table goes, when that is short of the greater one.
		now.stream().filter(fit -> fit.bench() < fit.greater()).findFirst().ifPresent(fit ->
			body.add(new Text(Component.translatable("gui.forja.libros.probador.mesa", dev.forja.menu.Station.FORJA.capacity()), INK_SOFT)));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.probador.caben", now.size())));
		body.add(new Text(Component.translatable("gui.forja.libros.probador.pliegues"), INK_SOFT));
		this.probeGroups(body, "caben", now, state.synergies());
		for (dev.forja.upgrade.UpgradeFit.Fit fit : now) {
			this.probeReport.add("fits:" + fit.upgrade().name());
		}
		body.add(new SubHeader(Component.translatable("gui.forja.libros.probador.no_caben", not.size())));
		if (not.isEmpty()) {
			body.add(new Text(Component.translatable("gui.forja.libros.probador.ninguna"), INK_SOFT));
		}
		this.probeGroups(body, "no", not, state.synergies());
		for (dev.forja.upgrade.UpgradeFit.Fit fit : not) {
			this.probeReport.add("no:" + fit.upgrade().name() + ":" + fit.reason());
		}
		return body;
	}

	/**
	 * One list of the probe, cut by the sections the upgrades are listed under everywhere else (tools, weapons,
	 * armour, "para todo"...), each under a heading with its count: a new sword is then a handful of short lists
	 * and not ten flat pages (Andy, 2026-09-30).
	 */
	private void probeGroups(List<Element> body, String list, List<dev.forja.upgrade.UpgradeFit.Fit> fits, int awake) {
		for (String section : GuideText.UPGRADE_SECTIONS) {
			List<dev.forja.upgrade.UpgradeFit.Fit> here = fits.stream().filter(fit -> GuideText.section(fit.upgrade()).equals(section)).toList();
			if (here.isEmpty()) {
				continue;
			}
			String key = list + ":" + section;
			boolean open = OPEN_GROUPS.contains(key);
			body.add(new GroupHeading(Component.translatable(open ? "gui.forja.libros.probador.grupo_abierto" : "gui.forja.libros.probador.grupo",
				Component.translatable("gui.forja.libro.seccion." + section), here.size()), key));
			if (!open) {
				// Folded: the names only, on a line or two; a click on the heading unfolds the whole of each.
				net.minecraft.network.chat.MutableComponent names = Component.empty();
				for (int i = 0; i < here.size(); i++) {
					if (i > 0) {
						names.append(", ");
					}
					Upgrade upgrade = here.get(i).upgrade();
					names.append(upgrade.isPact() && !this.pactKnown(upgrade) ? Component.translatable("gui.forja.libros.pacto_sellado") : upgrade.displayName());
				}
				body.add(new Text(names, INK));
				continue;
			}
			for (dev.forja.upgrade.UpgradeFit.Fit fit : here) {
				body.add(new FitEntry(fit, awake));
			}
		}
	}

	/** The probe's groups the reader has unfolded, kept while the game runs. */
	private static final Set<String> OPEN_GROUPS = new java.util.HashSet<>();

	/** Fold or unfold one of the probe's groups, and lay the book out again where the reader is. Public for the client test. */
	public void toggleGroup(String key) {
		if (!OPEN_GROUPS.remove(key)) {
			OPEN_GROUPS.add(key);
		}
		int at = this.spread;
		this.pages.clear();
		this.chapters.clear();
		this.creatures.clear();
		this.shadowed.clear();
		this.build();
		this.goToPage(Math.min(at * 2, this.pages.size() - 1));
	}

	/**
	 * Put a piece in the probe and lay the book out again, staying at the probe. A copy: the piece in the bag is not
	 * moved, taken or changed. Public for the client test.
	 */
	public void probe(ItemStack stack) {
		probe = stack.copy();
		this.picking = false;
		this.pages.clear();
		this.chapters.clear();
		this.creatures.clear();
		this.shadowed.clear();
		this.build();
		this.goToPage(this.chapterPage("probador"));
	}

	/** What the probe listed for the piece in it, for the client test. */
	public List<String> probeReport() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		return List.copyOf(this.probeReport);
	}

	/** The pieces of the reader's bag the picker offers: whatever is forged, worn and held included. */
	private List<ItemStack> pickable() {
		List<ItemStack> found = new ArrayList<>();
		var player = this.minecraft.player;
		if (player == null) {
			return found;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (dev.forja.upgrade.UpgradeFit.upgradable(stack)) {
				found.add(stack);
			}
		}
		return found;
	}

	private static final int PICK_COLUMNS = 9;
	private static final int PICK_CELL = 20;

	private int pickLeft() {
		return this.width / 2 - PICK_COLUMNS * PICK_CELL / 2 - 6;
	}

	private int pickTop() {
		return this.bookTop() + 30;
	}

	/** The picker, over the book: a dark veil, a panel, and one cell per forged piece of the bag. */
	private void drawPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.nextStratum();
		g.fill(0, 0, this.width, this.height, 0xA0100C08);
		List<ItemStack> items = this.pickable();
		int rows = Math.max(1, (items.size() + PICK_COLUMNS - 1) / PICK_COLUMNS);
		int left = this.pickLeft();
		int top = this.pickTop();
		int right = left + PICK_COLUMNS * PICK_CELL + 12;
		int bottom = top + 22 + rows * PICK_CELL + 8;
		g.fill(left, top, right, bottom, PAPER);
		g.outline(left, top, right - left, bottom - top, INK_SOFT);
		g.text(this.font, Component.translatable("gui.forja.libros.probador.elige"), left + 6, top + 6, INK, false);
		if (items.isEmpty()) {
			g.text(this.font, Component.translatable("gui.forja.libros.probador.nada"), left + 6, top + 24, INK_SOFT, false);
			return;
		}
		ItemStack hovered = ItemStack.EMPTY;
		for (int i = 0; i < items.size(); i++) {
			int x = left + 6 + i % PICK_COLUMNS * PICK_CELL;
			int y = top + 20 + i / PICK_COLUMNS * PICK_CELL;
			boolean over = over(mouseX, mouseY, x, y, PICK_CELL - 2, PICK_CELL - 2);
			g.fill(x, y, x + PICK_CELL - 2, y + PICK_CELL - 2, over ? BAND : PAPER_SHADE);
			g.item(items.get(i), x + 1, y + 1);
			if (over) {
				hovered = items.get(i);
			}
		}
		if (!hovered.isEmpty()) {
			g.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
		}
	}

	/** A click while the picker is open: a piece goes into the probe; anywhere else closes the picker. */
	private boolean pickAt(double mouseX, double mouseY) {
		List<ItemStack> items = this.pickable();
		for (int i = 0; i < items.size(); i++) {
			int x = this.pickLeft() + 6 + i % PICK_COLUMNS * PICK_CELL;
			int y = this.pickTop() + 20 + i / PICK_COLUMNS * PICK_CELL;
			if (mouseX >= x && mouseY >= y && mouseX < x + PICK_CELL - 2 && mouseY < y + PICK_CELL - 2) {
				this.probe(items.get(i));
				return true;
			}
		}
		this.picking = false;
		return true;
	}

	/** Opens the picker, for the client test and the slot. */
	public void openPicker() {
		this.picking = true;
	}

	// ------------------------------------------------------------------ book III, La fundición

	/** Book III opens on where book I left off: the first table, and what heat is about to change. */
	private List<Element> foundryRecapChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.fundicion_sabes"), INK));
		body.add(new IconRow(List.of(new ItemStack(ModItems.MESA_DE_FORJA), new ItemStack(Items.CAMPFIRE), new ItemStack(Items.MAGMA_BLOCK),
			new ItemStack(Items.LAVA_BUCKET))));
		body.add(new ChapterLink("mejorar"));
		return body;
	}

	/** Heat under the table and the first alloys: what a campfire makes, and melting parts back down. */
	private List<Element> firstAlloysChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.aleaciones_intro", dev.forja.forge.Alloys.ALL.size()), INK));
		for (dev.forja.forge.Alloys.Heat heat : dev.forja.forge.Alloys.Heat.values()) {
			if (heat == dev.forja.forge.Alloys.Heat.FRIA) {
				continue;
			}
			body.add(new Text(Component.translatable("gui.forja.libros.fundicion.calor_linea",
				Component.translatable("gui.forja.libro.calor." + heat.id()), Component.translatable("gui.forja.libro.calor." + heat.id() + ".desc")), INK_SOFT));
		}
		body.add(new SubHeader(ForjaPath.Step.ALEACION.title()));
		body.add(new Text(ForjaPath.Step.ALEACION.description(), INK));
		this.alloyRecipes(body, dev.forja.forge.Alloys.Heat.TEMPLADA);
		body.add(new ChapterLink("catalogo_aleaciones"));
		body.add(new SubHeader(Component.translatable("gui.forja.fundir.titulo")));
		body.add(new IconRow(List.of(Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO), new ItemStack(Items.LAVA_BUCKET),
			new ItemStack(Items.IRON_INGOT))));
		body.add(new Text(Component.translatable("gui.forja.libro.fundir_piezas"), INK));
		return body;
	}

	/** Every alloy at one heat: what goes in, what comes out and how much. */
	private void alloyRecipes(List<Element> body, dev.forja.forge.Alloys.Heat heat) {
		for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.ALL) {
			if (recipe.heat() != heat || !dev.forja.forge.Alloys.anywhere(recipe)) {
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
	}

	/**
	 * The alloys only a far forge makes (docs/ALEACIONES_NETHER_END.md), each under the forge that makes it: no
	 * heat under a table reaches them, so they are not listed by heat.
	 */
	private void farAlloyRecipes(List<Element> body) {
		for (dev.forja.forge.Alloys.Place place : dev.forja.forge.Alloys.Place.values()) {
			List<dev.forja.forge.Alloys.Recipe> made = dev.forja.forge.Alloys.at(place);
			if (place == dev.forja.forge.Alloys.Place.ANY || made.isEmpty()) {
				continue;
			}
			body.add(new SubHeader(Component.translatable("gui.forja.fragua_lejana." + place.id())));
			body.add(new Text(Component.translatable("gui.forja.fragua_lejana.donde." + place.id()), INK_SOFT));
			for (dev.forja.forge.Alloys.Recipe recipe : made) {
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
	}

	/** The catalogue's alloys, one by one by heat, and how long a sword of each lasts. */
	private List<Element> alloyListChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.catalogo.aleaciones"), INK_SOFT));
		for (dev.forja.forge.Alloys.Heat heat : dev.forja.forge.Alloys.Heat.values()) {
			if (heat == dev.forja.forge.Alloys.Heat.FRIA) {
				continue;
			}
			body.add(new SubHeader(Component.translatable("gui.forja.libro.calor." + heat.id())));
			body.add(new Text(Component.translatable("gui.forja.libro.calor." + heat.id() + ".desc"), INK_SOFT));
			this.alloyRecipes(body, heat);
			body.add(new Spacer(3));
		}
		this.farAlloyRecipes(body);
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

	/** The last page of book III: the greater table's book and the world's, and the path. */
	private List<Element> foundryNextChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.fundicion_siguiente"), INK));
		body.add(new BookCard(GuideBooks.Book.MESA_MAYOR));
		body.add(new BookCard(GuideBooks.Book.BASTION));
		body.add(new ChapterLink("siguiente_paso"));
		return body;
	}

	// ------------------------------------------------------------------ book IV, La mesa mayor

	/** Book IV opens on what the greater table changes: everything the first would not make, and upgrades to the top. */
	private List<Element> greaterRecapChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.mayor_sabes", dev.forja.menu.Station.FORJA.capacity(),
			dev.forja.menu.Station.FORJA_MAYOR.capacity()), INK));
		body.add(new IconRow(List.of(new ItemStack(ModItems.MESA_DE_FORJA_MAYOR),
			Assembler.create(ForgeType.ESPADON, Assembler.defaultMaterials(ForgeType.ESPADON)),
			Assembler.create(ForgeType.ESCUDO, Assembler.defaultMaterials(ForgeType.ESCUDO)),
			new ItemStack(ModItems.FUNDENTE_MAESTRO))));
		body.add(new ChapterLink("mesa_mayor"));
		body.add(new ChapterLink("probador"));
		return body;
	}

	/** The last page of book IV: the classes and the world, and the path. */
	private List<Element> greaterNextChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.mayor_siguiente"), INK));
		body.add(new BookCard(GuideBooks.Book.CLASES));
		body.add(new BookCard(GuideBooks.Book.BASTION));
		body.add(new ChapterLink("siguiente_paso"));
		return body;
	}

	// ------------------------------------------------------------------ book V, Clases

	/** Book V opens on what a class is, and on the rule that opening this book is what opens the classes. */
	private List<Element> classesRecapChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.clases_sabes", keyName(ClassClient.TREE, "K")), INK));
		List<ItemStack> icons = new ArrayList<>();
		for (dev.forja.clase.PlayerClass clazz : dev.forja.clase.PlayerClass.values()) {
			icons.add(clazz.icon());
		}
		for (int from = 0; from < icons.size(); from += 5) {
			body.add(new IconRow(icons.subList(from, Math.min(from + 5, icons.size()))));
		}
		body.add(new ClassButton());
		return body;
	}

	/** The last page of book V: the world's book and the path. */
	private List<Element> classesNextChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.clases_siguiente"), INK));
		body.add(new BookCard(GuideBooks.Book.BASTION));
		body.add(new ChapterLink("siguiente_paso"));
		return body;
	}

	// ------------------------------------------------------------------ book VI, El Bastión y el Herrero

	/** Book VI opens on the world outside the workshop: what there is to find, and why go. */
	private List<Element> bastionRecapChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.bastion_sabes"), INK));
		body.add(new IconRow(List.of(new ItemStack(Items.FILLED_MAP), new ItemStack(Items.EMERALD), new ItemStack(ModItems.PLANTILLA),
			dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 50))));
		return body;
	}

	/** The ruins: the abandoned forge, the barrow, the raiders' camp, the forge castle and its guardian, and the Nether's. */
	private List<Element> ruinsChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new SubHeader(Component.translatable("gui.forja.libros.ruinas.forja.titulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.MESA_DE_PIEZAS), new ItemStack(ModItems.MESA_DE_FORJA), new ItemStack(Items.CHEST))));
		body.add(new Text(Component.translatable("gui.forja.libros.ruinas.forja"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.tumulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.YUNQUE_DEL_HERRERO), new ItemStack(Items.SOUL_LANTERN), new ItemStack(ModItems.SELLO))));
		body.add(new Text(Component.translatable("gui.forja.libro.tumulo_desc"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.campamento")));
		body.add(new IconRow(List.of(new ItemStack(Items.SPRUCE_LOG), new ItemStack(Items.CAMPFIRE), new ItemStack(Items.BELL), new ItemStack(Items.FILLED_MAP))));
		body.add(new Text(Component.translatable("gui.forja.libro.campamento_desc"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.ruinas.castillo.titulo")));
		body.add(new IconRow(List.of(new ItemStack(Items.DEEPSLATE_BRICKS), new ItemStack(ModItems.FAROL_DE_PAVESA), new ItemStack(Items.IRON_BARS))));
		body.add(new Text(Component.translatable("gui.forja.libros.ruinas.castillo"), INK));
		// His page writes itself when he is met, as in book II's bestiary.
		this.creature(body, true, "guardian_de_cuno", level -> new dev.forja.entity.CuneGuardian(dev.forja.registry.ModEntities.GUARDIAN_DE_CUNO, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.guardian_de_cuno", Math.round(dev.forja.entity.CuneGuardian.HEALTH)), INK));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("gui.forja.libros.ruinas.nether.titulo")));
		body.add(new IconRow(List.of(new ItemStack(Items.POLISHED_BLACKSTONE_BRICKS), new ItemStack(ModItems.FRAGUA_DE_ALMAS),
			new ItemStack(ModItems.alloy("fatuo")))));
		body.add(new Text(Component.translatable("gui.forja.libros.ruinas.nether_almas"), INK));
		body.add(new ChapterLink("fraguas_lejanas"));
		return body;
	}

	/** The far forges (docs/ALEACIONES_NETHER_END.md): where each one is, what lights it and what it wakes. */
	private List<Element> farForgesChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new SubHeader(Component.translatable("gui.forja.fragua_lejana.almas")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.FRAGUA_DE_ALMAS), new ItemStack(Items.BLAZE_ROD),
			new ItemStack(Items.BLAZE_POWDER), new ItemStack(ModItems.alloy("fatuo")), new ItemStack(ModItems.alloy("magmacero")))));
		body.add(new Text(Component.translatable("gui.forja.libros.fraguas_lejanas.almas"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.fragua_lejana.vacio")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.FRAGUA_DEL_VACIO), new ItemStack(Items.ENDER_EYE),
			new ItemStack(Items.ENDER_PEARL), new ItemStack(ModItems.alloy("eterio")))));
		body.add(new Text(Component.translatable("gui.forja.libros.fraguas_lejanas.vacio"), INK));
		body.add(new ChapterLink("aleaciones_lejanas"));
		return body;
	}

	/** Book III: the alloys only a far forge makes, their recipes and what their metal does. */
	private List<Element> farAlloysChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.aleaciones_lejanas"), INK));
		this.farAlloyRecipes(body);
		for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.ALL) {
			ForgeMaterial material = dev.forja.forge.Alloys.anywhere(recipe) ? null : ForgeMaterial.fromInput(recipe.result());
			if (material != null) {
				body.add(new Text(Component.translatable("gui.forja.libros.aleaciones_lejanas.rasgo", material.trait.displayName(),
					Component.translatable("trait.forja." + material.trait.id() + ".largo")), INK_SOFT));
			}
		}
		body.add(new ChapterLink("fraguas_lejanas"));
		return body;
	}

	/**
	 * Book III: the middle tier and the peak alloys (docs/ALEACIONES_CUMBRE.md): the four poured from two alloys and one more
	 * thing, and what each one's trait does. The far forges' three are also in {@link #farAlloysChapter()}.
	 */
	private List<Element> peakAlloysChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.aleaciones_cumbre"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.aleaciones_cumbre.intermedias")));
		this.alloyRows(body, dev.forja.forge.Alloys.MIDDLE);
		body.add(new Text(Component.translatable("gui.forja.libros.aleaciones_cumbre.donde"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.cap.aleaciones_cumbre")));
		this.alloyRows(body, dev.forja.forge.Alloys.PEAK);
		this.alloyTraits(body, dev.forja.forge.Alloys.MIDDLE);
		this.alloyTraits(body, dev.forja.forge.Alloys.PEAK);
		body.add(new ChapterLink("aleaciones_lejanas"));
		return body;
	}

	/** One icon row and one line per alloy of this set: what goes in, how many come out. */
	private void alloyRows(List<Element> body, java.util.Set<String> ids) {
		for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.ALL) {
			if (!ids.contains(recipe.id())) {
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
	}

	/** What the trait of each alloy of this set does, in the same words as the catalogue. */
	private void alloyTraits(List<Element> body, java.util.Set<String> ids) {
		for (dev.forja.forge.Alloys.Recipe recipe : dev.forja.forge.Alloys.ALL) {
			ForgeMaterial material = ids.contains(recipe.id()) ? ForgeMaterial.fromInput(recipe.result()) : null;
			if (material != null) {
				body.add(new Text(Component.translatable("gui.forja.libros.aleaciones_cumbre.rasgo", material.trait.displayName(),
					Component.translatable("trait.forja." + material.trait.id() + ".largo")), INK_SOFT));
			}
		}
	}

	/** Who the Fallen Smith was: the story, with no mechanics in it. */
	private List<Element> smithStoryChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.herrero_historia"), INK));
		body.add(new IconRow(List.of(new ItemStack(ModItems.FRAGUA_APAGADA), new ItemStack(ModItems.CORAZON_DE_FORJA))));
		body.add(new Text(Component.translatable("gui.forja.libros.herrero_historia.dos"), INK));
		return body;
	}

	/** The Guild's Bastion: where it stands, how to find it, what is inside and what it is for. */
	private List<Element> bastionChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.bastion.donde"), INK));
		body.add(new IconRow(List.of(new ItemStack(Items.FILLED_MAP), new ItemStack(Items.EMERALD, 24))));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.bastion.dentro.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.bastion.dentro"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.bastion.forja.titulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.MENSULA_ESTELAR), new ItemStack(ModItems.PERLA_DE_ORICALCO))));
		body.add(new Text(Component.translatable("gui.forja.libros.bastion.forja"), INK));
		return body;
	}

	/** The way to the Smith's world: orichalcum, the pearl and the frame. */
	private List<Element> starPortalChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new SubHeader(Component.translatable("gui.forja.libro.cementerio.oricalco.titulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.HIERRO_ESTELAR), new ItemStack(ModItems.PLACA_HUECA), new ItemStack(ModItems.ESCORIA),
			new ItemStack(ModItems.alloy("acero_estelar")), new ItemStack(ModItems.alloy("almacero")), new ItemStack(ModItems.ORICALCO))));
		body.add(new Text(Component.translatable("gui.forja.libro.cementerio.oricalco"), INK_SOFT));
		// Oricalco is a forge material too (docs/HERRERO_DIMENSION.md, 1.4): any part, cast on a soul table.
		body.add(new SubHeader(Component.translatable("gui.forja.libros.oricalco_forja.titulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.MESA_DE_ALMAS),
			Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.ORICALCO), Assembler.createPart(PartType.HOJA, ForgeMaterial.ORICALCO),
			Assembler.createPart(PartType.PLACA_PECHERA, ForgeMaterial.ORICALCO), new ItemStack(ModItems.HIERRO_ESTELAR))));
		body.add(new Text(Component.translatable("gui.forja.libros.oricalco_forja",
			Math.round(dev.forja.magic.Mana.ORICHALCUM_REGEN * 100), Math.round(dev.forja.magic.Mana.ORICHALCUM_SET_MANA)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.cementerio.perla.titulo")));
		body.add(new IconRow(List.of(new ItemStack(Items.ENDER_PEARL), new ItemStack(ModItems.MESA_DE_LOSA), new ItemStack(ModItems.PERLA_DE_ORICALCO))));
		body.add(new Text(Component.translatable("gui.forja.libro.cementerio.perla", dev.forja.block.entity.CastingTableBlockEntity.PEARL_COST), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.cementerio.portal.titulo")));
		body.add(new IconRow(List.of(new ItemStack(ModItems.MENSULA_ESTELAR), new ItemStack(ModItems.PERLA_DE_ORICALCO))));
		body.add(new Text(Component.translatable("gui.forja.libro.cementerio.portal"), INK_SOFT));
		return body;
	}

	/** The last page of book VI: the last book. */
	private List<Element> bastionNextChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.bastion_siguiente"), INK));
		body.add(new BookCard(GuideBooks.Book.CEMENTERIO));
		body.add(new ChapterLink("siguiente_paso"));
		return body;
	}

	// ------------------------------------------------------------------ book VII, El Cementerio entre Estrellas

	/** The journey: what the world beyond the portal is, how to come back, and what cannot be done there. */
	private List<Element> graveyardJourneyChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.cementerio.intro"), INK));
		body.add(new IconRow(List.of(new ItemStack(ModItems.PERLA_DE_ORICALCO), new ItemStack(Items.NETHER_STAR), new ItemStack(ModItems.HIERRO_ESTELAR))));
		body.add(new Text(Component.translatable("gui.forja.libros.cementerio.meseta"), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.cementerio.vuelta"), INK_SOFT));
		body.add(new ChapterLink("portal_estelar"));
		return body;
	}

	/** The fight, in the order it comes: the fall, the stages, the apprentices, the Reforging, the constellations. */
	private List<Element> graveyardFightChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Summary(Component.translatable("gui.forja.libros.cementerio.pelea.resumen")));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.cementerio.llegada.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.cementerio.llegada"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.cementerio.fases.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.herrero_caido_fases",
			dev.forja.entity.FallenSmith.healthFor(dev.forja.difficulty.Ladder.current()), dev.forja.entity.FallenSmith.embersFor(1)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.cementerio.fuerza.titulo")));
		body.add(this.smithStrength(INK));
		body.add(this.smithFury(INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.cementerio.braseros.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.cementerio.braseros", dev.forja.world.StarFight.REFILL_IRON), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.cementerio.constelaciones.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.cementerio.constelaciones"), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.cementerio.golpes.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.ataques.herrero", Math.round(dev.forja.entity.FallenSmith.WAVE_DAMAGE)), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.herrero_caido_defensa"), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.herrero_caido_reclama",
			dev.forja.entity.FallenSmith.RECLAIM_WINDOW / 20, Math.round(dev.forja.entity.FallenSmith.RECLAIM_HEAVY_DAMAGE),
			Math.round(dev.forja.entity.FallenSmith.RECLAIM_RADIUS),
			dev.forja.entity.FallenSmith.RECLAIM_COOLDOWN / 20), INK_SOFT));
		return body;
	}

	/** What beating him is worth: the Forged Star, his heart, his anvil and hammer, the way home and the rematch. */
	private List<Element> graveyardRewardChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new IconRow(List.of(new ItemStack(ModItems.ESTRELLA_FORJADA), new ItemStack(ModItems.CORAZON_DE_FORJA),
			new ItemStack(ModItems.YUNQUE_DEL_HERRERO), new ItemStack(ModItems.MARTILLO_DEL_MAESTRO))));
		body.add(new SubHeader(Component.translatable("item.forja.estrella_forjada")));
		body.add(new Text(Component.translatable("gui.forja.libros.cementerio.estrella", dev.forja.forge.ForgedStar.MOST,
			dev.forja.forge.ForgedStar.POTENTIAL_BONUS, times(dev.forja.forge.ForgedStar.DAMAGE), times(dev.forja.forge.ForgedStar.DURABILITY),
			dev.forja.forge.ForgedStar.ARMOR, number(dev.forja.forge.ForgedStar.TOUGHNESS)), INK));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.cementerio.botin.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.cementerio.botin"), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.yunque"), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.martillo_maestro"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libros.cementerio.revancha.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libros.cementerio.revancha"), INK));
		return body;
	}

	/** The Smith's own page, in shadow until he has been seen. */
	private List<Element> graveyardSmithChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.cementerio.ficha"), INK_SOFT));
		this.creature(body, true, "herrero_caido", level -> new dev.forja.entity.FallenSmith(dev.forja.registry.ModEntities.HERRERO_CAIDO, level),
			new IconRow(List.of(new ItemStack(ModItems.CORAZON_DE_FORJA), new ItemStack(ModItems.YUNQUE_DEL_HERRERO), new ItemStack(ModItems.ESTRELLA_FORJADA))),
			new Text(Component.translatable("gui.forja.libro.bestiario.herrero",
				dev.forja.entity.FallenSmith.healthFor(dev.forja.difficulty.Ladder.current())), INK));
		return body;
	}

	/** His strength by level, gear and players (FallenSmith: Grade, GEAR_HEALTH_PER_TIER, HEALTH_PER_PLAYER). */
	private Text smithStrength(int colour) {
		return new Text(Component.translatable("gui.forja.libros.cementerio.fuerza",
			dev.forja.entity.FallenSmith.healthFor(dev.forja.difficulty.Ladder.FACIL),
			dev.forja.entity.FallenSmith.healthFor(dev.forja.difficulty.Ladder.NORMAL),
			dev.forja.entity.FallenSmith.healthFor(dev.forja.difficulty.Ladder.DIFICIL),
			dev.forja.entity.FallenSmith.healthFor(dev.forja.difficulty.Ladder.EXTREMO),
			pct(dev.forja.entity.FallenSmith.GEAR_HEALTH_PER_TIER),
			dev.forja.entity.FallenSmith.grade(dev.forja.difficulty.Ladder.DIFICIL).keepers(),
			dev.forja.entity.FallenSmith.grade(dev.forja.difficulty.Ladder.EXTREMO).keepers()), colour);
	}

	/** His fury under a third (FallenSmith.ENRAGE_SPEED, ENRAGE_DAMAGE, WAVE_HEALTH). */
	private Text smithFury(int colour) {
		return new Text(Component.translatable("gui.forja.libros.cementerio.furia", pct(dev.forja.entity.FallenSmith.ENRAGE_SPEED),
			pct(dev.forja.entity.FallenSmith.ENRAGE_DAMAGE), pct(dev.forja.entity.FallenSmith.WAVE_HEALTH[1])), colour);
	}

	/** The end of the guide. */
	private List<Element> graveyardEndChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libros.cementerio.fin"), INK));
		body.add(new IconRow(List.of(new ItemStack(ModItems.ESTANTERIA_DEL_HERRERO))));
		body.add(new Crafting(new Item[] {Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.BOOK, Items.IRON_INGOT, Items.BOOK,
			Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS}, new ItemStack(ModItems.ESTANTERIA_DEL_HERRERO)));
		body.add(new Text(Component.translatable("gui.forja.libros.estanteria_bloque"), INK_SOFT));
		return body;
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

	/**
	 * Siguiente paso: the page about where the reader is rather than about the mod. The step to do next
	 * (the first of ForjaPath's not done yet), what to do, where and with what, and a link to the chapter
	 * that explains it; then the whole path as a list that ticks itself off from the player's own
	 * advancements, each line a way to its chapter.
	 */
	private List<Element> nextStepChapter() {
		ForjaPath.Step next = ForjaPath.next(this.pathDone::contains);
		List<Element> body = new ArrayList<>();
		body.add(new StepCard(next, ForjaPath.doneCount(this.pathDone::contains)));
		if (next != null) {
			body.add(new Text(next.description(), INK));
			body.add(new ChapterLink(next.chapter));
		} else {
			// The end of the path is the start of the rest of the book, which is the world.
			body.add(new Text(Component.translatable("gui.forja.camino.completo.desc"), INK));
			body.add(new ChapterLink("eventos"));
		}
		// The whole path on a page of its own: ten steps and their heading fill one, and split over a turn they
		// are a list whose last lines nobody sees.
		body.add(new PageBreak());
		body.add(new SubHeader(Component.translatable("gui.forja.camino.titulo")));
		body.add(new Text(Component.translatable("gui.forja.camino.intro", ForjaPath.STEPS.size()), INK_SOFT));
		for (ForjaPath.Step step : ForjaPath.STEPS) {
			body.add(new PathRow(step, this.pathDone.contains(step.advancement()), step == next));
		}
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
		// Counted, not written in: it said "eight alloys, the best three only off lava" long after there
		// were sixteen and the best three had moved to the obsidian crucible.
		body.add(new Text(Component.translatable("gui.forja.libro.aleaciones_intro", dev.forja.forge.Alloys.ALL.size()), INK));
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
				if (recipe.heat() != heat || !dev.forja.forge.Alloys.anywhere(recipe)) {
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
		this.farAlloyRecipes(body);
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
	 * box, strainer and table, and what each one wants from the one before. (Steps 5 to 7 were rewritten
	 * when casting left the box's menu for the tables: the box only prepares now, the strainer is a block
	 * standing on the table, and the metal falls through it onto the mould.) Every number in it comes out of the code, so it
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
		// How the network shares the metal out (Andy: "llenando 1 por 1"), and the valve that cuts it.
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.llenado"), INK));
		body.add(new IconRow(List.of(new ItemStack(ModItems.LLAVE_DE_PASO), new ItemStack(Items.REDSTONE_TORCH),
			new ItemStack(Items.COMPARATOR))));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.llave"), INK));

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
		// Where it goes now: on the table, under the spout, not in a slot.
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.CANO_DE_COLADA), dev.forja.item.StrainerItem.of(null),
			dev.forja.item.CastingMouldItem.of(PartType.CABEZA_PICO), new ItemStack(ModItems.MESA_DE_LOSA)
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso6.basta",
			Math.round(-dev.forja.forge.Quality.ROUGH_PENALTY * 100),
			dev.forja.block.entity.CastingTableBlockEntity.CAST_PERCENT), 0xFF9A3412));

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
				Component.translatable("block.forja." + tier.id()),
				tier.holds == Integer.MAX_VALUE ? Component.translatable("gui.forja.caja.todo")
					: Component.literal(String.valueOf(tier.holds)),
				tier.cools, Math.round(tier.luck * 100.0F)), INK_SOFT));
		}
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso7.frio",
			dev.forja.block.entity.CastingTableBlockEntity.SPEND,
			Math.round(-dev.forja.forge.Quality.ROUGH_PENALTY * 100)), 0xFF9A3412));

		// 8. Heat down a pipe (FUNDICION_V2, part B): the boiler, the depot, and the five fluids.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.calor")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.calor.desc",
			dev.forja.block.entity.BoilerBlockEntity.CALDERA_CAPACITY,
			dev.forja.block.entity.BoilerBlockEntity.DEPOSITO_CAPACITY), INK));
		body.add(new IconRow(List.of(
			new ItemStack(ModItems.TUBO_DE_CALOR), new ItemStack(ModItems.CALDERA), new ItemStack(ModItems.DEPOSITO_DE_CALOR)
		)));
		for (dev.forja.forge.HeatFluid fluid : dev.forja.forge.HeatFluid.values()) {
			body.add(new FluidSwatch(fluid));
			body.add(new Text(this.fluidLine(fluid), INK_SOFT));
			body.add(new IconRow(this.fluidInputs(fluid)));
		}
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.calor.uno"), 0xFF9A3412));

		// 9. And what the whole thing is for at the top end.
		// 9. The whole line with nobody at it (docs/FUNDICION_V2.md, part C): hoppers in, hoppers out.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.paso8")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso8.desc"), INK));
		body.add(new IconRow(List.of(
			new ItemStack(Items.HOPPER), new ItemStack(Items.RAW_IRON), new ItemStack(ModItems.ASCUA),
			new ItemStack(ModItems.FAROL_DE_PAVESA), new ItemStack(Items.CHEST)
		)));

		// 10. The assembler: the forge star without the smith, and always a plain press.
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.paso9")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso9.desc"), INK));
		body.add(new IconRow(List.of(
			Assembler.createPart(PartType.CABEZA_PICO, ForgeMaterial.HIERRO), Assembler.createPart(PartType.MANGO, ForgeMaterial.MADERA),
			Assembler.createPart(PartType.ATADURA, ForgeMaterial.CUERO), new ItemStack(ModItems.MONTADORA),
			Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.MADERA, ForgeMaterial.CUERO))
		)));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso9.calor",
			dev.forja.block.entity.AssemblerMachineBlockEntity.WORK_TEMPLADA / 20.0F,
			dev.forja.block.entity.AssemblerMachineBlockEntity.WORK_CALIENTE / 20.0F,
			dev.forja.block.entity.AssemblerMachineBlockEntity.WORK_FUNDIDA / 20.0F), INK_SOFT));
		body.add(new IconRow(List.of(dev.forja.item.CastingFrameItem.of(ForgeType.ESPADON), new ItemStack(ModItems.MONTADORA),
			new ItemStack(Items.COMPARATOR))));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.paso9.marco"), INK_SOFT));

		// 10. And what the whole thing is for at the top end.
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("gui.forja.libro.fundicion.blanca")));
		body.add(new Text(Component.translatable("gui.forja.libro.fundicion.blanca.desc"), INK));
		body.add(new IconRow(dev.forja.forge.Alloys.ALL.stream()
			.filter(recipe -> dev.forja.forge.Alloys.WHITE_HEAT_ONLY.contains(recipe.id()))
			.map(dev.forja.forge.Alloys.Recipe::result).toList()));
		return body;
	}

	/** What makes each heat fluid: the items a boiler or a depot turns into it. */
	private static final java.util.Map<dev.forja.forge.HeatFluid, List<net.minecraft.world.item.Item>> FLUID_INPUTS = java.util.Map.of(
		dev.forja.forge.HeatFluid.VAPOR, List.of(Items.WATER_BUCKET),
		dev.forja.forge.HeatFluid.LAVA, List.of(Items.LAVA_BUCKET, Items.MAGMA_BLOCK),
		dev.forja.forge.HeatFluid.SANGRE_DE_BLAZE, List.of(Items.BLAZE_ROD, Items.BLAZE_POWDER),
		dev.forja.forge.HeatFluid.ALIENTO_DE_FORJA, List.of(),
		dev.forja.forge.HeatFluid.ALIENTO_DE_DRAGON, List.of(Items.DRAGON_BREATH),
		dev.forja.forge.HeatFluid.SALMUERA_HELADA, List.of(Items.PACKED_ICE, Items.BLUE_ICE));

	private List<ItemStack> fluidInputs(dev.forja.forge.HeatFluid fluid) {
		List<ItemStack> stacks = new ArrayList<>();
		if (fluid == dev.forja.forge.HeatFluid.ALIENTO_DE_FORJA) {
			// The mod's own two, which do not exist yet when the map above is built.
			stacks.add(new ItemStack(ModItems.ESCORIA));
			stacks.add(new ItemStack(ModItems.CORAZON_DE_FORJA));
		}
		for (net.minecraft.world.item.Item item : FLUID_INPUTS.get(fluid)) {
			stacks.add(new ItemStack(item));
		}
		return stacks;
	}

	/** How much of the fluid one of this item makes, read off the code, in mB. */
	private static int yieldOf(net.minecraft.world.item.Item item) {
		for (dev.forja.forge.HeatFluid.Vessel vessel : dev.forja.forge.HeatFluid.Vessel.values()) {
			dev.forja.forge.HeatFluid.Yield yield = dev.forja.forge.HeatFluid.yield(new ItemStack(item), vessel);
			if (yield != null) {
				return yield.amount();
			}
		}
		return 0;
	}

	/** One fluid's line in the table: where it comes from, what it costs and what it does, every number from the code. */
	private Component fluidLine(dev.forja.forge.HeatFluid fluid) {
		String key = "gui.forja.libro.fundicion.fluido." + fluid.id();
		int hot = dev.forja.block.entity.CastingTableBlockEntity.HOT;
		return switch (fluid) {
			case VAPOR -> Component.translatable(key, yieldOf(Items.WATER_BUCKET), fluid.draw, fluid.meltsUpTo, fluid.tableCap * 100 / hot);
			case LAVA -> Component.translatable(key, yieldOf(Items.LAVA_BUCKET), yieldOf(Items.MAGMA_BLOCK), fluid.draw);
			case SANGRE_DE_BLAZE -> Component.translatable(key, yieldOf(Items.BLAZE_ROD), yieldOf(Items.BLAZE_POWDER), fluid.draw,
				fluid.meltPercent, fluid.tableWarms);
			case ALIENTO_DE_FORJA -> Component.translatable(key, yieldOf(ModItems.ESCORIA), yieldOf(ModItems.CORAZON_DE_FORJA),
				fluid.draw, Math.round(fluid.steadyBonus * 100));
			case ALIENTO_DE_DRAGON -> Component.translatable(key, yieldOf(Items.DRAGON_BREATH), fluid.draw);
			case SALMUERA_HELADA -> Component.translatable(key, yieldOf(Items.PACKED_ICE), yieldOf(Items.BLUE_ICE),
				dev.forja.forge.HeatFluid.QUENCH_COST);
		};
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
		return this.weaponsChapter(true);
	}

	/** The weapons compared, frenzy, bleeding, the special moves, throwing and the lance; the parry only for the tome. */
	private List<Element> weaponsChapter(boolean parry) {
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
		if (parry) {
			body.add(new SubHeader(Component.translatable("gui.forja.libro.parada.titulo")));
			body.add(new Text(Component.translatable("gui.forja.libro.parada"), INK));
		}
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
	 * Maná y estamina: the blue bar the staff and the tome spend, what it costs and how it comes back, what
	 * kills give both bars, and every upgrade that grows either. Every number is read from the code, so the
	 * page cannot go on saying eight after the bolt has been made to cost ten.
	 */
	private List<Element> manaChapter() {
		dev.forja.combat.CombatConfig cfg = dev.forja.combat.CombatConfig.get();
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.mana.intro"), INK));
		body.add(new IconRow(List.of(
			Assembler.create(ForgeType.BACULO, List.of(ForgeMaterial.AMATISTA, ForgeMaterial.HIERRO, ForgeMaterial.MADERA)),
			Assembler.create(ForgeType.GRIMORIO, List.of(ForgeMaterial.AMATISTA, ForgeMaterial.HIERRO, ForgeMaterial.ORO)),
			new ItemStack(Items.LAPIS_LAZULI))));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.mana.costes.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.mana.costes", number(cfg.manaMax), number(cfg.manaBoltCost),
			dev.forja.magic.Spellcasting.BOLT_COOLDOWN, dev.forja.magic.Spellcasting.MONSTER_BOLT_COOLDOWN, number(cfg.manaTomeCost),
			dev.forja.magic.Spellcasting.TOME_COOLDOWN, dev.forja.magic.Spellcasting.MONSTER_TOME_COOLDOWN,
			Math.round(cfg.manaChargeExtra * 100), Math.round(dev.forja.magic.Spellcasting.CHARGE_BONUS * 100)), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.mana.vacio"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.mana.vuelve.titulo")));
		body.add(new Text(Component.translatable("gui.forja.libro.mana.vuelve", number(cfg.manaRegenPerTick * 20.0F),
			number(cfg.manaIdleDelayTicks / 20.0F), number(cfg.manaIdleRegenPerTick * 20.0F),
			Math.round(cfg.manaMax / Math.max(1.0E-4F, cfg.manaIdleRegenPerTick * 20.0F) / 60.0F),
			number(1.0F + dev.forja.clase.PlayerClass.MAGO.base(dev.forja.clase.ClassStat.MANA_REGEN)),
			number(1.0F + dev.forja.clase.PlayerClass.CURANDERO.base(dev.forja.clase.ClassStat.MANA_REGEN))), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.mana.muertes", Math.round(cfg.killFlowShare * 100), cfg.killFlowEveryTicks,
			number(cfg.killManaBase), number(cfg.killManaPerHealth), Math.round(cfg.killManaCapShare * 100),
			number(cfg.killStaminaBase), number(cfg.killStaminaPerHealth), Math.round(cfg.killStaminaCapShare * 100)), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.mana.muerte_propia"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.mana.mejoras")));
		for (Upgrade upgrade : List.of(Upgrade.CONCENTRACION, Upgrade.SIFON, Upgrade.DESCARGA, Upgrade.MEDITACION, Upgrade.RESERVA, Upgrade.FLUJO,
			Upgrade.FILO_ARCANO, Upgrade.ESTALLIDO_ARCANO, Upgrade.PASO_ARCANO)) {
			this.manaUpgrade(body, upgrade);
		}
		body.add(new Text(Component.translatable("gui.forja.libro.mana.conjuntos", number(dev.forja.magic.Mana.AMETHYST_SET_MANA),
			Math.round(dev.forja.magic.Mana.ECHO_SET_REGEN * 100)), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.mana.filo"), INK_SOFT));
		body.add(new SubHeader(Component.translatable("gui.forja.libro.estamina.mejoras")));
		for (Upgrade upgrade : List.of(Upgrade.AGUANTE, Upgrade.FUELLE, Upgrade.QUIEBRO, Upgrade.IMPULSO, Upgrade.SOLTURA)) {
			this.manaUpgrade(body, upgrade);
		}
		body.add(new Text(Component.translatable("gui.forja.libro.mana.monstruos"), INK_SOFT));
		return body;
	}

	/** One upgrade of the mana chapter: its name and recipe, where it goes and what it does at a hundred. */
	private void manaUpgrade(List<Element> body, Upgrade upgrade) {
		body.add(new UpgradeEntry(upgrade));
		body.add(new Text(Component.translatable("gui.forja.libro.mana.linea",
			Component.translatable("gui.forja.guia.para." + GuideText.category(upgrade)), upgrade.effect(100)), INK_SOFT));
	}

	private static String number(float value) {
		return value == Math.floor(value) ? String.valueOf((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
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
			dev.forja.forge.Potential.PER_WEAPON_PACT, dev.forja.forge.Potential.PER_PACT, dev.forja.forge.Potential.ANNEAL), INK_SOFT));
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
		body.add(new Text(Component.translatable("gui.forja.libro.potencial.carga.libres", dev.forja.upgrade.Synergy.THRESHOLD,
			dev.forja.forge.Potential.WEAPON_PACT_WEIGHT), INK_SOFT));
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
			boolean known = this.pactKnown(pact);
			this.shadowed.add("pact:" + pact.name() + ":" + (known ? "seen" : "shadow"));
			// Sealed, it is a shadow: only the offering that opens it, which is the way in (Andy, 2026-09-30).
			body.add(new SubHeader(known ? pact.displayName() : Component.translatable("gui.forja.libros.pacto_sellado")));
			body.add(new Text(known ? pact.effect(100) : Component.translatable("gui.forja.libros.pacto_sellado.desc"), INK_SOFT));
			net.minecraft.world.item.Item offering = dev.forja.upgrade.Pacts.offering(pact);
			if (offering != null) {
				body.add(new IconRow(List.of(new ItemStack(offering))));
				body.add(new Text(Component.translatable("gui.forja.libro.pacto_ofrenda", new ItemStack(offering).getHoverName()), INK_SOFT));
			}
		}
		body.add(new Spacer(3));
		body.add(new Text(Component.translatable("gui.forja.libro.pactos_limite", dev.forja.upgrade.Pacts.MOST), INK));
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
			// The one event with something to build against it.
			if (event == dev.forja.world.WorldEvents.METEORITOS) {
				body.add(new IconRow(List.of(new ItemStack(ModItems.PARARRAYOS))));
				body.add(new Text(Component.translatable("gui.forja.libro.pararrayos",
					dev.forja.block.StarRodBlock.RADIUS, dev.forja.block.StarRodBlock.STRIKES), INK_SOFT));
			}
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
		// The badges over their heads (ThreatBadge), so the book says what the three metals mean.
		body.add(new Text(Component.translatable("gui.forja.libro.insignias"), INK));
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
		// No offering any more: the dead forge is the frame of the portal to his world (docs/HERRERO_DIMENSION.md).
		body.add(new IconRow(List.of(new ItemStack(ModItems.FRAGUA_APAGADA), new ItemStack(ModItems.PERLA_DE_ORICALCO),
			new ItemStack(ModItems.CORAZON_DE_FORJA))));
		body.add(new Text(Component.translatable("gui.forja.libro.herrero_caido"), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.herrero_caido_fases",
			dev.forja.entity.FallenSmith.healthFor(dev.forja.difficulty.Ladder.current()), dev.forja.entity.FallenSmith.embersFor(1)), INK_SOFT));
		body.add(this.smithStrength(INK_SOFT));
		body.add(this.smithFury(INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.herrero_caido_defensa"), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.herrero_caido_reclama",
			dev.forja.entity.FallenSmith.RECLAIM_WINDOW / 20, Math.round(dev.forja.entity.FallenSmith.RECLAIM_HEAVY_DAMAGE),
			Math.round(dev.forja.entity.FallenSmith.RECLAIM_RADIUS),
			dev.forja.entity.FallenSmith.RECLAIM_COOLDOWN / 20), INK_SOFT));
		return body;
	}

	/**
	 * What the mod puts in the world to fight, with what each one leaves behind. Short on purpose: the chapters on
	 * the enemies say how they behave, this one says what they are and what they are worth.
	 *
	 * <p>In book II it writes itself (docs/LIBROS_GUIA.md, Andy's answer 7): a creature the reader has not seen yet is
	 * a shadow and a question mark, and its page fills in the first time it is in sight (client/CreatureSightings).
	 * The tome, for creative, shows them all, and keeps the barrow, the camp and the Smith, which live in books VI
	 * and VII.
	 */
	private List<Element> bestiaryChapter() {
		boolean shadows = this.book == GuideBooks.Book.COMBATE;
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable(shadows ? "gui.forja.libros.bestiario.sombras" : "gui.forja.libro.bestiario_intro"), INK_SOFT));

		this.creature(body, shadows, "automata_de_forja", level -> new dev.forja.entity.ForgeAutomaton(dev.forja.registry.ModEntities.AUTOMATA, level),
			new IconRow(List.of(
				Assembler.createPart(PartType.CABEZA_MARTILLO, ForgeMaterial.HIERRO),
				Assembler.createPart(PartType.BOLA, ForgeMaterial.PIEDRA),
				new ItemStack(Items.IRON_NUGGET)
			)),
			new Text(Component.translatable("gui.forja.libro.bestiario.automata", Math.round(dev.forja.entity.ForgeAutomaton.HEALTH)), INK),
			new Text(Component.translatable("gui.forja.libro.ataques.automata",
				Math.round(dev.forja.entity.ForgeAutomaton.STEAM_DAMAGE)), INK_SOFT));
		this.creature(body, shadows, "coraza_vacia", level -> new dev.forja.entity.HollowArmor(dev.forja.registry.ModEntities.CORAZA, level),
			new IconRow(List.of(
				Assembler.createPart(PartType.PLACA_PECHERA, ForgeMaterial.HIERRO),
				Assembler.createPart(PartType.PLACA_CASCO, ForgeMaterial.HIERRO),
				dev.forja.item.UpgradeOrbItem.create(Upgrade.PROTECCION, 25)
			)),
			new Text(Component.translatable("gui.forja.libro.bestiario.coraza",
				Math.round(dev.forja.entity.HollowArmor.HEALTH),
				Math.round(dev.forja.entity.HollowArmor.UNFORGED_SHARE * 100)), INK),
			new Text(Component.translatable("gui.forja.libro.coraza_alma",
				Math.round(dev.forja.entity.HollowArmor.SOUL_REACH),
				Math.round(dev.forja.entity.HollowArmor.SOUL_HEAL),
				dev.forja.entity.HollowArmor.SOUL_RAGE / 20), INK),
			new Text(Component.translatable("gui.forja.libro.ataques.coraza",
				Math.round(dev.forja.entity.HollowArmor.DASH_DAMAGE)), INK_SOFT),
			new Text(Component.translatable("gui.forja.libro.coraza_visita",
				Math.round(dev.forja.ForjaConfig.get().corazas * 100)), INK_SOFT));
		this.creature(body, shadows, "pavesa", level -> new dev.forja.entity.EmberWisp(dev.forja.registry.ModEntities.PAVESA, level),
			new IconRow(List.of(
				new ItemStack(Items.COAL), new ItemStack(Items.BLAZE_POWDER), new ItemStack(ModItems.HUEVO_PAVESA)
			)),
			new Text(Component.translatable("gui.forja.libro.bestiario.pavesa",
				Math.round(dev.forja.entity.EmberWisp.HEALTH),
				Math.round(dev.forja.entity.EmberWisp.DIVE_DAMAGE),
				Math.round(dev.forja.entity.EmberWisp.FLARE_DAMAGE)), INK),
			new Text(Component.translatable("gui.forja.libro.farol"), INK),
			new IconRow(List.of(new ItemStack(Items.LANTERN), new ItemStack(ModItems.FAROL_DE_PAVESA))),
			new Text(Component.translatable("gui.forja.libro.farol_precio"), INK_SOFT),
			new Text(Component.translatable("gui.forja.libro.pavesa_visita",
				Math.round(dev.forja.ForjaConfig.get().pavesas * 100), dev.forja.world.WispWatch.limit()), INK_SOFT));
		// The raiders' captain is a pillager with a name, not a creature of its own: no portrait.
		this.creature(body, shadows, "capitan_saqueador", null,
			new IconRow(List.of(
				dev.forja.item.UpgradeOrbItem.create(Upgrade.FILO, 50),
				dev.forja.item.UpgradeOrbItem.create(Upgrade.PROTECCION, 75)
			)),
			new Text(Component.translatable("gui.forja.libro.bestiario.capitan", dev.forja.world.ForgeRaiders.BAND), INK),
			new Text(Component.translatable("gui.forja.libro.ataques.capitan",
				dev.forja.world.ForgeRaiders.RALLY_TICKS / 20), INK_SOFT));
		if (!shadows) {
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
			this.creature(body, false, "herrero_caido", level -> new dev.forja.entity.FallenSmith(dev.forja.registry.ModEntities.HERRERO_CAIDO, level),
				new IconRow(List.of(
					new ItemStack(ModItems.CORAZON_DE_FORJA), new ItemStack(ModItems.YUNQUE_DEL_HERRERO), new ItemStack(ModItems.FRAGUA_APAGADA)
				)),
				new Text(Component.translatable("gui.forja.libro.bestiario.herrero",
					dev.forja.entity.FallenSmith.healthFor(dev.forja.difficulty.Ladder.current())), INK),
				new Text(Component.translatable("gui.forja.libro.ataques.herrero",
					Math.round(dev.forja.entity.FallenSmith.WAVE_DAMAGE)), INK_SOFT),
				new Text(Component.translatable("gui.forja.libro.yunque"), INK_SOFT));
		}
		// The eleven that came after the book was written. They were in the world for days with no page:
		// the only way to learn that the Herrumbre eats armour, or that the Nucleo is better left
		// alone, was to find out. One page each — what it is, what it costs you, and the way round it.
		this.creature(body, shadows, "herrumbre", level -> new dev.forja.entity.RustSwarm(dev.forja.registry.ModEntities.HERRUMBRE, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.herrumbre", Math.round(dev.forja.entity.RustSwarm.HEALTH)), INK));
		this.creature(body, shadows, "ascua_mayor", level -> new dev.forja.entity.GreaterEmber(dev.forja.registry.ModEntities.ASCUA_MAYOR, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.ascua_mayor", Math.round(dev.forja.entity.GreaterEmber.HEALTH)), INK));
		this.creature(body, shadows, "escoria_viviente", level -> new dev.forja.entity.LivingSlag(dev.forja.registry.ModEntities.ESCORIA, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.escoria_viviente"), INK));
		this.creature(body, shadows, "yunque_andante", level -> new dev.forja.entity.WalkingAnvil(dev.forja.registry.ModEntities.YUNQUE_ANDANTE, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.yunque_andante", Math.round(dev.forja.entity.WalkingAnvil.HEALTH)), INK));
		this.creature(body, shadows, "percutor", level -> new dev.forja.entity.Striker(dev.forja.registry.ModEntities.PERCUTOR, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.percutor", Math.round(dev.forja.entity.Striker.HEALTH)), INK));
		this.creature(body, shadows, "tenaza", level -> new dev.forja.entity.Tongs(dev.forja.registry.ModEntities.TENAZA, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.tenaza", Math.round(dev.forja.entity.Tongs.HEALTH)), INK));
		this.creature(body, shadows, "cargador_de_carbon", level -> new dev.forja.entity.CoalHauler(dev.forja.registry.ModEntities.CARGADOR_DE_CARBON, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.cargador_de_carbon", Math.round(dev.forja.entity.CoalHauler.HEALTH)), INK));
		this.creature(body, shadows, "templador", level -> new dev.forja.entity.Quencher(dev.forja.registry.ModEntities.TEMPLADOR, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.templador", Math.round(dev.forja.entity.Quencher.HEALTH)), INK));
		this.creature(body, shadows, "nucleo_estelar", level -> new dev.forja.entity.StarCore(dev.forja.registry.ModEntities.NUCLEO_ESTELAR, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.nucleo_estelar", Math.round(dev.forja.entity.StarCore.HEALTH)), INK));
		this.creature(body, shadows, "molde_roto", level -> new dev.forja.entity.BrokenMould(dev.forja.registry.ModEntities.MOLDE_ROTO, level),
			new Text(Component.translatable("gui.forja.libro.bestiario.molde_roto", Math.round(dev.forja.entity.BrokenMould.HEALTH)), INK));
		// The Guardian lives in the forge castle, and his page goes with it into book VI.
		if (!shadows) {
			this.creature(body, false, "guardian_de_cuno", level -> new dev.forja.entity.CuneGuardian(dev.forja.registry.ModEntities.GUARDIAN_DE_CUNO, level),
				new Text(Component.translatable("gui.forja.libro.bestiario.guardian_de_cuno", Math.round(dev.forja.entity.CuneGuardian.HEALTH)), INK));
		}
		return body;
	}

	/** Every creature page of this book as "id:seen" or "id:shadow", for the client test. */
	private final List<String> creatures = new ArrayList<>();

	public List<String> creaturePages() {
		if (this.pages.isEmpty()) {
			this.build();
		}
		return List.copyOf(this.creatures);
	}

	/**
	 * One creature's page: its name, its portrait and what it is, when it has been seen (or the book does not keep
	 * shadows); a shadow, three question marks and nothing else when not. A creature seen but whose page the
	 * reader has not looked at yet carries a "nuevo" mark.
	 */
	private void creature(List<Element> body, boolean shadows, String id,
		java.util.function.@Nullable Function<net.minecraft.world.level.Level, net.minecraft.world.entity.LivingEntity> maker, Element... details) {
		if (this.creatures.size() > 0 || body.size() > 1) {
			body.add(new Divider());
		}
		String key = dev.forja.Forja.MOD_ID + ":" + id;
		boolean seen = !shadows || BookMemory.hasSeen(key);
		this.creatures.add(id + ":" + (seen ? "seen" : "shadow"));
		if (!seen) {
			body.add(new SubHeader(Component.translatable("gui.forja.libros.bestiario.oculto")));
			body.add(new CreatureShadow());
			body.add(new Text(Component.translatable("gui.forja.libros.bestiario.sin_ver"), INK_SOFT));
			return;
		}
		boolean fresh = shadows && !BookMemory.hasRead(key);
		body.add(new SubHeader(fresh
			? Component.translatable("entity.forja." + id).copy().append(Component.translatable("gui.forja.libros.nuevo").withColor(0xB02020))
			: Component.translatable("entity.forja." + id)));
		if (maker != null) {
			body.add(new Portrait(maker));
		}
		if (shadows) {
			body.add(new CreatureRead(key));
		}
		body.addAll(List.of(details));
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

	/**
	 * The classes (docs/CLASES.md, docs/ARBOLES.md): how to take one, each of the six with its numbers and its three skills,
	 * what changing costs, and the healing lantern. Every number is read from the enums the game plays by.
	 */
	private List<Element> classesChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.clases.intro", dev.forja.clase.ClassProgress.key(dev.forja.clase.ClassProgress.KEY_TREE),
			dev.forja.clase.ClassProgress.key(dev.forja.clase.ClassProgress.KEY_SKILL_1),
			dev.forja.clase.ClassProgress.key(dev.forja.clase.ClassProgress.KEY_SKILL_2),
			dev.forja.clase.ClassProgress.key(dev.forja.clase.ClassProgress.KEY_SKILL_3)), INK));
		body.add(new ClassButton());
		// Every number from the tree's file (clase/ClassTree): the cap, the points, the milestones, the curve.
		int levelPoints = dev.forja.clase.ClassTree.levelPoints(dev.forja.clase.ClassTree.maxLevel());
		int milestonePoints = dev.forja.clase.ClassTree.allMilestonePoints();
		// What one can own: everything but the two ultimates not chosen (docs/ARBOLES.md).
		int treeCost = dev.forja.clase.PlayerClass.GUERRERO.tree().ownableCost();
		body.add(new Text(Component.translatable("gui.forja.libro.clases.niveles", dev.forja.clase.ClassTree.maxLevel(), levelPoints,
			milestonePoints, dev.forja.clase.ClassTree.milestoneCapPerLevel(), levelPoints + milestonePoints, treeCost,
			Math.round(100.0F * (levelPoints + milestonePoints) / treeCost), dev.forja.clase.ClassTree.firstStep(),
			dev.forja.clase.ClassTree.stepGrowth()), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.clases.arbol", dev.forja.clase.ClassTree.cost("menor"),
			dev.forja.clase.ClassTree.cost("clave"), dev.forja.clase.ClassTree.cost("puente")), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.clases.ultimas", dev.forja.clase.ClassProgress.key(dev.forja.clase.ClassProgress.KEY_SKILL_3),
			dev.forja.item.OblivionCandleItem.POINTS), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.clases.experiencia"), INK_SOFT));
		for (dev.forja.clase.PlayerClass clazz : dev.forja.clase.PlayerClass.values()) {
			body.add(new Divider());
			body.add(new SubHeader(clazz.displayName()));
			List<dev.forja.clase.ActiveSkill> ultimates = clazz.ultimates();
			body.add(new IconRow(List.of(clazz.icon(), clazz.firstSkill.icon(), clazz.secondSkill().icon(), ultimates.get(0).icon(),
				ultimates.get(1).icon(), ultimates.get(2).icon())));
			body.add(new Text(clazz.description(), INK));
			net.minecraft.network.chat.MutableComponent numbers = Component.empty();
			for (int i = 0; i < clazz.base.size(); i++) {
				if (i > 0) {
					numbers.append(" · ");
				}
				numbers.append(clazz.base.get(i).stat().plain(clazz.base.get(i).value()));
			}
			body.add(new Text(numbers, INK_SOFT));
			// Andy's damage factors (clase/ClassDamage), multiplied on top of everything above.
			body.add(new Text(Component.translatable("gui.forja.libro.clases.dano",
				dev.forja.clase.ClassDamage.format(clazz.damageFactor(dev.forja.clase.ClassDamage.Blow.MELEE)),
				dev.forja.clase.ClassDamage.format(clazz.damageFactor(dev.forja.clase.ClassDamage.Blow.PROJECTILE)),
				dev.forja.clase.ClassDamage.format(clazz.damageFactor(dev.forja.clase.ClassDamage.Blow.MAGIC))), INK_SOFT));
			body.add(new Text(Component.translatable("gui.forja.libro.clases.habilidades", clazz.firstSkill.displayName(),
				dev.forja.clase.ClassProgress.key(dev.forja.clase.ClassProgress.KEY_SKILL_1), clazz.secondSkill().displayName(),
				dev.forja.clase.ClassProgress.key(dev.forja.clase.ClassProgress.KEY_SKILL_2), ultimates.get(0).displayName(),
				ultimates.get(1).displayName(), ultimates.get(2).displayName(),
				dev.forja.clase.ClassProgress.key(dev.forja.clase.ClassProgress.KEY_SKILL_3)), INK_SOFT));
		}
		body.add(new Divider());
		// The Medallón del olvido is forged, not crafted (forge/Relic): its three parts, then the medallion.
		dev.forja.forge.Relic medallion = dev.forja.forge.Relic.MEDALLON_DEL_OLVIDO;
		body.add(new SubHeader(medallion.displayName()));
		List<ItemStack> medallionRow = new ArrayList<>();
		for (int slot = 0; slot < medallion.slots.size(); slot++) {
			medallionRow.add(Assembler.createPart(medallion.slots.get(slot), medallion.defaultMaterials().get(slot)));
		}
		medallionRow.add(medallion.create(medallion.defaultMaterials()));
		body.add(new IconRow(medallionRow));
		body.add(new Text(Component.translatable("gui.forja.libro.clases.medallon", medallion.core.displayName()), INK_SOFT));
		body.add(new Text(Component.translatable("gui.forja.libro.clases.cambio"), INK));
		// The cheap way back from a node or two (docs/ARBOLES.md, "Reiniciar").
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("item.forja.vela_del_olvido")));
		body.add(new Crafting(new Item[] {Items.CANDLE, Items.AMETHYST_SHARD, Items.AMETHYST_SHARD, Items.GHAST_TEAR, null, null, null, null, null},
			new ItemStack(ModItems.VELA_DEL_OLVIDO)));
		body.add(new Text(Component.translatable("gui.forja.libro.clases.vela", dev.forja.item.OblivionCandleItem.POINTS), INK_SOFT));
		body.add(new Divider());
		body.add(new SubHeader(Component.translatable("item.forja.farol")));
		body.add(new IconRow(List.of(
			Assembler.create(ForgeType.FAROL, List.of(ForgeMaterial.ESMERALDA, ForgeMaterial.ORO, ForgeMaterial.MADERA)),
			Assembler.create(ForgeType.FAROL, List.of(ForgeMaterial.DIAMANTE, ForgeMaterial.HIERRO, ForgeMaterial.HUESO)),
			Assembler.create(ForgeType.FAROL, List.of(ForgeMaterial.AMATISTA, ForgeMaterial.COBRE, ForgeMaterial.MADERA)))));
		body.add(new Text(Component.translatable("gui.forja.libro.clases.farol", Math.round(dev.forja.magic.Healing.BEAM_REACH),
			Math.round(dev.forja.magic.Healing.RING_REACH), Math.round(dev.forja.magic.Healing.RING_GROWTH),
			Math.round(dev.forja.magic.Healing.SELF_SHARE * 100)), INK));
		body.add(new Text(Component.translatable("gui.forja.libro.farol.curandero"), INK_SOFT));
		return body;
	}

	/** The upgrade pairs that do something extra together. */
	private List<Element> synergiesChapter() {
		List<Element> body = new ArrayList<>();
		body.add(new Text(Component.translatable("gui.forja.libro.sinergias_intro", dev.forja.upgrade.Synergy.THRESHOLD,
			dev.forja.upgrade.Synergy.MOST), INK_SOFT));
		int sleeping = 0;
		for (dev.forja.upgrade.Synergy synergy : dev.forja.upgrade.Synergy.values()) {
			boolean known = this.synergyKnown(synergy);
			this.shadowed.add("synergy:" + synergy.name() + ":" + (known ? "seen" : "shadow"));
			if (!known) {
				// All the ones not woken yet go together at the end, as a count: a page of question marks each
				// would be pages of nothing.
				sleeping++;
				continue;
			}
			body.add(new Spacer(2));
			body.add(new SubHeader(synergy.displayName()));
			body.add(new Text(Component.translatable("gui.forja.libro.sinergia_par", synergy.first.displayName(), synergy.second.displayName()), INK_SOFT));
			body.add(new Text(synergy.description(), INK));
		}
		if (sleeping > 0) {
			body.add(new Divider());
			body.add(new SubHeader(Component.translatable("gui.forja.libros.sinergias_dormidas", sleeping)));
			body.add(new Text(Component.translatable("gui.forja.libros.sinergia_dormida.desc"), INK_SOFT));
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
		// The second bench, which the path ends on and the chapter never showed.
		body.add(new Text(Component.translatable("gui.forja.libro.mesa_mayor", dev.forja.menu.Station.FORJA_MAYOR.capacity()), INK));
		Item stone = Items.POLISHED_BLACKSTONE;
		Item damascus = ModItems.alloy("damasco");
		body.add(new Crafting(new Item[] {stone, Items.GOLD_INGOT, stone, damascus, ModItems.MESA_DE_FORJA, damascus, stone, stone, stone},
			new ItemStack(ModItems.MESA_DE_FORJA_MAYOR)));
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
		for (int section = 0; section < this.book.sections.size(); section++) {
			boolean lit = section == open || section == pointed;
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BOOK, this.tabX(), this.tabY(section),
				this.sectionColour(section) * 24.0F, lit ? 234.0F : 212.0F, lit ? 22 : 18, TAB_H, 512, 256);
		}
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BOOK, left - OVERHANG, top - OVERHANG,
			0.0F, 0.0F, BOOK_W + OVERHANG * 2, BOOK_H + OVERHANG * 2, 512, 256);
		// The ribbon, out of the foot of the spine, in the gap between the two buttons.
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BOOK, left + 4 + PAGE_W + 1, top + BOOK_H + OVERHANG - 2,
			130.0F, 212.0F, 4, 24, 512, 256);
		for (int section = 0; section < this.book.sections.size(); section++) {
			ItemStack icon = chapterIcon(this.book.sections.get(section).chapters().get(0));
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
			BookMemory.saw(this.book, page, this.pages.size());
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
			int section = this.sectionColour(this.sectionOf(keys.get(i)));
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
		if (this.picking) {
			this.drawPicker(g, mouseX, mouseY);
			return;
		}
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
		if (this.picking) {
			return this.pickAt(event.x(), event.y());
		}
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
		if (this.picking) {
			return true;
		}
		if (scrollY != 0.0) {
			this.turn(scrollY < 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.picking && event.key() == 256) {
			// Escape closes the picker, not the book.
			this.picking = false;
			return true;
		}
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

	/** One heat fluid's name and heat, on a square of its own colour: the head of its line in the table. */
	private static final class FluidSwatch extends Element {
		private final dev.forja.forge.HeatFluid fluid;

		FluidSwatch(dev.forja.forge.HeatFluid fluid) {
			this.fluid = fluid;
		}

		@Override
		int height() {
			return 12;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			g.fill(x, y + 1, x + 9, y + 10, 0xFF3A302A);
			g.fill(x + 1, y + 2, x + 8, y + 9, 0xFF000000 | this.fluid.colour);
			Component line = Component.translatable("gui.forja.libro.fundicion.fluido_titulo",
				this.fluid.displayName(), this.fluid.heat.displayName());
			g.text(screen.font, line, x + 13, y + 2, INK, false);
		}
	}

	/** Not drawn: the next element starts a new page (see flow). */
	private static final class PageBreak extends Element {
		@Override
		int height() {
			return 0;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
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

	/**
	 * The book's name on its cover, a third larger than the text, on as many lines as it takes: one title per
	 * book now, and "El Cementerio entre Estrellas" is half as wide again as the page at that size.
	 */
	private static final class Title extends Element {
		private static final float SCALE = 1.3F;
		private final List<FormattedCharSequence> lines;

		Title(Font font, Component text) {
			this.lines = font.split(text, (int) Math.floor(CONTENT_W / SCALE));
		}

		@Override
		int widest(Font font) {
			int widest = 0;
			for (FormattedCharSequence line : this.lines) {
				widest = Math.max(widest, Math.round(font.width(line) * SCALE));
			}
			return widest;
		}

		@Override
		int height() {
			return 22 + (this.lines.size() - 1) * 12;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			for (int i = 0; i < this.lines.size(); i++) {
				int width = Math.round(screen.font.width(this.lines.get(i)) * SCALE);
				g.pose().pushMatrix();
				g.pose().translate(x + (CONTENT_W - width) / 2.0F, y + 3 + i * 12);
				g.pose().scale(SCALE, SCALE);
				g.text(screen.font, this.lines.get(i), 0, 0, 0xFF6B2A0E, false);
				g.pose().popMatrix();
			}
		}
	}

	private static final class Header extends Element {
		/** Room for the name between the two studs at the ends of the banner. */
		private static final int ROOM = CONTENT_W - 22;

		/**
		 * The chapter's name is written smaller when it does not fit, as the section titles are. "El Cementerio
		 * entre Estrellas" was 149 pixels on a page of 140 and ran off the banner, and the client test said so.
		 */
		private float scale(Font font) {
			int width = font.width(this.text);
			return width <= ROOM ? 1.0F : ROOM / (float) width;
		}

		@Override
		int widest(Font font) {
			return Math.round(font.width(this.text) * this.scale(font));
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
			float scale = this.scale(screen.font);
			float width = screen.font.width(this.text) * scale;
			g.pose().pushMatrix();
			g.pose().translate(x + (CONTENT_W - width) / 2.0F, y + 3 + (1.0F - scale) * 4.0F);
			g.pose().scale(scale, scale);
			g.text(screen.font, this.text, 0, 0, INK, false);
			g.pose().popMatrix();
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

		private Text(List<FormattedCharSequence> lines, int color) {
			this.lines = lines;
			this.color = color;
		}

		/** This paragraph cut into pieces no taller than a page, for one too long for any page. */
		List<Element> pieces(int room) {
			int each = Math.max(1, (room - 3) / 8);
			List<Element> pieces = new ArrayList<>();
			for (int from = 0; from < this.lines.size(); from += each) {
				pieces.add(new Text(this.lines.subList(from, Math.min(from + each, this.lines.size())), this.color));
			}
			return pieces;
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
				float scale = fitScale(screen.font, this.chapter.name, room, 0.8F);
				g.pose().pushMatrix();
				g.pose().translate(x + 14, y + 3 + (1.0F - scale) * 4.0F);
				g.pose().scale(scale, scale);
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

		/** A grid of stacks rather than items, for a recipe whose inputs carry data (a worn piece, say). */
		Crafting(ItemStack[] stacks, ItemStack result) {
			for (int i = 0; i < 9; i++) {
				this.grid[i] = i < stacks.length && stacks[i] != null ? stacks[i] : ItemStack.EMPTY;
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
		/** A pact the reader has not opened: its name, recipe and tooltip stay hidden. */
		private final boolean sealed;

		UpgradeEntry(Upgrade upgrade) {
			this.upgrade = upgrade;
			this.sealed = upgrade.isPact() && !GuideBookScreen.this.pactKnown(upgrade);
			if (upgrade.isPact()) {
				GuideBookScreen.this.shadowed.add("pact:" + upgrade.name() + ":" + (this.sealed ? "shadow" : "seen"));
			}
		}

		@Override
		int height() {
			return 23;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			if (this.sealed) {
				screen.small(g, Component.translatable("gui.forja.libros.pacto_sellado"), x, y + 1, INK_SOFT);
				screen.small(g, Component.translatable("gui.forja.libros.pacto_sellado.corto"), x, y + 12, INK_SOFT);
				return;
			}
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
			return this.sealed ? null : GuideText.upgradeTooltip(this.upgrade);
		}
	}

	// ------------------------------------------------------------------ the smith's path

	/** The scale a line is written at so it fits its room: the one asked for, or smaller, never cut. */
	private static float fitScale(Font font, Component text, int room, float scale) {
		int width = font.width(text);
		return width * scale <= room ? scale : room / (float) width;
	}

	/** Every step of the path as done or not, in order, for the pips and the list. */
	private boolean[] stepsDone() {
		boolean[] done = new boolean[ForjaPath.STEPS.size()];
		for (ForjaPath.Step step : ForjaPath.STEPS) {
			done[step.ordinal()] = this.pathDone.contains(step.advancement());
		}
		return done;
	}

	/**
	 * The sign under the title on the cover, for a reader who has not walked the path yet: the step to do
	 * next and the page it is on. A click goes there.
	 */
	private final class PathSign extends Element {
		private static final int ROOM = CONTENT_W - 18;

		private final ItemStack icon;
		private final Component title;

		PathSign(ForjaPath.Step step) {
			this.icon = step.icon();
			this.title = step.title();
		}

		private Component where() {
			return Component.translatable("gui.forja.camino.portada", GuideBookScreen.this.chapterPage("siguiente_paso") + 1);
		}

		@Override
		int height() {
			return 22;
		}

		@Override
		int widest(Font font) {
			return 18 + Math.max(Math.round(font.width(this.where()) * SMALL),
				Math.round(font.width(this.title) * fitScale(font, this.title, ROOM, 1.0F)));
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			boolean hovered = over(mouseX, mouseY, x - 2, y, CONTENT_W + 4, 21);
			g.fill(x - 2, y, x + CONTENT_W + 2, y + 21, hovered ? PAPER_SHADE : BAND);
			g.fill(x - 2, y + 20, x + CONTENT_W + 2, y + 21, INK_SOFT);
			g.item(this.icon, x, y + 2);
			screen.small(g, this.where(), x + 18, y + 2, INK_SOFT);
			float scale = fitScale(screen.font, this.title, ROOM, 1.0F);
			g.pose().pushMatrix();
			g.pose().translate(x + 18, y + 10 + (1.0F - scale) * 4.0F);
			g.pose().scale(scale, scale);
			g.text(screen.font, this.title, 0, 0, 0xFF000000 | GuideText.RUBRIC, false);
			g.pose().popMatrix();
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			return over(mouseX, mouseY, x, y + 2, 16, 16) ? this.icon : null;
		}

		@Override
		boolean click(GuideBookScreen screen) {
			screen.jumpTo(screen.chapterPage("siguiente_paso"));
			return true;
		}
	}

	/**
	 * The head of the "Siguiente paso" page: the step's icon half as large again, which step it is of how
	 * many, one pip for every step of the path (filled for the ones already done), and under them the
	 * step's name across the whole width of the page, where it has room to be read.
	 */
	private final class StepCard extends Element {
		private static final int ROOM = CONTENT_W - 30;

		private final ForjaPath.@Nullable Step step;
		private final boolean[] done;
		private final ItemStack icon;
		private final Component count;
		private final Component title;

		StepCard(ForjaPath.@Nullable Step step, int doneCount) {
			this.step = step;
			this.done = GuideBookScreen.this.stepsDone();
			this.icon = step == null ? new ItemStack(ModItems.MESA_DE_FORJA_MAYOR) : step.icon();
			this.count = step == null
				? Component.translatable("gui.forja.camino.paso", doneCount, ForjaPath.STEPS.size())
				: Component.translatable("gui.forja.camino.paso", step.number(), ForjaPath.STEPS.size());
			this.title = step == null ? Component.translatable("gui.forja.camino.completo") : step.title();
		}

		@Override
		int height() {
			return 44;
		}

		@Override
		int widest(Font font) {
			return Math.max(30 + Math.round(font.width(this.count) * SMALL),
				Math.round(font.width(this.title) * fitScale(font, this.title, CONTENT_W, 1.0F)));
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			g.fill(x - 2, y + 1, x + CONTENT_W + 2, y + 42, PAPER_SHADE);
			g.fill(x - 2, y + 1, x + CONTENT_W + 2, y + 2, INK_SOFT);
			g.fill(x - 2, y + 41, x + CONTENT_W + 2, y + 42, INK_SOFT);
			g.pose().pushMatrix();
			g.pose().translate(x + 1, y + 4);
			g.pose().scale(1.5F, 1.5F);
			g.item(this.icon, 0, 0);
			g.pose().popMatrix();
			int textX = x + 30;
			screen.small(g, this.count, textX, y + 7, INK_SOFT);
			// The whole path at a glance: done in the workshop's own colour, the one to do in the rubric.
			int each = (ROOM + 2) / this.done.length;
			for (int i = 0; i < this.done.length; i++) {
				int colour = this.done[i] ? SECTION_COLOURS[0]
					: this.step != null && i == this.step.ordinal() ? 0xFF000000 | GuideText.RUBRIC
					: 0x50806848;
				g.fill(textX + i * each, y + 17, textX + (i + 1) * each - 2, y + 22, colour);
			}
			float scale = fitScale(screen.font, this.title, CONTENT_W, 1.0F);
			int width = Math.round(screen.font.width(this.title) * scale);
			g.pose().pushMatrix();
			g.pose().translate(x + (CONTENT_W - width) / 2.0F, y + 30 + (1.0F - scale) * 4.0F);
			g.pose().scale(scale, scale);
			g.text(screen.font, this.title, 0, 0, 0xFF000000 | GuideText.RUBRIC, false);
			g.pose().popMatrix();
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			return over(mouseX, mouseY, x + 1, y + 4, 24, 24) ? this.icon : null;
		}
	}

	/**
	 * "Read it in (chapter), p. N" behind an arrow: the way from a step to the chapter that explains it.
	 *
	 * <p>Laid out for a three-figure page number, because the real one is only known once the whole book
	 * has been put together, after this has already been given its place on a page.
	 */
	private final class ChapterLink extends Element {
		private final String chapter;
		private final Component name;
		private final int laidOut;
		/** The book the chapter is in, when it is not this one. */
		private final GuideBooks.@Nullable Book elsewhere;

		ChapterLink(String chapter) {
			this.chapter = chapter;
			this.name = Component.translatable("gui.forja.libro.cap." + chapter);
			this.elsewhere = GuideBookScreen.this.book.chapters().contains(chapter) ? null : GuideBooks.bookOf(chapter);
			this.laidOut = this.elsewhere != null ? this.elsewhereLines().size() : this.lines(999).size();
		}

		private List<FormattedCharSequence> lines(int page) {
			return GuideBookScreen.this.font.split(Component.translatable("gui.forja.camino.leer", this.name, page), WRAP - 12);
		}

		/** "Léelo en «capítulo», en «libro»", or, for a book still to be written, where it will be. */
		private List<FormattedCharSequence> elsewhereLines() {
			GuideBooks.Book book = this.elsewhere;
			String key = book != null && book.ready ? "gui.forja.camino.leer_libro" : "gui.forja.camino.leer_pronto";
			return GuideBookScreen.this.font.split(Component.translatable(key, this.name,
				book == null ? GuideBooks.Book.TOMO.title() : book.title()), WRAP - 12);
		}

		private List<FormattedCharSequence> shown() {
			return this.elsewhere != null ? this.elsewhereLines() : this.lines(GuideBookScreen.this.chapterPage(this.chapter) + 1);
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			GuideBooks.Book book = this.elsewhere;
			if (book == null || GuideBookScreen.this.canRead(book)) {
				return null;
			}
			return GuideBookScreen.this.whereToGet(book);
		}

		@Override
		int height() {
			return this.laidOut * 8 + 5;
		}

		@Override
		int widest(Font font) {
			int widest = 0;
			for (FormattedCharSequence line : this.shown()) {
				widest = Math.max(widest, Math.round(font.width(line) * SMALL));
			}
			return 9 + widest;
		}

		@Override
		boolean elided() {
			return this.shown().size() > this.laidOut;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			boolean hovered = over(mouseX, mouseY, x - 2, y, CONTENT_W + 4, this.height() - 1);
			if (hovered) {
				g.fill(x - 2, y, x + CONTENT_W + 2, y + this.height() - 1, PAPER_SHADE);
			}
			int ink = hovered ? 0xFF6B2A0E : 0xFF000000 | GuideText.RUBRIC;
			for (int i = 0; i < 3; i++) {
				g.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 7 - i, ink);
			}
			List<FormattedCharSequence> lines = this.shown();
			for (int i = 0; i < lines.size(); i++) {
				screen.small(g, lines.get(i), x + 9, y + 2 + i * 8, ink);
			}
		}

		@Override
		boolean click(GuideBookScreen screen) {
			return screen.openChapter(this.chapter);
		}
	}

	/**
	 * A button on the page: "Elegir clase" without one, "Ver tu árbol" with one. It opens the same screens as
	 * the K key (client/ClassClient), because the guide is where a new player looks first.
	 */
	private final class ClassButton extends Element {
		private Component label() {
			return Component.translatable(dev.forja.clase.ClassProgress.clazz(GuideBookScreen.this.minecraft.player) == null
				? "gui.forja.libro.clases.boton_elegir" : "gui.forja.libro.clases.boton_arbol");
		}

		@Override
		int height() {
			return 18;
		}

		@Override
		int widest(Font font) {
			return CONTENT_W - 10;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			boolean hovered = over(mouseX, mouseY, x + 5, y + 1, CONTENT_W - 10, 14);
			g.fill(x + 5, y + 1, x + CONTENT_W - 5, y + 15, hovered ? 0xFF8A5A2A : 0xFF6B4422);
			g.fill(x + 6, y + 2, x + CONTENT_W - 6, y + 3, 0xFFC8904A);
			g.fill(x + 5, y + 14, x + CONTENT_W - 5, y + 15, 0xFF3A2410);
			Component label = this.label();
			int width = Math.round(screen.font.width(label) * SMALL);
			screen.small(g, label, x + (CONTENT_W - width) / 2, y + 5, 0xFFFFF0D0);
		}

		@Override
		boolean click(GuideBookScreen screen) {
			ClassClient.openClassScreen(screen.minecraft);
			return true;
		}
	}

	/** One step in the list: whether it is done, what it is done with, its name, and a way to its chapter. */
	private final class PathRow extends Element {
		private static final int ROOM = CONTENT_W - 24;

		private final ForjaPath.Step step;
		private final boolean done;
		private final boolean current;
		private final ItemStack icon;
		private final Component title;
		private final Component description;

		PathRow(ForjaPath.Step step, boolean done, boolean current) {
			this.step = step;
			this.done = done;
			this.current = current;
			this.icon = step.icon();
			this.title = step.title();
			// The hover says the whole of it; a tooltip is dark, so without the rubric marks.
			this.description = Component.literal(step.description().getString().replace("**", ""));
		}

		@Override
		int height() {
			return 11;
		}

		@Override
		int widest(Font font) {
			return 22 + Math.round(font.width(this.title) * fitScale(font, this.title, ROOM, SMALL));
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			if (over(mouseX, mouseY, x - 2, y, CONTENT_W + 4, 11)) {
				g.fill(x - 2, y, x + CONTENT_W + 2, y + 11, PAPER_SHADE);
			}
			if (this.done) {
				// A filled box with a tick knocked out of it.
				g.fill(x + 1, y + 2, x + 8, y + 9, 0xFF5E7A34);
				g.fill(x + 2, y + 5, x + 3, y + 6, PAPER);
				g.fill(x + 3, y + 6, x + 4, y + 7, PAPER);
				g.fill(x + 4, y + 5, x + 5, y + 6, PAPER);
				g.fill(x + 5, y + 4, x + 6, y + 5, PAPER);
				g.fill(x + 6, y + 3, x + 7, y + 4, PAPER);
			} else if (this.current) {
				for (int i = 0; i < 4; i++) {
					g.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 9 - i, 0xFF000000 | GuideText.RUBRIC);
				}
			} else {
				g.fill(x + 1, y + 2, x + 8, y + 3, INK_SOFT);
				g.fill(x + 1, y + 8, x + 8, y + 9, INK_SOFT);
				g.fill(x + 1, y + 2, x + 2, y + 9, INK_SOFT);
				g.fill(x + 7, y + 2, x + 8, y + 9, INK_SOFT);
			}
			g.pose().pushMatrix();
			g.pose().translate(x + 10, y + 1);
			g.pose().scale(0.62F, 0.62F);
			g.item(this.icon, 0, 0);
			g.pose().popMatrix();
			float scale = fitScale(screen.font, this.title, ROOM, SMALL);
			int ink = this.done ? INK_SOFT : this.current ? 0xFF000000 | GuideText.RUBRIC : INK;
			g.pose().pushMatrix();
			g.pose().translate(x + 22, y + 3 + (SMALL - scale) * 4.0F);
			g.pose().scale(scale, scale);
			g.text(screen.font, this.title, 0, 0, ink, false);
			g.pose().popMatrix();
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			GuideBooks.Book in = GuideBooks.bookOf(this.step.chapter);
			return in == null ? List.of(this.title.copy().withColor(0xFFE9A8), this.description)
				: List.of(this.title.copy().withColor(0xFFE9A8), this.description,
					Component.translatable("gui.forja.libros.en_libro", in.title()).withColor(0xC9A96A));
		}

		@Override
		boolean click(GuideBookScreen screen) {
			return screen.openChapter(this.step.chapter);
		}
	}

	// ------------------------------------------------------------------ elements of the books

	/**
	 * One book of the shelf, the way the bestiary shows a creature not met yet (Andy, 2026-09-29): until its recipe
	 * is learned the card is dark — a shadow of a book, a lock, and only the hint of when it opens; the recipe is not
	 * shown at all. Learned, it lights up: its cover, its name, what it is about, its recipe drawn, and "open it"
	 * when the reader carries it. A red seal marks a book whose recipe is new (learned and never opened); a thin
	 * bar, how much of it has been read.
	 */
	private final class BookCard extends Element {
		private static final int SHADOW = 0xFF2E2620;
		private static final int SHADOW_INK = 0xFF8C7A62;
		private final GuideBooks.Book target;
		private final boolean readable;
		/** Written and its recipe learned: the card is lit and the recipe shown. */
		private final boolean unlocked;
		private final boolean fresh;
		private final ItemStack cover;
		private final ItemStack ingredient;
		private final Component name;
		private final Component motto;
		private final List<FormattedCharSequence> status;

		BookCard(GuideBooks.Book target) {
			this.target = target;
			this.readable = GuideBookScreen.this.canRead(target);
			this.unlocked = target.ready && (target.unlock == null || GuideBookScreen.this.pathDone.contains(target.unlock));
			this.fresh = this.unlocked && target != GuideBookScreen.this.book && !BookMemory.wasOpened(target);
			Item item = target.item();
			this.cover = item != null ? new ItemStack(item) : new ItemStack(Items.BOOK);
			this.ingredient = new ItemStack(target.ingredient.get());
			this.name = this.unlocked
				? Component.literal(target.numeral() + " · ").append(target.title())
				: Component.translatable("gui.forja.libros.carta.oculto", target.numeral());
			this.motto = this.unlocked ? target.motto() : Component.empty();
			int pages = target == GuideBookScreen.this.book || !this.unlocked ? -1 : pagesOf(target);
			Component line;
			if (!target.ready) {
				line = Component.translatable("gui.forja.libros.carta.pronto", target.when());
			} else if (!this.unlocked) {
				line = Component.translatable("gui.forja.libros.carta.aprende", target.when());
			} else if (this.readable) {
				line = pages > 0
					? Component.translatable("gui.forja.libros.carta.abrir", pages, minutes(pages))
					: Component.translatable("gui.forja.libros.carta.tienes");
			} else {
				line = Component.translatable("gui.forja.libros.carta.hazlo");
			}
			this.status = GuideBookScreen.this.font.split(line, WRAP);
		}

		/** Whether the card is lit: for the client test. */
		boolean lit() {
			return this.unlocked;
		}

		private int statusY() {
			return 26;
		}

		private int recipeY() {
			return this.statusY() + this.status.size() * 8 + 1;
		}

		@Override
		int height() {
			return this.recipeY() + 18;
		}

		@Override
		int widest(Font font) {
			int widest = 26 + Math.round(font.width(this.name) * fitScale(font, this.name, CONTENT_W - 26, 1.0F));
			for (FormattedCharSequence line : this.status) {
				widest = Math.max(widest, Math.round(font.width(line) * SMALL));
			}
			return widest;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int colour = this.unlocked ? 0xFF000000 | this.target.colour : SHADOW;
			boolean hovered = this.readable && this.unlocked && over(mouseX, mouseY, x - 2, y, CONTENT_W + 4, this.height() - 2);
			if (!this.unlocked) {
				// The whole card in shadow, like a page of the bestiary for a creature never seen.
				g.fill(x - 2, y, x + CONTENT_W + 2, y + this.height() - 2, 0x66241C14);
			} else if (hovered) {
				g.fill(x - 2, y, x + CONTENT_W + 2, y + this.height() - 2, PAPER_SHADE);
			}
			// The cover in small: its leather and a gilt edge when lit; when not, a dark book shape and a lock.
			g.fill(x, y + 2, x + 22, y + 24, colour);
			g.fill(x, y + 2, x + 22, y + 3, this.unlocked ? 0xFFD6B05A : 0xFF4A3E32);
			g.fill(x, y + 23, x + 22, y + 24, this.unlocked ? 0xFFD6B05A : 0xFF4A3E32);
			g.fill(x, y + 2, x + 1, y + 24, 0x60000000);
			g.item(this.cover, x + 3, y + 5);
			if (!this.unlocked) {
				// Its silhouette: the book drawn, then blacked out over it.
				g.fill(x + 3, y + 5, x + 19, y + 21, 0xE6120E0A);
				lock(g, x + 8, y + 9, SHADOW_INK);
			}
			float scale = fitScale(screen.font, this.name, CONTENT_W - 26, 1.0F);
			g.pose().pushMatrix();
			g.pose().translate(x + 26, y + 3 + (1.0F - scale) * 4.0F);
			g.pose().scale(scale, scale);
			g.text(screen.font, this.name, 0, 0, this.unlocked ? 0xFF6B2A0E : SHADOW_INK, false);
			g.pose().popMatrix();
			if (this.unlocked) {
				float mottoScale = fitScale(screen.font, this.motto, CONTENT_W - 26, SMALL);
				g.pose().pushMatrix();
				g.pose().translate(x + 26, y + 14);
				g.pose().scale(mottoScale, mottoScale);
				g.text(screen.font, this.motto, 0, 0, INK_SOFT, false);
				g.pose().popMatrix();
			}
			int ink = !this.unlocked ? SHADOW_INK : this.readable ? 0xFF000000 | GuideText.RUBRIC : INK;
			for (int i = 0; i < this.status.size(); i++) {
				screen.small(g, this.status.get(i), x, y + this.statusY() + i * 8, ink);
			}
			int row = y + this.recipeY();
			if (this.unlocked) {
				// The recipe, drawn: a book and the ingredient make the book.
				smallItem(g, new ItemStack(Items.BOOK), x + 2, row + 2);
				screen.small(g, Component.literal("+"), x + 16, row + 5, INK_SOFT);
				smallItem(g, this.ingredient, x + 22, row + 2);
				screen.small(g, Component.literal("="), x + 36, row + 5, INK_SOFT);
				smallItem(g, this.cover, x + 42, row + 2);
			} else {
				// Not learned: three dark slots and a lock, and nothing in them.
				for (int slot = 0; slot < 3; slot++) {
					g.fill(x + 2 + slot * 20, row + 2, x + 14 + slot * 20, row + 14, 0xFF3A3028);
				}
				screen.small(g, Component.literal("+"), x + 16, row + 5, SHADOW_INK);
				screen.small(g, Component.literal("="), x + 36, row + 5, SHADOW_INK);
				lock(g, x + 67, row + 4, SHADOW_INK);
			}
			if (this.unlocked && this.readable) {
				int pages = pagesOf(this.target);
				float read = pages > 0 ? BookMemory.readShare(this.target, pages) : 0.0F;
				int left = x + 70;
				int right = x + CONTENT_W - 2;
				g.fill(left, row + 6, right, row + 9, PAPER_SHADE);
				g.fill(left, row + 6, left + Math.round((right - left) * read), row + 9, 0xFF5E7A34);
			}
			if (this.fresh) {
				// "Nuevo": a red seal on the corner of a book just learned.
				g.fill(x + CONTENT_W - 9, y + 1, x + CONTENT_W - 1, y + 9, 0xFFB02020);
				g.fill(x + CONTENT_W - 6, y + 2, x + CONTENT_W - 4, y + 6, 0xFFFFE0C0);
				g.fill(x + CONTENT_W - 6, y + 7, x + CONTENT_W - 4, y + 8, 0xFFFFE0C0);
			}
			g.fill(x + 6, y + this.height() - 2, x + CONTENT_W - 6, y + this.height() - 1, PAPER_SHADE);
		}

		/** A small padlock: a shackle over a body, seven pixels wide. */
		private static void lock(GuiGraphicsExtractor g, int x, int y, int ink) {
			g.fill(x, y + 3, x + 6, y + 7, ink);
			g.fill(x + 1, y, x + 2, y + 3, ink);
			g.fill(x + 4, y, x + 5, y + 3, ink);
			g.fill(x + 1, y, x + 5, y + 1, ink);
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			if (!this.unlocked) {
				return List.of(this.name.copy().withColor(0xC9A96A), this.status.isEmpty() ? Component.empty()
					: Component.translatable(this.target.ready ? "gui.forja.libros.carta.aprende" : "gui.forja.libros.carta.pronto", this.target.when()));
			}
			int row = y + this.recipeY();
			if (over(mouseX, mouseY, x + 22, row + 2, 12, 12)) {
				return this.ingredient;
			}
			if (over(mouseX, mouseY, x, y + 2, 22, 22) || over(mouseX, mouseY, x + 42, row + 2, 12, 12)) {
				return this.cover;
			}
			return this.readable ? null : GuideBookScreen.this.whereToGet(this.target);
		}

		@Override
		boolean click(GuideBookScreen screen) {
			if (!this.readable || !this.unlocked || this.target == screen.book) {
				return false;
			}
			screen.minecraft.gui.setScreen(new GuideBookScreen(this.target));
			return true;
		}
	}

	/** Under the title: which book, how many pages, about how long, and a bar of how much has been read. */
	private final class BookInfo extends Element {
		private Component line(int pages) {
			GuideBooks.Book book = GuideBookScreen.this.book;
			String where = book == GuideBooks.Book.CUADERNO ? "gui.forja.libros.info.cuaderno"
				: book == GuideBooks.Book.BIBLIOTECA ? "gui.forja.libros.info.biblioteca" : "gui.forja.libros.info.libro";
			return Component.translatable(where, book.numeral(), pages, minutes(pages));
		}

		@Override
		int height() {
			return 20;
		}

		@Override
		int widest(Font font) {
			Component line = this.line(GuideBookScreen.this.pages.size());
			return Math.round(font.width(line) * fitScale(font, line, CONTENT_W, SMALL));
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int pages = screen.pages.size();
			Component line = this.line(pages);
			float scale = fitScale(screen.font, line, CONTENT_W, SMALL);
			int width = Math.round(screen.font.width(line) * scale);
			g.pose().pushMatrix();
			g.pose().translate(x + (CONTENT_W - width) / 2.0F, y + 2);
			g.pose().scale(scale, scale);
			g.text(screen.font, line, 0, 0, INK_SOFT, false);
			g.pose().popMatrix();
			float read = BookMemory.readShare(screen.book, pages);
			Component share = Component.translatable("gui.forja.libros.leido", Math.round(read * 100));
			int shareWidth = Math.round(screen.font.width(share) * SMALL);
			int left = x + 10;
			int right = x + CONTENT_W - 14 - shareWidth;
			g.fill(left, y + 12, right, y + 15, PAPER_SHADE);
			g.fill(left, y + 12, left + Math.round((right - left) * read), y + 15, 0xFF5E7A34);
			screen.small(g, share, right + 4, y + 11, INK_SOFT);
		}
	}

	/** The steps of the path this book teaches, each with its tick: "Tus pasos aquí: 2 de 4". */
	private final class BookSteps extends Element {
		private final List<ForjaPath.Step> steps;

		BookSteps(List<ForjaPath.Step> steps) {
			this.steps = steps;
		}

		private Component line() {
			int done = 0;
			for (ForjaPath.Step step : this.steps) {
				done += GuideBookScreen.this.pathDone.contains(step.advancement()) ? 1 : 0;
			}
			return Component.translatable("gui.forja.libros.pasos_aqui", done, this.steps.size());
		}

		@Override
		int height() {
			return 26;
		}

		@Override
		int widest(Font font) {
			return Math.max(Math.round(font.width(this.line()) * SMALL), this.steps.size() * 20);
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			Component line = this.line();
			int width = Math.round(screen.font.width(line) * SMALL);
			screen.small(g, line, x + (CONTENT_W - width) / 2, y + 1, INK_SOFT);
			int total = this.steps.size() * 20 - 4;
			int iconX = x + (CONTENT_W - total) / 2;
			for (ForjaPath.Step step : this.steps) {
				boolean done = screen.pathDone.contains(step.advancement());
				g.fill(iconX - 1, y + 9, iconX + 17, y + 25, done ? 0x505E7A34 : 0x30806848);
				g.item(step.icon(), iconX, y + 9);
				if (done) {
					g.fill(iconX + 11, y + 19, iconX + 17, y + 25, 0xFF5E7A34);
					g.fill(iconX + 12, y + 22, iconX + 13, y + 23, PAPER);
					g.fill(iconX + 13, y + 23, iconX + 14, y + 24, PAPER);
					g.fill(iconX + 14, y + 22, iconX + 15, y + 23, PAPER);
					g.fill(iconX + 15, y + 21, iconX + 16, y + 22, PAPER);
				}
				iconX += 20;
			}
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			int total = this.steps.size() * 20 - 4;
			int iconX = x + (CONTENT_W - total) / 2;
			for (ForjaPath.Step step : this.steps) {
				if (over(mouseX, mouseY, iconX, y + 9, 16, 16)) {
					return List.of(step.title().copy().withColor(0xFFE9A8), Component.literal(step.description().getString().replace("**", "")));
				}
				iconX += 20;
			}
			return null;
		}

		@Override
		boolean click(GuideBookScreen screen) {
			for (ForjaPath.Step step : this.steps) {
				if (!screen.pathDone.contains(step.advancement())) {
					return screen.openChapter(step.chapter);
				}
			}
			return false;
		}
	}

	/** "Seguir leyendo en la página N": back to where the reader left this book. */
	private final class BookmarkLink extends Element {
		private final int page;

		BookmarkLink(int page) {
			this.page = page;
		}

		private Component line() {
			return Component.translatable("gui.forja.libros.seguir", this.page + 1);
		}

		@Override
		int height() {
			return 12;
		}

		@Override
		int widest(Font font) {
			return 9 + Math.round(font.width(this.line()) * SMALL);
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			boolean hovered = over(mouseX, mouseY, x - 2, y, CONTENT_W + 4, 11);
			if (hovered) {
				g.fill(x - 2, y, x + CONTENT_W + 2, y + 11, PAPER_SHADE);
			}
			int ink = hovered ? 0xFF6B2A0E : 0xFF000000 | GuideText.RUBRIC;
			// A ribbon's end, for a bookmark.
			g.fill(x + 2, y + 1, x + 6, y + 8, ink);
			g.fill(x + 3, y + 8, x + 5, y + 9, PAPER);
			screen.small(g, this.line(), x + 9, y + 2, ink);
		}

		@Override
		boolean click(GuideBookScreen screen) {
			screen.jumpTo(Math.min(this.page, screen.pages.size() - 1));
			return true;
		}
	}

	/**
	 * "En una página": the whole of a part of the book in a few lines, on a shaded card, first thing in it. For the
	 * reader who wants the gist and nothing more (Andy: a long book, well sectioned, that does not scare).
	 */
	/** The signs a {@link Formula} writes between its items. */
	private static final String PLUS = "+";
	private static final String EQUALS = "=";
	private static final String ARROW = "→";

	/** An item drawn at any size, with its count when it has more than one. */
	private static void itemAt(GuiGraphicsExtractor g, Font font, ItemStack stack, float x, float y, float scale) {
		if (stack.isEmpty()) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(stack, 0, 0);
		g.itemDecorations(font, stack, 0, 0);
		g.pose().popMatrix();
	}

	/**
	 * A line of items with signs between them, read left to right: "3 roca + plantilla = cabeza". A recipe of the
	 * forge is not a crafting grid, and a row of icons with nothing between them does not say which way it goes.
	 * Items show their count, and each answers the mouse with its own tooltip.
	 */
	private final class Formula extends Element {
		private static final int ITEM = 18;
		private static final int SIGN = 9;
		private final List<?> parts;

		Formula(List<?> parts) {
			this.parts = parts;
		}

		private int span() {
			int total = 0;
			for (Object part : this.parts) {
				total += part instanceof ItemStack ? ITEM : SIGN;
			}
			return total - 2;
		}

		@Override
		int height() {
			return 22;
		}

		@Override
		int widest(Font font) {
			return this.span();
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int at = x + (CONTENT_W - this.span()) / 2;
			for (Object part : this.parts) {
				if (part instanceof ItemStack stack) {
					itemAt(g, screen.font, stack, at, y + 3, 1.0F);
					at += ITEM;
				} else {
					String sign = String.valueOf(part);
					g.text(screen.font, sign, at + (SIGN - 2 - screen.font.width(sign)) / 2, y + 7, INK_SOFT, false);
					at += SIGN;
				}
			}
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			int at = x + (CONTENT_W - this.span()) / 2;
			for (Object part : this.parts) {
				if (part instanceof ItemStack stack) {
					if (!stack.isEmpty() && over(mouseX, mouseY, at, y + 3, 16, 16)) {
						return stack;
					}
					at += ITEM;
				} else {
					at += SIGN;
				}
			}
			return null;
		}
	}

	/**
	 * One of the tables' own screens, drawn small from its real texture with things laid in its slots and numbered
	 * marks on what to fill or click, so that a step can say "(1)" and the reader can see where 1 is. Everything is
	 * placed in the texture's own coordinates (ForgeMenu's slot positions), so the picture is the screen the player
	 * will open, not a sketch of it.
	 */
	private static final class TablePanel extends Element {
		private static final net.minecraft.resources.Identifier PARTS = dev.forja.Forja.id("textures/gui/mesa_de_piezas.png");
		private static final net.minecraft.resources.Identifier FORGE = dev.forja.Forja.id("textures/gui/mesa_de_forja.png");
		/** The mark's colour: the rubric red the text uses for the words it wants seen. */
		private static final int MARK = 0xFF000000 | GuideText.RUBRIC;

		/** Something laid on the screen, at the slot's own corner in texture pixels. */
		private record Shown(int u, int v, ItemStack stack) {
		}

		/** A numbered mark, its centre in texture pixels. */
		private record Mark(int u, int v, int number) {
		}

		private final net.minecraft.resources.Identifier texture;
		private final int u0;
		private final int v0;
		private final int w;
		private final int h;
		private final List<Shown> shown = new ArrayList<>();
		private final List<Mark> marks = new ArrayList<>();
		/** The parts table's pattern grid, which the screen draws in code and the texture leaves blank. */
		private boolean patterns;
		/** The forge button and the hammer's rail under it, likewise drawn in code. */
		private boolean button;
		/** What the button says: the star's Forjar, or Mejorar with a piece in the centre and an ingredient on a point. */
		private String buttonKey = "forjar";
		/** What the forge table's panel on the right says it would make. */
		private ItemStack preview = ItemStack.EMPTY;

		private TablePanel(net.minecraft.resources.Identifier texture, int u0, int v0, int w, int h) {
			this.texture = texture;
			this.u0 = u0;
			this.v0 = v0;
			this.w = w;
			this.h = h;
		}

		/** The parts table with a blank template in its slot and the pattern grid lit: (1) the template, (2) the shape. */
		static TablePanel engraving() {
			TablePanel panel = new TablePanel(PARTS, 4, 17, 200, 92);
			panel.patterns = true;
			panel.shown.add(new Shown(TEMPLATE_U, ROW_V, new ItemStack(ModItems.PLANTILLA)));
			panel.marks.add(new Mark(TEMPLATE_U - 2, ROW_V - 2, 1));
			// Beside the cell rather than on it, so the shape to click stays in sight.
			panel.marks.add(new Mark(GRID_X + GRID_CELL + 4, GRID_Y + 3, 2));
			return panel;
		}

		/** The parts table's row of slots: (1) the engraved template, (2) the material, (3) the part to take. */
		static TablePanel cutting(ItemStack template, ItemStack material, ItemStack part) {
			TablePanel panel = new TablePanel(PARTS, 12, 78, 154, 32);
			panel.shown.add(new Shown(TEMPLATE_U, ROW_V, template));
			panel.shown.add(new Shown(MATERIAL_U, ROW_V, material));
			panel.shown.add(new Shown(RESULT_U, ROW_V, part));
			panel.marks.add(new Mark(TEMPLATE_U - 2, ROW_V - 2, 1));
			panel.marks.add(new Mark(MATERIAL_U - 2, ROW_V - 2, 2));
			panel.marks.add(new Mark(RESULT_U - 4, ROW_V - 4, 3));
			return panel;
		}

		/**
		 * The forge table's star. Its marks follow the order the work is done in: with things on the points and the
		 * centre empty, (1) the points, (2) the panel that says what comes out, (3) Forjar; with nothing on the points,
		 * (1) Forjar and (2) the piece to take from the centre; with both, (1) the piece, (2) the points, (3) Forjar.
		 */
		static TablePanel star(List<ItemStack> points, ItemStack centre) {
			TablePanel panel = new TablePanel(FORGE, 4, 12, 200, 100);
			panel.button = true;
			for (int i = 0; i < points.size() && i < dev.forja.menu.ForgeMenu.STAR_POINTS.length; i++) {
				panel.shown.add(new Shown(dev.forja.menu.ForgeMenu.STAR_POINTS[i][0], dev.forja.menu.ForgeMenu.STAR_POINTS[i][1], points.get(i)));
			}
			panel.shown.add(new Shown(dev.forja.menu.ForgeMenu.CENTER_X, dev.forja.menu.ForgeMenu.CENTER_Y, centre));
			int[] point = dev.forja.menu.ForgeMenu.STAR_POINTS[0];
			int buttonU = FORGE_BUTTON_U + 4;
			int buttonV = FORGE_BUTTON_V + 2;
			if (!points.isEmpty() && centre.isEmpty()) {
				var level = net.minecraft.client.Minecraft.getInstance().level;
				if (level != null && points.stream().allMatch(stack -> stack.getItem() instanceof dev.forja.item.PartItem)) {
					List<ItemStack> parts = points.stream().map(stack -> stack.copyWithCount(1)).toList();
					panel.preview = Assembler.evaluate(parts, level.registryAccess()).stack();
				}
				panel.marks.add(new Mark(point[0] - 2, point[1] - 2, 1));
				panel.marks.add(new Mark(INFO_U + 4, INFO_V + 4, 2));
				panel.marks.add(new Mark(buttonU, buttonV, 3));
			} else if (points.isEmpty()) {
				panel.marks.add(new Mark(buttonU, buttonV, 1));
				panel.marks.add(new Mark(dev.forja.menu.ForgeMenu.CENTER_X - 2, dev.forja.menu.ForgeMenu.CENTER_Y - 2, 2));
			} else {
				panel.buttonKey = "mejorar";
				panel.marks.add(new Mark(dev.forja.menu.ForgeMenu.CENTER_X - 2, dev.forja.menu.ForgeMenu.CENTER_Y - 2, 1));
				panel.marks.add(new Mark(point[0] - 2, point[1] - 2, 2));
				panel.marks.add(new Mark(buttonU, buttonV, 3));
			}
			return panel;
		}

		/** The parts table's row of slots (ForgeMenu's template, material and result slots). */
		private static final int TEMPLATE_U = 22;
		private static final int MATERIAL_U = 64;
		private static final int RESULT_U = 136;
		private static final int ROW_V = 85;
		/** Where ForgeScreen puts its pattern grid, the panel on the right and the forge button (its constants). */
		private static final int GRID_X = 7;
		private static final int GRID_Y = 21;
		private static final int GRID_CELL = 16;
		private static final int GRID_ROW = 15;
		private static final int GRID_COLUMNS = 12;
		private static final int INFO_U = 111;
		private static final int INFO_V = 19;
		private static final int FORGE_BUTTON_U = 110;
		private static final int FORGE_BUTTON_V = 88;
		private static final int FORGE_BUTTON_W = 91;
		private static final int FORGE_BUTTON_H = 15;

		private float scale() {
			return CONTENT_W / (float) this.w;
		}

		@Override
		int height() {
			return Math.round(this.h * this.scale()) + 4;
		}

		@Override
		int widest(Font font) {
			return CONTENT_W;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			float scale = this.scale();
			g.pose().pushMatrix();
			g.pose().translate(x, y + 2);
			g.pose().scale(scale, scale);
			g.pose().translate(-this.u0, -this.v0);
			// The texture, cut to the part of the screen this step is about.
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, this.texture, this.u0, this.v0, this.u0, this.v0, this.w, this.h, 256, 256);
			if (this.patterns) {
				PartType[] parts = PartType.values();
				for (int i = 0; i < parts.length; i++) {
					int bx = GRID_X + i % GRID_COLUMNS * GRID_CELL;
					int by = GRID_Y + i / GRID_COLUMNS * GRID_ROW;
					g.fill(bx, by, bx + GRID_CELL, by + GRID_CELL, i == 0 ? 0xFFD9A15A : 0xFF9C8E78);
					g.fill(bx, by + GRID_CELL - 1, bx + GRID_CELL, by + GRID_CELL, 0xFF5E5244);
					g.item(Assembler.createPart(parts[i], parts[i].showcase()), bx, by);
				}
				// The shape the step is about, ringed in the marks' red.
				g.outline(GRID_X - 1, GRID_Y - 1, GRID_CELL + 2, GRID_CELL + 2, MARK);
			}
			if (this.button) {
				int bx = FORGE_BUTTON_U;
				int by = FORGE_BUTTON_V;
				g.fill(bx, by, bx + FORGE_BUTTON_W, by + FORGE_BUTTON_H, 0xFF5A3A18);
				g.fill(bx + 1, by + 1, bx + FORGE_BUTTON_W - 1, by + FORGE_BUTTON_H - 1, 0xFFB8733A);
				Component label = Component.translatable("gui.forja.boton." + this.buttonKey);
				g.text(screen.font, label, bx + (FORGE_BUTTON_W - screen.font.width(label)) / 2, by + 4, 0xFFFFF4E0, false);
			}
			if (this.button && this.buttonKey.equals("forjar")) {
				// The rail and its lit middle, with the hammer on its way across: only a piece being born takes a press.
				int bx = FORGE_BUTTON_U;
				int railY = FORGE_BUTTON_V + FORGE_BUTTON_H + 2;
				g.fill(bx, railY, bx + FORGE_BUTTON_W, railY + 5, 0xFF2B2216);
				int centre = bx + FORGE_BUTTON_W / 2;
				g.fill(centre - 10, railY, centre + 10, railY + 5, 0xFF7A4A1C);
				g.fill(centre - 4, railY, centre + 4, railY + 5, 0xFFE8A33C);
				int hammer = bx + FORGE_BUTTON_W / 4;
				g.fill(hammer + 2, railY - 4, hammer + 3, railY + 1, 0xFF8A5A2C);
				g.fill(hammer, railY - 1, hammer + 5, railY + 3, 0xFFE8E0D0);
			}
			if (!this.preview.isEmpty()) {
				g.item(this.preview, INFO_U + 4, INFO_V + 12);
				g.pose().pushMatrix();
				g.pose().translate(INFO_U + 23, INFO_V + 16);
				g.pose().scale(0.8F, 0.8F);
				g.text(screen.font, screen.font.plainSubstrByWidth(this.preview.getHoverName().getString(), 80), 0, 0, 0xFFE8E0D0, false);
				g.pose().popMatrix();
			}
			for (Shown thing : this.shown) {
				itemAt(g, screen.font, thing.stack(), thing.u(), thing.v(), 1.0F);
			}
			g.pose().popMatrix();
			// The marks last, at the page's own size: a number shrunk with the screen would be unreadable.
			for (Mark mark : this.marks) {
				int mx = Math.round(x + (mark.u() - this.u0) * scale);
				int my = Math.round(y + 2 + (mark.v() - this.v0) * scale);
				g.fill(mx - 4, my - 4, mx + 5, my + 5, 0xFFF4E9CC);
				g.fill(mx - 3, my - 3, mx + 4, my + 4, MARK);
				String number = String.valueOf(mark.number());
				g.pose().pushMatrix();
				g.pose().translate(mx - screen.font.width(number) * 0.35F + 0.5F, my - 2.5F);
				g.pose().scale(0.75F, 0.75F);
				g.text(screen.font, number, 0, 0, 0xFFFFFFFF, false);
				g.pose().popMatrix();
			}
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			float scale = this.scale();
			for (Shown thing : this.shown) {
				int sx = Math.round(x + (thing.u() - this.u0) * scale);
				int sy = Math.round(y + 2 + (thing.v() - this.v0) * scale);
				int size = Math.round(16 * scale);
				if (!thing.stack().isEmpty() && over(mouseX, mouseY, sx, sy, size, size)) {
					return thing.stack();
				}
			}
			return null;
		}
	}

	private final class Summary extends Element {
		private final List<FormattedCharSequence> lines;
		private final Component label = Component.translatable("gui.forja.libros.en_una_pagina");

		Summary(Component text) {
			this.lines = GuideBookScreen.this.font.split(GuideText.rubric(text), WRAP - 8);
		}

		@Override
		int height() {
			return 16 + this.lines.size() * 8;
		}

		@Override
		int widest(Font font) {
			int widest = Math.round(font.width(this.label) * SMALL);
			for (FormattedCharSequence line : this.lines) {
				widest = Math.max(widest, 4 + Math.round(font.width(line) * SMALL));
			}
			return widest;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			g.fill(x - 2, y + 1, x + CONTENT_W + 2, y + this.height() - 2, PAPER_SHADE);
			g.fill(x - 2, y + 1, x, y + this.height() - 2, 0xFF000000 | GuideText.RUBRIC);
			screen.small(g, this.label, x + 3, y + 3, 0xFF000000 | GuideText.RUBRIC);
			for (int i = 0; i < this.lines.size(); i++) {
				screen.small(g, this.lines.get(i), x + 3, y + 12 + i * 8, INK);
			}
		}
	}

	/** A heading inside a list: a section's name and its count, in the rubric, with a thin rule. */
	private final class GroupHeading extends Element {
		private final Component text;
		/** The group this heading folds and unfolds, or null for one that does not. */
		private final @Nullable String key;

		GroupHeading(Component text, @Nullable String key) {
			this.text = text;
			this.key = key;
		}

		@Override
		boolean click(GuideBookScreen screen) {
			if (this.key == null) {
				return false;
			}
			screen.toggleGroup(this.key);
			return true;
		}

		@Override
		int height() {
			return 12;
		}

		@Override
		int widest(Font font) {
			return Math.round(font.width(this.text) * fitScale(font, this.text, CONTENT_W, SMALL));
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			if (this.key != null && over(mouseX, mouseY, x - 2, y, CONTENT_W + 4, 12)) {
				g.fill(x - 2, y, x + CONTENT_W + 2, y + 12, PAPER_SHADE);
			}
			float scale = fitScale(screen.font, this.text, CONTENT_W, SMALL);
			g.pose().pushMatrix();
			g.pose().translate(x, y + 2);
			g.pose().scale(scale, scale);
			g.text(screen.font, this.text, 0, 0, 0xFF000000 | GuideText.RUBRIC, false);
			g.pose().popMatrix();
			g.fill(x, y + 10, x + CONTENT_W / 2, y + 11, BAND);
		}
	}

	/** A creature not seen yet: a dark plate and a question mark where its portrait will be. */
	private static final class CreatureShadow extends Element {
		@Override
		int height() {
			return 60;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			int left = x + 14;
			int right = x + CONTENT_W - 14;
			g.fill(left, y + 2, right, y + 56, 0xFF2E2620);
			g.outline(left, y + 2, right - left, 54, 0x90806848);
			g.pose().pushMatrix();
			g.pose().translate(x + CONTENT_W / 2.0F - 9, y + 14);
			g.pose().scale(3.0F, 3.0F);
			g.text(screen.font, "?", 0, 0, 0xFF8C7A62, false);
			g.pose().popMatrix();
		}
	}

	/** Not drawn: on screen, it marks its creature's page as read, so the "nuevo" mark goes away. */
	private static final class CreatureRead extends Element {
		private final String creature;

		CreatureRead(String creature) {
			this.creature = creature;
		}

		@Override
		int height() {
			return 0;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			BookMemory.read(this.creature);
		}
	}

	/** The probe's slot: the piece in it, its name, and "clic: elige otra de tu bolsa". A click opens the picker. */
	private final class ItemProbe extends Element {
		private final Component hint = Component.translatable("gui.forja.libros.probador.clic");

		@Override
		int height() {
			return 28;
		}

		@Override
		int widest(Font font) {
			return 28 + Math.round(font.width(this.hint) * fitScale(font, this.hint, CONTENT_W - 28, SMALL));
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			boolean hovered = over(mouseX, mouseY, x, y + 2, CONTENT_W, 24);
			g.fill(x + 1, y + 3, x + 23, y + 25, hovered ? BAND : PAPER_SHADE);
			g.outline(x + 1, y + 3, 22, 22, INK_SOFT);
			if (!probe.isEmpty()) {
				g.item(probe, x + 4, y + 6);
			}
			Component name = probe.isEmpty() ? Component.translatable("gui.forja.libros.probador.ranura") : probe.getHoverName();
			float scale = fitScale(screen.font, name, CONTENT_W - 28, 1.0F);
			g.pose().pushMatrix();
			g.pose().translate(x + 28, y + 5 + (1.0F - scale) * 4.0F);
			g.pose().scale(scale, scale);
			g.text(screen.font, name, 0, 0, INK, false);
			g.pose().popMatrix();
			float hintScale = fitScale(screen.font, this.hint, CONTENT_W - 28, SMALL);
			g.pose().pushMatrix();
			g.pose().translate(x + 28, y + 16);
			g.pose().scale(hintScale, hintScale);
			g.text(screen.font, this.hint, 0, 0, 0xFF000000 | GuideText.RUBRIC, false);
			g.pose().popMatrix();
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			return !probe.isEmpty() && over(mouseX, mouseY, x + 1, y + 3, 22, 22) ? probe : null;
		}

		@Override
		boolean click(GuideBookScreen screen) {
			screen.picking = true;
			return true;
		}
	}

	/**
	 * One upgrade for the piece in the probe: its name and how far it goes (now and at most), what it does at that,
	 * why it does not fit when it does not, the synergies it would wake with what is on, and its recipe drawn — or
	 * the flask's orb for the ones only the sky gives.
	 */
	private final class FitEntry extends Element {
		private final dev.forja.upgrade.UpgradeFit.Fit fit;
		private final Component head;
		private final List<FormattedCharSequence> lines = new ArrayList<>();

		/** A sealed pact: a shadow with only how it is opened. */
		private final boolean sealed;

		FitEntry(dev.forja.upgrade.UpgradeFit.Fit fit, int awake) {
			this.fit = fit;
			Upgrade upgrade = fit.upgrade();
			this.sealed = upgrade.isPact() && !GuideBookScreen.this.pactKnown(upgrade);
			if (this.sealed) {
				this.head = Component.translatable("gui.forja.libros.pacto_sellado");
				this.lines.addAll(GuideBookScreen.this.font.split(Component.translatable("gui.forja.libros.probador.razon.sealed"), WRAP));
				return;
			}
			this.head = fit.fits()
				? Component.translatable("gui.forja.libros.probador.sube", upgrade.displayName(), fit.current(), fit.greater())
				: Component.translatable("gui.forja.libros.probador.esta", upgrade.displayName(), fit.current());
			int target = fit.fits() ? fit.greater() : fit.current() > 0 ? fit.current() : 100;
			List<Component> parts = new ArrayList<>();
			parts.add(Component.translatable("gui.forja.libros.probador.efecto", target, upgrade.effect(target)));
			if (!fit.fits()) {
				parts.add(fit.conflict() != null
					? Component.translatable("gui.forja.libros.probador.razon.conflict", fit.conflict().displayName())
					: Component.translatable("gui.forja.libros.probador.razon." + fit.reason().name().toLowerCase(java.util.Locale.ROOT)));
			}
			for (dev.forja.upgrade.Synergy synergy : fit.pairs()) {
				Upgrade other = synergy.first == upgrade ? synergy.second : synergy.first;
				if (!GuideBookScreen.this.synergyKnown(synergy)) {
					// It would wake one, but which is for the reader to find out.
					parts.add(Component.translatable("gui.forja.libros.probador.sinergia_oculta", other.displayName()));
					continue;
				}
				parts.add(Component.translatable(awake >= dev.forja.upgrade.Synergy.MOST ? "gui.forja.libros.probador.sinergia_dormida"
					: "gui.forja.libros.probador.sinergia", other.displayName(), synergy.displayName()));
			}
			for (Component part : parts) {
				this.lines.addAll(GuideBookScreen.this.font.split(part, WRAP));
			}
		}

		@Override
		int height() {
			return 9 + this.lines.size() * 8 + 14;
		}

		@Override
		int widest(Font font) {
			int widest = Math.round(font.width(this.head) * fitScale(font, this.head, CONTENT_W, SMALL));
			for (FormattedCharSequence line : this.lines) {
				widest = Math.max(widest, Math.round(font.width(line) * SMALL));
			}
			return widest;
		}

		@Override
		void draw(GuideBookScreen screen, GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
			Upgrade upgrade = this.fit.upgrade();
			int colour = this.fit.fits() ? 0xFF000000 | GuideText.darken(upgrade.color) : INK_SOFT;
			float scale = fitScale(screen.font, this.head, CONTENT_W, SMALL);
			g.pose().pushMatrix();
			g.pose().translate(x, y + 1);
			g.pose().scale(scale, scale);
			g.text(screen.font, this.head, 0, 0, colour, false);
			g.pose().popMatrix();
			for (int i = 0; i < this.lines.size(); i++) {
				screen.small(g, this.lines.get(i), x, y + 9 + i * 8, this.fit.fits() ? INK : INK_SOFT);
			}
			int row = y + 9 + this.lines.size() * 8;
			int iconX = x;
			if (this.sealed) {
				net.minecraft.world.item.Item offering = dev.forja.upgrade.Pacts.offering(upgrade);
				if (offering != null) {
					smallItem(g, new ItemStack(offering), iconX, row + 1);
					screen.small(g, Component.translatable("gui.forja.libros.probador.ofrenda"), iconX + 14, row + 4, INK_SOFT);
				}
				return;
			}
			if (upgrade.options.isEmpty()) {
				// Only the sky gives it: the flask's orb is the way.
				smallItem(g, dev.forja.item.UpgradeOrbItem.create(upgrade, 100), iconX, row + 1);
				screen.small(g, Component.translatable("gui.forja.libros.probador.orbe"), iconX + 14, row + 4, INK_SOFT);
				return;
			}
			boolean first = true;
			for (Upgrade.Option option : upgrade.options) {
				if (iconX > x + CONTENT_W - 30) {
					break;
				}
				if (!first) {
					screen.small(g, Component.literal("/"), iconX - 1, row + 4, INK_SOFT);
					iconX += 5;
				}
				first = false;
				for (int i = 0; i < option.requirements().size(); i++) {
					if (i > 0) {
						screen.small(g, Component.literal("+"), iconX - 1, row + 4, INK_SOFT);
						iconX += 4;
					}
					smallItem(g, option.requirements().get(i).displayStack(), iconX, row + 1);
					iconX += 13;
				}
				String percent = "+" + option.percent() + "%";
				screen.small(g, Component.literal(percent), iconX, row + 4, INK_SOFT);
				iconX += Math.round(screen.font.width(percent) * SMALL) + 3;
			}
		}

		@Override
		@Nullable Object tooltip(int x, int y, int mouseX, int mouseY) {
			return this.sealed ? null : GuideText.upgradeTooltip(this.fit.upgrade());
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
