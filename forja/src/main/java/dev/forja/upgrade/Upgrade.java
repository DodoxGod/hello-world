package dev.forja.upgrade;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import dev.forja.forge.ForgeType;
import dev.forja.part.PartType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

import static dev.forja.forge.ForgeType.ARCO;
import static dev.forja.forge.ForgeType.TRIDENTE;
import static dev.forja.forge.ForgeType.ALAS;
import static dev.forja.forge.ForgeType.AZADA;
import static dev.forja.forge.ForgeType.BACULO;
import static dev.forja.forge.ForgeType.BALLESTA;
import static dev.forja.forge.ForgeType.BOTAS;
import static dev.forja.forge.ForgeType.CANA;
import static dev.forja.forge.ForgeType.CASCO;
import static dev.forja.forge.ForgeType.DAGA;
import static dev.forja.forge.ForgeType.ESPADA;
import static dev.forja.forge.ForgeType.ESPADON;
import static dev.forja.forge.ForgeType.GREBAS;
import static dev.forja.forge.ForgeType.GRIMORIO;
import static dev.forja.forge.ForgeType.GUADANA;
import static dev.forja.forge.ForgeType.HACHA;
import static dev.forja.forge.ForgeType.FLECHA;
import static dev.forja.forge.ForgeType.GANCHO;
import static dev.forja.forge.ForgeType.GUANTELETES;
import static dev.forja.forge.ForgeType.LANZA;
import static dev.forja.forge.ForgeType.MANGUAL;
import static dev.forja.forge.ForgeType.MARTILLO;
import static dev.forja.forge.ForgeType.MAZO;
import static dev.forja.forge.ForgeType.PALA;
import static dev.forja.forge.ForgeType.PECHERA;
import static dev.forja.forge.ForgeType.PICAHACHA;
import static dev.forja.forge.ForgeType.PICO;

/**
 * Every upgrade the forge can put on an assembled item. Forged items cannot be enchanted the vanilla
 * way; these replace enchantments. Each upgrade is fed with items at the forge table and fills from
 * 0 to 100%, and its effect grows with that percentage.
 *
 * <p>Cheap upgrades take one kind of item, usually with a block form worth nine. Strong ones need a
 * set of two or three different items per step.
 *
 * <p>Upgrades with an {@link #enchantment} are vanilla enchantments in disguise: the percentage maps
 * to a level (Filo at 60% is Sharpness III) that is written, hidden, into the item's enchantments,
 * so vanilla does the actual work. The rest are implemented by the handlers in this package; their
 * numbers live in the static helpers below, which the tooltips also use.
 */
