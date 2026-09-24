package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.forja.entity.ThrownHead;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/** Draws the flying head as its part item, spinning end over end along its flight direction. */
public class ThrownHeadRenderer extends EntityRenderer<ThrownHead, ThrownHeadRenderer.State> {
	private final ItemModelResolver itemModelResolver;

	public ThrownHeadRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float yaw;
		public float spin;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ThrownHead entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		this.itemModelResolver.updateForNonLiving(state.item, entity.getHead(), ItemDisplayContext.FIXED, entity);
		var motion = entity.getDeltaMovement();
		state.yaw = (float) Math.toDegrees(Math.atan2(motion.x, motion.z));
		state.spin = (entity.tickCount + partialTicks) * 45.0F;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-state.spin));
		poseStack.scale(0.9F, 0.9F, 0.9F);
		state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, camera);
	}
}
