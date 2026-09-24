package dev.forja.world;

import dev.forja.entity.EmberWisp;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Catching a wisp in an empty lantern.
 *
 * <p>The catch only works while it is fed, which is the awkward part: fed is exactly when it is hitting
 * hardest and diving twice as often, so the lantern has to be brought out in the worst moment of the
 * fight rather than at the end of it. What you get back is a lamp that counts as lava under a forge
 * table, so the whole trip is worth making.
 */
public final class WispCatch {
	private WispCatch() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof EmberWisp wisp) || !player.getItemInHand(hand).is(Items.LANTERN)) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer smith) || !(level instanceof ServerLevel server)) {
				// The client only needs to know the hand went through.
				return InteractionResult.SUCCESS;
			}
			if (!wisp.isFed()) {
				smith.sendSystemMessage(Component.translatable("gui.forja.pavesa_apagada").withColor(0x9A9A9A));
				return InteractionResult.SUCCESS;
			}
			catchIt(server, smith, wisp, hand);
			return InteractionResult.SUCCESS;
		});
	}

	private static void catchIt(ServerLevel level, ServerPlayer smith, EmberWisp wisp, net.minecraft.world.InteractionHand hand) {
		ItemStack lantern = smith.getItemInHand(hand);
		lantern.shrink(1);
		ItemStack caught = new ItemStack(ModItems.FAROL_DE_PAVESA);
		if (!smith.getInventory().add(caught)) {
			smith.drop(caught, false);
		}
		level.playSound(null, wisp.getX(), wisp.getY(), wisp.getZ(), SoundEvents.LANTERN_PLACE, SoundSource.PLAYERS, 1.2F, 0.8F);
		level.playSound(null, wisp.getX(), wisp.getY(), wisp.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.sendParticles(ParticleTypes.FLAME, wisp.getX(), wisp.getY(0.5), wisp.getZ(), 24, 0.3, 0.3, 0.3, 0.05);
		smith.sendSystemMessage(Component.translatable("gui.forja.pavesa_atrapada").withColor(0xFFA83E));
		dev.forja.ForjaAdvancements.award(smith, "farol");
		// Taken, not killed: it leaves nothing behind and it does not go off.
		wisp.remove(Entity.RemovalReason.DISCARDED);
	}
}
