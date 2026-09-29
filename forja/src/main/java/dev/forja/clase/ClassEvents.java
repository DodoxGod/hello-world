package dev.forja.clase;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.forja.combat.Stamina;
import dev.forja.difficulty.Bosses;
import dev.forja.difficulty.Threat;
import dev.forja.entity.MagicBolt;
import dev.forja.entity.Shockwave;
import dev.forja.magic.Healing;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * Everything the class system listens for: where class experience comes from (docs/CLASES.md), the
 * attributes put back after a respawn or a login, and the talents that are events rather than numbers.
 */
public final class ClassEvents {
	/** Experience for the things only one class is paid for. */
	public static final int XP_PARRY = 3;
	public static final int XP_STAGGER = 4;
	public static final int XP_PERFECT_DODGE = 3;
	public static final int XP_BACKSTAB = 2;
	public static final int XP_SPELL_HIT = 1;
	public static final int XP_ARROW_HIT = 1;
	/** Arquero: one more for every this many blocks the arrow flew. */
	public static final int ARROW_BLOCKS_PER_XP = 10;
	/** Tanque: damage stopped or taken per point. */
	public static final float TANK_DAMAGE_PER_XP = 4.0F;
	/** A kill that is the class's own kind of kill is worth this much more. */
	public static final float OWN_KILL_BONUS = 0.5F;
	public static final int BOSS_KILL_FACTOR = 10;

	private static final Map<UUID, Float> TANK_OWED = new HashMap<>();