public enum Upgrade implements StringRepresentable {
	// ---------------------------------------------------------------- tools
	LANZACABEZAS(0x9FD3FF, t -> t.throwableHead, combo(25, Items.PISTON, Items.SLIME_BALL, Items.ENDER_PEARL)),
	VETA(0xE8DDCF, t -> t.slots.contains(PartType.CABEZA_PICO) || t == MARTILLO, single(4, Items.QUARTZ), single(16, Items.QUARTZ_BLOCK)),
	EXCAVACION(0xD9A066, t -> t == PICO || t == PALA || t == MARTILLO || t == PICAHACHA, combo(25, Items.TNT, Items.PISTON)),
	LENADOR(0x7DBE5A, t -> t == HACHA || t == PICAHACHA, single(5, ItemTags.SAPLINGS, Items.OAK_SAPLING)),
	FUNDICION(0xFF8C3A, Upgrade::isTool, combo(20, Items.BLAZE_ROD, Items.COAL_BLOCK)),
	TELEQUINESIS(0xC07BFF, Upgrade::isTool, combo(25, Items.ENDER_PEARL, Items.HOPPER)),
	COSECHADOR(0xC8E07A, t -> t == AZADA || t == GUADANA, single(2, Items.BONE_MEAL), single(18, Items.BONE_BLOCK)),
	EFICIENCIA(0xF5F5A0, Upgrade::isTool, Group.NONE, Enchantments.EFFICIENCY, 5, single(2, Items.SUGAR)),
	FORTUNA(0x4F7BE8, Upgrade::isTool, Group.MINING, Enchantments.FORTUNE, 3, single(2, Items.LAPIS_LAZULI), single(18, Items.LAPIS_BLOCK)),
	TOQUE_DE_SEDA(0xF0F0F0, Upgrade::isTool, Group.MINING, Enchantments.SILK_TOUCH, 1, single(25, Items.COBWEB)),
	ALCANCE(0xE6D7FF, t -> t.kind == ForgeType.Kind.TOOL || t.kind == ForgeType.Kind.WEAPON, single(10, Items.END_ROD)),
	LUZ(0xFFE08A, Upgrade::isTool, single(4, Items.GLOWSTONE)),
	SABIDURIA(0x7CFF9A, t -> t.kind == ForgeType.Kind.TOOL || t.kind == ForgeType.Kind.WEAPON, single(5, Items.BOOK)),
	// ---------------------------------------------------------------- weapons (axes count too)
	VAMPIRISMO(0xD0343F, Upgrade::isWeapon, combo(20, Items.GHAST_TEAR, Items.ROTTEN_FLESH)),
	DECAPITADOR(0xE0E0E0, Upgrade::isWeapon, single(4, Items.BONE)),
	ONDA_DE_CHOQUE(0x9BE3E0, Upgrade::isWeapon, combo(20, Items.WIND_CHARGE, Items.GUNPOWDER)),
	TORMENTA(0xFFE45C, Upgrade::isWeapon, combo(20, Items.LIGHTNING_ROD.weathering().unaffected(), Items.GLOWSTONE_DUST)),
	ESCARCHA(0x9CD6FF, Upgrade::isWeapon, single(1, Items.SNOWBALL), single(4, Items.ICE), single(36, Items.PACKED_ICE)),
	VENENO(0x6FC04A, Upgrade::isWeapon, single(5, Items.POISONOUS_POTATO), single(8, Items.SPIDER_EYE)),
	CRITICO(0xFF5555, Upgrade::isWeapon, combo(10, Items.FLINT, Items.FEATHER)),
	FRENESI(0xC98A4B, Upgrade::isWeapon, single(2, Items.COCOA_BEANS)),
	EJECUCION(0x9E2A2A, Upgrade::isWeapon, single(5, Items.NETHER_WART)),
	MATAGIGANTES(0xC9B37E, Upgrade::isWeapon, single(10, Items.PHANTOM_MEMBRANE)),
	FILO(0xE0E8F0, Upgrade::isWeapon, Group.DAMAGE, Enchantments.SHARPNESS, 5, single(4, Items.AMETHYST_SHARD), single(16, Items.AMETHYST_BLOCK)),
	CASTIGO(0xF7E27C, Upgrade::isWeapon, Group.DAMAGE, Enchantments.SMITE, 5, single(2, Items.ROTTEN_FLESH)),
	PERDICION_DE_ARTROPODOS(0x8C5A3C, Upgrade::isWeapon, Group.DAMAGE, Enchantments.BANE_OF_ARTHROPODS, 5, single(2, Items.STRING)),
	BRECHA(0x6B8CFF, Upgrade::isWeapon, Group.DAMAGE, Enchantments.BREACH, 4, single(10, Items.DIAMOND)),
	ASPECTO_IGNEO(0xFF6A00, Upgrade::isWeapon, Group.NONE, Enchantments.FIRE_ASPECT, 2, single(5, Items.BLAZE_POWDER), single(10, Items.FIRE_CHARGE)),
	EMPUJE(0xB0B0B0, Upgrade::isWeapon, Group.NONE, Enchantments.KNOCKBACK, 2, single(10, Items.PISTON)),
	BOTIN(0x3FD46A, Upgrade::isWeapon, Group.NONE, Enchantments.LOOTING, 3, single(10, Items.EMERALD), single(90, Items.EMERALD_BLOCK)),
	FILO_ARRASADOR(0xD8F0FF, t -> t == ESPADA || t == DAGA || t == ESPADON || t == GUADANA, Group.NONE, Enchantments.SWEEPING_EDGE, 3, single(20, Items.SHEARS)),
	// ---------------------------------------------------------------- spear and mace
	EMBESTIDA(0x9FE2BF, t -> t == LANZA, Group.NONE, Enchantments.LUNGE, 3, single(4, Items.SUGAR)),
	DENSIDAD(0x6E6E7E, t -> t == MAZO, Group.DAMAGE, Enchantments.DENSITY, 5, single(4, Items.HEAVY_WEIGHTED_PRESSURE_PLATE), single(50, Items.HEAVY_CORE)),
	ESTALLIDO_DE_VIENTO(0xB8E6F5, t -> t == MAZO, Group.NONE, Enchantments.WIND_BURST, 3, single(10, Items.BREEZE_ROD)),
	// ---------------------------------------------------------------- bow
	PODER(0xE8C170, Upgrade::isBow, Group.NONE, Enchantments.POWER, 5, single(2, Items.ARROW)),
	RETROCESO(0xB0B0B0, Upgrade::isBow, Group.NONE, Enchantments.PUNCH, 2, single(10, Items.PISTON)),
	LLAMA(0xFF7A00, Upgrade::isBow, Group.NONE, Enchantments.FLAME, 1, single(25, Items.FIRE_CHARGE)),
	INFINIDAD(0xF5E6FF, Upgrade::isBow, Group.NONE, Enchantments.INFINITY, 1, combo(20, Items.SPECTRAL_ARROW, Items.GOLD_BLOCK)),
	TENSION(0xD9C9A8, t -> t == ARCO, single(2, Items.STRING)),
	MULTIDISPARO(0xCFE3FF, Upgrade::isBow, Group.CROSSBOW, Enchantments.MULTISHOT, 1, combo(25, Items.ARROW, Items.FEATHER)),
	CARGA_RAPIDA(0xE6C27A, t -> t == BALLESTA, Group.NONE, Enchantments.QUICK_CHARGE, 3, single(10, Items.TRIPWIRE_HOOK)),
	PERFORACION(0xB7C3D0, t -> t == BALLESTA, Group.CROSSBOW, Enchantments.PIERCING, 4, single(5, Items.FLINT)),
	RETORNO(0x8A6BE2, t -> dev.forja.upgrade.WeaponThrow.throwable(t), single(20, Items.ENDER_PEARL)),
	// ---------------------------------------------------------------- what the sky leaves behind
	/**
	 * Event upgrades. They take no ingredients at all: the only way onto a piece is an orb caught in a
	 * flask while the sky was doing something, which is the whole point of them.
	 */
	LLUVIA_ESTELAR(0xBFE8FF, Upgrade::isWeaponOrTool),
	CONDUCTOR(0xFFE45C, Upgrade::isWeapon),
	SIEGA_DE_ALMAS(0x6BC7C7, Upgrade::isWeapon),
	AURORA(0x9FE2BF, Upgrade::isArmor),
	CARNICERO(0xB3241F, Upgrade::isWeapon),
	SOMBRA_LARGA(0x3B3B4A, Upgrade::isWeaponOrTool),
	TEMPANO(0xD8F0FF, Upgrade::isArmor),
	RESCOLDO(0xFF8A3A, Upgrade::isArmor),
	RESACA(0x3FBFD0, Upgrade::isWeapon),
	// ---------------------------------------------------------------- arrows
	PUNTA_AFILADA(0xE8E8F0, t -> t == FLECHA, single(10, Items.FLINT), single(30, Items.QUARTZ)),
	ASTA_LIGERA(0xF3F0E4, t -> t == FLECHA, single(10, Items.FEATHER), single(30, Items.PHANTOM_MEMBRANE)),
	PUNTA_ENVENENADA(0x7FBF4F, t -> t == FLECHA, combo(20, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE)),
	PUNTA_IGNEA(0xE2622B, t -> t == FLECHA, single(15, Items.BLAZE_POWDER)),
	PUNTA_PERFORANTE(0xB0B0BC, t -> t == FLECHA, single(20, Items.IRON_INGOT), single(50, Items.NETHERITE_SCRAP)),
	// ---------------------------------------------------------------- mounts
	HERRADURA(0xD8D8E0, t -> t.kind == ForgeType.Kind.MONTURA, single(12, Items.IRON_INGOT), single(40, Items.IRON_BLOCK)),
	PETO(0xC9A27A, t -> t.kind == ForgeType.Kind.MONTURA, single(10, Items.COPPER_INGOT), single(30, Items.RAW_COPPER_BLOCK)),
	// ---------------------------------------------------------------- grappling hook
	/** Corriente: in water or rain, using the trident throws you the way you are looking. */
	CORRIENTE(0x3FBFD0, t -> t == TRIDENTE, single(15, Items.PRISMARINE_SHARD), single(40, Items.HEART_OF_THE_SEA)),
	/** Canalizacion: under an open sky in a storm, a hit calls the lightning down on it. */
	CANALIZACION(0xFFE45C, t -> t == TRIDENTE, single(20, Items.COPPER_INGOT), single(45, Items.LIGHTNING_ROD.weathering().unaffected())),
	SIRGA(0xB9C0C8, t -> t == GANCHO, single(15, Items.IRON_CHAIN), single(40, Items.IRON_BLOCK)),
	/** Soga larga: more rope on the drum, so the claw goes further — and drags harder when it bites. */
	SOGA_LARGA(0xCBA36A, t -> t == GANCHO, single(12, Items.STRING), single(35, Items.LEAD)),
	// ---------------------------------------------------------------- pacts
	/**
	 * Pactos: upgrades that take more than they give, and that no grindstone will ever undo. They are
	 * here for the player who would rather hit far harder and live with what it costs.
	 */
	PACTO_DE_SED(0x8B1A1A, Upgrade::isWeapon, combo(25, Items.ROTTEN_FLESH, Items.REDSTONE)),
	PACTO_DE_VIDRIO(0xBFE8F0, Upgrade::isWeapon, single(25, Items.GLASS)),
	PACTO_DE_SOMBRA(0x3B3B4A, Upgrade::isArmor, combo(25, Items.INK_SAC, Items.PHANTOM_MEMBRANE)),
	/** Tools: bites through stone far faster and wears out for it. */
	PACTO_DE_LA_PRISA(0xE8C25A, Upgrade::isTool, combo(25, Items.SUGAR, Items.MAGMA_CREAM)),
	// ---------------------------------------------------------------- mangual and gauntlets
	ATURDIMIENTO(0xE8D26A, t -> t == MANGUAL, single(10, Items.COPPER_INGOT), single(30, Items.BELL)),
	SEGUNDA_CABEZA(0x9A9AA6, t -> t == MANGUAL, combo(25, Items.IRON_CHAIN, Items.IRON_INGOT)),
	RAFAGA(0xFFB0C8, t -> t == GUANTELETES, single(8, Items.SUGAR), single(25, Items.RABBIT_FOOT)),
	NUDILLOS_DE_HIERRO(0xD8D8E0, t -> t == GUANTELETES, single(12, Items.IRON_INGOT), single(40, Items.IRON_BLOCK)),
	DESGARRO(0xB3241F, t -> t == DAGA || t == GUADANA, single(10, Items.FLINT), single(30, Items.QUARTZ)),
	// ---------------------------------------------------------------- wings
	AERODINAMICA(0xA9E2FF, t -> t == ALAS, single(50, Items.ELYTRA)),
	PROPULSION(0xFFB85C, t -> t == ALAS, single(4, Items.GUNPOWDER), single(30, Items.FIREWORK_ROCKET)),
	// ---------------------------------------------------------------- fishing rod
	CEBO(0x9BD3E6, t -> t == CANA, Group.NONE, Enchantments.LURE, 3, single(5, Items.WHEAT_SEEDS), single(20, Items.COD)),
	SUERTE_DEL_MAR(0x4FD0C2, t -> t == CANA, Group.NONE, Enchantments.LUCK_OF_THE_SEA, 3, single(20, Items.NAUTILUS_SHELL), single(100, Items.HEART_OF_THE_SEA)),
	// ---------------------------------------------------------------- shield
	REBOTE(0x8FE07A, Upgrade::isShield, combo(20, Items.SLIME_BALL, Items.ARROW)),
	BUMERAN(0x9BD3E6, Upgrade::isShield, combo(25, Items.ENDER_PEARL, Items.SLIME_BALL)),
	PUAS(0xA88A6B, Upgrade::isShield, single(5, Items.POINTED_DRIPSTONE)),
	REFLEJOS(0xE0C9A0, Upgrade::isShield, single(20, Items.RABBIT_FOOT)),
	ABSORCION(0xFFD24A, Upgrade::isShield, single(2, Items.GOLD_NUGGET), single(18, Items.GOLD_INGOT)),
	REPULSION(0x9BD37A, Upgrade::isShield, single(10, Items.STICKY_PISTON)),
	// ---------------------------------------------------------------- armor
	MAGNETISMO(0xB8C0D0, Upgrade::isArmor, single(2, Items.IRON_INGOT), single(18, Items.IRON_BLOCK)),
	VITALIDAD(0xFF6F8F, t -> t == PECHERA, combo(25, Items.GOLDEN_APPLE, Items.GLISTERING_MELON_SLICE)),
	RESORTE(0x7FDB6A, t -> t == BOTAS, single(4, Items.SLIME_BALL), single(36, Items.SLIME_BLOCK)),
	PRESTEZA(0xFF4040, t -> t == GREBAS, single(1, Items.REDSTONE), single(9, Items.REDSTONE_BLOCK)),
	VISION_NOCTURNA(0xFFD27A, t -> t == CASCO, single(10, Items.GOLDEN_CARROT)),
	REPRESALIA(0xFF7A1F, t -> t == PECHERA, single(5, Items.MAGMA_CREAM), single(20, Items.MAGMA_BLOCK)),
	REGENERACION(0xFF9FC8, Upgrade::isArmor, single(10, Items.GHAST_TEAR)),
	ZANCADA(0xC49A6C, t -> t == BOTAS, single(10, Items.RABBIT_HIDE)),
	NUTRICION(0xE3B45A, t -> t == PECHERA, single(5, Items.BREAD), single(45, Items.HAY_BLOCK)),
	SONAR(0x4FD1C5, t -> t == CASCO, single(25, Items.SPYGLASS), single(50, Items.ECHO_SHARD)),
	PURIFICACION(0xF2C14E, Upgrade::isArmor, single(10, Items.HONEY_BOTTLE)),
	ANCLAJE(0x6E6A62, t -> t == BOTAS, single(4, Items.IRON_BARS), single(20, Items.ANVIL)),
	AISLANTE(0x9FD8E8, Upgrade::isArmor, single(2, Items.CLAY_BALL), single(18, Items.CLAY)),
	TEMPLE(0xD8C46A, Upgrade::isArmor, single(5, Items.BLAZE_POWDER), single(12, Items.BLAZE_ROD)),
	PROTECCION(0xC9A27A, Upgrade::isArmor, Group.PROTECTION, Enchantments.PROTECTION, 4, single(2, Items.COPPER_INGOT), single(18, Items.COPPER_BLOCK.weathering().unaffected())),
	PROTECCION_CONTRA_FUEGO(0xFF9F5A, Upgrade::isArmor, Group.PROTECTION, Enchantments.FIRE_PROTECTION, 4, single(4, Items.ICE), single(36, Items.PACKED_ICE)),
	PROTECCION_CONTRA_EXPLOSIONES(0x8A8A8A, Upgrade::isArmor, Group.PROTECTION, Enchantments.BLAST_PROTECTION, 4, single(2, Items.GUNPOWDER)),
	PROTECCION_CONTRA_PROYECTILES(0xB39B7A, Upgrade::isArmor, Group.PROTECTION, Enchantments.PROJECTILE_PROTECTION, 4, single(2, Items.ARROW)),
	ESPINAS(0x4E9A3A, Upgrade::isArmor, Group.NONE, Enchantments.THORNS, 3, single(2, Items.CACTUS)),
	RESPIRACION(0x4FB3D9, t -> t == CASCO, Group.NONE, Enchantments.RESPIRATION, 3, single(2, Items.KELP)),
	AFINIDAD_ACUATICA(0x6FD0E0, t -> t == CASCO, Group.NONE, Enchantments.AQUA_AFFINITY, 1, single(25, Items.SPONGE)),
	CAIDA_DE_PLUMA(0xF4F4F4, t -> t == BOTAS, Group.NONE, Enchantments.FEATHER_FALLING, 4, single(2, Items.FEATHER)),
	AGILIDAD_ACUATICA(0x3A5FA8, t -> t == BOTAS, Group.BOOTS, Enchantments.DEPTH_STRIDER, 3, single(2, Items.INK_SAC)),
	PASO_HELADO(0xA8E4FF, t -> t == BOTAS, Group.BOOTS, Enchantments.FROST_WALKER, 2, single(25, Items.BLUE_ICE)),
	VELOCIDAD_DE_ALMA(0x6B5140, t -> t == BOTAS, Group.NONE, Enchantments.SOUL_SPEED, 3, single(2, Items.SOUL_SAND)),
	SIGILO_VELOZ(0x6A4A8C, t -> t == GREBAS, Group.NONE, Enchantments.SWIFT_SNEAK, 3, single(2, ItemTags.WOOL, Items.WOOL.white())),
	// ---------------------------------------------------------------- everything
	IRROMPIBLE(0x7A5AA8, t -> true, Group.NONE, Enchantments.UNBREAKING, 3, single(4, Items.OBSIDIAN)),
	REPARACION(0x9CFF6B, t -> true, Group.NONE, Enchantments.MENDING, 1, combo(10, Items.EXPERIENCE_BOTTLE, Items.GOLD_INGOT)),
	AUTORREPARACION(0x5DAA3F, t -> true, single(5, Items.MOSS_BLOCK)),
	/**
	 * Recocido: packed in clay, brought up to heat and left to cool as slowly as it likes. It does nothing
	 * a blade can feel. What it does is make room: up to fifteen points more of potential for everything
	 * else (forge/Potential). It was last in the list on purpose — upgrades go over the wire by their place in
	 * it — and whatever has come since goes after it for the same reason.
	 */
	RECOCIDO(0xE8A35C, t -> true, combo(20, Items.BLAZE_POWDER, Items.CLAY)),
	// ---------------------------------------------------------------- staff and tome (magic/Spellcasting)
	/**
	 * Andy: "tambien dame mejoras para las armas magicas". Everything a blade takes already rides on a spell
	 * (Filo, Escarcha, Vampirismo...), so these are the ones that are about the <b>casting</b>: how often, how
	 * many, where it goes, and what the rune does while it lies there.
	 */
	/** Conjuro veloz: the wait between two spells, shorter. */
	CONJURO_VELOZ(0xFFE9A8, Upgrade::isMagic, single(2, Items.GLOWSTONE_DUST), single(8, Items.GLOWSTONE)),
	/** Sobrecarga: every fourth spell comes out bigger and hits harder. */
	SOBRECARGA(0xFF5A3C, Upgrade::isMagic, combo(20, Items.REDSTONE_BLOCK, Items.AMETHYST_SHARD)),
	/** Resonancia: the spell sounds twice — a second bolt, a second opening — at a share of its damage. */
	RESONANCIA(0x2FB5B5, Upgrade::isMagic, combo(25, Items.ECHO_SHARD, Items.AMETHYST_SHARD)),
	/** Prisma: the bolt comes out as a fan of three. */
	PRISMA(0x9FE8E0, t -> t == BACULO, combo(20, Items.PRISMARINE_CRYSTALS, Items.GLASS)),
	/** Buscador: the bolt bends towards whatever is hunting you. */
	BUSCADOR(0x7BD36A, t -> t == BACULO, combo(25, Items.ENDER_EYE, Items.FEATHER)),
	/** Tinta indeleble: the rune lies there longer. */
	TINTA_INDELEBLE(0x5468C8, t -> t == GRIMORIO, single(5, Items.INK_SAC), single(15, Items.GLOW_INK_SAC)),
	/** Vortice: every bite of the rune drags what stands on it towards the middle. */
	VORTICE(0x8A5AC8, t -> t == GRIMORIO, combo(20, Items.COBWEB, Items.ENDER_PEARL)),
	/** Santuario: the reader's own rune mends them while they stand on it. */
	SANTUARIO(0xFFD86B, t -> t == GRIMORIO, single(10, Items.GLISTERING_MELON_SLICE));

