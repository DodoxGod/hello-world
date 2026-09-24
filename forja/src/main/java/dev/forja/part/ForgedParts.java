package dev.forja.part;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Which material sits in each slot of an assembled item, in the slot order of its {@link ForgeType}. */
public record ForgedParts(ForgeType type, List<ForgeMaterial> materials) {
	public static final StreamCodec<ByteBuf, ForgeMaterial> MATERIAL_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> ForgeMaterial.values()[i], Enum::ordinal);
	public static final StreamCodec<ByteBuf, ForgeType> TYPE_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> ForgeType.values()[i], Enum::ordinal);

	public static final Codec<ForgedParts> CODEC = RecordCodecBuilder.create(i -> i.group(
			ForgeType.CODEC.fieldOf("type").forGetter(ForgedParts::type),
			ForgeMaterial.CODEC.listOf().fieldOf("materials").forGetter(ForgedParts::materials)
		).apply(i, ForgedParts::new)
	);
	public static final StreamCodec<ByteBuf, ForgedParts> STREAM_CODEC = StreamCodec.composite(
		TYPE_STREAM_CODEC, ForgedParts::type,
		MATERIAL_STREAM_CODEC.apply(ByteBufCodecs.list()), ForgedParts::materials,
		ForgedParts::new
	);

	public ForgedParts {
		materials = List.copyOf(materials);
	}

	public ForgeMaterial material(int slot) {
		return this.materials.get(slot);
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
