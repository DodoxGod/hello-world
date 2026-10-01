package dev.forja.test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import dev.forja.combat.ChargedStrike;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.KillFlow;
import dev.forja.combat.Stamina;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.PlayerClass;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Potential;
import dev.forja.forge.SpecialAttacks;
import dev.forja.magic.Mana;
import dev.forja.magic.Spellcasting;
import dev.forja.material.ForgeMaterial;
import dev.forja.upgrade.CombatUpgrades;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.UpgradeRecipes;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;

/**
 * La barra de maná y lo que la rodea (Andy, 2026-09-28): "¿podrías hacer una barra de maná? y mejoras que
 * tengan que ver con el maná", las armas mágicas con "maná y un enfriamiento corto", "al matar recuperas un
 * máximo de 3 % de maná por cada 5 ticks", y luego "matar también debe regenerar stamina, con el mismo
 * límite, y también deben existir mejoras que te den más stamina, distancia de lunge/esquive".
 */
public class ManaGameTests {
	private static final float EPS = 0.01F;

	private static CombatGameTests.TestPlayer player(GameTestHelper helper, BlockPos at) {
		CombatGameTests.TestPlayer player = CombatGameTests.player(helper, at);
		Mana.forget(player);
		return player;
	}

	private static ItemStack forged(GameTestHelper helper, ForgeType type, Object... upgrades) {
		var registries = helper.getLevel().registryAccess();
		ItemStack made = Assembler.create(type, Assembler.defaultMaterials(type), registries);
		for (int i = 0; i < upgrades.length; i += 2) {
			made = UpgradeRecipes.upgraded(made, type, (Upgrade) upgrades[i], (Integer) upgrades[i + 1], registries);
		}
		return made;
	}

	private static ItemStack staff(GameTestHelper helper, Object... upgrades) {
		return forged(helper, ForgeType.BACULO, upgrades);
	}

	/** Casts a tap with whatever is in the main hand, the way a click and a release at once would. */
	private static InteractionResult tap(GameTestHelper helper, CombatGameTests.TestPlayer player, ForgeType type) {
		player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
		InteractionResult result = Spellcasting.tryCast(helper.getLevel(), player, InteractionHand.MAIN_HAND, type);
		if (result == InteractionResult.CONSUME) {
			player.releaseUsingItem();
		}
		return result;
	}

	/** What a bolt that is in the air carries. */
	private static float boltDamage(dev.forja.entity.MagicBolt bolt) {
		try {
			Field field = dev.forja.entity.MagicBolt.class.getDeclaredField("damage");
			field.setAccessible(true);
			return field.getFloat(bolt);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("MagicBolt.damage", failure);
		}
	}

	private static void clearBolts(GameTestHelper helper) {
		helper.getLevel().getEntitiesOfClass(dev.forja.entity.MagicBolt.class, helper.getBounds().inflate(32.0)).forEach(bolt -> bolt.discard());
	}

	// ---------------------------------------------------------------- the bar and the spells

