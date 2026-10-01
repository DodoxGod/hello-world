package dev.forja.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.forja.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A tool head flying out of its handle. Server-side it carries a copy of the whole tool, so block
 * drops follow the tool's tier and enchantments (Fortune, Silk Touch, Fundicion, Telequinesis).
 * Drops land at the thrower's feet. The head is never saved: if the world closes mid-flight the
 * cooldown simply runs out.
 *
 * <p>A thrown weapon or shield is different: it carries the only copy of the item, since the hand it
 * left was emptied. However its flight ends (back to the hand, against a wall, out of time, its thrower
 * dead or gone, its chunk unloaded) that one copy goes to exactly one place: the ground, for a weapon
 * that does not come back and hit something, or else {@link dev.forja.upgrade.ThrowReturns}, which
 * puts it in the thrower's inventory or keeps it for them.
 */
public class ThrownHead extends Projectile {
	/** What is flying, and what it does when it reaches something. */
	public enum Mode {
		/** Lanzacabezas: the head of a tool, mining its way through and coming back. */
		HEAD,
		/** A whole axe or dagger, thrown for two thirds of what it does in hand. */
		WEAPON,
		/** A shield that shoves and stuns whoever it meets, then looks for the next one. */
		SHIELD,
		/** A grappling claw: it pulls, it does not cut. */
		GARFIO
	}

