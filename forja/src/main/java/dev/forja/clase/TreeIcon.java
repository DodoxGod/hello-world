package dev.forja.clase;

import java.util.HashMap;
import java.util.Map;

import dev.forja.Forja;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What a tree node's {@code icono} (tools/arboles_datos.py) means: {@code "minecraft:x"} or {@code "forja:x"} is an
 * item, {@code "sprite:x"} is {@code textures/gui/arbol/x.png} (made by tools/arbol_iconos.py), and
 * {@code "clase:x"} is that class's emblem (the forged weapon that stands for it).
 */
public final class TreeIcon {
	private static final Map<String, ItemStack> STACKS = new HashMap<>();

	private TreeIcon() {
	}

	/** The texture of a "sprite:" icon, or null for any other kind. */
	public static @Nullable Identifier sprite(String icon) {
		return icon.startsWith("sprite:") ? Forja.id("textures/gui/arbol/" + icon.substring(7) + ".png") : null;
	}

	/** The stack of an item or class icon (cached; never modify it), or null for a sprite or an unknown icon. */
	public static @Nullable ItemStack stack(String icon) {
		ItemStack cached = STACKS.get(icon);
		if (cached != null) {
			return cached;
		}
		ItemStack made = null;
		if (icon.startsWith("clase:")) {
			PlayerClass clazz = PlayerClass.byId(icon.substring(6));
			made = clazz == null ? null : clazz.icon();
		} else if (!icon.startsWith("sprite:")) {
			Identifier id = Identifier.tryParse(icon);
			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				made = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
			}
		}
		if (made != null) {
			STACKS.put(icon, made);
		}
		return made;
	}

	/** Whether the icon resolves to a real item, class or texture file. */
	public static boolean valid(String icon) {
		Identifier sprite = sprite(icon);
		if (sprite != null) {
			return TreeIcon.class.getResource("/assets/" + sprite.getNamespace() + "/" + sprite.getPath()) != null;
		}
		return stack(icon) != null;
	}
}
