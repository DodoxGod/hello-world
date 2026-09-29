package dev.forja.clase;

import dev.forja.Forja;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A player's class, level, experience and talents (docs/CLASES.md): the attachment that holds them, and
 * everything that changes them. The attachment is persistent, kept through death and synced to its own
 * player, whose screens and dodge read it.
 */
public final class ClassProgress {
	public static final int MAX_LEVEL = 15;
	/** Experience from level N to N+1: {@code FIRST_STEP + STEP_GROWTH * (N - 1)}. */
	public static final int FIRST_STEP = 80;
	public static final int STEP_GROWTH = 40;
	/** "Cada nivel da puntos": one per level, the first included. */
	public static final int POINTS_PER_LEVEL = 1;

	/**
	 * The class keys (Andy, 2026-09-29: K, V and B by default, rebindable). client/ClassClient registers a
	 * KeyMapping under each name in the Forja category, and every text that names one of them does it with
	 * {@link #key}, which the client fills with whatever the player has bound — never with a letter.
	 */
	public static final String KEY_TREE = "key.forja.clase_arbol";
	public static final String KEY_SKILL_1 = "key.forja.habilidad_1";
	public static final String KEY_SKILL_2 = "key.forja.habilidad_2";
	/** The guide's key (client/ForjaClient), named in the reminder to pick a class. */
	public static final String KEY_GUIDE = "key.forja.guia";

	/** A key as the player has it bound, for a text: resolved on the client, so it follows the Controls screen. */
	public static Component key(String name) {
		return Component.keybind(name);
	}

	public static final AttachmentType<ClassData> DATA = AttachmentRegistry.create(Forja.id("clase"), builder -> builder
		.persistent(ClassData.CODEC)
		.copyOnDeath()
		.initializer(() -> ClassData.NONE)
		.syncWith(ClassData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private ClassProgress() {
	}

	/** Touched at start-up so the attachment is registered on both sides before any world is loaded. */
	public static void register() {
		assert DATA != null;
	}

	public static ClassData data(@Nullable Player player) {
		return player == null ? ClassData.NONE : player.getAttachedOrElse(DATA, ClassData.NONE);
	}

	public static @Nullable PlayerClass clazz(@Nullable Player player) {
		return data(player).playerClass();
	}

	public static boolean has(@Nullable Player player, Talent talent) {
		return data(player).has(talent);
	}

	/** Experience needed to go from this level to the next. */
	public static int step(int level) {
		return FIRST_STEP + STEP_GROWTH * (Math.max(1, level) - 1);
	}

	/** Experience in total to reach a level from nothing (level 1 is 0). */
	public static int totalFor(int level) {
		int total = 0;
		for (int l = 1; l < level; l++) {
			total += step(l);
		}
		return total;
	}

	public static int pointsAt(int level) {
		return Math.max(0, level) * POINTS_PER_LEVEL;
	}

	/** Writes the data and puts the attributes on to match. */
	public static void set(ServerPlayer player, ClassData data) {
		player.setAttached(DATA, data);
		ClassAttributes.sync(player);
	}

	/**
	 * Takes a class. The first time is free; after that it is a change, which the caller has paid for (the
	 * Medallón del olvido, or a command). Andy (2026-09-29): a change does not keep the level — the new class
	 * starts at level 1 with no experience, like the first one; only the count of changes is carried over.
	 */
	public static void choose(ServerPlayer player, PlayerClass chosen) {
		ClassData before = data(player);
		if (before.playerClass() == chosen) {
			// The medallion on your own class is not a change: the tree is emptied and the level stays.
			resetTalents(player);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
			player.sendSystemMessage(Component.translatable("gui.forja.clase.reiniciada", chosen.displayName()));
			// The choice screen moves on to the emptied tree, as after a change.
			ClassNetwork.toast(player, ClassNetwork.Toast.CHOSEN, chosen, before.level());
			return;
		}
		boolean change = before.playerClass() != null;
		int level = 1;
		int xp = 0;
		set(player, new ClassData(chosen.id(), level, xp, 0, 0L, 0L, before.changes() + (change ? 1 : 0)));
		ClassEffects.forget(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.sendSystemMessage(change ? Component.translatable("gui.forja.clase.cambiada", chosen.displayName())
			: Component.translatable("gui.forja.clase.elegida", chosen.displayName(), key(KEY_TREE)));
		ClassNetwork.toast(player, ClassNetwork.Toast.CHOSEN, chosen, level);
		dev.forja.ForjaAdvancements.award(player, "clase");
	}

	/** Leaves the player without a class (commands only). */
	public static void clear(ServerPlayer player) {
		set(player, ClassData.NONE);
		ClassEffects.forget(player);
	}

	/**
	 * Class experience. Nothing without a class; levels are said out loud — a sound, a toast and a line in
	 * the chat — and each brings its point.
	 */
	public static void award(Player player, int amount) {
		if (!(player instanceof ServerPlayer server) || amount <= 0) {
			return;
		}
		ClassData data = data(player);
		PlayerClass owner = data.playerClass();
		if (owner == null || data.level() >= MAX_LEVEL) {
			return;
		}
		int level = data.level();
		int xp = data.xp() + amount;
		while (level < MAX_LEVEL && xp >= totalFor(level + 1)) {
			level++;
		}
		if (level >= MAX_LEVEL) {
			xp = totalFor(MAX_LEVEL);
		}
		server.setAttached(DATA, data.withLevel(level, xp));
		if (level > data.level()) {
			server.level().playSound(null, server.getX(), server.getY(), server.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 1.1F);
			server.sendSystemMessage(Component.translatable("gui.forja.clase.sube", owner.displayName(), level, data(player).points())
				.withColor(0xFFF0C070));
			ClassNetwork.toast(server, ClassNetwork.Toast.LEVEL, owner, level);
		}
	}

	/** Sets the level outright, and the experience to its start (commands and tests). */
	public static void setLevel(ServerPlayer player, int level) {
		ClassData data = data(player);
		int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
		ClassData next = data.withLevel(clamped, totalFor(clamped));
		// Fewer points than are spent: the tree is emptied rather than left owing.
		if (next.spent() > pointsAt(clamped)) {
			next = next.withMask(0);
		}
		set(player, next);
	}

	public enum Refusal {
		NONE,
		NO_CLASS,
		OTHER_CLASS,
		ALREADY,
		PREREQUISITE,
		POINTS
	}

	/** Why this talent cannot be learned right now, or NONE if it can. */
	public static Refusal check(Player player, Talent talent) {
		ClassData data = data(player);
		if (data.playerClass() == null) {
			return Refusal.NO_CLASS;
		}
		if (talent.owner != data.playerClass()) {
			return Refusal.OTHER_CLASS;
		}
		if (data.has(talent)) {
			return Refusal.ALREADY;
		}
		if (!talent.prerequisitesMet(data.mask())) {
			return Refusal.PREREQUISITE;
		}
		if (data.points() < talent.cost()) {
			return Refusal.POINTS;
		}
		return Refusal.NONE;
	}

	public static boolean unlock(ServerPlayer player, Talent talent) {
		if (check(player, talent) != Refusal.NONE) {
			return false;
		}
		ClassData data = data(player);
		set(player, data.withMask(data.mask() | 1 << talent.index()));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 1.3F);
		player.sendSystemMessage(Component.translatable("gui.forja.talento.aprendido", talent.displayName()).withColor(0xFFF0C070));
		return true;
	}

	/** Empties the tree and gives every point back (the emblem on your own class, or a command). */
	public static void resetTalents(ServerPlayer player) {
		set(player, data(player).withMask(0));
		ClassEffects.forget(player);
	}
}
