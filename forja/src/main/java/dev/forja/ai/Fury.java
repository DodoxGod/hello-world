package dev.forja.ai;

import java.util.ArrayList;
import java.util.List;

import dev.forja.Forja;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Fury (docs/red_mob_v4_diseno.md §4.3, output 51; Andy's decision 3, 2026-09-29): +25 % damage and +20 % speed for
 * 10 s, then 5 s spent (−20 % speed and no turn). Only a tenth of each group can, at least one in groups of five or
 * more, and they are chosen when the group spawns ({@link #choose}: the tag {@link #TAG}); the rest have it masked.
 *
 * <p>It is there (furia_disponible) for a mob that can, is not a coward, has not used it this fight, and whose group
 * took a blow to its morale in the last 200 ticks: its captain died, or half the group is down (Captain). In a fury it
 * cannot raise its shield, dodge, feint, run, regroup, lie in wait or pearl away (MobAi.maskV4). It shows: red dust and a
 * roar when it starts, red dust while it lasts.
 */
public final class Fury {
	public static final String TAG = "forja_furia";
	public static final int TICKS = 200;
	public static final int SPENT_TICKS = 100;
	public static final double DAMAGE = 0.25;
	public static final double SPEED = 0.20;
	public static final double SPENT_SPEED = -0.20;
	/** A tenth of each group, and one at least in a group of this many. */
	public static final double SHARE = 0.10;
	public static final int ONE_AT_LEAST_FROM = 5;
	private static final Identifier FAST = Forja.id("furia");
	private static final Identifier SLOW = Forja.id("furia_agotado");
	private static final DustParticleOptions RED = new DustParticleOptions(0xD01010, 1.2F);

	private Fury() {
	}

	/** Chooses who of a group just spawned can go into a fury: a tenth, one at least from five. */
	public static void choose(List<Mob> group, RandomSource random) {
		int n = group.size();
		int k = (int) Math.floor(n * SHARE);
		if (n >= ONE_AT_LEAST_FROM) {
			k = Math.max(1, k);
		}
		List<Mob> left = new ArrayList<>(group);
		for (int i = 0; i < k && !left.isEmpty(); i++) {
			left.remove(random.nextInt(left.size())).addTag(TAG);
		}
	}

	/** Whether it is one of those who can. */
	public static boolean able(Mob mob) {
		return mob.entityTags().contains(TAG);
	}

	/** furia_disponible: can, is no coward, has not used it this fight, and its group took a blow to its morale lately. */
	public static boolean available(MobMind mind, long now) {
		Mob mob = mind.mob;
		return able(mob) && !mind.furyUsed && mind.target != null && Personality.trait(mob) != Personality.Trait.COBARDE
			&& Captain.moraleBlow(mind.target, now) && now >= mind.exhaustedUntil;
	}

	/** Output 51's mask: available, not staggered and not in the middle of a warning. */
	public static boolean allowed(MobMind mind, long now) {
		return available(mind, now) && !dev.forja.combat.Posture.isStaggered(mind.mob, now) && mind.windup == 0
			&& (mind.specials == null || mind.specials.warningProgress() < 0.0);
	}

	/** Each tick (MobAi): starts one the decision asked for, keeps the speed right, and shows it. */
	public static void tick(MobMind mind, long now) {
		Mob mob = mind.mob;
		if (mind.decision.fury() && mind.decidedAt == now && allowed(mind, now)) {
			mind.furyUsed = true;
			mind.furyUntil = now + TICKS;
			mind.exhaustedUntil = now + TICKS + SPENT_TICKS;
			mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.3F);
			show(mob, 20);
		}
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean fury = mind.furyActive(now);
		boolean spent = !fury && now < mind.exhaustedUntil;
		set(speed, FAST, fury ? SPEED : 0.0);
		set(speed, SLOW, spent ? SPENT_SPEED : 0.0);
		if (fury && now % 5 == 0) {
			show(mob, 3);
		}
	}

	private static void set(AttributeInstance speed, Identifier id, double amount) {
		AttributeModifier had = speed.getModifier(id);
		if (amount == 0.0) {
			if (had != null) {
				speed.removeModifier(id);
			}
		} else if (had == null) {
			speed.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void show(Mob mob, int count) {
		if (mob.level() instanceof ServerLevel level) {
			level.sendParticles(RED, mob.getX(), mob.getY(0.6), mob.getZ(), count, 0.3, 0.4, 0.3, 0.02);
			if (count > 5) {
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 3, 0.3, 0.1, 0.3, 0.0);
			}
		}
	}

	/** The damage multiplier of a mob in a fury (Personality.damage). */
	public static double damage(Mob mob) {
		MobMind mind = MobAi.mind(mob);
		return mind != null && mind.furyActive(mob.level().getGameTime()) ? 1.0 + DAMAGE : 1.0;
	}

	/** Spent after a fury: it takes no turn. */
	public static boolean spent(Mob mob) {
		MobMind mind = MobAi.mind(mob);
		long now = mob.level().getGameTime();
		return mind != null && !mind.furyActive(now) && now < mind.exhaustedUntil;
	}
}
