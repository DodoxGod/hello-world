package dev.forja.upgrade;

import dev.forja.entity.ThrownHead;
import dev.forja.forge.Assembler;
import dev.forja.forge.ForgeType;
import dev.forja.part.ForgedParts;
import dev.forja.part.PartType;
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
 * Lanzacabezas: right-click throws the tool's head. It flies straight, mines up to 16 blocks the
 * tool can mine (scaled by the upgrade's percentage), cuts through mobs and flies back to the
 * handle. The tool is on cooldown, and drawn without its head, until the head returns.
 */
public final class HeadThrow {
	private HeadThrow() {
	}

	public static InteractionResult tryThrow(Level level, Player player, InteractionHand hand, ForgeType type) {
		ItemStack stack = player.getItemInHand(hand);
		ForgedParts parts = stack.get(ModComponents.PARTS);
		int blocks = Upgrade.headThrowBlocks(Upgrades.fraction(stack, Upgrade.LANZACABEZAS));
		if (!type.throwableHead || parts == null || blocks <= 0) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel serverLevel) {
			int headSlot = type.slotOf(PartType.Role.HEAD);
			ItemStack headStack = Assembler.createPart(type.slots.get(headSlot), parts.material(headSlot));
			serverLevel.addFreshEntity(new ThrownHead(serverLevel, player, stack.copy(), headStack, hand, blocks));
			player.getCooldowns().addCooldown(stack, ThrownHead.MAX_LIFETIME);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		player.swing(hand);
		return InteractionResult.SUCCESS;
	}
}
