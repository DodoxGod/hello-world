package dev.forja.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who the reader has met, for book II's bestiary, which stays in shadow until then (docs/LIBROS_GUIA.md, Andy's
 * answer 7). Once a second it looks at the mod's creatures within {@link #RANGE} blocks that the player can
 * actually see — in the open, not through a wall — and remembers each kind the first time (BookMemory). The
 * raiders' captain is a pillager with a name, so it is known by that name.
 */
public final class CreatureSightings {
	/** How close a creature has to be to count as met, in blocks. */
	public static final double RANGE = 24.0;

	private CreatureSightings() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null || client.player.tickCount % 20 != 0) {
				return;
			}
			look(client);
		});
	}

	private static void look(Minecraft client) {
		boolean changed = false;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || living == client.player || !living.isAlive()
				|| living.distanceToSqr(client.player) > RANGE * RANGE || !client.player.hasLineOfSight(living)) {
				continue;
			}
			String id = id(living);
			if (id != null && BookMemory.sawCreature(id)) {
				changed = true;
			}
		}
		if (changed) {
			BookMemory.save();
		}
	}

	/** The bestiary's id for a creature: the mod's own by entity type, the captain by his name; null for the rest. */
	static String id(LivingEntity living) {
		Component name = living.getCustomName();
		if (name != null && name.getContents() instanceof TranslatableContents key && key.getKey().equals("entity.forja.capitan_saqueador")) {
			return "forja:capitan_saqueador";
		}
		var type = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
		return type.getNamespace().equals(dev.forja.Forja.MOD_ID) ? type.toString() : null;
	}
}
