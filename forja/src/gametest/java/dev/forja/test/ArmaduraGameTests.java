package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.combat.ArmorCalculator;
import dev.forja.combat.AttackProfile;
import dev.forja.combat.DamageKind;
import dev.forja.combat.HitZone;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Mastery;
import dev.forja.forge.Perk;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.ArmorSets;
import dev.forja.upgrade.HiddenEnchantments;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Armaduras (Andy, 2026-09-29): "diamond with Protection IV, and the mod's armour even more, makes crowds
 * harmless". Every forged set, of every material, is put on a player at the most the forge's upgrades give it
 * (Protección IV on every piece, Fortaleza on the chestplate, the full-set bonus, a leather lining, which is
 * the one that soaks a blunt blow best) and hit by a zombie's fist and a vindicator's axe, 6 and 10 raw
 * damage, penetration 0. The hit goes through the mod's armour ({@link ArmorCalculator#apply}) and then
 * through vanilla's enchantment protection, the same two steps a real blow takes. Next to them, vanilla
 * diamond and netherite with Protection IV on every piece, through the same two steps.
 *
 * <p>The rule: no forged set stops more than netherite with Protection IV plus five points, wherever the
 * blow lands. The table goes to the log with the prefix {@code [forja-test] armadura:}; docs/EQUILIBRIO.md
 * has the reading of it.
 */
public class ArmaduraGameTests {
	/** How far over netherite with Protection IV a forged set may go, in points of reduction. */
	private static final double MAX_OVER_NETHERITE = 5.0;
	/**
	 * How far forged diamond may sit from vanilla diamond with Protection IV: the same five points. It sits about
	 * three over against a fist (the leather lining, the set bonus and Fortaleza) and one over against an edge.
	 */
	private static final double DIAMOND_BAND = 5.0;
	private static final double[] BLOWS = {6.0, 10.0};
	private static final DamageKind[] KINDS = {DamageKind.BLUNT, DamageKind.SLASH};
	private static final HitZone[] ZONES = {HitZone.HEAD, HitZone.TORSO, HitZone.LEGS, HitZone.FEET};
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final ForgeType[] PIECES = {ForgeType.CASCO, ForgeType.PECHERA, ForgeType.GREBAS, ForgeType.BOTAS};

	/** What one set lets through: [kind][zone][blow], in health points after armour and enchantments. */
	private record Measured(String name, double[][][] taken, float protection) {
		double reduction(int kind, int zone) {
			return 100.0 * (1.0 - this.taken[kind][zone][0] / BLOWS[0]);
		}
	}

