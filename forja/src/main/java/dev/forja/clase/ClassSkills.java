package dev.forja.clase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.forja.combat.Posture;
import dev.forja.combat.Stamina;
import dev.forja.difficulty.Bosses;
import dev.forja.entity.Shockwave;
import dev.forja.magic.Healing;
import dev.forja.magic.Mana;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.commands.arguments.EntityAnchorArgument;
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
 * What the thirty active skills do: five a class — V, B, and the three ultimates of which a player learns one, all
 * on N (their numbers, and their II's, are in the tree's file, {@link ActiveSkill#numbers}). A skill that finds
 * nothing to do — no mob in sight to mark, not enough stamina for the whirlwind — says so and does not start its
 * wait, and spends nothing.
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

	/** Segunda vida: who is warded, until when, and with what share of their health a lethal blow leaves them. */
	private record Ward(long until, float share) {
	}

	private static final Map<UUID, Ward> WARDS = new HashMap<>();
	/** The class's shields of absorption still standing: their cap goes when they are spent. */
	private static final Map<LivingEntity, Boolean> SHIELDED = new java.util.WeakHashMap<>();
	private static final net.minecraft.resources.Identifier SHIELD = dev.forja.Forja.id("escudo_de_clase");
	/** What the skill just cast does to its own wait (Ejecución II after a kill); 1 otherwise. */
	private static float cooldownScale = 1.0F;

	private ClassSkills() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> SHIELDED.keySet().removeIf(ClassSkills::shieldSpent));
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

	/**
	 * The skill on this key (1 V, 2 B, 3 N), if the class has it: the first always, the second once learned, and on
	 * the third whichever of the three ultimates the player chose.
	 */
	public static @Nullable ActiveSkill skill(Player player, int slot) {
		return skill(ClassProgress.data(player), slot);
	}

	public static @Nullable ActiveSkill skill(ClassData data, int slot) {
		PlayerClass owner = data.playerClass();
		if (owner == null) {
			return null;
		}
		if (slot == 1) {
			return owner.firstSkill;
		}
		ClassTree.Tree tree = owner.tree();
		if (slot == 2) {
			ClassTree.Node node = tree.bySlot(ClassTree.Slot.B);
			return node != null && data.has(node.id) ? owner.secondSkill() : null;
		}
		int chosen = tree.chosenUltimate(data.nodes());
		return chosen == 0 ? null : ActiveSkill.byId(tree.ultimates.get(chosen - 1).id);
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
		cooldownScale = 1.0F;
		if (!perform(player, skill)) {
			return false;
		}
		int cooldown = Math.round(skill.cooldownTicks(player) * cooldownScale);
		ClassProgress.set(player, ClassProgress.data(player).withReady(slot, player.level().getGameTime() + cooldown));
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
				level.sendParticles(new dev.forja.registry.GlintOptions(0x8A6BC8, 1.5F), target.getX(), target.getY() + target.getBbHeight() + 0.4, target.getZ(),
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
					shield(player, n[2]);
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
			case BRAMIDO -> {
				List<LivingEntity> foes = hostilesAround(level, player.position(), n[0], player);
				if (foes.isEmpty()) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_objetivo"));
					return false;
				}
				if (!stamina(player, n[5])) {
					return false;
				}
				long now = level.getGameTime();
				for (LivingEntity foe : foes) {
					Posture.push(foe, n[1], now);
					foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ActiveSkill.ticks(n[2]), 0), player);
				}
				shield(player, Math.min(n[4], n[3] * foes.size()));
				level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 0.7F);
				ring(level, player, n[0], 0xC0463A);
			}
			case HENDEDURA -> {
				Vec3 ahead = flatLook(player);
				double half = Math.toRadians(n[1] / 2.0F);
				List<LivingEntity> foes = new ArrayList<>();
				for (LivingEntity foe : hostilesAround(level, player.position(), n[0] + 0.5F, player)) {
					Vec3 to = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
					if (to.lengthSqr() < 0.25 || Math.acos(Math.max(-1.0, Math.min(1.0, to.normalize().dot(ahead)))) <= half) {
						foes.add(foe);
					}
				}
				if (foes.isEmpty()) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_objetivo"));
					return false;
				}
				if (!stamina(player, n[4])) {
					return false;
				}
				float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * n[2];
				long now = level.getGameTime();
				for (LivingEntity foe : foes) {
					foe.invulnerableTime = 0;
					foe.hurtServer(level, level.damageSources().playerAttack(player), damage);
					Posture.push(foe, n[3], now);
				}
				for (int step = -4; step <= 4; step++) {
					Vec3 p = player.position().add(ahead.yRot((float) (half * step / 4.0)).scale(n[0] * 0.7));
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, player.getY() + 1.0, p.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
				player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.4F);
			}
			case DANZA_DE_SOMBRAS -> {
				List<LivingEntity> foes = hostilesAround(level, player.position(), n[0], player);
				if (foes.isEmpty()) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_objetivo"));
					return false;
				}
				if (!stamina(player, n[3])) {
					return false;
				}
				foes.sort(Comparator.comparingDouble(foe -> foe.distanceToSqr(player)));
				List<LivingEntity> dance = foes.subList(0, Math.min(foes.size(), Math.max(1, Math.round(n[1]))));
				int every = 5;
				// Resistance V: nothing gets through while the dance lasts.
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, dance.size() * every + 10, 4, false, false), player);
				float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * n[2];
				for (int i = 0; i < dance.size(); i++) {
					LivingEntity foe = dance.get(i);
					later(level, 1 + i * every, () -> {
						if (present(player) && foe.isAlive()) {
							behind(player, foe);
							foe.invulnerableTime = 0;
							foe.hurtServer(level, level.damageSources().playerAttack(player), damage);
							level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
							level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 1.3F);
						}
					});
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 0.8F);
			}
			case EJECUCION -> {
				LivingEntity target = foeInSight(level, player, n[0]);
				if (target == null) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_objetivo"));
					return false;
				}
				if (!stamina(player, n[3])) {
					return false;
				}
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.6, 0.3, 0.02);
				behind(player, target);
				float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * n[1];
				if (target.getHealth() < target.getMaxHealth() * n[2]) {
					damage *= 2.0F;
				}
				target.invulnerableTime = 0;
				target.hurtServer(level, level.damageSources().playerAttack(player), damage);
				level.sendParticles(new dev.forja.registry.GlintOptions(0x8A6BC8, 1.5F), target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
					20, 0.3, 0.4, 0.3, 0.0);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
				if (two && !target.isAlive()) {
					cooldownScale = 1.0F - n[4];
				}
			}
			case GOLPE_SISMICO -> {
				List<LivingEntity> foes = hostilesAround(level, player.position(), n[0], player);
				if (foes.isEmpty()) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_objetivo"));
					return false;
				}
				if (!stamina(player, n[4])) {
					return false;
				}
				long now = level.getGameTime();
				for (LivingEntity foe : foes) {
					foe.invulnerableTime = 0;
					foe.hurtServer(level, level.damageSources().playerAttack(player), n[1]);
					Posture.push(foe, n[2], now);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ActiveSkill.ticks(n[3]), 1), player);
					Vec3 away = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
					away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.4);
					foe.setDeltaMovement(away.x, 0.55, away.z);
					foe.hurtMarked = true;
				}
				Shockwave.burst(level, player.position(), n[0], 12, 0x8C99A6, 0.4F);
				level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.2, player.getZ(), 4, n[0] * 0.3, 0.1, n[0] * 0.3, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.8F);
			}
			case SANTUARIO_DE_ACERO -> {
				if (!stamina(player, n[2])) {
					return false;
				}
				sanctuary(level, player, player.position(), n, two);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.8F, 0.6F);
			}
			case RELAMPAGO_EN_CADENA -> {
				LivingEntity first = foeInSight(level, player, n[0]);
				if (first == null) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_objetivo"));
					return false;
				}
				if (!mana(player, n[5])) {
					return false;
				}
				chain(level, player, first, n);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.4F, 1.6F);
			}
			case PRISION_DE_HIELO -> {
				Vec3 at = spot(level, player, n[0]);
				List<LivingEntity> foes = hostilesAround(level, at, n[1], player);
				if (foes.isEmpty()) {
					player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_objetivo"));
					return false;
				}
				if (!mana(player, n[4])) {
					return false;
				}
				float damage = n[3] * ClassEffects.spellDamageMultiplier(player);
				long now = level.getGameTime();
				for (LivingEntity foe : foes) {
					foe.invulnerableTime = 0;
					foe.hurtServer(level, level.damageSources().indirectMagic(player, player), damage);
					foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ActiveSkill.ticks(n[2]), 6), player);
					if (Bosses.isBoss(foe)) {
						Posture.push(foe, 40.0, now);
					} else {
						// Staggered: whatever it was winding up is cut off.
						Posture.breakPosture(foe, now);
					}
					level.sendParticles(ParticleTypes.SNOWFLAKE, foe.getX(), foe.getY() + foe.getBbHeight() * 0.5, foe.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
				}
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), at.x, at.y + 0.3, at.z, 60,
					n[1] * 0.4, 0.3, n[1] * 0.4, 0.0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
			}
			case OLEADA_DE_VIDA -> {
				if (!mana(player, n[4])) {
					return false;
				}
				float amount = n[1] * ClassEffects.healingMultiplier(player);
				for (LivingEntity ally : alliesAround(level, player, player.position(), n[0])) {
					Healing.mend(level, player, ally, amount);
					ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ActiveSkill.ticks(n[2]), 1), player);
					if (two) {
						for (MobEffectInstance effect : new ArrayList<>(ally.getActiveEffects())) {
							if (!effect.getEffect().value().isBeneficial()) {
								ally.removeEffect(effect.getEffect());
							}
						}
					}
				}
				for (LivingEntity foe : hostilesAround(level, player.position(), n[3], player)) {
					Vec3 away = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
					away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
					foe.setDeltaMovement(away.x * 1.0, 0.35, away.z * 1.0);
					foe.hurtMarked = true;
				}
				Shockwave.burst(level, player.position(), n[3], 12, 0x5CC46A, 0.0F);
				ring(level, player, n[0], 0x5CC46A);
				level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5, player.getZ(), 12, 1.5, 0.5, 1.5, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
			}
			case SEGUNDA_VIDA -> {
				LivingEntity ally = Healing.allyInSight(level, player, n[0]);
				LivingEntity target = ally != null ? ally : player;
				if (!mana(player, n[3])) {
					return false;
				}
				WARDS.put(target.getUUID(), new Ward(level.getGameTime() + ActiveSkill.ticks(n[1]), n[2]));
				level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, target.getX(), target.getY() + 1.0, target.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 1.2F);
			}
			case SAETA_LETAL -> {
				if (!stamina(player, n[5])) {
					return false;
				}
				bolt(level, player, n);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
			}
			case FLECHA_EXPLOSIVA -> {
				if (!stamina(player, n[4])) {
					return false;
				}
				Vec3 at = spot(level, player, n[0]);
				Vec3 from = player.getEyePosition();
				for (int step = 0; step <= 16; step++) {
					Vec3 p = from.lerp(at, step / 16.0);
					level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
				Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
				long now = level.getGameTime();
				for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(n[1], 2.0, n[1]),
					e -> e != player && e.isAlive() && !(e instanceof Player) && !Healing.ally(player, e) && e.position().distanceTo(at) <= n[1] + 0.5)) {
					foe.invulnerableTime = 0;
					foe.hurtServer(level, level.damageSources().arrow(arrow, player), n[2]);
					Posture.push(foe, n[3], now);
					Vec3 away = foe.position().subtract(at).multiply(1.0, 0.0, 1.0);
					away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(0.8);
					foe.setDeltaMovement(away.x, 0.4, away.z);
					foe.hurtMarked = true;
				}
				level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 4, n[1] * 0.3, 0.3, n[1] * 0.3, 0.0);
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 30, n[1] * 0.4, 0.2, n[1] * 0.4, 0.02);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.2F);
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
				shield(target, n[1]);
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

	/**
	 * Absorption from a skill or a node (Provocar II, Bramido, Escudo de luz, Égida, Milagro). Vanilla caps absorption
	 * at the MAX_ABSORPTION attribute, 0 without an Absorption effect, so a bare setAbsorptionAmount did nothing: the
	 * cap is raised for as long as the shield lasts, and dropped once it is spent.
	 */
	public static void shield(LivingEntity target, float amount) {
		var max = target.getAttribute(Attributes.MAX_ABSORPTION);
		if (max != null) {
			var old = max.getModifier(SHIELD);
			double cap = Math.max(amount, old == null ? 0.0 : Math.min(old.amount(), target.getAbsorptionAmount()));
			max.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SHIELD, cap,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		}
		target.setAbsorptionAmount(Math.max(target.getAbsorptionAmount(), amount));
		SHIELDED.put(target, Boolean.TRUE);
	}

	private static boolean shieldSpent(LivingEntity target) {
		if (target.isAlive() && !target.isRemoved() && target.getAbsorptionAmount() > 0.0F) {
			return false;
		}
		var max = target.getAttribute(Attributes.MAX_ABSORPTION);
		if (max != null) {
			max.removeModifier(SHIELD);
		}
		return true;
	}

	/** Whether a player a delayed part of a skill holds on to is still there (alive, not logged out nor respawned). */
	private static boolean present(ServerPlayer player) {
		return player.isAlive() && !player.isRemoved();
	}

	/** The caster and their allies within reach of a point. */
	private static List<LivingEntity> alliesAround(ServerLevel level, ServerPlayer caster, Vec3 at, double reach) {
		List<LivingEntity> out = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach, 2.0, reach),
			e -> e != caster && Healing.ally(caster, e) && e.position().distanceTo(at) <= reach));
		if (caster.position().distanceTo(at) <= reach + 0.5) {
			out.add(caster);
		}
		return out;
	}

	private static boolean stamina(ServerPlayer player, float amount) {
		if (!Stamina.trySpend(player, amount)) {
			player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_estamina"));
			return false;
		}
		return true;
	}

	private static boolean mana(ServerPlayer player, float amount) {
		if (!Mana.trySpend(player, amount * ClassEffects.spellCostMultiplier(player))) {
			Mana.deny(player);
			player.sendOverlayMessage(Component.translatable("gui.forja.habilidad.sin_mana"));
			return false;
		}
		return true;
	}

	/** The hostile monsters within reach of a point, the caster left out. */
	private static List<LivingEntity> hostilesAround(ServerLevel level, Vec3 at, double reach, Player caster) {
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach, 2.0, reach),
			e -> e != caster && e.isAlive() && e instanceof Enemy && e.position().distanceTo(at) <= reach + 0.5);
	}

	private static Vec3 flatLook(Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		return flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
	}

	/** Puts the player right behind a foe (the way it faces), looking at it: what Danza de sombras and Ejecución do. */
	private static void behind(ServerPlayer player, LivingEntity foe) {
		double yaw = Math.toRadians(foe.getYHeadRot());
		double back = foe.getBbWidth() / 2.0 + 0.9;
		double x = foe.getX() + Math.sin(yaw) * back;
		double z = foe.getZ() - Math.cos(yaw) * back;
		player.teleportTo(x, foe.getY(), z);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, foe.getEyePosition());
		player.hurtMarked = true;
	}

	/** Santuario de acero: every half second, the allies in the circle are shielded and mended, and hostiles thrown out. */
	private static void sanctuary(ServerLevel level, ServerPlayer player, Vec3 at, float[] n, boolean strong) {
		int halves = Math.max(1, Math.round(n[0] * 2.0F));
		long end = level.getGameTime() + ActiveSkill.ticks(n[0]);
		for (int h = 0; h < halves; h++) {
			later(level, 1 + h * 10, () -> {
				if (!present(player)) {
					return;
				}
				ServerPlayer owner = player;
				for (int step = 0; step < 32; step++) {
					double angle = step * Math.PI / 16.0;
					level.sendParticles(new dev.forja.registry.GlintOptions(0xC8D0D8, 1.2F), at.x + Math.cos(angle) * n[1], at.y + 0.15, at.z + Math.sin(angle) * n[1],
						1, 0.0, 0.0, 0.0, 0.0);
				}
				int left = (int) Math.max(20L, end - level.getGameTime());
				for (LivingEntity ally : alliesAround(level, owner, at, n[1])) {
					ally.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 15, ally == owner && strong ? 1 : 0, false, true), owner);
					// Regeneration heals on a beat its timer keeps: given once for what is left, not refreshed every time.
					if (!ally.hasEffect(MobEffects.REGENERATION)) {
						ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, left, 0, false, true), owner);
					}
				}
				for (LivingEntity foe : hostilesAround(level, at, n[1], owner)) {
					Vec3 away = foe.position().subtract(at).multiply(1.0, 0.0, 1.0);
					away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
					foe.setDeltaMovement(away.x * 0.9, 0.3, away.z * 0.9);
					foe.hurtMarked = true;
				}
			});
		}
	}

	/** Relámpago en cadena: the bolt and its leaps, each one a little weaker, never twice on the same foe. */
	private static void chain(ServerLevel level, ServerPlayer player, LivingEntity first, float[] n) {
		float damage = n[1] * ClassEffects.spellDamageMultiplier(player);
		List<LivingEntity> struck = new ArrayList<>();
		Vec3 from = player.getEyePosition();
		LivingEntity current = first;
		int leaps = Math.max(0, Math.round(n[2]));
		for (int i = 0; i <= leaps && current != null; i++) {
			Vec3 to = current.position().add(0.0, current.getBbHeight() * 0.6, 0.0);
			for (int step = 0; step <= 10; step++) {
				Vec3 p = from.lerp(to, step / 10.0);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
			}
			current.invulnerableTime = 0;
			current.hurtServer(level, level.damageSources().indirectMagic(player, player), damage);
			struck.add(current);
			damage *= 1.0F - n[4];
			from = to;
			LivingEntity at = current;
			current = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity next : hostilesAround(level, at.position(), n[3], player)) {
				if (!struck.contains(next) && next.distanceToSqr(at) < best) {
					best = next.distanceToSqr(at);
					current = next;
				}
			}
		}
	}

	/**
	 * Saeta letal: a line of light along the look while the archer aims, then the bolt along wherever they look by
	 * then, through everything on it up to the first wall. A projectile, for the class's factors.
	 */
	private static void bolt(ServerLevel level, ServerPlayer still, float[] n) {
		int aim = Math.max(1, ActiveSkill.ticks(n[0]));
		for (int t = 0; t < aim; t += 2) {
			later(level, 1 + t, () -> {
				if (present(still)) {
					Vec3 from = still.getEyePosition();
					Vec3 to = reach(level, still, n[1]);
					for (int step = 1; step <= 24; step++) {
						Vec3 p = from.lerp(to, step / 24.0);
						level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
					}
				}
			});
		}
		later(level, aim, () -> {
			if (!present(still)) {
				return;
			}
			Vec3 from = still.getEyePosition();
			Vec3 to = reach(level, still, n[1]);
			Arrow arrow = new Arrow(level, still, new ItemStack(Items.ARROW), null);
			for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0),
				e -> e != still && e.isAlive() && !(e instanceof Player) && !Healing.ally(still, e) && e.getBoundingBox().inflate(0.3).clip(from, to).isPresent())) {
				float damage = foe.getHealth() < foe.getMaxHealth() * n[4] ? n[2] * n[3] : n[2];
				foe.invulnerableTime = 0;
				foe.hurtServer(level, level.damageSources().arrow(arrow, still), damage);
			}
			for (int step = 1; step <= 32; step++) {
				Vec3 p = from.lerp(to, step / 32.0);
				level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.0);
			}
			level.playSound(null, still.getX(), still.getY(), still.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		});
	}

	/** Where the look ends: the first wall, or {@code distance} blocks out. */
	private static Vec3 reach(ServerLevel level, Player player, double distance) {
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(distance));
		HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
	}

	/**
	 * Segunda vida: a lethal blow on someone warded leaves them standing with a share of their health instead
	 * (ClassEvents, on the death that is about to happen). Spent by the first blow it stops.
	 */
	public static boolean spare(LivingEntity entity) {
		Ward ward = WARDS.remove(entity.getUUID());
		if (ward == null || ward.until < entity.level().getGameTime()) {
			return false;
		}
		entity.setHealth(Math.max(1.0F, entity.getMaxHealth() * ward.share));
		if (entity.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, entity.getX(), entity.getY() + 1.0, entity.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
		}
		return true;
	}

	/** Whether Segunda vida is still waiting on someone (tests and the tooltip). */
	public static boolean warded(LivingEntity entity) {
		Ward ward = WARDS.get(entity.getUUID());
		return ward != null && ward.until >= entity.level().getGameTime();
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
					level.sendParticles(new dev.forja.registry.GlintOptions(0xF4E6A0, 1.0F), at.x + Math.cos(angle) * n[1], at.y + 0.15, at.z + Math.sin(angle) * n[1],
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
			level.sendParticles(new dev.forja.registry.GlintOptions(colour, 1.2F), player.getX() + Math.cos(angle) * reach, player.getY() + 0.2,
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
