package com.dodoxgod.filo.gametest;

import com.dodoxgod.filo.combat.ArmorMath;
import com.dodoxgod.filo.combat.StaminaManager;
import com.dodoxgod.filo.config.FiloConfig;
import com.dodoxgod.filo.mixin.MobEntityAccessor;
import com.dodoxgod.filo.mixin.ServerPlayerEntityAccessor;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
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
import net.minecraft.util.Hand;
import net.minecraft.world.Difficulty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

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

	/** Contra aldeanos los mobs atacan como en vanilla (sin aviso), así que los asedios siguen funcionando. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void mobStillHitsVillagers(TestContext context) {
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

	/** Jugador simulado de pie, mirando hacia +X, en la posición relativa indicada. */
	private static FakePlayer player(TestContext context, BlockPos relative) {
		// Un jugador distinto por prueba: las pruebas se ejecutan a la vez.
		// Dificultad normal: en pacífico los golpes de mobs a jugadores no hacen daño.
		context.getWorld().getServer().setDifficulty(Difficulty.NORMAL, true);
		GameProfile profile = new GameProfile(UUID.randomUUID(), "filo_test");
		FakePlayer player = new TestPlayer(context.getWorld(), profile);
		((ServerPlayerEntityAccessor) player).filo$setJoinInvulnerabilityTicks(0);
		Vec3d pos = context.getAbsolute(Vec3d.ofBottomCenter(relative));
		player.refreshPositionAndAngles(pos.x, pos.y, pos.z, -90.0f, 0.0f);
		player.setHeadYaw(-90.0f);
		player.setHealth(player.getMaxHealth());
		player.clearActiveItem();
		player.getInventory().clear();
		StaminaManager.remove(player);
		return player;
	}

	/** Durante la esquiva, los golpes no hacen daño. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void dodgeGivesIframes(TestContext context) {
		ZombieEntity attacker = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
		FakePlayer control = player(context, new BlockPos(1, 1, 1));
		if (!control.damage(context.getWorld().getDamageSources().mobAttack(attacker), 6f)) {
			throw new GameTestException("control: sin esquivar, el golpe debería entrar");
		}
		FakePlayer player = player(context, new BlockPos(1, 1, 3));
		StaminaManager.onDodge(player);
		boolean hurt = player.damage(context.getWorld().getDamageSources().mobAttack(attacker), 6f);
		if (hurt || player.getHealth() < player.getMaxHealth()) {
			throw new GameTestException("la esquiva no dio invulnerabilidad");
		}
		context.complete();
	}

	/** Escudo recién levantado mirando al atacante: el golpe se anula y el atacante queda aturdido. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void parryStunsAttacker(TestContext context) {
		FakePlayer player = player(context, new BlockPos(1, 1, 1));
		ZombieEntity attacker = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
		player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.setCurrentHand(Hand.OFF_HAND);
		boolean hurt = player.damage(context.getWorld().getDamageSources().mobAttack(attacker), 6f);
		if (hurt || player.getHealth() < player.getMaxHealth()) {
			throw new GameTestException("el parry no anuló el golpe");
		}
		if (!attacker.hasStatusEffect(StatusEffects.SLOWNESS)) {
			throw new GameTestException("el parry no aturdió al atacante");
		}
		context.complete();
	}

	/** Atacar sin estamina hace menos daño. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void tiredAttacksDealLess(TestContext context) {
		FakePlayer player = player(context, new BlockPos(1, 1, 1));
		ZombieEntity target = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
		FiloConfig cfg = FiloConfig.get();
		int attacks = (int) Math.ceil(cfg.stamina.max / cfg.stamina.attackCost) + 1;
		for (int i = 0; i < attacks; i++) {
			StaminaManager.onAttack(player);
		}
		target.damage(context.getWorld().getDamageSources().playerAttack(player), 10f);
		double full = expected(10, 2, cfg.penetration.fist);
		double tired = expected(10 * cfg.stamina.tiredDamageMultiplier, 2, cfg.penetration.fist);
		double dealt = 20f - target.getHealth();
		// El puñetazo puede caer en la cabeza o no según hacia dónde mire; comprobamos que es el reducido.
		if (!(Math.abs(dealt - tired) < EPS || Math.abs(dealt - tired * cfg.armor.headMultiplier) < 0.2)) {
			throw new GameTestException("golpe cansado: esperado ~" + tired + " (sin cansancio " + full + "), obtenido " + dealt);
		}
		context.complete();
	}

	/** Un mob cuerpo a cuerpo avisa y acaba golpeando a un jugador que se queda quieto. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void telegraphedAttackHitsStillPlayer(TestContext context) {
		FakePlayer player = player(context, new BlockPos(1, 1, 1));
		HuskEntity husk = context.spawnEntity(EntityType.HUSK, new BlockPos(2, 1, 1));
		husk.setTarget(player);
		StringBuilder goals = new StringBuilder();
		context.waitAndRun(20, () -> ((MobEntityAccessor) husk).filo$getGoalSelector().getRunningGoals()
				.forEach(g -> goals.append(g.getGoal().getClass().getSimpleName()).append(' ')));
		context.waitAndRun(120, () -> {
			if (player.getHealth() < player.getMaxHealth()) {
				context.complete();
			} else {
				throw new GameTestException(String.format(
						"el husk no llegó a golpear al jugador quieto (distancia %.2f, objetivo=%s, vivo=%s, lentitud=%s,"
								+ " y husk=%.2f, y jugador=%.2f, ve=%s, alcance=%s, objetivos=[%s])",
						husk.distanceTo(player), husk.getTarget() == player ? "jugador" : String.valueOf(husk.getTarget()),
						husk.isAlive(), husk.hasStatusEffect(StatusEffects.SLOWNESS), husk.getY(), player.getY(),
						husk.canSee(player), husk.isInAttackRange(player), goals));
			}
		});
	}
}
