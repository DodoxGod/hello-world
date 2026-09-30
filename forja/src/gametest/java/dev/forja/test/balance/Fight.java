package dev.forja.test.balance;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import dev.forja.combat.CombatConfig;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.upgrade.CombatUpgrades;
import dev.forja.upgrade.Frenzy;
import dev.forja.upgrade.Synergy;
import dev.forja.upgrade.TraitEffects;
import dev.forja.upgrade.Upgrade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

/**
 * A fight, tick by tick, between one player holding one {@link Build} and one {@link Target} standing
 * still. Everything a blow is worth comes from the mod: the per-point factors are measured
 * ({@link Probe}), the multipliers and timings are CombatConfig's, the upgrade numbers are Upgrade's own
 * helpers and Frenzy's ceilings, the softening of stacked procs is CombatUpgrades.softened.
 *
 * <p>What this class writes down itself is the <b>order and timing</b> the mod follows, so they can be
 * run twenty times a second for a minute: Player#attack's strength curve, vanilla's ten ticks of
 * invulnerability after a hit, Stamina (cost, the delay before it comes back, the tired blow), Combos,
 * ChargedStrike, CombatHooks.afterArmor (stagger, finisher, cap), Posture's bar with its repeats, the
 * order of CombatUpgrades.onWeaponHit, and the effects that keep hurting (fire, poison, bleeding, wither)
 * at vanilla's rates. {@code BalanceGameTests} checks this order against the real handlers.
 *
 * <p>Random procs are rolled, not averaged: {@link #run} plays the same fight {@code runs} times with a
 * fixed series of seeds, so two builds compared are rolled with the same dice.
 */
public final class Fight {
	public enum Charge {
		/** Never holds the button. */
		NONE,
		/** Holds it only to finish a staggered foe. */
		FINISHER,
		/** Holds it after every swing. */
		ALWAYS
	}

	/** What the player does about breath. */
	public enum Breath {
		/** Swings on, tired or not. */
		SPAM,
		/** Only swings with the stamina for the blow. */
		WAIT,
		/** Swings until out of breath, then stops until the bar is full again. */
		REST
	}

	/** How the player swings: every so many ticks, holding for a charged blow or not, and what it does about breath. */
	public record Policy(int interval, Charge charge, Breath breath) {
		public boolean pace() {
			return this.breath != Breath.SPAM;
		}

		public String describe() {
			String charge = switch (this.charge) {
				case NONE -> "";
				case FINISHER -> ", carga para rematar";
				case ALWAYS -> ", carga siempre";
			};
			String breath = switch (this.breath) {
				case SPAM -> "";
				case WAIT -> ", esperando estamina";
				case REST -> ", descansando al vaciarse";
			};
			return "cada " + this.interval + " ticks" + charge + breath;
		}
	}

	/** Switches for the outlier measurements: each one turns one rule of the mod off to see what it is worth. */
	public static final class Options {
		public boolean stamina = true;
		/** The mana bar (magic/Mana): off, a staff or a tome casts every time its wait is over, as it did before the bar. */
		public boolean mana = true;
		/**
		 * How much faster than bare the bar fills, and how much deeper it is (magic/Mana.regenFactor, maxOf): 1 and 1
		 * for a player without a magic class, the Mago's own numbers for a Mago (BalanceGameTests.mago).
		 */
		public double manaRegen = 1.0;
		public double manaMax = 1.0;
		/**
		 * The class on the spells themselves (clase/ClassEffects): what they hit for, how long the wait after them is
		 * and what they cost, as multipliers. 1 for a player without a magic class.
		 */
		public double spellDamage = 1.0;
		public double spellCooldown = 1.0;
		public double spellCost = 1.0;
		public boolean iframes = true;
		public boolean cap = true;
		public boolean posture = true;
		public boolean combos = true;
		public boolean frenzy = true;
		/** Armor weight of the player, 0..1: slows stamina coming back. */
		public double armorWeight = 0.0;
		/** The foundry alloys: under open sky at noon, in the dark, on fire. */
		public boolean sun;
		public boolean dark;
		public boolean burning;
		/** Every blow a jump crit (vanilla x1.5), or aimed at the head (CombatConfig.headMultiplier). */
		public boolean jumpCrits;
		public boolean headshots;

		public Options copy() {
			Options copy = new Options();
			copy.stamina = this.stamina;
			copy.mana = this.mana;
			copy.manaRegen = this.manaRegen;
			copy.manaMax = this.manaMax;
			copy.spellDamage = this.spellDamage;
			copy.spellCooldown = this.spellCooldown;
			copy.spellCost = this.spellCost;
			copy.iframes = this.iframes;
			copy.cap = this.cap;
			copy.posture = this.posture;
			copy.combos = this.combos;
			copy.frenzy = this.frenzy;
			copy.armorWeight = this.armorWeight;
			copy.sun = this.sun;
			copy.dark = this.dark;
			copy.burning = this.burning;
			copy.jumpCrits = this.jumpCrits;
			copy.headshots = this.headshots;
			return copy;
		}
	}

