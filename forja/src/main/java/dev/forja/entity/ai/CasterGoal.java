package dev.forja.entity.ai;

import java.util.EnumSet;

import dev.forja.combat.CombatAnim;
import dev.forja.combat.CombatStats;
import dev.forja.entity.Shockwave;
import dev.forja.forge.ForgeType;
import dev.forja.magic.Spellcasting;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A monster with a forged staff or tome in its hand, fighting with it by rules — the same spells a
 * player casts, out of magic/Spellcasting, and never a network's: the networks were trained on what a
 * body and a bow can do and know nothing of casting (that is for a later contract). ai/MobAi keeps a
 * caster on the rules and ai/RuleBrain leaves it to this goal.
 *
 * <p>Both spells say they are coming, because this mod's rule is fair before hard. A staff is aimed for
 * as long as a skeleton draws its bow, glowing in the colour of its núcleo, and only then does the
 * bolt leave, once the staff's own wait allows it. A tome is worse, so it warns more: the circle of the
 * area it is about to open lies on the floor where it will open, and fills, and whoever is still in it
 * when it is full is in the area.
 */
public final class CasterGoal extends Goal {
	/** A staff: how long it aims before the bolt leaves, which is as long as a skeleton draws. */
	public static final int BOLT_AIM = 20;
	/** How far from its prey a staff-bearer likes to stand, and past what it will not throw at all. */
	public static final double BOLT_NEAR = 6.0;
	public static final double BOLT_FAR = 14.0;
	public static final double BOLT_RANGE = 16.0;
	/** A tome: how long the circle lies on the floor before the area opens under it. */
	public static final int TOME_WINDUP = 30;
	/**
	 * It opens the book only with its prey this near the five blocks the area lands at, so the prey is
	 * inside the circle when it appears; and it keeps within half of that of the five blocks while it
	 * waits, stepping back from a prey that crowds it and after one that steps away.
	 */
	public static final double TOME_SLACK = 1.5;

	/** What it did, for the tests to count (combat/CombatStats). */
	public static final String BOLT = "caster_bolt";
	public static final String TOME_WARNED = "caster_tome_warned";
	public static final String TOME_OPENED = "caster_tome_opened";

	private final Mob mob;
	private final Windup windup = new Windup();
	private int cooldown;
	private int repath;

	public CasterGoal(Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	/** The spell in a mob's hand: a staff's or a tome's that still holds together, or null. */
	public static @Nullable ForgeType spell(Mob mob) {
		ItemStack held = mob.getMainHandItem();
		ForgedParts parts = held.get(ModComponents.PARTS);
		return parts != null && Spellcasting.casts(parts.type()) && !held.isBroken() ? parts.type() : null;
	}

	/** Whether this mob fights by casting, and so by the rules (see ai/MobAi). */
	public static boolean casts(Mob mob) {
		return spell(mob) != null;
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.mob.getTarget();
		return target != null && target.isAlive() && spell(this.mob) != null
			&& !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.mob.setAggressive(true);
	}

	@Override
	public void stop() {
		this.windup.cancel();
		this.mob.setAggressive(false);
		this.mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		ForgeType spell = spell(this.mob);
		if (target == null || spell == null || !(this.mob.level() instanceof ServerLevel level)) {
			return;
		}
		this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		// Committed: it stands and finishes what it started, which is what makes it something to dodge.
		if (this.windup.charging()) {
			this.mob.getNavigation().stop();
			this.windup.tick(level);
			return;
		}
		if (this.cooldown > 0) {
			this.cooldown--;
		}
		boolean sees = this.mob.getSensing().hasLineOfSight(target);
		double distance = Math.hypot(target.getX() - this.mob.getX(), target.getZ() - this.mob.getZ());
		if (spell == ForgeType.BACULO) {
			this.keep(target, sees, distance, BOLT_NEAR, BOLT_FAR);
			if (this.cooldown <= 0 && sees && distance <= BOLT_RANGE) {
				this.aim(level);
			}
		} else {
			this.keep(target, sees, distance, Spellcasting.TOME_DISTANCE - TOME_SLACK * 0.5, Spellcasting.TOME_DISTANCE + TOME_SLACK * 0.5);
			if (this.cooldown <= 0 && sees && Math.abs(distance - Spellcasting.TOME_DISTANCE) <= TOME_SLACK) {
				this.read(level);
			}
		}
	}

	/** Out of sight or too far, it closes in; too near, it backs off; in between, it stands and casts. */
	private void keep(LivingEntity target, boolean sees, double distance, double near, double far) {
		if (!sees || distance > far) {
			this.pathTo(target.getX(), target.getY(), target.getZ(), 1.0);
		} else if (distance < near) {
			double dx = this.mob.getX() - target.getX();
			double dz = this.mob.getZ() - target.getZ();
			double length = Math.max(1.0E-6, Math.hypot(dx, dz));
			if (!this.pathTo(this.mob.getX() + dx / length * 4.0, this.mob.getY(), this.mob.getZ() + dz / length * 4.0, 1.1)
				&& this.mob instanceof PathfinderMob pathfinder) {
				Vec3 away = DefaultRandomPos.getPosAway(pathfinder, 8, 4, target.position());
				if (away != null) {
					this.pathTo(away.x, away.y, away.z, 1.1);
				}
			}
		} else {
			this.mob.getNavigation().stop();
		}
	}

	/** The staff comes up and glows while it aims; the bolt leaves when the aim is done. */
	private void aim(ServerLevel level) {
		int colour = colour(this.mob.getMainHandItem());
		CombatAnim.broadcast(this.mob, CombatAnim.Kind.TELEGRAPH, BOLT_AIM);
		level.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.0F, 1.5F);
		this.windup.start(BOLT_AIM, (world, left, total) -> {
			if (left % 3 == 0) {
				Vec3 tip = this.mob.getEyePosition().add(Spellcasting.aim(this.mob).scale(0.7));
				world.sendParticles(new DustParticleOptions(colour, 0.6F + 0.8F * (total - left) / total), tip.x, tip.y, tip.z, 2, 0.05, 0.05, 0.05, 0.0);
			}
		}, this::bolt);
	}

