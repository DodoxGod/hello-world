package dev.forja.clase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * A player's class, level, experience, tree and milestones (docs/CLASES.md, docs/ARBOLES.md): the attachment
 * that holds them, and everything that changes them. The attachment is persistent, kept through death and
 * synced to its own player, whose screens and dodge read it.
 *
 * <p>The cap, the points a level gives and the experience curve are the tree file's ({@link ClassTree}): Andy
 * (2026-09-30) wanted about 87 % of a tree bought at the top, with a higher cap and fewer points a level.
 */
public final class ClassProgress {
	public static final int MAX_LEVEL = ClassTree.maxLevel();

	/**
	 * The class keys (Andy, 2026-09-29: K, V and B by default, and N for the third skill since the big trees;
	 * all rebindable). client/ClassClient registers a KeyMapping under each name in the Forja category, and every
	 * text that names one of them does it with {@link #key}, which the client fills with whatever the player has
	 * bound — never with a letter.
	 */
	public static final String KEY_TREE = "key.forja.clase_arbol";
	public static final String KEY_SKILL_1 = "key.forja.habilidad_1";
	public static final String KEY_SKILL_2 = "key.forja.habilidad_2";
	public static final String KEY_SKILL_3 = "key.forja.habilidad_3";
	/** The guide's key (client/ForjaClient), named in the reminder to pick a class. */
	public static final String KEY_GUIDE = "key.forja.guia";

	/** A key as the player has it bound, for a text: resolved on the client, so it follows the Controls screen. */
	public static Component key(String name) {
		return Component.keybind(name);
	}

	public static String skillKey(int slot) {
		return slot == 1 ? KEY_SKILL_1 : slot == 2 ? KEY_SKILL_2 : KEY_SKILL_3;
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

	public static boolean has(@Nullable Player player, String node) {
		return data(player).has(node);
	}

	/** Experience needed to go from this level to the next. */
	public static int step(int level) {
		return ClassTree.firstStep() + ClassTree.stepGrowth() * (Math.max(1, level) - 1);
	}

	/** Experience in total to reach a level from nothing (level 1 is 0). */
	public static int totalFor(int level) {
		int total = 0;
		for (int l = 1; l < level; l++) {
			total += step(l);
		}
		return total;
	}

	/** Writes the data and puts the attributes on to match. */
	public static void set(ServerPlayer player, ClassData data) {
		player.setAttached(DATA, data);
		ClassAttributes.sync(player);
	}

	/**
	 * A save from before the big trees (docs/ARBOLES.md, "Migración"): its old talents go and their points come
	 * back by themselves; a Herrero — a class that no longer exists — is left with no class to choose again.
	 * Andy (2026-09-30): nobody plays the mod yet, so nothing more than that. Run when the player joins.
	 */
	public static boolean migrate(ServerPlayer player) {
		ClassData data = data(player);
		if (data.version() >= ClassData.VERSION && (data.clazz().isEmpty() || data.playerClass() != null)) {
			return false;
		}
		boolean known = data.playerClass() != null;
		boolean hadTalents = data.legacyMask() != 0;
		set(player, data.migrated(known ? data.clazz() : "", known ? data.level() : 0, known ? data.xp() : 0));
		ClassEffects.forget(player);
		if (!known && !data.clazz().isEmpty()) {
			player.sendSystemMessage(Component.translatable("gui.forja.clase.herrero_retirado", key(KEY_TREE)).withColor(0xFFF0C070));
		} else if (hadTalents) {
			player.sendSystemMessage(Component.translatable("gui.forja.clase.arbol_nuevo", data(player).points(), key(KEY_TREE)).withColor(0xFFF0C070));
		}
		return true;
	}

	/**
	 * Takes a class. The first time is free; after that it is a change, which the caller has paid for (the
	 * Medallón del olvido, or a command). Andy (2026-09-29): a change does not keep the level — the new class
	 * starts at level 1 with no experience, like the first one; only the count of changes and the milestones,
	 * which are the player's, are carried over.
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
		set(player, before.withClass(chosen.id(), level, 0, before.changes() + (change ? 1 : 0)));
		ClassEffects.forget(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.sendSystemMessage(change ? Component.translatable("gui.forja.clase.cambiada", chosen.displayName())
			: Component.translatable("gui.forja.clase.elegida", chosen.displayName(), key(KEY_TREE)));
		ClassNetwork.toast(player, ClassNetwork.Toast.CHOSEN, chosen, level);
		dev.forja.ForjaAdvancements.award(player, "clase");
	}

	/** Leaves the player without a class (commands only). The milestones stay: they are the player's. */
	public static void clear(ServerPlayer player) {
		set(player, data(player).withClass("", 0, 0, data(player).changes()));
		ClassEffects.forget(player);
	}

	/**
	 * Class experience. Nothing without a class; levels are said out loud — a sound, a toast and a line in
	 * the chat — and each brings its points.
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
		if (next.spent() > next.earned()) {
			next = next.withNodes(List.of());
		}
		set(player, next);
	}

	// ------------------------------------------------------------------ the tree

	public enum Refusal {
		NONE,
		NO_CLASS,
		OTHER_CLASS,
		ALREADY,
		/** Not next to anything learned. */
		PREREQUISITE,
		/** The other keystone of its branch is learned. */
		EXCLUDED,
		POINTS
	}

