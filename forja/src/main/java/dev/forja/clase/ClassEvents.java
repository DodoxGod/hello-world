package dev.forja.clase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.forja.combat.Posture;
import dev.forja.combat.Stamina;
import dev.forja.difficulty.Bosses;
import dev.forja.difficulty.Threat;
import dev.forja.entity.MagicBolt;
import dev.forja.entity.Shockwave;
import dev.forja.magic.Healing;
import dev.forja.magic.Mana;
import dev.forja.registry.ModComponents;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Everything the class system listens for: where class experience comes from (docs/CLASES.md), the
 * attributes put back after a respawn or a login, and the tree's nodes that are events rather than numbers
 * (docs/ARBOLES.md).
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
	/** Halcón: arrows shot in the air, and on the ground. Ojo de águila: shot sneaking. */
	public static final String TAG_AIR = "forja_halcon_aire";
	public static final String TAG_GROUND = "forja_halcon_suelo";
	public static final String TAG_EAGLE = "forja_ojo_de_aguila";
	/** Ojo de águila: how long an arrow flies straight before it starts to fall (about forty blocks). */
	public static final int EAGLE_TICKS = 14;
	/** Funámbulo and Pisotón: how long the tree keeps a fall in mind once you land. */
	private static final Map<UUID, Float> TANK_OWED = new HashMap<>();
	/** Arrows of Ojo de águila still flying straight, and when they start to fall. */
	private static final Map<AbstractArrow, Long> STRAIGHT = new java.util.WeakHashMap<>();
	/** Hechizo encadenado: a bolt's leap is itself a spell landing, and must not leap again. */
	private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> false);

	private ClassEvents() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			ClassProgress.migrate(player);
			ClassAttributes.sync(player);
			Milestones.check(player);
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
			if (entity instanceof AbstractArrow arrow && entity.tickCount == 0 && arrow.getOwner() instanceof ServerPlayer owner
				&& !arrow.entityTags().contains(ClassSkills.RAIN_TAG) && !arrow.entityTags().contains(ClassSkills.KNIFE_TAG)) {
				shot(arrow, owner);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.overworld().getGameTime();
			STRAIGHT.entrySet().removeIf(entry -> {
				if (entry.getKey().isRemoved() || now >= entry.getValue()) {
					entry.getKey().setNoGravity(false);
					return true;
				}
				return false;
			});
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				landing(player);
			}
			if (server.getTickCount() % 20 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				passives(player);
			}
		});
	}

	/** An arrow of this archer's just left the string: Flecha veloz, Flecha perforante, Halcón, Ojo de águila. */
	private static void shot(AbstractArrow arrow, ServerPlayer owner) {
		float faster = ClassEffects.arrowSpeedMultiplier(owner);
		if (faster != 1.0F) {
			arrow.setDeltaMovement(arrow.getDeltaMovement().scale(faster));
		}
		float[] pierce = ClassEffects.hook(owner, Hooks.FLECHA_PERFORANTE);
		if (pierce != null && arrow.isCritArrow()) {
			((dev.forja.mixin.AbstractArrowAccess) arrow).forja$setPierceLevel((byte) Math.max(arrow.getPierceLevel(), Math.round(pierce[0])));
		}
		if (ClassEffects.has(owner, Hooks.HALCON)) {
			arrow.addTag(owner.onGround() ? TAG_GROUND : TAG_AIR);
		}
		if (ClassEffects.has(owner, Hooks.OJO_DE_AGUILA) && owner.isShiftKeyDown()) {
			arrow.addTag(TAG_EAGLE);
			arrow.setNoGravity(true);
			STRAIGHT.put(arrow, owner.level().getGameTime() + EAGLE_TICKS);
		}
	}

	/** Funámbulo and Pisotón: the tick a player lands after a fall of more than a few blocks. */
	private static void landing(ServerPlayer player) {
		if (ClassProgress.clazz(player) == null) {
			return;
		}
		float[] tightrope = ClassEffects.hook(player, Hooks.FUNAMBULO);
		float[] stomp = ClassEffects.hook(player, Hooks.PISOTON);
		if (tightrope == null && stomp == null) {
			return;
		}
		float before = ClassEffects.swapFalling(player, (float) player.fallDistance);
		if (!player.onGround() || player.fallDistance > 0.0F) {
			return;
		}
		if (tightrope != null && before > tightrope[0]) {
			ClassEffects.nextHit(player, tightrope[2], ActiveSkill.ticks(tightrope[1]));
		}
		if (stomp != null && before >= stomp[0]) {
			ServerLevel level = player.level();
			long now = level.getGameTime();
			Shockwave.burst(level, player.position(), stomp[1], 10, 0x8C99A6, 0.5F);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(stomp[1]),
				m -> m.isAlive() && m instanceof Enemy && m.distanceTo(player) <= stomp[1])) {
				Vec3 away = mob.position().subtract(player.position()).multiply(1, 0, 1);
				if (away.lengthSqr() > 1.0E-4) {
					away = away.normalize().scale(0.8);
					mob.setDeltaMovement(away.x, 0.3, away.z);
					mob.hurtMarked = true;
				}
				Posture.push(mob, stomp[2], now);
			}
		}
	}

	/** The nodes that tick, once a second on each player with a class. */
	public static void passives(ServerPlayer player) {
		if (!player.isAlive() || ClassProgress.clazz(player) == null) {
			return;
		}
		ServerLevel level = player.level();
		int second = level.getServer().getTickCount() / 20;
		float[] recovery = ClassEffects.hook(player, Hooks.RECUPERACION);
		if (recovery != null && second % Math.max(1, Math.round(recovery[1])) == 0 && player.getHealth() < player.getMaxHealth()) {
			player.heal(recovery[0]);
		}
		float[] aura = ClassEffects.hook(player, Hooks.AURA);
		if (aura != null) {
			float[] pilgrim = ClassEffects.hook(player, Hooks.PEREGRINO);
			float reach = pilgrim != null ? pilgrim[0] : aura[0];
			float amount = pilgrim != null ? pilgrim[1] : aura[1];
			if (second % Math.max(1, Math.round(aura[2])) == 0) {
				for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
					e -> (e == player || Healing.ally(player, e)) && e.distanceTo(player) <= reach && e.getHealth() < e.getMaxHealth())) {
					Healing.mend(level, player, ally, amount);
				}
			}
		}
		float[] aegis = ClassEffects.hook(player, Hooks.EGIDA);
		if (aegis != null && ClassEffects.aegisDue(player, Math.round(aegis[0] * 20.0F)) && player.getAbsorptionAmount() < aegis[1]) {
			player.setAbsorptionAmount(aegis[1]);
			level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.4);
		}
		float[] bandage = ClassEffects.hook(player, Hooks.VENDAJE);
		if (bandage != null && second % Math.max(1, Math.round(bandage[0])) == 0 && player.getHealth() < player.getMaxHealth()
			&& level.getGameTime() - ClassEffects.lastHurt(player) >= bandage[0] * 20.0F) {
			player.heal(bandage[1]);
		}
		float[] magnet = ClassEffects.hook(player, Hooks.IMAN_DE_GOLPES);
		if (magnet != null) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(magnet[0]),
				m -> m.isAlive() && m instanceof Enemy && m.distanceTo(player) <= magnet[0] && m.getTarget() != player)) {
				mob.setTarget(player);
			}
		}
		// Temple de campaña: once a minute, forged gear mends a little on its own.
		if (second % 60 == 0) {
			float[] two = ClassEffects.hook(player, Hooks.TEMPLE_DE_CAMPANA_II);
			float[] one = ClassEffects.hook(player, Hooks.TEMPLE_DE_CAMPANA);
			if (two != null) {
				temper(player.getMainHandItem(), two[0]);
				for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					temper(player.getItemBySlot(slot), two[0]);
				}
			} else if (one != null) {
				temper(player.getMainHandItem(), one[0]);
			}
		}
		// Último bastión's price (less health while it recharges) comes and goes with the recharge.
		if (ClassEffects.has(player, Hooks.ULTIMO_BASTION)) {
			ClassAttributes.sync(player);
		}
	}

	/** Temple de campaña: a forged piece mends this share of itself. */
	public static void temper(ItemStack stack, float share) {
		if (!stack.has(ModComponents.PARTS) || !stack.isDamageableItem() || !stack.isDamaged()) {
			return;
		}
		int mend = Math.max(1, Math.round(stack.getMaxDamage() * share));
		stack.setDamageValue(Math.max(0, stack.getDamageValue() - mend));
	}

	// ------------------------------------------------------------------ kills, blows and class experience

	/** A kill: what the monster was worth, the milestones, and the nodes that feed on kills. */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		// Marca de muerte II: whoever marked it gets part of the wait back, whoever made the kill.
		for (ServerPlayer player : entity.level() instanceof ServerLevel level ? level.players() : List.<ServerPlayer>of()) {
			if (ClassEffects.marked(player, entity) && ActiveSkill.MARCA_DE_MUERTE.upgraded(player)) {
				float cut = ActiveSkill.MARCA_DE_MUERTE.numbers(player)[3];
				long now = player.level().getGameTime();
				ClassData data = ClassProgress.data(player);
				long left = Math.max(0L, data.secondReady() - now);
				ClassProgress.set(player, data.withReady(2, now + Math.round(left * (1.0F - cut))));
			}
		}
		if (!(source.getEntity() instanceof ServerPlayer killer)) {
			return;
		}
		Milestones.onKill(killer, entity);
		if (!(entity instanceof Enemy)) {
			return;
		}
		PlayerClass clazz = ClassProgress.clazz(killer);
		if (clazz == null) {
			return;
		}
		boolean melee = source.getDirectEntity() == killer;
		if (melee) {
			float[] lethal = ClassEffects.hook(killer, Hooks.GOLPE_LETAL);
			if (lethal != null) {
				Stamina.restore(killer, lethal[0]);
				killer.addEffect(new MobEffectInstance(MobEffects.SPEED, ActiveSkill.ticks(lethal[1]), 1), killer);
			}
			float[] frenzy = ClassEffects.hook(killer, Hooks.FRENESI);
			if (frenzy != null) {
				ClassEffects.frenzy(killer, Math.round(frenzy[2]), ActiveSkill.ticks(frenzy[1]));
			}
			float[] thirst = ClassEffects.hook(killer, Hooks.SED_DE_SANGRE);
			if (thirst != null) {
				killer.heal(killer.getMaxHealth() * thirst[0]);
			}
		}
		float[] catalyst = ClassEffects.hook(killer, Hooks.CATALIZADOR);
		if (catalyst != null && ClassDamage.magic(source)) {
			Mana.give(killer, catalyst[0]);
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
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity) {
			struck(attacker, entity, source, damageTaken);
		}
		if (entity instanceof ServerPlayer victim && victim.isAlive() && source.getEntity() != null && source.getEntity() != victim) {
			ClassEffects.hurt(victim);
			float[] adrenaline = ClassEffects.hook(victim, Hooks.ADRENALINA);
			if (adrenaline != null) {
				Stamina.restore(victim, adrenaline[0]);
			}
			tank(victim, damageTaken);
		}
	}

	/** A blow of this player's landed on something: class experience and the nodes that follow a blow. */
	private static void struck(ServerPlayer attacker, LivingEntity entity, DamageSource source, float damage) {
		if (source.getDirectEntity() instanceof AbstractArrow knife && knife.entityTags().contains(ClassSkills.KNIFE_TAG)) {
			ActiveSkill fan = ActiveSkill.ABANICO_DE_DAGAS;
			boolean strong = knife.entityTags().contains(ClassSkills.KNIFE_TAG + "_ii");
			entity.addEffect(new MobEffectInstance(MobEffects.POISON, ActiveSkill.ticks(fan.numbers(attacker)[3]), strong ? 1 : 0), attacker);
		}
		if (ClassDamage.magic(source)) {
			float[] frost = ClassEffects.hook(attacker, Hooks.RUNA_DE_ESCARCHA);
			if (frost != null) {
				entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ActiveSkill.ticks(frost[0]), 0), attacker);
			}
			float[] chain = ClassEffects.hook(attacker, Hooks.HECHIZO_ENCADENADO);
			if (chain != null && source.getDirectEntity() instanceof MagicBolt && !CHAINING.get() && entity.level() instanceof ServerLevel level) {
				leap(level, attacker, entity, damage * chain[1], chain[0]);
			}
		}
		if (source.getDirectEntity() == attacker) {
			float[] faith = ClassEffects.hook(attacker, Hooks.MARTILLO_DE_LA_FE);
			if (faith != null && attacker.level() instanceof ServerLevel level) {
				for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(faith[0]),
					e -> e != attacker && Healing.ally(attacker, e) && e.distanceTo(attacker) <= faith[0])) {
					Healing.mend(level, attacker, ally, damage * faith[1]);
				}
			}
		}
		if (!(entity instanceof Enemy)) {
			return;
		}
		PlayerClass clazz = ClassProgress.clazz(attacker);
		if (clazz == PlayerClass.ASESINO && source.getDirectEntity() == attacker && ClassEffects.behind(entity, attacker)) {
			ClassProgress.award(attacker, XP_BACKSTAB);
		} else if (clazz == PlayerClass.MAGO && (source.getDirectEntity() instanceof MagicBolt || source.getDirectEntity() instanceof Shockwave)) {
			ClassProgress.award(attacker, XP_SPELL_HIT);
		} else if (clazz == PlayerClass.ARQUERO && source.getDirectEntity() instanceof AbstractArrow) {
			ClassProgress.award(attacker, XP_ARROW_HIT + (int) (attacker.distanceTo(entity) / ARROW_BLOCKS_PER_XP));
		}
	}

	/** Hechizo encadenado: the bolt's bite leaps on to the nearest other foe. */
	private static void leap(ServerLevel level, ServerPlayer caster, LivingEntity from, float damage, float reach) {
		LivingEntity next = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(reach),
			e -> e != from && e.isAlive() && e instanceof Enemy && e.distanceTo(from) <= reach)) {
			double d = other.distanceToSqr(from);
			if (d < best) {
				best = d;
				next = other;
			}
		}
		if (next == null) {
			return;
		}
		Vec3 a = from.position().add(0, from.getBbHeight() * 0.6, 0);
		Vec3 b = next.position().add(0, next.getBbHeight() * 0.6, 0);
		for (int step = 0; step <= 8; step++) {
			Vec3 p = a.lerp(b, step / 8.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		CHAINING.set(true);
		try {
			next.invulnerableTime = 0;
			next.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);
		} finally {
			CHAINING.set(false);
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
		if (entity instanceof ServerPlayer player && source.getDirectEntity() instanceof Projectile && source.getEntity() != player) {
			float[] evasion = ClassEffects.hook(player, Hooks.EVASION);
			if (evasion != null && player.getRandom().nextFloat() < evasion[0]) {
				player.level().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
				player.sendOverlayMessage(Component.translatable("gui.forja.talento.evasion"));
				return false;
			}
		}
		return true;
	}

	/** Último bastión: a blow that would kill a Tanque leaves them standing, once every few minutes. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer player && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			float[] n = ClassEffects.hook(player, Hooks.ULTIMO_BASTION);
			if (n != null && ClassEffects.spendLastStand(player, Math.round(n[0] * 60.0F * 20.0F))) {
				player.setHealth(1.0F);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ActiveSkill.ticks(n[1]), 2), player);
				player.level().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 0.8F);
				player.sendOverlayMessage(Component.translatable("gui.forja.talento.ultimo_bastion"));
				ClassAttributes.sync(player);
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ hooks the rest of the mod calls

	/** A parry landed (upgrade/CombatUpgrades.parry): the Guerrero's experience, Réplica and Filo de vuelta. */
	public static void onParry(LivingEntity defender, net.minecraft.world.entity.@Nullable Entity attacker) {
		if (!(defender instanceof ServerPlayer player) || ClassProgress.clazz(player) == null) {
			return;
		}
		if (ClassEffects.is(player, PlayerClass.GUERRERO)) {
			ClassProgress.award(player, XP_PARRY);
		}
		float[] riposte = ClassEffects.hook(player, Hooks.REPLICA);
		if (riposte != null) {
			Stamina.restore(player, riposte[0]);
			ClassEffects.nextHit(player, riposte[2], ActiveSkill.ticks(riposte[1]));
		}
		float[] lesser = ClassEffects.hook(player, Hooks.REPLICA_MENOR);
		if (lesser != null) {
			Stamina.restore(player, lesser[0]);
		}
		float[] edge = ClassEffects.hook(player, Hooks.FILO_DE_VUELTA);
		if (edge != null && attacker instanceof LivingEntity struck) {
			Posture.push(struck, edge[0], player.level().getGameTime());
		}
	}

	/**
	 * A raised shield took a blow (combat/CombatHooks.allowDamage): Represalia and Espinas de acero strike back
	 * at whoever swung, and the Tanque is paid for what it stopped.
	 */
	public static void onShieldBlock(Player player, DamageSource source, float amount) {
		if (!(player instanceof ServerPlayer server) || ClassProgress.clazz(player) == null) {
			return;
		}
		if (source.getEntity() instanceof LivingEntity swinger && source.getDirectEntity() == swinger && swinger != player) {
			float back = 0.0F;
			float[] retaliation = ClassEffects.hook(player, Hooks.REPRESALIA);
			float[] lesser = ClassEffects.hook(player, Hooks.REPRESALIA_MENOR);
			back += retaliation != null ? retaliation[0] : lesser != null ? lesser[0] : 0.0F;
			float[] thorns = ClassEffects.hook(player, Hooks.ESPINAS_DE_ACERO);
			if (thorns != null) {
				back += amount * thorns[0];
			}
			if (back > 0.0F) {
				swinger.hurtServer(server.level(), server.level().damageSources().thorns(player), back);
			}
		}
		tank(server, amount);
	}

	/** A dodge that met a blow (combat/Stamina.markPerfectDodge): the Asesino's experience and Filo del viento. */
	public static void onPerfectDodge(Player player) {
		if (ClassEffects.is(player, PlayerClass.ASESINO)) {
			ClassProgress.award(player, XP_PERFECT_DODGE);
		}
		float[] wind = ClassEffects.hook(player, Hooks.FILO_DEL_VIENTO);
		if (wind != null) {
			ClassEffects.nextHit(player, wind[1], ActiveSkill.ticks(wind[0]));
		}
	}

	/** A dodge the server accepted (combat/Stamina.onDodge): Espejismo, Sin sombra and Parpadeo. */
	public static void onDodge(ServerPlayer player) {
		if (ClassProgress.clazz(player) == null) {
			return;
		}
		ServerLevel level = player.level();
		float[] mirage = ClassEffects.hook(player, Hooks.ESPEJISMO);
		if (mirage != null && ClassEffects.mirageReady(player, ActiveSkill.ticks(mirage[1]))) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(mirage[0]),
				m -> m.getTarget() == player && m.distanceTo(player) <= mirage[0])) {
				mob.setTarget(null);
			}
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
		}
		float[] shadowless = ClassEffects.hook(player, Hooks.SIN_SOMBRA);
		if (shadowless != null && player.getHealth() > 1.0F) {
			player.setHealth(Math.max(1.0F, player.getHealth() - shadowless[0]));
		}
		float[] blink = ClassEffects.hook(player, Hooks.PARPADEO);
		if (blink != null && Mana.trySpend(player, blink[1])) {
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ActiveSkill.ticks(blink[0]), 0, false, false), player);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(0.5), player.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
		}
	}

	/** A blow of this player's broke a monster's posture (combat/CombatHooks.afterArmor). */
	public static void onStagger(Player player) {
		if (ClassEffects.is(player, PlayerClass.GUERRERO)) {
			ClassProgress.award(player, XP_STAGGER);
		}
	}

	/** A charged blow landed (combat/CombatHooks.afterArmor): Carga brutal breaks a normal monster's posture. */
	public static void onChargedHit(Player player, LivingEntity target) {
		if (ClassEffects.has(player, Hooks.CARGA_BRUTAL) && Threat.of(target) == Threat.NORMAL && !Bosses.isBoss(target)) {
			Posture.breakPosture(target, player.level().getGameTime());
		}
	}

	/** Health a Curandero mended on others, already turned into experience (magic/Healing.mend). */
	public static void healed(Player player, int xp) {
		ClassProgress.award(player, xp);
	}

	/** Every skill whose cooldown a test might want to read, in key order. */
	public static List<ActiveSkill> skillsOf(PlayerClass clazz) {
		List<ActiveSkill> out = new ArrayList<>();
		for (int key = 1; key <= 3; key++) {
			out.add(clazz.skill(key));
		}
		return out;
	}
}
