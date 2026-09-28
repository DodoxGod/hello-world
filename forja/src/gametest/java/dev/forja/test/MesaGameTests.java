package dev.forja.test;

import java.util.List;

import dev.forja.block.SmithAnvilBlock;
import dev.forja.forge.Alloys;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.item.TemplateItem;
import dev.forja.item.UpgradeOrbItem;
import dev.forja.menu.ExtractionMenu;
import dev.forja.menu.ForgeMenu;
import dev.forja.menu.Station;
import dev.forja.part.PartType;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static dev.forja.material.ForgeMaterial.AMATISTA;
import static dev.forja.material.ForgeMaterial.DIAMANTE;
import static dev.forja.material.ForgeMaterial.HIERRO;
import static dev.forja.material.ForgeMaterial.MADERA;

/**
 * The work tables, played by hand: what each slot takes and refuses, what shift-click does with it, and
 * what a press costs. Every check here is a bug that was in the game (Andy: "se puede poner cualquier
 * objeto en los contenedores", "hacer herramientas llega a ser extraño"); the client test's
 * FORJA_SOLO=mesa section plays the same things with real clicks on the screens.
 */
public class MesaGameTests {
	private static final BlockPos TABLE = new BlockPos(1, 2, 1);

	/** A forge menu of that station on a real table, with lava under it if asked. */
	private static ForgeMenu table(GameTestHelper helper, ServerPlayer player, Station station, boolean lava) {
		helper.setBlock(TABLE.below(), lava ? Blocks.LAVA : Blocks.STONE);
		helper.setBlock(TABLE, station.block());
		return new ForgeMenu(station, 0, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
	}

	private static ServerPlayer smith(GameTestHelper helper) {
		ServerPlayer player = CombatGameTests.player(helper, new BlockPos(3, 2, 3));
		player.getInventory().clearContent();
		return player;
	}

	/** The menu slot that shows this slot of the player's inventory. */
	private static int inventorySlot(ForgeMenu menu, Inventory inventory, int index) {
		for (Slot slot : menu.slots) {
			if (slot.container == inventory && slot.getContainerSlot() == index) {
				return slot.index;
			}
		}
		throw new IllegalStateException("no slot for inventory " + index);
	}

	private static int count(Inventory inventory, net.minecraft.world.item.Item item) {
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				total += inventory.getItem(i).getCount();
			}
		}
		return total;
	}

	/** The star, the material slot, the salvage slot and the extraction tray turn away what is not theirs. */
	@GameTest
	public void slotsRefuseWhatIsNotTheirs(GameTestHelper helper) {
		ServerPlayer player = smith(helper);
		ForgeMenu forge = table(helper, player, Station.FORJA, false);
		Slot point = forge.getSlot(ForgeMenu.STAR_FIRST);
		for (ItemStack junk : List.of(new ItemStack(Items.DIRT), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.STICK),
			new ItemStack(Items.FLINT_AND_STEEL), Assembler.create(ForgeType.FLECHA, Assembler.defaultMaterials(ForgeType.FLECHA)))) {
			helper.assertFalse(point.mayPlace(junk), "la estrella no debería aceptar " + junk);
		}
		for (ItemStack good : List.of(Assembler.createPart(PartType.MANGO, MADERA), UpgradeOrbItem.create(Upgrade.FILO, 20),
			new ItemStack(Items.ENCHANTED_BOOK), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.AMETHYST_SHARD),
			new ItemStack(Items.COAL), new ItemStack(Items.PISTON), new ItemStack(ModItems.FUNDENTE_MAESTRO),
			new ItemStack(ModItems.CORAZON_DE_FORJA), dev.forja.item.SealItem.create(dev.forja.forge.Perk.MINERO),
			new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.OAK_PLANKS),
			Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, MADERA)))) {
			helper.assertTrue(point.mayPlace(good), "la estrella debería aceptar " + good);
		}
		// Every ingredient of every upgrade and alloy is welcome.
		for (Upgrade upgrade : Upgrade.values()) {
			for (Upgrade.Option option : upgrade.options) {
				for (Upgrade.Requirement requirement : option.requirements()) {
					helper.assertTrue(point.mayPlace(requirement.displayStack()), "la estrella debería aceptar " + requirement.displayStack() + " de " + upgrade);
				}
			}
		}
		for (Alloys.Recipe recipe : Alloys.POURABLE) {
			for (Alloys.Part part : recipe.inputs()) {
				helper.assertTrue(point.mayPlace(new ItemStack(part.item().get())), "la estrella debería aceptar " + part.item().get() + " de " + recipe.id());
			}
		}

		// A real click with dirt in the hand leaves the point empty and the dirt in the hand.
		forge.setCarried(new ItemStack(Items.DIRT, 5));
		forge.clicked(ForgeMenu.STAR_FIRST, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(forge.getSlot(ForgeMenu.STAR_FIRST).getItem().isEmpty() && forge.getCarried().getCount() == 5,
			"un clic con tierra no la deja en la estrella");
		forge.setCarried(ItemStack.EMPTY);

		ForgeMenu parts = table(helper, player, Station.PIEZAS, false);
		Slot material = parts.getSlot(ForgeMenu.MATERIAL_SLOT);
		helper.assertFalse(material.mayPlace(new ItemStack(Items.IRON_INGOT)), "el hierro se cuela, no se corta: la mesa no lo acepta");
		helper.assertFalse(material.mayPlace(new ItemStack(Items.DIAMOND)), "ni el diamante");
		helper.assertFalse(material.mayPlace(new ItemStack(Items.DIRT)), "ni la tierra");
		helper.assertTrue(material.mayPlace(new ItemStack(Items.AMETHYST_SHARD)) && material.mayPlace(new ItemStack(Items.OAK_PLANKS)),
			"la amatista y la madera sí se cortan");
		helper.assertFalse(parts.getSlot(ForgeMenu.TEMPLATE_SLOT).mayPlace(new ItemStack(Items.PAPER)), "la plantilla solo acepta plantillas");

		parts.clickMenuButton(player, ForgeMenu.BUTTON_TAB + ForgeMenu.MODE_DISASSEMBLE);
		Slot salvage = parts.getSlot(ForgeMenu.DISASSEMBLE_SLOT);
		helper.assertFalse(salvage.mayPlace(new ItemStack(Items.IRON_SWORD)), "desarmar no acepta una espada de vanilla");
		helper.assertFalse(salvage.mayPlace(Assembler.create(ForgeType.FLECHA, Assembler.defaultMaterials(ForgeType.FLECHA))),
			"ni flechas: cuatro salen de un juego de piezas");
		helper.assertTrue(Assembler.disassemble(Assembler.create(ForgeType.FLECHA, Assembler.defaultMaterials(ForgeType.FLECHA))).returned().isEmpty(),
			"una flecha no devuelve un juego de piezas");
		helper.assertTrue(salvage.mayPlace(Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, MADERA))), "un pico forjado sí");
		helper.assertTrue(salvage.mayPlace(Assembler.createPart(PartType.MANGO, MADERA)), "y una pieza suelta también");

		ExtractionMenu extraction = new ExtractionMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
		Slot tray = extraction.getSlot(ExtractionMenu.PAYMENT_FIRST);
		helper.assertFalse(tray.mayPlace(new ItemStack(Items.DIRT)), "la bandeja de extracción no acepta tierra");
		helper.assertTrue(tray.mayPlace(new ItemStack(Items.AMETHYST_SHARD)) && tray.mayPlace(new ItemStack(Items.GLASS_BOTTLE)),
			"pero sí lo que paga una mejora y las botellas");
		helper.succeed();
	}

	/** Shift-click: junk hops between inventory and hotbar instead of landing on the star; an old piece goes on a point. */
	@GameTest
	public void shiftClickRoutes(GameTestHelper helper) {
		ServerPlayer player = smith(helper);
		ForgeMenu forge = table(helper, player, Station.FORJA, false);
		Inventory inventory = player.getInventory();
		inventory.setItem(9, new ItemStack(Items.DIRT, 10));
		forge.quickMoveStack(player, inventorySlot(forge, inventory, 9));
		for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
			helper.assertTrue(forge.getSlot(ForgeMenu.STAR_FIRST + i).getItem().isEmpty(), "la tierra no va a la estrella");
		}
		helper.assertTrue(count(inventory, Items.DIRT) == 10, "y no se pierde: " + count(inventory, Items.DIRT));

		inventory.setItem(10, Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, MADERA)));
		inventory.setItem(11, Assembler.create(ForgeType.PICO, List.of(DIAMANTE, MADERA, MADERA)));
		forge.quickMoveStack(player, inventorySlot(forge, inventory, 10));
		helper.assertTrue(forge.getSlot(ForgeMenu.CENTER_SLOT).getItem().has(ModComponents.PARTS), "el primer pico va al centro");
		forge.quickMoveStack(player, inventorySlot(forge, inventory, 11));
		helper.assertTrue(forge.getSlot(ForgeMenu.STAR_FIRST).getItem().has(ModComponents.PARTS),
			"con el centro ocupado, el segundo va a una punta (herencia), no a la barra");

		// A real shift-click (vanilla repeats it while the stack comes back) on a pile of three handles puts
		// one handle on the star. It used to spread the pile over three points, and nothing formed.
		forge.getSlot(ForgeMenu.STAR_FIRST).set(ItemStack.EMPTY);
		forge.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
		ItemStack handles = Assembler.createPart(PartType.MANGO, MADERA);
		handles.setCount(3);
		inventory.setItem(12, handles);
		forge.clicked(inventorySlot(forge, inventory, 12), 0, ContainerInput.QUICK_MOVE, player);
		int onStar = 0;
		for (int i = 0; i < ForgeMenu.STAR_COUNT; i++) {
			onStar += forge.getSlot(ForgeMenu.STAR_FIRST + i).getItem().getCount();
		}
		helper.assertTrue(onStar == 1 && inventory.getItem(12).getCount() == 2, "un mango a la estrella, dos en la mano del inventario: " + onStar);
		// The same for a pile of blank templates at the parts table: one in the slot, the rest where they were.
		ForgeMenu parts = table(helper, player, Station.PIEZAS, false);
		inventory.setItem(13, new ItemStack(ModItems.PLANTILLA, 5));
		parts.clicked(inventorySlot(parts, inventory, 13), 0, ContainerInput.QUICK_MOVE, player);
		helper.assertTrue(parts.getSlot(ForgeMenu.TEMPLATE_SLOT).getItem().getCount() == 1 && inventory.getItem(13).getCount() == 4,
			"una plantilla a la mesa y las otras cuatro quietas");
		helper.succeed();
	}

	/** No two things are made of the same parts: the star would build the first and never the second. */
	@GameTest
	public void everyRecipeIsItsOwn(GameTestHelper helper) {
		for (ForgeType a : ForgeType.values()) {
			for (ForgeType b : ForgeType.values()) {
				if (a != b) {
					List<PartType> left = new java.util.ArrayList<>(a.slots);
					boolean same = a.slots.size() == b.slots.size() && b.slots.stream().allMatch(left::remove);
					helper.assertFalse(same, a + " y " + b + " llevan las mismas piezas");
				}
			}
			helper.assertTrue(ForgeType.match(a.slots) == a, a + " se forja con sus propias piezas");
		}
		helper.succeed();
	}

	/** Melting a pile of parts takes the whole pile; it used to count every part and take one. */
	@GameTest
	public void meltTakesWhatItPays(GameTestHelper helper) {
		ServerPlayer player = smith(helper);
		ForgeMenu forge = table(helper, player, Station.FORJA, true);
		ItemStack heads = Assembler.createPart(PartType.CABEZA_PICO, HIERRO);
		heads.setCount(10);
		forge.getSlot(ForgeMenu.STAR_FIRST).set(heads);
		helper.assertTrue(forge.action() == ForgeMenu.Action.FUNDIR, "diez cabezas de hierro sobre lava se funden, pero: " + forge.action());
		helper.assertTrue(forge.forgePreview().getCount() == 10, "una por cabeza: " + forge.forgePreview().getCount());
		helper.assertTrue(forge.clickMenuButton(player, ForgeMenu.BUTTON_FORGE), "el botón funde");
		helper.assertTrue(forge.getSlot(ForgeMenu.STAR_FIRST).getItem().isEmpty(), "y se lleva las diez cabezas, no una: "
			+ forge.getSlot(ForgeMenu.STAR_FIRST).getItem().getCount());
		helper.assertTrue(forge.getSlot(ForgeMenu.CENTER_SLOT).getItem().is(Items.IRON_INGOT)
			&& forge.getSlot(ForgeMenu.CENTER_SLOT).getItem().getCount() == 10, "diez lingotes en el centro");

		// Past a stack of metal, what does not fit stays on the star.
		forge.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
		ItemStack plates = Assembler.createPart(PartType.PLACA_PECHERA, HIERRO);
		plates.setCount(20);
		forge.getSlot(ForgeMenu.STAR_FIRST).set(plates);
		helper.assertTrue(forge.forgePreview().getCount() == 64, "veinte placas de 4 caben en 64: " + forge.forgePreview().getCount());
		forge.clickMenuButton(player, ForgeMenu.BUTTON_FORGE);
		helper.assertTrue(forge.getSlot(ForgeMenu.STAR_FIRST).getItem().getCount() == 4, "quedan las cuatro que no caben: "
			+ forge.getSlot(ForgeMenu.STAR_FIRST).getItem().getCount());
		helper.succeed();
	}

	/** A whole set on a table over lava is forged, not melted. */
	@GameTest
	public void wholeSetForgesOverLava(GameTestHelper helper) {
		ServerPlayer player = smith(helper);
		ForgeMenu forge = table(helper, player, Station.FORJA, true);
		helper.assertTrue(forge.heat() == Alloys.Heat.FUNDIDA, "la mesa sabe que está sobre lava antes de poner nada: " + forge.heat());
		forge.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.CABEZA_PICO, HIERRO));
		forge.getSlot(ForgeMenu.STAR_FIRST + 1).set(Assembler.createPart(PartType.MANGO, HIERRO));
		forge.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.ATADURA, HIERRO));
		helper.assertTrue(forge.action() == ForgeMenu.Action.FORGE, "tres piezas de hierro de un pico forjan un pico, no se funden: " + forge.action());
		forge.getSlot(ForgeMenu.STAR_FIRST + 2).set(ItemStack.EMPTY);
		helper.assertTrue(forge.action() == ForgeMenu.Action.FUNDIR, "dos que no forman nada sí se funden: " + forge.action());
		// Nor a set that only the greater table builds: the bench names that table instead of melting it.
		forge.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.HOJA, HIERRO));
		forge.getSlot(ForgeMenu.STAR_FIRST + 1).set(Assembler.createPart(PartType.HOJA, HIERRO));
		forge.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.MANGO, HIERRO));
		forge.getSlot(ForgeMenu.STAR_FIRST + 3).set(Assembler.createPart(PartType.GUARDA, HIERRO));
		helper.assertTrue(forge.action() == ForgeMenu.Action.NONE && forge.beyondBench() == ForgeType.ESPADON,
			"un espadón de hierro en el banco sobre lava no se funde: " + forge.action());
		helper.succeed();
	}

	/** What the star says it will not build is forgotten when the parts come off. */
	@GameTest
	public void refusedTypeIsForgotten(GameTestHelper helper) {
		ServerPlayer player = smith(helper);
		ForgeMenu forge = table(helper, player, Station.FORJA, false);
		forge.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.HOJA, HIERRO));
		forge.getSlot(ForgeMenu.STAR_FIRST + 1).set(Assembler.createPart(PartType.HOJA, HIERRO));
		forge.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.MANGO, MADERA));
		forge.getSlot(ForgeMenu.STAR_FIRST + 3).set(Assembler.createPart(PartType.GUARDA, MADERA));
		helper.assertTrue(forge.beyondBench() == ForgeType.ESPADON && forge.action() == ForgeMenu.Action.NONE, "el banco no monta un espadón");
		for (int i = 0; i < 4; i++) {
			forge.getSlot(ForgeMenu.STAR_FIRST + i).set(ItemStack.EMPTY);
		}
		helper.assertTrue(forge.beyondBench() == null, "y lo olvida al quitar las piezas");
		forge.getSlot(ForgeMenu.CENTER_SLOT).set(Assembler.create(ForgeType.PICO, List.of(HIERRO, MADERA, MADERA)));
		forge.getSlot(ForgeMenu.STAR_FIRST).set(new ItemStack(Items.AMETHYST_SHARD));
		helper.assertTrue(forge.beyondBench() == null, "también con un pico en el centro");
		// Barding at a forge: the saddlery's, and now the star says so.
		forge.getSlot(ForgeMenu.CENTER_SLOT).set(ItemStack.EMPTY);
		forge.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.PLACA_BARDA, HIERRO));
		forge.getSlot(ForgeMenu.STAR_FIRST + 1).set(Assembler.createPart(PartType.FORRO, dev.forja.material.ForgeMaterial.CUERO));
		helper.assertTrue(forge.beyondBench() == ForgeType.BARDA, "una barda en la forja dice que no la monta: " + forge.beyondBench());
		helper.succeed();
	}

	/** Salvage takes one from a pile and hands back what that one gives; arrows do not come apart. */
	@GameTest
	public void salvageOneAtATime(GameTestHelper helper) {
		ServerPlayer player = smith(helper);
		ForgeMenu parts = table(helper, player, Station.PIEZAS, false);
		parts.clickMenuButton(player, ForgeMenu.BUTTON_TAB + ForgeMenu.MODE_DISASSEMBLE);
		ItemStack heads = Assembler.createPart(PartType.CABEZA_HACHA, AMATISTA);
		heads.setCount(5);
		parts.getSlot(ForgeMenu.DISASSEMBLE_SLOT).set(heads);
		helper.assertTrue(parts.clickMenuButton(player, ForgeMenu.BUTTON_DISASSEMBLE), "desarmar la pila");
		helper.assertTrue(parts.getSlot(ForgeMenu.DISASSEMBLE_SLOT).getItem().getCount() == 4,
			"una de cinco, no las cinco: " + parts.getSlot(ForgeMenu.DISASSEMBLE_SLOT).getItem().getCount());
		helper.assertTrue(count(player.getInventory(), Items.AMETHYST_SHARD) == 1, "y la amatista de una: " + count(player.getInventory(), Items.AMETHYST_SHARD));
		helper.succeed();
	}

	/** The portable anvil has no block behind its menu; what was left on it still comes back on close. */
	@GameTest
	public void portableAnvilGivesBack(GameTestHelper helper) {
		ServerPlayer player = smith(helper);
		ForgeMenu anvil = new ForgeMenu(Station.PIEZAS, 0, player.getInventory());
		ItemStack template = new ItemStack(ModItems.PLANTILLA);
		TemplateItem.engrave(template, PartType.MANGO);
		anvil.getSlot(ForgeMenu.TEMPLATE_SLOT).set(template);
		anvil.getSlot(ForgeMenu.MATERIAL_SLOT).set(new ItemStack(Items.OAK_PLANKS, 7));
		helper.assertTrue(anvil.getSlot(ForgeMenu.PART_RESULT_SLOT).hasItem(), "el yunque de viaje corta un mango");
		anvil.removed(player);
		helper.assertTrue(count(player.getInventory(), ModItems.PLANTILLA) == 1, "la plantilla vuelve al cerrar");
		helper.assertTrue(count(player.getInventory(), Items.OAK_PLANKS) == 7, "y los tablones: " + count(player.getInventory(), Items.OAK_PLANKS));
		helper.succeed();
	}

	/** Cutting a part costs its material once, by click or by shift-click. */
	@GameTest
	public void partCostsOnce(GameTestHelper helper) {
		ServerPlayer player = smith(helper);
		ForgeMenu parts = table(helper, player, Station.PIEZAS, false);
		ItemStack template = new ItemStack(ModItems.PLANTILLA);
		TemplateItem.engrave(template, PartType.CABEZA_PICO);
		parts.getSlot(ForgeMenu.TEMPLATE_SLOT).set(template);
		parts.getSlot(ForgeMenu.MATERIAL_SLOT).set(new ItemStack(Items.AMETHYST_SHARD, 10));
		parts.clicked(ForgeMenu.PART_RESULT_SLOT, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(parts.getCarried().getItem() == ModItems.part(PartType.CABEZA_PICO), "la cabeza en la mano");
		helper.assertTrue(parts.getSlot(ForgeMenu.MATERIAL_SLOT).getItem().getCount() == 7, "tres de amatista: "
			+ parts.getSlot(ForgeMenu.MATERIAL_SLOT).getItem().getCount());
		parts.setCarried(ItemStack.EMPTY);
		parts.clicked(ForgeMenu.PART_RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, player);
		int heads = count(player.getInventory(), ModItems.part(PartType.CABEZA_PICO));
		helper.assertTrue(heads == 2 && parts.getSlot(ForgeMenu.MATERIAL_SLOT).getItem().getCount() == 1,
			"mayús+clic corta hasta que no alcanza: " + heads + " cabezas, quedan " + parts.getSlot(ForgeMenu.MATERIAL_SLOT).getItem().getCount());
		helper.succeed();
	}

	/** Changing the piece on the extraction table forgets the upgrade pointed at on the last one. */
	@GameTest
	public void extractionForgetsTheLastPiece(GameTestHelper helper) {
		ServerPlayer player = smith(helper);
		ExtractionMenu menu = new ExtractionMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
		ItemStack first = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, MADERA));
		first.set(ModComponents.UPGRADES, Upgrades.EMPTY.with(Upgrade.FILO, 40));
		ItemStack second = Assembler.create(ForgeType.ESPADA, List.of(HIERRO, MADERA, MADERA));
		second.set(ModComponents.UPGRADES, Upgrades.EMPTY.with(Upgrade.IRROMPIBLE, 40));
		menu.getSlot(ExtractionMenu.GEAR_SLOT).set(first);
		helper.assertTrue(menu.clickMenuButton(player, 0) && menu.selected() == Upgrade.FILO, "Filo elegido");
		menu.getSlot(ExtractionMenu.GEAR_SLOT).set(second);
		helper.assertTrue(menu.selected() == null, "otra espada, nada elegido (antes quedaba elegida su Irrompible): " + menu.selected());
		helper.succeed();
	}

	/** The anvil answers for the tables with a star, not for a parts table beside it. */
	@GameTest
	public void anvilCountsStarTables(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.YUNQUE_DEL_HERRERO);
		BlockPos anvil = helper.absolutePos(new BlockPos(1, 2, 1));
		// Counted against what was already there: the tests next door put their own tables down.
		helper.setBlock(new BlockPos(3, 2, 1), Blocks.AIR);
		int before = SmithAnvilBlock.tablesServed(helper.getLevel(), anvil);
		helper.setBlock(new BlockPos(3, 2, 1), ModBlocks.MESA_DE_PIEZAS);
		helper.assertTrue(SmithAnvilBlock.tablesServed(helper.getLevel(), anvil) == before, "una mesa de piezas no es una mesa con estrella");
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.MESA_DE_FORJA);
		helper.assertTrue(SmithAnvilBlock.tablesServed(helper.getLevel(), anvil) == before + 1, "una mesa de forja sí");
		helper.succeed();
	}
}
