package dev.forja.material;

import java.util.Locale;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Everything a part can be made of. Tool numbers follow vanilla's ToolMaterial and armor numbers
 * follow vanilla's ArmorMaterials, so a diamond head behaves like a diamond pickaxe.
 *
 * <p>Handle stats only matter when the material is used as a handle, binding, guard or lining:
 * they scale durability and nudge attack and mining speed.
 */
public enum ForgeMaterial implements StringRepresentable {
	//        color     input                             head   dur   speed dmg  tier                                       ench hDur  hAtk   hMine  defense[boots,legs,chest,helm] aDur tough kb    sound
	MADERA(   0xB8894F, ItemTags.PLANKS, null,             true,  59,  2.0F, 0F, BlockTags.INCORRECT_FOR_WOODEN_TOOL,     15, 1.00F, 0.00F, 1.00F, new int[]{1, 2, 3, 1},  6, 0F,   0F,   SoundEvents.ARMOR_EQUIP_LEATHER, Trait.NONE),
	PIEDRA(   0xA3A3A3, ItemTags.STONE_TOOL_MATERIALS, null, true, 131, 4.0F, 1F, BlockTags.INCORRECT_FOR_STONE_TOOL,      5, 0.90F, -0.10F, 0.95F, new int[]{1, 3, 4, 1},  9, 0F,   0F,   SoundEvents.ARMOR_EQUIP_CHAIN, Trait.NONE),
	HUESO(    0xF1ECD2, null, Items.BONE,                  true, 180, 4.5F, 1F, BlockTags.INCORRECT_FOR_STONE_TOOL,     12, 1.15F, 0.10F, 1.00F, new int[]{1, 3, 4, 2}, 10, 0F,   0F,   SoundEvents.ARMOR_EQUIP_GENERIC, Trait.NONE),
	CUERO(    0xA86B3C, null, Items.LEATHER,               false, 60, 1.0F, 0F, BlockTags.INCORRECT_FOR_WOODEN_TOOL,     15, 1.10F, 0.10F, 1.00F, new int[]{1, 2, 3, 1},  5, 0F,   0F,   SoundEvents.ARMOR_EQUIP_LEATHER, Trait.NONE),
	COBRE(    0xE58A5C, ItemTags.COPPER_TOOL_MATERIALS, null, true, 190, 5.0F, 1F, BlockTags.INCORRECT_FOR_COPPER_TOOL,  13, 1.05F, 0.00F, 1.00F, new int[]{1, 3, 4, 2}, 11, 0F,   0F,   SoundEvents.ARMOR_EQUIP_COPPER, Trait.NONE),
	HIERRO(   0xE4E4E4, ItemTags.IRON_TOOL_MATERIALS, null, true, 250, 6.0F, 2F, BlockTags.INCORRECT_FOR_IRON_TOOL,     14, 1.20F, 0.00F, 1.00F, new int[]{2, 5, 6, 2}, 15, 0F,   0F,   SoundEvents.ARMOR_EQUIP_IRON, Trait.NONE),
	ORO(      0xFFD83D, ItemTags.GOLD_TOOL_MATERIALS, null, true,  32, 12.0F, 0F, BlockTags.INCORRECT_FOR_GOLD_TOOL,     22, 0.70F, 0.20F, 1.15F, new int[]{1, 3, 5, 2},  7, 0F,   0F,   SoundEvents.ARMOR_EQUIP_GOLD, Trait.NONE),
	AMATISTA( 0xB57BEA, null, Items.AMETHYST_SHARD,        true, 350, 7.0F, 2F, BlockTags.INCORRECT_FOR_IRON_TOOL,     20, 1.10F, 0.15F, 1.10F, new int[]{2, 5, 6, 3}, 20, 1F,   0F,   SoundEvents.ARMOR_EQUIP_GENERIC, Trait.NONE),
	DIAMANTE( 0x5FF0E2, ItemTags.DIAMOND_TOOL_MATERIALS, null, true, 1561, 8.0F, 3F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 10, 1.40F, -0.05F, 1.00F, new int[]{3, 6, 8, 3}, 33, 2F,   0F,   SoundEvents.ARMOR_EQUIP_DIAMOND, Trait.NONE),
	OBSIDIANA(0x6A4C9C, null, Items.OBSIDIAN,              true, 1100, 5.5F, 3F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL,  6, 1.60F, -0.25F, 0.90F, new int[]{3, 6, 8, 3}, 40, 1F,   0.1F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.NONE),
	NETHERITA(0x6B5A5A, ItemTags.NETHERITE_TOOL_MATERIALS, null, true, 2031, 9.0F, 4F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 15, 1.50F, 0.00F, 1.05F, new int[]{3, 6, 8, 3}, 37, 3F, 0.1F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.NONE),
	// Materials with a trait: a special effect whenever any part of an item is made of them.
	ESMERALDA(0x3FD46A, null, Items.EMERALD,              true, 700, 7.5F, 2.5F, BlockTags.INCORRECT_FOR_IRON_TOOL,   18, 1.10F, 0.10F, 1.05F, new int[]{2, 5, 7, 3}, 25, 1F,   0F,   SoundEvents.ARMOR_EQUIP_DIAMOND,   Trait.AFORTUNADO),
	PRISMARINA(0x6CC7B2, null, Items.PRISMARINE_SHARD,    true, 400, 6.5F, 2F, BlockTags.INCORRECT_FOR_IRON_TOOL,     16, 1.00F, 0.05F, 1.00F, new int[]{2, 5, 6, 2}, 18, 0.5F, 0F,   SoundEvents.ARMOR_EQUIP_IRON,      Trait.ACUATICO),
	VARA_DE_BLAZE(0xFFB02E, null, Items.BLAZE_ROD,        false, 300, 1.0F, 0F, BlockTags.INCORRECT_FOR_WOODEN_TOOL,  14, 1.25F, 0.10F, 1.10F, new int[]{1, 4, 5, 2}, 14, 0F,   0F,   SoundEvents.ARMOR_EQUIP_GOLD,      Trait.IGNEO),
	CUARZO(0xEEE6DA, null, Items.QUARTZ,                  true, 220, 7.0F, 3F, BlockTags.INCORRECT_FOR_STONE_TOOL,    12, 0.85F, 0.10F, 1.05F, new int[]{1, 4, 5, 2}, 12, 0F,   0F,   SoundEvents.ARMOR_EQUIP_GENERIC,   Trait.AFILADO),
	PURPUR(0xA97AA9, null, Items.PURPUR_BLOCK,            true, 600, 6.5F, 2F, BlockTags.INCORRECT_FOR_IRON_TOOL,     14, 1.05F, 0.00F, 1.00F, new int[]{2, 5, 6, 2}, 20, 1F,   0F,   SoundEvents.ARMOR_EQUIP_GENERIC,   Trait.DEL_END),
	OBSIDIANA_LLORONA(0x7A33C9, null, Items.CRYING_OBSIDIAN, true, 1300, 5.5F, 3F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 10, 1.50F, -0.20F, 0.90F, new int[]{3, 6, 8, 3}, 38, 1.5F, 0.05F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.LLANTO),
	ECO(0x2A8C94, null, Items.ECHO_SHARD,                 true, 1000, 7.5F, 2.5F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 16, 1.25F, 0.05F, 1.05F, new int[]{3, 6, 7, 3}, 30, 1.5F, 0F,   SoundEvents.ARMOR_EQUIP_DIAMOND,   Trait.RESONANTE),
	RESINA(0xE8762B, null, Items.RESIN_BRICK,             true, 200, 5.0F, 1.5F, BlockTags.INCORRECT_FOR_STONE_TOOL,    14, 1.15F, 0.05F, 1.00F, new int[]{1, 3, 5, 2}, 13, 0F,   0F,   SoundEvents.ARMOR_EQUIP_GENERIC,   Trait.PEGAJOSO),
	/**
	 * The heart of the fallen smith: the last material in the mod, and the only one you cannot dig up,
	 * buy or find. It hits like damascus, lasts like obsidian steel and keeps working when it breaks.
	 */
	CORAZON(0xFF7A3C, TagKey.create(net.minecraft.core.registries.Registries.ITEM, dev.forja.Forja.id("corazon_de_forja")), null,
		true, 2400, 9.5F, 4.5F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 25, 1.60F, 0.10F, 1.15F, new int[]{4, 7, 9, 4}, 48, 3.5F, 0.1F,
		SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.LLANTO),
	// ---------------------------------------------------------------- alloys, melted at the star
	/** Bronze: what copper becomes when it grows up. Cheap, steady, nothing special. */
	BRONCE(0xC98A3C, alloyTag("bronce"), null, true, 320, 5.5F, 1.5F, BlockTags.INCORRECT_FOR_IRON_TOOL,
		12, 1.15F, 0.00F, 1.00F, new int[]{2, 5, 6, 2}, 16, 0.5F, 0F, SoundEvents.ARMOR_EQUIP_IRON, Trait.NONE),
	/** Brass: soft and quick, the handle metal. */
	LATON(0xE0B94A, alloyTag("laton"), null, true, 240, 6.5F, 1.0F, BlockTags.INCORRECT_FOR_IRON_TOOL,
		20, 1.05F, 0.15F, 1.05F, new int[]{1, 4, 5, 2}, 13, 0F, 0F, SoundEvents.ARMOR_EQUIP_GOLD, Trait.NONE),
	/** Pewter: light and forgiving, made to be worn rather than swung. */
	PELTRE(0xA3A79A, alloyTag("peltre"), null, true, 220, 4.5F, 0.5F, BlockTags.INCORRECT_FOR_STONE_TOOL,
		14, 1.20F, 0.10F, 0.95F, new int[]{2, 4, 5, 2}, 14, 0F, 0F, SoundEvents.ARMOR_EQUIP_CHAIN, Trait.NONE),
	/** Steel: iron done properly, and the road to every alloy above it. */
	ACERO(0xBFC4CC, alloyTag("acero"), null, true, 700, 7.0F, 2.5F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
		13, 1.35F, -0.05F, 1.00F, new int[]{2, 6, 7, 3}, 26, 1.5F, 0F, SoundEvents.ARMOR_EQUIP_IRON, Trait.NONE),
	/** Electrum: gold that learned to hold an edge, and luckier than it should be. */
	ELECTRO(0xF2E29A, alloyTag("electro"), null, true, 260, 10.0F, 1.0F, BlockTags.INCORRECT_FOR_IRON_TOOL,
		26, 0.85F, 0.25F, 1.15F, new int[]{1, 3, 5, 2}, 10, 0F, 0F, SoundEvents.ARMOR_EQUIP_GOLD, Trait.AFORTUNADO),
	/** Damascus: folded steel with netherite in it, and the sharpest thing the star can make. */
	DAMASCO(0xA0A6B4, alloyTag("damasco"), null, true, 1400, 8.0F, 4.0F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		15, 1.45F, 0.05F, 1.05F, new int[]{3, 6, 8, 3}, 34, 2.5F, 0.05F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.AFILADO),
	/** Star steel: the sky alloyed into something that lasts. */
	ACERO_ESTELAR(0xC2BFE3, alloyTag("acero_estelar"), null, true, 1100, 8.5F, 3.0F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		24, 1.10F, 0.30F, 1.10F, new int[]{3, 6, 7, 3}, 28, 1.5F, 0F, SoundEvents.ARMOR_EQUIP_CHAIN, Trait.ESTELAR),
	/** Obsidian steel: the heaviest thing you can hold, and it never gives. */
	OBSIDIACERO(0x5B4A77, alloyTag("obsidiacero"), null, true, 1900, 6.0F, 3.5F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		8, 1.70F, -0.30F, 0.85F, new int[]{3, 7, 8, 3}, 44, 2.0F, 0.15F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.NONE),
	/** Star iron: it fell out of the sky, it weighs almost nothing and it takes the fall out of a fall. */
	ESTELAR(0xCFE9FF, TagKey.create(net.minecraft.core.registries.Registries.ITEM, dev.forja.Forja.id("hierro_estelar")), null,
		true, 820, 7.0F, 2.5F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 22, 0.80F, 0.25F, 1.10F, new int[]{2, 5, 7, 3}, 26, 1F, 0F,
		SoundEvents.ARMOR_EQUIP_CHAIN, Trait.ESTELAR),
	/** Hollow plate: what is left of a suit that stood up on its own. Light, and hard for plain steel to bite. */
	HUECO(0x9AA4B0, TagKey.create(net.minecraft.core.registries.Registries.ITEM, dev.forja.Forja.id("placa_hueca")), null,
		true, 760, 5.5F, 2.0F, BlockTags.INCORRECT_FOR_IRON_TOOL, 18, 0.85F, 0.10F, 1.00F, new int[]{2, 5, 6, 2}, 24, 1F, 0.05F,
		SoundEvents.ARMOR_EQUIP_CHAIN, Trait.VACIO),
	ESCAMA(0xAD716D, null, Items.ARMADILLO_SCUTE,         false, 240, 1.0F, 0F, BlockTags.INCORRECT_FOR_WOODEN_TOOL,    10, 1.30F, -0.05F, 1.00F, new int[]{2, 5, 6, 2}, 22, 1F,   0.05F, SoundEvents.ARMOR_EQUIP_WOLF,     Trait.ACORAZADO),
	// ------------------------------------------------- the foundry alloys: poured, never hammered out
	/** Cinereous steel: steel quenched in live embers. Fire feeds it instead of eating it. */
	CINERIO(0xC4562A, alloyTag("cinerio"), null, true, 620, 7.5F, 3.0F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
		16, 1.25F, 0.05F, 1.05F, new int[]{2, 5, 7, 3}, 24, 1.5F, 0F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.ASCUA),
	/** Voltaic brass: quick, brittle, and it keeps whatever charge you put into it. */
	VOLTAICO(0x6FD6E8, alloyTag("voltaico"), null, true, 480, 9.5F, 2.0F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
		24, 0.90F, 0.30F, 1.15F, new int[]{2, 4, 6, 2}, 18, 1F, 0F, SoundEvents.ARMOR_EQUIP_GOLD, Trait.CARGADO),
	/** Soul steel: star steel poured over hollow plate. Something in it still wants to fight. */
	ALMACERO(0x77CCD3, alloyTag("almacero"), null, true, 1300, 7.5F, 3.5F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		20, 1.30F, 0.10F, 1.05F, new int[]{3, 6, 8, 3}, 32, 2.0F, 0.05F, SoundEvents.ARMOR_EQUIP_CHAIN, Trait.ANIMADO),
	/** Glass steel: obsidian steel you can see through, and barely feel on your back. */
	VIDRIACERO(0xA8D8E0, alloyTag("vidriacero"), null, true, 900, 8.5F, 3.0F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		18, 0.75F, 0.30F, 1.15F, new int[]{3, 6, 7, 3}, 26, 2.5F, 0F, SoundEvents.ARMOR_EQUIP_GENERIC, Trait.DIAFANO),
	// ---- white heat: the obsidian crucible or nothing
	/** Sun steel: it keeps the noon in it, and gives it back to whoever stands under open sky. */
	SOLACERO(0xFFC341, alloyTag("solacero"), null, true, 1700, 9.0F, 4.0F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		20, 1.45F, 0.10F, 1.10F, new int[]{3, 7, 9, 4}, 40, 3.0F, 0.05F, SoundEvents.ARMOR_EQUIP_GOLD, Trait.SOLAR),
	/** Moon steel: the same metal facing the other way. It is worth nothing at noon and everything at midnight. */
	LUNACERO(0x5A6CC0, alloyTag("lunacero"), null, true, 1700, 8.0F, 4.0F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		22, 1.45F, 0.15F, 1.05F, new int[]{3, 7, 9, 4}, 40, 3.0F, 0.05F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.NOCTURNO),
	/** Living steel: a smith's heart poured into damascus. It eats what you kill and mends itself on it. */
	ACERO_VIVO(0xE8231A, alloyTag("acero_vivo"), null, true, 2200, 9.0F, 4.5F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
		24, 1.55F, 0.10F, 1.10F, new int[]{4, 7, 9, 4}, 46, 3.5F, 0.1F, SoundEvents.ARMOR_EQUIP_NETHERITE, Trait.VIVO),
	/**
	 * Slag: what gets skimmed off the top of a melt, still hot. Brittle, blunt and worth nothing on
	 * paper — and the only <b>igneous</b> material in the mod you can get without going to the Nether.
	 *
	 * <p>That is the whole of it. A blaze rod is behind a portal, a fortress and a fight; escoria is
	 * behind a mob that gets up out of any pool of lava underground and splits when you kill it. So
	 * the early game gets a real choice it did not have: a worse weapon that <b>sets things on fire</b>
	 * against a better one that does not. It falls apart quickly, which keeps it from being the answer
	 * for very long, and it comes back for free, which keeps that from mattering.
	 */
	ESCORIA(0x7A6A5E, TagKey.create(net.minecraft.core.registries.Registries.ITEM, dev.forja.Forja.id("escoria")), null,
		true, 140, 4.5F, 1.0F, BlockTags.INCORRECT_FOR_STONE_TOOL, 8, 0.85F, 0.05F, 0.95F, new int[]{1, 3, 4, 1}, 10, 0F, 0F,
		SoundEvents.ARMOR_EQUIP_GENERIC, Trait.IGNEO);