	public static final Codec<Upgrade> CODEC = StringRepresentable.fromEnum(Upgrade::values);

	/** Vanilla-style exclusive sets: two upgrades of the same group cannot share an item. */
	public enum Group {
		NONE,
		DAMAGE,
		PROTECTION,
		MINING,
		BOOTS,
		CROSSBOW
	}

	/** One accepted item: a specific item or any item in a tag. */
	public record Requirement(@Nullable Item item, @Nullable TagKey<Item> tag, Item display) {
		public boolean test(ItemStack stack) {
			return this.tag != null ? stack.is(this.tag) : stack.is(this.item);
		}

		public ItemStack displayStack() {
			return new ItemStack(this.display);
		}
	}

	/** A set of items consumed together, one of each, for {@code percent} progress. */
	public record Option(List<Requirement> requirements, int percent) {
		public boolean isSingle() {
			return this.requirements.size() == 1;
		}
	}

	public final int color;
	private final Predicate<ForgeType> appliesTo;
	public final Group group;
	public final @Nullable ResourceKey<Enchantment> enchantment;
	public final int maxLevel;
	public final List<Option> options;

	Upgrade(int color, Predicate<ForgeType> appliesTo, Option... options) {
		this(color, appliesTo, Group.NONE, null, 0, options);
	}

