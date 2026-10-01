package dev.forja.clase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.forja.combat.Posture;
import dev.forja.combat.Stamina;
import dev.forja.entity.Shockwave;
import dev.forja.magic.Healing;
import dev.forja.magic.Mana;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the eighteen active skills do: three a class, on V, B and N (their numbers, and their II's, are in the
 * tree's file, {@link ActiveSkill#numbers}). A skill that finds nothing to do — no mob in sight to mark, not
 * enough stamina for the whirlwind — says so and does not start its wait.
 */
public final class ClassSkills {
	/** Something a skill does a moment later: an arrow of the rain, the meteor landing, a whirl's second turn. */
	private record Later(ServerLevel level, long at, Runnable action) {
	}

	private static final List<Later> LATER = new ArrayList<>();
	/** Tag on a rain arrow, so Flecha veloz leaves its fall alone and nobody picks it up. */
	public static final String RAIN_TAG = "forja_lluvia";
	/** Tag on a knife of the Abanico de dagas: it poisons what it hits. */
	public static final String KNIFE_TAG = "forja_daga";

	private ClassSkills() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (LATER.isEmpty()) {
				return;
			}
			List<Later> due = new ArrayList<>();
			LATER.removeIf(later -> {
				if (later.level.getGameTime() >= later.at) {
					due.add(later);
					return true;
				}
				return false;
			});
			for (Later later : due) {
				later.action.run();
			}
		});
	}

	public static void later(ServerLevel level, int ticks, Runnable action) {
		LATER.add(new Later(level, level.getGameTime() + Math.max(1, ticks), action));
	}

	/** The skill on this key (1 V, 2 B, 3 N), if the class has it: the first always, the others once learned. */
	public static @Nullable ActiveSkill skill(Player player, int slot) {
		ClassData data = ClassProgress.data(player);
		PlayerClass owner = data.playerClass();
		if (owner == null) {
			return null;
		}
		if (slot == 1) {
			return owner.firstSkill;
		}
		ClassTree.Node node = owner.tree().bySlot(slot == 2 ? ClassTree.Slot.B : ClassTree.Slot.N);
		return node != null && data.has(node.id) ? owner.skill(slot) : null;
	}

	/** Ticks until the skill on this key is ready, 0 if it is. Works on the client, from the synced data. */
	public static int waiting(Player player, int slot) {
		return (int) Math.max(0L, ClassProgress.data(player).ready(slot) - player.level().getGameTime());
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
			player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.esperando", skill.displayName(player), (wait + 19) / 20));
			return false;
		}
		if (!perform(player, skill)) {
			return false;
		}
		ClassProgress.set(player, ClassProgress.data(player).withReady(slot, player.level().getGameTime() + skill.cooldownTicks(player)));
		player.sendOverlayMessage(skill.displayName(player).copy().withColor(0xFF000000 | ClassProgress.clazz(player).color));
		return true;
	}

	private static boolean perform(ServerPlayer player, ActiveSkill skill) {
		ServerLevel level = player.level();
		float[] n = skill.numbers(player);
		boolean two = skill.upgraded(player);
		switch (skill) {
			case GRITO_DE_GUERRA -> {
				Stamina.restore(player, n[0]);
				for (Player other : playersAround(player, n[1])) {
					other.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ActiveSkill.ticks(n[2]), 0), player);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7F, 1.4F);
				ring(level, player, n[1], 0xC0463A);
			}
			case POSTURA_DE_HIERRO -> {
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ActiveSkill.ticks(n[0]), 1), player);
				if (two) {
					UUID id = player.getUUID();
					later(level, ActiveSkill.ticks(n[0]), () -> {
						Player still = level.getPlayerByUUID(id);
						if (still != null && still.isAlive()) {
							Stamina.restore(still, n[1]);
						}
					});
				} else {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ActiveSkill.ticks(n[0]), 0), player);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.7F);
				level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
			}
			case TORBELLINO -> {
				if (!Stamina.trySpend(player, n[3])) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_estamina"));
					return false;
				}
				whirl(level, player, n);
				if (two) {
					UUID id = player.getUUID();
					later(level, 10, () -> {
						if (level.getPlayerByUUID(id) instanceof ServerPlayer still && still.isAlive()) {
							whirl(level, still, n);
						}
					});
				}
			}
			case PASO_SOMBRIO -> {
				float[] ghost = ClassEffects.hook(player, Hooks.FANTASMA);
				int ticks = ActiveSkill.ticks(n[0]) * (ghost != null ? 2 : 1);
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false), player);
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1), player);
				ClassEffects.nextHit(player, n[1], ticks);
				if (ghost != null) {
					ClassEffects.ghost(player);
				}
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.6, 0.4, 0.02);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.2F);
			}
			case MARCA_DE_MUERTE -> {
				LivingEntity target = foeInSight(level, player, n[0]);
				if (target == null) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_objetivo"));
					return false;
				}
				ClassEffects.mark(player, target, ActiveSkill.ticks(n[1]));
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ActiveSkill.ticks(n[1]), 0), player);
				level.sendParticles(new DustParticleOptions(0x8A6BC8, 1.5F), target.getX(), target.getY() + target.getBbHeight() + 0.4, target.getZ(),
					16, 0.3, 0.2, 0.3, 0.0);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.8F);
			}
			case ABANICO_DE_DAGAS -> {
				int count = Math.max(1, Math.round(n[0]));
				float spread = n[1];
				for (int i = 0; i < count; i++) {
					float yaw = player.getYRot() + (count == 1 ? 0.0F : -spread / 2.0F + spread * i / (count - 1));
					knife(level, player, yaw, n[2], two);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
			}
			case PROVOCAR -> {
				float[] challenge = ClassEffects.hook(player, Hooks.DESAFIO);
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(n[0]),
					m -> m.isAlive() && m instanceof Enemy && m.distanceTo(player) <= n[0])) {
					mob.setTarget(player);
					if (challenge != null) {
						mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ActiveSkill.ticks(challenge[0]), 0), player);
					}
				}
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ActiveSkill.ticks(n[1]), 0), player);
				if (two) {
					player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), n[2]));
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
				ring(level, player, n[0], 0x8C99A6);
			}
			case BALUARTE -> {
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ActiveSkill.ticks(n[0]), 1), player);
				for (Player other : playersAround(player, n[1])) {
					if (other != player) {
						other.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ActiveSkill.ticks(n[0]), 0), player);
					}
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
				ring(level, player, n[1], 0xC8D0D8);
			}
			case EMBESTIDA_DE_ESCUDO -> charge(level, player, n, two);
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
				ClassEffects.concentrate(player, ActiveSkill.ticks(n[0]));
				level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.6, 0.8, 0.6, 0.6);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.5F);
			}
			case METEORO -> {
				float cost = n[5] * ClassEffects.spellCostMultiplier(player);
				if (!Mana.trySpend(player, cost)) {
					Mana.deny(player);
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_mana"));
					return false;
				}
				Vec3 at = spot(level, player, n[1]);
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, 40, n[3] * 0.5, 0.1, n[3] * 0.5, 0.01);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
				UUID id = player.getUUID();
				float damage = n[2] * ClassEffects.spellDamageMultiplier(player);
				int falling = ActiveSkill.ticks(n[0]);
				for (int step = 1; step < falling; step += 3) {
					double height = 12.0 * (1.0 - step / (double) falling);
					later(level, step, () -> level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + height, at.z, 3, 0.2, 0.2, 0.2, 0.0));
				}
				later(level, falling, () -> {
					Player caster = level.getPlayerByUUID(id);
					Shockwave.burst(level, at, n[3], 14, 0xE86A2A, 0.8F);
					level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, 0.6, 0.3, 0.6, 0.0);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.1F);
					for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(n[3], 2.0, n[3]),
						e -> e.isAlive() && e instanceof Enemy && e.position().distanceTo(at) <= n[3] + 0.5)) {
						victim.invulnerableTime = 0;
						victim.hurtServer(level, caster != null ? level.damageSources().indirectMagic(caster, caster) : level.damageSources().magic(), damage);
						victim.igniteForTicks(ActiveSkill.ticks(n[4]));
					}
				});
			}
			case PULSO_SANADOR -> {
				float amount = n[0] * ClassEffects.healingMultiplier(player);
				for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(n[1]),
					e -> (e == player || Healing.ally(player, e)) && e.distanceTo(player) <= n[1])) {
					Healing.mend(level, player, ally, amount);
					if (two) {
						ally.removeEffect(MobEffects.POISON);
						ally.removeEffect(MobEffects.WITHER);
					}
				}
				ring(level, player, n[1], 0x5CC46A);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
				float[] ground = ClassEffects.hook(player, Hooks.TIERRA_SAGRADA);
				if (ground != null) {
					hallow(level, player, player.position(), ground);
				}
			}
			case RESURGIR -> {
				LivingEntity ally = Healing.allyInSight(level, player, n[0]);
				if (ally == null) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_aliado"));
					return false;
				}
				float missing = ally.getMaxHealth() - ally.getHealth();
				Healing.mend(level, player, ally, missing * n[1] * ClassEffects.healingMultiplier(player));
				ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ActiveSkill.ticks(n[2]), 1), player);
				level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, ally.getX(), ally.getY() + 1.0, ally.getZ(), 40, 0.4, 0.6, 0.4, 0.3);
				level.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
			}
			case ESCUDO_DE_LUZ -> {
				LivingEntity ally = Healing.allyInSight(level, player, n[0]);
				LivingEntity target = ally != null ? ally : player;
				target.setAbsorptionAmount(Math.max(target.getAbsorptionAmount(), n[1]));
				target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ActiveSkill.ticks(n[2]), 0), player);
				if (two) {
					for (MobEffectInstance effect : new ArrayList<>(target.getActiveEffects())) {
						if (!effect.getEffect().value().isBeneficial()) {
							target.removeEffect(effect.getEffect());
							break;
						}
					}
				}
				level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0, target.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
			}
			case SALTO_ATRAS -> {
				Vec3 look = player.getLookAngle();
				Vec3 back = new Vec3(-look.x, 0.0, -look.z);
				back = back.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : back.normalize();
				// About six blocks: the push fades with the ground friction over the leap.
				double push = n[0] * 0.27;
				player.setDeltaMovement(back.x * push, 0.55, back.z * push);
				player.hurtMarked = true;
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ActiveSkill.ticks(n[1]), 0, false, false), player);
				if (two) {
					ClassEffects.nextArrow(player, n[3], ActiveSkill.ticks(n[2]));
				}
				level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 12, 0.3, 0.05, 0.3, 0.02);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.3F);
			}
			case LLUVIA_DE_FLECHAS -> {
				Vec3 at = spot(level, player, n[3]);
				int count = Math.round(n[0]);
				int every = Math.max(1, Math.round(n[1] * 20.0F / count));
				UUID id = player.getUUID();
				for (int i = 0; i < count; i++) {
					later(level, 4 + i * every, () -> {
						Player owner = level.getPlayerByUUID(id);
						if (owner != null) {
							fall(level, owner, at, n[2], n[4]);
						}
					});
				}
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.2, at.z, 30, n[2] * 0.5, 0.1, n[2] * 0.5, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
			case FLECHA_DE_RED -> {
				Vec3 at = spot(level, player, n[0]);
				BlockParticleOption web = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
				Vec3 from = player.getEyePosition();
				for (int step = 0; step <= 12; step++) {
					Vec3 p = from.lerp(at, step / 12.0);
					level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
				level.sendParticles(web, at.x, at.y + 0.3, at.z, 50, n[1] * 0.4, 0.2, n[1] * 0.4, 0.0);
				for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(n[1], 2.0, n[1]),
					e -> e.isAlive() && e instanceof Enemy && e.position().distanceTo(at) <= n[1] + 0.5)) {
					victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ActiveSkill.ticks(n[2]), 3), player);
					ClassEffects.net(player, victim, ActiveSkill.ticks(n[2]));
				}
				level.playSound(null, at.x, at.y, at.z, SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.5F);
			}
		}
		return true;
	}

	/** One turn of the Torbellino: everything hostile around takes a share of the weapon and a shove of posture. */
	private static void whirl(ServerLevel level, ServerPlayer player, float[] n) {
		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * n[1];
		long now = level.getGameTime();
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(n[0], 1.0, n[0]),
			e -> e != player && e.isAlive() && e instanceof Enemy && e.distanceTo(player) <= n[0])) {
			victim.invulnerableTime = 0;
			victim.hurtServer(level, level.damageSources().playerAttack(player), damage);
			Posture.push(victim, n[2], now);
		}
		for (int step = 0; step < 24; step++) {
			double angle = step * Math.PI / 12.0;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(angle) * n[0] * 0.7, player.getY() + 1.0,
				player.getZ() + Math.sin(angle) * n[0] * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
		}
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** One knife of the Abanico: flies like an arrow, is never picked up, and poisons what it hits (ClassEvents). */
	private static void knife(ServerLevel level, ServerPlayer player, float yaw, float damage, boolean strong) {
		Arrow knife = new Arrow(level, player, new ItemStack(Items.ARROW), null);
		knife.pickup = AbstractArrow.Pickup.DISALLOWED;
		knife.addTag(KNIFE_TAG);
		if (strong) {
			knife.addTag(KNIFE_TAG + "_ii");
		}
		double speed = 1.8;
		knife.shootFromRotation(player, player.getXRot(), yaw, 0.0F, (float) speed, 1.0F);
		// Vanilla bites for speed times base damage: this puts each knife at its number.
		knife.setBaseDamage(damage / speed);
		level.addFreshEntity(knife);
	}

	/** The Embestida de escudo: a shove forward, and whatever stands in the way is struck, shaken and thrown back. */
	private static void charge(ServerLevel level, ServerPlayer player, float[] n, boolean stun) {
		Vec3 look = player.getLookAngle();
		Vec3 ahead = new Vec3(look.x, 0.0, look.z);
		ahead = ahead.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : ahead.normalize();
		double push = n[0] * 0.27;
		player.setDeltaMovement(ahead.x * push, 0.2, ahead.z * push);
		player.hurtMarked = true;
		Vec3 from = player.position();
		Vec3 to = from.add(ahead.scale(n[0]));
		long now = level.getGameTime();
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0, 1.0, 1.0),
			e -> e != player && e.isAlive() && !(e instanceof Player) && !Healing.ally(player, e))) {
			victim.invulnerableTime = 0;
			victim.hurtServer(level, level.damageSources().playerAttack(player), n[1]);
			Posture.push(victim, n[2], now);
			if (stun) {
				Posture.breakPosture(victim, now);
			}
			victim.setDeltaMovement(ahead.x * 1.1, 0.4, ahead.z * 1.1);
			victim.hurtMarked = true;
		}
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 16, 0.4, 0.1, 0.4, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	/** Tierra sagrada: for a few seconds, the allies standing in the circle get Resistance I. */
	private static void hallow(ServerLevel level, ServerPlayer player, Vec3 at, float[] n) {
		UUID id = player.getUUID();
		int seconds = Math.max(1, Math.round(n[0]));
		for (int s = 0; s < seconds; s++) {
			later(level, 1 + s * 20, () -> {
				Player owner = level.getPlayerByUUID(id);
				if (owner == null) {
					return;
				}
				for (int step = 0; step < 24; step++) {
					double angle = step * Math.PI / 12.0;
					level.sendParticles(new DustParticleOptions(0xF4E6A0, 1.0F), at.x + Math.cos(angle) * n[1], at.y + 0.15, at.z + Math.sin(angle) * n[1],
						1, 0.0, 0.0, 0.0, 0.0);
				}
				for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(n[1], 2.0, n[1]),
					e -> (e == owner || Healing.ally(owner, e)) && e.position().distanceTo(at) <= n[1])) {
					ally.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 0, false, true), owner);
				}
			});
		}
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

	/** One arrow of the rain: from high above a random point of the circle, straight down, not to be picked up. */
	private static void fall(ServerLevel level, Player owner, Vec3 at, float radius, float damage) {
		double r = radius * Math.sqrt(level.getRandom().nextDouble());
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		double x = at.x + Math.cos(angle) * r;
		double z = at.z + Math.sin(angle) * r;
		Arrow arrow = new Arrow(level, x, at.y + 10.0, z, new ItemStack(Items.ARROW), null);
		arrow.setOwner(owner);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.addTag(RAIN_TAG);
		double speed = 2.5;
		// Vanilla bites for speed times base damage: this puts the rain at its number.
		arrow.setBaseDamage(damage / speed);
		arrow.setDeltaMovement(0.0, -speed, 0.0);
		level.addFreshEntity(arrow);
	}
}
