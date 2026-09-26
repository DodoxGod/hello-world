package dev.forja.forge;

import java.util.List;

import dev.forja.registry.ModComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Heavy weapons have a move of their own: sneak and right-click. The greatsword sweeps everything
 * around it and the hammer and mace slam the ground, throwing whatever stands near into the air. Both
 * cost durability and go on cooldown, so they are an opening, not a rotation.
 */
public final class SpecialAttacks {
	public static final int WHIRL_COOLDOWN = 120;
	public static final int QUAKE_COOLDOWN = 160;
	public static final int REAP_COOLDOWN = 140;
	public static final int CHARGE_COOLDOWN = 100;
	public static final double WHIRL_RANGE = 3.5;
	public static final double QUAKE_RANGE = 4.5;
	/** The scythe pulls from further away than anything else swings. */
	public static final double REAP_RANGE = 6.0;
	/** How far the gauntlets carry you when you throw yourself forward. */
	public static final double CHARGE_PUSH = 1.4;

	private SpecialAttacks() {
	}

	/** Whether this kind of gear has a special move at all. */
	public static boolean has(ForgeType type) {
		return type == ForgeType.ESPADON || type == ForgeType.MARTILLO || type == ForgeType.MAZO
			|| type == ForgeType.GUADANA || type == ForgeType.GUANTELETES;
	}

