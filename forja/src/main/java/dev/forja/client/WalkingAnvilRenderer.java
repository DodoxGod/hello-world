package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.WalkingAnvil;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Yunque Andante. The seam in its waist is the only thing on it that glows. */
public class WalkingAnvilRenderer extends GeoEntityRenderer<WalkingAnvil, LivingEntityRenderState> {
	public WalkingAnvilRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.YUNQUE_ANDANTE);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(WalkingAnvil mob, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
