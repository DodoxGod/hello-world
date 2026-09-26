package dev.forja.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.forja.Forja;
import dev.forja.item.PartItem;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.upgrade.HiddenEnchantments;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * Turns parts into items. All stats live in vanilla data components (tool rules, attributes,
 * durability, equippable), so vanilla handles mining, combat, enchanting and anvil repair.
 */
public final class Assembler {
	/** How many arrows one set of parts makes. */
	public static final int ARROWS_PER_FORGE = 4;

	/** Receives components; lets the same code fill item properties at registration and stacks at runtime. */
	public interface ComponentSink {
		<T> void set(DataComponentType<T> type, T value);
	}

	public record Result(ItemStack stack, @Nullable List<PartType> missing) {
		static final Result EMPTY = new Result(ItemStack.EMPTY, null);
	}

	private Assembler() {
	}

	public static ItemStack createPart(PartType type, ForgeMaterial material) {
		ItemStack stack = new ItemStack(ModItems.part(type));
		writePart(type, material, sink(stack));
		return stack;
	}

	public static ItemStack create(ForgeType type, List<ForgeMaterial> materials) {
		// Say so here rather than three frames deeper. Giving a type the wrong number of materials used
		// to surface as an IndexOutOfBounds inside the stat sheet, and when it happened on the kit
		// command it took the whole world load down with it and said nothing about whose fault it was.
		if (materials.size() != type.slots.size()) {
			throw new IllegalArgumentException(type + " takes " + type.slots.size() + " materials ("
				+ type.slots + "), got " + materials.size() + ": " + materials);
		}
		ItemStack stack = new ItemStack(ModItems.forged(type));
		write(type, materials, Upgrades.EMPTY, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM, sink(stack));
		return stack;
	}

	public static ComponentSink sink(ItemStack stack) {
		return new ComponentSink() {
			@Override
			public <T> void set(DataComponentType<T> type, T value) {
				stack.set(type, value);
			}
		};
	}

	public static void writePart(PartType type, ForgeMaterial material, ComponentSink sink) {
		sink.set(ModComponents.MATERIAL, material);
		sink.set(DataComponents.CUSTOM_MODEL_DATA, colors(List.of(material)));
		sink.set(DataComponents.ITEM_NAME, Component.translatable("part.forja." + type.id() + ".de", material.displayName()));
	}

	/** Writes every stat component for an assembled item. Leaves damage, enchantments and custom names alone. */
	public static void write(ForgeType type, List<ForgeMaterial> materials, Upgrades upgrades, HolderGetter<Block> blocks, HolderGetter<Item> items, ComponentSink sink) {
		write(type, materials, upgrades, 0, blocks, items, sink);
	}

	/** Like {@link #write(ForgeType, List, Upgrades, HolderGetter, HolderGetter, ComponentSink)} for gear at a Maestria level. */
	public static void write(ForgeType type, List<ForgeMaterial> materials, Upgrades upgrades, int mastery, HolderGetter<Block> blocks, HolderGetter<Item> items, ComponentSink sink) {
		write(type, materials, upgrades, mastery, null, blocks, items, sink);
	}

	public static void write(
		ForgeType type, List<ForgeMaterial> materials, Upgrades upgrades, int mastery, @Nullable Perk perk,
		HolderGetter<Block> blocks, HolderGetter<Item> items, ComponentSink sink
	) {
		write(type, materials, upgrades, mastery, perk, 0.0F, blocks, items, sink);
	}

