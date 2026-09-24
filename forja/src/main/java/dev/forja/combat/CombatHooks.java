package dev.forja.combat;

import dev.forja.registry.ModComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** What the mixins and damage events call into. Server side only. */
public final class CombatHooks {
	private CombatHooks() {
	}

	/**
	 * Before a blow reaches a player: dodge i-frames, and the stamina a raised shield pays. Registered
	 * after Forja's own dodge chain, so its trait dodges still roll first.
	 *
	 * @return false to stop the blow outright
	 */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.enabled || !(entity instanceof Player player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		boolean combat = source.getEntity() != null || source.getDirectEntity() instanceof Projectile;
		if (!combat) return true;

		if (Stamina.isDodging(player, player.level().getGameTime())) {
			CombatFeedback.dodgedHit(player);
			return false;
		}

		ItemStack shield = player.getItemBlockingWith();
		if (shield != null && facing(player, source) && !source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			// A forged shield's parry is Forja's own and costs nothing; everything else is paid for.
			boolean parry = shield.has(ModComponents.PARTS) && dev.forja.upgrade.CombatUpgrades.isParry(player, shield);
			if (!parry && !Stamina.trySpend(player, amount * cfg.blockCostPerDamage)) {
				player.getCooldowns().addCooldown(shield, cfg.guardBreakTicks);
				player.stopUsingItem();
				CombatFeedback.guardBreak(player);
			}
		}
		return true;
	}

	private static boolean facing(Player player, DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (from == null) return false;
		Vec3 toSource = from.subtract(player.position());
		return new Vec3(toSource.x, 0.0, toSource.z).normalize().dot(player.getViewVector(1.0F)) > 0.0;
	}

	/**
	 * Stands in for LivingEntity#getDamageAfterArmorAbsorb: the attacker's multipliers, posture, and
	 * the armor formula. {@code hurtArmor} wears the pieces exactly as vanilla would.
	 */
	public static float afterArmor(LivingEntity target, DamageSource source, float amount, Consumer<Float> hurtArmor) {
		CombatConfig cfg = CombatConfig.get();
		long now = target.level().getGameTime();
		AttackProfile attack = AttackClassifier.classify(source, target);

		float scaled = amount;
		if (source.getEntity() instanceof Player attacker && source.getDirectEntity() == attacker
			&& Stamina.consumeTiredAttack(attacker)) {
			scaled *= (float) cfg.tiredDamageMultiplier;
		}
		if (ChargedArrows.isCharged(source.getDirectEntity())) {
			scaled *= (float) cfg.chargedArrowDamageMultiplier;
		}
		if (attack.precise() && attack.zone() == HitZone.HEAD) {
			scaled *= (float) cfg.headMultiplier;
			CombatFeedback.headHit(target);
		}
		if (Posture.isStaggered(target, now)) {
			scaled *= (float) cfg.staggerDamageMultiplier;
		}
		if (source.getEntity() != null) {
			Posture.onHit(target, attack.kind(), scaled, now);
		}

		if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return scaled;
		hurtArmor.accept(scaled);
		// Breach (Forja's Brecha upgrade rides on it) still eats into armor, on top of the weapon's own bite.
		double breach = 0.0;
		ItemStack weapon = source.getWeaponItem();
		if (weapon != null && target.level() instanceof ServerLevel level) {
			breach = 1.0 - EnchantmentHelper.modifyArmorEffectiveness(level, weapon, target, source, 1.0F);
		}
		return ArmorCalculator.apply(target, scaled, attack, breach);
	}
}
