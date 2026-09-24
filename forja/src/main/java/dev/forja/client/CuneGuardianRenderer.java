package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.CuneGuardian;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The Guardian de Cuno. Only the seal cut into its face burns; the gold bands are just gold. */
public class CuneGuardianRenderer extends GeoEntityRenderer<CuneGuardian, LivingEntityRenderState> {
	public CuneGuardianRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.GUARDIAN_DE_CUNO);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(CuneGuardian mob, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
