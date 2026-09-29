package dev.forja.combat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.ArmorSets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Peso: what a fighter carries, and what it costs them.
 *
 * <p>Every weapon weighs something, in kilograms: its kind sets how much (a dagger half a kilo, a war hammer
 * three and a half), and its materials how that moves - the same heaviness a plate of that material has
 * (MaterialCombat), iron being the measure. Armour adds to it by how much it slows whoever wears it, and a
 * set that makes you faster takes weight off. Andy (2026-09-26):
 *
 * <ul>
 *   <li>a <b>player</b> reaches a full-strength blow later with a heavier weapon (its attack speed), and later
 *   still in heavy armour;</li>
 *   <li>a <b>monster</b> takes longer between showing its blow and landing it (the warning), and waits longer
 *   before the next one (the interval).</li>
 * </ul>
 *
 * The weights an iron weapon of each kind had before this are where the numbers were tuned, so an iron one
 * swings as it always did in a player's hands; lighter materials swing faster, heavier ones slower.
 */
public final class Weight {
	/** Kilograms of each kind with an iron head and its default handle. Anything not listed is not swung. */
	private static final Map<ForgeType, Float> BASE_KG = new EnumMap<>(ForgeType.class);

	static {
		BASE_KG.put(ForgeType.DAGA, 0.5F);
		BASE_KG.put(ForgeType.CINCEL, 0.4F);
		BASE_KG.put(ForgeType.ESPADA, 1.3F);
		BASE_KG.put(ForgeType.AZADA, 1.1F);
		BASE_KG.put(ForgeType.PALA, 1.4F);
		BASE_KG.put(ForgeType.HACHA, 1.6F);
		BASE_KG.put(ForgeType.GUANTELETES, 1.6F);
		BASE_KG.put(ForgeType.PICO, 1.8F);
		BASE_KG.put(ForgeType.LANZA, 1.8F);
		BASE_KG.put(ForgeType.PICAHACHA, 2.0F);
		BASE_KG.put(ForgeType.TRIDENTE, 2.4F);
		BASE_KG.put(ForgeType.GUADANA, 2.6F);
		BASE_KG.put(ForgeType.ESPADON, 3.0F);
		BASE_KG.put(ForgeType.MANGUAL, 3.0F);
		BASE_KG.put(ForgeType.MAZO, 3.2F);
		BASE_KG.put(ForgeType.MARTILLO, 3.6F);
		BASE_KG.put(ForgeType.BACULO, 1.5F);
		BASE_KG.put(ForgeType.GRIMORIO, 1.2F);
		BASE_KG.put(ForgeType.FAROL, 1.6F);
		BASE_KG.put(ForgeType.ARCO, 0.9F);
		BASE_KG.put(ForgeType.BALLESTA, 3.0F);
		BASE_KG.put(ForgeType.ESCUDO, 4.0F);
		BASE_KG.put(ForgeType.CANA, 0.6F);
		BASE_KG.put(ForgeType.GANCHO, 1.2F);
	}

	/** How much of a weapon's weight is its head (and guard, and binding); the rest is its handle. */
	public static final float HEAD_SHARE = 0.75F;

	/** Player: attack speed ÷ (1 + this × (weight relative to the iron one − 1)). */
	public static final float SWING_PER_RELATIVE = 0.35F;
	/** Player: a full set of the heaviest plate counts as this many kilograms. */
	public static final float ARMOR_FULL_KG = 5.0F;
	/** Player and mob: every +10 % walking speed a set gives takes this many kilograms off. */
	public static final float SPEED_KG_PER_TENTH = 2.0F;
	/** Player: attack speed lost per kilogram of armour (a full set of the heaviest plate: −15 %). */
	public static final float ARMOR_SWING_PER_KG = 0.03F;
	/** Player: the most armour can take off, or a light set give, the attack speed. */
	public static final float ARMOR_SWING_MIN = -0.2F;
	public static final float ARMOR_SWING_MAX = 0.1F;

	/** Monster: extra ticks of warning per kilogram carried, and the most it adds. */
	public static final float WINDUP_PER_KG = 1.5F;
	public static final int WINDUP_MAX_EXTRA = 14;
	/** Monster: the wait between blows grows by this share per kilogram carried. */
	public static final float INTERVAL_PER_KG = 0.1F;

	/** The swing a weapon shows: ticks = this + per kilogram (vanilla's is 6; a dagger 5, a war hammer 9 to 12). */
	public static final float SWING_TICKS_BASE = 4.0F;
	public static final float SWING_TICKS_PER_KG = 1.5F;

	private Weight() {
	}

	/** How dense a material is next to iron: iron 1, wood about 0.6, netherite about 1.5. */
	public static float density(ForgeMaterial material) {
		return (float) ((0.55 + 0.9 * MaterialCombat.heaviness(material)) / (0.55 + 0.9 * MaterialCombat.heaviness(ForgeMaterial.HIERRO)));
	}

	/** Kilograms of a forged thing of this kind in these materials (0 for what is not swung: armour, arrows, wings). */
	public static float kg(ForgeType type, List<ForgeMaterial> materials) {
		Float base = BASE_KG.get(type);
		if (base == null || materials.isEmpty()) {
			return 0.0F;
		}
		float head = 0.0F;
		int heads = 0;
		float rest = 0.0F;
		int others = 0;
		for (int slot = 0; slot < type.slots.size() && slot < materials.size(); slot++) {
			if (slot == 0 || type.slots.get(slot).role == PartType.Role.HEAD) {
				head += density(materials.get(slot));
				heads++;
			} else {
				rest += density(materials.get(slot));
				others++;
			}
		}
		float headDensity = heads == 0 ? 1.0F : head / heads;
		float restDensity = others == 0 ? headDensity : rest / others;
		return base * (HEAD_SHARE * headDensity + (1.0F - HEAD_SHARE) * restDensity) / reference(type);
	}