	public static final int MAX_LIFETIME = 200;
	private static final double SPEED = 1.1;
	private static final double RETURN_SPEED = 1.4;
	private static final double MAX_DISTANCE = 32.0;
	/**
	 * How long the rope hauls for once the claw bites.
	 *
	 * <p>It used to be a single shove and then the entity went home, so what the hook actually did was
	 * throw you a couple of blocks and drop you: gravity and drag ate the impulse in three ticks. A
	 * rope does not shove, it <b>winches</b> — it keeps pulling until you are there.
	 */
	private static final int HAUL_TICKS = 30;
	/** Near enough to the anchor to call it arrived. */
	private static final double ARRIVED = 2.2;
	private static final EntityDataAccessor<ItemStack> DATA_HEAD = SynchedEntityData.defineId(ThrownHead.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Boolean> DATA_RETURNING = SynchedEntityData.defineId(ThrownHead.class, EntityDataSerializers.BOOLEAN);
	/** A pointed weapon (trident, dagger, spear) flies tip first instead of tumbling end over end. */
	private static final EntityDataAccessor<Boolean> DATA_POINT_FIRST = SynchedEntityData.defineId(ThrownHead.class, EntityDataSerializers.BOOLEAN);

	private ItemStack tool = ItemStack.EMPTY;
	private InteractionHand hand = InteractionHand.MAIN_HAND;
	/** Who threw it, kept apart from the owner reference so it is known even when they are not there. */
	private UUID ownerId;
	private Mode mode = Mode.HEAD;
	private boolean returns = true;
	/**
	 * Whether the throw took the item out of the hand.
	 *
	 * <p>Only a thrown <em>weapon</em> does: the hand is emptied when it leaves and the item is put
	 * back when it lands. A grappling hook does not — you keep holding it and only the claw flies, so
	 * giving "it" back on landing hands you a second hook. This used to be inferred from the mode,
	 * which was true for the two throwing modes and quietly wrong for the hook.
	 */
	private boolean carriesItem;
	private int bounces;
	private double pull = 1.0;
	/** How far this one travels per tick; a hook sets its own so every rope runs out together. */
	private double speed = SPEED;
	/** Ticks of hauling left once the claw has bitten, and where it bit. */
	private int hauling;
	private Vec3 anchor = Vec3.ZERO;
	private double reach = MAX_DISTANCE;
	private int quota;
	private int mined;
	private double traveled;
	private final Set<Integer> hitEntities = new HashSet<>();
	private BlockPos lastBlock = BlockPos.ZERO;

	public ThrownHead(EntityType<? extends ThrownHead> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public ThrownHead(ServerLevel level, Player owner, ItemStack tool, ItemStack headStack, InteractionHand hand, int quota) {
		this(ModEntities.THROWN_HEAD, level);
		this.setOwner(owner);
		this.ownerId = owner.getUUID();
		this.tool = tool;
		this.hand = hand;
		this.quota = quota;
		this.entityData.set(DATA_HEAD, headStack);
		Vec3 look = owner.getLookAngle();
		this.setPos(owner.getX() + look.x * 0.5, owner.getEyeY() - 0.2 + look.y * 0.5, owner.getZ() + look.z * 0.5);
		this.setDeltaMovement(look.scale(SPEED));
		this.faceAlong(this.getDeltaMovement());
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	/** A thrown weapon or shield: it carries the real item, so the hand it left is empty until it lands. */
	public ThrownHead(ServerLevel level, Player owner, ItemStack thrown, InteractionHand hand, Mode mode, boolean returns, int bounces) {
		this(level, owner, thrown, thrown, hand, 0);
		this.mode = mode;
		this.returns = returns;
		this.bounces = bounces;
		// This constructor is the one the hand was emptied for.
		this.carriesItem = true;
		this.entityData.set(DATA_POINT_FIRST, mode == Mode.WEAPON && dev.forja.upgrade.WeaponThrow.fliesPointFirst(thrown));
	}

	public Mode mode() {
		return this.mode;
	}

	/** Who threw it, or null on the client. */
	public UUID ownerId() {
		return this.ownerId;
	}

	/** Whether it is drawn tip first along its flight rather than tumbling. */
	public boolean isPointFirst() {
		return this.entityData.get(DATA_POINT_FIRST);
	}

	/**
	 * Faces the way it moves, the way an arrow does: yaw and pitch of the motion, in the convention the
	 * arrow renderer reads. Synced like any rotation, so the client draws it without guessing.
	 */
	private void faceAlong(Vec3 motion) {
		if (motion.lengthSqr() < 1.0E-7) {
			return;
		}
		this.setYRot((float) Math.toDegrees(Math.atan2(motion.x, motion.z)));
		this.setXRot((float) Math.toDegrees(Math.atan2(motion.y, motion.horizontalDistance())));
	}

	/** Ends the flight now and sends what it carries home: the thrower is leaving the game. */
	public void recall() {
		this.finish(this.getOwner() instanceof ServerPlayer owner ? owner : null);
	}

	/**
	 * The rope, drawn between the hand and the claw.
	 *
	 * <p>The mod calls this thing a rope in four comments and a grapple in its name, and there was
	 * never a rope: a claw flew away from you, stopped, and then you flew towards it with nothing in
	 * between. It reads as a bug more than as a grapple — and it is also the only way to tell, in the
	 * moment, whether the claw actually caught anything.
	 *
	 * <p>Drawn every other tick and thinned by distance, because at full reach a solid line of
	 * particles from here to thirty blocks away is a wall rather than a rope.
	 */
	private void rope(ServerLevel level, ServerPlayer owner) {
		if (this.mode != Mode.GARFIO || level.getGameTime() % 2 != 0) {
			return;
		}
		Vec3 from = owner.getEyePosition().subtract(0.0, 0.25, 0.0);
		Vec3 to = this.position();
		double span = from.distanceTo(to);
		if (span < 1.0) {
			return;
		}
		int links = Math.min(26, Math.max(4, (int) (span * 1.1)));
		for (int step = 1; step < links; step++) {
			double fraction = step / (double) links;
			// A little slack in the middle, which is what a rope does and a laser does not.
			double sag = Math.sin(fraction * Math.PI) * Math.min(0.9, span * 0.05);
			level.sendParticles(ROPE,
				net.minecraft.util.Mth.lerp(fraction, from.x, to.x),
				net.minecraft.util.Mth.lerp(fraction, from.y, to.y) - sag,
				net.minecraft.util.Mth.lerp(fraction, from.z, to.z),
				1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** The colour of the chain: the dull grey of the links it is made of. */
	private static final net.minecraft.core.particles.DustParticleOptions ROPE =
		new net.minecraft.core.particles.DustParticleOptions(0xA8ADB6, 0.6F);

	/** A grappling claw: how hard it pulls and how far the rope goes. */
	public ThrownHead withRope(double pull, double reach) {
		this.mode = Mode.GARFIO;
		this.pull = pull;
		this.reach = reach;
		// Every hook runs out its own rope in the same throw, so a long one is faster rather than
		// slower. Re-aimed from where it already is, because the constructor has already set off.
		this.speed = dev.forja.forge.ForgeStats.hookSpeed(reach);
		this.setDeltaMovement(this.getDeltaMovement().normalize().scale(this.speed));
		return this;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_HEAD, ItemStack.EMPTY);
		builder.define(DATA_RETURNING, false);
		builder.define(DATA_POINT_FIRST, false);
	}

	public ItemStack getHead() {
		return this.entityData.get(DATA_HEAD);
	}

	public boolean isReturning() {
		return this.entityData.get(DATA_RETURNING);
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
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		// The base tick can take it out of the world (below the void, say), and onRemoval has already sent
		// the weapon home by then.
		if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
			return;
		}

		ServerPlayer owner = this.getOwner() instanceof ServerPlayer player ? player : null;
		if (owner == null || !owner.isAlive() || owner.level() != level || this.tickCount > MAX_LIFETIME) {
			// Out of time, or no thrower here to fly back to: it goes home straight away rather than
			// falling where it is (or, as it used to, vanishing with the weapon still in it).
			this.finish(owner);
			return;
		}

		this.rope(level, owner);

		if (this.hauling > 0) {
			this.haul(level, owner);
			return;
		}

		if (this.isReturning()) {
			Vec3 target = owner.getEyePosition().subtract(0.0, 0.4, 0.0);
			Vec3 toOwner = target.subtract(this.position());
			double distance = toOwner.length();
			if (distance < 1.2) {
				this.finish(owner);
				return;
			}
			Vec3 motion = toOwner.normalize().scale(Math.min(RETURN_SPEED, distance));
			this.setDeltaMovement(motion);
			// Coming back it turns its tip away and flies haft first into the hand, as vanilla's loyal
			// trident does.
			this.faceAlong(motion.reverse());
			this.setPos(this.position().add(motion));
			return;
		}

		Vec3 start = this.position();
		Vec3 motion = this.getDeltaMovement();
		Vec3 end = start.add(motion);
		this.hitEntities(level, owner, start, end);

		for (int step = 1; step <= 5 && !this.isReturning(); step++) {
			BlockPos pos = BlockPos.containing(start.lerp(end, step / 5.0));
			if (!pos.equals(this.lastBlock)) {
				this.lastBlock = pos.immutable();
				this.handleBlock(level, owner, pos);
			}
		}

		this.faceAlong(motion);
		this.setPos(end);
		this.traveled += this.speed;
		if (this.traveled >= this.reach) {
			this.startReturn();
		}
	}

	private void handleBlock(ServerLevel level, ServerPlayer owner, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
			return;
		}
		if (this.mode == Mode.GARFIO) {
			// The claw bites the wall, sets itself there, and the rope starts hauling.
			this.anchor = Vec3.atCenterOf(pos).add(0.0, 0.3, 0.0);
			this.setPos(this.anchor);
			this.setDeltaMovement(Vec3.ZERO);
			this.hauling = HAUL_TICKS;
			level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.9F, 1.2F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
			return;
		}
		if (this.mode != Mode.HEAD) {
			// A wall is where a thrown weapon stops: it either comes back or it stays there.
			level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.4F);
			this.stopFlight(level);
			return;
		}
		if (this.canMine(level, owner, pos, state)) {
			this.mineBlock(level, owner, pos, state);
			if (++this.mined >= this.quota) {
				this.startReturn();
			}
		} else {
			level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.6F);
			this.startReturn();
		}
	}

	private boolean canMine(ServerLevel level, ServerPlayer owner, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || !level.mayInteract(owner, pos) || owner.blockActionRestricted(level, pos, owner.gameMode.getGameModeForPlayer())) {
			return false;
		}
		if (this.tool.getDestroySpeed(state) > 1.0F) {
			return this.tool.isCorrectToolForDrops(state) || !state.requiresCorrectToolForDrops();
		}
		// Soft stuff in the way (dirt, sand, leaves, glass) gets smashed through too.
		return hardness <= 1.0F;
	}

