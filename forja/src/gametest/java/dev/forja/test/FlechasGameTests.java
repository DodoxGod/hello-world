package dev.forja.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import dev.forja.combat.ArrowTips;
import dev.forja.entity.ForgedArrow;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.magic.Mana;
import dev.forja.material.ForgeMaterial;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Forged arrows, each tip its own (Andy, 2026-09-30: "las flechas son todas iguales"): a heavy tip hits harder
 * and drops sooner, a light one flies flat, a hard one goes through armour, and the special tips do what their
 * tooltip says. See combat/ArrowTips.
 */
public class FlechasGameTests {
	/** Every material that can be an arrow's tip. */
	private static List<ForgeMaterial> tips() {
		List<ForgeMaterial> tips = new ArrayList<>();
		for (ForgeMaterial material : ForgeMaterial.values()) {
			if (material.canBeHead) {
				tips.add(material);
			}
		}
		return tips;
	}

	private static ItemStack arrow(GameTestHelper helper, ForgeMaterial tip) {
		return Assembler.create(ForgeType.FLECHA, List.of(tip, ForgeMaterial.CUERO), helper.getLevel().registryAccess());
	}

	/** Every tip is a different arrow: no two alike in weight, hardness and special, and each says so in its tooltip. */
	@GameTest(maxTicks = 20)
	public void everyTipIsItsOwnArrow(GameTestHelper helper) {
		java.util.Map<String, ForgeMaterial> seen = new java.util.HashMap<>();
		for (ForgeMaterial material : tips()) {
			ArrowTips.Tip tip = ArrowTips.of(material);
			String signature = String.format(java.util.Locale.ROOT, "%.3f|%.4f|%.3f|%s", dev.forja.forge.ForgeStats.arrowDamage(material) * tip.damage(),
				tip.gravity(), tip.penetration(), tip.special());
			dev.forja.Forja.LOGGER.info("punta | {} | {} | x{} | {} | {} % | {}", material.getSerializedName(),
				String.format(java.util.Locale.ROOT, "%.2f", dev.forja.forge.ForgeStats.arrowDamage(material) * tip.damage()),
				String.format(java.util.Locale.ROOT, "%.2f", tip.speed()), Math.round(tip.gravity() / ArrowTips.VANILLA_GRAVITY * 100) + " %",
				Math.round(tip.penetration() * 100), tip.special().id());
			ForgeMaterial twin = seen.put(signature, material);
			helper.assertTrue(twin == null, "la punta de " + material.getSerializedName() + " es igual que la de " + (twin == null ? "" : twin.getSerializedName()) + ": " + signature);
			helper.assertTrue(!ArrowTips.describe(material).isEmpty(), "la punta de " + material.getSerializedName() + " dice lo que hace");
			// Held against vanilla: no tip more than a fifth off a plain arrow's damage.
			helper.assertTrue(tip.damage() > 0.8F && tip.damage() < 1.2F, "daño de " + material.getSerializedName() + " dentro de ±20 %: " + tip.damage());
			if (material.trait != ForgeMaterial.Trait.NONE) {
				helper.assertTrue(tip.special() != ArrowTips.Special.NONE, material.getSerializedName() + " tiene rasgo y su punta hace algo");
			}
		}
		ArrowTips.Tip wood = ArrowTips.of(ForgeMaterial.MADERA);
		ArrowTips.Tip netherite = ArrowTips.of(ForgeMaterial.NETHERITA);
		ArrowTips.Tip diamond = ArrowTips.of(ForgeMaterial.DIAMANTE);
		helper.assertTrue(netherite.damage() > wood.damage() && netherite.gravity() > wood.gravity() && netherite.speed() < wood.speed(),
			"la netherita pesa: pega más, cae antes y sale más lenta que la madera");
		helper.assertTrue(netherite.heavy() && !wood.heavy(), "la netherita empuja, la madera no");
		helper.assertTrue(diamond.penetration() > wood.penetration() + 0.1F, "el diamante atraviesa más armadura que la madera");
		helper.assertTrue(ArrowTips.of(ForgeMaterial.HUECO).penetration() >= ArrowTips.HOLLOW_PENETRATION, "la hueca encuentra el hueco");
		helper.assertTrue(ArrowTips.of(ForgeMaterial.ESTELAR).gravity() < wood.gravity() * 0.5, "la estelar casi no cae");
		helper.succeed();
	}

