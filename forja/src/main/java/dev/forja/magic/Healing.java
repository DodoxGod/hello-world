package dev.forja.magic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.forja.clase.ClassEffects;
import dev.forja.clase.ClassEvents;
import dev.forja.clase.PlayerClass;
import dev.forja.clase.Talent;
import dev.forja.material.ForgeMaterial;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Magic that mends instead of hurting (docs/CLASES.md): the healing lantern, anyone's, and the Curandero's
 * rule for the staff and the tome.
 *
 * <p>The <b>lantern</b> (ForgeType.FAROL) is Andy's A3 drawing, the crook with a cage hanging from it: a tap
 * sends a beam that mends the first ally along it, a charge lets out a ring round the reader that mends every
 * ally inside, and the reader at half. The núcleo is the colour and the strength, as on the staff.
 *
 * <p>Allies are players, the reader's own tamed animals, and whoever is on the reader's team. Never a
 * monster, and never someone else's animals.
 */
public final class Healing {
	/**
	 * Andy: "cambia el daño de las armas mágicas por curación (sólo cura 1/10 parte del daño)". A Curandero's
	 * bolt and rune mend an ally by a tenth of what they would have hurt a foe for — no more, whatever the
	 * class's healing talents say — and do not hurt monsters at all.
	 */
	public static final float MAGIC_HEAL_SHARE = 0.1F;

	public static final int COOLDOWN = 30;
	public static final int CHARGE_TICKS = 25;
	public static final double BEAM_REACH = 16.0;
	/** A charge under this share leaves as the beam, not the ring. */
	public static final float TAP_SHARE = 1.0F / 3.0F;
	public static final double RING_REACH = 4.0;
	public static final double RING_GROWTH = 2.0;
	/** What the reader gets of their own ring. */
	public static final float SELF_SHARE = 0.5F;
	public static final float CHARGE_BONUS = 0.5F;

	/** Curandero: health healed on others and not yet paid out as class experience (1 per 2). */
	private static final Map<UUID, Float> OWED = new HashMap<>();
	public static final float HEALTH_PER_XP = 2.0F;

	private Healing() {
	}

	/** What the beam mends: 2 and three quarters of the núcleo's bite. */
	public static float beamHeal(ForgeMaterial core) {
		return 2.0F + 0.75F * core.attackDamageBonus;
	}

	/** What the ring mends at no charge; a full charge is worth {@link #CHARGE_BONUS} more. */
	public static float ringHeal(ForgeMaterial core) {
		return 2.5F + 0.75F * core.attackDamageBonus;
	}

	public static double ringReach(float charge) {
		return RING_REACH + RING_GROWTH * charge;
	}

	/** Whether this caster's staff and tome heal instead of hurting: a Curandero player. */
	public static boolean converts(@Nullable Object caster) {
		return caster instanceof Player player && ClassEffects.is(player, PlayerClass.CURANDERO);
	}

	/** Whether {@code other} is someone {@code healer} mends: a player, their own animal, their team; never a monster. */
	public static boolean ally(LivingEntity healer, LivingEntity other) {
		if (!other.isAlive() || other instanceof Enemy) {
			return false;
		}
		if (other instanceof Player player) {
			return !player.isSpectator();
		}
		if (other instanceof OwnableEntity owned && owned.getOwnerReference() != null
			&& owned.getOwnerReference().getUUID().equals(healer.getUUID())) {
			return true;
		}
		return other.isAlliedTo(healer);
	}

