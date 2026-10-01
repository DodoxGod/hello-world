package dev.forja.client;

import dev.forja.entity.CoalHauler;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The Cargador de Carbon. The seams along its flanks and the two stacks are what glow. */
public class CoalHaulerRenderer extends MobGeoRenderer<CoalHauler> {
	public CoalHaulerRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.CARGADOR_DE_CARBON);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}
}
