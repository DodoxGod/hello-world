package dev.forja.test.balance;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.forja.clase.ClassData;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.ClassStat;
import dev.forja.clase.ClassTree;
import dev.forja.clase.PlayerClass;
import dev.forja.combat.CombatConfig;
import dev.forja.difficulty.ForjaDifficulty;
import dev.forja.difficulty.GearScore;
import dev.forja.difficulty.Ladder;
import dev.forja.entity.FallenSmith;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.ForgedStar;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEntities;
import dev.forja.test.CombatGameTests;
import dev.forja.upgrade.ArmorSets;
import dev.forja.upgrade.HiddenEnchantments;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import dev.forja.world.ApprenticeKits;
import dev.forja.world.Elites;
import dev.forja.world.StarFight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Herrero Caído's fight, measured (docs/EQUILIBRIO.md, "Herrero Caído"; Andy, 2026-09-30: "parece que puedes llegar a
 * estar muy fuerte, o el Herrero Caído es muy débil").
 *
 * <p>What is measured on the real boss, through the real damage pipeline: what a blow of each weapon type, a bolt and
 * the rest take off him in each of his three stages and while he is stunned (his size, armour and guard as the fight
 * sets them for the level, the number of players and their gear); what his apprentices and the walking anvils take; and
 * what each of his blows takes off a geared player (the plain swing, the backhand, the shockwave, the hook and the
 * stars), fresh and under the pressure of a long fight.
 *
 * <p>What is a model, written down here so it can be argued with: the player's damage a second against each stage is
 * the balance report's tick-by-tick fight (Fight) over two minutes against that stage; the share of the time a player
 * can be hitting him (the uptime) is what is left once each of his moves has been dodged as often as it comes back; the
 * pauses (getting up, the two calls of the apprentices, the Reforjado and its embers) are the fight's own lengths; the
 * apprentices and the anvils are killed, one after another, with the same weapon. Two players share the damage, the
 * apprentices and the embers; the one he is not after has more time to hit him.
 */
public final class SmithFight {
	/** The levels with a fight: a peaceful world keeps no monster, him included. */
	public static final List<Ladder> LEVELS = List.of(Ladder.FACIL, Ladder.NORMAL, Ladder.DIFICIL, Ladder.EXTREMO);

	/**
	 * The window the fight of the reference kit (Kit.ESTRELLA), alone, has to fall in on each level, in seconds of real
	 * fighting from the moment he is on his feet. Andy, 2026-09-30: "3 a 5 minutos en Difícil, más en Extremo y menos en
	 * Normal y Fácil".
	 */
	public static final Map<Ladder, double[]> WINDOW = new EnumMap<>(Map.of(
		Ladder.FACIL, new double[] {90.0, 180.0},
		Ladder.NORMAL, new double[] {120.0, 240.0},
		Ladder.DIFICIL, new double[] {180.0, 300.0},
		Ladder.EXTREMO, new double[] {240.0, 420.0}));

	/**
	 * Most of a geared player's health one of his blows may take: half on a player who has just come in, and short of
	 * all of it on one whose armour the pressure of a long fight has worn down (Difficulty/Pressure, 60 % at most). No
	 * unavoidable one-shot.
	 */
	public static final double MOST_OF_ONE_HIT = 0.5;
	public static final double MOST_UNDER_PRESSURE = 0.8;
	/** From Difícil on, a careless geared player in front of him dies within this many seconds, even in his first stage. */
	public static final double CARELESS_MOST = 60.0;
	/** No kit, alone, beats him in less than this share of the start of the level's window. */
	public static final double FASTEST_SHARE = 0.6;

	// ---------------------------------------------------------------- the model's own numbers

	/** Share of the fight a player loses to moving, aiming and following him, close in and from afar. */
	public static final double MELEE_LOSS = 0.15;
	public static final double RANGED_LOSS = 0.10;
	/** Seconds a dodge costs: jumping or leaving the shockwave, stepping out of the backhand, the stars and the hook from afar. */
	public static final double WAVE_DODGE = 1.5;
	public static final double STRIKE_DODGE = 1.0;
	public static final double STAR_DODGE = 1.0;
	public static final double HOOK_DODGE = 1.5;
	/** His side of the sky: an event every 35 s on average, half of them his, three seconds each to get out of. */
	public static final double SKY_LOSS = 3.0 / 70.0;
	/** The least share of the time anyone is hitting him. */
	public static final double LEAST_UPTIME = 0.3;
	/** Seconds to get to a brasero and tip it, for each ember of the Reforjado; then its metal runs (StarFight.FLOW_TICKS). */
	public static final double EMBER_SECONDS = 6.0;
	/** The share of the time spent on apprentices and anvils that goes on hitting them (they move, the archers keep off). */
	public static final double ADD_UPTIME = 0.8;
	/** How often his plain blow comes: the melee goal's 20 ticks and the wind-up (entity/MobMoves.WINDUP_TICKS). */
	public static final double PLAIN_EVERY = 1.4;
	/** Apprentices on a careless player at once, and how often each swings. */
	public static final int APPRENTICES_ON_YOU = 2;
	public static final double APPRENTICE_EVERY = 1.5;
	/** What an attentive player still takes of his plain blow: half (the other half is blocked, parried or stepped out of). */
	public static final double ATTENTIVE_PLAIN = 0.5;
	/** How long a fight runs in the damage simulation, and how many times. */
	public static final int SIM_TICKS = 2400;
	public static final int SIM_RUNS = 4;

