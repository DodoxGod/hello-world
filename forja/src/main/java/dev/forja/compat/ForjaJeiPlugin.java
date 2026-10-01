package dev.forja.compat;

import java.util.ArrayList;
import java.util.List;

import dev.forja.Forja;
import dev.forja.forge.Alloys;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.item.UpgradeOrbItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.Upgrade;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * What Forja tells JEI. The mod has more upgrades and alloys than a book chapter can hold in one
 * screen, so JEI gets three pages of its own: what the parts of each item are, what feeds each
 * upgrade and how much per item, and which alloy comes out of which metals at which heat.
 *
 * <p>JEI is an optional dependency: this class is only ever loaded by JEI itself, so a game without
 * it never touches any of it.
 */
@JeiPlugin
public class ForjaJeiPlugin implements IModPlugin {
	/** One row of the upgrades page: an upgrade, and one of the ways to feed it. */
	public record UpgradeStep(Upgrade upgrade, Upgrade.Option option) {
	}

	/** One row of the pieces page: an item and the parts it is assembled from. */
	public record Assembly(ForgeType type, List<ItemStack> parts, ItemStack result) {
	}

	public static final RecipeType<UpgradeStep> UPGRADES = RecipeType.create("forja", "mejoras", UpgradeStep.class);
	public static final RecipeType<Alloys.Recipe> ALLOYS = RecipeType.create("forja", "aleaciones", Alloys.Recipe.class);
	public static final RecipeType<Assembly> ASSEMBLIES = RecipeType.create("forja", "piezas", Assembly.class);

	@Override
	public Identifier getPluginUid() {
		return Forja.id("jei");
	}

	private static mezz.jei.api.runtime.@org.jspecify.annotations.Nullable IJeiRuntime runtime;

