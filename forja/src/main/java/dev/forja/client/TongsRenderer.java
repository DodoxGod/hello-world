package dev.forja.client;

import dev.forja.entity.Tongs;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The Tenaza, lit by whatever is inside the hinge. */
public class TongsRenderer extends MobGeoRenderer<Tongs> {
	public TongsRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.TENAZA);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}
}
