package dev.forja.ai;

import java.util.ArrayList;
import java.util.List;

import dev.forja.registry.ModComponents;
import dev.forja.upgrade.Upgrades;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.Vec3;

/**
 * Things on the floor worth picking up (docs/red_mob_v4_diseno.md §2.7, block O, and §4.7): better weapons, the
 * player's own included, a shield, consumables for the kit (MobKit) and, for a body, a fishing rod for the hook.
 *
 * <p><b>Value of a weapon</b> (one function, the simulator copies it): {@code damage × attack speed × (1 + 0.15 · extra
 * reach) × (1.3 if forged with upgrades)}, damage = 1 + its attack damage modifiers, speed = 4 + its attack speed
 * modifiers (an iron sword: 6 × 1.6 = 9.6). A bow is 3, a crossbow 3.8 ({@link #rangedValue}); each is worth
 * anything only to the shooters that can use it ({@link #canShoot}): a bow to a skeleton, a stray or a bogged, a crossbow
 * to a pillager or a piglin. To anyone else, empty-handed or not, 0: a zombie never takes up a bow (2026-09-30,
 * docs/mod_spec_v4c.md, 3; a zombie that turned archer would be new, and Andy's call). Empty hands are worth 0.
 *
 * <p><b>mejora</b> = what the thing would be worth in its hands − what it holds now; a shield to a mob without one and
 * a free off hand 3; a consumable its kit has room for 1; a rod to a body without a spare 0.5. Only things with
 * mejora &gt; 0 are "useful", and only those the mob may take now (no pickup delay left, or a player's drop older than
 * {@link MobActions#PLAYER_DROP_TICKS}).
 *
 * <p><b>RECOGER</b>: to the nearest useful thing; within {@link MobActions#TAKE_REACH} it stops, and {@link #TAKE_TICKS}
 * ticks later it has it. A weapon goes in its main hand and what it held to its spare slot (if free; else to the floor).
 * Whatever it picks up is a guaranteed drop when it dies, a consumable excepted (Andy's decision 6).
 */
public final class GroundItems {
	/** How far things on the floor are seen (block O). */
	public static final double RANGE = 12.0;
	/** Ticks it takes to pick something up, with its arm going down to it. */
	public static final int TAKE_TICKS = 10;
	/** The least time between two changes of weapon, in ticks. */
	public static final int SWAP_GAP = 40;
	/** What block O calls each thing: body weapon, ranged weapon, shield, consumable (a rod reads as a ranged weapon). */
	public static final int MELEE = 0;
	public static final int RANGED = 1;
	public static final int SHIELD = 2;
	public static final int CONSUMABLE = 3;
	public static final int ROD = 4;

	/** One thing on the floor as a mob sees it. */
	public record Seen(ItemEntity item, int type, double gain, boolean players) {
	}

	private GroundItems() {
	}

	// ---------------------------------------------------------------- values

