package dev.forja.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * What a monster carries besides its hands (docs/red_mob_v4_diseno.md §4.8; Andy, 2026-09-29): potions to drink and to
 * throw, food, ender pearls and wind charges, as counts by kind, and one spare slot for a weapon or a fishing rod it
 * picked up.
 *
 * <ul>
 *   <li><b>Consumables</b> are counts kept in an attachment ({@link #KIT}, saved with the mob). They never drop when it
 *   dies (Andy's decision 6: no farm of pearls and wind charges), whether it spawned with them or picked them up.</li>
 *   <li><b>The spare</b> is the mob's {@link EquipmentSlot#BODY} slot, which no zombie or skeleton renders or wears:
 *   saved with the mob by vanilla, and dropped by vanilla's drop chances. Whatever it picked up is a guaranteed drop, so
 *   a player whose sword a zombie took always wins it back; what it spawned with keeps its own chance.</li>
 * </ul>
 *
 * <p>What a monster spawns with (§4.8), rolled once in Scaling.sizeUp for natural spawns and their packs only (a test
 * or a spawn egg brings nothing): normal 10 % a piece of food; veteran also 20 % a healing potion, 15 % a splash potion
 * and 30 % one to three wind charges; elite a healing and a splash potion, 60 % one to three wind charges and 40 % an
 * ender pearl; champion two of everything.
 */
public final class MobKit {
	/** The kinds of consumable, in the order of the C block's inventory inputs. */
	public enum Kind {
		/** A potion that heals (healing, regeneration): +4 health per level when drunk (§4.8). */
		HEAL,
		/** A potion that betters it (strength, swiftness, fire resistance): vanilla's effect for 90 s. */
		BUFF,
		/** A splash potion to throw (harming, poison, slowness, weakness). */
		SPLASH,
		/** Food: +4 health, 32 ticks eating (mobs are never hungry). */
		FOOD,
		PEARL,
		WIND
	}

	/** One stack of the kit: its kind, a variant (a potion's id, a food's item id; "" for none) and how many. */
	public record Entry(Kind kind, String variant, int count) {
		String encode() {
			return this.kind.name().toLowerCase(Locale.ROOT) + ";" + this.variant + ";" + this.count;
		}

		static Entry decode(String text) {
			String[] parts = text.split(";", -1);
			try {
				return new Entry(Kind.valueOf(parts[0].toUpperCase(Locale.ROOT)), parts[1], Integer.parseInt(parts[2]));
			} catch (RuntimeException broken) {
				return null;
			}
		}
	}

	/** The most of one kind it carries: two potions, three splash potions, food or wind charges, two pearls. */
	public static final int[] MAX = {2, 2, 3, 3, 2, 3};

	/** The kit, as its encoded entries: saved with the mob, never synced. */
	public static final AttachmentType<List<String>> KIT = AttachmentRegistry.<List<String>>builder()
		.persistent(Codec.STRING.listOf())
		.buildAndRegister(Forja.id("mochila"));

	private MobKit() {
	}

	public static List<Entry> entries(Mob mob) {
		List<String> raw = mob.getAttached(KIT);
		List<Entry> out = new ArrayList<>();
		if (raw != null) {
			for (String text : raw) {
				Entry entry = Entry.decode(text);
				if (entry != null && entry.count > 0) {
					out.add(entry);
				}
			}
		}
		return out;
	}

	private static void store(Mob mob, List<Entry> entries) {
		List<String> raw = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.count > 0) {
				raw.add(entry.encode());
			}
		}
		if (raw.isEmpty()) {
			mob.removeAttached(KIT);
		} else {
			mob.setAttached(KIT, List.copyOf(raw));
		}
	}

	/** How many of a kind it carries. */
	public static int count(Mob mob, Kind kind) {
		int n = 0;
		for (Entry entry : entries(mob)) {
			n += entry.kind == kind ? entry.count : 0;
		}
		return n;
	}

	/** All six counts at once, in {@link Kind} order (the C block reads them every decision). */
	public static int[] counts(Mob mob) {
		int[] out = new int[Kind.values().length];
		List<String> raw = mob.getAttached(KIT);
		if (raw == null) {
			return out;
		}
		for (Entry entry : entries(mob)) {
			out[entry.kind.ordinal()] += entry.count;
		}
		return out;
	}

	/** Adds {@code n} of a kind (up to its {@link #MAX}); returns how many went in. */
	public static int add(Mob mob, Kind kind, String variant, int n) {
		List<Entry> entries = entries(mob);
		int have = 0;
		for (Entry entry : entries) {
			have += entry.kind == kind ? entry.count : 0;
		}
		int room = Math.max(0, MAX[kind.ordinal()] - have);
		int in = Math.min(room, n);
		if (in <= 0) {
			return 0;
		}
		boolean merged = false;
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if (entry.kind == kind && entry.variant.equals(variant)) {
				entries.set(i, new Entry(kind, variant, entry.count + in));
				merged = true;
				break;
			}
		}
		if (!merged) {
			entries.add(new Entry(kind, variant, in));
		}
		store(mob, entries);
		return in;
	}

	/** Takes one of a kind out (the first stack of it); null when it has none. */
	public static Entry take(Mob mob, Kind kind) {
		List<Entry> entries = entries(mob);
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if (entry.kind == kind && entry.count > 0) {
				entries.set(i, new Entry(kind, entry.variant, entry.count - 1));
				store(mob, entries);
				return new Entry(kind, entry.variant, 1);
			}
		}
		return null;
	}

	/** The first stack of a kind without taking it (to show it in the hand while it warns), or null. */
	public static Entry peek(Mob mob, Kind kind) {
		for (Entry entry : entries(mob)) {
			if (entry.kind == kind && entry.count > 0) {
				return entry;
			}
		}
		return null;
	}

	public static void clear(Mob mob) {
		mob.removeAttached(KIT);
	}

	// ---------------------------------------------------------------- items <-> kinds

	/** The kind of consumable an item on the floor would be in the kit, or null for none it can use. */
	public static Kind kindOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		if (stack.is(Items.ENDER_PEARL)) {
			return Kind.PEARL;
		}
		if (stack.is(Items.WIND_CHARGE)) {
			return Kind.WIND;
		}
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents != null && (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))) {
			boolean splash = !stack.is(Items.POTION);
			for (MobEffectInstance effect : contents.getAllEffects()) {
				Holder<MobEffect> e = effect.getEffect();
				if (!splash && (e.value() == MobEffects.INSTANT_HEALTH.value() || e.value() == MobEffects.REGENERATION.value())) {
					return Kind.HEAL;
				}
				if (!splash && (e.value() == MobEffects.STRENGTH.value() || e.value() == MobEffects.SPEED.value() || e.value() == MobEffects.FIRE_RESISTANCE.value())) {
					return Kind.BUFF;
				}
				if (splash && (e.value() == MobEffects.INSTANT_DAMAGE.value() || e.value() == MobEffects.POISON.value() || e.value() == MobEffects.SLOWNESS.value() || e.value() == MobEffects.WEAKNESS.value())) {
					return Kind.SPLASH;
				}
			}
			return null;
		}
		return stack.has(DataComponents.FOOD) ? Kind.FOOD : null;
	}

	/** The variant an item is kept as: its potion's id, or its own item id for food; "" for pearls and wind charges. */
	public static String variantOf(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents != null && contents.potion().isPresent()) {
			return contents.potion().get().unwrapKey().map(key -> key.identifier().toString()).orElse("");
		}
		if (stack.has(DataComponents.FOOD)) {
			return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		}
		return "";
	}

	/** The potion a potion variant stands for, or null (a food, a pearl, an unknown id). */
	public static Holder<Potion> potion(String variant) {
		if (variant == null || variant.isEmpty()) {
			return null;
		}
		try {
			return net.minecraft.core.registries.BuiltInRegistries.POTION.get(net.minecraft.resources.Identifier.parse(variant))
				.<Holder<Potion>>map(reference -> reference).orElse(null);
		} catch (RuntimeException broken) {
			return null;
		}
	}

	/** The item one entry of the kit is, to show in the hand or to throw. */
	public static ItemStack stack(Entry entry) {
		return switch (entry.kind) {
			case PEARL -> new ItemStack(Items.ENDER_PEARL);
			case WIND -> new ItemStack(Items.WIND_CHARGE);
			case FOOD -> {
				try {
					var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(entry.variant));
					yield item == Items.AIR ? new ItemStack(Items.BREAD) : new ItemStack(item);
				} catch (RuntimeException broken) {
					yield new ItemStack(Items.BREAD);
				}
			}
			case HEAL, BUFF, SPLASH -> {
				Holder<Potion> potion = potion(entry.variant);
				if (potion == null) {
					potion = entry.kind == Kind.HEAL ? Potions.HEALING : entry.kind == Kind.BUFF ? Potions.STRENGTH : Potions.HARMING;
				}
				yield PotionContents.createItemStack(entry.kind == Kind.SPLASH ? Items.SPLASH_POTION : Items.POTION, potion);
			}
		};
	}

	// ---------------------------------------------------------------- the spare slot

	/** The spare weapon or rod it carries (the BODY slot), empty for none. */
	public static ItemStack spare(Mob mob) {
		return mob.getItemBySlot(EquipmentSlot.BODY);
	}

	/** Whether the spare is a fishing rod (the hook, VanillaSpecials.HOOK, needs one). */
	public static boolean hasRod(Mob mob) {
		return spare(mob).is(Items.FISHING_ROD);
	}

	/**
	 * Puts a stack in the spare slot: {@code picked} (from the floor) makes it a guaranteed drop, anything else keeps
	 * whatever chance the mob had for it.
	 */
	public static void setSpare(Mob mob, ItemStack stack, boolean picked, float chance) {
		mob.setItemSlot(EquipmentSlot.BODY, stack);
		if (picked) {
			mob.setGuaranteedDrop(EquipmentSlot.BODY);
		} else {
			mob.setDropChance(EquipmentSlot.BODY, chance);
		}
	}

	// ---------------------------------------------------------------- what it spawns with

	/** The tag that says the kit was rolled, so a reloaded mob does not roll again. */
	public static final String ROLLED = "forja_mochila";

	/**
	 * Rolls what a monster spawns with (§4.8), once: {@code chance} scales the rolls (the difficulty's threat). The
	 * kit of a champion is two of everything.
	 */
	public static void roll(Mob mob, RandomSource random, double chance) {
		if (!mob.addTag(ROLLED)) {
			return;
		}
		Threat threat = Threat.of(mob);
		double k = Math.max(0.0, chance);
		switch (threat) {
			case NORMAL -> {
				if (random.nextDouble() < 0.10 * k) {
					add(mob, Kind.FOOD, "minecraft:bread", 1);
				}
			}
			case VETERANO -> {
				if (random.nextDouble() < 0.10 * k) {
					add(mob, Kind.FOOD, "minecraft:bread", 1);
				}
				if (random.nextDouble() < 0.20 * k) {
					add(mob, Kind.HEAL, "minecraft:healing", 1);
				}
				if (random.nextDouble() < 0.15 * k) {
					add(mob, Kind.SPLASH, splash(random), 1);
				}
				if (random.nextDouble() < 0.30 * k) {
					add(mob, Kind.WIND, "", 1 + random.nextInt(3));
				}
			}
			case ELITE -> {
				add(mob, Kind.HEAL, "minecraft:healing", 1);
				add(mob, Kind.SPLASH, splash(random), 1);
				if (random.nextDouble() < 0.60 * k) {
					add(mob, Kind.WIND, "", 1 + random.nextInt(3));
				}
				if (random.nextDouble() < 0.40 * k) {
					add(mob, Kind.PEARL, "", 1);
				}
			}
			case CAMPEON -> {
				add(mob, Kind.HEAL, "minecraft:healing", 2);
				add(mob, Kind.BUFF, random.nextBoolean() ? "minecraft:strength" : "minecraft:swiftness", 2);
				add(mob, Kind.SPLASH, splash(random), 2);
				add(mob, Kind.FOOD, "minecraft:bread", 2);
				add(mob, Kind.PEARL, "", 2);
				add(mob, Kind.WIND, "", 2);
			}
		}
	}

	private static String splash(RandomSource random) {
		return switch (random.nextInt(4)) {
			case 0 -> "minecraft:harming";
			case 1 -> "minecraft:poison";
			case 2 -> "minecraft:slowness";
			default -> "minecraft:weakness";
		};
	}
}
