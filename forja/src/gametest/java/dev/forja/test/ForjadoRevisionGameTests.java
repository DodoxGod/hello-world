package dev.forja.test;

import java.util.List;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Perk;
import dev.forja.forge.Quality;
import dev.forja.material.ForgeMaterial;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
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
}
