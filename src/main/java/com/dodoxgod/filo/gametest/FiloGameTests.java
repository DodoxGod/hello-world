package com.dodoxgod.filo.gametest;

import com.dodoxgod.filo.combat.ArmorMath;
import com.dodoxgod.filo.config.FiloConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Pruebas dentro de un servidor real de Minecraft. Solo se ejecutan con
 * {@code ./gradlew runGametest}; en una partida normal no hacen nada.
 */
public class FiloGameTests implements FabricGameTest {
	private static final double EPS = 0.01;

	private static void assertNear(double expected, double actual, String what) {
		if (Math.abs(expected - actual) > EPS) {
			throw new GameTestException(what + ": esperado " + expected + ", obtenido " + actual);
		}
	}

	private static double expected(double damage, double armor, double pen) {
		FiloConfig.Armor a = FiloConfig.get().armor;
		return ArmorMath.damageAfterArmor(damage, armor, pen, 0, a.curveK, a.maxReduction, a.toughnessScale);
	}

	/** Zombi sin armadura (2 de armadura natural) golpeado por otro zombi: fórmula nueva, no la vanilla. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void unarmoredUsesNewFormula(TestContext context) {
		ZombieEntity target = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		ZombieEntity attacker = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
		target.damage(context.getWorld().getDamageSources().mobAttack(attacker), 10f);
		// Vanilla: 9.84. Filo: 10 * (1 - 2/22) = 9.09.
		assertNear(expected(10, 2, 0), 20f - target.getHealth(), "daño sin armadura");
		context.complete();
	}

	/** Hierro completo, golpe de zombi a la altura del torso. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void ironArmorTorsoHit(TestContext context) {
		ZombieEntity target = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		target.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		target.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		target.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		target.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		ZombieEntity attacker = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
		target.damage(context.getWorld().getDamageSources().mobAttack(attacker), 10f);
		// Torso: casco 2*(20/3)*0.1 + peto 6*(20/8)*0.7 + pantalones 5*(20/6)*0.2 + natural 2.
		double armor = 2 * (20.0 / 3) * 0.1 + 6 * (20.0 / 8) * 0.7 + 5 * (20.0 / 6) * 0.2 + 2;
		assertNear(expected(10, armor, 0), 20f - target.getHealth(), "daño con hierro en el torso");
		context.complete();
	}

	/** Flecha rápida a la cabeza sin casco: penetra, hace +30 % y el peto apenas ayuda. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void arrowHeadshotPenetrates(TestContext context) {
		ZombieEntity target = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		target.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		ArrowEntity arrow = EntityType.ARROW.create(context.getWorld());
		if (arrow == null) throw new GameTestException("no se pudo crear la flecha");
		double headY = target.getY() + target.getHeight() * 0.9 - arrow.getHeight() * 0.5;
		arrow.setPosition(target.getX(), headY, target.getZ());
		arrow.setVelocity(3.0, 0.0, 0.0);
		target.damage(context.getWorld().getDamageSources().arrow(arrow, null), 10f);

		FiloConfig cfg = FiloConfig.get();
		double pen = Math.min(cfg.penetration.arrowMax, cfg.penetration.arrowBase + 3.0 * cfg.penetration.arrowPerSpeed);
		// Cabeza: 30 % del peto normalizado + armadura natural; +30 % de daño por ser en la cabeza.
		double armor = 6 * (20.0 / 8) * 0.3 + 2;
		double dealt = expected(10 * cfg.armor.headMultiplier, armor, pen);
		assertNear(dealt, 20f - target.getHealth(), "flecha a la cabeza");
		context.complete();
	}

	/** Golpes contundentes seguidos acaban aturdiendo a un mob grande. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void postureBreakStaggers(TestContext context) {
		IronGolemEntity golem = context.spawnEntity(EntityType.IRON_GOLEM, new BlockPos(1, 1, 1));
		ZombieEntity attacker = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
		for (int i = 0; i < 6 && !golem.hasStatusEffect(StatusEffects.SLOWNESS); i++) {
			golem.timeUntilRegen = 0;
			golem.damage(context.getWorld().getDamageSources().mobAttack(attacker), 10f);
		}
		if (!golem.hasStatusEffect(StatusEffects.SLOWNESS)) {
			throw new GameTestException("el gólem no quedó aturdido tras 6 golpes");
		}
		context.complete();
	}

	/** Con el aviso previo, un mob cuerpo a cuerpo sigue siendo capaz de golpear. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void telegraphedMobStillHits(TestContext context) {
		VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, new BlockPos(1, 1, 1));
		HuskEntity husk = context.spawnEntity(EntityType.HUSK, new BlockPos(2, 1, 1));
		husk.setTarget(villager);
		float start = villager.getHealth();
		context.waitAndRun(150, () -> {
			if (!villager.isAlive() || villager.getHealth() < start) {
				context.complete();
			} else {
				throw new GameTestException("el husk no llegó a golpear al aldeano");
			}
		});
	}
}
