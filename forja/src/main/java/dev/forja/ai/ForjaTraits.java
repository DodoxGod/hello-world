package dev.forja.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.AttackClassifier;
import dev.forja.combat.CombatAnim;
import dev.forja.combat.DamageKind;
import dev.forja.entity.FallenSmith;
import dev.forja.entity.ForgeAutomaton;
import dev.forja.entity.HollowArmor;
import dev.forja.entity.LivingSlag;
import dev.forja.entity.Tongs;
import dev.forja.entity.WalkingAnvil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What makes each of the mod's own monsters fight like itself against the combat overhaul (ideas 41 to
 * 50 of the plan). The moves themselves stay in each monster's class; this is how they meet posture,
 * guards and the player's habits.
 */
public final class ForjaTraits {
	/** The smith is open to a stagger for this long after one of his heavy blows lands. */
	public static final int SMITH_OPENING = 40;
	/** A blunt blow at least this hard makes the tongs let go. */
	public static final float TONGS_RELEASE_DAMAGE = 3.0F;
	/** The hollow armor plays dead once, at this share of its health, for this long. */
	public static final float HOLLOW_FEIGN_HEALTH = 0.3F;
	public static final int HOLLOW_FEIGN_TICKS = 60;
	/** A slag splits off a piece under an edge this hard, at most this many times. */
	public static final float SLAG_SPLIT_DAMAGE = 3.0F;
	public static final int SLAG_SPLITS = 2;
	/** Pieces left alone this long next to their slag fold back into it. */
	public static final int SLAG_MERGE_TICKS = 100;

	private static final Map<LivingEntity, Long> SMITH_OPEN_UNTIL = new WeakHashMap<>();
	private static final Map<HollowArmor, Long> FEIGNING = new WeakHashMap<>();
	private static final Map<HollowArmor, Boolean> FEIGNED = new WeakHashMap<>();
	private static final Map<LivingSlag, Integer> SPLITS = new WeakHashMap<>();

	private record Piece(LivingSlag piece, LivingSlag parent) {
	}

	private static final List<Piece> PIECES = new ArrayList<>();

