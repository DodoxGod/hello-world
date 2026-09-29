package dev.forja.registry;

import dev.forja.Forja;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The mod's own sound events. There is no audio of our own behind them: assets/forja/sounds.json puts
 * vanilla recordings together, pitched down, and tools/dimension_assets.py writes it. They have to be
 * registered because a dimension's ambience and music name them by id.
 */
public final class ModSounds {
	/** The Cementerio entre Estrellas: its hollow wind, its moods, the odd hammer or bell far off, and its music. */
	public static final SoundEvent CEMENTERIO_LOOP = register("ambient.cementerio.loop");
	public static final SoundEvent CEMENTERIO_MOOD = register("ambient.cementerio.mood");
	public static final SoundEvent CEMENTERIO_ADDITIONS = register("ambient.cementerio.additions");
	public static final SoundEvent CEMENTERIO_MUSIC = register("music.cementerio");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Forja.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
