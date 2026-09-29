package dev.forja.world;

import java.util.List;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.item.ForgedItems;
import dev.forja.material.ForgeMaterial;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Forja in the world, part two: zombies, skeletons, vindicators and pillagers sometimes spawn wearing
 * forged armor or holding forged weapons instead of their vanilla gear, in the same materials. Enchanted
 * pieces are left alone. The archers keep a bow and the pillagers a crossbow, forged or not — the
 * mixins in dev.forja.mixin teach vanilla's ranged AI that ours is one — and a few skeletons and
 * zombies turn up with a staff or a tome instead (entity/ai/CasterGoal).
 */
public final class ForjaMobs {
	public static final float CHANCE = 0.35F;
	private static final ForgeMaterial[] LININGS = {ForgeMaterial.CUERO, ForgeMaterial.CUERO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO, ForgeMaterial.HUESO};
	private static final ForgeMaterial[] HANDLES = {ForgeMaterial.MADERA, ForgeMaterial.MADERA, ForgeMaterial.HUESO, ForgeMaterial.CUERO};
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** What the limbs of a monster's forged bow are: the wood a vanilla bow is, or the bone a skeleton has to hand. */
	private static final ForgeMaterial[] BOW_LIMBS = {ForgeMaterial.MADERA, ForgeMaterial.HUESO};
	/** The stones a monster's staff or tome is cut round, which are the colour and the bite of its magic. */
	private static final ForgeMaterial[] CORES = {ForgeMaterial.AMATISTA, ForgeMaterial.AMATISTA, ForgeMaterial.COBRE, ForgeMaterial.PRISMARINA};

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
		int worn = armour(mob, random, plate);
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

	/**
	 * The armour half of Andy's rule on its own: each of the four slots at fifteen per cent.
	 *
	 * <p>This is the whole kit of anything whose hands are already spoken for — an archer's bow, a
	 * pillager's crossbow, a caster's staff or tome. Before, a skeleton never got any of it: it always
	 * holds a bow, so it was never "bare", and it only ever wore forged plate if vanilla had dressed it
	 * first.
	 *
	 * @return how many pieces it put on
	 */
	public static int armour(Mob mob, RandomSource random, ForgeMaterial plate) {
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
		return worn;
	}

	/** One minus fifteen per cent per piece already on: four pieces leaves forty. */
	public static float weaponChance(int armourPieces) {
		return Math.max(0.0F, 1.0F - WEAPON_PENALTY * armourPieces);
	}

	/**
	 * What a mob shoots with, by what it is: a skeleton (and a stray, a bogged, a parched) a bow, a
	 * pillager a crossbow; null for everything that fights up close, the wither skeleton included.
	 */
	public static @Nullable ForgeType launcher(LivingEntity mob) {
		if (mob instanceof AbstractSkeleton && !(mob instanceof WitherSkeleton)) {
			return ForgeType.ARCO;
		}
		return mob instanceof CrossbowAttackMob ? ForgeType.BALLESTA : null;
	}

	/** Whether a stack is our bow or our crossbow standing in for vanilla's {@code vanilla}. */
	public static boolean forgedLauncher(ItemStack stack, Item vanilla) {
		return vanilla == Items.BOW && stack.getItem() instanceof ForgedItems.ForgedBowItem
			|| vanilla == Items.CROSSBOW && stack.getItem() instanceof ForgedItems.ForgedCrossbowItem;
	}

	/** Whether a mob has one of ours standing in for vanilla's bow or crossbow in either hand. */
	public static boolean holdsForged(LivingEntity mob, Item vanilla) {
		return forgedLauncher(mob.getMainHandItem(), vanilla) || forgedLauncher(mob.getOffhandItem(), vanilla);
	}

	/**
	 * The magic a mob may be born carrying: a staff for the archers, a tome for the zombies that walk
	 * the land. Not the drowned, whose tome would open on the sea floor, nor the zombified piglins, which
	 * mind their own business until someone gives them a reason.
	 */
	public static @Nullable ForgeType spellFor(Mob mob) {
		if (launcher(mob) == ForgeType.ARCO) {
			return ForgeType.BACULO;
		}
		if (mob instanceof Zombie && !(mob instanceof Drowned) && !(mob instanceof ZombifiedPiglin)) {
			return ForgeType.GRIMORIO;
		}
		return null;
	}

