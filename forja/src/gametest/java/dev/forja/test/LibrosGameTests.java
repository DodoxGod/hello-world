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

	/**
	 * The classes open with book V (Andy's answer 3): a player who has not opened it cannot take a class; opening the
	 * book (using it) gives the advancement, and then the first class is taken.
	 */
	@GameTest
	public void theClassesOpenWithTheirBook(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AdvancementHolder read = helper.getLevel().getServer().getAdvancements().get(Forja.id("forja/" + GuideBooks.CLASSES_READ));
		helper.assertTrue(read != null, "falta el logro forja/" + GuideBooks.CLASSES_READ);
		player.getAdvancements().revoke(read, "done");
		helper.assertTrue(dev.forja.clase.ClassProgress.clazz(player) == null, "el jugador de prueba no debería tener clase");
		helper.assertTrue(!dev.forja.clase.ClassNetwork.tryChoose(player, dev.forja.clase.PlayerClass.GUERRERO)
			&& dev.forja.clase.ClassProgress.clazz(player) == null, "sin abrir el libro de clases no se elige clase");
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.LIBRO_CLASES));
		ModItems.LIBRO_CLASES.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertTrue(player.getAdvancements().getOrStartProgress(read).isDone(), "abrir el libro de clases debería dar su logro");
		helper.assertTrue(dev.forja.clase.ClassNetwork.tryChoose(player, dev.forja.clase.PlayerClass.GUERRERO)
			&& dev.forja.clase.ClassProgress.clazz(player) == dev.forja.clase.PlayerClass.GUERRERO, "con el libro abierto la primera clase se elige");
		helper.succeed();
	}

	/** The Forjador sells book VI at level 3, always: it is not one of the random draws of the level. */
	@GameTest
	public void theForjadorSellsBookSixAtLevelThree(GameTestHelper helper) {
		for (int attempt = 0; attempt < 5; attempt++) {
			net.minecraft.world.entity.npc.villager.Villager villager = helper.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
			var forjador = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION)
				.getOrThrow(dev.forja.registry.ModVillagers.FORJADOR);
			villager.setVillagerData(villager.getVillagerData().withProfession(forjador).withLevel(3));
			boolean sells = villager.getOffers().stream().anyMatch(offer -> offer.getResult().is(ModItems.LIBRO_BASTION));
			helper.assertTrue(sells, "un Forjador de nivel 3 debería vender El Bastión y el Herrero (intento " + attempt + ")");
			villager.discard();
		}
		helper.succeed();
	}

	/**
	 * Lighting the star portal teaches book VII: whoever stands at the frame gets the "portal" advancement (and with it
	 * the recipe), and a lectern with the book appears beside the frame.
	 */
	@GameTest(maxTicks = 100)
	public void lightingThePortalTeachesBookSeven(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		BlockPos forge = helper.absolutePos(new BlockPos(5, 3, 5));
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				level.setBlockAndUpdate(forge.offset(dx, -2, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(forge.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			}
		}
		level.setBlockAndUpdate(forge, dev.forja.registry.ModBlocks.FRAGUA_APAGADA.defaultBlockState());
		dev.forja.block.DeadForgeBlock.openFrame(level, forge);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.teleportTo(forge.getX() + 0.5, forge.getY(), forge.getZ() + 3.5);
		AdvancementHolder portal = level.getServer().getAdvancements().get(Forja.id("forja/portal"));
		helper.assertTrue(portal != null, "falta el logro forja/portal");
		player.getAdvancements().revoke(portal, "done");
		player.getRecipeBook().remove(Book.CEMENTERIO.recipe());
		BlockPos centre = forge.below();
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			dev.forja.block.StarBracketBlock.setPearl(level, centre.relative(side, dev.forja.block.StarBracketBlock.REACH));
		}
		helper.assertTrue(level.getBlockState(centre).is(dev.forja.registry.ModBlocks.PORTAL_ESTELAR), "el portal debería estar encendido");
		helper.assertTrue(player.getAdvancements().getOrStartProgress(portal).isDone(), "quien está junto al marco debería recibir forja/portal");
		helper.assertTrue(player.getRecipeBook().contains(Book.CEMENTERIO.recipe()), "con el portal encendido se aprende la receta del libro VII");
		boolean lectern = false;
		for (BlockPos at : BlockPos.betweenClosed(centre.offset(-5, -1, -5), centre.offset(5, 1, 5))) {
			lectern |= level.getBlockState(at).is(dev.forja.registry.ModBlocks.ATRIL_DEL_HERRERO);
		}
		helper.assertTrue(lectern, "junto al portal encendido debería aparecer el atril del Herrero");
		helper.succeed();
	}

	/** The smith's shelf: a book goes into its own place, comes back out with a click there, and a comparator counts. */
	@GameTest
	public void theShelfKeepsEachBookInItsPlace(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, dev.forja.registry.ModBlocks.ESTANTERIA_DEL_HERRERO.defaultBlockState());
		BlockPos pos = helper.absolutePos(rel);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.LIBRO_COMBATE));
		var state = helper.getLevel().getBlockState(pos);
		net.minecraft.world.phys.BlockHitResult front = new net.minecraft.world.phys.BlockHitResult(
			net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0.0, 0.0, -0.5), net.minecraft.core.Direction.NORTH, pos, false);
		state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, front);
		int place = GuideBooks.SHELF.indexOf(Book.COMBATE);
		state = helper.getLevel().getBlockState(pos);
		helper.assertTrue(state.getValue(dev.forja.block.SmithShelfBlock.BOOKS[place]), "el libro II debería ir a su sitio de la estantería");
		// (The test's player is in creative, where a placed item stays in the hand, as with any block.)
		helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), pos, net.minecraft.core.Direction.NORTH) == 1, "un comparador cuenta un libro");
		// A click on its place (seen from the north, place 0 is at the east edge; each place is two pixels wide).
		double x = pos.getX() + 1.0 - (place * 2 + 1) / 16.0;
		net.minecraft.world.phys.BlockHitResult atPlace = new net.minecraft.world.phys.BlockHitResult(
			new net.minecraft.world.phys.Vec3(x, pos.getY() + 0.5, pos.getZ()), net.minecraft.core.Direction.NORTH, pos, false);
		state.useWithoutItem(helper.getLevel(), player, atPlace);
		helper.assertTrue(!helper.getLevel().getBlockState(pos).getValue(dev.forja.block.SmithShelfBlock.BOOKS[place]), "un clic en su sitio saca el libro");
		helper.assertTrue(player.getInventory().contains(new ItemStack(ModItems.LIBRO_COMBATE)), "el libro sacado vuelve a la bolsa");
		helper.succeed();
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