	/** What a fight came to, averaged over its runs. */
	public static final class Result {
		public double damage;
		public double ticks;
		public double killedShare;
		public double swings;
		public double tired;
		public double capped;
		public double swallowed;
		public double staggers;
		public double finishers;
		public double charged;
		/** Main blows that landed inside the ten ticks of invulnerability, and so only took their excess. */
		public double iframed;
		/** Health taken by the main blows and by everything the upgrades added on top. */
		public double mainDamage;
		public double extraDamage;

		public double dps() {
			return this.ticks <= 0 ? 0.0 : this.damage / (this.ticks / 20.0);
		}

		public double seconds() {
			return this.ticks / 20.0;
		}
	}

	private enum Source {
		MELEE, LIGHTNING, MAGIC, FIRE, WITHER, BOLT, AREA
	}

	/** One blow as afterArmor sees it: its multipliers, and whether the player struck it with their own hand. */
	private record Blow(double damage, double posture, boolean direct, boolean charged) {
		static final Blow NONE = new Blow(1.0, 1.0, false, false);
	}

	private final Build build;
	private final Target target;
	private final Options options;
	private final ServerLevel level;
	private final DamageSource probeSource;
	private final CombatConfig cfg = CombatConfig.get();

	// ---------------------------------------------------------------- state of one run
	private SplittableRandom rng;
	private int t;
	private double health;
	private boolean endless;
	private double dealt;
	private int invulnerable;
	private double lastHurt;
	private double posture;
	private int postureLastHit;
	private int staggerUntil;
	private int staggers;
	private int lastStagger;
	private int lastFinisher;
	private int feignLeft;
	private boolean feigned;
	private int fireTicks;
	private int poisonTicks;
	private int witherTicks;
	private int bleedTicks;
	private int bleedStacks;
	private double stamina;
	private int lastSpend;
	private int comboStep;
	private int comboLast;
	private boolean comboFinishing;
	private int frenzyHits;
	private int frenzyExpires;
	private int procTick;
	private double procs;
	private int casts;
	/** The mana bar, as magic/Mana keeps it: what there is, and when the last spell was paid for. */
	private double mana;
	private int lastCast;
	private final List<double[]> runes = new ArrayList<>();
	private final List<double[]> pending = new ArrayList<>();
	// counters
	private int swings;
	private int tiredSwings;
	private int cappedBlows;
	private int swallowedBlows;
	private int staggerCount;
	private int finisherCount;
	private int chargedCount;
	private int iframedBlows;
	private double mainDealt;
	private double extraDealt;

	public Fight(Build build, Target target, Options options, ServerLevel level, DamageSource probeSource) {
		this.build = build;
		this.target = target;
		this.options = options;
		this.level = level;
		this.probeSource = probeSource;
	}

	/** Plays the fight {@code runs} times and averages it: to the kill, or for {@code maxTicks} against endless health. */
	public Result run(Policy policy, int runs, int maxTicks, boolean toTheDeath) {
		Result sum = new Result();
		for (int run = 0; run < runs; run++) {
			this.reset(0x5EEDL * 31 + run * 0x9E3779B97F4A7C15L, !toTheDeath);
			boolean killed = this.play(policy, maxTicks);
			sum.damage += this.dealt;
			sum.ticks += this.t;
			sum.killedShare += killed ? 1 : 0;
			sum.swings += this.swings;
			sum.tired += this.tiredSwings;
			sum.capped += this.cappedBlows;
			sum.swallowed += this.swallowedBlows;
			sum.staggers += this.staggerCount;
			sum.finishers += this.finisherCount;
			sum.charged += this.chargedCount;
			sum.iframed += this.iframedBlows;
			sum.mainDamage += this.mainDealt;
			sum.extraDamage += this.extraDealt;
		}
		sum.damage /= runs;
		sum.ticks /= runs;
		sum.killedShare /= runs;
		sum.swings /= runs;
		sum.tired /= runs;
		sum.capped /= runs;
		sum.swallowed /= runs;
		sum.staggers /= runs;
		sum.finishers /= runs;
		sum.charged /= runs;
		sum.iframed /= runs;
		sum.mainDamage /= runs;
		sum.extraDamage /= runs;
		return sum;
	}

