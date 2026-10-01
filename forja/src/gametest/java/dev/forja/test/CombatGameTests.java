package dev.forja.test;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import dev.forja.combat.ArmorCalculator;
import dev.forja.combat.ArmorMath;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.WeaponGuard;
import dev.forja.combat.Combos;
import dev.forja.combat.ChargedStrike;
import dev.forja.combat.ChargePayload;
import dev.forja.combat.CombatStats;
import dev.forja.combat.MaterialCombat;
import dev.forja.combat.ParryRhythm;
import dev.forja.combat.Posture;
import dev.forja.combat.Stamina;
import dev.forja.combat.SwingStyle;
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
	public static class TestPlayer extends FakePlayer {
		public TestPlayer(ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "forja_test"));
		}

		@Override
		public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
			return false;
		}
	}

	/** A player standing at a relative position, looking along +X, with nothing in hand. */
	static TestPlayer player(GameTestHelper helper, BlockPos relative) {
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

	/** Tests compare mobs with each other, so none of them may come as a veteran or an elite by chance. */
	static void noRandomThreat() {
		CombatConfig.get().veteranChance = 0.0;
		CombatConfig.get().eliteChance = 0.0;
		// Nor with a shield, which changes how they fight.
		CombatConfig.get().shieldChance = 0.0;
		// Nor with a staff or a tome, which makes them keep their distance and cast instead of swinging.
		dev.forja.ForjaConfig.get().baculos = 0.0F;
		dev.forja.ForjaConfig.get().grimorios = 0.0F;
	}

	/**
	 * Stone under the test's box at y 0, the level {@link #player} stands on. Without it a mob spawned at y 1 drops
	 * a block onto the world's floor and fights a test player hanging a block over its head (a test player never
	 * falls), which is not the fight the test describes.
	 */
	static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
	}

	/** A mob with nothing in its hands: vanilla gives some a spear or a sword, and a spear fights another way. */
	static void emptyHands(net.minecraft.world.entity.Mob mob) {
		mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
	}

	private static Zombie bareZombie(GameTestHelper helper, BlockPos pos) {
		noRandomThreat();
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
		Stamina.onDodge(dodger, 1.0F, 0.0F);
		helper.assertTrue(hit(dodger, blow, 6F) == 0, "la esquiva no dio invulnerabilidad");
		helper.succeed();
	}

	/**
	 * The client moves first and asks after, from a stamina bar synced in half-point steps: a dodge a
	 * hair short of the cost still goes through, one far short does not.
	 */
	@GameTest
	public void dodgeForgivesAStaleStaminaBar(GameTestHelper helper) {
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 1));
		TestPlayer nearly = player(helper, new BlockPos(1, 1, 1));
		TestPlayer spent = player(helper, new BlockPos(1, 1, 4));
		CombatConfig cfg = CombatConfig.get();
		Stamina.trySpend(nearly, cfg.staminaMax - (cfg.dodgeCost - 2.0F));
		Stamina.trySpend(spent, cfg.staminaMax - cfg.dodgeCost * 0.4F);
		Stamina.onDodge(nearly, 0.0F, -1.0F);
		Stamina.onDodge(spent, 0.0F, -1.0F);
		helper.assertTrue(Stamina.value(nearly) == 0.0F, "la esquiva perdonada debería vaciar la estamina, no dejarla negativa");
		DamageSource blow = helper.getLevel().damageSources().mobAttack(attacker);
		helper.assertTrue(hit(nearly, blow, 6F) == 0, "una esquiva a 2 puntos del coste debería contar");
		helper.assertTrue(hit(spent, blow, 6F) > 0, "una esquiva sin estamina no debería dar invulnerabilidad");
		helper.succeed();
	}

	/** Each weapon gets its own swing: blades sweep, heavy heads chop, points thrust, the rest stay vanilla. */
	@GameTest
	public void weaponsSwingTheirOwnWay(GameTestHelper helper) {
		helper.assertTrue(SwingStyle.of(new ItemStack(Items.IRON_SWORD)) == SwingStyle.SLASH, "espada");
		helper.assertTrue(SwingStyle.of(new ItemStack(Items.IRON_AXE)) == SwingStyle.CHOP, "hacha");
		helper.assertTrue(SwingStyle.of(new ItemStack(Items.MACE)) == SwingStyle.CHOP, "maza");
		helper.assertTrue(SwingStyle.of(new ItemStack(Items.TRIDENT)) == SwingStyle.THRUST, "tridente");
		helper.assertTrue(SwingStyle.of(new ItemStack(Items.STICK)) == SwingStyle.VANILLA, "palo");
		helper.assertTrue(SwingStyle.of(ItemStack.EMPTY) == SwingStyle.VANILLA, "mano vacia");
		helper.assertTrue(SwingStyle.of(Assembler.create(ForgeType.MARTILLO, Assembler.defaultMaterials(ForgeType.MARTILLO))) == SwingStyle.CHOP, "martillo forjado");
		helper.assertTrue(SwingStyle.of(Assembler.create(ForgeType.LANZA, Assembler.defaultMaterials(ForgeType.LANZA))) == SwingStyle.THRUST, "lanza forjada");
		helper.assertTrue(SwingStyle.of(Assembler.create(ForgeType.ESPADON, Assembler.defaultMaterials(ForgeType.ESPADON))) == SwingStyle.SLASH, "espadon forjado");
		helper.succeed();
	}

	/** What the tooltip shows is what the formula uses: chain turns edges, leather soaks blows, a sword is not armor. */
	@GameTest
	public void armorTooltipProfileMatchesTheFormula(GameTestHelper helper) {
		MaterialCombat.Profile chain = ArmorCalculator.profileOf(new ItemStack(Items.CHAINMAIL_CHESTPLATE));
		MaterialCombat.Profile leather = ArmorCalculator.profileOf(new ItemStack(Items.LEATHER_CHESTPLATE));
		MaterialCombat.Profile plate = ArmorCalculator.profileOf(new ItemStack(Items.NETHERITE_CHESTPLATE));
		helper.assertTrue(chain != null && leather != null && plate != null, "las armaduras deberian tener perfil");
		helper.assertTrue(leather.blunt() > leather.slash(), "el cuero deberia aguantar mejor los golpes que los cortes");
		helper.assertTrue(plate.weight() > leather.weight(), "la netherite deberia pesar mas que el cuero");
		helper.assertTrue(ArmorCalculator.profileOf(new ItemStack(Items.IRON_SWORD)) == null, "una espada no es armadura");
		helper.assertTrue(ArmorCalculator.profileOf(Assembler.create(ForgeType.PECHERA, Assembler.defaultMaterials(ForgeType.PECHERA))) != null,
			"una pechera forjada deberia tener perfil");
		helper.succeed();
	}

	/** A player holding a weapon, standing at a relative position and looking along +X. */
	private static TestPlayer armed(GameTestHelper helper, BlockPos pos, ItemStack weapon) {
		TestPlayer player = player(helper, pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		return player;
	}

	/** Turns a player to look straight at an entity. */
	private static void face(ServerPlayer player, LivingEntity target) {
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
	}

	private static float strike(ServerPlayer player, LivingEntity target) {
		float before = target.getHealth();
		target.invulnerableTime = 0;
		player.attack(target);
		return before - target.getHealth();
	}

	/** A charged blow let go at full charge hits about twice as hard as a plain one. */
	@GameTest(maxTicks = 100)
	public void chargedStrikeHitsHarder(GameTestHelper helper) {
		Zombie plain = bareZombie(helper, new BlockPos(3, 1, 1));
		Zombie charged = bareZombie(helper, new BlockPos(3, 1, 4));
		TestPlayer a = armed(helper, new BlockPos(1, 1, 1), new ItemStack(Items.IRON_SWORD));
		TestPlayer b = armed(helper, new BlockPos(1, 1, 4), new ItemStack(Items.IRON_SWORD));
		face(a, plain);
		face(b, charged);
		float normal = strike(a, plain);
		ChargedStrike.onPayload(b, ChargePayload.START);
		helper.runAfterDelay(CombatConfig.get().chargeFullTicks + 1, () -> {
			float before = charged.getHealth();
			charged.invulnerableTime = 0;
			ChargedStrike.onPayload(b, ChargePayload.RELEASE);
			float heavy = before - charged.getHealth();
			helper.assertTrue(normal > 0, "el golpe normal no hizo daño");
			helper.assertTrue(heavy > normal * 1.6F, "cargado " + heavy + " vs normal " + normal);
			helper.succeed();
		});
	}

	/** Let go too early and the charge strikes nothing. */
	@GameTest
	public void earlyReleaseStrikesNothing(GameTestHelper helper) {
		Zombie target = bareZombie(helper, new BlockPos(3, 1, 1));
		TestPlayer player = armed(helper, new BlockPos(1, 1, 1), new ItemStack(Items.IRON_SWORD));
		face(player, target);
		float before = target.getHealth();
		ChargedStrike.onPayload(player, ChargePayload.START);
		ChargedStrike.onPayload(player, ChargePayload.RELEASE);
		helper.assertTrue(target.getHealth() == before, "una carga soltada al instante no debería golpear");
		helper.succeed();
	}

	/** A staggered foe struck from behind takes a finisher, and the stagger ends. */
	@GameTest
	public void finisherFromBehind(GameTestHelper helper) {
		Zombie front = bareZombie(helper, new BlockPos(1, 1, 2));
		Zombie back = bareZombie(helper, new BlockPos(5, 1, 2));
		// Both face +Z; one player stands in front of its zombie, the other behind.
		TestPlayer facing = armed(helper, new BlockPos(1, 1, 4), new ItemStack(Items.IRON_SWORD));
		TestPlayer behind = armed(helper, new BlockPos(5, 1, 0), new ItemStack(Items.IRON_SWORD));
		face(facing, front);
		face(behind, back);
		long now = helper.getLevel().getGameTime();
		Posture.breakPosture(front, now);
		Posture.breakPosture(back, now);
		float plain = strike(facing, front);
		float finished = strike(behind, back);
		helper.assertTrue(finished > plain * 1.7F, "remate " + finished + " vs de frente " + plain);
		helper.assertFalse(Posture.isStaggered(back, now), "el remate debería acabar el aturdimiento");
		helper.succeed();
	}

	/** The third full-strength hit of a combo lands heavier than the first. */
	@GameTest
	public void comboThirdHitIsHeavier(GameTestHelper helper) {
		Zombie target = bareZombie(helper, new BlockPos(3, 1, 1));
		target.setHealth(target.getMaxHealth());
		TestPlayer player = armed(helper, new BlockPos(1, 1, 1), new ItemStack(Items.IRON_SWORD));
		face(player, target);
		float[] hits = new float[3];
		for (int i = 0; i < 3; i++) {
			// Straight through the damage hook: a simulated player never recovers its swing, so a real
			// attack from it always counts as a weak one and would break the combo on its own.
			Combos.onAttack(player, 1.0F);
			target.setHealth(target.getMaxHealth());
			hits[i] = hit(target, helper.getLevel().damageSources().playerAttack(player), 6F);
		}
		helper.assertTrue(hits[2] > hits[0] * 1.2F, "tercer golpe " + hits[2] + " vs primero " + hits[0]);
		helper.succeed();
	}

	/** A dodge that meets a blow opens a counter: the next hit lands harder. */
	@GameTest
	public void perfectDodgeOpensACounter(GameTestHelper helper) {
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 1));
		Zombie control = bareZombie(helper, new BlockPos(3, 1, 4));
		TestPlayer dodger = armed(helper, new BlockPos(1, 1, 1), new ItemStack(Items.IRON_SWORD));
		TestPlayer plain = armed(helper, new BlockPos(1, 1, 4), new ItemStack(Items.IRON_SWORD));
		face(dodger, attacker);
		face(plain, control);
		Stamina.onDodge(dodger, -1.0F, 0.0F);
		helper.assertTrue(hit(dodger, helper.getLevel().damageSources().mobAttack(attacker), 6F) == 0, "la esquiva debería evitar el golpe");
		helper.assertTrue(Stamina.counterOpen(dodger), "esquivar un golpe de verdad debería abrir el contraataque");
		float counter = strike(dodger, attacker);
		float normal = strike(plain, control);
		helper.assertTrue(counter > normal * 1.3F, "contraataque " + counter + " vs normal " + normal);
		helper.assertFalse(Stamina.counterOpen(dodger), "el contraataque se gasta con el golpe");
		helper.succeed();
	}

	/** A sword raised in time parries a blow completely; with a shield in the other hand, the shield is used. */
	@GameTest
	public void swordGuardParries(GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		helper.assertTrue(WeaponGuard.is(sword), "una espada debería poder ponerse en guardia");
		helper.assertFalse(WeaponGuard.is(new ItemStack(Items.SHIELD)), "un escudo no es una guardia de arma");
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 1));
		TestPlayer player = armed(helper, new BlockPos(1, 1, 1), sword);
		face(player, attacker);
		player.startUsingItem(InteractionHand.MAIN_HAND);
		helper.assertTrue(player.getItemBlockingWith() != null, "la espada levantada debería bloquear");
		helper.assertTrue(hit(player, helper.getLevel().damageSources().mobAttack(attacker), 6F) == 0, "la parada con la espada debería parar todo el golpe");
		TestPlayer both = armed(helper, new BlockPos(1, 1, 4), new ItemStack(Items.IRON_SWORD));
		both.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		helper.assertTrue(WeaponGuard.yieldsToShield(both, InteractionHand.MAIN_HAND, both.getMainHandItem()), "con escudo en la otra mano, manda el escudo");
		helper.succeed();
	}

	/** Mail turns edges better than hammers, and says so. */
	@GameTest
	public void chainmailTurnsEdges(GameTestHelper helper) {
		MaterialCombat.Profile mail = ArmorCalculator.profileOf(new ItemStack(Items.CHAINMAIL_CHESTPLATE));
		helper.assertTrue(mail != null && mail.slash() > 1.2 && mail.blunt() < 1.0, "la cota de malla debería parar cortes y no golpes");
		helper.succeed();
	}

	/** No ordinary blow takes more than 45 % of a normal mob's health; a blow on a staggered one does. */
	@GameTest
	public void hitCapStopsOneShots(GameTestHelper helper) {
		Zombie capped = bareZombie(helper, new BlockPos(3, 1, 1));
		Zombie staggered = bareZombie(helper, new BlockPos(3, 1, 4));
		TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		float max = capped.getMaxHealth();
		float dealt = hit(capped, helper.getLevel().damageSources().playerAttack(player), 100F);
		helper.assertTrue(dealt <= max * 0.45F + 0.01F && dealt > 0, "tope: " + dealt + " de " + max);
		Posture.breakPosture(staggered, helper.getLevel().getGameTime());
		float open = hit(staggered, helper.getLevel().damageSources().playerAttack(player), 100F);
		helper.assertTrue(open > max * 0.45F, "a un aturdido no le aplica el tope: " + open);
		helper.succeed();
	}

	/** An elite's guard halves what reaches its health until its posture breaks. */
	@GameTest
	public void eliteGuardHalvesDamage(GameTestHelper helper) {
		Zombie normal = bareZombie(helper, new BlockPos(3, 1, 1));
		Zombie elite = bareZombie(helper, new BlockPos(3, 1, 4));
		dev.forja.difficulty.Threat.ELITE.mark(elite);
		// The same blow on both: a mob's swing lands at the same height on either.
		Zombie attacker = bareZombie(helper, new BlockPos(5, 1, 3));
		float plain = hit(normal, helper.getLevel().damageSources().mobAttack(attacker), 4F);
		float guarded = hit(elite, helper.getLevel().damageSources().mobAttack(attacker), 4F);
		helper.assertTrue(Math.abs(guarded - plain * 0.5F) < 0.05F, "guardia: " + guarded + " vs " + plain);
		helper.succeed();
	}

	/** Blows in a row build pressure, up to 0.6, and pressure gets through armor. */
	@GameTest
	public void pressureBuildsAndPierces(GameTestHelper helper) {
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 1));
		TestPlayer fresh = player(helper, new BlockPos(1, 1, 1));
		TestPlayer pressed = player(helper, new BlockPos(1, 1, 4));
		for (TestPlayer p : List.of(fresh, pressed)) {
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		}
		for (int i = 0; i < 20; i++) {
			dev.forja.difficulty.Pressure.onHit(pressed);
		}
		double pressure = dev.forja.difficulty.Pressure.of(pressed);
		helper.assertTrue(Math.abs(pressure - 0.6) < 1.0E-6, "la presión debería topar en 0,6: " + pressure);
		DamageSource blow = helper.getLevel().damageSources().mobAttack(attacker);
		float calm = hit(fresh, blow, 6F);
		pressed.setHealth(pressed.getMaxHealth());
		float under = hit(pressed, blow, 6F);
		helper.assertTrue(under > calm * 1.3F, "bajo presión " + under + " vs tranquilo " + calm);
		helper.succeed();
	}

	/** Each stagger in a row is shorter, and the bar to the next one is longer. */
	@GameTest
	public void staggersResistRepeats(GameTestHelper helper) {
		Zombie zombie = bareZombie(helper, new BlockPos(3, 1, 1));
		long now = helper.getLevel().getGameTime();
		double firstMax = Posture.max(zombie);
		Posture.breakPosture(zombie, now);
		int first = Posture.staggerLeft(zombie);
		Posture.endStagger(zombie);
		Posture.breakPosture(zombie, now);
		int second = Posture.staggerLeft(zombie);
		helper.assertTrue(second < first, "el segundo aturdimiento debería durar menos: " + first + " -> " + second);
		helper.assertTrue(Posture.max(zombie) > firstMax, "la postura máxima debería subir tras aturdirlo");
		helper.assertTrue(Posture.resistance(zombie) > 0.0, "debería tener resistencia al aturdimiento");
		helper.succeed();
	}

	/** Right after a finisher the same foe cannot take another one. */
	@GameTest
	public void finisherHasACooldown(GameTestHelper helper) {
		Zombie zombie = bareZombie(helper, new BlockPos(5, 1, 2));
		TestPlayer behind = armed(helper, new BlockPos(5, 1, 0), new ItemStack(Items.IRON_SWORD));
		face(behind, zombie);
		long now = helper.getLevel().getGameTime();
		Posture.breakPosture(zombie, now);
		zombie.setHealth(zombie.getMaxHealth());
		strike(behind, zombie);
		helper.assertFalse(Posture.isStaggered(zombie, now), "el primer remate debería acabar el aturdimiento");
		Posture.breakPosture(zombie, now);
		zombie.setHealth(zombie.getMaxHealth());
		strike(behind, zombie);
		helper.assertTrue(Posture.isStaggered(zombie, now), "un segundo remate enseguida no debería contar");
		helper.succeed();
	}

	/** Each mob takes each kind of blow its own way. */
	@GameTest
	public void mobsResistKindsOfBlow(GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(3, 1, 1));
		helper.assertTrue(dev.forja.difficulty.MobResistances.factor(skeleton, dev.forja.combat.DamageKind.BLUNT)
			> dev.forja.difficulty.MobResistances.factor(skeleton, dev.forja.combat.DamageKind.PIERCE), "un esqueleto sufre más los golpes que las flechas");
		skeleton.discard();
		helper.succeed();
	}

	/** Legend makes monsters hit harder than Smith. */
	@GameTest
	public void difficultyScalesMobDamage(GameTestHelper helper) {
		Zombie attacker = bareZombie(helper, new BlockPos(3, 1, 1));
		TestPlayer a = player(helper, new BlockPos(1, 1, 1));
		TestPlayer b = player(helper, new BlockPos(1, 1, 4));
		CombatConfig cfg = CombatConfig.get();
		String previous = cfg.dificultad;
		try {
			cfg.dificultad = "HERRERO";
			float smith = hit(a, helper.getLevel().damageSources().mobAttack(attacker), 4F);
			cfg.dificultad = "LEYENDA";
			float legend = hit(b, helper.getLevel().damageSources().mobAttack(attacker), 4F);
			helper.assertTrue(Math.abs(legend - smith * 1.5F) < 0.05F, "leyenda " + legend + " vs herrero " + smith);
		} finally {
			cfg.dificultad = previous;
		}
		helper.succeed();
	}

	/** With the chances forced up, a fresh hostile comes as an elite: tagged, named and tougher. */
	@GameTest
	public void spawnsRollThreat(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		double vet = cfg.veteranChance;
		double elite = cfg.eliteChance;
		double eliteMax = cfg.eliteChanceMax;
		cfg.eliteChance = 1.0;
		cfg.eliteChanceMax = 1.0;
		Zombie zombie;
		try {
			zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
		} finally {
			cfg.veteranChance = vet;
			cfg.eliteChance = elite;
			cfg.eliteChanceMax = eliteMax;
		}
		helper.assertTrue(dev.forja.difficulty.Threat.of(zombie) == dev.forja.difficulty.Threat.ELITE, "debería salir de élite");
		helper.assertTrue(zombie.getMaxHealth() >= 20.0F * 2.5F - 0.01F, "un élite debería tener más vida: " + zombie.getMaxHealth());
		helper.assertTrue(zombie.hasCustomName(), "un élite lleva nombre");
		zombie.discard();
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
		floor(helper);
		TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		noRandomThreat();
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(2, 1, 1));
		emptyHands(husk);
		husk.setTarget(player);
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(player.getHealth() < player.getMaxHealth(),
				"el husk no golpeó al jugador quieto (distancia " + husk.distanceTo(player)
					+ ", objetivo " + husk.getTarget() + ", le pegó " + husk.getLastHurtByMob()
					+ ", vida " + husk.getHealth() + ", aturdido " + Posture.isStaggered(husk, helper.getLevel().getGameTime())
					+ ", turnos del jugador " + dev.forja.combat.AttackTokens.held(player)
					+ ", decisión " + (dev.forja.ai.MobAi.mind(husk) == null ? "sin mente" : dev.forja.ai.MobAi.mind(husk).decision)
					+ ", ticks vividos " + husk.tickCount + ", trozo activo " + helper.getLevel().isPositionEntityTicking(husk.blockPosition()) + ", etiquetas " + husk.entityTags() + ", mira un duelo " + dev.forja.ai.Duels.watching(husk)
					+ ", metas " + ((dev.forja.mixin.MobGoalsAccess) husk).forjaGoals().getAvailableGoals().stream().filter(g -> g.isRunning())
						.map(g -> g.getGoal().getClass().getSimpleName()).toList() + ", " + whyNoSwing(husk, player) + ")");
			helper.succeed();
		});
	}

	/** What stands between a mob and a swing at its target: its goals, a path, its reach, its turn, its sight. */
	private static String whyNoSwing(net.minecraft.world.entity.Mob mob, TestPlayer player) {
		var goals = ((dev.forja.mixin.MobGoalsAccess) mob).forjaGoals().getAvailableGoals().stream()
			.map(g -> g.getGoal().getClass().getSimpleName() + "(" + g.getPriority() + ")").toList();
		var mind = dev.forja.ai.MobAi.mind(mob);
		long now = mob.level().getGameTime();
		return "todas las metas " + goals + ", camino " + (mob.getNavigation().createPath(player, 0) != null)
			+ ", al alcance " + mob.isWithinMeleeAttackRange(player) + ", perdido " + (mind != null && dev.forja.ai.HonestPerception.lost(mind, now))
			+ ", visto hace " + (mind == null ? -1 : now - mind.lastSeenAt) + ", turnos libres "
			+ dev.forja.combat.AttackTokens.free(player, dev.forja.ai.Aggression.maxAttackers(mob, player)) + " de "
			+ dev.forja.ai.Aggression.maxAttackers(mob, player) + ", espera " + (mind == null ? -1 : mind.windup);
	}

	/**
	 * Forja's own monsters warn their plain blow as well (Andy, 2026-09-26). Before this they struck with
	 * no warning while the network-driven mob of the same family always warned, which put the rules a
	 * head start ahead in every comparison.
	 */
	@GameTest(maxTicks = 200)
	public void forjaMobsWarnTheirBlowToo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Asked of mobs that are never put in the world: a zombie standing about with nothing to do goes after the
		// nearest player, and in a run of seventy tests that is another test's, whose turn it then takes.
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(zombie != null && dev.forja.combat.AttackTokens.warns(zombie), "un zombi vanilla debería avisar");
		for (var type : List.of(dev.forja.registry.ModEntities.TENAZA, dev.forja.registry.ModEntities.PERCUTOR,
			dev.forja.registry.ModEntities.MOLDE_ROTO, dev.forja.registry.ModEntities.CARGADOR_DE_CARBON,
			dev.forja.registry.ModEntities.AUTOMATA)) {
			var mob = type.create(level, EntitySpawnReason.EVENT);
			helper.assertTrue(mob != null && dev.forja.combat.AttackTokens.warns(mob),
				"los mobs de Forja también avisan su golpe: " + type);
		}
		// And the warned blow still lands on somebody who stands there and takes it.
		TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		noRandomThreat();
		var mould = helper.spawn(dev.forja.registry.ModEntities.MOLDE_ROTO, new BlockPos(2, 1, 1));
		mould.setTarget(player);
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(player.getHealth() < player.getMaxHealth(),
				"el molde roto no golpeó al jugador quieto (distancia " + mould.distanceTo(player) + ")");
			helper.succeed();
		});
	}

	/**
	 * The broken mould holds what it copied (Andy, 2026-09-26): after a recast its main hand carries a cast
	 * of the weapon that hit it — same item, same colours — which adds nothing to its attack and which it
	 * never drops, not even killed by the player it copied.
	 */
	@GameTest(maxTicks = 100)
	public void mouldHoldsItsCopyAndNeverDropsIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		noRandomThreat();
		ItemStack sword = Assembler.create(ForgeType.ESPADA,
			List.of(ForgeMaterial.NETHERITA, ForgeMaterial.MADERA, ForgeMaterial.HIERRO), level.registryAccess());
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		var mould = helper.spawn(dev.forja.registry.ModEntities.MOLDE_ROTO, new BlockPos(3, 1, 1));
		mould.setNoAi(true);
		var attack = mould.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		helper.assertTrue(mould.getMainHandItem().isEmpty(), "un molde recién hecho no sostiene nada");
		mould.consider(level, player.getMainHandItem());
		helper.runAfterDelay(dev.forja.entity.BrokenMould.RECAST_WINDUP + 2, () -> {
			ItemStack held = mould.getMainHandItem();
			helper.assertTrue(held.is(sword.getItem()), "tras refundir debería sostener la copia de la espada, sostiene " + held);
			helper.assertTrue(held != player.getMainHandItem() && player.getMainHandItem().has(dev.forja.registry.ModComponents.PARTS),
				"lo que sostiene es una copia: el jugador sigue con su espada");
			helper.assertTrue(ItemStack.isSameItemSameComponents(held, sword),
				"la copia es exactamente la espada, con sus piezas y mejoras");
			float chance = mould.getDropChances().byEquipment(EquipmentSlot.MAINHAND);
			helper.assertTrue(chance == 0.0F, "la copia no se suelta nunca, probabilidad " + chance);
			// It hits for what the recast set: the copy brings its own damage and the base makes up the rest,
			// so the two are not counted twice.
			double expected = Math.max(dev.forja.entity.BrokenMould.BASE_DAMAGE,
				Math.min(dev.forja.entity.BrokenMould.COPY_CEILING, dev.forja.entity.BrokenMould.damageOf(sword) * 1.4));
			double total = attack.getBaseValue() + dev.forja.entity.BrokenMould.damageOf(held);
			helper.assertTrue(Math.abs(total - expected) < EPS,
				"base " + attack.getBaseValue() + " + copia " + dev.forja.entity.BrokenMould.damageOf(held) + " deberían sumar " + expected);
			// Killed by the player, which is when a mob drops what it holds: the template, never the sword.
			var around = mould.getBoundingBox().inflate(3.0);
			for (var stray : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around)) {
				stray.discard();
			}
			mould.setLastHurtByPlayer(player, 100);
			mould.die(level.damageSources().playerAttack(player));
			int swords = 0;
			int templates = 0;
			for (var dropped : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around)) {
				swords += dropped.getItem().is(sword.getItem()) ? 1 : 0;
				templates += dev.forja.item.TemplateItem.pattern(dropped.getItem()) == dev.forja.part.PartType.HOJA ? 1 : 0;
				dropped.discard();
			}
			helper.assertTrue(swords == 0, "el molde soltó " + swords + " espada(s) al morir");
			helper.assertTrue(templates >= 1, "y debería soltar la plantilla de la hoja, soltó " + templates);
			helper.succeed();
		});
	}

	/** {@code /forja fundicion} stands the whole foundry line up in front of whoever asks (command/FoundryDemo). */
	@GameTest
	public void foundryCommandStandsUpTheLine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A long way from every other test: the line clears a box twelve blocks across around itself.
		BlockPos feet = helper.absolutePos(new BlockPos(1, 1, 1)).offset(4096, 0, 0);
		var source = level.getServer().createCommandSourceStack().withLevel(level)
			.withPosition(Vec3.atBottomCenterOf(feet)).withRotation(new net.minecraft.world.phys.Vec2(0.0F, 0.0F))
			.withSuppressedOutput();
		int result;
		try {
			result = level.getServer().getCommands().getDispatcher().execute("forja fundicion", source);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			helper.fail("/forja fundicion no se pudo ejecutar: " + e.getMessage());
			return;
		}
		// Looking south (yaw 0), so the box starts past the feet on +Z.
		BlockPos origin = dev.forja.command.FoundryDemo.originFor(feet, net.minecraft.core.Direction.SOUTH);
		int tanks = 0;
		int full = 0;
		int tables = 0;
		int framed = 0;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(dev.forja.command.FoundryDemo.MIN_X, 0, dev.forja.command.FoundryDemo.MIN_Z),
			origin.offset(dev.forja.command.FoundryDemo.MAX_X, 5, dev.forja.command.FoundryDemo.MAX_Z))) {
			if (level.getBlockEntity(pos) instanceof dev.forja.block.entity.MeltTankBlockEntity tank
				&& level.getBlockState(pos).is(dev.forja.registry.ModBlocks.CUBA_DE_COLADA)) {
				tanks++;
				full += tank.amount() > 0 ? 1 : 0;
			}
			if (level.getBlockEntity(pos) instanceof dev.forja.block.entity.CastingTableBlockEntity table) {
				tables++;
				framed += table.frame().isEmpty() ? 0 : 1;
			}
		}
		helper.assertTrue(result == 1, "/forja fundicion devolvió " + result);
		helper.assertTrue(tanks == 4 && full == 4, "la línea debería tener 4 cubas con metal, tiene " + tanks + " (" + full + " con metal)");
		helper.assertTrue(tables == 2 && framed == 2, "y 2 mesas de colada con su marco, tiene " + tables + " (" + framed + " con marco)");
		helper.assertTrue(origin.getZ() + dev.forja.command.FoundryDemo.MIN_Z > feet.getZ(), "la línea debería quedar delante, no encima");
		helper.succeed();
	}

	/**
	 * Every batch runs on a clear morning that stands still (test_environment: minecraft:default and each of
	 * forja-test's lean on forja-test:estable). The test world is kept between runs, and its clock and weather with it:
	 * one run went at noon, the next at night or in the rain, and what the tests saw with it (undead burning or not,
	 * the light a torch gives, rain on a blaze) changed from run to run.
	 */
	@GameTest
	public void testWorldIsAClearMorningThatStandsStill(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getOverworldClockTime();
		helper.assertTrue(time % 24000L == 1000L, "la hora de las pruebas es la mañana (1000): " + time);
		helper.assertTrue(!level.isRaining() && !level.isThundering(), "y sin lluvia");
		helper.assertFalse(level.isDarkOutside(), "de día");
		helper.assertFalse(level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME), "la hora no avanza");
		helper.assertFalse(level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER), "ni el tiempo");
		helper.assertFalse(CombatConfig.get().iaRepartirObjetivos, "las escuadras no pasan monstruos al jugador de otra prueba (TestDefaults)");
		helper.succeed();
	}

	/**
	 * A stunned mob lets go of its turn (Andy, 2026-09-26). The network's goal dropped its warning on a
	 * stagger but kept the turn until its next blow, so in a group one stunned mob held the pack back.
	 */
	@GameTest(maxTicks = 40)
	public void staggerLetsGoOfTheTurn(GameTestHelper helper) {
		TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
		zombie.setNoAi(true);                      // it only has to hold the turn, not go looking for anybody
		zombie.setTarget(player);
		helper.assertTrue(dev.forja.combat.AttackTokens.tryAcquire(player, zombie, 2), "el zombi debería coger turno");
		helper.assertTrue(dev.forja.combat.AttackTokens.holds(player, zombie), "y tenerlo");
		long now = helper.getLevel().getGameTime();
		Posture.breakPosture(zombie, now);
		helper.assertTrue(Posture.isStaggered(zombie, now), "romperle la postura debería aturdirlo");
		helper.assertTrue(!dev.forja.combat.AttackTokens.holds(player, zombie), "aturdido, debería soltar el turno");
		helper.assertTrue(!dev.forja.combat.AttackTokens.tryAcquire(player, zombie, 2),
			"y no poder coger otro mientras dure");
		helper.succeed();
	}

	/** Against villagers, mobs fight as they always did. */
	@GameTest(maxTicks = 200)
	public void mobsStillHitVillagers(GameTestHelper helper) {
		noRandomThreat();
		Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
		// Standing still: a villager runs from a husk faster than the husk walks, and whether it is caught in
		// time was a race, not a question of how the husk fights.
		villager.setNoAi(true);
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(2, 1, 1));
		emptyHands(husk);
		husk.setTarget(villager);
		float start = villager.getHealth();
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(!villager.isAlive() || villager.getHealth() < start, "el husk no golpeó al aldeano (distancia " + husk.distanceTo(villager)
				+ ", ticks vividos " + husk.tickCount + ", trozo activo " + helper.getLevel().isPositionEntityTicking(husk.blockPosition()) + ")");
			helper.succeed();
		});
	}

	/**
	 * A zombie that comes at the player from the far end of the lunge's range (7 blocks) leaps at them before it is
	 * too close (3.5). It failed now and then (once in a full run on 2026-10-01; 15 of 400 side by side): it started
	 * five blocks off, and walking in it was in range for some 15 ticks, where the rules start the lunge with a chance of
	 * 0.3 on the ticks its goal is looked at (every other tick), and about one zombie in 25 walked through without it.
	 * From 7 blocks it was in range 34 ticks and 2 of 400 still walked through; slowed (Slowness III) it is in range
	 * nearly twice as long again, and 400 of 400 leapt. It is also the plain zombie the lunge is for: on the ground the
	 * player stands on, not dropped below the box onto the world's floor; with the chances of a veteran, an elite (who
	 * never lunges) or a shield at nothing, whatever another test left them at; and with its hands emptied, for a shield
	 * given as it came into the world (MobDefense.arm, from Scaling.sizeUp on ENTITY_LOAD) fights another way.
	 */
	@GameTest(maxTicks = 200)
	public void zombieLungesAtMidRange(GameTestHelper helper) {
		floor(helper);
		TestPlayer player = player(helper, new BlockPos(0, 1, 1));
		noRandomThreat();
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(7, 1, 1));
		emptyHands(zombie);
		zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOWNESS, 200, 2));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setTarget(player);
		// What it saw tick by tick, for the message: in range, on the ground, seeing, could start, the mind's target.
		int[] seen = new int[6];
		StringBuilder trace = new StringBuilder();
		helper.onEachTick(() -> {
			dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(zombie);
			double d = zombie.distanceTo(player);
			boolean range = d >= CombatConfig.get().lungeMinDistance && d <= CombatConfig.get().lungeMaxDistance;
			seen[0] += range ? 1 : 0;
			seen[1] += zombie.onGround() ? 1 : 0;
			seen[2] += zombie.getSensing().hasLineOfSight(player) ? 1 : 0;
			seen[3] += mind != null && mind.specials != null && mind.specials.available(0, player) ? 1 : 0;
			seen[4] += mind != null && mind.target == player ? 1 : 0;
			seen[5] += mind != null && mind.networked ? 1 : 0;
			if (helper.getTick() % 10 == 0) {
				trace.append(String.format(java.util.Locale.ROOT, " t%d d%.1f%s%s", helper.getTick(), d, zombie.onGround() ? "s" : "a",
					mind != null && mind.specials != null && mind.specials.active() ? "!" : ""));
			}
		});
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(CombatStats.count(zombie, CombatStats.LUNGE) > 0, "el zombi no embistió: a distancia " + seen[0] + ", en el suelo " + seen[1]
				+ ", lo ve " + seen[2] + ", podía empezar " + seen[3] + ", objetivo de la mente " + seen[4] + ", con red " + seen[5] + ";" + trace);
			helper.succeed();
		});
	}

	/**
	 * A skeleton's charged shot: a draw of its own length, a flash, and the arrow. What is tested is the shot, so
	 * this skeleton is kept from what made it come and go (53 runs of 400 failed): its specials, which under the
	 * rules start by chance on any tick and took over halfway through the 40-tick draw (a volley or a hop back
	 * instead); an elite by chance (the test never turned them off), which took its place in a formation and never
	 * drew; and the setting shared by every test (skeletonChargedEvery = 1), which charged the shots of the other
	 * tests' skeletons while this one ran. Its own bow goal is told that the next shot is the charged one.
	 */
	@GameTest(maxTicks = 200)
	public void skeletonFiresChargedShot(GameTestHelper helper) {
		floor(helper);
		TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		noRandomThreat();
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(6, 1, 6));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(skeleton);
		helper.assertTrue(mind != null, "el esqueleto debería tener cerebro");
		mind.specials = null;
		nextShotCharged(helper, skeleton);
		skeleton.setTarget(player);
		helper.runAfterDelay(110, () -> {
			helper.assertTrue(CombatStats.count(skeleton, CombatStats.CHARGED_SHOT) > 0, "el esqueleto no hizo un disparo cargado");
			helper.succeed();
		});
	}

	/**
	 * Sets this skeleton's bow goal to charge its next shot: the goal counts its shots and charges every
	 * skeletonChargedEvery-th (RangedBowAttackGoalMixin), so the count is put one short of it.
	 */
	private static void nextShotCharged(GameTestHelper helper, Skeleton skeleton) {
		int every = CombatConfig.get().skeletonChargedEvery;
		helper.assertTrue(every > 0, "los disparos cargados están apagados: skeletonChargedEvery " + every);
		for (var wrapped : ((dev.forja.mixin.MobGoalsAccess) skeleton).forjaGoals().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof net.minecraft.world.entity.ai.goal.RangedBowAttackGoal<?> goal) {
				for (java.lang.reflect.Field field : net.minecraft.world.entity.ai.goal.RangedBowAttackGoal.class.getDeclaredFields()) {
					if (field.getType() == int.class && field.getName().endsWith("shots")) {
						try {
							field.setAccessible(true);
							field.setInt(goal, every - 1);
							return;
						} catch (ReflectiveOperationException failure) {
							throw new IllegalStateException(failure);
						}
					}
				}
			}
		}
		helper.fail("no se encuentra la cuenta de disparos del arco del esqueleto (RangedBowAttackGoalMixin.forja$shots)");
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
