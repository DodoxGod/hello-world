package dev.forja.part;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import com.mojang.serialization.Codec;
import dev.forja.material.ForgeMaterial;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * How a handle or a binding is made: the plain one, a heavy one or a light one (Andy, 2026-09-29). A choice,
 * not an upgrade: the plain one stays the middle ground, the heavy one hits harder and swings later, the light
 * one swings sooner and hits softer. What each one does lives in combat/Grip.
 *
 * <p>A heavy part is poured in a dense metal and a light one cut from a light material; which ones is
 * {@link #HEAVY} and {@link #LIGHT}.
 */
public enum PartVariant implements StringRepresentable {
	NORMAL,
	PESADO,
	LIGERO;

	public static final Codec<PartVariant> CODEC = StringRepresentable.fromEnum(PartVariant::values);
	public static final StreamCodec<ByteBuf, PartVariant> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

	/** The dense metals a counterweighted handle or a riveted binding is poured in. */
	public static final Set<ForgeMaterial> HEAVY = java.util.Collections.unmodifiableSet(EnumSet.of(
		ForgeMaterial.COBRE, ForgeMaterial.HIERRO, ForgeMaterial.BRONCE, ForgeMaterial.ACERO, ForgeMaterial.ESCORIA,
		ForgeMaterial.CINERIO, ForgeMaterial.OBSIDIACERO, ForgeMaterial.NETHERITA));

	/** What a light handle or binding is cut from: wood (bamboo planks are wood too), bone and leather. */
	public static final Set<ForgeMaterial> LIGHT = java.util.Collections.unmodifiableSet(EnumSet.of(
		ForgeMaterial.MADERA, ForgeMaterial.HUESO, ForgeMaterial.CUERO));

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String getSerializedName() {
		return this.id();
	}
}
