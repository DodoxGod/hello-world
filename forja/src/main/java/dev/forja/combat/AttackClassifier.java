package dev.forja.combat;

import java.util.Optional;

import dev.forja.forge.ForgeType;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Works out the kind, penetration and landing zone of a blow. */
public final class AttackClassifier {
	private AttackClassifier() {
	}

	public static AttackProfile classify(DamageSource source, LivingEntity target) {
		CombatConfig cfg = CombatConfig.get();
		Entity direct = source.getDirectEntity();
		Entity attacker = source.getEntity();

		if (direct instanceof ThrownTrident trident) {
			return new AttackProfile(DamageKind.PIERCE, cfg.penThrownTrident, zoneAt(target, centerY(trident)), true);
		}
		if (direct instanceof AbstractArrow arrow) {
			double speed = arrow.getDeltaMovement().length();
			double pen = ArmorMath.clamp(cfg.penArrowBase + speed * cfg.penArrowPerSpeed, 0.0, cfg.penArrowMax);
			if (ChargedArrows.isCharged(arrow)) {
				pen = ArmorMath.clamp(pen + cfg.chargedArrowExtraPenetration, 0.0, 1.0);
			}
			return new AttackProfile(DamageKind.PIERCE, pen, zoneAt(target, centerY(arrow)), true);
		}
		if (direct instanceof Projectile projectile) {
			return new AttackProfile(DamageKind.BLUNT, 0.0, zoneAt(target, centerY(projectile)), true);
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return new AttackProfile(DamageKind.BLUNT, 0.0, HitZone.WHOLE, false);
		}
		if (direct instanceof LivingEntity living && direct == attacker) {
			return melee(living, target, cfg);
		}
		return AttackProfile.NEUTRAL;
	}

	private static AttackProfile melee(LivingEntity attacker, LivingEntity target, CombatConfig cfg) {
		ItemStack weapon = attacker.getMainHandItem();
		DamageKind kind;
		double pen;
		ForgedParts parts = weapon.get(ModComponents.PARTS);
		if (parts != null && (parts.type().kind == ForgeType.Kind.WEAPON || parts.type().kind == ForgeType.Kind.TOOL)) {
			ForgeType type = parts.type();
			switch (type) {
				case HACHA, PICAHACHA, GUADANA -> { kind = DamageKind.SLASH; pen = cfg.penAxe; }
				case ESPADA, DAGA, ESPADON -> { kind = DamageKind.SLASH; pen = cfg.penBlade; }
				case MAZO, MARTILLO, MANGUAL, GUANTELETES, BACULO -> { kind = DamageKind.BLUNT; pen = cfg.penBlunt; }
				case LANZA, TRIDENTE -> { kind = DamageKind.PIERCE; pen = cfg.penSpear; }
				case PICO -> { kind = DamageKind.PIERCE; pen = cfg.penPick; }
				default -> { kind = DamageKind.BLUNT; pen = cfg.penOtherTool; }
			}
		} else if (weapon.is(Items.MACE)) {
			kind = DamageKind.BLUNT;
			pen = cfg.penBlunt;
		} else if (weapon.is(ItemTags.AXES)) {
			kind = DamageKind.SLASH;
			pen = cfg.penAxe;
		} else if (weapon.is(ItemTags.SWORDS)) {
			kind = DamageKind.SLASH;
			pen = cfg.penBlade;
		} else if (weapon.is(Items.TRIDENT) || weapon.has(DataComponents.PIERCING_WEAPON)) {
			kind = DamageKind.PIERCE;
			pen = cfg.penSpear;
		} else if (weapon.is(ItemTags.PICKAXES)) {
			kind = DamageKind.PIERCE;
			pen = cfg.penPick;
		} else if (!weapon.isEmpty() && weapon.has(DataComponents.TOOL)) {
			kind = DamageKind.BLUNT;
			pen = cfg.penOtherTool;
		} else if (attacker instanceof Player) {
			kind = DamageKind.BLUNT;
			pen = cfg.penFist;
		} else {
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()).toString();
			kind = cfg.ataquesNaturales.getOrDefault(id, DamageKind.BLUNT);
			pen = 0.0;
		}
		return new AttackProfile(kind, pen, meleeZone(attacker, target), attacker instanceof Player);
	}

	/** Players hit where they look; mobs at the height of their arms, so spiders go for the legs. */
	private static HitZone meleeZone(LivingEntity attacker, LivingEntity target) {
		if (attacker instanceof Player) {
			Vec3 eye = attacker.getEyePosition();
			Vec3 end = eye.add(attacker.getViewVector(1.0F).scale(8.0));
			Optional<Vec3> hit = target.getBoundingBox().inflate(0.1).clip(eye, end);
			return hit.map(v -> zoneAt(target, v.y)).orElse(HitZone.TORSO);
		}
		return zoneAt(target, attacker.getY() + attacker.getBbHeight() * 0.6);
	}

	private static double centerY(Entity e) {
		return e.getY() + e.getBbHeight() * 0.5;
	}

	public static HitZone zoneAt(LivingEntity target, double y) {
		if (!CombatConfig.get().hitZones) return HitZone.WHOLE;
		return HitZone.fromRelativeHeight((y - target.getY()) / Math.max(target.getBbHeight(), 0.01));
	}
}
