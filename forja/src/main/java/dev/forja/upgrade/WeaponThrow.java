package dev.forja.upgrade;

import dev.forja.entity.ThrownHead;
import dev.forja.forge.ForgeType;
import dev.forja.registry.ModComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Throwing the whole weapon, not just its head. Crouch and use an axe or a dagger and it leaves your
 * hand for two thirds of its bite; with Retorno it comes back, and without it you walk over and pick
 * it up. A shield thrown the same way is a discus: it shoves and stuns up to three in a row and
 * always comes home.
 */
public final class WeaponThrow {
	/** How long the hand stays empty at most, matching the flight of the thrown item. */
	private static final int COOLDOWN = ThrownHead.MAX_LIFETIME;

	/** How long the trident takes to answer again after it has thrown you. */
	private static final int RIPTIDE_COOLDOWN = 30;

	private WeaponThrow() {
	}

	/** Whether this kind of weapon is one you can throw whole. */
	public static boolean throwable(ForgeType type) {
		return type == ForgeType.DAGA || type == ForgeType.HACHA || type == ForgeType.PICAHACHA || type == ForgeType.TRIDENTE;
	}

	/** Crouch and use: the axe or dagger flies. */
	public static InteractionResult tryThrowWeapon(Level level, Player player, InteractionHand hand, ForgeType type) {
		ItemStack stack = player.getItemInHand(hand);
		if (!throwable(type) || !player.isShiftKeyDown() || !stack.has(ModComponents.PARTS) || stack.isBroken()) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			boolean returns = serverLevel.getRandom().nextFloat() < Upgrade.returnChance(Upgrades.fraction(stack, Upgrade.RETORNO));
			ItemStack thrown = stack.copy();
			player.setItemInHand(hand, ItemStack.EMPTY);
			serverLevel.addFreshEntity(new ThrownHead(serverLevel, player, thrown, hand, ThrownHead.Mode.WEAPON, returns, 0));
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.1F);
		}
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}

	/**
	 * Corriente: standing in water or out in the rain, using the trident throws you the way you are
	 * looking, the way vanilla's Riptide does. Out of the water it does nothing at all.
	 */
	public static InteractionResult tryRiptide(Level level, Player player, InteractionHand hand, ForgeType type) {
		ItemStack stack = player.getItemInHand(hand);
		if (type != ForgeType.TRIDENTE || player.isShiftKeyDown() || !stack.has(ModComponents.PARTS) || stack.isBroken()) {
			return InteractionResult.PASS;
		}
		float power = Upgrade.riptidePower(Upgrades.fraction(stack, Upgrade.CORRIENTE));
		if (power <= 0.0F || !player.isInWaterOrRain()) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			net.minecraft.world.phys.Vec3 look = player.getLookAngle().scale(power * 0.35);
			player.setDeltaMovement(player.getDeltaMovement().add(look.x, Math.max(look.y, 0.25), look.z));
			player.hurtMarked = true;
			player.resetFallDistance();
			player.getCooldowns().addCooldown(stack, RIPTIDE_COOLDOWN);
			stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
			// Tempestad: a storm overhead answers the jump with a bolt where the smith was standing.
			if (Synergy.TEMPESTAD.active(stack) && serverLevel.isThundering()
				&& serverLevel.canSeeSky(player.blockPosition())) {
				var bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(serverLevel,
					net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.snapTo(player.getX(), player.getY(), player.getZ());
					bolt.setCause(player instanceof net.minecraft.server.level.ServerPlayer smith ? smith : null);
					serverLevel.addFreshEntity(bolt);
				}
			}
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
			serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_COLUMN_UP, player.getX(), player.getY(0.5), player.getZ(), 24, 0.4, 0.4, 0.4, 0.1);
		}
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}

	/** Use a grappling hook: the claw flies out, bites, and the rope pulls. */
	public static InteractionResult tryHook(Level level, Player player, InteractionHand hand) {
		ItemStack hook = player.getItemInHand(hand);
		dev.forja.part.ForgedParts parts = hook.get(ModComponents.PARTS);
		if (parts == null || parts.type() != ForgeType.GANCHO || hook.isBroken()) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(hook)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			// One claw at a time. The cooldown alone never enforced that: twenty-five ticks runs out
			// while the claw is still flying, biting and hauling, so holding the button down threw a
			// second hook over the top of the first.
			for (ThrownHead flying : serverLevel.getEntitiesOfClass(ThrownHead.class, player.getBoundingBox().inflate(64.0))) {
				if (flying.getOwner() == player && flying.mode() == ThrownHead.Mode.GARFIO && flying.isAlive()) {
					return InteractionResult.FAIL;
				}
			}
			double reach = dev.forja.forge.ForgeStats.hookReach(parts.material(parts.type().slotOf(dev.forja.part.PartType.Role.EXTRA)))
				+ Upgrade.ropeBlocks(Upgrades.fraction(hook, Upgrade.SOGA_LARGA));
			// More rope is a bigger winch: it reaches further and it drags harder for it.
			double pull = dev.forja.forge.ForgeStats.hookPull(parts.primary())
				* dev.forja.forge.ForgeStats.hookHaul((float) reach)
				* (1.0 + Upgrade.winchBonus(Upgrades.fraction(hook, Upgrade.SIRGA)));
			ItemStack claw = dev.forja.forge.Assembler.createPart(dev.forja.part.PartType.GARFIO, parts.primary());
			serverLevel.addFreshEntity(new ThrownHead(serverLevel, player, hook.copy(), claw, hand, 0).withRope(pull, reach));
			player.getCooldowns().addCooldown(hook, 25);
			hook.hurtAndBreak(1, player, hand.asEquipmentSlot());
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.9F, 0.9F);
		}
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}

	/** Crouch and use with nothing in front: the shield goes out and comes back. */
	public static InteractionResult tryThrowShield(Level level, Player player, InteractionHand hand) {
		ItemStack shield = player.getItemInHand(hand);
		int bounces = Upgrade.shieldBounces(Upgrades.fraction(shield, Upgrade.BUMERAN));
		if (bounces <= 0 || !shield.has(ModComponents.PARTS) || shield.isBroken()) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(shield)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel) {
			ItemStack thrown = shield.copy();
			player.setItemInHand(hand, ItemStack.EMPTY);
			serverLevel.addFreshEntity(new ThrownHead(serverLevel, player, thrown, hand, ThrownHead.Mode.SHIELD, true, bounces - 1));
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.4F);
		}
		player.getCooldowns().addCooldown(shield, COOLDOWN);
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}
}
