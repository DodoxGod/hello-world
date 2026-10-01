package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.Forja;
import dev.forja.forge.Assembler;
import dev.forja.forge.BrokenGear;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Mastery;
import dev.forja.forge.RepairKitRecipe;
import dev.forja.forge.RepairKits;
import dev.forja.item.RepairKitItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * The repair kits (forge/RepairKits, Andy 2026-10-01): a kit and a forged piece in a crafting grid give the piece back
 * with 300 more uses, capped, with everything it carries; only when the piece's main part is of the kit's metal; and
 * every metal has its kit, its recipe, its unlock and its texture. FORJA_SOLO=kits in ForjaClientTest photographs them.
 */
public class KitsGameTests {
	private static com.google.gson.JsonObject readLang(net.fabricmc.loader.api.ModContainer mod, String name) {
		try {
			var path = mod.findPath("assets/forja/lang/" + name + ".json").orElseThrow();
			return com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/** A crafting table's menu for a mock player, with these stacks in these grid slots (0 to 8). */
	private static CraftingMenu grid(GameTestHelper helper, ServerPlayer player, int[] slots, ItemStack... stacks) {
		CraftingMenu menu = new CraftingMenu(1, player.getInventory(),
			ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		for (int i = 0; i < stacks.length; i++) {
			menu.getSlot(1 + slots[i]).set(stacks[i]);
		}
		menu.slotsChanged(menu.getSlot(1).container);
		return menu;
	}

	private static ItemStack kit(ForgeMaterial material) {
		return new ItemStack(RepairKits.kit(material));
	}

	/** A steel pickaxe with upgrades, potential, Maestria, a name and an enchantment, worn by {@code damage}. */
	private static ItemStack steelPick(GameTestHelper helper, int damage) {
		var registries = helper.getLevel().registryAccess();
		ItemStack pick = Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.ACERO, ForgeMaterial.MADERA, ForgeMaterial.CUERO), registries);
		pick.set(ModComponents.UPGRADES, Upgrades.EMPTY.with(Upgrade.FILO, 40));
		pick.set(ModComponents.POTENCIAL, 7);
		Mastery.setLevel(pick, 3, registries);
		pick.set(DataComponents.CUSTOM_NAME, Component.literal("Vieja Fiel"));
		var unbreaking = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);
		pick.enchant(unbreaking, 2);
		pick.setDamageValue(damage);
		return pick;
	}

	/** +300 through a real crafting grid, kit and piece in two far-apart slots, and nothing else about the piece changed. */
	@GameTest
	public void aKitMendsThreeHundredAndKeepsEverything(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack worn = steelPick(helper, 0);
		int max = worn.getMaxDamage();
		helper.assertTrue(max > 400, "un pico de acero debería pasar de 400 de uso, tiene " + max);
		worn.setDamageValue(max - 50);
		CraftingMenu menu = grid(helper, player, new int[] {0, 8}, worn.copy(), kit(ForgeMaterial.ACERO));
		ItemStack result = menu.getSlot(0).getItem();
		helper.assertTrue(result.is(ModItems.forged(ForgeType.PICO)), "el kit de acero y el pico deberían dar el pico, dan " + result);
		helper.assertTrue(result.getDamageValue() == max - 350,
			"debería quedar con 350 de uso (50 + 300), tiene " + (max - result.getDamageValue()));
		ItemStack expected = worn.copy();
		expected.setDamageValue(max - 350);
		helper.assertTrue(ItemStack.isSameItemSameComponents(result, expected),
			"todo lo demás debería seguir igual (piezas, mejoras, potencial, maestría, nombre, encantamiento): " + result.getComponentsPatch()
				+ " frente a " + expected.getComponentsPatch());
		helper.assertTrue(result.get(ModComponents.UPGRADES).percent(Upgrade.FILO) == 40 && result.get(ModComponents.POTENCIAL) == 7
			&& Mastery.level(result) == 3 && "Vieja Fiel".equals(result.getHoverName().getString()), "y en concreto sus datos");

		// Through the recipe manager too: the special recipe is the one that matched.
		CraftingInput input = CraftingInput.of(3, 3, List.of(kit(ForgeMaterial.ACERO), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, worn.copy()));
		var holder = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
		helper.assertTrue(holder.isPresent() && holder.get().value() instanceof RepairKitRecipe, "la receta especial del kit debería encajar");

		// Capped at the maximum: 100 worn comes back whole, not at -200.
		CraftingMenu capped = grid(helper, player, new int[] {4, 2}, steelPick(helper, 100), kit(ForgeMaterial.ACERO));
		helper.assertTrue(capped.getSlot(0).getItem().getDamageValue() == 0,
			"con 100 de desgaste debería quedar entero, queda con " + capped.getSlot(0).getItem().getDamageValue() + " de desgaste");

		// Armour: the plate is its main part. An iron chestplate lined with leather takes the iron kit.
		ItemStack chest = Assembler.create(ForgeType.PECHERA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.CUERO), helper.getLevel().registryAccess());
		chest.setDamageValue(chest.getMaxDamage() - 1);
		CraftingMenu armour = grid(helper, player, new int[] {1, 3}, kit(ForgeMaterial.HIERRO), chest);
		helper.assertTrue(armour.getSlot(0).getItem().getDamageValue() == Math.max(0, chest.getMaxDamage() - 1 - RepairKits.AMOUNT),
			"una pechera de hierro debería tomar el kit de hierro");

