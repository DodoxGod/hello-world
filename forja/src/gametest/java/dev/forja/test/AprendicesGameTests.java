package dev.forja.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.forja.combat.CombatConfig;
import dev.forja.entity.FallenSmith;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Potential;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEntities;
import dev.forja.upgrade.Upgrades;
import dev.forja.world.ApprenticeKits;
import dev.forja.world.Apprentices;
import dev.forja.world.Elites;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Fallen Smith's apprentices are end-game gear (Andy, 2026-09-30: no armour, always the same weapons,
 * sometimes wooden ones): fully armoured in endgame alloys, a different weapon each, a few upgrades, archers
 * that shoot, and nothing that can strip a geared player in one blow.
 */
public class AprendicesGameTests {
	private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Every part of a forged piece is made of an endgame alloy: no wood, no stone, no iron, no leather. */
	private static void assertEndgame(GameTestHelper helper, ItemStack stack, String what) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		helper.assertTrue(parts != null, what + " deberia ser una pieza forjada: " + stack);
		for (ForgeMaterial material : parts.materials()) {
			helper.assertTrue(ApprenticeKits.ENDGAME.contains(material), what + " lleva " + material + ", que no es de final de juego");
		}
	}

	private static void assertKit(GameTestHelper helper, Mob apprentice, boolean live) {
		for (EquipmentSlot slot : WORN) {
			ItemStack piece = apprentice.getItemBySlot(slot);
			helper.assertFalse(piece.isEmpty(), "falta la pieza de " + slot);
			ForgedParts parts = piece.get(ModComponents.PARTS);
			helper.assertTrue(parts != null && parts.type().kind == ForgeType.Kind.ARMOR, slot + " deberia ser armadura forjada: " + piece);
			assertEndgame(helper, piece, slot.getName());
		}
		assertEndgame(helper, apprentice.getMainHandItem(), "su arma");
		if (!apprentice.getOffhandItem().isEmpty()) {
			helper.assertTrue(apprentice.getOffhandItem().get(ModComponents.PARTS).type() == ForgeType.ESCUDO, "en la otra mano, solo escudo");
			assertEndgame(helper, apprentice.getOffhandItem(), "su escudo");
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			helper.assertTrue(apprentice.getDropChances().byEquipment(slot) <= ApprenticeKits.PIECE_DROP_CHANCE, "no suelta nada: " + slot);
		}
		helper.assertTrue(!live || apprentice.getArmorValue() >= 15, "con esa armadura, al menos 15 puntos (un elite sin placa tenia 8): " + apprentice.getArmorValue());
	}

	/** A real wave of the real fight: four apprentices, all armoured to the teeth, and none of them with wood or stone. */
	@GameTest(maxTicks = 80)
	public void apprenticesComeUpFullyArmoured(GameTestHelper helper) {
		FallenSmith smith = helper.spawn(ModEntities.HERRERO_CAIDO, new BlockPos(4, 1, 4));
		smith.setNoAi(true);
		smith.setHealth(smith.getMaxHealth() * 0.65F);
		helper.runAfterDelay(8, () -> {
			List<Mob> risen = helper.getLevel().getEntitiesOfClass(Mob.class, smith.getBoundingBox().inflate(4.5),
				mob -> mob.isAlive() && Apprentices.isApprentice(mob));
			helper.assertTrue(risen.size() == 4, "cuatro aprendices, hay " + risen.size());
			Set<ForgeType> weapons = new HashSet<>();
			for (Mob apprentice : risen) {
				assertKit(helper, apprentice, true);
				weapons.add(apprentice.getMainHandItem().get(ModComponents.PARTS).type());
			}
			helper.assertTrue(weapons.size() == 4, "cuatro aprendices, cuatro armas distintas: " + weapons);
			risen.forEach(Mob::discard);
			smith.discard();
			helper.succeed();
		});
	}

	/** A wave of thirteen: nine different melee weapons, archers among them, every set of plate told apart from its neighbours. */
	@GameTest
	public void weaponsVaryAcrossAWave(GameTestHelper helper) {
		RandomSource random = RandomSource.create(7);
		int count = 13;
		List<ApprenticeKits.Role> roles = ApprenticeKits.wave(count, random);
		helper.assertTrue(roles.size() == count, "un rol por aprendiz");
		Set<ForgeType> melee = new HashSet<>();
		int archers = 0;
		int shields = 0;
		List<String> signatures = new ArrayList<>();
		int salt = 11;
		for (int i = 0; i < count; i++) {
			ApprenticeKits.Role role = roles.get(i);
			Mob mob = role.body().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
			Elites.makeElite(mob, random);
			ApprenticeKits.equip(mob, role, i, salt, random);
			assertKit(helper, mob, false);
			ForgeType weapon = mob.getMainHandItem().get(ModComponents.PARTS).type();
			if (role.ranged()) {
				archers++;
				helper.assertTrue(weapon == ForgeType.ARCO, "el arquero lleva arco: " + weapon);
				helper.assertTrue(mob.getType() == net.minecraft.world.entity.EntityTypes.SKELETON, "un arquero sale con cuerpo de esqueleto");
			} else {
				helper.assertTrue(melee.add(weapon), "arma de cuerpo a cuerpo repetida: " + weapon);
				helper.assertTrue(mob.getType() == net.minecraft.world.entity.EntityTypes.WITHER_SKELETON, "el de cuerpo a cuerpo es esqueleto atrofiado");
			}
			if (!mob.getOffhandItem().isEmpty()) {
				shields++;
				helper.assertFalse(weapon == ForgeType.ESPADON || weapon == ForgeType.GUADANA || weapon == ForgeType.MANGUAL || weapon == ForgeType.MARTILLO || weapon == ForgeType.ARCO,
					"el arma a dos manos no lleva escudo: " + weapon);
			}
			// Upgrades on the weapon, within what the piece holds and under what a smith reaches without flux.
			Upgrades upgrades = mob.getMainHandItem().getOrDefault(ModComponents.UPGRADES, Upgrades.EMPTY);
			helper.assertTrue(!upgrades.percents().isEmpty(), "su arma lleva mejoras: " + weapon);
			upgrades.percents().forEach((upgrade, percent) -> helper.assertTrue(percent <= ApprenticeKits.UPGRADE_MAX && percent >= 1,
				upgrade + " al " + percent + " %, pasa de lo que se alcanza sin fundente"));
			helper.assertTrue(Potential.load(mob.getMainHandItem()) <= Potential.capacity(mob.getMainHandItem()),
				"las mejoras caben en el arma: " + Potential.load(mob.getMainHandItem()) + " de " + Potential.capacity(mob.getMainHandItem()));
			signatures.add(mob.getItemBySlot(EquipmentSlot.CHEST).get(ModComponents.PARTS).materials() + "/"
				+ mob.getMainHandItem().get(ModComponents.PARTS).materials().get(0));
		}
		helper.assertTrue(melee.size() == 9, "nueve armas distintas, " + melee);
		helper.assertTrue(archers >= 3 && archers < count / 2, "de trece, unos cuantos arqueros: " + archers);
		helper.assertTrue(shields >= 3, "algunos llevan escudo: " + shields);
		for (int i = 1; i < signatures.size(); i++) {
			helper.assertFalse(signatures.get(i).equals(signatures.get(i - 1)), "dos vecinos iguales: " + signatures.get(i));
		}
		helper.assertTrue(new HashSet<>(signatures).size() >= 8, "mucha variedad de aleaciones: " + new HashSet<>(signatures).size());
		helper.succeed();
	}

	/** An archer apprentice draws its forged bow and looses arrows at a player. */
	@GameTest(maxTicks = 240)
	public void rangedApprenticesShoot(GameTestHelper helper) {
		CombatGameTests.noRandomThreat();
		// The archer stands at x 8, past the test's 8 x 8 box: where the box is the last of its row (or the last
		// column, as the test is turned) that chunk is nobody's, and an entity there never ticks. It stood where it
		// was spawned for the whole test, 7.0 from the player, without ever seeing them, now and then (6 in 400
		// with FORJA_VERIFICAR, and once in a whole run). Its chunks are forced, as RedV4ModGameTests.room does.
		TestChunks.force(helper, 10);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ApprenticeKits.Role role = ApprenticeKits.ARCHER;
		Mob archer = helper.spawn(role.body(), new BlockPos(8, 1, 1));
		Elites.makeElite(archer, RandomSource.create(3));
		ApprenticeKits.equip(archer, role, 2, 5, RandomSource.create(3));
		Apprentices.enlist(archer);
		helper.assertTrue(archer.getMainHandItem().get(ModComponents.PARTS).type() == ForgeType.ARCO, "lleva un arco forjado");
		archer.setTarget(player);
		boolean[] shot = {false};
		helper.onEachTick(() -> shot[0] |= !helper.getLevel().getEntitiesOfClass(AbstractArrow.class, archer.getBoundingBox().inflate(30.0),
			arrow -> arrow.getOwner() == archer).isEmpty());
		helper.runAfterDelay(220, () -> {
			dev.forja.ai.MobMind mind = dev.forja.ai.MobAi.mind(archer);
			helper.assertTrue(shot[0] || player.getHealth() < player.getMaxHealth(), "el arquero no disparo (a " + archer.distanceTo(player)
				+ ", usando " + archer.isUsingItem() + ", objetivo " + (archer.getTarget() == player ? "el jugador" : String.valueOf(archer.getTarget()))
				+ ", nivel " + dev.forja.difficulty.Ladder.current() + ", decision " + (mind == null ? null : mind.decision) + ", red " + (mind != null && mind.networked)
				+ ", turno " + dev.forja.combat.AttackTokens.holds(player, archer) + ", tipo " + archer.getType() + ", vista " + archer.hasLineOfSight(player)
				+ ", noAi " + archer.isNoAi() + ", vivo " + archer.isAlive() + ")");
			archer.discard();
			TestChunks.release(helper);
			helper.succeed();
		});
	}

	/**
	 * Balance: against a player in full netherite none of them takes a third of the health in one blow, and the
	 * armour one blow can strip stays at the base 30 % however good the weapon.
	 */
	@GameTest
	public void noApprenticeOneShotsAGearedPlayer(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		helper.assertTrue(dev.forja.difficulty.Pressure.total(1.0, cfg.penetrationChampion, 0.0, cfg) <= 0.30 + 1.0E-9,
			"un golpe nunca quita mas del 30 % de la armadura");
		RandomSource random = RandomSource.create(9);
		List<String> report = new ArrayList<>();
		float worst = 0.0F;
		int i = 0;
		for (ApprenticeKits.Role role : ApprenticeKits.MELEE) {
			CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
			Mob mob = helper.spawn(role.body(), new BlockPos(2, 1, 1));
			mob.setNoAi(true);
			Elites.makeElite(mob, random);
			ApprenticeKits.equip(mob, role, i++, 3, random);
			float before = player.getHealth();
			player.invulnerableTime = 0;
			mob.doHurtTarget(helper.getLevel(), player);
			float lost = before - player.getHealth();
			report.add(role.id() + "=" + lost);
			worst = Math.max(worst, lost);
			mob.discard();
		}
		System.out.println("[aprendices] dano por golpe a un jugador de netherita: " + report);
		helper.assertTrue(worst < 20.0F / 3.0F, "un golpe quita demasiado a un jugador con netherita: " + report);
		helper.succeed();
	}
}
