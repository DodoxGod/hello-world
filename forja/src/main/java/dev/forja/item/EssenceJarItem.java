package dev.forja.item;

import dev.forja.registry.ModComponents;
import dev.forja.registry.ModItems;
import dev.forja.world.WorldEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Jarra de esencia: glass, worth a nether star to make, and worth it.
 *
 * <p>Held up under the open sky while one of the world events runs, it catches that night and keeps
 * it. Pour the full jar out and you get the orb for that event's upgrade.
 *
 * <p>It is two steps rather than one on purpose. An orb is an orb: nine of them on a shelf look like
 * nine of the same thing. A jar is glass, and it takes the colour of what is in it, so a row of them
 * reads as the nights you were outside for — a blood-red one, a green aurora, a pale one full of
 * meteor light. That is the whole reason the item exists, and it was invisible while the jar turned
 * into an orb the instant it was filled.
 */
public class EssenceJarItem extends Item {
	public EssenceJarItem(Item.Properties properties) {
		super(properties);
	}

	/** The night in this jar, or null when it is empty. */
	public static @Nullable WorldEvents held(ItemStack jar) {
		return jar.get(ModComponents.ESENCIA);
	}

	/** A jar with that night in it, coloured by it. */
	public static ItemStack filled(WorldEvents event) {
		ItemStack jar = new ItemStack(ModItems.JARRA);
		jar.set(ModComponents.ESENCIA, event);
		// "lleno" picks the model that has anything in it at all; the colour paints what is in it.
		jar.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
			java.util.List.of(), java.util.List.of(), java.util.List.of("lleno"), java.util.List.of(event.color)));
		return jar;
	}

	@Override
	public Component getName(ItemStack stack) {
		WorldEvents event = held(stack);
		return event == null
			? Component.translatable("item.forja.jarra")
			: Component.translatable("item.forja.jarra.de", event.displayName());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer smith)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack jar = smith.getItemInHand(hand);
		WorldEvents inside = held(jar);
		if (inside != null) {
			return this.pour(server, smith, jar, inside);
		}
		WorldEvents event = WorldEvents.active(server);
		if (event == null) {
			smith.sendOverlayMessage(Component.translatable("gui.forja.frasco.nada"));
			return InteractionResult.FAIL;
		}
		if (!WorldEvents.underOpenSky(server, smith.blockPosition())) {
			smith.sendOverlayMessage(Component.translatable("gui.forja.frasco.techo"));
			return InteractionResult.FAIL;
		}
		jar.shrink(1);
		this.give(smith, filled(event));
		server.playSound(null, smith.getX(), smith.getY(), smith.getZ(), SoundEvents.BOTTLE_FILL_DRAGONBREATH,
			net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
		server.sendParticles(ParticleTypes.END_ROD, smith.getX(), smith.getY(1.2), smith.getZ(), 30, 0.3, 0.5, 0.3, 0.1);
		smith.sendOverlayMessage(Component.translatable("gui.forja.frasco.lleno", event.upgrade.displayName()));
		dev.forja.ForjaAdvancements.award(smith, "evento");
		return InteractionResult.SUCCESS;
	}

	/** Emptying a full jar: the night comes out as the orb it always was. */
	private InteractionResult pour(ServerLevel server, ServerPlayer smith, ItemStack jar, WorldEvents inside) {
		jar.shrink(1);
		this.give(smith, UpgradeOrbItem.create(inside.upgrade, 100));
		server.playSound(null, smith.getX(), smith.getY(), smith.getZ(), SoundEvents.BOTTLE_EMPTY,
			net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
		server.sendParticles(ParticleTypes.END_ROD, smith.getX(), smith.getY(1.2), smith.getZ(), 16, 0.2, 0.3, 0.2, 0.05);
		smith.sendOverlayMessage(Component.translatable("gui.forja.jarra.vaciada", inside.upgrade.displayName()));
		return InteractionResult.SUCCESS;
	}

	private void give(ServerPlayer smith, ItemStack stack) {
		if (!smith.getInventory().add(stack)) {
			smith.drop(stack, false);
		}
	}
}
