package dev.forja;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import dev.forja.registry.ModItems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

/**
 * The guide as a shelf of short books (docs/LIBROS_GUIA.md) rather than one tome of 283 pages.
 *
 * <p>Andy, 2026-09-29: "Ver un libro que tiene más de 200 páginas termina asustando, y no necesitas saber todo al
 * momento 0 del juego". So each book explains one stretch of the game, and each is <b>crafted</b> — a book and one
 * thing out of what it explains — from a recipe the player <b>learns</b> by getting there: the advancement named
 * in {@link Book#unlock}. Until then the crafting grid refuses it (mixin/CraftingMenuMixin), and the starter
 * notebook shows every recipe and when it is learned. Only the notebook is given, once, on first join; it can
 * always be crafted again.
 *
 * <p>This is the common half: which books there are, what goes into them (chapter keys of the guide screen), and
 * who may craft which. The pages themselves are client/GuideBookScreen.
 */
public final class GuideBooks {
	/** One tab of a book: its key (the index heading is gui.forja.libro.seccion.KEY), its tab colour and its chapters. */
	public record Section(String key, int colour, List<String> chapters) {
	}

	public enum Book {
		CUADERNO("guia_de_forja", 0xC9A465, null, () -> Items.IRON_INGOT, true, true,
			List.of(new Section("cuaderno", 0, List.of("bienvenida", "primeras_mesas", "teclas")),
				new Section("camino", 3, List.of("siguiente_paso", "estanteria")))),
		YUNQUE("libro_yunque", 0xC8641E, "plantilla", () -> ModItems.PLANTILLA, true, true,
			List.of(new Section("yunque_taller", 0, List.of("yunque_sabes", "mesas", "cortar", "estrella", "temple")),
				new Section("yunque_mejorar", 1, List.of("mejorar", "probador", "desarmar", "estadisticas", "yunque_siguiente")))),
		// The six below are designed (docs/LIBROS_GUIA.md, 2.3) and come one delivery at a time; until then their
		// chapters are read in the creative tome, and the notebook shows them as coming. The chapter lists are the
		// first cut of 2.4, so a link to one of them can already say which book it will be in.
		// Book II (Andy): combat, stamina, magic and the enemies in one book, bigger than the rest, in five parts.
		COMBATE("libro_combate", 0xA8322C, "forja", () -> Items.BONE, true, true,
			List.of(new Section("combate_cuerpo", 2, List.of("tu_cuerpo", "golpear", "armas", "defenderse")),
				new Section("combate_magia", 1, List.of("magia", "mana")),
				new Section("combate_enemigos", 4, List.of("como_pelean", "rangos", "peleas_mundo", "dificultad")),
				new Section("combate_cielo", 3, List.of("eventos")),
				new Section("combate_bestiario", 0, List.of("bestiario", "combate_siguiente")))),
		// Book III: heat, the first alloys, the whole foundry line and the greater table at the end of it.
		FUNDICION("libro_fundicion", 0x7A4A2A, "mejora", () -> Items.COPPER_INGOT, true, true,
			List.of(new Section("fundicion_calor", 0, List.of("fundicion_sabes", "primeras_aleaciones")),
				new Section("fundicion_linea", 4, List.of("fundicion")),
				new Section("fundicion_mayor", 1, List.of("mesa_mayor", "fundicion_siguiente")))),
		// Book IV: what a piece can become, and what the smith becomes.
		MESA_MAYOR("libro_mesa_mayor", 0x7850BE, "mesa_mayor", () -> ModItems.alloy("damasco"), true, true,
			List.of(new Section("mayor_mejoras", 1, List.of("mayor_sabes", "potencial", "sinergias", "pactos")),
				new Section("mayor_maestria", 0, List.of("maestria", "herrero", "tecnicas", "mi_taller")),
				new Section("mayor_llevar", 3, List.of("accesorios", "mayor_siguiente")))),
		// Book V: the classes, which open the first time this book is opened (Andy's answer 3).
		CLASES("libro_clases", 0x3C7A4A, "parada", () -> Items.EMERALD, true, true,
			List.of(new Section("clases_elegir", 2, List.of("clases_sabes", "clases", "clases_siguiente")))),
		// Book VI: going out into the world, the Smith's story and his castle. The Forjador sells it at level 3.
		BASTION("libro_bastion", 0x28827F, "ruina", () -> Items.MAP, true, true,
			List.of(new Section("bastion_mundo", 3, List.of("bastion_sabes", "mundo", "encargos")),
				new Section("bastion_ruinas", 4, List.of("ruinas", "herrero_historia")),
				new Section("bastion_castillo", 2, List.of("bastion", "portal_estelar", "bastion_siguiente")))),
		// Book VII: the Smith's world and the fight. On a lectern beside the portal when it is lit, and crafted as any.
		CEMENTERIO("libro_cementerio", 0x342658, "portal", () -> ModItems.HIERRO_ESTELAR, true, true,
			List.of(new Section("cementerio_mundo", 1, List.of("cementerio_viaje", "cementerio_pelea")),
				new Section("cementerio_final", 2, List.of("cementerio_recompensa", "cementerio_herrero", "cementerio_fin")))),
		/** What G opens when the notebook is carried: the shelf, the path and the reference tables (the catalogue). */
		BIBLIOTECA(null, 0x8A6A3A, null, () -> Items.AIR, true, false,
			List.of(new Section("estanteria", 0, List.of("estanteria", "siguiente_paso")),
				new Section("catalogo_objetos", 4, List.of("catalogo", "objetos", "piezas")),
				new Section("catalogo_materiales", 3, List.of("materiales", "rasgos", "catalogo_aleaciones")),
				new Section("catalogo_mejoras", 1, List.of("probador", "mejoras", "estadisticas")))),
		/** The whole guide in one volume, as it was: creative only (Andy's answer 6). */
		TOMO("tomo_de_forja", 0x7A2A20, null, () -> Items.AIR, true, false,
			List.of(new Section("taller", 0, List.of("primeros_pasos", "siguiente_paso", "mesas", "mesa_mayor", "objetos", "piezas", "materiales", "rasgos",
					"aleaciones", "fundicion", "temple", "herrero", "tecnicas")),
				new Section("mejoras", 1, List.of("mejoras", "potencial", "maestria", "sinergias", "pactos")),
				new Section("pelear", 2, List.of("combate", "mana", "accesorios", "clases")),
				new Section("mundo", 3, List.of("eventos", "encargos", "amenazas", "bestiario", "mundo", "cementerio")),
				new Section("referencia", 4, List.of("mi_taller", "estadisticas"))));

