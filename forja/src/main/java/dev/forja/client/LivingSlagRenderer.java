package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.LivingSlag;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * The Escoria Viviente, drawn at whatever size it happens to be.
 *
 * <p>One model, three sizes: a half is the same lump smaller rather than a second model, which is
 * both cheaper and right — it is the same stuff, there is just less of it.
 */
public class LivingSlagRenderer extends GeoEntityRenderer<LivingSlag, LivingEntityRenderState> {
	public LivingSlagRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.ESCORIA);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(LivingSlag slag, Void relatedObject) {
		LivingEntityRenderState state = new LivingEntityRenderState();
		state.scale = 0.45F + slag.size() / (float) LivingSlag.BIG * 0.55F;
		return state;
	}
}
