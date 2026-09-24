package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.Striker;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Percutor: the vent and the visor glow, the rest is stone and iron. */
public class StrikerRenderer extends GeoEntityRenderer<Striker, LivingEntityRenderState> {
	public StrikerRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.PERCUTOR);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(Striker mob, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
