package dev.forja.upgrade;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.mojang.math.Transformation;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * The effects of the echo shard, resin and armadillo scute traits that are not plain enchantments or
 * attributes: Resonante ore echoes and immunity to Darkness, and Pegajoso slowness.
 */
public final class TraitEffects {
	/** Tag on the glowing ore markers, so any left behind by a crash are removed when they load. */
	public static final String ECHO_TAG = "forja_eco";
	public static final int ECHO_RADIUS = 6;
	private static final int ECHO_LIMIT = 16;
	private static final int ECHO_TICKS = 60;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Map<UUID, Long> ECHOES = new HashMap<>();

	private TraitEffects() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (ECHOES.isEmpty()) {
				return;
			}
			long now = level.getGameTime();
			for (Iterator<Map.Entry<UUID, Long>> it = ECHOES.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, Long> echo = it.next();
				if (now < echo.getValue()) {
					continue;
				}
				var entity = level.getEntity(echo.getKey());
				if (entity != null) {
					entity.discard();
				}
				// Forget it either way: a marker in an unloaded chunk is discarded when it loads again.
				it.remove();
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(ECHO_TAG) && !ECHOES.containsKey(entity.getUUID())) {
				entity.discard();
			}
		});
	}

	public static int armorPieces(LivingEntity entity, ForgeMaterial.Trait trait) {
		int pieces = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack piece = entity.getItemBySlot(slot);
			ForgedParts parts = piece.get(ModComponents.PARTS);
			pieces += parts != null && !piece.isBroken() && parts.hasTrait(trait) ? 1 : 0;
		}
		return pieces;
	}

	public static boolean resonantArmor(LivingEntity entity) {
		return armorPieces(entity, ForgeMaterial.Trait.RESONANTE) > 0;
	}

	/** Pegajoso: forged weapons and the arrows of forged bows slow what they hit; worn resin slows melee attackers. */
	public static void onHit(ServerLevel level, LivingEntity attacker, LivingEntity victim, DamageSource source, ItemStack weapon) {
		boolean melee = source.getDirectEntity() == attacker;
		ForgedParts parts = weapon.get(ModComponents.PARTS);
		if (parts != null && !weapon.isBroken() && parts.hasTrait(ForgeMaterial.Trait.PEGAJOSO)) {
			ForgeType.Kind kind = parts.type().kind;
			if (melee && (kind == ForgeType.Kind.WEAPON || kind == ForgeType.Kind.TOOL)) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), attacker);
			} else if (kind == ForgeType.Kind.RANGED && source.getDirectEntity() instanceof AbstractArrow) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), attacker);
			}
		}
		int resin = armorPieces(victim, ForgeMaterial.Trait.PEGAJOSO);
		if (melee && resin > 0) {
			attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * resin, 0), victim);
		}
	}

	// ------------------------------------------------------------------ the foundry alloys
	/** How much charge a voltaic weapon holds before it lets go. */
	public static final int VOLTAIC_FULL = 4;
	/** What the arc costs the things standing next to the one you actually hit. */
	public static final float VOLTAIC_ARC = 0.5F;
	/** How far the arc reaches. */
	public static final double VOLTAIC_REACH = 5.0;
	/** Extra damage a cinereous weapon does while the hand holding it is on fire. */
	public static final float EMBER_DAMAGE = 2.5F;
	/** What a cinereous piece mends every five seconds while its bearer burns. */
	public static final int EMBER_REPAIR = 3;
	/** Extra damage sun steel does under open sky in daylight, doubled against the undead. */
	public static final float SUN_DAMAGE = 3.0F;
	/** Extra damage moon steel does where the light does not reach. */
	public static final float MOON_DAMAGE = 3.5F;
	/** The light level moon steel counts as dark. */
	public static final int MOON_DARK = 7;
	/** How much a living steel weapon mends itself on a kill. */
	public static final int LIVING_REPAIR = 25;
	/** And what it gives back to the hand when there is nothing left to mend. */
	public static final float LIVING_HEAL = 1.0F;
	/** How long soul steel takes to turn another blow aside, in ticks. */
	public static final int SOUL_GUARD_TICKS = 600;
	/** And how long a full suit of it takes, which is the whole reason to wear four. */
	public static final int SOUL_GUARD_SET_TICKS = 400;
	/** When each bearer of soul steel may be saved by it again. */
	private static final Map<UUID, Long> SOUL_GUARDS = new HashMap<>();

	/** Whether this stack is forged, whole, and has the trait. */
	private static boolean has(ItemStack stack, ForgeMaterial.Trait trait) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		return parts != null && !stack.isBroken() && parts.hasTrait(trait);
	}

	/** Open sky and daylight overhead: what sun steel wants and moon steel does not. */
	public static boolean inSun(ServerLevel level, LivingEntity entity) {
		return level.isBrightOutside() && level.canSeeSky(entity.blockPosition()) && !level.isRaining();
	}

	/** Dark enough for moon steel, which does not care whether it is night so much as whether it is black. */
	public static boolean inDark(ServerLevel level, LivingEntity entity) {
		// With the sky's own darkening folded in: under open sky at midnight this is dark, at noon it is not.
		return level.getMaxLocalRawBrightness(entity.blockPosition(), level.getSkyDarken()) <= MOON_DARK;
	}

	/**
	 * What the foundry alloys add to a blow, handed back rather than dealt here so the caller keeps the
	 * one place that knows how to hurt something without starting the whole damage event again.
	 */
	public static float weaponBonus(ServerLevel level, LivingEntity attacker, LivingEntity victim, ItemStack weapon) {
		float bonus = 0.0F;
		// Ascua: fire is what it runs on. A smith who is burning hits harder, which is a strange thing
		// to build a weapon around and exactly why it is worth building.
		if (attacker.isOnFire() && has(weapon, ForgeMaterial.Trait.ASCUA)) {
			bonus += EMBER_DAMAGE;
			level.sendParticles(ParticleTypes.LAVA, victim.getX(), victim.getY(0.9), victim.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
		}
		// Solar: it keeps the noon in it and spends it on whatever stands in front of you, and twice
		// over on things that should not be standing at all.
		if (has(weapon, ForgeMaterial.Trait.SOLAR) && inSun(level, attacker)) {
			boolean undead = victim.isInvertedHealAndHarm();
			bonus += undead ? SUN_DAMAGE * 2.0F : SUN_DAMAGE;
			if (undead) {
				victim.igniteForSeconds(4.0F);
			}
			level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY(1.0), victim.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		}
		// Nocturno: the same metal facing the other way. It does not ask what time it is, it asks how
		// dark it is where you are standing, which is a fairer question underground.
		if (has(weapon, ForgeMaterial.Trait.NOCTURNO) && inDark(level, victim)) {
			bonus += MOON_DAMAGE;
			level.sendParticles(ParticleTypes.SCULK_SOUL, victim.getX(), victim.getY(1.0), victim.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
		}
		return bonus;
	}

	/**
	 * Cargado: every blow puts one charge into the weapon, and the fourth lets go sideways. Returns the
	 * things the arc found, so the caller can spend the damage on them.
	 */
	public static java.util.List<LivingEntity> charge(ServerLevel level, LivingEntity attacker, LivingEntity victim, ItemStack weapon) {
		if (!has(weapon, ForgeMaterial.Trait.CARGADO)) {
			return java.util.List.of();
		}
		int held = weapon.getOrDefault(ModComponents.CARGA, 0) + 1;
		if (held < VOLTAIC_FULL) {
			weapon.set(ModComponents.CARGA, held);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getY(1.0), victim.getZ(), held, 0.2, 0.2, 0.2, 0.0);
			return java.util.List.of();
		}
		weapon.set(ModComponents.CARGA, 0);
		java.util.List<LivingEntity> found = level.getEntitiesOfClass(
			LivingEntity.class, victim.getBoundingBox().inflate(VOLTAIC_REACH),
			other -> other != attacker && other != victim && other.isAlive() && other instanceof net.minecraft.world.entity.monster.Enemy
		);
		java.util.List<LivingEntity> arc = found.size() > 2 ? found.subList(0, 2) : found;
		for (LivingEntity struck : arc) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, struck.getX(), struck.getY(1.0), struck.getZ(), 24, 0.3, 0.5, 0.3, 0.3);
		}
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F, 1.8F);
		return arc;
	}

	/**
	 * Animado: soul steel turns a blow aside by itself, once every thirty seconds, and does not ask
	 * whether the blow was worth it. Worn or held, one guard for the whole smith.
	 */
	public static boolean guards(ServerLevel level, LivingEntity entity) {
		boolean carries = has(entity.getMainHandItem(), ForgeMaterial.Trait.ANIMADO)
			|| armorPieces(entity, ForgeMaterial.Trait.ANIMADO) > 0;
		if (!carries) {
			return false;
		}
		long now = level.getGameTime();
		Long ready = SOUL_GUARDS.get(entity.getUUID());
		if (ready != null && now < ready) {
			return false;
		}
		// Mobs die and players leave; nothing else prunes this, so sweep it when it gets silly.
		if (SOUL_GUARDS.size() > 256) {
			SOUL_GUARDS.values().removeIf(expiry -> expiry < now);
		}
		boolean suit = ArmorSets.fullSet(entity) == ForgeMaterial.ALMACERO;
		SOUL_GUARDS.put(entity.getUUID(), now + (suit ? SOUL_GUARD_SET_TICKS : SOUL_GUARD_TICKS));
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, entity.getX(), entity.getY(1.0), entity.getZ(), 20, 0.4, 0.5, 0.4, 0.02);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.6F);
		return true;
	}

	/** Vivo: living steel eats what it kills, and hands the rest to whoever is holding it. */
	public static void onKill(ServerLevel level, LivingEntity attacker, ItemStack weapon) {
		if (!has(weapon, ForgeMaterial.Trait.VIVO)) {
			return;
		}
		// A full suit of it takes twice as much back out of the kill.
		float share = ArmorSets.fullSet(attacker) == ForgeMaterial.ACERO_VIVO ? 2.0F : 1.0F;
		if (weapon.isDamaged()) {
			weapon.setDamageValue(Math.max(0, weapon.getDamageValue() - Math.round(LIVING_REPAIR * share)));
		} else if (attacker.getHealth() < attacker.getMaxHealth()) {
			attacker.heal(LIVING_HEAL * share);
		}
		level.sendParticles(ParticleTypes.HEART, attacker.getX(), attacker.getY(1.4), attacker.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
	}

	/**
	 * What the foundry armor does when nothing is happening: sun steel warms its wearer under open sky,
	 * moon steel wakes up in the dark. Driven off the same five-second tick as the other armor effects.
	 */
	public static void armorTick(ServerLevel level, ServerPlayer player) {
		int sun = armorPieces(player, ForgeMaterial.Trait.SOLAR);
		if (sun > 0 && inSun(level, player) && player.getHealth() < player.getMaxHealth()) {
			player.heal(0.5F * sun);
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(1.2), player.getZ(), 4, 0.3, 0.5, 0.3, 0.01);
		}
		int moon = armorPieces(player, ForgeMaterial.Trait.NOCTURNO);
		if (moon > 0 && inDark(level, player)) {
			// Refreshed well before it runs out, so it never flickers.
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0, true, false, true));
			if (moon >= 4) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
			}
		}
	}

	/** Ascua: while its bearer burns, cinereous gear mends instead of wearing. */
	public static int emberRepair(ItemStack stack, LivingEntity bearer) {
		return bearer.isOnFire() && has(stack, ForgeMaterial.Trait.ASCUA) ? EMBER_REPAIR : 0;
	}

	/**
	 * Resonante tools: breaking an ore rings through the rock and outlines the same ore nearby for three
	 * seconds, seen through walls.
	 */
	public static int echoOres(ServerLevel level, ServerPlayer player, BlockPos origin, BlockState state, ItemStack tool) {
		ForgedParts parts = tool.get(ModComponents.PARTS);
		if (parts == null || tool.isBroken() || parts.type().kind != ForgeType.Kind.TOOL || !parts.hasTrait(ForgeMaterial.Trait.RESONANTE) || !state.is(ConventionalBlockTags.ORES)) {
			return 0;
		}
		int found = 0;
		long expires = level.getGameTime() + ECHO_TICKS;
		for (BlockPos pos : BlockPos.withinManhattan(origin, ECHO_RADIUS, ECHO_RADIUS, ECHO_RADIUS)) {
			if (found >= ECHO_LIMIT) {
				break;
			}
			BlockState other = level.getBlockState(pos);
			if (pos.equals(origin) || !MiningUpgrades.sameOre(state, other)) {
				continue;
			}
			Display.BlockDisplay marker = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
			marker.setBlockState(other);
			// A hair smaller than the block so it doesn't flicker against the real ore.
			marker.setTransformation(new Transformation(new Vector3f(0.02F, 0.02F, 0.02F), null, new Vector3f(0.96F, 0.96F, 0.96F), null));
			marker.setPos(pos.getX(), pos.getY(), pos.getZ());
			marker.setGlowingTag(true);
			marker.setGlowColorOverride(0x29DFEB);
			marker.addTag(ECHO_TAG);
			ECHOES.put(marker.getUUID(), expires);
			level.addFreshEntity(marker);
			found++;
		}
		if (found > 0) {
			level.playSound(null, origin, SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.0F, 1.2F);
			dev.forja.ForjaAdvancements.award(player, "eco");
		}
		return found;
	}
}