	/** The tip is on the arrow in flight: its colour for the client, its gravity, its piercing, its way through water. */
	@GameTest(maxTicks = 20)
	public void theArrowCarriesItsTip(GameTestHelper helper) {
		CombatGameTests.TestPlayer archer = CombatGameTests.player(helper, new BlockPos(1, 1, 1));
		ItemStack bow = new ItemStack(Items.BOW);
		ForgedArrow echo = new ForgedArrow(helper.getLevel(), archer, arrow(helper, ForgeMaterial.ECO), bow);
		helper.assertTrue(echo.tipMaterial() == ForgeMaterial.ECO, "la flecha sabe de qué es su punta");
		helper.assertTrue(echo.getPierceLevel() >= 1, "la de eco atraviesa a uno más: " + echo.getPierceLevel());
		ForgedArrow heavy = new ForgedArrow(helper.getLevel(), archer, arrow(helper, ForgeMaterial.NETHERITA), bow);
		ForgedArrow light = new ForgedArrow(helper.getLevel(), archer, arrow(helper, ForgeMaterial.MADERA), bow);
		helper.assertTrue(heavy.baseDamage() > light.baseDamage(), "la pesada pega más: " + heavy.baseDamage() + " contra " + light.baseDamage());
		helper.assertTrue(heavy.speedFactor() < light.speedFactor(), "y sale más lenta");
		helper.assertTrue(heavy.tipPenetration() > light.tipPenetration(), "y atraviesa más armadura");
		helper.succeed();
	}

	/** Fires a forged arrow of this tip at a frozen husk four blocks off, and checks what it did once it has landed. */
	private static void shootAt(GameTestHelper helper, ForgeMaterial tip, CombatGameTests.TestPlayer archer, Husk husk, Predicate<Husk> did, String what) {
		ForgedArrow arrow = new ForgedArrow(helper.getLevel(), archer, arrow(helper, tip), new ItemStack(Items.BOW));
		Vec3 from = archer.getEyePosition();
		Vec3 to = husk.getBoundingBox().getCenter();
		arrow.setPos(from.x, from.y, from.z);
		Vec3 way = to.subtract(from);
		arrow.shoot(way.x, way.y + way.horizontalDistance() * 0.02, way.z, 2.5F, 0.0F);
		helper.getLevel().addFreshEntity(arrow);
		helper.succeedWhen(() -> helper.assertTrue(did.test(husk), "la punta de " + tip.getSerializedName() + " " + what));
	}

	private static Husk husk(GameTestHelper helper, int x, int z) {
		Husk husk = helper.spawn(EntityTypes.HUSK, new BlockPos(x, 1, z));
		husk.setNoAi(true);
		husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		husk.setHealth(100.0F);
		return husk;
	}

	@GameTest(maxTicks = 60)
	public void slagTipSetsOnFire(GameTestHelper helper) {
		shootAt(helper, ForgeMaterial.ESCORIA, CombatGameTests.player(helper, new BlockPos(1, 1, 3)), husk(helper, 5, 3), Husk::isOnFire, "prende");
	}

	@GameTest(maxTicks = 60)
	public void resinTipSlows(GameTestHelper helper) {
		shootAt(helper, ForgeMaterial.RESINA, CombatGameTests.player(helper, new BlockPos(1, 1, 3)), husk(helper, 5, 3),
			h -> h.hasEffect(MobEffects.SLOWNESS), "pega (Lentitud)");
	}

	@GameTest(maxTicks = 60)
	public void quartzTipBleeds(GameTestHelper helper) {
		shootAt(helper, ForgeMaterial.CUARZO, CombatGameTests.player(helper, new BlockPos(1, 1, 3)), husk(helper, 5, 3),
			h -> h.hasEffect(dev.forja.registry.ModEffects.SANGRADO), "hace sangrar");
	}

	@GameTest(maxTicks = 60)
	public void cryingObsidianTipWeakens(GameTestHelper helper) {
		shootAt(helper, ForgeMaterial.OBSIDIANA_LLORONA, CombatGameTests.player(helper, new BlockPos(1, 1, 3)), husk(helper, 5, 3),
			h -> h.hasEffect(MobEffects.WEAKNESS), "debilita");
	}

	@GameTest(maxTicks = 60)
	public void goldTipMarks(GameTestHelper helper) {
		shootAt(helper, ForgeMaterial.ORO, CombatGameTests.player(helper, new BlockPos(1, 1, 3)), husk(helper, 5, 3),
			h -> h.hasEffect(MobEffects.GLOWING), "marca (brilla)");
	}

	@GameTest(maxTicks = 60)
	public void amethystTipGivesMana(GameTestHelper helper) {
		CombatGameTests.TestPlayer archer = CombatGameTests.player(helper, new BlockPos(1, 1, 3));
		Mana.forget(archer);
		Mana.set(archer, 50.0F);
		shootAt(helper, ForgeMaterial.AMATISTA, archer, husk(helper, 5, 3), h -> Mana.value(archer) >= 50.0F + ArrowTips.SPELL_MANA - 0.01F, "devuelve maná");
	}

