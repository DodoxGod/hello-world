package dev.forja.ai;

import dev.forja.combat.ChargedStrike;
import dev.forja.combat.CombatFeedback;
import dev.forja.combat.Posture;
import dev.forja.combat.Stamina;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;

/**
 * The smart shield (docs/red_mob_v4_diseno.md §4.9 and §2.9, block G): what a v4 network reads about its shield and
 * the player's next blow, and the shield bash, the counter that lowers a shield that has just blocked.
 *
 * <p><b>The bash</b> (golpe_escudo, output 52): allowed with the shield up, a block or a parry in the last
 * {@link #BASH_WINDOW} ticks, the player within its blow's reach and the bash ready. {@link #WARNING} ticks of warning,
 * then the shield comes down and the player is shoved {@link #PUSH} blocks' worth away from the mob (less by Anclaje),
 * loses {@link #STAMINA} stamina and any charge they were drawing back. {@link #COOLDOWN} ticks before the next.
 */
public final class ShieldPlay {
	public static final int BASH_WINDOW = 20;
	public static final int WARNING = 4;
	public static final double PUSH = 0.8;
	public static final float STAMINA = 15.0F;
	public static final int COOLDOWN = 60;
	/** The speed a player closes in at, blocks per tick (sprinting), for jug_golpe_en. */
	public static final double PLAYER_SPEED = 0.28;
	/** A charge this far drawn back counts as a heavy blow (jug_golpe_fuerte). */
	public static final double HEAVY_CHARGE = 0.8;

	private ShieldPlay() {
	}

	/** Its shield is up: in the off hand, being used. */
	public static boolean up(Mob mob) {
		return mob.isUsingItem() && mob.getUsedItemHand() == InteractionHand.OFF_HAND && MobDefense.hasShield(mob);
	}

	/** A blow was blocked or parried by this mob's shield (MobDefense.allowDamage). */
	public static void blocked(Mob mob) {
		MobMind mind = MobAi.mind(mob);
		if (mind != null) {
			mind.lastBlockAt = mob.level().getGameTime();
		}
	}

	/** Ticks since its last block or parry; a large number for never. */
	public static long sinceBlock(MobMind mind, long now) {
		return mind.lastBlockAt <= Long.MIN_VALUE / 4 ? Long.MAX_VALUE / 4 : now - mind.lastBlockAt;
	}

	/** Whether the bash may start now (output 52's mask, and golpe_escudo_listo). */
	public static boolean bashReady(Mob mob, MobMind mind, Player target, long now) {
		return target != null && up(mob) && sinceBlock(mind, now) < BASH_WINDOW && now >= mind.bashReadyAt && mind.bashWindup == 0
			&& Reach.reaches(mob, target) && !Posture.isStaggered(mob, now);
	}

	/**
	 * Ticks until one of the player's blows could reach the mob (jug_golpe_en, before the /10): 0 if their weapon is
	 * recharged and the mob is within its reach; otherwise the most of the recharge still to go and the time it takes
	 * them to close the gap at {@link #PLAYER_SPEED}. At most 20.
	 */
	public static double blowIn(Mob mob, Player target) {
		double reach = Reach.player(target);
		double gap = Math.max(0.0, mob.distanceTo(target) - reach);
		double recharge = (1.0 - target.getAttackStrengthScale(0.0F)) * target.getCurrentItemAttackStrengthDelay();
		if (ChargedStrike.isCharging(target)) {
			recharge = 0.0;
		}
		return Math.min(20.0, Math.max(Math.max(0.0, recharge), gap / PLAYER_SPEED));
	}

	/**
	 * Whether the blow coming is a heavy one (jug_golpe_fuerte): a charge drawn back at least {@link #HEAVY_CHARGE}, or
	 * the player's weapon has an area special, is recharged and the mob stands inside its radius.
	 */
	public static boolean heavy(Mob mob, Player target) {
		if (target instanceof net.minecraft.server.level.ServerPlayer server && ChargedStrike.share(server) >= HEAVY_CHARGE) {
			return true;
		}
		double area = ObsV3.areaRadius(target.getMainHandItem());
		return area > 0.0 && target.getAttackStrengthScale(0.0F) >= 0.9F && mob.distanceTo(target) <= area;
	}

