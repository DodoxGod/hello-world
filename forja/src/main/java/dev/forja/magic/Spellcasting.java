package dev.forja.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.forja.entity.MagicBolt;
import dev.forja.entity.Shockwave;
import dev.forja.forge.ForgeType;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Synergy;
import dev.forja.upgrade.Upgrade;
import dev.forja.upgrade.Upgrades;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The two magic weapons, which Andy chose from drawings: the crescent staff throws a bolt, and the forged
 * tome lays an area five blocks ahead of whoever opens it, in the colour of the circle on its cover, and
 * leaves a rune there.
 *
 * <p>Neither has an element of its own. Both are built round a <b>núcleo</b>, a stone cut like any other
 * head, and the núcleo's material is the magic: its colour is the colour of the bolt, of the area and
 * of the rune, and its bite is what they do. So an amethyst staff and a blaze-rod staff are different
 * weapons without a line of code between them, which is how the rest of the mod works too.
 */
public final class Spellcasting {
	public static final int BOLT_COOLDOWN = 14;
	public static final double BOLT_SPEED = 1.5;
	public static final int TOME_COOLDOWN = 70;
	/** "un área 5 bloques delante del jugador". */
	public static final double TOME_DISTANCE = 5.0;
	public static final double RUNE_REACH = 3.0;
	public static final int RUNE_TICKS = 120;
	/** How often a rune bites whoever stands on it, and so how often client/ShockwaveRenderer makes it flare. */
	public static final int RUNE_EVERY = 10;
	/** Resonancia: how long after a bolt its echo leaves the staff, and after an opening the area opens again. */
	public static final int ECHO_BOLT_TICKS = 6;
	public static final int ECHO_AREA_TICKS = 16;
	/** Prisma: how far apart the bolts of the fan are, in degrees. */
	public static final float FAN_DEGREES = 9.0F;
	/** Sobrecarga: how much wider the big area is, in blocks. */
	public static final double OVERCHARGE_REACH = 1.0;
	/** Colapso: what a rune going out is worth, against its opening. */
	public static final float COLLAPSE_SHARE = 0.75F;

	/**
	 * A rune lying on the floor. It counts its own age rather than what it has left, and bites on the tens of
	 * it: the client flares the drawing on the tens of the same age, so the flare is the bite however long
	 * Tinta indeleble has made the rune last.
	 */
	private static final class Rune {
		final ServerLevel level;
		final Vec3 at;
		final double reach;
		final int ticks;
		final float opening;
		final int colour;
		final UUID owner;
		final ItemStack weapon;
		final Shockwave mark;
		final float pull;
		final float heal;
		final boolean collapses;
		int age;

		Rune(ServerLevel level, Vec3 at, double reach, int ticks, float opening, int colour, UUID owner, ItemStack weapon, Shockwave mark) {
			this.level = level;
			this.at = at;
			this.reach = reach;
			this.ticks = ticks;
			this.opening = opening;
			this.colour = colour;
			this.owner = owner;
			this.weapon = weapon;
			this.mark = mark;
			this.pull = Upgrade.vortexPull(Upgrades.fraction(weapon, Upgrade.VORTICE));
			this.heal = Upgrade.sanctuaryHeal(Upgrades.fraction(weapon, Upgrade.SANTUARIO));
			this.collapses = Synergy.COLAPSO.active(weapon);
		}

		float bite() {
			return this.opening * 0.25F;
		}
	}

	private static final List<Rune> RUNES = new ArrayList<>();

	/** A spell that Resonancia will repeat: a bolt from wherever its caster is looking by then, or the same area again. */
	private record Echo(ServerLevel level, UUID owner, ItemStack weapon, long due, float share, boolean big, @org.jspecify.annotations.Nullable Rune rune) {
	}

	private static final List<Echo> ECHOES = new ArrayList<>();

	/** Sobrecarga: spells cast since the last big one, by caster. */
	private static final Map<UUID, Integer> CASTS = new HashMap<>();

	/** What is landing right now: the staff or tome behind it, and whether it lands as a blow. */
	private record Cast(ItemStack weapon, boolean blow) {
	}

	private static final ThreadLocal<Cast> CASTING = new ThreadLocal<>();

	/**
	 * The staff or tome whose magic is hurting something at this moment, or {@code null}.
	 *
	 * <p>A bolt is not the hand that threw it and a rune is not anybody's hand at all, so to the rest of the
	 * mod a spell's damage would belong to whatever its caster happens to be holding by then — a rune
	 * laid with a tome would poison with the sword drawn afterwards. upgrade/CombatUpgrades asks this first.
	 */
	public static @org.jspecify.annotations.Nullable ItemStack casting() {
		Cast cast = CASTING.get();
		return cast == null ? null : cast.weapon();
	}

