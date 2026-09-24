package dev.forja.item;

import dev.forja.part.PartType;
import net.minecraft.world.item.Item;

/** A loose part: head, handle, blade, plate... Its material lives in the forja:material component. */
public class PartItem extends Item {
	public final PartType type;

	public PartItem(PartType type, Item.Properties properties) {
		super(properties);
		this.type = type;
	}
}