	@GameTest
	public void forgedArmourStaysNearNetheriteWithProtection(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder<Enchantment> protection = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(zombie != null, "no se pudo crear el zombi que pega");
		DamageSource blow = level.damageSources().mobAttack(zombie);

		Measured diamond = measure(helper, blow, "diamante vanilla P4",
			vanillaSet(protection, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS));
		Measured netherite = measure(helper, blow, "netherita vanilla P4",
			vanillaSet(protection, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS));
		helper.assertTrue(Math.abs(netherite.protection() - 16.0F) < 0.01F,
			"la Protección IV vanilla en las cuatro piezas debería dar 16 de protección, da " + netherite.protection());

		List<Measured> upgraded = new ArrayList<>();
		List<Measured> absolute = new ArrayList<>();
		for (ForgeMaterial material : ForgeMaterial.values()) {
			upgraded.add(measure(helper, blow, material.getSerializedName(), forgedSet(level, material, false)));
			absolute.add(measure(helper, blow, material.getSerializedName(), forgedSet(level, material, true)));
		}

		print("reducción con Protección IV; golpe en el torso (zombi: contundente, vindicador: cortante), penetración 0");
		print(String.format(Locale.ROOT, "%-22s | %-27s | %-27s | %-27s", "conjunto",
			"contundente % (6 / 10)", "cortante % (6 / 10)", "sobre netherita P4 (c / c)"));
		row(diamond, netherite);
		row(netherite, netherite);
		for (Measured set : upgraded) {
			row(set, netherite);
		}
		print("máximo absoluto (además don Baluarte en cada pieza, forja perfecta y obra maestra):");
		for (Measured set : absolute) {
			row(set, netherite);
		}

		// The rule, in every zone and for both kinds of blow.
		Measured worst = null;
		double worstOver = -100.0;
		String where = "";
		for (Measured set : upgraded) {
			for (int k = 0; k < KINDS.length; k++) {
				for (int z = 0; z < ZONES.length; z++) {
					double over = set.reduction(k, z) - netherite.reduction(k, z);
					if (over > worstOver) {
						worstOver = over;
						worst = set;
						where = ZONES[z] + "/" + KINDS[k];
					}
				}
			}
		}
		print(String.format(Locale.ROOT, "el que más se pasa de la netherita P4: %s, %+.2f puntos (%s)", worst.name(), worstOver, where));
		// The absolute ceiling is only reported: a gift and a masterpiece are a piece's whole life, not an upgrade.
		double absoluteOver = -100.0;
		String absoluteWhere = "";
		for (Measured set : absolute) {
			for (int k = 0; k < KINDS.length; k++) {
				for (int z = 0; z < ZONES.length; z++) {
					double over = set.reduction(k, z) - netherite.reduction(k, z);
					if (over > absoluteOver) {
						absoluteOver = over;
						absoluteWhere = set.name() + ", " + ZONES[z] + "/" + KINDS[k];
					}
				}
			}
		}
		print(String.format(Locale.ROOT, "máximo absoluto sobre la netherita P4 (solo informa): %+.2f puntos (%s)", absoluteOver, absoluteWhere));
		helper.assertTrue(worstOver <= MAX_OVER_NETHERITE, String.format(Locale.ROOT,
			"el conjunto de %s para %.2f puntos más que la netherita con Protección IV (%s); el tope es %.1f",
			worst.name(), worstOver, where, MAX_OVER_NETHERITE));

		// Forged diamond sits next to vanilla diamond with Protection IV, not a tier above it.
		Measured forgedDiamond = upgraded.get(ForgeMaterial.DIAMANTE.ordinal());
		int torso = 1;
		for (int k = 0; k < KINDS.length; k++) {
			double gap = forgedDiamond.reduction(k, torso) - diamond.reduction(k, torso);
			helper.assertTrue(Math.abs(gap) <= DIAMOND_BAND, String.format(Locale.ROOT,
				"el diamante forjado se aleja %.2f puntos del diamante vanilla con Protección IV (%s); el margen es %.1f",
				gap, KINDS[k], DIAMOND_BAND));
		}
		helper.succeed();
	}

	private static ItemStack[] vanillaSet(Holder<Enchantment> protection, Item... items) {
		ItemStack[] set = new ItemStack[items.length];
		for (int i = 0; i < items.length; i++) {
			set[i] = new ItemStack(items[i]);
			set[i].enchant(protection, 4);
		}
		return set;
	}

