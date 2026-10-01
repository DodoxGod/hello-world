package dev.forja.client;

import dev.forja.entity.WalkingAnvil;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The Yunque Andante. The seam in its waist is the only thing on it that glows. */
public class WalkingAnvilRenderer extends MobGeoRenderer<WalkingAnvil> {
	public WalkingAnvilRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.YUNQUE_ANDANTE);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}
}
