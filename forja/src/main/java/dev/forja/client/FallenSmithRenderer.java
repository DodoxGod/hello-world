package dev.forja.client;

import dev.forja.entity.FallenSmith;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * The Fallen Smith, drawn by GeckoLib: the model, the animations and the texture live in
 * assets/forja/geo, assets/forja/animations and assets/forja/textures/entity.
 */
public class FallenSmithRenderer extends MobGeoRenderer<FallenSmith> {
	public FallenSmithRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.HERRERO_CAIDO);
		// The fire in it glows through the dark: GeckoLib reads the _glowmask beside the texture.
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}
}
