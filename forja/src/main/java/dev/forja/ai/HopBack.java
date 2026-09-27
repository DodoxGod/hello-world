package dev.forja.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.forja.combat.AttackTokens;
import dev.forja.combat.CombatAnim;
import dev.forja.combat.CombatConfig;
import dev.forja.combat.Posture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * In, strike, out: the hop back after a blow lands (Andy, 2026-09-27: "sí; arañas sí, creepers salto
 * cortito; cada 6 segundos"). Every melee monster that lands a blow crouches for a moment and hops two or
 * three blocks back out of reach, letting go of its turn so the next one comes in from its own side. A
 * creeper has no blow to land; it gets a short hop back when it breaks off a hiss.
 *
 * <p>Never more than once every {@link #COOLDOWN} ticks, never stunned or off the ground, and never into
 * a wall, over a drop or into lava: a hop that ends the monster's own fight is not a tactic.
 */
public final class HopBack {
	/** "Cada 6 segundos". */
	public static final int COOLDOWN = 120;
	/** The crouch before it, which is what the player has to read it by. */
	public static final int TELL = 3;
	public static final double SPEED = 0.5;
	public static final double LIFT = 0.3;
	public static final double REACH = 2.5;
	/** The creeper's: a short one. */
	public static final double SHORT_SPEED = 0.25;
	public static final double SHORT_LIFT = 0.2;
	public static final double SHORT_REACH = 1.2;
	/** The deepest drop it will hop down. */
	public static final int SAFE_DROP = 2;

	private record Pending(Mob mob, LivingEntity from, long due, boolean small) {
	}

	private static final Map<Mob, Long> READY = new WeakHashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();

	private HopBack() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (int i = PENDING.size() - 1; i >= 0; i--) {
				Pending hop = PENDING.get(i);
				if (hop.mob.level().getGameTime() < hop.due) {
					continue;
				}
				PENDING.remove(i);
				hop(hop);
			}
		});
	}

	/** A melee blow of this monster's has just landed on {@code target}. */
	public static void afterHit(Mob mob, LivingEntity target) {
		plan(mob, target, false);
	}

	/** A creeper has broken off its hiss: a short hop back. */
	public static void afterFeint(Creeper creeper, LivingEntity target) {
		plan(creeper, target, true);
	}

	/** Whether this monster could hop back now, cooldown aside of nothing. */
	public static boolean ready(Mob mob) {
		return mob.level().getGameTime() >= READY.getOrDefault(mob, Long.MIN_VALUE);
	}

	private static void plan(Mob mob, LivingEntity target, boolean small) {
		if (!CombatConfig.get().enabled || !(mob.level() instanceof ServerLevel level) || target == null) {
			return;
		}
		long now = level.getGameTime();
		if (!ready(mob) || !able(mob, now)) {
			return;
		}
		Vec3 away = away(mob, target);
		if (away == null || !safe(level, mob, away, small ? SHORT_REACH : REACH)) {
			return;
		}
		READY.put(mob, now + COOLDOWN);
		PENDING.add(new Pending(mob, target, now + TELL, small));
		CombatAnim.broadcast(mob, CombatAnim.Kind.LUNGE, TELL);
	}

	private static void hop(Pending hop) {
		Mob mob = hop.mob;
		if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || !able(mob, level.getGameTime())) {
			return;
		}
		Vec3 away = away(mob, hop.from);
		if (away == null) {
			return;
		}
		double speed = hop.small ? SHORT_SPEED : SPEED;
		mob.setDeltaMovement(away.x * speed, hop.small ? SHORT_LIFT : LIFT, away.z * speed);
		mob.hurtMarked = true;
		mob.getNavigation().stop();
		CombatAnim.broadcast(mob, CombatAnim.Kind.LEAP, 20, CombatAnim.Kind.LEAP_BACK, 0.0F);
		AttackTokens.release(hop.from, mob);
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.1, mob.getZ(), 4, 0.2, 0.02, 0.2, 0.01);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GOAT_LONG_JUMP, SoundSource.HOSTILE, 0.5F, 1.4F);
	}

	private static boolean able(Mob mob, long now) {
		return mob.onGround() && !mob.isPassenger() && !mob.isInWater() && !mob.isInLava() && !Posture.isStaggered(mob, now)
			&& !mob.isNoAi();
	}

	/** Straight away from the target, flat. */
	private static Vec3 away(Mob mob, LivingEntity target) {
		Vec3 away = new Vec3(mob.getX() - target.getX(), 0.0, mob.getZ() - target.getZ());
		return away.lengthSqr() < 1.0E-6 ? null : away.normalize();
	}

	/** Room to land where it would: nothing in the way, something to land on no further down than a short drop, no lava. */
	static boolean safe(ServerLevel level, Mob mob, Vec3 away, double reach) {
		for (double step = 0.5; step <= reach; step += 0.5) {
			AABB box = mob.getBoundingBox().move(away.x * step, 0.1, away.z * step);
			if (!level.noCollision(mob, box)) {
				return false;
			}
		}
		Vec3 land = mob.position().add(away.scale(reach));
		BlockPos feet = BlockPos.containing(land);
		for (int down = 0; down <= SAFE_DROP + 1; down++) {
			BlockPos at = feet.below(down);
			if (level.getFluidState(at).is(net.minecraft.tags.FluidTags.LAVA) || level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.FIRE)) {
				return false;
			}
			if (down > 0 && level.getBlockState(at).isFaceSturdy(level, at, Direction.UP)) {
				return true;
			}
		}
		return false;
	}
}
