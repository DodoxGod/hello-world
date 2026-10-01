package com.dodoxgod.filo.combat;

import com.dodoxgod.filo.config.FiloConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/** Decide el tipo de daño, la penetración y la zona golpeada de cada ataque. */
public final class AttackClassifier {
	private AttackClassifier() {
	}

	public static AttackProfile classify(DamageSource source, LivingEntity target) {
		FiloConfig.Penetration pen = FiloConfig.get().penetration;
		Entity direct = source.getSource();
		Entity attacker = source.getAttacker();

		if (direct instanceof TridentEntity trident) {
			return new AttackProfile(DamageKind.PIERCE, pen.thrownTrident, zoneAt(target, centerY(trident)), true);
		}
		if (direct instanceof PersistentProjectileEntity arrow) {
			double speed = arrow.getVelocity().length();
			double p = ArmorMath.clamp(pen.arrowBase + speed * pen.arrowPerSpeed, 0.0, pen.arrowMax);
			if (ChargedArrows.isCharged(arrow)) {
				p = ArmorMath.clamp(p + FiloConfig.get().mobs.chargedArrowExtraPenetration, 0.0, 1.0);
			}
			return new AttackProfile(DamageKind.PIERCE, p, zoneAt(target, centerY(arrow)), true);
		}
		if (direct instanceof ProjectileEntity projectile) {
			// Bolas de nieve, cargas de viento, bolas de fuego...
			return new AttackProfile(DamageKind.BLUNT, 0.0, zoneAt(target, centerY(projectile)), true);
		}
		if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
			return new AttackProfile(DamageKind.BLUNT, 0.0, HitZone.WHOLE, false);
		}
		if (direct instanceof LivingEntity living && direct == attacker) {
			return melee(living, target);
		}
		return AttackProfile.NEUTRAL;
	}

	private static AttackProfile melee(LivingEntity attacker, LivingEntity target) {
		FiloConfig cfg = FiloConfig.get();
		FiloConfig.Penetration pen = cfg.penetration;
		ItemStack weapon = attacker.getMainHandStack();
		Item item = weapon.getItem();

		DamageKind kind;
		double penetration;
		if (item instanceof MaceItem) {
			kind = DamageKind.BLUNT;
			penetration = pen.mace;
		} else if (item instanceof AxeItem) {
			kind = DamageKind.SLASH;
			penetration = pen.axe;
		} else if (item instanceof SwordItem) {
			kind = DamageKind.SLASH;
			penetration = pen.sword;
		} else if (item instanceof TridentItem) {
			kind = DamageKind.PIERCE;
			penetration = pen.trident;
		} else if (item instanceof PickaxeItem) {
			kind = DamageKind.PIERCE;
			penetration = pen.pickaxe;
		} else if (item instanceof MiningToolItem) {
			kind = DamageKind.BLUNT;
			penetration = pen.otherTool;
		} else if (attacker instanceof PlayerEntity) {
			kind = DamageKind.BLUNT;
			penetration = pen.fist;
		} else {
			String id = Registries.ENTITY_TYPE.getId(attacker.getType()).toString();
			kind = cfg.naturalAttacks.getOrDefault(id, DamageKind.BLUNT);
			penetration = pen.mobNatural;
		}

		boolean precise = attacker instanceof PlayerEntity;
		return new AttackProfile(kind, penetration, meleeZone(attacker, target), precise);
	}

	/**
	 * Jugadores: donde apuntan. Mobs: a la altura de sus brazos, así que los mobs bajos
	 * (arañas, zombis bebé) golpean las piernas y los altos el torso.
	 */
	private static HitZone meleeZone(LivingEntity attacker, LivingEntity target) {
		if (attacker instanceof PlayerEntity) {
			Vec3d eye = attacker.getEyePos();
			Vec3d end = eye.add(attacker.getRotationVec(1.0f).multiply(8.0));
			Optional<Vec3d> hit = target.getBoundingBox().expand(0.1).raycast(eye, end);
			return hit.map(v -> zoneAt(target, v.y)).orElse(HitZone.TORSO);
		}
		return zoneAt(target, attacker.getY() + attacker.getHeight() * 0.6);
	}

	private static double centerY(Entity e) {
		return e.getY() + e.getHeight() * 0.5;
	}

	public static HitZone zoneAt(LivingEntity target, double y) {
		if (!FiloConfig.get().armor.hitZones) return HitZone.WHOLE;
		double height = Math.max(target.getHeight(), 0.01);
		return HitZone.fromRelativeHeight((y - target.getY()) / height);
	}
}
