package dev.forja.client;

import com.geckolib.renderer.GeoEntityRenderer;
import dev.forja.entity.ForgeAutomaton;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** The forge automaton, drawn by GeckoLib from its own model and animations. */
public class ForgeAutomatonRenderer extends GeoEntityRenderer<ForgeAutomaton, LivingEntityRenderState> {
	public ForgeAutomatonRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.AUTOMATA);
		// The fire in it glows through the dark: GeckoLib reads the _glowmask beside the texture.
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
	}

	@Override
	public LivingEntityRenderState createRenderState(ForgeAutomaton automaton, Void relatedObject) {
		return new LivingEntityRenderState();
	}
}