	/** A bolt costs mana, the wait after it is the short one, and with less than a tap in the bar nothing leaves. */
	@GameTest
	public void spellCostsManaAndFailsWhenEmpty(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		ItemStack staff = staff(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, staff);
		helper.assertTrue(Math.abs(Mana.value(player) - cfg.manaMax) < EPS, "un jugador nuevo empieza con la barra llena: " + Mana.value(player));
		helper.assertTrue(tap(helper, player, ForgeType.BACULO) == InteractionResult.CONSUME, "con maná, el báculo lanza");
		helper.assertTrue(Math.abs(Mana.value(player) - (cfg.manaMax - cfg.manaBoltCost)) < EPS,
			"un toque cuesta " + cfg.manaBoltCost + ", queda " + Mana.value(player));
		helper.assertTrue(player.getCooldowns().isOnCooldown(staff), "y el báculo espera");
		// The tome: dearer.
		ItemStack tome = forged(helper, ForgeType.GRIMORIO);
		player.setItemInHand(InteractionHand.MAIN_HAND, tome);
		Mana.set(player, 50.0F);
		helper.assertTrue(tap(helper, player, ForgeType.GRIMORIO) == InteractionResult.CONSUME, "con 50 el grimorio abre su área");
		helper.assertTrue(Math.abs(Mana.value(player) - (50.0F - cfg.manaTomeCost)) < EPS, "el área cuesta " + cfg.manaTomeCost + ": " + Mana.value(player));
		// Out: refused, counted, and nothing spent.
		player.setItemInHand(InteractionHand.MAIN_HAND, staff);
		Mana.set(player, cfg.manaBoltCost - 1.0F);
		int denied = Mana.denied(player);
		clearBolts(helper);
		helper.assertTrue(tap(helper, player, ForgeType.BACULO) == InteractionResult.FAIL, "sin un toque de maná el báculo no lanza");
		helper.assertTrue(Mana.denied(player) == denied + 1, "y cuenta la negativa (la barra destella)");
		helper.assertTrue(Math.abs(Mana.value(player) - (cfg.manaBoltCost - 1.0F)) < EPS, "sin gastar nada");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(dev.forja.entity.MagicBolt.class, helper.getBounds().inflate(32.0)).isEmpty(),
			"y no sale ningún proyectil");
		// Creative spends nothing.
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
		Mana.set(player, 0.0F);
		helper.assertTrue(tap(helper, player, ForgeType.BACULO) == InteractionResult.CONSUME, "en creativo el báculo lanza siempre");
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		clearBolts(helper);
		helper.succeed();
	}

	/** The waits: a player's short ones, a monster's the old ones, Conjuro veloz on both. And the charge's price. */
	@GameTest
	public void cooldownsAreShortAndChargeCostsMore(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		ItemStack staff = staff(helper);
		ItemStack tome = forged(helper, ForgeType.GRIMORIO);
		helper.assertTrue(Spellcasting.cooldown(staff, ForgeType.BACULO) == 10 && Spellcasting.cooldown(tome, ForgeType.GRIMORIO) == 20,
			"las esperas del jugador son 10 y 20 tics: " + Spellcasting.cooldown(staff, ForgeType.BACULO) + ", " + Spellcasting.cooldown(tome, ForgeType.GRIMORIO));
		helper.assertTrue(Spellcasting.monsterCooldown(staff, ForgeType.BACULO) == 14 && Spellcasting.monsterCooldown(tome, ForgeType.GRIMORIO) == 70,
			"los monstruos no pagan maná y esperan lo de antes, 14 y 70");
		ItemStack quick = staff(helper, Upgrade.CONJURO_VELOZ, 100);
		helper.assertTrue(Spellcasting.cooldown(quick, ForgeType.BACULO) == 8 && Spellcasting.monsterCooldown(quick, ForgeType.BACULO) == 11,
			"Conjuro veloz quita dos décimas a las dos esperas: " + Spellcasting.cooldown(quick, ForgeType.BACULO) + ", " + Spellcasting.monsterCooldown(quick, ForgeType.BACULO));
		float full = Spellcasting.manaCost(staff, ForgeType.BACULO, 1.0F);
		helper.assertTrue(Math.abs(full - cfg.manaBoltCost * (1.0F + cfg.manaChargeExtra)) < EPS, "una carga llena cuesta un cuarto más: " + full);
		// A charge bigger than the bar pays for leaves as strong as the bar allows.
		float half = Spellcasting.affordableCharge(staff, ForgeType.BACULO, 1.0F, cfg.manaBoltCost * (1.0F + cfg.manaChargeExtra * 0.5F));
		helper.assertTrue(Math.abs(half - 0.5F) < EPS, "con maná para media carga, sale media carga: " + half);
		helper.succeed();
	}

	/** Seconds of quiet an empty bar takes to fill, ticking the real bar. */
	private static float secondsToFill(CombatGameTests.TestPlayer player, long now) {
		Mana.set(player, 0.0F);
		float max = Mana.maxOf(player);
		int ticks = 0;
		while (Mana.value(player) < max - 1.0E-3F && ticks < 20 * 600) {
			ticks++;
			Mana.tick(player, now + ticks);
		}
		return ticks / 20.0F;
	}

	/**
	 * Andy, 2026-09-30: "se debe regenerar lentísimo si no tienes la clase". Without a magic class an empty bar takes
	 * about two minutes; the Mago gets it back six times as fast and the Curandero four, and their talents more.
	 */
	@GameTest(maxTicks = 40)
	public void manaRegenDependsOnTheClass(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		long now = helper.getLevel().getGameTime();
		CombatGameTests.TestPlayer plain = player(helper, new BlockPos(1, 1, 1));
		helper.assertTrue(Math.abs(Mana.regenFactor(plain) - 1.0F) < EPS, "sin clase, el ritmo de base: " + Mana.regenFactor(plain));
		helper.assertTrue(cfg.manaIdleRegenPerTick * 20.0F <= 1.0F, "sin clase vuelve como mucho 1 por segundo en calma: " + cfg.manaIdleRegenPerTick * 20.0F);
		float plainFill = secondsToFill(plain, now);
		helper.assertTrue(plainFill >= 100.0F && plainFill <= 150.0F, "sin clase, una barra vacía tarda unos dos minutos: " + plainFill + " s");
		// A guerrero has a class, but not a magic one.
		ClassProgress.choose(plain, PlayerClass.GUERRERO);
		helper.assertTrue(Math.abs(Mana.regenFactor(plain) - 1.0F) < EPS, "un guerrero, igual que sin clase: " + Mana.regenFactor(plain));
		ClassProgress.clear(plain);

		CombatGameTests.TestPlayer mage = player(helper, new BlockPos(3, 1, 1));
		ClassProgress.choose(mage, PlayerClass.MAGO);
		helper.assertTrue(Math.abs(Mana.regenFactor(mage) - 6.0F) < EPS, "el Mago, ×6: " + Mana.regenFactor(mage));
		float mageFill = secondsToFill(mage, now);
		helper.assertTrue(mageFill <= 30.0F && mageFill < plainFill / 4.0F, "el Mago llena su barra (más grande) en menos de 30 s: " + mageFill + " s");
		ClassProgress.award(mage, ClassProgress.totalFor(5));
		// Mente clara, at the end of the Flujo trunk (docs/ARBOLES.md): +1, on top of the small nodes on the way.
		helper.assertTrue(ArbolGameTests.learnTo(mage, "mago.b.tronco_4"), "Mente clara se aprende");
		float mind = 1.0F + dev.forja.clase.ClassEffects.manaRegenBonus(mage);
		helper.assertTrue(mind >= 7.0F && Math.abs(Mana.regenFactor(mage) - mind) < EPS, "con Mente clara, ×7 o más: " + Mana.regenFactor(mage));
		// The upgrades add to the base, and the class multiplies the sum.
		mage.setItemInHand(InteractionHand.MAIN_HAND, staff(helper, Upgrade.MEDITACION, 100));
		helper.assertTrue(Math.abs(Mana.regenFactor(mage) - mind * 1.4F) < EPS, "Meditación suma a la base y la clase la multiplica: " + Mana.regenFactor(mage));
		plain.setItemInHand(InteractionHand.MAIN_HAND, staff(helper, Upgrade.MEDITACION, 100));
		helper.assertTrue(Mana.regenFactor(plain) < 2.0F, "sin clase, Meditación sola no llega ni a ×2: " + Mana.regenFactor(plain));
		ClassProgress.clear(mage);

		CombatGameTests.TestPlayer healer = player(helper, new BlockPos(5, 1, 1));
		ClassProgress.choose(healer, PlayerClass.CURANDERO);
		helper.assertTrue(Math.abs(Mana.regenFactor(healer) - 4.0F) < EPS, "el Curandero, ×4: " + Mana.regenFactor(healer));
		float healerFill = secondsToFill(healer, now);
		helper.assertTrue(healerFill > mageFill && healerFill <= 40.0F, "el Curandero, entre el Mago y los demás: " + healerFill + " s");
		ClassProgress.award(healer, ClassProgress.totalFor(5));
		helper.assertTrue(ArbolGameTests.learnTo(healer, "curandero.c.tronco_4"), "Serenidad se aprende");
		float serene = 1.0F + dev.forja.clase.ClassEffects.manaRegenBonus(healer);
		helper.assertTrue(serene >= 5.0F && Math.abs(Mana.regenFactor(healer) - serene) < EPS, "con Serenidad, ×5 o más: " + Mana.regenFactor(healer));
		ClassProgress.clear(healer);
		helper.succeed();
	}

	/** Slow while spells keep coming, twice as quick once they have stopped for five seconds. */
	@GameTest
	public void manaComesBackOverTime(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		long now = helper.getLevel().getGameTime();
		Mana.set(player, 20.0F);
		Mana.trySpend(player, 10.0F);
		for (int t = 1; t <= 20; t++) {
			Mana.tick(player, now + t);
		}
		float casting = Mana.value(player) - 10.0F;
		helper.assertTrue(Math.abs(casting - cfg.manaRegenPerTick * 20) < 0.05F, "un segundo tras un hechizo vuelven " + cfg.manaRegenPerTick * 20 + ", volvieron " + casting);
		float before = Mana.value(player);
		for (int t = cfg.manaIdleDelayTicks; t < cfg.manaIdleDelayTicks + 20; t++) {
			Mana.tick(player, now + t);
		}
		float idle = Mana.value(player) - before;
		// Five seconds on from the last spell the quicker rate has taken over.
		float expected = cfg.manaIdleRegenPerTick * 20;
		helper.assertTrue(Math.abs(idle - expected) < 0.05F, "cinco segundos sin lanzar, " + cfg.manaIdleRegenPerTick * 20 + " por segundo: " + idle);
		helper.succeed();
	}

	/** Kills wait in the pool and flow in at most 3 % of the bar every 5 ticks, to mana and to stamina alike. */
	@GameTest
	public void killsFlowInSlowly(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
		Mana.cast(player, 0.0F);
		Mana.set(player, 0.0F);
		Mana.trySpend(player, 0.0F);
		for (int kill = 0; kill < 5; kill++) {
			KillFlow.onKill(player, zombie);
		}
		float max = Mana.max(player);
		float worth = KillFlow.reward(cfg.killManaBase, cfg.killManaPerHealth, cfg.killManaCapShare, max, zombie);
		helper.assertTrue(Math.abs(Mana.pending(player) - worth * 5) < EPS, "cinco muertes esperan " + worth * 5 + ": " + Mana.pending(player));
		long now = helper.getLevel().getGameTime();
		long beat = now + (cfg.killFlowEveryTicks - now % cfg.killFlowEveryTicks);
		float step = max * cfg.killFlowShare;
		for (long t = beat - 4; t <= beat + cfg.killFlowEveryTicks * 3; t++) {
			float before = Mana.value(player);
			float regen = Mana.regenPerTick(player, t);
			Mana.tick(player, t);
			float flowed = Mana.value(player) - before - regen;
			float wanted = t % cfg.killFlowEveryTicks == 0 ? step : 0.0F;
			helper.assertTrue(Math.abs(flowed - wanted) < 0.02F, "en el tic " + (t - beat) + " entra " + flowed + " de las muertes, debería " + wanted);
		}
		helper.assertTrue(Math.abs(Mana.pending(player) - (worth * 5 - step * 4)) < 0.05F, "tras cuatro latidos queda lo demás esperando");
		// A pool never holds more than the room in the bar.
		Mana.set(player, max - 2.0F);
		KillFlow.onKill(player, zombie);
		helper.assertTrue(Mana.pending(player) <= 2.0F + EPS, "no espera más de lo que cabe: " + Mana.pending(player));

		// Stamina: the same pool, the same beat.
		Stamina.trySpend(player, 90.0F);
		float stamina = Stamina.value(player);
		Stamina.onKill(player, zombie);
		float staminaWorth = KillFlow.reward(cfg.killStaminaBase, cfg.killStaminaPerHealth, cfg.killStaminaCapShare, Stamina.maxOf(player), zombie);
		helper.assertTrue(Math.abs(Stamina.pending(player) - staminaWorth) < EPS, "la estamina también espera: " + Stamina.pending(player));
		long start = helper.getLevel().getGameTime();
		for (long t = start + 1; t <= start + cfg.killFlowEveryTicks; t++) {
			Stamina.tick(player, t);
		}
		float gained = Stamina.value(player) - stamina;
		helper.assertTrue(Math.abs(gained - Stamina.maxOf(player) * cfg.killFlowShare) < 0.02F,
			"en cinco tics entra a la estamina un 3 % de la barra y nada más: " + gained);
		helper.succeed();
	}

	/** The bar is kept with the player (codec, and the copy it carries), synced to them, and whole after death. */
	@GameTest
	public void manaIsSavedSyncedAndRefilledOnDeath(GameTestHelper helper) {
		Mana.State state = new Mana.State(42.5F, 100.0F, 7.0F, true, 3);
		var tag = Mana.State.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
		Mana.State back = Mana.State.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.mana() == 42.5F && back.pending() == 7.0F && back.awake(), "el maná se guarda y se lee igual: " + back);

		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		Mana.cast(player, 0.0F);
		Mana.set(player, 37.0F);
		Mana.tick(player, helper.getLevel().getGameTime());
		Mana.State synced = player.getAttached(Mana.STATE);
		helper.assertTrue(synced != null && Math.abs(synced.mana() - Mana.value(player)) < 0.5F && synced.awake(),
			"la copia que va al cliente sigue al valor: " + synced);
		Mana.save(player);
		float kept = Mana.value(player);
		Mana.forget(player);
		helper.assertTrue(Math.abs(Mana.value(player) - kept) < EPS, "olvidado el valor exacto, se lee el guardado: " + Mana.value(player) + " de " + kept);

		Mana.set(player, 5.0F);
		ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(player, player, false);
		helper.assertTrue(Math.abs(Mana.value(player) - Mana.max(player)) < EPS && Mana.pending(player) == 0.0F, "al reaparecer la barra vuelve llena");
		helper.assertTrue(Math.abs(player.getAttached(Mana.STATE).mana() - Mana.max(player)) < EPS, "y el cliente lo sabe");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the upgrades

	/** Every option of every new upgrade, on every piece it goes on, is that upgrade and no other one. */
	@GameTest
	public void newUpgradesHaveTheirOwnRecipes(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		for (Upgrade upgrade : List.of(Upgrade.CONCENTRACION, Upgrade.SIFON, Upgrade.DESCARGA, Upgrade.MEDITACION, Upgrade.RESERVA, Upgrade.FLUJO,
			Upgrade.FILO_ARCANO, Upgrade.ESTALLIDO_ARCANO, Upgrade.PASO_ARCANO, Upgrade.AGUANTE, Upgrade.FUELLE, Upgrade.QUIEBRO, Upgrade.IMPULSO,
			Upgrade.SOLTURA)) {
			int types = 0;
			for (ForgeType type : ForgeType.values()) {
				if (!upgrade.appliesTo(type)) {
					continue;
				}
				types++;
				for (Upgrade.Option option : upgrade.options) {
					ItemStack piece = Assembler.create(type, Assembler.defaultMaterials(type), registries);
					List<ItemStack> ingredients = new java.util.ArrayList<>();
					for (Upgrade.Requirement requirement : option.requirements()) {
						ingredients.add(requirement.displayStack().copyWithCount(8));
					}
					var applied = UpgradeRecipes.apply(piece, ingredients, registries);
					helper.assertTrue(applied != null && applied.upgrade() == upgrade && !applied.result().isEmpty(),
						upgrade + " en " + type + " con " + ingredients + " dio " + (applied == null ? null : applied.upgrade()));
				}
			}
			helper.assertTrue(types > 0, upgrade + " no va en nada");
			int weight = Potential.weight(upgrade);
			helper.assertTrue(weight == 2 || weight == 3, upgrade + " pesa " + weight);
			helper.assertTrue(!upgrade.effect(100).getString().isEmpty(), upgrade + " dice lo que hace");
		}
		helper.assertTrue(Upgrade.FILO_ARCANO.appliesTo(ForgeType.ESPADA) && Upgrade.FILO_ARCANO.appliesTo(ForgeType.LANZA)
			&& !Upgrade.FILO_ARCANO.appliesTo(ForgeType.MAZO) && !Upgrade.FILO_ARCANO.appliesTo(ForgeType.BACULO),
			"la magia melee es de las armas de filo");
		helper.succeed();
	}

	/** Concentración, Meditación, Reserva, Flujo and the two sets: numbers the bar reads. */
	@GameTest
	public void barUpgradesMoveTheNumbers(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		helper.assertTrue(Math.abs(Spellcasting.tapCost(staff(helper, Upgrade.CONCENTRACION, 100), ForgeType.BACULO) - cfg.manaBoltCost * 0.65F) < EPS,
			"Concentración al 100 quita un 35 % al coste");
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		helper.assertTrue(Math.abs(Mana.regenFactor(player) - 1.0F) < EPS, "desnudo, el maná vuelve a su ritmo");
		player.setItemInHand(InteractionHand.MAIN_HAND, staff(helper, Upgrade.MEDITACION, 100));
		helper.assertTrue(Math.abs(Mana.regenFactor(player) - 1.4F) < EPS, "Meditación en la mano, un 40 % más rápido: " + Mana.regenFactor(player));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ForgeType type = switch (slot) {
				case HEAD -> ForgeType.CASCO;
				case CHEST -> ForgeType.PECHERA;
				case LEGS -> ForgeType.GREBAS;
				default -> ForgeType.BOTAS;
			};
			player.setItemSlot(slot, forged(helper, type, Upgrade.RESERVA, 100, Upgrade.FLUJO, 100, Upgrade.AGUANTE, 100, Upgrade.FUELLE, 100));
		}
		helper.assertTrue(Math.abs(Mana.maxOf(player) - (cfg.manaMax + 100.0F)) < EPS, "Reserva en las cuatro piezas, +25 cada una: " + Mana.maxOf(player));
		helper.assertTrue(Math.abs(Mana.regenFactor(player) - 1.6F) < EPS, "Flujo en las cuatro, +15 % cada una: " + Mana.regenFactor(player));
		helper.assertTrue(Math.abs(Stamina.maxOf(player) - (cfg.staminaMax + 60.0F)) < EPS, "Aguante en las cuatro, +15 cada una: " + Stamina.maxOf(player));
		helper.assertTrue(Math.abs(Stamina.regenFactor(player) - 1.8F) < EPS, "Fuelle en las cuatro, +20 % cada una: " + Stamina.regenFactor(player));
		// The max the bar is drawn to follows the armour.
		Mana.tick(player, helper.getLevel().getGameTime());
		helper.assertTrue(Math.abs(player.getAttached(Mana.STATE).max() - (cfg.manaMax + 100.0F)) < EPS, "y el cliente ve la barra más larga");
		Stamina.tick(player, helper.getLevel().getGameTime());
		helper.assertTrue(Math.abs(player.getAttached(Stamina.RESERVE).max() - (cfg.staminaMax + 60.0F)) < EPS, "la de estamina también");

		// Sets: amethyst holds more, echo gives back faster.
		CombatGameTests.TestPlayer mage = player(helper, new BlockPos(3, 1, 1));
		var registries = helper.getLevel().registryAccess();
		for (ForgeMaterial material : List.of(ForgeMaterial.AMATISTA, ForgeMaterial.ECO)) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ForgeType type = switch (slot) {
					case HEAD -> ForgeType.CASCO;
					case CHEST -> ForgeType.PECHERA;
					case LEGS -> ForgeType.GREBAS;
					default -> ForgeType.BOTAS;
				};
				List<ForgeMaterial> materials = new java.util.ArrayList<>(Assembler.defaultMaterials(type));
				materials.set(0, material);
				mage.setItemSlot(slot, Assembler.create(type, materials, registries));
			}
			if (material == ForgeMaterial.AMATISTA) {
				helper.assertTrue(Math.abs(Mana.maxOf(mage) - (cfg.manaMax + Mana.AMETHYST_SET_MANA)) < EPS, "cuatro de amatista: +40 de maná: " + Mana.maxOf(mage));
			} else {
				helper.assertTrue(Math.abs(Mana.regenFactor(mage) - (1.0F + Mana.ECHO_SET_REGEN)) < EPS, "cuatro de eco: +30 % de regeneración: " + Mana.regenFactor(mage));
			}
		}
		helper.succeed();
	}

	/** Sifón pays back once a spell; Descarga pours the bar into a full charge. */
	@GameTest
	public void siphonAndDischarge(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		ItemStack siphon = staff(helper, Upgrade.SIFON, 100);
		player.setItemInHand(InteractionHand.MAIN_HAND, siphon);
		Mana.set(player, 50.0F);
		tap(helper, player, ForgeType.BACULO);
		float after = Mana.value(player);
		Mana.onSpellLanded(player, siphon);
		helper.assertTrue(Math.abs(Mana.value(player) - (after + cfg.manaBoltCost * 0.5F)) < EPS, "Sifón al 100 devuelve la mitad del coste: " + Mana.value(player));
		float once = Mana.value(player);
		Mana.onSpellLanded(player, siphon);
		helper.assertTrue(Math.abs(Mana.value(player) - once) < EPS, "una sola vez por hechizo");
		clearBolts(helper);

		// A full charge, plain: the bolt carries half again, and costs a quarter more.
		ItemStack plain = staff(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, plain);
		Mana.set(player, 100.0F);
		player.startUsingItem(InteractionHand.MAIN_HAND);
		Spellcasting.release(helper.getLevel(), player, plain, ForgeType.BACULO, Spellcasting.STAFF_CHARGE_TICKS);
		var plainBolts = helper.getLevel().getEntitiesOfClass(dev.forja.entity.MagicBolt.class, helper.getBounds().inflate(32.0));
		helper.assertTrue(plainBolts.size() == 1, "una carga llena lanza un proyectil, vi " + plainBolts.size());
		float plainDamage = boltDamage(plainBolts.getFirst());
		helper.assertTrue(Math.abs(Mana.value(player) - (100.0F - cfg.manaBoltCost * (1.0F + cfg.manaChargeExtra))) < EPS, "y cuesta un cuarto más: " + Mana.value(player));
		clearBolts(helper);

		ItemStack dump = staff(helper, Upgrade.DESCARGA, 100);
		player.setItemInHand(InteractionHand.MAIN_HAND, dump);
		Mana.set(player, 100.0F);
		player.startUsingItem(InteractionHand.MAIN_HAND);
		Spellcasting.release(helper.getLevel(), player, dump, ForgeType.BACULO, Spellcasting.STAFF_CHARGE_TICKS);
		var dumped = helper.getLevel().getEntitiesOfClass(dev.forja.entity.MagicBolt.class, helper.getBounds().inflate(32.0));
		helper.assertTrue(dumped.size() == 1, "Descarga lanza un proyectil, vi " + dumped.size());
		float poured = 100.0F - cfg.manaBoltCost * (1.0F + cfg.manaChargeExtra);
		float expected = plainDamage * (1.0F + Math.min(Spellcasting.POUR_MOST, poured / 10.0F * 0.1F));
		helper.assertTrue(Mana.value(player) < EPS, "Descarga vacía la barra: " + Mana.value(player));
		helper.assertTrue(Math.abs(boltDamage(dumped.getFirst()) - expected) < 0.05F,
			"y cada 10 de más pega un 10 % más, hasta el doble: " + boltDamage(dumped.getFirst()) + ", esperado " + expected);
		clearBolts(helper);

		// A tap with Descarga is a tap: the bar is not poured.
		player.setItemInHand(InteractionHand.MAIN_HAND, dump);
		Mana.set(player, 60.0F);
		tap(helper, player, ForgeType.BACULO);
		helper.assertTrue(Math.abs(Mana.value(player) - (60.0F - cfg.manaBoltCost)) < EPS, "un toque con Descarga no vacía nada");
		clearBolts(helper);
		helper.succeed();
	}

	/** Filo arcano: mana for magic on every blow, and plain steel with the bar empty. */
	@GameTest
	public void arcaneEdgeSpendsManaForMagic(GameTestHelper helper) {
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		IronGolem golem = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(3, 1, 3));
		golem.setNoAi(true);
		ItemStack plain = forged(helper, ForgeType.ESPADA);
		ItemStack arcane = forged(helper, ForgeType.ESPADA, Upgrade.FILO_ARCANO, 100);
		Mana.set(player, 50.0F);
		double without = hitWith(helper, player, golem, plain, 6.0F);
		double with = hitWith(helper, player, golem, arcane, 6.0F);
		helper.assertTrue(Math.abs(Mana.value(player) - (50.0F - Upgrade.ARCANE_EDGE_COST)) < EPS, "cada golpe gasta " + Upgrade.ARCANE_EDGE_COST + ": " + Mana.value(player));
		helper.assertTrue(with > without + 1.5, "con Filo arcano el golpe suma magia: " + with + " contra " + without);
		Mana.set(player, 2.0F);
		double empty = hitWith(helper, player, golem, arcane, 6.0F);
		helper.assertTrue(Math.abs(empty - without) < 0.01 && Math.abs(Mana.value(player) - 2.0F) < EPS, "sin maná es acero y nada más: " + empty);
		helper.succeed();
	}

	/** Estallido arcano: a full charge bursts round the target for mana; short of full, or out of mana, nothing. */
	@GameTest
	public void arcaneBurstNeedsAFullChargeAndMana(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		IronGolem target = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(3, 1, 3));
		IronGolem beside = helper.spawn(EntityTypes.IRON_GOLEM, new BlockPos(5, 1, 3));
		target.setNoAi(true);
		beside.setNoAi(true);
		player.setItemInHand(InteractionHand.MAIN_HAND, forged(helper, ForgeType.ESPADON, Upgrade.ESTALLIDO_ARCANO, 100));
		Mana.set(player, 100.0F);
		float health = beside.getHealth();
		ChargedStrike.arcaneBurst(player, target, 0.9, cfg);
		helper.assertTrue(beside.getHealth() == health && Math.abs(Mana.value(player) - 100.0F) < EPS, "sin carga llena no estalla");
		ChargedStrike.arcaneBurst(player, target, 1.0, cfg);
		helper.assertTrue(beside.getHealth() < health, "a carga llena lo de al lado recibe el estallido");
		helper.assertTrue(Math.abs(Mana.value(player) - (100.0F - Upgrade.ARCANE_BURST_COST)) < EPS, "y cuesta " + Upgrade.ARCANE_BURST_COST);
		float hurt = beside.getHealth();
		Mana.set(player, Upgrade.ARCANE_BURST_COST - 1.0F);
		beside.invulnerableTime = 0;
		ChargedStrike.arcaneBurst(player, target, 1.0, cfg);
		helper.assertTrue(beside.getHealth() == hurt, "sin maná bastante no estalla");
		helper.succeed();
	}

	/** Quiebro, Paso arcano, Impulso and Soltura: how far the moves go and what they cost. */
	@GameTest
	public void movementUpgrades(GameTestHelper helper) {
		CombatConfig cfg = CombatConfig.get();
		CombatGameTests.TestPlayer player = player(helper, new BlockPos(1, 1, 1));
		helper.assertTrue(Math.abs(Stamina.dodgeReach(player) - 1.0) < EPS, "descalzo, la esquiva de siempre");
		player.setItemSlot(EquipmentSlot.FEET, forged(helper, ForgeType.BOTAS, Upgrade.QUIEBRO, 100));
		helper.assertTrue(Math.abs(Stamina.dodgeReach(player) - 1.5) < EPS, "Quiebro al 100: un 50 % más lejos: " + Stamina.dodgeReach(player));
		player.setItemInHand(InteractionHand.MAIN_HAND, forged(helper, ForgeType.DAGA, Upgrade.PASO_ARCANO, 100));
		Mana.set(player, 50.0F);
		helper.assertTrue(Math.abs(Stamina.dodgeReach(player) - 2.3) < EPS, "y Paso arcano con maná, un 80 % más: " + Stamina.dodgeReach(player));
		long now = helper.getLevel().getGameTime();
		Stamina.onDodge(player, 1.0F, 0.0F);
		helper.assertTrue(Math.abs(Mana.value(player) - (50.0F - Upgrade.BLINK_COST)) < EPS, "el paso cuesta " + Upgrade.BLINK_COST + " de maná: " + Mana.value(player));
		helper.assertTrue(Stamina.isDodging(player, now + cfg.dodgeIframeTicks + Stamina.BLINK_IFRAMES), "y guarda " + Stamina.BLINK_IFRAMES + " tics más de invulnerabilidad");
		Mana.set(player, Upgrade.BLINK_COST - 1.0F);
		helper.assertTrue(Math.abs(Stamina.dodgeReach(player) - 1.5) < EPS, "sin maná, solo lo de las botas");

		helper.assertTrue(Math.abs(SpecialAttacks.chargePush(player) - SpecialAttacks.CHARGE_PUSH) < EPS, "sin grebas, la embestida de siempre");
		player.setItemSlot(EquipmentSlot.LEGS, forged(helper, ForgeType.GREBAS, Upgrade.IMPULSO, 100));
		helper.assertTrue(Math.abs(SpecialAttacks.chargePush(player) - SpecialAttacks.CHARGE_PUSH * 1.6) < EPS, "Impulso: la embestida un 60 % más lejos");

		helper.assertTrue(Math.abs(Stamina.cost(player, cfg.dodgeCost) - cfg.dodgeCost) < EPS, "sin Soltura la esquiva cuesta lo de siempre");
		player.setItemSlot(EquipmentSlot.CHEST, forged(helper, ForgeType.PECHERA, Upgrade.SOLTURA, 100));
		helper.assertTrue(Math.abs(Stamina.cost(player, cfg.dodgeCost) - cfg.dodgeCost * 0.65F) < EPS, "Soltura quita un 35 %: " + Stamina.cost(player, cfg.dodgeCost));
		CombatGameTests.TestPlayer jumper = player(helper, new BlockPos(3, 1, 1));
		jumper.setItemSlot(EquipmentSlot.CHEST, forged(helper, ForgeType.PECHERA, Upgrade.SOLTURA, 100));
		float before = Stamina.value(jumper);
		Stamina.onJump(jumper);
		helper.assertTrue(Math.abs(before - Stamina.value(jumper) - cfg.jumpCost * 0.65F) < EPS, "y el salto cuesta menos: " + (before - Stamina.value(jumper)));
		helper.succeed();
	}

	// ---------------------------------------------------------------- helpers borrowed from BalanceGameTests

	/** One blow of {@code weapon} through the real upgrade handler, with the flag the damage event puts up. */
	private static double hitWith(GameTestHelper helper, CombatGameTests.TestPlayer player, LivingEntity victim, ItemStack weapon, float taken) {
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		victim.setHealth(victim.getMaxHealth());
		victim.invulnerableTime = 0;
		clearProcs();
		float before = victim.getHealth();
		asTheDamageEventDoes(() -> CombatUpgrades.onWeaponHitForTest(helper.getLevel(), player, victim, weapon, taken));
		double lost = before - victim.getHealth();
		victim.setHealth(victim.getMaxHealth());
		dev.forja.combat.Posture.endStagger(victim);
		return lost;
	}

	private static void clearProcs() {
		try {
			Field procs = CombatUpgrades.class.getDeclaredField("PROCS_THIS_TICK");
			procs.setAccessible(true);
			((Map<?, ?>) procs.get(null)).clear();
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("PROCS_THIS_TICK", failure);
		}
	}

	@SuppressWarnings("unchecked")
	private static void asTheDamageEventDoes(Runnable handler) {
		ThreadLocal<Boolean> flag;
		try {
			Field field = CombatUpgrades.class.getDeclaredField("EXTRA_DAMAGE");
			field.setAccessible(true);
			flag = (ThreadLocal<Boolean>) field.get(null);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("EXTRA_DAMAGE", failure);
		}
		flag.set(true);
		try {
			handler.run();
		} finally {
			flag.set(false);
		}
	}
}