	/** Why this node cannot be learned on top of what {@code data} has, or NONE if it can. */
	public static Refusal check(ClassData data, String id) {
		ClassTree.Tree tree = data.tree();
		if (tree == null) {
			return Refusal.NO_CLASS;
		}
		ClassTree.Node node = tree.node(id);
		if (node == null) {
			return Refusal.OTHER_CLASS;
		}
		if (node.kind == ClassTree.Kind.ORIGEN || data.has(id)) {
			return Refusal.ALREADY;
		}
		if (node.excludes != null && data.has(node.excludes)) {
			return Refusal.EXCLUDED;
		}
		if (!reachable(tree, data, node)) {
			return Refusal.PREREQUISITE;
		}
		if (data.points() < node.cost) {
			return Refusal.POINTS;
		}
		return Refusal.NONE;
	}

	public static Refusal check(Player player, String id) {
		return check(data(player), id);
	}

	/** Whether a node touches the origin or something learned. */
	public static boolean reachable(ClassTree.Tree tree, ClassData data, ClassTree.Node node) {
		for (String link : node.links) {
			ClassTree.Node other = tree.node(link);
			if (other != null && (other.kind == ClassTree.Kind.ORIGEN || data.has(link))) {
				return true;
			}
		}
		return false;
	}

	public static boolean unlock(ServerPlayer player, String id) {
		if (check(player, id) != Refusal.NONE) {
			return false;
		}
		ClassData data = data(player);
		set(player, data.plus(id));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 1.3F);
		ClassTree.Node node = data.tree().node(id);
		player.sendOverlayMessage(Component.translatable("gui.forja.talento.aprendido", node.displayName(data.playerClass())).withColor(0xFFF0C070));
		return true;
	}

	/**
	 * A plan from the tree's "Probar" (a list of nodes in the order they were picked): learned one by one, each
	 * checked as it comes, stopping at the first that cannot be. Says how many were learned.
	 */
	public static int unlockAll(ServerPlayer player, List<String> plan) {
		int learned = 0;
		for (String id : plan) {
			if (check(player, id) != Refusal.NONE) {
				break;
			}
			set(player, data(player).plus(id));
			learned++;
		}
		if (learned > 0) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 1.3F);
			player.sendOverlayMessage(Component.translatable("gui.forja.arbol.aplicado", learned).withColor(0xFFF0C070));
		}
		return learned;
	}

	/** Empties the tree and gives every point back (the medallion on your own class, or a command). */
	public static void resetTalents(ServerPlayer player) {
		set(player, data(player).withNodes(List.of()));
		ClassEffects.forget(player);
	}

	/**
	 * Whether these learned nodes can go together (the Vela del olvido): all learned, none of them the origin,
	 * worth no more than {@code most} points, and what stays still all hangs from the origin.
	 */
	public static boolean canForget(ClassData data, Collection<String> ids, int most) {
		ClassTree.Tree tree = data.tree();
		if (tree == null || ids.isEmpty()) {
			return false;
		}
		int cost = 0;
		for (String id : ids) {
			ClassTree.Node node = tree.node(id);
			if (node == null || !data.has(id) || node.kind == ClassTree.Kind.ORIGEN) {
				return false;
			}
			cost += node.cost;
		}
		if (cost > most) {
			return false;
		}
		Set<String> kept = new HashSet<>(data.nodes());
		kept.removeAll(ids);
		return connected(tree, kept);
	}

	/** Whether every node in {@code owned} hangs from the origin through owned nodes. */
	public static boolean connected(ClassTree.Tree tree, Set<String> owned) {
		Set<String> seen = new HashSet<>();
		Deque<String> open = new ArrayDeque<>();
		open.add(tree.origin().id);
		while (!open.isEmpty()) {
			String id = open.poll();
			for (String link : tree.node(id).links) {
				if (owned.contains(link) && seen.add(link)) {
					open.add(link);
				}
			}
		}
		return seen.containsAll(owned);
	}

	/** Takes nodes off the tree, their points back (the Vela del olvido checks and pays first). */
	public static void forget(ServerPlayer player, Collection<String> ids) {
		ClassData data = data(player);
		List<String> kept = new ArrayList<>(data.nodes());
		kept.removeAll(ids);
		set(player, data.withNodes(kept));
		ClassEffects.forget(player);
	}

	// ------------------------------------------------------------------ milestones

	/**
	 * A milestone reached: once per player, whatever the class (or none). Its points are spendable as the level
	 * allows ({@link ClassData#usableMilestonePoints}). Said with a sound and a line in the chat.
	 */
	public static boolean reach(ServerPlayer player, String id) {
		ClassTree.Milestone milestone = ClassTree.milestone(id);
		ClassData data = data(player);
		if (milestone == null || data.hasMilestone(id)) {
			return false;
		}
		set(player, data.withMilestone(id));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.2F);
		ClassData after = data(player);
		player.sendSystemMessage(Component.translatable(after.waitingMilestonePoints() > 0 ? "gui.forja.hito.logrado_espera" : "gui.forja.hito.logrado",
			milestone.displayName(), milestone.points(), after.waitingMilestonePoints()).withColor(0xFFF0C070));
		return true;
	}
}
