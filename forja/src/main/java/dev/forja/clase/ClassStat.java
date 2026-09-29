package dev.forja.clase;

import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

/**
 * Everything a class or a talent can change, as one number each. A class's base and every talent it has
 * learned add their numbers together (docs/CLASES.md: "todos los porcentajes se suman"), and whatever
 * reads the stat applies the total once.
 *
 * <p>Some of them are attribute modifiers (health, speed, melee damage...), written onto the player by
 * {@link ClassAttributes}; the rest are read by the mod's own systems through {@link ClassEffects}.
 */
public enum ClassStat {
	MAX_HEALTH(Unit.PERCENT, false, Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
	MOVE_SPEED(Unit.PERCENT, false, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
	MELEE_DAMAGE(Unit.PERCENT, false, Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
	ARMOR(Unit.FLAT, false, Attributes.ARMOR, AttributeModifier.Operation.ADD_VALUE),
	TOUGHNESS(Unit.FLAT, false, Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE),
	KNOCKBACK(Unit.PERCENT, false, Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE),
	MINING(Unit.PERCENT, false, Attributes.BLOCK_BREAK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
	JUMP(Unit.PERCENT, false, Attributes.JUMP_STRENGTH, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
	SNEAK_SPEED(Unit.PERCENT, false, Attributes.SNEAKING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
	FALL_DAMAGE(Unit.PERCENT, true, Attributes.FALL_DAMAGE_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
	BURNING(Unit.PERCENT, true, Attributes.BURNING_TIME, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),

	STAMINA_MAX(Unit.PERCENT, false),
	STAMINA_REGEN(Unit.PERCENT, false),
	/** What swings, jumps and charged blows spend. */
	STAMINA_COST(Unit.PERCENT, true),
	DODGE_DISTANCE(Unit.PERCENT, false),
	DODGE_COOLDOWN(Unit.PERCENT, true),
	DODGE_COST(Unit.PERCENT, true),
	DODGE_IFRAMES(Unit.TICKS, false),
	PARRY_WINDOW(Unit.TICKS, false),
	/** The stamina a raised shield pays for what it stops. */
	BLOCK_COST(Unit.PERCENT, true),
	POSTURE(Unit.PERCENT, false),
	DAMAGE_TAKEN(Unit.PERCENT, true),
	MAGIC_TAKEN(Unit.PERCENT, true),
	FIRE_TAKEN(Unit.PERCENT, true),
	BACKSTAB(Unit.PERCENT, false),
	COUNTER(Unit.PERCENT, false),
	STAGGERED_BONUS(Unit.PERCENT, false),
	FINISHER(Unit.PERCENT, false),
	/** Extra damage on a foe under {@link ClassEffects#EXECUTE_BELOW} of its health. */
	EXECUTE(Unit.PERCENT, false),
	PROJECTILE_DAMAGE(Unit.PERCENT, false),
	HEADSHOT(Unit.PERCENT, false),
	DRAW_SPEED(Unit.PERCENT, false),
	ARROW_SPEED(Unit.PERCENT, false),
	SPELL_DAMAGE(Unit.PERCENT, false),
	SPELL_COOLDOWN(Unit.PERCENT, true),
	/** How long a staff, tome or lantern takes to gather a full charge. */
	SPELL_CHARGE(Unit.PERCENT, true),
	/** What a full charge adds, on top of Spellcasting.CHARGE_BONUS. */
	CHARGE_BONUS(Unit.PERCENT, false),
	HEALING(Unit.PERCENT, false),
	/** Mana hooks: nothing reads these until the mana branch is merged (see ClassEffects). */
	MANA_MAX(Unit.PERCENT, false),
	MANA_REGEN(Unit.PERCENT, false),
	SPELL_COST(Unit.PERCENT, true),
	/** Hundredths added to the half-width of the forge's perfect window (the base is 0.05). */
	FORGE_WINDOW(Unit.HUNDREDTHS, false),
	/** Points of potential a piece is forged with. */
	POTENTIAL(Unit.POINTS, false),
	/** How much of a piece each repair ingot mends. */
	REPAIR(Unit.PERCENT, false),
	/** Extra share every upgrade takes, like the smith's Maestria. */
	UPGRADE_BONUS(Unit.POINTS, false),
	/** Damage with hammers, maces, pickaxes and axes. */
	SMITH_WEAPON(Unit.PERCENT, false);

	/** One number of a class or a talent: "vida −30 %" is {@code new Mod(MAX_HEALTH, -0.30F)}. */
	public record Mod(ClassStat stat, float value) {
		public Component line() {
			return this.stat.line(this.value);
		}
	}

	public Mod of(float value) {
		return new Mod(this, value);
	}

	public enum Unit {
		PERCENT,
		FLAT,
		TICKS,
		HUNDREDTHS,
		POINTS
	}

	public final Unit unit;
	/** Whether a negative number is the good direction (a shorter wait, less damage taken). */
	public final boolean lowerIsBetter;
	public final @Nullable Holder<Attribute> attribute;
	public final AttributeModifier.@Nullable Operation operation;

	ClassStat(Unit unit, boolean lowerIsBetter) {
		this(unit, lowerIsBetter, null, null);
	}

	ClassStat(Unit unit, boolean lowerIsBetter, @Nullable Holder<Attribute> attribute, AttributeModifier.@Nullable Operation operation) {
		this.unit = unit;
		this.lowerIsBetter = lowerIsBetter;
		this.attribute = attribute;
		this.operation = operation;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** The number as the screens print it: "+15 %", "−2", "+3 ticks". */
	public String format(float value) {
		String sign = value > 0 ? "+" : value < 0 ? "−" : "±";
		float magnitude = Math.abs(value);
		return switch (this.unit) {
			case PERCENT -> sign + Math.round(magnitude * 100.0F) + " %";
			case FLAT, POINTS -> sign + trim(magnitude);
			case TICKS -> sign + Math.round(magnitude);
			case HUNDREDTHS -> sign + String.format(Locale.ROOT, "0,%02d", Math.round(magnitude * 100.0F));
		};
	}

	private static String trim(float value) {
		return value == Math.rint(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value).replace('.', ',');
	}

	/** "Vida +10 %", uncoloured, for the guide's pages. */
	public Component plain(float value) {
		return Component.translatable("gui.forja.clase.stat." + this.id(), this.format(value));
	}

	/** "Vida +10 %", coloured by whether it helps. */
	public Component line(float value) {
		boolean good = this.lowerIsBetter ? value < 0 : value > 0;
		return Component.translatable("gui.forja.clase.stat." + this.id(), this.format(value)).withColor(good ? 0xFF7FD34E : 0xFFE0533D);
	}
}
