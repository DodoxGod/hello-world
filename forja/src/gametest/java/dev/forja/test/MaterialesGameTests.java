package dev.forja.test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.forja.Forja;
import dev.forja.forge.Alloys;
import dev.forja.forge.Assembler;
import dev.forja.material.ForgeMaterial;
import dev.forja.menu.ForgeMenu;
import dev.forja.part.PartType;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where every material comes from, checked against the data that is supposed to make it possible.
 *
 * <p>The mod has no ores of its own: every material is either something vanilla already hands out,
 * an alloy of those, or a drop from one of the mod's own creatures. So what can break is not a missing
 * ore feature but the joins — a material whose "one item" is another material's, a loot table the game
 * throws away, a structure block that no longer exists, an alloy that needs something nothing makes.
 * Each of those was found once by playing; these make sure it is found by the build next time.
 * Run with ./gradlew runGametest. The in-world half is FORJA_SOLO=materiales in ForjaClientTest.
 */
public class MaterialesGameTests {
	/**
	 * The mod's own items that some material or alloy is made of and that no recipe makes: each is a
	 * drop, and this names what drops it so a new one cannot slip in without a source.
	 */
	static Map<Item, String> dropped() {
		// A method, not a field: the test classes are loaded before the mod has registered its items.
		return Map.of(
			ModItems.HIERRO_ESTELAR, "meteoritos (WorldEvents.strike)",
			ModItems.PLACA_HUECA, "coraza vacia (HollowArmor.die)",
			ModItems.ASCUA, "pavesa y ascua mayor (EmberWisp, GreaterEmber)",
			ModItems.ESCORIA, "escoria viviente (LivingSlag.die)",
			ModItems.CORAZON_DE_FORJA, "herrero caido (FallenSmith.die)"
		);
	}

	/**
	 * Every material hands back an item it itself takes. The crucible melts gear into it and the parts
	 * table breaks loose parts down into it, so a wrong one is not a wrong picture but a transmutation:
	 * escoria used to fall through to the netherite ingot, and a slag pickaxe melted into netherite.
	 */
	@GameTest
	public void everyMaterialHandsBackItsOwnItem(GameTestHelper helper) {
		var items = helper.getLevel().registryAccess().lookupOrThrow(Registries.ITEM);
		List<String> wrong = new ArrayList<>();
		for (ForgeMaterial material : ForgeMaterial.values()) {
			ItemStack stack = material.displayStack();
			if (stack.isEmpty()) {
				wrong.add(material + ": no item");
				continue;
			}
			if (ForgeMaterial.fromInput(stack) != material) {
				wrong.add(material + ": " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " reads as " + ForgeMaterial.fromInput(stack));
			}
			if (material.repairItems(items).stream().noneMatch(holder -> holder.value() == stack.getItem())) {
				wrong.add(material + ": " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " does not repair it");
			}
			// A loose part taken apart goes back to what it was cut or poured from, and to nothing else.
			for (PartType type : PartType.values()) {
				if (!type.accepts(material)) {
					continue;
				}
				for (ItemStack back : Assembler.disassemble(Assembler.createPart(type, material)).returned()) {
					if (ForgeMaterial.fromInput(back) != material) {
						wrong.add(material + ": a loose " + type.id() + " breaks down into " + BuiltInRegistries.ITEM.getKey(back.getItem()));
					}
				}
				break;
			}
		}
		helper.assertTrue(wrong.isEmpty(), "materiales que devuelven otra cosa: " + wrong);
		helper.assertTrue(ForgeMaterial.ESCORIA.displayStack().is(ModItems.ESCORIA),
			"la escoria debe devolver escoria, devuelve " + ForgeMaterial.ESCORIA.displayStack());
		helper.succeed();
	}

