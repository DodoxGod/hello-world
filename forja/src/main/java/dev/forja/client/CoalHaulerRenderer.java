package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.CoalHauler;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Cargador de Carbon. The seams along its flanks and the two stacks are what glow. */
public class CoalHaulerRenderer extends GeoEntityRenderer<CoalHauler, LivingEntityRenderState> {
	public CoalHaulerRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.CARGADOR_DE_CARBON);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(CoalHauler mob, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
