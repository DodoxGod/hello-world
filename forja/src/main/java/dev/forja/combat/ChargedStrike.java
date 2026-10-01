package dev.forja.combat;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The heavy blow: hold the attack button after a swing and the weapon draws back; let go and it lands
 * harder, and hits the target's balance much harder. It costs stamina, it can be seen coming (the arm
 * stays drawn back, and everyone watching is told), and a release too early is simply nothing.
 *
 * <p>The client only says when the charge starts and when it is let go; the server times it.
 */
public final class ChargedStrike {
	/** Players charging right now: the game time the charge began. */
	private static final Map<ServerPlayer, Long> CHARGING = new WeakHashMap<>();
	/** A charged blow being struck right now: its damage and posture multipliers, read by the armor hook. */
	private static final Map<LivingEntity, double[]> STRIKING = new WeakHashMap<>();

	private ChargedStrike() {
	}

	/** Whether this stack swings with a style of its own; only those charge. */
	public static boolean charges(net.minecraft.world.item.ItemStack weapon) {
		if (SwingStyle.of(weapon) != SwingStyle.VANILLA) {
			return true;
		}
		// Every forged thing that is swung: the gauntlets, the staff and the tome, the tools. They had a charge pose
		// and never got to use it, because charging went by the swing style and theirs is the plain one.
		dev.forja.part.ForgedParts parts = weapon.get(dev.forja.registry.ModComponents.PARTS);
		// Except the staff and the tome: their charge is the spell's, on the right button (magic/Spellcasting).
		return parts != null && !dev.forja.magic.Spellcasting.casts(parts.type())
			&& (parts.type().kind == dev.forja.forge.ForgeType.Kind.WEAPON || parts.type().kind == dev.forja.forge.ForgeType.Kind.TOOL);
	}

	public static void onPayload(ServerPlayer player, byte action) {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.enabled || !cfg.chargedAttack || player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		if (action == ChargePayload.START) {
			if (charges(player.getMainHandItem()) && !player.isUsingItem()) {
				CHARGING.put(player, now);
				CombatAnim.broadcast(player, CombatAnim.Kind.CHARGE, cfg.chargeFullTicks, 1.0F, 0.0F);
			}
			return;
		}
		Long began = CHARGING.remove(player);
		CombatAnim.broadcast(player, CombatAnim.Kind.CHARGE, 0, 0.0F, 0.0F);
		if (began == null || action != ChargePayload.RELEASE) {
			return;
		}
		double share = Math.min(1.0, (now - began) / (double) Math.max(1, cfg.chargeFullTicks));
		if (share < cfg.chargeMinShare || !charges(player.getMainHandItem())) {
			return;
		}
		strike(player, share, cfg);
	}

	/** How far the player's charge has got, 0 to 1, or -1 when not charging. */
	public static double share(ServerPlayer player) {
		Long began = CHARGING.get(player);
		if (began == null) {
			return -1.0;
		}
		return Math.min(1.0, (player.level().getGameTime() - began) / (double) Math.max(1, CombatConfig.get().chargeFullTicks));
	}

	public static boolean isCharging(LivingEntity entity) {
		return entity instanceof ServerPlayer player && CHARGING.containsKey(player);
	}

	/**
	 * Knocks a charge out of the player's hands (a monster's shield bash, ai/ShieldPlay): the blow drawn back is lost,
	 * as if let go too early, and the pose drops. Returns whether there was one.
	 */
	public static boolean interrupt(LivingEntity entity) {
		// Rivets and iron bands: the head does not shake loose, and neither does the blow drawn back with it.
		if (!(entity instanceof ServerPlayer player) || Grip.holdsCharge(player.getMainHandItem()) || CHARGING.remove(player) == null) {
			return false;
		}
		CombatAnim.broadcast(player, CombatAnim.Kind.CHARGE, 0, 0.0F, 0.0F);
		return true;
	}

