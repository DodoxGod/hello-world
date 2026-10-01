package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.block.CrucibleBlock;
import dev.forja.block.FarForgeBlock;
import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.block.entity.FarForgeBlockEntity;
import dev.forja.block.entity.MeltTankBlockEntity;
import dev.forja.combat.ArrowTips;
import dev.forja.combat.CombatHooks;
import dev.forja.forge.Alloys;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.magic.Mana;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModBlocks;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEffects;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.ArmorSets;
import dev.forja.upgrade.TraitEffects;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The middle tier and the peak alloys (docs/ALEACIONES_CUMBRE.md): what each recipe is made of and where it is made, how
 * the new traits behave, what the sets and the arrow tips do, and that every number stays under the forge heart in what
 * it is not for.
 */
public class CumbreGameTests {
	private static final float EPS = 1.0E-4F;
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final ForgeType[] PIECES = {ForgeType.CASCO, ForgeType.PECHERA, ForgeType.GREBAS, ForgeType.BOTAS};
	private static final BlockPos POT = new BlockPos(1, 1, 1);
	/** Where the far dimensions are used: far from spawn and from the other tests that use them. */
	private static final BlockPos FAR_SPOT = new BlockPos(4300, 70, 4300);

	private static Alloys.Recipe recipe(String id) {
		return Alloys.ALL.stream().filter(recipe -> recipe.id().equals(id)).findFirst().orElseThrow();
	}

	private static ForgeMaterial material(String id) {
		return ForgeMaterial.valueOf(id.toUpperCase(java.util.Locale.ROOT));
	}

	/** One armour piece with this plate on the lining a piece of that kind usually has. */
	private static ItemStack piece(GameTestHelper helper, ForgeType type, ForgeMaterial plate) {
		return Assembler.create(type, List.of(plate, ForgeMaterial.CUERO), helper.getLevel().registryAccess());
	}

	/** The first {@code count} armour pieces of this plate, on the player. */
	private static void wear(GameTestHelper helper, net.minecraft.world.entity.player.Player player, ForgeMaterial plate, int count) {
		for (int i = 0; i < 4; i++) {
			player.setItemSlot(ARMOUR[i], i < count ? piece(helper, PIECES[i], plate) : ItemStack.EMPTY);
		}
	}

	private static ItemStack all(GameTestHelper helper, ForgeType type, ForgeMaterial material) {
		List<ForgeMaterial> every = new ArrayList<>();
		for (int slot = 0; slot < type.slots.size(); slot++) {
			every.add(material);
		}
		return Assembler.create(type, every, helper.getLevel().registryAccess());
	}

