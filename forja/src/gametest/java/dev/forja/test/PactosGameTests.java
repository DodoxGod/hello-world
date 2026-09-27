package dev.forja.test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Potential;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Pacts;
import dev.forja.upgrade.Synergy;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.UpgradeRecipes;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pactos y sinergias (Andy, 2026-09-26): "mejor pon pactos 2 por objeto y 3 sinergias por objeto", and a
 * pact is opened once, per smith, with something rare.
 */
public class PactosGameTests {
	/** Every pact asks for its own rare thing, and nothing that is not a pact asks for anything. */
	@GameTest
	public void everyPactHasItsOffering(GameTestHelper helper) {
		Set<Item> seen = new HashSet<>();
		for (Upgrade upgrade : Upgrade.values()) {
			Item offering = Pacts.offering(upgrade);
			if (upgrade.isPact()) {
				helper.assertTrue(offering != null && seen.add(offering), upgrade + " necesita una ofrenda propia, tiene " + offering);
			} else {
				helper.assertTrue(offering == null, upgrade + " no es un pacto y pide " + offering);
			}
		}
		helper.assertTrue(Pacts.unlocked(null, Upgrade.PACTO_DE_SED), "sin herrero (botín, comandos) no hay nada que abrir");
		helper.assertTrue(Pacts.unlocked(null, Upgrade.FILO), "y una mejora corriente nunca está sellada");
		helper.succeed();
	}

	/** Two pacts on a piece and no third, while the two it has can still be raised. */
	@GameTest
	public void twoPactsAtMost(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		Upgrades two = Upgrades.EMPTY.with(Upgrade.PACTO_DE_VIDRIO, 100).with(Upgrade.PACTO_DE_SOMBRA, 50);
		helper.assertTrue(Pacts.count(two) == 2, "dos pactos cuentan dos");
		helper.assertTrue(!Pacts.fits(two, Upgrade.PACTO_DE_SED), "un tercero no cabe");
		helper.assertTrue(Pacts.fits(two, Upgrade.PACTO_DE_SOMBRA), "subir uno que ya lleva sí");
		helper.assertTrue(Pacts.fits(two, Upgrade.FILO), "y lo que no es pacto no cuenta");

		// Through the forge's own matching: a sword carrying two pacts (the second forced on, since a sword
		// has only two of its own) is refused the third, and says why.
		ItemStack sword = Assembler.create(ForgeType.ESPADA, Assembler.defaultMaterials(ForgeType.ESPADA), registries);
		sword = UpgradeRecipes.upgraded(sword, ForgeType.ESPADA, Upgrade.PACTO_DE_VIDRIO, 100, registries);
		sword = UpgradeRecipes.upgraded(sword, ForgeType.ESPADA, Upgrade.PACTO_DE_SOMBRA, 50, registries);
		UpgradeRecipes.Application third = UpgradeRecipes.apply(sword,
			List.of(new ItemStack(Items.ROTTEN_FLESH, 8), new ItemStack(Items.REDSTONE, 8), ItemStack.EMPTY), registries);
		helper.assertTrue(third != null && third.upgrade() == Upgrade.PACTO_DE_SED && third.result().isEmpty()
			&& third.limit() == Potential.Limit.PACTS, "el tercer pacto debe quedarse fuera por el tope: " + third);

		// With one pact the second goes on as always.
		ItemStack one = Assembler.create(ForgeType.ESPADA, Assembler.defaultMaterials(ForgeType.ESPADA), registries);
		one = UpgradeRecipes.upgraded(one, ForgeType.ESPADA, Upgrade.PACTO_DE_VIDRIO, 100, registries);
		UpgradeRecipes.Application second = UpgradeRecipes.apply(one,
			List.of(new ItemStack(Items.ROTTEN_FLESH, 8), new ItemStack(Items.REDSTONE, 8), ItemStack.EMPTY), registries);
		helper.assertTrue(second != null && !second.result().isEmpty()
			&& second.result().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percent(Upgrade.PACTO_DE_SED) > 0,
			"el segundo pacto sí entra: " + second);
		helper.succeed();
	}

	/** Four synergies reached, three awake: the strongest, and the fourth wakes when it outgrows one. */
	@GameTest
	public void threeSynergiesAtMost(GameTestHelper helper) {
		Upgrades four = Upgrades.EMPTY
			.with(Upgrade.VAMPIRISMO, 100).with(Upgrade.EJECUCION, 100)     // Sed de sangre, 200
			.with(Upgrade.BOTIN, 90).with(Upgrade.DECAPITADOR, 90)          // Cazarrecompensas, 180
			.with(Upgrade.ESCARCHA, 60).with(Upgrade.TORMENTA, 60)          // Tormenta helada, 120
			.with(Upgrade.ONDA_DE_CHOQUE, 50);                              // Cadena de rayos, 60 + 50
		helper.assertTrue(Synergy.CADENA_DE_RAYOS.reached(four), "la cuarta llega al umbral");
		helper.assertTrue(Synergy.SED_DE_SANGRE.active(four) && Synergy.CAZARRECOMPENSAS.active(four) && Synergy.TORMENTA_HELADA.active(four),
			"las tres más fuertes despiertan");
		helper.assertTrue(!Synergy.CADENA_DE_RAYOS.active(four), "y la cuarta duerme");
		int awake = 0;
		for (Synergy synergy : Synergy.values()) {
			awake += synergy.active(four) ? 1 : 0;
		}
		helper.assertTrue(awake == Synergy.MOST, "nunca más de " + Synergy.MOST + " despiertas, hay " + awake);

		Upgrades grown = four.with(Upgrade.ONDA_DE_CHOQUE, 70);         // Cadena de rayos, 130 > 120
		helper.assertTrue(Synergy.CADENA_DE_RAYOS.active(grown) && !Synergy.TORMENTA_HELADA.active(grown),
			"al superar a una la cuarta despierta y la más débil se duerme");
		helper.succeed();
	}
}
