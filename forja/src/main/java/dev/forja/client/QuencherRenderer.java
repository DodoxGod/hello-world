package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.Quencher;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Templador and his cart. Nothing on him glows; the oil is the darkest thing in the mod. */
public class QuencherRenderer extends GeoEntityRenderer<Quencher, LivingEntityRenderState> {
	public QuencherRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.TEMPLADOR);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(Quencher mob, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
