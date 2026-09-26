package dev.forja.upgrade;

import java.util.List;

import dev.forja.Forja;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Conjunto: wearing four forged armor pieces whose plates share a material adds armor, toughness and a
 * bonus of that material's own. The bonuses are transient attribute modifiers checked every second, so
 * they never stick to an item.
 */
public final class ArmorSets {
	public static final float ARMOR_BONUS = 2.0F;
	public static final float TOUGHNESS_BONUS = 2.0F;
	private static final Identifier ID = Forja.id("conjunto");
	private static final Identifier BONUS_ID = Forja.id("conjunto.material");

	/** One attribute a full set of some material raises. */
	public record Bonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		static Bonus add(Holder<Attribute> attribute, double amount) {
			return new Bonus(attribute, amount, AttributeModifier.Operation.ADD_VALUE);
		}

		static Bonus percent(Holder<Attribute> attribute, double amount) {
			return new Bonus(attribute, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
	}

	/** The set bonus of each plate material, on top of the armor and toughness every full set gets. */
	public static List<Bonus> bonuses(ForgeMaterial material) {
		return switch (material) {
			case MADERA -> List.of(Bonus.percent(Attributes.MOVEMENT_SPEED, 0.10));
			case PIEDRA -> List.of(Bonus.add(Attributes.KNOCKBACK_RESISTANCE, 0.2));
			case HUESO -> List.of(Bonus.percent(Attributes.ATTACK_SPEED, 0.10));
			case CUERO -> List.of(Bonus.add(Attributes.SAFE_FALL_DISTANCE, 3.0));
			case COBRE -> List.of(Bonus.percent(Attributes.BLOCK_BREAK_SPEED, 0.15));
			case HIERRO -> List.of(Bonus.add(Attributes.MAX_HEALTH, 4.0));
			case ORO -> List.of(Bonus.add(Attributes.LUCK, 2.0));
			case AMATISTA -> List.of(Bonus.add(Attributes.ATTACK_DAMAGE, 1.0));
			case DIAMANTE -> List.of(Bonus.add(Attributes.ARMOR_TOUGHNESS, 2.0));
			case OBSIDIANA -> List.of(Bonus.add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0));
			case NETHERITA -> List.of(Bonus.add(Attributes.BURNING_TIME, -0.5));
			case ESMERALDA -> List.of(Bonus.add(Attributes.LUCK, 3.0));
			case PRISMARINA -> List.of(Bonus.add(Attributes.OXYGEN_BONUS, 3.0), Bonus.add(Attributes.WATER_MOVEMENT_EFFICIENCY, 0.33));
			case VARA_DE_BLAZE -> List.of(Bonus.add(Attributes.BURNING_TIME, -1.0));
			case CUARZO -> List.of(Bonus.add(Attributes.SWEEPING_DAMAGE_RATIO, 0.25));
			case PURPUR -> List.of(Bonus.percent(Attributes.GRAVITY, -0.25), Bonus.add(Attributes.SAFE_FALL_DISTANCE, 4.0));
			case OBSIDIANA_LLORONA -> List.of(Bonus.add(Attributes.MAX_HEALTH, 6.0));
			case ECO -> List.of(Bonus.add(Attributes.SNEAKING_SPEED, 0.3));
			case RESINA -> List.of(Bonus.add(Attributes.MOVEMENT_EFFICIENCY, 1.0));
			case ESCAMA -> List.of(Bonus.add(Attributes.ARMOR, 3.0));
			// Slag: a suit of it is a crust of cooled melt that is still warm underneath. It shrugs
			// off fire and nothing else, which is the only thing a full set of waste has any business
			// being good at.
			case ESCORIA -> List.of(Bonus.add(Attributes.BURNING_TIME, -0.6));
			case CORAZON -> List.of(Bonus.add(Attributes.MAX_HEALTH, 8.0), Bonus.add(Attributes.ARMOR_TOUGHNESS, 4.0), Bonus.add(Attributes.BURNING_TIME, -1.0));
			case BRONCE -> List.of(Bonus.add(Attributes.ARMOR, 1.0), Bonus.add(Attributes.MAX_HEALTH, 2.0));
			case LATON -> List.of(Bonus.percent(Attributes.ATTACK_SPEED, 0.12));
			case PELTRE -> List.of(Bonus.add(Attributes.SAFE_FALL_DISTANCE, 4.0), Bonus.percent(Attributes.MOVEMENT_SPEED, 0.05));
			case ACERO -> List.of(Bonus.add(Attributes.ARMOR_TOUGHNESS, 3.0));
			case ELECTRO -> List.of(Bonus.add(Attributes.LUCK, 4.0), Bonus.percent(Attributes.BLOCK_BREAK_SPEED, 0.10));
			case DAMASCO -> List.of(Bonus.add(Attributes.ATTACK_DAMAGE, 2.0), Bonus.add(Attributes.SWEEPING_DAMAGE_RATIO, 0.2));
			case ACERO_ESTELAR -> List.of(Bonus.percent(Attributes.GRAVITY, -0.25), Bonus.add(Attributes.SAFE_FALL_DISTANCE, 6.0), Bonus.add(Attributes.ARMOR, 1.0));
			case OBSIDIACERO -> List.of(Bonus.add(Attributes.KNOCKBACK_RESISTANCE, 0.4), Bonus.add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0));
			// Star iron: it was falling when you found it and it has no intention of stopping.
			case ESTELAR -> List.of(Bonus.percent(Attributes.GRAVITY, -0.35), Bonus.add(Attributes.SAFE_FALL_DISTANCE, 8.0));
			// Hollow plate: there is nothing inside it, so it weighs almost nothing and hides you well.
			case HUECO -> List.of(Bonus.add(Attributes.SNEAKING_SPEED, 0.4), Bonus.percent(Attributes.MOVEMENT_SPEED, 0.08));
			// ---- the foundry alloys
			// Cinereous: a full suit of it puts a fire out the moment it catches.
			case CINERIO -> List.of(Bonus.add(Attributes.BURNING_TIME, -1.0), Bonus.add(Attributes.ARMOR, 1.0));
			// Voltaic: the charge runs through the smith as well as the weapon.
			case VOLTAICO -> List.of(Bonus.percent(Attributes.ATTACK_SPEED, 0.15), Bonus.percent(Attributes.MOVEMENT_SPEED, 0.05));
			// Soul steel: the suit is more awake than the smith. The shorter guard is in TraitEffects.
			case ALMACERO -> List.of(Bonus.add(Attributes.ARMOR_TOUGHNESS, 2.0), Bonus.add(Attributes.MAX_HEALTH, 2.0));
			// Glass steel: the only full plate you can run in.
			case VIDRIACERO -> List.of(Bonus.percent(Attributes.MOVEMENT_SPEED, 0.10), Bonus.add(Attributes.SAFE_FALL_DISTANCE, 4.0));
			// Sun steel: it gives back what it soaked up at noon.
			case SOLACERO -> List.of(Bonus.add(Attributes.ATTACK_DAMAGE, 2.0), Bonus.add(Attributes.ARMOR, 2.0));
			// Moon steel: it does not make you stronger so much as harder to find.
			case LUNACERO -> List.of(Bonus.add(Attributes.SNEAKING_SPEED, 0.4), Bonus.add(Attributes.ATTACK_DAMAGE, 2.0));
			// Living steel: the last set in the mod, and the kill healing doubles with it on.
			case ACERO_VIVO -> List.of(Bonus.add(Attributes.MAX_HEALTH, 8.0), Bonus.add(Attributes.ARMOR_TOUGHNESS, 3.0));
		};
	}

	/** Every attribute some set bonus touches, so a set taken off clears its bonus. */
	private static final List<Holder<Attribute>> BONUS_ATTRIBUTES = java.util.Arrays.stream(ForgeMaterial.values())
		.flatMap(material -> bonuses(material).stream())
		.map(Bonus::attribute)
		.distinct()
		.toList();
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private ArmorSets() {
	}

	/** The plate material of a worn armor piece, or null when the slot holds no forged armor. */
	public static @Nullable ForgeMaterial plate(LivingEntity entity, EquipmentSlot slot) {
		ItemStack piece = entity.getItemBySlot(slot);
		ForgedParts parts = piece.get(ModComponents.PARTS);
		return parts != null && parts.type().kind == ForgeType.Kind.ARMOR && !piece.isBroken() ? parts.primary() : null;
	}

	/** How many worn pieces have plates of this material. */
	public static int count(LivingEntity entity, ForgeMaterial material) {
		int count = 0;
		for (EquipmentSlot slot : SLOTS) {
			count += plate(entity, slot) == material ? 1 : 0;
		}
		return count;
	}

	public static @Nullable ForgeMaterial fullSet(LivingEntity entity) {
		ForgeMaterial first = plate(entity, EquipmentSlot.HEAD);
		return first != null && count(entity, first) == SLOTS.length ? first : null;
	}

	public static void update(LivingEntity entity) {
		ForgeMaterial set = fullSet(entity);
		boolean complete = set != null;
		if (complete && entity instanceof net.minecraft.world.entity.player.Player player) {
			dev.forja.ForjaAdvancements.award(player, "conjunto");
		}
		apply(entity, Attributes.ARMOR, ARMOR_BONUS, complete);
		apply(entity, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_BONUS, complete);
		List<Bonus> active = complete ? bonuses(set) : List.of();
		for (Holder<Attribute> attribute : BONUS_ATTRIBUTES) {
			AttributeInstance instance = entity.getAttribute(attribute);
			if (instance == null) {
				continue;
			}
			Bonus wanted = active.stream().filter(bonus -> bonus.attribute().equals(attribute)).findFirst().orElse(null);
			AttributeModifier present = instance.getModifier(BONUS_ID);
			if (wanted == null) {
				if (present != null) {
					instance.removeModifier(BONUS_ID);
				}
			} else if (present == null || present.amount() != wanted.amount() || present.operation() != wanted.operation()) {
				instance.removeModifier(BONUS_ID);
				instance.addTransientModifier(new AttributeModifier(BONUS_ID, wanted.amount(), wanted.operation()));
			}
		}
		if (entity.getHealth() > entity.getMaxHealth()) {
			entity.setHealth(entity.getMaxHealth());
		}
	}

	private static void apply(LivingEntity entity, Holder<Attribute> attribute, float amount, boolean active) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		boolean present = instance.getModifier(ID) != null;
		if (active && !present) {
			instance.addTransientModifier(new AttributeModifier(ID, amount, AttributeModifier.Operation.ADD_VALUE));
		} else if (!active && present) {
			instance.removeModifier(ID);
		}
	}
}
