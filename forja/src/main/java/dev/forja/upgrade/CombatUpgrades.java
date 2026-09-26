package dev.forja.upgrade;

import java.util.ArrayList;
import java.util.List;

import dev.forja.forge.ForgeType;
import dev.forja.forge.Mastery;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import net.minecraft.world.entity.projectile.Projectile;
import dev.forja.forge.ForgeStats;
import dev.forja.registry.ModComponents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Everything that happens on a hit: weapon upgrades (Vampirismo, Onda de choque, Tormenta, Escarcha,
 * Veneno, Critico), Decapitador on a kill, the chestplate's Represalia ignea, shield upgrades (Puas,
 * Rebote) and the combat side of material traits (Afilado, Del End).
 */
public final class CombatUpgrades {
	/** Set while an upgrade deals its own extra damage, so that damage does not trigger upgrades again. */
	private static final ThreadLocal<Boolean> EXTRA_DAMAGE = ThreadLocal.withInitial(() -> false);
	/** Vendaval's lift, applied at the end of the tick so vanilla's own smash knockback cannot undo it. */
	private static final List<Lift> PENDING_LIFT = new ArrayList<>();

	/** Arrows a parry sends back, turned around on the next tick so vanilla does not undo it. */
	private static final List<Reflected> PENDING_REFLECT = new ArrayList<>();

	/** How long a pending effect waits before it is dropped, so nothing can pile up here. */
	private static final int PENDING_TICKS = 20;

	/** Vacio: the share of an unforged blow each piece of hollow plate turns aside. Most things in the
	 * world swing unforged steel or nothing at all, so this stays small. */
	private static final float HOLLOW_DODGE = 0.03F;

	/** A parried projectile, the archer it is being sent back to, and when it was parried. */
	private record Reflected(Projectile projectile, LivingEntity target, long added) {
	}

	/** Something a Vendaval hit is about to throw into the air, and when it was hit. */
	private record Lift(LivingEntity entity, long added) {
	}

	/** Who has just parried: their next blow lands doubled. Weak keys, so nothing is kept alive by this. */
	private static final java.util.Map<LivingEntity, Integer> RIPOSTE = new java.util.WeakHashMap<>();
	public static final double VENDAVAL_LIFT = 1.0;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private CombatUpgrades() {
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(level -> {
			long now = level.getGameTime();
			PENDING_REFLECT.removeIf(reflected -> {
				Projectile projectile = reflected.projectile();
				if (projectile.level() != level) {
					// Not this level: keep it for its own, but never longer than a second.
					return now - reflected.added() > PENDING_TICKS;
				}
				if (projectile.isAlive() && reflected.target().isAlive()) {
					// Straight back at the archer, and now it answers to whoever parried it.
					Vec3 aim = reflected.target().getEyePosition().subtract(projectile.position()).normalize();
					projectile.setDeltaMovement(aim.scale(1.6));
					projectile.setPos(projectile.position().add(aim.scale(0.4)));
					projectile.hurtMarked = true;
				}
				return true;
			});
			PENDING_LIFT.removeIf(lift -> {
				if (lift.entity().level() != level) {
					return now - lift.added() > PENDING_TICKS;
				}
				if (lift.entity().isAlive()) {
					// Straight up: whatever push the hit gave them is wiped.
					lift.entity().setDeltaMovement(0.0, VENDAVAL_LIFT, 0.0);
					lift.entity().hurtMarked = true;
				}
				return true;
			});
		});

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (EXTRA_DAMAGE.get() || !(entity.level() instanceof ServerLevel level)) {
				return true;
			}
			// A hit the shield fully stops never reaches the after-damage event, so shield upgrades react here.
			ItemStack shield = entity.getItemBlockingWith();
			if (shield != null && (shield.has(ModComponents.PARTS) || dev.forja.combat.WeaponGuard.is(shield))
				&& wouldBlock(entity, source, amount, shield)) {
				EXTRA_DAMAGE.set(true);
				try {
					onShieldBlock(level, entity, source, shield);
				} finally {
					EXTRA_DAMAGE.set(false);
				}
			}

			// Jinete: the barding turns part of what comes at the mount aside.
			ItemStack barding = entity.getItemBySlot(EquipmentSlot.BODY);
			if (barding.has(ModComponents.PARTS) && dev.forja.forge.Perk.has(barding, dev.forja.forge.Perk.JINETE)
				&& level.getRandom().nextFloat() < dev.forja.forge.Perk.JINETE_SHARE) {
				level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY(0.8), entity.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
				dev.forja.forge.Perk.JINETE.spark(level, entity, 8);
				return false;
			}

			// Estelar: nothing made of star iron lets a fall hurt you.
			if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
				for (EquipmentSlot slot : ARMOR) {
					ForgedParts worn = entity.getItemBySlot(slot).get(ModComponents.PARTS);
					if (worn != null && worn.hasTrait(ForgeMaterial.Trait.ESTELAR)) {
						level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY(0.2), entity.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
						return false;
					}
				}
			}

			// Vacio: hollow plate is as hard for plain steel to bite as the suit it came from.
			if (source.getEntity() instanceof LivingEntity plain && !plain.getMainHandItem().has(ModComponents.PARTS)) {
				int hollow = 0;
				for (EquipmentSlot slot : ARMOR) {
					ForgedParts worn = entity.getItemBySlot(slot).get(ModComponents.PARTS);
					hollow += worn != null && !entity.getItemBySlot(slot).isBroken()
						&& worn.hasTrait(ForgeMaterial.Trait.VACIO) ? 1 : 0;
				}
				if (hollow > 0 && level.getRandom().nextFloat() < HOLLOW_DODGE * hollow) {
					level.sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY(1.0), entity.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
					return false;
				}
			}

