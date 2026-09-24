package dev.forja.entity;

import dev.forja.magic.Spellcasting;
import dev.forja.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the crescent staff throws: a bolt in the colour of the staff's núcleo.
 *
 * <p>It has no model. It is a line of coloured dust the server lays along its own flight, with a
 * brighter spark at the head — which is what a bolt of light looks like anyway, takes its colour from
 * any material without a texture for each, and costs the client nothing it was not already drawing.
 * Moved by hand like the thrown head: straight, no gravity, stopped by the first wall or the first
 * living thing that is not its owner.
 */
public class MagicBolt extends Projectile {
	public static final int LIFETIME = 40;

	private int colour = 0xFFFFFF;
	private float damage = 4.0F;
	/** The staff it came from: its upgrades ride on the bolt, and the kill is the staff's. */
	private net.minecraft.world.item.ItemStack weapon = net.minecraft.world.item.ItemStack.EMPTY;
	/** Buscador: how far it can turn in a tick, in radians. */
	private float seek;
	/** Sobrecarga: the big one. */
	private boolean big;
	/**
	 * Resonancia: an echo lands even on what is still flinching from the bolt it echoes. Without this the
	 * echo of a bolt is worth nothing against the one thing it was aimed at — a hurt mob shrugs off any
	 * lesser blow for half a second. Prisma's side bolts do not have it, the way three arrows of a
	 * Multishot do not all count on one target: a fan is for a crowd.
	 */
	private boolean insistent;

	public MagicBolt insistent() {
		this.insistent = true;
		return this;
	}

	/** How far off a seeking bolt notices something, and how far off its own line that may be (the cosine of it). */
	private static final double SEEK_RANGE = 12.0;
	private static final double SEEK_CONE = 0.5;

	public MagicBolt(EntityType<? extends MagicBolt> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public MagicBolt(ServerLevel level, Player owner, Vec3 look, int colour, float damage, net.minecraft.world.item.ItemStack weapon, float seek, boolean big) {
		this(ModEntities.PROYECTIL_MAGICO, level);
		this.setOwner(owner);
		this.colour = colour;
		this.damage = damage;
		this.weapon = weapon;
		this.seek = seek;
		this.big = big;
		this.setPos(owner.getX() + look.x * 0.6, owner.getEyeY() - 0.15 + look.y * 0.6, owner.getZ() + look.z * 0.6);
		this.setDeltaMovement(look.scale(Spellcasting.BOLT_SPEED));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public net.minecraft.world.item.ItemStack getWeaponItem() {
		return this.weapon.isEmpty() ? null : this.weapon;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.tickCount > LIFETIME) {
			this.discard();
			return;
		}
		this.steer(level);
		Vec3 start = this.position();
		Vec3 end = start.add(this.getDeltaMovement());
		for (int step = 1; step <= 6; step++) {
			Vec3 at = start.lerp(end, step / 6.0);
			level.sendParticles(new DustParticleOptions(this.colour, this.big ? 2.2F : 1.1F), at.x, at.y, at.z, this.big ? 3 : 1, this.big ? 0.08 : 0.02, this.big ? 0.08 : 0.02, this.big ? 0.08 : 0.02, 0.0);
			BlockPos pos = BlockPos.containing(at);
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
				this.burst(level, at);
				return;
			}
			AABB box = new AABB(at, at).inflate(this.big ? 0.6 : 0.35);
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, other -> other.isAlive() && other != this.getOwner())) {
				if (this.insistent) {
					victim.invulnerableTime = 0;
				}
				Spellcasting.land(level, victim, level.damageSources().indirectMagic(this, this.getOwner()), this.damage, this.weapon, true);
				this.burst(level, at);
				return;
			}
		}
		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 1, 0.0, 0.0, 0.0, 0.0);
		this.setPos(end);
	}

	/**
	 * Buscador: bends the flight towards whatever is hunting the caster and lies most nearly ahead.
	 *
	 * <p>Only what is hostile — a monster, or anything that has the caster for its target. A bolt that
	 * turned for the nearest living thing would turn for the villager being defended and for the horse
	 * being ridden, and an upgrade that kills your horse is not an upgrade.
	 */
	private void steer(ServerLevel level) {
		if (this.seek <= 0.0F) {
			return;
		}
		Vec3 flight = this.getDeltaMovement();
		double speed = flight.length();
		if (speed < 1.0E-4) {
			return;
		}
		Vec3 heading = flight.scale(1.0 / speed);
		Vec3 from = this.position();
		net.minecraft.world.entity.Entity owner = this.getOwner();
		Vec3 wanted = null;
		double best = SEEK_CONE;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(SEEK_RANGE), e -> e.isAlive() && e != owner)) {
			boolean hostile = other instanceof net.minecraft.world.entity.monster.Enemy
				|| (other instanceof net.minecraft.world.entity.Mob mob && owner != null && mob.getTarget() == owner);
			if (!hostile || (owner != null && other.isAlliedTo(owner))) {
				continue;
			}
			Vec3 to = other.getBoundingBox().getCenter().subtract(from);
			double distance = to.length();
			if (distance < 0.5 || distance > SEEK_RANGE) {
				continue;
			}
			double ahead = heading.dot(to.scale(1.0 / distance));
			if (ahead > best) {
				best = ahead;
				wanted = to.scale(1.0 / distance);
			}
		}
		if (wanted == null) {
			return;
		}
		double angle = Math.acos(Math.max(-1.0, Math.min(1.0, best)));
		if (angle < 1.0E-3) {
			return;
		}
		double share = Math.min(1.0, this.seek / angle);
		this.setDeltaMovement(heading.scale(1.0 - share).add(wanted.scale(share)).normalize().scale(speed));
	}

	private void burst(ServerLevel level, Vec3 at) {
		level.sendParticles(new DustParticleOptions(this.colour, this.big ? 2.4F : 1.6F), at.x, at.y, at.z, this.big ? 40 : 18, this.big ? 0.5 : 0.25, this.big ? 0.5 : 0.25, this.big ? 0.5 : 0.25, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.08);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.9F, 1.5F);
		this.discard();
	}
}
