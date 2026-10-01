package dev.forja.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Destello: a point of light in a colour, the size it is thrown at (ModParticles.DESTELLO).
 *
 * <p>What the spells threw before was vanilla's redstone dust in the spell's colour, and dust is a speck of
 * something lit by the world: at dusk an amber tome opened a field of brown flecks and a violet bolt burst into
 * a dark purple smudge over whatever it hit. A spell is light, so its particle gives off its own — it is born
 * nearly white, settles into its colour and goes out (client/ForjaParticles.Glint).
 *
 * @param colour the light's colour, 0xRRGGBB
 * @param scale how big it is, about as dust's scale reads: 1 is a spark of it, 2.5 a burst
 */
public record GlintOptions(int colour, float scale) implements ParticleOptions {
	public static final MapCodec<GlintOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.INT.fieldOf("color").forGetter(GlintOptions::colour),
		Codec.floatRange(0.05F, 6.0F).fieldOf("scale").forGetter(GlintOptions::scale)
	).apply(instance, GlintOptions::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, GlintOptions> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.INT, GlintOptions::colour,
		ByteBufCodecs.FLOAT, GlintOptions::scale,
		GlintOptions::new);

	public GlintOptions {
		colour &= 0xFFFFFF;
		scale = Math.max(0.05F, Math.min(6.0F, scale));
	}

	@Override
	public ParticleType<GlintOptions> getType() {
		return ModParticles.DESTELLO;
	}
}