	// ---------------------------------------------------------------- kits

	/** What the players fight him with. */
	public enum Kit {
		CC("mejor arma cuerpo a cuerpo al 100 %, sin clase", null, false, false),
		GUERRERO("la misma, Guerrero de nivel 50", PlayerClass.GUERRERO, false, false),
		ESTRELLA("la misma estrellada, Guerrero de nivel 50, armadura estrellada (referencia)", PlayerClass.GUERRERO, true, false),
		MAGO("báculo con Enjambre estrellado, Mago de nivel 50, armadura estrellada", PlayerClass.MAGO, true, true);

		public final String label;
		public final PlayerClass clazz;
		public final boolean starred;
		public final boolean magic;

		Kit(String label, PlayerClass clazz, boolean starred, boolean magic) {
			this.label = label;
			this.clazz = clazz;
			this.starred = starred;
			this.magic = magic;
		}
	}

	/** A kit made: the weapon, the fight's options for its class, and the player wearing it all. */
	public static final class Gear {
		public Kit kit;
		public Build weapon;
		public Fight.Options options;
		public CombatGameTests.TestPlayer player;
		public double score;
		public double maxHealth;
	}

	/** One fight worked out: a level, how many players, a kit. */
	public static final class Row {
		public Ladder level;
		public int players;
		public Kit kit;
		public double health;
		public double gear;
		/** Damage a second of one player against each stage (1, 2, 3) and while he is stunned. */
		public final double[] dps = new double[4];
		/** Share of the time the player he is after is hitting him, by stage. */
		public final double[] uptime = new double[3];
		public double hitting;
		public double pauses;
		public double adds;
		public double reforge;
		public double seconds;
		/** Seconds a player standing in front of him lasts: careless (eats everything), attentive (dodges what is warned), by stage. */
		public final double[] careless = new double[3];
		public final double[] attentive = new double[3];
		/** The biggest single hit on the geared player, as a share of their health, and which move: fresh, and under full pressure. */
		public double worst;
		public String worstMove = "";
		public double worstPressed;
		public String worstPressedMove = "";
		public double playerHealth;
		/** Seconds of hitting him without ever stopping (no dodging, no pauses, no apprentices): the old way of counting. */
		public double raw;
		/** Share of the player's own blows on him that his per-blow cap (hitCapBoss) cut short, in his first stage. */
		public double capped;
	}

