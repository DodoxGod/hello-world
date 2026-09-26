package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.BrokenMould;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Molde Roto: the furnace in its belly, its eye and the blank it is holding all burn. */
public class BrokenMouldRenderer extends GeoEntityRenderer<BrokenMould, LivingEntityRenderState> {
	public BrokenMouldRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.MOLDE_ROTO);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(BrokenMould mob, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
