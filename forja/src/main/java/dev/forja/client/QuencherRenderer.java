package dev.forja.client;

import dev.forja.entity.Quencher;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The Templador and his cart. Nothing on him glows; the oil is the darkest thing in the mod. */
public class QuencherRenderer extends MobGeoRenderer<Quencher> {
	public QuencherRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.TEMPLADOR);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}
}