	/**
	 * The same fights measured on the boss as he was before this rebalance (commit 953f913, 2026-09-30), kept so the
	 * report can say what changed: level, players, kit to {seconds, his health, careless player's seconds in stage 1, in
	 * stage 3}. Measured with this very model, so before and after compare like with like.
	 */
	private static final Map<String, double[]> BEFORE = Map.ofEntries(
		Map.entry("FACIL|1|CC", new double[] {103, 256, 13, 8}),
		Map.entry("FACIL|2|CC", new double[] {59, 256, 14, 8}),
		Map.entry("FACIL|1|GUERRERO", new double[] {95, 256, 21, 12}),
		Map.entry("FACIL|2|GUERRERO", new double[] {55, 256, 22, 12}),
		Map.entry("FACIL|1|ESTRELLA", new double[] {91, 256, 23, 13}),
		Map.entry("FACIL|2|ESTRELLA", new double[] {54, 256, 24, 13}),
		Map.entry("FACIL|1|MAGO", new double[] {106, 256, 20, 13}),
		Map.entry("FACIL|2|MAGO", new double[] {62, 256, 21, 13}),
		Map.entry("NORMAL|1|CC", new double[] {176, 512, 5, 4}),
		Map.entry("NORMAL|2|CC", new double[] {89, 512, 6, 4}),
		Map.entry("NORMAL|1|GUERRERO", new double[] {158, 512, 9, 6}),
		Map.entry("NORMAL|2|GUERRERO", new double[] {81, 512, 9, 6}),
		Map.entry("NORMAL|1|ESTRELLA", new double[] {149, 512, 10, 7}),
		Map.entry("NORMAL|2|ESTRELLA", new double[] {78, 512, 10, 6}),
		Map.entry("NORMAL|1|MAGO", new double[] {180, 512, 8, 6}),
		Map.entry("NORMAL|2|MAGO", new double[] {93, 512, 9, 6}),
		Map.entry("DIFICIL|1|CC", new double[] {176, 512, 6, 4}),
		Map.entry("DIFICIL|2|CC", new double[] {89, 512, 6, 4}),
		Map.entry("DIFICIL|1|GUERRERO", new double[] {158, 512, 9, 6}),
		Map.entry("DIFICIL|2|GUERRERO", new double[] {81, 512, 9, 6}),
		Map.entry("DIFICIL|1|ESTRELLA", new double[] {149, 512, 10, 7}),
		Map.entry("DIFICIL|2|ESTRELLA", new double[] {78, 512, 10, 7}),
		Map.entry("DIFICIL|1|MAGO", new double[] {180, 512, 9, 6}),
		Map.entry("DIFICIL|2|MAGO", new double[] {93, 512, 9, 6}),
		Map.entry("EXTREMO|1|CC", new double[] {225, 666, 5, 3}),
		Map.entry("EXTREMO|2|CC", new double[] {108, 666, 5, 3}),
		Map.entry("EXTREMO|1|GUERRERO", new double[] {199, 666, 7, 5}),
		Map.entry("EXTREMO|2|GUERRERO", new double[] {96, 666, 7, 5}),
		Map.entry("EXTREMO|1|ESTRELLA", new double[] {187, 666, 8, 5}),
		Map.entry("EXTREMO|2|ESTRELLA", new double[] {91, 666, 8, 5}),
		Map.entry("EXTREMO|1|MAGO", new double[] {225, 666, 7, 5}),
		Map.entry("EXTREMO|2|MAGO", new double[] {110, 666, 7, 5}));

	public static double[] before(Ladder level, int players, Kit kit) {
		return BEFORE.get(level.name() + "|" + players + "|" + kit.name());
	}

	public final List<Row> rows = new ArrayList<>();
	public final Map<Kit, Gear> gear = new EnumMap<>(Kit.class);

	private final Analysis analysis;
	private final Probe probe;
	private final GameTestHelper helper;
	private final ServerLevel level;
	private final List<LivingEntity> spawned = new ArrayList<>();

	public SmithFight(Analysis analysis, Probe probe) {
		this.analysis = analysis;
		this.probe = probe;
		this.helper = probe.helper();
		this.level = this.helper.getLevel();
	}

	/** Every level, alone and two, with every kit. Puts the config back as it was. */
	public void measure(Build melee, Build staff) {
		CombatConfig cfg = CombatConfig.get();
		String savedLevel = cfg.nivel;
		String savedPreset = cfg.dificultad;
		try {
			cfg.dificultad = "auto";
			for (Kit kit : Kit.values()) {
				this.gear.put(kit, this.dress(kit, kit.magic ? staff : melee));
			}
			for (Ladder ladder : LEVELS) {
				cfg.nivel = ladder.name();
				for (Kit kit : Kit.values()) {
					for (int players = 1; players <= 2; players++) {
						this.rows.add(this.fight(ladder, players, this.gear.get(kit)));
					}
				}
			}
		} finally {
			cfg.nivel = savedLevel;
			cfg.dificultad = savedPreset;
			for (LivingEntity entity : this.spawned) {
				entity.discard();
			}
			this.spawned.clear();
		}
	}

