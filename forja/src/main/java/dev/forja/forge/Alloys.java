package dev.forja.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * Aleaciones: metals the forge star melts together. What comes out is not decided by the ingredients
 * alone but by how hot the table is standing, and the table takes its heat from whatever is directly
 * underneath it. The same copper and iron are bronze over a campfire and nothing at all on stone, and
 * the best alloys only come off a table built over lava.
 */
public final class Alloys {
	/** How hot a table is, from the block under it. */
	public enum Heat {
		/** Bare ground: the star forges and upgrades, but melts nothing. */
		FRIA,
		/** A campfire or an open fire under the table. */
		TEMPLADA,
		/** A magma block, soul fire or a lit blast furnace. */
		CALIENTE,
		/** Lava, or a cauldron of it. */
		FUNDIDA,
		/**
		 * White heat: hotter than lava, and there is no block that gives it.
		 *
		 * <p>{@link #heatUnder} never returns this, whatever you build the table over, and no technique
		 * reads its way up to it. The only thing in the mod that burns this hot is the obsidian crucible,
		 * which is the point: the last three alloys are not something you can reach by carrying a bucket
		 * of lava into the workshop. You build the dear crucible or you do without them.
		 *
		 * <p>Or you pay for it by the tick: forge breath (HeatFluid.ALIENTO_DE_FORJA), boiled out of slag or a
		 * forge heart and piped in, burns this hot too, and HeatSources hands it to whatever it touches.
		 */
		FORJA_BLANCA;

		/**
		 * The next heat up, for a smith who reads the fire better than the fire deserves. It stops at
		 * molten: reading the fire well is not the same as owning an obsidian crucible.
		 */
		public Heat hotter() {
			Heat next = this.ordinal() + 1 < values().length ? values()[this.ordinal() + 1] : this;
			return next == FORJA_BLANCA ? this : next;
		}

		public boolean reaches(Heat needed) {
			return this.ordinal() >= needed.ordinal();
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public Component displayName() {
			return Component.translatable("calor.forja." + this.id());
		}
	}

	/**
	 * One ingredient of an alloy. The item comes through a supplier because some ingredients are the
	 * mod's own, and those do not exist yet when this table is built.
	 */
	public record Part(java.util.function.Supplier<Item> item, int count) {
		public boolean test(ItemStack stack) {
			return stack.is(this.item().get()) && stack.getCount() >= this.count();
		}
	}

	/**
	 * One alloy. The heat is the lowest the table has to be: a hotter table still melts a simpler
	 * alloy, which is why building over lava is never wrong.
	 */
	public record Recipe(String id, Heat heat, List<Part> inputs, int output,
		java.util.function.Supplier<Item> made) {
		/** The usual case: a pour whose name is also the name of the ingot it makes. */
		public Recipe(String id, Heat heat, List<Part> inputs, int output) {
			this(id, heat, inputs, output, () -> ModItems.alloy(id));
		}

		public ItemStack result() {
			return new ItemStack(this.made().get(), this.output);
		}

		public Component displayName() {
			return Component.translatable("item.forja." + this.id());
		}
	}

