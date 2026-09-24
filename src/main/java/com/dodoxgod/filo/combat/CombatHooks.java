package com.dodoxgod.filo.combat;

import com.dodoxgod.filo.config.FiloConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;

import java.util.function.Consumer;

/** Puntos de entrada que llaman los mixins de LivingEntity. Todo se ejecuta solo en el servidor. */
public final class CombatHooks {
	private CombatHooks() {
	}

	/**
	 * Antes de que un jugador reciba daño: esquiva, parry y gasto de estamina al bloquear.
	 *
	 * @return true si el golpe se anula por completo
	 */
	public static boolean interceptPlayerDamage(PlayerEntity player, DamageSource source, float amount) {
		if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
		boolean combat = source.getAttacker() != null || source.getSource() instanceof ProjectileEntity;
		if (!combat) return false;

		FiloConfig cfg = FiloConfig.get();
		long now = player.getWorld().getTime();

		if (StaminaManager.isDodging(player, now)) {
			CombatFeedback.dodgedHit(player);
			return true;
		}

		if (!raisingShieldTowards(player, source)) return false;

		if (cfg.parry.enabled && player.getItemUseTime() <= cfg.parry.windowTicks) {
			parry(player, source, now, cfg);
			return true;
		}

		if (player.isBlocking() && !StaminaManager.trySpend(player, amount * cfg.stamina.blockCostPerDamage)) {
			// Sin estamina la guardia se rompe y el golpe entra entero.
			player.disableShield();
			CombatFeedback.guardBreak(player);
		}
		return false;
	}

	private static void parry(PlayerEntity player, DamageSource source, long now, FiloConfig cfg) {
		CombatFeedback.parry(player);
		StaminaManager.restore(player, cfg.parry.staminaRefund);
		Entity direct = source.getSource();
		if (direct instanceof LivingEntity attacker && attacker != player
				&& attacker.squaredDistanceTo(player) <= cfg.parry.stunRange * cfg.parry.stunRange) {
			PostureManager.breakPosture(attacker, now);
			attacker.takeKnockback(0.6, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
		}
	}

	/** Mismo criterio que el escudo vanilla: escudo en uso y el golpe viene de delante. */
	private static boolean raisingShieldTowards(PlayerEntity player, DamageSource source) {
		if (!player.isUsingItem() || player.getActiveItem().getUseAction() != UseAction.BLOCK) return false;
		if (source.isIn(DamageTypeTags.BYPASSES_SHIELD)) return false;
		Vec3d from = source.getPosition();
		if (from == null) return false;
		Vec3d toPlayer = from.relativize(player.getPos());
		toPlayer = new Vec3d(toPlayer.x, 0.0, toPlayer.z).normalize();
		return toPlayer.dotProduct(player.getRotationVec(1.0f)) < 0.0;
	}

	/**
	 * Sustituye LivingEntity.applyArmorToDamage: multiplicadores del ataque, postura y nuevo cálculo
	 * de armadura. {@code damageArmor} desgasta las piezas como en vanilla.
	 */
	public static float applyArmor(LivingEntity target, DamageSource source, float amount, Consumer<Float> damageArmor) {
		FiloConfig cfg = FiloConfig.get();
		long now = target.getWorld().getTime();
		AttackProfile profile = AttackClassifier.classify(source, target);

		float scaled = amount;
		if (source.getAttacker() instanceof PlayerEntity attacker && source.getSource() == attacker
				&& StaminaManager.consumeTiredAttack(attacker)) {
			scaled *= (float) cfg.stamina.tiredDamageMultiplier;
		}
		if (ChargedArrows.isCharged(source.getSource())) {
			scaled *= (float) cfg.mobs.chargedArrowDamageMultiplier;
		}
		if (profile.precise() && profile.zone() == HitZone.HEAD) {
			scaled *= (float) cfg.armor.headMultiplier;
			CombatFeedback.headHit(target);
		}
		if (PostureManager.isStaggered(target, now)) {
			scaled *= (float) cfg.posture.staggerDamageMultiplier;
		}
		if (source.getAttacker() != null) {
			PostureManager.onHit(target, profile.kind(), scaled, now);
		}

		if (source.isIn(DamageTypeTags.BYPASSES_ARMOR)) return scaled;
		damageArmor.accept(scaled);
		if (!cfg.armor.enabled) {
			return DamageUtil.getDamageLeft(target, scaled, source, (float) target.getArmor(),
					(float) target.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS));
		}
		return ArmorCalculator.apply(target, scaled, profile);
	}
}
