package dev.forja.client;

import dev.forja.entity.GreaterEmber;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The Ascua Mayor: the wisp's renderer at the wisp's scale, on something twice the size. */
public class GreaterEmberRenderer extends MobGeoRenderer<GreaterEmber> {
	public GreaterEmberRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.ASCUA_MAYOR);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}
}
