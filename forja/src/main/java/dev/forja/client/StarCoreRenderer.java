package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.StarCore;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * The Nucleo Estelar, drawn in the colour of how full it is.
 *
 * <p>This is the first of Andy's two tells and the cheap one: the whole model is tinted, cold blue
 * when it is empty and white when it is about to let go, so a core that has been fed reads as a
 * different object across a room. The second tell — the shards swinging out and the spin going up —
 * is in the animation, and it is there because colour on its own fails in the dark and fails for a
 * colour-blind player, and "stop hitting this" is not a thing you can afford to miss.
 */
public class StarCoreRenderer extends GeoEntityRenderer<StarCore, LivingEntityRenderState> {
	public StarCoreRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.NUCLEO_ESTELAR);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public int getRenderColor(StarCore core, Void relatedObject, float partialTick) {
		return core.tint();
	}

	@Override
	public LivingEntityRenderState createRenderState(StarCore mob, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
