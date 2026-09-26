package dev.forja.test;

import dev.forja.ai.MobFamily;
import dev.forja.ai.ObsM1;
import dev.forja.ai.ObsV3;
import dev.forja.ai.Squad;
import dev.forja.combat.CombatStats;
import dev.forja.entity.ai.CasterGoal;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.world.ForjaMobs;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The monsters with the mod's own weapons, and the fixes asked for by the simulator's side (2026-09-26):
 * forged bows that shoot, staffs and tomes that cast, the family by the weapon, the spear's warning, the
 * wider ring, and the first block of red_mob_v3's observation.
 */
public class ArsenalGameTests {
	private static ItemStack forged(ForgeType type) {
		return Assembler.create(type, Assembler.defaultMaterials(type));
	}

	/** What a mob holds decides its family: a skeleton with a sword is a body, with our bow an archer. */
	@GameTest
	public void familyFollowsTheWeapon(GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 1));
		skeleton.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.assertTrue(MobFamily.of(skeleton) == MobFamily.CUERPO, "con espada es cuerpo: " + MobFamily.of(skeleton));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, forged(ForgeType.ARCO));
		helper.assertTrue(MobFamily.of(skeleton) == MobFamily.ARQUERO, "con el arco forjado es arquero");
		helper.assertTrue(ObsM1.trident(forged(ForgeType.TRIDENTE)), "un tridente forjado es un tridente para la red");
		helper.assertTrue(ObsM1.trident(new ItemStack(Items.TRIDENT)), "y el vanilla también");
		helper.assertTrue(ForjaMobs.forgedLauncher(forged(ForgeType.ARCO), Items.BOW), "nuestro arco hace de arco");
		helper.assertTrue(ForjaMobs.forgedLauncher(forged(ForgeType.BALLESTA), Items.CROSSBOW), "nuestra ballesta hace de ballesta");
		helper.succeed();
	}

	/** The ring widens with a crowd so that neighbours stand at least MIN_GAP apart. */
	@GameTest
	public void ringWidensForACrowd(GameTestHelper helper) {
		helper.assertTrue(Squad.ringRadius(4) == dev.forja.ai.TacticGoal.RING_RADIUS, "cuatro caben en 3.5");
		for (int n = 2; n <= 16; n++) {
			double radius = Squad.ringRadius(n);
			double gap = 2.0 * radius * Math.sin(Math.PI / n);
			helper.assertTrue(gap >= Squad.MIN_GAP - 1.0E-9 || radius == dev.forja.ai.TacticGoal.RING_RADIUS && n < 3,
				"con " + n + " quedan a " + gap);
		}
		helper.assertTrue(Squad.ringRadius(8) > dev.forja.ai.TacticGoal.RING_RADIUS, "ocho ya no caben en 3.5");
		helper.succeed();
	}

	/** A skeleton with a forged bow shoots it (it used to walk up and club you with it). */
	@GameTest(maxTicks = 200)
	public void skeletonShootsAForgedBow(GameTestHelper helper) {
		CombatGameTests.noRandomThreat();
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(7, 1, 1));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, forged(ForgeType.ARCO));
		skeleton.setTarget(player);
		helper.runAfterDelay(160, () -> {
			boolean shot = !helper.getLevel().getEntitiesOfClass(AbstractArrow.class, skeleton.getBoundingBox().inflate(24.0),
				arrow -> arrow.getOwner() == skeleton).isEmpty();
			helper.assertTrue(shot || player.getHealth() < player.getMaxHealth(), "el esqueleto no disparó su arco forjado (a "
				+ skeleton.distanceTo(player) + ", usando " + skeleton.isUsingItem() + ")");
			helper.succeed();
		});
	}

	/** A skeleton with a staff aims and throws its bolt; a zombie with a tome warns on the floor and then opens it. */
	@GameTest(maxTicks = 240)
	public void castersCast(GameTestHelper helper) {
		CombatGameTests.noRandomThreat();
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 4));
		Skeleton mage = helper.spawn(EntityTypes.SKELETON, new BlockPos(7, 1, 1));
		mage.setItemSlot(EquipmentSlot.MAINHAND, ForjaMobs.magic(ForgeType.BACULO, mage, RandomSource.create(1)));
		mage.setTarget(player);
		Zombie reader = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(6, 1, 7));
		reader.setItemSlot(EquipmentSlot.MAINHAND, ForjaMobs.magic(ForgeType.GRIMORIO, reader, RandomSource.create(2)));
		reader.setTarget(player);
		helper.assertTrue(CasterGoal.casts(mage) && CasterGoal.casts(reader), "los dos deberían contar como lanzadores");
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(CombatStats.count(mage, CasterGoal.BOLT) > 0, "el del báculo no lanzó (a " + mage.distanceTo(player) + ")");
			helper.assertTrue(CombatStats.count(reader, CasterGoal.TOME_WARNED) > 0, "el del grimorio no avisó (a " + reader.distanceTo(player) + ")");
			helper.succeed();
		});
	}

	/** A zombie with a spear warns before it charges, like every other blow. */
	@GameTest(maxTicks = 240)
	public void spearChargeIsWarned(GameTestHelper helper) {
		CombatGameTests.noRandomThreat();
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(7, 1, 7));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, forged(ForgeType.LANZA));
		zombie.setTarget(player);
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(CombatStats.count(zombie, "spear_warned") > 0, "el zombi con lanza no avisó su carga (a "
				+ zombie.distanceTo(player) + ")");
			helper.succeed();
		});
	}

	/** red_mob_v3, P: an arrow flying at a mob is seen coming; one flying away is not. */
	@GameTest
	public void incomingArrowIsSeen(GameTestHelper helper) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		zombie.setPos(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(zombie);
		zombie.setNoAi(true);
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 4));
		int present = ObsV3.names().indexOf("proy0_presente");
		int owner = ObsV3.names().indexOf("proy0_dueno_jugador");
		int arrowKind = ObsV3.names().indexOf("proy0_tipo_flecha");
		helper.assertTrue(ObsV3.of(zombie, player)[present] == 0.0, "sin flechas, nada llega");
		Arrow arrow = new Arrow(EntityTypes.ARROW, helper.getLevel());
		arrow.setOwner(player);
		arrow.setPos(at.x - 6.0, at.y + 1.0, at.z);
		arrow.setDeltaMovement(1.5, 0.05, 0.0);
		arrow.setNoGravity(false);
		helper.getLevel().addFreshEntity(arrow);
		double[] seen = ObsV3.of(zombie, player);
		helper.assertTrue(seen[present] == 1.0, "la flecha que va hacia él debería verse");
		helper.assertTrue(seen[owner] == 1.0 && seen[arrowKind] == 1.0, "de un jugador, y flecha");
		arrow.setDeltaMovement(-1.5, 0.05, 0.0);
		helper.assertTrue(ObsV3.of(zombie, player)[present] == 0.0, "la que se aleja no");
		arrow.discard();
		zombie.discard();
		helper.succeed();
	}
}