	/**
	 * One CombatUpgrades.onWeaponHit, alone: the foe at {@code health}, {@code frenzyBefore} frenzy hits already
	 * behind the blow, a main blow that took {@code taken}. What the extras take off it. This is what the
	 * balance test holds against the real handler.
	 */
	public double extrasOnce(double taken, double health, int frenzyBefore, long seed) {
		this.reset(seed, false);
		this.health = health;
		this.frenzyHits = frenzyBefore;
		this.frenzyExpires = frenzyBefore > 0 ? Frenzy.WINDOW_TICKS : -1;
		this.onWeaponHit(taken, 1.0, 1.0);
		return this.dealt;
	}

	/** Every rhythm worth trying with this weapon. */
	public static List<Policy> policies(Build build) {
		List<Policy> policies = new ArrayList<>();
		if (dev.forja.magic.Spellcasting.casts(build.type)) {
			// Two ways with a bar of mana: cast whenever a tap is paid for, or burst it empty and rest till it is full.
			policies.add(new Policy(build.spellCooldown, Charge.NONE, Breath.SPAM));
			policies.add(new Policy(build.spellCooldown, Charge.NONE, Breath.REST));
			return policies;
		}
		double delay = 20.0 / Math.max(0.05, build.attackSpeed);
		List<Integer> intervals = new ArrayList<>();
		// The quickest blow that still chains a combo, a full-strength one, the edge of vanilla's ten ticks of
		// invulnerability, and the pause after which stamina starts coming back.
		for (int interval : new int[] {
			(int) Math.ceil(delay * dev.forja.combat.Combos.MIN_STRENGTH - 0.5), (int) Math.ceil(delay - 0.5), 10, 20
		}) {
			int clamped = Math.max(1, Math.min(60, interval));
			if (!intervals.contains(clamped) && clamped >= (int) Math.ceil(delay * 0.5)) {
				intervals.add(clamped);
			}
		}
		List<Charge> charges = build.charges ? List.of(Charge.NONE, Charge.FINISHER, Charge.ALWAYS) : List.of(Charge.NONE);
		// Every way of holding the button while swinging on regardless, and every way of minding the breath
		// without charging or charging only to finish.
		for (int interval : intervals) {
			for (Charge charge : charges) {
				policies.add(new Policy(interval, charge, Breath.SPAM));
			}
			for (Breath breath : List.of(Breath.WAIT, Breath.REST)) {
				policies.add(new Policy(interval, Charge.NONE, breath));
				if (build.charges) {
					policies.add(new Policy(interval, Charge.FINISHER, breath));
				}
			}
		}
		return policies;
	}

	// ---------------------------------------------------------------- the fight

	private void reset(long seed, boolean endless) {
		this.rng = new SplittableRandom(seed);
		this.t = 0;
		this.health = this.target.maxHealth;
		this.endless = endless;
		this.dealt = 0;
		this.invulnerable = 0;
		this.lastHurt = 0;
		this.posture = 0;
		this.postureLastHit = -100000;
		this.staggerUntil = -1;
		this.staggers = 0;
		this.lastStagger = -100000;
		this.lastFinisher = -100000;
		this.feignLeft = 0;
		this.feigned = false;
		this.fireTicks = 0;
		this.poisonTicks = 0;
		this.witherTicks = 0;
		this.bleedTicks = 0;
		this.bleedStacks = 0;
		this.stamina = this.cfg.staminaMax;
		this.lastSpend = -100000;
		this.comboStep = 0;
		this.comboLast = -100000;
		this.comboFinishing = false;
		this.frenzyHits = 0;
		this.frenzyExpires = -1;
		this.procTick = -1;
		this.procs = 0;
		this.casts = 0;
		this.mana = this.manaMax();
		this.lastCast = -100000;
		this.runes.clear();
		this.pending.clear();
		this.swings = 0;
		this.tiredSwings = 0;
		this.cappedBlows = 0;
		this.swallowedBlows = 0;
		this.staggerCount = 0;
		this.finisherCount = 0;
		this.chargedCount = 0;
		this.iframedBlows = 0;
		this.mainDealt = 0;
		this.extraDealt = 0;
	}

	private boolean dead() {
		return !this.endless && this.health <= 0.0;
	}

