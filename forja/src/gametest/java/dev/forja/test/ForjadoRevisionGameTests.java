package dev.forja.test;

import java.util.List;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.ForgeStats;
import dev.forja.forge.Perk;
import dev.forja.forge.Quality;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public class ForjadoRevisionGameTests {
	@GameTest
	public void engravingKeepsPressQuality(GameTestHelper helper) {
		ItemStack sword = Assembler.create(ForgeType.ESPADA,
			List.of(ForgeMaterial.HIERRO, ForgeMaterial.HIERRO, ForgeMaterial.HIERRO));
		Quality.markPerfect(sword);
		Assembler.rewrite(sword, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		int before = sword.getMaxDamage();
		ItemStack engraved = Assembler.engrave(sword, Perk.FILO_ETERNO, helper.getLevel().registryAccess());
		helper.assertTrue(engraved.getMaxDamage() == before,
			"engraving must preserve the perfect press bonus: " + before + " -> " + engraved.getMaxDamage());
		helper.succeed();
	}

	@GameTest
	public void mixedStatsMatchWrittenAttributes(GameTestHelper helper) {
		checkMixed(helper, ForgeType.ESPADA, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.ECO, ForgeMaterial.MAGMACERO));
		checkMixed(helper, ForgeType.PICO, List.of(ForgeMaterial.ECO, ForgeMaterial.DAMASCO, ForgeMaterial.MAGMACERO));
		checkMixed(helper, ForgeType.PECHERA, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.ECO));
		helper.succeed();
	}

	private static void checkMixed(GameTestHelper helper, ForgeType type, List<ForgeMaterial> materials) {
		ItemStack stack = Assembler.create(type, materials);
		stack.set(ModComponents.UPGRADES, Upgrades.EMPTY.with(type == ForgeType.PECHERA ? Upgrade.PROTECCION : Upgrade.FILO, 40));
		Quality.markPerfect(stack);
		Assembler.rewrite(stack, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		ForgeStats.Sheet shown = ForgeStats.sheet(stack, stack.get(ModComponents.PARTS));
		helper.assertTrue(stack.getMaxDamage() == shown.durability, type + " durability: " + stack.getMaxDamage() + " != " + shown.durability);
		var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers();
		if (type == ForgeType.PECHERA) {
			double armor = modifiers.stream().filter(entry -> entry.attribute().equals(Attributes.ARMOR))
				.mapToDouble(entry -> entry.modifier().amount()).sum();
			helper.assertTrue(Math.abs(armor - shown.armor) < 0.001, type + " armor: " + armor + " != " + shown.armor);
		} else {
			double damage = modifiers.stream().filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
				.mapToDouble(entry -> entry.modifier().amount()).sum();
			double speed = modifiers.stream().filter(entry -> entry.attribute().equals(Attributes.ATTACK_SPEED))
				.mapToDouble(entry -> entry.modifier().amount()).sum();
			helper.assertTrue(Math.abs(damage - shown.attackDamage) < 0.001, type + " damage: " + damage + " != " + shown.attackDamage);
			helper.assertTrue(Math.abs(speed - shown.attackSpeed) < 0.001, type + " speed: " + speed + " != " + shown.attackSpeed);
		}
	}
}
