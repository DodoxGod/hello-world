package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.RustSwarm;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Herrumbre, drawn by GeckoLib. Nothing on it glows; it is rust. */
public class RustSwarmRenderer extends GeoEntityRenderer<RustSwarm, LivingEntityRenderState> {
	public RustSwarmRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.HERRUMBRE);
		this.withScale(0.9F);
	}

	@Override
	public LivingEntityRenderState createRenderState(RustSwarm swarm, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