	private boolean play(Policy policy, int maxTicks) {
		boolean magic = dev.forja.magic.Spellcasting.casts(this.build.type);
		double delay = 20.0 / Math.max(0.05, this.build.attackSpeed);
		int ticker = 1000;
		int chargeAt = -1;
		boolean resting = false;
		int nextCast = 0;
		for (this.t = 0; this.t < maxTicks; this.t++) {
			this.tickTarget();
			if (this.dead()) {
				return true;
			}
			this.tickStamina();
			if (magic) {
				this.tickMana();
				if (this.t >= nextCast && !(resting = this.manaless(policy, resting))) {
					this.payMana();
					this.cast();
					nextCast = this.t + this.spellWait();
				}
				this.tickRunes();
			} else if (chargeAt >= 0) {
				if (this.t >= chargeAt) {
					this.chargedStrike(ticker, delay);
					ticker = 0;
					chargeAt = -1;
				}
			} else if (ticker >= policy.interval() && !(resting = this.breathless(policy, resting))) {
				this.swing(ticker, delay);
				ticker = 0;
				boolean wantCharge = switch (policy.charge()) {
					case NONE -> false;
					case ALWAYS -> true;
					case FINISHER -> this.t < this.staggerUntil && this.finisherReady();
				};
				if (wantCharge && (policy.breath() == Breath.SPAM || !this.options.stamina
					|| this.stamina >= this.chargeCost())) {
					chargeAt = this.t + this.cfg.chargeDelayTicks + this.cfg.chargeFullTicks;
				}
			}
			ticker++;
			if (this.dead()) {
				return true;
			}
		}
		return false;
	}

	/** Whether the player holds back this tick for want of breath, and whether it is resting to a full bar. */
	private boolean breathless(Policy policy, boolean resting) {
		if (!this.options.stamina) {
			return false;
		}
		return switch (policy.breath()) {
			case SPAM -> false;
			case WAIT -> this.stamina < this.swingCost();
			case REST -> resting ? this.stamina < this.cfg.staminaMax : this.stamina < this.swingCost();
		};
	}

	/** Stamina a plain swing costs with this weapon: the config's, moved by its handle (combat/Grip). */
	private double swingCost() {
		return this.cfg.attackCost * this.build.swingCost;
	}

	/** And a charged blow at full charge. */
	private double chargeCost() {
		return (this.cfg.chargeStaminaCost + this.cfg.chargeStaminaPerShare) * this.build.chargeCost;
	}

	/** Vanilla's per-tick work on the target: invulnerability running down, and whatever keeps hurting it. */
	private void tickTarget() {
		if (this.invulnerable > 0) {
			this.invulnerable--;
		}
		if (this.feignLeft > 0) {
			this.feignLeft--;
		}
		if (this.fireTicks > 0) {
			if (this.fireTicks % 20 == 0) {
				this.hurt(1.0, Source.FIRE, Blow.NONE, false);
			}
			this.fireTicks--;
		}
		if (this.poisonTicks > 0) {
			// Poison: a point every 25 ticks at level I, and never the last one.
			if (this.poisonTicks % 25 == 0 && this.health > 1.0) {
				this.hurt(1.0, Source.MAGIC, Blow.NONE, false);
			}
			this.poisonTicks--;
		}
		if (this.witherTicks > 0) {
			// Wither II: a point every 20 ticks.
			if (this.witherTicks % 20 == 0) {
				this.hurt(1.0, Source.WITHER, Blow.NONE, false);
			}
			this.witherTicks--;
		}
		if (this.bleedTicks > 0) {
			// Sangrado (ModEffects.Bleeding): 1 + half a point per stack past the first, every 20 ticks, through any cooldown.
			if (this.bleedTicks % 20 == 0) {
				this.hurt(1.0 + 0.5 * (this.bleedStacks - 1), Source.MAGIC, Blow.NONE, true);
			}
			this.bleedTicks--;
			if (this.bleedTicks == 0) {
				this.bleedStacks = 0;
			}
		}
	}

	private void tickStamina() {
		if (!this.options.stamina) {
			this.stamina = this.cfg.staminaMax;
			return;
		}
		if (this.t - this.lastSpend >= this.cfg.staminaRegenDelayTicks && this.stamina < this.cfg.staminaMax) {
			double penalty = Math.min(0.9, Math.max(0.0, this.options.armorWeight) * this.cfg.regenPenaltyPerWeight);
			this.stamina = Math.min(this.cfg.staminaMax, this.stamina + this.cfg.staminaRegenPerTick * (1.0 - penalty));
		}
	}

	/** What a tap of this build's staff or tome costs (magic/Spellcasting.tapCost), with the class's price. */
	private double tapCost() {
		return dev.forja.magic.Spellcasting.tapCost(this.build.stack, this.build.type) * this.options.spellCost;
	}

	/** The wait after a spell, with the class's (Spellcasting.release: never under 2 ticks). */
	private int spellWait() {
		if (this.options.spellCooldown == 1.0) {
			return Math.max(1, this.build.spellCooldown);
		}
		return Math.max(2, (int) Math.round(this.build.spellCooldown * this.options.spellCooldown));
	}

	/** The bar's size: the base, times the class's (Options.manaMax). */
	private double manaMax() {
		return this.cfg.manaMax * this.options.manaMax;
	}