	private void mineBlock(ServerLevel level, ServerPlayer owner, BlockPos pos, BlockState state) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		boolean dropsLoot = !owner.isCreative() && (!state.requiresCorrectToolForDrops() || this.tool.isCorrectToolForDrops(state));
		if (!level.destroyBlock(pos, false, owner)) {
			return;
		}
		if (dropsLoot) {
			List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, owner, this.tool);
			for (ItemStack drop : drops) {
				Block.popResource(level, owner.blockPosition(), drop);
			}
			state.spawnAfterBreak(level, pos, this.tool, true);
		}

		ItemStack held = owner.getItemInHand(this.hand);
		if (!owner.isCreative() && held.is(this.tool.getItem())) {
			held.hurtAndBreak(1, owner, this.hand);
		}
	}

	/** A weapon's blow as if it were in the owner's hand: their bare attack and what the weapon adds to it. */
	public static float thrownDamage(ServerPlayer owner, ItemStack weapon) {
		var attack = owner.getAttribute(Attributes.ATTACK_DAMAGE);
		double damage = attack != null ? attack.getBaseValue() : 1.0;
		var modifiers = weapon.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers != null) {
			for (var entry : modifiers.modifiers()) {
				if (entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.slot().test(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
					&& entry.modifier().operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
					damage += entry.modifier().amount();
				}
			}
		}
		return (float) damage;
	}

	private void hitEntities(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 end) {
		AABB sweep = new AABB(start, end).inflate(0.4);
		for (Entity entity : level.getEntities(this, sweep, e -> e instanceof LivingEntity && e != owner && e.isAlive() && !this.hitEntities.contains(e.getId()))) {
			this.hitEntities.add(entity.getId());
			LivingEntity victim = (LivingEntity) entity;
			float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE);
			switch (this.mode) {
				case HEAD -> entity.hurtServer(level, this.damageSources().thrown(this, owner), Math.max(2.0F, damage));
				case WEAPON -> {
					// Thrown, it bites for two thirds of what it does in the hand. What it does in the hand is its
					// own: the hand it left is empty by now (or holds something else), so the owner's attack is no
					// measure of it - thrown daggers, axes and tridents used to land for 1.
					entity.hurtServer(level, this.damageSources().thrown(this, owner), Math.max(1.0F, thrownDamage(owner, this.tool) * 2.0F / 3.0F));
					this.stopFlight(level);
					return;
				}
				case GARFIO -> {
					// Something alive on the end of the rope comes to you instead.
					Vec3 towards = owner.position().subtract(victim.position()).normalize().scale(0.9 * this.pull);
					victim.setDeltaMovement(towards.x, Math.max(0.3, towards.y + 0.25), towards.z);
					victim.hurtMarked = true;
					entity.hurtServer(level, this.damageSources().thrown(this, owner), 2.0F);
					level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0F, 1.1F);
					this.stopFlight(level);
					return;
				}
				case SHIELD -> {
					this.shove(level, victim);
					if (this.bounces-- <= 0 || !this.bounceTo(level, owner, victim)) {
						this.stopFlight(level);
					}
					return;
				}
			}
		}
	}

	/** The shield does not cut: it shoves two blocks back and leaves whoever it meets stunned. */
	private void shove(ServerLevel level, LivingEntity victim) {
		Vec3 away = victim.position().subtract(this.position()).normalize();
		victim.setDeltaMovement(away.x * 0.55, 0.22, away.z * 0.55);
		victim.hurtMarked = true;
		int stun = 30;
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, stun, 5));
		victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stun, 2));
		victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, stun, 2));
		if (victim instanceof net.minecraft.world.entity.Mob mob) {
			mob.getNavigation().stop();
		}
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, victim.getX(), victim.getY(1.0), victim.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
	}

	/** Looks for the next one to shove, within eight blocks of the one just hit. */
	private boolean bounceTo(ServerLevel level, ServerPlayer owner, LivingEntity from) {
		LivingEntity next = null;
		double best = Double.MAX_VALUE;
		for (Entity candidate : level.getEntities(this, from.getBoundingBox().inflate(8.0),
			e -> e instanceof LivingEntity && e != owner && e.isAlive() && !this.hitEntities.contains(e.getId()))) {
			double distance = candidate.distanceToSqr(from);
			if (distance < best) {
				best = distance;
				next = (LivingEntity) candidate;
			}
		}
		if (next == null) {
			return false;
		}
		this.setDeltaMovement(next.getEyePosition().subtract(this.position()).normalize().scale(SPEED));
		return true;
	}

	/** End of the outward flight: back to the hand, or down on the ground where it stopped. */
	/**
	 * The winch: every tick the rope pulls the smith toward where the claw bit.
	 *
	 * <p>Toward, not at. The pull is set as velocity rather than added, so it beats gravity outright
	 * and the climb is steady instead of a jump that stalls. It also eases off near the anchor, which
	 * is what stops the rope slamming you into the wall you were trying to reach.
	 */
	private void haul(ServerLevel level, ServerPlayer owner) {
		Vec3 rope = this.anchor.subtract(owner.position());
		double distance = rope.length();
		this.hauling--;
		if (distance < ARRIVED || this.hauling <= 0) {
			this.hauling = 0;
			this.startReturn();
			return;
		}
		double speed = Math.min(1.35, 0.55 + distance * 0.055) * this.pull;
		// The last couple of blocks are taken gently, so arriving is not a crash.
		if (distance < ARRIVED * 2.0) {
			speed *= 0.55;
		}
		Vec3 haul = rope.normalize().scale(speed);
		// A floor on the climb: a rope that pulls level is a rope that drags you along the ground.
		owner.setDeltaMovement(haul.x, Math.max(haul.y, this.anchor.y > owner.getY() + 1.0 ? 0.38 : haul.y), haul.z);
		owner.hurtMarked = true;
		owner.resetFallDistance();
		if (this.hauling % 6 == 0) {
			level.playSound(null, this.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.35F, 1.6F);
		}
	}

	private void stopFlight(ServerLevel level) {
		if (this.returns) {
			this.startReturn();
			return;
		}
		// Only drop what actually left the hand. Dropping the hook here would be the same duplication
		// as handing it back in finish().
		if (this.carriesItem && !this.tool.isEmpty()) {
			// The weapon itself goes to the ground, and with it out of the entity finish() has nothing left
			// to give back. It used to drop a copy and keep the original, so finish() then put that in the
			// inventory too: one throw, two weapons.
			ItemStack dropped = this.tool;
			this.tool = ItemStack.EMPTY;
			level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
				level, this.getX(), this.getY(), this.getZ(), dropped));
		}
		this.finish(this.getOwner() instanceof ServerPlayer owner ? owner : null);
	}

	private void startReturn() {
		if (!this.isReturning()) {
			this.entityData.set(DATA_RETURNING, true);
			this.hitEntities.clear();
		}
	}

	private void finish(ServerPlayer owner) {
		if (owner != null && owner.isAlive()) {
			owner.getCooldowns().removeCooldown(owner.getCooldowns().getCooldownGroup(this.tool));
			this.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 0.7F);
		}
		this.sendHome(owner);
		this.discard();
	}

	/**
	 * A thrown weapon left the hand empty; this is it coming back. A hook never left it. The entity lets
	 * go of the stack before handing it over, so nothing can give it back a second time.
	 */
	private void sendHome(ServerPlayer owner) {
		if (!this.carriesItem || this.tool.isEmpty() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack weapon = this.tool;
		this.tool = ItemStack.EMPTY;
		if (this.ownerId == null) {
			// No thrower known at all: never from a real throw, and the ground still beats nowhere.
			level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, this.getX(), this.getY(), this.getZ(), weapon));
			return;
		}
		dev.forja.upgrade.ThrowReturns.giveBack(level.getServer(), owner, this.ownerId, weapon, this.hand);
	}

	/**
	 * Whatever takes it out of the world (its chunk unloading, the void, a command) the weapon in it
	 * still goes home. finish() has already emptied it by the time it discards itself, so this only acts
	 * when something else ends the flight.
	 */
	@Override
	public void onRemoval(RemovalReason reason) {
		this.sendHome(this.getOwner() instanceof ServerPlayer owner ? owner : null);
		super.onRemoval(reason);
	}

	/** It never goes through a portal: the copy on the far side would be a second weapon. */
	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}
}
