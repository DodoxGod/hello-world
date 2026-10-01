package dev.forja.client;

import dev.forja.entity.EmberWisp;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The Pavesa, drawn by GeckoLib: almost all of it is the glow layer. */
public class EmberWispRenderer extends MobGeoRenderer<EmberWisp> {
	public EmberWispRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.PAVESA);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}
}