	private static CombatGameTests.TestPlayer player(GameTestHelper helper, BlockPos at) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, at);
		Mana.forget(player);
		return player;
	}

	private static Mob zombie(GameTestHelper helper, BlockPos at) {
		Mob zombie = helper.spawn(EntityTypes.ZOMBIE, at);
		zombie.setNoAi(true);
		zombie.clearFire();
		return zombie;
	}

	/**
	 * A sealed cell of blackstone in the Nether (no sky there, and walled off from whatever lights its caves): three by
	 * three on the floor and two high, with its floor at {@code at}. As dark as it gets.
	 */
	private static void sealedCell(ServerLevel nether, BlockPos at) {
		for (int dx = -1; dx <= 5; dx++) {
			for (int dz = -1; dz <= 5; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					boolean inside = dx >= 0 && dx <= 4 && dz >= 0 && dz <= 4 && dy >= 0 && dy <= 2;
					nether.setBlockAndUpdate(at.offset(dx, dy, dz), inside ? Blocks.AIR.defaultBlockState()
						: Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				}
			}
		}
	}

	/** A player standing in the cell made by {@link #sealedCell}. */
	private static CombatGameTests.TestPlayer playerInTheDark(ServerLevel nether, BlockPos at) {
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(nether);
		player.setPos(at.getX() + 1.5, at.getY(), at.getZ() + 2.5);
		player.setHealth(player.getMaxHealth());
		Mana.forget(player);
		return player;
	}

	private static void clearAround(ServerLevel level, BlockPos centre, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				level.setBlockAndUpdate(centre.offset(dx, -2, dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				for (int dy = -1; dy <= 3; dy++) {
					level.setBlockAndUpdate(centre.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private static void fill(FarForgeBlockEntity forge, Alloys.Recipe recipe, int batches) {
		for (Alloys.Part part : recipe.inputs()) {
			forge.insert(new ItemStack(part.item().get(), part.count() * batches));
		}
	}

	private static void tick(ServerLevel level, BlockPos pos, int ticks) {
		for (int i = 0; i < ticks; i++) {
			BlockState state = level.getBlockState(pos);
			if (level.getBlockEntity(pos) instanceof FarForgeBlockEntity forge) {
				FarForgeBlockEntity.serverTick(level, pos, state, forge);
			}
		}
	}

	private static int count(Container container, net.minecraft.world.item.Item item) {
		int found = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).is(item)) {
				found += container.getItem(slot).getCount();
			}
		}
		return found;
	}

	private static CrucibleBlockEntity pot(GameTestHelper helper, BlockPos at, Block tier) {
		helper.setBlock(at, tier.defaultBlockState());
		return helper.getBlockEntity(at, CrucibleBlockEntity.class);
	}

	private static MeltTankBlockEntity tank(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.CUBA_DE_COLADA.defaultBlockState());
		return helper.getBlockEntity(at, MeltTankBlockEntity.class);
	}

	private static void run(GameTestHelper helper, BlockPos at, CrucibleBlockEntity pot, int ticks) {
		ServerLevel level = helper.getLevel();
		BlockPos where = helper.absolutePos(at);
		for (int tick = 0; tick < ticks; tick++) {
			CrucibleBlockEntity.serverTick(level, where, level.getBlockState(where), pot);
		}
	}

	/** Whether this ingredient is the ingot of one of the alloys (or orichalcum's), which the middle tier is poured from. */
	private static boolean isAnAlloyIngot(net.minecraft.world.item.Item item) {
		return item == ModItems.ORICALCO || Alloys.ALL.stream().anyMatch(other -> ModItems.alloy(other.id()) == item);
	}

	// ------------------------------------------------------------------ the middle tier: recipes and places

	/** Two alloys and one more thing each, never the forge heart, and each where its design says. */
	@GameTest
	public void middleRecipesAreTwoAlloysAndOneMore(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		List<String> places = List.of("ALMAS", "ALMAS", "VACIO", "ANY");
		int index = 0;
		for (String id : List.of("espectracero", "corazon_de_volcan", "eclipse", "astralita")) {
			Alloys.Recipe recipe = recipe(id);
			long alloys = recipe.inputs().stream().filter(part -> isAnAlloyIngot(part.item().get())).count();
			long others = recipe.inputs().stream().filter(part -> !isAnAlloyIngot(part.item().get())).count();
			if (alloys < 2 || others != 1) {
				wrong.add(id + ": " + alloys + " aleaciones y " + others + " más");
			}
			if (recipe.inputs().stream().anyMatch(part -> part.item().get() == ModItems.CORAZON_DE_FORJA)) {
				wrong.add(id + " lleva corazón de forja");
			}
			if (Alloys.place(recipe).name().equals(places.get(index)) == false) {
				wrong.add(id + " se hace en " + Alloys.place(recipe));
			}
			if (recipe.output() != 2) {
				wrong.add(id + " saca " + recipe.output());
			}
			if (!Alloys.MIDDLE.contains(id)) {
				wrong.add(id + " no está en MIDDLE");
			}
			index++;
		}
		helper.assertTrue(Alloys.MIDDLE.size() == 4, "MIDDLE tiene las cuatro");
		helper.assertTrue(Alloys.WHITE_HEAT_ONLY.contains("astralita") && Alloys.FOUNDRY_ONLY.contains("astralita"),
			"la astralita es de calor blanco y de línea de fundición");
		helper.assertTrue(recipe("espectracero").heat() == Alloys.Heat.FUNDIDA && recipe("corazon_de_volcan").heat() == Alloys.Heat.FORJA_BLANCA
			&& recipe("eclipse").heat() == Alloys.Heat.FORJA_BLANCA && recipe("astralita").heat() == Alloys.Heat.FORJA_BLANCA,
			"y sus calores son los del diseño");
		helper.assertTrue(wrong.isEmpty(), "recetas intermedias mal: " + wrong);
		helper.succeed();
	}

	/** The soul forge, lit in the Nether, pours a batch of spectresteel and one of volcano heart; the void forge, in the End, eclipse. */
	@GameTest(maxTicks = 40)
	public void theFarForgesMakeTheMiddleTier(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		BlockPos pos = FAR_SPOT;
		clearAround(nether, pos, 2);
		nether.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
		nether.setBlockAndUpdate(pos, ModBlocks.FRAGUA_DE_ALMAS.defaultBlockState().setValue(FarForgeBlock.LIT, true));
		FarForgeBlockEntity forge = (FarForgeBlockEntity) nether.getBlockEntity(pos);
		Container chest = (Container) nether.getBlockEntity(pos.below());
		for (String id : List.of("espectracero", "corazon_de_volcan")) {
			Alloys.Recipe recipe = recipe(id);
			fill(forge, recipe, 1);
			helper.assertTrue(forge.ready() == recipe, "con la receta entera en el hogar, está lista para " + id + ": " + forge.ready());
			forge.addFuel(1);
			tick(nether, pos, FarForgeBlockEntity.BATCH_TICKS);
			helper.assertTrue(count(chest, ModItems.alloy(id)) == 2, "una tanda de " + id + " son 2 lingotes: " + count(chest, ModItems.alloy(id)));
			helper.assertTrue(forge.fuel() == 0 && forge.hearth().isEmpty(), id + " gasta el combustible (un polvo de blaze) y la receta");
		}
		nether.setBlockAndUpdate(pos.below(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

		ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
		clearAround(end, pos, 2);
		end.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
		end.setBlockAndUpdate(pos, ModBlocks.FRAGUA_DEL_VACIO.defaultBlockState().setValue(FarForgeBlock.LIT, true));
		FarForgeBlockEntity voidForge = (FarForgeBlockEntity) end.getBlockEntity(pos);
		Alloys.Recipe eclipse = recipe("eclipse");
		fill(voidForge, eclipse, 1);
		voidForge.addFuel(1);
		tick(end, pos, FarForgeBlockEntity.BATCH_TICKS);
		helper.assertTrue(count((Container) end.getBlockEntity(pos.below()), ModItems.alloy("eclipse")) == 2, "y el eclipse, 2 lingotes en el End");
		end.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
		end.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

		// Out of their dimension they melt nothing: the soul forge in the End, the void forge in the Nether.
		clearAround(end, pos, 2);
		end.setBlockAndUpdate(pos, ModBlocks.FRAGUA_DE_ALMAS.defaultBlockState().setValue(FarForgeBlock.LIT, true));
		FarForgeBlockEntity soulInTheEnd = (FarForgeBlockEntity) end.getBlockEntity(pos);
		fill(soulInTheEnd, recipe("espectracero"), 1);
		soulInTheEnd.addFuel(1);
		tick(end, pos, FarForgeBlockEntity.BATCH_TICKS + 5);
		helper.assertTrue(soulInTheEnd.fuel() == 1, "la fragua de almas no funde espectracero fuera del Nether");
		end.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		clearAround(nether, pos, 2);
		nether.setBlockAndUpdate(pos, ModBlocks.FRAGUA_DEL_VACIO.defaultBlockState().setValue(FarForgeBlock.LIT, true));
		FarForgeBlockEntity voidInTheNether = (FarForgeBlockEntity) nether.getBlockEntity(pos);
		fill(voidInTheNether, eclipse, 1);
		voidInTheNether.addFuel(1);
		tick(nether, pos, FarForgeBlockEntity.BATCH_TICKS + 5);
		helper.assertTrue(voidInTheNether.fuel() == 1, "ni la del vacío el eclipse fuera del End");
		nether.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	/**
	 * Two orichalcum and one aetherium in the pot and star iron in a tank pour two astralite into the empty tank beside it, on
	 * an obsidian crucible; an iron one pours nothing.
	 */
	@GameTest
	public void obsidianCruciblePoursAstralite(GameTestHelper helper) {
		Alloys.Recipe astralite = recipe("astralita");
		CrucibleBlockEntity pot = pot(helper, POT, ModBlocks.CRISOL_DE_OBSIDIANA);
		MeltTankBlockEntity iron = tank(helper, POT.east());
		MeltTankBlockEntity empty = tank(helper, POT.west());
		iron.fill(ModItems.HIERRO_ESTELAR, 2);
		pot.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(ModItems.ORICALCO, 2));
		pot.setItem(CrucibleBlockEntity.SLOT_SECOND, new ItemStack(ModItems.alloy("eterio"), 1));
		pot.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 2));
		run(helper, POT, pot, CrucibleBlock.Tier.OBSIDIANA.cook + 10);
		// The obsidian pot gives one more than the recipe says, as it does for every alloy it pours.
		int made = astralite.output() + CrucibleBlock.Tier.OBSIDIANA.bonus;
		helper.assertTrue(empty.bankMetal() == ModItems.alloy("astralita") && empty.bankAmount() == made,
			"la cuba vacía tiene " + made + " de astralita: " + empty.bankAmount() + " de " + empty.bankMetal());
		helper.assertTrue(pot.getItem(CrucibleBlockEntity.SLOT_FIRST).isEmpty() && pot.getItem(CrucibleBlockEntity.SLOT_SECOND).isEmpty(),
			"y los huecos quedan vacíos");
		helper.assertTrue(iron.bankAmount() == 0, "el hierro estelar de la cuba se gastó");

		BlockPos other = POT.offset(0, 0, 4);
		CrucibleBlockEntity cheap = pot(helper, other, ModBlocks.CRISOL_DE_HIERRO);
		MeltTankBlockEntity iron2 = tank(helper, other.east());
		MeltTankBlockEntity empty2 = tank(helper, other.west());
		iron2.fill(ModItems.HIERRO_ESTELAR, 2);
		cheap.setItem(CrucibleBlockEntity.SLOT_FIRST, new ItemStack(ModItems.ORICALCO, 2));
		cheap.setItem(CrucibleBlockEntity.SLOT_SECOND, new ItemStack(ModItems.alloy("eterio"), 1));
		cheap.setItem(CrucibleBlockEntity.SLOT_FUEL, new ItemStack(ModItems.ASCUA, 2));
		run(helper, other, cheap, CrucibleBlock.Tier.HIERRO.cook + 10);
		helper.assertTrue(empty2.bankAmount() == 0, "el crisol de hierro no cuela astralita: " + empty2.bankAmount() + " de " + empty2.bankMetal());
		helper.assertTrue(cheap.getItem(CrucibleBlockEntity.SLOT_FIRST).getCount() == 2 && iron2.bankAmount() == 2, "y no gasta nada");
		helper.assertTrue(Alloys.match(List.of(new ItemStack(ModItems.ORICALCO, 2), new ItemStack(ModItems.alloy("eterio"), 1),
			new ItemStack(ModItems.HIERRO_ESTELAR, 2)), Alloys.Heat.FUNDIDA) == null, "ni una mesa a calor de fundición");
		helper.succeed();
	}

	/** Every middle-tier alloy is a material every part takes, and every forged item can be made all of it, trait included. */
	@GameTest(maxTicks = 20)
	public void middleAndPeakMakeEveryPart(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (String id : Alloys.ALL.stream().map(Alloys.Recipe::id).filter(id -> Alloys.MIDDLE.contains(id)).toList()) {
			ForgeMaterial material = ForgeMaterial.fromInput(new ItemStack(ModItems.alloy(id)));
			if (material == null || !material.getSerializedName().equals(id)) {
				wrong.add(id + " no es un material");
				continue;
			}
			helper.assertFalse(material.isBasic(), id + " se cuela, no se corta");
			for (PartType type : PartType.values()) {
				if (!type.accepts(material)) {
					wrong.add(type.id() + " no acepta " + id);
					continue;
				}
				ItemStack part = Assembler.createPart(type, material);
				if (part.isEmpty() || part.get(ModComponents.MATERIAL) != material) {
					wrong.add(type.id() + " de " + id + " sale mal: " + part);
				}
				checked++;
			}
			for (ForgeType type : ForgeType.values()) {
				ItemStack made = all(helper, type, material);
				ForgedParts forged = made.get(ModComponents.PARTS);
				if (made.isEmpty() || forged == null || !forged.hasTrait(material.trait)) {
					wrong.add(type.id() + " de " + id + " sale mal");
				}
				checked++;
			}
		}
		helper.assertTrue(checked > 0, "no se probó nada");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ------------------------------------------------------------------ the middle tier: numbers

	/** The middle tier is strong in one thing each and never above the forge heart (nor above the peak alloy it feeds) in it. */
	@GameTest(maxTicks = 20)
	public void middleTierStaysUnderTheHeart(GameTestHelper helper) {
		ForgeMaterial heart = ForgeMaterial.CORAZON;
		ForgeMaterial spectre = ForgeMaterial.ESPECTRACERO;
		helper.assertTrue(spectre.durability < heart.durability && spectre.armorDurability < heart.armorDurability
			&& spectre.toughness < heart.toughness && spectre.handleDurability < heart.handleDurability,
			"el espectracero dura, y aguanta, menos que el corazón");
		helper.assertTrue(ForgeMaterial.CORAZON_DE_VOLCAN.attackDamageBonus < heart.attackDamageBonus, "el corazón de volcán pega menos que el corazón");
		for (ForgeMaterial magic : List.of(ForgeMaterial.ECLIPSE, ForgeMaterial.ASTRALITA)) {
			helper.assertTrue(magic.enchantability <= 25 && magic.miningSpeed <= 9.5F, magic + ": encantabilidad " + magic.enchantability
				+ " y minado " + magic.miningSpeed);
		}
		for (ForgeMaterial middle : List.of(ForgeMaterial.ESPECTRACERO, ForgeMaterial.CORAZON_DE_VOLCAN, ForgeMaterial.ECLIPSE, ForgeMaterial.ASTRALITA)) {
			int armour = 0;
			for (net.minecraft.world.item.equipment.ArmorType slot : net.minecraft.world.item.equipment.ArmorType.values()) {
				armour += slot == net.minecraft.world.item.equipment.ArmorType.BODY ? 0 : middle.defense(slot);
			}
			helper.assertTrue(armour <= 20, middle + ": armadura " + armour);
			helper.assertTrue(middle.knockbackResistance <= 0.10F, middle + ": empuje " + middle.knockbackResistance);
			helper.assertTrue(middle.attackDamageBonus < 4.5F && middle.attackDamageBonus <= 4.0F, middle + ": daño " + middle.attackDamageBonus);
			helper.assertTrue(!ArmorSets.bonuses(middle).stream().anyMatch(bonus -> bonus.attribute().equals(Attributes.ARMOR)),
				middle + ": su conjunto no da armadura plana");
		}
		helper.assertTrue(ForgeMaterial.ESPECTRACERO.attackDamageBonus < 4.0F && ForgeMaterial.ECLIPSE.attackDamageBonus < 4.0F,
			"espectracero y eclipse pegan menos de +4,0");
		helper.assertTrue(ForgeMaterial.CORAZON_DE_VOLCAN.durability < 2031, "el corazón de volcán dura menos de 2031");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the middle tier: traits

	/**
	 * Amparo: with one piece and 20 health, a blow of 30 that armour reads is cut to 8 and whoever struck catches wispfire;
	 * again straight away it is not; after the wait it is again; the full set waits less.
	 */
	@GameTest(maxTicks = 20)
	public void shelterCapsOnceThenWaits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(2, 2, 2));
		Mob zombie = zombie(helper, new BlockPos(4, 2, 2));
		wear(helper, player, ForgeMaterial.ESPECTRACERO, 1);
		var blow = level.damageSources().mobAttack(zombie);
		helper.assertTrue(player.getMaxHealth() == 20.0F, "vida máxima 20");
		float first = CombatHooks.capped(player, blow, 30.0F, false);
		helper.assertTrue(Math.abs(first - 8.0F) < EPS, "un golpe de 30 se corta a 8: " + first);
		helper.assertTrue(zombie.hasEffect(ModEffects.LLAMA_FATUA), "y el que pegó se lleva llama fatua");
		helper.assertTrue(Math.abs(CombatHooks.capped(player, blow, 30.0F, false) - 30.0F) < EPS, "otra vez enseguida no ampara");
		helper.assertTrue(TraitEffects.shelterReadyAt(player) - level.getGameTime() == TraitEffects.SHELTER_COOLDOWN,
			"y espera " + TraitEffects.SHELTER_COOLDOWN + " ticks");
		TraitEffects.forgetShelter(player);
		helper.assertTrue(Math.abs(CombatHooks.capped(player, blow, 30.0F, false) - 8.0F) < EPS, "pasada la espera, otra vez");
		TraitEffects.forgetShelter(player);
		helper.assertTrue(Math.abs(CombatHooks.capped(player, level.damageSources().fall(), 30.0F, false) - 30.0F) < EPS,
			"una caída no la cubre: la armadura no la lee");
		helper.assertTrue(Math.abs(CombatHooks.capped(player, level.damageSources().genericKill(), 30.0F, false) - 30.0F) < EPS, "ni /kill");
		helper.assertTrue(Math.abs(CombatHooks.capped(player, blow, 6.0F, false) - 6.0F) < EPS, "un golpe que ya es menor no se toca");
		helper.assertTrue(TraitEffects.shelterReadyAt(player) <= level.getGameTime(), "y no gasta la espera");
		wear(helper, player, ForgeMaterial.ESPECTRACERO, 4);
		TraitEffects.forgetShelter(player);
		CombatHooks.capped(player, blow, 30.0F, false);
		helper.assertTrue(TraitEffects.shelterReadyAt(player) - level.getGameTime() == TraitEffects.SHELTER_SET_COOLDOWN,
			"con el conjunto la espera es " + TraitEffects.SHELTER_SET_COOLDOWN);
		wear(helper, player, ForgeMaterial.CUERO, 0);
		TraitEffects.forgetShelter(player);
		player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, all(helper, ForgeType.ESCUDO, ForgeMaterial.ESPECTRACERO));
		helper.assertTrue(Math.abs(CombatHooks.capped(player, blow, 30.0F, false) - 8.0F) < EPS, "también vale en la mano");
		zombie.discard();
		helper.succeed();
	}

	/** Ardor: harder as health falls, alight from two steps; its armour answers a bad wound with fire resistance. */
	@GameTest(maxTicks = 20)
	public void ardorBurnsWhenThingsGoBadly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(2, 2, 2));
		Mob zombie = zombie(helper, new BlockPos(4, 2, 2));
		ItemStack sword = all(helper, ForgeType.ESPADA, ForgeMaterial.CORAZON_DE_VOLCAN);
		helper.assertTrue(Math.abs(TraitEffects.weaponBonus(level, player, zombie, sword)) < EPS, "a vida llena no suma nada");
		player.setHealth(4.0F);
		float bonus = TraitEffects.weaponBonus(level, player, zombie, sword);
		helper.assertTrue(Math.abs(bonus - 2.0F) < EPS, "a 4 de 20 suma 2,0: " + bonus);
		zombie.clearFire();
		player.setHealth(12.0F);
		helper.assertTrue(Math.abs(TraitEffects.weaponBonus(level, player, zombie, sword) - 1.0F) < EPS, "a 12 de 20, 1,0");
		helper.assertTrue(zombie.getRemainingFireTicks() > 0, "y a 12 de 20 el zombi arde");
		zombie.clearFire();
		player.setHealth(16.0F);
		helper.assertTrue(Math.abs(TraitEffects.weaponBonus(level, player, zombie, sword) - 0.5F) < EPS, "a 16 de 20, 0,5");
		helper.assertTrue(zombie.getRemainingFireTicks() <= 0, "y a 16 de 20 no arde");

		wear(helper, player, ForgeMaterial.CORAZON_DE_VOLCAN, 4);
		player.setHealth(7.0F);
		player.setRemainingFireTicks(100);
		var blow = level.damageSources().mobAttack(zombie);
		TraitEffects.onHurt(level, player, blow, 4.0F);
		helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE), "herido a 7 de 20, resistencia al fuego");
		helper.assertTrue(player.getRemainingFireTicks() == 0, "y con las cuatro piezas te apaga: " + player.getRemainingFireTicks());
		player.removeEffect(MobEffects.FIRE_RESISTANCE);
		TraitEffects.onHurt(level, player, blow, 4.0F);
		helper.assertFalse(player.hasEffect(MobEffects.FIRE_RESISTANCE), "otra vez antes de " + TraitEffects.ARDOR_COOLDOWN + " ticks, no");
		zombie.discard();
		helper.succeed();
	}

	/** Penumbra: spells cost less only in the dark, a blow on something in the dark gives mana, a wound in the dark more. */
	@GameTest(maxTicks = 40)
	public void penumbraOnlyInTheDark(GameTestHelper helper) {
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		BlockPos at = FAR_SPOT.offset(0, 0, 32);
		sealedCell(nether, at);
		CombatGameTests.TestPlayer dark = playerInTheDark(nether, at);
		dark.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, all(helper, ForgeType.BACULO, ForgeMaterial.ECLIPSE));
		helper.assertTrue(TraitEffects.inDark(nether, dark), "en una celda cerrada del Nether no hay luz");
		helper.assertTrue(Math.abs(TraitEffects.gearSpellCost(dark) - 0.85F) < EPS, "a oscuras el báculo de eclipse cuesta ×0,85: " + TraitEffects.gearSpellCost(dark));

		CombatGameTests.TestPlayer sunny = player(helper, new BlockPos(2, 2, 2));
		sunny.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, all(helper, ForgeType.BACULO, ForgeMaterial.ECLIPSE));
		boolean bright = !TraitEffects.inDark(helper.getLevel(), sunny);
		helper.assertTrue(bright == (Math.abs(TraitEffects.gearSpellCost(sunny) - 1.0F) < EPS), "y a la luz del día cuesta lo de siempre");

		// A blow on something in the dark gives 0.5 mana; the same blow in the light gives none.
		ItemStack sword = all(helper, ForgeType.ESPADA, ForgeMaterial.ECLIPSE);
		Mob inTheDark = EntityTypes.ZOMBIE.create(nether, EntitySpawnReason.EVENT);
		inTheDark.setPos(at.getX() + 3.5, at.getY(), at.getZ() + 2.5);
		Mana.set(dark, 0.0F);
		TraitEffects.onHit(nether, dark, inTheDark, nether.damageSources().playerAttack(dark), sword);
		helper.assertTrue(Math.abs(Mana.value(dark) - TraitEffects.PENUMBRA_HIT_MANA) < EPS, "golpear algo a oscuras da 0,5 de maná: " + Mana.value(dark));
		// Four pieces, four damage in the dark: min(3, 4 x 0.25 x 4) = 3.
		wear(helper, dark, ForgeMaterial.ECLIPSE, 4);
		Mana.set(dark, 0.0F);
		TraitEffects.onHurt(nether, dark, nether.damageSources().mobAttack(inTheDark), 4.0F);
		helper.assertTrue(Math.abs(Mana.value(dark) - 3.0F) < EPS, "herido a oscuras con 4 piezas, 3 de maná: " + Mana.value(dark));
		// The same blow in the light of day gives nothing.
		if (bright) {
			Mana.set(sunny, 0.0F);
			Mob inTheLight = zombie(helper, new BlockPos(4, 2, 2));
			TraitEffects.onHit(helper.getLevel(), sunny, inTheLight, helper.getLevel().damageSources().playerAttack(sunny), sword);
			helper.assertTrue(Mana.value(sunny) == 0.0F, "y a la luz no da nada: " + Mana.value(sunny));
			inTheLight.discard();
		}
		helper.succeed();
	}

	/**
	 * Sideral: a staff costs ×0.9, ×0.8 at night under open sky without rain, and a roof over the head is never night; the
	 * cheapest of the gear counts, not the product; a pick gives 0.1 mana a block.
	 */
	@GameTest(maxTicks = 40)
	public void siderealCheaperUnderTheNightSky(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(2, 2, 2));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, all(helper, ForgeType.BACULO, ForgeMaterial.ASTRALITA));
		boolean night = !level.isBrightOutside() && level.canSeeSky(player.blockPosition()) && !level.isRaining();
		float cost = TraitEffects.gearSpellCost(player);
		helper.assertTrue(Math.abs(cost - (night ? TraitEffects.SIDEREAL_NIGHT_COST : TraitEffects.SIDEREAL_COST)) < EPS,
			"el báculo de astralita cuesta " + (night ? "×0,80 de noche" : "×0,90 de día") + ": " + cost);
		helper.assertTrue(Math.abs(TraitEffects.SIDEREAL_COST - 0.90F) < EPS && Math.abs(TraitEffects.SIDEREAL_NIGHT_COST - 0.80F) < EPS,
			"los precios son 0,90 y 0,80");
		// Under a roof it is never the night sky.
		BlockPos head = player.blockPosition().above(2);
		level.setBlockAndUpdate(head, Blocks.STONE.defaultBlockState());
		helper.assertTrue(Math.abs(TraitEffects.gearSpellCost(player) - TraitEffects.SIDEREAL_COST) < EPS
			|| !level.canSeeSky(player.blockPosition()), "con techo, el precio de día");
		level.setBlockAndUpdate(head, Blocks.AIR.defaultBlockState());
		// The cheaper of two does not multiply with the other.
		player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, all(helper, ForgeType.BACULO, ForgeMaterial.ECLIPSE));
		helper.assertTrue(TraitEffects.gearSpellCost(player) <= cost + EPS && TraitEffects.gearSpellCost(player) >= 0.8F - EPS,
			"con dos, vale la mejor y no el producto: " + TraitEffects.gearSpellCost(player));
		// A pick gives a little mana for each block of some hardness.
		player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
		ItemStack pick = all(helper, ForgeType.PICO, ForgeMaterial.ASTRALITA);
		Mana.set(player, 0.0F);
		BlockPos stone = helper.absolutePos(new BlockPos(5, 2, 5));
		TraitEffects.workMana(level, player, stone, Blocks.STONE.defaultBlockState(), pick);
		helper.assertTrue(Math.abs(Mana.value(player) - TraitEffects.SIDEREAL_BLOCK_MANA) < EPS, "un bloque de piedra da 0,1 de maná: " + Mana.value(player));
		Mana.set(player, 0.0F);
		TraitEffects.workMana(level, player, stone, Blocks.AIR.defaultBlockState(), pick);
		helper.assertTrue(Mana.value(player) == 0.0F, "y el aire no da nada");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the middle tier: sets and arrows

	/** The sets of the middle tier give what the design says, and none gives flat armour. */
	@GameTest(maxTicks = 20)
	public void middleSetsAndArrows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(2, 2, 2));
		float base = Mana.maxOf(player);
		float health = player.getMaxHealth();
		wear(helper, player, ForgeMaterial.ESPECTRACERO, 4);
		ArmorSets.update(player);
		helper.assertTrue(Math.abs(player.getMaxHealth() - (health + 4.0F)) < EPS, "el conjunto de espectracero da +4 de vida: " + player.getMaxHealth());
		helper.assertTrue(ArmorSets.bonuses(ForgeMaterial.ESPECTRACERO).stream().anyMatch(bonus -> bonus.attribute().equals(Attributes.ARMOR_TOUGHNESS)
			&& bonus.amount() == 2.0) && ArmorSets.bonuses(ForgeMaterial.ESPECTRACERO).stream().anyMatch(bonus -> bonus.attribute().equals(Attributes.BURNING_TIME)
			&& bonus.amount() == -0.5), "+2 de dureza y el fuego dura menos");
		wear(helper, player, ForgeMaterial.CORAZON_DE_VOLCAN, 4);
		ArmorSets.update(player);
		helper.assertTrue(ArmorSets.bonuses(ForgeMaterial.CORAZON_DE_VOLCAN).stream().anyMatch(bonus -> bonus.attribute().equals(Attributes.ATTACK_DAMAGE)
			&& bonus.amount() == 2.0) && ArmorSets.bonuses(ForgeMaterial.CORAZON_DE_VOLCAN).stream().anyMatch(bonus -> bonus.attribute().equals(Attributes.BURNING_TIME)
			&& bonus.amount() == -1.0), "el corazón de volcán: +2 de daño y el fuego, -1");
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1.0 + 2.0)) < 0.01, "y el ataque sube 2: " + player.getAttributeValue(Attributes.ATTACK_DAMAGE));
		wear(helper, player, ForgeMaterial.ECLIPSE, 4);
		ArmorSets.update(player);
		helper.assertTrue(Math.abs(Mana.maxOf(player) - (base + Mana.ECLIPSE_SET_MANA)) < EPS && Mana.ECLIPSE_SET_MANA == 20.0F,
			"el eclipse suma 20 de maná máximo: " + (Mana.maxOf(player) - base));
		helper.assertTrue(ArmorSets.bonuses(ForgeMaterial.ECLIPSE).stream().anyMatch(bonus -> bonus.attribute().equals(Attributes.SNEAKING_SPEED)
			&& Math.abs(bonus.amount() - 0.3) < 1.0E-6), "y andas agachado más deprisa");
		wear(helper, player, ForgeMaterial.ASTRALITA, 4);
		ArmorSets.update(player);
		helper.assertTrue(Math.abs(Mana.maxOf(player) - (base + Mana.ASTRALITE_SET_MANA)) < EPS && Mana.ASTRALITE_SET_MANA == 30.0F,
			"la astralita suma 30: " + (Mana.maxOf(player) - base));
		helper.assertTrue(ArmorSets.bonuses(ForgeMaterial.ASTRALITA).stream().anyMatch(bonus -> bonus.attribute().equals(Attributes.ARMOR_TOUGHNESS)
			&& bonus.amount() == 1.0), "y +1 de dureza");
		helper.assertTrue(Mana.carriesMana(player), "con el conjunto la barra de maná está a la vista");
		wear(helper, player, ForgeMaterial.CUERO, 0);
		ArmorSets.update(player);

		// The arrow tips.
		helper.assertTrue(ArrowTips.special(ForgeMaterial.ESPECTRACERO) == ArrowTips.Special.VELO, "espectracero: velo");
		helper.assertTrue(ArrowTips.special(ForgeMaterial.CORAZON_DE_VOLCAN) == ArrowTips.Special.ARDOR, "corazón de volcán: ardor");
		helper.assertTrue(ArrowTips.special(ForgeMaterial.ECLIPSE) == ArrowTips.Special.SOMBRA, "eclipse: sombra");
		helper.assertTrue(ArrowTips.special(ForgeMaterial.ASTRALITA) == ArrowTips.Special.ASTRO, "astralita: astro");
		helper.assertTrue(Math.abs(ArrowTips.wrathBonus(ArrowTips.Special.ARDOR, player) - 0.0F) < EPS, "a vida llena el ardor de la flecha no suma");
		player.setHealth(4.0F);
		helper.assertTrue(Math.abs(ArrowTips.wrathBonus(ArrowTips.Special.ARDOR, player) - 1.0F) < EPS, "a 4 de 20 suma 1,0: " + ArrowTips.wrathBonus(ArrowTips.Special.ARDOR, player));
		Mob zombie = zombie(helper, new BlockPos(4, 2, 2));
		ArrowTips.onHit(level, null, player, zombie, ArrowTips.Special.ARDOR);
		helper.assertTrue(zombie.getRemainingFireTicks() > 0, "con 4 escalones el objetivo arde");
		player.setHealth(20.0F);
		ArrowTips.onHit(level, null, player, zombie, ArrowTips.Special.VELO);
		helper.assertTrue(player.hasEffect(MobEffects.RESISTANCE), "el velo da resistencia al que dispara");
		Mana.set(player, 0.0F);
		ArrowTips.onHit(level, null, player, zombie, ArrowTips.Special.ASTRO);
		helper.assertTrue(Math.abs(Mana.value(player) - ArrowTips.ASTRO_ARROW_MANA) < EPS && zombie.hasEffect(MobEffects.GLOWING),
			"el astro da 1,5 de maná y el objetivo brilla: " + Mana.value(player));
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		BlockPos cell = FAR_SPOT.offset(0, 0, 64);
		sealedCell(nether, cell);
		Mob inTheDark = EntityTypes.ZOMBIE.create(nether, EntitySpawnReason.EVENT);
		inTheDark.setPos(cell.getX() + 1.5, cell.getY(), cell.getZ() + 2.5);
		ArrowTips.onHit(nether, null, player, inTheDark, ArrowTips.Special.SOMBRA);
		helper.assertTrue(inTheDark.hasEffect(MobEffects.BLINDNESS), "la sombra ciega a lo que está a oscuras");
		zombie.discard();
		helper.succeed();
	}

	/** The middle-tier ingots are rare and fireproof. */
	@GameTest(maxTicks = 20)
	public void middleIngotsAreRareAndFireproof(GameTestHelper helper) {
		for (String id : Alloys.MIDDLE) {
			ItemStack ingot = new ItemStack(ModItems.alloy(id));
			helper.assertTrue(ingot.getRarity() == net.minecraft.world.item.Rarity.RARE, id + " es raro: " + ingot.getRarity());
			helper.assertTrue(ingot.has(net.minecraft.core.component.DataComponents.DAMAGE_RESISTANT), id + " no se quema");
		}
		helper.succeed();
	}
}
