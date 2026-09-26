package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.HollowArmor;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The empty suit of plate, drawn by GeckoLib with the cold light showing through. */
public class HollowArmorRenderer extends GeoEntityRenderer<HollowArmor, LivingEntityRenderState> {
	public HollowArmorRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.CORAZA);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(HollowArmor armor, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
