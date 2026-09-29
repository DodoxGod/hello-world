package dev.forja.client;

import dev.forja.entity.RustSwarm;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The Herrumbre, drawn by GeckoLib. Nothing on it glows; it is rust. */
public class RustSwarmRenderer extends MobGeoRenderer<RustSwarm> {
	public RustSwarmRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.HERRUMBRE);
		this.withScale(0.9F);
	}
}