		/** The item's id under forja:, which is also its recipe's; null for the library, which is not an item. */
		public final @Nullable String itemId;
		/** The cover's colour, for the cards on the shelf and the tint of the book's own marks. */
		public final int colour;
		/** The forja/ advancement that teaches its recipe; null when the recipe is always known. */
		public final @Nullable String unlock;
		/** What goes into the crafting grid with the book. */
		public final Supplier<Item> ingredient;
		/** Whether this book is written yet: an item, a recipe and pages of its own. */
		public final boolean ready;
		/** Whether it stands on the shelf (the eight books) rather than being the library or the tome. */
		public final boolean onShelf;
		public final List<Section> sections;

		Book(@Nullable String itemId, int colour, @Nullable String unlock, Supplier<Item> ingredient, boolean ready, boolean onShelf,
			List<Section> sections) {
			this.itemId = itemId;
			this.colour = colour;
			this.unlock = unlock;
			this.ingredient = ingredient;
			this.ready = ready;
			this.onShelf = onShelf;
			this.sections = sections;
		}

		/** Its key in the lang files: gui.forja.libros.KEY.titulo, .lema and .cuando. */
		public String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public Component title() {
			return Component.translatable("gui.forja.libros." + this.key() + ".titulo");
		}

		/** One line under the title: what the book is about. */
		public Component motto() {
			return Component.translatable("gui.forja.libros." + this.key() + ".lema");
		}

		/** When its recipe is learned, said the way a player would: "al grabar tu primera plantilla". */
		public Component when() {
			return Component.translatable("gui.forja.libros." + this.key() + ".cuando");
		}

		/** Roman numeral on the cover and the card: 0 for the notebook, I to VII for the rest. */
		public String numeral() {
			return switch (this) {
				case CUADERNO -> "0";
				case YUNQUE -> "I";
				case COMBATE -> "II";
				case FUNDICION -> "III";
				case MESA_MAYOR -> "IV";
				case CLASES -> "V";
				case BASTION -> "VI";
				case CEMENTERIO -> "VII";
				default -> "";
			};
		}

		/** Every chapter, in the order the book prints them. The tome keeps the order it always had. */
		public List<String> chapters() {
			if (this == TOMO) {
				return List.of("primeros_pasos", "siguiente_paso", "mesas", "objetos", "piezas", "materiales", "rasgos", "mejoras", "potencial",
					"maestria", "aleaciones", "fundicion", "mesa_mayor", "temple", "herrero", "tecnicas", "mi_taller", "sinergias", "pactos", "combate", "mana",
					"accesorios", "clases", "eventos", "encargos", "amenazas", "bestiario", "mundo", "cementerio", "estadisticas");
			}
			List<String> all = new ArrayList<>();
			for (Section section : this.sections) {
				all.addAll(section.chapters());
			}
			return all;
		}

		public @Nullable ResourceKey<Recipe<?>> recipe() {
			return this.itemId == null || !this.onShelf ? null : ResourceKey.create(Registries.RECIPE, Forja.id(this.itemId));
		}

