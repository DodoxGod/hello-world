package dev.forja.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Herrumbre: a flake of rust that got up, and eats the armour off you rather than the life out of you.
 *
 * <p>It is the answer to a hole the mod had. Every other monster in Forja threatens your health, and
 * health comes back on its own — so a fight you survive has cost you nothing, and a player in good gear
 * can walk through most of the world without paying attention. This one costs you **durability**, which
 * in a mod where a piece carries its own materials, its upgrades and its Maestría is the expensive
 * currency. It bites for almost nothing and takes a chunk out of whatever it bit.
 *
 * <p>Two things keep it from being merely annoying. It comes in numbers, so ignoring one is fine and
 * ignoring six is not; and it <b>prefers metal</b> — it goes for armour first and only worries the
 * player underneath when there is none left, which means the obvious counter is to take the good plate
 * off and fight it in your shirt.
 */
public class RustSwarm extends Monster implements GeoEntity {
	public static final double HEALTH = 8.0;

	/** What a bite costs in health, which is almost nothing on purpose. */
	public static final float BITE = 1.0F;

	/** And what it costs the piece it bit, which is the whole point of the thing. */
	public static final int WEAR = 24;

	/** How many turn up together. */
	public static final int PACK_MIN = 2;
	public static final int PACK_MAX = 5;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation BITE_ANIM = RawAnimation.begin().thenPlay("bite");

	/** The colour of what it leaves behind on a plate it has been at. */
	private static final net.minecraft.core.particles.DustParticleOptions RUST =
		new net.minecraft.core.particles.DustParticleOptions(0x9A5428, 0.8F);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public RustSwarm(EntityType<? extends RustSwarm> type, Level level) {
		super(type, level);
		this.xpReward = 3;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, BITE)
			// Quick, because something this small has to reach you before you step back.
			.add(Attributes.MOVEMENT_SPEED, 0.32)
			.add(Attributes.FOLLOW_RANGE, 20.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		this.triggerAnim("herrumbre", "bite");
		boolean hurt = super.doHurtTarget(level, target);
		if (hurt && target instanceof LivingEntity victim) {
			this.gnaw(level, victim);
		}
		return hurt;
	}

	/**
	 * Takes a bite out of the best piece of metal it can find on whoever it just hit.
	 *
	 * <p>Worst first. Going for the strongest piece would mean the only sensible answer is never to
	 * wear your good plate, which is not an interesting choice; going for the piece nearest to
	 * breaking means it is eating the thing you were going to have to repair anyway, and the pressure
	 * is on your repair habits rather than on your loadout.
	 */
	private void gnaw(ServerLevel level, LivingEntity victim) {
		EquipmentSlot worst = null;
		float worstShare = Float.MAX_VALUE;
		for (EquipmentSlot slot : new EquipmentSlot[] {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
		}) {
			ItemStack worn = victim.getItemBySlot(slot);
			if (worn.isEmpty() || !worn.isDamageableItem() || worn.isBroken()) {
				continue;
			}
			float share = 1.0F - worn.getDamageValue() / (float) worn.getMaxDamage();
			if (share < worstShare) {
				worstShare = share;
				worst = slot;
			}
		}
		if (worst == null) {
			// Nothing left to eat: it has to make do with the person, which it is bad at.
			return;
		}
		ItemStack worn = victim.getItemBySlot(worst);
		worn.hurtAndBreak(WEAR, victim, worst);
		level.sendParticles(RUST, victim.getX(), victim.getY(victim.getBbHeight() * 0.6), victim.getZ(),
			10, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
			SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 0.5F, 1.6F);
	}

	@Override
	public void tick() {
		super.tick();
		// It sheds what it has eaten, which is how you find a nest of them before they find you.
		if (this.level() instanceof ServerLevel level && this.tickCount % 12 == 0
			&& this.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
			level.sendParticles(RUST, this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.12, 0.02, 0.12, 0.0);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		// What it had eaten and not finished digesting.
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.2), this.getZ(),
			new ItemStack(Items.IRON_NUGGET, 1 + this.random.nextInt(2))));
		level.sendParticles(RUST, this.getX(), this.getY(0.3), this.getZ(), 18, 0.25, 0.2, 0.25, 0.05);
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(0.3), this.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<RustSwarm>("herrumbre", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("bite", BITE_ANIM));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
