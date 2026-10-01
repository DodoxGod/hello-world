package dev.forja.forge;

import com.mojang.serialization.MapCodec;
import dev.forja.Forja;
import dev.forja.material.ForgeMaterial;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A repair kit and a forged piece, anywhere in a crafting grid: the piece, mended (forge/RepairKits).
 *
 * <p>A special recipe, like vanilla's own two-damaged-tools repair: its result depends on what is put in, so
 * the recipe book cannot list it and JEI shows it as each kit's information page instead. Its one JSON file is
 * data/forja/recipe/reparar_con_kit.json, of type forja:kit_de_reparacion.
 */
public class RepairKitRecipe extends CustomRecipe {
	public static final RepairKitRecipe INSTANCE = new RepairKitRecipe();
	public static final MapCodec<RepairKitRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, RepairKitRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<RepairKitRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	public static void register() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Forja.id("kit_de_reparacion"), SERIALIZER);
	}

	/** The mended piece, or empty: exactly one kit and one other stack, which the kit fits. */
	public static ItemStack result(CraftingInput input) {
		if (input.ingredientCount() != 2) {
			return ItemStack.EMPTY;
		}
		@Nullable ForgeMaterial kit = null;
		ItemStack gear = ItemStack.EMPTY;
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			ForgeMaterial metal = RepairKits.materialOf(stack);
			if (metal != null && kit == null) {
				kit = metal;
			} else {
				gear = stack;
			}
		}
		return kit == null || gear.isEmpty() ? ItemStack.EMPTY : RepairKits.repair(gear, kit);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return !result(input).isEmpty();
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return result(input);
	}

	@Override
	public CraftingBookCategory category() {
		return CraftingBookCategory.EQUIPMENT;
	}

	@Override
	public RecipeSerializer<RepairKitRecipe> getSerializer() {
		return SERIALIZER;
	}
}
