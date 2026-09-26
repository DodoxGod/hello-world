package dev.forja.client;

import java.util.List;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.forja.entity.BrokenMould;
import dev.forja.registry.ModEntities;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The Molde Roto: the furnace in its belly, its eye and the blank it is holding all burn. */
public class BrokenMouldRenderer extends GeoEntityRenderer<BrokenMould, LivingEntityRenderState> {
	public BrokenMouldRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.MOLDE_ROTO);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
		this.withRenderLayer(new CastInHand(context, this));
	}

	@Override
	public LivingEntityRenderState createRenderState(BrokenMould mob, Void relatedObject) {
		return new LivingEntityRenderState();
	}

	/**
	 * The copy the recast brought out, held upright in both fists where the blank was (the model scales
	 * the blank away meanwhile: BrokenMould's "blank" controller). It is drawn on the hands' own bone, so
	 * it goes down into the furnace and comes back out with them.
	 */
	private static final class CastInHand extends BlockAndItemGeoLayer<BrokenMould, Void, LivingEntityRenderState> {
		/** The bone the two hand cubes hang on, in molde_roto.geo.json. */
		private static final String HANDS = "tool";
		/**
		 * From the hands' pivot (0, 20, -8) to the middle of the item, in blocks. Up far enough that the
		 * sword's guard sits on top of the fists (21.5) and its grip runs down through them, and back to
		 * the grip's own depth (-6.5), between the two hand cubes.
		 */
		private static final float UP = 7.5F / 16.0F;
		private static final float BACK = 1.5F / 16.0F;
		/**
		 * A flat item sprite is drawn corner to corner, about 1.3 blocks from pommel to tip. At one and a
		 * half times that it is as long as the blank it replaces, and its handle comes out three units
		 * wide: the gap between the fists.
		 */
		private static final float SIZE = 1.5F;

		CastInHand(EntityRendererProvider.Context context, BrokenMouldRenderer renderer) {
			super(context, renderer);
		}

		@Override
		protected List<RenderData> getRelevantBones(BrokenMould mould, Void relatedObject, LivingEntityRenderState state, float partialTick) {
			ItemStack held = mould.getMainHandItem();
			if (held.isEmpty()) {
				return List.of();
			}
			ItemStackRenderState item = RenderUtil.createRenderStateForItem(held, this.itemModelResolver, ItemDisplayContext.NONE, mould);
			return List.of(RenderData.item(HANDS, ItemDisplayContext.NONE, item));
		}

		@Override
		public void addRenderData(BrokenMould mould, Void relatedObject, LivingEntityRenderState state, float partialTick) {
			List<RenderData> held = this.getRelevantBones(mould, relatedObject, state, partialTick);
			if (!held.isEmpty()) {
				state.addGeckolibData(CONTENTS, held);
			}
		}

		@Override
		protected void submitItemStackRender(PoseStack poseStack, GeoBone bone, ItemStackRenderState item, ItemDisplayContext context,
			LivingEntityRenderState state, SubmitNodeCollector collector, int packedLight) {
			poseStack.pushPose();
			poseStack.translate(0.0F, UP, BACK);
			// The sprite's face towards the front of the mould, then its diagonal (handle low and to the
			// left, tip high and to the right, as every tool sprite is drawn) stood straight up.
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
			poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F));
			poseStack.scale(SIZE, SIZE, SIZE);
			super.submitItemStackRender(poseStack, bone, item, context, state, collector, packedLight);
			poseStack.popPose();
		}
	}
}
