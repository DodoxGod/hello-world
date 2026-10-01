package dev.forja.entity;

import java.util.List;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.forge.Mastery;
import dev.forja.material.ForgeMaterial;
import dev.forja.registry.ModEntities;
import dev.forja.registry.ModItems;
import dev.forja.world.Legends;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * El Herrero Caído: what is left of the smith who built the fallen forge, still working. He is woken
 * by an offering on his own dead forge and he fights in three stages, each one asking for something
 * different from the player.
 *
 * <ul>
 *   <li>Under three quarters he calls up the apprentices that died with him.
 *   <li>At half he goes back to the forge: nothing touches him while he reforges, and the only way
 *       through it is to put out the embers he lights around himself.
 *   <li>Under a quarter he brings the sky down, the same shower that buried the forge.
 * </ul>
 *
 * <p>What he leaves behind is his heart, and one piece of what he was carrying.
 */
public class FallenSmith extends Monster implements GeoEntity {
	/** How much health he has: this is not a mob you meet by accident. */
	public static final double HEALTH = 320.0;

	/** Ticks the reforge stage keeps him out of reach. */
	public static final int REFORGE_TICKS = 160;

	/** How many embers have to be put out to break the reforge. */
	public static final int EMBERS = 3;

	/**
	 * The anvils he stands up when his last quarter begins.
	 *
	 * <p>Six of them, and they are not there to fight you — a walking anvil has no target goal at all.
	 * They are there to <b>mend him</b>, which turns his last quarter from "hit him faster" into a
	 * question about what you break first. The anvils are soft, slow and scattered; he is hard, quick
	 * and in your face. Every second spent on one of them is a second he is not being hurt in, and
	 * leaving them up means his health bar stops going down.
	 */
	public static final int FINAL_ANVILS = 6;

	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("slam");
	private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("roar");
	private static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("strike");
	private static final RawAnimation HOOK = RawAnimation.begin().thenPlay("hook");
	/** His plain blow, out of its wind-up (see MobMoves); the backhand is STRIKE, a special of its own. */
	private static final RawAnimation SWING = RawAnimation.begin().thenPlay("swing");
	private static final RawAnimation WINDUP = RawAnimation.begin().thenPlayAndHold("windup");
	private static final RawAnimation STAGGER = RawAnimation.begin().thenLoop("stagger");
	private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
	private static final RawAnimation REFORGE = RawAnimation.begin().thenLoop("reforge");
	/** A heavy run of his own, for when he is ever sprinting (bosses do not, today: ai.MobSprint.runs). */
	private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
	/** The hammer on the floor as his apprentices come up round him. */
	private static final RawAnimation CALL = RawAnimation.begin().thenPlay("call");
	/** The hammer raised to the sky for STARFALL_WINDUP, and pulled down as the stars fall. */
	private static final RawAnimation STARCALL = RawAnimation.begin().thenPlay("starcall");
	/** La forja reclama: the hammer held out over the floor while everything round him is dragged in (RECLAIM_WINDUP). */
	private static final RawAnimation RECLAIM = RawAnimation.begin().thenPlay("reclaim");
	// The fire runs on its own controller, so it can burn however it likes while he does something else.
	private static final RawAnimation FIRE_CALM = RawAnimation.begin().thenLoop("fire_calm");
	private static final RawAnimation FIRE_RAGE = RawAnimation.begin().thenLoop("fire_rage");
	private static final RawAnimation FIRE_FLASH = RawAnimation.begin().thenPlay("fire_flash");
	private static final RawAnimation FIRE_FLASH_HOT = RawAnimation.begin().thenPlay("fire_flash_hot");

