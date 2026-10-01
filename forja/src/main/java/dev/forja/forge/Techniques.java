package dev.forja.forge;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Which techniques a smith has taken. One per tier, kept as a handful of bits on the player and synced
 * to them, because the forge screen has to know: the press window it draws depends on it.
 */
public final class Techniques {
	public static final AttachmentType<Integer> LEARNED = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.copyOnDeath()
		.initializer(() -> 0)
		.syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Forja.id("tecnicas"));

	private Techniques() {
	}

	/** Touched at start-up so the attachment is registered before any world is loaded. */
	public static void register() {
		assert LEARNED != null;
	}

	public static int mask(@Nullable Player player) {
		return player == null ? 0 : player.getAttachedOrElse(LEARNED, 0);
	}

	public static boolean has(@Nullable Player player, Technique technique) {
		return (mask(player) & 1 << technique.ordinal()) != 0;
	}

	/** The one taken in a tier, or nothing if that choice is still open. */
	public static @Nullable Technique chosen(@Nullable Player player, int tier) {
		for (Technique technique : Technique.ofTier(tier)) {
			if (has(player, technique)) {
				return technique;
			}
		}
		return null;
	}

	/** Whether this one can be taken right now: the Maestria is there and the tier is still open. */
	public static boolean canLearn(Player player, Technique technique) {
		return SmithLevel.level(player) >= technique.levelNeeded() && chosen(player, technique.tier) == null;
	}

	/** How many choices are open and waiting to be made. */
	public static int pending(Player player) {
		int level = SmithLevel.level(player);
		int open = 0;
		for (int tier = 1; tier <= Technique.TIERS; tier++) {
			if (level >= Technique.levelFor(tier) && chosen(player, tier) == null) {
				open++;
			}
		}
		return open;
	}

	/** Takes the technique, if it can be taken. Says so, because the choice cannot be undone. */
	public static boolean learn(Player player, Technique technique) {
		if (!(player instanceof ServerPlayer server) || !canLearn(player, technique)) {
			return false;
		}
		server.setAttached(LEARNED, mask(player) | 1 << technique.ordinal());
		server.sendSystemMessage(Component.translatable("gui.forja.tecnica.aprendida", technique.displayName()).withColor(0xF0C070));
		dev.forja.ForjaAdvancements.award(server, "tecnica");
		return true;
	}
}
