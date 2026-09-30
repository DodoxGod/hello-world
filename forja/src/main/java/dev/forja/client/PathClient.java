package dev.forja.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.forja.Forja;
import dev.forja.ForjaConfig;
import dev.forja.ForjaPath;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The guide's path as this client sees it: which forja/ advancements the server has said are done,
 * for the book's "Siguiente paso" page, and the one line in chat when the path moves on.
 *
 * <p>Read from the advancements the client already keeps, rather than from a packet of the mod's own:
 * the server sends every advancement that is done, so what the advancement screen knows, the book knows.
 */
public final class PathClient {
	/** What the advancements mixin exposes of ClientAdvancements: its progress, which vanilla keeps private. */
	public interface Progress {
		Map<AdvancementHolder, AdvancementProgress> forja$progress();
	}

	/**
	 * Run after every advancements packet: what learned book recipes a recipe viewer may show changes with them
	 * (compat/ForjaJeiPlugin sets it when JEI is there; without it, nothing).
	 */
	public static Runnable onProgress = () -> {
	};

	/** How many lines the path has put in chat since the game started, and the last of them, for the client test. */
	public static int hintsShown;
	public static @Nullable Component lastHint;

	private PathClient() {
	}

	/** The ids under forja/ of the advancements this progress has done. */
	public static Set<String> done(Map<AdvancementHolder, AdvancementProgress> progress) {
		Set<String> done = new HashSet<>();
		for (Map.Entry<AdvancementHolder, AdvancementProgress> entry : progress.entrySet()) {
			Identifier id = entry.getKey().id();
			if (id.getNamespace().equals(Forja.MOD_ID) && id.getPath().startsWith("forja/") && entry.getValue().isDone()) {
				done.add(id.getPath().substring("forja/".length()));
			}
		}
		return done;
	}

	/** What the local player has done of the forja/ advancements; nothing at all outside a world. */
	public static Set<String> done() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return Set.of();
		}
		return done(((Progress) connection.getAdvancements()).forja$progress());
	}

	/** The step the player is on now. */
	public static ForjaPath.@Nullable Step next() {
		return ForjaPath.next(done()::contains);
	}

	/**
	 * Called after every advancements packet, with the next step as it stood before the packet and after.
	 *
	 * <p>The first packet of a world, and the one after a /reload, is the whole list sent again: nothing
	 * was earned just then, so it says nothing. Anything else speaks only when the path moved forward,
	 * which is what keeps it to one line per step and never the same line twice.
	 */
	public static void updated(ForjaPath.@Nullable Step before, ForjaPath.@Nullable Step after, boolean reset) {
		onProgress.run();
		if (reset || !ForjaPath.worthAHint(before, after) || !ForjaConfig.get().pistas) {
			return;
		}
		Component hint = ForjaPath.hint(after);
		Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(hint);
		hintsShown++;
		lastHint = hint;
	}
}
