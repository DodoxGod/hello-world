package dev.forja.test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.forja.Forja;
import dev.forja.ForjaAdvancements;
import dev.forja.ForjaPath;
import dev.forja.GuideBooks;
import dev.forja.GuideBooks.Book;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * The guide's books on a real server (docs/LIBROS_GUIA.md): every book is crafted, from a recipe the player learns
 * with an advancement; the grid refuses a book whose recipe is not learned yet; a world from before the books gets
 * the recipes its advancements earn; and the books and the path agree on where every step is explained. The pages
 * themselves are the client test's (FORJA_SOLO=libro). Run with ./gradlew runGametest.
 */
public class LibrosGameTests {
	/** Eight books on the shelf, each with its colour, and each written one with an item and a recipe of a book and one thing. */
	@GameTest
	public void everyWrittenBookIsABookAndOneThing(GameTestHelper helper) {
		helper.assertTrue(GuideBooks.SHELF.size() == 8, "la estantería tiene " + GuideBooks.SHELF.size() + " libros, no 8");
		helper.assertTrue(GuideBooks.SHELF.getFirst() == Book.CUADERNO, "el primero de la estantería debería ser el cuaderno");
		var recipes = helper.getLevel().getServer().getRecipeManager();
		var advancements = helper.getLevel().getServer().getAdvancements();
		for (Book book : GuideBooks.SHELF) {
			helper.assertTrue(!book.chapters().isEmpty(), "el libro " + book + " no tiene capítulos");
			if (!book.ready) {
				continue;
			}
			helper.assertTrue(book.item() != null, "el libro " + book + " está escrito y no tiene objeto");
			var holder = recipes.byKey(book.recipe());
			helper.assertTrue(holder.isPresent() && holder.get().value() instanceof ShapelessRecipe,
				"el libro " + book + " no tiene receta sin forma " + book.recipe());
			ShapelessRecipe recipe = (ShapelessRecipe) holder.get().value();
			var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), new ItemStack(book.ingredient.get())));
			helper.assertTrue(recipe.matches(input, helper.getLevel()) && recipe.assemble(input).is(book.item()),
				"la receta de " + book + " debería ser un libro y " + book.ingredient.get());
			if (book.unlock != null) {
				AdvancementHolder unlock = advancements.get(Forja.id("forja/" + book.unlock));
				helper.assertTrue(unlock != null, "el libro " + book + " se aprende con forja/" + book.unlock + ", que no existe");
				// A recipe advancement would teach it to whoever holds a book: the books after the notebook must not have one.
				helper.assertTrue(advancements.get(Forja.id("recipes/misc/" + book.itemId)) == null,
					"el libro " + book + " no debería tener logro de receta: se aprende con su logro de Forja");
			}
		}
		helper.assertTrue(GuideBooks.of(ModItems.GUIA_DE_FORJA) == Book.CUADERNO && GuideBooks.of(ModItems.LIBRO_YUNQUE) == Book.YUNQUE
			&& GuideBooks.of(ModItems.TOMO_DE_FORJA) == Book.TOMO, "cada objeto debería abrir su libro");
		helper.succeed();
	}

	/** The grid gives nothing for book I until its recipe is learned, and the book once it is. */
	@GameTest
	public void theGridRefusesAnUnlearnedBook(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var key = Book.YUNQUE.recipe();
		player.getRecipeBook().remove(key);
		CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		menu.getSlot(1).set(new ItemStack(Items.BOOK));
		menu.getSlot(2).set(new ItemStack(ModItems.PLANTILLA));
		menu.slotsChanged(menu.getSlot(1).container);
		helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "sin aprender la receta la mesa no debería dar El yunque, da " + menu.getSlot(0).getItem());
		// The notebook is never refused: book and iron always make one.
		menu.getSlot(2).set(new ItemStack(Items.IRON_INGOT));
		menu.slotsChanged(menu.getSlot(1).container);
		helper.assertTrue(menu.getSlot(0).getItem().is(ModItems.GUIA_DE_FORJA), "un libro y hierro deberían dar siempre el cuaderno, dan " + menu.getSlot(0).getItem());
		// Learned, the same grid makes it.
		player.getRecipeBook().add(key);
		menu.getSlot(2).set(new ItemStack(ModItems.PLANTILLA));
		menu.slotsChanged(menu.getSlot(1).container);
		helper.assertTrue(menu.getSlot(0).getItem().is(ModItems.LIBRO_YUNQUE), "con la receta aprendida la mesa debería dar El yunque, da " + menu.getSlot(0).getItem());
		helper.succeed();
	}

	/** The recipe comes with the advancement, however it is granted; and a player who had it done already gets it on joining. */
	@GameTest
	public void theRecipeIsLearnedWithItsAdvancement(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var key = Book.YUNQUE.recipe();
		AdvancementHolder template = helper.getLevel().getServer().getAdvancements().get(Forja.id("forja/plantilla"));
		player.getAdvancements().revoke(template, "done");
		player.getRecipeBook().remove(key);
		helper.assertTrue(!player.getRecipeBook().contains(key), "el jugador nuevo no debería saber hacer El yunque");
		ForjaAdvancements.award(player, "plantilla");
		helper.assertTrue(player.getRecipeBook().contains(key), "grabar la primera plantilla debería enseñar la receta de El yunque");
		// An old world: the advancement done, the recipe never taught. Joining teaches it.
		player.getRecipeBook().remove(key);
		GuideBooks.catchUp(player);
		helper.assertTrue(player.getRecipeBook().contains(key), "al entrar, un logro ya hecho debería enseñar su receta");
		// And nothing else teaches it: another advancement does not.
		player.getRecipeBook().remove(key);
		player.getAdvancements().revoke(template, "done");
		ForjaAdvancements.award(player, "desarmar");
		GuideBooks.catchUp(player);
		helper.assertTrue(!player.getRecipeBook().contains(key), "otro logro no debería enseñar El yunque");
		helper.succeed();
	}

	/** The notebook is handed out on joining, and only it. */
	@GameTest
	public void onlyTheNotebookIsGiven(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		boolean notebook = false;
		boolean other = false;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			notebook |= stack.is(ModItems.GUIA_DE_FORJA);
			other |= stack.is(ModItems.LIBRO_YUNQUE) || stack.is(ModItems.TOMO_DE_FORJA);
		}
		helper.assertTrue(notebook, "al entrar se debería recibir el cuaderno del aprendiz");
		helper.assertTrue(!other, "al entrar no se debería recibir ningún otro libro");
		helper.succeed();
	}

	/**
	 * The book's upgrade probe (UpgradeFit) against the star itself (UpgradeRecipes.apply at the greater table, with
	 * flux): for a sword, a pickaxe and a chestplate — fresh, carrying an upgrade that shuts out its group, and with a
	 * potential so low the load is full — every upgrade the probe says fits does go up with its own ingredients, and
	 * every one it says does not, does not. Pacts are left to Pacts.fits, which the star asks on its own.
	 */
	@GameTest
	public void theProbeAgreesWithTheStar(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		List<ItemStack> pieces = new java.util.ArrayList<>();
		for (dev.forja.forge.ForgeType type : List.of(dev.forja.forge.ForgeType.ESPADA, dev.forja.forge.ForgeType.PICO, dev.forja.forge.ForgeType.PECHERA)) {
			ItemStack fresh = dev.forja.forge.Assembler.create(type, dev.forja.forge.Assembler.defaultMaterials(type), registries);
			pieces.add(fresh);
			ItemStack grouped = fresh.copy();
			dev.forja.upgrade.Upgrade first = dev.forja.upgrade.UpgradeFit.of(fresh, null).stream()
				.filter(fit -> fit.upgrade().group != dev.forja.upgrade.Upgrade.Group.NONE).findFirst().orElseThrow().upgrade();
			pieces.add(dev.forja.upgrade.Upgrades.with(grouped, first, 60));
			ItemStack poor = fresh.copy();
			poor.set(dev.forja.registry.ModComponents.POTENCIAL, 24);
			pieces.add(poor);
		}
		int checked = 0;
		StringBuilder wrong = new StringBuilder();
		for (ItemStack gear : pieces) {
			for (dev.forja.upgrade.UpgradeFit.Fit fit : dev.forja.upgrade.UpgradeFit.of(gear, null)) {
				dev.forja.upgrade.Upgrade upgrade = fit.upgrade();
				helper.assertTrue(upgrade.appliesTo(gear.get(dev.forja.registry.ModComponents.PARTS).type()), "el probador lista " + upgrade + ", que no va en esa pieza");
				if (upgrade.options.isEmpty() || upgrade.isPact()) {
					continue;
				}
				List<ItemStack> ingredients = new java.util.ArrayList<>();
				for (dev.forja.upgrade.Upgrade.Requirement requirement : upgrade.options.getFirst().requirements()) {
					ingredients.add(requirement.displayStack().copyWithCount(64));
				}
				while (ingredients.size() < 4) {
					ingredients.add(ItemStack.EMPTY);
				}
				ingredients.add(new ItemStack(ModItems.FUNDENTE_MAESTRO, 64));
				dev.forja.upgrade.UpgradeRecipes.Application application = dev.forja.upgrade.UpgradeRecipes.apply(gear, ingredients, registries, 0,
					(candidate, flux) -> dev.forja.forge.Potential.ceiling(gear, candidate, dev.forja.menu.Station.FORJA_MAYOR, flux));
				if (application == null || application.upgrade() != upgrade) {
					// The ingredients belong to another upgrade too, and the star took that one: not this test's question.
					continue;
				}
				boolean rose = application.conflict() == null && application.after() > application.before();
				if (rose != fit.fits()) {
					wrong.append(gear.getHoverName().getString()).append(' ').append(upgrade).append(": probador ").append(fit.reason())
						.append(", estrella ").append(application.before()).append("->").append(application.after()).append("; ");
				}
				checked++;
			}
		}
		helper.assertTrue(wrong.isEmpty(), "el probador y la estrella no coinciden: " + wrong);
		helper.assertTrue(checked > 60, "el probador debería haberse comparado con la estrella en muchas mejoras, solo " + checked);
		// And the three kinds of piece get their own lists.
		helper.assertTrue(names(pieces.get(0)).contains("FILO") && !names(pieces.get(0)).contains("EFICIENCIA"), "una espada lleva Filo y no Eficiencia");
		helper.assertTrue(names(pieces.get(3)).contains("EFICIENCIA") && names(pieces.get(3)).contains("FORTUNA"), "un pico lleva Eficiencia y Fortuna");
		helper.assertTrue(names(pieces.get(6)).contains("PROTECCION") && !names(pieces.get(6)).contains("FILO"), "una pechera lleva Protección y no Filo");
		helper.succeed();
	}

	private static Set<String> names(ItemStack gear) {
		Set<String> found = new HashSet<>();
		for (dev.forja.upgrade.UpgradeFit.Fit fit : dev.forja.upgrade.UpgradeFit.of(gear, null)) {
			found.add(fit.upgrade().name());
		}
		return found;
	}

	/** Every step of the path is explained in a chapter some book has, and the books cover the whole path between them. */
	@GameTest
	public void everyStepHasItsBook(GameTestHelper helper) {
		Set<Book> teaching = new HashSet<>();
		for (ForjaPath.Step step : ForjaPath.STEPS) {
			Book book = GuideBooks.bookOf(step.chapter);
			helper.assertTrue(book != null && book.onShelf, "el paso " + step + " (" + step.chapter + ") no está en ningún libro de la estantería");
			teaching.add(book);
		}
		helper.assertTrue(GuideBooks.bookOf(ForjaPath.Step.PLANTILLA.chapter) == Book.CUADERNO, "la primera plantilla la enseña el cuaderno");
		for (ForjaPath.Step step : List.of(ForjaPath.Step.PIEZA, ForjaPath.Step.FORJA, ForjaPath.Step.TEMPLE, ForjaPath.Step.MEJORA)) {
			helper.assertTrue(GuideBooks.bookOf(step.chapter) == Book.YUNQUE, "el paso " + step + " debería estar en El yunque");
		}
		helper.assertTrue(GuideBooks.bookOf(ForjaPath.Step.TECNICA.chapter) == Book.MESA_MAYOR, "la técnica debería estar en La mesa mayor");
		helper.assertTrue(teaching.contains(Book.FUNDICION) && teaching.contains(Book.COMBATE), "el camino debería pasar por el combate y la fundición");
		// The catalogue is the library's, not a book's.
		for (String table : List.of("objetos", "piezas", "materiales", "rasgos", "mejoras")) {
			helper.assertTrue(GuideBooks.bookOf(table) == Book.BIBLIOTECA, "la tabla " + table + " debería estar en el catálogo de la biblioteca");
		}
		helper.succeed();
	}
}
