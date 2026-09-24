package dev.forja.part;

import java.util.Locale;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import dev.forja.material.ForgeMaterial;
import net.minecraft.network.chat.Component;

/** Every part the forge table can cut, how much material it costs and what job it does in an assembly. */
public enum PartType implements StringRepresentable {
	CABEZA_PICO(Role.HEAD, 3),
	CABEZA_HACHA(Role.HEAD, 3),
	CABEZA_PALA(Role.HEAD, 1),
	CABEZA_AZADA(Role.HEAD, 2),
	CABEZA_MARTILLO(Role.HEAD, 6),
	HOJA(Role.HEAD, 2),
	PUNTA_LANZA(Role.HEAD, 2),
	/** Three prongs on one collar: it costs more metal than any other head of its size. */
	PUNTA_TRIDENTE(Role.HEAD, 4),
	/** The flat, bevelled edge of a chisel. */
	PUNTA_CINCEL(Role.HEAD, 2),
	CABEZA_MAZO(Role.HEAD, 5),
	/** The claw of a grappling hook: three prongs on a ring. */
	GARFIO(Role.HEAD, 4),
	/** The barding of a horse: the widest plate the table cuts. */
	PLACA_BARDA(Role.PLATE, 8),
	/** The plate of a wolf harness, cut small. */
	PLACA_LOBO(Role.PLATE, 5),
	/** The tip of an arrow: what it does when it arrives. */
	PUNTA_FLECHA(Role.HEAD, 1),
	/** The fletching: light feathers leave the string faster than heavy ones. */
	EMPLUMADO(Role.EXTRA, 1),
	/** The head of a mangual: all the weight of the weapon, at the end of a chain. */
	BOLA(Role.HEAD, 5),
	/** The chain that carries the ball, and what makes a mangual go around a shield. */
	CADENA(Role.EXTRA, 3),
	/** The band over the knuckles of a gauntlet: small, fast, and the only part that bites. */
	NUDILLOS(Role.HEAD, 3),
	MANGO(Role.HANDLE, 1),
	/** The mitt itself: it carries the band and takes the wear of every punch. */
	MANOPLA(Role.HANDLE, 2),
	/** The rivets through the band. Almost nothing on their own, and the cheapest way to put a good
	 * metal on a gauntlet that is otherwise made of what you could afford. */
	REMACHE(Role.EXTRA, 1),
	ATADURA(Role.EXTRA, 1),
	GUARDA(Role.EXTRA, 1),
	PLACA_CASCO(Role.PLATE, 5),
	PLACA_PECHERA(Role.PLATE, 8),
	PLACA_GREBAS(Role.PLATE, 7),
	PLACA_BOTAS(Role.PLATE, 4),
	FORRO(Role.LINING, 2),
	/** The wing sheet of a pair of alas: light materials glide, heavy ones drop. */
	MEMBRANA(Role.PLATE, 6),
	BRAZOS_ARCO(Role.HEAD, 3),
	CUERDA(Role.EXTRA, 2),
	PLACA_ESCUDO(Role.PLATE, 6),
	BORDE_ESCUDO(Role.EXTRA, 2),
	/**
	 * The stone a staff or a tome is built round, cut like any other head. Its material is the magic:
	 * the colour of what is thrown and the bite of it (magic/Spellcasting).
	 */
	NUCLEO(Role.HEAD, 3),
	/** The metal that holds a staff's núcleo: the crescent. */
	ENGASTE(Role.EXTRA, 2),
	/** The boards of a forged tome. They are to it what a handle is to a tool: how long it lasts. */
	TAPAS(Role.HANDLE, 4);

	public static final Codec<PartType> CODEC = StringRepresentable.fromEnum(PartType::values);
	public static final StreamCodec<ByteBuf, PartType> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

	public enum Role {
		/** Decides tier, mining speed and damage. */
		HEAD,
		/** Scales durability and attack speed. */
		HANDLE,
		/** Binding or guard: a little extra durability. */
		EXTRA,
		/** Decides armor points, toughness and the armor's look. */
		PLATE,
		/** Scales armor durability and adds a bit of toughness. */
		LINING
	}

	public final Role role;
	public final int cost;

	PartType(Role role, int cost) {
		this.role = role;
		this.cost = cost;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String getSerializedName() {
		return this.id();
	}

	public Component displayName() {
		return Component.translatable("part.forja." + this.id());
	}

	public boolean accepts(ForgeMaterial material) {
		return this.role != Role.HEAD || material.canBeHead;
	}
}
