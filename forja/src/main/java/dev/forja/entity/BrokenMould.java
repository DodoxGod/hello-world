package dev.forja.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
import dev.forja.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Molde Roto: an automaton with a forge in its stomach that copies the weapon you hit it with.
 *
 * <p>The blade it carries is not a weapon, it is a blank — a bar of metal that is still running, held
 * up in both hands because it has not decided what it is yet. Hit it with something, and it puts the
 * blank into its own furnace and brings it back out <b>shaped like yours</b>: the recast takes thirty
 * ticks with the fire visibly up, and afterwards it hits for what your weapon hits for.
 *
 * <p>Which makes it the only fight in the mod where your own kit is the difficulty setting. Bring the
 * best thing you own and it copies the best thing you own. Bring something plain and it stays plain,
 * and that is a real option, because the payoff is the same either way: it dies holding the shape of
 * what it copied, and drops it as an <b>engraved template</b> — the permanent pattern for that part,
 * which is the one thing in the mod worth more than the metal it was cut from.
 */
public class BrokenMould extends Monster implements GeoEntity {
	public static final double HEALTH = 54.0;

	/** What it hits for with nothing copied: a bar of hot metal, swung badly. */
	public static final double BASE_DAMAGE = 7.0;
	/** And the most it will ever copy, so a legendary weapon does not simply end the fight. */
	public static final double COPY_CEILING = 22.0;

	/** Thirty ticks, where the animation swaps the blade inside the furnace. */
	public static final int RECAST_WINDUP = 30;
	/** It will not recast again for this long, so one fight is at most a handful of shapes. */
	public static final int RECAST_COOLDOWN = 160;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation RECAST = RawAnimation.begin().thenPlay("recast");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();

	private int recastCooldown;

	/** The shape it is currently wearing, and the pattern that shape drops as. */
	private @Nullable PartType copied;
	private double copiedDamage;

