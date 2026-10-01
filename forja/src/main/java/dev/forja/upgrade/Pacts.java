package dev.forja.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import dev.forja.registry.ModComponents;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What a pact costs before it costs anything: "pactos 2 por objeto", and each one has to be opened first.
 *
 * <p>A pact is sealed until the smith offers it something rare, once: the offering goes on the star with
 * the pact's own ingredients the first time, is taken, and from then on that pact is open to that smith
 * for good. It is the smith who learns it, not the table and not the piece, so it follows the player
 * through death and is synced to them, because the forge screen has to say what is missing.
 */
public final class Pacts {
	/** At most this many pacts on one piece. */
	public static final int MOST = 2;

	/** The rare thing each pact asks for the first time. */
	private static final Map<Upgrade, Item> OFFERINGS = Map.of(
		Upgrade.PACTO_DE_SED, Items.TOTEM_OF_UNDYING,
		Upgrade.PACTO_DE_VIDRIO, Items.NETHER_STAR,
		Upgrade.PACTO_DE_SOMBRA, Items.ECHO_SHARD,
		Upgrade.PACTO_DE_LA_PRISA, Items.HEART_OF_THE_SEA
	);

	/** The pacts a smith has opened, by name, kept on the player. */
	@SuppressWarnings("deprecation")
	public static final AttachmentType<List<String>> UNLOCKED = AttachmentRegistry.<List<String>>builder()
		.initializer(List::of)
		.persistent(Codec.STRING.listOf())
		.copyOnDeath()
		.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Forja.id("pactos"));

	private Pacts() {
	}

	/** Touched at start-up so the attachment is registered before any world is loaded. */
	public static void register() {
		java.util.Objects.requireNonNull(UNLOCKED);
	}

	/** What opens this pact, or null for an upgrade that is not one. */
	public static @Nullable Item offering(Upgrade pact) {
		return OFFERINGS.get(pact);
	}

	/**
	 * Whether this smith may pour the pact. No smith at all is loot, commands and the tests, which never
	 * had to open anything.
	 */
	public static boolean unlocked(@Nullable Player player, Upgrade pact) {
		return player == null || !pact.isPact() || player.getAttachedOrElse(UNLOCKED, List.of()).contains(pact.name());
	}

	public static void unlock(Player player, Upgrade pact) {
		if (unlocked(player, pact)) {
			return;
		}
		List<String> opened = new ArrayList<>(player.getAttachedOrElse(UNLOCKED, List.of()));
		opened.add(pact.name());
		player.setAttached(UNLOCKED, List.copyOf(opened));
	}

	/** The sealed pact this stack would open for this smith, or null if it opens nothing they still need. */
	public static @Nullable Upgrade opens(@Nullable Player player, ItemStack stack) {
		for (Map.Entry<Upgrade, Item> entry : OFFERINGS.entrySet()) {
			if (stack.is(entry.getValue()) && !unlocked(player, entry.getKey())) {
				return entry.getKey();
			}
		}
		return null;
	}

	public static int count(Upgrades upgrades) {
		int pacts = 0;
		for (Upgrade upgrade : upgrades.percents().keySet()) {
			if (upgrade.isPact()) {
				pacts++;
			}
		}
		return pacts;
	}

	/** Whether this upgrade still fits among the pacts already on the piece: always, unless it is a new one past the limit. */
	public static boolean fits(Upgrades upgrades, Upgrade upgrade) {
		return !upgrade.isPact() || upgrades.percent(upgrade) > 0 || count(upgrades) < MOST;
	}

	public static boolean fits(ItemStack stack, Upgrade upgrade) {
		return fits(stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY), upgrade);
	}
}
