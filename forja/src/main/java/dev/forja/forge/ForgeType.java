package dev.forja.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import dev.forja.part.PartType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import static dev.forja.part.PartType.ATADURA;
import static dev.forja.part.PartType.ENGASTE;
import static dev.forja.part.PartType.NUCLEO;
import static dev.forja.part.PartType.TAPAS;
import static dev.forja.part.PartType.BORDE_ESCUDO;
import static dev.forja.part.PartType.BRAZOS_ARCO;
import static dev.forja.part.PartType.CABEZA_AZADA;
import static dev.forja.part.PartType.CABEZA_HACHA;
import static dev.forja.part.PartType.CABEZA_MARTILLO;
import static dev.forja.part.PartType.CABEZA_MAZO;
import static dev.forja.part.PartType.CABEZA_PALA;
import static dev.forja.part.PartType.CABEZA_PICO;
import static dev.forja.part.PartType.CUERDA;
import static dev.forja.part.PartType.FORRO;
import static dev.forja.part.PartType.GUARDA;
import static dev.forja.part.PartType.HOJA;
import static dev.forja.part.PartType.BOLA;
import static dev.forja.part.PartType.EMPLUMADO;
import static dev.forja.part.PartType.GARFIO;
import static dev.forja.part.PartType.PLACA_BARDA;
import static dev.forja.part.PartType.PLACA_LOBO;
import static dev.forja.part.PartType.PUNTA_FLECHA;
import static dev.forja.part.PartType.CADENA;
import static dev.forja.part.PartType.MANOPLA;
import static dev.forja.part.PartType.MANGO;
import static dev.forja.part.PartType.NUDILLOS;
import static dev.forja.part.PartType.REMACHE;
import static dev.forja.part.PartType.MEMBRANA;
import static dev.forja.part.PartType.PLACA_BOTAS;
import static dev.forja.part.PartType.PLACA_CASCO;
import static dev.forja.part.PartType.PLACA_ESCUDO;
import static dev.forja.part.PartType.PLACA_GREBAS;
import static dev.forja.part.PartType.PLACA_PECHERA;
import static dev.forja.part.PartType.PUNTA_LANZA;
import static dev.forja.part.PartType.PUNTA_CINCEL;
import static dev.forja.part.PartType.PUNTA_TRIDENTE;

/**
 * Every item the forge can assemble. The multiset of part types is the recipe: whatever parts you
 * drop in the table decide which of these comes out.
 */
