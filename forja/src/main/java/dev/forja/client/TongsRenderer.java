package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.Tongs;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Tenaza, lit by whatever is inside the hinge. */
public class TongsRenderer extends GeoEntityRenderer<Tongs, LivingEntityRenderState> {
	public TongsRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.TENAZA);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(Tongs mob, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