		// Taking the result spends the kit and the piece, like any recipe.
		menu.getSlot(0).onTake(player, result.copy());
		helper.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(9).getItem().isEmpty(), "al sacar el resultado se gastan el kit y la pieza");
		helper.succeed();
	}

	/** Another metal, another part, nothing to mend or nothing forged: no result. */
	@GameTest
	public void aKitOfAnotherMetalGivesNothing(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var registries = helper.getLevel().registryAccess();
		ItemStack worn = steelPick(helper, 200);
		helper.assertTrue(grid(helper, player, new int[] {0, 1}, worn.copy(), kit(ForgeMaterial.HIERRO)).getSlot(0).getItem().isEmpty(),
			"el kit de hierro no debería reparar un pico de acero");
		helper.assertTrue(grid(helper, player, new int[] {0, 1}, worn.copy(), kit(ForgeMaterial.DAMASCO)).getSlot(0).getItem().isEmpty(),
			"ni el de damasco");
		// The binding and handle do not count: an iron head on a steel binding is iron.
		ItemStack ironHead = Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.HIERRO, ForgeMaterial.ACERO, ForgeMaterial.ACERO), registries);
		ironHead.setDamageValue(100);
		helper.assertTrue(grid(helper, player, new int[] {0, 1}, ironHead.copy(), kit(ForgeMaterial.ACERO)).getSlot(0).getItem().isEmpty(),
			"el kit de acero no debería reparar un pico con cabeza de hierro aunque el mango y la atadura sean de acero");
		helper.assertTrue(!grid(helper, player, new int[] {0, 1}, ironHead.copy(), kit(ForgeMaterial.HIERRO)).getSlot(0).getItem().isEmpty(),
			"y el de hierro sí");
		// A whole piece has nothing to mend, and spending a kit on it would be a loss.
		helper.assertTrue(grid(helper, player, new int[] {0, 1}, steelPick(helper, 0), kit(ForgeMaterial.ACERO)).getSlot(0).getItem().isEmpty(),
			"un pico sin desgaste no debería aceptar el kit");
		// Not forged gear: a vanilla pickaxe, a loose part, a second kit.
		ItemStack vanilla = new ItemStack(Items.IRON_PICKAXE);
		vanilla.setDamageValue(100);
		helper.assertTrue(grid(helper, player, new int[] {0, 1}, vanilla, kit(ForgeMaterial.HIERRO)).getSlot(0).getItem().isEmpty(),
			"un pico vanilla no es una pieza forjada");
		helper.assertTrue(grid(helper, player, new int[] {0, 1}, kit(ForgeMaterial.HIERRO), kit(ForgeMaterial.HIERRO)).getSlot(0).getItem().isEmpty(),
			"dos kits no dan nada");
		// And a third thing in the grid spoils it.
		helper.assertTrue(grid(helper, player, new int[] {0, 1, 2}, worn.copy(), kit(ForgeMaterial.ACERO), new ItemStack(Items.STICK))
			.getSlot(0).getItem().isEmpty(), "con algo más en la mesa no debería dar nada");
		helper.succeed();
	}

	/** Forged gear never vanishes; at zero uses left it is broken, and a kit brings it back with 300. */
	@GameTest
	public void aKitBringsBackABrokenPiece(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack broken = steelPick(helper, 0);
		broken.setDamageValue(broken.getMaxDamage());
		helper.assertTrue(BrokenGear.isBroken(broken), "a cero de uso el pico debería estar roto");
		ItemStack result = grid(helper, player, new int[] {5, 6}, kit(ForgeMaterial.ACERO), broken).getSlot(0).getItem();
		helper.assertTrue(!result.isEmpty() && !BrokenGear.isBroken(result), "el kit debería sacar el pico de roto");
		helper.assertTrue(result.getMaxDamage() - result.getDamageValue() == RepairKits.AMOUNT,
			"y dejarlo con 300 de uso, tiene " + (result.getMaxDamage() - result.getDamageValue()));
		helper.succeed();
	}

	/** Every metal a main part can be has a kit, a recipe that makes it, an unlock, a model, a texture and a name. */
	@GameTest
	public void everyMetalHasAKitARecipeAndATexture(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		var recipes = server.getRecipeManager();
		var advancements = server.getAdvancements();
		var forja = FabricLoader.getInstance().getModContainer("forja").orElseThrow();
		List<ForgeMaterial> expected = new ArrayList<>();
		for (ForgeMaterial material : ForgeMaterial.values()) {
			if (material.canBeHead && (RepairKits.VANILLA_METALS.contains(material) || ModItems.alloy(material.getSerializedName()) != null)) {
				expected.add(material);
			}
		}
		helper.assertTrue(expected.size() >= 19, "deberían ser al menos 19 metales (15 aleaciones y 4 vanilla), son " + expected.size());
		helper.assertTrue(RepairKits.materials().equals(expected), "los kits deberían ser " + expected + ", son " + RepairKits.materials());
		List<String> wrong = new ArrayList<>();
		java.util.Map<String, com.google.gson.JsonObject> langs = new java.util.HashMap<>();
		for (ForgeMaterial material : expected) {
			String id = RepairKits.id(material);
			if (!(RepairKits.kit(material) instanceof RepairKitItem kit) || kit.material != material) {
				wrong.add(id + ": sin objeto");
				continue;
			}
			// Its recipe: two ingots of the metal, a leather and a string.
			var holder = recipes.byKey(ResourceKey.create(Registries.RECIPE, Forja.id(id)));
			ItemStack ingot = material.displayStack();
			CraftingInput input = CraftingInput.of(2, 2, List.of(ingot.copy(), ingot.copy(), new ItemStack(Items.LEATHER), new ItemStack(Items.STRING)));
			if (holder.isEmpty() || !(holder.get().value() instanceof ShapelessRecipe recipe)
				|| !recipe.matches(input, helper.getLevel()) || !recipe.assemble(input).is(kit)) {
				wrong.add(id + ": sin receta de dos " + ingot.getItem() + ", cuero y cuerda");
			}
			if (advancements.get(Forja.id("recipes/equipment/" + id)) == null) {
				wrong.add(id + ": su receta no se aprende con nada");
			}
			for (String path : List.of("assets/forja/textures/item/" + id + ".png", "assets/forja/items/" + id + ".json",
				"assets/forja/models/item/" + id + ".json")) {
				if (forja.findPath(path).isEmpty()) {
					wrong.add(id + ": falta " + path);
				}
			}
			for (String lang : List.of("es_mx", "es_es", "en_us")) {
				if (!langs.computeIfAbsent(lang, name -> readLang(forja, name)).has("item.forja." + id)) {
					wrong.add(id + ": sin nombre en " + lang);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "kits incompletos: " + wrong);
		Recipe<?> use = recipes.byKey(ResourceKey.create(Registries.RECIPE, Forja.id("reparar_con_kit"))).map(h -> h.value()).orElse(null);
		helper.assertTrue(use instanceof RepairKitRecipe, "la receta de usar un kit (reparar_con_kit) debería existir");
		helper.succeed();
	}
}
