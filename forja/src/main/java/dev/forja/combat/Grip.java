package dev.forja.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeStats;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.part.PartVariant;
import dev.forja.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Agarre: what a heavy or a light handle, and a riveted or a thin binding, change in a fight (Andy,
 * 2026-09-29). A choice, never an upgrade: the plain parts stay the middle, and each variant pays for what it
 * gives.
 *
 * <ul>
 *   <li><b>Heavy handle</b> (a counterweight): the charged blow hits harder, every blow shakes the foe's
 *   balance more and throws it further. It weighs more (combat/Weight), so the full-strength blow comes later
 *   and a monster holding it warns longer; and every swing costs more breath.</li>
 *   <li><b>Light handle</b>: the other way round: sooner and cheaper, softer on the balance and the push, and
 *   a charged blow a little weaker.</li>
 *   <li><b>Heavy binding</b> (rivets and iron bands): it lasts longer, a broken guard comes back sooner, a
 *   blocked blow costs less breath, and a shield bash cannot knock a charge out of your hands. A little
 *   heavier.</li>
 *   <li><b>Light binding</b>: a little lighter, so a little quicker; it wears sooner and a broken guard takes
 *   longer to come back.</li>
 * </ul>
 *
 * Every variant comes in every material a plain handle or binding takes (Andy, 2026-09-30): the variant is the
 * shape and these trades ride on top of whatever the material gives. The weight is the one thing the two share:
 * a part weighs its material's density times its shape ({@link #shape}, read by combat/Weight), so a heavy oak
 * handle is a modest counterweight and a light netherite one is still no feather.
 *
 * <p>The numbers were set against the balance probes (docs/EQUILIBRIO.md, "Mangos y ataduras").
 */
public final class Grip {
	/**
	 * What a variant part weighs next to the plain part of the same material: its shape. A counterweighted
	 * handle carries a pommel more than its own mass again; a slim, hollowed one keeps about a third of it.
	 * Multiplied by the material's density in combat/Weight, so on an iron sword the heavy handle adds about
	 * 18 % and the light one takes off about 9 %; in oak, about 11 % and 5 %; in netherite, 28 % and 14 %.
	 */
	public static final float HEAVY_HANDLE_SHAPE = 2.3F;
	public static final float LIGHT_HANDLE_SHAPE = 0.35F;
	public static final float HEAVY_BINDING_SHAPE = 1.6F;
	public static final float LIGHT_BINDING_SHAPE = 0.4F;

	/** The charged blow's damage, on top of its own multiplier. */
	public static final float HEAVY_CHARGE = 1.20F;
	public static final float LIGHT_CHARGE = 0.90F;
	/** Balance (posture) every blow takes off a foe. */
	public static final float HEAVY_POSTURE = 1.25F;
	public static final float LIGHT_POSTURE = 0.80F;
	/** How far every blow throws. */
	public static final float HEAVY_KNOCKBACK = 1.30F;
	public static final float LIGHT_KNOCKBACK = 0.75F;
	/** Stamina a plain swing costs. */
	public static final float HEAVY_SWING_COST = 1.15F;
	public static final float LIGHT_SWING_COST = 0.90F;
	/** Stamina a charged blow costs: the heavy one is drawn back with the same arm. */
	public static final float HEAVY_CHARGE_COST = 1.10F;
	public static final float LIGHT_CHARGE_COST = 0.90F;

	/** The whole piece's durability. */
	public static final float HEAVY_DURABILITY = 1.20F;
	public static final float LIGHT_DURABILITY = 0.85F;
	/** How long a broken guard takes to come back. */
	public static final float HEAVY_GUARD_BREAK = 0.5F;
	public static final float LIGHT_GUARD_BREAK = 1.35F;
	/** Stamina a blocked blow costs while the weapon is in hand. */
	public static final float HEAVY_BLOCK_COST = 0.80F;

	private Grip() {
	}

	// ---------------------------------------------------------------- which variant

	/** How the handle of this piece was made (plain for anything without one). */
	public static PartVariant handle(ForgedParts parts) {
		return variantOf(parts, PartType.MANGO);
	}

	/** How the binding of this piece was made (plain for anything without one). */
	public static PartVariant binding(ForgedParts parts) {
		return variantOf(parts, PartType.ATADURA);
	}

	private static PartVariant variantOf(ForgedParts parts, PartType plain) {
		if (!parts.hasVariants()) {
			return PartVariant.NORMAL;
		}
		int slot = parts.type().slots.indexOf(plain);
		return slot < 0 ? PartVariant.NORMAL : parts.variant(slot);
	}

	public static PartVariant handle(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		return parts == null ? PartVariant.NORMAL : handle(parts);
	}

	public static PartVariant binding(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		return parts == null ? PartVariant.NORMAL : binding(parts);
	}

	private static float pick(PartVariant variant, float heavy, float light) {
		return switch (variant) {
			case PESADO -> heavy;
			case LIGERO -> light;
			case NORMAL -> 1.0F;
		};
	}