	/** A material's special effect, active when any part of the item uses it. */
	public enum Trait {
		NONE,
		/** Weapons +1 Looting, tools +1 Fortune, armor +1 luck. */
		AFORTUNADO,
		/** Tools mine at full speed underwater, weapons get Impaling II, armor swims faster and breathes longer. */
		ACUATICO,
		/** Melee gear sets targets on fire, bows shoot flaming arrows, armor burns out sooner. */
		IGNEO,
		/** Weapons and tools deal extra damage while they are new; armor pricks attackers. */
		AFILADO,
		/** Tools sometimes teleport drops to you, weapons sometimes teleport the target, armor sometimes dodges. */
		DEL_END,
		/** Repairs itself at night. */
		LLANTO,
		/** Nothing made of star iron lets a fall hurt you, and it hangs in the air a little longer. */
		ESTELAR,
		/** Weapons +1 Breach, bows +1 Piercing, tools reveal matching ores nearby, armor shrugs off Darkness and Blindness. */
		RESONANTE,
		/** +1 Unbreaking; weapons and arrows slow targets, armor slows melee attackers. */
		PEGAJOSO,
		/** Armor +1 Projectile and Blast Protection per piece; held gear resists knockback. */
		ACORAZADO,
		/** Armor of hollow plate turns part of what plain, unforged steel throws at you. */
		VACIO,
		/** Fire mends it instead of eating it: while you burn it repairs itself and hits harder. */
		ASCUA,
		/** Every hit stores a charge; when it is full the next one arcs to something else nearby. */
		CARGADO,
		/** It fights on its own: now and then it turns a blow aside without being asked. */
		ANIMADO,
		/** Almost weightless: armor of it does not slow you and leaves you quicker on your feet. */
		DIAFANO,
		/** It keeps the noon in it: under open sky in daylight it hits harder and burns the undead. */
		SOLAR,
		/** Worth nothing at noon: in the dark it hits harder, walks quieter and sees further. */
		NOCTURNO,
		/** It feeds: every kill mends it, and once it is whole the rest goes to you. */
		VIVO;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public Component displayName() {
			return Component.translatable("trait.forja." + this.id());
		}

