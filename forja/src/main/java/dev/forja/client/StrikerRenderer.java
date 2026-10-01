package dev.forja.client;

import dev.forja.entity.Striker;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The Percutor: the vent and the visor glow, the rest is stone and iron. */
public class StrikerRenderer extends MobGeoRenderer<Striker> {
	public StrikerRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.PERCUTOR);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}
}