	/** magic/Mana's regeneration: slow while spells keep coming, quicker once they have stopped, times the class. */
	private void tickMana() {
		if (!this.options.mana) {
			this.mana = this.manaMax();
			return;
		}
		double rate = this.t - this.lastCast >= this.cfg.manaIdleDelayTicks ? this.cfg.manaIdleRegenPerTick : this.cfg.manaRegenPerTick;
		this.mana = Math.min(this.manaMax(), this.mana + rate * this.options.manaRegen);
	}

	/** Whether the caster holds back for want of mana: SPAM casts whenever a tap is paid for, REST empties the bar and waits for it full. */
	private boolean manaless(Policy policy, boolean resting) {
		if (!this.options.mana) {
			return false;
		}
		double tap = this.tapCost();
		return switch (policy.breath()) {
			case SPAM, WAIT -> this.mana < tap;
			case REST -> resting ? this.mana < this.manaMax() : this.mana < tap;
		};
	}

	private void payMana() {
		if (this.options.mana) {
			this.mana -= this.tapCost();
			this.lastCast = this.t;
		}
	}

	private boolean spend(double amount) {
		if (!this.options.stamina) {
			return true;
		}
		this.lastSpend = this.t;
		if (this.stamina >= amount) {
			this.stamina -= amount;
			return true;
		}
		this.stamina = 0;
		return false;
	}

	/** Player#attack's damage: the attribute scaled by 0.2 + 0.8 s², enchantments by s. */
	private double raw(int ticker, double delay) {
		double strength = Math.min(1.0, (ticker + 0.5) / delay);
		double base = this.build.attackDamage * (0.2 + 0.8 * strength * strength);
		double enchant = this.build.enchantBonus(this.target, this.level, this.probeSource, this.build.attackDamage) * strength;
		double raw = base + enchant;
		if (this.options.jumpCrits && strength > 0.9) {
			raw = base * 1.5 + enchant;
		}
		return raw;
	}

	private void swing(int ticker, double delay) {
		this.swings++;
		double strength = Math.min(1.0, (ticker + 0.5) / delay);
		boolean tired = !this.spend(this.swingCost());
		if (tired) {
			this.tiredSwings++;
		}
		// Combos.onAttack: blows at nearly full strength chain, the third is heavier.
		if (this.options.combos) {
			if (strength >= dev.forja.combat.Combos.MIN_STRENGTH) {
				this.comboStep = this.t - this.comboLast <= this.cfg.comboWindowTicks ? this.comboStep + 1 : 1;
				this.comboLast = this.t;
				if (this.comboStep >= 3) {
					this.comboFinishing = true;
					this.comboStep = 0;
				}
			} else {
				this.comboStep = 0;
			}
		}
		double damage = tired ? this.cfg.tiredDamageMultiplier : 1.0;
		double posture = this.build.posture;
		if (this.comboFinishing) {
			damage *= this.cfg.comboFinisherDamage;
			posture *= this.cfg.comboFinisherPosture;
			this.comboFinishing = false;
		}
		double raw = this.raw(ticker, delay);
		double taken = this.hurt(raw, Source.MELEE, new Blow(damage, posture, true, false), false);
		if (taken > 0) {
			this.afterMainHit(taken, false, 1.0, 1.0);
		}
	}

	private void chargedStrike(int ticker, double delay) {
		this.chargedCount++;
		boolean paid = this.spend(this.chargeCost());
		double damage = paid ? (1.0 + this.cfg.chargeDamageBonus) * this.build.chargeDamage : this.cfg.tiredDamageMultiplier;
		double posture = (paid ? 1.0 + this.cfg.chargePostureBonus : 1.0) * this.build.posture;
		double comboDamage = 1.0;
		double comboPosture = 1.0;
		if (this.comboFinishing) {
			comboDamage = this.cfg.comboFinisherDamage;
			comboPosture = this.cfg.comboFinisherPosture;
			this.comboFinishing = false;
		}
		double raw = this.raw(ticker, delay);
		double taken = this.hurt(raw, Source.MELEE, new Blow(damage * comboDamage, posture * comboPosture, true, true), false);
		if (taken > 0) {
			this.afterMainHit(taken, true, damage, posture);
		}
	}

	private void afterMainHit(double taken, boolean charged, double chargeDamage, double chargePosture) {
		// Fire Aspect (the upgrade and Igneo): vanilla sets the target alight for four seconds a level.
		if (this.build.fireAspect > 0 && !this.target.fireImmune) {
			this.fireTicks = Math.max(this.fireTicks, 80 * this.build.fireAspect);
		}
		this.onWeaponHit(taken, charged ? chargeDamage : 1.0, charged ? chargePosture : 1.0);
	}

	// ---------------------------------------------------------------- magic

