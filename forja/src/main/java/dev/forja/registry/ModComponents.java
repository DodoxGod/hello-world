package dev.forja.registry;

import java.util.function.UnaryOperator;

import dev.forja.Forja;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.upgrade.Upgrades;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModComponents {
	/** Material of a loose part. */
	public static final DataComponentType<ForgeMaterial> MATERIAL = register(
		"material", b -> b.persistent(ForgeMaterial.CODEC).networkSynchronized(ForgedParts.MATERIAL_STREAM_CODEC)
	);
	/** Materials of every slot of an assembled tool, weapon or armor piece. */
	public static final DataComponentType<ForgedParts> PARTS = register(
		"partes", b -> b.persistent(ForgedParts.CODEC).networkSynchronized(ForgedParts.STREAM_CODEC)
	);

	/** The part shape engraved on a template; blank templates have none. */
	public static final DataComponentType<PartType> PATTERN = register(
		"molde", b -> b.persistent(PartType.CODEC).networkSynchronized(PartType.STREAM_CODEC)
	);

	/** The part a casting mould was taken from; see item/CastingMouldItem. */
	public static final DataComponentType<PartType> MOULD = register(
		"pieza_moldeada", b -> b.persistent(PartType.CODEC).networkSynchronized(PartType.STREAM_CODEC)
	);

	/** What a strainer is made of, and so how hot a pour it will survive; see item/StrainerItem. */
	public static final DataComponentType<dev.forja.material.ForgeMaterial> STRAINER = register(
		"colador", b -> b.persistent(dev.forja.material.ForgeMaterial.CODEC).networkSynchronized(ForgedParts.MATERIAL_STREAM_CODEC)
	);

	/** The night a jar caught, which is also what colours the glass; see item/EssenceJarItem. */
	public static final DataComponentType<dev.forja.world.WorldEvents> ESENCIA = register(
		"esencia", b -> b.persistent(dev.forja.world.WorldEvents.CODEC).networkSynchronized(dev.forja.world.WorldEvents.STREAM_CODEC)
	);

	/**
	 * Potencial: what a piece was forged with toward how far its upgrades can go, not counting what its
	 * parts add (that is {@link #COLADAS}) or anything it has earned since. See forge/Potential.
	 * Absent on anything forged before the potential existed, which is taken to be a middling piece.
	 */
	public static final DataComponentType<Integer> POTENCIAL = register(
		"potencial", b -> b.persistent(com.mojang.serialization.Codec.intRange(0, 100)).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
	);

	/**
	 * Which slots of an assembled piece hold a part that was poured clean, one bit a slot. A mask and not
	 * a count so that changing a part sets or clears exactly its own bit: a count could only ever go up,
	 * and swapping the same cast blade in and out would have been a way to print potential.
	 */
	public static final DataComponentType<Integer> COLADAS = register(
		"coladas", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
	);

	/** On a loose part: it was poured in the foundry, cleanly, rather than cut at the bench. */
	public static final DataComponentType<Boolean> COLADA = register(
		"colada", b -> b.persistent(com.mojang.serialization.Codec.BOOL).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
	);

	/** A part poured without a strainer that held: the finished piece is worth less for it. */
	public static final DataComponentType<Boolean> ROUGH = register(
		"basta", b -> b.persistent(com.mojang.serialization.Codec.BOOL).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
	);

	/** Maestria experience of an assembled item; it grows with use, so it must not replay the hand swap animation. */
	/**
	 * Marco: the shape of a whole finished tool, cut into refractory steel.
	 *
	 * <p>A mould is one part. A frame is the assembly: every hollow of a pickaxe at once, which is why
	 * it costs a finished pickaxe to cut and why only the casting tables can fill one.
	 */
	public static final DataComponentType<dev.forja.forge.ForgeType> MARCO = register(
		"marco", b -> b.persistent(dev.forja.forge.ForgeType.CODEC).networkSynchronized(dev.forja.forge.ForgeType.STREAM_CODEC)
	);

	/**
	 * Carga: what a voltaic weapon has stored up. Every blow puts one in, and when it is full the next
	 * one goes out sideways into whatever else is standing close. Ignored by the swap animation, or the
	 * weapon would flash in your hand on every hit.
	 */
	public static final DataComponentType<Integer> CARGA = register(
		"carga", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).ignoreSwapAnimation()
	);

	public static final DataComponentType<Integer> MAESTRIA = register(
		"maestria", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).ignoreSwapAnimation()
	);

	/** The upgrade stored in an upgrade orb. */
	public static final DataComponentType<dev.forja.upgrade.UpgradeOrb> ORBE = register(
		"orbe", b -> b.persistent(dev.forja.upgrade.UpgradeOrb.CODEC).networkSynchronized(dev.forja.upgrade.UpgradeOrb.STREAM_CODEC)
	);

	/** The gift a seal carries, by its id. */
	public static final DataComponentType<String> SELLO = register(
		"sello", b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
	);

	/** The Maestria gift engraved on an item, by its id. */
	public static final DataComponentType<String> DON = register(
		"don", b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
	);

	/** The legend an item is, for the ones that turn up already named in treasure. */
	public static final DataComponentType<String> LEYENDA = register(
		"leyenda", b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
	);

	/** How far the copper in a piece has gone green, from 0 to 3. */
	public static final DataComponentType<Integer> OXIDO = register(
		"oxido", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).ignoreSwapAnimation()
	);

	/** Set when honeycomb has sealed the copper where it is. */
	public static final DataComponentType<Boolean> ENCERADO = register(
		"encerado", b -> b.persistent(com.mojang.serialization.Codec.BOOL).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
	);

	/** The gem a talisman holds, by its id. */
	public static final DataComponentType<String> TALISMAN = register(
		"talisman", b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
	);

	/** Game time the tool belt will swap again; it keeps the swap from being spammed. */
	public static final DataComponentType<Long> CINTURON = register(
		"cinturon", b -> b.persistent(com.mojang.serialization.Codec.LONG).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG).ignoreSwapAnimation()
	);

	/** Set on a piece that came out of a well timed press: every stat of it is a little higher. */
	/** Set once a piece has everything a smith can give it; see {@link dev.forja.forge.Masterpiece}. */
	public static final DataComponentType<Boolean> OBRA_MAESTRA = register(
		"obra_maestra", builder -> builder.persistent(com.mojang.serialization.Codec.BOOL).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
	);

	public static final DataComponentType<Boolean> PERFECTA = register(
		"perfecta", b -> b.persistent(com.mojang.serialization.Codec.BOOL).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
	);

	/** The quench a freshly forged piece was cooled in, by its id. Permanent. */
	public static final DataComponentType<String> TEMPLE = register(
		"temple", b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
	);

	/** Game time a freshly forged piece stops being hot enough to quench. */
	public static final DataComponentType<Long> CALIENTE = register(
		"caliente", b -> b.persistent(com.mojang.serialization.Codec.LONG).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG).ignoreSwapAnimation()
	);

	/** The name of the smith who forged the piece. */
	public static final DataComponentType<String> HERRERO = register(
		"herrero", b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
	);

	/** The smith of the piece, by uuid, which is what affinity is checked against. */
	public static final DataComponentType<String> HERRERO_ID = register(
		"herrero_id", b -> b.persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8)
	);

	/** What the piece has done: kills, blocks, seconds in the air, fish. */
	public static final DataComponentType<dev.forja.forge.ItemHistory> HISTORIA = register(
		"historia", b -> b.persistent(dev.forja.forge.ItemHistory.CODEC).networkSynchronized(dev.forja.forge.ItemHistory.STREAM_CODEC).ignoreSwapAnimation()
	);

	/** Flight left in a pair of wings, in ticks; it drains in the air and fills on the ground. */
	public static final DataComponentType<Integer> VUELO = register(
		"vuelo", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).ignoreSwapAnimation()
	);

	/** Upgrade progress of an assembled item. */
	public static final DataComponentType<Upgrades> UPGRADES = register(
		"mejoras", b -> b.persistent(Upgrades.CODEC).networkSynchronized(Upgrades.STREAM_CODEC)
	);

	private ModComponents() {
	}

	private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Forja.id(name), builder.apply(DataComponentType.builder()).build());
	}

	public static void init() {
	}
}
