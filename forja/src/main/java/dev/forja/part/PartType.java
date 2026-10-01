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
	TAPAS(Role.HANDLE, 4),
	/**
	 * A handle with a counterweight in its butt: the blow lands later and harder (combat/Grip). It goes
	 * wherever a handle goes, in anything a handle is made of. The four variants come last: parts go over the
	 * wire by their place in this list.
	 */
	MANGO_PESADO(Role.HANDLE, 2, MANGO, PartVariant.PESADO),
	/** A slim, hollowed handle: the blow comes sooner and costs less breath, and hits softer. */
	MANGO_LIGERO(Role.HANDLE, 1, MANGO, PartVariant.LIGERO),
	/** Rivets and iron bands instead of a wrap: it lasts, and it holds the head when something tries to knock it loose. */
	ATADURA_PESADA(Role.EXTRA, 2, ATADURA, PartVariant.PESADO),
	/** A thin wrap: a little lighter and a little quicker, and it gives sooner. */
	ATADURA_LIGERA(Role.EXTRA, 1, ATADURA, PartVariant.LIGERO);

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
	/** Which plain part this one stands in for in an assembly (itself for the plain ones). */
	private final @org.jspecify.annotations.Nullable PartType base;
	public final PartVariant variant;

	PartType(Role role, int cost) {
		this(role, cost, null, PartVariant.NORMAL);
	}

	PartType(Role role, int cost, @org.jspecify.annotations.Nullable PartType base, PartVariant variant) {
		this.role = role;
		this.cost = cost;
		this.base = base;
		this.variant = variant;
	}

	/** The plain part this one fills the slot of: a heavy handle is a handle to every recipe. */
	public PartType base() {
		return this.base == null ? this : this.base;
	}

	/** The part that is this plain part made in that variant, or the plain part itself when there is none. */
	public static PartType of(PartType base, PartVariant variant) {
		if (variant != PartVariant.NORMAL) {
			for (PartType part : values()) {
				if (part.base == base && part.variant == variant) {
					return part;
				}
			}
		}
		return base;
	}

	/** Whether some plain part of this one has heavy and light variants (the handle and the binding). */
	public boolean hasVariants() {
		return of(this, PartVariant.PESADO) != this;
	}

	/**
	 * Whether a bench can cut this part out of anything at all. Every part can now (a heavy or a light handle
	 * or binding takes whatever the plain one takes, wood included); what cannot be cut is the material,
	 * metal, which is poured through a mould (CastingBoxBlockEntity).
	 */
	public boolean cuttable() {
		for (ForgeMaterial material : ForgeMaterial.BASIC) {
			if (this.accepts(material)) {
				return true;
			}
		}
		return false;
	}

	/** The material the part is drawn in when none is chosen: iron, or wood for a part that cannot be iron. */
	public ForgeMaterial showcase() {
		return this.accepts(ForgeMaterial.HIERRO) ? ForgeMaterial.HIERRO : ForgeMaterial.MADERA;
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

	/**
	 * Whether this part can be made of that material. A heavy or a light handle or binding takes exactly what
	 * the plain one takes (Andy, 2026-09-30): the variant is its shape, and a light handle of netherite or a
	 * heavy one of oak is the player's call.
	 */
	public boolean accepts(ForgeMaterial material) {
		if (this.base != null) {
			return this.base.accepts(material);
		}
		return this.role != Role.HEAD || material.canBeHead;
	}
}
