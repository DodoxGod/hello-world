package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.GreaterEmber;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Ascua Mayor: the wisp's renderer at the wisp's scale, on something twice the size. */
public class GreaterEmberRenderer extends GeoEntityRenderer<GreaterEmber, LivingEntityRenderState> {
	public GreaterEmberRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.ASCUA_MAYOR);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(GreaterEmber ember, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
