package dev.forja.part;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Which material sits in each slot of an assembled item, in the slot order of its {@link ForgeType}, and
 * how each slot's part was made ({@link PartVariant}): a heavy or a light handle, a riveted or a thin binding.
 *
 * <p>The variants are empty when every part is a plain one, and then nothing about them is saved: an item
 * from before the variants existed, and a plain one made today, are written and read exactly alike.
 */
public record ForgedParts(ForgeType type, List<ForgeMaterial> materials, List<PartVariant> variants) {
	public static final StreamCodec<ByteBuf, ForgeMaterial> MATERIAL_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> ForgeMaterial.values()[i], Enum::ordinal);
	public static final StreamCodec<ByteBuf, ForgeType> TYPE_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> ForgeType.values()[i], Enum::ordinal);

	public static final Codec<ForgedParts> CODEC = RecordCodecBuilder.create(i -> i.group(
			ForgeType.CODEC.fieldOf("type").forGetter(ForgedParts::type),
			ForgeMaterial.CODEC.listOf().fieldOf("materials").forGetter(ForgedParts::materials),
			PartVariant.CODEC.listOf().optionalFieldOf("variants", List.of()).forGetter(ForgedParts::variants)
		).apply(i, ForgedParts::new)
	);
	public static final StreamCodec<ByteBuf, ForgedParts> STREAM_CODEC = StreamCodec.composite(
		TYPE_STREAM_CODEC, ForgedParts::type,
		MATERIAL_STREAM_CODEC.apply(ByteBufCodecs.list()), ForgedParts::materials,
		PartVariant.STREAM_CODEC.apply(ByteBufCodecs.list()), ForgedParts::variants,
		ForgedParts::new
	);

	public ForgedParts(ForgeType type, List<ForgeMaterial> materials) {
		this(type, materials, List.of());
	}

	public ForgedParts {
		materials = List.copyOf(materials);
		variants = normalized(type, variants);
	}

	/**
	 * One entry per slot when any part is not a plain one, else none; a variant on a slot whose part has no
	 * variants (a blade, a guard) is dropped rather than kept as a promise nothing reads.
	 */
	private static List<PartVariant> normalized(ForgeType type, List<PartVariant> variants) {
		if (variants.isEmpty()) {
			return List.of();
		}
		List<PartVariant> result = new ArrayList<>();
		boolean any = false;
		for (int slot = 0; slot < type.slots.size(); slot++) {
			PartVariant variant = slot < variants.size() && variants.get(slot) != null ? variants.get(slot) : PartVariant.NORMAL;
			if (variant != PartVariant.NORMAL && PartType.of(type.slots.get(slot), variant) == type.slots.get(slot)) {
				variant = PartVariant.NORMAL;
			}
			any |= variant != PartVariant.NORMAL;
			result.add(variant);
		}
		return any ? List.copyOf(result) : List.of();
	}

	public ForgeMaterial material(int slot) {
		return this.materials.get(slot);
	}

	/** How the part in this slot was made: plain unless it says otherwise. */
	public PartVariant variant(int slot) {
		return slot < this.variants.size() ? this.variants.get(slot) : PartVariant.NORMAL;
	}

	/** The part that really sits in this slot: the slot's plain part, or its heavy or light version. */
	public PartType part(int slot) {
		return PartType.of(this.type.slots.get(slot), this.variant(slot));
	}

	/** Whether any part is heavy or light. */
	public boolean hasVariants() {
		return !this.variants.isEmpty();
	}

	/** The same parts with every slot made plain: what this piece would be with an ordinary handle and binding. */
	public ForgedParts plain() {
		return this.hasVariants() ? new ForgedParts(this.type, this.materials) : this;
	}

	/** The material that names the item: its first head, blade, limbs or plate, else its first slot. */
	public ForgeMaterial primary() {
		int slot = this.type.slotOf(PartType.Role.HEAD);
		if (slot < 0) {
			slot = this.type.slotOf(PartType.Role.PLATE);
		}
		// A fishing rod has neither, so its shaft names it.
		return this.materials.get(Math.max(0, slot));
	}

	public boolean hasTrait(ForgeMaterial.Trait trait) {
		return this.materials.stream().anyMatch(material -> material.trait == trait);
	}
}