	/**
	 * Whether the forge in his chest is running violet. The client has to know, because the fire is drawn
	 * there, and the only thing it can read off him by itself is his health.
	 */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_RAGING =
		net.minecraft.network.syncher.SynchedEntityData.defineId(FallenSmith.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

	/** How long a heavy blow keeps the forge violet after it lands. */
	public static final int RAGE_TICKS = 70;

	/** The shockwave: how often he can drop the hammer, how far the ring runs and what it costs you. */
	public static final int WAVE_COOLDOWN = 160;
	public static final int WAVE_TICKS = 20;
	public static final double WAVE_REACH = 9.0;
	public static final float WAVE_DAMAGE = 8.0F;
	/** How far off the floor your feet have to be for the ring to pass under them: most of a jump, not a hop. */
	public static final double WAVE_CLEARANCE = 0.5;

	/** The ring that leaves him as his last quarter opens: wider than the shockwave, slower, and harmless. */
	public static final double PHASE_RING_REACH = 12.0;
	public static final int PHASE_RING_TICKS = 26;

	/**
	 * How long each blow hangs in the air before it lands, read off the animation that draws it.
	 *
	 * <p>These are not numbers that felt about right. {@code slam} has the hammer up by twenty-four
	 * ticks, holds it over his head until fifty and brings it down at fifty-four; {@code hook} cocks
	 * the dead arm and throws at eight. Matching them is what makes the move readable — the hammer is
	 * up, so move.
	 */
	// Three times what it used to be. The hammer is enormous and it was coming down like a stick: at
	// eighteen ticks the weight of it was in the model and nowhere else. The animation has to land on
	// the same tick — the "slam" keyframes in tools/generate_assets.py say 2.7 s for exactly this reason.
	// For a day they did not: this was tripled, the animation was left at 0.9 s, and the ring left the
	// floor nearly two seconds after the hammer had hit it. The gametest checks the two agree now.
	public static final int WAVE_WINDUP = 54;
	public static final int HOOK_WINDUP = 8;
	public static final int STRIKE_WINDUP = 10;
	/**
	 * The star shower of his last quarter: he raises the hammer to the sky over the spot it will fall on,
	 * and it comes down at twenty ticks ("starcall" in tools/generate_assets.py pulls the hammer down on
	 * that tick). It used to hit on the tick it was announced, which is no warning at all.
	 */
	public static final int STARFALL_WINDUP = 20;
	/** How far round the marked spot the shower catches you. */
	public static final double STARFALL_RADIUS = 3.0;

	/**
	 * La forja reclama: what he does when the world's own creatures gang up on him (Andy, 2026-09-29: "si
	 * varios golems lo atacan, que haga una animación, atraiga a todos los mobs y los mate sin que suelten
	 * nada").
	 *
	 * <p>It goes off when, within the last {@link #RECLAIM_WINDOW} ticks, either {@link #RECLAIM_CROWD}
	 * different creatures that are nobody's have tried to hurt him, or the heavy ones among them (anything
	 * with {@link #RECLAIM_HEAVY_HEALTH} health or more: an iron golem, a ravager, a warden, a wither) have
	 * between them tried to take {@link #RECLAIM_HEAVY_SHARE} of his health, counted after
	 * {@code jefeDanoAjeno}: a warden gets there in three blows and a golem in about seven. A player, a
	 * player's pet (anything tamed or owned: wolves, cats, parrots, horses), his apprentices and the rest
	 * of Forja's own side never count and are never taken, so a player with a dog never sees it.
	 *
	 * <p>Then: {@link #RECLAIM_WINDUP} ticks with the hammer out over the floor, a ring of violet closing
	 * in on him and the sound of it gathering; for the last {@link #RECLAIM_PULL} of them everything that
	 * can be taken within {@link #RECLAIM_RADIUS} blocks is dragged towards him; and on the last tick it is
	 * gone. Discarded, not killed: no loot, no experience, no death. Not again for {@link #RECLAIM_COOLDOWN}
	 * ticks.
	 */
	public static final int RECLAIM_WINDUP = 30;
	public static final int RECLAIM_PULL = 16;
	public static final int RECLAIM_COOLDOWN = 800;
	public static final double RECLAIM_RADIUS = 12.0;
	public static final int RECLAIM_WINDOW = 200;
	public static final int RECLAIM_CROWD = 3;
	public static final double RECLAIM_HEAVY_HEALTH = 100.0;
	public static final float RECLAIM_HEAVY_SHARE = 0.10F;

	/** The backhand: close range, fast, and the one he punishes you with for standing next to him. */
	public static final int STRIKE_COOLDOWN = 90;
	public static final double STRIKE_REACH = 4.5;
	public static final float STRIKE_DAMAGE = 7.0F;

	/** The hook: how often he can throw the claw, how far it reaches and what the pull costs you. */
	public static final int HOOK_COOLDOWN = 220;
	public static final double HOOK_MIN = 5.0;
	public static final double HOOK_MAX = 16.0;
	public static final float HOOK_DAMAGE = 4.0F;

	/** The violet the forge burns with once he stops holding back, matching the model's hot plate. */
	private static final net.minecraft.core.particles.DustParticleOptions VIOLET =
		new net.minecraft.core.particles.DustParticleOptions(0xC460FF, 1.6F);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final ServerBossEvent bar = new ServerBossEvent(
		java.util.UUID.randomUUID(), Component.translatable("entity.forja.herrero_caido"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6
	);

	private int reforging;
	private int embersLeft;
	private int raging;
	private int waveCooldown = 60;
	private int hookCooldown = 120;
	private int strikeCooldown = 40;
	private final dev.forja.entity.ai.Windup windup = new dev.forja.entity.ai.Windup();
	private int wave;
	private final java.util.Set<java.util.UUID> waveHit = new java.util.HashSet<>();
	/** Where the hammer came down: the ring runs out from there, not from wherever he has walked to since. */
	private Vec3 waveOrigin = Vec3.ZERO;
	private boolean calledHelp;
	private boolean broughtSky;
	/** Ticks until the stars he has called come down, and where; 0 when none are coming. */
	private int starfall;
	private Vec3 starfallAt = Vec3.ZERO;
	/** Ticks left of La forja reclama (0 when it is not under way), and how long until it can come again. */
	private int reclaim;
	private int reclaimCooldown;

	// ---- the graveyard's fight (docs/HERRERO_DIMENSION.md, section 3)

	/** He comes down out of the sky: how long the fall takes, from how high, and the moment on the ground after. */
	public static final int FALL_TICKS = 40;
	public static final double FALL_HEIGHT = 120.0;
	public static final int SETTLE_TICKS = 20;
	/** The pause of a change of phase, in which he cannot be hurt while his apprentices come up. */
	public static final int PHASE_GUARD = 60;
	/** Apprentices come up out of the ground this deep, over this long, each ring this much after the one inside it. */
	public static final double RISE_DEPTH = 2.2;
	public static final int RISE_TICKS = 40;
	public static final int RISE_STAGGER = 8;
	/** The Reforjado estelar: the forge fires, three and one more a player after the first, six at most. */
	public static final int EMBERS_BASE = 3;
	public static final int EMBERS_MOST = 6;
	/** Put out, it leaves him stunned: this long, taking this much more. */
	public static final int STUN_TICKS = 100;
	public static final float STUN_DAMAGE = 1.5F;

	private int falling;
	private int settling;
	private int phaseGuard;
	/** How many times the apprentices have been called: at two thirds and at one third. */
	private int wavesCalled;
	/** The Reforjado estelar: 0 not yet, 1 burning, 2 put out. */
	private int starReforge;
	private final java.util.List<net.minecraft.core.BlockPos> embersAt = new java.util.ArrayList<>();
	private int stunned;
	private int aegis;
	/** Whether he was held still before a pause of his own (a test's NoAI): the pause gives it back as it was. */
	private boolean heldStill;
	private final java.util.List<Riser> risers = new java.util.ArrayList<>();

	/** An apprentice on its way up out of the ground. Not saved: see Apprentices, which finishes it on a reload. */
	private static final class Riser {
		final java.util.UUID id;
		final double floor;
		int delay;
		int ticks;

		Riser(java.util.UUID id, double floor, int delay) {
			this.id = id;
			this.floor = floor;
			this.delay = delay;
		}
	}

	/** One try at hurting him by something that can be reclaimed, for the RECLAIM_WINDOW count. */
	private record Blow(java.util.UUID who, int at, float damage, boolean heavy) {
	}

	private final java.util.ArrayDeque<Blow> blows = new java.util.ArrayDeque<>();

	public FallenSmith(EntityType<? extends FallenSmith> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 250;
		this.bar.setDarkenScreen(true);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_RAGING, false);
		builder.define(DATA_REFORGING, false);
	}

	/**
	 * Whether he is at the forge reforging, as the client sees it: he kneels at it and works the hammer. The
	 * controller used to read the server's own count here, which the client never has, so it never did.
	 */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_REFORGING =
		net.minecraft.network.syncher.SynchedEntityData.defineId(FallenSmith.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

	/** The forge goes violet in his last quarter, while he is reforging, and just after a heavy blow. */
	public boolean isRaging() {
		return this.entityData.get(DATA_RAGING);
	}

	/** Lights the forge violet for a while: what the heavy moves call when they go off. */
	public void lightForge(int ticks) {
		this.raging = Math.max(this.raging, ticks);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, 12.0)
			.add(Attributes.ARMOR, 14.0)
			.add(Attributes.ARMOR_TOUGHNESS, 8.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.STEP_HEIGHT, 1.2);
	}

	/** Wakes him up with his own gear on, wherever the offering was made. */
	public static FallenSmith summon(ServerLevel level, net.minecraft.core.BlockPos pos) {
		FallenSmith smith = ModEntities.HERRERO_CAIDO.create(level, EntitySpawnReason.EVENT);
		if (smith == null) {
			throw new IllegalStateException("the fallen smith could not be created");
		}
		smith.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		smith.dress(level);
		level.addFreshEntity(smith);
		level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0F, 0.5F);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 80, 1.0, 1.5, 1.0, 0.05);
		return smith;
	}

	/**
	 * He falls out of the sky onto the middle of the arena, like a star (docs/HERRERO_DIMENSION.md, 3.2):
	 * FALL_TICKS from FALL_HEIGHT up, with a trail, and nothing touches him until he is on his feet.
	 */
	public static FallenSmith fallFromSky(ServerLevel level) {
		FallenSmith smith = ModEntities.HERRERO_CAIDO.create(level, EntitySpawnReason.EVENT);
		if (smith == null) {
			throw new IllegalStateException("the fallen smith could not be created");
		}
		smith.snapTo(0.5, dev.forja.world.StarYard.SURFACE + 1.0 + FALL_HEIGHT, 0.5, 180.0F, 0.0F);
		smith.dress(level);
		smith.falling = FALL_TICKS;
		smith.setNoAi(true);
		smith.setNoGravity(true);
		level.addFreshEntity(smith);
		for (ServerPlayer player : level.players()) {
			level.playSound(null, player.getX(), player.getY() + 20.0, player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5F, 0.6F);
			player.sendSystemMessage(Component.translatable("gui.forja.pelea.cae").withColor(0xC460FF));
		}
		return smith;
	}

	/** Whether he fights in the Cementerio entre Estrellas, where the dimension does its part. */
	public boolean inYard() {
		return this.yardForTests || this.level().dimension() == dev.forja.world.StarYard.LEVEL;
	}

	/**
	 * The gametest server has no datapack dimensions, so a test that wants the graveyard's fight asks for it
	 * here: the arena's braseros and fires are at the graveyard's own coordinates round (0, 81, 0).
	 */
	public boolean yardForTests;

	/** Coming down, getting up, or between phases: the sky waits, and nothing touches him. */
	public boolean busy() {
		return this.falling > 0 || this.settling > 0 || this.phaseGuard > 0;
	}

	public boolean isFalling() {
		return this.falling > 0 || this.settling > 0;
	}

	public boolean isStunned() {
		return this.stunned > 0;
	}

	public int wavesCalled() {
		return this.wavesCalled;
	}

	/** One tick of the fall, and the landing. */
	private void fall(ServerLevel level) {
		if (this.falling > 0) {
			this.falling--;
			double share = this.falling / (double) FALL_TICKS;
			double y = dev.forja.world.StarYard.SURFACE + 1.0 + FALL_HEIGHT * share * share;
			this.setPos(0.5, y, 0.5);
			this.setDeltaMovement(Vec3.ZERO);
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(1.0), this.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(0.5), this.getZ(), 14, 0.5, 0.8, 0.5, 0.04);
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY(1.0), this.getZ(), 10, 0.4, 0.6, 0.4, 0.3);
			level.sendParticles(ParticleTypes.FIREWORK, this.getX(), this.getY() + 4.0, this.getZ(), 6, 0.3, 2.5, 0.3, 0.01);
			if (this.falling == 0) {
				this.land(level);
			}
			return;
		}
		if (--this.settling == 0) {
			this.setNoAi(false);
			this.triggerAnim("boss", "roar");
		}
	}

	private void land(ServerLevel level) {
		this.setNoGravity(false);
		this.settling = SETTLE_TICKS;
		Shockwave.burst(level, this.position(), 14.0, 18, Shockwave.VIOLET);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0F, 0.5F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 5.0F, 0.4F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.5, this.getZ(), 80, 1.5, 0.3, 1.5, 0.25);
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA, this.getX(), this.getY() + 0.5, this.getZ(), 80, 3.0, 0.5, 3.0, 0.05);
		this.lightForge(RAGE_TICKS * 2);
	}

	/** The Égida (docs 3.6, the Escudo): a shield of light that takes this much for this long. */
	public void aegis(float amount, int ticks) {
		var max = this.getAttribute(Attributes.MAX_ABSORPTION);
		if (max != null) {
			max.removeModifier(AEGIS);
			max.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(AEGIS, amount,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		}
		this.setAbsorptionAmount(amount);
		this.aegis = ticks;
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(1.0), this.getZ(), 50, 1.0, 1.4, 1.0, 0.05);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0F, 0.7F);
		}
	}

	private static final net.minecraft.resources.Identifier AEGIS = dev.forja.Forja.id("egida");

	/** His own work: a damascus flail and obsidian steel plate, all of it at full Maestria. */
	private void dress(ServerLevel level) {
		ItemStack flail = Assembler.create(ForgeType.MANGUAL, List.of(ForgeMaterial.DAMASCO, ForgeMaterial.DAMASCO, ForgeMaterial.OBSIDIACERO), level.registryAccess());
		Mastery.setLevel(flail, Mastery.MAX_LEVEL, level.registryAccess());
		this.setItemSlot(EquipmentSlot.MAINHAND, flail);
		this.setItemSlot(EquipmentSlot.HEAD, armour(level, ForgeType.CASCO));
		this.setItemSlot(EquipmentSlot.CHEST, armour(level, ForgeType.PECHERA));
		this.setItemSlot(EquipmentSlot.LEGS, armour(level, ForgeType.GREBAS));
		this.setItemSlot(EquipmentSlot.FEET, armour(level, ForgeType.BOTAS));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0.0F);
		}
	}

	private ItemStack armour(ServerLevel level, ForgeType type) {
		return Assembler.create(type, List.of(ForgeMaterial.OBSIDIACERO, ForgeMaterial.ESCAMA), level.registryAccess());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		// Whoever hurts him is answered in hurtServer rather than by a HurtByTargetGoal, because the
		// vanilla one never lets go: the first golem to hit him kept him forever, player or no player.
		// The rules are on FOCUS_TICKS. While something other than a player is still hitting him, he does
		// not go looking for one.
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
			(player, level) -> !this.heldByFoe()));
	}

	/**
	 * How long a blow holds his attention, in ticks. Andy, 2026-09-28: "el jefe no se defiende si algo lo
	 * ataca" — a warden stood next to him could kill him while he walked after the player.
	 *
	 * <p>Who he fights, in order:
	 * <ol>
	 *   <li>A player who hits him (with anything: a blade, an arrow) gets him at once, whatever he was
	 *       doing. Players are what he is for.
	 *   <li>Anything else that hits him — a warden, a golem, somebody's wolf — gets him too, unless he is
	 *       busy with a player who has hit him within this many ticks. A player fighting him keeps him;
	 *       a player standing by and letting something else do it does not.
	 *   <li>Something that is not a player holds him only while it keeps hitting him. Once it has not
	 *       for this long, he turns to the nearest player he can see, if there is one — a golem that
	 *       wandered off does not keep him from the player in front of him.
	 * </ol>
	 */
	public static final int FOCUS_TICKS = 100;

	/** The last thing that hurt him and when (his own tick count): what his apprentices answer. */
	private LivingEntity lastAttacker;
	private int lastAttackedAt = Integer.MIN_VALUE / 2;
	/** The last player who hit him, and the last thing that was not a player. */
	private Player striker;
	private int struckAt = Integer.MIN_VALUE / 2;
	private LivingEntity foe;
	private int foeStruckAt = Integer.MIN_VALUE / 2;

	public LivingEntity lastAttacker() {
		return this.lastAttacker != null && this.lastAttacker.isAlive() ? this.lastAttacker : null;
	}

	public int lastAttackedAt() {
		return this.lastAttackedAt;
	}

	/** Someone went for him: rules 1 and 2 of {@link #FOCUS_TICKS}. */
	private void answer(LivingEntity attacker) {
		if (attacker == this || !attacker.isAlive() || !this.canAttack(attacker)) {
			return;
		}
		this.lastAttacker = attacker;
		this.lastAttackedAt = this.tickCount;
		if (attacker instanceof Player player) {
			this.striker = player;
			this.struckAt = this.tickCount;
			this.setTarget(player);
			return;
		}
		this.foe = attacker;
		this.foeStruckAt = this.tickCount;
		if (!this.busyWithPlayer()) {
			this.setTarget(attacker);
		}
	}

	/** Whether he is fighting a player who has hit him lately, which nothing else can take him off. */
	private boolean busyWithPlayer() {
		return this.getTarget() instanceof Player player && player == this.striker && player.isAlive()
			&& this.canAttack(player) && this.tickCount - this.struckAt <= FOCUS_TICKS;
	}

	/** Whether what he is fighting is not a player and is still hitting him: rule 3 of {@link #FOCUS_TICKS}. */
	public boolean heldByFoe() {
		LivingEntity target = this.getTarget();
		return target != null && !(target instanceof Player) && target == this.foe && target.isAlive()
			&& this.tickCount - this.foeStruckAt <= FOCUS_TICKS;
	}

	/** Twice a second: drops a dead target, and lets go of a foe that has stopped hitting him (rule 3). */
	private void reconsider(ServerLevel level) {
		LivingEntity target = this.getTarget();
		// In the Guadaña's ash storm he sees no further than anyone else: sixteen blocks.
		if (target != null && this.inYard() && dev.forja.world.StarFight.storming(level) && target.distanceTo(this) > 16.0F) {
			this.setTarget(null);
			return;
		}
		if (target == null || target instanceof Player) {
			return;
		}
		if (!target.isAlive()) {
			this.setTarget(null);
			return;
		}
		if (this.heldByFoe()) {
			return;
		}
		Player player = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), this.getAttributeValue(Attributes.FOLLOW_RANGE),
			seen -> seen instanceof Player candidate && this.canAttack(candidate) && this.hasLineOfSight(candidate));
		if (player != null) {
			this.setTarget(player);
		}
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return effect.is(MobEffects.WITHER) ? false : super.canBeAffected(effect);
	}

	/**
	 * While he is reforging, nothing gets through: the embers are the way in.
	 *
	 * <p>Whoever swung is answered either way (see {@link #FOCUS_TICKS}), and a blow from anything that
	 * is not a player, or a player's pet, only does {@link dev.forja.combat.CombatConfig#jefeDanoAjeno}
	 * of itself — so a warden parked beside him is a fight he joins, not a way of skipping him.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		// One of ours is kept off him by the truce further down; it must not turn him round either.
		if (source.getEntity() instanceof LivingEntity attacker && !dev.forja.world.Truce.blocks(this, source)) {
			this.answer(attacker);
			// Counted as tried, not as landed: three golems swinging on the same tick are three, even if
			// the hurt cooldown lets only one of them through.
			if (reclaimable(attacker)) {
				this.forgetOldBlows();
				this.blows.addLast(new Blow(attacker.getUUID(), this.tickCount,
					damage * dev.forja.difficulty.Bosses.othersShare(source), attacker.getMaxHealth() >= RECLAIM_HEAVY_HEALTH));
			}
		}
		boolean guarded = this.reforging > 0 || this.falling > 0 || this.settling > 0 || this.phaseGuard > 0 || this.starReforge == 1;
		if (guarded && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 1.8F);
			return false;
		}
		// Stunned when his forge is put out: every blow lands half as hard again.
		float stun = this.stunned > 0 ? STUN_DAMAGE : 1.0F;
		return super.hurtServer(level, source, damage * stun * dev.forja.difficulty.Bosses.othersShare(source));
	}

	@Override
	public void tick() {
		// Nobody left in the graveyard: the fight stands exactly where it was, him included.
		if (this.level() instanceof ServerLevel yard && yard.dimension() == dev.forja.world.StarYard.LEVEL
			&& dev.forja.world.StarFight.fighters(yard).isEmpty()) {
			return;
		}
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		if (this.raging > 0) {
			this.raging--;
		}
		boolean hot = this.reforging > 0 || this.raging > 0 || this.getHealth() / this.getMaxHealth() <= 0.25F;
		if (this.isReforging() != this.entityData.get(DATA_REFORGING)) {
			this.entityData.set(DATA_REFORGING, this.isReforging());
		}
		if (hot != this.entityData.get(DATA_RAGING)) {
			this.entityData.set(DATA_RAGING, hot);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, hot ? 0.5F : 1.4F);
		}
		this.forgeBreathes(level, hot);
		if (this.tickCount % 20 == 0) {
			this.watchers(level);
		}
		if (this.tickCount % 10 == 0) {
			this.reconsider(level);
		}
		// He has been burning for a long time and it settles on everything around him.
		if (this.tickCount % 6 == 0) {
			level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
				this.getX(), this.getY(1.4), this.getZ(), 2, 0.6, 0.5, 0.6, 0.01);
		}
		this.rise(level);
		if (this.aegis > 0 && --this.aegis == 0) {
			var max = this.getAttribute(Attributes.MAX_ABSORPTION);
			if (max != null) {
				max.removeModifier(AEGIS);
			}
			this.setAbsorptionAmount(0.0F);
		}
		if (this.falling > 0 || this.settling > 0) {
			this.fall(level);
			return;
		}
		if (this.stunned > 0) {
			this.windup.cancel();
			if (this.tickCount % 4 == 0) {
				level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(2.1), this.getZ(), 4, 0.5, 0.1, 0.5, 0.05);
			}
			if (--this.stunned == 0) {
				this.letGo();
			}
			return;
		}
		if (this.phaseGuard > 0) {
			this.windup.cancel();
			if (--this.phaseGuard == 0) {
				this.letGo();
			}
			return;
		}
		if (this.starReforge == 1) {
			this.wave = 0;
			this.windup.cancel();
			this.starReforgeTick(level);
			return;
		}
		if (this.reforging > 0) {
			this.wave = 0;
			// Whatever he was winding up when he turned back to the forge is not coming. Left alone it
			// used to sit paused for the whole reforge and then land, out of nowhere, as he came out.
			this.windup.cancel();
			this.reforge(level);
			return;
		}
		// La forja reclama: while it is under way it is all he does.
		if (this.reclaim > 0) {
			this.rollReclaim(level);
			return;
		}
		if (this.reclaimCooldown > 0) {
			this.reclaimCooldown--;
		}
		// Not over a blow of his own or a shower on its way: those finish first, and the count waits.
		if (this.reclaimCooldown == 0 && !this.windup.charging() && this.starfall == 0 && this.wave == 0 && this.gangedUp()) {
			this.startReclaim(level);
			return;
		}
		float share = this.getHealth() / this.getMaxHealth();
		// The phases (docs/HERRERO_DIMENSION.md, 3.3): apprentices at two thirds and at one third, and the sky
		// at the second. Each only once, and each opens with the pause they come up in.
		if (this.wavesCalled < 1 && share <= 2.0F / 3.0F) {
			this.wavesCalled = 1;
			this.calledHelp = true;
			this.phaseShift(level);
			return;
		}
		if (this.wavesCalled < 2 && share <= 1.0F / 3.0F) {
			this.wavesCalled = 2;
			this.broughtSky = true;
			this.phaseShift(level);
			this.triggerAnim("boss", "roar");
			this.phaseBreak(level);
			return;
		}
		// At half, between the two: the reforge. In the graveyard it is the Reforjado estelar.
		if (share <= 0.5F && this.wavesCalled == 1) {
			if (this.inYard()) {
				if (this.starReforge == 0) {
					this.startStarReforge(level);
					return;
				}
			} else if (this.embersLeft == 0) {
				this.startReforge(level);
			}
		}
		// Not over one of his own blows: a special under way finishes first, and the shower waits for the next time.
		if (this.broughtSky && this.starfall == 0 && this.tickCount % 60 == 0 && this.getTarget() != null && !this.windup.charging()) {
			this.callStars(level, this.getTarget());
		}
		if (this.starfall > 0) {
			this.rollStarfall(level);
		}
		this.heavyMoves(level);
	}

	/**
	 * The two things he does with his hands when you are too far to hit and too close to ignore: he
	 * drops the hammer and sends the floor at you, or he throws the claw on the dead arm and hauls you
	 * back in. One is answered by getting off the ground, the other by staying out of the middle range.
	 */
	private void heavyMoves(ServerLevel level) {
		if (this.wave > 0) {
			this.rollWave(level);
		}
		if (this.waveCooldown > 0) {
			this.waveCooldown--;
		}
		if (this.hookCooldown > 0) {
			this.hookCooldown--;
		}
		if (this.strikeCooldown > 0) {
			this.strikeCooldown--;
		}
		// A blow already on its way finishes first, and while it is on its way he is committed: he
		// plants his feet and stops following you. That is the whole bargain — the move can be dodged
		// because he cannot correct it, and it hurts because he cannot be talked out of it either.
		if (this.windup.charging()) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.35, 1.0, 0.35));
			this.windup.tick(level);
			return;
		}
		// The same while he holds the hammer up to the sky: nothing new starts until the stars are down.
		if (this.starfall > 0) {
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.35, 1.0, 0.35));
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || this.wave > 0) {
			return;
		}
		double distance = this.distanceTo(target);
		// He remembers: against someone who keeps getting out of his wave, he goes in close first (idea 100).
		if (dev.forja.ai.WorldFights.smithPrefersClose(target) && this.strikeCooldown == 0 && distance <= STRIKE_REACH) {
			this.backhand(level);
		} else if (this.waveCooldown == 0 && distance >= 3.0 && distance <= WAVE_REACH + 4.0) {
			this.anvilWave(level);
		} else if (this.strikeCooldown == 0 && distance <= STRIKE_REACH) {
			this.backhand(level);
		} else if (this.hookCooldown == 0 && this.phase() >= 2 && distance >= HOOK_MIN && distance <= HOOK_MAX
			&& this.hasLineOfSight(target)) {
			this.hookIn(level, target);
		}
	}

	/**
	 * Revés: the one move he had an animation for and never used.
	 *
	 * <p>{@code strike} has been sitting in his animation file since the model was drawn — arm back at
	 * four ticks, through at ten, recovered at eighteen — and nothing ever played it. It fills the hole
	 * in his range: the shockwave needs you three blocks out, the hook needs you five, and between those
	 * you could stand in his face and take nothing but ordinary melee.
	 */
	public void backhand(ServerLevel level) {
		this.strikeCooldown = STRIKE_COOLDOWN;
		this.triggerAnim("boss", "strike");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.6F);
		this.windup.start(STRIKE_WINDUP, (world, left, total) -> {
			Vec3 arc = this.getEyePosition().add(this.getLookAngle().scale(1.6)).subtract(0.0, 0.6, 0.0);
			world.sendParticles(ParticleTypes.CRIT, arc.x, arc.y, arc.z, 2, 0.35, 0.2, 0.35, 0.02);
		}, world -> {
			dev.forja.ai.ForjaTraits.smithStruck(this);
			dev.forja.ai.WorldFights.smithBlowLanded(this, false);
			Vec3 reach = this.position().add(this.getLookAngle().scale(STRIKE_REACH * 0.5));
			world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0F, 0.7F);
			for (LivingEntity victim : world.getEntitiesOfClass(LivingEntity.class,
				this.getBoundingBox().inflate(STRIKE_REACH * 0.5 + 1.0),
				other -> other != this && other.isAlive() && !(other instanceof FallenSmith))) {
				// A backhand only catches what is in front of him, which is the way out of it.
				if (victim.distanceToSqr(reach) > STRIKE_REACH * STRIKE_REACH * 0.36) {
					continue;
				}
				victim.hurtServer(world, this.damageSources().mobAttack(this), STRIKE_DAMAGE);
				Vec3 away = victim.position().subtract(this.position()).normalize();
				double hold = 1.0 - dev.forja.upgrade.Upgrades.anchor(victim);
				victim.push(away.x * 0.8 * hold, 0.32 * hold, away.z * 0.8 * hold);
				victim.hurtMarked = true;
			}
			world.sendParticles(ParticleTypes.SWEEP_ATTACK, reach.x, reach.y + 1.0, reach.z, 3, 0.4, 0.3, 0.4, 0.0);
			world.sendParticles(dev.forja.registry.ModParticles.CHISPA, reach.x, reach.y + 0.9, reach.z, 14, 0.4, 0.3, 0.4, 0.25);
		});
	}

	/**
	 * The moment the last quarter of him starts, drawn.
	 *
	 * <p>He already had the roar. What he did not have was anything to look at: a boss going into his
	 * final phase played an animation and then carried on, and the only way to know it had happened was
	 * to be watching the health bar rather than the fight. This is the one moment in the whole encounter
	 * that deserves to stop you, so it gets a ring that leaves him, a column of ash off him, and the
	 * forge in his chest going violet for good.
	 */
	private void phaseBreak(ServerLevel level) {
		this.lightForge(RAGE_TICKS * 3);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.4F, 0.7F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(),
			SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0F, 0.4F);
		// A ring on the floor that leaves him, to say the room has changed and not just his health. It
		// was six still circles of dust standing in for one moving one; now it is the moving one. It
		// hurts nobody — this is the room being told, not an attack.
		Shockwave.burst(level, this.position(), PHASE_RING_REACH, PHASE_RING_TICKS, Shockwave.VIOLET);
		// And everything that has been burning in him for a hundred years, at once.
		level.sendParticles(dev.forja.registry.ModParticles.CENIZA,
			this.getX(), this.getY(1.6), this.getZ(), 60, 0.8, 1.6, 0.8, 0.05);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA,
			this.getX(), this.getY(1.2), this.getZ(), 50, 0.6, 0.8, 0.6, 0.6);
		this.standUpAnvils(level);
	}

	/** Six walking anvils, in a ring round him, as the last quarter opens. */
	private void standUpAnvils(ServerLevel level) {
		for (int index = 0; index < FINAL_ANVILS; index++) {
			double angle = index * 2.0 * Math.PI / FINAL_ANVILS + this.random.nextDouble() * 0.3;
			double radius = 6.0 + this.random.nextDouble() * 2.0;
			dev.forja.entity.WalkingAnvil anvil = dev.forja.registry.ModEntities.YUNQUE_ANDANTE
				.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			if (anvil == null) {
				return;
			}
			double x = this.getX() + Math.cos(angle) * radius;
			double z = this.getZ() + Math.sin(angle) * radius;
			anvil.snapTo(x, this.getY(), z, (float) Math.toDegrees(angle) + 90.0F, 0.0F);
			anvil.setPersistenceRequired();
			level.addFreshEntity(anvil);
			level.playSound(null, x, this.getY(), z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.8F, 0.5F);
			level.sendParticles(dev.forja.registry.ModParticles.CHISPA, x, this.getY() + 0.6, z, 20, 0.35, 0.5, 0.35, 0.3);
		}
	}

	/**
	 * Onda de yunque: the hammer comes down and a ring of fire runs out of him along the floor.
	 *
	 * <p>The ring used to leave him on the same tick the animation started, while the hammer was still
	 * on its way up. Now it waits for the hammer. Eighteen ticks is a long time to watch something
	 * coming, and it is meant to be: this is the move you are supposed to get off the ground for.
	 */
	public void anvilWave(ServerLevel level) {
		this.waveCooldown = WAVE_COOLDOWN;
		this.triggerAnim("boss", "slam");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 2.5F, 0.7F);
		// The floor lights up under the whole ring before any of it moves, so the reach is not
		// something you have to learn by being hit by it. It used to be twenty particles a tick; now
		// it is one entity, told once, that every client draws for itself. The charge owns it: it goes
		// if the charge is dropped, and the blow is the only thing that can turn it into the ring.
		int colour = this.isRaging() ? Shockwave.VIOLET : Shockwave.EMBER;
		this.windup.start(WAVE_WINDUP, (world, left, total) -> {
			float grown = 1.0F - (float) left / total;
			if (left % 4 == 0) {
				world.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(1.9), this.getZ(),
					(int) (2 + grown * 4), 0.3, 0.2, 0.3, 0.01);
			}
		}, world -> {
			dev.forja.ai.ForjaTraits.smithStruck(this);
			dev.forja.ai.WorldFights.smithBlowLanded(this, true);
			this.wave = WAVE_TICKS;
			this.waveHit.clear();
			this.waveOrigin = this.position();
			// The ring leaves from where the hammer came down and stays there: he is free to walk off
			// again, and a ring that followed him would be hitting people it never went past.
			Shockwave ring = this.windup.takeWarning();
			if (ring != null) {
				ring.fire(this.waveOrigin);
			} else {
				Shockwave.burst(world, this.waveOrigin, WAVE_REACH, WAVE_TICKS, colour);
			}
			this.lightForge(RAGE_TICKS);
			// The hammer meeting the floor throws metal, not smoke.
			world.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY() + 0.2, this.getZ(),
				40, 0.6, 0.15, 0.6, 0.45);
			world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4.0F, 0.5F);
		});
		this.windup.warn(Shockwave.telegraph(level, this, WAVE_REACH, WAVE_WINDUP, WAVE_TICKS, colour));
	}

	/**
	 * One tick of the ring: it grows, and it only ever hits you once.
	 *
	 * <p>It no longer draws itself — the {@link Shockwave} it fired does that, on the client, off the
	 * same {@link Shockwave#radiusAt}. So the circle tested here and the circle on the screen are one
	 * circle, which matters now that the one on the screen has an edge you can see.
	 */
	private void rollWave(ServerLevel level) {
		int step = WAVE_TICKS - this.wave;
		this.wave--;
		double before = Shockwave.radiusAt(WAVE_REACH, step, WAVE_TICKS);
		double radius = Shockwave.radiusAt(WAVE_REACH, step + 1, WAVE_TICKS);
		Vec3 origin = this.waveOrigin;
		net.minecraft.world.phys.AABB sweep = new net.minecraft.world.phys.AABB(
			origin.x - radius - 1.5, origin.y - 3.0, origin.z - radius - 1.5,
			origin.x + radius + 1.5, origin.y + this.getBbHeight() + 3.0, origin.z + radius + 1.5);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, sweep,
			other -> other != this && other.isAlive() && !(other instanceof FallenSmith))) {
			double reach = Math.sqrt(victim.distanceToSqr(origin.x, victim.getY(), origin.z));
			double half = victim.getBbWidth() * 0.5;
			// Swept, not sampled: whoever the front crossed between last tick and this one, however
			// fast it is going. The old test was a band 1.2 either side of the ring, which also caught
			// people the ring had not reached yet — invisible when the ring was dots, plain to see now.
			if (reach - half > radius || reach + half < before) {
				continue;
			}
			// Off the floor as it gets to you, and it goes under you. The guide has said "se salta" since
			// the move was written and nothing here ever checked; with a wall of flame to clear, it does.
			// Tested before the name goes in the set on purpose: whoever comes down while the front is
			// still passing through them has not got away with it.
			double floor = origin.y + Shockwave.floorAt(level, victim.getX(), origin.y, victim.getZ());
			if (victim.getY() - floor > WAVE_CLEARANCE || !this.waveHit.add(victim.getUUID())) {
				continue;
			}
			victim.invulnerableTime = 0;
			victim.hurtServer(level, this.damageSources().mobAttack(this), WAVE_DAMAGE);
			// Away from where the hammer landed, which is the way the ring is travelling when it gets there.
			Vec3 away = victim.position().subtract(origin).multiply(1.0, 0.0, 1.0).normalize();
			double hold = 1.0 - dev.forja.upgrade.Upgrades.anchor(victim);
			victim.push(away.x * 0.6 * hold, 0.55 * hold, away.z * 0.6 * hold);
			victim.hurtMarked = true;
			victim.igniteForSeconds(3.0F);
		}
	}

	/** Garfio: the claw on the ruined arm goes out on its chain and drags what it catches back to him. */
	public void hookIn(ServerLevel level, LivingEntity target) {
		this.hookCooldown = HOOK_COOLDOWN;
		this.triggerAnim("boss", "hook");
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2.2F, 0.5F);
		// Eight ticks with the chain gathered on the dead arm: short, because the answer to this one is
		// to break his line to you rather than to outrun it.
		this.windup.start(HOOK_WINDUP, (world, left, total) -> {
			Vec3 arm = this.getEyePosition().add(this.getLookAngle().scale(0.9)).subtract(0.0, 0.4, 0.0);
			world.sendParticles(ParticleTypes.CRIT, arm.x, arm.y, arm.z, 2, 0.15, 0.15, 0.15, 0.01);
		}, world -> this.throwClaw(world, target));
	}

	/** The claw actually leaving his hand, once the arm has finished cocking. */
	private void throwClaw(ServerLevel level, LivingEntity target) {
		if (!target.isAlive()) {
			return;
		}
		this.lightForge(RAGE_TICKS);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 3.0F, 0.6F);
		Vec3 from = this.getEyePosition().add(this.getLookAngle().scale(0.8));
		Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		for (int step = 0; step <= 24; step++) {
			double fraction = step / 24.0;
			level.sendParticles(ParticleTypes.CRIT,
				net.minecraft.util.Mth.lerp(fraction, from.x, to.x),
				net.minecraft.util.Mth.lerp(fraction, from.y, to.y),
				net.minecraft.util.Mth.lerp(fraction, from.z, to.z),
				1, 0.02, 0.02, 0.02, 0.0);
		}
		target.invulnerableTime = 0;
		target.hurtServer(level, this.damageSources().mobAttack(this), HOOK_DAMAGE);
		// Hauled in to just short of arm's reach, which is where the hammer is waiting.
		Vec3 pull = this.position().subtract(target.position());
		double reach = Math.max(1.0, pull.horizontalDistance());
		double hold = 1.0 - dev.forja.upgrade.Upgrades.anchor(target);
		target.push(pull.x / reach * 1.5 * hold, 0.42 * hold, pull.z / reach * 1.5 * hold);
		target.hurtMarked = true;
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 1.4F, 0.5F);
	}

	/**
	 * The forge is open at the front, so it has to be seen working: embers drifting out of the grate
	 * while he is banked down, and a column of violet fire out of the opening and the flue when he is not.
	 */
	private void forgeBreathes(ServerLevel level, boolean hot) {
		Vec3 facing = this.getLookAngle();
		double mouthX = this.getX() - facing.x * 0.55;
		double mouthY = this.getY(0.78);
		double mouthZ = this.getZ() - facing.z * 0.55;
		if (!hot) {
			if (this.tickCount % 6 == 0) {
				level.sendParticles(ParticleTypes.FLAME, mouthX, mouthY, mouthZ, 1, 0.22, 0.2, 0.22, 0.01);
			}
			if (this.tickCount % 18 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, this.getX() + facing.z * 0.5, this.getY(1.16), this.getZ() - facing.x * 0.5, 2, 0.1, 0.1, 0.1, 0.02);
			}
			return;
		}
		if (this.tickCount % 2 == 0) {
			level.sendParticles(VIOLET, mouthX, mouthY, mouthZ, 2, 0.25, 0.3, 0.25, 0.01);
			level.sendParticles(ParticleTypes.PORTAL, mouthX, mouthY + 0.4, mouthZ, 3, 0.22, 0.35, 0.22, 0.06);
		}
		if (this.tickCount % 4 == 0) {
			// Out of the flue, which is the only bit of him that still vents the way it was built to.
			level.sendParticles(VIOLET, this.getX() + facing.z * 0.5, this.getY(1.9), this.getZ() - facing.x * 0.5, 2, 0.1, 0.1, 0.1, 0.03);
		}
	}

	/** Everyone close enough sees the bar; everyone who walks away loses it. */
	private void watchers(ServerLevel level) {
		// And so does everyone no longer in this world at all: gone through a portal, or the body a
		// respawn left behind. Those are not in level.players() any more, so the loop below never took
		// the bar off them; the second kind shares its connection with the new body, and a bar update
		// for a bar that client has already dropped fails on its side and disconnects it.
		for (ServerPlayer shown : List.copyOf(this.bar.getPlayers())) {
			if (shown.isRemoved() || shown.level() != level) {
				this.bar.removePlayer(shown);
			}
		}
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) <= 60.0 * 60.0) {
				this.bar.addPlayer(player);
			} else {
				this.bar.removePlayer(player);
			}
		}
	}

	/**
	 * A change of phase (docs/HERRERO_DIMENSION.md, 3.4): the hammer goes into the floor, he cannot be hurt
	 * for PHASE_GUARD ticks, and his apprentices come up out of the ground round him.
	 */
	private void phaseShift(ServerLevel level) {
		this.phaseGuard = PHASE_GUARD;
		this.holdStill();
		this.getNavigation().stop();
		this.windup.cancel();
		Shockwave.burst(level, this.position(), PHASE_RING_REACH, PHASE_RING_TICKS, Shockwave.VIOLET);
		this.callApprentices(level);
	}

	/**
	 * The apprentices (docs 3.5): 4, and 3 more for each player after the first, in rings of regular
	 * polygons round him (world/Formation). They come up out of the floor like the dead: below it at first,
	 * rising through it with the floor breaking round them and the sound of digging, and nothing touches
	 * them until they are out.
	 */
	private void callApprentices(ServerLevel level) {
		// Not the roar of his last quarter: the hammer goes down on the floor and they come up out of it.
		this.triggerAnim("boss", "call");
		this.lightForge(RAGE_TICKS * 2);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.7F);
		int players = Math.max(1, dev.forja.world.StarFight.fightersNear(level, this).size());
		int count = dev.forja.world.Formation.count(players);
		List<Vec3> offsets = dev.forja.world.Formation.offsets(count);
		List<Integer> rings = dev.forja.world.Formation.ringOf(count);
		double floor = this.getY();
		// Each one has a calling of its own (world/ApprenticeKits): the melee ones come up as wither skeletons and
		// the archers as plain skeletons, which are the ones that can draw a bow.
		List<dev.forja.world.ApprenticeKits.Role> roles = dev.forja.world.ApprenticeKits.wave(offsets.size(), level.getRandom());
		int salt = level.getRandom().nextInt(64);
		for (int i = 0; i < offsets.size(); i++) {
			Mob apprentice = roles.get(i).body().create(level, EntitySpawnReason.EVENT);
			if (apprentice == null) {
				continue;
			}
			Vec3 at = this.position().add(offsets.get(i));
			apprentice.snapTo(at.x, floor - RISE_DEPTH, at.z, (float) Math.toDegrees(Math.atan2(-offsets.get(i).x, offsets.get(i).z)) + 180.0F, 0.0F);
			dev.forja.world.Elites.makeElite(apprentice, level.getRandom());
			dev.forja.world.ApprenticeKits.equip(apprentice, roles.get(i), i, salt, level.getRandom());
			apprentice.setCustomName(Component.translatable("entity.forja.aprendiz"));
			// His, not just more elites: they go for whatever goes for him.
			dev.forja.world.Apprentices.enlist(apprentice);
			level.addFreshEntity(apprentice);
			// Buried after it is added: a buried apprentice being loaded is one a save caught half out, and is set free.
			dev.forja.world.Apprentices.bury(apprentice, floor);
			this.risers.add(new Riser(apprentice.getUUID(), floor, rings.get(i) * RISE_STAGGER));
		}
	}

	/** One tick of every apprentice still coming up. */
	private void rise(ServerLevel level) {
		for (int i = this.risers.size() - 1; i >= 0; i--) {
			Riser riser = this.risers.get(i);
			if (!(level.getEntity(riser.id) instanceof Mob apprentice) || !apprentice.isAlive()) {
				this.risers.remove(i);
				continue;
			}
			if (riser.delay > 0) {
				riser.delay--;
				continue;
			}
			riser.ticks++;
			double share = Math.min(1.0, riser.ticks / (double) RISE_TICKS);
			apprentice.setPos(apprentice.getX(), riser.floor - RISE_DEPTH + RISE_DEPTH * share, apprentice.getZ());
			apprentice.setDeltaMovement(Vec3.ZERO);
			net.minecraft.core.BlockPos under = net.minecraft.core.BlockPos.containing(apprentice.getX(), riser.floor - 0.5, apprentice.getZ());
			net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(under);
			if (!ground.isAir()) {
				level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, ground),
					apprentice.getX(), riser.floor + 0.1, apprentice.getZ(), 8, 0.35, 0.1, 0.35, 0.15);
			}
			if (riser.ticks % 10 == 1) {
				level.playSound(null, apprentice.getX(), riser.floor, apprentice.getZ(), SoundEvents.WARDEN_DIG, SoundSource.HOSTILE, 1.2F, 1.4F);
			}
			if (riser.ticks >= RISE_TICKS) {
				dev.forja.world.Apprentices.unbury(apprentice);
				this.risers.remove(i);
			}
		}
	}

	/** Stops him for a pause of his own, remembering whether something else had stopped him already. */
	private void holdStill() {
		if (!this.isNoAi()) {
			this.heldStill = false;
			this.setNoAi(true);
		} else if (this.phaseGuard == 0 && this.stunned == 0 && this.starReforge != 1) {
			this.heldStill = true;
		}
	}

	private void letGo() {
		if (!this.heldStill) {
			this.setNoAi(false);
		}
	}

	/** Whether any of his apprentices is still coming up out of the ground. */
	public boolean apprenticesRising() {
		return !this.risers.isEmpty();
	}

	// ------------------------------------------------------------------ the Reforjado estelar (docs 3.7)

	/**
	 * At half, in the graveyard: he kneels on the disc in the middle and lights his forge fires on the four
	 * diagonals, where the braseros' metal runs. Until the last of them is out he cannot be hurt, however
	 * long it takes. The braseros are filled for the start of it.
	 */
	private void startStarReforge(ServerLevel level) {
		this.starReforge = 1;
		this.holdStill();
		this.getNavigation().stop();
		this.snapTo(0.5, dev.forja.world.StarYard.SURFACE + 1.0, 0.5, this.getYRot(), 0.0F);
		this.triggerAnim("boss", "slam");
		this.bar.setColor(BossEvent.BossBarColor.YELLOW);
		int players = Math.max(1, dev.forja.world.StarFight.fightersNear(level, this).size());
		int count = Math.min(EMBERS_MOST, EMBERS_BASE + players - 1);
		this.embersAt.clear();
		for (int i = 0; i < count; i++) {
			double angle = Math.PI / 4.0 + (i % 4) * Math.PI / 2.0;
			double radius = i < 4 ? 10.0 : 14.0;
			net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos((int) Math.round(Math.cos(angle) * radius),
				dev.forja.world.StarYard.SURFACE + 1, (int) Math.round(Math.sin(angle) * radius));
			level.setBlockAndUpdate(at, dev.forja.registry.ModBlocks.BRASA_ESTELAR.defaultBlockState());
			this.embersAt.add(at);
		}
		for (int i = 0; i < 4; i++) {
			dev.forja.world.StarFight.refill(level, i);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 4.0F, 0.4F);
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Component.translatable("gui.forja.pelea.reforjado", count).withColor(0xFFC24A));
		}
	}

	private void starReforgeTick(ServerLevel level) {
		this.lightForge(20);
		int burning = 0;
		for (net.minecraft.core.BlockPos at : this.embersAt) {
			if (level.getBlockState(at).is(dev.forja.registry.ModBlocks.BRASA_ESTELAR)) {
				burning++;
				// The beam from each fire to him, that says what is keeping him whole.
				if (this.tickCount % 5 == 0) {
					Vec3 from = Vec3.atCenterOf(at);
					Vec3 to = this.position().add(0.0, 1.4, 0.0);
					for (int s = 1; s < 10; s++) {
						Vec3 p = from.lerp(to, s / 10.0);
						level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
					}
				}
			}
		}
		this.embersLeft = burning;
		if (burning == 0) {
			this.starReforge = 2;
			this.stunned = STUN_TICKS;
			this.bar.setColor(BossEvent.BossBarColor.RED);
			Shockwave.burst(level, this.position(), PHASE_RING_REACH, PHASE_RING_TICKS, Shockwave.VIOLET);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 4.0F, 0.5F);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 3.0F, 0.5F);
			for (ServerPlayer player : level.players()) {
				player.sendSystemMessage(Component.translatable("gui.forja.pelea.aturdido").withColor(0x9CFFD8));
			}
		}
	}

	/** Where his forge fires were lit, for the tests. */
	public List<net.minecraft.core.BlockPos> embersAt() {
		return List.copyOf(this.embersAt);
	}

	public int starReforge() {
		return this.starReforge;
	}

	/** He goes back to the forge, lights three embers and waits. Put them out or wait him out. */
	private void startReforge(ServerLevel level) {
		this.reforging = REFORGE_TICKS;
		this.embersLeft = EMBERS;
		this.triggerAnim("boss", "slam");
		this.lightForge(REFORGE_TICKS);
		this.bar.setColor(BossEvent.BossBarColor.YELLOW);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_USE, SoundSource.HOSTILE, 4.0F, 0.6F);
		for (int i = 0; i < EMBERS; i++) {
			double angle = i * 2.0 * Math.PI / EMBERS;
			net.minecraft.core.BlockPos ember = this.blockPosition().offset((int) Math.round(Math.cos(angle) * 5.0), 0, (int) Math.round(Math.sin(angle) * 5.0));
			for (int dy = 0; dy < 3; dy++) {
				net.minecraft.core.BlockPos at = ember.above(dy);
				if (level.getBlockState(at).canBeReplaced()) {
					level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
					break;
				}
			}
		}
	}

	/** Every second of the reforge heals him, unless the embers are out. */
	private void reforge(ServerLevel level) {
		this.reforging--;
		level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY(1.0), this.getZ(), 4, 0.6, 0.8, 0.6, 0.0);
		// Counting the embers walks a box of blocks, so it happens twice a second and not sixty times.
		if (this.tickCount % 10 == 0) {
			int burning = 0;
			for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
				this.blockPosition().offset(-7, -2, -7), this.blockPosition().offset(7, 3, 7)
			)) {
				if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FIRE)) {
					burning++;
				}
			}
			this.embersLeft = burning;
		}
		int burning = this.embersLeft;
		if (burning > 0 && this.tickCount % 20 == 0) {
			this.heal(this.getMaxHealth() * 0.01F * burning);
		}
		if (burning == 0 || this.reforging <= 0) {
			this.reforging = 0;
			this.bar.setColor(BossEvent.BossBarColor.RED);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 3.0F, 0.7F);
		}
	}

	/**
	 * The last stage: the shower that buried the forge, called down on whoever is fighting him. The spot is
	 * marked when he raises the hammer, and the stars fall on it STARFALL_WINDUP ticks later: step off it.
	 */
	public void callStars(ServerLevel level, LivingEntity target) {
		this.starfall = STARFALL_WINDUP;
		this.starfallAt = target.position();
		this.triggerAnim("boss", "starcall");
		this.lightForge(RAGE_TICKS);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 1.2F);
	}

	/**
	 * Whether something may be taken by La forja reclama: a creature that belongs to nobody. Never a player
	 * (not a Mob at all), never anything tamed or owned, never one of his apprentices or anything else on
	 * Forja's side, never him, and never the dragon, whose fight is not his to end.
	 */
	public static boolean reclaimable(net.minecraft.world.entity.Entity entity) {
		if (!(entity instanceof Mob mob) || !mob.isAlive() || mob instanceof FallenSmith) {
			return false;
		}
		if (dev.forja.difficulty.Bosses.fromPlayer(mob)
			|| mob instanceof net.minecraft.world.entity.OwnableEntity owned && owned.getOwnerReference() != null) {
			return false;
		}
		if (dev.forja.world.Apprentices.isApprentice(mob) || dev.forja.world.Truce.ours(mob)
			|| mob instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
			return false;
		}
		// Somebody riding it is somebody's.
		for (net.minecraft.world.entity.Entity rider : mob.getPassengers()) {
			if (rider instanceof Player) {
				return false;
			}
		}
		return true;
	}

	/** Drops the blows older than RECLAIM_WINDOW, so the count only ever holds the last ten seconds. */
	private void forgetOldBlows() {
		while (!this.blows.isEmpty() && this.tickCount - this.blows.peekFirst().at() > RECLAIM_WINDOW) {
			this.blows.removeFirst();
		}
	}

	/** The RECLAIM_WINDOW count: three of them, or the heavy ones having tried for a tenth of him. */
	private boolean gangedUp() {
		this.forgetOldBlows();
		if (this.blows.isEmpty()) {
			return false;
		}
		java.util.Set<java.util.UUID> who = new java.util.HashSet<>();
		float heavy = 0.0F;
		for (Blow blow : this.blows) {
			who.add(blow.who());
			if (blow.heavy()) {
				heavy += blow.damage();
			}
		}
		return who.size() >= RECLAIM_CROWD || heavy >= this.getMaxHealth() * RECLAIM_HEAVY_SHARE;
	}

	/** Whether La forja reclama is under way, for the tests and the footage. */
	public boolean isReclaiming() {
		return this.reclaim > 0;
	}

	/** Ticks left of La forja reclama, 0 when it is not under way: what the footage times its shots by. */
	public int reclaimLeft() {
		return this.reclaim;
	}

	/** Ticks until La forja reclama can come again. */
	public int reclaimCooldown() {
		return this.reclaimCooldown;
	}

	/** La forja reclama, from its first tick: he plants the hammer, and the room starts to close in on him. */
	public void startReclaim(ServerLevel level) {
		this.reclaim = RECLAIM_WINDUP;
		this.reclaimCooldown = RECLAIM_COOLDOWN;
		this.blows.clear();
		this.windup.cancel();
		this.getNavigation().stop();
		this.triggerAnim("boss", "reclaim");
		this.lightForge(RECLAIM_WINDUP + RAGE_TICKS);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3.0F, 0.5F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 3.0F, 0.6F);
	}

	/** Everything within RECLAIM_RADIUS that La forja reclama takes. */
	public List<Mob> reclaimed(ServerLevel level) {
		return level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(RECLAIM_RADIUS),
			mob -> reclaimable(mob) && mob.distanceToSqr(this) <= RECLAIM_RADIUS * RECLAIM_RADIUS);
	}

	/** One tick of it: the ring closing in, then the pull, then nothing left. */
	private void rollReclaim(ServerLevel level) {
		this.reclaim--;
		this.getNavigation().stop();
		this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
		Vec3 heart = this.position().add(0.0, this.getBbHeight() * 0.45, 0.0);
		List<Mob> caught = this.reclaimed(level);
		if (this.reclaim > 0) {
			// The ring on the floor, closing from the edge of his reach to his feet over the whole warning,
			// every mote in it already moving in: the reach is the thing to read.
			double ring = RECLAIM_RADIUS * this.reclaim / (double) RECLAIM_WINDUP;
			for (int i = 0; i < 16; i++) {
				double angle = i * Math.PI / 8.0 + this.reclaim * 0.15;
				double px = this.getX() + Math.cos(angle) * ring;
				double pz = this.getZ() + Math.sin(angle) * ring;
				level.sendParticles(VIOLET, px, this.getY() + 0.2, pz, 1, 0.05, 0.05, 0.05, 0.0);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, this.getY() + 0.3, pz, 0,
					this.getX() - px, 0.0, this.getZ() - pz, 0.06);
			}
			// A thread from each of them to him: they are marked from the first tick.
			for (Mob mob : caught) {
				Vec3 from = mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0);
				Vec3 in = heart.subtract(from);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 3, 0.2, 0.3, 0.2, 0.02);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 0, in.x, in.y, in.z, 0.08);
				if (this.reclaim <= RECLAIM_PULL) {
					double distance = Math.max(0.5, in.length());
					double pull = Math.min(0.9, distance * 0.14);
					mob.getNavigation().stop();
					mob.setDeltaMovement(in.x / distance * pull, 0.12, in.z / distance * pull);
					mob.hurtMarked = true;
				}
			}
			if (this.reclaim == RECLAIM_PULL) {
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 3.0F, 0.4F);
			}
			return;
		}
		// Gone: discarded rather than killed, so nothing drops, no experience comes out and no death is dealt.
		for (Mob mob : caught) {
			Vec3 at = mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0);
			level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 16, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.35, mob.getBbWidth() * 0.4, 0.04);
			level.sendParticles(dev.forja.registry.ModParticles.CENIZA, at.x, at.y, at.z, 24, mob.getBbWidth() * 0.5, mob.getBbHeight() * 0.4, mob.getBbWidth() * 0.5, 0.03);
			level.sendParticles(VIOLET, at.x, at.y, at.z, 10, 0.3, 0.4, 0.3, 0.02);
			mob.discard();
		}
		Shockwave.burst(level, this.position(), 4.0, 12, Shockwave.VIOLET);
		level.sendParticles(dev.forja.registry.ModParticles.CHISPA, this.getX(), this.getY() + 0.3, this.getZ(), 40, 0.8, 0.2, 0.8, 0.4);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4.0F, 0.4F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 3.0F, 0.5F);
		if (this.getTarget() != null && !this.getTarget().isAlive()) {
			this.setTarget(null);
		}
	}

	/** The warning: the ring on the floor where they will land, and the light gathering over it. */
	private void rollStarfall(ServerLevel level) {
		this.starfall--;
		Vec3 at = this.starfallAt;
		if (this.starfall > 0) {
			float grown = 1.0F - (float) this.starfall / STARFALL_WINDUP;
			for (int i = 0; i < 10; i++) {
				double angle = i * Math.PI / 5.0 + this.starfall * 0.2;
				level.sendParticles(ParticleTypes.END_ROD, at.x + Math.cos(angle) * STARFALL_RADIUS, at.y + 0.1,
					at.z + Math.sin(angle) * STARFALL_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 7.0 - grown * 3.0, at.z, 2 + (int) (grown * 6), 0.8, 0.4, 0.8, 0.01);
			return;
		}
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 6.0, at.z, 60, 1.0, 1.0, 1.0, 0.1);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 0.6F);
		// Aimed at whatever he is fighting, which is not always a player now; the apprentices crowding a
		// golem with him are his own side and the shower passes them by (it has no attacker for the truce).
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(at.x - STARFALL_RADIUS, at.y - 1.0, at.z - STARFALL_RADIUS,
			at.x + STARFALL_RADIUS, at.y + 4.0, at.z + STARFALL_RADIUS);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
			other -> other != this && other.isAlive() && !dev.forja.world.Truce.ours(other))) {
			victim.invulnerableTime = 0;
			victim.hurtServer(level, level.damageSources().magic(), 9.0F);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.bar.removeAllPlayers();
		// His heart, and one piece of what he was carrying.
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(), new ItemStack(ModItems.CORAZON_DE_FORJA)));
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(), Legends.create(level.getRandom(), level.registryAccess())));
		// His anvil only the first time: a rematch in the graveyard is fought for the star, not the anvil.
		if (!this.inYard() || dev.forja.world.StarFight.state(level).fights() == 0) {
			level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(), new ItemStack(dev.forja.registry.ModItems.YUNQUE_DEL_HERRERO)));
		}
		// The hammer he was working with: the only thing that takes back a technique.
		level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY(1.0), this.getZ(),
			new ItemStack(dev.forja.registry.ModItems.MARTILLO_DEL_MAESTRO)));
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 4.0F, 0.6F);
		level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY(1.0), this.getZ(), 120, 1.0, 1.5, 1.0, 0.1);
		if (source.getEntity() instanceof ServerPlayer player) {
			dev.forja.ForjaAdvancements.award(player, "herrero_caido");
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bar.removeAllPlayers();
		this.windup.cancel();
		this.reclaim = 0;
		super.remove(reason);
	}

	/** The hammer comes down out of its wind-up as it is swung, whether or not it lands (see MobMoves). */
	@Override
	public void swing(net.minecraft.world.InteractionHand hand, boolean updateSelf) {
		super.swing(hand, updateSelf);
		// Only when this call started a swing, not when one already under way was left alone (as BrokenMould).
		if (this.level() instanceof ServerLevel && this.swingTime == -1) {
			this.triggerAnim("boss", "swing");
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		this.triggerAnim("fire", this.isRaging() ? "flash_hot" : "flash");
		Vec3 facing = this.getLookAngle();
		double mouthX = this.getX() - facing.x * 0.55;
		double mouthZ = this.getZ() - facing.z * 0.55;
		if (this.isRaging()) {
			level.sendParticles(VIOLET, mouthX, this.getY(0.78), mouthZ, 12, 0.3, 0.3, 0.3, 0.06);
		} else {
			level.sendParticles(ParticleTypes.FLAME, mouthX, this.getY(0.78), mouthZ, 10, 0.3, 0.3, 0.3, 0.06);
		}
		return super.doHurtTarget(level, target);
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ANVIL_LAND;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return SoundEvents.WITHER_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(MobMoves.controller("boss", MobMoves.Clips.<FallenSmith>of(IDLE, WALK).run(RUN)
			.windup(WINDUP, MobMoves.WINDUP_TICKS).stagger(STAGGER).death(DEATH)
			.state(smith -> smith.entityData.get(DATA_REFORGING) ? REFORGE : null))
			.triggerableAnim("slam", SLAM).triggerableAnim("roar", ROAR).triggerableAnim("strike", STRIKE).triggerableAnim("hook", HOOK)
			.triggerableAnim("swing", SWING).triggerableAnim("call", CALL).triggerableAnim("starcall", STARCALL)
				.triggerableAnim("reclaim", RECLAIM));
		// The fire is its own controller: it only touches the two bones the flames hang off, so it can
		// keep burning through a swing, a roar or a reforge without any of them fighting over a bone. It goes
		// back to burning after a flash, too: it used to stop dead on the flash's last frame.
		controllers.add(MobMoves.controller("fire", MobMoves.Clips.<FallenSmith>of(FIRE_CALM, FIRE_CALM)
			.state(smith -> smith.isRaging() ? FIRE_RAGE : FIRE_CALM))
			.triggerableAnim("flash", FIRE_FLASH).triggerableAnim("flash_hot", FIRE_FLASH_HOT));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	/** How many stages he fights in, for whatever wants to say which one this is. */
	public static final int PHASES = 3;

	/**
	 * Which stage he is in, read off his health: the apprentices come at three quarters and the sky at
	 * one quarter, so the thirds line up with what he actually does.
	 */
	public int phase() {
		float share = this.getHealth() / this.getMaxHealth();
		if (share <= 1.0F / 3.0F) {
			return 3;
		}
		return share <= 2.0F / 3.0F ? 2 : 1;
	}

	/** Whether he is in the stage where nothing can touch him, for the tests and the tooltip. */
	public boolean isReforging() {
		return this.reforging > 0 || this.starReforge == 1;
	}

	// ------------------------------------------------------------------ what outlives a save

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("forja_cae", this.falling);
		output.putInt("forja_asienta", this.settling);
		output.putInt("forja_fase", this.phaseGuard);
		output.putInt("forja_oleadas", this.wavesCalled);
		output.putInt("forja_reforjado", this.starReforge);
		output.putInt("forja_aturdido", this.stunned);
		int[] embers = new int[this.embersAt.size() * 3];
		for (int i = 0; i < this.embersAt.size(); i++) {
			embers[i * 3] = this.embersAt.get(i).getX();
			embers[i * 3 + 1] = this.embersAt.get(i).getY();
			embers[i * 3 + 2] = this.embersAt.get(i).getZ();
		}
		output.putIntArray("forja_brasas", embers);
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
		super.readAdditionalSaveData(input);
		this.falling = input.getIntOr("forja_cae", 0);
		this.settling = input.getIntOr("forja_asienta", 0);
		this.phaseGuard = input.getIntOr("forja_fase", 0);
		this.wavesCalled = input.getIntOr("forja_oleadas", 0);
		this.starReforge = input.getIntOr("forja_reforjado", 0);
		this.stunned = input.getIntOr("forja_aturdido", 0);
		this.calledHelp = this.wavesCalled >= 1;
		this.broughtSky = this.wavesCalled >= 2;
		this.embersAt.clear();
		input.getIntArray("forja_brasas").ifPresent(packed -> {
			for (int i = 0; i + 2 < packed.length; i += 3) {
				this.embersAt.add(new net.minecraft.core.BlockPos(packed[i], packed[i + 1], packed[i + 2]));
			}
		});
	}

	public int embers() {
		return this.embersLeft;
	}
}