	/**
	 * Mends {@code target} by {@code amount} as {@code healer}'s doing: the heal itself, and what a Curandero's
	 * talents hang on it — Renuevo, Bendición, Purificar, Vínculo — and the class experience it is worth.
	 *
	 * @return what it actually mended
	 */
	public static float mend(ServerLevel level, @Nullable LivingEntity healer, LivingEntity target, float amount) {
		if (amount <= 0.0F || !target.isAlive()) {
			return 0.0F;
		}
		boolean low = target.getHealth() < target.getMaxHealth() * Talent.CURANDERO_BENDICION.numbers[0];
		float before = target.getHealth();
		target.heal(amount);
		float mended = target.getHealth() - before;
		if (healer instanceof Player player) {
			if (ClassEffects.has(player, Talent.CURANDERO_RENUEVO)) {
				target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Math.round(Talent.CURANDERO_RENUEVO.numbers[0] * 20.0F), 0), player);
			}
			if (low && ClassEffects.has(player, Talent.CURANDERO_BENDICION)) {
				target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, Math.round(Talent.CURANDERO_BENDICION.numbers[1] * 20.0F), 0), player);
			}
			if (ClassEffects.has(player, Talent.CURANDERO_PURIFICAR)) {
				target.removeEffect(MobEffects.POISON);
				target.removeEffect(MobEffects.WITHER);
				target.removeEffect(MobEffects.WEAKNESS);
				target.removeEffect(MobEffects.SLOWNESS);
			}
			if (target != player && mended > 0.0F) {
				if (ClassEffects.has(player, Talent.CURANDERO_VINCULO)) {
					player.heal(mended * Talent.CURANDERO_VINCULO.numbers[0]);
				}
				if (ClassEffects.is(player, PlayerClass.CURANDERO)) {
					float owed = OWED.getOrDefault(player.getUUID(), 0.0F) + mended;
					int xp = (int) (owed / HEALTH_PER_XP);
					OWED.put(player.getUUID(), owed - xp * HEALTH_PER_XP);
					if (xp > 0) {
						ClassEvents.healed(player, xp);
					}
				}
			}
		}
		level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(),
			Math.max(1, Math.min(4, Math.round(amount / 2.0F))), 0.3, 0.2, 0.3, 0.0);
		return mended;
	}

	// ------------------------------------------------------------------ the lantern

	/**
	 * The lantern's spell, charge 0 (a tap) to 1 (full). A player pays for it first ({@link ManaHooks}); a
	 * refusal is a fizzle and no spell.
	 */
	public static void lantern(ServerLevel level, LivingEntity caster, ItemStack stack, float charge) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		if (parts == null) {
			return;
		}
		if (caster instanceof Player player && !ManaHooks.spend(player, ManaHooks.cost(player, dev.forja.forge.ForgeType.FAROL, charge))) {
			level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
			return;
		}
		ForgeMaterial core = Spellcasting.core(parts);
		float power = ClassEffects.healingMultiplier(caster);
		if (charge < TAP_SHARE) {
			beam(level, caster, core, beamHeal(core) * power);
		} else {
			ring(level, caster, core, charge, ringHeal(core) * power * (1.0F + CHARGE_BONUS * Math.min(1.0F, charge)));
		}
	}

	/** The first ally along the reader's look, within reach and not behind a wall. */
	public static @Nullable LivingEntity allyInSight(ServerLevel level, LivingEntity caster, double reach) {
		Vec3 from = caster.getEyePosition();
		Vec3 look = Spellcasting.aim(caster);
		Vec3 to = from.add(look.scale(reach));
		HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		if (wall.getType() != HitResult.Type.MISS) {
			to = wall.getLocation();
		}
		LivingEntity best = null;
		double nearest = Double.MAX_VALUE;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.0),
			e -> e != caster && ally(caster, e))) {
			var hit = other.getBoundingBox().inflate(0.4).clip(from, to);
			if (hit.isPresent()) {
				double distance = from.distanceToSqr(hit.get());
				if (distance < nearest) {
					nearest = distance;
					best = other;
				}
			}
		}
		return best;
	}

	/** Bálsamo: a line of light to the first ally in the way, mending them. Never the reader. */
	private static void beam(ServerLevel level, LivingEntity caster, ForgeMaterial core, float amount) {
		LivingEntity target = allyInSight(level, caster, BEAM_REACH);
		Vec3 from = caster.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 to = target != null ? target.getBoundingBox().getCenter() : caster.getEyePosition().add(Spellcasting.aim(caster).scale(BEAM_REACH * 0.5));
		int steps = (int) Math.max(4, from.distanceTo(to) * 3.0);
		for (int i = 1; i <= steps; i++) {
			Vec3 at = from.lerp(to, i / (double) steps);
			level.sendParticles(new DustParticleOptions(core.color, 0.9F), at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, caster.getSoundSource(), 1.0F, 1.9F);
		if (target != null) {
			level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 6, 0.2, 0.3, 0.2, 0.02);
			mend(level, caster, target, amount);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5F, 1.8F);
		}
	}

	/** Pulso: a ring round the reader that mends every ally inside, and the reader at half. */
	private static void ring(ServerLevel level, LivingEntity caster, ForgeMaterial core, float charge, float amount) {
		double reach = ringReach(charge);
		dev.forja.entity.Shockwave.burst(level, caster.position(), reach, 12, core.color, 0.35F);
		for (int step = 0; step < 32; step++) {
			double angle = step * Math.PI / 16.0;
			level.sendParticles(new DustParticleOptions(core.color, 1.4F), caster.getX() + Math.cos(angle) * reach, caster.getY() + 0.2,
				caster.getZ() + Math.sin(angle) * reach, 1, 0.0, 0.05, 0.0, 0.0);
		}
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BEACON_ACTIVATE, caster.getSoundSource(), 0.8F, 1.6F);
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(reach, 2.0, reach),
			e -> e != caster && ally(caster, e) && e.distanceToSqr(caster) <= reach * reach)) {
			mend(level, caster, other, amount);
		}
		mend(level, caster, caster, amount * SELF_SHARE);
	}

	// ------------------------------------------------------------------ the Curandero's staff and tome

	/** The first ally in this box that is not the caster, for a Curandero's bolt. */
	public static @Nullable LivingEntity allyIn(ServerLevel level, AABB box, LivingEntity caster) {
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && ally(caster, e))) {
			return other;
		}
		return null;
	}

	/** A Curandero's spell lands on an ally: a tenth of its damage, as health. */
	public static void spellHeal(ServerLevel level, LivingEntity caster, LivingEntity ally, float damage) {
		mend(level, caster, ally, damage * MAGIC_HEAL_SHARE);
	}

	/** A Curandero's area or rune: every ally on it but the reader is mended a tenth of what it would bite. */
	public static void runeHeal(ServerLevel level, Vec3 at, double reach, LivingEntity caster, float damage, int colour) {
		AABB box = new AABB(at, at).inflate(reach, 2.0, reach);
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && ally(caster, e))) {
			double dx = other.getX() - at.x;
			double dz = other.getZ() - at.z;
			if (dx * dx + dz * dz <= reach * reach) {
				spellHeal(level, caster, other, damage);
				level.sendParticles(new DustParticleOptions(colour, 0.8F), other.getX(), other.getY() + 0.5, other.getZ(), 3, 0.2, 0.3, 0.2, 0.0);
			}
		}
	}
}
