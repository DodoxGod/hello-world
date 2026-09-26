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
		// These three have two ingredients apiece on purpose: the crucible has two input slots, and a
		// table can never reach them, so a third ingredient would mean a recipe nothing can actually pour.
		/** Sun steel: damascus taken past molten with the fire still in it. It is brightest at noon. */
		new Recipe("solacero", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.alloy("damasco"), 1), new Part(() -> Items.BLAZE_ROD, 3)), 1),
		/** Moon steel: the same trick the other way round. It wakes up when the sun goes down. */
		new Recipe("lunacero", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.alloy("obsidiacero"), 1), new Part(() -> Items.ECHO_SHARD, 3)), 1),
		/** Living steel: the smith's own heart melted into damascus. The last metal in the mod. */
		new Recipe("acero_vivo", Heat.FORJA_BLANCA, List.of(new Part(() -> ModItems.CORAZON_DE_FORJA, 1), new Part(() -> ModItems.alloy("damasco"), 2)), 1)
	);

	/**
	 * What the crucible pours that is <b>not</b> an alloy.
	 *
	 * <p>{@link #ALL} is read by the material tables, the guide's ingot grid and the armour matrix, so
	 * everything in it has to be a metal you can wear. This list is for pours that are just items.
	 */
	public static final List<Recipe> EXTRA = List.of(
		new Recipe("lingote_de_temple", Heat.CALIENTE,
			List.of(new Part(() -> ModItems.alloy("acero_refractario"), 1), new Part(() -> Items.CLAY_BALL, 2)), 2,
			() -> ModItems.LINGOTE_DE_TEMPLE)
	);

	/** Everything the crucible will pour, alloy or not. */
	public static final List<Recipe> POURABLE =
		java.util.stream.Stream.concat(ALL.stream(), EXTRA.stream()).toList();

	/** The alloys no table can reach: white heat, and therefore the obsidian crucible or nothing. */
	public static final java.util.Set<String> WHITE_HEAT_ONLY = java.util.Set.of("solacero", "lunacero", "acero_vivo");

	/**
	 * Alloys that are not gear metal.
	 *
	 * <p>Every other alloy is also a {@link dev.forja.material.ForgeMaterial}, so it can be cut into
	 * parts and worn. Refractory steel cannot: it exists to be poured over a part and cut into a mould,
	 * and making it a material would add it to the armour texture matrix for nothing at all.
	 */
	public static final java.util.Set<String> SHAPING_ONLY = java.util.Set.of("acero_refractario");

	private Alloys() {
	}

	/** The heat of the table at this position, read off the block right under it. */
	public static Heat heatUnder(Level level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		// A caged wisp burns as hot as lava and does not set the workshop on fire.
		if (below.is(Blocks.LAVA) || below.is(Blocks.LAVA_CAULDRON) || below.is(dev.forja.registry.ModBlocks.FAROL_DE_PAVESA)) {
			return Heat.FUNDIDA;
		}
		if (below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.SOUL_CAMPFIRE)
			|| (below.is(Blocks.BLAST_FURNACE) && below.getOptionalValue(BlockStateProperties.LIT).orElse(false))) {
			return Heat.CALIENTE;
		}
		if (below.is(Blocks.FIRE) || (below.getBlock() instanceof CampfireBlock && below.getOptionalValue(CampfireBlock.LIT).orElse(false))
			|| (below.is(Blocks.FURNACE) && below.getOptionalValue(BlockStateProperties.LIT).orElse(false))) {
			return Heat.TEMPLADA;
		}
		return Heat.FRIA;
	}

	/**
	 * The alloy these ingredients make at this heat, or null. The best match wins, so a table over lava
	 * with iron and coal still gives steel rather than nothing.
	 */
	public static @Nullable Recipe match(List<ItemStack> inputs, Heat heat) {
		Recipe best = null;
		for (Recipe recipe : POURABLE) {
			if (!heat.reaches(recipe.heat()) || !matches(recipe, inputs)) {
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