public enum ForgeType implements StringRepresentable {
	PICO(Kind.TOOL, List.of(CABEZA_PICO, MANGO, ATADURA), 1.0F, -2.8F, 1.0F, 1.0F, 0.0F, 0, true),
	HACHA(Kind.TOOL, List.of(CABEZA_HACHA, MANGO, ATADURA), 5.0F, -3.0F, 1.0F, 1.0F, 5.0F, 0, true),
	PALA(Kind.TOOL, List.of(CABEZA_PALA, MANGO, ATADURA), 1.5F, -3.0F, 1.0F, 1.0F, 0.0F, 0, true),
	AZADA(Kind.TOOL, List.of(CABEZA_AZADA, MANGO, ATADURA), 0.0F, -1.0F, 1.0F, 1.0F, 0.0F, 0, false),
	MARTILLO(Kind.TOOL, List.of(CABEZA_MARTILLO, MANGO, ATADURA), 6.0F, -3.4F, 0.6F, 2.5F, 0.0F, 1, true),
	PICAHACHA(Kind.TOOL, List.of(CABEZA_PICO, CABEZA_HACHA, MANGO), 4.0F, -3.0F, 0.9F, 1.5F, 3.0F, 0, true),
	/** A blade on a handle for working stone, not for mining it: right-click carves, it never digs fast. */
	CINCEL(Kind.TOOL, List.of(PUNTA_CINCEL, MANGO), 0.0F, -2.0F, 0.4F, 0.6F, 0.0F, 0, false),
	ESPADA(Kind.WEAPON, List.of(HOJA, MANGO, GUARDA), 3.0F, -2.4F, 1.0F, 1.0F, 0.0F, 0, false),
	/** The only weapon whose head can be thrown: with Lanzacabezas the blade flies and comes back. */
	DAGA(Kind.WEAPON, List.of(HOJA, MANGO), 1.5F, -1.5F, 1.0F, 0.75F, 0.0F, 0, true),
	ESPADON(Kind.WEAPON, List.of(HOJA, HOJA, MANGO, GUARDA), 6.0F, -3.1F, 1.0F, 1.8F, 0.0F, 0, false),
	/** Attack speed and the charge attack come from the tip's material, see ForgeStats. */
	LANZA(Kind.WEAPON, List.of(PUNTA_LANZA, MANGO, ATADURA), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0, false),
	MAZO(Kind.WEAPON, List.of(CABEZA_MAZO, MANGO, ATADURA), 3.0F, -3.4F, 1.0F, 2.0F, 0.0F, 0, false),
	/**
	 * Three prongs on a shaft: it reaches further than a sword, it is thrown whole, and its two upgrades
	 * only work where there is water or a storm. Slower than a sword and it costs four ingots of head.
	 */
	TRIDENTE(Kind.WEAPON, List.of(PUNTA_TRIDENTE, MANGO, ATADURA), 4.0F, -2.9F, 1.0F, 1.2F, 0.0F, 0, false),
	/**
	 * A ball on a chain: slow and heavy, it hits everything around what it lands on and goes around a
	 * raised shield instead of stopping at it (see CombatUpgrades).
	 */
	MANGUAL(Kind.WEAPON, List.of(BOLA, CADENA, MANGO), 5.0F, -3.2F, 1.0F, 1.7F, 1.5F, 2, false),
	/** Studded gloves: little damage per punch, but the fastest hands and the quickest Maestria in the mod. */
	GUANTELETES(Kind.WEAPON, List.of(MANOPLA, NUDILLOS, REMACHE), 0.5F, -0.7F, 1.0F, 0.7F, 0.0F, 0, false),
	/** A slow sweeping blade with a longer reach that also reaps crops, see FieldUpgrades. */
	GUADANA(Kind.WEAPON, List.of(HOJA, MANGO, ATADURA), 4.0F, -3.0F, 1.0F, 1.3F, 0.0F, 0, false),
	CASCO(ArmorType.HELMET, List.of(PLACA_CASCO, FORRO)),
	PECHERA(ArmorType.CHESTPLATE, List.of(PLACA_PECHERA, FORRO)),
	GREBAS(ArmorType.LEGGINGS, List.of(PLACA_GREBAS, FORRO)),
	BOTAS(ArmorType.BOOTS, List.of(PLACA_BOTAS, FORRO)),
	ARCO(Kind.RANGED, List.of(BRAZOS_ARCO, CUERDA, MANGO), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0, false),
	/** The guard works as the trigger; limbs, string and stock are the bow's parts. */
	BALLESTA(Kind.RANGED, List.of(BRAZOS_ARCO, CUERDA, MANGO, GUARDA), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0, false),
	ESCUDO(Kind.SHIELD, List.of(PLACA_ESCUDO, BORDE_ESCUDO, MANGO), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0, false),
	/** The rod is the handle and the line is the string; Cebo and Suerte del mar are its upgrades. */
	CANA(Kind.PESCA, List.of(MANGO, CUERDA), 0.0F, -1.0F, 1.0F, 1.0F, 0.0F, 0, false),
	/**
	 * A claw on a rope. Throw it at a wall and it pulls you to it; throw it at something alive and it
	 * pulls that towards you instead. The rope decides how far it reaches.
	 */
	GANCHO(Kind.TOOL, List.of(GARFIO, CUERDA, MANGO), 1.0F, -2.6F, 1.0F, 1.0F, 0.0F, 0, false),
	/** Barding: worn by horses, donkeys and mules, and only cut at the saddlery. */
	BARDA(Kind.MONTURA, List.of(PLACA_BARDA, FORRO), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0, false),
	/** A harness for a wolf, the same idea in a smaller size. */
	ARMADURA_DE_LOBO(Kind.MONTURA, List.of(PLACA_LOBO, FORRO), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0, false),
	/** Arrows by the handful: the tip decides the bite, the fletching the speed off the string. */
	FLECHA(Kind.MUNICION, List.of(PUNTA_FLECHA, EMPLUMADO), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0, false),
	/** Wings for the chest slot: the membrane's material decides how far a fall carries you. */
	ALAS(Kind.ALAS, List.of(MEMBRANA, FORRO), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0, false),
	/**
	 * The crescent staff: use it and it throws a bolt in the colour of its núcleo (magic/Spellcasting).
	 * Andy picked the shape from three drawings; it is a poor club and is not meant to be swung.
	 */
	BACULO(Kind.WEAPON, List.of(NUCLEO, ENGASTE, MANGO), 1.0F, -2.6F, 1.0F, 1.0F, 0.0F, 0, false),
	/**
	 * The forged tome: use it and an area opens five blocks ahead, in the colour of the circle on its
	 * cover, and leaves a rune behind. The boards are its handle and the rivets its corner plates.
	 */
	GRIMORIO(Kind.WEAPON, List.of(NUCLEO, TAPAS, REMACHE), 0.5F, -2.0F, 1.0F, 1.2F, 0.0F, 0, false);