	@GameTest(maxTicks = 60)
	public void livingSteelTipHealsTheArcher(GameTestHelper helper) {
		CombatGameTests.TestPlayer archer = CombatGameTests.player(helper, new BlockPos(1, 1, 3));
		archer.setHealth(10.0F);
		shootAt(helper, ForgeMaterial.ACERO_VIVO, archer, husk(helper, 5, 3), h -> archer.getHealth() >= 10.0F + ArrowTips.LIVING_HEAL - 0.01F, "cura al arquero");
	}

	@GameTest(maxTicks = 60)
	public void purpurTipBlinksTheTarget(GameTestHelper helper) {
		Husk target = husk(helper, 5, 3);
		Vec3 start = target.position();
		shootAt(helper, ForgeMaterial.PURPUR, CombatGameTests.player(helper, new BlockPos(1, 1, 3)), target,
			h -> h.getHealth() < 100.0F && h.position().distanceTo(start) > 1.5, "hace saltar al blanco");
	}

	@GameTest(maxTicks = 60)
	public void voltaicTipSparksToTheNext(GameTestHelper helper) {
		Husk next = husk(helper, 6, 5);
		shootAt(helper, ForgeMaterial.VOLTAICO, CombatGameTests.player(helper, new BlockPos(1, 1, 3)), husk(helper, 5, 3),
			h -> h.getHealth() < 100.0F && next.getHealth() < 100.0F, "suelta una chispa al de al lado");
	}

	@GameTest(maxTicks = 60)
	public void glassTipShatters(GameTestHelper helper) {
		CombatGameTests.TestPlayer archer = CombatGameTests.player(helper, new BlockPos(1, 1, 3));
		shootAt(helper, ForgeMaterial.VIDRIACERO, archer, husk(helper, 5, 3),
			h -> h.getHealth() < 100.0F && helper.getLevel().getEntitiesOfClass(ForgedArrow.class, helper.getBounds().inflate(8.0)).isEmpty(),
			"se rompe al acertar");
	}

	/** Almacero: loosed well wide of a husk, it bends onto it anyway. */
	@GameTest(maxTicks = 80)
	public void soulSteelTipSeeks(GameTestHelper helper) {
		CombatGameTests.TestPlayer archer = CombatGameTests.player(helper, new BlockPos(1, 1, 3));
		Husk target = husk(helper, 7, 3);
		ForgedArrow arrow = new ForgedArrow(helper.getLevel(), archer, arrow(helper, ForgeMaterial.ALMACERO), new ItemStack(Items.BOW));
		Vec3 from = archer.getEyePosition();
		Vec3 way = target.getBoundingBox().getCenter().subtract(from).yRot((float) Math.toRadians(10.0));
		arrow.setPos(from.x, from.y, from.z);
		arrow.shoot(way.x, way.y + 0.15, way.z, 1.0F, 0.0F);
		helper.getLevel().addFreshEntity(arrow);
		helper.succeedWhen(() -> helper.assertTrue(target.getHealth() < 100.0F, "la de almacero se tuerce y acierta"));
	}

	/** The conditional ones: ember on what burns, sun in the light, moon in the dark, nothing otherwise. */
	@GameTest(maxTicks = 20)
	public void conditionalTipsNeedTheirMoment(GameTestHelper helper) {
		helper.assertTrue(ArrowTips.conditionalBonus(ArrowTips.Special.BRASA, true, false, false) > 0.0F
			&& ArrowTips.conditionalBonus(ArrowTips.Special.BRASA, false, true, true) == 0.0F, "la brasa, sólo en lo que arde");
		helper.assertTrue(ArrowTips.conditionalBonus(ArrowTips.Special.SOL, false, true, false) > 0.0F
			&& ArrowTips.conditionalBonus(ArrowTips.Special.SOL, true, false, true) == 0.0F, "el sol, sólo a pleno sol");
		helper.assertTrue(ArrowTips.conditionalBonus(ArrowTips.Special.LUNA, false, false, true) > 0.0F
			&& ArrowTips.conditionalBonus(ArrowTips.Special.LUNA, true, true, false) == 0.0F, "la luna, sólo a oscuras");
		helper.assertTrue(ArrowTips.conditionalBonus(ArrowTips.Special.NONE, true, true, true) == 0.0F, "y una punta llana, nunca");
		ForgedArrow tide = new ForgedArrow(helper.getLevel(), CombatGameTests.player(helper, new BlockPos(1, 1, 1)), arrow(helper, ForgeMaterial.PRISMARINA),
			new ItemStack(Items.BOW));
		helper.assertTrue(tide.special() == ArrowTips.Special.MAREA, "la de prismarina es de marea");
		helper.succeed();
	}
}
