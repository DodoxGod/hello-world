package dev.forja.client;

import dev.forja.entity.HollowArmor;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The empty suit of plate, drawn by GeckoLib with the cold light showing through. */
public class HollowArmorRenderer extends MobGeoRenderer<HollowArmor> {
	public HollowArmorRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.CORAZA);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}
}