	@Override
	public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime available) {
		runtime = available;
		dev.forja.client.PathClient.onProgress = ForjaJeiPlugin::showLearnedBooks;
		showLearnedBooks();
	}

	@Override
	public void onRuntimeUnavailable() {
		runtime = null;
		dev.forja.client.PathClient.onProgress = () -> {
		};
	}

	/**
	 * The guide's books are crafted from recipes learned along the way (GuideBooks), and a book's recipe is not to
	 * be seen before it is learned (Andy, 2026-09-29). Vanilla's recipe book already keeps it out; JEI shows every
	 * recipe it knows, so the ones not learned yet are hidden there, and shown again as they are learned.
	 */
	private static void showLearnedBooks() {
		mezz.jei.api.runtime.IJeiRuntime jei = runtime;
		if (jei == null) {
			return;
		}
		java.util.Set<String> done = dev.forja.client.PathClient.done();
		List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> hide = new ArrayList<>();
		List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> show = new ArrayList<>();
		jei.getRecipeManager().createRecipeLookup(mezz.jei.api.constants.RecipeTypes.CRAFTING).includeHidden().get().forEach(holder -> {
			dev.forja.GuideBooks.Book book = dev.forja.GuideBooks.gatedBy(holder.id());
			if (book != null) {
				(done.contains(book.unlock) ? show : hide).add(holder);
			}
		});
		if (!hide.isEmpty()) {
			jei.getRecipeManager().hideRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, hide);
		}
		if (!show.isEmpty()) {
			jei.getRecipeManager().unhideRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, show);
		}
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
		registration.addRecipeCategories(new UpgradeCategory(gui), new AlloyCategory(gui), new AssemblyCategory(gui));
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		List<UpgradeStep> steps = new ArrayList<>();
		for (Upgrade upgrade : Upgrade.values()) {
			for (Upgrade.Option option : upgrade.options) {
				steps.add(new UpgradeStep(upgrade, option));
			}
		}
		registration.addRecipes(UPGRADES, steps);
		// Everything a crucible pours, including the tempering bar, which is not an alloy.
		registration.addRecipes(ALLOYS, Alloys.POURABLE);

		List<Assembly> assemblies = new ArrayList<>();
		for (ForgeType type : ForgeType.values()) {
			List<ForgeMaterial> materials = Assembler.defaultMaterials(type);
			List<ItemStack> parts = new ArrayList<>();
			for (int slot = 0; slot < type.slots.size(); slot++) {
				parts.add(Assembler.createPart(type.slots.get(slot), materials.get(slot)));
			}
			assemblies.add(new Assembly(type, parts, Assembler.create(type, materials)));
		}
		registration.addRecipes(ASSEMBLIES, assemblies);

		// The repair kits (forge/RepairKits) are used through a special recipe whose result depends on the piece put in,
		// which neither the recipe book nor JEI can list: each kit gets an information page saying what it mends.
		for (ForgeMaterial material : dev.forja.forge.RepairKits.materials()) {
			registration.addItemStackInfo(new ItemStack(dev.forja.forge.RepairKits.kit(material)),
				Component.translatable("gui.forja.jei.kit_de_reparacion", dev.forja.forge.RepairKits.AMOUNT, material.displayName()));
		}
		// Oricalco's fourteen-metal recipe is wider than the alloy page, and that it is a forge material at all is news:
		// its ingot says both (ForgeMaterial.ORICALCO, docs/HERRERO_DIMENSION.md 1.4).
		registration.addItemStackInfo(new ItemStack(ModItems.ORICALCO), Component.translatable("gui.forja.jei.oricalco"));
	}

	/** The parts an item is made of, shown as the star would take them. */
	private record AssemblyCategory(IGuiHelper gui) implements IRecipeCategory<Assembly> {
		@Override
		public IRecipeType<Assembly> getRecipeType() {
			return ASSEMBLIES;
		}

		@Override
		public Component getTitle() {
			return Component.translatable("gui.forja.jei.piezas");
		}

		@Override
		public int getWidth() {
			return 150;
		}

		@Override
		public int getHeight() {
			return 40;
		}

		@Override
		public IDrawable getIcon() {
			return this.gui.createDrawableItemLike(ModItems.MESA_DE_FORJA);
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, Assembly assembly, IFocusGroup focuses) {
			for (int i = 0; i < assembly.parts().size(); i++) {
				builder.addInputSlot(1 + i * 19, 12).setStandardSlotBackground().addItemStack(assembly.parts().get(i));
			}
			builder.addOutputSlot(126, 12).setOutputSlotBackground().addItemStack(assembly.result());
		}

		@Override
		public void draw(Assembly assembly, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
			graphics.text(net.minecraft.client.Minecraft.getInstance().font,
				Component.translatable("item.forja." + assembly.type().id()), 1, 1, 0xFF404040, false);
		}

		@Override
		public Identifier getIdentifier(Assembly assembly) {
			return Forja.id("piezas/" + assembly.type().id());
		}
	}

	/** One way to feed one upgrade, with what each item is worth. */
	private record UpgradeCategory(IGuiHelper gui) implements IRecipeCategory<UpgradeStep> {
		@Override
		public IRecipeType<UpgradeStep> getRecipeType() {
			return UPGRADES;
		}

		@Override
		public Component getTitle() {
			return Component.translatable("gui.forja.jei.mejoras");
		}

		@Override
		public int getWidth() {
			return 150;
		}

		@Override
		public int getHeight() {
			return 46;
		}

		@Override
		public IDrawable getIcon() {
			return this.gui.createDrawableItemLike(ModItems.ORBE_DE_MEJORA);
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, UpgradeStep step, IFocusGroup focuses) {
			List<Upgrade.Requirement> requirements = step.option().requirements();
			for (int i = 0; i < requirements.size(); i++) {
				builder.addInputSlot(1 + i * 19, 18).setStandardSlotBackground().addItemStacks(display(requirements.get(i)));
			}
			builder.addOutputSlot(126, 18).setOutputSlotBackground()
				.addItemStack(UpgradeOrbItem.create(step.upgrade(), Math.min(100, step.option().percent())));
		}

		private static List<ItemStack> display(Upgrade.Requirement requirement) {
			return List.of(new ItemStack(requirement.display()));
		}

		@Override
		public void draw(UpgradeStep step, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
			var font = net.minecraft.client.Minecraft.getInstance().font;
			graphics.text(font, step.upgrade().displayName(), 1, 1, 0xFF000000 | step.upgrade().color, false);
			graphics.text(font, Component.translatable("gui.forja.jei.paso", step.option().percent()), 1, 10, 0xFF404040, false);
		}

		@Override
		public Identifier getIdentifier(UpgradeStep step) {
			return Forja.id("mejoras/" + step.upgrade().id() + "/" + step.option().percent() + "/" + step.option().requirements().size());
		}
	}

	/** Which alloy comes out of which metals, and how hot the table has to be. */
	private record AlloyCategory(IGuiHelper gui) implements IRecipeCategory<Alloys.Recipe> {
		@Override
		public IRecipeType<Alloys.Recipe> getRecipeType() {
			return ALLOYS;
		}

		@Override
		public Component getTitle() {
			return Component.translatable("gui.forja.jei.aleaciones");
		}

		@Override
		public int getWidth() {
			return 150;
		}

		@Override
		public int getHeight() {
			return 46;
		}

		@Override
		public IDrawable getIcon() {
			return this.gui.createDrawableItemLike(ModItems.alloy("acero"));
		}

		@Override
		public void setRecipe(IRecipeLayoutBuilder builder, Alloys.Recipe recipe, IFocusGroup focuses) {
			for (int i = 0; i < recipe.inputs().size(); i++) {
				Alloys.Part part = recipe.inputs().get(i);
				builder.addInputSlot(1 + i * 19, 18).setStandardSlotBackground()
					.addItemStack(new ItemStack(part.item().get(), part.count()));
			}
			builder.addOutputSlot(126, 18).setOutputSlotBackground().addItemStack(recipe.result());
		}

		@Override
		public void draw(Alloys.Recipe recipe, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
			var font = net.minecraft.client.Minecraft.getInstance().font;
			graphics.text(font, recipe.displayName(), 1, 1, 0xFF404040, false);
			// "The table is X" is a lie for white heat, where there is no table that will do, and for the far forge
			// alloys, which no table and no crucible make at all (docs/ALEACIONES_NETHER_END.md).
			Component where = !Alloys.anywhere(recipe)
				? Component.translatable("gui.forja.jei.fragua_lejana." + Alloys.place(recipe).id())
				: Alloys.FOUNDRY_ONLY.contains(recipe.id()) && recipe.heat() == Alloys.Heat.FORJA_BLANCA
				? Component.translatable("gui.forja.jei.cumbre")
				: recipe.heat() == Alloys.Heat.FORJA_BLANCA
				? Component.translatable("gui.forja.calor.crisol")
				: Component.translatable("gui.forja.calor", recipe.heat().displayName());
			graphics.text(font, where, 1, 10, 0xFF804000, false);
		}

		@Override
		public Identifier getIdentifier(Alloys.Recipe recipe) {
			return Forja.id("aleaciones/" + recipe.id());
		}
	}
}
