package dev.forja.forge;

import java.util.ArrayList;
import java.util.List;

import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Copper does what copper does. A piece with copper in it goes green with use, through the same four
 * stages as the blocks, and a full patina is worth a tenth of its durability. Honeycomb stops it
 * wherever you like the colour.
 */
public final class Oxidation {
	/** The last stage; a piece at this stage has the full patina. */
	public static final int STAGES = 3;

	/** What a full patina is worth in durability. */
	public static final float PATINA_DURABILITY = 0.10F;

	/** One use in this many turns the copper a shade greener. */
	private static final int SLOWNESS = 220;

	/** The colour of copper at each stage, from new to fully weathered. */
	private static final int[] SHADES = {0xE58A5C, 0xB79A72, 0x8FAE8A, 0x6FA894};

	private Oxidation() {
	}

	public static boolean isCopper(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null) {
			return false;
		}
		for (int slot = 0; slot < parts.type().slots.size(); slot++) {
			if (parts.material(slot) == ForgeMaterial.COBRE) {
				return true;
			}
		}
		return false;
	}

	public static int stage(ItemStack stack) {
		return Math.min(STAGES, stack.getOrDefault(ModComponents.OXIDO, 0));
	}

	public static boolean waxed(ItemStack stack) {
		return stack.getOrDefault(ModComponents.ENCERADO, false);
	}

	/** Whether the patina is complete, which is what pays the durability back. */
	public static boolean full(ItemStack stack) {
		return isCopper(stack) && stage(stack) >= STAGES;
	}

	/** Seals the piece where it is: honeycomb, the same as on a block. */
	public static void wax(ItemStack stack) {
		stack.set(ModComponents.ENCERADO, true);
		paint(stack);
	}

	/**
	 * One more use of a copper piece. Every so often it turns a shade greener, and at that point the
	 * whole sheet is written again, because the patina is worth durability.
	 */
	public static void use(LivingEntity owner, ItemStack stack) {
		if (waxed(stack) || !isCopper(stack) || stage(stack) >= STAGES) {
			return;
		}
		if (owner.getRandom().nextInt(SLOWNESS) != 0) {
			return;
		}
		stack.set(ModComponents.OXIDO, stage(stack) + 1);
		dev.forja.forge.Assembler.rewrite(stack, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		paint(stack);
	}

	/** Repaints the copper parts for the stage the piece is at; everything else keeps its colour. */
	public static void paint(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null) {
			return;
		}
		int stage = stage(stack);
		List<Integer> colors = new ArrayList<>();
		for (int slot = 0; slot < parts.type().slots.size(); slot++) {
			ForgeMaterial material = parts.material(slot);
			colors.add(material == ForgeMaterial.COBRE ? SHADES[stage] : material.color);
		}
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), colors));
	}
}
