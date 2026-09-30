package dev.forja.test;

import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.magic.Spellcasting;
import dev.forja.upgrade.Synergy;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.UpgradeRecipes;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The magic weapons, reined in (Andy, 2026-09-30): "Enjambre" killed a warden in seconds because every bolt of
 * the fan carried the whole spell. Now the fan shares one spell out among its bolts, and a reader has one rune
 * on the floor at a time. The balance of magic against melee is in BalanceGameTests.magiaEnSuSitio.
 */
public class MagiaGameTests {
	private static ItemStack staff(GameTestHelper helper, Object... upgrades) {
		var registries = helper.getLevel().registryAccess();
		ItemStack made = Assembler.create(ForgeType.BACULO, Assembler.defaultMaterials(ForgeType.BACULO), registries);
		for (int i = 0; i < upgrades.length; i += 2) {
			made = UpgradeRecipes.upgraded(made, ForgeType.BACULO, (Upgrade) upgrades[i], (Integer) upgrades[i + 1], registries);
		}
		return made;
	}

	/** The damage every bolt now in the air carries, added up. */
	private static float carried(GameTestHelper helper) {
		float sum = 0.0F;
		for (dev.forja.entity.MagicBolt bolt : helper.getLevel().getEntitiesOfClass(dev.forja.entity.MagicBolt.class, helper.getBounds().inflate(32.0))) {
			try {
				java.lang.reflect.Field field = dev.forja.entity.MagicBolt.class.getDeclaredField("damage");
				field.setAccessible(true);
				sum += field.getFloat(bolt);
			} catch (ReflectiveOperationException failure) {
				throw new IllegalStateException("MagicBolt.damage", failure);
			}
		}
		return sum;
	}

	private static int bolts(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(dev.forja.entity.MagicBolt.class, helper.getBounds().inflate(32.0)).size();
	}

	/**
	 * A staff with Enjambre (Prisma and Buscador) throws five bolts that all hunt the one zombie in front of it,
	 * and all five together take off what one bolt of the same staff without them does: the spell is shared out.
	 */
	@GameTest(maxTicks = 120)
	public void swarmSharesOutTheSpell(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 3));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(5, 1, 3));
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(200.0F);
		ItemStack plain = staff(helper);
		ItemStack swarm = staff(helper, Upgrade.PRISMA, 100, Upgrade.BUSCADOR, 100);
		helper.assertTrue(Synergy.ENJAMBRE.active(swarm), "Prisma y Buscador al 100 despiertan Enjambre");
		Vec3 torso = zombie.position().add(0.0, zombie.getBbHeight() * 0.6, 0.0);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, torso);
		Spellcasting.cast(helper.getLevel(), player, plain, ForgeType.BACULO, null);
		helper.assertTrue(bolts(helper) == 1, "sin Prisma sale un proyectil, vi " + bolts(helper));
		float single = carried(helper);
		float[] lost = new float[2];
		helper.runAfterDelay(30, () -> {
			lost[0] = 200.0F - zombie.getHealth();
			zombie.setHealth(200.0F);
			zombie.invulnerableTime = 0;
			player.lookAt(EntityAnchorArgument.Anchor.EYES, torso);
			Spellcasting.cast(helper.getLevel(), player, swarm, ForgeType.BACULO, null);
			helper.assertTrue(bolts(helper) == 5, "con Enjambre salen cinco, vi " + bolts(helper));
			// What the five carry, all told, is what the one bolt carried: the spell is shared out, not copied.
			float shared = carried(helper);
			helper.assertTrue(Math.abs(shared - single) < 1.0E-3F * single + 1.0E-3F,
				"los cinco de Enjambre llevan entre todos el daño de uno: " + shared + " contra " + single);
		});
		helper.runAfterDelay(70, () -> {
			lost[1] = 200.0F - zombie.getHealth();
			helper.assertTrue(lost[0] > 1.0F, "un proyectil solo hace daño: " + lost[0]);
			// On the zombie: the five together take off about what the one did. Not exactly, as a bolt that
			// happens to find the head does a little more (CombatConfig.headMultiplier), but never five times.
			helper.assertTrue(lost[1] >= lost[0] * 0.9F && lost[1] <= lost[0] * 1.35F,
				"en el zombi, los cinco juntos quitan lo que uno solo: " + lost[1] + " contra " + lost[0]);
			helper.succeed();
		});
	}

	/** A player's new rune puts out the last one they laid: one reader, one rune on the floor. */
	@GameTest(maxTicks = 40)
	public void oneRunePerReader(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		var registries = helper.getLevel().registryAccess();
		ItemStack tome = Assembler.create(ForgeType.GRIMORIO, Assembler.defaultMaterials(ForgeType.GRIMORIO), registries);
		Spellcasting.cast(helper.getLevel(), player, tome, ForgeType.GRIMORIO, helper.absoluteVec(new Vec3(3.5, 1.0, 3.5)));
		helper.assertTrue(Spellcasting.runesOf(player.getUUID()) == 1, "una runa en el suelo");
		Spellcasting.cast(helper.getLevel(), player, tome, ForgeType.GRIMORIO, helper.absoluteVec(new Vec3(5.5, 1.0, 5.5)));
		helper.assertTrue(Spellcasting.runesOf(player.getUUID()) == 1, "la segunda apaga la primera: " + Spellcasting.runesOf(player.getUUID()));
		helper.succeed();
	}

	/** Monsters keep their old numbers: their bolt and area are what they always were, and so are their waits. */
	@GameTest(maxTicks = 20)
	public void monstersKeepTheirMagic(GameTestHelper helper) {
		var core = dev.forja.material.ForgeMaterial.AMATISTA;
		helper.assertTrue(Spellcasting.monsterBoltDamage(core) > Spellcasting.boltDamage(core), "el proyectil de un monstruo no baja");
		helper.assertTrue(Math.abs(Spellcasting.monsterBoltDamage(core) - (4.0F + core.attackDamageBonus * 0.9F)) < 1.0E-4F, "es el de antes");
		helper.assertTrue(Spellcasting.MONSTER_BOLT_COOLDOWN == 14 && Spellcasting.MONSTER_TOME_COOLDOWN == 70, "y espera lo de antes");
		helper.succeed();
	}
}
