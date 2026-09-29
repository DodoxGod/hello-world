package dev.forja.test;

import java.util.List;

import dev.forja.clase.ClassDamage;
import dev.forja.clase.ClassEffects;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.PlayerClass;
import dev.forja.clase.Talent;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.Stamina;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Relic;
import dev.forja.item.OblivionEmblemItem;
import dev.forja.magic.Healing;
import dev.forja.magic.Mana;
import dev.forja.magic.Spellcasting;
import dev.forja.material.ForgeMaterial;
import dev.forja.menu.ForgeMenu;
import dev.forja.menu.Station;
import dev.forja.part.PartType;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Las clases y el farol de curación (docs/CLASES.md), tras unir la rama forja-clases con el maná. */
public class ClasesGameTests {
	private static ItemStack lantern(GameTestHelper helper) {
		return Assembler.create(ForgeType.FAROL, List.of(ForgeMaterial.ESMERALDA, ForgeMaterial.ORO, ForgeMaterial.MADERA),
			helper.getLevel().registryAccess());
	}

	/** Andy's Asesino: "más stamina, más esquive, un poquito más de daño, bastante menos vida". */
	@GameTest
	public void anAssassinHasLessHealthAndMoreStamina(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		float bareStamina = Stamina.maxOf(player);
		double bareHealth = player.getAttributeValue(Attributes.MAX_HEALTH);
		ClassProgress.choose(player, PlayerClass.ASESINO);
		helper.assertTrue(ClassProgress.clazz(player) == PlayerClass.ASESINO, "debería ser asesino");
		helper.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) < bareHealth,
			"menos vida: " + player.getAttributeValue(Attributes.MAX_HEALTH) + " frente a " + bareHealth);
		helper.assertTrue(Stamina.maxOf(player) > bareStamina, "más estamina: " + Stamina.maxOf(player) + " frente a " + bareStamina);
		helper.assertTrue(ClassEffects.dodgeCostMultiplier(player) < 1.0F, "esquivar le cuesta menos");
		ClassProgress.clear(player);
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MAX_HEALTH) - bareHealth) < 1.0E-6, "sin clase, la vida de siempre");
		helper.succeed();
	}

	/** Experience brings levels, each level a point, and a point buys a talent of one's own class only. */
	@GameTest
	public void levelsGivePointsForTheTree(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.GUERRERO);
		int before = ClassProgress.data(player).points();
		ClassProgress.award(player, ClassProgress.totalFor(3) - ClassProgress.data(player).xp());
		helper.assertTrue(ClassProgress.data(player).level() == 3, "debería ser nivel 3, es " + ClassProgress.data(player).level());
		helper.assertTrue(ClassProgress.data(player).points() > before, "subir de nivel da puntos");
		helper.assertTrue(ClassProgress.check(player, Talent.ASESINO_PIES_LIGEROS) == ClassProgress.Refusal.OTHER_CLASS,
			"un talento de otra clase no se aprende");
		helper.assertTrue(ClassProgress.unlock(player, Talent.GUERRERO_SEGUNDO_ALIENTO), "el primer talento del guerrero se aprende");
		helper.assertTrue(ClassProgress.has(player, Talent.GUERRERO_SEGUNDO_ALIENTO), "y queda aprendido");
		helper.assertTrue(ClassProgress.check(player, Talent.GUERRERO_SEGUNDO_ALIENTO) == ClassProgress.Refusal.ALREADY, "no dos veces");
		// Andy (2026-09-29): a change of class starts again at level 1 (aClassChangeStartsAtLevelOne).
		ClassProgress.choose(player, PlayerClass.TANQUE);
		helper.assertTrue(ClassProgress.data(player).level() == 1, "cambiar de clase vuelve al nivel 1");
		helper.assertTrue(ClassProgress.data(player).spent() == 0, "y sin talentos");
		ClassProgress.clear(player);
		helper.succeed();
	}

	/** Andy (2026-09-29): changing class does not keep the level. Picking your own class again is only a reset of the tree. */
	@GameTest
	public void aClassChangeStartsAtLevelOne(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(player, PlayerClass.GUERRERO);
		ClassProgress.award(player, ClassProgress.totalFor(5));
		ClassProgress.unlock(player, Talent.GUERRERO_SEGUNDO_ALIENTO);
		helper.assertTrue(ClassProgress.data(player).level() == 5, "debería ser nivel 5, es " + ClassProgress.data(player).level());
		helper.assertTrue(ClassProgress.data(player).changes() == 0, "elegir la primera vez no es un cambio");

		// The same class: the tree is emptied, the level and the experience stay, and it is not counted as a change.
		int xp = ClassProgress.data(player).xp();
		ClassProgress.choose(player, PlayerClass.GUERRERO);
		helper.assertTrue(ClassProgress.data(player).level() == 5 && ClassProgress.data(player).xp() == xp,
			"tu misma clase conserva el nivel: " + ClassProgress.data(player).level());
		helper.assertTrue(ClassProgress.data(player).spent() == 0, "pero borra los talentos");
		helper.assertTrue(ClassProgress.data(player).changes() == 0, "y no cuenta como cambio");

		// Another class: level 1, no experience, no talents, one change more.
		ClassProgress.unlock(player, Talent.GUERRERO_SEGUNDO_ALIENTO);
		ClassProgress.choose(player, PlayerClass.MAGO);
		helper.assertTrue(ClassProgress.clazz(player) == PlayerClass.MAGO, "ahora es mago");
		helper.assertTrue(ClassProgress.data(player).level() == 1, "cambiar de clase vuelve al nivel 1, es " + ClassProgress.data(player).level());
		helper.assertTrue(ClassProgress.data(player).xp() == 0, "sin experiencia: " + ClassProgress.data(player).xp());
		helper.assertTrue(ClassProgress.data(player).spent() == 0, "sin talentos");
		helper.assertTrue(ClassProgress.data(player).points() == ClassProgress.pointsAt(1), "con los puntos del nivel 1");
		helper.assertTrue(ClassProgress.data(player).changes() == 1, "y un cambio contado: " + ClassProgress.data(player).changes());
		ClassProgress.clear(player);
		helper.succeed();
	}

	private static net.minecraft.world.entity.monster.zombie.Zombie zombie(GameTestHelper helper, BlockPos at) {
		CombatGameTests.noRandomThreat();
		var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, at);
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		zombie.setNoAi(true);
		return zombie;
	}

	/** What one blow takes off a creature, fresh from any other. */
	private static float hit(net.minecraft.world.entity.LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
		float before = target.getHealth();
		target.invulnerableTime = 0;
		target.hurtServer((net.minecraft.server.level.ServerLevel) target.level(), source, amount);
		return before - target.getHealth();
	}

	/**
	 * Andy (2026-09-29): the Curandero's magic hurts, at a third, and still mends. The same tome read by a player
	 * with no class and by a Curandero, each on a zombie of its own: the Curandero's rune takes a third of what the
	 * other's does, and mends the Curandero's wolf standing on it, a tenth of the whole bite.
	 */
	@GameTest
	public void aHealersRuneHurtsAtAThirdAndStillHeals(GameTestHelper helper) {
		CombatGameTests.TestPlayer plain = CombatGameTests.player(helper, new BlockPos(1, 1, 4));
		CombatGameTests.TestPlayer healer = CombatGameTests.player(helper, new BlockPos(4, 1, 6));
		ClassProgress.choose(healer, PlayerClass.CURANDERO);
		var plainTarget = zombie(helper, new BlockPos(1, 1, 1));
		var healerTarget = zombie(helper, new BlockPos(6, 1, 6));
		var wolf = helper.spawn(net.minecraft.world.entity.EntityTypes.WOLF, new BlockPos(6, 1, 5));
		wolf.setNoAi(true);
		wolf.tame(healer);
		wolf.setHealth(2.0F);
		// A wooden núcleo bites for 5, well under what a zombie may lose to one blow.
		ItemStack tome = Assembler.create(ForgeType.GRIMORIO, List.of(ForgeMaterial.MADERA, ForgeMaterial.MADERA, ForgeMaterial.HIERRO),
			helper.getLevel().registryAccess());
		float bite = Spellcasting.areaDamage(ForgeMaterial.MADERA);
		float plainBefore = plainTarget.getHealth();
		float healerBefore = healerTarget.getHealth();
		Spellcasting.cast(helper.getLevel(), plain, tome, ForgeType.GRIMORIO, plainTarget.position());
		Spellcasting.cast(helper.getLevel(), healer, tome, ForgeType.GRIMORIO, healerTarget.position());
		float plainLoss = plainBefore - plainTarget.getHealth();
		float healerLoss = healerBefore - healerTarget.getHealth();
		helper.assertTrue(plainLoss > 0.0F, "la runa de alguien sin clase daña al zombi");
		helper.assertTrue(healerLoss > 0.0F, "la runa del curandero ahora sí daña al zombi");
		helper.assertTrue(Math.abs(healerLoss - plainLoss * ClassDamage.CURANDERO_MAGIC) < 0.05F,
			"a un tercio: " + healerLoss + " frente a " + plainLoss);
		float mended = wolf.getHealth() - 2.0F;
		helper.assertTrue(Math.abs(mended - bite * Healing.MAGIC_HEAL_SHARE) < 1.0E-3F,
			"y sigue curando al aliado una décima parte del daño entero: " + mended + " de " + bite);
		ClassProgress.clear(healer);
		helper.succeed();
	}

	/** Andy's factors are in the hook every blow of a player goes through (ClassEffects.dealt), by kind of blow. */
	@GameTest
	public void classDamageFactorsByKindOfBlow(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		var target = zombie(helper, new BlockPos(3, 1, 1));
		var sources = helper.getLevel().damageSources();
		float bareMelee = ClassEffects.dealt(sources.playerAttack(player), target, false, false);
		float bareMagic = ClassEffects.dealt(sources.indirectMagic(player, player), target, false, false);
		helper.assertTrue(bareMelee == 1.0F && bareMagic == 1.0F, "sin clase no cambia nada");

		ClassProgress.choose(player, PlayerClass.TANQUE);
		float tankMelee = ClassEffects.dealt(sources.playerAttack(player), target, false, false);
		helper.assertTrue(Math.abs(tankMelee - 0.67F * bareMelee) < 1.0E-4F, "el tanque pega ×0,67 cuerpo a cuerpo: " + tankMelee);
		helper.assertTrue(Math.abs(ClassEffects.dealt(sources.indirectMagic(player, player), target, false, false) - 0.67F) < 1.0E-4F,
			"y con magia también ×0,67");
		helper.assertTrue(ClassDamage.factor(player, ClassDamage.Blow.PROJECTILE) == ClassDamage.TANQUE_ALL, "y con proyectiles");

		ClassProgress.choose(player, PlayerClass.MAGO);
		float mageMelee = ClassEffects.dealt(sources.playerAttack(player), target, false, false);
		helper.assertTrue(Math.abs(mageMelee - 0.7F * bareMelee) < 1.0E-4F, "el mago pega ×0,7 cuerpo a cuerpo: " + mageMelee);
		helper.assertTrue(ClassEffects.dealt(sources.indirectMagic(player, player), target, false, false) == 1.0F,
			"su magia no lleva factor (el +15 % de hechizos va al lanzar)");
		helper.assertTrue(ClassProgress.clazz(player).base(dev.forja.clase.ClassStat.MELEE_DAMAGE) == 0.0F,
			"el ×0,7 sustituye su −10 % de antes, no se suma");

		ClassProgress.choose(player, PlayerClass.ARQUERO);
		helper.assertTrue(Math.abs(ClassEffects.dealt(sources.playerAttack(player), target, false, false) - 0.7F) < 1.0E-4F,
			"el arquero pega ×0,7 cuerpo a cuerpo");

		ClassProgress.choose(player, PlayerClass.CURANDERO);
		helper.assertTrue(Math.abs(ClassEffects.dealt(sources.playerAttack(player), target, false, false) - 0.5F) < 1.0E-4F,
			"el curandero pega ×0,5 cuerpo a cuerpo");
		helper.assertTrue(Math.abs(ClassEffects.dealt(sources.indirectMagic(player, player), target, false, false) - 1.0F / 3.0F) < 1.0E-4F,
			"y su magia hace ×1/3");
		ClassProgress.clear(player);
		helper.succeed();
	}

	/** A Tanque's blow of the hand takes 0.67 of what the same blow of a player with no class takes. */
	@GameTest
	public void aTanksMeleeIsTwoThirds(GameTestHelper helper) {
		CombatGameTests.TestPlayer bare = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer tank = CombatGameTests.player(helper, new BlockPos(1, 1, 5));
		ClassProgress.choose(tank, PlayerClass.TANQUE);
		var target = zombie(helper, new BlockPos(3, 1, 3));
		var sources = helper.getLevel().damageSources();
		float ratio = ClassEffects.dealt(sources.playerAttack(tank), target, false, false)
			/ ClassEffects.dealt(sources.playerAttack(bare), target, false, false);
		helper.assertTrue(Math.abs(ratio - ClassDamage.TANQUE_ALL) < 1.0E-4F, "×0,67: " + ratio);
		ClassProgress.clear(tank);
		helper.succeed();
	}

	/** A Mago's blow of the hand takes 0.7 of what a player with no class does. */
	@GameTest
	public void aMagesMeleeIsSevenTenths(GameTestHelper helper) {
		CombatGameTests.TestPlayer bare = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer mage = CombatGameTests.player(helper, new BlockPos(1, 1, 5));
		ClassProgress.choose(mage, PlayerClass.MAGO);
		var target = zombie(helper, new BlockPos(3, 1, 3));
		var sources = helper.getLevel().damageSources();
		float ratio = ClassEffects.dealt(sources.playerAttack(mage), target, false, false)
			/ ClassEffects.dealt(sources.playerAttack(bare), target, false, false);
		helper.assertTrue(Math.abs(ratio - ClassDamage.MAGO_MELEE) < 1.0E-4F, "×0,7: " + ratio);
		ClassProgress.clear(mage);
		helper.succeed();
	}

	/**
	 * A Guerrero's spell hurts at 0.4: the same magic blow from a player with no class and from a Guerrero, on two
	 * zombies (magic goes through armour, so what they lose is the blow itself).
	 */
	@GameTest
	public void aWarriorsSpellIsFourTenths(GameTestHelper helper) {
		CombatGameTests.TestPlayer bare = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		CombatGameTests.TestPlayer warrior = CombatGameTests.player(helper, new BlockPos(1, 1, 5));
		ClassProgress.choose(warrior, PlayerClass.GUERRERO);
		var first = zombie(helper, new BlockPos(4, 1, 1));
		var second = zombie(helper, new BlockPos(4, 1, 5));
		var sources = helper.getLevel().damageSources();
		float plain = hit(first, sources.indirectMagic(bare, bare), 5.0F);
		float warriors = hit(second, sources.indirectMagic(warrior, warrior), 5.0F);
		helper.assertTrue(plain > 0.0F, "el hechizo daña: " + plain);
		helper.assertTrue(Math.abs(warriors - plain * ClassDamage.GUERRERO_MAGIC) < 0.05F, "×0,4: " + warriors + " frente a " + plain);
		ClassProgress.clear(warrior);
		helper.succeed();
	}

	/**
	 * Andy (2026-09-29): what changes class is forged. A núcleo of echo, an engaste and a chain make the Medallón del
	 * olvido, through the same rule as any forged piece (Assembler.evaluate) and on the star of a forge table with
	 * one press; the same parts with a núcleo of anything else make nothing.
	 */
	@GameTest
	public void theMedallionOfOblivionIsForged(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		List<PartType> types = List.of(PartType.NUCLEO, PartType.ENGASTE, PartType.CADENA);
		helper.assertTrue(ForgeType.match(types) == null, "ninguna pieza de equipo tiene esas tres partes");
		helper.assertTrue(Relic.match(types) == Relic.MEDALLON_DEL_OLVIDO, "son las del medallón");

		ItemStack made = Assembler.evaluate(List.of(Assembler.createPart(PartType.CADENA, ForgeMaterial.HIERRO),
			Assembler.createPart(PartType.NUCLEO, ForgeMaterial.ECO), Assembler.createPart(PartType.ENGASTE, ForgeMaterial.ORO)), registries).stack();
		helper.assertTrue(made.is(ModItems.MEDALLON_DEL_OLVIDO), "núcleo de eco, engaste y cadena forjan el medallón: " + made);
		helper.assertTrue(made.getItem() instanceof OblivionEmblemItem, "y cambia de clase como el emblema");
		var colours = made.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
		helper.assertTrue(colours != null && colours.colors().equals(List.of(ForgeMaterial.ECO.color, ForgeMaterial.ORO.color, ForgeMaterial.HIERRO.color)),
			"con el color de cada pieza en su capa: " + colours);
		ItemStack wrong = Assembler.evaluate(List.of(Assembler.createPart(PartType.NUCLEO, ForgeMaterial.AMATISTA),
			Assembler.createPart(PartType.ENGASTE, ForgeMaterial.ORO), Assembler.createPart(PartType.CADENA, ForgeMaterial.HIERRO)), registries).stack();
		helper.assertTrue(wrong.isEmpty(), "con un núcleo que no es de eco no sale nada");
		helper.assertTrue(Relic.wrongCore(List.of(Assembler.createPart(PartType.NUCLEO, ForgeMaterial.AMATISTA),
			Assembler.createPart(PartType.ENGASTE, ForgeMaterial.ORO), Assembler.createPart(PartType.CADENA, ForgeMaterial.HIERRO))) == Relic.MEDALLON_DEL_OLVIDO,
			"y la mesa dice qué núcleo pide");
		helper.assertTrue(Assembler.evaluate(List.of(Assembler.createPart(PartType.NUCLEO, ForgeMaterial.ECO),
			Assembler.createPart(PartType.ENGASTE, ForgeMaterial.ORO)), registries).missing() != null, "dos de tres piezas dicen qué falta");

		// At the table, on the star, with a press: over lava too, where a set that makes nothing would be melted.
		net.minecraft.server.level.ServerPlayer smith = CombatGameTests.player(helper, new BlockPos(3, 2, 3));
		smith.getInventory().clearContent();
		BlockPos table = new BlockPos(1, 1, 1);
		helper.setBlock(table.below(), net.minecraft.world.level.block.Blocks.LAVA);
		helper.setBlock(table, Station.FORJA.block());
		ForgeMenu forge = new ForgeMenu(Station.FORJA, 0, smith.getInventory(),
			net.minecraft.world.inventory.ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
		forge.getSlot(ForgeMenu.STAR_FIRST).set(Assembler.createPart(PartType.NUCLEO, ForgeMaterial.ECO));
		forge.getSlot(ForgeMenu.STAR_FIRST + 1).set(Assembler.createPart(PartType.ENGASTE, ForgeMaterial.ORO));
		forge.getSlot(ForgeMenu.STAR_FIRST + 2).set(Assembler.createPart(PartType.CADENA, ForgeMaterial.HIERRO));
		helper.assertTrue(forge.action() == ForgeMenu.Action.FORGE, "la estrella lo forja, no lo funde: " + forge.action());
		helper.assertTrue(forge.forgePreview().is(ModItems.MEDALLON_DEL_OLVIDO), "y enseña el medallón");
		helper.assertTrue(forge.clickMenuButton(smith, ForgeMenu.BUTTON_PRESS + 1), "un golpe de martillo");
		helper.assertTrue(forge.getSlot(ForgeMenu.CENTER_SLOT).getItem().is(ModItems.MEDALLON_DEL_OLVIDO), "el medallón queda en el centro");
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(forge.getSlot(ForgeMenu.STAR_FIRST + i).getItem().isEmpty(), "y las piezas se gastan");
		}
		helper.succeed();
	}

	/** The lantern is magic like the staff: it asks for mana, its own price, and the Curandero's class makes it cheaper. */
	@GameTest
	public void theLanternCostsMana(GameTestHelper helper) {
		ItemStack lantern = lantern(helper);
		helper.assertTrue(Mana.usesMana(lantern), "el farol usa maná");
		helper.assertTrue(Spellcasting.casts(ForgeType.FAROL), "el farol lanza");
		helper.assertTrue(Math.abs(Spellcasting.tapCost(lantern, ForgeType.FAROL) - Healing.MANA_COST) < 1.0E-4F,
			"un toque cuesta " + Healing.MANA_COST + ", costó " + Spellcasting.tapCost(lantern, ForgeType.FAROL));
		helper.assertTrue(Spellcasting.manaCost(lantern, ForgeType.FAROL, 1.0F) > Spellcasting.tapCost(lantern, ForgeType.FAROL),
			"cargado cuesta más");
		helper.succeed();
	}

	/** A full charge lets out the ring: the reader's own hurt wolf beside them is mended, a monster is not. */
	@GameTest
	public void theLanternRingMendsAFriend(GameTestHelper helper) {
		CombatGameTests.TestPlayer reader = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		var wolf = helper.spawn(net.minecraft.world.entity.EntityTypes.WOLF, new BlockPos(2, 1, 1));
		wolf.setNoAi(true);
		wolf.tame(reader);
		wolf.setHealth(2.0F);
		var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 1, 2));
		zombie.setNoAi(true);
		zombie.setHealth(5.0F);
		Healing.lantern(helper.getLevel(), reader, lantern(helper), 1.0F);
		helper.assertTrue(wolf.getHealth() > 2.0F, "su lobo debería curarse, tiene " + wolf.getHealth());
		helper.assertTrue(zombie.getHealth() <= 5.0F, "un monstruo no se cura, tiene " + zombie.getHealth());
		helper.succeed();
	}

	/** Andy: a Curandero's staff or tome mends the Curandero too, a third of what it mended the ally. */
	@GameTest
	public void theHealerGetsAThirdOfWhatAnAllyGets(GameTestHelper helper) {
		CombatGameTests.TestPlayer healer = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ClassProgress.choose(healer, PlayerClass.CURANDERO);
		var wolf = helper.spawn(net.minecraft.world.entity.EntityTypes.WOLF, new BlockPos(2, 1, 1));
		wolf.setNoAi(true);
		wolf.tame(healer);
		wolf.setHealth(2.0F);
		healer.setHealth(10.0F);
		// A bite of 30 is a mend of 3.
		Healing.spellHeal(helper.getLevel(), healer, wolf, 30.0F);
		float toWolf = wolf.getHealth() - 2.0F;
		float toSelf = healer.getHealth() - 10.0F;
		helper.assertTrue(toWolf > 0.0F, "el lobo debería curarse");
		helper.assertTrue(Math.abs(toSelf - toWolf / 3.0F) < 1.0E-3F, "el curandero se cura un tercio: " + toSelf + " de " + toWolf);
		ClassProgress.clear(healer);
		helper.succeed();
	}

	/** Mana's bar grows with the Mago's class. */
	@GameTest
	public void aMageHasADeeperManaBar(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		float bare = Mana.maxOf(player);
		helper.assertTrue(Math.abs(bare - CombatConfig.get().manaMax) < 1.0E-4F, "sin clase ni mejoras, el maná de siempre");
		ClassProgress.choose(player, PlayerClass.MAGO);
		helper.assertTrue(Mana.maxOf(player) > bare, "el mago tiene más maná: " + Mana.maxOf(player));
		ClassProgress.clear(player);
		helper.succeed();
	}
}
