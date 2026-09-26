package dev.forja.forge;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.forja.registry.ModComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * What a piece has done. The counters are kept on the item itself, so a weapon that has been carried
 * a long way says so, and a legend gathers its own story instead of being handed one.
 */
public record ItemHistory(int kills, int blocks, int flight, int fish) {
	public static final ItemHistory EMPTY = new ItemHistory(0, 0, 0, 0);

	public static final Codec<ItemHistory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.optionalFieldOf("bajas", 0).forGetter(ItemHistory::kills),
		Codec.INT.optionalFieldOf("bloques", 0).forGetter(ItemHistory::blocks),
		Codec.INT.optionalFieldOf("vuelo", 0).forGetter(ItemHistory::flight),
		Codec.INT.optionalFieldOf("peces", 0).forGetter(ItemHistory::fish)
	).apply(instance, ItemHistory::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ItemHistory> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ItemHistory::kills,
		ByteBufCodecs.VAR_INT, ItemHistory::blocks,
		ByteBufCodecs.VAR_INT, ItemHistory::flight,
		ByteBufCodecs.VAR_INT, ItemHistory::fish,
		ItemHistory::new
	);

	public static ItemHistory of(ItemStack stack) {
		return stack.getOrDefault(ModComponents.HISTORIA, EMPTY);
	}

	public static void addKill(ItemStack stack) {
		write(stack, history -> new ItemHistory(history.kills + 1, history.blocks, history.flight, history.fish));
	}

	public static void addBlocks(ItemStack stack, int count) {
		write(stack, history -> new ItemHistory(history.kills, history.blocks + count, history.flight, history.fish));
	}

	public static void addFlight(ItemStack stack, int ticks) {
		write(stack, history -> new ItemHistory(history.kills, history.blocks, history.flight + ticks, history.fish));
	}

	public static void addFish(ItemStack stack) {
		write(stack, history -> new ItemHistory(history.kills, history.blocks, history.flight, history.fish + 1));
	}

	private static void write(ItemStack stack, java.util.function.UnaryOperator<ItemHistory> change) {
		if (stack.has(ModComponents.PARTS)) {
			stack.set(ModComponents.HISTORIA, change.apply(of(stack)));
		}
	}

	/** The lines the tooltip shows, leaving out whatever this piece has never done. */
	public List<Component> lines() {
		List<Component> lines = new ArrayList<>();
		if (this.kills > 0) {
			lines.add(Component.translatable("tooltip.forja.historia.bajas", this.kills));
		}
		if (this.blocks > 0) {
			lines.add(Component.translatable("tooltip.forja.historia.bloques", this.blocks));
		}
		if (this.flight >= 20) {
			lines.add(Component.translatable("tooltip.forja.historia.vuelo", this.flight / 20));
		}
		if (this.fish > 0) {
			lines.add(Component.translatable("tooltip.forja.historia.peces", this.fish));
		}
		return lines;
	}

	public boolean isEmpty() {
		return this.kills == 0 && this.blocks == 0 && this.flight < 20 && this.fish == 0;
	}
}
