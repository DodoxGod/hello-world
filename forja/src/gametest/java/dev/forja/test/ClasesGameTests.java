package dev.forja.test;

import java.util.List;

import dev.forja.clase.ClassEffects;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.PlayerClass;
import dev.forja.clase.Talent;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.Stamina;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.magic.Healing;
import dev.forja.magic.Mana;
import dev.forja.magic.Spellcasting;
import dev.forja.material.ForgeMaterial;
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
		// A change of class keeps the level and gives every point back.
		ClassProgress.choose(player, PlayerClass.TANQUE);
		helper.assertTrue(ClassProgress.data(player).level() == 3, "cambiar de clase conserva el nivel");
		helper.assertTrue(ClassProgress.data(player).spent() == 0, "y devuelve los puntos");
		ClassProgress.clear(player);
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
