package dev.forja.forge;

import java.util.Locale;

import dev.forja.registry.ModComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Temple: a piece comes off the star still hot. For one minute you can put that heat out in something,
 * and whatever you choose stays in the steel for good. One quench per piece, and it can never be
 * changed, so the choice belongs to the moment it was made.
 */
public enum Temple {
	/** Water: a tight, tough grain that wears far more slowly. */
	AGUA(0x4FA3D1),
	/** Lava: the edge keeps the fire it was cooled in. */
	LAVA(0xE2622B),
	/** Powder snow: the cold bites into whoever it touches. */
	NIEVE(0xCFE9F5),
	/** Honey: the blow sticks, and what it hits can barely move. */
	MIEL(0xE8A93C);

	/** How long a fresh piece stays hot. */
	public static final int HOT_TICKS = 1200;

	/** The share of wear a water quench simply skips. */
	public static final float WATER_WEAR_SKIP = 0.10F;

	public final int color;

	Temple(int color) {
		this.color = color;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public Component displayName() {
		return Component.translatable("temple.forja." + this.id());
	}

	public Component description() {
		return Component.translatable("temple.forja." + this.id() + ".desc");
	}

	/** The quench on a piece, if it has one and still works. */
	public static @Nullable Temple of(ItemStack stack) {
		String id = stack.get(ModComponents.TEMPLE);
		if (id == null || stack.isBroken()) {
			return null;
		}
		for (Temple temple : values()) {
			if (temple.id().equals(id)) {
				return temple;
			}
		}
		return null;
	}

	public static boolean has(ItemStack stack, Temple temple) {
		return of(stack) == temple;
	}

	/** Marks a piece as just forged, and so open to a quench for the next minute. */
	public static void markHot(ItemStack stack, ServerLevel level) {
		if (stack.has(ModComponents.PARTS) && !stack.has(ModComponents.TEMPLE)) {
			stack.set(ModComponents.CALIENTE, level.getGameTime() + HOT_TICKS);
		}
	}

	/** Whether the piece is still hot enough to take a quench. */
	public static boolean hot(ItemStack stack, long gameTime) {
		Long until = stack.get(ModComponents.CALIENTE);
		return until != null && gameTime < until && !stack.has(ModComponents.TEMPLE);
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 5 != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				// Only what you are holding or wearing can be put out; the rest of the bag stays hot.
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					ItemStack stack = player.getItemBySlot(slot);
					if (hot(stack, level.getGameTime())) {
						tryQuench(level, player, stack);
					}
				}
			}
		});
	}

	private static void tryQuench(ServerLevel level, ServerPlayer player, ItemStack stack) {
		Temple temple = around(level, player.blockPosition(), player.isInWater(), player.isInLava());
		if (temple == null) {
			return;
		}
		stack.set(ModComponents.TEMPLE, temple.id());
		stack.remove(ModComponents.CALIENTE);
		player.sendOverlayMessage(Component.translatable("gui.forja.temple", temple.displayName()));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
		// Steam off the blade, not a cloud round the smith: it goes up and it is gone.
		level.sendParticles(dev.forja.registry.ModParticles.VAPOR, player.getX(), player.getY(0.6), player.getZ(), 24, 0.3, 0.3, 0.3, 0.07);
		dev.forja.ForjaAdvancements.award(player, "temple");
	}

	/** What the smith is standing in, of the four things a quench can be done in. */
	private static @Nullable Temple around(ServerLevel level, BlockPos pos, boolean water, boolean lava) {
		if (lava) {
			return LAVA;
		}
		if (water) {
			return AGUA;
		}
		BlockState state = level.getBlockState(pos);
		BlockState below = level.getBlockState(pos.below());
		if (state.is(Blocks.POWDER_SNOW) || below.is(Blocks.POWDER_SNOW)) {
			return NIEVE;
		}
		if (state.is(Blocks.HONEY_BLOCK) || below.is(Blocks.HONEY_BLOCK)) {
			return MIEL;
		}
		return null;
	}
}