	public static final Codec<ForgeType> CODEC = StringRepresentable.fromEnum(ForgeType::values);
	public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, ForgeType> STREAM_CODEC =
		net.minecraft.network.codec.ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

	public enum Kind {
		TOOL,
		WEAPON,
		ARMOR,
		RANGED,
		SHIELD,
		PESCA,
		ALAS,
		/** Arrows: a stack of them, with no durability of their own. */
		MUNICION,
		/** Armor for something that is not you: a horse or a wolf. */
		MONTURA
	}

	public final Kind kind;
	/** Part slots in a fixed order; material colors and the parts component use the same order. */
	public final List<PartType> slots;
	public final float attackDamage;
	public final float attackSpeed;
	public final float miningSpeedMultiplier;
	public final float durabilityMultiplier;
	public final float disableBlockingSeconds;
	/** Built-in area mining radius: 1 means 3x3. */
	public final int areaRadius;
	/** Whether the Lanzacabezas enchantment can throw this item's head. */
	public final boolean throwableHead;
	public final @Nullable ArmorType armorType;

	ForgeType(
		Kind kind, List<PartType> slots, float attackDamage, float attackSpeed, float miningSpeedMultiplier, float durabilityMultiplier,
		float disableBlockingSeconds, int areaRadius, boolean throwableHead
	) {
		this.kind = kind;
		this.slots = slots;
		this.attackDamage = attackDamage;
		this.attackSpeed = attackSpeed;
		this.miningSpeedMultiplier = miningSpeedMultiplier;
		this.durabilityMultiplier = durabilityMultiplier;
		this.disableBlockingSeconds = disableBlockingSeconds;
		this.areaRadius = areaRadius;
		this.throwableHead = throwableHead;
		this.armorType = null;
	}

	ForgeType(ArmorType armorType, List<PartType> slots) {
		this.kind = Kind.ARMOR;
		this.slots = slots;
		this.attackDamage = 0.0F;
		this.attackSpeed = 0.0F;
		this.miningSpeedMultiplier = 1.0F;
		this.durabilityMultiplier = 1.0F;
		this.disableBlockingSeconds = 0.0F;
		this.areaRadius = 0;
		this.throwableHead = false;
		this.armorType = armorType;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** The plain name of the kind of thing this is, with no material in front of it. */
	public net.minecraft.network.chat.Component displayName() {
		return net.minecraft.network.chat.Component.translatable("item.forja." + this.id());
	}

	@Override
	public String getSerializedName() {
		return this.id();
	}

	/** Block tag a head in this slot mines efficiently, or null for weapons and armor. */
	public @Nullable TagKey<Block> mineableFor(PartType head) {
		return switch (head) {
			case CABEZA_PICO, CABEZA_MARTILLO -> BlockTags.MINEABLE_WITH_PICKAXE;
			case CABEZA_HACHA -> BlockTags.MINEABLE_WITH_AXE;
			case CABEZA_PALA -> BlockTags.MINEABLE_WITH_SHOVEL;
			case CABEZA_AZADA -> BlockTags.MINEABLE_WITH_HOE;
			default -> null;
		};
	}

	/** Index of the first slot holding the given role, or -1. */
	public int slotOf(PartType.Role role) {
		for (int i = 0; i < this.slots.size(); i++) {
			if (this.slots.get(i).role == role) {
				return i;
			}
		}
		return -1;
	}

	public List<Integer> slotsOf(PartType.Role role) {
		List<Integer> result = new ArrayList<>();
		for (int i = 0; i < this.slots.size(); i++) {
			if (this.slots.get(i).role == role) {
				result.add(i);
			}
		}
		return result;
	}

	/** The type whose slots are exactly this multiset of part types, if any. */
	public static @Nullable ForgeType match(List<PartType> parts) {
		for (ForgeType type : values()) {
			if (sameMultiset(type.slots, parts)) {
				return type;
			}
		}
		return null;
	}

	/** Parts still missing for the closest type that contains everything placed so far. */
	public static @Nullable List<PartType> missingFor(List<PartType> parts) {
		List<PartType> best = null;
		for (ForgeType type : values()) {
			List<PartType> remaining = new ArrayList<>(type.slots);
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