	private static void strike(ServerPlayer player, double share, CombatConfig cfg) {
		// Paid in full or not at all: out of breath, the blow still lands, but only as a tired one.
		ItemStack weapon = player.getMainHandItem();
		boolean paid = Stamina.trySpend(player, (cfg.chargeStaminaCost + (float) (cfg.chargeStaminaPerShare * share)) * Grip.chargeCost(weapon));
		player.swing(InteractionHand.MAIN_HAND, true);
		Entity target = target(player);
		if (target == null) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
				SoundSource.PLAYERS, 0.8F, 0.6F);
			return;
		}
		// A counterweight lands the drawn-back blow harder, a light handle a little softer (combat/Grip).
		double damage = paid ? (1.0 + cfg.chargeDamageBonus * share) * Grip.chargeDamage(weapon) : cfg.tiredDamageMultiplier;
		double posture = paid ? 1.0 + cfg.chargePostureBonus * share : 1.0;
		if (target instanceof LivingEntity living) {
			STRIKING.put(player, new double[] {damage, posture, share});
			try {
				player.attack(living);
			} finally {
				STRIKING.remove(player);
			}
			if (paid) {
				arcaneBurst(player, living, share, cfg);
			}
		} else {
			player.attack(target);
		}
		player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
			SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	/**
	 * Estallido arcano: a charged blow held all the way, with the blade's mana to pay for it, bursts round what it
	 * hit — everything else within reach of the target takes a share of the blow as magic. Short of a full
	 * charge, or with too little in the bar, nothing: the upgrade does nothing without mana.
	 */
	public static void arcaneBurst(ServerPlayer player, LivingEntity target, double share, CombatConfig cfg) {
		ItemStack weapon = player.getMainHandItem();
		float burst = dev.forja.upgrade.Upgrade.arcaneBurstShare(dev.forja.upgrade.Upgrades.fraction(weapon, dev.forja.upgrade.Upgrade.ESTALLIDO_ARCANO));
		if (burst <= 0.0F || share < 1.0 - 1.0E-6 || !dev.forja.magic.Mana.trySpend(player, dev.forja.upgrade.Upgrade.ARCANE_BURST_COST)) {
			return;
		}
		net.minecraft.server.level.ServerLevel level = player.level();
		float blow = (float) (player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * (1.0 + cfg.chargeDamageBonus));
		double radius = dev.forja.upgrade.Upgrade.ARCANE_BURST_RADIUS;
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
			other -> other != player && other != target && other.isAlive() && !other.isAlliedTo(player) && other.distanceToSqr(target) <= radius * radius)) {
			dev.forja.upgrade.CombatUpgrades.sideDamage(level, near, level.damageSources().indirectMagic(player, player), blow * burst);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT, near.getX(), near.getY(0.6), near.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
		}
		dev.forja.entity.Shockwave.burst(level, target.position(), radius, 8, dev.forja.upgrade.Upgrade.ESTALLIDO_ARCANO.color, 0.5F);
		level.sendParticles(new dev.forja.registry.GlintOptions(dev.forja.upgrade.Upgrade.ESTALLIDO_ARCANO.color, 1.3F),
			target.getX(), target.getY(0.5), target.getZ(), 30, radius * 0.4, 0.3, radius * 0.4, 0.0);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.6F, 1.3F);
	}

	/**
	 * The multipliers of the charged blow landing right now from this attacker, or null. Read by the armor
	 * hook while the attack is being dealt.
	 */
	public static double[] striking(LivingEntity attacker) {
		return STRIKING.get(attacker);
	}

	/** What the player is aiming at within reach, with nothing solid in between. */
	private static Entity target(ServerPlayer player) {
		double reach = player.entityInteractionRange() + 0.5;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(reach));
		HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB area = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
			entity -> !entity.isSpectator() && entity.isPickable() && entity != player.getVehicle(), reach * reach);
		return hit == null ? null : hit.getEntity();
	}
}
