package dev.forja.entity;

import java.util.UUID;
import java.util.function.Consumer;

import dev.forja.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ring that runs out along the floor, and the warning that comes before it.
 *
 * <p>The smith's shockwave used to be twenty-eight particles sent from the server every tick. At nine
 * blocks out that is a flame every two blocks, moving twenty times a second however fast the game is
 * drawn, and a packet for each of them. A ring is one shape, so it is one thing now: the server says
 * <b>once</b> where it is, how far it goes and when it started, and every client works out the radius
 * for itself on every frame. That is the same bargain the casting tables made with their pour — the
 * world clock is the only thing that has to be shared.
 *
 * <p>It hurts nobody. Whoever owns the attack still decides who is hit, with {@link #radiusAt} so that
 * the hit and the picture are the same circle. What this entity owns is the part you look at: the
 * circle on the floor while the blow is on its way ({@link #telegraph}), and the front that leaves it
 * when the blow lands ({@link #fire}).
 */
public class Shockwave extends Entity {
	/** The orange the forge burns with, and the violet it burns with once he stops holding back. */
	public static final int EMBER = 0xFF7A1E;
	public static final int VIOLET = 0xC460FF;
	/** What the other things in the mod that throw a ring are made of. */
	public static final int STEAM = 0xDCE6EA;
	public static final int SOUL = 0x6BE0F2;
	public static final int SEAL = 0xD8B454;
	public static final int OIL = 0x7FA08A;
	/** The same oil lying on the floor: dark, as a slick is, where the warning for it has to be seen at night. */
	public static final int SLICK = 0x3A4A3E;
	public static final int BLAST = 0xFFB43C;
	public static final int RALLY = 0xFFE9A8;

	/** How long a fired wave outlives its run: the renderer's afterglow, and a tick or two for a late packet. */
	private static final int LINGER = 8;

	/** A warning that never became a blow is dropped this long after it should have landed. */
	private static final int OVERDUE = 10;

	private static final EntityDataAccessor<Float> DATA_REACH = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_WINDUP = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_COLOUR = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_STARTED = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Long> DATA_FIRED = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Float> DATA_FLAME = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_POOL = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_FACING = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_ARC = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_FALL = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_GLYPH = SynchedEntityData.defineId(Shockwave.class, EntityDataSerializers.BOOLEAN);

	/** The half-angle that means "all the way round". */
	public static final float WHOLE = (float) Math.PI;

	/**
	 * What the client does with a wave each tick: the debris, the sound arriving, the camera. Set by the
	 * client initializer and left alone on a dedicated server, which has no business loading any of it.
	 */
	public static Consumer<Shockwave> clientTicker = wave -> {
	};

	private UUID owner;

	/** Whether the warning is carried round by its owner, or stays on the spot it was put down on. */
	private boolean follows = true;

	/** Client-side only: whether this wave has already reached the player watching it. */
	public boolean arrived;

	/** Client-side only: whether the blow itself has been felt yet. */
	public boolean landed;

	/**
	 * Client-side only: the floor under the whole reach as the renderer last measured it, and where
	 * and when it did. Kept here because the entity is the one thing that lasts from frame to frame.
	 */
	public float[] floorColumns;
	/** The columns of {@link #floorColumns} that stand above the level the wave lies at: the ones it tints. */
	public int[] floorRaised;
	/** The one level the wave is drawn at, relative to itself: the floor most of its reach shares. */
	public float floorLevel;
	public int floorSpan;
	public int floorMinX;
	public int floorMinZ;
	public long floorStamp;
	public double floorX;
	public double floorY;
	public double floorZ;

	public Shockwave(EntityType<? extends Shockwave> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/**
	 * The circle on the floor while a blow is still on its way, round whoever is about to deliver it.
	 *
	 * <p>It follows its owner until it is fired, because a warning that stays behind when he is shoved
	 * is a warning about somewhere else, and it goes away by itself if he dies with the hammer up.
	 *
	 * @param windup ticks until the blow, which is how long the circle takes to fill
	 * @param duration ticks the front takes to run from the middle to {@code reach}
	 */
	public static Shockwave telegraph(ServerLevel level, Entity owner, double reach, int windup, int duration, int colour) {
		return telegraph(level, owner, null, reach, windup, duration, colour, 1.0F);
	}

	/**
	 * The same, for a blow that lands somewhere other than on top of whoever throws it.
	 *
	 * @param centre where it will land, and where the circle stays; {@code null} to follow the owner
	 * @param flame how tall the fire on the front stands, against the smith's hammer at 1. Steam, oil
	 *     and a struck seal are not bonfires, and the same number is how hard the ring shakes the camera
	 */
	public static Shockwave telegraph(ServerLevel level, Entity owner, Vec3 centre, double reach, int windup, int duration, int colour, float flame) {
		return make(level, owner, centre, reach, windup, duration, colour, flame, false, false, 0.0F, WHOLE, 0.0F);
	}

	/**
	 * A warning that is a wedge and not a circle: for a blow thrown one way.
	 *
	 * <p>The automaton's slag lands in a quarter turn in front of it, and was the one area attack left
	 * drawing itself in particles because a circle would have been a lie about it. Same line, same
	 * glow filling towards it, same blow when they meet — with a straight edge down each side.
	 *
	 * @param aim the way it is thrown; only the level part of it is used
	 * @param halfAngle how far either side of that it reaches, in radians
	 */
	public static Shockwave wedge(ServerLevel level, Entity owner, Vec3 centre, Vec3 aim, double halfAngle, double reach,
		int windup, int duration, int colour, float flame) {
		return make(level, owner, centre, reach, windup, duration, colour, flame, false, false, (float) Math.atan2(aim.z, aim.x), (float) halfAngle, 0.0F);
	}

	/**
	 * A warning with nobody behind it: for something the world itself is about to do to a spot. It goes
	 * by its own clock if whatever it warns of never fires it.
	 */
	public static Shockwave mark(ServerLevel level, Vec3 centre, double reach, int windup, int duration, int colour, float flame) {
		return make(level, null, centre, reach, windup, duration, colour, flame, false, false, 0.0F, WHOLE, 0.0F);
	}

	/**
	 * The same, with the thing that is coming down drawn above it: it starts {@code fall} blocks up and
	 * arrives as the warning runs out, slowly at first and then not — {@link #fallHeight} is the one
	 * place that curve is written, for whoever is also moving something down it on the server.
	 */
	public static Shockwave markFalling(ServerLevel level, Vec3 centre, double reach, int windup, int duration, int colour, float flame, float fall) {
		return make(level, null, centre, reach, windup, duration, colour, flame, false, false, 0.0F, WHOLE, fall);
	}

	/** How high the falling thing still is when {@code left} of its fall (1 at the start, 0 on landing) remains. */
	public static double fallHeight(double fall, double left) {
		return fall * left * left;
	}

	/** A wave with no warning in front of it: something that has already happened, drawn. */
	public static Shockwave burst(ServerLevel level, Vec3 centre, double reach, int duration, int colour) {
		return burst(level, centre, reach, duration, colour, 1.0F);
	}

	public static Shockwave burst(ServerLevel level, Vec3 centre, double reach, int duration, int colour, float flame) {
		return make(level, null, centre, reach, 0, duration, colour, flame, true, false, 0.0F, WHOLE, 0.0F);
	}

	/**
	 * Ground that stays dangerous for a while: a patch the size of {@code reach} that lies there for
	 * {@code ticks} and thins out as it goes, so how long it has left can be read off it.
	 *
	 * <p>The slag a Living Slag dies into and the oil the Quencher throws were both a handful of
	 * particles scattered over a circle by the server, every tick, for as long as the patch lasted —
	 * which said "something is here" and not where it ended. This is the same one-packet bargain as
	 * the ring: told once, drawn by every client, and the edge of it is the edge of what hurts.
	 */
	public static Shockwave pool(ServerLevel level, Vec3 centre, double reach, int ticks, int colour, float flame) {
		return make(level, null, centre, reach, 0, ticks, colour, flame, true, true, 0.0F, WHOLE, 0.0F);
	}

	/**
	 * A patch with writing on it: what a forged tome leaves where its area opened. The same patch as
	 * {@link #pool}, quieter, with a rune turning on it — the letters one way and the star the other — that
	 * flares each time it bites. No fire stands on it: what it is made of is whatever its colour is.
	 */
	public static Shockwave rune(ServerLevel level, Vec3 centre, double reach, int ticks, int colour) {
		return make(level, null, centre, reach, 0, ticks, colour, 0.0F, true, true, 0.0F, WHOLE, 0.0F, true);
	}

	/** The same, in the shape of a wedge: what the automaton's fists leave burning in front of it. */
	public static Shockwave poolWedge(ServerLevel level, Vec3 centre, Vec3 aim, double halfAngle, double reach, int ticks, int colour, float flame) {
		return make(level, null, centre, reach, 0, ticks, colour, flame, true, true, (float) Math.atan2(aim.z, aim.x), (float) halfAngle, 0.0F);
	}

	/**
	 * Everything is set before the entity goes into the world, so that what the client is told on the
	 * first tick is already the whole truth. Set afterwards, the shape arrives a tick late, and a wedge
	 * spends its first frame as a full circle.
	 */
	private static Shockwave make(ServerLevel level, @org.jspecify.annotations.Nullable Entity owner, @org.jspecify.annotations.Nullable Vec3 centre,
		double reach, int windup, int duration, int colour, float flame, boolean fired, boolean pool, float facing, float arc, float fall) {
		return make(level, owner, centre, reach, windup, duration, colour, flame, fired, pool, facing, arc, fall, false);
	}

	private static Shockwave make(ServerLevel level, @org.jspecify.annotations.Nullable Entity owner, @org.jspecify.annotations.Nullable Vec3 centre,
		double reach, int windup, int duration, int colour, float flame, boolean fired, boolean pool, float facing, float arc, float fall, boolean glyph) {
		Shockwave wave = new Shockwave(ModEntities.ONDA, level);
		wave.owner = owner == null ? null : owner.getUUID();
		wave.follows = centre == null;
		wave.setPos(centre == null ? owner.position() : centre);
		wave.entityData.set(DATA_REACH, (float) reach);
		wave.entityData.set(DATA_WINDUP, fired ? 0 : Math.max(1, windup));
		wave.entityData.set(DATA_DURATION, Math.max(1, duration));
		wave.entityData.set(DATA_COLOUR, colour);
		wave.entityData.set(DATA_FLAME, flame);
		wave.entityData.set(DATA_POOL, pool);
		wave.entityData.set(DATA_FACING, facing);
		wave.entityData.set(DATA_ARC, Mth.clamp(arc, 0.05F, WHOLE));
		wave.entityData.set(DATA_FALL, fall);
		wave.entityData.set(DATA_GLYPH, glyph);
		wave.entityData.set(DATA_STARTED, level.getGameTime());
		if (fired) {
			wave.entityData.set(DATA_FIRED, level.getGameTime());
		}
		level.addFreshEntity(wave);
		return wave;
	}

	/** Moves a warning that has not landed yet: for a mark that is worked out from someone who can be shoved. */
	public void recentre(Vec3 centre) {
		if (!this.fired() && this.position().distanceToSqr(centre) > 1.0E-4) {
			this.setPos(centre);
		}
	}

	/** The blow lands: the circle stops following anyone and the front leaves from here. */
	public void fire(Vec3 centre) {
		this.setPos(centre);
		this.entityData.set(DATA_FIRED, this.level().getGameTime());
	}

	/**
	 * How far the front has run {@code age} ticks after the blow.
	 *
	 * <p>The one place the speed of the ring is written down. Whoever deals the damage asks this, and
	 * so does the renderer, which is the whole reason the ring you see is the ring that hits you. It
	 * runs at a constant speed on purpose: an eased ring looks better and is harder to time a jump
	 * against, and this is a move that is meant to be timed.
	 */
	public static double radiusAt(double reach, float age, int duration) {
		return reach * Mth.clamp(age / Math.max(1, duration), 0.0F, 1.0F);
	}

	/**
	 * How high the floor stands at a spot near the wave, relative to the wave itself.
	 *
	 * <p>A short look up and down from where the blow landed, not the heightmap: under a roof — and
	 * his forge is in the Nether, under the biggest roof there is — the heightmap answers with the
	 * bedrock ceiling. Nothing found means level ground, which is the honest guess over a drop.
	 */
	public static float floorAt(Level level, double x, double baseY, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = Mth.floor(baseY + 1.5);
		int bottom = Mth.floor(baseY - 3.0);
		// A column that is solid all the way up is a wall, not a floor: the ring goes into it level and
		// the wall hides it, rather than the ring climbing the wall to get over it.
		pos.set(Mth.floor(x), top + 1, Mth.floor(z));
		boolean clearAbove = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
		for (int y = top; y >= bottom; y--) {
			pos.set(Mth.floor(x), y, Mth.floor(z));
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				clearAbove = true;
				continue;
			}
			if (clearAbove) {
				return (float) (y + shape.max(Direction.Axis.Y) - baseY);
			}
			clearAbove = false;
		}
		return 0.0F;
	}

	public float reach() {
		return this.entityData.get(DATA_REACH);
	}

	public int windup() {
		return this.entityData.get(DATA_WINDUP);
	}

	public int duration() {
		return this.entityData.get(DATA_DURATION);
	}

	public int colour() {
		return this.entityData.get(DATA_COLOUR);
	}

	/** How far up whatever this warns of starts its fall, in blocks, or zero if nothing is falling. */
	public float fall() {
		return this.entityData.get(DATA_FALL);
	}

	/** The way a wedge points, in radians, the way {@code atan2(z, x)} gives it. */
	public float facing() {
		return this.entityData.get(DATA_FACING);
	}

	/** How far either side of {@link #facing} it reaches, in radians; {@link #WHOLE} for a full circle. */
	public float arc() {
		return this.entityData.get(DATA_ARC);
	}

	/** Whether a spot, given as an offset from the centre, is inside the wedge (always, for a circle). */
	public boolean covers(double dx, double dz) {
		float arc = this.arc();
		if (arc >= WHOLE - 0.001F) {
			return true;
		}
		double turn = Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx) - this.facing()));
		return Math.abs(turn) <= Math.toDegrees(arc);
	}

	/** Whether this is a patch lying on the ground for its whole life rather than a ring crossing it. */
	public boolean pool() {
		return this.entityData.get(DATA_POOL);
	}

	/** Whether the patch has a rune written on it. */
	public boolean glyph() {
		return this.entityData.get(DATA_GLYPH);
	}

	/** How tall the fire stands on the front and how hard the ring lands, against the smith's hammer at 1. */
	public float flame() {
		return this.entityData.get(DATA_FLAME);
	}

	public long startedAt() {
		return this.entityData.get(DATA_STARTED);
	}

	/** The tick the blow landed on, or -1 while it is still only a warning. */
	public long firedAt() {
		return this.entityData.get(DATA_FIRED);
	}

	public boolean fired() {
		return this.firedAt() >= 0L;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_REACH, 8.0F);
		builder.define(DATA_WINDUP, 0);
		builder.define(DATA_DURATION, 20);
		builder.define(DATA_COLOUR, EMBER);
		builder.define(DATA_STARTED, 0L);
		builder.define(DATA_FIRED, -1L);
		builder.define(DATA_FLAME, 1.0F);
		builder.define(DATA_POOL, false);
		builder.define(DATA_FACING, 0.0F);
		builder.define(DATA_ARC, WHOLE);
		builder.define(DATA_FALL, 0.0F);
		builder.define(DATA_GLYPH, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			clientTicker.accept(this);
			return;
		}
		long now = level.getGameTime();
		if (this.fired()) {
			if (now - this.firedAt() > this.duration() + LINGER) {
				this.discard();
			}
			return;
		}
		if (now - this.startedAt() > this.windup() + OVERDUE) {
			this.discard();
			return;
		}
		// A warning nobody owns — where a meteorite is going to land — lives by its clock alone.
		if (this.owner == null) {
			return;
		}
		Entity holder = level.getEntity(this.owner);
		if (holder == null || !holder.isAlive()) {
			this.discard();
			return;
		}
		if (this.follows) {
			this.setPos(holder.position());
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	/** It is a drawing on the floor: it should not hold a pressure plate down. */
	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE;
	}

	/**
	 * Drawn from much further off than a half-block entity normally would be.
	 *
	 * <p>The distance an entity is drawn from is worked out from the size of its box, and this one's
	 * box is a formality: the thing it draws is eighteen blocks across.
	 */
	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128.0 * 128.0;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