		public Component description() {
			return Component.translatable("trait.forja." + this.id() + ".desc");
		}
	}

	/**
	 * What a parts table can still cut: the things you shape cold.
	 *
	 * <p>Everything else has to be <b>poured</b>. You cannot carve a pickaxe head out of a bar of steel
	 * with a chisel and a template — you melt it and you cast it, which is what the foundry is for and
	 * what it was missing a reason to be. Wood, stone, bone, leather, quartz and the rest are worked at
	 * the bench because that is how they are worked.
	 *
	 * <p>The line is not "is it a metal": amethyst and echo shards are cut, obsidian and diamond are
	 * poured. It is Andy's line, drawn where the game plays best, and it lives here so both the table
	 * and the guide read it from the same place.
	 */
	public static final java.util.Set<ForgeMaterial> BASIC = java.util.Collections.unmodifiableSet(
		java.util.EnumSet.of(MADERA, PIEDRA, HUESO, CUERO, AMATISTA, PRISMARINA, VARA_DE_BLAZE,
			CUARZO, PURPUR, ECO, RESINA, ESCAMA));

	/** Whether a parts table will cut this, as opposed to it having to be cast. */
	public boolean isBasic() {
		return BASIC.contains(this);
	}

	public static final Codec<ForgeMaterial> CODEC = StringRepresentable.fromEnum(ForgeMaterial::values);