	/**
	 * Baluarte's price (Andy, 2026-09-29: "algo malo debe de tener"): the same chestplate with the gift has one
	 * more armour point and counts 30 % heavier (slower walk, swing and stamina).
	 */
	@GameTest
	public void baluarteIsHeavier(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack plain = Assembler.create(ForgeType.PECHERA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.CUERO), level.registryAccess());
		ItemStack gifted = plain.copy();
		gifted.set(ModComponents.DON, Perk.BALUARTE.id());
		Assembler.rewrite(plain, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		Assembler.rewrite(gifted, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		double light = ArmorCalculator.profileOf(plain).weight();
		double heavy = ArmorCalculator.profileOf(gifted).weight();
		helper.assertTrue(light > 0.0 && Math.abs(heavy / light - (1.0 + Perk.BALUARTE_WEIGHT)) < 1.0E-6,
			"con Baluarte la pieza pesa un 30 % más: " + light + " -> " + heavy);
		helper.succeed();
	}

	/**
	 * A full set of one plate with a leather lining, with everything the forge's upgrades give it: Protección
	 * at 100 % on every piece and Vitalidad on the chestplate, which with it wakes Fortaleza (+1 armour). With
	 * {@code absolute}, also what only a long life gives a piece: Maestría 10, the Baluarte gift (+1 armour),
	 * a perfect press and the masterpiece mark.
	 */
	private static ItemStack[] forgedSet(ServerLevel level, ForgeMaterial plate, boolean absolute) {
		ItemStack[] set = new ItemStack[PIECES.length];
		for (int i = 0; i < PIECES.length; i++) {
			ItemStack piece = Assembler.create(PIECES[i], List.of(plate, ForgeMaterial.CUERO), level.registryAccess());
			Upgrades upgrades = Upgrades.EMPTY.with(Upgrade.PROTECCION, 100);
			if (PIECES[i] == ForgeType.PECHERA) {
				upgrades = upgrades.with(Upgrade.VITALIDAD, 100);
			}
			piece.set(ModComponents.UPGRADES, upgrades);
			if (absolute) {
				piece.set(ModComponents.MAESTRIA, Mastery.experienceFor(Mastery.MAX_LEVEL));
				piece.set(ModComponents.DON, Perk.BALUARTE.id());
				piece.set(ModComponents.PERFECTA, true);
				piece.set(ModComponents.OBRA_MAESTRA, true);
			}
			Assembler.rewrite(piece, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
			HiddenEnchantments.write(piece, level.registryAccess());
			set[i] = piece;
		}
		return set;
	}

	/** Puts a set on a fresh player (its equipment attributes applied by hand: a test player never ticks them) and hits it. */
	private static Measured measure(GameTestHelper helper, DamageSource blow, String name, ItemStack[] set) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		for (int i = 0; i < SLOTS.length; i++) {
			EquipmentSlot slot = SLOTS[i];
			player.setItemSlot(slot, set[i]);
			set[i].forEachModifier(slot, (attribute, modifier) -> {
				var instance = player.getAttribute(attribute);
				if (instance != null) {
					instance.addOrUpdateTransientModifier(modifier);
				}
			});
		}
		ArmorSets.update(player);
		float protection = EnchantmentHelper.getDamageProtection(helper.getLevel(), player, blow);
		double[][][] taken = new double[KINDS.length][ZONES.length][BLOWS.length];
		for (int k = 0; k < KINDS.length; k++) {
			for (int z = 0; z < ZONES.length; z++) {
				AttackProfile attack = new AttackProfile(KINDS[k], 0.0, ZONES[z], false);
				for (int b = 0; b < BLOWS.length; b++) {
					float afterArmour = ArmorCalculator.apply(player, (float) BLOWS[b], attack, 0.0);
					taken[k][z][b] = net.minecraft.world.damagesource.CombatRules.getDamageAfterMagicAbsorb(afterArmour, protection);
				}
			}
		}
		return new Measured(name, taken, protection);
	}

	private static void row(Measured set, Measured netherite) {
		int torso = 1;
		int blunt = 0;
		int slash = 1;
		print(String.format(Locale.ROOT, "%-22s | %5.1f %% (%.2f / %.2f)       | %5.1f %% (%.2f / %.2f)       | %+5.1f / %+5.1f",
			set.name(),
			set.reduction(blunt, torso), set.taken()[blunt][torso][0], set.taken()[blunt][torso][1],
			set.reduction(slash, torso), set.taken()[slash][torso][0], set.taken()[slash][torso][1],
			set.reduction(blunt, torso) - netherite.reduction(blunt, torso),
			set.reduction(slash, torso) - netherite.reduction(slash, torso)));
	}

	private static void print(String line) {
		System.out.println("[forja-test] armadura: " + line);
	}
}