	private void cast() {
		this.casts++;
		float overcharge = this.build.fraction(Upgrade.SOBRECARGA);
		boolean big = overcharge > 0.0F && this.casts % Upgrade.OVERCHARGE_EVERY == 0;
		double power = (big ? 1.0 + Upgrade.overchargeBonus(overcharge) : 1.0) * this.options.spellDamage;
		double echo = Upgrade.echoShare(this.build.fraction(Upgrade.RESONANCIA));
		double opening = this.build.spellDamage * power;
		if (this.build.type == ForgeType.BACULO) {
			this.volley(opening);
			if (echo > 0) {
				this.pending.add(new double[] {this.t + dev.forja.magic.Spellcasting.ECHO_BOLT_TICKS, this.build.spellDamage * power * echo, 0});
			}
		} else {
			this.spellHit(opening, Source.AREA, true);
			// One rune per reader: the new one puts the last one out (Spellcasting.open).
			this.runes.clear();
			this.runes.add(new double[] {0, opening, this.build.runeTicks});
			if (echo > 0) {
				this.pending.add(new double[] {this.t + dev.forja.magic.Spellcasting.ECHO_AREA_TICKS, opening * echo, 1});
			}
		}
	}

	/**
	 * magic/Spellcasting.volley against one foe: the spell's damage shared out among the middle bolt and Prisma's
	 * side bolts, which reach a single foe only when Buscador bends them onto it (without it they fly past). The
	 * side bolts are not blows of the staff, so no upgrade answers them; every bolt goes through the invulnerability.
	 */
	private void volley(double opening) {
		double prism = Upgrade.prismShare(this.build.fraction(Upgrade.PRISMA));
		int sides = prism <= 0.0 ? 0 : this.build.synergy(Synergy.ENJAMBRE) ? 4 : 2;
		double whole = 1.0 + sides * prism;
		this.spellHit(opening / whole, Source.BOLT, true);
		if (sides == 0 || this.build.fraction(Upgrade.BUSCADOR) <= 0.0F) {
			return;
		}
		for (int side = 0; side < sides; side++) {
			this.spellHit(opening * prism / whole, Source.BOLT, false);
		}
	}

	private void tickRunes() {
		for (int i = this.pending.size() - 1; i >= 0; i--) {
			double[] echo = this.pending.get(i);
			if (this.t >= echo[0]) {
				this.pending.remove(i);
				this.spellHit(echo[1], echo[2] == 0 ? Source.BOLT : Source.AREA, true);
			}
		}
		boolean collapses = this.build.synergy(Synergy.COLAPSO);
		for (int i = this.runes.size() - 1; i >= 0; i--) {
			double[] rune = this.runes.get(i);
			rune[0]++;
			double opening = rune[1];
			if (rune[0] >= rune[2]) {
				this.runes.remove(i);
				if (collapses) {
					this.spellHit(opening * dev.forja.magic.Spellcasting.COLLAPSE_SHARE, Source.AREA, true);
				}
				continue;
			}
			if (((int) rune[0]) % dev.forja.magic.Spellcasting.RUNE_EVERY == 0) {
				// A bite of the rune: the reader's, but not a blow, so no upgrade answers it.
				this.spellHit(opening * dev.forja.magic.Spellcasting.BITE_SHARE, Source.AREA, false);
			}
		}
	}

	private void spellHit(double base, Source source, boolean blow) {
		if (this.dead()) {
			return;
		}
		double raw = base + this.build.enchantBonus(this.target, this.level, this.probeSource, base) * CombatUpgrades.SPELL_EXTRA_SHARE;
		this.swings += blow ? 1 : 0;
		double taken = this.hurt(raw, source, Blow.NONE, true);
		this.mainDealt += taken;
		if (taken > 0 && blow) {
			// CombatUpgrades: a spell's extras are worked out on a share of it.
			this.onWeaponHit(taken * CombatUpgrades.SPELL_EXTRA_SHARE, 1.0, 1.0);
		}
	}

	// ---------------------------------------------------------------- CombatUpgrades.onWeaponHit, in its order

	private float frenzied(Upgrade upgrade, double frenzyLevel) {
		float base = this.build.fraction(upgrade);
		if (base <= 0.0F || !this.options.frenzy) {
			return base;
		}
		return base * (1.0F + (upgrade.frenzyCeiling() - 1.0F) * (float) frenzyLevel);
	}

	private void frenzyHit() {
		if (!this.options.frenzy) {
			return;
		}
		this.frenzyHits = this.t <= this.frenzyExpires ? Math.min(Frenzy.MAX_HITS, this.frenzyHits + 1) : 1;
		this.frenzyExpires = this.t + Frenzy.WINDOW_TICKS;
	}