	/**
	 * Whether that magic lands as a blow of the weapon: a bolt and a tome's area do, and carry every
	 * upgrade a blade's edge would — Vampirismo, Escarcha, Tormenta, the lot. A rune's bite does not: it
	 * bites twice a second at everything on it, and a dozen blows' worth of lightning and mastery from
	 * one reading would make the tome the answer to everything.
	 */
	public static boolean blow() {
		Cast cast = CASTING.get();
		return cast != null && cast.blow();
	}

	/**
	 * Hurts something with a spell of {@code weapon}'s: what the enchantments on it add to a blow they add
	 * to this (Filo is an edge on a sword and a sharper stone on a staff), and while it lands
	 * {@link #casting} answers with the weapon.
	 */
	public static boolean land(ServerLevel level, LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float base, ItemStack weapon, boolean blow) {
		Cast before = CASTING.get();
		CASTING.set(new Cast(weapon, blow));
		try {
			float damage = weapon.isEmpty() ? base : net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level, weapon, victim, source, base);
			return victim.hurtServer(level, source, damage);
		} finally {
			if (before == null) {
				CASTING.remove();
			} else {
				CASTING.set(before);
			}
		}
	}

	private Spellcasting() {
	}

	public static boolean casts(ForgeType type) {
		return type == ForgeType.BACULO || type == ForgeType.GRIMORIO;
	}

	/** What the núcleo of a staff or a tome is made of, which is everything about its magic. */
	public static ForgeMaterial core(ForgedParts parts) {
		int slot = parts.type().slots.indexOf(PartType.NUCLEO);
		return parts.material(Math.max(0, slot));
	}

	/** What a bolt is worth when it lands: the core's bite, a gem of it being worth a little more loose than on a blade. */
	public static float boltDamage(ForgeMaterial core) {
		return Math.max(3.0F, 4.0F + core.attackDamageBonus * 0.9F);
	}

	/** What the tome's area does when it opens, and a quarter of it again every half second on the rune. */
	public static float areaDamage(ForgeMaterial core) {
		return Math.max(3.0F, 5.0F + core.attackDamageBonus);
	}

	/** The wait after a spell, in ticks: the weapon's own, less what Conjuro veloz takes off it. */
	public static int cooldown(ItemStack stack, ForgeType type) {
		int wait = type == ForgeType.BACULO ? BOLT_COOLDOWN : TOME_COOLDOWN;
		return Math.max(2, Math.round(wait * (1.0F - Upgrade.castHaste(Upgrades.fraction(stack, Upgrade.CONJURO_VELOZ)))));
	}

	/** How long a rune laid with this tome lasts, in ticks. */
	public static int runeTicks(ItemStack tome) {
		return RUNE_TICKS + Upgrade.runeExtraTicks(Upgrades.fraction(tome, Upgrade.TINTA_INDELEBLE));
	}

	/**
	 * Sobrecarga: counts this spell, and says whether it is the big one. The one before it says so out
	 * loud — a rising note and a ring of the núcleo's colour round the caster — because a charge nobody
	 * knows they are holding is a charge spent on the first thing that moves.
	 */
	public static boolean overcharged(ServerLevel level, Player player, ItemStack stack, int colour) {
		if (Upgrades.fraction(stack, Upgrade.SOBRECARGA) <= 0.0F) {
			CASTS.remove(player.getUUID());
			return false;
		}
		int count = CASTS.merge(player.getUUID(), 1, Integer::sum);
		if (count >= Upgrade.OVERCHARGE_EVERY) {
			CASTS.put(player.getUUID(), 0);
			return true;
		}
		if (count == Upgrade.OVERCHARGE_EVERY - 1) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6F, 1.7F);
			for (int step = 0; step < 16; step++) {
				double angle = step * Math.PI / 8.0;
				level.sendParticles(new DustParticleOptions(colour, 1.2F), player.getX() + Math.cos(angle) * 0.8, player.getY() + 0.1, player.getZ() + Math.sin(angle) * 0.8,
					1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		return false;
	}

	public static InteractionResult tryCast(Level level, Player player, InteractionHand hand, ForgeType type) {
		ItemStack stack = player.getItemInHand(hand);
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (!casts(type) || parts == null || stack.isBroken()) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			ForgeMaterial core = core(parts);
			boolean big = overcharged(server, player, stack, core.color);
			float power = big ? 1.0F + Upgrade.overchargeBonus(Upgrades.fraction(stack, Upgrade.SOBRECARGA)) : 1.0F;
			float echo = Upgrade.echoShare(Upgrades.fraction(stack, Upgrade.RESONANCIA));
			if (type == ForgeType.BACULO) {
				volley(server, player, core, stack, power, big, true);
				if (echo > 0.0F) {
					ECHOES.add(new Echo(server, player.getUUID(), stack, server.getGameTime() + ECHO_BOLT_TICKS, power * echo, big, null));
				}
			} else {
				Rune rune = open(server, player, core, stack, power, big);
				if (echo > 0.0F) {
					ECHOES.add(new Echo(server, player.getUUID(), stack, server.getGameTime() + ECHO_AREA_TICKS, echo, big, rune));
				}
			}
			player.getCooldowns().addCooldown(stack, cooldown(stack, type));
			stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
		}
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}

	/**
	 * What leaves the staff: the bolt, and with Prisma one more either side of it (two with Enjambre), each
	 * worth a share of the one in the middle. Buscador is on every one of them. An echo is the middle bolt
	 * alone: it is the spell heard again, not cast again.
	 */
	private static void volley(ServerLevel level, Player player, ForgeMaterial core, ItemStack staff, float power, boolean big, boolean fan) {
		float damage = boltDamage(core) * power;
		float seek = (float) Math.toRadians(Upgrade.seekDegrees(Upgrades.fraction(staff, Upgrade.BUSCADOR)));
		Vec3 look = player.getLookAngle();
		MagicBolt bolt = new MagicBolt(level, player, look, core.color, damage, staff, seek, big);
		level.addFreshEntity(fan ? bolt : bolt.insistent());
		float prism = fan ? Upgrade.prismShare(Upgrades.fraction(staff, Upgrade.PRISMA)) : 0.0F;
		if (prism > 0.0F) {
			int pairs = Synergy.ENJAMBRE.active(staff) ? 2 : 1;
			for (int pair = 1; pair <= pairs; pair++) {
				for (int side = -1; side <= 1; side += 2) {
					Vec3 aside = look.yRot((float) Math.toRadians(side * pair * FAN_DEGREES));
					level.addFreshEntity(new MagicBolt(level, player, aside, core.color, damage * prism, staff, seek, false));
				}
			}
			if (pairs == 2) {
				Synergy.ENJAMBRE.spark(level, player.getEyePosition().add(look.scale(0.8)), 8);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, big ? 0.8F : fan ? 1.4F : 1.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, big ? 0.6F : 0.35F, big ? 1.0F : 1.6F);
	}

	/** Where the tome's area lands: five blocks along the way the reader faces, nearer if a wall is in the way, on the floor there. */
	public static Vec3 target(Level level, Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
		Vec3 end = eye.add(flat.scale(TOME_DISTANCE));
		HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 spot = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation().subtract(flat.scale(0.6));
		float floor = Shockwave.floorAt(level, spot.x, player.getY(), spot.z);
		return new Vec3(spot.x, player.getY() + floor, spot.z);
	}

	private static Rune open(ServerLevel level, Player player, ForgeMaterial core, ItemStack tome, float power, boolean big) {
		Vec3 at = target(level, player);
		float damage = areaDamage(core) * power;
		double reach = RUNE_REACH + (big ? OVERCHARGE_REACH : 0.0);
		int ticks = runeTicks(tome);
		// The area, in the colour of the circle on the book, and the rune it leaves lying there.
		Shockwave.burst(level, at, reach, 10, core.color, big ? 0.8F : 0.45F);
		Shockwave mark = Shockwave.rune(level, at, reach, ticks, core.color);
		Rune rune = new Rune(level, at, reach, ticks, damage, core.color, player.getUUID(), tome, mark);
		RUNES.add(rune);
		strike(rune, damage, player, true);
		level.sendParticles(new DustParticleOptions(core.color, 1.6F), at.x, at.y + 0.4, at.z, big ? 70 : 40, reach * 0.5, 0.3, reach * 0.5, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.2F, big ? 0.55F : 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		return rune;
	}

	/**
	 * Everything standing on a rune is hurt: as the area opens (and when it echoes, and when it collapses),
	 * which are blows of the tome's, and by the rune's own bites, which are not and which slow whatever they
	 * catch. What does the hurting is the rune and who is behind it is the reader — so it is never a blow of
	 * the hand's, and whoever dies on it is still the reader's kill.
	 *
	 * <p>None of it shoves. Magic damage knocks back from where it came from, which here is the middle of
	 * the rune: left alone, the opening throws its prey off the rune it is about to leave and every bite
	 * pushes it further. So what a struck thing was doing it goes on doing — or, with Vortice, a bite drags
	 * it towards the middle instead.
	 */
	private static void strike(Rune rune, float damage, @org.jspecify.annotations.Nullable Player caster, boolean blow) {
		ServerLevel level = rune.level;
		Vec3 at = rune.at;
		double reach = rune.reach;
		AABB box = new AABB(at, at).inflate(reach, 2.0, reach);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, other -> other.isAlive() && other != caster)) {
			double dx = victim.getX() - at.x;
			double dz = victim.getZ() - at.z;
			if (dx * dx + dz * dz > reach * reach || (caster != null && victim.isAlliedTo(caster))) {
				continue;
			}
			Vec3 moving = victim.getDeltaMovement();
			victim.invulnerableTime = 0;
			land(level, victim, level.damageSources().indirectMagic(rune.mark, caster), damage, rune.weapon, blow);
			if (!blow) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1, false, false));
				double away = Math.sqrt(dx * dx + dz * dz);
				if (rune.pull > 0.0F && away > 0.5) {
					moving = new Vec3(-dx / away * rune.pull, moving.y, -dz / away * rune.pull);
				}
			}
			victim.setDeltaMovement(moving);
			victim.hurtMarked = true;
		}
	}

	/** Santuario: the reader's own rune mends them while they stand on it, and hardens them from half way. */
	private static void shelter(Rune rune, @org.jspecify.annotations.Nullable Player caster) {
		if (rune.heal <= 0.0F || caster == null || !caster.isAlive()) {
			return;
		}
		double dx = caster.getX() - rune.at.x;
		double dz = caster.getZ() - rune.at.z;
		if (dx * dx + dz * dz > rune.reach * rune.reach || Math.abs(caster.getY() - rune.at.y) > 2.0) {
			return;
		}
		caster.heal(rune.heal);
		if (Upgrades.fraction(rune.weapon, Upgrade.SANTUARIO) >= 0.5F) {
			caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 0, true, false));
		}
		rune.level.sendParticles(new DustParticleOptions(Upgrade.SANTUARIO.color, 0.9F), caster.getX(), caster.getY() + 0.9, caster.getZ(), 4, 0.3, 0.5, 0.3, 0.0);
	}

	/** How many runes are lying about, which is what the test asks. */
	public static int runes() {
		return RUNES.size();
	}

	/** How long the rune that has longest to go still has, in ticks. */
	public static int longestRune() {
		int longest = 0;
		for (Rune rune : RUNES) {
			longest = Math.max(longest, rune.ticks - rune.age);
		}
		return longest;
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			for (int index = ECHOES.size() - 1; index >= 0; index--) {
				Echo echo = ECHOES.get(index);
				if (echo.level() != level || level.getGameTime() < echo.due()) {
					continue;
				}
				ECHOES.remove(index);
				resound(level, echo);
			}
			for (int index = RUNES.size() - 1; index >= 0; index--) {
				Rune rune = RUNES.get(index);
				if (rune.level != level) {
					continue;
				}
				rune.age++;
				Player caster = level.getPlayerByUUID(rune.owner);
				if (rune.age >= rune.ticks) {
					RUNES.remove(index);
					if (rune.collapses) {
						Shockwave.burst(level, rune.at, rune.reach, 8, rune.colour, 0.7F);
						Synergy.COLAPSO.spark(level, rune.at.add(0.0, 0.5, 0.0), 24);
						level.playSound(null, rune.at.x, rune.at.y, rune.at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.4F, 0.5F);
						strike(rune, rune.opening * COLLAPSE_SHARE, caster, true);
					}
					continue;
				}
				if (rune.age % RUNE_EVERY == 0) {
					strike(rune, rune.bite(), caster, false);
					shelter(rune, caster);
					level.sendParticles(new DustParticleOptions(rune.colour, 1.0F), rune.at.x, rune.at.y + 0.15, rune.at.z, 6,
						rune.reach * 0.45, 0.05, rune.reach * 0.45, 0.0);
				}
			}
		});
	}

	/** Resonancia, coming due: the staff speaks again the way its caster is looking now; the area opens again where it lies. */
	private static void resound(ServerLevel level, Echo echo) {
		Player caster = level.getPlayerByUUID(echo.owner());
		ForgedParts parts = echo.weapon().get(ModComponents.PARTS);
		if (caster == null || !caster.isAlive() || parts == null) {
			return;
		}
		if (echo.rune() == null) {
			volley(level, caster, core(parts), echo.weapon(), echo.share(), false, false);
			return;
		}
		Rune rune = echo.rune();
		Shockwave.burst(level, rune.at, rune.reach, 10, rune.colour, 0.3F);
		level.playSound(null, rune.at.x, rune.at.y, rune.at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.9F);
		strike(rune, rune.opening * echo.share(), caster, true);
	}
}
