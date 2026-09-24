package dev.forja.world;

import java.util.List;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Forja in the world, part two: zombies, skeletons and vindicators sometimes spawn wearing forged
 * armor or holding forged weapons instead of their vanilla gear, in the same materials. Enchanted
 * pieces are left alone. Skeletons keep vanilla bows, which their AI needs.
 */
public final class ForjaMobs {
	public static final float CHANCE = 0.35F;
	private static final ForgeMaterial[] LININGS = {ForgeMaterial.CUERO, ForgeMaterial.CUERO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO, ForgeMaterial.HUESO};
	private static final ForgeMaterial[] HANDLES = {ForgeMaterial.MADERA, ForgeMaterial.MADERA, ForgeMaterial.HUESO, ForgeMaterial.CUERO};
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** How likely each armour slot is to be filled, on its own. */
	public static final float ARMOUR_CHANCE = 0.15F;

	/** And what each piece it is already wearing takes off its chance of also carrying a weapon. */
	public static final float WEAPON_PENALTY = 0.15F;

	private ForjaMobs() {
	}

	/**
	 * Kits out one of the mod's own monsters.
	 *
	 * <p>Andy's rule, and it is a good one: each of the four armour slots fills on its own at fifteen
	 * per cent, and <b>every piece it ends up wearing takes fifteen per cent off its chance of also
	 * having a weapon</b>. So a bare one always has something to hit you with and a fully armoured one
	 * is only about two in five likely to — which means the dangerous-looking ones are often the ones
	 * you can walk away from, and the shabby one in the corner is always armed.
	 *
	 * <p>And only weapons go in the hands. That was a real bug: the gear was picked from everything the
	 * mod makes, so a raider could spawn holding a set of wings or a horse's barding, neither of which
	 * it could use and neither of which did anything for it.
	 */
	public static void arm(Mob mob, RandomSource random, ForgeMaterial plate) {
		int worn = 0;
		for (EquipmentSlot slot : ARMOR) {
			if (random.nextFloat() >= ARMOUR_CHANCE) {
				continue;
			}
			ForgeType type = switch (slot) {
				case HEAD -> ForgeType.CASCO;
				case CHEST -> ForgeType.PECHERA;
				case LEGS -> ForgeType.GREBAS;
				default -> ForgeType.BOTAS;
			};
			mob.setItemSlot(slot, Assembler.create(type,
				List.of(plate, LININGS[random.nextInt(LININGS.length)]), mob.registryAccess()));
			worn++;
		}
		if (random.nextFloat() >= weaponChance(worn)) {
			return;
		}
		ForgeType weapon = HAND_WEAPONS[random.nextInt(HAND_WEAPONS.length)];
		List<ForgeMaterial> materials = new java.util.ArrayList<>(Assembler.defaultMaterials(weapon));
		materials.set(0, plate);
		for (int i = 1; i < materials.size(); i++) {
			if (weapon.slots.get(i).role == dev.forja.part.PartType.Role.HANDLE) {
				materials.set(i, HANDLES[random.nextInt(HANDLES.length)]);
			}
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, Assembler.create(weapon, materials, mob.registryAccess()));
	}

	/** One minus fifteen per cent per piece already on: four pieces leaves forty. */
	public static float weaponChance(int armourPieces) {
		return Math.max(0.0F, 1.0F - WEAPON_PENALTY * armourPieces);
	}

	/** The only things that go in a hand. */
	private static final ForgeType[] HAND_WEAPONS = {
		ForgeType.ESPADA, ForgeType.ESPADA, ForgeType.HACHA, ForgeType.LANZA,
		ForgeType.DAGA, ForgeType.MAZO, ForgeType.MARTILLO,
	};

	public static void forgeEquipment(Mob mob, RandomSource random, float chance) {
		if (!(mob instanceof Zombie) && !(mob instanceof AbstractSkeleton) && !(mob instanceof Vindicator)) {
			return;
		}
		// One in a hundred is not a monster with gear but an elite carrying a legend. They keep away from
		// the first days and from spawn, so nobody meets one before they have anything to fight it with.
		if (Elites.canAppear(mob) && random.nextFloat() < dev.forja.ForjaConfig.get().elites) {
			Elites.makeElite(mob, random);
			return;
		}
		for (EquipmentSlot slot : ARMOR) {
			ItemStack worn = mob.getItemBySlot(slot);
			ForgeMaterial plate = armorMaterial(worn.getItem());
			if (plate != null && !worn.isEnchanted() && random.nextFloat() < chance) {
				ForgeType type = switch (slot) {
					case HEAD -> ForgeType.CASCO;
					case CHEST -> ForgeType.PECHERA;
					case LEGS -> ForgeType.GREBAS;
					default -> ForgeType.BOTAS;
				};
				mob.setItemSlot(slot, Assembler.create(type, List.of(plate, LININGS[random.nextInt(LININGS.length)]), mob.registryAccess()));
			}
		}
		ItemStack held = mob.getMainHandItem();
		ForgeType weapon = weaponType(held.getItem());
		if (weapon != null && !held.isEnchanted() && random.nextFloat() < chance) {
			List<ForgeMaterial> materials = new java.util.ArrayList<>(Assembler.defaultMaterials(weapon));
			materials.set(0, ForgeMaterial.HIERRO);
			for (int i = 1; i < materials.size(); i++) {
				if (weapon.slots.get(i).role == dev.forja.part.PartType.Role.HANDLE) {
					materials.set(i, HANDLES[random.nextInt(HANDLES.length)]);
				}
			}
			mob.setItemSlot(EquipmentSlot.MAINHAND, Assembler.create(weapon, materials, mob.registryAccess()));
			return;
		}
		// Nothing of vanilla's to upgrade: this one gets a kit of its own, by Andy's rule.
		if (weapon == null && bare(mob) && random.nextFloat() < chance) {
			arm(mob, random, ForgeMaterial.HIERRO);
		}
	}

	/** Whether the mob is carrying nothing worth keeping. */
	private static boolean bare(Mob mob) {
		if (!mob.getMainHandItem().isEmpty()) {
			return false;
		}
		for (EquipmentSlot slot : ARMOR) {
			if (!mob.getItemBySlot(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static @Nullable ForgeMaterial armorMaterial(Item item) {
		if (item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE || item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS) {
			return ForgeMaterial.CUERO;
		} else if (item == Items.COPPER_HELMET || item == Items.COPPER_CHESTPLATE || item == Items.COPPER_LEGGINGS || item == Items.COPPER_BOOTS) {
			return ForgeMaterial.COBRE;
		} else if (item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE || item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS) {
			return ForgeMaterial.ORO;
		} else if (item == Items.CHAINMAIL_HELMET || item == Items.CHAINMAIL_CHESTPLATE || item == Items.CHAINMAIL_LEGGINGS || item == Items.CHAINMAIL_BOOTS
			|| item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS) {
			return ForgeMaterial.HIERRO;
		} else if (item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS) {
			return ForgeMaterial.DIAMANTE;
		}
		return null;
	}

	private static @Nullable ForgeType weaponType(Item item) {
		if (item == Items.IRON_SWORD) {
			return ForgeType.ESPADA;
		} else if (item == Items.IRON_SPEAR) {
			return ForgeType.LANZA;
		} else if (item == Items.IRON_SHOVEL) {
			return ForgeType.PALA;
		} else if (item == Items.IRON_AXE) {
			return ForgeType.HACHA;
		}
		return null;
	}
}
