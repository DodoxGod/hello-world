package dev.forja.clase;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.jspecify.annotations.Nullable;

/**
 * What each class does to the damage it deals, by kind of blow (Andy, 2026-09-29; docs/CLASES.md). One number
 * per class and kind, all of them here:
 *
 * <ul>
 *   <li>Arquero and Mago: melee x0.7.</li>
 *   <li>Guerrero and Asesino: magic x0.4.</li>
 *   <li>Tanque: everything x0.67 (melee, projectiles and magic).</li>
 *   <li>Curandero: magic x1/3 and melee x0.5; its magic still heals allies as before (magic/Healing).</li>
 * </ul>
 *
 * <p><b>How they combine.</b> These are <em>factors</em>, applied last and multiplied, never added. The class
 * stats in {@link ClassStat} (the base and the talents: MELEE_DAMAGE, PROJECTILE_DAMAGE, SPELL_DAMAGE, backstab,
 * headshots...) are percentages that add up among themselves and are applied once, as before. So a blow is
 * {@code weapon x (1 + sum of the stat percentages) x factor}: a Mago's spell with Catalizador is
 * {@code x (1 + 0.15 + 0.15) x 1}, a Tanque's arrow {@code x 1 x 0.67}, and an Arquero's blade with nothing
 * learned {@code x 1 x 0.7}. The factor goes on in {@link ClassEffects#dealt}, the one hook every blow of a
 * player goes through (combat/CombatHooks.afterArmor), so it counts wherever the blow started: the hand, a bow,
 * a thrown head, the staff's bolt, the tome's rune, a skill.
 *
 * <p>Two numbers were replaced rather than stacked: the Mago's base melee −10 % and the Curandero's −15 % are
 * gone from their base stats, since these factors say the same thing with Andy's numbers.
 */
public final class ClassDamage {
	/** Andy: "Arquero y Mago: cuerpo a cuerpo x0,7". */
	public static final float ARQUERO_MELEE = 0.7F;
	public static final float MAGO_MELEE = 0.7F;
	/** Andy: "Guerrero y Asesino: daño mágico x0,4". */
	public static final float GUERRERO_MAGIC = 0.4F;
	public static final float ASESINO_MAGIC = 0.4F;
	/** Andy: "Tanque: todo x0,67". */
	public static final float TANQUE_ALL = 0.67F;
	/** Andy: the Curandero's magic hurts at a third, and its melee at half. */
	public static final float CURANDERO_MAGIC = 1.0F / 3.0F;
	public static final float CURANDERO_MELEE = 0.5F;

	/** The three kinds of blow a factor is set for. */
	public enum Blow {
		MELEE,
		PROJECTILE,
		MAGIC;

		public Component displayName() {
			return Component.translatable("gui.forja.clase.golpe." + this.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private ClassDamage() {
	}

	/** The factor for this class and kind of blow; 1 without a class. */
	public static float factor(@Nullable PlayerClass clazz, Blow blow) {
		if (clazz == null) {
			return 1.0F;
		}
		return switch (clazz) {
			case GUERRERO -> blow == Blow.MAGIC ? GUERRERO_MAGIC : 1.0F;
			case ASESINO -> blow == Blow.MAGIC ? ASESINO_MAGIC : 1.0F;
			case TANQUE -> TANQUE_ALL;
			case MAGO -> blow == Blow.MELEE ? MAGO_MELEE : 1.0F;
			case CURANDERO -> blow == Blow.MAGIC ? CURANDERO_MAGIC : blow == Blow.MELEE ? CURANDERO_MELEE : 1.0F;
			case ARQUERO -> blow == Blow.MELEE ? ARQUERO_MELEE : 1.0F;
		};
	}

	public static float factor(@Nullable Player player, Blow blow) {
		return factor(ClassProgress.clazz(player), blow);
	}

	/** A factor as the screens and the guide print it: "×0,7", "×0,67", "×1/3", "×1". */
	public static String format(float factor) {
		if (Math.abs(factor - 1.0F / 3.0F) < 1.0E-4F) {
			return "×1/3";
		}
		String text = String.format(java.util.Locale.ROOT, "%.2f", factor);
		while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
			text = text.substring(0, text.length() - 1);
		}
		return "×" + text.replace('.', ',');
	}

	/** Whether a blow is a spell: the staff's bolt, the tome's rune, the Nova arcana. */
	public static boolean magic(DamageSource source) {
		return source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
	}

	/**
	 * What kind of blow this is for the one who struck it: a spell, the hand (the attacker is what struck), or
	 * something that flew (an arrow, a thrown head, a thrown weapon). Null for anything else (fire, thorns...).
	 */
	public static @Nullable Blow of(DamageSource source, Player attacker) {
		if (magic(source)) {
			return Blow.MAGIC;
		}
		if (source.is(DamageTypes.THORNS)) {
			// Thorns name the wearer as what struck; it is armour answering, not a blow of the hand.
			return null;
		}
		Entity direct = source.getDirectEntity();
		if (direct == attacker) {
			return Blow.MELEE;
		}
		return direct instanceof Projectile ? Blow.PROJECTILE : null;
	}
}
