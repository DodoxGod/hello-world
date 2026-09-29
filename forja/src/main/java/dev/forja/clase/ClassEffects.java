package dev.forja.clase;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.forja.forge.ForgeType;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The one place the rest of the mod asks what a player's class does to it (docs/CLASES.md). Every method is
 * safe for anyone: no class, no player, or the client reading its own synced copy — the answer is simply
 * "nothing changes" (1 for multipliers, 0 for bonuses).
 *
 * <p>The mana methods ({@link #manaMaxBonus}, {@link #manaRegenBonus}, {@link #spellCostMultiplier}) are read by
 * magic/Mana (the bar's size and how fast it fills) and magic/Spellcasting (what a spell costs).
 */
public final class ClassEffects {
	/** Ejecutor: a foe counts as nearly dead under this share of its health. */
	public static final float EXECUTE_BELOW = 0.35F;

	/** What a skill leaves running on a player for a while: server side, forgotten on a class change. */
	private static final class Buffs {
		/** Paso sombrío or Réplica: the next melee blow before then does {@link #nextHitBonus} more. */
		long nextHitUntil = -1;
		float nextHitBonus;
		/** Marca de muerte: this mob takes more from this player until then. */
		@Nullable UUID marked;
		long markedUntil = -1;
		long redHotUntil = -1;
		long concentrationUntil = -1;
		/** Último bastión: when it can save you again. */
		long lastStandReady;
		/** Égida: when the next shield comes. */
		long aegisDue;
	}

	private static final Map<UUID, Buffs> BUFFS = new HashMap<>();

	private ClassEffects() {
	}

	private static Buffs buffs(Player player) {
		return BUFFS.computeIfAbsent(player.getUUID(), id -> new Buffs());
	}

	/** Drops whatever a skill left running (a class change, a reset, leaving the server). */
	public static void forget(Player player) {
		BUFFS.remove(player.getUUID());
	}

	// ------------------------------------------------------------------ numbers

	/** The sum of the class's base and every learned talent for one stat. */
	public static float stat(@Nullable Player player, ClassStat stat) {
		ClassData data = ClassProgress.data(player);
		PlayerClass owner = data.playerClass();
		if (owner == null) {
			return 0.0F;
		}
		float total = owner.base(stat);
		for (Talent talent : owner.talents()) {
			if (!talent.mods.isEmpty() && data.has(talent)) {
				for (ClassStat.Mod mod : talent.mods) {
					if (mod.stat() == stat) {
						total += mod.value();
					}
				}
			}
		}
		return total;
	}

	private static float multiplier(@Nullable Player player, ClassStat stat, float floor) {
		return Math.max(floor, 1.0F + stat(player, stat));
	}

	public static boolean has(@Nullable Player player, Talent talent) {
		return ClassProgress.has(player, talent);
	}

	public static boolean is(@Nullable Player player, PlayerClass clazz) {
		return ClassProgress.clazz(player) == clazz;
	}

	// ------------------------------------------------------------------ stamina and dodge (combat/Stamina, client/CombatClient)

	public static float staminaMaxMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.STAMINA_MAX, 0.2F);
	}

	public static float staminaRegenMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.STAMINA_REGEN, 0.1F);
	}

	/** What swings, jumps and charged blows cost. */
	public static float staminaCostMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.STAMINA_COST, 0.1F);
	}

	/** How far a dodge carries. Read on the client, which moves the player; Quiebro and Paso arcano multiply with it. */
	public static float dodgeDistanceMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.DODGE_DISTANCE, 0.2F);
	}

	public static float dodgeCooldownMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.DODGE_COOLDOWN, 0.2F);
	}

	public static float dodgeCostMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.DODGE_COST, 0.1F);
	}

	public static int dodgeIframeBonus(@Nullable Player player) {
		return Math.round(stat(player, ClassStat.DODGE_IFRAMES));
	}

	// ------------------------------------------------------------------ guard and posture

	public static int parryWindowBonus(@Nullable LivingEntity defender) {
		return defender instanceof Player player ? Math.round(stat(player, ClassStat.PARRY_WINDOW)) : 0;
	}

	public static float blockCostMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.BLOCK_COST, 0.1F);
	}

	public static float postureMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.POSTURE, 0.1F);
	}

	public static float counterBonus(@Nullable Player player) {
		return stat(player, ClassStat.COUNTER);
	}

	public static float finisherBonus(@Nullable Player player) {
		return stat(player, ClassStat.FINISHER);
	}

	// ------------------------------------------------------------------ blows given and taken (combat/CombatHooks.afterArmor)

	/**
	 * What the attacker's class does to a blow: melee (backstab, a nearly dead foe, a staggered one, the
	 * skills' next-hit bonuses, a smith's own tools), arrows (their damage, the head, the distance) and the
	 * death mark on anything — all of which add up — and then, multiplied on top, the class's own factor for
	 * that kind of blow ({@link ClassDamage}: melee, projectile, magic). The spell bonuses of the class and its
	 * talents are put on where the spell is cast (magic/Spellcasting, {@link #spellDamageMultiplier}); the
	 * factor for magic is put on here, where the spell lands, so a Curandero's bolt hurts at a third while the
	 * ally it reaches is still mended a tenth of the bolt's whole bite (magic/Healing).
	 */
	public static float dealt(DamageSource source, LivingEntity target, boolean staggered, boolean head) {
		if (!(source.getEntity() instanceof Player attacker) || attacker == target || ClassProgress.clazz(attacker) == null) {
			return 1.0F;
		}
		float bonus = 0.0F;
		long now = attacker.level().getGameTime();
		Buffs buffs = BUFFS.get(attacker.getUUID());
		ClassDamage.Blow blow = ClassDamage.of(source, attacker);
		// A spell cast from the hand (the Nova arcana) names the caster as what struck; it is still not a blow of the hand.
		boolean melee = blow == ClassDamage.Blow.MELEE;
		if (melee) {
			if (behind(target, attacker)) {
				bonus += stat(attacker, ClassStat.BACKSTAB);
			}
			if (target.getHealth() <= target.getMaxHealth() * EXECUTE_BELOW) {
				bonus += stat(attacker, ClassStat.EXECUTE);
			}
			if (staggered) {
				bonus += stat(attacker, ClassStat.STAGGERED_BONUS);
			}
			ItemStack weapon = attacker.getMainHandItem();
			ForgedParts parts = weapon.get(ModComponents.PARTS);
			if (parts != null && smithsTool(parts.type())) {
				bonus += stat(attacker, ClassStat.SMITH_WEAPON);
			}
			if (buffs != null && buffs.nextHitUntil >= now) {
				bonus += buffs.nextHitBonus;
				buffs.nextHitUntil = -1;
				// Paso sombrío: the blow is what gives you away.
				attacker.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
			}
			if (buffs != null && buffs.redHotUntil >= now) {
				bonus += ActiveSkill.FORJA_AL_ROJO.numbers[2];
				target.igniteForTicks(ActiveSkill.FORJA_AL_ROJO.ticks(1));
			}
		} else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			bonus += stat(attacker, ClassStat.PROJECTILE_DAMAGE);
			if (head) {
				bonus += stat(attacker, ClassStat.HEADSHOT);
			}
			if (has(attacker, Talent.ARQUERO_TIRO_LEJANO)) {
				float[] n = Talent.ARQUERO_TIRO_LEJANO.numbers;
				double far = attacker.distanceTo(target) - n[0];
				bonus += (float) Math.max(0.0, Math.min(n[2], far * n[1]));
			}
			if (has(attacker, Talent.ARQUERO_TIRO_CERTERO) && arrow.isCritArrow()) {
				target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS,
					Math.round(Talent.ARQUERO_TIRO_CERTERO.numbers[0] * 20.0F), 1), attacker);
			}
		}
		if (buffs != null && buffs.markedUntil >= now && target.getUUID().equals(buffs.marked)) {
			bonus += ActiveSkill.MARCA_DE_MUERTE.numbers[2];
		}
		float factor = blow == null ? 1.0F : ClassDamage.factor(attacker, blow);
		return Math.max(0.0F, 1.0F + bonus) * factor;
	}

	/** Hammers, maces, pickaxes and axes: what a smith's arm is used to (Brazo de herrero). */
	public static boolean smithsTool(ForgeType type) {
		return type == ForgeType.MARTILLO || type == ForgeType.MAZO || type == ForgeType.PICO || type == ForgeType.HACHA
			|| type == ForgeType.PICAHACHA;
	}

	/** The same test the combat overhaul uses for a finisher: more than about 110 degrees off where the target faces. */
	public static boolean behind(LivingEntity target, LivingEntity attacker) {
		double dx = attacker.getX() - target.getX();
		double dz = attacker.getZ() - target.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-3) {
			return false;
		}
		double yaw = Math.toRadians(target.getYHeadRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		return (dx * fx + dz * fz) / length < -0.35;
	}

	/** What the victim's class does to a blow it takes: less of everything, of magic, of fire, and Inquebrantable. */
	public static float taken(Player victim, DamageSource source) {
		if (ClassProgress.clazz(victim) == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 1.0F;
		}
		float change = stat(victim, ClassStat.DAMAGE_TAKEN);
		if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
			change += stat(victim, ClassStat.MAGIC_TAKEN);
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			change += stat(victim, ClassStat.FIRE_TAKEN);
		}
		if (has(victim, Talent.GUERRERO_INQUEBRANTABLE)) {
			float[] n = Talent.GUERRERO_INQUEBRANTABLE.numbers;
			if (victim.getHealth() <= victim.getMaxHealth() * n[0]) {
				change -= n[1];
			}
		}
		return Math.max(0.05F, 1.0F + change);
	}

	// ------------------------------------------------------------------ magic (magic/Spellcasting, magic/Healing)

	/**
	 * The class's and its talents' spell bonus (SPELL_DAMAGE, added up), put on where a spell is cast. The
	 * class's factor for magic ({@link ClassDamage}) is not in here: it goes on where the spell lands, in
	 * {@link #dealt}.
	 */
	public static float spellDamageMultiplier(@Nullable LivingEntity caster) {
		return caster instanceof Player player ? multiplier(player, ClassStat.SPELL_DAMAGE, 0.1F) : 1.0F;
	}

	/** The wait after a spell: the class, its talents, and Concentración while it lasts. */
	public static float spellCooldownMultiplier(@Nullable LivingEntity caster) {
		if (!(caster instanceof Player player)) {
			return 1.0F;
		}
		float multiplier = multiplier(player, ClassStat.SPELL_COOLDOWN, 0.1F);
		if (concentrating(player)) {
			multiplier *= ActiveSkill.CONCENTRACION.numbers[1];
		}
		return multiplier;
	}

	/** How long a full charge takes, as a share of the weapon's own. */
	public static float spellChargeMultiplier(@Nullable LivingEntity caster) {
		return caster instanceof Player player ? multiplier(player, ClassStat.SPELL_CHARGE, 0.2F) : 1.0F;
	}

	/** What a full charge adds on top of the weapon's own bonus. */
	public static float chargeBonusExtra(@Nullable LivingEntity caster) {
		return caster instanceof Player player ? stat(player, ClassStat.CHARGE_BONUS) : 0.0F;
	}

	public static float healingMultiplier(@Nullable LivingEntity healer) {
		return healer instanceof Player player ? multiplier(player, ClassStat.HEALING, 0.0F) : 1.0F;
	}

	public static boolean concentrating(Player player) {
		Buffs buffs = BUFFS.get(player.getUUID());
		return buffs != null && buffs.concentrationUntil >= player.level().getGameTime();
	}

	// ------------------------------------------------------------------ mana (magic/Mana, magic/Spellcasting)

	/** Share to add to the player's maximum mana: the bar's maximum multiplies by (1 + this). */
	public static float manaMaxBonus(@Nullable Player player) {
		return stat(player, ClassStat.MANA_MAX);
	}

	/** Share to add to mana regeneration: it multiplies by (1 + this). */
	public static float manaRegenBonus(@Nullable Player player) {
		return stat(player, ClassStat.MANA_REGEN);
	}

	/**
	 * What a spell's mana costs this player, as a multiplier: Economía arcana takes a quarter off, and under
	 * Concentración spells are free. Every spell's cost (staff, tome, lantern) multiplies by this.
	 */
	public static float spellCostMultiplier(@Nullable Player player) {
		if (player == null) {
			return 1.0F;
		}
		if (concentrating(player)) {
			return 0.0F;
		}
		return multiplier(player, ClassStat.SPELL_COST, 0.0F);
	}

	// ------------------------------------------------------------------ bows

	public static float drawSpeedMultiplier(@Nullable LivingEntity archer) {
		return archer instanceof Player player ? multiplier(player, ClassStat.DRAW_SPEED, 0.2F) : 1.0F;
	}

	public static float arrowSpeedMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.ARROW_SPEED, 0.2F);
	}

	// ------------------------------------------------------------------ the forge

	/** Added to the half-width of the perfect window (client/ForgeScreen.window). */
	public static float forgeWindowBonus(@Nullable Player player) {
		return stat(player, ClassStat.FORGE_WINDOW);
	}

	/** Points of potential a piece is forged with (forge/Potential.atForge). */
	public static int potentialBonus(@Nullable Player player) {
		return Math.round(stat(player, ClassStat.POTENTIAL));
	}

	/** How much of a piece one repair ingot mends, as a multiplier (menu/ForgeMenu.planRepair). */
	public static float repairMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.REPAIR, 0.1F);
	}

	/** The extra share every upgrade takes (forge/SmithLevel.upgradeBonus). */
	public static int upgradeBonus(@Nullable Player player) {
		return Math.round(stat(player, ClassStat.UPGRADE_BONUS));
	}

	// ------------------------------------------------------------------ what the skills leave running

	static void nextHit(Player player, float bonus, int ticks) {
		Buffs buffs = buffs(player);
		buffs.nextHitUntil = player.level().getGameTime() + ticks;
		buffs.nextHitBonus = bonus;
	}

	static void mark(Player player, LivingEntity target, int ticks) {
		Buffs buffs = buffs(player);
		buffs.marked = target.getUUID();
		buffs.markedUntil = player.level().getGameTime() + ticks;
	}

	static void redHot(Player player, int ticks) {
		buffs(player).redHotUntil = player.level().getGameTime() + ticks;
	}

	static void concentrate(Player player, int ticks) {
		buffs(player).concentrationUntil = player.level().getGameTime() + ticks;
	}

	/** Último bastión: whether it can save this player now, and if so, spends it. */
	static boolean spendLastStand(Player player, int cooldownTicks) {
		Buffs buffs = buffs(player);
		long now = player.level().getGameTime();
		if (now < buffs.lastStandReady) {
			return false;
		}
		buffs.lastStandReady = now + cooldownTicks;
		return true;
	}

	/** Égida: whether a shield is due, and if so, schedules the next one. */
	static boolean aegisDue(Player player, int everyTicks) {
		Buffs buffs = buffs(player);
		long now = player.level().getGameTime();
		if (now < buffs.aegisDue) {
			return false;
		}
		buffs.aegisDue = now + everyTicks;
		return true;
	}
}
