package dev.forja.clase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.forja.combat.Stamina;
import dev.forja.entity.Shockwave;
import dev.forja.magic.Healing;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the fourteen active skills do (their numbers are in {@link ActiveSkill}). A skill that finds nothing
 * to do — no mob in sight to mark, no forged piece in hand to mend — says so and does not start its wait.
 */
public final class ClassSkills {
	/** Arrows of a Lluvia de flechas still to fall. */
	private record Volley(ServerLevel level, UUID owner, Vec3 at, int left, long next) {
	}

	private static final List<Volley> VOLLEYS = new ArrayList<>();
	/** Tag on a rain arrow, so Flecha veloz leaves its fall alone and nobody picks it up. */
	public static final String RAIN_TAG = "forja_lluvia";

	private ClassSkills() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (int i = VOLLEYS.size() - 1; i >= 0; i--) {
				Volley volley = VOLLEYS.get(i);
				if (volley.level.getGameTime() < volley.next) {
					continue;
				}
				VOLLEYS.remove(i);
				Player owner = volley.level.getPlayerByUUID(volley.owner);
				if (owner == null) {
					continue;
				}
				fall(volley.level, owner, volley.at);
				if (volley.left > 1) {
					VOLLEYS.add(new Volley(volley.level, volley.owner, volley.at, volley.left - 1, volley.next + rainEvery()));
				}
			}
		});
	}

	/** The skill on this key (1 or 2), if the class has it: the first always, the second once learned. */
	public static @Nullable ActiveSkill skill(Player player, int slot) {
		ClassData data = ClassProgress.data(player);
		PlayerClass owner = data.playerClass();
		if (owner == null) {
			return null;
		}
		if (slot == 1) {
			return owner.firstSkill;
		}
		for (Talent talent : owner.talents()) {
			if (talent.skill != null && data.has(talent)) {
				return talent.skill;
			}
		}
		return null;
	}

	/** Ticks until the skill on this key is ready, 0 if it is. Works on the client, from the synced data. */
	public static int waiting(Player player, int slot) {
		ClassData data = ClassProgress.data(player);
		long ready = slot == 1 ? data.skillReady() : data.secondReady();
		return (int) Math.max(0L, ready - player.level().getGameTime());
	}

	/**
	 * The key was pressed. Checked here, on the server: a class, a skill on that key, the wait over. A skill
	 * that did something starts its wait; one that found nothing to do does not.
	 */
	public static boolean use(ServerPlayer player, int slot, boolean ignoreWait) {
		ActiveSkill skill = skill(player, slot);
		if (skill == null) {
			player.sendOverlayMessage(slot == 1 || ClassProgress.clazz(player) == null
				? Component.translatable("gui.forja.habilidad.sin_clase", ClassProgress.key(ClassProgress.KEY_TREE))
				: Component.translatable("gui.forja.habilidad.sin_aprender"));
			return false;
		}
		int wait = waiting(player, slot);
		if (wait > 0 && !ignoreWait) {
			player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.esperando", skill.displayName(), (wait + 19) / 20));
			return false;
		}
		if (!perform(player, skill)) {
			return false;
		}
		ClassProgress.set(player, ClassProgress.data(player).withReady(slot, player.level().getGameTime() + skill.cooldownTicks()));
		player.sendOverlayMessage(skill.displayName().copy().withColor(0xFF000000 | ClassProgress.clazz(player).color));
		return true;
	}

	private static boolean perform(ServerPlayer player, ActiveSkill skill) {
		ServerLevel level = player.level();
		float[] n = skill.numbers;
		switch (skill) {
			case GRITO_DE_GUERRA -> {
				Stamina.restore(player, n[0]);
				for (Player other : playersAround(player, n[1])) {
					other.addEffect(new MobEffectInstance(MobEffects.STRENGTH, skill.ticks(2), 0), player);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7F, 1.4F);
				ring(level, player, n[1], 0xC0463A);
			}
			case POSTURA_DE_HIERRO -> {
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, skill.ticks(0), 1), player);
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, skill.ticks(0), 0), player);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.7F);
				level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
			}
			case PASO_SOMBRIO -> {
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, skill.ticks(0), 0, false, false), player);
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, skill.ticks(0), 1), player);
				ClassEffects.nextHit(player, n[1], skill.ticks(0));
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.6, 0.4, 0.02);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.2F);
			}
			case MARCA_DE_MUERTE -> {
				LivingEntity target = foeInSight(level, player, n[0]);
				if (target == null) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_objetivo"));
					return false;
				}
				ClassEffects.mark(player, target, skill.ticks(1));
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, skill.ticks(1), 0), player);
				level.sendParticles(new DustParticleOptions(0x8A6BC8, 1.5F), target.getX(), target.getY() + target.getBbHeight() + 0.4, target.getZ(),
					16, 0.3, 0.2, 0.3, 0.0);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.8F);
			}
			case PROVOCAR -> {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(n[0]),
					m -> m.isAlive() && m instanceof Enemy && m.distanceTo(player) <= n[0])) {
					mob.setTarget(player);
				}
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, skill.ticks(1), 0), player);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
				ring(level, player, n[0], 0x8C99A6);
			}
			case BALUARTE -> {
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, skill.ticks(0), 1), player);
				for (Player other : playersAround(player, n[1])) {
					if (other != player) {
						other.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, skill.ticks(0), 0), player);
					}
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
				ring(level, player, n[1], 0xC8D0D8);
			}
			case NOVA_ARCANA -> {
				float damage = n[1] * ClassEffects.spellDamageMultiplier(player);
				Shockwave.burst(level, player.position(), n[0], 12, 0x4F7FE8, 0.6F);
				for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(n[0], 2.0, n[0]),
					e -> e.isAlive() && e instanceof Enemy && e.distanceTo(player) <= n[0])) {
					victim.invulnerableTime = 0;
					victim.hurtServer(level, level.damageSources().indirectMagic(player, player), damage);
					Vec3 away = victim.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
					if (away.lengthSqr() > 1.0E-4) {
						away = away.normalize().scale(0.9);
						victim.setDeltaMovement(away.x, 0.35, away.z);
						victim.hurtMarked = true;
					}
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.2F);
			}
			case CONCENTRACION -> {
				ClassEffects.concentrate(player, skill.ticks(0));
				level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.6, 0.8, 0.6, 0.6);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.5F);
			}
			case PULSO_SANADOR -> {
				float amount = n[1] * ClassEffects.healingMultiplier(player);
				for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(n[0]),
					e -> (e == player || Healing.ally(player, e)) && e.distanceTo(player) <= n[0])) {
					Healing.mend(level, player, ally, amount);
				}
				ring(level, player, n[0], 0x5CC46A);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
			}
			case RESURGIR -> {
				LivingEntity ally = Healing.allyInSight(level, player, n[0]);
				if (ally == null) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_aliado"));
					return false;
				}
				float missing = ally.getMaxHealth() - ally.getHealth();
				Healing.mend(level, player, ally, missing * n[1] * ClassEffects.healingMultiplier(player));
				ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, skill.ticks(2), 1), player);
				level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, ally.getX(), ally.getY() + 1.0, ally.getZ(), 40, 0.4, 0.6, 0.4, 0.3);
				level.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
			}
			case SALTO_ATRAS -> {
				Vec3 look = player.getLookAngle();
				Vec3 back = new Vec3(-look.x, 0.0, -look.z);
				back = back.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : back.normalize();
				// About six blocks: the push fades with the ground friction over the leap.
				double push = n[0] * 0.27;
				player.setDeltaMovement(back.x * push, 0.55, back.z * push);
				player.hurtMarked = true;
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, skill.ticks(1), 0, false, false), player);
				level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 12, 0.3, 0.05, 0.3, 0.02);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.3F);
			}
			case LLUVIA_DE_FLECHAS -> {
				Vec3 at = spot(level, player, n[3]);
				VOLLEYS.add(new Volley(level, player.getUUID(), at, Math.round(n[0]), level.getGameTime() + 4));
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.2, at.z, 30, n[2] * 0.5, 0.1, n[2] * 0.5, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
			case TEMPLE_DE_CAMPANA -> {
				ItemStack held = player.getMainHandItem();
				if (!held.has(dev.forja.registry.ModComponents.PARTS) || !held.isDamageableItem() || !held.isDamaged()) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_pieza"));
					return false;
				}
				int mend = Math.max(1, Math.round(held.getMaxDamage() * n[0]));
				held.setDamageValue(Math.max(0, held.getDamageValue() - mend));
				player.addEffect(new MobEffectInstance(MobEffects.HASTE, skill.ticks(1), 1), player);
				level.sendParticles(dev.forja.registry.ModParticles.CHISPA, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.3, 0.3, 0.3);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7F, 1.3F);
			}
			case FORJA_AL_ROJO -> {
				ClassEffects.redHot(player, skill.ticks(0));
				level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 0.8F);
			}
		}
		return true;
	}

	private static List<Player> playersAround(Player player, double reach) {
		List<Player> out = new ArrayList<>();
		for (Player other : player.level().players()) {
			if (!other.isSpectator() && other.isAlive() && other.distanceTo(player) <= reach) {
				out.add(other);
			}
		}
		if (!out.contains(player)) {
			out.add(player);
		}
		return out;
	}

	/** A flat ring of the class's colour round the player, as far as the skill reaches. */
	private static void ring(ServerLevel level, Player player, double reach, int colour) {
		for (int step = 0; step < 36; step++) {
			double angle = step * Math.PI / 18.0;
			level.sendParticles(new DustParticleOptions(colour, 1.2F), player.getX() + Math.cos(angle) * reach, player.getY() + 0.2,
				player.getZ() + Math.sin(angle) * reach, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** The first living thing along the look that is not an ally, within reach and not behind a wall. */
	static @Nullable LivingEntity foeInSight(ServerLevel level, Player player, double reach) {
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(reach));
		HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			to = wall.getLocation();
		}
		LivingEntity best = null;
		double nearest = Double.MAX_VALUE;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0),
			e -> e != player && e.isAlive() && !(e instanceof Player) && !Healing.ally(player, e))) {
			var hit = other.getBoundingBox().inflate(0.3).clip(from, to);
			if (hit.isPresent() && from.distanceToSqr(hit.get()) < nearest) {
				nearest = from.distanceToSqr(hit.get());
				best = other;
			}
		}
		return best;
	}

	/** Where the player is looking, on the ground, within reach. */
	private static Vec3 spot(ServerLevel level, Player player, double reach) {
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(reach));
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
		float floor = Shockwave.floorAt(level, at.x, at.y + 1.0, at.z);
		return new Vec3(at.x, at.y + 1.0 + floor, at.z);
	}

	private static int rainEvery() {
		ActiveSkill rain = ActiveSkill.LLUVIA_DE_FLECHAS;
		return Math.max(1, Math.round(rain.numbers[1] * 20.0F / rain.numbers[0]));
	}

	/** One arrow of the rain: from high above a random point of the circle, straight down, not to be picked up. */
	private static void fall(ServerLevel level, Player owner, Vec3 at) {
		ActiveSkill rain = ActiveSkill.LLUVIA_DE_FLECHAS;
		double radius = rain.numbers[2] * Math.sqrt(level.getRandom().nextDouble());
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		double x = at.x + Math.cos(angle) * radius;
		double z = at.z + Math.sin(angle) * radius;
		Arrow arrow = new Arrow(level, x, at.y + 10.0, z, new ItemStack(Items.ARROW), null);
		arrow.setOwner(owner);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.addTag(RAIN_TAG);
		double speed = 2.5;
		// Vanilla bites for speed times base damage: this puts the rain at its four.
		arrow.setBaseDamage(rain.numbers[4] / speed);
		arrow.setDeltaMovement(0.0, -speed, 0.0);
		level.addFreshEntity(arrow);
	}
}