	private ClassEvents() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			ClassAttributes.sync(player);
			if (ClassProgress.clazz(player) == null) {
				player.sendSystemMessage(Component.translatable("gui.forja.clase.aviso", ClassProgress.key(ClassProgress.KEY_GUIDE),
					ClassProgress.key(ClassProgress.KEY_TREE)).withColor(0xFFF0C070));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ClassEffects.forget(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ClassAttributes.sync(newPlayer);
			if (!alive) {
				newPlayer.setHealth(newPlayer.getMaxHealth());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(ClassEvents::afterDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ClassEvents::afterDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ClassEvents::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(ClassEvents::allowDeath);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// Flecha veloz: an arrow of this archer's leaves the string faster.
			if (entity instanceof AbstractArrow arrow && entity.tickCount == 0 && arrow.getOwner() instanceof Player owner
				&& !arrow.entityTags().contains(ClassSkills.RAIN_TAG)) {
				float faster = ClassEffects.arrowSpeedMultiplier(owner);
				if (faster != 1.0F) {
					arrow.setDeltaMovement(arrow.getDeltaMovement().scale(faster));
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				passives(player);
			}
		});
	}

	/** The talents that tick: Recuperación, Aura and Égida. Once a second, on each player with a class. */
	public static void passives(ServerPlayer player) {
		if (!player.isAlive() || ClassProgress.clazz(player) == null) {
			return;
		}
		int second = player.level().getServer().getTickCount() / 20;
		if (ClassEffects.has(player, Talent.TANQUE_RECUPERACION)) {
			float[] n = Talent.TANQUE_RECUPERACION.numbers;
			if (second % Math.max(1, Math.round(n[1])) == 0 && player.getHealth() < player.getMaxHealth()) {
				player.heal(n[0]);
			}
		}
		if (ClassEffects.has(player, Talent.CURANDERO_AURA)) {
			float[] n = Talent.CURANDERO_AURA.numbers;
			if (second % Math.max(1, Math.round(n[2])) == 0) {
				ServerLevel level = player.level();
				for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(n[0]),
					e -> (e == player || Healing.ally(player, e)) && e.distanceTo(player) <= n[0] && e.getHealth() < e.getMaxHealth())) {
					Healing.mend(level, player, ally, n[1]);
				}
			}
		}
		if (ClassEffects.has(player, Talent.MAGO_EGIDA)) {
			float[] n = Talent.MAGO_EGIDA.numbers;
			if (ClassEffects.aegisDue(player, Math.round(n[0] * 20.0F)) && player.getAbsorptionAmount() < n[1]) {
				player.setAbsorptionAmount(n[1]);
				player.level().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.4);
			}
		}
	}

	// ------------------------------------------------------------------ class experience

	/** A kill: what the monster was worth to everyone, and more when it was the class's own kind of kill. */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || !(entity instanceof Enemy)) {
			return;
		}
		PlayerClass clazz = ClassProgress.clazz(killer);
		if (clazz == null) {
			return;
		}
		boolean melee = source.getDirectEntity() == killer;
		if (melee && ClassEffects.has(killer, Talent.ASESINO_GOLPE_LETAL)) {
			float[] n = Talent.ASESINO_GOLPE_LETAL.numbers;
			Stamina.restore(killer, n[0]);
			killer.addEffect(new MobEffectInstance(MobEffects.SPEED, Math.round(n[1] * 20.0F), 1), killer);
		}
		ClassProgress.award(killer, killXp(entity, clazz, source, killer));
	}

	/** max(1, round(max health / 4)) × threat, a boss ×10, and half again for the class's own kind of kill. */
	public static int killXp(LivingEntity victim, PlayerClass clazz, DamageSource source, Player killer) {
		int base = Math.max(1, Math.round(victim.getMaxHealth() / 4.0F));
		int threat = switch (Threat.of(victim)) {
			case NORMAL -> 1;
			case VETERANO -> 2;
			case ELITE -> 4;
			case CAMPEON -> 8;
		};
		float xp = base * threat * (Bosses.isBoss(victim) ? BOSS_KILL_FACTOR : 1);
		boolean melee = source.getDirectEntity() == killer;
		boolean spell = source.getDirectEntity() instanceof MagicBolt || source.getDirectEntity() instanceof Shockwave;
		boolean arrow = source.getDirectEntity() instanceof Projectile && !spell;
		boolean own = switch (clazz) {
			case GUERRERO, ASESINO, TANQUE -> melee;
			case MAGO -> spell;
			case ARQUERO -> arrow;
			default -> false;
		};
		return Math.round(xp * (own ? 1.0F + OWN_KILL_BONUS : 1.0F));
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (damageTaken <= 0.0F || blocked) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && entity instanceof Enemy) {
			PlayerClass clazz = ClassProgress.clazz(attacker);
			if (clazz == PlayerClass.ASESINO && source.getDirectEntity() == attacker && ClassEffects.behind(entity, attacker)) {
				ClassProgress.award(attacker, XP_BACKSTAB);
			} else if (clazz == PlayerClass.MAGO && (source.getDirectEntity() instanceof MagicBolt || source.getDirectEntity() instanceof Shockwave)) {
				ClassProgress.award(attacker, XP_SPELL_HIT);
			} else if (clazz == PlayerClass.ARQUERO && source.getDirectEntity() instanceof AbstractArrow) {
				ClassProgress.award(attacker, XP_ARROW_HIT + (int) (attacker.distanceTo(entity) / ARROW_BLOCKS_PER_XP));
			}
		}
		if (entity instanceof ServerPlayer victim && victim.isAlive() && source.getEntity() != null && source.getEntity() != victim) {
			tank(victim, damageTaken);
		}
	}

	private static void tank(ServerPlayer player, float damage) {
		if (!ClassEffects.is(player, PlayerClass.TANQUE)) {
			return;
		}
		float owed = TANK_OWED.getOrDefault(player.getUUID(), 0.0F) + damage;
		int xp = (int) (owed / TANK_DAMAGE_PER_XP);
		TANK_OWED.put(player.getUUID(), owed - xp * TANK_DAMAGE_PER_XP);
		ClassProgress.award(player, xp);
	}

	/** Evasión: now and then an arrow or a bolt meant for the Asesino simply misses. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer player && source.getDirectEntity() instanceof Projectile && source.getEntity() != player
			&& ClassEffects.has(player, Talent.ASESINO_EVASION)
			&& player.getRandom().nextFloat() < Talent.ASESINO_EVASION.numbers[0]) {
			player.level().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
			player.sendOverlayMessage(Component.translatable("gui.forja.talento.evasion"));
			return false;
		}
		return true;
	}

	/** Último bastión: a blow that would kill a Tanque leaves them standing, once every few minutes. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer player && ClassEffects.has(player, Talent.TANQUE_ULTIMO_BASTION)
			&& !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			float[] n = Talent.TANQUE_ULTIMO_BASTION.numbers;
			if (ClassEffects.spendLastStand(player, Math.round(n[0] * 60.0F * 20.0F))) {
				player.setHealth(1.0F);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, Math.round(n[1] * 20.0F), 2), player);
				player.level().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 0.8F);
				player.sendOverlayMessage(Component.translatable("gui.forja.talento.ultimo_bastion"));
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ hooks the rest of the mod calls

	/** A parry landed (upgrade/CombatUpgrades.parry): the Guerrero's experience, and Réplica. */
	public static void onParry(LivingEntity defender) {
		if (!(defender instanceof ServerPlayer player) || ClassProgress.clazz(player) == null) {
			return;
		}
		if (ClassEffects.is(player, PlayerClass.GUERRERO)) {
			ClassProgress.award(player, XP_PARRY);
		}
		if (ClassEffects.has(player, Talent.GUERRERO_REPLICA)) {
			float[] n = Talent.GUERRERO_REPLICA.numbers;
			Stamina.restore(player, n[0]);
			ClassEffects.nextHit(player, n[2], Math.round(n[1] * 20.0F));
		}
	}

	/**
	 * A raised shield took a blow (combat/CombatHooks.allowDamage): Represalia strikes back at whoever swung,
	 * and the Tanque is paid for what it stopped.
	 */
	public static void onShieldBlock(Player player, DamageSource source, float amount) {
		if (!(player instanceof ServerPlayer server) || ClassProgress.clazz(player) == null) {
			return;
		}
		if (ClassEffects.has(player, Talent.TANQUE_REPRESALIA) && source.getEntity() instanceof LivingEntity swinger
			&& source.getDirectEntity() == swinger && swinger != player) {
			swinger.hurtServer(server.level(), server.level().damageSources().thorns(player), Talent.TANQUE_REPRESALIA.numbers[0]);
		}
		tank(server, amount);
	}

	/** A dodge that met a blow (combat/Stamina.markPerfectDodge). */
	public static void onPerfectDodge(Player player) {
		if (ClassEffects.is(player, PlayerClass.ASESINO)) {
			ClassProgress.award(player, XP_PERFECT_DODGE);
		}
	}

	/** A blow of this player's broke a monster's posture (combat/CombatHooks.afterArmor). */
	public static void onStagger(Player player) {
		if (ClassEffects.is(player, PlayerClass.GUERRERO)) {
			ClassProgress.award(player, XP_STAGGER);
		}
	}

	/** What the smith's Maestria is paid (forge/SmithLevel.award) is the Herrero's class experience too. */
	public static void onSmithXp(Player player, int amount) {
		if (ClassEffects.is(player, PlayerClass.HERRERO)) {
			ClassProgress.award(player, amount);
		}
	}

	/** Health a Curandero mended on others, already turned into experience (magic/Healing.mend). */
	public static void healed(Player player, int xp) {
		ClassProgress.award(player, xp);
	}
}