	private ForjaTraits() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ForjaTraits::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> afterDamage(entity, source, taken, blocked));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				tick(level);
			}
		});
	}

	// --- 41, 47: posture that only some blows (or some moments) reach ---------------------------------

	/**
	 * Multiplier on the posture a blow of this kind puts into this monster: the walking anvil and the
	 * automaton only feel blunt blows, the cune guardian feels edges and points half; the fallen smith
	 * only in the opening after one of his heavy blows.
	 */
	public static double postureFactor(LivingEntity entity, DamageKind kind, long now) {
		if (entity instanceof WalkingAnvil || entity instanceof ForgeAutomaton) {
			return kind == DamageKind.BLUNT ? 1.2 : 0.2;
		}
		if (entity instanceof dev.forja.entity.CuneGuardian) {
			return kind == DamageKind.BLUNT ? 1.0 : 0.5;
		}
		if (entity instanceof FallenSmith) {
			return SMITH_OPEN_UNTIL.getOrDefault(entity, Long.MIN_VALUE) > now ? 1.5 : 0.1;
		}
		return 1.0;
	}

	/** Multiplier on the size of the posture bar of the mod's tanks. */
	public static double postureMax(LivingEntity entity) {
		if (entity instanceof WalkingAnvil) return 2.0;
		if (entity instanceof ForgeAutomaton) return 1.8;
		if (entity instanceof dev.forja.entity.CuneGuardian) return 1.5;
		return 1.0;
	}

	/** Called when one of the smith's heavy blows lands: he is open for a moment. */
	public static void smithStruck(FallenSmith smith) {
		SMITH_OPEN_UNTIL.put(smith, smith.level().getGameTime() + SMITH_OPENING);
		if (smith.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, smith.getX(), smith.getY(1.0), smith.getZ(), 20, 0.5, 0.6, 0.5, 0.1);
		}
		CombatAnim.broadcast(smith, CombatAnim.Kind.POSTURE, 0, (float) dev.forja.combat.Posture.fill(smith), 0.0F);
	}

	public static boolean smithOpen(LivingEntity entity) {
		return SMITH_OPEN_UNTIL.getOrDefault(entity, Long.MIN_VALUE) > entity.level().getGameTime();
	}

	// --- Damage hooks ----------------------------------------------------------------------------------

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		// 46: the hollow armor plays dead once, instead of going under 30 %.
		if (entity instanceof HollowArmor hollow && !FEIGNED.getOrDefault(hollow, false) && source.getEntity() instanceof Player
			&& hollow.getHealth() - amount < hollow.getMaxHealth() * HOLLOW_FEIGN_HEALTH && hollow.level() instanceof ServerLevel level) {
			FEIGNED.put(hollow, true);
			FEIGNING.put(hollow, level.getGameTime() + HOLLOW_FEIGN_TICKS);
			hollow.setHealth(Math.max(1.0F, hollow.getMaxHealth() * HOLLOW_FEIGN_HEALTH));
			hollow.setNoAi(true);
			hollow.setInvulnerable(true);
			hollow.setDeltaMovement(Vec3.ZERO);
			level.playSound(null, hollow.getX(), hollow.getY(), hollow.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.HOSTILE, 1.2F, 0.5F);
			level.sendParticles(ParticleTypes.SOUL, hollow.getX(), hollow.getY(0.5), hollow.getZ(), 20, 0.4, 0.3, 0.4, 0.02);
			return false;
		}
		// 44: the rust swarm eats into a shield that stops it: better to step out of its way.
		if (entity instanceof Player player && source.getEntity() instanceof dev.forja.entity.RustSwarm) {
			var shield = player.getItemBlockingWith();
			if (shield != null && shield.isDamageableItem()) {
				shield.hurtAndBreak(4, player, player.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND
					? net.minecraft.world.entity.EquipmentSlot.OFFHAND : net.minecraft.world.entity.EquipmentSlot.MAINHAND);
			}
		}
		return true;
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float taken, boolean blocked) {
		if (blocked || taken <= 0.0F || !(source.getEntity() instanceof Player)) {
			return;
		}
		DamageKind kind = AttackClassifier.classify(source, entity).kind();
		// 49: a heavy blunt blow makes the tongs let go.
		if (entity instanceof Tongs tongs && kind == DamageKind.BLUNT && taken >= TONGS_RELEASE_DAMAGE && tongs.holding() != null) {
			tongs.letGo();
		}
		// 43: an edge splits a piece off the slag.
		if (entity instanceof LivingSlag slag && kind == DamageKind.SLASH && taken >= SLAG_SPLIT_DAMAGE && slag.isAlive()
			&& slag.size() > LivingSlag.SMALLEST && SPLITS.getOrDefault(slag, 0) < SLAG_SPLITS && slag.level() instanceof ServerLevel level) {
			split(level, slag);
		}
	}

	private static void split(ServerLevel level, LivingSlag slag) {
		var created = dev.forja.registry.ModEntities.ESCORIA.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		if (!(created instanceof LivingSlag piece)) {
			return;
		}
		float ratio = slag.getHealth() / slag.getMaxHealth();
		int size = slag.size() - 1;
		slag.setSize(size);
		slag.setHealth(Math.max(1.0F, slag.getMaxHealth() * ratio));
		piece.setSize(size);
		piece.setHealth(Math.max(1.0F, piece.getMaxHealth() * ratio));
		piece.snapTo(slag.getX() + 0.6, slag.getY(), slag.getZ() + 0.6, slag.getYRot(), 0.0F);
		piece.addTag("forja_fragmento");
		level.addFreshEntity(piece);
		SPLITS.merge(slag, 1, Integer::sum);
		PIECES.add(new Piece(piece, slag));
		level.playSound(null, slag.getX(), slag.getY(), slag.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		// 46: the hollow armor gets up behind whoever it was fighting.
		for (Iterator<Map.Entry<HollowArmor, Long>> it = FEIGNING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<HollowArmor, Long> entry = it.next();
			HollowArmor hollow = entry.getKey();
			if (hollow.level() != level) {
				continue;
			}
			if (!hollow.isAlive()) {
				it.remove();
				continue;
			}
			if (now < entry.getValue()) {
				continue;
			}
			it.remove();
			hollow.setNoAi(false);
			hollow.setInvulnerable(false);
			Player player = level.getNearestPlayer(hollow, 16.0);
			if (player != null && !player.isCreative() && !player.isSpectator()) {
				double yaw = Math.toRadians(player.getYRot());
				hollow.randomTeleport(player.getX() + Math.sin(yaw) * 2.0, player.getY(), player.getZ() - Math.cos(yaw) * 2.0, true);
				hollow.setTarget(player);
				dev.forja.combat.CombatFeedback.telegraph(hollow);
			}
		}
		// 43: pieces left alone next to their slag fold back into it.
		if (now % 20 == 0 && !PIECES.isEmpty()) {
			for (Iterator<Piece> it = PIECES.iterator(); it.hasNext();) {
				Piece p = it.next();
				if (p.piece().level() != level) {
					continue;
				}
				if (!p.piece().isAlive() || !p.parent().isAlive()) {
					it.remove();
					continue;
				}
				boolean quiet = p.piece().tickCount - p.piece().getLastHurtByMobTimestamp() > SLAG_MERGE_TICKS
					&& p.parent().tickCount - p.parent().getLastHurtByMobTimestamp() > SLAG_MERGE_TICKS;
				if (quiet && p.piece().distanceTo(p.parent()) < 2.0 && p.parent().size() < LivingSlag.BIG) {
					float ratio = Math.min(1.0F, (p.parent().getHealth() + p.piece().getHealth()) / (p.parent().getMaxHealth() * 2.0F));
					p.parent().setSize(p.parent().size() + 1);
					p.parent().setHealth(Math.max(1.0F, p.parent().getMaxHealth() * ratio));
					p.piece().discard();
					SPLITS.merge(p.parent(), -1, Integer::sum);
					level.playSound(null, p.parent().getX(), p.parent().getY(), p.parent().getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 1.4F);
					it.remove();
				} else if (quiet && p.piece().distanceTo(p.parent()) >= 2.0) {
					p.piece().getNavigation().moveTo(p.parent(), 1.0);
				}
			}
		}
	}

	/** Whether this mob is one of the mod's own (its brain leaves the rules to it). */
	public static boolean forja(Mob mob) {
		return "forja".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace());
	}
}
