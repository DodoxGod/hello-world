package dev.forja.forge;

import dev.forja.menu.Station;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Potencial: how far the upgrades of one piece can be taken, as a percentage.
 *
 * <p>Andy's design. Until this, every upgrade on every piece went to 100 % for the price of its
 * ingredients, so a stack of sugar was Efficiency V on the first day and the only question a piece
 * ever asked was whether you had farmed enough. Now the ceiling belongs to the piece and comes out of
 * how it was made: what its parts are, how the hammer fell, who held it, and at which table. It can
 * grow afterwards — the piece's own mastery, a pact, an anneal — and nothing here ever takes a
 * percentage that is already on a piece away from it. A ceiling stops a number going up. That is all
 * it does.
 *
 * <p>Two ceilings, in fact, and an upgrade is raised to the lower of them: the piece's potential, and
 * what the table it is lying on can do ({@link Station#capacity()}). And over ninety, a third: the
 * master flux, which is the new material the top of every upgrade asks for.
 *
 * <p>And it decides how MUCH a piece carries as well as how far: every upgrade has a {@link #weight},
 * the piece a {@link #capacity} that comes out of its potential, and what is on it may not weigh more
 * than it holds. Andy again: "una herramienta con potencial bajo puede tener pocas mejoras, una con uno
 * muy alto puede tener muchas más", and then "no todas las mejoras deberían tener el mismo peso".
 *
 * <p>Every number is here, on purpose. See docs/POTENCIAL.md for why each is what it is.
 */
public final class Potential {
	/** What any forged piece starts from, however it was made. */
	public static final int FLOOR = 40;
	/** Every part poured clean in the foundry, none of them cut at the bench. Shared out by part. */
	public static final int CAST_PARTS = 20;
	/** The press: nothing for a miss, this for a decent blow, twice it for a perfect one. */
	public static final int PER_QUALITY = 5;
	/** A point a level of smith. */
	public static final int PER_SMITH_LEVEL = 1;
	public static final int GREATER_TABLE = 10;
	public static final int WHOLE_WORKSHOP = 5;
	/** Afterwards: a point for each level of the piece's own mastery. */
	public static final int PER_MASTERY_LEVEL = 1;
	/** A pact is a bargain: the curse it carries buys this much more room for everything else. */
	public static final int PER_PACT = 10;
	/** Recocido at 100 %. */
	public static final int ANNEAL = 15;
	/** What a piece from before any of this is taken to be, all told. */
	public static final int LEGACY = 70;
	public static final int MOST = 100;
	/** From this weight up, an all-or-nothing upgrade is made at the greater table or not at all. */
	public static final int HEAVY = 3;
	/** Past this, an upgrade asks for master flux. */
	public static final int WITHOUT_FLUX = 90;
	/** Where an ingredient starts being worth half, and then a quarter, of what it says. */
	public static final int HALF_FROM = 50;
	public static final int QUARTER_FROM = 75;
	/**
	 * Capacity: a point for every {@link #POINTS_PER_LOAD} of potential over {@link #CAPACITY_FROM}. The
	 * worst piece there is (40) holds five points — Filo and a trifle, and that is that — and a perfect
	 * one twenty, which is seven or eight upgrades chosen with some care and never all the good ones.
	 */
	public static final int CAPACITY_FROM = 20;
	public static final int POINTS_PER_LOAD = 4;
	/** What a perfect piece holds: the length every picture of a load is drawn to. */
	public static final int MOST_CAPACITY = (MOST - CAPACITY_FROM) / POINTS_PER_LOAD;

	private Potential() {
	}

	/** Why an upgrade would go no higher, for the screen to say. */
	public enum Limit {
		NONE, STATION, POTENTIAL, FLUX,
		/** Not a ceiling on the percentage at all: the piece has no room left for another upgrade this heavy. */
		LOAD,
		/** A heavy all-or-nothing upgrade on a plain bench: not one percent of it, it is the greater table's. */
		GREATER;

		public Component message(int ceiling) {
			return Component.translatable("gui.forja.potencial.tope." + this.name().toLowerCase(java.util.Locale.ROOT), ceiling);
		}
	}

	/** A ceiling, and whose it is. */
	public record Ceiling(int percent, Limit limit) {
	}

	/** Asked by whatever applies upgrades: how high may this one go, with or without flux to hand? */
	@FunctionalInterface
	public interface Limits {
		Limits NONE = (upgrade, flux) -> new Ceiling(MOST, Limit.NONE);

		Ceiling ceiling(Upgrade upgrade, boolean flux);
	}

	/**
	 * Upgrades no ceiling applies to: what the sky leaves behind, pacts, and the anneal itself.
	 *
	 * <p>"Los pactos y mejoras de evento no afectan al %": they are not bought with ingredients in the
	 * ordinary way — one is caught in a flask and the other is paid for with a curse — so they are not
	 * what the ceiling is there to ration. The anneal is out because it is the thing that raises the
	 * ceiling, and a ceiling that limited it would be limiting itself.
	 */
	public static boolean exempt(Upgrade upgrade) {
		return upgrade.options.isEmpty() || upgrade.isPact() || upgrade == Upgrade.RECOCIDO;
	}

	/**
	 * Upgrades that do nothing at all short of a hundred: a one-level enchantment (Silk Touch is either
	 * there or it is not) and the night vision, which only comes on when it is whole.
	 *
	 * <p>No ceiling holds these down. A ceiling under a hundred does not make one of them weaker, it
	 * makes it nothing, for ever, after eating every cobweb on the way up — and the first version of
	 * the potential did exactly that to seven upgrades on every piece under a hundred. Andy's rule for
	 * them, which {@link #ceiling} applies for ingredients, books, orbs and inheritance alike: the light
	 * ones (weight one or two) go to a hundred at any table, and table, potential and flux are not asked;
	 * the heavy ones (weight {@link #HEAVY} and up) are the greater table's work alone — a plain bench
	 * will not take the first percent of one, spends nothing and says where to go — and there they too
	 * go to a hundred with neither potential nor flux asked. What they weigh counts like anyone's.
	 */
	public static boolean allOrNothing(Upgrade upgrade) {
		return upgrade.enchantment != null && upgrade.maxLevel == 1 || upgrade == Upgrade.VISION_NOCTURNA;
	}

	/**
	 * What an upgrade weighs: how much of a piece's {@link #capacity} it takes, by what it is worth at a
	 * hundred percent. Four for what a piece is built around, three for what is strong, one for a trifle,
	 * and two for everything else — which is where a new upgrade lands until somebody ranks it.
	 *
	 * <p>Ranked from the numbers in Upgrade, role by role, and with the synergies in mind: Tormenta and
	 * Onda de choque would be twos on their own and are threes because each is half of two pairs; the
	 * pairs that pay best (Fortuna + Veta, Excavación + Eficiencia, Protección + Vitalidad) are the
	 * dearest to carry, and the ones taken for fun (Botín + Decapitador) are cheap. A synergy itself
	 * weighs nothing: it is what spending the room on a pair buys. Inside an exclusive group the general
	 * one is the heavy one, which is what finally gives Castigo a reason to exist beside Filo.
	 *
	 * <p>Pacts, what the sky leaves and the anneal weigh nothing, as they answer to no ceiling.
	 */
	public static int weight(Upgrade upgrade) {
		if (exempt(upgrade)) {
			return 0;
		}
		return switch (upgrade) {
			case RESONANCIA, FILO, VAMPIRISMO, PODER, FORTUNA, EXCAVACION, PROTECCION, VITALIDAD, REPARACION -> 4;
			case CONJURO_VELOZ, SOBRECARGA, PRISMA, BUSCADOR, VORTICE, SANTUARIO, CRITICO, FRENESI, ONDA_DE_CHOQUE, TORMENTA, BOTIN, ALCANCE, DENSIDAD, ATURDIMIENTO, NUDILLOS_DE_HIERRO,
				SEGUNDA_CABEZA, DESGARRO, INFINIDAD, MULTIDISPARO, CARGA_RAPIDA, TENSION, PUNTA_AFILADA, EFICIENCIA,
				TOQUE_DE_SEDA, VETA, LENADOR, TELEQUINESIS, REGENERACION, PRESTEZA, REBOTE, ABSORCION, IRROMPIBLE -> 3;
			case EMPUJE, DECAPITADOR, PERDICION_DE_ARTROPODOS, RETROCESO, LUZ, ZANCADA, AFINIDAD_ACUATICA, PASO_HELADO,
				SIRGA, CEBO -> 1;
			default -> 2;
		};
	}

	/** How much a piece with this potential holds. */
	public static int capacity(int potential) {
		return Math.max(0, potential - CAPACITY_FROM) / POINTS_PER_LOAD;
	}

	public static int capacity(ItemStack stack) {
		return capacity(of(stack));
	}

	/** What is on the piece already, weighed. */
	public static int load(ItemStack stack) {
		return load(stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY));
	}

	public static int load(Upgrades upgrades) {
		int total = 0;
		for (Upgrade upgrade : upgrades.percents().keySet()) {
			total += weight(upgrade);
		}
		return total;
	}

	/**
	 * Whether this upgrade can go on: always, if it is on already or weighs nothing. A piece from before
	 * the load existed may be over what it holds, and keeps all of it; it just takes nothing new.
	 */
	public static boolean fits(ItemStack gear, Upgrade upgrade) {
		int weight = weight(upgrade);
		return weight == 0 || gear.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(upgrade) > 0
			|| load(gear) + weight <= capacity(gear);
	}

	/** The potential a piece needs before it holds this many points, for the screens that say what is missing. */
	public static int potentialFor(int points) {
		return Math.min(MOST, CAPACITY_FROM + points * POINTS_PER_LOAD);
	}

	/** What the parts of a piece add: the share of its slots that hold something poured clean. */
	public static int fromParts(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		int slots = parts == null ? 0 : parts.type().slots.size();
		if (slots == 0) {
			return 0;
		}
		int mask = stack.getOrDefault(ModComponents.COLADAS, 0) & ((1 << slots) - 1);
		return Math.round(CAST_PARTS * Integer.bitCount(mask) / (float) slots);
	}

	/** The piece's potential now: what it was forged with and everything that has raised it since. */
	public static int of(ItemStack stack) {
		if (!stack.has(ModComponents.PARTS)) {
			return 0;
		}
		Integer forged = stack.get(ModComponents.POTENCIAL);
		// A piece from before the potential existed: a middling one, and its parts are not asked about.
		int total = forged == null ? LEGACY : forged + fromParts(stack);
		total += Mastery.level(stack) * PER_MASTERY_LEVEL;
		Upgrades upgrades = stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
		for (Upgrade upgrade : upgrades.percents().keySet()) {
			if (upgrade.isPact()) {
				total += PER_PACT;
			}
		}
		total += ANNEAL * upgrades.percent(Upgrade.RECOCIDO) / 100;
		return Math.min(MOST, total);
	}

	/**
	 * The highest this upgrade can be taken on this piece, at this table, with or without flux to hand.
	 * Never lower than it already is: a ceiling under a number leaves the number alone.
	 */
	public static Ceiling ceiling(ItemStack gear, Upgrade upgrade, @Nullable Station station, boolean flux) {
		if (exempt(upgrade)) {
			return new Ceiling(MOST, Limit.NONE);
		}
		int current = gear.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(upgrade);
		if (!fits(gear, upgrade)) {
			return new Ceiling(0, Limit.LOAD);
		}
		if (allOrNothing(upgrade)) {
			// All of it or none of it, so no ceiling in between: see allOrNothing. Only the bench turns
			// the heavy ones away, whole, and what is already on a piece stays as it is.
			boolean bench = station != null && station.capacity() < MOST;
			return bench && weight(upgrade) >= HEAVY ? new Ceiling(current, Limit.GREATER) : new Ceiling(MOST, Limit.NONE);
		}
		int percent = MOST;
		Limit limit = Limit.NONE;
		// The flux is what carries an upgrade over ninety, once. One that is already over has had its flux.
		if (!flux && current <= WITHOUT_FLUX) {
			percent = WITHOUT_FLUX;
			limit = Limit.FLUX;
		}
		int potential = of(gear);
		if (potential < percent) {
			percent = potential;
			limit = Limit.POTENTIAL;
		}
		if (station != null && station.capacity() < percent) {
			percent = station.capacity();
			limit = Limit.STATION;
		}
		return new Ceiling(Math.max(current, percent), limit);
	}

	/**
	 * Where progress stands after one more ingredient, in quarters of a percent.
	 *
	 * <p>The first half of an upgrade costs what its recipe says. From fifty an ingredient is worth half
	 * of that and from seventy-five a quarter, and one that straddles a line is split across it, so a
	 * block dropped in at forty-nine does not carry the whole of itself over at the cheap rate. Quarters
	 * because the smallest ingredient is worth two percent and a quarter of two is not a whole number.
	 */
	public static int spend(int progressQuarters, int value) {
		int left = value * 4;
		while (left > 0 && progressQuarters < MOST * 4) {
			int percent = progressQuarters / 4;
			int cost = percent < HALF_FROM ? 1 : percent < QUARTER_FROM ? 2 : 4;
			int line = (percent < HALF_FROM ? HALF_FROM : percent < QUARTER_FROM ? QUARTER_FROM : MOST) * 4;
			int affordable = left / cost;
			if (affordable <= 0) {
				break;
			}
			int step = Math.min(line - progressQuarters, affordable);
			progressQuarters += step;
			left -= step * cost;
		}
		return progressQuarters;
	}

	/**
	 * What a percentage cost to reach from nothing, in the same currency ingredients are worth.
	 *
	 * <p>This is what an orb really holds. Put on a bare piece an orb gives back exactly the percentage
	 * that went into it; put on top of an upgrade that is already there, or merged with another orb, it
	 * pays the same rising price an ingredient would. Without that, two cheap halves extracted and
	 * merged would have been a whole upgrade at half price.
	 */
	public static int value(int percent) {
		int clamped = Math.max(0, Math.min(MOST, percent));
		int value = Math.min(clamped, HALF_FROM);
		if (clamped > HALF_FROM) {
			value += (Math.min(clamped, QUARTER_FROM) - HALF_FROM) * 2;
		}
		if (clamped > QUARTER_FROM) {
			value += (clamped - QUARTER_FROM) * 4;
		}
		return value;
	}

	/** The percentage reached by putting this much value on top of a percentage that is already there. */
	public static int raised(int before, int value) {
		return spend(before * 4, value) / 4;
	}

	/**
	 * What a piece is forged with, leaving out its parts: those are counted where they sit.
	 *
	 * @param quality the press: 0 a miss, 1 decent, 2 perfect
	 */
	public static int atForge(int quality, @Nullable Player smith, @Nullable Station station, boolean wholeWorkshop) {
		int total = FLOOR
			+ PER_QUALITY * Math.max(0, Math.min(2, quality))
			+ PER_SMITH_LEVEL * SmithLevel.level(smith)
			+ (station == Station.FORJA_MAYOR ? GREATER_TABLE : 0)
			+ (wholeWorkshop ? WHOLE_WORKSHOP : 0);
		return Math.min(MOST, total);
	}

	/**
	 * A piece poured whole on a casting table: nobody swung at it and no smith stood over it, so the
	 * table's own steady hand stands in for the press. Its parts, all of them cast, are marked apart.
	 */
	public static int atCasting(boolean rough, boolean perfect) {
		return FLOOR + (perfect ? PER_QUALITY * 2 : rough ? 0 : PER_QUALITY);
	}

	/** Found, not made: somebody forged it once, and how well is anybody's guess. */
	public static int found(RandomSource random) {
		return 60 + random.nextInt(31);
	}

	/** Whether taking this upgrade over ninety is something the flux is spent on. */
	public static boolean needsFlux(Upgrade upgrade) {
		return !exempt(upgrade) && !allOrNothing(upgrade);
	}

	public static boolean isFlux(ItemStack stack) {
		return stack.is(ModItems.FUNDENTE_MAESTRO);
	}

	/** "Potencial 68 %", in the colour of how much that is. */
	public static Component describe(ItemStack stack) {
		int potential = of(stack);
		int colour = potential >= 90 ? 0xC79BFF : potential >= 75 ? 0x7FD0FF : potential >= 60 ? 0x8FE07A : potential >= 50 ? 0xE6E6E6 : 0xFF9A7A;
		return Component.translatable("tooltip.forja.potencial", potential).withColor(colour);
	}
}
