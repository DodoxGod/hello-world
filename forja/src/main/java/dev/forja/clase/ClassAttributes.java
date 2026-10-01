package dev.forja.clase;

import dev.forja.Forja;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * The class's attribute stats as attribute modifiers: one per stat, {@code forja:clase_<stat>}, with the
 * total of the class's base and its learned talents. They are permanent modifiers, so a Tanque who logs out
 * at 32 health comes back at 32 rather than clamped to 20 before the class is read; and they are rewritten
 * whole every time anything changes, so nothing is ever left over from a class the player no longer has.
 */
public final class ClassAttributes {
	private ClassAttributes() {
	}

	public static Identifier modifierId(ClassStat stat) {
		return Forja.id("clase_" + stat.id());
	}

	/** Puts the modifiers on to match the player's class and talents, and takes off any that no longer apply. */
	public static void sync(ServerPlayer player) {
		for (ClassStat stat : ClassStat.values()) {
			if (stat.attribute == null || stat.operation == null) {
				continue;
			}
			AttributeInstance instance = player.getAttribute(stat.attribute);
			if (instance == null) {
				continue;
			}
			float value = ClassEffects.stat(player, stat);
			Identifier id = modifierId(stat);
			AttributeModifier current = instance.getModifier(id);
			if (Math.abs(value) < 1.0E-4F) {
				if (current != null) {
					instance.removeModifier(id);
				}
				continue;
			}
			if (current == null || Math.abs(current.amount() - value) > 1.0E-4 || current.operation() != stat.operation) {
				instance.removeModifier(id);
				instance.addPermanentModifier(new AttributeModifier(id, value, stat.operation));
			}
		}
		// Less health than before: what the player had cannot stay above the new top.
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}
}
