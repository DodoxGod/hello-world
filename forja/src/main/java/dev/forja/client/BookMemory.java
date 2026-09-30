package dev.forja.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.forja.GuideBooks;
import net.fabricmc.loader.api.FabricLoader;

/**
 * What this client remembers of each book (docs/LIBROS_GUIA.md, 4): whether it has been opened, the page the
 * reader left it on, and the furthest page seen, for the covers' "leído" bar, the red seal on a book never
 * opened and "Seguir leyendo".
 *
 * <p>Kept on the client in config/forja/libros_leidos.json, because it is about the person reading and not about
 * the world: nothing on the server needs it, and a book read in one world has been read.
 */
public final class BookMemory {
	/** Per book: [opened (0 or 1), last page, furthest page]. */
	private static final Map<GuideBooks.Book, int[]> READ = new EnumMap<>(GuideBooks.Book.class);
	private static boolean loaded;
	private static boolean dirty;

	private BookMemory() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("forja/libros_leidos.json");
	}

	private static int[] of(GuideBooks.Book book) {
		load();
		return READ.computeIfAbsent(book, key -> new int[3]);
	}

	/** The book was opened now. */
	public static void opened(GuideBooks.Book book) {
		int[] read = of(book);
		if (read[0] == 0) {
			read[0] = 1;
			dirty = true;
		}
	}

	public static boolean wasOpened(GuideBooks.Book book) {
		return of(book)[0] != 0;
	}

	/** A page of the book is on screen: it becomes the bookmark, and the furthest seen if it is further. */
	public static void saw(GuideBooks.Book book, int page, int pages) {
		int[] read = of(book);
		if (read[1] != page) {
			read[1] = page;
			dirty = true;
		}
		int last = Math.min(page, Math.max(0, pages - 1));
		if (last > read[2]) {
			read[2] = last;
			dirty = true;
		}
	}

	/** The page to go back to, or 0 for a book never read past its cover. */
	public static int bookmark(GuideBooks.Book book) {
		return of(book)[1];
	}

	/** How much of the book has been read, from 0 to 1: the furthest page seen of how many there are. */
	public static float readShare(GuideBooks.Book book, int pages) {
		if (pages <= 1) {
			return 0.0F;
		}
		return Math.min(1.0F, of(book)[2] / (float) (pages - 1));
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = file();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			JsonObject all = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			for (GuideBooks.Book book : GuideBooks.Book.values()) {
				if (all.has(book.key()) && all.get(book.key()).isJsonArray() && all.getAsJsonArray(book.key()).size() == 3) {
					var array = all.getAsJsonArray(book.key());
					READ.put(book, new int[] {array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt()});
				}
			}
		} catch (Exception unreadable) {
			// A file that cannot be read is a reader starting again, not a crash.
			READ.clear();
		}
	}

	/** Written when a book is closed, and only if something changed. */
	public static void save() {
		if (!dirty) {
			return;
		}
		dirty = false;
		JsonObject all = new JsonObject();
		for (Map.Entry<GuideBooks.Book, int[]> entry : READ.entrySet()) {
			var array = new com.google.gson.JsonArray();
			for (int value : entry.getValue()) {
				array.add(value);
			}
			all.add(entry.getKey().key(), array);
		}
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), all.toString(), StandardCharsets.UTF_8);
		} catch (java.io.IOException ignored) {
			// Not being able to remember a page is not worth interrupting anyone over.
		}
	}
}