	// ---------------------------------------------------------------- what it does

	/** What this part weighs next to the plain part it stands in for, made of the same material: 1 for a plain part. */
	public static float shape(PartType part) {
		return switch (part) {
			case MANGO_PESADO -> HEAVY_HANDLE_SHAPE;
			case MANGO_LIGERO -> LIGHT_HANDLE_SHAPE;
			case ATADURA_PESADA -> HEAVY_BINDING_SHAPE;
			case ATADURA_LIGERA -> LIGHT_BINDING_SHAPE;
			default -> 1.0F;
		};
	}

	/** What a loose part of this material weighs next to a plain iron one of its kind: density times shape. */
	public static float partWeight(PartType part, ForgeMaterial material) {
		return Weight.density(material) * shape(part);
	}

	public static float durability(ForgedParts parts) {
		return pick(binding(parts), HEAVY_DURABILITY, LIGHT_DURABILITY);
	}

	public static float chargeDamage(ItemStack weapon) {
		return pick(handle(weapon), HEAVY_CHARGE, LIGHT_CHARGE);
	}

	public static float posture(ItemStack weapon) {
		return pick(handle(weapon), HEAVY_POSTURE, LIGHT_POSTURE);
	}

	public static float knockback(ItemStack weapon) {
		return pick(handle(weapon), HEAVY_KNOCKBACK, LIGHT_KNOCKBACK);
	}

	public static float swingCost(ItemStack weapon) {
		return pick(handle(weapon), HEAVY_SWING_COST, LIGHT_SWING_COST);
	}

	public static float chargeCost(ItemStack weapon) {
		return pick(handle(weapon), HEAVY_CHARGE_COST, LIGHT_CHARGE_COST);
	}

	public static float guardBreak(ItemStack weapon) {
		return pick(binding(weapon), HEAVY_GUARD_BREAK, LIGHT_GUARD_BREAK);
	}

	public static float blockCost(ItemStack weapon) {
		return pick(binding(weapon), HEAVY_BLOCK_COST, 1.0F);
	}

	/** Whether a bash can knock this weapon's charge out of the hands holding it: not past rivets and bands. */
	public static boolean holdsCharge(ItemStack weapon) {
		return binding(weapon) == PartVariant.PESADO;
	}

	/** Ticks a broken guard stays down with this weapon in hand. */
	public static int guardBreakTicks(ItemStack weapon, int ticks) {
		return Math.max(1, Math.round(ticks * guardBreak(weapon)));
	}

	// ---------------------------------------------------------------- how it reads

	/** A share as the tooltips write it: "+18 %", "−10 %". */
	public static String percent(float factor) {
		int delta = Math.round((factor - 1.0F) * 100.0F);
		return (delta >= 0 ? "+" : "−") + Math.abs(delta) + " %";
	}

	/**
	 * The swing a loose variant part of this material buys or costs, next to the plain part of the same
	 * material: on an iron sword for a handle, an iron axe for a binding (kinds the balance report measures).
	 */
	public static float looseSwing(PartType part, ForgeMaterial material) {
		ForgeType type = part.base() == PartType.ATADURA ? ForgeType.HACHA : ForgeType.ESPADA;
		int slot = type.slots.indexOf(part.base());
		if (slot < 0 || part.variant == PartVariant.NORMAL) {
			return 1.0F;
		}
		List<ForgeMaterial> materials = new ArrayList<>(Assembler.defaultMaterials(type));
		materials.set(slot, material);
		List<PartVariant> variants = new ArrayList<>(Collections.nCopies(type.slots.size(), PartVariant.NORMAL));
		variants.set(slot, part.variant);
		ForgedParts plain = new ForgedParts(type, materials);
		ForgedParts made = new ForgedParts(type, materials, variants);
		return Weight.swingFactor(Weight.relative(made)) / Weight.swingFactor(Weight.relative(plain));
	}

	/**
	 * The trade a loose variant part makes, in two lines: "Pesado: +18 % golpe cargado · …". Empty for a plain
	 * part. The speed is what the part alone does to an iron sword or axe, in the material it is made of (iron
	 * for a template, which has none yet); on a finished piece {@link #tradeoff(ItemStack)} says what it really
	 * does.
	 */
	public static List<Component> tradeoff(PartType part) {
		return tradeoff(part, ForgeMaterial.HIERRO);
	}

	public static List<Component> tradeoff(PartType part, ForgeMaterial material) {
		return lines(part, looseSwing(part, material));
	}

	/**
	 * What a loose variant part of this material weighs, in a line: next to a plain iron part of its kind, the
	 * material's density times the shape. Null for a plain part.
	 */
	public static @Nullable Component weightLine(PartType part, ForgeMaterial material) {
		if (part.variant == PartVariant.NORMAL) {
			return null;
		}
		return Component.translatable("tooltip.forja.variante.peso." + part.base().id(), decimal(partWeight(part, material)),
			decimal(Weight.density(material)), decimal(shape(part))).withColor(0xFF9A9A9A);
	}

