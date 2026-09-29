package dev.forja.test.balance;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.forja.Forja;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.Posture;
import dev.forja.difficulty.Bosses;
import dev.forja.difficulty.ForjaDifficulty;
import dev.forja.difficulty.Threat;
import dev.forja.entity.MagicBolt;
import dev.forja.entity.Shockwave;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.magic.Spellcasting;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.PartType;
import dev.forja.test.CombatGameTests;
import dev.forja.upgrade.Upgrade;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Measures every mob through the real damage pipeline. Each mob is spawned, stripped of whatever it
 * came with (gear, a veteran's or an elite's roll, the difficulty modifier Scaling puts on at load) so
 * that it stands as a plain one at the configured difficulty, frozen, and then hit: small blows, healed
 * back after each one, so that nothing it does at low health or when its bar fills gets in the way.
 */
public final class Probe {
	/** The vanilla hostiles the report covers: the same list as docs/objetos_contrato.json. */
	public static final List<EntityType<? extends LivingEntity>> VANILLA = List.of(
		EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.DROWNED, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.SKELETON,
		EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.WITHER_SKELETON, EntityTypes.CREEPER, EntityTypes.SPIDER,
		EntityTypes.CAVE_SPIDER, EntityTypes.PILLAGER, EntityTypes.VINDICATOR, EntityTypes.EVOKER, EntityTypes.WITCH,
		EntityTypes.ENDERMAN, EntityTypes.BLAZE, EntityTypes.PIGLIN_BRUTE);

	/** The raw blow every factor is measured with: small enough that no bar fills and no slag splits. */
	static final float RAW = 2.0F;

	private final GameTestHelper helper;
	private final ServerLevel level;
	private final CombatGameTests.TestPlayer player;
	/** For every weapon type, a plain stack at each breach level (Brecha 0..IV, then IV with a Resonante head). */
	private final Map<ForgeType, Build[]> probes = new EnumMap<>(ForgeType.class);
	private final List<Entity> spawned = new ArrayList<>();

	public Probe(GameTestHelper helper, List<ForgeType> types) {
		this.helper = helper;
		this.level = helper.getLevel();
		this.player = new CombatGameTests.TestPlayer(this.level);
		var registries = this.level.registryAccess();
		for (ForgeType type : types) {
			Build[] byBreach = new Build[Target.BREACH_LEVELS];
			for (int breach = 0; breach < Target.BREACH_LEVELS; breach++) {
				List<ForgeMaterial> materials = new ArrayList<>(Assembler.defaultMaterials(type));
				if (breach == Target.BREACH_LEVELS - 1) {
					for (int slot = 0; slot < type.slots.size(); slot++) {
						if (type.slots.get(slot).role == PartType.Role.HEAD) {
							materials.set(slot, ForgeMaterial.ECO);
						}
					}
				}
				int percent = Math.min(100, breach * 25);
				Map<Upgrade, Integer> upgrades = percent > 0 && Upgrade.BRECHA.appliesTo(type) ? Map.of(Upgrade.BRECHA, percent) : Map.of();
				byBreach[breach] = new Build(type, materials, upgrades, registries);
			}
			this.probes.put(type, byBreach);
		}
	}

	public CombatGameTests.TestPlayer player() {
		return this.player;
	}

	/** The breach level each probe stack really carries, read back off it. */
	public int breachOf(ForgeType type, int index) {
		return this.probes.get(type)[index].breach;
	}

