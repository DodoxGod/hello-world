package dev.forja.test;

import java.util.ArrayList;
import java.util.List;

import dev.forja.ai.MobDefense;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.Weight;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Peso (Andy, 2026-09-26): every weapon weighs something; a player swings a heavier one to full strength
 * later, a monster warns longer before landing it and waits longer before the next; armour adds to it by
 * how much it slows, and a set that quickens its wearer takes some off.
 */
public class PesoGameTests {
	private static List<ForgeMaterial> withHead(ForgeType type, ForgeMaterial head) {
		List<ForgeMaterial> materials = new ArrayList<>(Assembler.defaultMaterials(type));
		materials.set(0, head);
		return materials;
	}

	/** The weights themselves: iron at the base, wood lighter, netherite heavier, a dagger far lighter than a hammer. */
	@GameTest
	public void weaponsWeighWhatTheyShould(GameTestHelper helper) {
		float iron = Weight.kg(ForgeType.ESPADA, withHead(ForgeType.ESPADA, ForgeMaterial.HIERRO));
		float wood = Weight.kg(ForgeType.ESPADA, withHead(ForgeType.ESPADA, ForgeMaterial.MADERA));
		float netherite = Weight.kg(ForgeType.ESPADA, withHead(ForgeType.ESPADA, ForgeMaterial.NETHERITA));
		helper.assertTrue(Math.abs(iron - 1.3F) < 0.01F, "una espada de hierro pesa 1.3 kg: " + iron);
		helper.assertTrue(wood < iron && iron < netherite, "madera " + wood + " < hierro " + iron + " < netherita " + netherite);
		float dagger = Weight.kg(ForgeType.DAGA, withHead(ForgeType.DAGA, ForgeMaterial.HIERRO));
		float hammer = Weight.kg(ForgeType.MARTILLO, withHead(ForgeType.MARTILLO, ForgeMaterial.HIERRO));
		helper.assertTrue(dagger < 1.0F && hammer > 3.0F, "daga " + dagger + ", martillo " + hammer);
		helper.assertTrue(Math.abs(Weight.kg(new ItemStack(Items.IRON_SWORD)) - iron) < 0.01F, "la espada de hierro vanilla pesa como la forjada");
		helper.assertTrue(Weight.kg(new ItemStack(Items.NETHERITE_AXE)) > Weight.kg(new ItemStack(Items.WOODEN_AXE)), "hacha de netherita más pesada que la de madera");
		helper.assertTrue(Weight.kg(ItemStack.EMPTY) == 0.0F, "la mano vacía no pesa");
		helper.succeed();
	}

	/** A player's swing: the iron one as it always was, lighter sooner, heavier later; and the weight shows. */
	@GameTest
	public void heavierSwingsLater(GameTestHelper helper) {
		ForgeStats.Sheet iron = ForgeStats.sheet(ForgeType.ESPADA, withHead(ForgeType.ESPADA, ForgeMaterial.HIERRO), Upgrades.EMPTY);
		ForgeStats.Sheet wood = ForgeStats.sheet(ForgeType.ESPADA, withHead(ForgeType.ESPADA, ForgeMaterial.MADERA), Upgrades.EMPTY);
		ForgeStats.Sheet netherite = ForgeStats.sheet(ForgeType.ESPADA, withHead(ForgeType.ESPADA, ForgeMaterial.NETHERITA), Upgrades.EMPTY);
		float base = ForgeType.ESPADA.attackSpeed + ForgeMaterial.MADERA.handleAttackSpeed;
		helper.assertTrue(Math.abs(iron.attackSpeed - base) < 0.001F, "la de hierro va como antes: " + iron.attackSpeed + " / " + base);
		helper.assertTrue(wood.attackSpeed > iron.attackSpeed, "la de madera va más rápida");
		helper.assertTrue(netherite.attackSpeed < iron.attackSpeed, "la de netherita va más lenta");
		helper.assertTrue(netherite.weight > iron.weight && iron.weight > 0.0F, "y el peso se ve en la hoja");
		helper.assertTrue(iron.lines().stream().anyMatch(line -> line.stat() == ForgeStats.Stat.PESO), "la hoja muestra el peso");
		// the swing that is seen: a hammer's is longer than a dagger's
		ItemStack hammer = Assembler.create(ForgeType.MARTILLO, Assembler.defaultMaterials(ForgeType.MARTILLO));
		ItemStack dagger = Assembler.create(ForgeType.DAGA, Assembler.defaultMaterials(ForgeType.DAGA));
		var hammerSwing = hammer.get(net.minecraft.core.component.DataComponents.SWING_ANIMATION);
		var daggerSwing = dagger.get(net.minecraft.core.component.DataComponents.SWING_ANIMATION);
		helper.assertTrue(hammerSwing != null && daggerSwing != null && hammerSwing.duration() > daggerSwing.duration(),
			"el martillo se ve más lento que la daga");
		helper.succeed();
	}

	/** A monster: bare-handed it warns as always; with a heavy weapon and heavy plate it warns longer and waits longer. */
	@GameTest
	public void heavyMonstersWarnLonger(GameTestHelper helper) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		CombatGameTests.emptyHands(zombie);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		int bare = MobDefense.windup(zombie);
		helper.assertTrue(bare == Math.max(1, CombatConfig.get().windupTicks), "sin nada, el aviso de siempre: " + bare);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, Assembler.create(ForgeType.MARTILLO, withHead(ForgeType.MARTILLO, ForgeMaterial.NETHERITA)));
		int hammer = MobDefense.windup(zombie);
		zombie.setItemSlot(EquipmentSlot.CHEST, Assembler.create(ForgeType.PECHERA, List.of(ForgeMaterial.NETHERITA, ForgeMaterial.CUERO)));
		int plated = MobDefense.windup(zombie);
		helper.assertTrue(bare < hammer && hammer < plated, "avisos: sin nada " + bare + ", con martillo " + hammer + ", y con peto " + plated);
		helper.assertTrue(plated - bare <= Weight.WINDUP_MAX_EXTRA, "con un tope");
		helper.assertTrue(Weight.interval(zombie, 20) > 20, "y espera más entre golpes: " + Weight.interval(zombie, 20));
		CombatGameTests.emptyHands(zombie);
		zombie.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		helper.assertTrue(Weight.interval(zombie, 20) == 20, "sin nada, la espera de siempre");
		helper.succeed();
	}

	/** A player: heavy plate slows the arm, a set that quickens the walk quickens it. */
	@GameTest
	public void armourMovesTheSwing(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		helper.assertTrue(Weight.armourSwing(player) == 0.0F, "sin armadura no cambia nada");
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		ForgeType[] pieces = {ForgeType.CASCO, ForgeType.PECHERA, ForgeType.GREBAS, ForgeType.BOTAS};
		for (int i = 0; i < 4; i++) {
			player.setItemSlot(slots[i], Assembler.create(pieces[i], List.of(ForgeMaterial.NETHERITA, ForgeMaterial.CUERO)));
		}
		float heavy = Weight.armourSwing(player);
		helper.assertTrue(heavy < 0.0F, "la netherita frena el brazo: " + heavy);
		for (int i = 0; i < 4; i++) {
			player.setItemSlot(slots[i], Assembler.create(pieces[i], List.of(ForgeMaterial.MADERA, ForgeMaterial.CUERO)));
		}
		dev.forja.upgrade.ArmorSets.update(player);
		float light = Weight.armourSwing(player);
		helper.assertTrue(light > heavy, "un conjunto de madera (que da velocidad) pesa menos: " + light + " > " + heavy);
		helper.succeed();
	}
}
