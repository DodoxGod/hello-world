package dev.forja.combat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import dev.forja.registry.ModEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What the tip of a forged arrow does (Andy, 2026-09-30: "las flechas son todas iguales"). Every tip material
 * flies and bites its own way, read from the material itself rather than listed:
 *
 * <ul>
 *   <li><b>density</b> (combat/Weight.density): a heavy tip hits harder, leaves the string a little slower,
 *       drops sooner and shoves further; a light one flies flatter and faster and bites less;</li>
 *   <li><b>hardness</b> (MaterialCombat.rigidity): a hard tip goes through more armour;</li>
 *   <li>and the material's <b>trait</b>, or for gold and amethyst their own nature, gives the special on-hit
 *       effect ({@link Special}): fire, bleeding, resin, a blink, a spark, a seeking tip...</li>
 * </ul>
 *
 * <p>The numbers are held against vanilla's arrows: an iron tip is the plain forged arrow it always was, the
 * heaviest tip hits about a tenth harder and the lightest a tenth softer, and no special does more than a tipped
 * arrow of vanilla's would (a few seconds of an effect at level I or II, never the eleven seconds of Slowness IV).
 * entity/ForgedArrow does the flying and the hitting; the tooltip reads {@link #describe}.
 */
public final class ArrowTips {
	/** Gravity of a vanilla arrow, a tick. */
	public static final double VANILLA_GRAVITY = 0.05;
	/** A tip at least this dense (iron is 1) is heavy: it shoves what it hits. */
	public static final float HEAVY = 1.3F;
	/** How much further a heavy tip shoves, in blocks of knockback. */
	public static final double HEAVY_SHOVE = 0.35;
	/** The most armour a hard tip goes through on top of an arrow's own share (diamond and the hardest alloys). */
	public static final float HARD_PENETRATION = 0.30F;
	/** Hollow plate: its tip finds the gap in any armour. */
	public static final float HOLLOW_PENETRATION = 0.25F;

	/** Seconds of each special's effect, and its sizes. */
	public static final int FIRE_SECONDS = 4;
	public static final int EMBER_SECONDS = 3;
	/** Brasa: what it adds, in base damage (before the arrow's speed multiplies it), to a target already burning. */
	public static final float EMBER_BONUS = 0.8F;
	public static final int BLEED_TICKS = 100;
	public static final int RESIN_TICKS = 60;
	public static final int WEAKNESS_TICKS = 100;
	public static final int GLOW_TICKS = 160;
	/** Chispa: what the spark does to the nearest other foe, and how far it looks. */
	public static final float SPARK_DAMAGE = 3.0F;
	public static final double SPARK_REACH = 5.0;
	/** Salto: how far the target blinks. */
	public static final double BLINK_REACH = 8.0;
	/** Estrella: its share of an arrow's gravity. */
	public static final float STAR_GRAVITY = 0.25F;
	/** Vidrio: how much faster it leaves the string. */
	public static final float GLASS_SPEED = 1.08F;
	/** Buscadora: how far it turns a tick, in degrees, and how far it looks. */
	public static final float SEEK_DEGREES = 6.0F;
	public static final double SEEK_RANGE = 12.0;
	/** Sol and Luna: base damage they add in the light or the dark they want. */
	public static final float SUN_BONUS = 0.8F;
	public static final float MOON_BONUS = 0.9F;
	/** Viva: health it gives back to whoever loosed it. */
	public static final float LIVING_HEAL = 1.0F;
	/** Fortuna: the chance that it leaves the string critical. */
	public static final float LUCK_CRIT = 0.25F;
	/** Conductora: base damage it adds to a wet target. */
	public static final float WET_BONUS = 0.6F;
	/** Hechizo: mana it gives back to whoever loosed it. */
	public static final float SPELL_MANA = 3.0F;

	/** The one thing a tip does beyond its weight and hardness. */
	public enum Special {
		NONE,
		/** Escoria (Ígneo): sets the target alight. */
		FUEGO,
		/** Cinerio (Ascua): alight, and harder on what is already burning. */
		BRASA,
		/** Cuarzo, damasco (Afilado): a bleeding wound. */
		SANGRADO,
		/** Resina (Pegajoso): gums the target up, Slowness II. */
		RESINA,
		/** Prismarina (Acuático): flies underwater as in air. */
		MAREA,
		/** Púrpur (Del End): the target blinks a few blocks away. */
		SALTO,
		/** Obsidiana llorona, corazón (Llanto): Weakness. */
		LLANTO,
		/** Eco (Resonante): goes through one more. */
		ECO,
		/** Hierro estelar, acero estelar (Estelar): barely falls. */
		ESTRELLA,
		/** Placa hueca (Vacío): finds the gap in armour. */
		HUECA,
		/** Voltaico (Cargado): a spark jumps to the next foe. */
		CHISPA,
		/** Almacero (Animado): turns towards what hunts you. */
		BUSCADORA,
		/** Vidriacero (Diáfano): faster and flatter, and shatters on what it hits. */
		VIDRIO,
		/** Solacero (Solar): harder under the sun, and burns the undead there. */
		SOL,
		/** Lunacero (Nocturno): harder in the dark. */
		LUNA,
		/** Acero vivo (Vivo): mends whoever loosed it. */
		VIVA,
		/** Esmeralda, electro (Afortunado): sometimes leaves critical. */
		FORTUNA,
		/** Oro: marks the target, which glows through walls. */
		MARCA,
		/** Amatista: gives back a little mana. */
		HECHIZO,
		/** Cobre: on something wet (in water or rain) the hit carries a shock. */
		CONDUCTORA,
		/** Fatuo (Espectral): the blue fire, which burns what does not burn. */
		FATUA;

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public Component displayName() {
			return Component.translatable("flecha.forja.especial." + this.id());
		}

		public Component description() {
			return switch (this) {
				case FUEGO -> Component.translatable("flecha.forja.especial.fuego.desc", FIRE_SECONDS);
				case BRASA -> Component.translatable("flecha.forja.especial.brasa.desc", EMBER_SECONDS);
				case FATUA -> Component.translatable("flecha.forja.especial.fatua.desc", dev.forja.upgrade.TraitEffects.SOUL_FLAME_TICKS / 20);
				case SANGRADO -> Component.translatable("flecha.forja.especial.sangrado.desc", BLEED_TICKS / 20);
				case RESINA -> Component.translatable("flecha.forja.especial.resina.desc", RESIN_TICKS / 20);
				case LLANTO -> Component.translatable("flecha.forja.especial.llanto.desc", WEAKNESS_TICKS / 20);
				case MARCA -> Component.translatable("flecha.forja.especial.marca.desc", GLOW_TICKS / 20);
				case SALTO -> Component.translatable("flecha.forja.especial.salto.desc", Math.round(BLINK_REACH));
				case CHISPA -> Component.translatable("flecha.forja.especial.chispa.desc", Math.round(SPARK_DAMAGE), Math.round(SPARK_REACH));
				case FORTUNA -> Component.translatable("flecha.forja.especial.fortuna.desc", Math.round(LUCK_CRIT * 100));
				case HECHIZO -> Component.translatable("flecha.forja.especial.hechizo.desc", Math.round(SPELL_MANA));
				case VIVA -> Component.translatable("flecha.forja.especial.viva.desc", Math.round(LIVING_HEAL));
				case CONDUCTORA -> Component.translatable("flecha.forja.especial.conductora.desc", String.format(Locale.ROOT, "%.1f", WET_BONUS));
				default -> Component.translatable("flecha.forja.especial." + this.id() + ".desc");
			};
		}
	}

	/**
	 * A tip, worked out: its density against iron, what it multiplies the arrow's damage and speed by, its
	 * gravity a tick, the armour it goes through on top of an arrow's own, extra targets it pierces, whether it
	 * shoves, and its special.
	 */
	public record Tip(ForgeMaterial material, float density, float damage, float speed, double gravity, float penetration, int pierce, boolean heavy,
		Special special) {
	}

	private static final Map<ForgeMaterial, Tip> TIPS = new EnumMap<>(ForgeMaterial.class);

	private ArrowTips() {
	}

	/** The special a tip of this material has: from its trait, and for gold and amethyst from what they are. */
	public static Special special(ForgeMaterial material) {
		if (material == ForgeMaterial.ORO) {
			return Special.MARCA;
		}
		if (material == ForgeMaterial.AMATISTA) {
			return Special.HECHIZO;
		}
		if (material == ForgeMaterial.COBRE) {
			return Special.CONDUCTORA;
		}
		return switch (material.trait) {
			case IGNEO -> Special.FUEGO;
			case ASCUA -> Special.BRASA;
			case AFILADO -> Special.SANGRADO;
			case PEGAJOSO -> Special.RESINA;
			case ACUATICO -> Special.MAREA;
			case DEL_END -> Special.SALTO;
			case LLANTO -> Special.LLANTO;
			case RESONANTE -> Special.ECO;
			case ESTELAR -> Special.ESTRELLA;
			case VACIO -> Special.HUECA;
			case CARGADO -> Special.CHISPA;
			case ANIMADO -> Special.BUSCADORA;
			case DIAFANO -> Special.VIDRIO;
			case SOLAR -> Special.SOL;
			case NOCTURNO -> Special.LUNA;
			case VIVO -> Special.VIVA;
			case AFORTUNADO -> Special.FORTUNA;
			// Orichalcum carries magic as amethyst does: its tip hands the archer mana.
			case ASTRAL -> Special.HECHIZO;
			case ESPECTRAL -> Special.FATUA;
			default -> Special.NONE;
		};
	}

	public static synchronized Tip of(ForgeMaterial material) {
		return TIPS.computeIfAbsent(material, ArrowTips::work);
	}

	private static Tip work(ForgeMaterial material) {
		float density = Math.max(0.5F, Math.min(1.8F, Weight.density(material)));
		Special special = special(material);
		float damage = 0.85F + 0.15F * density;
		float speed = 1.1F - 0.1F * density;
		double gravity = VANILLA_GRAVITY * density;
		float penetration = HARD_PENETRATION * (float) Math.max(0.0, Math.min(1.0, MaterialCombat.rigidity(material)));
		int pierce = 0;
		switch (special) {
			case ESTRELLA -> gravity *= STAR_GRAVITY;
			case VIDRIO -> speed *= GLASS_SPEED;
			case HUECA -> penetration += HOLLOW_PENETRATION;
			case ECO -> pierce = 1;
			default -> {
			}
		}
		return new Tip(material, density, damage, speed, gravity, penetration, pierce, density >= HEAVY, special);
	}

	/**
	 * Brasa, Sol and Luna: base damage the tip adds (before the arrow's speed multiplies it, as vanilla does with
	 * all of an arrow's damage) when the fire, the daylight or the dark it wants is there.
	 */
	public static float conditionalBonus(Special special, boolean burning, boolean sun, boolean dark) {
		return conditionalBonus(special, burning, sun, dark, false);
	}

	/** The same, with Conductora's water. */
	public static float conditionalBonus(Special special, boolean burning, boolean sun, boolean dark, boolean wet) {
		return switch (special) {
			case CONDUCTORA -> wet ? WET_BONUS : 0.0F;
			case BRASA -> burning ? EMBER_BONUS : 0.0F;
			case SOL -> sun ? SUN_BONUS : 0.0F;
			case LUNA -> dark ? MOON_BONUS : 0.0F;
			default -> 0.0F;
		};
	}

	/** What the tip does to what it has just hurt (entity/ForgedArrow.doPostHurtEffects). */
	public static void onHit(ServerLevel level, AbstractArrow arrow, @Nullable LivingEntity shooter, LivingEntity target, Special special) {
		switch (special) {
			case FUEGO -> target.igniteForSeconds(FIRE_SECONDS);
			case BRASA -> target.igniteForSeconds(EMBER_SECONDS);
			case SANGRADO -> {
				MobEffectInstance wound = target.getEffect(ModEffects.SANGRADO);
				int stacks = wound == null ? 0 : Math.min(1, wound.getAmplifier() + 1);
				target.addEffect(new MobEffectInstance(ModEffects.SANGRADO, BLEED_TICKS, stacks), shooter);
				level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.7), target.getZ(), 4, 0.3, 0.3, 0.3, 0.1);
			}
			case RESINA -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RESIN_TICKS, 1), shooter);
			case LLANTO -> {
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0), shooter);
				level.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, target.getX(), target.getY(0.9), target.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
			}
			case MARCA -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0), shooter);
			case CONDUCTORA -> {
				if (target.isInWaterOrRain()) {
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
				}
			}
			case SALTO -> blink(level, target);
			case FATUA -> dev.forja.upgrade.TraitEffects.soulFlame(level, target, shooter != null ? shooter : target,
				dev.forja.upgrade.TraitEffects.SOUL_FLAME_TICKS);
			case CHISPA -> spark(level, arrow, shooter, target);
			case SOL -> {
				if (target.isInvertedHealAndHarm() && dev.forja.upgrade.TraitEffects.inSun(level, target)) {
					target.igniteForSeconds(4.0F);
					level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(1.0), target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
				}
			}
			case VIVA -> {
				if (shooter != null && shooter.isAlive()) {
					shooter.heal(LIVING_HEAL);
					level.sendParticles(ParticleTypes.HEART, shooter.getX(), shooter.getY(1.1), shooter.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
				}
			}
			case HECHIZO -> {
				if (shooter instanceof Player player && !dev.forja.magic.Mana.exempt(player)) {
					dev.forja.magic.Mana.give(player, SPELL_MANA);
					level.sendParticles(new DustParticleOptions(ForgeMaterial.AMATISTA.color, 0.9F), target.getX(), target.getY(0.8), target.getZ(),
						6, 0.3, 0.3, 0.3, 0.0);
				}
			}
			default -> {
			}
		}
	}

	/** Salto: the target blinks somewhere within {@link #BLINK_REACH}, like a chorus fruit. Never a boss. */
	private static void blink(ServerLevel level, LivingEntity target) {
		if (!target.isAlive() || dev.forja.difficulty.Bosses.isBoss(target) || target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
			return;
		}
		var random = target.getRandom();
		double fromX = target.getX();
		double fromY = target.getY();
		double fromZ = target.getZ();
		for (int attempt = 0; attempt < 16; attempt++) {
			double x = fromX + (random.nextDouble() - 0.5) * 2.0 * BLINK_REACH;
			double y = net.minecraft.util.Mth.clamp(fromY + (random.nextInt(8) - 4), level.getMinY(), level.getMaxY());
			double z = fromZ + (random.nextDouble() - 0.5) * 2.0 * BLINK_REACH;
			if ((x - fromX) * (x - fromX) + (z - fromZ) * (z - fromZ) < 4.0) {
				continue;
			}
			if (target.randomTeleport(x, y, z, true)) {
				level.playSound(null, fromX, fromY, fromZ, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
				level.sendParticles(ParticleTypes.PORTAL, fromX, fromY + 1.0, fromZ, 20, 0.3, 0.6, 0.3, 0.2);
				return;
			}
		}
	}

	/** Chispa: a spark from the struck foe to the nearest other one, which it hurts a little. */
	private static void spark(ServerLevel level, AbstractArrow arrow, @Nullable LivingEntity shooter, LivingEntity target) {
		LivingEntity next = null;
		double nearest = SPARK_REACH * SPARK_REACH;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(SPARK_REACH),
			e -> e.isAlive() && e != target && e != shooter && e instanceof Enemy)) {
			double distance = other.distanceToSqr(target);
			if (distance < nearest) {
				nearest = distance;
				next = other;
			}
		}
		if (next == null) {
			return;
		}
		net.minecraft.world.phys.Vec3 from = target.getBoundingBox().getCenter();
		net.minecraft.world.phys.Vec3 to = next.getBoundingBox().getCenter();
		for (int step = 0; step <= 8; step++) {
			net.minecraft.world.phys.Vec3 at = from.lerp(to, step / 8.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3F, 1.8F);
		dev.forja.upgrade.CombatUpgrades.sideDamage(level, next, level.damageSources().indirectMagic(arrow, shooter), SPARK_DAMAGE);
	}

	/** The tip of a forged arrow, or null for anything else. */
	public static @Nullable ForgeMaterial tipOf(ItemStack arrow) {
		ForgedParts parts = arrow.get(ModComponents.PARTS);
		if (parts == null || parts.type() != dev.forja.forge.ForgeType.FLECHA) {
			return null;
		}
		int slot = parts.type().slots.indexOf(PartType.PUNTA_FLECHA);
		return slot < 0 ? null : parts.material(slot);
	}

	/** What the tip does, for the tooltip: its weight and how it flies, its hardness, and its special. */
	public static List<Component> describe(ForgeMaterial material) {
		Tip tip = of(material);
		List<Component> lines = new ArrayList<>();
		int damage = Math.round((tip.damage() - 1.0F) * 100.0F);
		int drop = Math.round((float) (tip.gravity() / VANILLA_GRAVITY * 100.0));
		String weight = tip.heavy() ? "pesada" : tip.density() <= 0.8F ? "ligera" : "media";
		lines.add(Component.translatable("tooltip.forja.punta." + weight, material.displayName(), signed(damage), drop));
		int penetration = Math.round(tip.penetration() * 100.0F);
		if (penetration >= 3) {
			lines.add(Component.translatable("tooltip.forja.punta.dura", penetration));
		}
		if (tip.special() != Special.NONE) {
			lines.add(Component.translatable("tooltip.forja.punta.especial", tip.special().displayName(), tip.special().description()));
		}
		return lines;
	}

	private static String signed(int percent) {
		return (percent > 0 ? "+" : percent < 0 ? "−" : "±") + Math.abs(percent);
	}
}
