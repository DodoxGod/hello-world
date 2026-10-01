package dev.forja.ai;

import dev.forja.combat.ChargedStrike;
import dev.forja.combat.CombatConfig;
import dev.forja.entity.BrokenMould;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The actions a monster will be able to take once the mob network's v4 contract decides them (Andy, 2026-09-29):
 * pick up a better weapon from the floor, drink or throw a potion, eat when hurt, throw an ender pearl, break a
 * light and raise its shield when something is aimed at it.
 *
 * <p>This class is only the plumbing. Nothing calls it yet: Andy wants the network, not the rules, to choose when
 * a mob does any of this, and the v4 contract that will carry those outputs is not written. Both brains will call
 * the same methods (RuleBrain through {@link TacticGoal}, a v4 net through its outputs), so what a mob can do is the
 * same whoever decides. See docs/red_mob_v4_propuesta.md, "Acciones preparadas para la v4".
 *
 * <p>Every action is off until {@link CombatConfig#mobActionsV4} is true, and breaking lights also needs
 * {@link CombatConfig#mobsBreakLights}: monsters never place or break blocks (Andy, 2026-09-29), and lights are the
 * one exception planned, with the v4. Each method returns whether it did something; nothing happens server-side
 * unless the mob is in a {@link ServerLevel}.
 */
public final class MobActions {
	/** An item this close (blocks, centre to centre) is taken; further away the mob walks to it. */
	public static final double TAKE_REACH = 1.5;
	/** A light this far from the mob's eyes, at most, can be struck. */
	public static final double LIGHT_REACH = 2.5;
	/** An item a player dropped counts as free after this many ticks, even while its pickup delay lasts. */
	public static final int PLAYER_DROP_TICKS = 40;
	/** Delay on the weapon a mob lets go of, so that it does not take it straight back. */
	public static final int DROPPED_DELAY = 40;
	/** A threat's view has to point at the mob this closely (cosine) to count as aimed at it. */
	public static final double AIM_COS = 0.9;
	/** A blow being wound up only matters this close; a bow or a crossbow at any distance. */
	public static final double WINDUP_RANGE = 6.0;
	/** Gravity of a thrown pearl per tick (vanilla's thrown items), for the lob. */
	private static final double PEARL_GRAVITY = 0.03;

	private MobActions() {
	}

	private static boolean on() {
		return CombatConfig.get().mobActionsV4;
	}

	// --- 1. Weapons from the floor --------------------------------------------------------------------

	/**
	 * What a weapon hits for, read off its attribute modifiers the way the broken mould rates them
	 * ({@link BrokenMould#damageOf}): the same number for a forged piece and a vanilla sword.
	 */
	public static double weaponDamage(ItemStack stack) {
		return stack == null || stack.isEmpty() ? 0.0 : BrokenMould.damageOf(stack);
	}

	/**
	 * Takes a better weapon lying within {@link CombatConfig#mobPickupRange} that the mob can see: within
	 * {@link #TAKE_REACH} it swaps it into its main hand and drops the old one, further away it walks to it. The
	 * weapon it takes always drops when it dies, so a player whose sword was picked up gets it back. Anything on the
	 * floor counts, the player's own dropped weapon included; only items still under their pickup delay are left,
	 * unless a player dropped them more than {@link #PLAYER_DROP_TICKS} ticks ago.
	 *
	 * @return true if it took a weapon or set off towards one
	 */
	public static boolean pickUpBetterWeapon(Mob mob) {
		if (!on() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		double range = CombatConfig.get().mobPickupRange;
		double have = weaponDamage(mob.getMainHandItem());
		ItemEntity best = null;
		double bestDamage = have;
		double bestDistance = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(range), e -> e.isAlive() && free(e))) {
			double distance = mob.distanceTo(item);
			if (distance > range) {
				continue;
			}
			double damage = weaponDamage(item.getItem());
			// Strictly better than what it holds; between two as good, the nearer.
			if (damage <= have + 1.0E-3 || damage < bestDamage - 1.0E-3
				|| Math.abs(damage - bestDamage) <= 1.0E-3 && distance >= bestDistance) {
				continue;
			}
			if (!mob.getSensing().hasLineOfSight(item)) {
				continue;
			}
			best = item;
			bestDamage = damage;
			bestDistance = distance;
		}
		if (best == null) {
			return false;
		}
		if (bestDistance > TAKE_REACH) {
			return mob.getNavigation().moveTo(best, 1.0);
		}
		ItemStack old = mob.getMainHandItem().copy();
		mob.take(best, 1);
		ItemStack taken = best.getItem().split(1);
		if (best.getItem().isEmpty()) {
			best.discard();
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, taken);
		// Always dropped on death, like a thief's loot (WorldFights): the player can win it back.
		mob.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
		mob.setPersistenceRequired();
		if (!old.isEmpty()) {
			ItemEntity dropped = new ItemEntity(level, mob.getX(), mob.getY(0.5), mob.getZ(), old);
			dropped.setPickUpDelay(DROPPED_DELAY);
			level.addFreshEntity(dropped);
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.8F, 0.9F);
		return true;
	}

	/** Whether an item on the floor may be taken: no pickup delay left, or a player's drop old enough. */
	private static boolean free(ItemEntity item) {
		return !item.hasPickUpDelay() || item.getOwner() instanceof Player && item.tickCount > PLAYER_DROP_TICKS;
	}

	// --- 2. Potions -----------------------------------------------------------------------------------

	/**
	 * Drinks a potion held in either hand (main hand first): its effects go on the mob, the bottle is used up.
	 * Instant ones (healing) land on the mob's next tick, as when a witch drinks.
	 *
	 * @return true if it drank
	 */
	public static boolean drinkPotion(Mob mob) {
		if (!on() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		InteractionHand hand = drinkable(mob.getMainHandItem()) ? InteractionHand.MAIN_HAND
			: drinkable(mob.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
		if (hand == null) {
			return false;
		}
		ItemStack stack = mob.getItemInHand(hand);
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		for (MobEffectInstance effect : contents.getAllEffects()) {
			// A copy: the potion's own instances are shared by every bottle of it.
			mob.addEffect(new MobEffectInstance(effect));
		}
		stack.shrink(1);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.HOSTILE, 1.0F, 1.0F);
		return true;
	}

	private static boolean drinkable(ItemStack stack) {
		return stack.is(Items.POTION) && stack.has(DataComponents.POTION_CONTENTS);
	}

	/**
	 * Throws the splash potion in the mob's off hand at the target, the way a witch throws (a lob that leads a
	 * moving target a little). One potion from the stack.
	 *
	 * @return true if it threw
	 */
	public static boolean throwSplash(Mob mob, LivingEntity target) {
		if (!on() || !mob.isAlive() || target == null || !target.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		ItemStack stack = mob.getOffhandItem();
		if (!stack.is(Items.SPLASH_POTION) || !mob.getSensing().hasLineOfSight(target)) {
			return false;
		}
		ThrownSplashPotion thrown = new ThrownSplashPotion(level, mob, stack.copyWithCount(1));
		Vec3 lead = target.getDeltaMovement();
		double dx = target.getX() + lead.x - mob.getX();
		double dy = target.getEyeY() - 1.1 - mob.getY();
		double dz = target.getZ() + lead.z - mob.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		thrown.setXRot(thrown.getXRot() + 20.0F);
		thrown.shoot(dx, dy + flat * 0.2, dz, 0.75F, 8.0F);
		level.addFreshEntity(thrown);
		stack.shrink(1);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0F, 0.8F + mob.getRandom().nextFloat() * 0.4F);
		return true;
	}

	// --- 3. Food --------------------------------------------------------------------------------------

	/**
	 * Eats the food in the mob's off hand when it is below half its health: it heals as many points as the food
	 * feeds a player (bread 5, steak 8). One item from the stack.
	 *
	 * @return true if it ate
	 */
	public static boolean eat(Mob mob) {
		if (!on() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		ItemStack stack = mob.getOffhandItem();
		var food = stack.get(DataComponents.FOOD);
		if (food == null || mob.getHealth() >= mob.getMaxHealth() * 0.5F) {
			return false;
		}
		mob.heal(food.nutrition());
		stack.shrink(1);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 1.0F, 1.0F);
		return true;
	}

	// --- 4. Ender pearls ------------------------------------------------------------------------------

	/**
	 * Throws an ender pearl from the mob's off hand towards a spot, owned by the mob. Vanilla's pearl already
	 * teleports whatever entity owns it when it lands, not only players (ThrownEnderpearl.onHit: a player gets the
	 * endermite chance and the fall damage, any other owner is simply moved), so no mixin is needed; the test
	 * {@code pearlMovesTheMob} checks it. The lob is aimed at 45 degrees with the speed that carries it that far
	 * under a thrown item's gravity, a little more for the air's drag.
	 *
	 * @return true if it threw one; false with no pearl in the off hand
	 */
	public static boolean throwPearl(Mob mob, Vec3 to) {
		if (!on() || !mob.isAlive() || to == null || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		ItemStack stack = mob.getOffhandItem();
		if (!stack.is(Items.ENDER_PEARL)) {
			return false;
		}
		ThrownEnderpearl pearl = new ThrownEnderpearl(level, mob, stack.copyWithCount(1));
		double dx = to.x - pearl.getX();
		double dz = to.z - pearl.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		double dy = to.y - pearl.getY();
		float speed = (float) Math.max(0.35, Math.sqrt(Math.max(1.0, flat) * PEARL_GRAVITY) * 1.15);
		pearl.shoot(dx, flat + dy, dz, speed, 0.0F);
		level.addFreshEntity(pearl);
		stack.shrink(1);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 0.5F, 0.4F);
		return true;
	}

	// --- 5. Lights ------------------------------------------------------------------------------------

	/**
	 * Whether a block is a light a monster may put out: a torch, a wall torch, a soul torch or a soul wall torch,
	 * and nothing else (Andy, 2026-09-29: no lanterns, no redstone or copper torches).
	 */
	public static boolean isLight(BlockState state) {
		return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH);
	}

	/**
	 * Breaks the light at {@code pos} if it is within {@link #LIGHT_REACH} of the mob's eyes, with a swing of its
	 * arm; the light drops as if a player broke it. Only with {@link CombatConfig#mobsBreakLights} on (off by
	 * default, until the v4 network decides it) and only with the mobGriefing game rule on: Andy, 2026-09-29,
	 * monsters break nothing else they did not break in vanilla, and torches are the one exception.
	 *
	 * @return true if it broke one
	 */
	public static boolean breakLight(Mob mob, BlockPos pos) {
		if (!on() || !CombatConfig.get().mobsBreakLights || pos == null || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)
			|| !Terrain.griefing(level)) {
			return false;
		}
		if (!isLight(level.getBlockState(pos)) || mob.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > LIGHT_REACH) {
			return false;
		}
		// Nothing solid between its eyes and the torch: no putting one out through a glass pane or a wall, so a closed
		// base keeps its lights as it keeps everything else.
		var hit = level.clip(new net.minecraft.world.level.ClipContext(mob.getEyePosition(), Vec3.atCenterOf(pos),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mob));
		if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && !hit.getBlockPos().equals(pos)) {
			return false;
		}
		mob.swing(InteractionHand.MAIN_HAND);
		return level.destroyBlock(pos, true, mob);
	}

	/**
	 * The light closest to the player within {@code radius} blocks of them (a cube), or null: the one whose
	 * darkness would hurt them most. The mob still has to walk there; {@link #breakLight} checks the reach.
	 */
	public static BlockPos nearestLight(Mob mob, Player target, int radius) {
		if (target == null || radius <= 0) {
			return null;
		}
		BlockPos centre = target.blockPosition();
		BlockPos best = null;
		double bestSq = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius), centre.offset(radius, radius, radius))) {
			if (!isLight(mob.level().getBlockState(pos))) {
				continue;
			}
			double sq = Vec3.atCenterOf(pos).distanceToSqr(target.position());
			if (sq < bestSq) {
				bestSq = sq;
				best = pos.immutable();
			}
		}
		return best;
	}

	// --- 6. Shield ------------------------------------------------------------------------------------

	/**
	 * Raises the shield in the mob's off hand while {@code threat} is aimed at it with a bow or crossbow drawn (or a
	 * loaded crossbow), or is winding up a blow close by; lowers it otherwise. The raising itself is
	 * {@link MobDefense#raise}, so a broken guard stays down and the parry window still starts when it goes up.
	 *
	 * @return true if the shield is up after the call
	 */
	public static boolean raiseShieldSmart(Mob mob, LivingEntity threat) {
		if (!on() || !MobDefense.hasShield(mob)) {
			return false;
		}
		boolean up = mob.isUsingItem() && mob.getUsedItemHand() == InteractionHand.OFF_HAND;
		if (threat != null && threat.isAlive() && threatens(threat, mob)) {
			MobDefense.raise(mob);
			return mob.isUsingItem() && mob.getUsedItemHand() == InteractionHand.OFF_HAND;
		}
		if (up) {
			mob.stopUsingItem();
		}
		return false;
	}

	/** Whether the threat is aimed at the mob and about to let go: a drawn bow or crossbow, or a blow wound up near. */
	public static boolean threatens(LivingEntity threat, Mob mob) {
		if (!aimedAt(threat, mob)) {
			return false;
		}
		return drawing(threat) || windingUp(threat) && threat.distanceTo(mob) <= WINDUP_RANGE;
	}

	private static boolean drawing(LivingEntity threat) {
		if (threat.isUsingItem() && (threat.getUseItem().getItem() instanceof BowItem || threat.getUseItem().getItem() instanceof CrossbowItem)) {
			return true;
		}
		ItemStack held = threat.getMainHandItem();
		return held.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(held);
	}

	/** A player charging a heavy blow (ChargedStrike), or a mob whose brain is winding one up. */
	private static boolean windingUp(LivingEntity threat) {
		if (ChargedStrike.isCharging(threat)) {
			return true;
		}
		MobMind mind = threat instanceof Mob other ? MobAi.mind(other) : null;
		return mind != null && mind.windup > 0;
	}

	private static boolean aimedAt(LivingEntity threat, Mob mob) {
		Vec3 to = mob.getEyePosition().subtract(threat.getEyePosition());
		if (to.lengthSqr() < 1.0E-6) {
			return true;
		}
		return to.normalize().dot(threat.getViewVector(1.0F)) > AIM_COS;
	}
}
