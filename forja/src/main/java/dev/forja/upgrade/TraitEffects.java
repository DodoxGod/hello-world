package dev.forja.upgrade;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import net.minecraft.world.entity.player.Player;
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
		// Volcánico: every tick, like Frost Walker, but only for a player who has some on. Flotante: remember firm
		// ground once a second, and hand back whoever has fallen into the void.
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			boolean second = level.getGameTime() % 20 == 0;
			for (ServerPlayer player : List.copyOf(level.players())) {
				if (player.isSpectator()) {
					continue;
				}
				if (player.onGround()) {
					int pieces = armorPieces(player, ForgeMaterial.Trait.VOLCANICO);
					if (pieces > 0) {
						volcanicStep(level, player, pieces);
					}
				}
				if (player.getY() < level.getMinY()) {
					voidRescue(level, player);
				} else if (second && armorPieces(player, ForgeMaterial.Trait.FLOTANTE) > 0) {
					rememberGround(level, player);
				}
			}
		});
	}

	/** How far the lava cools round someone in magmasteel: one piece a block, two or three two, a full suit three. */
	public static int volcanicRadius(int pieces) {
		return pieces >= 4 ? 3 : pieces >= 2 ? 2 : 1;
	}

	/**
	 * Volcánico: the lava under and round the walker's feet cools into crust (block/MagmaCrustBlock), still lava
	 * sources only and only where nothing stands on top. Returns how many cooled.
	 */
	public static int volcanicStep(ServerLevel level, LivingEntity walker, int pieces) {
		int radius = volcanicRadius(pieces);
		BlockPos below = walker.blockPosition().below();
		double reach = (radius + 0.5) * (radius + 0.5);
		int cooled = 0;
		for (BlockPos pos : BlockPos.betweenClosed(below.offset(-radius, 0, -radius), below.offset(radius, 0, radius))) {
			double dx = pos.getX() + 0.5 - walker.getX();
			double dz = pos.getZ() + 0.5 - walker.getZ();
			if (dx * dx + dz * dz > reach) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(net.minecraft.world.level.block.Blocks.LAVA) && state.getFluidState().isSource()
				&& level.getBlockState(pos.above()).isAir()) {
				level.setBlockAndUpdate(pos, dev.forja.registry.ModBlocks.COSTRA_DE_MAGMA.defaultBlockState());
				cooled++;
			}
		}
		if (cooled > 0) {
			level.playSound(null, walker.getX(), walker.getY(), walker.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.15F, 1.4F);
		}
		return cooled;
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
		// Espectral: the blue fire goes with every blow, and from the plate to whoever hits it.
		if (parts != null && !weapon.isBroken() && parts.hasTrait(ForgeMaterial.Trait.ESPECTRAL)) {
			ForgeType.Kind kind = parts.type().kind;
			if ((melee && (kind == ForgeType.Kind.WEAPON || kind == ForgeType.Kind.TOOL))
				|| (kind == ForgeType.Kind.RANGED && source.getDirectEntity() instanceof AbstractArrow)) {
				soulFlame(level, victim, attacker, SOUL_FLAME_TICKS);
			}
		}
		int spectral = armorPieces(victim, ForgeMaterial.Trait.ESPECTRAL);
		if (melee && spectral > 0) {
			soulFlame(level, attacker, victim, SOUL_FLAME_ARMOR_TICKS * spectral);
		}
		// Flotante: what it strikes goes up for a moment.
		if (parts != null && !weapon.isBroken() && parts.hasTrait(ForgeMaterial.Trait.FLOTANTE)) {
			ForgeType.Kind kind = parts.type().kind;
			if ((melee && (kind == ForgeType.Kind.WEAPON || kind == ForgeType.Kind.TOOL))
				|| (kind == ForgeType.Kind.RANGED && source.getDirectEntity() instanceof AbstractArrow)) {
				lift(level, victim, attacker);
			}
		}
		// Penumbra: a blow on something that stands in the dark gives the player a little mana.
		if (parts != null && !weapon.isBroken() && parts.hasTrait(ForgeMaterial.Trait.PENUMBRA) && attacker instanceof Player player
			&& parts.type().kind == ForgeType.Kind.WEAPON && (melee || dev.forja.magic.Spellcasting.blow())
			&& !dev.forja.magic.Mana.exempt(player) && inDark(level, victim)) {
			dev.forja.magic.Mana.give(player, PENUMBRA_HIT_MANA);
		}
	}

	/** How long a blow of aetherium lifts what it hits, and how hard. */
	public static final int LIFT_TICKS = 20;
	public static final int LIFT_AMPLIFIER = 1;
	/** How long before the same target can be lifted again. */
	public static final int LIFT_COOLDOWN = 60;
	/** When each target may be lifted again. */
	private static final Map<UUID, Long> LIFTED = new HashMap<>();

	/**
	 * Flotante: Levitation for a moment, as a shulker's bullet gives, once every {@link #LIFT_COOLDOWN} ticks for any one
	 * target, and never to a boss. Returns whether it rose.
	 */
	public static boolean lift(ServerLevel level, LivingEntity victim, @org.jspecify.annotations.Nullable LivingEntity source) {
		if (!victim.isAlive() || victim.getType().builtInRegistryHolder().is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags.BOSSES)
			|| victim instanceof dev.forja.entity.FallenSmith || victim instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
			return false;
		}
		long now = level.getGameTime();
		Long ready = LIFTED.get(victim.getUUID());
		if (ready != null && now < ready) {
			return false;
		}
		if (LIFTED.size() > 256) {
			LIFTED.values().removeIf(expiry -> expiry < now);
		}
		LIFTED.put(victim.getUUID(), now + LIFT_COOLDOWN);
		victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LIFT_TICKS, LIFT_AMPLIFIER), source);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, victim.getX(), victim.getY(0.3), victim.getZ(), 14, 0.3, 0.2, 0.3, 0.05);
		return true;
	}

	// ------------------------------------------------------------------ the void hands you back
	/** How far under the bottom of the world the void hands an aetherium wearer back. */
	public static final int VOID_DEPTH = 16;
	/** How often it will, and with a full suit. */
	public static final int VOID_COOLDOWN = 3600;
	public static final int VOID_COOLDOWN_SET = 1800;
	/** What it costs each piece of aetherium, a share of its durability. */
	public static final float VOID_WEAR = 0.10F;
	/** The slow falling it leaves you with. */
	public static final int VOID_SLOW_FALL = 120;
	/** The last firm ground each wearer stood on, and in which dimension. */
	private static final Map<UUID, net.minecraft.core.GlobalPos> SAFE_GROUND = new HashMap<>();
	/** When the void will hand each wearer back again. */
	private static final Map<UUID, Long> VOID_READY = new HashMap<>();

	/** Remembers where an aetherium wearer last stood on something solid. */
	public static void rememberGround(ServerLevel level, ServerPlayer player) {
		BlockPos under = player.blockPosition().below();
		if (player.onGround() && level.getBlockState(under).isFaceSturdy(level, under, net.minecraft.core.Direction.UP)) {
			SAFE_GROUND.put(player.getUUID(), net.minecraft.core.GlobalPos.of(level.dimension(), player.blockPosition()));
		}
	}

	/**
	 * Flotante armour: fallen into the void, the wearer is handed back to the last firm ground they stood on in this
	 * dimension, with slow falling, at the cost of a tenth of every aetherium piece. Returns whether it did.
	 */
	public static boolean voidRescue(ServerLevel level, ServerPlayer player) {
		if (player.getY() > level.getMinY() - VOID_DEPTH || armorPieces(player, ForgeMaterial.Trait.FLOTANTE) == 0) {
			return false;
		}
		long now = level.getGameTime();
		Long ready = VOID_READY.get(player.getUUID());
		net.minecraft.core.GlobalPos ground = SAFE_GROUND.get(player.getUUID());
		if ((ready != null && now < ready) || ground == null || ground.dimension() != level.dimension()) {
			return false;
		}
		BlockPos to = ground.pos();
		player.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
		player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, VOID_SLOW_FALL, 0, false, true, true));
		for (EquipmentSlot slot : ARMOR) {
			ItemStack piece = player.getItemBySlot(slot);
			ForgedParts parts = piece.get(ModComponents.PARTS);
			if (parts != null && parts.hasTrait(ForgeMaterial.Trait.FLOTANTE) && piece.isDamageableItem()) {
				piece.hurtAndBreak(Math.max(1, Math.round(piece.getMaxDamage() * VOID_WEAR)), player, slot);
			}
		}
		boolean suit = ArmorSets.fullSet(player) == ForgeMaterial.ETERIO;
		VOID_READY.put(player.getUUID(), now + (suit ? VOID_COOLDOWN_SET : VOID_COOLDOWN));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(1.0), player.getZ(), 60, 0.4, 0.8, 0.4, 0.1);
		player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.forja.eterio.vacio").withColor(0xB98AE6));
		return true;
	}

	// ------------------------------------------------------------------ the far forge alloys
	/** How long a blow of wispfire leaves the blue fire on what it hit. */
	public static final int SOUL_FLAME_TICKS = 80;
	/** How long it goes to an attacker, per piece of wispfire armor it struck. */
	public static final int SOUL_FLAME_ARMOR_TICKS = 20;
	/** What the blue fire takes each time it bites, and how often. */
	public static final float SOUL_FLAME_DAMAGE = 1.0F;
	public static final int SOUL_FLAME_PERIOD = 40;

	/**
	 * Sets the blue fire on something, or keeps it burning: a new blow renews it, it never stacks. Magic, so it
	 * burns what fire cannot; an effect, so water does not put it out.
	 */
	public static void soulFlame(ServerLevel level, LivingEntity victim, LivingEntity source, int ticks) {
		if (!victim.isAlive()) {
			return;
		}
		MobEffectInstance burning = victim.getEffect(dev.forja.registry.ModEffects.LLAMA_FATUA);
		if (burning == null || burning.getDuration() < ticks) {
			victim.addEffect(new MobEffectInstance(dev.forja.registry.ModEffects.LLAMA_FATUA, ticks, 0), source);
		}
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, victim.getX(), victim.getY(0.6), victim.getZ(), 6, 0.25, 0.35, 0.25, 0.01);
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
	public static boolean inSun(net.minecraft.world.level.Level level, LivingEntity entity) {
		return level.isBrightOutside() && level.canSeeSky(entity.blockPosition()) && !level.isRaining();
	}

	/** Dark enough for moon steel, which does not care whether it is night so much as whether it is black. */
	public static boolean inDark(net.minecraft.world.level.Level level, LivingEntity entity) {
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
		// Iracundo (docs/ALEACIONES_CUMBRE.md, 2.4): a full point of damage for every step of health its bearer is missing.
		if (has(weapon, ForgeMaterial.Trait.IRACUNDO)) {
			int steps = wrathSteps(attacker);
			if (steps > 0) {
				bonus += WRATH_DAMAGE * steps;
				level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY(0.9), victim.getZ(), 2 * steps, 0.25, 0.25, 0.25, 0.01);
			}
		}
		// Ardor (docs/ALEACIONES_CUMBRE.md, 2.7): the worse its bearer is doing, the harder it hits, and past two steps
		// the blow sets the target alight.
		if (has(weapon, ForgeMaterial.Trait.ARDOR)) {
			int steps = wrathSteps(attacker);
			if (steps > 0) {
				bonus += ARDOR_DAMAGE * steps;
				if (steps >= ARDOR_IGNITE_STEPS) {
					victim.igniteForSeconds(2.0F);
				}
				level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY(0.9), victim.getZ(), 2 * steps, 0.25, 0.25, 0.25, 0.01);
			}
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

	// ------------------------------------------------------------------ the middle tier (docs/ALEACIONES_CUMBRE.md, 2.7)
	/** Ardor: damage per step of health missing, from which step the blow sets the target alight. */
	public static final float ARDOR_DAMAGE = 0.5F;
	public static final int ARDOR_IGNITE_STEPS = 2;
	/** Ardor on armour: how long Fire Resistance lasts, and how often it can be asked for. */
	public static final int ARDOR_FIRE_TICKS = 120;
	public static final int ARDOR_COOLDOWN = 600;
	/** One step of health a bearer is missing, how many count, and below which share of health armour answers a wound. */
	public static final float WRATH_STEP = 0.20F;
	public static final int WRATH_MAX_STEPS = 4;
	public static final float WRATH_ARMOR_THRESHOLD = 0.40F;
	/** Amparo: the share of its max health no blow takes more than, and how long it waits between two (shorter with the full set). */
	public static final float SHELTER_SHARE = 0.40F;
	public static final int SHELTER_COOLDOWN = 400;
	public static final int SHELTER_SET_COOLDOWN = 300;
	/** Penumbra: the price of a spell in the dark, and the mana a blow or a wound in the dark gives. */
	public static final float PENUMBRA_COST = 0.85F;
	public static final float PENUMBRA_HIT_MANA = 0.5F;
	public static final float PENUMBRA_HURT_MANA = 0.25F;
	public static final float PENUMBRA_HURT_CAP = 3.0F;
	/** Sideral: the price of a spell, at night under open sky, and the mana a block of work gives. */
	public static final float SIDEREAL_COST = 0.90F;
	public static final float SIDEREAL_NIGHT_COST = 0.80F;
	public static final float SIDEREAL_BLOCK_MANA = 0.10F;
	/** When each bearer of Ardor armour may be answered again, and each bearer of Amparo sheltered again. */
	private static final Map<UUID, Long> ARDOR_GUARDS = new HashMap<>();
	private static final Map<UUID, Long> SHELTERS = new HashMap<>();

	// ------------------------------------------------------------------ the peak alloys (docs/ALEACIONES_CUMBRE.md, 2.4)
	/** Iracundo: damage per step of health missing; how long the Strength an armour answers a bad wound with lasts, and how often. */
	public static final float WRATH_DAMAGE = 1.0F;
	public static final int WRATH_STRENGTH_TICKS = 120;
	public static final int WRATH_COOLDOWN = 600;
	/** Inquebrantable: the share of its max health a bearer lets through of one blow is {@code BASE - PER * pieces}. */
	public static final float UNYIELDING_BASE = 0.45F;
	public static final float UNYIELDING_PER = 0.05F;
	/** Místico: the price of a spell, the mana a wound gives per point of damage and piece (and at most), and a block of work. */
	public static final float MYSTIC_COST = 0.75F;
	public static final float MYSTIC_HURT_MANA = 0.5F;
	public static final float MYSTIC_HURT_CAP = 6.0F;
	public static final float MYSTIC_BLOCK_MANA = 0.25F;
	/** When each bearer of Iracundo armour may be answered with Strength again. */
	private static final Map<UUID, Long> WRATH_GUARDS = new HashMap<>();

	/** Inquebrantable: the pieces of armour whole with it, and one more if either hand holds something whole with it; at most 5. */
	public static int unyieldingCount(LivingEntity entity) {
		int count = armorPieces(entity, ForgeMaterial.Trait.INQUEBRANTABLE) + (inHand(entity, ForgeMaterial.Trait.INQUEBRANTABLE) ? 1 : 0);
		return Math.min(5, count);
	}

	/** The share of its max health a bearer of {@code count} of them lets through of one blow: 1 with none, 40 % with one, 20 % with five. */
	public static float unyieldingShare(int count) {
		return count <= 0 ? 1.0F : UNYIELDING_BASE - UNYIELDING_PER * count;
	}

	/**
	 * How far down an entity's health has gone, in steps of {@link #WRATH_STEP}: 0 at full health, 1 under 80 %, 2 under
	 * 60 %, 3 under 40 % and 4 at 20 % or less. What Iracundo and Ardor read.
	 */
	public static int wrathSteps(LivingEntity entity) {
		float max = entity.getMaxHealth();
		if (max <= 0.0F) {
			return 0;
		}
		float missing = 1.0F - entity.getHealth() / max;
		return Math.max(0, Math.min(WRATH_MAX_STEPS, (int) Math.floor(missing / WRATH_STEP + 1.0E-4F)));
	}

	/** How many things a bearer holds or wears, whole, with this trait: every armour piece, and each hand once. */
	public static int carried(LivingEntity entity, ForgeMaterial.Trait trait) {
		return armorPieces(entity, trait) + (has(entity.getMainHandItem(), trait) ? 1 : 0) + (has(entity.getOffhandItem(), trait) ? 1 : 0);
	}

	/** Whether something whole with this trait is in a hand. */
	private static boolean inHand(LivingEntity entity, ForgeMaterial.Trait trait) {
		return has(entity.getMainHandItem(), trait) || has(entity.getOffhandItem(), trait);
	}

	/** Amparo: whether the bearer wears or holds something of it, whole. */
	public static boolean shelters(LivingEntity entity) {
		return carried(entity, ForgeMaterial.Trait.AMPARO) > 0;
	}

	/** The game time at which Amparo will shelter this bearer again, or 0 if it already can. */
	public static long shelterReadyAt(LivingEntity entity) {
		return SHELTERS.getOrDefault(entity.getUUID(), 0L);
	}

	/** Forgets the wait, for the game tests to drive without letting four hundred ticks go by. */
	public static void forgetShelter(LivingEntity entity) {
		SHELTERS.remove(entity.getUUID());
	}

	/**
	 * What the traits that cap a blow make of one that reached a bearer past their armour: Amparo, once in a while, cuts it to
	 * a share of their max health and sets whoever struck alight with wispfire. Only blows armour reads (nothing that bypasses
	 * armour or invulnerability: falls, the void, starvation, /kill). Called from CombatHooks.capped.
	 *
	 * @return the damage that goes through
	 */
	public static float bearerCap(LivingEntity target, DamageSource source, float damage) {
		if (damage <= 0.0F || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)
			|| source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || !(target.level() instanceof ServerLevel level)) {
			return damage;
		}
		float max = target.getMaxHealth();
		long now = level.getGameTime();
		float cap = Float.MAX_VALUE;
		boolean shelter = false;
		if (shelters(target) && now >= shelterReadyAt(target)) {
			cap = max * SHELTER_SHARE;
			shelter = true;
		}
		// Inquebrantable: the cap that is always there. When it is the lower of the two it is the one that cuts, and Amparo
		// keeps its wait for a blow only it would have cut.
		int unyielding = unyieldingCount(target);
		if (unyielding > 0) {
			float lower = max * unyieldingShare(unyielding);
			if (lower <= cap) {
				cap = lower;
				shelter = false;
			}
		}
		if (damage <= cap) {
			return damage;
		}
		if (!shelter) {
			level.sendParticles(ParticleTypes.WAX_ON, target.getX(), target.getY(1.0), target.getZ(), 10, 0.4, 0.5, 0.4, 0.0);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.6F, 0.8F);
		}
		if (shelter) {
			if (SHELTERS.size() > 256) {
				SHELTERS.values().removeIf(expiry -> expiry < now);
			}
			boolean suit = ArmorSets.fullSet(target) == ForgeMaterial.ESPECTRACERO;
			SHELTERS.put(target.getUUID(), now + (suit ? SHELTER_SET_COOLDOWN : SHELTER_COOLDOWN));
			if (source.getEntity() instanceof LivingEntity struck) {
				soulFlame(level, struck, target, SOUL_FLAME_ARMOR_TICKS);
			}
			level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(1.0), target.getZ(), 12, 0.35, 0.5, 0.35, 0.02);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
		}
		return cap;
	}

	/**
	 * The multiplier on a spell's mana cost that the gear in a player's hands gives: the cheapest of Místico, Sideral and
	 * Penumbra, each 1 when it does not apply. The minimum and not the product, so a staff of one and a tome of another
	 * are not cheaper than the better of them.
	 */
	public static float gearSpellCost(Player player) {
		return Math.min(mysticCost(player), Math.min(siderealCost(player), penumbraCost(player)));
	}

	/** Místico: every spell costs a quarter less with it in a hand, once, however many hands hold it. */
	public static float mysticCost(Player player) {
		return inHand(player, ForgeMaterial.Trait.MISTICO) ? MYSTIC_COST : 1.0F;
	}

	/** Sideral: spells cost a tenth less with it in a hand, a fifth at night under open sky without rain. */
	public static float siderealCost(Player player) {
		if (!inHand(player, ForgeMaterial.Trait.SIDERAL)) {
			return 1.0F;
		}
		net.minecraft.world.level.Level level = player.level();
		boolean night = !level.isBrightOutside() && level.canSeeSky(player.blockPosition()) && !level.isRaining();
		return night ? SIDEREAL_NIGHT_COST : SIDEREAL_COST;
	}

	/** Penumbra: spells cost 15 % less with it in a hand when the caster stands in the dark. */
	public static float penumbraCost(Player player) {
		return inHand(player, ForgeMaterial.Trait.PENUMBRA) && inDark(player.level(), player) ? PENUMBRA_COST : 1.0F;
	}

	/**
	 * Sideral: a tool in the main hand gives a little mana for every block of some hardness it breaks. Called from
	 * MiningUpgrades with the tool that did it.
	 */
	public static void workMana(ServerLevel level, Player player, BlockPos pos, BlockState state, ItemStack tool) {
		ForgedParts parts = tool.get(ModComponents.PARTS);
		if (parts == null || tool.isBroken() || parts.type().kind != ForgeType.Kind.TOOL || dev.forja.magic.Mana.exempt(player)
			|| state.getDestroySpeed(level, pos) <= 0.0F) {
			return;
		}
		float mana = (parts.hasTrait(ForgeMaterial.Trait.SIDERAL) ? SIDEREAL_BLOCK_MANA : 0.0F)
			+ (parts.hasTrait(ForgeMaterial.Trait.MISTICO) ? MYSTIC_BLOCK_MANA : 0.0F);
		if (mana > 0.0F) {
			dev.forja.magic.Mana.give(player, mana);
		}
	}

	/**
	 * What armour answers a blow with, once it has landed (CombatUpgrades, AFTER_DAMAGE): Iracundo's Strength and Ardor's fire
	 * resistance (which a full suit makes a fire put out), each once in a while and only when the wound leaves the bearer
	 * badly hurt, Místico's mana for every wound and Penumbra's for a wound taken in the dark.
	 */
	public static void onHurt(ServerLevel level, LivingEntity victim, DamageSource source, float damageTaken) {
		if (damageTaken <= 0.0F) {
			return;
		}
		long now = level.getGameTime();
		boolean badly = victim.getHealth() < victim.getMaxHealth() * WRATH_ARMOR_THRESHOLD;
		int wrath = armorPieces(victim, ForgeMaterial.Trait.IRACUNDO);
		if (wrath > 0 && badly) {
			Long ready = WRATH_GUARDS.get(victim.getUUID());
			if (ready == null || now >= ready) {
				if (WRATH_GUARDS.size() > 256) {
					WRATH_GUARDS.values().removeIf(expiry -> expiry < now);
				}
				WRATH_GUARDS.put(victim.getUUID(), now + WRATH_COOLDOWN);
				victim.addEffect(new MobEffectInstance(MobEffects.STRENGTH, WRATH_STRENGTH_TICKS, wrath >= 4 ? 1 : 0), victim);
				level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY(1.0), victim.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
				level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.5F, 1.4F);
			}
		}
		int ardor = armorPieces(victim, ForgeMaterial.Trait.ARDOR);
		if (ardor > 0 && badly) {
			Long ready = ARDOR_GUARDS.get(victim.getUUID());
			if (ready == null || now >= ready) {
				if (ARDOR_GUARDS.size() > 256) {
					ARDOR_GUARDS.values().removeIf(expiry -> expiry < now);
				}
				ARDOR_GUARDS.put(victim.getUUID(), now + ARDOR_COOLDOWN);
				victim.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ARDOR_FIRE_TICKS, 0), victim);
				if (ardor >= 4) {
					victim.extinguishFire();
				}
				level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY(1.0), victim.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
			}
		}
		if (victim instanceof Player player && !dev.forja.magic.Mana.exempt(player)) {
			int mystic = armorPieces(victim, ForgeMaterial.Trait.MISTICO);
			if (mystic > 0) {
				dev.forja.magic.Mana.give(player, Math.min(MYSTIC_HURT_CAP, damageTaken * MYSTIC_HURT_MANA * mystic));
				level.sendParticles(ParticleTypes.ENCHANT, victim.getX(), victim.getY(1.0), victim.getZ(), 6, 0.3, 0.5, 0.3, 0.2);
			}
			int penumbra = armorPieces(victim, ForgeMaterial.Trait.PENUMBRA);
			if (penumbra > 0 && inDark(level, victim)) {
				dev.forja.magic.Mana.give(player, Math.min(PENUMBRA_HURT_CAP, damageTaken * PENUMBRA_HURT_MANA * penumbra));
			}
		}
	}
}