	private void onWeaponHit(double taken, double chargeDamage, double chargePosture) {
		this.frenzyHit();
		double frenzy = this.options.frenzy && this.t <= this.frenzyExpires ? this.frenzyHits / (double) Frenzy.MAX_HITS : 0.0;
		Blow extra = new Blow(chargeDamage, chargePosture, false, false);
		ForgeType type = this.build.type;

		int poison = Upgrade.poisonTicks(this.frenzied(Upgrade.VENENO, frenzy));
		if (poison > 0 && this.target.poisonable) {
			this.poisonTicks = Math.max(this.poisonTicks, poison);
		}
		if (this.rng.nextDouble() < Upgrade.stormChance(this.frenzied(Upgrade.TORMENTA, frenzy))) {
			this.extra(4.0, Source.LIGHTNING, extra);
			if (!this.build.synergy(Synergy.TORMENTA_HELADA) && !this.target.fireImmune) {
				this.fireTicks = Math.max(this.fireTicks, 40);
			}
		}
		boolean crit = this.rng.nextDouble() < Upgrade.critChance(this.frenzied(Upgrade.CRITICO, frenzy));
		if (crit) {
			this.extra(taken * 0.5, Source.MELEE, extra);
		}
		if (crit && (type == ForgeType.DAGA || type == ForgeType.GUADANA)) {
			int most = Upgrade.bleedStacks(this.frenzied(Upgrade.DESGARRO, frenzy));
			if (this.bleedStacks >= most) {
				if (this.target.witherable) {
					this.witherTicks = Math.max(this.witherTicks, 60);
				}
			} else {
				this.bleedStacks++;
				this.bleedTicks = 100;
			}
		}
		double execute = Upgrade.executeBonus(this.frenzied(Upgrade.EJECUCION, frenzy));
		if (execute > 0 && !this.dead() && this.health <= this.target.maxHealth * 0.3) {
			this.extra(taken * execute, Source.MELEE, extra);
		}
		double giant = Upgrade.giantBonus(this.frenzied(Upgrade.MATAGIGANTES, frenzy));
		if (giant > 0 && this.target.maxHealth > 20.0) {
			this.extra(taken * giant, Source.MELEE, extra);
		}
		if (type == ForgeType.GUANTELETES) {
			double knuckles = Upgrade.knuckleBonus(this.build.fraction(Upgrade.NUDILLOS_DE_HIERRO)) * frenzy;
			if (knuckles > 0) {
				this.extra(taken * knuckles, Source.MELEE, extra);
			}
			double flurry = Upgrade.flurryChance(this.build.fraction(Upgrade.RAFAGA));
			if (this.build.synergy(Synergy.CIEN_MANOS)) {
				flurry *= 2.0;
				this.frenzyHit();
			}
			if (this.rng.nextDouble() < flurry) {
				this.extra(taken, Source.MELEE, extra);
			}
		}
		if (this.rng.nextDouble() < Upgrade.starfallChance(this.frenzied(Upgrade.LLUVIA_ESTELAR, frenzy))) {
			this.extra(taken * 0.6, Source.MAGIC, extra);
		}
		double foundry = 0.0;
		if (this.options.burning && this.build.hasTrait(ForgeMaterial.Trait.ASCUA)) {
			foundry += TraitEffects.EMBER_DAMAGE;
		}
		if (this.options.sun && this.build.hasTrait(ForgeMaterial.Trait.SOLAR)) {
			foundry += this.target.undead ? TraitEffects.SUN_DAMAGE * 2.0F : TraitEffects.SUN_DAMAGE;
		}
		if (this.options.dark && this.build.hasTrait(ForgeMaterial.Trait.NOCTURNO)) {
			foundry += TraitEffects.MOON_DAMAGE;
		}
		if (foundry > 0) {
			this.extra(foundry, Source.MELEE, extra);
		}
		if (this.build.hasTrait(ForgeMaterial.Trait.AFILADO)) {
			// A new edge: sharpness 1.
			this.extra(3.0, Source.MELEE, extra);
		}
	}

	private void extra(double amount, Source source, Blow blow) {
		if (this.dead() || amount <= 0.0) {
			return;
		}
		if (this.procTick != this.t) {
			this.procTick = this.t;
			this.procs = 0.0;
		}
		float softened = CombatUpgrades.softened((float) amount, this.procs);
		this.procs += softened;
		this.hurt(softened, source, blow, true);
	}

	// ---------------------------------------------------------------- one hurt, from hurtServer through afterArmor

