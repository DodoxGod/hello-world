package dev.forja.forge;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Tecnicas: what a smith decides to get good at. Maestria measures how much you have worked; a technique
 * is the shape that work took. Three come open along the way, at Maestria three, six and nine, and each
 * time you pick one of three and leave the other two behind. Nothing here is a number going up: each one
 * changes how the star behaves for you.
 */
public enum Technique {
	/** The press window is wider, so a perfect piece stops being a matter of luck. */
	PULSO_FIRME(1, Items.ANVIL),
	/** Every ingot mends a third more, so keeping a piece alive costs less. */
	AHORRO_DE_METAL(1, Items.IRON_NUGGET),
	/** An alloy comes out of the star with one more ingot than it should. */
	OJO_PARA_EL_METAL(1, Items.BLAST_FURNACE),
	/** A piece can be quenched again: the one choice that was forever stops being forever. */
	SEGUNDA_TEMPLADA(2, Items.WATER_BUCKET),
	/** Orbs and books give ten more points of their upgrade than they hold. */
	MANO_DE_ORFEBRE(2, Items.GOLD_INGOT),
	/** You read the heat well enough to work an alloy one step colder than the sheet says. */
	FUELLE_LARGO(2, Items.CAMPFIRE),
	/** Herencia passes four fifths of what the old piece learned instead of half. */
	HERENCIA_LIMPIA(3, Items.ECHO_SHARD),
	/** Your own work answers to you twice as well: four percent instead of two. */
	FIRMA_DEL_MAESTRO(3, Items.WRITABLE_BOOK),
	/** One piece in ten leaves the star already carrying an upgrade nobody put there. */
	ALMA_DE_FORJA(3, Items.FIRE_CHARGE);

	/** How many techniques there are in each tier, and how many tiers. */
	public static final int TIERS = 3;

	/** Maestria needed for the first tier; each one after that is this much further on. */
	public static final int LEVEL_STEP = 3;

	/** What each one is worth, where a single number says it. */
	public static final int REPAIR_NUMERATOR = 4;
	public static final int REPAIR_DENOMINATOR = 3;
	public static final int ALLOY_EXTRA = 1;
	public static final int ORB_BONUS = 5;
	public static final float INHERIT_SHARE = 0.8F;
	public static final float AFFINITY = 0.04F;
	public static final float SOUL_CHANCE = 0.10F;
	public static final int SOUL_PERCENT = 25;
	public static final int PULSE_WINDOW = 3;

	/** One, two or three: which unlock it belongs to. */
	public final int tier;

	private final Item icon;

	Technique(int tier, Item icon) {
		this.tier = tier;
		this.icon = icon;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public Component displayName() {
		return Component.translatable("tecnica.forja." + this.id());
	}

	public Component description() {
		return Component.translatable("tecnica.forja." + this.id() + ".desc");
	}

	public ItemStack icon() {
		return new ItemStack(this.icon);
	}

	/** The Maestria a tier asks for: three, six, nine. */
	public static int levelFor(int tier) {
		return tier * LEVEL_STEP;
	}

	public int levelNeeded() {
		return levelFor(this.tier);
	}

	/** The three choices of one tier, in the order they are drawn. */
	public static java.util.List<Technique> ofTier(int tier) {
		return java.util.Arrays.stream(values()).filter(technique -> technique.tier == tier).toList();
	}
}
