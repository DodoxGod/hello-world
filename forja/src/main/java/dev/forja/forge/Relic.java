package dev.forja.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.forja.item.PartItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.Nullable;

/**
 * Things the forge star puts together out of parts that are not gear: no stats, no durability, no Maestria, no
 * upgrades — an object with a use of its own. The multiset of part types is the recipe, as for a
 * {@link ForgeType}, and none of these may share one with a ForgeType (a type always wins; see
 * {@link Assembler#evaluate}).
 *
 * <p>Why not a ForgeType: every ForgeType is a piece of gear, and the whole mod reads it as one — its stat
 * sheet, durability, Maestria, upgrades, casting frames, loot, the legends, the guide's recipe pages and a
 * dozen contracts in the tests walk every type. A relic is made the same way (parts cut at the parts table,
 * put on the star of a forge table, one timed press) and nothing else about gear applies to it.
 */
public enum Relic {
	/**
	 * Andy (2026-09-29): changing class costs a forged object. A núcleo of echo (three echo shards, from the
	 * ancient cities), set in an engaste and hung from a chain: the medallion that makes you forget. The
	 * engaste and the chain may be of anything and colour it; the núcleo has to be echo.
	 */
	MEDALLON_DEL_OLVIDO(List.of(PartType.NUCLEO, PartType.ENGASTE, PartType.CADENA), ForgeMaterial.ECO);

	/** Part slots in a fixed order; the colours of the item's layers follow it. */
	public final List<PartType> slots;
	/** What the relic's núcleo has to be made of. */
	public final ForgeMaterial core;

	Relic(List<PartType> slots, ForgeMaterial core) {
		this.slots = slots;
		this.core = core;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public Item item() {
		return switch (this) {
			case MEDALLON_DEL_OLVIDO -> ModItems.MEDALLON_DEL_OLVIDO;
		};
	}

	public Component displayName() {
		return Component.translatable("item.forja." + this.id());
	}

	/** The relic whose slots are exactly this multiset of part types, if any. */
	public static @Nullable Relic match(List<PartType> parts) {
		for (Relic relic : values()) {
			if (sameMultiset(relic.slots, parts)) {
				return relic;
			}
		}
		return null;
	}

	/** Parts still missing for the closest relic that contains everything placed so far. */
	public static @Nullable List<PartType> missingFor(List<PartType> parts) {
		List<PartType> best = null;
		for (Relic relic : values()) {
			List<PartType> remaining = new ArrayList<>(relic.slots);
			boolean fits = true;
			for (PartType part : parts) {
				if (!remaining.remove(part)) {
					fits = false;
					break;
				}
			}
			if (fits && !remaining.isEmpty() && (best == null || remaining.size() < best.size())) {
				best = remaining;
			}
		}
		return best;
	}

	/**
	 * The relic these loose parts make, or empty when their núcleo is not of {@link #core}. The parts must be
	 * exactly this relic's multiset ({@link #match}).
	 */
	public ItemStack assemble(List<ItemStack> looseParts) {
		ForgeMaterial[] materials = new ForgeMaterial[this.slots.size()];
		boolean[] taken = new boolean[looseParts.size()];
		for (int slot = 0; slot < materials.length; slot++) {
			for (int i = 0; i < looseParts.size(); i++) {
				ItemStack part = looseParts.get(i);
				if (!taken[i] && part.getItem() instanceof PartItem item && item.type == this.slots.get(slot)) {
					taken[i] = true;
					materials[slot] = part.get(ModComponents.MATERIAL);
					break;
				}
			}
			if (materials[slot] == null) {
				return ItemStack.EMPTY;
			}
		}
		if (!this.coreFits(List.of(materials))) {
			return ItemStack.EMPTY;
		}
		return this.create(List.of(materials));
	}

	/** Whether the núcleo of these materials (in slot order) is what this relic asks for. */
	public boolean coreFits(List<ForgeMaterial> materials) {
		int slot = this.slots.indexOf(PartType.NUCLEO);
		return slot < 0 || materials.get(slot) == this.core;
	}

	/**
	 * A relic of these materials, in slot order: its layers take their colours (as a forged piece's do) and a
	 * line under the name says what it was made of.
	 */
	public ItemStack create(List<ForgeMaterial> materials) {
		ItemStack stack = new ItemStack(this.item());
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), materials.stream().map(m -> m.color).toList()));
		MutableComponent made = Component.empty();
		for (int slot = 0; slot < this.slots.size(); slot++) {
			if (slot > 0) {
				made.append(" · ");
			}
			made.append(Component.translatable("part.forja." + this.slots.get(slot).id() + ".de", materials.get(slot).displayName()));
		}
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("item.forja." + this.id() + ".desc"),
			made.withColor(0xFFA0A0A0))));
		return stack;
	}

	/** The materials a relic in the guide and the creative tab is shown in. */
	public List<ForgeMaterial> defaultMaterials() {
		return switch (this) {
			case MEDALLON_DEL_OLVIDO -> List.of(ForgeMaterial.ECO, ForgeMaterial.ORO, ForgeMaterial.HIERRO);
		};
	}

	/**
	 * For the star's screen: the relic these parts would make but for their núcleo, which is of the wrong
	 * stone; null if they are not such a set.
	 */
	public static @Nullable Relic wrongCore(List<ItemStack> parts) {
		List<PartType> types = new ArrayList<>();
		for (ItemStack part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (!(part.getItem() instanceof PartItem item)) {
				return null;
			}
			types.add(item.type);
		}
		Relic relic = match(types);
		if (relic == null) {
			return null;
		}
		List<ItemStack> loose = parts.stream().filter(stack -> !stack.isEmpty()).toList();
		return relic.assemble(loose).isEmpty() ? relic : null;
	}

	private static boolean sameMultiset(List<PartType> a, List<PartType> b) {
		if (a.size() != b.size()) {
			return false;
		}
		List<PartType> remaining = new ArrayList<>(a);
		for (PartType part : b) {
			if (!remaining.remove(part)) {
				return false;
			}
		}
		return true;
	}
}