	Upgrade(int color, Predicate<ForgeType> appliesTo, Group group, @Nullable ResourceKey<Enchantment> enchantment, int maxLevel, Option... options) {
		this.color = color;
		this.appliesTo = appliesTo;
		this.group = group;
		this.enchantment = enchantment;
		this.maxLevel = maxLevel;
		this.options = List.of(options);
	}

	/** A pact: an upgrade that takes more than it gives, and that nothing will ever take back off. */
	public boolean isPact() {
		return this.name().startsWith("PACTO_");
	}

	private static boolean isTool(ForgeType type) {
		return type.kind == ForgeType.Kind.TOOL;
	}

	private static boolean isWeapon(ForgeType type) {
		return type.kind == ForgeType.Kind.WEAPON || type == HACHA;
	}

	private static boolean isMagic(ForgeType type) {
		return type == BACULO || type == GRIMORIO;
	}

	private static boolean isBow(ForgeType type) {
		return type.kind == ForgeType.Kind.RANGED;
	}

	private static boolean isWeaponOrTool(ForgeType type) {
		return type.kind == ForgeType.Kind.WEAPON || type.kind == ForgeType.Kind.TOOL;
	}

	private static boolean isShield(ForgeType type) {
		return type.kind == ForgeType.Kind.SHIELD;
	}