	/**
	 * No two materials take the same item: {@link ForgeMaterial#fromInput} returns the first that
	 * matches, so an item in two of them would silently be only the first.
	 */
	@GameTest
	public void noItemIsTwoMaterials(GameTestHelper helper) {
		List<String> both = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ItemStack stack = new ItemStack(item);
			List<ForgeMaterial> takers = new ArrayList<>();
			for (ForgeMaterial material : ForgeMaterial.values()) {
				if (!stack.isEmpty() && material.matches(stack)) {
					takers.add(material);
				}
			}
			if (takers.size() > 1) {
				both.add(BuiltInRegistries.ITEM.getKey(item) + " " + takers);
			}
		}
		helper.assertTrue(both.isEmpty(), "objetos que son dos materiales: " + both);
		helper.succeed();
	}

	/**
	 * Every material has a way into a survival world: an alloy recipe, a line in the guide saying where
	 * it comes from, and — when the item is the mod's own — either a recipe that makes it or a creature
	 * named in {@link #dropped()} (the client test kills each one and picks it up).
	 */
	@GameTest
	public void everyMaterialHasASource(GameTestHelper helper) {
		JsonObject lang = lang("es_mx");
		JsonObject english = lang("en_us");
		List<String> missing = new ArrayList<>();
		for (ForgeMaterial material : ForgeMaterial.values()) {
			String id = material.getSerializedName();
			boolean alloy = Alloys.ALL.stream().anyMatch(recipe -> recipe.id().equals(id));
			if (!alloy && (!lang.has("material.forja." + id + ".origen") || !english.has("material.forja." + id + ".origen"))) {
				missing.add(id + ": the guide does not say where it comes from");
			}
			Item item = material.displayStack().getItem();
			if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Forja.MOD_ID) && !alloy && !dropped().containsKey(item)) {
				missing.add(id + ": " + BuiltInRegistries.ITEM.getKey(item) + " comes from nowhere");
			}
		}
		for (String key : List.of("gui.forja.guia.origen", "gui.forja.guia.origen.aleacion", "gui.forja.guia.origen.aleacion_blanca",
			"gui.forja.guia.se_corta", "gui.forja.guia.se_cuela")) {
			if (!lang.has(key) || !english.has(key)) {
				missing.add(key);
			}
		}
		helper.assertTrue(missing.isEmpty(), "materiales sin origen: " + missing);
		helper.succeed();
	}

	/**
	 * Every alloy can actually be poured: each ingredient is vanilla, another alloy made at no more heat
	 * than this one, or a known drop; the star has room for its ingredients; and the three white-heat
	 * ones fit the obsidian crucible's two slots, since no table can make them.
	 */
	@GameTest
	public void everyAlloyCanBePoured(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Set<String> ids = new java.util.HashSet<>();
		for (Alloys.Recipe recipe : Alloys.POURABLE) {
			if (!ids.add(recipe.id())) {
				wrong.add(recipe.id() + ": two recipes");
			}
			if (recipe.result().isEmpty()) {
				wrong.add(recipe.id() + ": makes nothing");
			}
			for (Alloys.Part part : recipe.inputs()) {
				Item item = part.item().get();
				if (item == null || item == Items.AIR) {
					wrong.add(recipe.id() + ": an ingredient that does not exist");
					continue;
				}
				Identifier key = BuiltInRegistries.ITEM.getKey(item);
				if (!key.getNamespace().equals(Forja.MOD_ID) || dropped().containsKey(item)) {
					continue;
				}
				Alloys.Recipe maker = Alloys.POURABLE.stream().filter(other -> other.made().get() == item).findFirst().orElse(null);
				if (maker == null) {
					wrong.add(recipe.id() + ": " + key + " is made by nothing");
				} else if (maker.heat().ordinal() > recipe.heat().ordinal()) {
					wrong.add(recipe.id() + ": needs " + key + ", which takes more heat than it has");
				}
			}
			if (recipe.heat() == Alloys.Heat.FORJA_BLANCA) {
				int total = recipe.inputs().stream().mapToInt(Alloys.Part::count).sum();
				if (recipe.inputs().size() > 2 || total > dev.forja.block.CrucibleBlock.Tier.OBSIDIANA.capacity) {
					wrong.add(recipe.id() + ": does not fit the obsidian crucible");
				}
			} else if (recipe.inputs().size() > ForgeMenu.STAR_COUNT && !Alloys.FOUNDRY_ONLY.contains(recipe.id())) {
				wrong.add(recipe.id() + ": more ingredients than the star has points");
			}
			// Every alloy that is gear metal is a material by the same name, and melts back into its ingot.
			if (Alloys.ALL.contains(recipe) && !Alloys.SHAPING_ONLY.contains(recipe.id())) {
				ForgeMaterial material = ForgeMaterial.fromInput(recipe.result());
				if (material == null || !material.getSerializedName().equals(recipe.id())) {
					wrong.add(recipe.id() + ": its ingot is not the material " + recipe.id());
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "aleaciones que no se pueden colar: " + wrong);
		helper.succeed();
	}

	/**
	 * Every block of the mod drops itself to the right tool: a loot table that loads and names the
	 * block, a mineable tag for the tool it wants, and the bare hand refused where it should be.
	 */
	@GameTest
	public void everyBlockDropsItself(GameTestHelper helper) {
		var level = helper.getLevel();
		var tables = level.getServer().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE);
		List<TagKey<Block>> mineable = List.of(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.MINEABLE_WITH_AXE,
			BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE);
		ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
		ItemStack axe = new ItemStack(Items.NETHERITE_AXE);
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			if (!id.getNamespace().equals(Forja.MOD_ID)) {
				continue;
			}
			// The Cementerio entre Estrellas' scenery has no item on purpose: the molten metal cannot be
			// broken, and a grave's weapon is rust (docs/HERRERO_DIMENSION.md, 2.5 and 2.6).
			if (id.getPath().equals("metal_fundido") || id.getPath().equals("arma_clavada")
				|| id.getPath().equals("mensula_estelar") || id.getPath().equals("portal_estelar")) {
				continue;
			}
			checked++;
			BlockState state = block.defaultBlockState();
			var key = block.getLootTable();
			if (key.isEmpty() || tables.get(key.get()).isEmpty()) {
				wrong.add(id + ": no loot table");
				continue;
			}
			ItemStack tool = state.is(BlockTags.MINEABLE_WITH_AXE) ? axe : pickaxe;
			List<ItemStack> drops = Block.getDrops(state, level, BlockPos.ZERO, null, null, tool);
			if (drops.stream().noneMatch(stack -> stack.is(block.asItem()))) {
				wrong.add(id + ": drops " + drops + " instead of itself");
			}
			if (mineable.stream().noneMatch(state::is)) {
				wrong.add(id + ": no tool mines it faster than a hand");
			}
			if (state.requiresCorrectToolForDrops()) {
				if (ItemStack.EMPTY.isCorrectToolForDrops(state)) {
					wrong.add(id + ": asks for a tool and takes a hand");
				}
				if (!tool.isCorrectToolForDrops(state)) {
					wrong.add(id + ": not even netherite is the right tool");
				}
			}
		}
		helper.assertTrue(checked >= 20, "solo " + checked + " bloques de forja");
		helper.assertTrue(wrong.isEmpty(), "bloques que no se sueltan bien: " + wrong);
		// The two the fallen smith leaves are diamond-tier, and an iron pickaxe does not get them.
		for (Block block : List.of(dev.forja.registry.ModBlocks.FRAGUA_APAGADA, dev.forja.registry.ModBlocks.YUNQUE_DEL_HERRERO)) {
			helper.assertFalse(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()),
				BuiltInRegistries.BLOCK.getKey(block) + " needs a diamond pickaxe");
			helper.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(block.defaultBlockState()),
				BuiltInRegistries.BLOCK.getKey(block) + " should come off with a diamond pickaxe");
		}
		helper.succeed();
	}

	/**
	 * Every data file the mod ships is one the game actually loaded. A file that fails to parse is not
	 * an error anybody sees: the game logs one line at boot and carries on without it. That is how the
	 * castle's guard-room chests came up empty for a whole version — "minecraft:chain" is
	 * "minecraft:iron_chain" now, and one bad item threw away the whole table.
	 */
	@GameTest
	public void everyDataFileLoads(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		var resources = server.getResourceManager();
		var tables = server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE);
		List<String> wrong = new ArrayList<>();
		int loot = 0;
		for (Identifier file : resources.listResources("loot_table", id -> id.getNamespace().equals(Forja.MOD_ID) && id.getPath().endsWith(".json")).keySet()) {
			Identifier id = Forja.id(file.getPath().substring("loot_table/".length(), file.getPath().length() - ".json".length()));
			loot++;
			if (tables.get(ResourceKey.create(Registries.LOOT_TABLE, id)).isEmpty()) {
				wrong.add("loot table " + id);
			}
		}
		int recipes = 0;
		for (Identifier file : resources.listResources("recipe", id -> id.getNamespace().equals(Forja.MOD_ID) && id.getPath().endsWith(".json")).keySet()) {
			Identifier id = Forja.id(file.getPath().substring("recipe/".length(), file.getPath().length() - ".json".length()));
			recipes++;
			if (server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id)).isEmpty()) {
				wrong.add("recipe " + id);
			}
		}
		int tags = 0;
		for (Identifier file : resources.listResources("tags/item", id -> id.getNamespace().equals(Forja.MOD_ID) && id.getPath().endsWith(".json")).keySet()) {
			Identifier id = Forja.id(file.getPath().substring("tags/item/".length(), file.getPath().length() - ".json".length()));
			tags++;
			TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
			if (!BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator().hasNext()) {
				wrong.add("empty item tag " + id);
			}
		}
		helper.assertTrue(loot > 30 && recipes > 40 && tags > 15, "too few files found: " + loot + " loot tables, " + recipes + " recipes, " + tags + " tags");
		helper.assertTrue(wrong.isEmpty(), "archivos que el juego tiró: " + wrong);
		helper.succeed();
	}

	/**
	 * Every block a structure of the mod is built of exists. An unknown name in a structure's palette
	 * is not an error at all: the game places air instead, and the barrow's vault had four holes in it
	 * where "polished_deepslate_bricks" (which never existed) was meant to be.
	 */
	@GameTest
	public void structuresAreBuiltOfRealBlocks(GameTestHelper helper) {
		var resources = helper.getLevel().getServer().getResourceManager();
		List<String> wrong = new ArrayList<>();
		int files = 0;
		for (var entry : resources.listResources("structure", id -> id.getNamespace().equals(Forja.MOD_ID) && id.getPath().endsWith(".nbt")).entrySet()) {
			files++;
			CompoundTag root;
			try (InputStream in = entry.getValue().open()) {
				root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			} catch (java.io.IOException e) {
				wrong.add(entry.getKey() + ": unreadable");
				continue;
			}
			List<ListTag> palettes = new ArrayList<>();
			root.getList("palette").ifPresent(palettes::add);
			ListTag many = root.getListOrEmpty("palettes");
			for (int i = 0; i < many.size(); i++) {
				many.getList(i).ifPresent(palettes::add);
			}
			for (ListTag palette : palettes) {
				for (int i = 0; i < palette.size(); i++) {
					String name = palette.getCompoundOrEmpty(i).getStringOr("Name", "");
					Identifier id = Identifier.tryParse(name);
					if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
						wrong.add(entry.getKey().getPath() + ": " + name);
					}
				}
			}
		}
		helper.assertTrue(files > 20, "only " + files + " structure files found");
		helper.assertTrue(wrong.isEmpty(), "bloques que no existen en las estructuras: " + wrong);
		helper.succeed();
	}

	/**
	 * The ores a smith lives on still smelt into what the materials take: this is vanilla, but it is the
	 * whole bottom of the ladder, and a datapack or a rename that broke it would break every metal.
	 */
	@GameTest
	public void oresSmeltIntoMaterials(GameTestHelper helper) {
		var level = helper.getLevel();
		Object[][] cases = {
			{Items.RAW_IRON, ForgeMaterial.HIERRO}, {Items.IRON_ORE, ForgeMaterial.HIERRO}, {Items.DEEPSLATE_IRON_ORE, ForgeMaterial.HIERRO},
			{Items.RAW_COPPER, ForgeMaterial.COBRE}, {Items.COPPER_ORE, ForgeMaterial.COBRE},
			{Items.RAW_GOLD, ForgeMaterial.ORO}, {Items.GOLD_ORE, ForgeMaterial.ORO}, {Items.NETHER_GOLD_ORE, ForgeMaterial.ORO},
			{Items.DIAMOND_ORE, ForgeMaterial.DIAMANTE}, {Items.EMERALD_ORE, ForgeMaterial.ESMERALDA},
			{Items.NETHER_QUARTZ_ORE, ForgeMaterial.CUARZO}, {Items.RESIN_CLUMP, ForgeMaterial.RESINA},
		};
		List<String> wrong = new ArrayList<>();
		for (Object[] row : cases) {
			SingleRecipeInput input = new SingleRecipeInput(new ItemStack((Item) row[0]));
			var smelted = level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level);
			ItemStack out = smelted.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
			if (ForgeMaterial.fromInput(out) != row[1]) {
				wrong.add(BuiltInRegistries.ITEM.getKey((Item) row[0]) + " -> " + out);
			}
		}
		// Netherite comes the long way: debris smelts into scrap, and the scrap is what damascus takes.
		SingleRecipeInput debris = new SingleRecipeInput(new ItemStack(Items.ANCIENT_DEBRIS));
		ItemStack scrap = level.getServer().getRecipeManager().getRecipeFor(RecipeType.BLASTING, debris, level)
			.map(recipe -> recipe.value().assemble(debris)).orElse(ItemStack.EMPTY);
		if (!scrap.is(Items.NETHERITE_SCRAP)) {
			wrong.add("ancient_debris -> " + scrap);
		}
		helper.assertTrue(wrong.isEmpty(), "menas que no dan su material: " + wrong);
		helper.succeed();
	}

	/**
	 * What comes off fire does not burn: embers, slag and the forge heart all drop where there is lava
	 * or fire about, and an item that burned up as it landed read as a creature that drops nothing.
	 */
	@GameTest
	public void fireMaterialsStandFire(GameTestHelper helper) {
		var sources = helper.getLevel().damageSources();
		for (Item item : List.of(ModItems.ASCUA, ModItems.ESCORIA, ModItems.CORAZON_DE_FORJA)) {
			ItemStack stack = new ItemStack(item);
			helper.assertFalse(stack.canBeHurtBy(sources.lava()), BuiltInRegistries.ITEM.getKey(item) + " should not burn in lava");
			helper.assertFalse(stack.canBeHurtBy(sources.inFire()), BuiltInRegistries.ITEM.getKey(item) + " should not burn in fire");
		}
		helper.succeed();
	}

	private static JsonObject lang(String name) {
		try (InputStream in = MaterialesGameTests.class.getResourceAsStream("/assets/forja/lang/" + name + ".json")) {
			if (in == null) {
				throw new AssertionError("no lang file " + name);
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}
}
