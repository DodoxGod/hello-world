package dev.forja.ai;

import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.CombatAnim;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatFeedback;
import dev.forja.combat.Posture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * How monsters defend themselves (ideas 31 to 40 of the plan):
 * <ul>
 *   <li><b>shields</b>: a raised one stops blows from the front, but every blow it takes eats into the
 *   mob's balance, and a broken balance breaks the guard: shield down for 3 seconds;</li>
 *   <li><b>parry</b>: a blow that meets a shield raised in the last 4 ticks bounces off, the player
 *   reels, and the mob's next blow comes after a short warning (its counter);</li>
 *   <li><b>dodge</b>: a hop to the side with 6 ticks of i-frames, every 3 seconds at most.</li>
 * </ul>
 */
public final class MobDefense {
	public static final int PARRY_TICKS = 4;
	public static final int GUARD_BROKEN_TICKS = 60;
	public static final int DODGE_IFRAMES = 6;
	public static final int DODGE_COOLDOWN = 60;
	public static final int COUNTER_TICKS = 40;
	public static final int COUNTER_WINDUP = 4;
	/** Share of a blocked blow that still goes into the mob's posture. */
	public static final double BLOCK_POSTURE = 0.7;

	private static final class State {
		long dodgeUntil = -1;
		long dodgeReady;
		long guardBrokenUntil;
		long counterUntil = -1;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();

	private MobDefense() {
	}

	private static State state(Mob mob) {
		return STATES.computeIfAbsent(mob, m -> new State());
	}

	public static boolean hasShield(Mob mob) {
		return mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	public static boolean guardBroken(Mob mob) {
		State s = STATES.get(mob);
		return s != null && mob.level().getGameTime() < s.guardBrokenUntil;
	}

	public static boolean dodgeReady(Mob mob) {
		State s = STATES.get(mob);
		return s == null || mob.level().getGameTime() >= s.dodgeReady;
	}

	public static boolean dodging(Mob mob) {
		State s = STATES.get(mob);
		return s != null && mob.level().getGameTime() <= s.dodgeUntil;
	}

	/** Whether the mob's counter is up: its next blow comes after a short warning. */
	public static boolean counterReady(Mob mob) {
		State s = STATES.get(mob);
		return s != null && mob.level().getGameTime() <= s.counterUntil;
	}

	public static void spendCounter(Mob mob) {
		State s = STATES.get(mob);
		if (s != null) {
			s.counterUntil = -1;
		}
	}

	/** The warning of the mob's next melee blow: short while its counter is up. */
	public static int windup(Mob mob) {
		return counterReady(mob) ? COUNTER_WINDUP : Math.max(1, CombatConfig.get().windupTicks);
	}

	/** Raise the shield (if it has one and its guard is not broken). */
	public static void raise(Mob mob) {
		if (hasShield(mob) && !guardBroken(mob) && !mob.isUsingItem()) {
			mob.startUsingItem(InteractionHand.OFF_HAND);
		}
	}

	/** A hop to the side, away from where the target is looking along; 6 ticks of i-frames. */
	public static boolean dodge(Mob mob, Player target) {
		State s = state(mob);
		long now = mob.level().getGameTime();
		if (now < s.dodgeReady || !mob.onGround()) {
			return false;
		}
		Vec3 toMob = new Vec3(mob.getX() - target.getX(), 0.0, mob.getZ() - target.getZ());
		if (toMob.lengthSqr() < 1.0E-6) {
			return false;
		}
		Vec3 side = new Vec3(-toMob.z, 0.0, toMob.x).normalize();
		if (mob.getRandom().nextBoolean()) {
			side = side.scale(-1.0);
		}
		mob.setDeltaMovement(side.x * 0.6, 0.25, side.z * 0.6);
		mob.hurtMarked = true;
		s.dodgeUntil = now + DODGE_IFRAMES;
		s.dodgeReady = now + DODGE_COOLDOWN;
		CombatAnim.broadcast(mob, CombatAnim.Kind.DODGE, DODGE_IFRAMES + 4, (float) side.x, (float) side.z);
		CombatFeedback.dodge(mob);
		return true;
	}

	/**
	 * Before a blow reaches a mob: its dodge, its parry, and the balance a raised shield costs it.
	 *
	 * @return false to stop the blow outright
	 */
	public static boolean allowDamage(Mob mob, DamageSource source, float amount) {
		if (dodging(mob) && source.getEntity() != null) {
			CombatFeedback.dodgedHit(mob);
			return false;
		}
		if (!mob.isUsingItem() || !hasShield(mob) || mob.getUsedItemHand() != InteractionHand.OFF_HAND
			|| !(source.getEntity() instanceof Player player) || !facing(mob, source)) {
			return true;
		}
		long now = mob.level().getGameTime();
		// A parry: the shield went up just now. The blow bounces off and the player reels.
		if (source.getDirectEntity() == player && mob.getTicksUsingItem() <= PARRY_TICKS) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0));
			Vec3 back = new Vec3(player.getX() - mob.getX(), 0.0, player.getZ() - mob.getZ());
			if (back.lengthSqr() > 1.0E-6) {
				back = back.normalize();
				player.setDeltaMovement(back.x * 0.6, 0.2, back.z * 0.6);
				player.hurtMarked = true;
			}
			state(mob).counterUntil = now + COUNTER_TICKS;
			CombatAnim.broadcast(mob, CombatAnim.Kind.PARRY, 8, 1.0F, 0.0F);
			mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 1.6F);
			return false;
		}
		// A plain block: the shield takes it, the balance pays for it; a broken balance breaks the guard.
		boolean wasStaggered = Posture.isStaggered(mob, now);
		Posture.onHit(mob, dev.forja.combat.AttackClassifier.classify(source, mob).kind(), (float) (amount * BLOCK_POSTURE), now);
		if (!wasStaggered && Posture.isStaggered(mob, now)) {
			breakGuard(mob);
		}
		return true;
	}

	/** The guard gives: shield down for 3 seconds. */
	public static void breakGuard(Mob mob) {
		mob.stopUsingItem();
		state(mob).guardBrokenUntil = mob.level().getGameTime() + GUARD_BROKEN_TICKS;
		CombatFeedback.guardBreak(mob);
	}

	private static boolean facing(Mob mob, DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return false;
		}
		Vec3 to = from.subtract(mob.position());
		return new Vec3(to.x, 0.0, to.z).normalize().dot(mob.getViewVector(1.0F)) > 0.0;
	}

	/** Gives some of the monsters that spawn a shield (zombies and their kin), more on higher difficulties. */
	public static void arm(Mob mob) {
		if (!(mob instanceof net.minecraft.world.entity.monster.zombie.Zombie) || !mob.getOffhandItem().isEmpty()
			|| !(mob.level() instanceof ServerLevel)) {
			return;
		}
		double chance = CombatConfig.get().shieldChance * dev.forja.difficulty.ForjaDifficulty.current().threat;
		if (mob.getRandom().nextDouble() < chance) {
			mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
			mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, 0.05F);
		}
	}
}
