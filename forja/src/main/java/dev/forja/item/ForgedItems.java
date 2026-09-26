package dev.forja.item;

import java.util.List;

import dev.forja.forge.ForgeStats;
import dev.forja.forge.Mastery;
import dev.forja.forge.ForgeType;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import dev.forja.upgrade.HeadThrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;

/**
 * Item classes for assembled gear. Axes, shovels and hoes extend their vanilla classes so stripping,
 * path making and tilling keep working; their stats are overwritten by the stack's components.
 * Throwable tools route right-click to the Lanzacabezas enchantment.
 */
public final class ForgedItems {
	private ForgedItems() {
	}

	public interface Forged {
		ForgeType forgeType();
	}

	public static class ForgedItem extends Item implements Forged {
		private final ForgeType type;

		public ForgedItem(ForgeType type, Item.Properties properties) {
			super(properties);
			this.type = type;
		}

		@Override
		public ForgeType forgeType() {
			return this.type;
		}

		@Override
		public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
			InteractionResult carved = dev.forja.forge.Chisel.tryUse(context, this.type);
			return carved != InteractionResult.PASS ? carved : super.useOn(context);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			InteractionResult cast = dev.forja.magic.Spellcasting.tryCast(level, player, hand, this.type);
			if (cast != InteractionResult.PASS) {
				return cast;
			}
			InteractionResult hooked = dev.forja.upgrade.WeaponThrow.tryHook(level, player, hand);
			if (hooked != InteractionResult.PASS) {
				return hooked;
			}
			InteractionResult special = dev.forja.forge.SpecialAttacks.tryUse(level, player, hand, this.type);
			if (special != InteractionResult.PASS) {
				return special;
			}
			InteractionResult riptide = dev.forja.upgrade.WeaponThrow.tryRiptide(level, player, hand, this.type);
			if (riptide != InteractionResult.PASS) {
				return riptide;
			}
			InteractionResult flung = dev.forja.upgrade.WeaponThrow.tryThrowWeapon(level, player, hand, this.type);
			if (flung != InteractionResult.PASS) {
				return flung;
			}
			InteractionResult thrown = HeadThrow.tryThrow(level, player, hand, this.type);
			return thrown != InteractionResult.PASS ? thrown : super.use(level, player, hand);
		}
	}

	public static class ForgedAxeItem extends AxeItem implements Forged {
		private final ForgeType type;

		public ForgedAxeItem(ForgeType type, Item.Properties properties) {
			super(ToolMaterial.IRON, type.attackDamage, type.attackSpeed, properties);
			this.type = type;
		}

		@Override
		public ForgeType forgeType() {
			return this.type;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			InteractionResult flung = dev.forja.upgrade.WeaponThrow.tryThrowWeapon(level, player, hand, this.type);
			if (flung != InteractionResult.PASS) {
				return flung;
			}
			InteractionResult thrown = HeadThrow.tryThrow(level, player, hand, this.type);
			return thrown != InteractionResult.PASS ? thrown : super.use(level, player, hand);
		}
	}

	/** Forged arrows: the entity they turn into reads its own tip and fletching. */
	/** Honeycomb on a copper piece seals the patina where it is, the same as on a block. */
	public static boolean waxWith(ItemStack gear, ItemStack other, Player player) {
		if (!other.is(net.minecraft.world.item.Items.HONEYCOMB) || !dev.forja.forge.Oxidation.isCopper(gear)
			|| dev.forja.forge.Oxidation.waxed(gear)) {
			return false;
		}
		dev.forja.forge.Oxidation.wax(gear);
		other.shrink(1);
		player.playSound(net.minecraft.sounds.SoundEvents.HONEYCOMB_WAX_ON, 1.0F, 1.0F);
		return true;
	}

	public static class ForgedArrowItem extends net.minecraft.world.item.ArrowItem implements Forged {
		public ForgedArrowItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ForgeType forgeType() {
			return ForgeType.FLECHA;
		}

		@Override
		public net.minecraft.world.entity.projectile.arrow.AbstractArrow createArrow(Level level, ItemStack arrow, LivingEntity shooter, ItemStack weapon) {
			return new dev.forja.entity.ForgedArrow(level, shooter, arrow, weapon);
		}
	}

	public static class ForgedShovelItem extends ShovelItem implements Forged {
		private final ForgeType type;

		public ForgedShovelItem(ForgeType type, Item.Properties properties) {
			super(ToolMaterial.IRON, type.attackDamage, type.attackSpeed, properties);
			this.type = type;
		}

		@Override
		public ForgeType forgeType() {
			return this.type;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			InteractionResult thrown = HeadThrow.tryThrow(level, player, hand, this.type);
			return thrown != InteractionResult.PASS ? thrown : super.use(level, player, hand);
		}
	}

	/**
	 * Extends ShieldItem so first-person blocking uses the shield pose instead of the sword one. Sneaking
	 * while using it turns the block into a shield bash: a shove that hurts and knocks back what is in
	 * front, on a short cooldown, and costs durability.
	 */
	public static class ForgedShieldItem extends ShieldItem implements Forged {
		public static final int BASH_COOLDOWN = 60;
		public static final double BASH_RANGE = 2.6;

		public ForgedShieldItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ForgeType forgeType() {
			return ForgeType.ESCUDO;
		}

		/** Damage of a bash: the plate's own bite plus the rim, so heavier shields shove harder. */
		public static float bashDamage(ItemStack shield) {
			ForgedParts parts = shield.get(ModComponents.PARTS);
			if (parts == null) {
				return 0.0F;
			}
			return 2.0F + parts.primary().attackDamageBonus + parts.primary().toughness;
		}

		/** What a bash would shove: whatever living thing stands right in front of the player. */
		public static List<LivingEntity> bashTargets(Level level, Player player) {
			Vec3 center = player.getEyePosition().add(player.getLookAngle().scale(BASH_RANGE / 2.0));
			return level.getEntitiesOfClass(
				LivingEntity.class, new AABB(center, center).inflate(BASH_RANGE / 2.0 + 0.5), other -> other != player && other.isAlive()
			);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack shield = player.getItemInHand(hand);
			// Sneaking still blocks with nothing in front; face something and the block becomes a bash.
			if (!player.isShiftKeyDown() || shield.isBroken() || player.getCooldowns().isOnCooldown(shield)) {
				return super.use(level, player, hand);
			}
			List<LivingEntity> targets = bashTargets(level, player);
			if (targets.isEmpty()) {
				// Nothing to shove up close: a shield with Bumeran goes out instead.
				InteractionResult flung = dev.forja.upgrade.WeaponThrow.tryThrowShield(level, player, hand);
				return flung != InteractionResult.PASS ? flung : super.use(level, player, hand);
			}
			if (level instanceof ServerLevel serverLevel) {
				bash(serverLevel, player, shield, hand, targets);
			}
			player.getCooldowns().addCooldown(shield, BASH_COOLDOWN);
			player.swing(hand, true);
			return InteractionResult.CONSUME;
		}

		private static void bash(ServerLevel level, Player player, ItemStack shield, InteractionHand hand, List<LivingEntity> targets) {
			Vec3 center = player.getEyePosition().add(player.getLookAngle().scale(BASH_RANGE / 2.0));
			float damage = bashDamage(shield);
			for (LivingEntity victim : targets) {
				DamageSource source = level.damageSources().playerAttack(player);
				victim.hurtServer(level, source, damage);
				victim.knockback(1.1, player.getX() - victim.getX(), player.getZ() - victim.getZ(), source, damage);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9F, 0.8F);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 4, 0.3, 0.3, 0.3, 0.0);
			shield.hurtAndBreak(2, player, hand.asEquipmentSlot());
		}
	}

	/**
	 * A vanilla bow whose limbs set how fast it draws and how hard its arrows hit. Arrows get
	 * +0.25 base damage per point of the limbs' damage bonus; Tension and Maestria draw faster.
	 */
	public static class ForgedBowItem extends BowItem implements Forged {
		public ForgedBowItem(ForgeType type, Item.Properties properties) {
			super(properties);
		}

		@Override
		public ForgeType forgeType() {
			return ForgeType.ARCO;
		}

		public static float drawSpeed(ItemStack stack) {
			ForgedParts parts = stack.get(ModComponents.PARTS);
			return parts == null ? 1.0F : sheet(stack, parts).drawSpeed;
		}

		public static double arrowDamageBonus(ItemStack stack) {
			ForgedParts parts = stack.get(ModComponents.PARTS);
			return parts == null ? 0.0 : sheet(stack, parts).arrowDamage - 2.0;
		}

		private static ForgeStats.Sheet sheet(ItemStack stack, ForgedParts parts) {
			return ForgeStats.sheet(stack, parts);
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
			int duration = this.getUseDuration(stack, entity);
			int held = Math.round((duration - remainingTime) * drawSpeed(stack));
			return super.releaseUsing(stack, level, entity, duration - held);
		}

		@Override
		protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack projectile, boolean isCrit) {
			Projectile arrow = super.createProjectile(level, shooter, weapon, projectile, isCrit);
			if (arrow instanceof AbstractArrow abstractArrow) {
				// Vanilla arrows always start at 2 base damage.
				abstractArrow.setBaseDamage(2.0 + arrowDamageBonus(weapon));
			}
			return arrow;
		}
	}

	/** A vanilla crossbow whose limbs and Maestria add base damage to the bolts it fires. */
	public static class ForgedCrossbowItem extends CrossbowItem implements Forged {
		public ForgedCrossbowItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ForgeType forgeType() {
			return ForgeType.BALLESTA;
		}

		@Override
		protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack projectile, boolean isCrit) {
			Projectile bolt = super.createProjectile(level, shooter, weapon, projectile, isCrit);
			if (bolt instanceof AbstractArrow arrow) {
				arrow.setBaseDamage(2.0 + ForgedBowItem.arrowDamageBonus(weapon));
				// Tormenta de flechas: the whole volley leaves the crossbow critical and biting harder.
				if (dev.forja.upgrade.Synergy.TORMENTA_DE_FLECHAS.active(weapon)) {
					arrow.setCritArrow(true);
					arrow.setBaseDamage(3.0 + ForgedBowItem.arrowDamageBonus(weapon));
				}
			}
			return bolt;
		}
	}

	/** A vanilla fishing rod; the shaft and the line decide how long it lasts. */
	public static class ForgedFishingRodItem extends FishingRodItem implements Forged {
		public ForgedFishingRodItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ForgeType forgeType() {
			return ForgeType.CANA;
		}
	}

	/** Vanilla's smash attack, with the fall damage scaled by the head's material. */
	public static class ForgedMaceItem extends MaceItem implements Forged {
		public ForgedMaceItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ForgeType forgeType() {
			return ForgeType.MAZO;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			InteractionResult special = dev.forja.forge.SpecialAttacks.tryUse(level, player, hand, ForgeType.MAZO);
			return special != InteractionResult.PASS ? special : super.use(level, player, hand);
		}

		public static float smashMultiplier(ItemStack stack) {
			ForgedParts parts = stack.get(ModComponents.PARTS);
			return parts == null ? 1.0F : ForgeStats.maceSmashMultiplier(parts.primary());
		}

		@Override
		public float getAttackDamageBonus(Entity victim, float damage, DamageSource source) {
			float bonus = super.getAttackDamageBonus(victim, damage, source);
			ItemStack weapon = source.getWeaponItem();
			if (weapon != null && weapon.isBroken()) {
				return 0.0F;
			}
			return weapon != null && bonus > 0.0F ? bonus * smashMultiplier(weapon) : bonus;
		}
	}

	public static class ForgedHoeItem extends HoeItem implements Forged {
		private final ForgeType type;

		public ForgedHoeItem(ForgeType type, Item.Properties properties) {
			super(ToolMaterial.IRON, type.attackDamage, type.attackSpeed, properties);
			this.type = type;
		}

		@Override
		public ForgeType forgeType() {
			return this.type;
		}
	}
}
