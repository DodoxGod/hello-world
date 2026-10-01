package dev.forja.upgrade;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import dev.forja.registry.ModComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;

/** Progress of every upgrade on an item, 1..100 percent. Upgrades at 0 are simply absent. */
public record Upgrades(Map<Upgrade, Integer> percents) {
	public static final Upgrades EMPTY = new Upgrades(Map.of());
	private static final StreamCodec<ByteBuf, Upgrade> UPGRADE_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> Upgrade.values()[i], Enum::ordinal);

	public static final Codec<Upgrades> CODEC = Codec.unboundedMap(Upgrade.CODEC, Codec.intRange(1, 100)).xmap(Upgrades::new, Upgrades::percents);
	public static final StreamCodec<ByteBuf, Upgrades> STREAM_CODEC = ByteBufCodecs.<ByteBuf, Upgrade, Integer, Map<Upgrade, Integer>>map(
		size -> new EnumMap<>(Upgrade.class), UPGRADE_STREAM_CODEC, ByteBufCodecs.VAR_INT
	).map(Upgrades::new, Upgrades::percents);

	public Upgrades {
		EnumMap<Upgrade, Integer> sorted = new EnumMap<>(Upgrade.class);
		percents.forEach((upgrade, percent) -> {
			if (percent > 0) {
				sorted.put(upgrade, Math.min(100, percent));
			}
		});
		percents = Collections.unmodifiableMap(sorted);
	}

	public int percent(Upgrade upgrade) {
		return this.percents.getOrDefault(upgrade, 0);
	}

	public Upgrades with(Upgrade upgrade, int percent) {
		EnumMap<Upgrade, Integer> copy = new EnumMap<>(Upgrade.class);
		copy.putAll(this.percents);
		copy.put(upgrade, percent);
		return new Upgrades(copy);
	}

	public boolean isEmpty() {
		return this.percents.isEmpty();
	}

	/** Upgrade progress on one stack as a 0..1 fraction. */
	/** The same item with one upgrade set to a percentage. */
	public static net.minecraft.world.item.ItemStack with(net.minecraft.world.item.ItemStack stack, Upgrade upgrade, int percent) {
		// Copying an empty plain map into an EnumMap throws, so the type comes from the class, not the copy.
		EnumMap<Upgrade, Integer> percents = new EnumMap<>(Upgrade.class);
		percents.putAll(stack.getOrDefault(dev.forja.registry.ModComponents.UPGRADES, EMPTY).percents());
		percents.put(upgrade, percent);
		stack.set(dev.forja.registry.ModComponents.UPGRADES, new Upgrades(percents));
		return stack;
	}

	public static float fraction(ItemInstance stack, Upgrade upgrade) {
		if (stack instanceof ItemStack item && item.isBroken()) {
			return 0.0F;
		}
		return stack.getOrDefault(ModComponents.UPGRADES, EMPTY).percent(upgrade) / 100.0F;
	}

	/** Best fraction across the entity's armor pieces. */
	/**
	 * How much of a shove Anclaje takes out of whoever is wearing it.
	 *
	 * <p>Knockback resistance only covers blows. The mod's own mobs hook, haul and charge by moving you
	 * directly, which goes straight past the attribute, so every one of those asks here instead.
	 */
	public static float anchor(LivingEntity entity) {
		float hold = Upgrade.anchorShare(armorFraction(entity, Upgrade.ANCLAJE));
		// Firme: boots that hold and a head that will not be told how to feel about it.
		return steadfast(entity) ? Math.min(0.85F, hold + 0.25F) : hold;
	}

	/** Whether the boots carry Firme, which is the answer to being pulled around and to the wail. */
	public static boolean steadfast(LivingEntity entity) {
		return Synergy.FIRME.active(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
	}

	public static float armorFraction(LivingEntity entity, Upgrade upgrade) {
		float best = 0.0F;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			best = Math.max(best, fraction(entity.getItemBySlot(slot), upgrade));
		}
		return best;
	}
}