	private void bolt(ServerLevel level) {
		LivingEntity target = this.mob.getTarget();
		if (target == null || spell(this.mob) != ForgeType.BACULO || !this.mob.getSensing().hasLineOfSight(target)) {
			// It lost its mark while aiming: the staff goes down and comes up again a little later.
			this.cooldown = BOLT_AIM / 2;
			return;
		}
		ItemStack staff = this.mob.getMainHandItem();
		this.mob.swing(InteractionHand.MAIN_HAND);
		Spellcasting.cast(level, this.mob, staff, ForgeType.BACULO, null);
		this.cooldown = Spellcasting.cooldown(staff, ForgeType.BACULO);
		CombatStats.record(this.mob, BOLT);
	}

	/**
	 * The book opens: the spot the area will land on is fixed now, five blocks towards its prey as the
	 * tome always lands, and marked with a circle that fills as the reading goes on.
	 */
	private void read(ServerLevel level) {
		ItemStack tome = this.mob.getMainHandItem();
		int colour = colour(tome);
		Vec3 at = Spellcasting.target(level, this.mob);
		CombatAnim.broadcast(this.mob, CombatAnim.Kind.TELEGRAPH, TOME_WINDUP);
		level.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0F, 0.7F);
		this.windup.start(TOME_WINDUP, (world, left, total) -> {
			if (left % 4 == 0) {
				world.sendParticles(new DustParticleOptions(colour, 1.0F), this.mob.getX(), this.mob.getY(0.6), this.mob.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
			}
		}, world -> this.open(world, at));
		this.windup.warn(Shockwave.telegraph(level, this.mob, at, Spellcasting.RUNE_REACH, TOME_WINDUP, 8, colour, 0.3F));
		CombatStats.record(this.mob, TOME_WARNED);
	}

	private void open(ServerLevel level, Vec3 at) {
		if (spell(this.mob) != ForgeType.GRIMORIO) {
			return;
		}
		ItemStack tome = this.mob.getMainHandItem();
		this.mob.swing(InteractionHand.MAIN_HAND);
		Spellcasting.cast(level, this.mob, tome, ForgeType.GRIMORIO, at);
		this.cooldown = Spellcasting.cooldown(tome, ForgeType.GRIMORIO);
		CombatStats.record(this.mob, TOME_OPENED);
	}

	private static int colour(ItemStack stack) {
		ForgedParts parts = stack.get(ModComponents.PARTS);
		return parts == null ? 0xFFFFFF : Spellcasting.core(parts).color;
	}

	/** A path, asked for at most every ten ticks so the pathfinder is not asked every tick. */
	private boolean pathTo(double x, double y, double z, double speed) {
		if (--this.repath > 0 && !this.mob.getNavigation().isDone()) {
			this.mob.getNavigation().setSpeedModifier(speed);
			return true;
		}
		this.repath = 10;
		return this.mob.getNavigation().moveTo(x, y, z, speed);
	}
}
