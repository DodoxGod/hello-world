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
	/**
	 * The wait after a player's spell. Andy, 2026-09-28, asked how the magic weapons should go with the mana bar:
	 * "maná y un enfriamiento corto" — each spell costs mana and the wait is cut a lot (a bolt from 14 ticks to
	 * 6, an area from 70 to 20), so a fight is a burst until the bar is empty and then a pause while it fills.
	 * Andy, 2026-09-30: the magic weapons were broken, the staff killing everything in the balance report
	 * (docs/EQUILIBRIO.md) three times as fast as melee. Ten ticks now, the same half second a mob stays
	 * invulnerable after a blow; the tome stays at twenty, as its rune now lies alone.
	 */
	public static final int BOLT_COOLDOWN = 10;
	public static final double BOLT_SPEED = 1.5;
	public static final int TOME_COOLDOWN = 20;
	/**
	 * The wait after a monster's spell (entity/ai/CasterGoal): the old one. Monsters spend no mana, so the wait
	 * is all that holds them back, and a skeleton throwing four bolts a second would be a different game.
	 */
	public static final int MONSTER_BOLT_COOLDOWN = 14;
	public static final int MONSTER_TOME_COOLDOWN = 70;
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
	 * What each bite of a player's rune is worth against its opening, twice a second. Monsters' runes keep the
	 * old quarter ({@link #MONSTER_BITE_SHARE}).
	 */
	public static final float BITE_SHARE = 0.15F;
	public static final float MONSTER_BITE_SHARE = 0.25F;
	/** Descarga: the most a poured bar adds to a spell, however deep the bar. */
	public static final float POUR_MOST = 1.0F;
	/**
	 * "Las armas mágicas se cargan con golpe izquierdo en lugar de derecho": the right button is the magic,
	 * so that is where the charge goes. Held, the spell gathers for this long; let go, it leaves, and a full
	 * charge is worth {@link #CHARGE_BONUS} more. A tap is still the plain spell, as it always was.
	 */
	public static final int STAFF_CHARGE_TICKS = 20;
	public static final int TOME_CHARGE_TICKS = 30;
	public static final float CHARGE_BONUS = 0.5F;
	/** Who is gathering a spell right now, so the pose is dropped when the charge ends some other way. */
	private static final Map<LivingEntity, ForgeType> GATHERING = new java.util.WeakHashMap<>();
	/** Who has heard the full-charge chime of the charge they are holding (a quicker charge skips ticks). */
	private static final java.util.Set<LivingEntity> CHIMED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

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
		/** Laid by a monster: it leaves the other monsters alone, even once its reader is dead. */
		boolean spareMonsters;
		/** What a bite is worth against the opening: a player's rune {@link #BITE_SHARE}, a monster's more. */
		float biteShare = BITE_SHARE;
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
			return this.opening * this.biteShare;
		}
	}

	private static final List<Rune> RUNES = new ArrayList<>();

	/** A rune lying open, as the monsters see it (ai/ObsV3): where, how far, how long it has left, and whose. */
	public record Area(Vec3 at, double reach, int ticksLeft, boolean sparesMonsters, UUID owner) {
	}

	/** Every rune open in a level right now. */
	public static List<Area> areas(ServerLevel level) {
		List<Area> out = new ArrayList<>();
		for (Rune rune : RUNES) {
			if (rune.level == level) {
				out.add(new Area(rune.at, rune.reach, rune.ticks - rune.age, rune.spareMonsters, rune.owner));
			}
		}
		return out;
	}

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
			// What the enchantments add (Filo and the rest) counts at the share every upgrade on a spell counts at
			// (CombatUpgrades.SPELL_EXTRA_SHARE): a flat +3 on a bolt of five is not the +3 on a blade of eight.
			float damage = weapon.isEmpty() ? base
				: base + (net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(level, weapon, victim, source, base) - base)
					* dev.forja.upgrade.CombatUpgrades.SPELL_EXTRA_SHARE;
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
		return type == ForgeType.BACULO || type == ForgeType.GRIMORIO || type == ForgeType.FAROL;
	}

	/** What the núcleo of a staff or a tome is made of, which is everything about its magic. */
	public static ForgeMaterial core(ForgedParts parts) {
		int slot = parts.type().slots.indexOf(PartType.NUCLEO);
		return parts.material(Math.max(0, slot));
	}

	/**
	 * What a player's bolt is worth when it lands: the core's bite. Andy, 2026-09-30: the staff was the best weapon
	 * in the game by far. Now a bolt every half second from the best núcleo kills about as fast as the typical
	 * melee weapon of the same tier, a Mago's a little faster, and neither goes on for long without mana.
	 */
	public static float boltDamage(ForgeMaterial core) {
		return Math.max(2.5F, 2.5F + core.attackDamageBonus * 0.5F);
	}

	/** What the tome's area does when it opens, and {@link #BITE_SHARE} of it again every half second on the rune. */
	public static float areaDamage(ForgeMaterial core) {
		return Math.max(3.0F, 4.0F + core.attackDamageBonus * 1.15F);
	}

	/** A monster's bolt and area: the old numbers, as a monster pays no mana and waits the old waits. */
	public static float monsterBoltDamage(ForgeMaterial core) {
		return Math.max(3.0F, 4.0F + core.attackDamageBonus * 0.9F);
	}

	public static float monsterAreaDamage(ForgeMaterial core) {
		return Math.max(3.0F, 5.0F + core.attackDamageBonus);
	}

	private static float boltDamage(ForgeMaterial core, LivingEntity caster) {
		return caster instanceof Player ? boltDamage(core) : monsterBoltDamage(core);
	}

	private static float areaDamage(ForgeMaterial core, LivingEntity caster) {
		return caster instanceof Player ? areaDamage(core) : monsterAreaDamage(core);
	}

	/** The wait after a player's spell, in ticks: the weapon's own, less what Conjuro veloz takes off it. */
	public static int cooldown(ItemStack stack, ForgeType type) {
		return hasted(stack, type == ForgeType.BACULO ? BOLT_COOLDOWN : type == ForgeType.FAROL ? Healing.COOLDOWN : TOME_COOLDOWN);
	}

	/** The wait after a monster's spell: the old, longer one, as a monster pays no mana. */
	public static int monsterCooldown(ItemStack stack, ForgeType type) {
		return hasted(stack, type == ForgeType.BACULO ? MONSTER_BOLT_COOLDOWN : MONSTER_TOME_COOLDOWN);
	}

	private static int hasted(ItemStack stack, int wait) {
		return Math.max(2, Math.round(wait * (1.0F - Upgrade.castHaste(Upgrades.fraction(stack, Upgrade.CONJURO_VELOZ)))));
	}

	/**
	 * What a spell of this weapon costs in mana: the tap's price, a quarter more at a full charge (for half again
	 * the damage, so holding the spell is the thrifty way to cast), less what Concentración saves.
	 */
	public static float manaCost(ItemStack stack, ForgeType type, float charge) {
		dev.forja.combat.CombatConfig cfg = dev.forja.combat.CombatConfig.get();
		return tapCost(stack, type) * (1.0F + cfg.manaChargeExtra * Math.max(0.0F, Math.min(1.0F, charge)));
	}

	/** The price of a tap, which is the least a spell of this weapon can cost. */
	public static float tapCost(ItemStack stack, ForgeType type) {
		dev.forja.combat.CombatConfig cfg = dev.forja.combat.CombatConfig.get();
		float base = type == ForgeType.BACULO ? cfg.manaBoltCost : type == ForgeType.FAROL ? Healing.MANA_COST : cfg.manaTomeCost;
		return base * (1.0F - Upgrade.manaDiscount(Upgrades.fraction(stack, Upgrade.CONCENTRACION)));
	}

	/**
	 * How far a charge can be paid for with {@code mana}: the charge itself if there is enough, and otherwise as
	 * far as the mana goes. A spell gathered past what the bar holds leaves as strong as the bar could make it.
	 */
	public static float affordableCharge(ItemStack stack, ForgeType type, float charge, float mana) {
		float extra = dev.forja.combat.CombatConfig.get().manaChargeExtra;
		float tap = tapCost(stack, type);
		if (manaCost(stack, type, charge) <= mana || extra <= 0.0F || tap <= 0.0F) {
			return charge;
		}
		return Math.max(0.0F, Math.min(charge, (mana / tap - 1.0F) / extra));
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
	public static boolean overcharged(ServerLevel level, LivingEntity player, ItemStack stack, int colour) {
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
		// Not even a tap's worth in the bar: a fizzle and a flash of the bar, and the arm stays down. Sangre por
		// maná (the Mago's tree) pays the rest in health when it lets go.
		if (!Mana.canAfford(player, tapCost(stack, type)) && !dev.forja.clase.ClassEffects.has(player, dev.forja.clase.Hooks.SANGRE_POR_MANA)) {
			Mana.deny(player);
			return InteractionResult.FAIL;
		}
		// Held, not thrown: the spell leaves when the button is let go (release).
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	/** How long this weapon takes to gather a full charge. */
	public static int chargeTicks(ForgeType type) {
		return type == ForgeType.BACULO ? STAFF_CHARGE_TICKS : type == ForgeType.FAROL ? Healing.CHARGE_TICKS : TOME_CHARGE_TICKS;
	}

	/** How far a charge held this long has got, 0 to 1. */
	public static float chargeShare(ForgeType type, int held) {
		return Math.min(1.0F, Math.max(0, held) / (float) chargeTicks(type));
	}

	/**
	 * A tick of the right button held on a staff or a tome: the arm draws back into its charge pose, and
	 * small motes of the núcleo's colour drift in towards the hand, closer and thicker the further it has
	 * got ("que cuando se carguen den pequeñas partículas"). Full, a chime and a little ring of them.
	 */
	public static void charging(Level level, LivingEntity caster, ItemStack stack, ForgeType type, int held) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (!(level instanceof ServerLevel server) || parts == null) {
			return;
		}
		// Canalización: a mage's charge fills sooner, so count the ticks held as more than they were.
		held = Math.round(held / dev.forja.clase.ClassEffects.spellChargeMultiplier(caster));
		int full = chargeTicks(type);
		if (GATHERING.put(caster, type) == null) {
			dev.forja.combat.CombatAnim.broadcast(caster, dev.forja.combat.CombatAnim.Kind.CHARGE, full, 1.0F, 0.0F);
		}
		int colour = core(parts).color;
		Vec3 hand = hand(caster);
		float share = chargeShare(type, held);
		var random = server.getRandom();
		int motes = share >= 1.0F ? (held % 4 == 0 ? 1 : 0) : 1 + (share > 0.5F ? 1 : 0);
		for (int i = 0; i < motes; i++) {
			// Somewhere on a shell round the hand that shrinks as the charge grows, drifting inwards.
			double radius = 0.9 - 0.55 * share;
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			Vec3 from = hand.add(dir.scale(radius));
			Vec3 in = dir.scale(-0.06);
			server.sendParticles(new DustParticleOptions(colour, 0.4F + 0.3F * share), from.x, from.y, from.z, 0, in.x, in.y, in.z, 1.0);
		}
		if (held >= full && !CHIMED.contains(caster)) {
			CHIMED.add(caster);
			for (int step = 0; step < 10; step++) {
				double angle = step * Math.PI / 5.0;
				server.sendParticles(new DustParticleOptions(colour, 0.7F), hand.x + Math.cos(angle) * 0.35, hand.y, hand.z + Math.sin(angle) * 0.35,
					1, 0.0, 0.0, 0.0, 0.0);
			}
			server.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
		}
	}

	/** The button let go: the spell leaves, as strong as the charge got. */
	public static boolean release(Level level, LivingEntity caster, ItemStack stack, ForgeType type, int held) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null || stack.isBroken() || !(caster instanceof Player player)) {
			return false;
		}
		InteractionHand hand = player.getUsedItemHand();
		if (level instanceof ServerLevel server) {
			stopGathering(caster);
			// Canalización (clase/ClassEffects): a mage's charge fills sooner, so the ticks held count for more.
			int counted = Math.round(held / dev.forja.clase.ClassEffects.spellChargeMultiplier(player));
			float charge = chargeShare(type, counted);
			// Carga profunda: a full charge held a while longer comes out stronger (ClassEffects.spellShape).
			float[] deep = dev.forja.clase.ClassEffects.hook(player, dev.forja.clase.Hooks.CARGA_PROFUNDA);
			dev.forja.clase.ClassEffects.noteOverheld(player, deep != null && counted >= chargeTicks(type) + Math.round(deep[0] * 20.0F));
			boolean free = Mana.exempt(player);
			// The class's price (Mago, Curandero with the lantern...) on everything below: the bar is read as
			// though it held that much more or less.
			float price = dev.forja.clase.ClassEffects.spellCostMultiplier(player);
			float mana = free ? Float.MAX_VALUE : Mana.value(player) / Math.max(0.01F, price);
			float[] blood = dev.forja.clase.ClassEffects.hook(player, dev.forja.clase.Hooks.SANGRE_POR_MANA);
			if (blood != null && !free && mana + 1.0E-4F < tapCost(stack, type)) {
				// Sangre por maná: what the bar lacks for a tap is paid in health, never down to the last point.
				float missing = (tapCost(stack, type) - Mana.value(player) / Math.max(0.01F, price)) * price;
				float health = missing / blood[1] * blood[0];
				if (player.getHealth() - health >= 1.0F) {
					player.setHealth(player.getHealth() - health);
					Mana.set(player, 0.0F);
					server.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY(1.0), player.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
					Mana.cast(player, 0.0F);
					cast(server, player, stack, type, null, 0.0F, 0.0F);
					player.getCooldowns().addCooldown(stack, Math.max(2, Math.round(cooldown(stack, type) * dev.forja.clase.ClassEffects.spellCooldownMultiplier(player))));
					stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
					player.swing(hand);
					return true;
				}
			}
			if (mana + 1.0E-4F < tapCost(stack, type)) {
				// Spent between the press and the release (a blade's Filo arcano, a blink): nothing leaves.
				Mana.deny(player);
				return false;
			}
			charge = affordableCharge(stack, type, charge, mana);
			float cost = manaCost(stack, type, charge) * price;
			// Descarga: a full charge pours the whole bar in, and every ten points past the price hit harder, up to
			// POUR_MOST: a bar deepened by four pieces of Reserva is not a spell four times over.
			float pour = 0.0F;
			float dump = Upgrade.dumpBonus(Upgrades.fraction(stack, Upgrade.DESCARGA));
			if (dump > 0.0F && charge >= 1.0F) {
				float poured = Mana.spendAll(player) - cost;
				pour = Math.min(POUR_MOST, Math.max(0.0F, poured) / 10.0F * dump);
			} else {
				Mana.trySpend(player, cost);
			}
			Mana.cast(player, cost);
			cast(server, player, stack, type, null, charge, pour);
			// The class's wait (Mago, Mente clara, Concentración) on top of the weapon's own.
			player.getCooldowns().addCooldown(stack, Math.max(2, Math.round(cooldown(stack, type) * dev.forja.clase.ClassEffects.spellCooldownMultiplier(player))));
			stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
		}
		player.swing(hand);
		return true;
	}

	private static void stopGathering(LivingEntity caster) {
		CHIMED.remove(caster);
		if (GATHERING.remove(caster) != null) {
			dev.forja.combat.CombatAnim.broadcast(caster, dev.forja.combat.CombatAnim.Kind.CHARGE, 0, 0.0F, 0.0F);
		}
	}

	/** Roughly where the hand holding the spell is: ahead of the eyes, a little down and to the side. */
	private static Vec3 hand(LivingEntity caster) {
		Vec3 look = caster.getViewVector(1.0F);
		Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		double sign = caster.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1.0 : -1.0;
		return caster.getEyePosition().add(look.scale(0.55)).add(side.scale(0.32 * sign)).add(0.0, -0.35, 0.0);
	}

	/**
	 * The spell itself, whoever reads it: a player's right click and a monster's rules (entity/ai/CasterGoal)
	 * both come here, so a staff in a skeleton's hand throws the bolt it would throw in yours, upgrades and
	 * all. What is only the player's — the item cooldown, the wear, the swing — stays in {@link #tryCast}.
	 *
	 * @param at where a tome's area opens; null for {@link #target}, five blocks ahead of the caster
	 */
	public static void cast(ServerLevel server, LivingEntity caster, ItemStack stack, ForgeType type, @org.jspecify.annotations.Nullable Vec3 at) {
		cast(server, caster, stack, type, at, 0.0F);
	}

	/** The same, gathered for a while first: {@code charge} from 0 (a tap) to 1 (full). */
	public static void cast(ServerLevel server, LivingEntity caster, ItemStack stack, ForgeType type, @org.jspecify.annotations.Nullable Vec3 at, float charge) {
		cast(server, caster, stack, type, at, charge, 0.0F);
	}

	/**
	 * The same with a bar poured into it (Descarga): {@code pour} is the extra share of damage the poured mana
	 * buys, and a poured spell comes out big, the way Sobrecarga's does.
	 */
	public static void cast(ServerLevel server, LivingEntity caster, ItemStack stack, ForgeType type, @org.jspecify.annotations.Nullable Vec3 at, float charge,
		float pour) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null || !casts(type)) {
			return;
		}
		if (type == ForgeType.FAROL) {
			Healing.lantern(server, caster, stack, charge);
			return;
		}
		ForgeMaterial core = core(parts);
		boolean big = overcharged(server, caster, stack, core.color);
		float power = (big ? 1.0F + Upgrade.overchargeBonus(Upgrades.fraction(stack, Upgrade.SOBRECARGA)) : 1.0F)
			* (1.0F + (CHARGE_BONUS + dev.forja.clase.ClassEffects.chargeBonusExtra(caster)) * Math.max(0.0F, Math.min(1.0F, charge)))
			* dev.forja.clase.ClassEffects.spellDamageMultiplier(caster)
			* dev.forja.clase.ClassEffects.spellShape(caster, charge);
		if (pour > 0.0F) {
			power *= 1.0F + pour;
			big = true;
			// The bar going out of the hand at once: a deep chime and a ring of the núcleo's colour.
			server.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
			Shockwave.burst(server, caster.position(), 1.6, 8, Upgrade.DESCARGA.color, 0.4F);
			server.sendParticles(new DustParticleOptions(Upgrade.DESCARGA.color, 1.4F), caster.getX(), caster.getY(1.0), caster.getZ(), 24, 0.5, 0.6, 0.5, 0.02);
		}
		float echo = Upgrade.echoShare(Upgrades.fraction(stack, Upgrade.RESONANCIA));
		if (type == ForgeType.BACULO) {
			volley(server, caster, core, stack, power, big, true);
			if (echo > 0.0F) {
				ECHOES.add(new Echo(server, caster.getUUID(), stack, server.getGameTime() + ECHO_BOLT_TICKS, power * echo, big, null));
			}
		} else {
			Rune rune = open(server, caster, core, stack, power, big, at != null ? at : target(server, caster));
			if (echo > 0.0F) {
				ECHOES.add(new Echo(server, caster.getUUID(), stack, server.getGameTime() + ECHO_AREA_TICKS, echo, big, rune));
			}
		}
	}

	/**
	 * Which way a spell leaves its caster: where a player is looking, and for a monster straight at what it
	 * is fighting, from where its bolt starts to the middle of the target — a mob's head turns towards its
	 * prey a few degrees a tick, and a bolt thrown along it would miss anyone who had just stepped aside.
	 */
	public static Vec3 aim(LivingEntity caster) {
		if (caster instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
			Vec3 from = new Vec3(caster.getX(), caster.getEyeY() - 0.15, caster.getZ());
			Vec3 to = mob.getTarget().getBoundingBox().getCenter().subtract(from);
			if (to.lengthSqr() > 1.0E-6) {
				return to.normalize();
			}
		}
		return caster.getLookAngle();
	}

	/** Whether a monster's spell leaves this one alone: the others of its kind, the way a pillager's arrow would not. */
	public static boolean spares(boolean spareMonsters, LivingEntity victim) {
		return spareMonsters && victim instanceof net.minecraft.world.entity.monster.Enemy;
	}

	/** Who cast a spell, if they are still about: a player first, as it always was, and otherwise any creature. */
	private static @org.jspecify.annotations.Nullable LivingEntity caster(ServerLevel level, UUID owner) {
		Player player = level.getPlayerByUUID(owner);
		if (player != null) {
			return player;
		}
		return level.getEntity(owner) instanceof LivingEntity living ? living : null;
	}

	/**
	 * What leaves the staff: the bolt, and with Prisma one more either side of it (two with Enjambre). Buscador is
	 * on every one of them. An echo is the middle bolt alone: it is the spell heard again, not cast again.
	 *
	 * <p>Andy, 2026-09-30, on Enjambre killing a warden in seconds: the spell's damage is <b>shared out</b> among the
	 * bolts, not handed whole to each. A side bolt weighs {@link Upgrade#prismShare} of the middle one, and all of
	 * them together are worth the one bolt the staff would have thrown alone: a fan covers more ground, it does not
	 * hit one foe harder. The side bolts are not blows of the staff either (no Tormenta, no Vampirismo on each), so
	 * five bolts are not five rolls of every upgrade. Every bolt goes through a mob's half second of invulnerability,
	 * which is what lets the shares of a fan that all find the same foe add up to the whole spell and no more.
	 */
	private static void volley(ServerLevel level, LivingEntity player, ForgeMaterial core, ItemStack staff, float power, boolean big, boolean fan) {
		float damage = boltDamage(core, player) * power;
		float seek = (float) Math.toRadians(Upgrade.seekDegrees(Upgrades.fraction(staff, Upgrade.BUSCADOR)));
		Vec3 look = aim(player);
		float prism = fan ? Upgrade.prismShare(Upgrades.fraction(staff, Upgrade.PRISMA)) : 0.0F;
		int pairs = prism > 0.0F ? (Synergy.ENJAMBRE.active(staff) ? 2 : 1) : 0;
		float whole = 1.0F + 2 * pairs * prism;
		MagicBolt bolt = new MagicBolt(level, player, look, core.color, damage / whole, staff, seek, big);
		// A monster's middle bolt minds the invulnerability as it always did (entity/ai/CasterGoal's old waits).
		level.addFreshEntity(player instanceof Player || pairs > 0 ? bolt.insistent() : bolt);
		if (pairs > 0) {
			for (int pair = 1; pair <= pairs; pair++) {
				for (int side = -1; side <= 1; side += 2) {
					Vec3 aside = look.yRot((float) Math.toRadians(side * pair * FAN_DEGREES));
					level.addFreshEntity(new MagicBolt(level, player, aside, core.color, damage * prism / whole, staff, seek, false).insistent().aside());
				}
			}
			if (pairs == 2) {
				Synergy.ENJAMBRE.spark(level, player.getEyePosition().add(look.scale(0.8)), 8);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, player.getSoundSource(), 1.2F, big ? 0.8F : fan ? 1.4F : 1.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, player.getSoundSource(), big ? 0.6F : 0.35F, big ? 1.0F : 1.6F);
	}

	/** Where the tome's area lands: five blocks along the way the reader faces, nearer if a wall is in the way, on the floor there. */
	public static Vec3 target(Level level, LivingEntity player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = aim(player);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
		Vec3 end = eye.add(flat.scale(TOME_DISTANCE));
		HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 spot = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation().subtract(flat.scale(0.6));
		float floor = Shockwave.floorAt(level, spot.x, player.getY(), spot.z);
		return new Vec3(spot.x, player.getY() + floor, spot.z);
	}

	private static Rune open(ServerLevel level, LivingEntity player, ForgeMaterial core, ItemStack tome, float power, boolean big, Vec3 at) {
		float damage = areaDamage(core, player) * power;
		double reach = RUNE_REACH + (big ? OVERCHARGE_REACH : 0.0);
		int ticks = runeTicks(tome);
		// Andy, 2026-09-30: runes laid one on another bit as many times over. A player's new rune puts out the
		// last one they laid (quietly: that is not a Colapso), so one reader is one rune on the floor.
		if (player instanceof Player) {
			for (int index = RUNES.size() - 1; index >= 0; index--) {
				Rune old = RUNES.get(index);
				if (old.owner.equals(player.getUUID())) {
					RUNES.remove(index);
					old.mark.discard();
				}
			}
		}
		// The area, in the colour of the circle on the book, and the rune it leaves lying there.
		Shockwave.burst(level, at, reach, 10, core.color, big ? 0.8F : 0.45F);
		Shockwave mark = Shockwave.rune(level, at, reach, ticks, core.color);
		Rune rune = new Rune(level, at, reach, ticks, damage, core.color, player.getUUID(), tome, mark);
		rune.spareMonsters = player instanceof net.minecraft.world.entity.monster.Enemy;
		rune.biteShare = player instanceof Player ? BITE_SHARE : MONSTER_BITE_SHARE;
		RUNES.add(rune);
		strike(rune, damage, player, true);
		level.sendParticles(new DustParticleOptions(core.color, 1.6F), at.x, at.y + 0.4, at.z, big ? 70 : 40, reach * 0.5, 0.3, reach * 0.5, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENCHANTMENT_TABLE_USE, player.getSoundSource(), 1.2F, big ? 0.55F : 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, player.getSoundSource(), 1.0F, 0.6F);
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
	private static void strike(Rune rune, float damage, @org.jspecify.annotations.Nullable LivingEntity caster, boolean blow) {
		ServerLevel level = rune.level;
		Vec3 at = rune.at;
		double reach = rune.reach;
		// A Curandero's tome mends its allies a tenth of the whole bite (magic/Healing), and still bites everything
		// else, at a third: the class's factor goes on where the blow lands (clase/ClassDamage).
		boolean heals = Healing.converts(caster);
		if (heals) {
			Healing.runeHeal(level, at, reach, caster, damage, rune.colour);
		}
		AABB box = new AABB(at, at).inflate(reach, 2.0, reach);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box,
			other -> other.isAlive() && other != caster && !spares(rune.spareMonsters, other))) {
			double dx = victim.getX() - at.x;
			double dz = victim.getZ() - at.z;
			if (dx * dx + dz * dz > reach * reach || (caster != null && victim.isAlliedTo(caster))
				|| (heals && Healing.ally(caster, victim))) {
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
	private static void shelter(Rune rune, @org.jspecify.annotations.Nullable LivingEntity caster) {
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

	/** How many runes this reader has lying about. */
	public static int runesOf(UUID owner) {
		int count = 0;
		for (Rune rune : RUNES) {
			count += rune.owner.equals(owner) ? 1 : 0;
		}
		return count;
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
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// A charge dropped without a release (another slot, a hit that stops the use, death) drops its pose too.
			for (var it = GATHERING.entrySet().iterator(); it.hasNext();) {
				var entry = it.next();
				LivingEntity caster = entry.getKey();
				if (!caster.isAlive() || !caster.isUsingItem() || !(caster.getUseItem().getItem() instanceof dev.forja.item.ForgedItems.Forged forged)
					|| forged.forgeType() != entry.getValue()) {
					it.remove();
					CHIMED.remove(caster);
					dev.forja.combat.CombatAnim.broadcast(caster, dev.forja.combat.CombatAnim.Kind.CHARGE, 0, 0.0F, 0.0F);
				}
			}
		});
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
				LivingEntity caster = caster(level, rune.owner);
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
		LivingEntity caster = caster(level, echo.owner());
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
