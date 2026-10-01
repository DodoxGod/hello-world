package dev.forja.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.forja.forge.Assembler;
import dev.forja.forge.Mastery;
import dev.forja.forge.Potential;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.UpgradeRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * What the Fallen Smith's apprentices carry: his own work, and the best the forge makes.
 *
 * <p>Andy, 2026-09-30: "no tienen armadura, siempre llevan las mismas armas y a veces hasta de madera. Es el
 * final del juego: que lleven equipo muy bueno". So every apprentice is dressed in four pieces of forged
 * plate in an endgame alloy, carries a different weapon out of everything the forge swings or shoots, and
 * the weapon, the shield and the breastplate carry a few upgrades, all of them within what a piece can hold
 * (the potential's load) and under the ceiling a smith reaches without flux ({@link Potential#WITHOUT_FLUX}).
 *
 * <p>The approach to the archers: a wither skeleton cannot be handed a bow and made to shoot, but a plain
 * skeleton can (the mixins teach it that a forged bow is a bow). So the base body follows the role: the
 * melee ones come up as wither skeletons, the archers as skeletons. Both are the same elite underneath
 * ({@link Elites#makeElite}), both are tagged and guard him the same way ({@link Apprentices}). There is no
 * crossbowman: nothing but a pillager shoots a crossbow, and a pillager has no business in his forge.
 *
 * <p>Nothing of it falls: every drop chance is {@link #PIECE_DROP_CHANCE}, and an apprentice gives up no
 * legend on death (see {@link Elites}).
 */
public final class ApprenticeKits {
	/** The chance that an apprentice gives up one piece of what it wears or carries. Andy's rule is none. */
	public static final float PIECE_DROP_CHANCE = 0.0F;

	/** Every apprentice's pieces are poured to this potential: room for about fifteen points of upgrades. */
	public static final int POTENTIAL = 80;

	/** Mastery level of the weapon: a smith's own, not a legend's (that one is 10). */
	public static final int MASTERY = 5;

	/** An upgrade is taken between these percentages: never past what a smith reaches without flux. */
	public static final int UPGRADE_MIN = 50;
	public static final int UPGRADE_MAX = Potential.WITHOUT_FLUX;

	/** The alloys the apprentices' weapons and armour are made of, and nothing else: no wood, no stone, no iron. */
	public static final List<ForgeMaterial> ENDGAME = List.of(
		ForgeMaterial.ACERO_ESTELAR, ForgeMaterial.SOLACERO, ForgeMaterial.LUNACERO, ForgeMaterial.OBSIDIACERO,
		ForgeMaterial.DAMASCO, ForgeMaterial.NETHERITA, ForgeMaterial.ACERO_VIVO, ForgeMaterial.ALMACERO,
		ForgeMaterial.VIDRIACERO);

	/** Plate and lining of the eight armour sets: what he forged each of them in. */
	private static final ForgeMaterial[][] SETS = {
		{ForgeMaterial.ACERO_ESTELAR, ForgeMaterial.DAMASCO},
		{ForgeMaterial.SOLACERO, ForgeMaterial.ACERO_VIVO},
		{ForgeMaterial.LUNACERO, ForgeMaterial.ALMACERO},
		{ForgeMaterial.OBSIDIACERO, ForgeMaterial.NETHERITA},
		{ForgeMaterial.DAMASCO, ForgeMaterial.OBSIDIACERO},
		{ForgeMaterial.NETHERITA, ForgeMaterial.ACERO_ESTELAR},
		{ForgeMaterial.ACERO_VIVO, ForgeMaterial.SOLACERO},
		{ForgeMaterial.ALMACERO, ForgeMaterial.LUNACERO},
	};

	/** Handles, bindings, guards, strings: the metal around the head, light ones among them for the quick weapons. */
	private static final ForgeMaterial[] FITTINGS = {
		ForgeMaterial.DAMASCO, ForgeMaterial.VIDRIACERO, ForgeMaterial.ACERO_ESTELAR, ForgeMaterial.ALMACERO,
		ForgeMaterial.ACERO_VIVO, ForgeMaterial.NETHERITA,
	};

	/**
	 * One apprentice's calling: what it swings or shoots, whether it keeps a shield in the other hand, which
	 * body it comes up in, and the upgrades its weapon may carry (a few are picked out of them).
	 */
	public record Role(String id, ForgeType weapon, boolean shield, boolean ranged, List<Upgrade> upgrades) {
		/** The body this role comes up in. */
		public EntityType<? extends Mob> body() {
			return this.ranged ? EntityTypes.SKELETON : EntityTypes.WITHER_SKELETON;
		}
	}

	/** The melee roles: nine different weapons, so a wave of up to thirteen never repeats one except in the bow. */
	public static final List<Role> MELEE = List.of(
		new Role("espada", ForgeType.ESPADA, true, false,
			List.of(Upgrade.FILO, Upgrade.CRITICO, Upgrade.ESCARCHA, Upgrade.VAMPIRISMO, Upgrade.EJECUCION)),
		new Role("espadon", ForgeType.ESPADON, false, false,
			List.of(Upgrade.FILO, Upgrade.FILO_ARRASADOR, Upgrade.CRITICO, Upgrade.ESCARCHA, Upgrade.VAMPIRISMO)),
		new Role("hacha", ForgeType.HACHA, true, false,
			List.of(Upgrade.FILO, Upgrade.CRITICO, Upgrade.DECAPITADOR, Upgrade.ASPECTO_IGNEO)),
		new Role("martillo", ForgeType.MARTILLO, false, false,
			List.of(Upgrade.ALCANCE, Upgrade.IRROMPIBLE, Upgrade.SABIDURIA)),
		new Role("maza", ForgeType.MAZO, true, false,
			List.of(Upgrade.DENSIDAD, Upgrade.ESTALLIDO_DE_VIENTO, Upgrade.FILO, Upgrade.EMPUJE)),
		new Role("lanza", ForgeType.LANZA, true, false,
			List.of(Upgrade.EMBESTIDA, Upgrade.FILO, Upgrade.CRITICO, Upgrade.VAMPIRISMO)),
		new Role("guadana", ForgeType.GUADANA, false, false,
			List.of(Upgrade.FILO, Upgrade.DESGARRO, Upgrade.FILO_ARRASADOR, Upgrade.CRITICO)),
		new Role("daga", ForgeType.DAGA, true, false,
			List.of(Upgrade.FILO, Upgrade.DESGARRO, Upgrade.CRITICO, Upgrade.VENENO, Upgrade.EJECUCION)),
		new Role("mangual", ForgeType.MANGUAL, false, false,
			List.of(Upgrade.ATURDIMIENTO, Upgrade.SEGUNDA_CABEZA, Upgrade.FILO, Upgrade.CRITICO)));

	/** The archers. Only a bow: see the class comment for why there is no crossbow. */
	public static final Role ARCHER = new Role("arquero", ForgeType.ARCO, false, true,
		List.of(Upgrade.PODER, Upgrade.TENSION, Upgrade.LLAMA, Upgrade.RETROCESO));

	/** What a shield may carry. */
	private static final List<Upgrade> SHIELD_UPGRADES = List.of(Upgrade.PUAS, Upgrade.REPULSION, Upgrade.ABSORCION, Upgrade.REFLEJOS);

	private ApprenticeKits() {
	}

	/** Whether the apprentice at this place of a formation is an archer: one in three, so a wave is never all melee. */
	public static boolean archerAt(int index) {
		return index % 3 == 2;
	}

	/**
	 * The roles of a whole wave, in formation order: an archer in every third place and, in the rest, the
	 * melee roles in a random order without repeating (nine of them, which is exactly what thirteen
	 * apprentices leave over once the archers are out).
	 */
	public static List<Role> wave(int count, RandomSource random) {
		List<Role> melee = new ArrayList<>(MELEE);
		Collections.shuffle(melee, new java.util.Random(random.nextLong()));
		List<Role> roles = new ArrayList<>();
		int next = 0;
		for (int i = 0; i < count; i++) {
			roles.add(archerAt(i) ? ARCHER : melee.get(next++ % melee.size()));
		}
		return roles;
	}

	/**
	 * Dresses and arms one apprentice. {@code index} is its place in the wave and {@code salt} a number picked
	 * once per wave: together they walk the armour sets and the weapon alloys so that no two neighbours share
	 * either.
	 */
	public static void equip(Mob mob, Role role, int index, int salt, RandomSource random) {
		// An elite is handed +8 armour for lack of plate; these wear real plate, so the stand-in goes.
		net.minecraft.world.entity.ai.attributes.AttributeInstance armourPoints = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
		if (armourPoints != null) {
			armourPoints.removeModifier(dev.forja.Forja.id("elite"));
		}
		HolderLookup.Provider registries = mob.registryAccess();
		ForgeMaterial head = ENDGAME.get(Math.floorMod(salt + index * 3, 8));
		ForgeMaterial[] set = SETS[Math.floorMod(salt * 3 + index * 5, SETS.length)];
		ForgeMaterial[] other = SETS[Math.floorMod(salt * 3 + index * 5 + 1, SETS.length)];

		mob.setItemSlot(EquipmentSlot.HEAD, armour(ForgeType.CASCO, random.nextInt(3) == 0 ? other : set, registries));
		ItemStack chest = armour(ForgeType.PECHERA, set, registries);
		mob.setItemSlot(EquipmentSlot.CHEST, upgrade(chest, ForgeType.PECHERA, List.of(Upgrade.PROTECCION), 1, random, registries));
		mob.setItemSlot(EquipmentSlot.LEGS, armour(ForgeType.GREBAS, set, registries));
		mob.setItemSlot(EquipmentSlot.FEET, armour(ForgeType.BOTAS, random.nextInt(3) == 0 ? other : set, registries));

		ItemStack weapon = weapon(role.weapon(), head, random, registries);
		weapon = upgrade(weapon, role.weapon(), role.upgrades(), 2 + random.nextInt(2), random, registries);
		Mastery.setLevel(weapon, MASTERY, registries);
		mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);

		if (role.shield()) {
			List<ForgeMaterial> plates = List.of(set[0], set[1], FITTINGS[random.nextInt(FITTINGS.length)]);
			ItemStack shield = Assembler.create(ForgeType.ESCUDO, plates, registries);
			shield.set(ModComponents.POTENCIAL, POTENTIAL);
			mob.setItemSlot(EquipmentSlot.OFFHAND, upgrade(shield, ForgeType.ESCUDO, SHIELD_UPGRADES, 1 + random.nextInt(2), random, registries));
		}

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, PIECE_DROP_CHANCE);
		}
	}

	private static ItemStack armour(ForgeType type, ForgeMaterial[] set, HolderLookup.Provider registries) {
		ItemStack stack = Assembler.create(type, List.of(set[0], set[1]), registries);
		stack.set(ModComponents.POTENCIAL, POTENTIAL);
		return stack;
	}

	/** The weapon in the given head alloy, every other part in a fitting alloy of the same standing. */
	private static ItemStack weapon(ForgeType type, ForgeMaterial head, RandomSource random, HolderLookup.Provider registries) {
		List<ForgeMaterial> materials = new ArrayList<>();
		for (dev.forja.part.PartType slot : type.slots) {
			ForgeMaterial material = slot.role == dev.forja.part.PartType.Role.HEAD ? head : FITTINGS[random.nextInt(FITTINGS.length)];
			materials.add(slot.accepts(material) ? material : head);
		}
		ItemStack stack = Assembler.create(type, materials, registries);
		stack.set(ModComponents.POTENCIAL, POTENTIAL);
		return stack;
	}

	/**
	 * Up to {@code picks} different upgrades out of the pool, each between {@link #UPGRADE_MIN} and
	 * {@link #UPGRADE_MAX} percent and each only if the piece still has room for it (Potential.fits) and it
	 * does not clash with one it carries already.
	 */
	private static ItemStack upgrade(ItemStack stack, ForgeType type, List<Upgrade> pool, int picks, RandomSource random, HolderLookup.Provider registries) {
		List<Upgrade> options = new ArrayList<>(pool);
		Collections.shuffle(options, new java.util.Random(random.nextLong()));
		int taken = 0;
		for (Upgrade upgrade : options) {
			if (taken >= picks) {
				break;
			}
			boolean clash = stack.getOrDefault(ModComponents.UPGRADES, dev.forja.upgrade.Upgrades.EMPTY).percents().keySet().stream()
				.anyMatch(have -> !upgrade.isCompatibleWith(have));
			if (clash || !upgrade.appliesTo(type) || !Potential.fits(stack, upgrade)) {
				continue;
			}
			int percent = UPGRADE_MIN + 10 * random.nextInt((UPGRADE_MAX - UPGRADE_MIN) / 10 + 1);
			stack = UpgradeRecipes.upgraded(stack, type, upgrade, percent, registries);
			taken++;
		}
		return stack;
	}
}