	public final int color;
	private final @Nullable TagKey<Item> inputTag;
	private final @Nullable Item inputItem;
	/** False for soft materials that cannot form heads or blades. */
	public final boolean canBeHead;
	public final int durability;
	public final float miningSpeed;
	public final float attackDamageBonus;
	public final TagKey<Block> incorrectBlocksForDrops;
	public final int enchantability;
	public final float handleDurability;
	public final float handleAttackSpeed;
	public final float handleMiningSpeed;
	private final int[] defense;
	public final int armorDurability;
	public final float toughness;
	public final float knockbackResistance;
	public final Holder<SoundEvent> equipSound;
	public final Trait trait;

	ForgeMaterial(
		int color, @Nullable TagKey<Item> inputTag, @Nullable Item inputItem, boolean canBeHead, int durability, float miningSpeed, float attackDamageBonus,
		TagKey<Block> incorrectBlocksForDrops, int enchantability, float handleDurability, float handleAttackSpeed, float handleMiningSpeed, int[] defense,
		int armorDurability, float toughness, float knockbackResistance, Holder<SoundEvent> equipSound, Trait trait
	) {
		this.color = color;
		this.inputTag = inputTag;
		this.inputItem = inputItem;
		this.canBeHead = canBeHead;
		this.durability = durability;
		this.miningSpeed = miningSpeed;
		this.attackDamageBonus = attackDamageBonus;
		this.incorrectBlocksForDrops = incorrectBlocksForDrops;
		this.enchantability = enchantability;
		this.handleDurability = handleDurability;
		this.handleAttackSpeed = handleAttackSpeed;
		this.handleMiningSpeed = handleMiningSpeed;
		this.defense = defense;
		this.armorDurability = armorDurability;
		this.toughness = toughness;
		this.knockbackResistance = knockbackResistance;
		this.equipSound = equipSound;
		this.trait = trait;
	}