			// Tempano: the cold in the plate bites back at whoever came close enough to swing.
			if (source.getDirectEntity() instanceof LivingEntity attacker && !source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
				&& level.getRandom().nextFloat() < Upgrade.frostbiteChance(Upgrades.armorFraction(entity, Upgrade.TEMPANO))) {
				attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, true, true));
				attacker.setTicksFrozen(Math.max(attacker.getTicksFrozen(), 160));
				level.sendParticles(ParticleTypes.SNOWFLAKE, attacker.getX(), attacker.getY(0.8), attacker.getZ(), 14, 0.3, 0.4, 0.3, 0.02);
			}

			// Aurora: the lights turn part of what magic throws at you aside.
			if (source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)
				&& level.getRandom().nextFloat() < Upgrade.auroraReduction(Upgrades.armorFraction(entity, Upgrade.AURORA))) {
				level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY(1.2), entity.getZ(), 16, 0.4, 0.5, 0.4, 0.08);
				return false;
			}

			// Amatista: an arrow finds its way past you now and then.
			if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) && entity instanceof net.minecraft.world.entity.player.Player bearer
				&& dev.forja.item.Talisman.carried(bearer, dev.forja.item.Talisman.AMATISTA)
				&& level.getRandom().nextFloat() < dev.forja.item.Talisman.DODGE_CHANCE) {
				level.sendParticles(ParticleTypes.WITCH, entity.getX(), entity.getY(1.0), entity.getZ(), 12, 0.3, 0.5, 0.3, 0.1);
				return false;
			}

			// Del End armor: each piece gives a 5% chance to dodge a hit entirely.
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}

			// Animado: soul steel stands the blow itself, once every thirty seconds, no roll involved.
			if (TraitEffects.guards(level, entity)) {
				return false;
			}
			int pieces = 0;
			for (EquipmentSlot slot : ARMOR) {
				ForgedParts parts = entity.getItemBySlot(slot).get(ModComponents.PARTS);
				pieces += parts != null && !entity.getItemBySlot(slot).isBroken() && parts.hasTrait(ForgeMaterial.Trait.DEL_END) ? 1 : 0;
			}
			if (pieces > 0 && level.getRandom().nextFloat() < 0.05F * pieces) {
				level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY(0.5), entity.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.4F);
				return false;
			}
			return true;
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damageTaken, blocked) -> {
			if (EXTRA_DAMAGE.get() || !(victim.level() instanceof ServerLevel level)) {
				return;
			}
			EXTRA_DAMAGE.set(true);
			try {
				// Rescoldo: fire is a cost of the job, not an emergency. This runs before the attacker
				// check on purpose, because most of what burns you has nobody behind it.
				if (damageTaken > 0.0F && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
					// Salamandra: with both on one piece the fire is given back whole and put out.
					boolean salamander = false;
					for (EquipmentSlot slot : ARMOR) {
						salamander |= Synergy.SALAMANDRA.active(victim.getItemBySlot(slot));
					}
					float ember = salamander ? 1.0F : Upgrade.emberShare(Upgrades.armorFraction(victim, Upgrade.RESCOLDO));
					if (ember > 0.0F) {
						victim.heal(damageTaken * ember);
						if (salamander) {
							victim.setRemainingFireTicks(0);
						}
						level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY(0.9), victim.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
					}
				}
				if (damageTaken <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker)) {
					return;
				}
				// A staff's bolt and a tome's area are blows of the staff and the tome (magic/Spellcasting).
				boolean spell = dev.forja.magic.Spellcasting.blow();
				boolean melee = source.getDirectEntity() == attacker;

				float retaliation = Upgrade.retaliationSeconds(Upgrades.fraction(victim.getItemBySlot(EquipmentSlot.CHEST), Upgrade.REPRESALIA));
				if (retaliation > 0.0F && melee) {
					attacker.igniteForSeconds(retaliation);
				}

				ItemStack weapon = weaponOf(source, attacker);
				TraitEffects.onHit(level, attacker, victim, source, weapon);
				if ((melee || spell) && weapon.has(ModComponents.PARTS) && !weapon.isBroken()) {
					onWeaponHit(level, attacker, victim, weapon, damageTaken);
					if (weapon.get(ModComponents.PARTS).type().kind == ForgeType.Kind.WEAPON) {
						// Gauntlets land far more blows than anything else, and they learn from every one of them.
						Mastery.addExperience(attacker, weapon, weapon.get(ModComponents.PARTS).type() == ForgeType.GUANTELETES ? 2 : 1);
					}
				}
				gainMasteryFromHit(attacker, victim, source, damageTaken);
			} finally {
				EXTRA_DAMAGE.set(false);
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(source.getEntity() instanceof LivingEntity attacker) || !(victim.level() instanceof ServerLevel level)) {
				return;
			}
			ItemStack killer = weaponOf(source, attacker);
			ForgedParts killerParts = killer.get(ModComponents.PARTS);
			if ((source.getDirectEntity() == attacker || dev.forja.magic.Spellcasting.blow()) && killerParts != null && killerParts.type().kind == ForgeType.Kind.WEAPON) {
				Mastery.addExperience(attacker, killer, 3);
			}
			int bonusExperience = Math.round(victim.getExperienceReward(level, attacker) * Upgrade.experienceBonus(Upgrades.fraction(killer, Upgrade.SABIDURIA)));
			if (bonusExperience > 0) {
				ExperienceOrb.award(level, victim.position(), bonusExperience);
			}
			dev.forja.forge.ItemHistory.addKill(killer);
			// Vivo: the metal takes its own share of what it just killed.
			TraitEffects.onKill(level, attacker, killer);
			// Sombra larga: a kill covers you for a moment.
			float shadow = Upgrade.shadowSeconds(Upgrades.fraction(killer, Upgrade.SOMBRA_LARGA));
			if (shadow > 0.0F) {
				attacker.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Math.round(shadow * 20.0F), 0, true, false), attacker);
			}

			// Siega de almas: what the blade takes, it gives back to the hand holding it.
			float souls = Upgrade.soulReapHealing(Upgrades.fraction(killer, Upgrade.SIEGA_DE_ALMAS));
			if (souls > 0.0F && attacker.isAlive()) {
				attacker.heal(souls);
				level.sendParticles(ParticleTypes.SOUL, attacker.getX(), attacker.getY(1.0), attacker.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
			}
			float chance = Upgrade.beheadChance(Upgrades.fraction(killer, Upgrade.DECAPITADOR));
			if (Synergy.CAZARRECOMPENSAS.active(killer)) {
				chance *= 2.0F;
				Synergy.CAZARRECOMPENSAS.spark(level, victim, 12);
			}
			Item head = headOf(victim.getType());
			if (chance > 0.0F && head != null && level.getRandom().nextFloat() < chance) {
				victim.spawnAtLocation(level, new ItemStack(head));
			}
		});
	}

	/** The payoff of a parry: the damage goes back, the attacker is thrown off and the shield learns from it. */
	private static void parry(ServerLevel level, LivingEntity defender, DamageSource source, ItemStack shield) {
		Mastery.addExperience(defender, shield, 3);
		level.playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.9F);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, defender.getX(), defender.getY(1.0), defender.getZ(), 14, 0.4, 0.4, 0.4, 0.2);
		// A parry is the tightest thing the mod asks of you — a handful of ticks at the start of a
		// block — and landing one looked the same as failing to. A flat disc of sparks in the direction
		// the blow came from says both that you caught it and where you caught it.
		if (source.getEntity() instanceof LivingEntity from) {
			Vec3 facing = from.position().subtract(defender.position()).normalize();
			Vec3 at = defender.position().add(0.0, defender.getBbHeight() * 0.6, 0.0).add(facing.scale(0.8));
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA, at.x, at.y, at.z, 22, 0.25, 0.25, 0.25, 0.45);
			// And a small ring in the plane of the shield, which reads as the blow glancing off it.
			Vec3 side = new Vec3(-facing.z, 0.0, facing.x).normalize();
			for (int step = 0; step < 14; step++) {
				double angle = step * Math.PI * 2.0 / 14;
				Vec3 edge = side.scale(Math.cos(angle) * 0.55).add(0.0, Math.sin(angle) * 0.55, 0.0);
				level.sendParticles(PARRY, at.x + edge.x, at.y + edge.y, at.z + edge.z, 1, 0.01, 0.01, 0.01, 0.0);
			}
		}
		// Combat overhaul: the first moments of the window are a perfect parry, the rest a plain one.
		boolean perfect = isPerfectParry(defender, shield);
		dev.forja.combat.CombatAnim.broadcast(defender, dev.forja.combat.CombatAnim.Kind.PARRY, 8, perfect ? 1.0F : 0.0F, 0.0F);
		if (defender instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable(perfect ? "gui.forja.parada" : "gui.forja.parada_normal"));
		}
		dev.forja.combat.ParryRhythm.landed(defender);
		RIPOSTE.put(defender, level.getServer().getTickCount() + (perfect ? RIPOSTE_TICKS * 2 : RIPOSTE_TICKS));
		if (source.getDirectEntity() instanceof Projectile projectile && projectile.isAlive() && source.getEntity() instanceof LivingEntity archer) {
			PENDING_REFLECT.add(new Reflected(projectile, archer, level.getGameTime()));
			projectile.setOwner(defender);
		}
		if (!(source.getEntity() instanceof LivingEntity attacker)) {
			return;
		}
		if (source.getDirectEntity() == attacker) {
			// Melee gets its own blow back, capped so a creeper does not evaporate the shield's owner's victim.
			float reflected = Math.min(8.0F, 2.0F + dev.forja.item.ForgedItems.ForgedShieldItem.bashDamage(shield));
			extraDamage(level, attacker, level.damageSources().thorns(defender), reflected);
		}
		attacker.knockback(1.4, defender.getX() - attacker.getX(), defender.getZ() - attacker.getZ(), source, 0.0F);
		attacker.hurtMarked = true;
		attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), defender);
		attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), defender);
		// Combat overhaul: a perfect parry takes the attacker's balance at once, a plain one half of it;
		// either gives the defender breath back.
		// The striker fights to a beat: any parry on it, caught in time, takes its balance (idea 48).
		if (perfect || attacker instanceof dev.forja.entity.Striker) {
			dev.forja.combat.Posture.breakPosture(attacker, level.getGameTime());
		} else {
			dev.forja.combat.Posture.shake(attacker, 0.5, level.getGameTime());
		}
		if (defender instanceof Player player) {
			float refund = dev.forja.combat.CombatConfig.get().parryStaminaRefund;
			dev.forja.combat.Stamina.restore(player, perfect ? refund * 2.0F : refund);
			dev.forja.ForjaAdvancements.award(player, "parada");
		}
	}

	/** Maestria for the rest of a fight: the bow that shot the arrow and the armor that took the hit. */
	private static void gainMasteryFromHit(LivingEntity attacker, LivingEntity victim, DamageSource source, float damageTaken) {
		if (source.getDirectEntity() instanceof AbstractArrow) {
			for (ItemStack held : List.of(attacker.getMainHandItem(), attacker.getOffhandItem())) {
				ForgedParts parts = held.get(ModComponents.PARTS);
				if (parts != null && parts.type().kind == ForgeType.Kind.RANGED) {
					Mastery.addExperience(attacker, held, 2);
					break;
				}
			}
		}
		if (damageTaken >= 1.0F) {
			for (EquipmentSlot slot : ARMOR) {
				Mastery.addExperience(victim, victim.getItemBySlot(slot), 1);
			}
		}
	}

	/** Mirrors LivingEntity#applyItemBlocking without its side effects. */
	private static boolean wouldBlock(LivingEntity entity, DamageSource source, float amount, ItemStack shield) {
		BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
		if (amount <= 0.0F || blocks == null || blocks.bypassedBy().map(types -> types.contains(source.typeHolder())).orElse(false)) {
			return false;
		}
		if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
			return false;
		}
		double angle = Math.PI;
		Vec3 sourcePosition = source.getSourcePosition();
		if (sourcePosition != null) {
			Vec3 view = Vec3.directionFromRotation(0.0F, entity.getYHeadRot());
			Vec3 toSource = sourcePosition.subtract(entity.position());
			angle = Math.acos(new Vec3(toSource.x, 0.0, toSource.z).normalize().dot(view));
		}
		return blocks.resolveBlockedDamage(source, amount, angle) > 0.0F;
	}

	/** The pale ring a caught blow leaves in the face of the shield. */
	private static final net.minecraft.core.particles.DustParticleOptions PARRY =
		new net.minecraft.core.particles.DustParticleOptions(0xE8ECF5, 1.0F);

	/** The narrowest parry window any shield has, in ticks. */
	public static final int PARRY_WINDOW_TICKS = 3;

	/** How long a parry leaves your own blow doubled. */
	public static final int RIPOSTE_TICKS = 20;

	/**
	 * The moment a shield can catch a blow instead of merely stopping it, in ticks from the instant it
	 * goes up. A forged shield blocks from tick zero, so this is pure reflex: a light plate holds the
	 * window open longer, Reflejos and Maestria widen it, and Baluarte adds four ticks on top.
	 */
	/** The extra ticks Baluarte adds to a parry window. */
	public static final int BALUARTE_EXTRA = 4;

	public static int parryWindow(ItemStack shield) {
		// A weapon's guard: a narrow window of its own, whatever the blade is made of.
		if (dev.forja.combat.WeaponGuard.is(shield)) {
			return dev.forja.combat.CombatConfig.get().weaponParryTicks;
		}
		ForgedParts parts = shield.get(ModComponents.PARTS);
		if (parts == null) {
			return 0;
		}
		float seconds = ForgeStats.sheet(shield, parts).blockDelay;
		int window = Math.max(PARRY_WINDOW_TICKS, Math.round(seconds * 20.0F));
		return window + (dev.forja.forge.Perk.has(shield, dev.forja.forge.Perk.BALUARTE) ? BALUARTE_EXTRA : 0);
	}

	/**
	 * A parry is a block caught in the first moments of raising the shield: the hit bounces back at
	 * whoever threw it, hurls them away and leaves them shaken.
	 */
	public static boolean isParry(LivingEntity defender, ItemStack shield) {
		return defender.getUseItem() == shield && !shield.isBroken() && defender.getTicksUsingItem() <= parryWindow(shield)
			&& !dev.forja.combat.ParryRhythm.rushed(defender);
	}

	/** How many ticks past the window still earn a "too late" hint. */
	public static final int LATE_PARRY_TICKS = 4;

	/** The first part of the window, where a parry is perfect: at least two ticks. */
	public static int perfectWindow(ItemStack shield) {
		return Math.max(2, Math.round(parryWindow(shield) * (float) dev.forja.combat.CombatConfig.get().parryPerfectShare));
	}

	public static boolean isPerfectParry(LivingEntity defender, ItemStack shield) {
		return isParry(defender, shield) && defender.getTicksUsingItem() <= perfectWindow(shield);
	}

	/** What a flail leaves behind: a moment where you can barely move, swing or mine. */
	private static void stun(LivingEntity victim, LivingEntity attacker, int ticks) {
		if (ticks <= 0) {
			return;
		}
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 4), attacker);
		victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1), attacker);
		victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, ticks, 1), attacker);
	}

	private static void onShieldBlock(ServerLevel level, LivingEntity defender, DamageSource source, ItemStack shield) {
		// A mangual swings around a raised shield: most of the blow lands anyway.
		if (source.getEntity() instanceof LivingEntity swinger && source.getDirectEntity() == swinger) {
			ForgedParts swung = weaponOf(source, swinger).get(ModComponents.PARTS);
			if (swung != null && swung.type() == ForgeType.MANGUAL) {
				float through = (float) swinger.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.5F;
				extraDamage(level, defender, level.damageSources().magic(), through);
				level.playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 0.9F);
			}
		}
		// Muralla: what the shield stops goes back to whoever swung, and the defender does not budge.
		if (dev.forja.forge.Perk.has(shield, dev.forja.forge.Perk.MURALLA)
			&& source.getEntity() instanceof LivingEntity swinger && source.getDirectEntity() == swinger) {
			float back = (float) swinger.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
				* dev.forja.forge.Perk.MURALLA_SHARE;
			extraDamage(level, swinger, level.damageSources().thorns(defender), back);
			defender.setDeltaMovement(0.0, defender.getDeltaMovement().y, 0.0);
			defender.hurtMarked = true;
			level.sendParticles(ParticleTypes.CRIT, swinger.getX(), swinger.getY(1.0), swinger.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
			dev.forja.forge.Perk.MURALLA.spark(level, swinger, 10);
		}
		Mastery.addExperience(defender, shield, 2);
		if (isParry(defender, shield)) {
			parry(level, defender, source, shield);
		} else if (defender instanceof ServerPlayer player && defender.getUseItem() == shield
			&& defender.getTicksUsingItem() <= parryWindow(shield) + LATE_PARRY_TICKS) {
			// Just past the window: say so, so the timing can be learned instead of guessed.
			player.sendOverlayMessage(Component.translatable(
				dev.forja.combat.ParryRhythm.rushed(defender) ? "gui.forja.parada_apresurada" : "gui.forja.parada_tarde"));
		}
		if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
			float push = Upgrade.repulsionStrength(Upgrades.fraction(shield, Upgrade.REPULSION));
			float spikeFactor = 1.0F;
			// Muro: spikes and shove feed each other.
			if (Synergy.MURO.active(shield)) {
				push *= 1.5F;
				spikeFactor = 2.0F;
				Synergy.MURO.spark(level, defender, 14);
			}
			if (push > 0.0F) {
				attacker.knockback(push, defender.getX() - attacker.getX(), defender.getZ() - attacker.getZ(), source, 0.0F);
				attacker.hurtMarked = true;
				level.playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6F, 0.7F);
			}
			float spikes = Upgrade.spikeDamage(Upgrades.fraction(shield, Upgrade.PUAS)) * spikeFactor;
			if (spikes > 0.0F) {
				attacker.hurtServer(level, level.damageSources().thorns(defender), spikes);
			}
		}
		float absorption = Upgrade.absorptionChance(Upgrades.fraction(shield, Upgrade.ABSORCION));
		if (absorption > 0.0F && !defender.hasEffect(MobEffects.ABSORPTION) && level.getRandom().nextFloat() < absorption) {
			defender.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0));
		}
		if (source.getDirectEntity() instanceof AbstractArrow && source.getEntity() instanceof LivingEntity shooter
			&& level.getRandom().nextFloat() < Upgrade.reboundChance(Upgrades.fraction(shield, Upgrade.REBOTE))) {
			Arrow rebound = new Arrow(level, defender, new ItemStack(Items.ARROW), null);
			rebound.pickup = AbstractArrow.Pickup.DISALLOWED;
			Vec3 toShooter = shooter.getEyePosition().subtract(defender.getEyePosition());
			rebound.shoot(toShooter.x, toShooter.y + toShooter.horizontalDistance() * 0.1, toShooter.z, 1.8F, 1.0F);
			level.addFreshEntity(rebound);
			level.playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 1.2F);
		}
	}

	private static void onWeaponHit(ServerLevel level, LivingEntity attacker, LivingEntity victim, ItemStack weapon, float damage) {
		Frenzy.onHit(attacker, weapon);

		// Contraataque: the blow right after a parry lands twice as hard.
		Integer riposte = RIPOSTE.remove(attacker);
		if (riposte != null && level.getServer().getTickCount() <= riposte) {
			// Combat overhaul: against a staggered foe the riposte is a finishing blow.
			boolean staggered = dev.forja.combat.Posture.isStaggered(victim, level.getGameTime());
			float riposteExtra = staggered ? damage * (float) dev.forja.combat.CombatConfig.get().riposteStaggeredExtra : damage;
			extraDamage(level, victim, level.damageSources().mobAttack(attacker), riposteExtra);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
			level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.8), victim.getZ(), 20, 0.4, 0.4, 0.4, 0.3);
		}
		RandomSource random = level.getRandom();
		ForgedParts parts = weapon.get(ModComponents.PARTS);

		float lifesteal = Upgrade.lifesteal(Frenzy.fraction(attacker, weapon, Upgrade.VAMPIRISMO));
		// Sed de sangre: the wounded give back twice as much.
		if (Synergy.SED_DE_SANGRE.active(weapon) && victim.getHealth() <= victim.getMaxHealth() * 0.3F) {
			lifesteal *= 2.0F;
			// On the victim and on the one drinking, because the point of it is the line between them.
			Synergy.SED_DE_SANGRE.spark(level, victim, 10);
			Synergy.SED_DE_SANGRE.spark(level, attacker, 6);
		}
		if (lifesteal > 0.0F && attacker.isAlive()) {
			attacker.heal(damage * lifesteal);
			level.sendParticles(ParticleTypes.HEART, attacker.getX(), attacker.getY(1.0) + 0.3, attacker.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
		}

		int poison = Upgrade.poisonTicks(Frenzy.fraction(attacker, weapon, Upgrade.VENENO));
		if (poison > 0) {
			victim.addEffect(new MobEffectInstance(MobEffects.POISON, poison, 0), attacker);
		}

		float frostFraction = Frenzy.fraction(attacker, weapon, Upgrade.ESCARCHA);
		int frost = Upgrade.frostTicks(frostFraction);
		if (frost > 0) {
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, frost, frostFraction >= 0.5F ? 1 : 0), attacker);
			// Stay under the 140 ticks that would start freeze damage; this is only the frosty overlay.
			victim.setTicksFrozen(Math.min(120, Math.max(victim.getTicksFrozen(), frost)));
			level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY(0.5), victim.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		}

		if (random.nextFloat() < Upgrade.stormChance(Frenzy.fraction(attacker, weapon, Upgrade.TORMENTA))) {
			extraDamage(level, victim, level.damageSources().lightningBolt(), 4.0F);
			// Cadena de rayos: the bolt jumps to whoever stands next to the target.
			if (Synergy.CADENA_DE_RAYOS.active(weapon)) {
				Synergy.CADENA_DE_RAYOS.ring(level, victim.position(), 3.5);
				for (LivingEntity nearby : level.getEntitiesOfClass(
					LivingEntity.class, victim.getBoundingBox().inflate(3.5), other -> other != attacker && other != victim && other.isAlive()
				)) {
					extraDamage(level, nearby, level.damageSources().lightningBolt(), 3.0F);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, nearby.getX(), nearby.getY(0.5), nearby.getZ(), 10, 0.3, 0.5, 0.3, 0.1);
				}
			}
			// Tormenta helada: the bolt leaves frost instead of fire.
			if (Synergy.TORMENTA_HELADA.active(weapon)) {
				Synergy.TORMENTA_HELADA.spark(level, victim, 16);
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1), attacker);
				victim.setTicksFrozen(Math.min(120, Math.max(victim.getTicksFrozen(), 80)));
				level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY(0.5), victim.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
			} else {
				victim.igniteForSeconds(2.0F);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getY(0.5), victim.getZ(), 20, 0.4, 0.6, 0.4, 0.2);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.6F);
		}

		boolean crit = random.nextFloat() < Upgrade.critChance(Frenzy.fraction(attacker, weapon, Upgrade.CRITICO));
		if (!crit && attacker instanceof net.minecraft.world.entity.player.Player bearer
			&& dev.forja.item.Talisman.carried(bearer, dev.forja.item.Talisman.CUARZO)
			&& random.nextFloat() < dev.forja.item.Talisman.CRIT_CHANCE) {
			crit = true;
		}
		if (crit) {
			extraDamage(level, victim, level.damageSources().mobAttack(attacker), damage * 0.5F);
			level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.5), victim.getZ(), 15, 0.4, 0.5, 0.4, 0.3);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
		}

		// Sangrado: a dagger or a scythe that catches you falling opens a wound that armor cannot stop.
		if (crit || (attacker instanceof net.minecraft.world.entity.player.Player player && player.fallDistance > 0.0F && !player.onGround())) {
			bleed(level, attacker, victim, weapon);
		}

		// Cazador: the first blow on something untouched bites deeper.
		if (dev.forja.forge.Perk.has(weapon, dev.forja.forge.Perk.CAZADOR) && victim.getHealth() + damage >= victim.getMaxHealth() - 0.01F) {
			extraDamage(level, victim, level.damageSources().mobAttack(attacker), damage * 0.25F);
			dev.forja.forge.Perk.CAZADOR.spark(level, victim, 8);
		}

		// Ejecucion finishes off the wounded; Matagigantes hits harder against bigger foes.
		float execute = Upgrade.executeBonus(Frenzy.fraction(attacker, weapon, Upgrade.EJECUCION));
		if (execute > 0.0F && victim.isAlive() && victim.getHealth() <= victim.getMaxHealth() * 0.3F) {
			extraDamage(level, victim, level.damageSources().mobAttack(attacker), damage * execute);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getY(0.6), victim.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
		}
		float giant = Upgrade.giantBonus(Frenzy.fraction(attacker, weapon, Upgrade.MATAGIGANTES));
		if (giant > 0.0F && victim.isAlive() && victim.getMaxHealth() > attacker.getMaxHealth()) {
			extraDamage(level, victim, level.damageSources().mobAttack(attacker), damage * giant);
		}

		// Justa: the spear runs through whoever stands behind the one you hit.
		if (Synergy.JUSTA.active(weapon)) {
			Vec3 forward = attacker.getLookAngle().normalize();
			Vec3 behind = victim.position().add(forward.scale(1.6));
			// Along the line it runs through, so you can see how far the thrust carried.
			for (int step = 1; step <= 6; step++) {
				Synergy.JUSTA.spark(level, victim.position()
					.add(0.0, victim.getBbHeight() * 0.5, 0.0).add(forward.scale(step * 0.35)), 1);
			}
			for (LivingEntity skewered : level.getEntitiesOfClass(
				LivingEntity.class, new net.minecraft.world.phys.AABB(behind, behind).inflate(1.4), other -> other != attacker && other != victim && other.isAlive()
			)) {
				extraDamage(level, skewered, level.damageSources().mobAttack(attacker), damage * 0.6F);
				level.sendParticles(ParticleTypes.CRIT, skewered.getX(), skewered.getY(0.6), skewered.getZ(), 8, 0.2, 0.3, 0.2, 0.1);
			}
		}

		// Vendaval: the mace's blow throws everything around the target straight up. Vanilla's own smash
		// knockback lands after this hit, so the lift waits for the end of the tick and then wipes the
		// sideways push: they go up, not away.
		if (Synergy.VENDAVAL.active(weapon)) {
			List<LivingEntity> gusted = level.getEntitiesOfClass(
				LivingEntity.class, victim.getBoundingBox().inflate(3.0), other -> other != attacker && other.isAlive()
			);
			for (LivingEntity gust : gusted) {
				PENDING_LIFT.add(new Lift(gust, level.getGameTime()));
			}
			level.sendParticles(ParticleTypes.GUST, victim.getX(), victim.getY(0.2), victim.getZ(), 6, 1.0, 0.1, 1.0, 0.0);
			Synergy.VENDAVAL.ring(level, victim.position(), 3.0);
		}

		// Afinidad: your own work bites a little deeper in your hands than in anyone else's.
		if (attacker instanceof net.minecraft.world.entity.player.Player smith && dev.forja.forge.Quality.ownWork(weapon, smith)) {
			extraDamage(level, victim, level.damageSources().mobAttack(attacker), damage * dev.forja.forge.Quality.AFFINITY_BONUS);
		}

		// The mangual: the ball carries around and everything near the target feels it, and the blow leaves
		// whoever it lands on reeling for a moment.
		if (parts.type() == ForgeType.MANGUAL) {
			float splash = Upgrade.flailSplash(Frenzy.fraction(attacker, weapon, Upgrade.SEGUNDA_CABEZA));
			int stun = Math.round(Upgrade.stunSeconds(Frenzy.fraction(attacker, weapon, Upgrade.ATURDIMIENTO)) * 20.0F);
			stun(victim, attacker, stun);
			boolean ring = Synergy.MARTILLO_PILON.active(weapon);
			if (ring) {
				Synergy.MARTILLO_PILON.ring(level, victim.position(), 2.0);
			}
			for (LivingEntity nearby : level.getEntitiesOfClass(
				LivingEntity.class, victim.getBoundingBox().inflate(2.0), other -> other != attacker && other != victim && other.isAlive()
			)) {
				extraDamage(level, nearby, level.damageSources().mobAttack(attacker), damage * splash);
				if (ring) {
					// Martillo pilon: the whole ring is left reeling, not only what the ball touched.
					stun(nearby, attacker, stun);
				}
			}
			level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.5), victim.getZ(), 8, 1.0, 0.3, 1.0, 0.1);
		}

		// The gauntlets: little per punch, but the combo turns the studs into something serious.
		if (parts.type() == ForgeType.GUANTELETES) {
			float knuckles = Upgrade.knuckleBonus(Upgrades.fraction(weapon, Upgrade.NUDILLOS_DE_HIERRO)) * Frenzy.level(attacker);
			if (knuckles > 0.0F) {
				extraDamage(level, victim, level.damageSources().mobAttack(attacker), damage * knuckles);
			}
			float flurry = Upgrade.flurryChance(Upgrades.fraction(weapon, Upgrade.RAFAGA));
			if (Synergy.CIEN_MANOS.active(weapon)) {
				// Cien manos: the hands go twice as fast, and the combo climbs twice as fast with them.
				flurry *= 2.0F;
				Frenzy.onHit(attacker);
			}
			if (random.nextFloat() < flurry) {
				extraDamage(level, victim, level.damageSources().mobAttack(attacker), damage);
				level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 1.5F);
			}
		}

		// Carnicero: the more of them there are around you, the worse each blow is for them.
		float butcher = Upgrade.butcherBonus(Frenzy.fraction(attacker, weapon, Upgrade.CARNICERO));
		if (butcher > 0.0F) {
			int crowd = level.getEntitiesOfClass(
				LivingEntity.class, attacker.getBoundingBox().inflate(5.0), other -> other != attacker && other.isAlive() && other instanceof Enemy
			).size();
			if (crowd > 1) {
				extraDamage(level, victim, level.damageSources().mobAttack(attacker), damage * butcher * crowd);
			}
		}

		// Canalizacion: a hit under a storm brings the lightning down on what it hit.
		float channel = Upgrade.channelChance(Upgrades.fraction(weapon, Upgrade.CANALIZACION));
		if (channel > 0.0F && level.isThundering() && level.canSeeSky(victim.blockPosition()) && random.nextFloat() < channel) {
			net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(victim.getX(), victim.getY(), victim.getZ());
				bolt.setCause(attacker instanceof net.minecraft.server.level.ServerPlayer server ? server : null);
				level.addFreshEntity(bolt);
			}
		}

		// Resaca: the blow drags what it hit back towards you instead of pushing it away.
		float undertow = Upgrade.undertowPull(Frenzy.fraction(attacker, weapon, Upgrade.RESACA));
		if (undertow > 0.0F && victim.isAlive()) {
			Vec3 towards = attacker.position().subtract(victim.position()).normalize().scale(undertow);
			victim.setDeltaMovement(towards.x, Math.max(0.05, towards.y * 0.2), towards.z);
			victim.hurtMarked = true;
			level.sendParticles(ParticleTypes.BUBBLE_POP, victim.getX(), victim.getY(1.0), victim.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
		}

		// Lluvia estelar: now and then the blow brings a piece of the sky down with it.
		if (random.nextFloat() < Upgrade.starfallChance(Frenzy.fraction(attacker, weapon, Upgrade.LLUVIA_ESTELAR))) {
			extraDamage(level, victim, level.damageSources().magic(), damage * 0.6F);
			level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY(2.2), victim.getZ(), 25, 0.2, 1.0, 0.2, 0.15);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.7F);
		}

		// Conductor: the charge walks from what you hit to whatever is standing near it.
		float conductor = Frenzy.fraction(attacker, weapon, Upgrade.CONDUCTOR);
		if (conductor > 0.0F) {
			double radius = Upgrade.conductorRadius(conductor);
			for (LivingEntity nearby : level.getEntitiesOfClass(
				LivingEntity.class, victim.getBoundingBox().inflate(radius), other -> other != attacker && other != victim && other.isAlive()
			)) {
				extraDamage(level, nearby, level.damageSources().lightningBolt(), damage * 0.35F);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, nearby.getX(), nearby.getY(0.6), nearby.getZ(), 8, 0.2, 0.4, 0.2, 0.1);
			}
		}

		// Pacto de sed: the blow feeds on you as much as on them.
		float hunger = Upgrade.thirstHunger(Upgrades.fraction(weapon, Upgrade.PACTO_DE_SED));
		if (hunger > 0.0F && attacker instanceof net.minecraft.server.level.ServerPlayer thirsty) {
			thirsty.causeFoodExhaustion(hunger);
		}

		// Temple: the quench the piece was cooled in shows up on every blow it lands.
		dev.forja.forge.Temple temple = dev.forja.forge.Temple.of(weapon);
		if (temple != null) {
			switch (temple) {
				case LAVA -> victim.igniteForSeconds(2.0F);
				case NIEVE -> victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), attacker);
				case MIEL -> {
					victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), attacker);
					victim.setDeltaMovement(victim.getDeltaMovement().scale(0.2));
					victim.hurtMarked = true;
				}
				case AGUA -> {
				}
			}
		}

		// Estelas: a quiet trail in the colour of the material, just enough to see the blow land.
		level.sendParticles(
			parts.primary() == ForgeMaterial.NETHERITA ? ParticleTypes.FLAME
				: parts.primary() == ForgeMaterial.AMATISTA ? ParticleTypes.END_ROD
				: parts.primary() == ForgeMaterial.ECO ? ParticleTypes.SONIC_BOOM
				: ParticleTypes.CRIT,
			victim.getX(), victim.getY(0.9), victim.getZ(), parts.primary() == ForgeMaterial.ECO ? 1 : 2, 0.2, 0.2, 0.2, 0.0
		);

		// Justa a caballo: a lance carried at a gallop hits like a wall, and the horn says so.
		if (parts.type() == ForgeType.LANZA && attacker.getVehicle() != null
			&& attacker.getVehicle().getDeltaMovement().horizontalDistance() > 0.22) {
			extraDamage(level, victim, level.damageSources().mobAttack(attacker), damage);
			level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.9), victim.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
		}

		// The foundry alloys: fire, daylight and darkness all put something extra behind a blow.
		float foundry = TraitEffects.weaponBonus(level, attacker, victim, weapon);
		if (foundry > 0.0F) {
			extraDamage(level, victim, level.damageSources().mobAttack(attacker), foundry);
		}

		// Cargado: the fourth blow lets go sideways into whatever else is standing close.
		for (LivingEntity struck : TraitEffects.charge(level, attacker, victim, weapon)) {
			extraDamage(level, struck, level.damageSources().mobAttack(attacker), damage * TraitEffects.VOLTAIC_ARC);
		}

		// Afilado: quartz parts cut deep while the edge is new, less as it wears down.
		if (parts.hasTrait(ForgeMaterial.Trait.AFILADO) && weapon.getMaxDamage() > 0) {
			float sharpness = 1.0F - (float) weapon.getDamageValue() / weapon.getMaxDamage();
			extraDamage(level, victim, level.damageSources().mobAttack(attacker), 3.0F * sharpness);
		}

		// Del End: 10% chance to blink the target somewhere nearby, like a chorus fruit.
		if (parts.hasTrait(ForgeMaterial.Trait.DEL_END) && victim.isAlive() && !(victim instanceof EnderDragon) && !(victim instanceof WitherBoss)
			&& random.nextFloat() < 0.1F) {
			for (int attempt = 0; attempt < 16; attempt++) {
				double x = victim.getX() + (random.nextDouble() - 0.5) * 16.0;
				double y = Mth.clamp(victim.getY() + (random.nextInt(16) - 8), level.getMinY(), level.getMaxY());
				double z = victim.getZ() + (random.nextDouble() - 0.5) * 16.0;
				if (victim.randomTeleport(x, y, z, true)) {
					level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
					break;
				}
			}
		}

		float splashShare = Upgrade.shockwaveShare(Frenzy.fraction(attacker, weapon, Upgrade.ONDA_DE_CHOQUE));
		if (splashShare > 0.0F) {
			float splash = damage * splashShare;
			for (LivingEntity nearby : level.getEntitiesOfClass(
				LivingEntity.class, victim.getBoundingBox().inflate(3.0), e -> e != attacker && e != victim && e.isAlive() && e instanceof Enemy
			)) {
				DamageSource splashSource = level.damageSources().mobAttack(attacker);
				nearby.hurtServer(level, splashSource, splash);
				nearby.knockback(0.8 * splashShare + 0.2, victim.getX() - nearby.getX(), victim.getZ() - nearby.getZ(), splashSource, splash);
			}
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY(0.5), victim.getZ(), 3, 1.2, 0.2, 1.2, 0.0);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
	}

	/** The weapon-hit handler, for the game test to drive without staging a real attack. */
	public static void onWeaponHitForTest(ServerLevel level, LivingEntity attacker, LivingEntity victim, ItemStack weapon, float damage) {
		onWeaponHit(level, attacker, victim, weapon, damage);
	}

	/** Damage on top of a hit that just landed; the victim's hurt cooldown would otherwise swallow it. */
	/**
	 * Sangrado: one more open wound, up to what the blade can hold. The last stack festers into wither,
	 * so a dagger that keeps cutting the same target kills without ever landing a heavy blow.
	 */
	private static void bleed(ServerLevel level, LivingEntity attacker, LivingEntity victim, ItemStack weapon) {
		ForgedParts parts = weapon.get(ModComponents.PARTS);
		if (parts == null || (parts.type() != ForgeType.DAGA && parts.type() != ForgeType.GUADANA)) {
			return;
		}
		int max = Upgrade.bleedStacks(Frenzy.fraction(attacker, weapon, Upgrade.DESGARRO));
		MobEffectInstance current = victim.getEffect(dev.forja.registry.ModEffects.SANGRADO);
		int stacks = current == null ? 0 : current.getAmplifier() + 1;
		if (stacks >= max) {
			// Nothing left to cut: the wounds turn on the body itself.
			victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), attacker);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.5F, 1.6F);
			return;
		}
		victim.addEffect(new MobEffectInstance(dev.forja.registry.ModEffects.SANGRADO, 100, stacks), attacker);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getY(0.7), victim.getZ(), 4 + stacks * 2, 0.3, 0.3, 0.3, 0.1);
	}

	/** Upgrade damage already dealt to each victim this tick: [game time, damage]. */
	private static final java.util.Map<LivingEntity, double[]> PROCS_THIS_TICK = new java.util.WeakHashMap<>();

	/**
	 * Damage an upgrade adds on top of a blow. Several upgrades proccing on the same foe in the same tick
	 * count for less and less (amount / (1 + already / softness)), so piling five damage upgrades on one
	 * weapon does not multiply its damage by five.
	 */
	private static void extraDamage(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
		if (victim.isAlive() && amount > 0.0F) {
			long now = level.getGameTime();
			double[] procs = PROCS_THIS_TICK.computeIfAbsent(victim, v -> new double[] {now, 0.0});
			if (procs[0] != now) {
				procs[0] = now;
				procs[1] = 0.0;
			}
			double softness = Math.max(0.1, dev.forja.combat.CombatConfig.get().upgradeProcSoftness);
			float softened = (float) (amount / (1.0 + procs[1] / softness));
			procs[1] += softened;
			victim.invulnerableTime = 0;
			victim.hurtServer(level, source, softened);
		}
	}

	private static ItemStack weaponOf(DamageSource source, LivingEntity attacker) {
		// A spell is its staff's or its tome's, whatever its caster has in the hand by the time it lands.
		ItemStack spell = dev.forja.magic.Spellcasting.casting();
		if (spell != null) {
			return spell;
		}
		ItemStack weapon = source.getWeaponItem();
		return weapon != null ? weapon : attacker.getMainHandItem();
	}

	private static @Nullable Item headOf(EntityType<?> type) {
		if (type == EntityTypes.ZOMBIE) {
			return Items.ZOMBIE_HEAD;
		} else if (type == EntityTypes.SKELETON) {
			return Items.SKELETON_SKULL;
		} else if (type == EntityTypes.WITHER_SKELETON) {
			return Items.WITHER_SKELETON_SKULL;
		} else if (type == EntityTypes.CREEPER) {
			return Items.CREEPER_HEAD;
		} else if (type == EntityTypes.PIGLIN) {
			return Items.PIGLIN_HEAD;
		} else if (type == EntityTypes.PLAYER) {
			return Items.PLAYER_HEAD;
		}
		return null;
	}
}