	/** How often, from the config: see ForjaConfig.baculos and ForjaConfig.grimorios. */
	public static float spellChance(ForgeType spell) {
		dev.forja.ForjaConfig config = dev.forja.ForjaConfig.get();
		return spell == ForgeType.BACULO ? config.baculos : spell == ForgeType.GRIMORIO ? config.grimorios : 0.0F;
	}

	/** A plain staff or tome for a monster: a stone at its heart from {@link #CORES}, a handle of whatever was about. */
	public static ItemStack magic(ForgeType spell, Mob mob, RandomSource random) {
		List<ForgeMaterial> materials = new java.util.ArrayList<>(Assembler.defaultMaterials(spell));
		materials.set(0, CORES[random.nextInt(CORES.length)]);
		for (int i = 1; i < materials.size(); i++) {
			if (spell.slots.get(i).role == dev.forja.part.PartType.Role.HANDLE) {
				materials.set(i, HANDLES[random.nextInt(HANDLES.length)]);
			}
		}
		return Assembler.create(spell, materials, mob.registryAccess());
	}

	/** Our bow or crossbow in place of vanilla's: the same wood, or bone for a bow, on a handle of whatever was about. */
	public static ItemStack forgedBow(ForgeType type, Mob mob, RandomSource random) {
		List<ForgeMaterial> materials = new java.util.ArrayList<>(Assembler.defaultMaterials(type));
		if (type == ForgeType.ARCO) {
			materials.set(0, BOW_LIMBS[random.nextInt(BOW_LIMBS.length)]);
		}
		for (int i = 1; i < materials.size(); i++) {
			if (type.slots.get(i).role == dev.forja.part.PartType.Role.HANDLE) {
				materials.set(i, HANDLES[random.nextInt(HANDLES.length)]);
			}
		}
		return Assembler.create(type, materials, mob.registryAccess());
	}

	/** The only things that go in a hand. */
	private static final ForgeType[] HAND_WEAPONS = {
		ForgeType.ESPADA, ForgeType.ESPADA, ForgeType.HACHA, ForgeType.LANZA,
		ForgeType.DAGA, ForgeType.MAZO, ForgeType.MARTILLO,
	};

	public static void forgeEquipment(Mob mob, RandomSource random, float chance) {
		if (!(mob instanceof Zombie) && !(mob instanceof AbstractSkeleton) && !(mob instanceof Vindicator) && !(mob instanceof Pillager)) {
			return;
		}
		// One in a hundred is not a monster with gear but an elite carrying a legend. They keep away from
		// the first days and from spawn, so nobody meets one before they have anything to fight it with.
		// A pack's companion never turns into a second champion beside the first.
		if (Elites.canAppear(mob) && !mob.entityTags().contains(dev.forja.difficulty.Scaling.COMPANION)
			&& random.nextFloat() < dev.forja.ForjaConfig.get().elites) {
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
		// A few are casters: a staff or a tome in the hand instead of what vanilla gave them, and the
		// rules to fight with it (entity/ai/CasterGoal). The rest of the kit is armour, as for an archer.
		ForgeType spell = spellFor(mob);
		if (spell != null && random.nextFloat() < spellChance(spell)) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, magic(spell, mob, random));
			if (unarmoured(mob) && random.nextFloat() < chance) {
				armour(mob, random, ForgeMaterial.HIERRO);
			}
			return;
		}
		// The archers and the crossbowmen: their weapon is the one they shoot, so it may become ours but it
		// is never swapped for a blade, and what the rule would have put in their hands goes unworn. Empty
		// hands count as vanilla's: the raiders' band is made without vanilla's gear and its pillagers had
		// nothing to shoot with.
		ForgeType shot = launcher(mob);
		if (shot != null) {
			ItemStack bow = mob.getMainHandItem();
			if ((bow.isEmpty() || bow.is(shot == ForgeType.ARCO ? Items.BOW : Items.CROSSBOW)) && !bow.isEnchanted() && random.nextFloat() < chance) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, forgedBow(shot, mob, random));
			}
			if (unarmoured(mob) && random.nextFloat() < chance) {
				armour(mob, random, ForgeMaterial.HIERRO);
			}
			return;
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
		return mob.getMainHandItem().isEmpty() && unarmoured(mob);
	}

	/** Whether it has nothing on: an archer with only its bow counts, where {@link #bare} would not. */
	private static boolean unarmoured(Mob mob) {
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
