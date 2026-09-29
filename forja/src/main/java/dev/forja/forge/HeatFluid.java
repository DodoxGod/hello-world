package dev.forja.forge;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The five heat fluids a heat pipe carries (docs/FUNDICION_V2.md, part B). Andy, 2026-09-28: "formas para
 * calentar la forja, como tubos de calor que transportan diferentes tipos de fluidos (...) estos fluidos se
 * pueden usar también para fundir materiales y todas las funciones que necesiten de calor en general, solo
 * que algunos serán mejores que otros".
 *
 * <p>Every number a consumer reads off a fluid is here, as a field with its javadoc, so the whole balance of
 * the heat line is one table. The units are millibuckets (mB): a bucket is {@value #BUCKET}.
 *
 * <p>How the numbers were set, against what already existed:
 * <ul>
 * <li>An ember keeps a crucible going for {@code CrucibleBlockEntity.EMBER_TICKS} (400) ticks. A bucket of lava
 * at one mB a tick runs a crucible for 1000 ticks: two and a half embers, for something a cauldron under
 * dripstone makes for free. That is the point of the line — but it has to be carried there.</li>
 * <li>A casting table warms {@code CastingTableBlockEntity.WARMS} (25) a second beside a fire, up to
 * {@code HOT} (200). A hot fluid warms it at its own rate, up to its own ceiling.</li>
 * <li>The forge table's star works in one press, so it pays {@link #FORGE_ACTION_TICKS} ticks of the fluid's
 * draw for each press that needed the heat (an alloy, melting parts back, a reheat).</li>
 * </ul>
 */
public enum HeatFluid implements StringRepresentable {
	/**
	 * Steam: a boiler with water in it and any fire under it. Cheap and endless, and only warm — it melts
	 * the soft metals (copper, gold) and the warm alloys, and keeps a casting table tepid, never hot.
	 */
	VAPOR(Alloys.Heat.TEMPLADA, 0xC9D6DE, 1, 100, 25, 100, 0.0F, HeatFluid.SOFT),
	/** Lava out of a heat depot: molten heat, exactly what lava under the table gives, but carried by pipe. */
	LAVA(Alloys.Heat.FUNDIDA, 0xFF6A12, 1, 100, 25, 200, 0.0F, Integer.MAX_VALUE),
	/**
	 * Blaze blood: blaze rods boiled down. As hot as lava and faster: a crucible melts half again as fast
	 * and a casting table warms twice as fast. It costs twice as much to run.
	 */
	SANGRE_DE_BLAZE(Alloys.Heat.FUNDIDA, 0xFFB21E, 2, 150, 50, 200, 0.0F, Integer.MAX_VALUE),
	/**
	 * Forge breath: slag or a forge heart boiled over a hot fire. White heat without the obsidian crucible,
	 * and a casting table fed by it pours a perfect tool more often. The dearest to run by far.
	 */
	ALIENTO_DE_FORJA(Alloys.Heat.FORJA_BLANCA, 0xFFF2B8, 4, 100, 25, 200, 0.20F, Integer.MAX_VALUE),
	/**
	 * Ice brine: packed or blue ice in the boiler. It carries no heat at all, it takes it away: a casting
	 * table it touches quenches every tool it pours in water (the {@link Temple#AGUA} quench, for good), a
	 * forge table it touches quenches what the star forges the same way, and a crucible it touches goes out.
	 * It is spent per quench ({@link #QUENCH_COST}), not per tick.
	 */
	SALMUERA_HELADA(Alloys.Heat.FRIA, 0x7FD8F2, 0, 100, 0, 0, 0.0F, 0);

	/**
	 * The hardest metal steam melts, on the durability scale the crucible already uses: gold, copper, pewter
	 * and brass (up to 240), not iron (250). A clay crucible's own fire is as warm as steam and does melt iron;
	 * steam is wet heat, and that is the difference.
	 */
	public static final int SOFT = 240;

	/** One bucket, in mB. */
	public static final int BUCKET = 1000;

	/** How much brine one quench spends, at a casting table or a forge table. */
	public static final int QUENCH_COST = 100;

	/**
	 * What one press of the forge star that needed the heat costs, in ticks of the fluid's {@link #draw}:
	 * five seconds of a crucible's work.
	 */
	public static final int FORGE_ACTION_TICKS = 100;

	/** What one second of a casting table warming off a pipe costs, in ticks of the fluid's {@link #draw}. */
	public static final int TABLE_DRAW_TICKS = 10;

	/** The heat it brings to whatever it touches, on the same scale as the block under a forge table. */
	public final Alloys.Heat heat;
	/** Its colour in the pipe, the boiler and the depot, 0xRRGGBB. */
	public final int colour;
	/** mB spent per tick a crucible works on it (0 for brine, which is paid per quench). */
	public final int draw;
	/** How fast a crucible works on it, in percent of an ember's fire: 150 is half again as fast. */
	public final int meltPercent;
	/** Heat a casting table gains per second off a pipe of it (a fire beside the table gives 25). */
	public final int tableWarms;
	/** The most heat a casting table reaches on this fluid alone (a fire gives the full 200). */
	public final int tableCap;
	/** Added to a casting table's own chance of pouring a perfect tool ("steady hand"). */
	public final float steadyBonus;
	/** The hardest metal (by durability) a crucible melts or melts back on this fluid alone. */
	public final int meltsUpTo;

	HeatFluid(Alloys.Heat heat, int colour, int draw, int meltPercent, int tableWarms, int tableCap, float steadyBonus,
		int meltsUpTo) {
		this.heat = heat;
		this.colour = colour;
		this.draw = draw;
		this.meltPercent = meltPercent;
		this.tableWarms = tableWarms;
		this.tableCap = tableCap;
		this.steadyBonus = steadyBonus;
		this.meltsUpTo = meltsUpTo;
	}

	/** Whether it cools rather than heats. */
	public boolean cools() {
		return this == SALMUERA_HELADA;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String getSerializedName() {
		return this.id();
	}

	public Component displayName() {
		return Component.translatable("fluido.forja." + this.id());
	}

	// ------------------------------------------------------------------ what makes each one

	/** Which of the two vessels turns an item into its fluid. */
	public enum Vessel {
		/** The boiler (caldera): steam, blaze blood, forge breath and brine. */
		CALDERA,
		/** The heat depot (depósito de calor): lava. */
		DEPOSITO
	}

	/**
	 * What one item gives when it goes into a vessel.
	 *
	 * @param fluid what it turns into
	 * @param amount how many mB of it
	 * @param leaves what is left behind (the empty bucket), or empty
	 * @param fire the least heat there has to be under the vessel for it to boil, or FRIA for none
	 */
	public record Yield(HeatFluid fluid, int amount, ItemStack leaves, Alloys.Heat fire) {
	}

	/** What this item gives in this vessel, or null if the vessel has no use for it. */
	public static @Nullable Yield yield(ItemStack stack, Vessel vessel) {
		if (stack.isEmpty()) {
			return null;
		}
		Item item = stack.getItem();
		if (vessel == Vessel.DEPOSITO) {
			if (item == Items.LAVA_BUCKET) {
				return new Yield(LAVA, BUCKET, new ItemStack(Items.BUCKET), Alloys.Heat.FRIA);
			}
			if (item == Items.MAGMA_BLOCK) {
				return new Yield(LAVA, BUCKET / 4, ItemStack.EMPTY, Alloys.Heat.FRIA);
			}
			return null;
		}
		if (item == Items.WATER_BUCKET) {
			// Any fire at all under the boiler: a campfire does.
			return new Yield(VAPOR, BUCKET, new ItemStack(Items.BUCKET), Alloys.Heat.TEMPLADA);
		}
		if (item == Items.BLAZE_ROD) {
			return new Yield(SANGRE_DE_BLAZE, BUCKET / 2, ItemStack.EMPTY, Alloys.Heat.FRIA);
		}
		if (item == Items.BLAZE_POWDER) {
			return new Yield(SANGRE_DE_BLAZE, BUCKET / 5, ItemStack.EMPTY, Alloys.Heat.FRIA);
		}
		if (item == dev.forja.registry.ModItems.ESCORIA) {
			// Slag is melted again, and that wants a hot fire: magma, soul fire, lava or a wisp lantern.
			return new Yield(ALIENTO_DE_FORJA, BUCKET / 4, ItemStack.EMPTY, Alloys.Heat.CALIENTE);
		}
		if (item == dev.forja.registry.ModItems.CORAZON_DE_FORJA) {
			return new Yield(ALIENTO_DE_FORJA, BUCKET * 4, ItemStack.EMPTY, Alloys.Heat.CALIENTE);
		}
		if (item == Items.PACKED_ICE) {
			return new Yield(SALMUERA_HELADA, BUCKET / 4, ItemStack.EMPTY, Alloys.Heat.FRIA);
		}
		if (item == Items.BLUE_ICE) {
			return new Yield(SALMUERA_HELADA, BUCKET, ItemStack.EMPTY, Alloys.Heat.FRIA);
		}
		return null;
	}

	// ------------------------------------------------------------------ what a pipe shows

	/** What a pipe, a boiler or a depot is drawn with: one of the fluids, or nothing. */
	public enum Shown implements StringRepresentable {
		VACIO(null),
		VAPOR(HeatFluid.VAPOR),
		LAVA(HeatFluid.LAVA),
		SANGRE_DE_BLAZE(HeatFluid.SANGRE_DE_BLAZE),
		ALIENTO_DE_FORJA(HeatFluid.ALIENTO_DE_FORJA),
		SALMUERA_HELADA(HeatFluid.SALMUERA_HELADA);

		public final @Nullable HeatFluid fluid;

		Shown(@Nullable HeatFluid fluid) {
			this.fluid = fluid;
		}

		public static Shown of(@Nullable HeatFluid fluid) {
			return fluid == null ? VACIO : values()[fluid.ordinal() + 1];
		}

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		/** The light a pipe of it gives off: the hot three glow, steam and brine do not. */
		public int light() {
			return this.fluid == null || this.fluid.heat.ordinal() < Alloys.Heat.FUNDIDA.ordinal() ? 0
				: this.fluid == HeatFluid.ALIENTO_DE_FORJA ? 12 : 9;
		}
	}
}
