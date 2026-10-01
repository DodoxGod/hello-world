package dev.forja.part;

import java.util.Locale;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * How a handle or a binding is shaped: the plain one, a heavy one or a light one (Andy, 2026-09-29). A choice,
 * not an upgrade: the plain one stays the middle ground, the heavy one hits harder and swings later, the light
 * one swings sooner and hits softer. What each one does lives in combat/Grip.
 *
 * <p>The variant is the shape, never the material (Andy, 2026-09-30: "todos los materiales para todas las
 * piezas"): a heavy handle carries a counterweight at its butt and a heavy binding rivets and bands, a light
 * handle is slim or hollow and a light binding a thin wrap, in whatever a plain handle or binding takes. The
 * material decides how it is made (a metal is poured, the rest cut at the bench, as for the plain part), what
 * it weighs (its density times the shape's, combat/Weight) and everything else it gives a plain part.
 */
public enum PartVariant implements StringRepresentable {
	NORMAL,
	PESADO,
	LIGERO;

	public static final Codec<PartVariant> CODEC = StringRepresentable.fromEnum(PartVariant::values);
	public static final StreamCodec<ByteBuf, PartVariant> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String getSerializedName() {
		return this.id();
	}
}
