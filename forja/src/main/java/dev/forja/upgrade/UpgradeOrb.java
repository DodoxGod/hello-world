package dev.forja.upgrade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** An upgrade pulled out of salvaged gear: the forge star adds its percentage to another item. */
public record UpgradeOrb(Upgrade upgrade, int percent) {
	public static final Codec<UpgradeOrb> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Upgrade.CODEC.fieldOf("mejora").forGetter(UpgradeOrb::upgrade),
		Codec.intRange(1, 100).fieldOf("porcentaje").forGetter(UpgradeOrb::percent)
	).apply(instance, UpgradeOrb::new));
	public static final StreamCodec<ByteBuf, UpgradeOrb> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT.map(i -> Upgrade.values()[i], Enum::ordinal), UpgradeOrb::upgrade,
		ByteBufCodecs.VAR_INT, UpgradeOrb::percent,
		UpgradeOrb::new
	);

	/** Salvaging keeps half of each upgrade. */
	public static int salvaged(int percent) {
		return percent / 2;
	}
}
