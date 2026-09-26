package dev.forja;

import dev.forja.command.ForjaCommand;
import dev.forja.registry.ModBlocks;
import dev.forja.world.ForjaLoot;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEntities;
import dev.forja.registry.ModItems;
import dev.forja.registry.ModMenus;
import dev.forja.upgrade.CombatUpgrades;
import dev.forja.upgrade.FieldUpgrades;
import dev.forja.upgrade.LootUpgrades;
import dev.forja.upgrade.MiningUpgrades;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/**
 * Forja: tools, weapons and armor assembled from interchangeable parts at a forge table, and
 * improved there with upgrades that replace enchantments.
 */
public final class Forja implements ModInitializer {
	public static final String MOD_ID = "forja";

	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ForjaConfig.load();
		ModComponents.init();
		ModBlocks.init();
		ModItems.init();
		ModMenus.init();
		ModEntities.init();
		dev.forja.block.entity.ModBlockEntities.init();
		dev.forja.registry.ModEffects.init();
		dev.forja.registry.ModParticles.init();
		dev.forja.world.EventSky.register();
		dev.forja.world.RuinMood.register();
		dev.forja.world.RustWatch.register();
		dev.forja.world.SlagPools.register();
		dev.forja.magic.Spellcasting.register();
		dev.forja.world.SlagWatch.register();
		dev.forja.world.ConstructWatch.register();
		dev.forja.world.FoundryWatch.register();
		dev.forja.world.CoreWatch.register();
		dev.forja.world.OilPools.register();
		dev.forja.registry.ModVillagers.init();

		MiningUpgrades.register();
		LootUpgrades.register();
		CombatUpgrades.register();
		FieldUpgrades.register();
		dev.forja.forge.Flight.register();
		dev.forja.upgrade.Frenzy.register();
		dev.forja.forge.Temple.register();
		dev.forja.forge.SmithLevel.register();
		dev.forja.forge.Techniques.register();
		dev.forja.forge.SmithRecord.register();
		ForjaCommand.register();
		ForjaLoot.register();
		dev.forja.world.ForjaVillages.register();
		dev.forja.world.Elites.register();
		dev.forja.world.Commissions.register();
		dev.forja.world.WorldEvents.register();
		dev.forja.world.ForgeRaiders.register();
		dev.forja.world.HollowWatch.register();
		dev.forja.world.WispWatch.register();
		dev.forja.world.WispCatch.register();
		dev.forja.upgrade.TraitEffects.register();
		// After every Forja damage hook, so trait dodges and shield reactions still come first.
		dev.forja.combat.CombatOverhaul.register();
		dev.forja.difficulty.DifficultyRules.register();
		dev.forja.ai.MobAi.register();

		// Forged gear is improved with upgrades at the forge table, never with the enchanting table or books.
		EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment, stack, context) ->
			stack.has(ModComponents.PARTS) ? TriState.FALSE : TriState.DEFAULT);
	}
}