	public BrokenMould(EntityType<? extends BrokenMould> type, Level level) {
		super(type, level);
		this.xpReward = 25;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, BASE_DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, 0.24)
			.add(Attributes.FOLLOW_RANGE, 26.0)
			.add(Attributes.ARMOR, 6.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 14.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker) {
			this.consider(level, attacker.getMainHandItem());
		}
		return hurt;
	}

	/** Looks at what just hit it, and starts a recast if that is a better shape than the one it has. */
	public void consider(ServerLevel level, ItemStack weapon) {
		if (this.recastCooldown > 0 || this.windup.charging()) {
			return;
		}
		PartType shape = headOf(weapon);
		if (shape == null || shape == this.copied) {
			return;
		}
		// A copy is worth more than the original in its hands: it is a whole mob built round one
		// weapon, and coming out of the furnace with exactly your numbers made the recast feel like
		// nothing happened.
		double damage = Math.min(COPY_CEILING, damageOf(weapon) * 1.4);
		if (damage <= 0.0) {
			return;
		}
		this.recast(level, shape, damage);
	}

	/** The blank goes into the furnace and comes back out as a copy. */
	public void recast(ServerLevel level, PartType shape, double damage) {
		this.recastCooldown = RECAST_COOLDOWN;
		this.triggerAnim("molde", "recast");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.HOSTILE, 1.8F, 0.7F);
		this.windup.start(RECAST_WINDUP, (world, left, total) -> {
			// The fire in its belly coming up. This is a wind-up with no blow at the end of it: what
			// it buys you is a free half-second, and what it costs you is the rest of the fight.
			float progress = 1.0F - left / (float) total;
			net.minecraft.world.phys.Vec3 hearth = this.position()
				.add(new net.minecraft.world.phys.Vec3(0.0, 0.75, -0.45).yRot(-this.getYRot() * ((float) Math.PI / 180.0F)));
			world.sendParticles(dev.forja.registry.ModParticles.CHISPA, hearth.x, hearth.y, hearth.z,
				1 + (int) (progress * 5), 0.18, 0.12, 0.18, 0.02 + progress * 0.08);
			if (left % 5 == 0) {
				net.minecraft.world.phys.Vec3 stack = this.position()
					.add(new net.minecraft.world.phys.Vec3(0.4, 2.7, 0.2).yRot(-this.getYRot() * ((float) Math.PI / 180.0F)));
				world.sendParticles(dev.forja.registry.ModParticles.CENIZA, stack.x, stack.y, stack.z, 2, 0.08, 0.0, 0.08, 0.12);
			}
		}, world -> this.take(world, shape, damage));
	}

	/** The copy coming out: from here it fights with your numbers. */
	private void take(ServerLevel level, PartType shape, double damage) {
		this.copied = shape;
		this.copiedDamage = damage;
		AttributeInstance attack = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.setBaseValue(Math.max(BASE_DAMAGE, damage));
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_USE, SoundSource.HOSTILE, 1.8F, 0.8F);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY(1.4), this.getZ(), 40, 0.4, 0.6, 0.4, 0.4);
		if (this.getTarget() instanceof ServerPlayer player) {
			player.sendSystemMessage(Component.translatable("gui.forja.molde_copia", shape.displayName()).withColor(0xFF7A1E));
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.recastCooldown > 0) {
			this.recastCooldown--;
		}
		// The furnace, always lit, and the stack always going.
		if (this.tickCount % 9 == 0) {
			net.minecraft.world.phys.Vec3 hearth = this.position()
				.add(new net.minecraft.world.phys.Vec3(0.0, 0.75, -0.45).yRot(-this.getYRot() * ((float) Math.PI / 180.0F)));
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA, hearth.x, hearth.y, hearth.z, 1, 0.14, 0.08, 0.14, 0.01);
			net.minecraft.world.phys.Vec3 stack = this.position()
				.add(new net.minecraft.world.phys.Vec3(0.4, 2.7, 0.2).yRot(-this.getYRot() * ((float) Math.PI / 180.0F)));
			level.sendParticles(dev.forja.registry.ModParticles.CENIZA, stack.x, stack.y, stack.z, 1, 0.05, 0.0, 0.05, 0.06);
		}
		if (this.windup.charging()) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.4, 1.0, 0.4));
			this.windup.tick(level);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 1.8F, 0.5F);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY(1.0), this.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
		// The shape it died wearing, as a template. Nothing if it never copied anything, which is the
		// whole reason hitting it with your best thing is a decision rather than a formality.
		if (this.copied != null) {
			ItemStack template = new ItemStack(dev.forja.registry.ModItems.PLANTILLA);
			dev.forja.item.TemplateItem.engrave(template, this.copied);
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.8), this.getZ(), template));
		}
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(0.5), this.getZ(),
			new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 1 + this.random.nextInt(3))));
	}

	/** The shape it is wearing right now, for the test and for the drop. */
	public @Nullable PartType copied() {
		return this.copied;
	}

	public double copiedDamage() {
		return this.copiedDamage;
	}

	/**
	 * The head shape of a forged weapon: the blade, the axe head, the spike, whatever the thing is
	 * actually for. Anything not forged in this mod has no shape it can take, which is the answer to
	 * "what if I hit it with a vanilla sword" — nothing happens, and it stays a bar of hot metal.
	 */
	public static @Nullable PartType headOf(ItemStack weapon) {
		ForgedParts parts = weapon.get(ModComponents.PARTS);
		if (parts == null) {
			return null;
		}
		int slot = parts.type().slotOf(PartType.Role.HEAD);
		return slot < 0 ? null : parts.type().slots.get(slot);
	}

	/** What a stack actually hits for, read off the finished item rather than recomputed. */
	public static double damageOf(ItemStack weapon) {
		ItemAttributeModifiers modifiers = weapon.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers == null) {
			return 0.0;
		}
		double total = 0.0;
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().is(Attributes.ATTACK_DAMAGE.unwrapKey().orElseThrow())
				&& entry.modifier().operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
				total += entry.modifier().amount();
			}
		}
		return total;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.BLASTFURNACE_FIRE_CRACKLE;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.DEEPSLATE_HIT;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<BrokenMould>("molde", test ->
			test.setAndContinue(test.isMoving() ? WALK : IDLE)
		).triggerableAnim("recast", RECAST));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
