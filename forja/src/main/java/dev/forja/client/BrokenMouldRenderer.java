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
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The Molde Roto: the furnace in its belly, its eye and the blank it is holding all burn. */
public class BrokenMouldRenderer extends GeoEntityRenderer<BrokenMould, LivingEntityRenderState> {
	public BrokenMouldRenderer(EntityRendererProvider.Context context) {
		super(context, ModEntities.MOLDE_ROTO);
		this.withRenderLayer(new com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer<>(this));
		this.withRenderLayer(new CastInHand(context, this));
	}

	/** Ticks the weapon stays up after the warning, for the blow (a packet behind it) to take it from there. */
	private static final int HELD = 4;

	/** Armed, so the copy can be drawn by the same code that puts a weapon in a zombie's fist. */
	@Override
	public LivingEntityRenderState createRenderState(BrokenMould mob, Void relatedObject) {
		return new ArmedEntityRenderState();
	}

	/**
	 * How far into the warning before a blow it is (0 to 1), for the model to raise what it holds over its
	 * head while the warning lasts (BrokenMould's "golpe" controller); -1 when it is not about to strike.
	 */
	@Override
	public void captureDefaultRenderState(BrokenMould mould, Void relatedObject, LivingEntityRenderState state, float partialTick) {
		super.captureDefaultRenderState(mould, relatedObject, state, partialTick);
		CombatAnims.State anim = CombatAnims.get(mould.getId());
		float raise = anim == null ? -1.0F : CombatAnims.progress(anim.telegraphAt, anim.telegraphTicks + HELD, CombatAnims.now(partialTick));
		state.addGeckolibData(BrokenMould.RAISE, raise);
	}

	/**
	 * The copy the recast brought out, held in both fists by its handle (the model scales the blank away
	 * meanwhile: BrokenMould's "blank" controller). It is drawn on the grip bone, which the two fists belong
	 * to, so it goes wherever the arms take it: into the furnace, up over the head and down in the chop.
	 *
	 * <p>The grip bone's frame has the handle running up its y and the face of the weapon to its front (-z).
	 * From there the weapon is handed to the same transform vanilla gives a zombie's right hand, so every
	 * item sits in the fist by its own grip, as it does in a player's hand: a sword by its hilt, a spear by
	 * its shaft, and the forged flail and tome as the 3D things the mod draws for them.
	 */
	private static final class CastInHand extends BlockAndItemGeoLayer<BrokenMould, Void, LivingEntityRenderState> {
		/** The bone the two fists hang on, in molde_roto.geo.json; its pivot is the middle of the handle between them. */
		private static final String GRIP = "grip";
		/** The mould is a head taller than a player and holds with both hands: its copy is half as big again. */
		private static final float SIZE = 1.5F;
		/** A spear's in-hand model is already drawn long, three blocks of it at the size above. */
		private static final float SPEAR_SIZE = 1.1F;
		/** How far the weapon's face is turned from the front, round the handle, in degrees. */
		private static final float TURN = 30.0F;
		/**
		 * Where vanilla's held item has its hilt: the middle of a hanging right arm's fist, a pixel in and
		 * eight and a half down from the shoulder (in the grip's frame, before it is turned into a vanilla arm).
		 */
		private static final float FIST_IN = 1.0F / 16.0F;
		private static final float FIST_DOWN = 8.5F / 16.0F;

		CastInHand(EntityRendererProvider.Context context, BrokenMouldRenderer renderer) {
			super(context, renderer);
		}

		@Override
		protected List<RenderData> getRelevantBones(BrokenMould mould, Void relatedObject, LivingEntityRenderState state, float partialTick) {
			ItemStack held = mould.getMainHandItem();
			if (held.isEmpty()) {
				return List.of();
			}
			ItemStackRenderState item = RenderUtil.createRenderStateForItem(held, this.itemModelResolver, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, mould);
			return List.of(RenderData.item(GRIP, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, item));
		}

		@Override
		public void addRenderData(BrokenMould mould, Void relatedObject, LivingEntityRenderState state, float partialTick) {
			List<RenderData> held = this.getRelevantBones(mould, relatedObject, state, partialTick);
			if (held.isEmpty()) {
				return;
			}
			state.addGeckolibData(CONTENTS, held);
			if (state instanceof ArmedEntityRenderState armed) {
				ArmedEntityRenderState.extractArmedEntityRenderState(mould, armed, this.itemModelResolver, partialTick);
				armed.setData(HeldFlail.KEY, HeldFlail.aim(mould, partialTick));
				armed.setData(HeldTome.KEY, HeldTome.reading(mould, partialTick));
			}
		}

		@Override
		protected void submitItemStackRender(PoseStack poseStack, GeoBone bone, ItemStackRenderState item, ItemDisplayContext context,
			LivingEntityRenderState state, SubmitNodeCollector collector, int packedLight) {
			ItemStack stack = state instanceof ArmedEntityRenderState armed ? armed.rightHandItemStack : ItemStack.EMPTY;
			poseStack.pushPose();
			// A spear, a trident or a dagger is held point forward, ready to stab; everything else stands up.
			poseStack.mulPose(Axis.XP.rotationDegrees(-lean(stack)));
			// Its face turned a little to the mould's right, round its own handle, so the flat of it shows
			// from the front and from either side.
			poseStack.mulPose(Axis.YP.rotationDegrees(-TURN));
			// The handle from the grip's y onto the line a held weapon takes out of a hanging arm's fist
			// (forward and ten degrees down), the weapon's face from the grip's front onto the arm's side.
			poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(100.0F));
			float size = WeaponMotions.of(stack) == WeaponMotions.LANZA ? SPEAR_SIZE : SIZE;
			poseStack.scale(size, size, size);
			// Up the arm to its shoulder, so the fist lands on the grip's pivot, and into a vanilla model's
			// frame (y down), which is GeckoLib's turned half round the z axis: this is where
			// HumanoidModel.translateToHand leaves a right arm hanging straight down.
			poseStack.translate(-FIST_IN, FIST_DOWN, 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
			if (HeldFlail.is(stack) && state instanceof ArmedEntityRenderState armed) {
				HeldFlail.submitInHand(poseStack, collector, packedLight, stack, HumanoidArm.RIGHT, armed);
			} else if (HeldTome.is(stack)) {
				HeldTome.submitInHand(poseStack, collector, packedLight, state.outlineColor, stack, false, state.getData(HeldTome.KEY));
			} else {
				// ItemInHandLayer.submitArmWithItem, for a right arm.
				poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
				poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
				poseStack.translate(1.0F / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
				super.submitItemStackRender(poseStack, bone, item, context, state, collector, packedLight);
			}
			poseStack.popPose();
		}

		/** How far forward the weapon leans out of the upright, in degrees, by the kind of weapon it is. */
		private static float lean(ItemStack stack) {
			return BrokenMould.thrusts(stack) ? 45.0F : 0.0F;
		}
	}
}