	/** Every mob of the report, measured. */
	public List<Target> measureAll() {
		List<Target> targets = new ArrayList<>();
		int index = 0;
		for (EntityType<? extends LivingEntity> type : VANILLA) {
			targets.add(this.measure(type, false, index++));
		}
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			var key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			if (!key.getNamespace().equals(Forja.MOD_ID)) {
				continue;
			}
			Entity probe = type.create(this.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			boolean monster = probe instanceof Monster;
			if (probe != null) {
				probe.discard();
			}
			if (monster) {
				@SuppressWarnings("unchecked")
				EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) type;
				targets.add(this.measure(living, true, index++));
			}
		}
		return targets;
	}

	/** Takes every mob and helper entity this probe made out of the world again. */
	public void cleanUp() {
		for (Entity entity : this.spawned) {
			entity.discard();
		}
		this.spawned.clear();
	}

	/** One mob, measured on its own, left standing (stripped and frozen) for whoever asked. */
	public Target measure(EntityType<? extends LivingEntity> type) {
		return this.measure(type, BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(Forja.MOD_ID), this.spawned.size());
	}

	private Target measure(EntityType<? extends LivingEntity> type, boolean forja, int index) {
		LivingEntity mob = this.helper.spawn(type, new BlockPos(1 + index % 6, 2, 1 + (index / 6) % 6));
		this.spawned.add(mob);
		strip(mob);
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
		Target target = new Target(id, type.getDescription().getString(), forja);
		target.entity = mob;
		target.maxHealth = mob.getMaxHealth();
		target.boss = Bosses.isBoss(mob);
		double share = target.boss ? CombatConfig.get().hitCapBoss : Threat.of(mob).hitCap();
		target.cap = target.maxHealth * share * ForjaDifficulty.current().cap;
		target.finishableBelow = target.boss ? CombatConfig.get().bossFinisherHealth : 1.0;
		target.postureMax = Posture.max(mob);
		target.undead = mob.isInvertedHealAndHarm();
		target.poisonable = mob.canBeAffected(new MobEffectInstance(MobEffects.POISON, 100));
		target.witherable = mob.canBeAffected(new MobEffectInstance(MobEffects.WITHER, 100));
		target.fireImmune = mob.fireImmune();
		if (mob instanceof dev.forja.entity.HollowArmor) {
			target.feignShare = dev.forja.ai.ForjaTraits.HOLLOW_FEIGN_HEALTH;
			target.feignTicks = dev.forja.ai.ForjaTraits.HOLLOW_FEIGN_TICKS;
		}
		target.special = special(mob);
		target.excluded = mob instanceof dev.forja.entity.StarCore;

		// Face the middle of its body: a torso hit, so that the head bonus stays a separate factor.
		Vec3 torso = mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0);
		this.player.setPos(mob.getX() - 2.0, mob.getY(), mob.getZ());
		this.player.lookAt(EntityAnchorArgument.Anchor.EYES, torso);

		for (Map.Entry<ForgeType, Build[]> entry : this.probes.entrySet()) {
			ForgeType weapon = entry.getKey();
			Build[] byBreach = entry.getValue();
			double[][] factors = new double[2][Target.BREACH_LEVELS];
			this.hold(byBreach[0]);
			DamageSource blow = this.level.damageSources().playerAttack(this.player);
			heal(mob);
			double lost = this.hurt(mob, blow, RAW);
			double posture = Posture.fill(mob) * Posture.max(mob) / RAW;
			Posture.endStagger(mob);
			heal(mob);
			target.posture.put(weapon, posture);
			factors[0][0] = lost / RAW;
			for (int breach = 1; breach < Target.BREACH_LEVELS; breach++) {
				this.hold(byBreach[breach]);
				factors[0][breach] = this.dealt(mob, this.level.damageSources().playerAttack(this.player), false) / RAW;
			}
			for (int breach = 0; breach < Target.BREACH_LEVELS; breach++) {
				this.hold(byBreach[breach]);
				factors[1][breach] = this.dealt(mob, this.level.damageSources().playerAttack(this.player), true) / RAW;
			}
			target.melee.put(weapon, factors);
		}

		// Spells: a bolt of the staff and the opening of the tome's area, each from a plain one.
		if (this.probes.containsKey(ForgeType.BACULO)) {
			ItemStack staff = this.probes.get(ForgeType.BACULO)[0].stack;
			this.hold(this.probes.get(ForgeType.BACULO)[0]);
			MagicBolt bolt = new MagicBolt(this.level, this.player, new Vec3(1.0, 0.0, 0.0), 0xFFFFFF, RAW, staff, 0.0F, false);
			bolt.setPos(mob.getX(), torso.y - bolt.getBbHeight() * 0.5, mob.getZ());
			target.bolt = this.spell(mob, this.level.damageSources().indirectMagic(bolt, this.player), staff);
			target.boltPosture = this.spellPosture(mob, this.level.damageSources().indirectMagic(bolt, this.player), staff);
		}
		if (this.probes.containsKey(ForgeType.GRIMORIO)) {
			ItemStack tome = this.probes.get(ForgeType.GRIMORIO)[0].stack;
			this.hold(this.probes.get(ForgeType.GRIMORIO)[0]);
			Shockwave mark = Shockwave.burst(this.level, mob.position(), 3.0, 10, 0xFFFFFF);
			this.spawned.add(mark);
			target.area = this.spell(mob, this.level.damageSources().indirectMagic(mark, this.player), tome);
			target.areaPosture = this.spellPosture(mob, this.level.damageSources().indirectMagic(mark, this.player), tome);
		}
		target.lightning = this.pair(mob, this.level.damageSources().lightningBolt());
		target.magic = this.pair(mob, this.level.damageSources().magic());
		target.fire = this.pair(mob, this.level.damageSources().onFire());
		target.wither = this.pair(mob, this.level.damageSources().wither());
		heal(mob);
		return target;
	}

	private double[] spell(LivingEntity mob, DamageSource source, ItemStack weapon) {
		double[] pair = new double[2];
		for (int staggered = 0; staggered < 2; staggered++) {
			heal(mob);
			if (staggered == 1) {
				Posture.breakPosture(mob, this.level.getGameTime());
			}
			float before = mob.getHealth();
			mob.invulnerableTime = 0;
			Spellcasting.land(this.level, mob, source, RAW, weapon, true);
			pair[staggered] = (before - mob.getHealth()) / RAW;
			Posture.endStagger(mob);
		}
		heal(mob);
		return pair;
	}

	private double spellPosture(LivingEntity mob, DamageSource source, ItemStack weapon) {
		heal(mob);
		Posture.endStagger(mob);
		mob.invulnerableTime = 0;
		Spellcasting.land(this.level, mob, source, RAW, weapon, true);
		double posture = Posture.fill(mob) * Posture.max(mob) / RAW;
		Posture.endStagger(mob);
		heal(mob);
		return posture;
	}

	private double[] pair(LivingEntity mob, DamageSource source) {
		return new double[] {this.dealt(mob, source, false) / RAW, this.dealt(mob, source, true) / RAW};
	}

	private void hold(Build build) {
		this.player.setItemInHand(InteractionHand.MAIN_HAND, build.stack.copy());
	}

	/** Health one raw blow of {@link #RAW} takes, standing or staggered, healed back after. */
	private double dealt(LivingEntity mob, DamageSource source, boolean staggered) {
		heal(mob);
		if (staggered) {
			Posture.breakPosture(mob, this.level.getGameTime());
		}
		double lost = this.hurt(mob, source, RAW);
		Posture.endStagger(mob);
		heal(mob);
		return lost;
	}

	private double hurt(LivingEntity mob, DamageSource source, float amount) {
		float before = mob.getHealth();
		mob.invulnerableTime = 0;
		mob.hurtServer(this.level, source, amount);
		return before - mob.getHealth();
	}

	public static void heal(LivingEntity mob) {
		mob.setHealth(mob.getMaxHealth());
		mob.invulnerableTime = 0;
		mob.setRemainingFireTicks(0);
		mob.removeAllEffects();
		// The probe measures the blow, not the enderman's dodge (ai/EnderDodge): as if it had just dodged.
		dev.forja.ai.EnderDodge.hold(mob);
	}

	/** A plain one of its kind: no gear, no veteran's or elite's roll, no difficulty modifier, no brain. */
	public static void strip(LivingEntity mob) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setItemSlot(slot, ItemStack.EMPTY);
		}
		for (String tag : List.of("forja_veterano", "forja_amenaza_elite", "forja_elite")) {
			mob.removeTag(tag);
		}
		var id = Forja.id("dificultad");
		for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS)) {
			var instance = mob.getAttribute(attribute);
			if (instance != null) {
				instance.removeModifier(id);
			}
		}
		if (mob instanceof Mob brain) {
			brain.setNoAi(true);
		}
		mob.setCustomName(null);
		heal(mob);
	}

	/** The mobs whose own damage rules the fight model does not follow blow by blow. */
	private static String special(LivingEntity mob) {
		if (mob instanceof dev.forja.entity.StarCore) {
			return "absorbe los primeros " + (int) dev.forja.entity.StarCore.CAPACITY + " de daño y los devuelve; se pelea por fases";
		}
		if (mob instanceof dev.forja.entity.CuneGuardian) {
			return "inmune mientras le queden sellos en su sala";
		}
		if (mob instanceof dev.forja.entity.FallenSmith) {
			return "jefe: guardia al 50 %, tope por golpe del 8 %, postura sólo tras sus golpes pesados, fases de reforja invulnerables";
		}
		if (mob instanceof dev.forja.entity.LivingSlag) {
			return "los cortes de 3 o más lo parten en trozos que hay que matar aparte";
		}
		if (mob instanceof dev.forja.entity.HollowArmor) {
			return "se hace el muerto una vez al 30 % (3 s invulnerable, modelado); lo no forjado le hace un 35 %";
		}
		if (mob instanceof dev.forja.entity.BrokenMould) {
			return "copia el arma con que le pegan";
		}
		if (mob.getType() == EntityTypes.ENDERMAN) {
			return "se teletransporta ante proyectiles; esquiva el 34 % de los golpes teletransportándose (7 s sin esquivar tras hacerlo), no modelado";
		}
		return "";
	}
}