	@Override
	public String getSerializedName() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The colour a material is when it is liquid: its own, lit from inside.
	 *
	 * <p>One function, because there were five. The tank, the channel, the casting table, the crucible's
	 * screen and the spout each had its own "push it toward orange", and each pushed four fifths of the
	 * way — at which point gold, steel and copper are the same orange puddle and the only way to tell
	 * what is in a pot is to read the label. Andy asked, looking at the crucible, whether the melt
	 * changed colour with the metal at all. It did; by about a fifth.
	 *
	 * <p>So: a third of the glow anything that hot gives off, two thirds the metal, and then lifted until
	 * its brightest channel is nearly full, because nothing molten is dark. Gold pours yellow, copper
	 * orange, diamond mint, moon steel lilac, living steel red.
	 *
	 * <p>A grey metal gets more of the glow, up to seven tenths. A third was tried for everything, and a
	 * tank of iron came out the colour of milk: a metal with no colour of its own has nothing to show
	 * through the heat, so what it shows is the heat. Iron and steel pour a pale orange, which is what
	 * they do.
	 *
	 * @return the colour as 0xRRGGBB, without alpha
	 */
	public static int molten(int colour) {
		float red = (colour >> 16) & 0xFF;
		float green = (colour >> 8) & 0xFF;
		float blue = colour & 0xFF;
		float most = Math.max(red, Math.max(green, blue));
		float least = Math.min(red, Math.min(green, blue));
		float saturation = most <= 0.0F ? 0.0F : (most - least) / most;
		float glow = 0.34F + 0.36F * (1.0F - saturation);
		float r = red * (1.0F - glow) + 255.0F * glow;
		float g = green * (1.0F - glow) + 168.0F * glow;
		float b = blue * (1.0F - glow) + 56.0F * glow;
		float high = Math.max(r, Math.max(g, b));
		float lift = high < 236.0F ? 236.0F / Math.max(1.0F, high) : 1.0F;
		return Math.min(255, Math.round(r * lift)) << 16 | Math.min(255, Math.round(g * lift)) << 8 | Math.min(255, Math.round(b * lift));
	}