	/** What the default materials of a kind weigh with an iron head, next to the same with all-iron: so that one is the base. */
	private static float reference(ForgeType type) {
		List<ForgeMaterial> defaults = new java.util.ArrayList<>(Assembler.defaultMaterials(type));
		if (defaults.isEmpty()) {
			return 1.0F;
		}
		defaults.set(0, ForgeMaterial.HIERRO);
		float rest = 0.0F;
		int others = 0;
		for (int slot = 1; slot < type.slots.size() && slot < defaults.size(); slot++) {
			if (type.slots.get(slot).role != PartType.Role.HEAD) {
				rest += density(defaults.get(slot));
				others++;
			}
		}
		return HEAD_SHARE + (1.0F - HEAD_SHARE) * (others == 0 ? 1.0F : rest / others);
	}

	/** Kilograms of what is in a hand: a forged thing by its parts, vanilla's by its kind and material, else 0. */
	public static float kg(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0F;
		}
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts != null) {
			return kg(parts.type(), parts.materials());
		}
		ForgeType like = vanillaKind(stack);
		if (like == null) {
			return 0.0F;
		}
		return BASE_KG.get(like) * density(vanillaMaterial(stack));
	}

	/** What a weapon weighs next to an iron one of its kind: 1 for iron. */
	public static float relative(ForgeType type, List<ForgeMaterial> materials) {
		Float base = BASE_KG.get(type);
		return base == null ? 1.0F : kg(type, materials) / base;
	}

	/** The player's swing: how a weapon's material scales its attack speed (iron 1, lighter faster, heavier slower). */
	public static float swingFactor(float relative) {
		return Math.max(0.6F, Math.min(1.4F, 1.0F / (1.0F + SWING_PER_RELATIVE * (relative - 1.0F))));
	}

	/** Ticks of the swing shown for a weapon of this weight. */
	public static int swingTicks(float kg) {
		return Math.max(3, Math.round(SWING_TICKS_BASE + SWING_TICKS_PER_KG * kg));
	}

	/**
	 * What someone's armour counts for, in kilograms: its weight (the plate's heaviness, by how much of the
	 * body each piece covers) against a full set of the heaviest plate, less what a set that quickens its
	 * wearer gives back. Negative for a light set that makes you faster.
	 */
	public static float armourKg(LivingEntity entity) {
		float kg = (float) ArmorCalculator.armorWeight(entity) * ARMOR_FULL_KG;
		ForgeMaterial set = ArmorSets.fullSet(entity);
		if (set != null) {
			for (ArmorSets.Bonus bonus : ArmorSets.bonuses(set)) {
				if (bonus.attribute().equals(Attributes.MOVEMENT_SPEED) && bonus.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
					kg -= (float) (bonus.amount() / 0.1) * SPEED_KG_PER_TENTH;
				}
			}
		}
		return kg;
	}

	/** Everything someone carries into a blow: the weapon in hand and their armour, never below nothing. */
	public static float carried(LivingEntity entity) {
		return Math.max(0.0F, kg(entity.getMainHandItem()) + armourKg(entity));
	}

	/** Player: the share of attack speed their armour takes (negative) or a light set gives (positive). */
	public static float armourSwing(LivingEntity entity) {
		return Math.max(ARMOR_SWING_MIN, Math.min(ARMOR_SWING_MAX, -ARMOR_SWING_PER_KG * armourKg(entity)));
	}

	/** Monster: the ticks its warning grows by with what it carries. */
	public static int windupExtra(Mob mob) {
		return Math.min(WINDUP_MAX_EXTRA, Math.round(carried(mob) * WINDUP_PER_KG));
	}

	/** Monster: the wait after a blow, from the wait it would have with nothing. */
	public static int interval(Mob mob, int base) {
		return Math.round(base * (1.0F + INTERVAL_PER_KG * carried(mob)));
	}

	// ---------------------------------------------------------------- vanilla's own weapons

	private static ForgeType vanillaKind(ItemStack stack) {
		if (stack.is(ItemTags.SWORDS)) return ForgeType.ESPADA;
		if (stack.is(ItemTags.AXES)) return ForgeType.HACHA;
		if (stack.is(ItemTags.PICKAXES)) return ForgeType.PICO;
		if (stack.is(ItemTags.SHOVELS)) return ForgeType.PALA;
		if (stack.is(ItemTags.HOES)) return ForgeType.AZADA;
		if (stack.is(ItemTags.SPEARS)) return ForgeType.LANZA;
		if (stack.is(Items.MACE)) return ForgeType.MAZO;
		if (stack.is(Items.TRIDENT)) return ForgeType.TRIDENTE;
		if (stack.is(Items.BOW)) return ForgeType.ARCO;
		if (stack.is(Items.CROSSBOW)) return ForgeType.BALLESTA;
		if (stack.is(Items.SHIELD)) return ForgeType.ESCUDO;
		return null;
	}

	/** Vanilla's material by the item's name: iron unless it says otherwise. */
	private static ForgeMaterial vanillaMaterial(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.startsWith("wooden_")) return ForgeMaterial.MADERA;
		if (path.startsWith("stone_")) return ForgeMaterial.PIEDRA;
		if (path.startsWith("copper_")) return ForgeMaterial.COBRE;
		if (path.startsWith("golden_")) return ForgeMaterial.ORO;
		if (path.startsWith("diamond_")) return ForgeMaterial.DIAMANTE;
		if (path.startsWith("netherite_")) return ForgeMaterial.NETHERITA;
		return ForgeMaterial.HIERRO;
	}
}
