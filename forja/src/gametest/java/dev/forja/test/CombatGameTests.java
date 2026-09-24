package dev.forja.test;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import dev.forja.combat.ArmorMath;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.CombatStats;
import dev.forja.combat.ParryRhythm;
import dev.forja.combat.Posture;
import dev.forja.combat.Stamina;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The combat overhaul, tested on a real server: the armor formula, forged materials, stamina, the
 * dodge, posture, the reworked parry and the new vanilla mob behaviour. Run with ./gradlew runGameTest.
 */
public class CombatGameTests {
	private static final double EPS = 0.02;

	/** A simulated player that, unlike Fabric's, can be hurt. */
	static class TestPlayer extends FakePlayer {
		TestPlayer(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "forja_test"));
		}

		@Override
		public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
			return false;
		}
	}

	/** A player standing at a relative position, looking along +X, with nothing in hand. */
	private static TestPlayer player(GameTestHelper helper, BlockPos relative) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		TestPlayer player = new TestPlayer(helper.getLevel());
		clearSpawnGrace(player);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(relative));
		player.setPos(pos.x, pos.y, pos.z);
		player.setYRot(-90.0F);
		player.setYHeadRot(-90.0F);
		player.setXRot(0.0F);
		player.setHealth(player.getMaxHealth());
		Stamina.forget(player);
		return player;
	}

	/**
	 * A fresh player gets a few seconds of spawn grace that only its own tick counts down, and a fake
	 * player never ticks. The field is private and its name is not ours to rely on, so any int field
	 * of ServerPlayer still at its starting 60 is set to zero.
	 */
	private static void clearSpawnGrace(ServerPlayer player) {
		for (java.lang.reflect.Field field : ServerPlayer.class.getDeclaredFields()) {
			if (field.getType() == int.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
				try {
					field.setAccessible(true);
					if (field.getInt(player) == 60) {
						field.setInt(player, 0);
					}
				} catch (ReflectiveOperationException | RuntimeException ignored) {
					// Not the field we are after.
				}
			}
		}
	}

	private static Zombie bareZombie(GameTestHelper helper, BlockPos pos) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, pos);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		zombie.setNoAi(true);
		return zombie;
	}

	private static float hit(LivingEntity target, DamageSource source, float amount) {
		float before = target.getHealth();
		target.invulnerableTime = 0;
		target.hurtServer((ServerLevel) target.level(), source, amount);
		return before - target.getHealth();
	}

	private static double expected(double damage, double armor, double pen) {
		CombatConfig c = CombatConfig.get();
		return ArmorMath.damageAfterArmor(damage, armor, pen, 0, c.curveK, c.maxReduction, c.toughnessScale);
	}

	/** No armor: only the zombie's own hide, and the new curve instead of vanilla's. */
	@GameTest
	public void unarmoredUsesNewFormula(GameTestHelper helper) {
		Zombie target = bareZombie(helper, new BlockPos(1, 1, 1));
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 1));
		helper.runAfterDelay(2, () -> {
			double natural = target.getArmorValue();
			float dealt = hit(target, helper.getLevel().damageSources().mobAttack(attacker), 10F);
			helper.assertTrue(Math.abs(dealt - expected(10, natural, 0)) < EPS,
				"daño sin armadura: esperado " + expected(10, natural, 0) + ", obtenido " + dealt);
			helper.succeed();
		});
	}

	/** Same plate, different lining: a leather lining soaks a blunt blow better than an iron one. */
	@GameTest
	public void liningSoaksBluntBlows(GameTestHelper helper) {
		Zombie soft = bareZombie(helper, new BlockPos(1, 1, 1));
		Zombie hard = bareZombie(helper, new BlockPos(1, 1, 4));
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 2));
		soft.setItemSlot(EquipmentSlot.CHEST, Assembler.create(ForgeType.PECHERA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.CUERO)));
		hard.setItemSlot(EquipmentSlot.CHEST, Assembler.create(ForgeType.PECHERA, List.of(ForgeMaterial.HIERRO, ForgeMaterial.HIERRO)));
		helper.runAfterDelay(2, () -> {
			DamageSource blow = helper.getLevel().damageSources().mobAttack(attacker);
			float onSoft = hit(soft, blow, 10F);
			float onHard = hit(hard, blow, 10F);
			helper.assertTrue(onSoft > 0 && onSoft < onHard, "forro de cuero " + onSoft + " vs forro de hierro " + onHard);
			helper.succeed();
		});
	}

	/** A fast arrow to the head hurts more than the same arrow to the chest. */
	@GameTest
	public void arrowToTheHeadHurtsMore(GameTestHelper helper) {
		Zombie headTarget = bareZombie(helper, new BlockPos(1, 1, 1));
		Zombie bodyTarget = bareZombie(helper, new BlockPos(1, 1, 4));
		helper.runAfterDelay(2, () -> {
			float toHead = hit(headTarget, arrowAt(helper, headTarget, 0.9), 8F);
			float toBody = hit(bodyTarget, arrowAt(helper, bodyTarget, 0.6), 8F);
			helper.assertTrue(toHead > toBody * 1.2F, "cabeza " + toHead + " vs torso " + toBody);
			helper.succeed();
		});
	}

	private static DamageSource arrowAt(GameTestHelper helper, LivingEntity target, double relativeHeight) {
		Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(arrow != null, "no se pudo crear la flecha");
		arrow.setPos(target.getX(), target.getY() + target.getBbHeight() * relativeHeight - arrow.getBbHeight() * 0.5, target.getZ());
		arrow.setDeltaMovement(3.0, 0.0, 0.0);
		return helper.getLevel().damageSources().arrow(arrow, null);
	}

	/** Blunt blows in a row stagger a big mob. */
	@GameTest
	public void postureBreakStaggersGolem(GameTestHelper helper) {
		IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(1, 1, 1));
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 1));
		for (int i = 0; i < 8 && !Posture.isStaggered(golem, helper.getLevel().getGameTime()); i++) {
			hit(golem, helper.getLevel().damageSources().mobAttack(attacker), 10F);
		}
		helper.assertTrue(golem.hasEffect(MobEffects.SLOWNESS), "el gólem no quedó aturdido");
		helper.succeed();
	}

	/** Out of stamina, a swing lands softer. */
	@GameTest
	public void tiredSwingsHitSofter(GameTestHelper helper) {
		TestPlayer fresh = player(helper, new BlockPos(1, 1, 1));
		TestPlayer tired = player(helper, new BlockPos(1, 1, 4));
		Zombie a = bareZombie(helper, new BlockPos(2, 1, 1));
		Zombie b = bareZombie(helper, new BlockPos(2, 1, 4));
		for (int i = 0; i < 12; i++) {
			Stamina.onAttack(tired);
		}
		Stamina.onAttack(fresh);
		helper.runAfterDelay(2, () -> {
			float full = hit(a, helper.getLevel().damageSources().playerAttack(fresh), 10F);
			float weak = hit(b, helper.getLevel().damageSources().playerAttack(tired), 10F);
			helper.assertTrue(weak < full * 0.8F, "cansado " + weak + " vs descansado " + full);
			helper.succeed();
		});
	}

	/** A dodge leaves no opening for a blow; without it, the same blow lands. */
	@GameTest
	public void dodgeGivesIframes(GameTestHelper helper) {
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 1));
		TestPlayer control = player(helper, new BlockPos(1, 1, 1));
		TestPlayer dodger = player(helper, new BlockPos(1, 1, 4));
		DamageSource blow = helper.getLevel().damageSources().mobAttack(attacker);
		helper.assertTrue(hit(control, blow, 6F) > 0, "control: sin esquivar el golpe debería entrar");
		Stamina.onDodge(dodger);
		helper.assertTrue(hit(dodger, blow, 6F) == 0, "la esquiva no dio invulnerabilidad");
		helper.succeed();
	}

	private static TestPlayer shieldUp(GameTestHelper helper, BlockPos pos) {
		TestPlayer player = player(helper, pos);
		ItemStack shield = Assembler.create(ForgeType.ESCUDO, Assembler.defaultMaterials(ForgeType.ESCUDO));
		player.setItemInHand(InteractionHand.OFF_HAND, shield);
		player.startUsingItem(InteractionHand.OFF_HAND);
		return player;
	}

	/** A forged shield raised in time: perfect parry, no damage, and the attacker loses its balance. */
	@GameTest
	public void perfectParryStaggers(GameTestHelper helper) {
		TestPlayer player = shieldUp(helper, new BlockPos(1, 1, 1));
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 1));
		float dealt = hit(player, helper.getLevel().damageSources().mobAttack(attacker), 6F);
		helper.assertTrue(dealt == 0, "el escudo no paró el golpe: " + dealt);
		helper.assertTrue(Posture.isStaggered(attacker, helper.getLevel().getGameTime()), "la parada perfecta no aturdió al atacante");
		helper.succeed();
	}

	/** A shield raised again right after the last raise has no parry window. */
	@GameTest
	public void mashedShieldDoesNotParry(GameTestHelper helper) {
		TestPlayer player = shieldUp(helper, new BlockPos(1, 1, 1));
		long now = helper.getLevel().getGameTime();
		ParryRhythm.onRaise(player, now - 2);
		ParryRhythm.onRaise(player, now);
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 1));
		hit(player, helper.getLevel().damageSources().mobAttack(attacker), 6F);
		helper.assertTrue(!Posture.isStaggered(attacker, helper.getLevel().getGameTime()), "una subida apresurada no debería parar");
		helper.succeed();
	}

	/** A vanilla melee mob warns, then lands its blow on a player who stays put. */
	@GameTest(maxTicks = 200)
	public void telegraphedAttackHitsStillPlayer(GameTestHelper helper) {
		TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(2, 1, 1));
		husk.setTarget(player);
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(player.getHealth() < player.getMaxHealth(),
				"el husk no golpeó al jugador quieto (distancia " + husk.distanceTo(player) + ")");
			helper.succeed();
		});
	}

	/** Against villagers, mobs fight as they always did. */
	@GameTest(maxTicks = 200)
	public void mobsStillHitVillagers(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(2, 1, 1));
		husk.setTarget(villager);
		float start = villager.getHealth();
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(!villager.isAlive() || villager.getHealth() < start, "el husk no golpeó al aldeano");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void zombieLungesAtMidRange(GameTestHelper helper) {
		TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(6, 1, 1));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setTarget(player);
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(CombatStats.count(zombie, CombatStats.LUNGE) > 0, "el zombi no embistió");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void skeletonFiresChargedShot(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		int previous = cfg.skeletonChargedEvery;
		cfg.skeletonChargedEvery = 1;
		TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(6, 1, 6));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setTarget(player);
		helper.runAfterDelay(110, () -> {
			cfg.skeletonChargedEvery = previous;
			helper.assertTrue(CombatStats.count(skeleton, CombatStats.CHARGED_SHOT) > 0, "el esqueleto no hizo un disparo cargado");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void creeperFeints(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		double previous = cfg.creeperFeintChance;
		cfg.creeperFeintChance = 1.0;
		TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		Creeper creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(3, 1, 1));
		creeper.setTarget(player);
		helper.runAfterDelay(40, () -> {
			cfg.creeperFeintChance = previous;
			boolean feinted = CombatStats.count(creeper, CombatStats.FEINT) > 0;
			boolean alive = creeper.isAlive();
			creeper.discard();
			helper.assertTrue(feinted && alive, "el creeper no fintó (finta=" + feinted + ", vivo=" + alive + ")");
			helper.succeed();
		});
	}
}