		/** The book's item, or null while it is not written yet (and always for the library). */
		public @Nullable Item item() {
			if (!this.ready || this.itemId == null) {
				return null;
			}
			return switch (this) {
				case CUADERNO -> ModItems.GUIA_DE_FORJA;
				case YUNQUE -> ModItems.LIBRO_YUNQUE;
				case COMBATE -> ModItems.LIBRO_COMBATE;
				case FUNDICION -> ModItems.LIBRO_FUNDICION;
				case MESA_MAYOR -> ModItems.LIBRO_MESA_MAYOR;
				case CLASES -> ModItems.LIBRO_CLASES;
				case BASTION -> ModItems.LIBRO_BASTION;
				case CEMENTERIO -> ModItems.LIBRO_CEMENTERIO;
				case TOMO -> ModItems.TOMO_DE_FORJA;
				default -> null;
			};
		}
	}

	/**
	 * The advancement given the first time a player opens book V: the classes open with it (Andy, 2026-09-29: "las
	 * clases se desbloquean la primera vez que abres su libro"). Before it, K only says to read the book.
	 */
	public static final String CLASSES_READ = "leer_clases";

	/** Whether this player has opened the classes: read book V, or already has a class from before the books. */
	public static boolean classesOpen(ServerPlayer player) {
		if (dev.forja.clase.ClassProgress.clazz(player) != null) {
			return true;
		}
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(Forja.id("forja/" + CLASSES_READ));
		return holder == null || player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** The eight books of the shelf, in reading order. */
	public static final List<Book> SHELF = java.util.Arrays.stream(Book.values()).filter(book -> book.onShelf).toList();

	private GuideBooks() {
	}

	/**
	 * Which book a chapter lives in, for links that cross from one book to another: the first shelf book that has
	 * it, written or not, then the library (the catalogue), then the tome. Null for a key no book has.
	 */
	public static @Nullable Book bookOf(String chapter) {
		for (Book book : SHELF) {
			if (book.chapters().contains(chapter)) {
				return book;
			}
		}
		if (Book.BIBLIOTECA.chapters().contains(chapter)) {
			return Book.BIBLIOTECA;
		}
		return Book.TOMO.chapters().contains(chapter) ? Book.TOMO : null;
	}

	/** The book an item opens, or null. */
	public static @Nullable Book of(Item item) {
		for (Book book : Book.values()) {
			if (book.item() == item && item != null) {
				return book;
			}
		}
		return null;
	}

	/** Whether this recipe is one of the books that must be learned before the grid will make it. */
	public static @Nullable Book gatedBy(ResourceKey<Recipe<?>> recipe) {
		for (Book book : SHELF) {
			if (book.unlock != null && book.ready && recipe.equals(book.recipe())) {
				return book;
			}
		}
		return null;
	}

	/** False when the crafting grid must refuse this recipe for this player: a book whose recipe they have not learned. */
	public static boolean mayCraft(ServerPlayer player, RecipeHolder<?> recipe) {
		return gatedBy(recipe.id()) == null || player.getRecipeBook().contains(recipe.id());
	}

	/** An advancement of the forja tab was just completed: teach the recipes it opens. */
	public static void completed(ServerPlayer player, AdvancementHolder holder) {
		Identifier id = holder.id();
		if (!id.getNamespace().equals(Forja.MOD_ID) || !id.getPath().startsWith("forja/")) {
			return;
		}
		String key = id.getPath().substring("forja/".length());
		for (Book book : SHELF) {
			if (book.ready && key.equals(book.unlock)) {
				teach(player, book);
			}
		}
	}

	/**
	 * On joining: every recipe the player's advancements already earn. For worlds from before the books, where the
	 * advancements were done while there was nothing to learn, and harmless every other time (a known recipe is
	 * not taught twice).
	 */
	public static void catchUp(ServerPlayer player) {
		var advancements = player.level().getServer().getAdvancements();
		// Whoever took a class before the books has read book V as far as the classes are concerned.
		if (dev.forja.clase.ClassProgress.clazz(player) != null) {
			ForjaAdvancements.award(player, CLASSES_READ);
		}
		for (Book book : SHELF) {
			if (!book.ready || book.unlock == null) {
				continue;
			}
			AdvancementHolder holder = advancements.get(Forja.id("forja/" + book.unlock));
			if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
				teach(player, book);
			}
		}
	}

	private static void teach(ServerPlayer player, Book book) {
		ResourceKey<Recipe<?>> recipe = book.recipe();
		if (recipe == null || player.getRecipeBook().contains(recipe)) {
			return;
		}
		if (player.connection == null) {
			// A player the tests build without a connection: the book's own add sends a packet to nobody.
			player.getRecipeBook().add(recipe);
			return;
		}
		player.awardRecipesByKey(List.of(recipe));
	}
}