	/** "1,52": two decimals with the comma the Spanish texts use (the English ones read it as well as a dot). */
	private static String decimal(float value) {
		return String.format(Locale.ROOT, "%.2f", value).replace('.', ',');
	}

	/** The trade each variant part of a finished piece makes, with the speed it really costs or gives it. */
	public static List<Component> tradeoff(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		List<Component> result = new ArrayList<>();
		if (parts == null || !parts.hasVariants()) {
			return result;
		}
		ForgeStats.Sheet without = ForgeStats.sheet(stack, parts.plain());
		for (int slot = 0; slot < parts.type().slots.size(); slot++) {
			PartType part = parts.part(slot);
			if (part.variant == PartVariant.NORMAL) {
				continue;
			}
			// What this variant alone does to the swing: the piece with only it, against the plain piece.
			List<PartVariant> only = new ArrayList<>();
			for (int other = 0; other < parts.type().slots.size(); other++) {
				only.add(other == slot ? part.variant : PartVariant.NORMAL);
			}
			ForgeStats.Sheet single = ForgeStats.sheet(stack, new ForgedParts(parts.type(), parts.materials(), only));
			float speed = without.attackSpeed + 4.0F <= 0.0F ? 1.0F : (single.attackSpeed + 4.0F) / (without.attackSpeed + 4.0F);
			result.addAll(lines(part, speed));
		}
		return result;
	}

	/**
	 * The same trade in the few words the forge's stat panel has room for: the part's name, then the two numbers
	 * that matter most on it.
	 */
	public static List<Component> shortTradeoff(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		List<Component> result = new ArrayList<>();
		if (parts == null || !parts.hasVariants()) {
			return result;
		}
		ForgeStats.Sheet without = ForgeStats.sheet(stack, parts.plain());
		for (int slot = 0; slot < parts.type().slots.size(); slot++) {
			PartType part = parts.part(slot);
			if (part.variant == PartVariant.NORMAL) {
				continue;
			}
			List<PartVariant> only = new ArrayList<>();
			for (int other = 0; other < parts.type().slots.size(); other++) {
				only.add(other == slot ? part.variant : PartVariant.NORMAL);
			}
			ForgeStats.Sheet single = ForgeStats.sheet(stack, new ForgedParts(parts.type(), parts.materials(), only));
			float speed = without.attackSpeed + 4.0F <= 0.0F ? 1.0F : (single.attackSpeed + 4.0F) / (without.attackSpeed + 4.0F);
			String first = switch (part) {
				case MANGO_PESADO -> percent(HEAVY_CHARGE);
				case MANGO_LIGERO -> percent(LIGHT_SWING_COST);
				case ATADURA_PESADA -> percent(HEAVY_DURABILITY);
				default -> percent(LIGHT_DURABILITY);
			};
			// One line each: the panel has room for few, and the speed is already on the line above.
			result.add(Component.translatable("gui.forja.variante.corto." + part.id(), first, percent(speed)).withColor(COLOR));
		}
		return result;
	}

	/** Two lines a trade: what it gives, then what it costs (one line ran off the screen). */
	private static List<Component> lines(PartType part, float speed) {
		List<Component> lines = new ArrayList<>();
		Object[] args = switch (part) {
			case MANGO_PESADO -> new Object[] {percent(HEAVY_CHARGE), percent(HEAVY_POSTURE), percent(HEAVY_KNOCKBACK), percent(speed), percent(HEAVY_SWING_COST)};
			case MANGO_LIGERO -> new Object[] {percent(speed), percent(LIGHT_SWING_COST), percent(LIGHT_POSTURE), percent(LIGHT_KNOCKBACK), percent(LIGHT_CHARGE)};
			case ATADURA_PESADA -> new Object[] {percent(HEAVY_DURABILITY), percent(HEAVY_BLOCK_COST), percent(HEAVY_GUARD_BREAK), percent(speed)};
			case ATADURA_LIGERA -> new Object[] {percent(speed), percent(LIGHT_DURABILITY), percent(LIGHT_GUARD_BREAK)};
			default -> null;
		};
		if (args != null) {
			String key = "tooltip.forja.variante." + part.id();
			lines.add(Component.translatable(key + ".gana", args).withColor(COLOR));
			lines.add(Component.translatable(key + ".cuesta", args).withColor(COST_COLOR));
		}
		return lines;
	}

	/** What a variant costs is written a shade duller than what it gives. */
	public static final int COST_COLOR = 0xFFB08A50;

	/** How a variant part is made, for the template's tooltip: of anything, poured if metal and cut if not. */
	public static Component materials(PartType part) {
		return Component.translatable("tooltip.forja.variante.materiales").withColor(0xFF9A9A9A);
	}

	/** The colour the trade lines are written in: brass, the colour of the balance on a scale. */
	public static final int COLOR = 0xFFE0B94A;
}
