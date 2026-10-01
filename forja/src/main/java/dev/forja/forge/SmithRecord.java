package dev.forja.forge;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What a smith has actually done, kept so the book can say it back to them: pieces born on the star,
 * presses landed clean, and upgrades worked into something. Synced to its own player, because the page
 * that shows it is drawn on the client.
 */
public final class SmithRecord {
	/** Pieces that have left the star. */
	public static final AttachmentType<Integer> FORGED = counter("forjadas");

	/** Of those, the ones born of a perfect press. */
	public static final AttachmentType<Integer> PERFECT = counter("perfectas");

	/** Upgrades worked into gear, counting each application once. */
	public static final AttachmentType<Integer> UPGRADED = counter("mejoradas");

	private SmithRecord() {
	}

	private static AttachmentType<Integer> counter(String name) {
		return AttachmentRegistry.<Integer>builder()
			.persistent(Codec.INT)
			.copyOnDeath()
			.initializer(() -> 0)
			.syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(Forja.id(name));
	}

	/** Touched at start-up so the three attachments exist on both sides before a world loads. */
	public static void register() {
		assert FORGED != null && PERFECT != null && UPGRADED != null;
	}

	public static int count(@Nullable Player player, AttachmentType<Integer> what) {
		return player == null ? 0 : player.getAttachedOrElse(what, 0);
	}

	/** One more of whatever this is, on the server. */
	public static void add(Player player, AttachmentType<Integer> what) {
		if (player instanceof ServerPlayer smith) {
			smith.setAttached(what, count(player, what) + 1);
		}
	}
}