	/**
	 * Rewrites every stat component of an assembled stack from what the stack itself carries: parts,
	 * upgrades, Maestria, gift and the press it came out of. Damage, name and enchantments are left alone.
	 */
	public static void rewrite(ItemStack stack, HolderGetter<Block> blocks, HolderGetter<Item> items) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null) {
			return;
		}
		write(
			parts.type(), parts.materials(), stack.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY),
			Mastery.level(stack), Perk.of(stack), Quality.bonus(stack), blocks, items, sink(stack)
		);
	}

	public static void write(
		ForgeType type, List<ForgeMaterial> materials, Upgrades upgrades, int mastery, @Nullable Perk perk, float quality,
		HolderGetter<Block> blocks, HolderGetter<Item> items, ComponentSink sink
	) {
		ForgedParts parts = new ForgedParts(type, materials);
		ForgeMaterial primary = parts.primary();
		ForgeStats.Sheet stats = ForgeStats.sheet(type, materials, upgrades, mastery, perk);
		stats.scale(quality);

		sink.set(ModComponents.PARTS, parts);
		sink.set(DataComponents.CUSTOM_MODEL_DATA, colors(materials));
		sink.set(DataComponents.ITEM_NAME, Component.translatable("item.forja." + type.id() + ".de", primary.displayName()));
		sink.set(DataComponents.ENCHANTABLE, new Enchantable(Math.max(1, Math.round((float) materials.stream().mapToInt(m -> m.enchantability).average().orElse(1)))));
		sink.set(DataComponents.REPAIRABLE, new Repairable(primary.repairItems(items)));
		if (type.kind == ForgeType.Kind.MUNICION) {
			// Arrows come in handfuls and never wear out, so they carry no durability at all.
			sink.set(DataComponents.MAX_STACK_SIZE, 64);
		} else {
			sink.set(DataComponents.MAX_DAMAGE, stats.durability);
		}
		// The frame around the tooltip says at a glance what this piece is.
		if (primary == ForgeMaterial.CORAZON) {
			sink.set(DataComponents.TOOLTIP_STYLE, dev.forja.Forja.id("corazon"));
		} else if (mastery >= Mastery.MAX_LEVEL) {
			sink.set(DataComponents.TOOLTIP_STYLE, dev.forja.Forja.id("maestria"));
		}
		// Maestria 10 makes the name shine like an epic item.
		sink.set(DataComponents.RARITY, mastery >= Mastery.MAX_LEVEL ? Rarity.EPIC : Rarity.COMMON);
		// Upgrade lines and the colored stat lines replace the enchantment and attribute tooltips.
		sink.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ENCHANTMENTS, true).withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));

		switch (type.kind) {
			case ARMOR -> writeArmor(type, parts, stats, upgrades, perk, sink);
			case SHIELD -> writeShield(stats, sink);
			case ALAS -> writeWings(parts, stats, sink);
			case MONTURA -> writeMount(type, parts, upgrades, stats, sink);
			// Bows keep vanilla's bow behavior; draw speed and arrow damage apply at shot time.
			case RANGED -> {
			}
			default -> writeToolOrWeapon(type, parts, stats, upgrades, blocks, sink);
		}
	}

	private static void writeArmor(ForgeType type, ForgedParts parts, ForgeStats.Sheet stats, Upgrades upgrades, @Nullable Perk perk, ComponentSink sink) {
		ArmorType armorType = type.armorType;
		ForgeMaterial plate = parts.primary();
		ForgeMaterial lining = parts.material(type.slotOf(PartType.Role.LINING));

		Identifier id = Identifier.withDefaultNamespace("armor." + armorType.getName());
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(armorType.getSlot());
		ItemAttributeModifiers.Builder modifiers = ItemAttributeModifiers.builder()
			.add(Attributes.ARMOR, new AttributeModifier(id, stats.armor, AttributeModifier.Operation.ADD_VALUE), group)
			.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, stats.toughness, AttributeModifier.Operation.ADD_VALUE), group);
		if (stats.knockbackResistance > 0.0F) {
			modifiers.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, stats.knockbackResistance, AttributeModifier.Operation.ADD_VALUE), group);
		}
		addUpgradeAttribute(modifiers, group, Attributes.MAX_HEALTH, "vitalidad", Upgrade.extraHealth(upgrades.percent(Upgrade.VITALIDAD) / 100.0F), AttributeModifier.Operation.ADD_VALUE);
		addUpgradeAttribute(modifiers, group, Attributes.JUMP_STRENGTH, "resorte", Upgrade.jumpBoost(upgrades.percent(Upgrade.RESORTE) / 100.0F), AttributeModifier.Operation.ADD_VALUE);
		addUpgradeAttribute(modifiers, group, Attributes.SAFE_FALL_DISTANCE, "resorte", Upgrade.safeFall(upgrades.percent(Upgrade.RESORTE) / 100.0F), AttributeModifier.Operation.ADD_VALUE);
		addUpgradeAttribute(modifiers, group, Attributes.MOVEMENT_SPEED, "presteza", Upgrade.speedBoost(upgrades.percent(Upgrade.PRESTEZA) / 100.0F), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		addUpgradeAttribute(modifiers, group, Attributes.STEP_HEIGHT, "zancada", Upgrade.stepBoost(upgrades.percent(Upgrade.ZANCADA) / 100.0F), AttributeModifier.Operation.ADD_VALUE);
		String piece = "." + armorType.getName();
		if (parts.hasTrait(ForgeMaterial.Trait.AFORTUNADO)) {
			addUpgradeAttribute(modifiers, group, Attributes.LUCK, "rasgo.afortunado" + piece, 1.0F, AttributeModifier.Operation.ADD_VALUE);
		}
		if (parts.hasTrait(ForgeMaterial.Trait.ACUATICO)) {
			addUpgradeAttribute(modifiers, group, Attributes.WATER_MOVEMENT_EFFICIENCY, "rasgo.acuatico" + piece, 0.25F, AttributeModifier.Operation.ADD_VALUE);
			addUpgradeAttribute(modifiers, group, Attributes.OXYGEN_BONUS, "rasgo.acuatico" + piece, 1.0F, AttributeModifier.Operation.ADD_VALUE);
		}
		if (parts.hasTrait(ForgeMaterial.Trait.IGNEO)) {
			addUpgradeAttribute(modifiers, group, Attributes.BURNING_TIME, "rasgo.igneo" + piece, -0.2F, AttributeModifier.Operation.ADD_VALUE);
		}
		// Ascua: the plate is at home in a fire, so the fire stops sticking to the smith inside it.
		if (parts.hasTrait(ForgeMaterial.Trait.ASCUA)) {
			addUpgradeAttribute(modifiers, group, Attributes.BURNING_TIME, "rasgo.ascua" + piece, -0.35F, AttributeModifier.Operation.ADD_VALUE);
		}
		// Diafano: glass steel weighs next to nothing, so it is the only heavy armour you can run in.
		if (parts.hasTrait(ForgeMaterial.Trait.DIAFANO)) {
			addUpgradeAttribute(modifiers, group, Attributes.MOVEMENT_SPEED, "rasgo.diafano" + piece, 0.03F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			addUpgradeAttribute(modifiers, group, Attributes.SNEAKING_SPEED, "rasgo.diafano" + piece, 0.15F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
		if (perk == Perk.VIAJERO) {
			addUpgradeAttribute(modifiers, group, Attributes.FALL_DAMAGE_MULTIPLIER, "don.viajero" + piece, -0.25F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			addUpgradeAttribute(modifiers, group, Attributes.MOVEMENT_SPEED, "don.viajero" + piece, 0.05F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
		sink.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.build());
		sink.set(
			DataComponents.EQUIPPABLE,
			Equippable.builder(armorType.getSlot()).setEquipSound(plate.equipSound).setAsset(ForgeMaterial.equipmentAsset(plate, lining)).build()
		);
	}

	private static void addUpgradeAttribute(
		ItemAttributeModifiers.Builder modifiers, EquipmentSlotGroup group, Holder<Attribute> attribute, String upgrade, float amount, AttributeModifier.Operation operation
	) {
		if (amount != 0.0F) {
			modifiers.add(attribute, new AttributeModifier(Forja.id("mejora." + upgrade), amount, operation), group);
		}
	}

	/** Extra entity reach of the scythe's long shaft. */
	public static final float GUADANA_REACH = 0.75F;

	/** Extra entity reach of the trident's shaft: shorter than the scythe, longer than a sword. */
	public static final float TRIDENTE_REACH = 0.5F;

	/**
	 * Extra entity reach of a mangual: the length of its chain.
	 *
	 * <p>The longest reach in the mod, and it should be — the head is not on the end of a shaft, it is
	 * on the end of a chain, and the whole point of the weapon is that you stand back and let the ball
	 * go out to them. Three blocks on top of your own reach, and Alcance stacks on that.
	 */
	public static final float MANGUAL_REACH = 3.0F;

	private static void writeToolOrWeapon(ForgeType type, ForgedParts parts, ForgeStats.Sheet stats, Upgrades upgrades, HolderGetter<Block> blocks, ComponentSink sink) {
		EquipmentSlotGroup hand = EquipmentSlotGroup.MAINHAND;
		ItemAttributeModifiers.Builder modifiers = ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, stats.attackDamage, AttributeModifier.Operation.ADD_VALUE), hand)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, stats.attackSpeed, AttributeModifier.Operation.ADD_VALUE), hand);
		float reach = upgrades.percent(Upgrade.ALCANCE) / 100.0F;
		addUpgradeAttribute(modifiers, hand, Attributes.BLOCK_INTERACTION_RANGE, "alcance", Upgrade.blockReach(reach), AttributeModifier.Operation.ADD_VALUE);
		addUpgradeAttribute(modifiers, hand, Attributes.ENTITY_INTERACTION_RANGE, "alcance", Upgrade.entityReach(reach), AttributeModifier.Operation.ADD_VALUE);
		if (type.kind == ForgeType.Kind.TOOL && parts.hasTrait(ForgeMaterial.Trait.ACUATICO)) {
			addUpgradeAttribute(modifiers, hand, Attributes.SUBMERGED_MINING_SPEED, "rasgo.acuatico", 0.8F, AttributeModifier.Operation.ADD_VALUE);
		}
		if (parts.hasTrait(ForgeMaterial.Trait.ACORAZADO)) {
			addUpgradeAttribute(modifiers, hand, Attributes.KNOCKBACK_RESISTANCE, "rasgo.acorazado", 0.2F, AttributeModifier.Operation.ADD_VALUE);
		}
		// Diafano: nothing this light is slow to swing.
		if (parts.hasTrait(ForgeMaterial.Trait.DIAFANO)) {
			addUpgradeAttribute(modifiers, hand, Attributes.ATTACK_SPEED, "rasgo.diafano", 0.3F, AttributeModifier.Operation.ADD_VALUE);
		}
		if (type == ForgeType.GUADANA) {
			addUpgradeAttribute(modifiers, hand, Attributes.ENTITY_INTERACTION_RANGE, "guadana", GUADANA_REACH, AttributeModifier.Operation.ADD_VALUE);
		}
		if (type == ForgeType.TRIDENTE) {
			addUpgradeAttribute(modifiers, hand, Attributes.ENTITY_INTERACTION_RANGE, "tridente", TRIDENTE_REACH, AttributeModifier.Operation.ADD_VALUE);
		}
		if (type == ForgeType.MANGUAL) {
			addUpgradeAttribute(modifiers, hand, Attributes.ENTITY_INTERACTION_RANGE, "mangual", MANGUAL_REACH, AttributeModifier.Operation.ADD_VALUE);
		}
		sink.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.build());

		if (type == ForgeType.LANZA) {
			writeSpear(parts.primary(), stats, reach, sink);
			return;
		}
		if (type == ForgeType.MAZO) {
			sink.set(DataComponents.TOOL, MaceItem.createToolProperties());
			sink.set(DataComponents.WEAPON, new Weapon(1));
			return;
		}
		if (type.kind == ForgeType.Kind.WEAPON) {
			sink.set(
				DataComponents.TOOL,
				new Tool(
					List.of(
						Tool.Rule.minesAndDrops(HolderSet.direct(Blocks.COBWEB.builtInRegistryHolder()), 15.0F),
						Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
						Tool.Rule.overrideSpeed(blocks.getOrThrow(BlockTags.SWORD_EFFICIENT), 1.5F)
					),
					1.0F,
					2,
					false
				)
			);
			sink.set(DataComponents.WEAPON, new Weapon(1));
			return;
		}

		// Tier comes from the first head; every head mines its own block family at its own speed.
		List<Tool.Rule> rules = new ArrayList<>();
		rules.add(Tool.Rule.deniesDrops(blocks.getOrThrow(parts.primary().incorrectBlocksForDrops)));
		for (int slot = 0; slot < type.slots.size(); slot++) {
			TagKey<Block> mineable = type.mineableFor(type.slots.get(slot));
			if (mineable != null) {
				rules.add(Tool.Rule.minesAndDrops(blocks.getOrThrow(mineable), stats.miningSpeeds[slot]));
			}
		}
		sink.set(DataComponents.TOOL, new Tool(rules, 1.0F, 1, true));
		sink.set(DataComponents.WEAPON, new Weapon(2, type.disableBlockingSeconds));
	}

	/**
	 * Vanilla spear behavior with numbers from the tip: jab length and charge strength follow the
	 * material the way they do from vanilla's wooden to netherite spears.
	 */
	private static void writeSpear(ForgeMaterial tip, ForgeStats.Sheet stats, float reach, ComponentSink sink) {
		float bonus = tip.attackDamageBonus;
		// Spears measure their jab by this component instead of the interaction range attribute, so Alcance lands here too.
		float extra = Upgrade.entityReach(reach);
		sink.set(DataComponents.ATTACK_RANGE, new AttackRange(2.0F, 4.5F + extra, 2.0F, 6.5F + extra, 0.125F, 0.5F));
		boolean wood = tip == ForgeMaterial.MADERA;
		sink.set(
			DataComponents.KINETIC_WEAPON,
			new KineticWeapon(
				10,
				Math.round(stats.chargeDelay * 20.0F),
				KineticWeapon.Condition.ofAttackerSpeed(Math.round((5.0F - 0.625F * bonus) * 20.0F), 14.0F - 1.25F * bonus),
				KineticWeapon.Condition.ofAttackerSpeed(Math.round((10.0F - 1.125F * bonus) * 20.0F), 5.1F),
				KineticWeapon.Condition.ofRelativeSpeed(Math.round((15.0F - 1.5625F * bonus) * 20.0F), 4.6F),
				0.38F,
				stats.chargeMultiplier,
				Optional.of(wood ? SoundEvents.SPEAR_WOOD_USE : SoundEvents.SPEAR_USE),
				Optional.of(wood ? SoundEvents.SPEAR_WOOD_HIT : SoundEvents.SPEAR_HIT)
			)
		);
		sink.set(
			DataComponents.PIERCING_WEAPON,
			new PiercingWeapon(
				true,
				false,
				Optional.of(wood ? SoundEvents.SPEAR_WOOD_ATTACK : SoundEvents.SPEAR_ATTACK),
				Optional.of(wood ? SoundEvents.SPEAR_WOOD_HIT : SoundEvents.SPEAR_HIT)
			)
		);
		sink.set(DataComponents.SWING_ANIMATION, new SwingAnimation(SwingAnimationType.STAB, Math.max(1, Math.round(stats.attackDuration * 20.0F))));
	}

	/** Heavy plates raise the shield slower, tough rims shrug off axes sooner. */
	/**
	 * Barding and harnesses: the body slot of something that is not the player. The plate is worth the
	 * same armor it would be worth on a chestplate, and the horse or the wolf wears it as its own.
	 */
	private static void writeMount(ForgeType type, ForgedParts parts, Upgrades upgrades, ForgeStats.Sheet stats, ComponentSink sink) {
		ItemAttributeModifiers.Builder modifiers = ItemAttributeModifiers.builder();
		EquipmentSlotGroup body = EquipmentSlotGroup.BODY;
		addUpgradeAttribute(modifiers, body, Attributes.ARMOR, "montura.armadura", stats.armor, AttributeModifier.Operation.ADD_VALUE);
		float plating = Upgrade.mountArmor(upgrades.percent(Upgrade.PETO) / 100.0F);
		addUpgradeAttribute(modifiers, body, Attributes.ARMOR, "montura.peto", plating, AttributeModifier.Operation.ADD_VALUE);
		if (dev.forja.upgrade.Synergy.CARGA.active(upgrades)) {
			// Carga: shoes and plating together, and nothing pushes it around any more.
			addUpgradeAttribute(modifiers, body, Attributes.KNOCKBACK_RESISTANCE, "montura.carga", 0.6F, AttributeModifier.Operation.ADD_VALUE);
		}
		addUpgradeAttribute(modifiers, body, Attributes.MOVEMENT_SPEED, "montura.herradura",
			Upgrade.mountSpeed(upgrades.percent(Upgrade.HERRADURA) / 100.0F), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		addUpgradeAttribute(modifiers, body, Attributes.ARMOR_TOUGHNESS, "montura.dureza", stats.toughness, AttributeModifier.Operation.ADD_VALUE);
		sink.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.build());
		boolean horse = type == ForgeType.BARDA;
		Equippable.Builder equippable = Equippable.builder(EquipmentSlot.BODY)
			.setEquipSound(parts.primary().equipSound)
			.setAsset(ForgeMaterial.mountAsset(type, parts.primary()))
			.setDamageOnHurt(!horse);
		// Named outright rather than through the vanilla tag: item components are written during
		// registration, and tags are not bound yet at that point.
		equippable.setAllowedEntities(horse
			? net.minecraft.core.HolderSet.direct(
				mount(net.minecraft.world.entity.EntityTypeIds.HORSE), mount(net.minecraft.world.entity.EntityTypeIds.DONKEY),
				mount(net.minecraft.world.entity.EntityTypeIds.MULE), mount(net.minecraft.world.entity.EntityTypeIds.SKELETON_HORSE),
				mount(net.minecraft.world.entity.EntityTypeIds.ZOMBIE_HORSE), mount(net.minecraft.world.entity.EntityTypeIds.CAMEL)
			)
			: net.minecraft.core.HolderSet.direct(mount(net.minecraft.world.entity.EntityTypeIds.WOLF)));
		sink.set(DataComponents.EQUIPPABLE, equippable.build());
	}

	/** One entity type that can wear a piece of mount armor. */
	private static net.minecraft.core.Holder<net.minecraft.world.entity.EntityType<?>> mount(
		net.minecraft.resources.ResourceKey<net.minecraft.world.entity.EntityType<?>> type
	) {
		return BuiltInRegistries.ENTITY_TYPE.getOrThrow(type);
	}

	/**
	 * Wings: vanilla's glider component in the chest slot, plus the lift of the membrane as less gravity
	 * and less drag, which is what makes a glide long or short.
	 */
	private static void writeWings(ForgedParts parts, ForgeStats.Sheet stats, ComponentSink sink) {
		ForgeMaterial membrane = parts.primary();
		float lift = stats.glide - 1.0F;
		ItemAttributeModifiers.Builder modifiers = ItemAttributeModifiers.builder();
		EquipmentSlotGroup chest = EquipmentSlotGroup.CHEST;
		addUpgradeAttribute(modifiers, chest, Attributes.GRAVITY, "alas.peso", -lift * 0.30F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		addUpgradeAttribute(modifiers, chest, Attributes.AIR_DRAG_MODIFIER, "alas.roce", -lift * 0.30F, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		sink.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.build());
		sink.set(DataComponents.GLIDER, net.minecraft.util.Unit.INSTANCE);
		sink.set(
			DataComponents.EQUIPPABLE,
			Equippable.builder(EquipmentSlot.CHEST)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
				.setAsset(ForgeMaterial.wingsAsset(membrane))
				.setDamageOnHurt(false)
				.build()
		);
	}

	private static void writeShield(ForgeStats.Sheet stats, ComponentSink sink) {
		// Copied from the registered shield so it keeps the damage types that bypass shields; during item
		// registration that default is used as is.
		Item shield = ModItems.forged(ForgeType.ESCUDO);
		BlocksAttacks base = shield == null ? null : shield.components().get(DataComponents.BLOCKS_ATTACKS);
		if (base != null) {
			sink.set(
				DataComponents.BLOCKS_ATTACKS,
				// No delay at all: a forged shield covers you the instant you raise it, and the plate pays for
				// that with the width of the parry window instead (CombatUpgrades.parryWindow).
				new BlocksAttacks(0.0F, stats.axeDisable, base.damageReductions(), base.itemDamage(), base.bypassedBy(), base.blockSound(), base.disableSound())
			);
		}
	}

	private static CustomModelData colors(List<ForgeMaterial> materials) {
		return new CustomModelData(List.of(), List.of(), List.of(), materials.stream().map(m -> m.color).toList());
	}

	/** Like {@link #create(ForgeType, List)}, plus the hidden enchantments its materials' traits grant. */
	public static ItemStack create(ForgeType type, List<ForgeMaterial> materials, HolderLookup.Provider registries) {
		ItemStack stack = create(type, materials);
		HiddenEnchantments.write(stack, registries);
		return stack;
	}

	/**
	 * What the assembly grid produces. Either loose parts forming a new item, or one assembled item
	 * plus replacement parts, which swaps those parts and keeps the upgrades.
	 */
	public static Result evaluate(List<ItemStack> inputs, HolderLookup.Provider registries) {
		Result result = evaluate(inputs);
		if (!result.stack().isEmpty()) {
			HiddenEnchantments.write(result.stack(), registries);
		}
		return result;
	}

	private static Result evaluate(List<ItemStack> inputs) {
		ItemStack forged = ItemStack.EMPTY;
		List<ItemStack> looseParts = new ArrayList<>();
		for (ItemStack input : inputs) {
			if (input.isEmpty()) {
				continue;
			}
			if (input.has(ModComponents.PARTS)) {
				if (!forged.isEmpty()) {
					return Result.EMPTY;
				}
				forged = input;
			} else if (input.getItem() instanceof PartItem && input.has(ModComponents.MATERIAL)) {
				looseParts.add(input);
			} else {
				return Result.EMPTY;
			}
		}

		if (!forged.isEmpty()) {
			return looseParts.isEmpty() ? Result.EMPTY : new Result(modify(forged, looseParts), null);
		}
		if (looseParts.isEmpty()) {
			return Result.EMPTY;
		}

		List<PartType> types = looseParts.stream().map(s -> ((PartItem) s.getItem()).type).toList();
		ForgeType type = ForgeType.match(types);
		if (type == null) {
			return new Result(ItemStack.EMPTY, ForgeType.missingFor(types));
		}

		ForgeMaterial[] materials = new ForgeMaterial[type.slots.size()];
		int poured = 0;
		for (ItemStack part : looseParts) {
			PartType partType = ((PartItem) part.getItem()).type;
			for (int slot = 0; slot < materials.length; slot++) {
				if (materials[slot] == null && type.slots.get(slot) == partType) {
					materials[slot] = part.get(ModComponents.MATERIAL);
					if (pouredClean(part)) {
						poured |= 1 << slot;
					}
					break;
				}
			}
		}
		ItemStack assembled = create(type, List.of(materials));
		if (poured != 0) {
			// Which of its slots hold a part out of the foundry: forge/Potential counts them.
			assembled.set(ModComponents.COLADAS, poured);
		}
		// Upgrades poured into the parts ride up into the finished thing. Where two parts carry the
		// same upgrade the better one wins rather than the two adding up, or a five-part item would be
		// worth five times a three-part one for no reason anybody chose.
		Upgrades carried = Upgrades.EMPTY;
		for (ItemStack part : looseParts) {
			for (var entry : part.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percents().entrySet()) {
				carried = carried.with(entry.getKey(), Math.max(carried.percent(entry.getKey()), entry.getValue()));
			}
		}
		if (!carried.percents().isEmpty()) {
			assembled.set(ModComponents.UPGRADES, carried);
			rewrite(assembled, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		}
		// A piece built out of a rough casting is a rough piece. The mark rides up from the part so the
		// price of pouring without a strainer that held is paid where it can be seen: on the stats.
		if (looseParts.stream().anyMatch(part -> part.getOrDefault(ModComponents.ROUGH, false))) {
			assembled.set(ModComponents.ROUGH, true);
			rewrite(assembled, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
		}
		if (type.kind == ForgeType.Kind.MUNICION) {
			// Nobody forges arrows one at a time.
			assembled.setCount(ARROWS_PER_FORGE);
		}
		return new Result(assembled, null);
	}

	/** A copy of the gear with that gift engraved and its stats rewritten around it. */
	public static ItemStack engrave(ItemStack gear, Perk perk, HolderLookup.Provider registries) {
		ForgedParts parts = gear.get(ModComponents.PARTS);
		if (parts == null) {
			return ItemStack.EMPTY;
		}
		ItemStack result = gear.copyWithCount(1);
		int damage = result.getDamageValue();
		result.set(ModComponents.DON, perk.id());
		write(parts.type(), parts.materials(), result.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY), Mastery.level(result), perk,
			BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM, sink(result));
		HiddenEnchantments.write(result, registries);
		result.setDamageValue(Math.min(damage, result.getMaxDamage()));
		return result;
	}

	private static ItemStack modify(ItemStack forged, List<ItemStack> replacements) {
		ForgedParts parts = forged.get(ModComponents.PARTS);
		ForgeType type = parts.type();
		List<ForgeMaterial> materials = new ArrayList<>(parts.materials());
		boolean[] replaced = new boolean[materials.size()];
		boolean newHead = false;
		int poured = forged.getOrDefault(ModComponents.COLADAS, 0);

		for (ItemStack part : replacements) {
			PartType partType = ((PartItem) part.getItem()).type;
			int target = -1;
			for (int slot = 0; slot < materials.size(); slot++) {
				if (!replaced[slot] && type.slots.get(slot) == partType) {
					target = slot;
					break;
				}
			}
			if (target < 0) {
				return ItemStack.EMPTY;
			}
			replaced[target] = true;
			materials.set(target, part.get(ModComponents.MATERIAL));
			poured = pouredClean(part) ? poured | 1 << target : poured & ~(1 << target);
			newHead |= partType.role == PartType.Role.HEAD || partType.role == PartType.Role.PLATE;
		}

		ItemStack result = forged.copyWithCount(1);
		// A changed part changes its own bit and nobody else's, so the potential follows the parts that
		// are actually in the piece: a poured blade swapped for a cut one takes its share away with it.
		if (poured != 0) {
			result.set(ModComponents.COLADAS, poured);
		} else {
			result.remove(ModComponents.COLADAS);
		}
		int oldMax = Math.max(1, result.getMaxDamage());
		int oldDamage = result.getDamageValue();
		write(type, materials, result.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY), Mastery.level(result), Perk.of(result),
			BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM, sink(result));
		int newMax = result.getMaxDamage();
		// A fresh head or plate is a repair; other parts keep the same wear ratio.
		// A new head or plate is a fresh edge; otherwise wear carries over, and broken gear stays broken.
		result.setDamageValue(newHead ? 0 : oldDamage >= oldMax ? newMax : Math.min(newMax - 1, Math.round((float) oldDamage * newMax / oldMax)));
		return result;
	}

	/** Whether a loose part came out of the foundry, and came out well. */
	private static boolean pouredClean(ItemStack part) {
		return part.getOrDefault(ModComponents.COLADA, false) && !part.getOrDefault(ModComponents.ROUGH, false);
	}

	public static ItemStack partResult(PartType type, ItemStack materialStack) {
		ForgeMaterial material = ForgeMaterial.fromInput(materialStack);
		if (material == null || !type.accepts(material) || materialStack.getCount() < type.cost) {
			return ItemStack.EMPTY;
		}
		// Metal is poured, not carved. This is the one place a table decides it can cut something, so
		// it is the one place the rule has to live.
		return material.isBasic() ? createPart(type, material) : ItemStack.EMPTY;
	}

	/**
	 * Parts a finished item breaks down into. Heads and plates worn past half their durability are lost;
	 * each upgrade comes out as an orb holding half its percentage.
	 */
	public record Disassembly(List<ItemStack> returned, List<ItemStack> lost, List<ItemStack> orbs) {
	}

	public static Disassembly disassemble(ItemStack forged) {
		ForgedParts parts = forged.get(ModComponents.PARTS);
		List<ItemStack> returned = new ArrayList<>();
		List<ItemStack> lost = new ArrayList<>();
		List<ItemStack> orbs = new ArrayList<>();
		// A loose part is recycled into half the material it cost.
		if (forged.getItem() instanceof dev.forja.item.PartItem part && forged.has(ModComponents.MATERIAL)) {
			returned.add(forged.get(ModComponents.MATERIAL).displayStack().copyWithCount(Math.max(1, part.type.cost / 2)));
			return new Disassembly(returned, lost, orbs);
		}
		if (parts == null) {
			return new Disassembly(returned, lost, orbs);
		}
		float remaining = forged.getMaxDamage() > 0 ? 1.0F - (float) forged.getDamageValue() / forged.getMaxDamage() : 1.0F;
		for (int slot = 0; slot < parts.type().slots.size(); slot++) {
			PartType type = parts.type().slots.get(slot);
			ItemStack part = createPart(type, parts.material(slot));
			if ((forged.getOrDefault(ModComponents.COLADAS, 0) & 1 << slot) != 0) {
				part.set(ModComponents.COLADA, true);
			}
			boolean worn = (type.role == PartType.Role.HEAD || type.role == PartType.Role.PLATE) && remaining < 0.5F;
			(worn ? lost : returned).add(part);
		}
		forged.getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY).percents().forEach((upgrade, percent) -> {
			int kept = dev.forja.upgrade.UpgradeOrb.salvaged(percent);
			if (kept > 0) {
				orbs.add(dev.forja.item.UpgradeOrbItem.create(upgrade, kept));
			}
		});
		return new Disassembly(returned, lost, orbs);
	}

	/** Default materials for items created without the forge, e.g. through /give. */
	public static List<ForgeMaterial> defaultMaterials(ForgeType type) {
		if (type == ForgeType.ARCO) {
			return List.of(ForgeMaterial.MADERA, ForgeMaterial.CUERO, ForgeMaterial.MADERA);
		}
		if (type == ForgeType.ESCUDO) {
			return List.of(ForgeMaterial.MADERA, ForgeMaterial.HIERRO, ForgeMaterial.MADERA);
		}
		if (type == ForgeType.BALLESTA) {
			return List.of(ForgeMaterial.MADERA, ForgeMaterial.CUERO, ForgeMaterial.MADERA, ForgeMaterial.HIERRO);
		}
		List<ForgeMaterial> result = new ArrayList<>();
		for (PartType slot : type.slots) {
			result.add(switch (slot.role) {
				case HEAD, PLATE -> ForgeMaterial.HIERRO;
				case LINING -> ForgeMaterial.CUERO;
				default -> ForgeMaterial.MADERA;
			});
		}
		return result;
	}
}