	/** A body weapon's value (see the class). */
	public static double meleeValue(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0.0;
		}
		ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers == null) {
			return 0.0;
		}
		double damage = 0.0;
		double speed = 0.0;
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.modifier().operation() != AttributeModifier.Operation.ADD_VALUE) {
				continue;
			}
			if (entry.attribute().value() == Attributes.ATTACK_DAMAGE.value()) {
				damage += entry.modifier().amount();
			} else if (entry.attribute().value() == Attributes.ATTACK_SPEED.value()) {
				speed += entry.modifier().amount();
			}
		}
		if (damage <= 0.0) {
			return 0.0;
		}
		double value = (1.0 + damage) * Math.max(0.1, 4.0 + speed) * (1.0 + 0.15 * Reach.extra(stack));
		Upgrades upgrades = stack.get(ModComponents.UPGRADES);
		if (stack.has(ModComponents.PARTS) && upgrades != null && !upgrades.isEmpty()) {
			value *= 1.3;
		}
		return value;
	}

	/** A ranged weapon's value: a bow 3 (+25 % per level of Power is left out: the arrow does the counting), a crossbow 3.8. */
	public static double rangedValue(ItemStack stack) {
		if (stack.getItem() instanceof CrossbowItem) {
			return 3.8;
		}
		return stack.getItem() instanceof BowItem ? 3.0 : 0.0;
	}

	private static boolean ranged(ItemStack stack) {
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
	}

	/** Whether the mob fights with a bow or crossbow by nature (a skeleton, a pillager): only those value one. */
	public static boolean shooter(Mob mob) {
		return mob instanceof RangedAttackMob && (MobFamily.of(mob.getType()) == MobFamily.ARQUERO
			|| mob.getType() == net.minecraft.world.entity.EntityTypes.PILLAGER || mob.getType() == net.minecraft.world.entity.EntityTypes.PIGLIN);
	}

	/**
	 * Whether this mob shoots with this bow or crossbow: a bow only a skeleton, a stray or a bogged (their bow goal), a
	 * crossbow only a pillager or a piglin (their crossbow goal). A skeleton with a crossbow, or a pillager with a bow,
	 * would fight as a body with it in its hand (MobFamily.of, MobFamily.network), so it is worth nothing to them.
	 */
	public static boolean canShoot(Mob mob, ItemStack stack) {
		if (!(mob instanceof RangedAttackMob)) {
			return false;
		}
		if (stack.getItem() instanceof BowItem) {
			return MobFamily.of(mob.getType()) == MobFamily.ARQUERO;
		}
		return stack.getItem() instanceof CrossbowItem
			&& (mob.getType() == net.minecraft.world.entity.EntityTypes.PILLAGER || mob.getType() == net.minecraft.world.entity.EntityTypes.PIGLIN);
	}

	/** What a weapon is worth in this mob's hands (0 for one it cannot use). */
	public static double valueFor(Mob mob, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0.0;
		}
		if (ranged(stack)) {
			return canShoot(mob, stack) ? rangedValue(stack) : 0.0;
		}
		// A shooter holding its bow does not trade it for a blade: the network it runs on is the archer's.
		if (shooter(mob) && ranged(mob.getMainHandItem())) {
			return 0.0;
		}
		return meleeValue(stack);
	}

	/** What it holds is worth, the same way. */
	public static double heldValue(Mob mob) {
		ItemStack held = mob.getMainHandItem();
		return ranged(held) ? (canShoot(mob, held) ? rangedValue(held) : 0.0) : meleeValue(held);
	}

	/** Block O's type of a thing on the floor, or -1 for nothing a mob wants. */
	public static int typeOf(ItemStack stack) {
		if (stack.is(Items.FISHING_ROD)) {
			return ROD;
		}
		// A shield blocks and does not hit: Forja's blades block too (their guard), and they are weapons.
		if (stack.has(DataComponents.BLOCKS_ATTACKS) && meleeValue(stack) <= 0.0) {
			return SHIELD;
		}
		if (ranged(stack)) {
			return RANGED;
		}
		if (MobKit.kindOf(stack) != null) {
			return CONSUMABLE;
		}
		return meleeValue(stack) > 0.0 ? MELEE : -1;
	}

	/** mejora: how much better off the mob would be with it (see the class); 0 or less for no use. */
	public static double gain(Mob mob, ItemStack stack) {
		int type = typeOf(stack);
		switch (type) {
			case MELEE, RANGED -> {
				return valueFor(mob, stack) - heldValue(mob);
			}
			case SHIELD -> {
				return !MobDefense.hasShield(mob) && mob.getOffhandItem().isEmpty() && !shooter(mob) ? 3.0 : 0.0;
			}
			case CONSUMABLE -> {
				MobKit.Kind kind = MobKit.kindOf(stack);
				return MobKit.count(mob, kind) < MobKit.MAX[kind.ordinal()] ? 1.0 : 0.0;
			}
			case ROD -> {
				return !shooter(mob) && MobKit.spare(mob).isEmpty() ? 0.5 : 0.0;
			}
			default -> {
				return 0.0;
			}
		}
	}

	/** Whether an item on the floor may be taken now (MobActions.free: no pickup delay left, or an old enough player's drop). */
	public static boolean free(ItemEntity item) {
		return !item.hasPickUpDelay() || item.getOwner() instanceof Player && item.tickCount > MobActions.PLAYER_DROP_TICKS;
	}

	/** The useful things within {@link #RANGE}, nearest first (worked out once a tick per mob). */
	public static List<Seen> useful(Mob mob) {
		MobMind mind = MobAi.mind(mob);
		long now = mob.level().getGameTime();
		if (mind != null && mind.usefulAt == now && mind.useful != null) {
			return mind.useful;
		}
		List<Seen> out = find(mob);
		if (mind != null) {
			mind.usefulAt = now;
			mind.useful = out;
		}
		return out;
	}

	private static List<Seen> find(Mob mob) {
		List<Seen> out = new ArrayList<>();
		List<ItemEntity> items = mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(RANGE),
			e -> e.isAlive() && free(e));
		items.sort(java.util.Comparator.comparingDouble(mob::distanceToSqr));
		for (ItemEntity item : items) {
			if (mob.distanceTo(item) > RANGE) {
				continue;
			}
			ItemStack stack = item.getItem();
			int type = typeOf(stack);
			if (type < 0) {
				continue;
			}
			double gain = gain(mob, stack);
			if (gain <= 0.0) {
				continue;
			}
			out.add(new Seen(item, type, gain, item.getOwner() instanceof Player));
			if (out.size() == 2) {
				break;
			}
		}
		return out;
	}

	/** Whether the thing can be walked to: within 3 up or down and in sight (RECOGER's "con ruta", cheaply). */
	public static boolean reachable(Mob mob, ItemEntity item) {
		return Math.abs(item.getY() - mob.getY()) <= 3.0 && mob.getSensing().hasLineOfSight(item);
	}

	/** The twenty of block O, written from {@code at}. */
	public static void observe(Mob mob, Player player, float[] out, int at) {
		List<Seen> seen = useful(mob);
		double dx = player.getX() - mob.getX();
		double dz = player.getZ() - mob.getZ();
		double d = Math.max(1.0E-6, Math.hypot(dx, dz));
		double fx = dx / d;
		double fz = dz / d;
		for (int k = 0; k < seen.size(); k++) {
			Seen s = seen.get(k);
			int i = at + k * 10;
			double ox = s.item.getX() - mob.getX();
			double oz = s.item.getZ() - mob.getZ();
			out[i] = 1.0F;
			out[i + 1] = (float) ObsM1.clip((ox * fx + oz * fz) / 8.0, -2.0, 2.0);
			out[i + 2] = (float) ObsM1.clip((ox * -fz + oz * fx) / 8.0, -2.0, 2.0);
			out[i + 3] = (float) ObsM1.clip((s.item.getY() - mob.getY()) / 4.0, -2.0, 2.0);
			out[i + 4] = (float) ObsM1.clip(s.gain / 10.0, -2.0, 2.0);
			out[i + 5] = s.players ? 1.0F : 0.0F;
			out[i + 6 + Math.min(3, s.type == ROD ? RANGED : s.type)] = 1.0F;
		}
	}

	/** RECOGER's mask: the nearest useful thing can be walked to. */
	public static boolean canPickUp(Mob mob) {
		List<Seen> seen = useful(mob);
		return !seen.isEmpty() && reachable(mob, seen.get(0).item);
	}

	// ---------------------------------------------------------------- taking it

	/**
	 * RECOGER, one tick: to the nearest useful thing and, next to it, the {@link #TAKE_TICKS} it takes to pick it up.
	 * Returns false when there is nothing (left) to pick up.
	 */
	public static boolean tick(Mob mob, MobMind mind) {
		if (mind.pickupItem != null && (!mind.pickupItem.isAlive() || gain(mob, mind.pickupItem.getItem()) <= 0.0)) {
			mind.pickupItem = null;
			mind.pickupTicks = 0;
		}
		if (mind.pickupItem == null) {
			List<Seen> seen = useful(mob);
			if (seen.isEmpty()) {
				return false;
			}
			mind.pickupItem = seen.get(0).item;
			mind.pickupTicks = 0;
		}
		ItemEntity item = mind.pickupItem;
		if (mob.distanceTo(item) > MobActions.TAKE_REACH) {
			mind.pickupTicks = 0;
			if (mind.pathDue(mob.level().getGameTime())) {
				mob.getNavigation().moveTo(item.getX(), item.getY(), item.getZ(), 1.1);
			}
			return true;
		}
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(item.getX(), item.getY(), item.getZ());
		if (mind.pickupTicks == 0) {
			mob.swing(InteractionHand.MAIN_HAND);
		}
		if (++mind.pickupTicks < TAKE_TICKS) {
			return true;
		}
		take(mob, item);
		mind.pickupItem = null;
		mind.pickupTicks = 0;
		return true;
	}

	/** Takes a thing off the floor now (it must be useful): into the hand, the off hand, the kit or the spare slot. */
	public static boolean take(Mob mob, ItemEntity item) {
		if (!(mob.level() instanceof ServerLevel level) || !item.isAlive()) {
			return false;
		}
		ItemStack stack = item.getItem();
		int type = typeOf(stack);
		if (type < 0 || gain(mob, stack) <= 0.0) {
			return false;
		}
		switch (type) {
			case MELEE, RANGED -> {
				ItemStack old = mob.getMainHandItem().copy();
				float oldChance = mob.getDropChances().byEquipment(EquipmentSlot.MAINHAND);
				boolean oldPicked = mob.getDropChances().isPreserved(EquipmentSlot.MAINHAND);
				mob.take(item, 1);
				ItemStack taken = stack.split(1);
				mob.setItemSlot(EquipmentSlot.MAINHAND, taken);
				mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
				if (!old.isEmpty()) {
					if (MobKit.spare(mob).isEmpty()) {
						// what it held goes to its spare slot, keeping its own chance to drop (a picked-up one: always)
						MobKit.setSpare(mob, old, oldPicked, oldChance);
					} else {
						ItemEntity dropped = new ItemEntity(level, mob.getX(), mob.getY(0.5), mob.getZ(), old);
						dropped.setPickUpDelay(MobActions.DROPPED_DELAY);
						level.addFreshEntity(dropped);
					}
				}
				MobMind mind = MobAi.mind(mob);
				if (mind != null) {
					mind.swapAt = level.getGameTime();
				}
			}
			case SHIELD -> {
				mob.take(item, 1);
				mob.setItemSlot(EquipmentSlot.OFFHAND, stack.split(1));
				mob.setGuaranteedDrop(EquipmentSlot.OFFHAND);
			}
			case ROD -> {
				mob.take(item, 1);
				MobKit.setSpare(mob, stack.split(1), true, 1.0F);
			}
			case CONSUMABLE -> {
				MobKit.Kind kind = MobKit.kindOf(stack);
				int in = MobKit.add(mob, kind, MobKit.variantOf(stack), stack.getCount());
				if (in <= 0) {
					return false;
				}
				mob.take(item, in);
				stack.shrink(in);
			}
			default -> {
				return false;
			}
		}
		if (stack.isEmpty()) {
			item.discard();
		}
		mob.setPersistenceRequired();
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 0.8F, 0.9F);
		return true;
	}

	/** Where the thing it is going for lies, for the tests and the debug view; null for none. */
	public static Vec3 going(MobMind mind) {
		return mind.pickupItem == null ? null : mind.pickupItem.position();
	}
}
