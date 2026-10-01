package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.block.entity.CastingTableBlockEntity;
import dev.forja.block.entity.MeltTankBlockEntity;
import dev.forja.block.entity.StrainerBlockEntity;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.RepairKits;
import dev.forja.item.CastingFrameItem;
import dev.forja.item.CastingMouldItem;
import dev.forja.magic.Mana;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.ArmorSets;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Oricalco as a forge material (Andy, 2026-10-01; docs/HERRERO_DIMENSION.md 1.4): every part takes it, it is poured on
 * a soul table and on nothing softer, its Astral trait quickens mana (twice beside star iron), and its repair kit
 * mends an oricalco-headed tool.
 */
public class OricalcoGameTests {
	private static final float EPS = 1.0E-4F;
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Every part type, every forged type with oricalco in every slot, and the ingot is its input. */
	@GameTest
	public void oricalcoGoesIntoEveryPart(GameTestHelper helper) {
		ForgeMaterial oricalco = ForgeMaterial.ORICALCO;
		helper.assertTrue(ForgeMaterial.fromInput(new ItemStack(ModItems.ORICALCO)) == oricalco, "el lingote de oricalco es oricalco");
		helper.assertTrue(oricalco.displayStack().is(ModItems.ORICALCO), "y el oricalco vuelve en su lingote: " + oricalco.displayStack());
		helper.assertFalse(oricalco.isBasic(), "se cuela, no se corta");
		helper.assertTrue(oricalco.canBeHead, "puede ser cabeza, hoja y punta");
		helper.assertTrue(MeltTankBlockEntity.holds(ModItems.ORICALCO), "las cubas lo guardan");
		List<String> wrong = new ArrayList<>();
		for (PartType part : PartType.values()) {
			if (!part.accepts(oricalco)) {
				wrong.add(part.id() + ": no lo acepta");
				continue;
			}
			ItemStack made = Assembler.createPart(part, oricalco);
			if (made.isEmpty() || made.get(ModComponents.MATERIAL) != oricalco) {
				wrong.add(part.id() + ": sale " + made);
			}
		}
		var registries = helper.getLevel().registryAccess();
		for (ForgeType type : ForgeType.values()) {
			List<ForgeMaterial> all = new ArrayList<>();
			for (int slot = 0; slot < type.slots.size(); slot++) {
				all.add(oricalco);
			}
			ItemStack made = Assembler.create(type, all, registries);
			ForgedParts parts = made.get(ModComponents.PARTS);
			if (parts == null || parts.primary() != oricalco || !parts.hasTrait(ForgeMaterial.Trait.ASTRAL)) {
				wrong.add(type.id() + ": sale " + made);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "todas las piezas y todos los objetos deberían tomar oricalco: " + wrong);
		// Its place on the ladder: netherite's tier, under the white-heat steels and the heart, over damascus in wear.
		helper.assertTrue(oricalco.incorrectBlocksForDrops == ForgeMaterial.NETHERITA.incorrectBlocksForDrops, "mina como la netherita");
		helper.assertTrue(oricalco.attackDamageBonus < ForgeMaterial.DAMASCO.attackDamageBonus
			&& oricalco.attackDamageBonus < ForgeMaterial.SOLACERO.attackDamageBonus, "pega menos que el damasco y el solacero");
		helper.assertTrue(oricalco.durability < ForgeMaterial.SOLACERO.durability && oricalco.durability < ForgeMaterial.CORAZON.durability,
			"dura menos que el solacero y el corazón");
		int set = 0;
		for (net.minecraft.world.item.equipment.ArmorType piece : net.minecraft.world.item.equipment.ArmorType.values()) {
			set += piece == net.minecraft.world.item.equipment.ArmorType.BODY ? 0 : oricalco.defense(piece);
		}
		helper.assertTrue(set == 20, "su armadura es la de la netherita, 20 en el conjunto: " + set);
		helper.succeed();
	}

	/** A soul table pours oricalco into a mould and a frame; a blackstone table, which holds 1600, does not. */
	@GameTest
	public void oricalcoIsPouredOnASoulTable(GameTestHelper helper) {
		BlockPos lantern = new BlockPos(1, 1, 1);
		BlockPos at = lantern.above();
		helper.setBlock(lantern, ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(at, ModBlocks.MESA_DE_ALMAS.defaultBlockState());
		helper.setBlock(at.east(), ModBlocks.CUBA_DE_COLADA.defaultBlockState());
		helper.getBlockEntity(at.east(), MeltTankBlockEntity.class).fill(ModItems.ORICALCO, 120);
		// A strainer of oricalco holds oricalco, so the pour comes out clean.
		helper.setBlock(at.above(), ModBlocks.COLADOR.defaultBlockState());
		helper.getBlockEntity(at.above(), StrainerBlockEntity.class).setMaterial(ForgeMaterial.ORICALCO);
		CastingTableBlockEntity table = helper.getBlockEntity(at, CastingTableBlockEntity.class);
		helper.assertTrue(table.takes(ForgeMaterial.ORICALCO), "la mesa de almas lo aguanta");
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingMouldItem.of(PartType.CABEZA_PICO));
		run(helper, at, table, 60 + CastingTableBlockEntity.COOK);
		ItemStack head = table.result();
		helper.assertTrue(head.getItem() == ModItems.part(PartType.CABEZA_PICO) && head.get(ModComponents.MATERIAL) == ForgeMaterial.ORICALCO,
			"sale una cabeza de pico de oricalco: " + head);
		helper.assertFalse(head.getOrDefault(ModComponents.ROUGH, false), "limpia, por un colador que lo aguanta");
		// A whole sword off a frame, every slot of it oricalco.
		table.setItem(CastingTableBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
		table.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingFrameItem.of(ForgeType.ESPADA));
		run(helper, at, table, CastingTableBlockEntity.COOK + 60);
		ForgedParts sword = table.result().get(ModComponents.PARTS);
		helper.assertTrue(sword != null && sword.type() == ForgeType.ESPADA && sword.primary() == ForgeMaterial.ORICALCO,
			"y una espada de oricalco del marco: " + table.result());
		// The blackstone table holds up to 1600 and oricalco is 1650: it stays in the tank.
		BlockPos lantern2 = new BlockPos(4, 1, 1);
		BlockPos at2 = lantern2.above();
		helper.setBlock(lantern2, ModBlocks.FAROL_DE_PAVESA.defaultBlockState());
		helper.setBlock(at2, ModBlocks.MESA_DE_BRASA.defaultBlockState());
		helper.setBlock(at2.east(), ModBlocks.CUBA_DE_COLADA.defaultBlockState());
		MeltTankBlockEntity tank2 = helper.getBlockEntity(at2.east(), MeltTankBlockEntity.class);
		tank2.fill(ModItems.ORICALCO, 120);
		CastingTableBlockEntity brasa = helper.getBlockEntity(at2, CastingTableBlockEntity.class);
		helper.assertFalse(brasa.takes(ForgeMaterial.ORICALCO), "la mesa de brasa no lo aguanta");
		brasa.setItem(CastingTableBlockEntity.SLOT_FRAME, CastingMouldItem.of(PartType.CABEZA_PICO));
		run(helper, at2, brasa, 60 + CastingTableBlockEntity.COOK);
		helper.assertTrue(brasa.result().isEmpty() && tank2.bankAmount() == 120, "y no cuela nada: " + brasa.result() + ", quedan " + tank2.bankAmount());
		helper.succeed();
	}

	private static void run(GameTestHelper helper, BlockPos at, CastingTableBlockEntity table, int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		for (int tick = 0; tick < ticks; tick++) {
			CastingTableBlockEntity.serverTick(level, where, level.getBlockState(where), table);
		}
	}

	/** One armour piece with oricalco plate on whatever lining the piece usually has. */
	private static ItemStack piece(GameTestHelper helper, EquipmentSlot slot, ForgeMaterial plate) {
		ForgeType type = switch (slot) {
			case HEAD -> ForgeType.CASCO;
			case CHEST -> ForgeType.PECHERA;
			case LEGS -> ForgeType.GREBAS;
			default -> ForgeType.BOTAS;
		};
		List<ForgeMaterial> materials = new ArrayList<>(Assembler.defaultMaterials(type));
		materials.set(0, plate);
		return Assembler.create(type, materials, helper.getLevel().registryAccess());
	}

	/**
	 * Astral: +10 % mana regeneration for each worn piece and each hand with oricalco in it, +20 % for one that also has
	 * star iron; a full suit adds 25 to the bar; a broken piece gives nothing; and other metals give nothing at all.
	 */
	@GameTest
	public void astralQuickensManaAndStarIronDoublesIt(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		var cfg = dev.forja.combat.CombatConfig.get();
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		Mana.forget(player);
		helper.assertTrue(Math.abs(Mana.regenFactor(player) - 1.0F) < EPS, "desnudo, el maná vuelve a su ritmo");
		// A damascus sword does nothing for mana; one with an oricalco guard does.
		player.setItemInHand(InteractionHand.MAIN_HAND, Assembler.create(ForgeType.ESPADA,
			List.of(ForgeMaterial.DAMASCO, ForgeMaterial.MADERA, ForgeMaterial.DAMASCO), registries));
		helper.assertTrue(Math.abs(Mana.regenFactor(player) - 1.0F) < EPS, "el damasco no toca el maná");
		ItemStack guarded = Assembler.create(ForgeType.ESPADA, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.MADERA, ForgeMaterial.ORICALCO), registries);
		player.setItemInHand(InteractionHand.MAIN_HAND, guarded);
		helper.assertTrue(Math.abs(Mana.regenFactor(player) - (1.0F + Mana.ORICHALCUM_REGEN)) < EPS,
			"una guarda de oricalco basta: +10 %: " + Mana.regenFactor(player));
		helper.assertTrue(Mana.usesMana(guarded), "y la barra de maná tiene por qué verse");
		// Star iron in the same piece doubles it.
		player.setItemInHand(InteractionHand.MAIN_HAND, Assembler.create(ForgeType.ESPADA,
			List.of(ForgeMaterial.ORICALCO, ForgeMaterial.ESTELAR, ForgeMaterial.ORICALCO), registries));
		helper.assertTrue(Math.abs(Mana.regenFactor(player) - (1.0F + 2.0F * Mana.ORICHALCUM_REGEN)) < EPS,
			"con hierro estelar en la misma pieza, el doble: " + Mana.regenFactor(player));
		// And star steel counts as star iron.
		player.setItemInHand(InteractionHand.OFF_HAND, Assembler.create(ForgeType.DAGA, List.of(ForgeMaterial.ORICALCO, ForgeMaterial.ACERO_ESTELAR), registries));
		helper.assertTrue(Math.abs(Mana.regenFactor(player) - (1.0F + 4.0F * Mana.ORICHALCUM_REGEN)) < EPS,
			"el acero estelar también, y la otra mano suma: " + Mana.regenFactor(player));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

		// Four pieces: +10 % each, and the suit's bonus to the bar.
		for (EquipmentSlot slot : ARMOUR) {
			player.setItemSlot(slot, piece(helper, slot, ForgeMaterial.ORICALCO));
		}
		helper.assertTrue(ArmorSets.fullSet(player) == ForgeMaterial.ORICALCO, "el conjunto entero es de oricalco");
		helper.assertTrue(Math.abs(Mana.regenFactor(player) - (1.0F + 4.0F * Mana.ORICHALCUM_REGEN)) < EPS,
			"cuatro piezas, +10 % cada una: " + Mana.regenFactor(player));
		helper.assertTrue(Math.abs(Mana.maxOf(player) - (cfg.manaMax + Mana.ORICHALCUM_SET_MANA)) < EPS,
			"y el conjunto da +25 de maná: " + Mana.maxOf(player));
		// A broken piece is no piece.
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		chest.setDamageValue(chest.getMaxDamage());
		helper.assertTrue(Math.abs(Mana.regenFactor(player) - (1.0F + 3.0F * Mana.ORICHALCUM_REGEN)) < EPS,
			"rota no cuenta: " + Mana.regenFactor(player));

		// In play: a tick with the bar empty fills it faster than bare.
		CombatGameTests.TestPlayer bare = CombatGameTests.player(helper, new BlockPos(3, 1, 1));
		Mana.forget(bare);
		Mana.set(player, 0.0F);
		Mana.set(bare, 0.0F);
		long now = helper.getLevel().getGameTime() + 1000;
		for (int i = 0; i < 100; i++) {
			Mana.tick(player, now + i);
			Mana.tick(bare, now + i);
		}
		helper.assertTrue(Mana.value(player) > Mana.value(bare) * 1.25F,
			"con oricalco el maná vuelve antes: " + Mana.value(player) + " frente a " + Mana.value(bare));
		Mana.forget(player);
		Mana.forget(bare);
		helper.succeed();
	}

