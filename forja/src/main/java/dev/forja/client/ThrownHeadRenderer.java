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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws what flies as its item. Tool heads, axes and shields tumble end over end along their flight, the
 * boomerang look. Pointed weapons (trident, dagger, spear) fly tip first instead, aimed along their
 * motion like an arrow.
 */
public class ThrownHeadRenderer extends EntityRenderer<ThrownHead, ThrownHeadRenderer.State> {
	/**
	 * The flat item sprites are drawn tip to the top right, which the fixed display's half turn about Y
	 * puts at 135 degrees in the plane. Turning it back by that much lays the tip along +X, the axis the
	 * yaw and pitch then aim down the flight.
	 */
	private static final float SPRITE_TIP_ANGLE = 135.0F;

	private final ItemModelResolver itemModelResolver;

	public ThrownHeadRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float yaw;
		public float pitch;
		public float spin;
		public boolean pointFirst;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ThrownHead entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		this.itemModelResolver.updateForNonLiving(state.item, entity.getHead(), ItemDisplayContext.FIXED, entity);
		state.pointFirst = entity.isPointFirst();
		if (state.pointFirst) {
			// The server aims it along its flight (and haft first on the way back); rotLerp so a turn
			// across the +-180 seam does not spin it the long way round.
			state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
			state.pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
		} else {
			var motion = entity.getDeltaMovement();
			state.yaw = (float) Math.toDegrees(Math.atan2(motion.x, motion.z));
			state.spin = (entity.tickCount + partialTicks) * 45.0F;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw - 90.0F));
		if (state.pointFirst) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(state.pitch));
			// Two planes crossed along the shaft, as vanilla draws arrows: a single flat sprite seen from
			// above or below is a line, and a trident in flight should read from any side.
			for (int plane = 0; plane < 2; plane++) {
				poseStack.pushPose();
				poseStack.mulPose(Axis.XP.rotationDegrees(plane * 90.0F));
				poseStack.mulPose(Axis.ZP.rotationDegrees(-SPRITE_TIP_ANGLE));
				poseStack.scale(1.1F, 1.1F, 1.1F);
				state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
				poseStack.popPose();
			}
		} else {
			poseStack.mulPose(Axis.ZP.rotationDegrees(-state.spin));
			poseStack.scale(0.9F, 0.9F, 0.9F);
			state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		}
		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, camera);
	}
}
