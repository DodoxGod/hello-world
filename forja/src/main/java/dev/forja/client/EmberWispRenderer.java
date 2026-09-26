package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.EmberWisp;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Pavesa, drawn by GeckoLib: almost all of it is the glow layer. */
public class EmberWispRenderer extends GeoEntityRenderer<EmberWisp, LivingEntityRenderState> {
	public EmberWispRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.PAVESA);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(EmberWisp wisp, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
