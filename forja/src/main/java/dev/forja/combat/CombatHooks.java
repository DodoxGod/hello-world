package dev.forja.combat;

import dev.forja.registry.ModComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
		if (!cfg.enabled || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (entity instanceof net.minecraft.world.entity.Mob mob) {
			return dev.forja.ai.MobDefense.allowDamage(mob, source, amount);
		}
		if (!(entity instanceof Player player)) {
			return true;
		}
		boolean combat = source.getEntity() != null || source.getDirectEntity() instanceof Projectile;
		if (!combat) return true;

		boolean struck = source.getEntity() instanceof LivingEntity;
		if (Stamina.isDodging(player, player.level().getGameTime())) {
			CombatFeedback.dodgedHit(player);
			if (struck) {
				dev.forja.ai.PlayerHabits.onBlow(player, dev.forja.ai.PlayerHabits.Outcome.DODGED);
			}
			// Only a blow that was really coming counts: something that struck, not a stray arrow.
			if (source.getEntity() instanceof LivingEntity) {
				Stamina.markPerfectDodge(player);
			}
			return false;
		}

		ItemStack shield = player.getItemBlockingWith();
		if (shield != null && facing(player, source) && !source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			// A forged shield's or a weapon's parry is Forja's own and costs nothing; everything else is paid for.
			boolean guard = WeaponGuard.is(shield);
			boolean parry = (shield.has(ModComponents.PARTS) || guard) && dev.forja.upgrade.CombatUpgrades.isParry(player, shield);
			if (struck) {
				dev.forja.ai.PlayerHabits.onBlow(player, parry ? dev.forja.ai.PlayerHabits.Outcome.PARRIED : dev.forja.ai.PlayerHabits.Outcome.BLOCKED);
			}
			if (parry && guard) {
				// A weapon only turns part of a blow aside, but a parry with it catches all of it.
				return false;
			}
			if (!parry && !Stamina.trySpend(player, amount * cfg.blockCostPerDamage)) {
				player.getCooldowns().addCooldown(shield, cfg.guardBreakTicks);
				player.stopUsingItem();
				CombatFeedback.guardBreak(player);
			}
			return true;
		}
		if (struck) {
			dev.forja.ai.PlayerHabits.onBlow(player, dev.forja.ai.PlayerHabits.Outcome.TAKEN);
		}
		return true;
	}

	/**
	 * After a blow has landed: tells the one who struck it (for the hitstop) and the one it struck (for
	 * the jolt). Only blows that actually did something, so a blocked or dodged one stays silent.
	 */
	public static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!CombatConfig.get().enabled || blocked || damageTaken <= 0.0F) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker && attacker != entity) {
			CombatAnim.sendTo(attacker, entity, CombatAnim.Kind.HIT, 0, damageTaken, 0.0F);
		}
		boolean combat = source.getEntity() != null || source.getDirectEntity() instanceof Projectile;
		if (combat && entity instanceof ServerPlayer victim) {
			CombatAnim.sendTo(victim, victim, CombatAnim.Kind.HURT, 0, damageTaken, 0.0F);
			if (source.getEntity() != victim) {
				dev.forja.difficulty.Pressure.onHit(victim);
			}
		}
	}

	/** When each foe last took a finisher; a finisher has to wait before the same foe can take another. */
	private static final java.util.Map<LivingEntity, Long> LAST_FINISHER = new java.util.WeakHashMap<>();

	private static boolean finisherReady(LivingEntity target, long now) {
		Long last = LAST_FINISHER.get(target);
		return (last == null || now - last >= CombatConfig.get().finisherCooldownTicks) && dev.forja.difficulty.Bosses.finishable(target);
	}

	/** Whether the attacker stands behind the target: more than about 110 degrees off where it faces. */
	static boolean fromBehind(LivingEntity target, LivingEntity attacker) {
		Vec3 toAttacker = attacker.position().subtract(target.position());
		Vec3 flat = new Vec3(toAttacker.x, 0.0, toAttacker.z);
		if (flat.lengthSqr() < 1.0E-6) return false;
		double yaw = Math.toRadians(target.getYHeadRot());
		Vec3 facing = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		return flat.normalize().dot(facing) < -0.35;
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
		double postureScale = 1.0;
		boolean charged = false;
		if (source.getEntity() instanceof Player attacker && source.getDirectEntity() == attacker) {
			double[] strike = ChargedStrike.striking(attacker);
			if (strike != null) {
				// A charged blow pays its own stamina, so the tired check of the swing does not apply.
				Stamina.consumeTiredAttack(attacker);
				scaled *= (float) strike[0];
				postureScale *= strike[1];
				charged = true;
			} else if (Stamina.consumeTiredAttack(attacker)) {
				scaled *= (float) cfg.tiredDamageMultiplier;
			}
			if (Combos.consumeFinisher(attacker)) {
				scaled *= (float) cfg.comboFinisherDamage;
				postureScale *= cfg.comboFinisherPosture;
			}
			if (Stamina.consumeCounter(attacker)) {
				scaled *= (float) cfg.counterDamage;
				postureScale *= cfg.counterPosture;
				CombatFeedback.headHit(target);
			}
		}
		if (ChargedArrows.isCharged(source.getDirectEntity())) {
			scaled *= (float) cfg.chargedArrowDamageMultiplier;
		}
		if (attack.precise() && attack.zone() == HitZone.HEAD) {
			scaled *= (float) cfg.headMultiplier;
			CombatFeedback.headHit(target);
		}
		boolean staggered = Posture.isStaggered(target, now);
		boolean finisher = false;
		if (staggered) {
			scaled *= (float) cfg.staggerDamageMultiplier;
			// A finisher: a staggered foe struck with a charged blow, or from behind.
			if (source.getEntity() instanceof Player attacker && source.getDirectEntity() == attacker
				&& (charged || fromBehind(target, attacker)) && finisherReady(target, now)) {
				scaled *= (float) cfg.finisherMultiplier;
				finisher = true;
				LAST_FINISHER.put(target, now);
				Posture.endStagger(target);
				CombatFeedback.finisher(target);
			}
		}
		// Each mob takes each kind of blow its own way.
		scaled *= (float) dev.forja.difficulty.MobResistances.factor(target, attack.kind());
		// Monsters hit players as hard as the difficulty, their own threat and how the player is doing say.
		if (source.getEntity() instanceof net.minecraft.world.entity.Mob mob && mob instanceof net.minecraft.world.entity.monster.Enemy
			&& target instanceof Player victim) {
			scaled *= (float) (dev.forja.difficulty.ForjaDifficulty.current().damage
				* dev.forja.difficulty.Threat.of(mob).damage * dev.forja.difficulty.Adaptive.damageMultiplier(victim)
				* dev.forja.ai.Personality.damage(mob, victim));
		}
		if (source.getEntity() != null) {
			Posture.onHit(target, attack.kind(), (float) (scaled * postureScale), now);
		}
		// Elites, champions and bosses: while their guard holds, only part of a blow reaches their health.
		if (!staggered && (dev.forja.difficulty.Threat.of(target).guarded() || dev.forja.difficulty.Bosses.isBoss(target))) {
			scaled *= (float) cfg.guardHealthShare;
		}
		// A player under pressure has no time to set their armor.
		if (target instanceof Player victim) {
			attack = new AttackProfile(attack.kind(), dev.forja.difficulty.Pressure.penetration(victim, attack.penetration()),
				attack.zone(), attack.precise());
		}

		float result;
		if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			result = scaled;
		} else {
			hurtArmor.accept(scaled);
			// Breach (Forja's Brecha upgrade rides on it) still eats into armor, on top of the weapon's own bite.
			double breach = 0.0;
			ItemStack weapon = source.getWeaponItem();
			if (weapon != null && target.level() instanceof ServerLevel level) {
				breach = 1.0 - EnchantmentHelper.modifyArmorEffectiveness(level, weapon, target, source, 1.0F);
			}
			result = ArmorCalculator.apply(target, scaled, attack, breach);
		}
		return capped(target, source, result, staggered || finisher);
	}

	/**
	 * Nothing dies to one ordinary blow from a player: a mob loses at most a share of its max health to
	 * it (by its threat, lower for bosses, scaled by the difficulty). A finisher, or any blow on a staggered
	 * foe, goes past the cap, so killing fast means breaking the guard first.
	 */
	static float capped(LivingEntity target, DamageSource source, float damage, boolean breaks) {
		if (breaks || target instanceof Player || !(target instanceof net.minecraft.world.entity.Mob)
			|| !(source.getEntity() instanceof Player)) {
			return damage;
		}
		double share = dev.forja.difficulty.Bosses.isBoss(target) ? CombatConfig.get().hitCapBoss
			: dev.forja.difficulty.Threat.of(target).hitCap();
		double cap = target.getMaxHealth() * share * dev.forja.difficulty.ForjaDifficulty.current().cap;
		return (float) Math.min(damage, cap);
	}
}
