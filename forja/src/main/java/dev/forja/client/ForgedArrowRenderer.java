package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.forja.Forja;
import dev.forja.entity.ForgedArrow;
import dev.forja.material.ForgeMaterial;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.ArrowModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * A forged arrow in flight or stuck in something: vanilla's arrow with its head drawn apart, in the colour of
 * the tip's material (Andy, 2026-09-30: every arrow looked the same). The textures are vanilla's arrow split in
 * two by tools/generate_assets.py (generate_arrow_entity_textures): the shaft and fletching as they are, and the
 * head alone in grey, tinted here.
 */
public class ForgedArrowRenderer extends ArrowRenderer<ForgedArrow, ForgedArrowRenderer.State> {
	private static final Identifier SHAFT = Forja.id("textures/entity/flecha_forjada/asta.png");
	private static final Identifier TIP = Forja.id("textures/entity/flecha_forjada/punta.png");
	/** A tip with no material (a bare /summon): iron grey, as vanilla's. */
	private static final int PLAIN = 0xFFB4B4B4;

	private final ArrowModel tipModel;

	public static final class State extends ArrowRenderState {
		public int tipColour = PLAIN;
	}

	public ForgedArrowRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.tipModel = new ArrowModel(context.bakeLayer(ModelLayers.ARROW));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected Identifier getTextureLocation(State state) {
		return SHAFT;
	}

	@Override
	public void extractRenderState(ForgedArrow arrow, State state, float partialTick) {
		super.extractRenderState(arrow, state, partialTick);
		ForgeMaterial tip = arrow.tipMaterial();
		state.tipColour = tip == null ? PLAIN : 0xFF000000 | tip.color;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		// The head, on the same turns ArrowRenderer puts the shaft on, in the tip's colour.
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot));
		collector.submitModel(this.tipModel, state, poseStack, RenderTypes.entityCutout(TIP), state.lightCoords, OverlayTexture.NO_OVERLAY,
			state.tipColour, null, state.outlineColor, null);
		poseStack.popPose();
	}
}