	/** This material, molten. See {@link #molten(int)}. */
	public int moltenColor() {
		return molten(this.color);
	}

	public Component displayName() {
		return Component.translatable("material.forja." + this.getSerializedName());
	}

	/** An item to show for this material in the guide. */
	/**
	 * One item that stands for this material, for the guide and for anything else that wants to show it.
	 *
	 * <p>Materials taken by tag have no single item to point at, so they are listed out. The fallback
	 * used to be a netherite ingot for everything left over, which meant <b>every alloy, star iron, the
	 * hollow plate and the forge heart all drew the same grey bar</b> — sixteen different metals that
	 * the guide showed as one. They have their own ingots; this hands them over.
	 */
	public ItemStack displayStack() {
		if (this.inputItem != null) {
			return new ItemStack(this.inputItem);
		}
		// Every alloy's id is also its material's name, so the ingot is one lookup away.
		Item alloy = dev.forja.registry.ModItems.alloy(this.getSerializedName());
		if (alloy != null) {
			return new ItemStack(alloy);
		}
		return new ItemStack(switch (this) {
			case MADERA -> Items.OAK_PLANKS;
			case PIEDRA -> Items.COBBLESTONE;
			case COBRE -> Items.COPPER_INGOT;
			case HIERRO -> Items.IRON_INGOT;
			case ORO -> Items.GOLD_INGOT;
			case DIAMANTE -> Items.DIAMOND;
			case ESTELAR -> dev.forja.registry.ModItems.HIERRO_ESTELAR;
			case HUECO -> dev.forja.registry.ModItems.PLACA_HUECA;
			case CORAZON -> dev.forja.registry.ModItems.CORAZON_DE_FORJA;
			default -> Items.NETHERITE_INGOT;
		});
	}