	/** The oricalco kit mends an oricalco-headed pickaxe and nothing headed in anything else. */
	@GameTest
	public void theOricalcoKitMendsAnOricalcoTool(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var registries = helper.getLevel().registryAccess();
		helper.assertTrue(RepairKits.kit(ForgeMaterial.ORICALCO) != null, "el oricalco tiene kit");
		ItemStack kit = new ItemStack(RepairKits.kit(ForgeMaterial.ORICALCO));
		ItemStack pick = Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.ORICALCO, ForgeMaterial.MADERA, ForgeMaterial.CUERO), registries);
		int max = pick.getMaxDamage();
		pick.setDamageValue(max - 10);
		ItemStack mended = grid(helper, player, pick.copy(), kit.copy()).getSlot(0).getItem();
		helper.assertTrue(mended.is(ModItems.forged(ForgeType.PICO)) && mended.getDamageValue() == max - 10 - RepairKits.AMOUNT,
			"el kit de oricalco le da +300 a un pico de oricalco: " + mended + " con " + (max - mended.getDamageValue()) + " de uso");
		ItemStack damascus = Assembler.create(ForgeType.PICO, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.ORICALCO, ForgeMaterial.ORICALCO), registries);
		damascus.setDamageValue(200);
		helper.assertTrue(grid(helper, player, damascus, kit.copy()).getSlot(0).getItem().isEmpty(),
			"pero no a uno con cabeza de damasco, aunque el mango y la atadura sean de oricalco");
		helper.succeed();
	}

	private static CraftingMenu grid(GameTestHelper helper, ServerPlayer player, ItemStack first, ItemStack second) {
		CraftingMenu menu = new CraftingMenu(1, player.getInventory(),
			ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		menu.getSlot(1).set(first);
		menu.getSlot(9).set(second);
		menu.slotsChanged(menu.getSlot(1).container);
		return menu;
	}
}