	public static InteractionResult tryUse(Level level, Player player, InteractionHand hand, ForgeType type) {
		ItemStack weapon = player.getItemInHand(hand);
		if (!has(type) || !player.isShiftKeyDown() || !weapon.has(ModComponents.PARTS) || weapon.isBroken()) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(weapon)) {
			return InteractionResult.FAIL;
		}
		boolean quake = type == ForgeType.MARTILLO || type == ForgeType.MAZO;
		if (quake && !player.onGround()) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel serverLevel) {
			switch (type) {
				case MARTILLO, MAZO -> quake(serverLevel, player, weapon, hand);
				case GUADANA -> reap(serverLevel, player, weapon, hand);
				case GUANTELETES -> charge(serverLevel, player, weapon, hand);
				default -> whirl(serverLevel, player, weapon, hand);
			}
		}
		player.getCooldowns().addCooldown(weapon, switch (type) {
			case MARTILLO, MAZO -> QUAKE_COOLDOWN;
			case GUADANA -> REAP_COOLDOWN;
			case GUANTELETES -> CHARGE_COOLDOWN;
			default -> WHIRL_COOLDOWN;
		});
		player.swing(hand, true);
		return InteractionResult.CONSUME;
	}

	/**
	 * What the weapon hits for. The player's attribute already includes the weapon once it has been held
	 * for a tick; the item's own modifiers cover the moment it is drawn.
	 */
	private static float attackDamage(Player player, ItemStack weapon) {
		float held = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		var modifiers = weapon.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
		float fromItem = modifiers == null ? 0.0F : (float) modifiers.modifiers().stream()
			.filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
			.mapToDouble(entry -> entry.modifier().amount())
			.sum();
		return Math.max(held, 1.0F + fromItem);
	}

	/** Torbellino: one turn of the greatsword that cuts everything around. */
	private static void whirl(ServerLevel level, Player player, ItemStack weapon, InteractionHand hand) {
		float damage = attackDamage(player, weapon) * 0.6F;
		List<LivingEntity> targets = around(level, player, WHIRL_RANGE);
		for (LivingEntity victim : targets) {
			DamageSource source = level.damageSources().playerAttack(player);
			victim.hurtServer(level, source, damage);
			victim.knockback(0.6, player.getX() - victim.getX(), player.getZ() - victim.getZ(), source, damage);
		}
		// How far the turn reached, drawn: nothing else about the move says, and it is the whole of it.
		dev.forja.entity.Shockwave.burst(level, player.position(), WHIRL_RANGE, 6, 0xE8EEF5, 0.3F);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY(0.6), player.getZ(), 8, WHIRL_RANGE / 2.0, 0.2, WHIRL_RANGE / 2.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);
		spend(weapon, player, hand, targets.isEmpty() ? 1 : 3);
	}

	/** Sismo: the head comes down and the ground throws everything nearby up. */
	private static void quake(ServerLevel level, Player player, ItemStack weapon, InteractionHand hand) {
		float damage = attackDamage(player, weapon) * 0.5F;
		List<LivingEntity> targets = around(level, player, QUAKE_RANGE);
		for (LivingEntity victim : targets) {
			DamageSource source = level.damageSources().playerAttack(player);
			victim.hurtServer(level, source, damage);
			victim.setDeltaMovement(victim.getDeltaMovement().add(0.0, 0.55, 0.0));
			victim.hurtMarked = true;
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), player);
		}
		// The ground going out from under the hammer: the ring throws up bits of whatever the floor
		// really is, which is exactly what a Sismo ought to look like and cost nothing to get.
		dev.forja.entity.Shockwave.burst(level, player.position(), QUAKE_RANGE, 8, 0xC8A070, 0.45F);
		level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 3, QUAKE_RANGE / 3.0, 0.1, QUAKE_RANGE / 3.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 30, QUAKE_RANGE / 2.0, 0.1, QUAKE_RANGE / 2.0, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.6F);
		if (player instanceof ServerPlayer server && !targets.isEmpty()) {
			server.sendOverlayMessage(Component.translatable("gui.forja.sismo", targets.size()));
		}
		spend(weapon, player, hand, targets.isEmpty() ? 1 : 4);
	}

	/**
	 * Siega: the scythe sweeps wide and drags everything it catches towards you. Little damage of its
	 * own; what it is for is putting a crowd where the next swing can reach all of it.
	 */
	private static void reap(ServerLevel level, Player player, ItemStack weapon, InteractionHand hand) {
		float damage = attackDamage(player, weapon) * 0.35F;
		List<LivingEntity> targets = around(level, player, REAP_RANGE);
		for (LivingEntity victim : targets) {
			DamageSource source = level.damageSources().playerAttack(player);
			victim.hurtServer(level, source, damage);
			// Straight towards the smith, and whatever push the hit gave them is wiped.
			net.minecraft.world.phys.Vec3 towards = player.position().subtract(victim.position()).normalize().scale(0.55);
			victim.setDeltaMovement(towards.x, 0.15, towards.z);
			victim.hurtMarked = true;
		}
		// Siega negra: what the scythe drags in feeds whoever swung it, up to three hearts.
		if (!targets.isEmpty() && dev.forja.upgrade.Synergy.SIEGA_NEGRA.active(weapon)) {
			player.heal(Math.min(6.0F, targets.size()));
			level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(1.0), player.getZ(), 16, 0.4, 0.5, 0.4, 0.05);
		}
		dev.forja.entity.Shockwave.burst(level, player.position(), REAP_RANGE, 7, 0x9AA6B8, 0.25F);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY(0.8), player.getZ(), 12, REAP_RANGE / 2.0, 0.3, REAP_RANGE / 2.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
		if (player instanceof ServerPlayer server && !targets.isEmpty()) {
			server.sendOverlayMessage(Component.translatable("gui.forja.siega", targets.size()));
		}
		spend(weapon, player, hand, targets.isEmpty() ? 1 : 3);
	}

	/**
	 * Embestida: the gloves throw the smith forward. Whatever is in the way takes the whole of the punch
	 * and is knocked off its feet; nothing in the way and you have only spent the distance.
	 */
	private static void charge(ServerLevel level, Player player, ItemStack weapon, InteractionHand hand) {
		net.minecraft.world.phys.Vec3 look = player.getLookAngle();
		player.setDeltaMovement(look.x * CHARGE_PUSH, Math.max(0.25, look.y * 0.4), look.z * CHARGE_PUSH);
		player.hurtMarked = true;
		player.resetFallDistance();
		float damage = attackDamage(player, weapon) * 1.5F;
		List<LivingEntity> ahead = level.getEntitiesOfClass(
			LivingEntity.class,
			player.getBoundingBox().expandTowards(look.scale(3.0)).inflate(1.0),
			other -> other != player && other.isAlive()
		);
		LivingEntity first = null;
		for (LivingEntity victim : ahead) {
			if (first == null || victim.distanceToSqr(player) < first.distanceToSqr(player)) {
				first = victim;
			}
		}
		if (first != null) {
			DamageSource source = level.damageSources().playerAttack(player);
			first.hurtServer(level, source, damage);
			first.knockback(1.2, -look.x, -look.z, source, damage);
			first.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), player);
			level.playSound(null, first.getX(), first.getY(), first.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.9F);
		}
		level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY(1.0), player.getZ(), 18, 0.3, 0.3, 0.3, 0.2);
		spend(weapon, player, hand, first == null ? 1 : 3);
	}

	private static List<LivingEntity> around(ServerLevel level, Player player, double range) {
		return level.getEntitiesOfClass(
			LivingEntity.class, new AABB(player.position(), player.position()).inflate(range, 2.0, range),
			other -> other != player && other.isAlive() && other.distanceToSqr(player) <= range * range
		);
	}

	private static void spend(ItemStack weapon, Player player, InteractionHand hand, int durability) {
		weapon.hurtAndBreak(durability, player, hand.asEquipmentSlot());
	}
}