	public Row row(Ladder ladder, int players, Kit kit) {
		for (Row row : this.rows) {
			if (row.level == ladder && row.players == players && row.kit == kit) {
				return row;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- the kits, made

	private Gear dress(Kit kit, Build base) {
		var registries = this.level.registryAccess();
		Gear gear = new Gear();
		gear.kit = kit;
		// An endgame weapon has been used a lifetime: its Maestría at the top, as the smith's own level.
		gear.weapon = new Build(base.parts(), base.upgrades, registries, kit.starred, dev.forja.forge.Mastery.MAX_LEVEL);
		Fight.Options options = kit.clazz == PlayerClass.MAGO ? Analysis.mago(this.analysis.options, true)
			: kit.clazz != null ? classOptions(this.analysis.options, kit.clazz) : this.analysis.options.copy();
		CombatGameTests.TestPlayer player = new CombatGameTests.TestPlayer(this.level);
		player.setPos(this.helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
		ItemStack[] armour = armour(kit.starred);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < slots.length; i++) {
			wear(player, slots[i], armour[i]);
		}
		wear(player, EquipmentSlot.MAINHAND, gear.weapon.stack.copy());
		ArmorSets.update(player);
		player.setAttached(dev.forja.forge.SmithLevel.EXPERIENCE,
			dev.forja.forge.SmithLevel.MAX_LEVEL * dev.forja.forge.SmithLevel.MAX_LEVEL * dev.forja.forge.SmithLevel.STEP);
		if (kit.clazz != null) {
			List<String> nodes = new ArrayList<>();
			for (ClassTree.Node node : kit.clazz.tree().nodes) {
				if (node.kind != ClassTree.Kind.CLAVE) {
					nodes.add(node.id);
				}
			}
			ClassProgress.set(player, new ClassData(kit.clazz.id(), ClassProgress.MAX_LEVEL, ClassProgress.totalFor(ClassProgress.MAX_LEVEL),
				nodes, 0L, 0L, 0L, 0, List.of(), ClassData.VERSION, 0));
		}
		player.setHealth(player.getMaxHealth());
		options.armorWeight = dev.forja.combat.ArmorCalculator.armorWeight(player);
		gear.options = options;
		gear.player = player;
		gear.score = GearScore.of(player);
		gear.maxHealth = player.getMaxHealth();
		return gear;
	}

	/**
	 * The reference armour: obsidian steel plate on leather, Protección at 100 % on each piece and Vitalidad on the chest,
	 * at full Maestría.
	 */
	private ItemStack[] armour(boolean starred) {
		ForgeType[] pieces = {ForgeType.CASCO, ForgeType.PECHERA, ForgeType.GREBAS, ForgeType.BOTAS};
		ItemStack[] set = new ItemStack[pieces.length];
		for (int i = 0; i < pieces.length; i++) {
			ItemStack piece = Assembler.create(pieces[i], List.of(ForgeMaterial.OBSIDIACERO, ForgeMaterial.CUERO), this.level.registryAccess());
			Upgrades upgrades = Upgrades.EMPTY.with(Upgrade.PROTECCION, 100);
			if (pieces[i] == ForgeType.PECHERA) {
				upgrades = upgrades.with(Upgrade.VITALIDAD, 100);
			}
			piece.set(ModComponents.UPGRADES, upgrades);
			Assembler.rewrite(piece, BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
			dev.forja.forge.Mastery.setLevel(piece, dev.forja.forge.Mastery.MAX_LEVEL, this.level.registryAccess());
			if (starred) {
				piece = ForgedStar.star(piece);
			}
			HiddenEnchantments.write(piece, this.level.registryAccess());
			set[i] = piece;
		}
		return set;
	}

	/** Puts a piece on a test player with its attributes (a test player never ticks them on by itself). */
	private static void wear(CombatGameTests.TestPlayer player, EquipmentSlot slot, ItemStack stack) {
		player.setItemSlot(slot, stack);
		stack.forEachModifier(slot, (attribute, modifier) -> {
			var instance = player.getAttribute(attribute);
			if (instance != null) {
				instance.addOrUpdateTransientModifier(modifier);
			}
		});
	}

	/**
	 * A melee class at the top (docs/ARBOLES.md), as Analysis.mago does it for the Mago: its base and every node of its
	 * tree but the keystones, added up as ClassEffects adds them, on the blow and the stamina bar.
	 */
	public static Fight.Options classOptions(Fight.Options base, PlayerClass clazz) {
		Map<ClassStat, Double> sum = sum(clazz);
		Fight.Options options = base.copy();
		options.meleeDamage = Math.max(0.1, 1.0 + sum.getOrDefault(ClassStat.MELEE_DAMAGE, 0.0));
		options.staminaMax = Math.max(0.1, 1.0 + sum.getOrDefault(ClassStat.STAMINA_MAX, 0.0));
		options.staminaRegen = Math.max(0.1, 1.0 + sum.getOrDefault(ClassStat.STAMINA_REGEN, 0.0));
		options.staminaCost = Math.max(0.1, 1.0 + sum.getOrDefault(ClassStat.STAMINA_COST, 0.0));
		return options;
	}

	private static Map<ClassStat, Double> sum(PlayerClass clazz) {
		Map<ClassStat, Double> sum = new EnumMap<>(ClassStat.class);
		for (ClassStat.Mod mod : clazz.base) {
			sum.merge(mod.stat(), (double) mod.value(), Double::sum);
		}
		for (ClassTree.Node node : clazz.tree().nodes) {
			if (node.kind == ClassTree.Kind.CLAVE) {
				continue;
			}
			for (ClassStat.Mod mod : node.mods) {
				sum.merge(mod.stat(), (double) mod.value(), Double::sum);
			}
		}
		return sum;
	}

	// ---------------------------------------------------------------- one fight

	private Row fight(Ladder ladder, int players, Gear gear) {
		Row row = new Row();
		row.level = ladder;
		row.players = players;
		row.kit = gear.kit;
		row.gear = gear.score;
		row.playerHealth = gear.maxHealth;
		ForjaDifficulty preset = ladder.preset();
		FallenSmith smith = this.helper.spawn(ModEntities.HERRERO_CAIDO, new BlockPos(3, 2, 3));
		this.spawned.add(smith);
		smith.setNoAi(true);
		// Dressed as the fight dresses him (a spawned one comes bare), and his gear's attributes on at once: nothing ticks here.
		smith.dress(this.level);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack worn = smith.getItemBySlot(slot);
			worn.forEachModifier(slot, (attribute, modifier) -> {
				var instance = smith.getAttribute(attribute);
				if (instance != null) {
					instance.addOrUpdateTransientModifier(modifier);
				}
			});
		}
		smith.sizedByHand = true;
		smith.scaleFor(players, gear.score);
		row.health = smith.getMaxHealth();

		// What a blow of his kit takes off him in each stage, and stunned.
		Target[] stages = new Target[4];
		for (int stage = 1; stage <= 3; stage++) {
			smith.stageForProbe(stage);
			stages[stage - 1] = this.probe.measureEntity(smith, "forja:herrero_caido", "Herrero Caído", true);
		}
		smith.stageForProbe(2);
		smith.stunForProbe(true);
		stages[3] = this.probe.measureEntity(smith, "forja:herrero_caido", "Herrero Caído", true);
		smith.stunForProbe(false);

		Fight.Policy policy = this.policy(gear, stages[0]);
		Fight.Options above = gear.options.copy();
		above.finishers = false;
		Fight.Options below = gear.options.copy();
		Fight.Options last = gear.options.copy();
		if (gear.kit.clazz != null && !gear.kit.magic) {
			// Ejecución under its share of his health: most of the last stage.
			double execute = sum(gear.kit.clazz).getOrDefault(ClassStat.EXECUTE, 0.0);
			last.meleeDamage *= 1.0 + execute * 0.9;
		}
		row.dps[0] = this.dps(gear, stages[0], policy, above);
		Fight.Result opening = new Fight(gear.weapon, stages[0], above, this.level, this.analysis.source).run(policy, SIM_RUNS, SIM_TICKS, false);
		row.capped = opening.swings <= 0 ? 0.0 : opening.capped / opening.swings;
		double dps2Above = this.dps(gear, stages[1], policy, above);
		row.dps[1] = this.dps(gear, stages[1], policy, below);
		row.dps[2] = this.dps(gear, stages[2], policy, last);
		row.dps[3] = this.dps(gear, stages[3], policy, below);

		// How much of the time he leaves the player he is after to hit him, stage by stage.
		double[] free = new double[3];
		for (int stage = 1; stage <= 3; stage++) {
			smith.stageForProbe(stage);
			row.uptime[stage - 1] = this.uptime(smith, stage, gear.kit.magic, true);
			free[stage - 1] = this.uptime(smith, stage, gear.kit.magic, false);
		}
		double[] team = new double[3];
		for (int i = 0; i < 3; i++) {
			team[i] = row.uptime[i] + (players - 1) * free[i];
		}
		double health = row.health;
		double stun = Math.min(health / 6.0, row.dps[3] * players * FallenSmith.STUN_TICKS / 20.0);
		row.raw = (health / 3.0 / row.dps[0] + health / 6.0 / dps2Above + health / 6.0 / row.dps[1] + health / 3.0 / row.dps[2]) / players;
		row.hitting = health / 3.0 / (row.dps[0] * team[0])
			+ health / 6.0 / (dps2Above * team[1])
			+ (health / 6.0 - stun) / (row.dps[1] * team[1])
			+ health / 3.0 / (row.dps[2] * team[2]);

		// The pauses: getting up, the two calls (he cannot be hurt), the stun's own five seconds.
		row.pauses = FallenSmith.SETTLE_TICKS / 20.0 + 2.0 * FallenSmith.PHASE_GUARD / 20.0 + FallenSmith.STUN_TICKS / 20.0;
		// The Reforjado: an ember after another, shared between the players, and the last pour.
		int embers = FallenSmith.embersFor(players);
		row.reforge = embers * EMBER_SECONDS / players + StarFight.FLOW_TICKS / 20.0;

		// The apprentices of both waves and the keepers of the Reforjado, and the six anvils, killed with the same weapon.
		int wave = FallenSmith.apprenticesFor(players);
		double first = this.apprentices(gear, policy, wave, FallenSmith.WAVE_HEALTH[0], ladder);
		double second = this.apprentices(gear, policy, wave, FallenSmith.WAVE_HEALTH[1], ladder);
		double keepers = FallenSmith.grade(ladder).keepers() > 0
			? this.apprentices(gear, policy, FallenSmith.grade(ladder).keepers(), FallenSmith.WAVE_HEALTH[0], ladder) : 0.0;
		double anvils = FallenSmith.FINAL_ANVILS * this.anvil(gear, policy, ladder);
		row.adds = (first + second + keepers + anvils) / ADD_UPTIME / players;
		row.seconds = row.hitting + row.pauses + row.reforge + row.adds;

		this.survival(row, smith, gear, ladder);
		dev.forja.Forja.LOGGER.info(String.format(java.util.Locale.ROOT,
			"herrero: %s x%d %s vida %.0f equipo %.2f | dps %.1f/%.1f/%.1f/%.1f uptime %.2f/%.2f/%.2f | pegando %.0f pausas %.0f reforjado %.0f anadidos %.0f"
				+ " (1a %.1f 2a %.1f yunque %.1f) = %.0f s | descuidado %.0f/%.0f/%.0f atento %.0f/%.0f/%.0f peor %.2f %s vida jugador %.0f",
			ladder, players, gear.kit, row.health, gear.score, row.dps[0], row.dps[1], row.dps[2], row.dps[3], row.uptime[0], row.uptime[1],
			row.uptime[2], row.hitting, row.pauses, row.reforge, row.adds, first / wave, second / wave, anvils / FallenSmith.FINAL_ANVILS,
			row.seconds, row.careless[0], row.careless[1], row.careless[2], row.attentive[0], row.attentive[1], row.attentive[2], row.worst,
			row.worstMove + " / con presion " + String.format(java.util.Locale.ROOT, "%.2f", row.worstPressed) + " " + row.worstPressedMove,
			row.playerHealth));
		smith.discard();
		this.spawned.remove(smith);
		return row;
	}

	/** The rhythm that does most against him, chosen once per kit and level against the first stage. */
	private Fight.Policy policy(Gear gear, Target target) {
		Fight fight = new Fight(gear.weapon, target, gear.options, this.level, this.analysis.source);
		Fight.Policy best = null;
		double most = -1.0;
		for (Fight.Policy policy : Fight.policies(gear.weapon)) {
			Fight.Result result = fight.run(policy, 2, SIM_TICKS, false);
			if (result.damage > most) {
				most = result.damage;
				best = policy;
			}
		}
		return best;
	}

	/** Damage a second of one player against him as he stands in that stage, over a long fight. */
	private double dps(Gear gear, Target target, Fight.Policy policy, Fight.Options options) {
		Fight fight = new Fight(gear.weapon, target, options, this.level, this.analysis.source);
		return Math.max(1.0E-3, fight.run(policy, SIM_RUNS, SIM_TICKS, false).dps());
	}

	/**
	 * The share of the time a player is hitting him: what is left once each of his moves has been dodged as often as it
	 * comes back, with his waits as they are in that stage. The one he is after dodges everything that reaches them; the
	 * other one, only the shockwave (it runs out round him) and the sky.
	 */
	private double uptime(FallenSmith smith, int stage, boolean ranged, boolean targeted) {
		double loss = (ranged ? RANGED_LOSS : MELEE_LOSS) + SKY_LOSS;
		loss += WAVE_DODGE / period(smith, FallenSmith.WAVE_COOLDOWN, FallenSmith.WAVE_WINDUP);
		if (targeted) {
			if (!ranged) {
				loss += STRIKE_DODGE / period(smith, FallenSmith.STRIKE_COOLDOWN, FallenSmith.STRIKE_WINDUP);
			} else if (stage >= 2) {
				loss += HOOK_DODGE / period(smith, FallenSmith.HOOK_COOLDOWN, FallenSmith.HOOK_WINDUP);
			}
			if (stage >= 3) {
				loss += STAR_DODGE / period(smith, FallenSmith.STARFALL_EVERY, FallenSmith.STARFALL_WINDUP);
			}
		}
		return Math.max(LEAST_UPTIME, 1.0 - loss);
	}

	/** Seconds between two of one of his moves: its wait as he has it now, and its wind-up. */
	private static double period(FallenSmith smith, int cooldown, int windup) {
		return (smith.cooldown(cooldown) + windup) / 20.0;
	}

	/** Seconds to kill a wave of apprentices one after another: two in three come up as wither skeletons, one in three with a bow. */
	private double apprentices(Gear gear, Fight.Policy policy, int count, double sturdier, Ladder ladder) {
		double melee = this.apprentice(gear, policy, false, sturdier, ladder);
		double archer = this.apprentice(gear, policy, true, sturdier, ladder);
		double total = 0.0;
		for (int i = 0; i < count; i++) {
			total += ApprenticeKits.archerAt(i) ? archer : melee;
		}
		return total;
	}

	private final Map<String, Double> apprenticeCache = new java.util.HashMap<>();

	/** One apprentice, made as the fight makes it (an elite, its kit, the wave's health, the level's and the gear's), killed. */
	private double apprentice(Gear gear, Fight.Policy policy, boolean archer, double sturdier, Ladder ladder) {
		String key = gear.kit + "|" + archer + "|" + sturdier + "|" + ladder;
		Double known = this.apprenticeCache.get(key);
		if (known != null) {
			return known;
		}
		ApprenticeKits.Role role = archer ? ApprenticeKits.ARCHER : ApprenticeKits.MELEE.getFirst();
		Mob mob = this.helper.spawn(role.body(), new BlockPos(1, 2, 5));
		this.spawned.add(mob);
		mob.setNoAi(true);
		RandomSource random = RandomSource.create(17);
		Elites.makeElite(mob, random);
		ApprenticeKits.equip(mob, role, archer ? 2 : 0, 5, random);
		if (sturdier > 0.0) {
			mob.getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(new AttributeModifier(dev.forja.Forja.id("oleada"), sturdier,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		this.sizedLikeScaling(mob, ladder, gear.score);
		Target target = this.probe.measureEntity(mob, "aprendiz", "Aprendiz", true);
		double seconds = this.ttk(gear, policy, target);
		mob.discard();
		this.spawned.remove(mob);
		this.apprenticeCache.put(key, seconds);
		return seconds;
	}

	/** A walking anvil of his last stage, sized as Scaling sizes it, killed. */
	private double anvil(Gear gear, Fight.Policy policy, Ladder ladder) {
		String key = gear.kit + "|yunque|" + ladder;
		Double known = this.apprenticeCache.get(key);
		if (known != null) {
			return known;
		}
		Mob mob = this.helper.spawn(ModEntities.YUNQUE_ANDANTE, new BlockPos(5, 2, 1));
		this.spawned.add(mob);
		mob.setNoAi(true);
		this.sizedLikeScaling(mob, ladder, gear.score);
		Target target = this.probe.measureEntity(mob, "forja:yunque_andante", "Yunque andante", true);
		double seconds = this.ttk(gear, policy, target);
		mob.discard();
		this.spawned.remove(mob);
		this.apprenticeCache.put(key, seconds);
		return seconds;
	}

	/** What Scaling.sizeUp puts on a mob that comes up near a player of this gear on this level, put on again by hand. */
	private void sizedLikeScaling(Mob mob, Ladder ladder, double score) {
		var id = dev.forja.Forja.id("dificultad");
		int tier = ladder.gear ? GearScore.tier(score) : 0;
		double health = ladder.preset().health * dev.forja.difficulty.Threat.of(mob).health * (1.0 + CombatConfig.get().gearHealthPerTier * tier);
		double armor = dev.forja.difficulty.Threat.of(mob).armor + CombatConfig.get().gearArmorPerTier * tier;
		var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
		maxHealth.removeModifier(id);
		maxHealth.addPermanentModifier(new AttributeModifier(id, health - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		var plates = mob.getAttribute(Attributes.ARMOR);
		plates.removeModifier(id);
		if (armor > 0.0) {
			plates.addPermanentModifier(new AttributeModifier(id, armor, AttributeModifier.Operation.ADD_VALUE));
		}
		mob.setHealth(mob.getMaxHealth());
	}

	/** Seconds to kill it, with this kit, its rhythm against him. */
	private double ttk(Gear gear, Fight.Policy policy, Target target) {
		Fight fight = new Fight(gear.weapon, target, gear.options, this.level, this.analysis.source);
		Fight.Result result = fight.run(policy, SIM_RUNS, SIM_TICKS, true);
		return result.killedShare < 0.999 ? SIM_TICKS / 20.0 : result.seconds();
	}

	// ---------------------------------------------------------------- what he does to a player

	/**
	 * His blows on the kit's player, through the real pipeline, fresh and under a long fight's pressure; and from them
	 * how long a careless player (in front of him, eating everything that comes) and an attentive one (dodging what is
	 * warned, half of his plain blows) last in each stage. No regeneration, no potions, no healing.
	 */
	private void survival(Row row, FallenSmith smith, Gear gear, Ladder ladder) {
		Mob apprentice = this.helper.spawn(ApprenticeKits.MELEE.getFirst().body(), new BlockPos(1, 2, 5));
		this.spawned.add(apprentice);
		apprentice.setNoAi(true);
		RandomSource random = RandomSource.create(23);
		Elites.makeElite(apprentice, random);
		ApprenticeKits.equip(apprentice, ApprenticeKits.MELEE.getFirst(), 0, 5, random);
		this.sizedLikeScaling(apprentice, ladder, gear.score);
		for (int stage = 1; stage <= 3; stage++) {
			smith.stageForProbe(stage);
			String[] names = {"golpe", "revés", "onda", "garfio", "estrellas"};
			double[] fresh = new double[names.length];
			double[] pressed = new double[names.length];
			for (int pressure = 0; pressure < 2; pressure++) {
				double[] into = pressure == 0 ? fresh : pressed;
				into[0] = this.taken(gear, pressure == 1, player -> smith.doHurtTarget(this.level, player));
				into[1] = this.taken(gear, pressure == 1, player -> player.hurtServer(this.level, smith.damageSources().mobAttack(smith),
					smith.moveDamage(FallenSmith.STRIKE_DAMAGE)));
				into[2] = this.taken(gear, pressure == 1, player -> player.hurtServer(this.level, smith.damageSources().mobAttack(smith),
					smith.moveDamage(FallenSmith.WAVE_DAMAGE)));
				into[3] = stage >= 2 ? this.taken(gear, pressure == 1, player -> player.hurtServer(this.level, smith.damageSources().mobAttack(smith),
					smith.moveDamage(FallenSmith.HOOK_DAMAGE))) : 0.0;
				into[4] = stage >= 3 ? this.taken(gear, pressure == 1, player -> player.hurtServer(this.level, this.level.damageSources().magic(),
					smith.moveDamage(FallenSmith.STARFALL_DAMAGE))) : 0.0;
			}
			for (int i = 0; i < names.length; i++) {
				if (fresh[i] / gear.maxHealth > row.worst) {
					row.worst = fresh[i] / gear.maxHealth;
					row.worstMove = names[i] + ", fase " + stage;
				}
				if (pressed[i] / gear.maxHealth > row.worstPressed) {
					row.worstPressed = pressed[i] / gear.maxHealth;
					row.worstPressedMove = names[i] + ", fase " + stage;
				}
			}
			double apprenticeBlow = stage >= 2 ? this.taken(gear, true, player -> apprentice.doHurtTarget(this.level, player)) : 0.0;
			double plain = pressed[0] / PLAIN_EVERY;
			double strike = pressed[1] / period(smith, FallenSmith.STRIKE_COOLDOWN, FallenSmith.STRIKE_WINDUP);
			double wave = pressed[2] / period(smith, FallenSmith.WAVE_COOLDOWN, FallenSmith.WAVE_WINDUP);
			double hook = pressed[3] / period(smith, FallenSmith.HOOK_COOLDOWN, FallenSmith.HOOK_WINDUP);
			double stars = pressed[4] / period(smith, FallenSmith.STARFALL_EVERY, FallenSmith.STARFALL_WINDUP);
			double helpers = APPRENTICES_ON_YOU * apprenticeBlow / APPRENTICE_EVERY;
			row.careless[stage - 1] = gear.maxHealth / Math.max(1.0E-6, plain + strike + wave + hook + stars + helpers);
			double attentive = fresh[0] * ATTENTIVE_PLAIN / PLAIN_EVERY + (gear.kit.magic ? 0.0 : fresh[3]
				/ period(smith, FallenSmith.HOOK_COOLDOWN, FallenSmith.HOOK_WINDUP));
			row.attentive[stage - 1] = attentive <= 1.0E-9 ? Double.POSITIVE_INFINITY : gear.maxHealth / attentive;
		}
		apprentice.discard();
		this.spawned.remove(apprentice);
	}

	/** Health one blow takes off the kit's player, fresh or under full pressure; healed back after. */
	private double taken(Gear gear, boolean pressed, java.util.function.Consumer<CombatGameTests.TestPlayer> blow) {
		CombatGameTests.TestPlayer player = gear.player;
		dev.forja.combat.Stamina.forget(player);
		dev.forja.difficulty.Pressure.forget(player);
		if (pressed) {
			for (int i = 0; i < 20; i++) {
				dev.forja.difficulty.Pressure.onHit(player);
			}
		}
		// Every blow on a fresh set: armour that wears down blow after blow (ArmorMath.durabilityFactor) is another fight.
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			player.getItemBySlot(slot).setDamageValue(0);
		}
		player.setHealth(player.getMaxHealth());
		player.setAbsorptionAmount(0.0F);
		player.invulnerableTime = 0;
		player.removeAllEffects();
		player.clearFire();
		float before = player.getHealth();
		blow.accept(player);
		float lost = before - player.getHealth();
		player.setHealth(player.getMaxHealth());
		player.invulnerableTime = 0;
		player.clearFire();
		player.setDeltaMovement(Vec3.ZERO);
		player.setPos(this.helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
		return Math.max(0.0, lost);
	}
}
