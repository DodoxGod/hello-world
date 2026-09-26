package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.forja.Forja;
import dev.forja.block.CrucibleBlock;
import dev.forja.block.entity.CrucibleBlockEntity;
import dev.forja.material.ForgeMaterial;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The metal in the mouth of a working crucible, in the metal's own colour.
 *
 * <p>The lit crucible's top is a texture, and a texture is one colour: every pot in the workshop glowed
 * the same orange whatever was in it, while the tanks beside it and the channels out of it were already
 * drawn in the colour of their metal. This lays the same scrolling melt the tank uses over the eight
 * pixels of the bowl, in {@link ForgeMaterial#molten} of whatever the pot is turning into — which the
 * block entity now tells the client, because until it did the client's copy of the pot was empty and
 * nothing out here could know.
 */
public class CrucibleRenderer implements BlockEntityRenderer<CrucibleBlockEntity, CrucibleRenderer.PotState> {
	private static final Identifier MELT = Forja.id("textures/block/colada.png");

	/** The bowl in the top texture: the middle eight pixels of sixteen. */
	private static final float BOWL_FROM = 4.0F / 16.0F;
	private static final float BOWL_TO = 12.0F / 16.0F;
	private static final float SURFACE = 1.0F + 0.003F;

	public CrucibleRenderer(BlockEntityRendererProvider.Context context) {
	}

	public static class PotState extends BlockEntityRenderState {
		public boolean lit;
		/** The molten colour, or -1 when the client has not been told what is in the pot. */
		public int colour = -1;
		public float time;
	}

	@Override
	public PotState createRenderState() {
		return new PotState();
	}

	@Override
	public void extractRenderState(CrucibleBlockEntity crucible, PotState state, float partialTick, Vec3 camera,
		ModelFeatureRenderer.@org.jspecify.annotations.Nullable CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(crucible, state, crumbling);
		state.lit = crucible.getBlockState().hasProperty(CrucibleBlock.LIT) && crucible.getBlockState().getValue(CrucibleBlock.LIT);
		int colour = crucible.meltColour();
		state.colour = colour < 0 ? -1 : ForgeMaterial.molten(colour);
		state.time = (System.currentTimeMillis() % 100000L) / 1000.0F;
	}

	@Override
	public void submit(PotState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.lit || state.colour < 0) {
			return;
		}
		float drift = state.time * 0.17F % 1.0F;
		// A slow swell, so the pot is never a still picture: a tenth either way, once every few seconds.
		float swell = 0.92F + 0.08F * (float) Math.sin(state.time * 1.7);
		int colour = scaled(state.colour, swell);
		collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(MELT, false), (p, buffer) ->
			flat(p, buffer, BOWL_FROM, SURFACE, BOWL_FROM, BOWL_TO, BOWL_TO, drift, colour, 240));
		// One bright band crossing it now and then, as it does the tanks.
		float sweep = (state.time * 0.5F % 2.2F) - 0.2F;
		if (sweep >= 0.0F && sweep <= 1.0F) {
			float from = Math.max(BOWL_FROM, across(sweep) - 0.06F);
			float to = Math.min(BOWL_TO, across(sweep) + 0.06F);
			if (to > from) {
				collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(MELT, false), (p, buffer) ->
					flat(p, buffer, from, SURFACE + 0.002F, BOWL_FROM, to, BOWL_TO, 0.0F, 0xFFFFFF, 110));
			}
		}
	}

	/** A share of the way across the block, brought into the bowl. */
	private static float across(float share) {
		return BOWL_FROM + (BOWL_TO - BOWL_FROM) * share;
	}

	private static void flat(PoseStack.Pose pose, VertexConsumer buffer,
		float x0, float y, float z0, float x1, float z1, float drift, int colour, int alpha) {
		put(pose, buffer, x0, y, z0, drift, 0.0F, colour, alpha);
		put(pose, buffer, x0, y, z1, drift, 0.5F, colour, alpha);
		put(pose, buffer, x1, y, z1, drift + 0.5F, 0.5F, colour, alpha);
		put(pose, buffer, x1, y, z0, drift + 0.5F, 0.0F, colour, alpha);
	}

	private static void put(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int colour, int alpha) {
		buffer.addVertex(pose, x, y, z)
			.setColor((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF, alpha)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static int scaled(int colour, float by) {
		int r = Math.min(255, Math.round(((colour >> 16) & 0xFF) * by));
		int g = Math.min(255, Math.round(((colour >> 8) & 0xFF) * by));
		int b = Math.min(255, Math.round((colour & 0xFF) * by));
		return r << 16 | g << 8 | b;
	}
}
