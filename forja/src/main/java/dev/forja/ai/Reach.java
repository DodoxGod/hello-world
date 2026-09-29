package dev.forja.ai;

import dev.forja.combat.CombatConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.AABB;

/**
 * How far a blow reaches, for a monster and for the player it fights. A monster's is its body's
 * ({@link ObsM1#REACH}, the gap between the two boxes) plus whatever the weapon in its hand adds: the
 * very number the network is shown as {@code yo_arma_alcance} (see {@link #of(ItemStack)}). A player's is
 * what {@code jug_alcance} shows. With nothing that adds reach in the hand, every number here is the one
 * the monsters always used, so vanilla's weapons change nothing.
 *
 * <p>It is worked out from the held stack each time it is asked: a component lookup and a walk over the
 * few attribute modifiers a weapon carries, cheaper than a cache keyed on the mob would be.
 */
public final class Reach {
	/** A player's reach with nothing in hand that adds to it: the entity_interaction_range attribute's base. */
	public static final double PLAYER_BASE = 3.0;
	/**
	 * How close to its full reach a monster with a long weapon stops walking in: it holds there and strikes,
	 * rather than walking up to the player's face and standing inside its own reach.
	 */
	public static final double STOP_SHORT = 0.3;
	/** A spear's point is useless this much inside its shortest reach: that is where it steps back from. */
	public static final double TOO_CLOSE_MARGIN = 0.2;
	/** Centre to centre, how close a thrusting weapon with no shortest reach of its own is too close (the old rule). */
	public static final double TOO_CLOSE_FALLBACK = 1.8;

	private Reach() {
	}

	/**
	 * What the weapon adds to the body's reach: its entity_interaction_range modifiers (scythe 0.75, trident
	 * 0.5, flail 3, Alcance...) and, with an attack_range component (spears, lances), max_reach − 3. The
	 * contract's {@code yo_arma_alcance}, unchanged: 0 for anything that adds nothing, a vanilla sword too.
	 */
	public static double extra(ItemStack held) {
		if (held.isEmpty()) {
			return 0.0;
		}
		double extra = 0.0;
		var modifiers = held.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (modifiers != null) {
			for (var entry : modifiers.modifiers()) {
				if (entry.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
					&& entry.slot().test(EquipmentSlot.MAINHAND)) {
					extra += entry.modifier().amount();
				}
			}
		}
		AttackRange range = held.get(DataComponents.ATTACK_RANGE);
		if (range != null) {
			extra += Math.max(0.0, range.maxReach() - 3.0);
		}
		return extra;
	}

	/** How far a blow with this in the hand reaches: the gap between the boxes, as {@link ObsM1#reaches}. */
	public static double of(ItemStack held) {
		return ObsM1.REACH + extra(held);
	}

	/** How far this mob's blow reaches, with what it holds. */
	public static double of(LivingEntity mob) {
		return of(mob.getMainHandItem());
	}

	public static double extra(LivingEntity mob) {
		return extra(mob.getMainHandItem());
	}

	/**
	 * The gap inside which the weapon cannot strike: a spear's or a lance's min_reach, scaled for a monster
	 * the way vanilla scales it (mob_factor: 2 × 0.5 = 1 block). 0 for everything else.
	 */
	public static double min(ItemStack held) {
		AttackRange range = held.isEmpty() ? null : held.get(DataComponents.ATTACK_RANGE);
		return range == null ? 0.0 : range.minReach() * range.mobFactor();
	}

	public static double min(LivingEntity mob) {
		return min(mob.getMainHandItem());
	}

	/** The flat gap between the two boxes (0 when they overlap). */
	public static double gap(LivingEntity mob, LivingEntity target) {
		AABB a = mob.getBoundingBox();
		AABB b = target.getBoundingBox();
		double gx = Math.max(0.0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
		double gz = Math.max(0.0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
		return Math.hypot(gx, gz);
	}

	/**
	 * Whether this mob's blow reaches the target, with the weapon it holds: as {@link ObsM1#reaches} with its
	 * own reach, and not inside a spear's shortest reach.
	 */
	public static boolean reaches(Mob mob, LivingEntity target) {
		ItemStack held = mob.getMainHandItem();
		if (!ObsM1.reaches(mob, target, of(held))) {
			return false;
		}
		double min = min(held);
		return min <= 0.0 || gap(mob, target) > min;
	}

	/**
	 * How far, centre to centre, a blow that was wound up may still land: the old tolerance (twice the mob's
	 * width, half the target's and strikeReachBonus) plus what the weapon adds.
	 */
	public static double landing(Mob mob, LivingEntity target) {
		return mob.getBbWidth() * 2.0 + target.getBbWidth() * 0.5 + CombatConfig.get().strikeReachBonus + extra(mob);
	}

	/**
	 * Whether a mob with a weapon that reaches further than its body has walked in far enough: within its
	 * reach less {@link #STOP_SHORT}. Always false for one whose weapon adds nothing, which walks in as before.
	 */
	public static boolean closeEnough(Mob mob, LivingEntity target) {
		ItemStack held = mob.getMainHandItem();
		double extra = extra(held);
		return extra > 0.0 && ObsM1.reaches(mob, target, ObsM1.REACH + extra - STOP_SHORT) && gap(mob, target) > min(held);
	}

	/**
	 * Whether a thrusting weapon is too close to use its point: inside its shortest reach and a little, or,
	 * for one with none of its own, closer than {@link #TOO_CLOSE_FALLBACK} centre to centre.
	 */
	public static boolean tooClose(Mob mob, LivingEntity target) {
		double min = min(mob);
		return min > 0.0 ? gap(mob, target) < min + TOO_CLOSE_MARGIN : mob.distanceTo(target) < TOO_CLOSE_FALLBACK;
	}

	/**
	 * The player's real reach, as {@code jug_alcance} shows it: the entity_interaction_range attribute (3,
	 * plus the scythe's, the flail's, Alcance...), or the lance's attack_range max_reach if that is longer.
	 */
	public static double player(Player player) {
		double reach = player.getAttributes().hasAttribute(Attributes.ENTITY_INTERACTION_RANGE)
			? player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) : PLAYER_BASE;
		AttackRange range = player.getMainHandItem().get(DataComponents.ATTACK_RANGE);
		if (range != null) {
			reach = Math.max(reach, range.maxReach());
		}
		return reach;
	}

	/** How much further than a bare hand the player reaches right now (0 with a sword). */
	public static double playerExtra(Player player) {
		return Math.max(0.0, player(player) - PLAYER_BASE);
	}

	/**
	 * A distance from the player chosen to keep a monster out of a bare-handed player's reach, pushed out by
	 * however much further this player reaches: the same room to spare against a flail or a lance as the
	 * distance had against a sword. The same as {@code max(distance, reach + (distance − 3))}.
	 */
	public static double outside(Player player, double distance) {
		return distance + playerExtra(player);
	}
}