	/** Something is about to reach the mob: a blow within 3 ticks, a heavy one, or a bow or crossbow drawn at it. */
	public static boolean incoming(Mob mob, Player target) {
		if (blowIn(mob, target) <= 3.0 || heavy(mob, target)) {
			return true;
		}
		boolean drawn = target.isUsingItem() && (target.getUseItem().getItem() instanceof BowItem || target.getUseItem().getItem() instanceof CrossbowItem)
			|| target.getMainHandItem().getItem() instanceof CrossbowItem && CrossbowItem.isCharged(target.getMainHandItem());
		return drawn && MobActions.threatens(target, mob);
	}

	/** The five inputs of block G, written from {@code at}. */
	public static void observe(Mob mob, MobMind mind, Player target, long now, float[] out, int at) {
		out[at] = up(mob) ? (float) ObsM1.clip(mob.getTicksUsingItem() / 20.0, 0.0, 2.0) : 0.0F;
		out[at + 1] = (float) (blowIn(mob, target) / 10.0);
		out[at + 2] = heavy(mob, target) ? 1.0F : 0.0F;
		long since = sinceBlock(mind, now);
		out[at + 3] = since > BASH_WINDOW ? 1.0F : (float) (since / 20.0);
		out[at + 4] = bashReady(mob, mind, target, now) ? 1.0F : 0.0F;
	}

	/** Starts the bash if the brain asked for it and it may: the warning begins. */
	public static boolean start(Mob mob, MobMind mind, Player target, long now) {
		if (!bashReady(mob, mind, target, now)) {
			return false;
		}
		mind.bashWindup = WARNING;
		mind.bashTarget = target;
		CombatFeedback.telegraph(mob, WARNING);
		mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 0.6F, 0.6F);
		return true;
	}

	/** Each tick (MobAi): a bash asked for is started, one under way counts down and lands. */
	public static void tick(MobMind mind, long now) {
		Mob mob = mind.mob;
		if (mind.bashWindup == 0) {
			if (mind.decision.bash() && mind.decidedAt == now && mind.target != null) {
				start(mob, mind, mind.target, now);
			}
			return;
		}
		Player target = mind.bashTarget;
		if (target == null || !target.isAlive() || Posture.isStaggered(mob, now) || !MobDefense.hasShield(mob)) {
			mind.bashWindup = 0;
			mind.bashTarget = null;
			return;
		}
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (--mind.bashWindup > 0) {
			return;
		}
		mind.bashTarget = null;
		mind.bashReadyAt = now + COOLDOWN;
		if (mob.isUsingItem()) {
			mob.stopUsingItem();
		}
		mob.swing(InteractionHand.OFF_HAND);
		// Out of reach by the time it lands: the shield still comes down, and nothing else happens.
		if (!Reach.reaches(mob, target) || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 away = new Vec3(target.getX() - mob.getX(), 0.0, target.getZ() - mob.getZ());
		if (away.lengthSqr() > 1.0E-6) {
			double push = PUSH * (1.0 - dev.forja.upgrade.Upgrades.anchor(target));
			away = away.normalize().scale(push);
			target.push(away.x, 0.15, away.z);
			target.hurtMarked = true;
		}
		Stamina.trySpend(target, STAMINA);
		ChargedStrike.interrupt(target);
		mind.lastBash = now;
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.HOSTILE, 0.8F, 1.3F);
	}

	/** The rules' bash: right after a block, with the player still in reach, half the time. */
	public static boolean ruleBash(Mob mob, MobMind mind, Player target, long now) {
		return bashReady(mob, mind, target, now) && mind.random.nextFloat() < 0.5F;
	}
}