	/** Every alloy, cheapest first. The ids match the items and the materials. */
	public static final List<Recipe> ALL = List.of(
		new Recipe("bronce", Heat.TEMPLADA, List.of(new Part(() -> Items.COPPER_INGOT, 2), new Part(() -> Items.IRON_INGOT, 1)), 3),
		new Recipe("laton", Heat.TEMPLADA, List.of(new Part(() -> Items.COPPER_INGOT, 2), new Part(() -> Items.GOLD_INGOT, 1)), 3),
		new Recipe("peltre", Heat.TEMPLADA, List.of(new Part(() -> Items.COPPER_INGOT, 2), new Part(() -> Items.RESIN_BRICK, 1)), 3),
		new Recipe("acero", Heat.CALIENTE, List.of(new Part(() -> Items.IRON_INGOT, 2), new Part(() -> Items.COAL, 2)), 2),
		new Recipe("electro", Heat.CALIENTE, List.of(new Part(() -> Items.GOLD_INGOT, 2), new Part(() -> Items.AMETHYST_SHARD, 1)), 2),
		new Recipe("damasco", Heat.FUNDIDA, List.of(new Part(() -> ModItems.alloy("acero"), 2), new Part(() -> Items.NETHERITE_SCRAP, 1)), 1),
		new Recipe("acero_estelar", Heat.FUNDIDA, List.of(new Part(() -> ModItems.alloy("acero"), 1), new Part(() -> ModItems.HIERRO_ESTELAR, 1)), 2),
		new Recipe("obsidiacero", Heat.FUNDIDA, List.of(new Part(() -> ModItems.alloy("acero"), 2), new Part(() -> Items.OBSIDIAN, 1)), 1),
		// Not a metal for gear: it is what a mould is cut from, and nothing else in the mod uses it.
		new Recipe("acero_refractario", Heat.CALIENTE, List.of(new Part(() -> ModItems.alloy("acero"), 2), new Part(() -> Items.CLAY_BALL, 2)), 2),

		// ---- the four the foundry is for. None of them need the best crucible, but all of them want a
		// tank of metal behind them: they are made of things you gather by the stack, not by the one.
		/** Cinereous steel: steel quenched in living embers, and it never really goes out. */
		new Recipe("cinerio", Heat.CALIENTE, List.of(new Part(() -> ModItems.alloy("acero"), 2), new Part(() -> ModItems.ASCUA, 4)), 2),
		/** Voltaic brass: brass with the charge still running through it. */
		new Recipe("voltaico", Heat.CALIENTE, List.of(new Part(() -> ModItems.alloy("laton"), 2), new Part(() -> Items.REDSTONE, 4), new Part(() -> Items.AMETHYST_SHARD, 1)), 2),
		/** Soul steel: star steel poured over the plate of a suit that stood up by itself. */
		new Recipe("almacero", Heat.FUNDIDA, List.of(new Part(() -> ModItems.alloy("acero_estelar"), 1), new Part(() -> ModItems.PLACA_HUECA, 2)), 1),
		/** Glass steel: obsidian steel run through with quartz until you can see daylight through it. */
		new Recipe("vidriacero", Heat.FUNDIDA, List.of(new Part(() -> ModItems.alloy("obsidiacero"), 1), new Part(() -> Items.QUARTZ, 4)), 1),

		// ---- the three only white heat makes, and therefore only the obsidian crucible.
		// Sun steel, moon steel and living steel have two ingredients apiece on purpose: the crucible has two input
		// slots, and a table can never reach them. A recipe with more than two (astralite, the peak alloys) is a
		// FOUNDRY_ONLY one: two in the pot and the rest from the tanks on its line.
		/** Sun steel: damascus taken past molten with the fire still in it. It is brightest at noon. */
		new Recipe("solacero", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.alloy("damasco"), 1), new Part(() -> Items.BLAZE_ROD, 3)), 1),
		/** Moon steel: the same trick the other way round. It wakes up when the sun goes down. */
		new Recipe("lunacero", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.alloy("obsidiacero"), 1), new Part(() -> Items.ECHO_SHARD, 3)), 1),
		/** Living steel: the smith's own heart melted into damascus. The last metal in the mod. */
		new Recipe("acero_vivo", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.CORAZON_DE_FORJA, 1), new Part(() -> ModItems.alloy("damasco"), 2)), 1),

		// ---- the far forges (docs/ALEACIONES_NETHER_END.md): only the one forge of their own makes them, see
		// place(). The heat is what they read as in the guide and the crucible's melting; no table reaches them.
		/** Wispfire: iron and netherite scrap run through soul soil in blue fire. It burns what does not burn. */
		new Recipe("fatuo", Heat.FUNDIDA, List.of(new Part(() -> Items.IRON_INGOT, 2), new Part(() -> Items.NETHERITE_SCRAP, 1),
			new Part(() -> Items.SOUL_SOIL, 4)), 2),
		/** Magmasteel: steel run with basalt, blackstone and magma cream. Heavy, and lava is a floor to it. */
		new Recipe("magmacero", Heat.FUNDIDA, List.of(new Part(() -> ModItems.alloy("acero"), 2), new Part(() -> Items.BASALT, 4),
			new Part(() -> Items.BLACKSTONE, 4), new Part(() -> Items.MAGMA_CREAM, 2)), 2),
		/** Aetherium: steel with a shulker's shell, popped chorus and end stone in it, at the void forge. It does not want to fall. */
		new Recipe("eterio", Heat.FUNDIDA, List.of(new Part(() -> ModItems.alloy("acero"), 2), new Part(() -> Items.SHULKER_SHELL, 1),
			new Part(() -> Items.POPPED_CHORUS_FRUIT, 4), new Part(() -> Items.END_STONE, 4)), 2),

		// ---- the middle tier (docs/ALEACIONES_CUMBRE.md, 2.7): two alloys and one more thing.
		/** Spectresteel: wispfire and soul steel with a ghast's tear, at the soul forge. Hard to put down. */
		new Recipe("espectracero", Heat.FUNDIDA, List.of(new Part(() -> ModItems.alloy("fatuo"), 2), new Part(() -> ModItems.alloy("almacero"), 1),
			new Part(() -> Items.GHAST_TEAR, 1)), 2),
		/** Volcano heart: magmasteel and sun steel with magma blocks, at the soul forge. It burns hotter the worse things go. */
		new Recipe("corazon_de_volcan", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.alloy("magmacero"), 2), new Part(() -> ModItems.alloy("solacero"), 1),
			new Part(() -> Items.MAGMA_BLOCK, 4)), 2),
		/** Eclipse: aetherium and moon steel with crying obsidian, at the void forge. */
		new Recipe("eclipse", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.alloy("eterio"), 2), new Part(() -> ModItems.alloy("lunacero"), 1),
			new Part(() -> Items.CRYING_OBSIDIAN, 2)), 2),
		/** Astralite: orichalcum, aetherium and star iron in the obsidian crucible; the Guild's metal has no quarry in it. */
		new Recipe("astralita", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.ORICALCO, 2), new Part(() -> ModItems.alloy("eterio"), 1),
			new Part(() -> ModItems.HIERRO_ESTELAR, 2)), 2)
	);

	/**
	 * Where an alloy can be made. Almost all of them anywhere that is hot enough; the far forge alloys only at
	 * their own forge (block/FarForgeBlock), which is the whole reason to go to the ruin that holds it.
	 */
	public enum Place {
		/** A forge table, the assembler or a crucible, at the heat the recipe asks. */
		ANY,
		/** The soul forge of the Fragua caída, lit, in the Nether. */
		ALMAS,
		/** The void forge of the End ruin, lit, in the End. */
		VACIO;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	private static final java.util.Map<String, Place> PLACES = java.util.Map.of(
		"fatuo", Place.ALMAS,
		"magmacero", Place.ALMAS,
		"eterio", Place.VACIO,
		"espectracero", Place.ALMAS,
		"corazon_de_volcan", Place.ALMAS,
		"eclipse", Place.VACIO
	);

	/** Where this alloy is made. */
	public static Place place(Recipe recipe) {
		return PLACES.getOrDefault(recipe.id(), Place.ANY);
	}

	/** Whether a table or a crucible may make it at all. */
	public static boolean anywhere(Recipe recipe) {
		return place(recipe) == Place.ANY;
	}

	/** The alloys one far forge makes, in the order of {@link #ALL}. */
	public static List<Recipe> at(Place place) {
		return ALL.stream().filter(recipe -> place(recipe) == place).toList();
	}

	/**
	 * What the crucible pours that is <b>not</b> an alloy.
	 *
	 * <p>{@link #ALL} is read by the material tables, the guide's ingot grid and the armour matrix, so
	 * everything in it has to be a metal you can wear. This list is for pours that are just items.
	 */
	public static final List<Recipe> EXTRA = List.of(
		new Recipe("lingote_de_temple", Heat.CALIENTE,
			List.of(new Part(() -> ModItems.alloy("acero_refractario"), 1), new Part(() -> Items.CLAY_BALL, 2)), 2,
			() -> ModItems.LINGOTE_DE_TEMPLE),
		// Oricalco is a forge material too since 2026-10-01 (ForgeMaterial.ORICALCO, docs/HERRERO_DIMENSION.md 1.4). It
		// stays in this list because its ingot is registered on its own (ModItems.ORICALCO, rare and fireproof) and
		// the guide's alloy pages are laid out for a handful of ingredients, not fourteen.
		// Oricalco (docs/HERRERO_DIMENSION.md, 1.1): one bar of every metal of the mod's own that can be
		// made again and again. Two go in the pot and the rest come from the tanks on its line, the way any
		// alloy of more than two does. Not the damascus line (netherite scrap runs out), not moon steel
		// (echo shards do), not the smith's heart or living steel (only he gives those).
		new Recipe("oricalco", Heat.FUNDIDA, List.of(
			new Part(() -> ModItems.HIERRO_ESTELAR, 1), new Part(() -> ModItems.PLACA_HUECA, 1), new Part(() -> ModItems.ESCORIA, 1),
			new Part(() -> ModItems.alloy("bronce"), 1), new Part(() -> ModItems.alloy("laton"), 1), new Part(() -> ModItems.alloy("peltre"), 1),
			new Part(() -> ModItems.alloy("electro"), 1), new Part(() -> ModItems.alloy("acero"), 1), new Part(() -> ModItems.alloy("cinerio"), 1),
			new Part(() -> ModItems.alloy("voltaico"), 1), new Part(() -> ModItems.alloy("acero_estelar"), 1),
			new Part(() -> ModItems.alloy("obsidiacero"), 1), new Part(() -> ModItems.alloy("almacero"), 1),
			new Part(() -> ModItems.alloy("vidriacero"), 1)), 4,
			() -> ModItems.ORICALCO)
	);

	/** Everything the crucible will pour, alloy or not. */
	public static final List<Recipe> POURABLE =
		java.util.stream.Stream.concat(ALL.stream(), EXTRA.stream()).toList();

	/** The alloys no table can reach: white heat, and therefore the obsidian crucible or nothing. */
	public static final java.util.Set<String> WHITE_HEAT_ONLY = java.util.Set.of("solacero", "lunacero", "acero_vivo",
		"astralita");

	/** The four between the far forges and the forge heart (docs/ALEACIONES_CUMBRE.md, 2.7): two alloys and one more thing each. */
	public static final java.util.Set<String> MIDDLE = java.util.Set.of("espectracero", "corazon_de_volcan", "eclipse", "astralita");

	/**
	 * Alloys that are not gear metal.
	 *
	 * <p>Every other alloy is also a {@link dev.forja.material.ForgeMaterial}, so it can be cut into
	 * parts and worn. Refractory steel cannot: it exists to be poured over a part and cut into a mould,
	 * and making it a material would add it to the armour texture matrix for nothing at all.
	 */
	public static final java.util.Set<String> SHAPING_ONLY = java.util.Set.of("acero_refractario");

	/**
	 * Alloys of more ingredients than the forge table's star has points: only a crucible on a foundry line
	 * makes them, two bars in the pot and the rest drawn from the tanks. Oricalco is fourteen metals.
	 */
	public static final java.util.Set<String> FOUNDRY_ONLY = java.util.Set.of("oricalco", "astralita");

	private Alloys() {
	}

	/** The heat of the table at this position, read off the block right under it. */
	public static Heat heatUnder(Level level, BlockPos pos) {
		return heatOf(level.getBlockState(pos.below()));
	}

	/** How hot one block burns, on the tables' scale: what a table standing on it would read. */
	public static Heat heatOf(BlockState block) {
		// A caged wisp burns as hot as lava and does not set the workshop on fire.
		if (block.is(Blocks.LAVA) || block.is(Blocks.LAVA_CAULDRON) || block.is(dev.forja.registry.ModBlocks.FAROL_DE_PAVESA)) {
			return Heat.FUNDIDA;
		}
		if (block.is(Blocks.MAGMA_BLOCK) || block.is(Blocks.SOUL_FIRE) || block.is(Blocks.SOUL_CAMPFIRE)
			|| (block.is(Blocks.BLAST_FURNACE) && block.getOptionalValue(BlockStateProperties.LIT).orElse(false))) {
			return Heat.CALIENTE;
		}
		if (block.is(Blocks.FIRE) || (block.getBlock() instanceof CampfireBlock && block.getOptionalValue(CampfireBlock.LIT).orElse(false))
			|| (block.is(Blocks.FURNACE) && block.getOptionalValue(BlockStateProperties.LIT).orElse(false))) {
			return Heat.TEMPLADA;
		}
		return Heat.FRIA;
	}

	/**
	 * Something other than a fire underneath that can keep a forge hot: the heat pipes of the second
	 * foundry (docs/FUNDICION_V2.md, part B) will register one of these, and every block that asks
	 * {@link #heatAt} — the forge tables and the assembler — takes whichever is hotter.
	 */
	@FunctionalInterface
	public interface HeatSource {
		/** The heat this source brings to a forge standing at that position; FRIA if none. */
		Heat heatAt(Level level, BlockPos pos);
	}

	private static final List<HeatSource> SOURCES = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** Adds a way of heating a forge besides the fire under it. Called once, at startup. */
	public static void addHeatSource(HeatSource source) {
		SOURCES.add(source);
	}

	/**
	 * The heat a forge at this position works with: the block under it, or whatever a registered
	 * {@link HeatSource} brings it, the best of them. With no source registered yet this is exactly
	 * {@link #heatUnder}, so nothing that already worked reads a different heat.
	 */
	public static Heat heatAt(Level level, BlockPos pos) {
		Heat best = heatUnder(level, pos);
		for (HeatSource source : SOURCES) {
			Heat brought = source.heatAt(level, pos);
			if (brought.ordinal() > best.ordinal()) {
				best = brought;
			}
		}
		return best;
	}

	/**
	 * The hottest of the four blocks beside this position.
	 *
	 * <p>For the assembler: its finished piece leaves through a hopper underneath, the way everything in
	 * the foundry is emptied, so the fire under a forge table has nowhere to go under it. It warms from the
	 * side instead, the way the casting tables always have.
	 */
	public static Heat heatBeside(Level level, BlockPos pos) {
		Heat best = Heat.FRIA;
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			Heat here = heatOf(level.getBlockState(pos.relative(side)));
			if (here.ordinal() > best.ordinal()) {
				best = here;
			}
		}
		return best;
	}

	/**
	 * The alloy these ingredients make at this heat, or null. The best match wins, so a table over lava
	 * with iron and coal still gives steel rather than nothing.
	 */
	public static @Nullable Recipe match(List<ItemStack> inputs, Heat heat) {
		Recipe best = null;
		for (Recipe recipe : POURABLE) {
			if (!anywhere(recipe) || !heat.reaches(recipe.heat()) || !matches(recipe, inputs)) {
				continue;
			}
			if (best == null || recipe.heat().ordinal() > best.heat().ordinal()) {
				best = recipe;
			}
		}
		return best;
	}

	/** Whether the star holds exactly this alloy's ingredients, in any order and in any slot. */
	private static boolean matches(Recipe recipe, List<ItemStack> inputs) {
		List<Part> needed = new ArrayList<>(recipe.inputs());
		for (ItemStack stack : inputs) {
			if (stack.isEmpty()) {
				continue;
			}
			boolean used = false;
			for (int i = 0; i < needed.size(); i++) {
				if (needed.get(i).test(stack)) {
					needed.remove(i);
					used = true;
					break;
				}
			}
			if (!used) {
				return false;
			}
		}
		return needed.isEmpty();
	}

	/** How many of each star slot an alloy eats, in the order the slots were given. */
	public static int[] consumption(Recipe recipe, List<ItemStack> inputs) {
		int[] used = new int[inputs.size()];
		List<Part> needed = new ArrayList<>(recipe.inputs());
		for (int slot = 0; slot < inputs.size(); slot++) {
			ItemStack stack = inputs.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			for (int i = 0; i < needed.size(); i++) {
				if (needed.get(i).test(stack)) {
					used[slot] = needed.get(i).count();
					needed.remove(i);
					break;
				}
			}
		}
		return used;
	}
}