	private static boolean isArmor(ForgeType type) {
		return type.kind == ForgeType.Kind.ARMOR;
	}

	private static Option single(int percent, Item item) {
		return new Option(List.of(new Requirement(item, null, item)), percent);
	}

	private static Option single(int percent, TagKey<Item> tag, Item display) {
		return new Option(List.of(new Requirement(null, tag, display)), percent);
	}

	private static Option combo(int percent, Item... items) {
		return new Option(Arrays.stream(items).map(item -> new Requirement(item, null, item)).toList(), percent);
	}

	/**
	 * How far a frenzy can push this upgrade. The ones that ask for a single common item climb the most;
	 * the ones that want two or three different things were already strong, so the combo adds little.
	 */
	public float frenzyCeiling() {
		int hardest = 1;
		for (Option option : this.options) {
			hardest = Math.max(hardest, option.requirements().size());
		}
		return switch (hardest) {
			case 1 -> 2.5F;
			case 2 -> 1.9F;
			default -> 1.3F;
		};
	}

	public boolean appliesTo(ForgeType type) {
		return this.appliesTo.test(type);
	}

	public boolean isCompatibleWith(Upgrade other) {
		if (other == this) {
			return true;
		}
		if (this.group != Group.NONE && this.group == other.group) {
			return false;
		}
		// Smelting and Silk Touch fight over the same drops; vanilla keeps Infinity and Mending apart.
		return !(this == FUNDICION && other == TOQUE_DE_SEDA || this == TOQUE_DE_SEDA && other == FUNDICION
			|| this == INFINIDAD && other == REPARACION || this == REPARACION && other == INFINIDAD);
	}

