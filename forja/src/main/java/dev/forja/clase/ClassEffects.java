package dev.forja.clase;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.forja.difficulty.Bosses;
import dev.forja.difficulty.Threat;
import dev.forja.forge.ForgeType;
import dev.forja.magic.Mana;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The one place the rest of the mod asks what a player's class does to it (docs/CLASES.md, docs/ARBOLES.md).
 * Every method is safe for anyone: no class, no player, or the client reading its own synced copy — the answer
 * is simply "nothing changes" (1 for multipliers, 0 for bonuses).
 *
 * <p>The numbers of a class and its learned nodes are added up once per change of the player's data and kept
 * ({@link #cache}); {@link #hook} says whether a node with a special effect is learned, and hands back its
 * numbers.
 */
public final class ClassEffects {
	/** Ejecutor: a foe counts as nearly dead under this share of its health. */
	public static final float EXECUTE_BELOW = 0.35F;
	/** The least of a blow a class lets through, whatever its nodes add up to: 40 %. */
	public static final float TAKEN_FLOOR = 0.40F;

	/** What a skill or a node leaves running on a player for a while: server side, forgotten on a class change. */
	private static final class Buffs {
		/** Paso sombrío, Réplica, Filo del viento, Funámbulo: the next melee blow before then does {@link #nextHitBonus} more. */
		long nextHitUntil = -1;
		float nextHitBonus;
		/** Salto atrás II: the next arrow before then does {@link #nextArrowBonus} more. */
		long nextArrowUntil = -1;
		float nextArrowBonus;
		/** Marca de muerte: this mob takes more from this player until then. */
		@Nullable UUID marked;
		long markedUntil = -1;
		/** Marca del cazador. */
		@Nullable UUID hunted;
		long huntedUntil = -1;
		/** Flecha de red II: what is caught in the net takes more from your arrows. */
		final Map<UUID, Long> netted = new HashMap<>();
		/** Forja al rojo, after a perfect forge. */
		long redHotUntil = -1;
		long concentrationUntil = -1;
		/** Último bastión: when it can save you again. */
		long lastStandReady;
		/** Égida: when the next shield comes. */
		long aegisDue;
		/** Frenesí. */
		int frenzy;
		long frenzyUntil = -1;
		/** Espejismo, Rocío, Mártir: when each can work again. */
		long mirageReady;
		long dewReady;
		long martyrReady;
		/** Vendaje: the last time this player was hurt. */
		long lastHurt;
		/** Carga profunda: the spell being released was held past full. */
		boolean overheld;
		/** Funámbulo and Pisotón: how far the player was falling last tick. */
		float falling;
		/** Fantasma: Paso sombrío's first blow does not give you away. */
		boolean ghostSpared;
	}

	private static final Map<UUID, Buffs> BUFFS = new HashMap<>();

	/** The class's and its learned nodes' numbers, added up for one copy of the player's data. */
	private record Cache(ClassData data, float[] totals, Map<String, ClassTree.Node> hooks) {
	}

	/** One map for the server's players and one for the client's own: they share a UUID in singleplayer. */
	private static final Map<UUID, Cache> SERVER_CACHE = new ConcurrentHashMap<>();
	private static final Map<UUID, Cache> CLIENT_CACHE = new ConcurrentHashMap<>();

	private ClassEffects() {
	}

	private static Buffs buffs(Player player) {
		return BUFFS.computeIfAbsent(player.getUUID(), id -> new Buffs());
	}

	/** Drops whatever a skill left running (a class change, a reset, leaving the server). */
	public static void forget(Player player) {
		BUFFS.remove(player.getUUID());
	}

	private static Cache cache(Player player) {
		ClassData data = ClassProgress.data(player);
		Map<UUID, Cache> map = player.level().isClientSide() ? CLIENT_CACHE : SERVER_CACHE;
		Cache cache = map.get(player.getUUID());
		if (cache != null && cache.data == data) {
			return cache;
		}
		float[] totals = new float[ClassStat.values().length];
		Map<String, ClassTree.Node> hooks = new HashMap<>();
		PlayerClass owner = data.playerClass();
		if (owner != null) {
			for (ClassStat.Mod mod : owner.base) {
				totals[mod.stat().ordinal()] += mod.value();
			}
			ClassTree.Tree tree = ClassTree.tree(owner);
			for (String id : data.nodes()) {
				ClassTree.Node node = tree.node(id);
				if (node == null) {
					continue;
				}
				for (ClassStat.Mod mod : node.mods) {
					totals[mod.stat().ordinal()] += mod.value();
				}
				if (node.hook != null) {
					hooks.put(node.hook, node);
				}
			}
		}
		cache = new Cache(data, totals, hooks);
		map.put(player.getUUID(), cache);
		return cache;
	}

	// ------------------------------------------------------------------ numbers

	/** The sum of the class's base and every learned node for one stat (plus what a keystone's price adds while it lasts). */
	public static float stat(@Nullable Player player, ClassStat stat) {
		if (player == null || ClassProgress.clazz(player) == null) {
			return 0.0F;
		}
		float total = cache(player).totals[stat.ordinal()];
		if (stat == ClassStat.MAX_HEALTH && !player.level().isClientSide()) {
			// Último bastión's price: less health while it recharges.
			float[] n = hook(player, Hooks.ULTIMO_BASTION);
			Buffs buffs = BUFFS.get(player.getUUID());
			if (n != null && buffs != null && player.level().getGameTime() < buffs.lastStandReady) {
				total -= n[2];
			}
		}
		return total;
	}

	/** The numbers of a learned node with a special effect, or null if the player does not have it. */
	public static float @Nullable [] hook(@Nullable Player player, String hook) {
		if (player == null || ClassProgress.clazz(player) == null) {
			return null;
		}
		ClassTree.Node node = cache(player).hooks.get(hook);
		return node == null ? null : node.numbers;
	}

	public static boolean has(@Nullable Player player, String hook) {
		return hook(player, hook) != null;
	}

	private static float multiplier(@Nullable Player player, ClassStat stat, float floor) {
		return Math.max(floor, 1.0F + stat(player, stat));
	}

	public static boolean is(@Nullable Player player, PlayerClass clazz) {
		return ClassProgress.clazz(player) == clazz;
	}

	// ------------------------------------------------------------------ stamina and dodge (combat/Stamina, client/CombatClient)

	public static float staminaMaxMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.STAMINA_MAX, 0.2F);
	}

	/** Segundo aliento: under a quarter of the bar, it fills twice as fast. */
	public static float staminaRegenMultiplier(@Nullable Player player) {
		float multiplier = multiplier(player, ClassStat.STAMINA_REGEN, 0.1F);
		float[] n = hook(player, Hooks.SEGUNDO_ALIENTO);
		if (n != null && dev.forja.combat.Stamina.value(player) < dev.forja.combat.Stamina.max(player) * n[0]) {
			multiplier *= 2.0F;
		}
		return multiplier;
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

	/** Sin sombra: a dodge costs nothing (it costs health instead, ClassEvents.onDodge). */
	public static float dodgeCostMultiplier(@Nullable Player player) {
		return has(player, Hooks.SIN_SOMBRA) ? 0.0F : multiplier(player, ClassStat.DODGE_COST, 0.1F);
	}

	public static int dodgeIframeBonus(@Nullable Player player) {
		return Math.round(stat(player, ClassStat.DODGE_IFRAMES));
	}

	/** Fortaleza's price: with a shield in the off hand there is no dodging. Asked on both sides. */
	public static boolean canDodge(@Nullable Player player) {
		return player == null || !has(player, Hooks.FORTALEZA) || !player.getOffhandItem().is(Items.SHIELD)
			&& !(player.getOffhandItem().has(ModComponents.PARTS) && player.getOffhandItem().get(ModComponents.PARTS).type() == ForgeType.ESCUDO);
	}

	// ------------------------------------------------------------------ guard and posture

	public static int parryWindowBonus(@Nullable LivingEntity defender) {
		return defender instanceof Player player ? Math.round(stat(player, ClassStat.PARRY_WINDOW)) : 0;
	}

	/** What a block that is not a parry costs: Duelista pays double for it. */
	public static float blockCostMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.BLOCK_COST, 0.1F) * (has(player, Hooks.DUELISTA) ? 2.0F : 1.0F);
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
	 * What the attacker's class does to a blow: melee (backstab, a nearly dead foe, a staggered one, the next-hit
	 * bonuses, a smith's own tools, Frenesí, Golpe de gracia...), arrows (their damage, the head, the distance,
	 * the marks) and the death mark on anything — all of which add up — and then, multiplied on top, the class's
	 * own factor for that kind of blow ({@link ClassDamage}: melee, projectile, magic). No node touches the factor.
	 */
	public static float dealt(DamageSource source, LivingEntity target, boolean staggered, boolean head) {
		if (!(source.getEntity() instanceof Player attacker) || attacker == target || ClassProgress.clazz(attacker) == null) {
			return 1.0F;
		}
		float bonus = 0.0F;
		long now = attacker.level().getGameTime();
		Buffs buffs = BUFFS.get(attacker.getUUID());
		ClassDamage.Blow blow = ClassDamage.of(source, attacker);
		boolean melee = blow == ClassDamage.Blow.MELEE;
		if (melee) {
			if (behind(target, attacker)) {
				bonus += stat(attacker, ClassStat.BACKSTAB);
				float[] poison = hook(attacker, Hooks.VENENO_EN_LA_HOJA);
				if (poison != null) {
					target.addEffect(new MobEffectInstance(MobEffects.POISON, ActiveSkill.ticks(poison[0]), 0), attacker);
				}
			}
			if (target.getHealth() <= target.getMaxHealth() * EXECUTE_BELOW) {
				bonus += stat(attacker, ClassStat.EXECUTE);
			}
			if (staggered) {
				bonus += stat(attacker, ClassStat.STAGGERED_BONUS);
			} else {
				float[] hammer = hook(attacker, Hooks.MARTILLO_DE_GUERRA);
				if (hammer != null) {
					bonus -= hammer[0];
				}
			}
			ItemStack weapon = attacker.getMainHandItem();
			ForgedParts parts = weapon.get(ModComponents.PARTS);
			if (parts != null && smithsTool(parts.type())) {
				bonus += stat(attacker, ClassStat.SMITH_WEAPON);
			}
			if (buffs != null && buffs.nextHitUntil >= now) {
				bonus += buffs.nextHitBonus;
				buffs.nextHitUntil = -1;
				// Paso sombrío: the blow is what gives you away (not the first, with Fantasma).
				if (buffs.ghostSpared) {
					buffs.ghostSpared = false;
				} else {
					attacker.removeEffect(MobEffects.INVISIBILITY);
				}
			}
			float[] red = hook(attacker, Hooks.FORJA_AL_ROJO);
			if (red != null && buffs != null && buffs.redHotUntil >= now) {
				bonus += red[2];
				target.igniteForTicks(ActiveSkill.ticks(red[1]));
			}
			float[] grace = hook(attacker, Hooks.GOLPE_DE_GRACIA);
			if (grace != null) {
				if (target.getHealth() <= target.getMaxHealth() * grace[0] && Threat.of(target) == Threat.NORMAL && !Bosses.isBoss(target)
					&& !(target instanceof Player)) {
					// Done: enough to finish anything that small, armour and all.
					bonus += 10.0F;
				} else if (target.getHealth() > target.getMaxHealth() * grace[1]) {
					bonus -= grace[2];
				}
			}
			float[] frenzy = hook(attacker, Hooks.FRENESI);
			if (frenzy != null && buffs != null && buffs.frenzyUntil >= now) {
				bonus += buffs.frenzy * frenzy[0];
			}
		} else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			bonus += stat(attacker, ClassStat.PROJECTILE_DAMAGE);
			if (head) {
				bonus += stat(attacker, ClassStat.HEADSHOT);
			}
			float[] far = hook(attacker, Hooks.TIRO_LEJANO);
			float[] sniper = hook(attacker, Hooks.FRANCOTIRADOR);
			double distance = attacker.distanceTo(target);
			if (far != null) {
				double past = distance - far[0];
				bonus += (float) Math.max(0.0, Math.min(far[2], past * far[1])) * (sniper != null ? 2.0F : 1.0F);
			}
			if (sniper != null && distance < sniper[0]) {
				bonus -= sniper[1];
			}
			float[] sure = hook(attacker, Hooks.TIRO_CERTERO);
			if (sure != null && arrow.isCritArrow()) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ActiveSkill.ticks(sure[0]), 1), attacker);
			}
			if (arrow.entityTags().contains(ClassEvents.TAG_AIR)) {
				bonus += hookOr(attacker, Hooks.HALCON, 0, 0.0F);
			} else if (arrow.entityTags().contains(ClassEvents.TAG_GROUND)) {
				bonus -= hookOr(attacker, Hooks.HALCON, 1, 0.0F);
			}
			if (arrow.entityTags().contains(ClassEvents.TAG_EAGLE)) {
				bonus += hookOr(attacker, Hooks.OJO_DE_AGUILA, 0, 0.0F);
			}
			float[] mark = hook(attacker, Hooks.MARCA_DEL_CAZADOR);
			if (mark != null) {
				Buffs own = buffs(attacker);
				if (own.huntedUntil >= now && target.getUUID().equals(own.hunted)) {
					bonus += mark[1];
				} else if (own.huntedUntil < now) {
					own.hunted = target.getUUID();
					own.huntedUntil = now + ActiveSkill.ticks(mark[0]);
				}
			}
			if (buffs != null) {
				Long caught = buffs.netted.get(target.getUUID());
				if (caught != null && caught >= now && ActiveSkill.FLECHA_DE_RED.upgraded(attacker)) {
					bonus += ActiveSkill.FLECHA_DE_RED.numbers(attacker)[3];
				}
				if (buffs.nextArrowUntil >= now) {
					bonus += buffs.nextArrowBonus;
					buffs.nextArrowUntil = -1;
				}
			}
		}
		if (buffs != null && buffs.markedUntil >= now && target.getUUID().equals(buffs.marked)) {
			bonus += ActiveSkill.MARCA_DE_MUERTE.numbers(attacker)[2];
		}
		float[] pilgrim = hook(attacker, Hooks.PEREGRINO);
		if (pilgrim != null) {
			bonus -= pilgrim[2];
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

	/**
	 * What the victim's class does to a blow it takes: less of everything, of magic, of fire, Inquebrantable,
	 * Imán de golpes, and Escudo de maná (which pays part of {@code amount} out of the bar).
	 */
	public static float taken(Player victim, DamageSource source, float amount) {
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
		float[] n = hook(victim, Hooks.INQUEBRANTABLE);
		if (n != null && victim.getHealth() <= victim.getMaxHealth() * n[0]) {
			change -= n[1];
		}
		float[] magnet = hook(victim, Hooks.IMAN_DE_GOLPES);
		if (magnet != null && victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(magnet[0]),
			mob -> mob.isAlive() && mob instanceof Enemy).size() >= Math.round(magnet[1])) {
			change -= magnet[2];
		}
		// However much the tree stacks, a class never takes off more than this much of a blow (docs/ARBOLES.md, "Equilibrio").
		float multiplier = Math.max(TAKEN_FLOOR, 1.0F + change);
		float[] shield = hook(victim, Hooks.ESCUDO_DE_MANA);
		if (shield != null && !victim.level().isClientSide() && amount > 0.0F) {
			float share = amount * multiplier * shield[0];
			float mana = share * shield[1];
			if (Mana.trySpend(victim, mana)) {
				multiplier *= 1.0F - shield[0];
			}
		}
		return multiplier;
	}

	/** Guards against Lazo vital passing a blow back and forth between two Curanderos. */
	private static final ThreadLocal<Boolean> SHARING = ThreadLocal.withInitial(() -> false);

	/**
	 * What other players' nodes do to a blow this player takes, whatever its class: Muralla viva (a Tanque in
	 * front with the shield up) and Lazo vital (a Curandero near who takes part of it on themselves).
	 */
	public static float sheltered(Player victim, DamageSource source, float amount) {
		if (victim.level().isClientSide() || SHARING.get() || source.getEntity() == null) {
			return 1.0F;
		}
		float multiplier = 1.0F;
		Vec3 from = source.getSourcePosition();
		for (Player other : victim.level().players()) {
			if (other == victim || !other.isAlive() || other.isSpectator() || ClassProgress.clazz(other) == null) {
				continue;
			}
			float[] wall = hook(other, Hooks.MURALLA_VIVA);
			if (wall != null && from != null && other.isBlocking() && other.distanceTo(victim) <= wall[0]) {
				// The victim is behind the Tanque: the Tanque stands between it and where the blow came from.
				Vec3 toSource = from.subtract(other.position()).multiply(1, 0, 1);
				Vec3 toVictim = victim.position().subtract(other.position()).multiply(1, 0, 1);
				if (toSource.lengthSqr() > 1.0E-4 && toVictim.lengthSqr() > 1.0E-4 && toSource.normalize().dot(toVictim.normalize()) < -0.3) {
					multiplier *= 1.0F - wall[1];
				}
			}
			float[] link = hook(other, Hooks.LAZO_VITAL);
			if (link != null && other.distanceTo(victim) <= link[1] && victim.level() instanceof net.minecraft.server.level.ServerLevel level) {
				float carried = amount * multiplier * link[0];
				multiplier *= 1.0F - link[0];
				SHARING.set(true);
				try {
					other.hurtServer(level, level.damageSources().magic(), carried);
				} finally {
					SHARING.set(false);
				}
			}
		}
		return multiplier;
	}

	// ------------------------------------------------------------------ magic (magic/Spellcasting, magic/Healing)

	/**
	 * The class's and its nodes' spell bonus (SPELL_DAMAGE, added up), put on where a spell is cast. The class's
	 * factor for magic ({@link ClassDamage}) is not in here: it goes on where the spell lands, in {@link #dealt}.
	 */
	public static float spellDamageMultiplier(@Nullable LivingEntity caster) {
		return caster instanceof Player player ? multiplier(player, ClassStat.SPELL_DAMAGE, 0.1F) : 1.0F;
	}

	/** The wait after a spell: the class, its nodes, and Concentración while it lasts. */
	public static float spellCooldownMultiplier(@Nullable LivingEntity caster) {
		if (!(caster instanceof Player player)) {
			return 1.0F;
		}
		float multiplier = multiplier(player, ClassStat.SPELL_COOLDOWN, 0.1F);
		if (concentrating(player)) {
			multiplier *= ActiveSkill.CONCENTRACION.numbers(player)[1];
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

	/** Carga profunda: the spell about to leave was held this far past full (magic/Spellcasting.release). */
	public static void noteOverheld(Player player, boolean overheld) {
		if (has(player, Hooks.CARGA_PROFUNDA)) {
			buffs(player).overheld = overheld;
		}
	}

	/** What the charge does to a spell beyond its bonus: Todo o nada's price on a tap, Carga profunda's extra. */
	public static float spellShape(@Nullable LivingEntity caster, float charge) {
		if (!(caster instanceof Player player)) {
			return 1.0F;
		}
		float shape = 1.0F;
		float[] all = hook(player, Hooks.TODO_O_NADA);
		if (all != null && charge < 1.0F / 3.0F) {
			shape *= 1.0F - all[0];
		}
		float[] deep = hook(player, Hooks.CARGA_PROFUNDA);
		Buffs buffs = BUFFS.get(player.getUUID());
		if (deep != null && buffs != null && buffs.overheld) {
			shape *= 1.0F + deep[1];
			buffs.overheld = false;
		}
		return shape;
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

	/**
	 * Share to add to mana regeneration: it multiplies by (1 + this). Pozo sin fondo, Sangre por maná and Escudo
	 * de maná slow the whole of it, so their price is taken off the total, not the sum.
	 */
	public static float manaRegenBonus(@Nullable Player player) {
		float total = 1.0F + stat(player, ClassStat.MANA_REGEN);
		float[] well = hook(player, Hooks.POZO_SIN_FONDO);
		if (well != null) {
			total *= well[0];
		}
		float[] blood = hook(player, Hooks.SANGRE_POR_MANA);
		if (blood != null) {
			total *= 1.0F - blood[2];
		}
		float[] shield = hook(player, Hooks.ESCUDO_DE_MANA);
		if (shield != null) {
			total *= 1.0F - shield[2];
		}
		return total - 1.0F;
	}

	/**
	 * What a spell's mana costs this player, as a multiplier: Economía arcana, and under Concentración spells
	 * are free. Every spell's cost (staff, tome, lantern) multiplies by this.
	 */
	public static float spellCostMultiplier(@Nullable Player player) {
		if (player == null) {
			return 1.0F;
		}
		if (concentrating(player)) {
			return 0.0F;
		}
		// The gear in the hands (upgrade/TraitEffects.gearSpellCost): the cheapest of Místico, Sideral and Penumbra.
		return multiplier(player, ClassStat.SPELL_COST, 0.0F) * dev.forja.upgrade.TraitEffects.gearSpellCost(player);
	}

	// ------------------------------------------------------------------ bows

	/** How fast a bow draws: the class, its nodes, and Disparo en carrera while the archer moves. */
	public static float drawSpeedMultiplier(@Nullable LivingEntity archer) {
		if (!(archer instanceof Player player)) {
			return 1.0F;
		}
		float multiplier = multiplier(player, ClassStat.DRAW_SPEED, 0.2F);
		float[] running = hook(player, Hooks.DISPARO_EN_CARRERA);
		if (running != null && Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) > 0.03) {
			multiplier *= 1.0F + running[0];
		}
		return multiplier;
	}

	/** Ráfaga: the draw at which a forged bow already shoots as hard as it can (1 without it). */
	public static float fullDrawAt(@Nullable LivingEntity archer) {
		return archer instanceof Player player ? hookOr(player, Hooks.RAFAGA, 0, 1.0F) : 1.0F;
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

	/** Extra load on what this player forges (Carga honda; forge/Potential.capacity). */
	public static int capacityBonus(@Nullable Player player) {
		return Math.round(stat(player, ClassStat.CAPACITY));
	}

	/** How much of a piece one repair ingot mends, as a multiplier (menu/ForgeMenu.planRepair). */
	public static float repairMultiplier(@Nullable Player player) {
		return multiplier(player, ClassStat.REPAIR, 0.1F);
	}

	/** The extra share every upgrade takes (forge/SmithLevel.upgradeBonus). */
	public static int upgradeBonus(@Nullable Player player) {
		return Math.round(stat(player, ClassStat.UPGRADE_BONUS));
	}

	/** Fuelle: how much faster an assembler at this spot works, from the best player with it near. */
	public static float foundryBoost(Level level, BlockPos pos) {
		return bestNear(level, pos, Hooks.FUELLE, ClassStat.FOUNDRY_SPEED);
	}

	/** Ajuste fino: the potential an assembler at this spot adds to what it makes. */
	public static int assemblerPotential(Level level, BlockPos pos) {
		return Math.round(bestNear(level, pos, Hooks.AJUSTE_FINO, ClassStat.ASSEMBLER_POTENTIAL));
	}

	private static float bestNear(Level level, BlockPos pos, String hook, ClassStat stat) {
		float best = 0.0F;
		for (Player player : level.players()) {
			float[] n = hook(player, hook);
			if (n != null && !player.isSpectator() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= n[0] * n[0]) {
				best = Math.max(best, stat(player, stat));
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ skill waits

	/** Keystones that stretch a skill's wait: Fantasma (Paso sombrío), Tierra sagrada (Pulso sanador). */
	public static float skillCooldownMultiplier(@Nullable Player player, ActiveSkill skill) {
		if (skill == ActiveSkill.PASO_SOMBRIO) {
			return 1.0F + hookOr(player, Hooks.FANTASMA, 0, 0.0F);
		}
		if (skill == ActiveSkill.PULSO_SANADOR) {
			return 1.0F + hookOr(player, Hooks.TIERRA_SAGRADA, 2, 0.0F);
		}
		return 1.0F;
	}

	private static float hookOr(@Nullable Player player, String hook, int index, float otherwise) {
		float[] n = hook(player, hook);
		return n == null ? otherwise : n[index];
	}

	// ------------------------------------------------------------------ what the skills and nodes leave running

	static void nextHit(Player player, float bonus, int ticks) {
		Buffs buffs = buffs(player);
		buffs.nextHitUntil = player.level().getGameTime() + ticks;
		buffs.nextHitBonus = bonus;
	}

	static void nextArrow(Player player, float bonus, int ticks) {
		Buffs buffs = buffs(player);
		buffs.nextArrowUntil = player.level().getGameTime() + ticks;
		buffs.nextArrowBonus = bonus;
	}

	static void ghost(Player player) {
		buffs(player).ghostSpared = true;
	}

	static void mark(Player player, LivingEntity target, int ticks) {
		Buffs buffs = buffs(player);
		buffs.marked = target.getUUID();
		buffs.markedUntil = player.level().getGameTime() + ticks;
	}

	/** Whether this mob is the one this player marked, still. */
	static boolean marked(Player player, LivingEntity target) {
		Buffs buffs = BUFFS.get(player.getUUID());
		return buffs != null && buffs.markedUntil >= player.level().getGameTime() && target.getUUID().equals(buffs.marked);
	}

	static void net(Player player, LivingEntity target, int ticks) {
		buffs(player).netted.put(target.getUUID(), player.level().getGameTime() + ticks);
	}

	/** Forja al rojo: a perfect forge sets the arm alight (menu/ForgeMenu, on the press). */
	public static void perfectForge(Player player) {
		float[] n = hook(player, Hooks.FORJA_AL_ROJO);
		if (n != null) {
			buffs(player).redHotUntil = player.level().getGameTime() + ActiveSkill.ticks(n[0]);
		}
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

	static boolean lastStandRecharging(Player player) {
		Buffs buffs = BUFFS.get(player.getUUID());
		return buffs != null && player.level().getGameTime() < buffs.lastStandReady;
	}

	/** Égida: whether a shield is due, and if so, schedules the next one. */
	static boolean aegisDue(Player player, int everyTicks) {
		return due(player, everyTicks, 0);
	}

	/** Espejismo: whether it can work now, and if so, spends it. */
	static boolean mirageReady(Player player, int everyTicks) {
		return due(player, everyTicks, 1);
	}

	/** Rocío. */
	public static boolean dewReady(Player player, int everyTicks) {
		return due(player, everyTicks, 2);
	}

	/** Mártir: at most once a second. */
	public static boolean martyrReady(Player player) {
		return due(player, 20, 3);
	}

	private static boolean due(Player player, int everyTicks, int which) {
		Buffs buffs = buffs(player);
		long now = player.level().getGameTime();
		long ready = switch (which) {
			case 0 -> buffs.aegisDue;
			case 1 -> buffs.mirageReady;
			case 2 -> buffs.dewReady;
			default -> buffs.martyrReady;
		};
		if (now < ready) {
			return false;
		}
		switch (which) {
			case 0 -> buffs.aegisDue = now + everyTicks;
			case 1 -> buffs.mirageReady = now + everyTicks;
			case 2 -> buffs.dewReady = now + everyTicks;
			default -> buffs.martyrReady = now + everyTicks;
		}
		return true;
	}

	/** Frenesí: a melee kill adds a stack and starts the clock again. */
	static void frenzy(Player player, int most, int ticks) {
		Buffs buffs = buffs(player);
		long now = player.level().getGameTime();
		buffs.frenzy = buffs.frenzyUntil >= now ? Math.min(most, buffs.frenzy + 1) : 1;
		buffs.frenzyUntil = now + ticks;
	}

	static int frenzyStacks(Player player) {
		Buffs buffs = BUFFS.get(player.getUUID());
		return buffs == null || buffs.frenzyUntil < player.level().getGameTime() ? 0 : buffs.frenzy;
	}

	/** Struck: Frenesí is lost, and Vendaje starts counting again. */
	static void hurt(Player player) {
		Buffs buffs = buffs(player);
		buffs.frenzy = 0;
		buffs.frenzyUntil = -1;
		buffs.lastHurt = player.level().getGameTime();
	}

	static long lastHurt(Player player) {
		return buffs(player).lastHurt;
	}

	/** How far the player was falling last tick, and now this tick's (Funámbulo, Pisotón). */
	static float swapFalling(Player player, float now) {
		Buffs buffs = buffs(player);
		float before = buffs.falling;
		buffs.falling = now;
		return before;
	}
}
