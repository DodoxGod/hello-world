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
 * The Maestria of the smith, as opposed to the Maestria of a single piece. Forging, swapping parts,
 * upgrading and engraving all teach you something, and what you learn follows you: your presses land
 * cleanly more often, your upgrades take a little better, and what leaves your star is already
 * broken in.
 */
public final class SmithLevel {
	public static final int MAX_LEVEL = 10;

	/** Experience for the first level; each one after that costs more than the last. */
	public static final int STEP = 40;

	/** What each thing done at the star teaches. */
	public static final int XP_FORGE = 10;
	public static final int XP_SWAP = 4;
	public static final int XP_UPGRADE = 8;
	public static final int XP_PERFECT = 15;
	public static final int XP_GIFT = 25;

	/** Synced to its own player, so the forge screen can widen the press window on the client. */
	public static final AttachmentType<Integer> EXPERIENCE = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.copyOnDeath()
		.initializer(() -> 0)
		.syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Forja.id("herrero"));

	private SmithLevel() {
	}

	/** Touched at start-up so the attachment is registered before any world is loaded. */
	public static void register() {
		assert EXPERIENCE != null;
	}

	public static int experience(@Nullable Player player) {
		return player == null ? 0 : player.getAttachedOrElse(EXPERIENCE, 0);
	}

	/** The level a given amount of experience buys, from 0 to ten. */
	public static int levelOf(int experience) {
		int level = (int) Math.floor(Math.sqrt(experience / (double) STEP));
		return Math.min(MAX_LEVEL, level);
	}

	public static int level(@Nullable Player player) {
		return levelOf(experience(player));
	}

	/** Experience still to go for the next level, or zero at the top. */
	public static int toNextLevel(Player player) {
		int level = level(player);
		if (level >= MAX_LEVEL) {
			return 0;
		}
		return (level + 1) * (level + 1) * STEP - experience(player);
	}

	/** Teaches the smith something, and says so when a level lands. */
	public static void award(Player player, int amount) {
		if (!(player instanceof ServerPlayer server) || amount <= 0) {
			return;
		}
		int before = level(player);
		server.setAttached(EXPERIENCE, experience(player) + amount);
		int after = level(player);
		if (after > before) {
			server.sendSystemMessage(Component.translatable("gui.forja.herrero.sube", after));
			// A level that opens a tier is worth saying out loud: the choice is waiting at the table.
			if (after % Technique.LEVEL_STEP == 0 && after <= Technique.levelFor(Technique.TIERS)) {
				server.sendSystemMessage(Component.translatable("gui.forja.herrero.tecnica").withColor(0xF0C070));
			}
		}
	}

	/** The extra share an upgrade takes at the hands of a practised smith. */
	public static int upgradeBonus(@Nullable Player player) {
		return level(player) / 2;
	}

	/** How much Maestria a piece leaves the star already carrying. */
	public static int masteryHeadStart(@Nullable Player player) {
		return level(player) * 20;
	}

	public static Component describe(Player player) {
		int level = level(player);
		return level >= MAX_LEVEL
			? Component.translatable("gui.forja.herrero.maximo", level)
			: Component.translatable("gui.forja.herrero", level, toNextLevel(player));
	}
}