	/** Enchantment level granted at this percentage, 0 below the first step. */
	public int enchantmentLevel(int percent) {
		return this.maxLevel * percent / 100;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String getSerializedName() {
		return this.id();
	}

	public Component displayName() {
		return Component.translatable("upgrade.forja." + this.id());
	}

	// ------------------------------------------------------------------ effect numbers, fraction is 0..1

	public static int headThrowBlocks(float f) {
		return f <= 0 ? 0 : Math.max(1, Math.round(16 * f));
	}

	public static int veinLimit(float f) {
		return f <= 0 ? 0 : Math.max(4, Math.round(48 * f));
	}

	/** Area radius from Excavacion: 3x3 as soon as it has any progress, 5x5 at 100%. */
	public static int excavationRadius(float f) {
		return f <= 0 ? 0 : f >= 1 ? 2 : 1;
	}

	public static int treeLimit(float f) {
		return f <= 0 ? 0 : Math.max(16, Math.round(128 * f));
	}

	public static int harvestRadius(float f) {
		return f <= 0 ? -1 : f < 0.5F ? 0 : f < 1 ? 1 : 2;
	}

	public static float blockReach(float f) {
		return 3.0F * f;
	}

	public static float entityReach(float f) {
		return 1.5F * f;
	}

	public static float lifesteal(float f) {
		return 0.3F * f;
	}

	public static float beheadChance(float f) {
		return 0.25F * f;
	}

	public static float shockwaveShare(float f) {
		return 0.6F * f;
	}

	public static float stormChance(float f) {
		return 0.3F * f;
	}

	public static int frostTicks(float f) {
		return f <= 0 ? 0 : Math.round(20 + 80 * f);
	}

	public static int poisonTicks(float f) {
		return f <= 0 ? 0 : Math.round(20 + 120 * f);
	}

	public static float critChance(float f) {
		return 0.3F * f;
	}

	public static float attackSpeedBoost(float f) {
		return 0.6F * f;
	}

	/** Aerodinamica: the share of your walking speed bonus that turns into push while gliding. */
	public static float glideBoost(float f) {
		return f;
	}

	/**
	 * Aerodinamica: the push per tick the wings get in exchange for their reserve, so a pair that could
	 * stay up a long time becomes a fast one instead. At 100% the flight bar goes away for good.
	 */
	public static float glideSpeed(float f, float flightSeconds) {
		return f * Math.min(2.0F, flightSeconds / 8.0F) * 0.022F;
	}

	/** Lluvia estelar: the share of a blow that comes down as a falling star on top of it. */
	public static float starfallChance(float f) {
		return f * 0.25F;
	}

	/** Conductor: how far the charge jumps from what you hit. */
	public static float conductorRadius(float f) {
		return 2.0F + f * 4.0F;
	}

	/** Siega de almas: the healing a kill hands back, in half hearts. */
	public static float soulReapHealing(float f) {
		return f * 4.0F;
	}

	/** Aurora: the share of magic damage the lights turn aside. */
	public static float auroraReduction(float f) {
		return f * 0.30F;
	}

	/** Punta afilada: what a sharpened tip adds to the bite of the arrow. */
	public static float arrowDamageBonus(float f) {
		return f * 0.5F;
	}

	/** Asta ligera: how much faster the arrow leaves the string. */
	public static float arrowSpeedBonus(float f) {
		return f * 0.25F;
	}

	/** Punta envenenada: seconds of poison the tip carries. */
	public static float arrowPoisonSeconds(float f) {
		return f * 5.0F;
	}

	/** Punta ignea: seconds of fire the tip carries. */
	public static float arrowFireSeconds(float f) {
		return f * 5.0F;
	}

	/** Punta perforante: how many bodies one arrow goes through. */
	public static float arrowPierce(float f) {
		return f * 3.0F;
	}

	/** Herradura: how much quicker the mount moves. */
	public static float mountSpeed(float f) {
		return f * 0.20F;
	}

	/** Peto: the armor the plating adds on top of the material. */
	public static float mountArmor(float f) {
		return f * 4.0F;
	}

	/** Carnicero: what each enemy within five blocks adds to the blow. */
	public static float butcherBonus(float f) {
		return f * 0.08F;
	}

	/** Sombra larga: seconds out of sight after a kill. */
	/** Corriente: how hard the water throws you when you use it. */
	public static float riptidePower(float f) {
		return f * 3.0F;
	}

	/** Canalizacion: how often a hit in a storm brings the lightning down. */
	public static float channelChance(float f) {
		return f * 0.5F;
	}

	public static float shadowSeconds(float f) {
		return f * 3.0F;
	}

	/** Tempano: how often the cold in the plate catches whoever swung at you. */
	public static float frostbiteChance(float f) {
		return f * 0.35F;
	}

	/** Resaca: how hard a hit drags what it hit back towards you. */
	public static float undertowPull(float f) {
		return f * 0.4F;
	}

	/** Sirga: how much harder the winch pulls than the bare claw. */
	public static float winchBonus(float f) {
		return f * 0.6F;
	}

	/** Soga larga: the blocks of rope it adds to the hook's reach. */
	public static float ropeBlocks(float f) {
		return f * 14.0F;
	}

	/** Pacto de sed: the damage it hands you, in exchange for your own hunger on every blow. */
	public static float thirstDamage(float f) {
		return f * 0.32F;
	}

	/** Pacto de sed: the hunger one blow costs. */
	public static float thirstHunger(float f) {
		return f * 4.0F;
	}

	/** Pacto de vidrio: a blade that bites much harder. */
	public static float glassDamage(float f) {
		return f * 0.40F;
	}

	/** Pacto de vidrio: and gives up half of what it could take. */
	public static float glassWear(float f) {
		return f * 0.50F;
	}

	/** Pacto de sombra: the share of the armor of that piece the shadow eats. */
	public static float shadowArmor(float f) {
		return f * 0.25F;
	}

	/** Pacto de la prisa: how much faster the tool bites. */
	public static float hasteSpeed(float f) {
		return f * 0.45F;
	}

	/** And what that haste costs it in durability. */
	public static float hasteWear(float f) {
		return f * 0.45F;
	}

	/** Aturdimiento: how long a flail leaves what it hits unable to do much, in seconds. */
	public static float stunSeconds(float f) {
		return 0.5F + f * 1.0F;
	}

	/** Segunda cabeza: the share of the blow everything around the target takes as well. */
	public static float flailSplash(float f) {
		return 0.3F + f * 0.4F;
	}

	/** Rafaga: the chance a punch lands twice. */
	public static float flurryChance(float f) {
		return f * 0.35F;
	}

	/** Nudillos de hierro: what each step of the combo adds to a punch. */
	public static float knuckleBonus(float f) {
		return f * 0.5F;
	}

	/** Retorno: the chance a thrown weapon finds its way back to your hand. */
	public static float returnChance(float f) {
		return f;
	}

	/** Bumeran: how many enemies one throw of the shield shoves, from one to three. */
	public static int shieldBounces(float f) {
		return f <= 0.0F ? 0 : 1 + Math.round(f * 2.0F);
	}

	/** Desgarro: how many wounds can bleed at once on the same target. */
	public static int bleedStacks(float f) {
		return 2 + Math.round(f * 2.0F);
	}

	/** Propulsion: how hard one pinch of gunpowder shoves you. */
	public static float burstPower(float f) {
		return 0.5F + f * 0.7F;
	}

	public static float magnetRadius(float f) {
		return f <= 0 ? 0 : 2 + 6 * f;
	}

	public static float extraHealth(float f) {
		return Math.round(6 * f * 2) / 2.0F;
	}

	public static float jumpBoost(float f) {
		return 0.25F * f;
	}

	public static float safeFall(float f) {
		return 6 * f;
	}

	public static float speedBoost(float f) {
		return 0.25F * f;
	}

	public static float retaliationSeconds(float f) {
		return f <= 0 ? 0 : 1 + 5 * f;
	}

	/** Extra draw speed of a bow, as a fraction: 0.5 draws 50% faster. */
	public static float drawSpeedBonus(float f) {
		return 0.5F * f;
	}

	public static float reboundChance(float f) {
		return f;
	}

	public static float spikeDamage(float f) {
		return f <= 0 ? 0 : 1 + 3 * f;
	}

	public static float blockDelayReduction(float f) {
		return 0.8F * f;
	}

	/** Chance to place one of the player's torches where a block was mined in the dark. */
	public static float torchChance(float f) {
		return f;
	}

	/** Extra experience from kills, as a fraction of the mob's own. */
	public static float experienceBonus(float f) {
		return f;
	}

	/** Extra experience points per ore mined. */
	public static int oreExperience(float f) {
		return f <= 0 ? 0 : Math.max(1, Math.round(3 * f));
	}

	/** Extra damage against enemies under 30% health, as a fraction of the hit. */
	public static float executeBonus(float f) {
		return 0.5F * f;
	}

	/** Extra damage against enemies with more max health than the attacker, as a fraction of the hit. */
	public static float giantBonus(float f) {
		return 0.4F * f;
	}

	public static float absorptionChance(float f) {
		return f;
	}

	public static float repulsionStrength(float f) {
		return f <= 0 ? 0 : 0.4F + 1.2F * f;
	}

	/** Extra step height in blocks; players step 0.6 blocks by default. */
	public static float stepBoost(float f) {
		return 0.5F * f;
	}

	public static float nutritionChance(float f) {
		return f;
	}

	public static float sonarRadius(float f) {
		return f <= 0 ? 0 : 4 + 12 * f;
	}

	public static float purifyChance(float f) {
		return f;
	}

	/**
	 * Anclaje: how much of a shove it takes out of you. It counts against vanilla knockback through a
	 * knockback-resistance modifier, and against the pulls and launches the mod's own mobs use, which
	 * move you directly and would otherwise ignore resistance entirely.
	 */
	public static float anchorShare(float f) {
		return 0.6F * f;
	}

	/** Aislante: extra fire ticks burned off every second, so you go out sooner. */
	public static int dousing(float f) {
		return Math.round(30 * f);
	}

	/**
	 * Rescoldo: how much of the fire's own damage it gives back as health instead.
	 *
	 * <p>It never makes burning good for you, only cheap: at full it turns the tick into a wash. A smith
	 * who works in a fire all day stops minding it, which is the whole idea.
	 */
	public static float emberShare(float f) {
		return 0.9F * f;
	}

	/** Temple: how much faster a bad effect runs down. */
	public static float resolveShare(float f) {
		return 0.5F * f;
	}

	/** Health restored every 5 seconds. */
	public static float regeneration(float f) {
		return f;
	}

	/** Durability restored every 5 seconds. */
	public static int selfRepair(float f) {
		return f <= 0 ? 0 : Math.max(1, Math.round(4 * f));
	}

	// ------------------------------------------------------------------ staff and tome

	/** Conjuro veloz: the share of the wait between two spells that goes. */
	public static float castHaste(float f) {
		return 0.4F * f;
	}

	/** Sobrecarga: which spell is the big one. */
	public static final int OVERCHARGE_EVERY = 4;

	/** And how much harder it hits: twice as hard at full. */
	public static float overchargeBonus(float f) {
		return f;
	}

	/** Resonancia: the share of the spell its echo is worth. */
	public static float echoShare(float f) {
		return 0.5F * f;
	}

	/** Prisma: what each of the two side bolts is worth, against the one in the middle. */
	public static float prismShare(float f) {
		return 0.6F * f;
	}

	/** Buscador: how far a bolt can turn in a tick, in degrees. */
	public static float seekDegrees(float f) {
		return 12.0F * f;
	}

	/** Tinta indeleble: ticks on top of the six seconds a rune lasts by itself. */
	public static int runeExtraTicks(float f) {
		return Math.round(120.0F * f);
	}

	/** Vortice: the shove towards the middle on every bite, in blocks a tick. */
	public static float vortexPull(float f) {
		return 0.4F * f;
	}

	/** What that shove comes to before the floor has rubbed it out, in blocks: how it is told to the player. */
	public static float vortexDrag(float f) {
		return vortexPull(f) * 2.2F;
	}

	/** Santuario: health the reader gets back on every bite of their own rune, which is two a second. */
	public static float sanctuaryHeal(float f) {
		return 0.5F * f;
	}

	/** What the upgrade does at this percentage, for tooltips and the forge screen. */
	public Component effect(int percent) {
		float f = percent / 100.0F;
		if (this.enchantment != null) {
			int level = this.enchantmentLevel(percent);
			Component name = Component.translatable("enchantment." + this.enchantment.identifier().getNamespace() + "." + this.enchantment.identifier().getPath());
			if (level <= 0) {
				return Component.translatable("upgrade.forja.nivel_al", name, (100 + this.maxLevel - 1) / this.maxLevel);
			}
			return this.maxLevel == 1 ? name : Component.translatable("upgrade.forja.nivel", name, Component.translatable("enchantment.level." + level));
		}
		String key = "upgrade.forja." + this.id() + ".efecto";
		return switch (this) {
			case LANZACABEZAS -> Component.translatable(key, headThrowBlocks(f));
			case VETA -> Component.translatable(key, veinLimit(f));
			case EXCAVACION -> {
				int size = 1 + 2 * excavationRadius(f);
				yield Component.translatable(key, size, size);
			}
			case LENADOR -> Component.translatable(key, treeLimit(f));
			case FUNDICION, TELEQUINESIS -> Component.translatable(key, percent);
			case COSECHADOR -> {
				int size = 1 + 2 * Math.max(0, harvestRadius(f));
				yield Component.translatable(key, size, size);
			}
			case ALCANCE -> Component.translatable(key, number(blockReach(f)), number(entityReach(f)));
			case VAMPIRISMO -> Component.translatable(key, pct(lifesteal(f)));
			case DECAPITADOR -> Component.translatable(key, pct(beheadChance(f)));
			case ONDA_DE_CHOQUE -> Component.translatable(key, pct(shockwaveShare(f)));
			case TORMENTA -> Component.translatable(key, pct(stormChance(f)));
			case ESCARCHA -> Component.translatable(key, number(frostTicks(f) / 20.0F));
			case VENENO -> Component.translatable(key, number(poisonTicks(f) / 20.0F));
			case CRITICO -> Component.translatable(key, pct(critChance(f)));
			case FRENESI -> Component.translatable(key, number(attackSpeedBoost(f)));
			// No ".libre" any more: the full hundred buys a reserve that goes five times as far, not one
			// that never runs out.
			case AERODINAMICA -> Component.translatable(key, pct(glideBoost(f)));
			case PROPULSION -> Component.translatable(key, number(burstPower(f)));
			case RETORNO -> Component.translatable(key, pct(returnChance(f)));
			case LLUVIA_ESTELAR -> Component.translatable(key, pct(starfallChance(f)));
			case CONDUCTOR -> Component.translatable(key, number(conductorRadius(f)));
			case SIEGA_DE_ALMAS -> Component.translatable(key, number(soulReapHealing(f) / 2.0F));
			case AURORA -> Component.translatable(key, pct(auroraReduction(f)));
			case PUNTA_AFILADA -> Component.translatable(key, pct(arrowDamageBonus(f)));
			case ASTA_LIGERA -> Component.translatable(key, pct(arrowSpeedBonus(f)));
			case PUNTA_ENVENENADA -> Component.translatable(key, number(arrowPoisonSeconds(f)));
			case PUNTA_IGNEA -> Component.translatable(key, number(arrowFireSeconds(f)));
			case PUNTA_PERFORANTE -> Component.translatable(key, number(arrowPierce(f)));
			case SIRGA -> Component.translatable(key, pct(winchBonus(f)));
			case SOGA_LARGA -> Component.translatable(key, number(ropeBlocks(f)));
			case HERRADURA -> Component.translatable(key, pct(mountSpeed(f)));
			case PETO -> Component.translatable(key, number(mountArmor(f)));
			case CARNICERO -> Component.translatable(key, pct(butcherBonus(f)));
			case SOMBRA_LARGA -> Component.translatable(key, number(shadowSeconds(f)));
			case CORRIENTE -> Component.translatable(key, number(riptidePower(f)));
			case CANALIZACION -> Component.translatable(key, pct(channelChance(f)));
			case TEMPANO -> Component.translatable(key, pct(frostbiteChance(f)));
			case RESACA -> Component.translatable(key, pct(undertowPull(f)));
			case PACTO_DE_SED -> Component.translatable(key, pct(thirstDamage(f)), number(thirstHunger(f)));
			case PACTO_DE_VIDRIO -> Component.translatable(key, pct(glassDamage(f)), pct(glassWear(f)));
			case PACTO_DE_SOMBRA -> Component.translatable(key, pct(shadowArmor(f)));
			case PACTO_DE_LA_PRISA -> Component.translatable(key, pct(hasteSpeed(f)), pct(hasteWear(f)));
			case ATURDIMIENTO -> Component.translatable(key, number(stunSeconds(f)));
			case SEGUNDA_CABEZA -> Component.translatable(key, pct(flailSplash(f)));
			case RAFAGA -> Component.translatable(key, pct(flurryChance(f)));
			case NUDILLOS_DE_HIERRO -> Component.translatable(key, pct(knuckleBonus(f)));
			case BUMERAN -> Component.translatable(key, shieldBounces(f));
			case DESGARRO -> Component.translatable(key, bleedStacks(f));
			case MAGNETISMO -> Component.translatable(key, number(magnetRadius(f)));
			case VITALIDAD -> Component.translatable(key, number(extraHealth(f) / 2.0F));
			case RESORTE -> Component.translatable(key, number(safeFall(f)));
			case PRESTEZA -> Component.translatable(key, pct(speedBoost(f)));
			case VISION_NOCTURNA -> Component.translatable(percent >= 100 ? key : key + ".inactiva");
			case REPRESALIA -> Component.translatable(key, number(retaliationSeconds(f)));
			case REGENERACION -> Component.translatable(key, number(regeneration(f) / 2.0F));
			case TENSION -> Component.translatable(key, pct(drawSpeedBonus(f)));
			case REBOTE -> Component.translatable(key, pct(reboundChance(f)));
			case PUAS -> Component.translatable(key, number(spikeDamage(f)));
			case REFLEJOS -> Component.translatable(key, pct(blockDelayReduction(f)));
			case AUTORREPARACION -> Component.translatable(key, selfRepair(f));
			case LUZ -> Component.translatable(key, pct(torchChance(f)));
			case SABIDURIA -> Component.translatable(key, pct(experienceBonus(f)), oreExperience(f));
			case EJECUCION -> Component.translatable(key, pct(executeBonus(f)));
			case MATAGIGANTES -> Component.translatable(key, pct(giantBonus(f)));
			case ABSORCION -> Component.translatable(key, pct(absorptionChance(f)));
			case REPULSION -> Component.translatable(key, number(repulsionStrength(f)));
			case ZANCADA -> Component.translatable(key, number(0.6F + stepBoost(f)));
			case NUTRICION -> Component.translatable(key, pct(nutritionChance(f)));
			case SONAR -> Component.translatable(key, number(sonarRadius(f)));
			case PURIFICACION -> Component.translatable(key, pct(purifyChance(f)));
			case ANCLAJE -> Component.translatable(key, pct(anchorShare(f)));
			case AISLANTE -> Component.translatable(key, number(dousing(f) / 20.0F));
			case TEMPLE -> Component.translatable(key, pct(resolveShare(f)));
			case RESCOLDO -> Component.translatable(key, pct(emberShare(f)));
			case RECOCIDO -> Component.translatable(key, dev.forja.forge.Potential.ANNEAL * percent / 100);
			case CONJURO_VELOZ -> Component.translatable(key, pct(castHaste(f)));
			case SOBRECARGA -> Component.translatable(key, OVERCHARGE_EVERY, pct(overchargeBonus(f)));
			case RESONANCIA -> Component.translatable(key, pct(echoShare(f)));
			case PRISMA -> Component.translatable(key, pct(prismShare(f)));
			case BUSCADOR -> Component.translatable(key, number(seekDegrees(f)));
			case TINTA_INDELEBLE -> Component.translatable(key, number(runeExtraTicks(f) / 20.0F));
			case VORTICE -> Component.translatable(key, number(vortexDrag(f)));
			// two bites a second, two points to the heart: the number is the same either way
			case SANTUARIO -> Component.translatable(key, number(sanctuaryHeal(f)));
			default -> Component.empty();
		};
	}

	private static int pct(float fraction) {
		return Math.round(fraction * 100);
	}

	private static String number(float value) {
		return value == Math.floor(value) ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.1f", value);
	}
}