	public boolean matches(ItemStack stack) {
		return this.inputTag != null ? stack.is(this.inputTag) : stack.is(this.inputItem);
	}

	/** The tag of one alloy ingot, which is how a material points at an item the mod registers later. */
	private static TagKey<Item> alloyTag(String id) {
		return TagKey.create(net.minecraft.core.registries.Registries.ITEM, dev.forja.Forja.id(id));
	}

	public static @Nullable ForgeMaterial fromInput(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		for (ForgeMaterial material : values()) {
			if (material.matches(stack)) {
				return material;
			}
		}
		return null;
	}

	public HolderSet<Item> repairItems(HolderGetter<Item> items) {
		if (this.inputTag != null) {
			return items.getOrThrow(this.inputTag);
		}
		return HolderSet.direct(this.inputItem.builtInRegistryHolder());
	}

	public int defense(ArmorType type) {
		return switch (type) {
			case BOOTS -> this.defense[0];
			case LEGGINGS -> this.defense[1];
			case CHESTPLATE -> this.defense[2];
			default -> this.defense[3];
		};
	}

	/** The worn armor look: Forja's own armor texture, plate and lining each tinted with their material. */
	/** The worn look of barding or a wolf harness, one per plate material. */
	public static ResourceKey<EquipmentAsset> mountAsset(dev.forja.forge.ForgeType type, ForgeMaterial plate) {
		String prefix = type == dev.forja.forge.ForgeType.BARDA ? "barda_" : "lobo_";
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Forja.id(prefix + plate.getSerializedName()));
	}

	/** The worn look of a pair of wings, one per membrane material. */
	public static ResourceKey<EquipmentAsset> wingsAsset(ForgeMaterial membrane) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Forja.id("alas_" + membrane.getSerializedName()));
	}

	public static ResourceKey<EquipmentAsset> equipmentAsset(ForgeMaterial plate, ForgeMaterial lining) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Forja.id(plate.getSerializedName() + "_" + lining.getSerializedName()));
	}
}