	private double hurt(double raw, Source source, Blow blow, boolean throughCooldown) {
		if (this.feignLeft > 0) {
			return 0.0;
		}
		boolean player = source == Source.MELEE || source == Source.BOLT || source == Source.AREA;
		// ForjaTraits.allowDamage: the hollow armor plays dead instead of going under its share, once.
		if (player && this.target.feignShare > 0 && !this.feigned && !this.endless
			&& this.health - raw < this.target.maxHealth * this.target.feignShare) {
			this.feigned = true;
			this.feignLeft = this.target.feignTicks;
			this.health = Math.max(1.0, this.target.maxHealth * this.target.feignShare);
			return 0.0;
		}
		double amount = raw;
		if (throughCooldown || !this.options.iframes) {
			this.lastHurt = raw;
			this.invulnerable = 20;
		} else if (this.invulnerable > 10) {
			if (raw <= this.lastHurt) {
				if (blow.direct()) {
					this.swallowedBlows++;
				}
				return 0.0;
			}
			amount = raw - this.lastHurt;
			this.lastHurt = raw;
			if (blow.direct()) {
				this.iframedBlows++;
			}
		} else {
			this.lastHurt = raw;
			this.invulnerable = 20;
		}
		boolean staggered = this.options.posture && this.t < this.staggerUntil;
		double multiplier = blow.damage();
		boolean finisher = false;
		if (staggered && blow.direct() && blow.charged() && this.finisherReady()) {
			multiplier *= this.cfg.finisherMultiplier;
			finisher = true;
			this.finisherCount++;
			this.lastFinisher = this.t;
			this.staggerUntil = -1;
			this.posture = 0;
		}
		if (this.options.headshots && blow.direct()) {
			multiplier *= this.cfg.headMultiplier;
		}
		double postureFactor = switch (source) {
			case MELEE -> this.target.posture.getOrDefault(this.build.type, 0.0);
			case BOLT -> this.target.boltPosture;
			case AREA -> this.target.areaPosture;
			default -> 0.0;
		};
		if (this.options.posture && postureFactor > 0) {
			this.addPosture(amount * multiplier * blow.posture() * postureFactor * (staggered ? this.cfg.staggerDamageMultiplier : 1.0));
		}
		double[] factors = switch (source) {
			case MELEE -> this.meleeFactors();
			case BOLT -> this.target.bolt;
			case AREA -> this.target.area;
			case LIGHTNING -> this.target.lightning;
			case MAGIC -> this.target.magic;
			case FIRE -> this.target.fire;
			case WITHER -> this.target.wither;
		};
		double lost = amount * multiplier * factors[staggered ? 1 : 0];
		if (player && this.options.cap && !(staggered || finisher) && lost > this.target.cap) {
			lost = this.target.cap;
			if (blow.direct()) {
				this.cappedBlows++;
			}
		}
		if (!this.endless) {
			lost = Math.min(lost, Math.max(0.0, this.health));
			this.health -= lost;
		}
		this.dealt += lost;
		if (blow.direct()) {
			this.mainDealt += lost;
		} else if (blow != Blow.NONE || source == Source.LIGHTNING) {
			this.extraDealt += lost;
		}
		return lost;
	}

	private double[] meleeFactors() {
		double[][] factors = this.target.melee.get(this.build.type);
		int breach = Math.min(Target.BREACH_LEVELS - 1, this.build.breach);
		return new double[] {factors[0][breach], factors[1][breach]};
	}

	private boolean finisherReady() {
		return this.t - this.lastFinisher >= this.cfg.finisherCooldownTicks
			&& (this.endless || this.health <= this.target.maxHealth * this.target.finishableBelow);
	}

	private int recentStaggers() {
		int fade = Math.max(1, this.cfg.staggerResistanceFadeTicks);
		return (int) Math.max(0, this.staggers - Math.max(0, this.t - this.lastStagger) / fade);
	}

	/** Posture.add: the bar drains after a pause, fills, and a full bar staggers, shorter and harder each time. */
	private void addPosture(double amount) {
		int idle = this.t - this.postureLastHit - this.cfg.postureRegenDelayTicks;
		if (idle > 0) {
			this.posture = Math.max(0.0, this.posture - idle * this.cfg.postureRegenPerTick);
		}
		this.postureLastHit = this.t;
		if (this.t < this.staggerUntil) {
			return;
		}
		this.posture += amount;
		int recent = this.recentStaggers();
		double max = this.target.postureMax * (1.0 + this.cfg.staggerRepeatPosture * recent);
		if (this.posture >= max) {
			int ticks = Math.max(this.cfg.staggerMinTicks, (int) Math.round(this.cfg.staggerTicks * Math.pow(this.cfg.staggerRepeatDuration, recent)));
			this.posture = 0;
			this.staggerUntil = this.t + ticks;
			this.staggers = recent + 1;
			this.lastStagger = this.t;
			this.staggerCount++;
		}
	}
}
