package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.Forja;
import dev.forja.block.MeltPipeBlock;
import dev.forja.block.MeltSpoutBlock;
import dev.forja.block.entity.MeltFlowBlockEntity;
import dev.forja.material.ForgeMaterial;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The metal falling out of a spout.
 *
 * <p>The stream is drawn here because how far it falls depends on what is standing under it, which a
 * block model cannot ask. It takes the colour of the metal actually going past — the same colour the
 * tanks and the casting tables use — so a spout pouring gold pours gold.
 *
 * <p>The metal <em>lying in a channel</em> was meant to be drawn here too, so a dry run could look dry
 * and a run of gold could look golden. It is still in the block model instead: see the note on submit().
 */
public class MeltFlowRenderer implements BlockEntityRenderer<MeltFlowBlockEntity, MeltFlowRenderer.FlowState> {
	private static final Identifier MELT = Forja.id("textures/block/colada.png");

	/** Where the metal sits in the channel, and so where a stream leaves from. */
	private static final float SURFACE = 5.5F / 16.0F;

	/** Half the width of a falling stream. A thread, not a plank. */
	private static final float THIN = 1.0F / 16.0F;

	/** How fast a stream falls. */
	private static final float FALL = 2.4F;

	public MeltFlowRenderer(BlockEntityRendererProvider.Context context) {
	}

	public static class FlowState extends BlockEntityRenderState {
		public boolean wet;
		public boolean spout;
		/** How far the stream falls, in blocks; 0 for a plain length of channel. */
		public float drop;
		public int colour;
		public float time;
	}

	@Override
	public FlowState createRenderState() {
		return new FlowState();
	}

	/** A falling stream hangs below its own block, and would be culled the moment the spout went off screen. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public void extractRenderState(MeltFlowBlockEntity flow, FlowState state, float partialTick, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(flow, state, crumbling);
		Item metal = flow.metal();
		state.wet = metal != null;
		state.colour = molten(colourOf(metal));
		state.time = (System.currentTimeMillis() % 100000L) / 1000.0F;
		state.spout = false;
		state.drop = 0.0F;
		if (flow.getLevel() == null || !state.wet) {
			return;
		}
		var block = flow.getBlockState().getBlock();
		state.spout = block instanceof MeltSpoutBlock;
		if (state.spout) {
			BlockPos lands = MeltPipeBlock.landing(flow.getLevel(), flow.getBlockPos());
			state.drop = lands == null ? 0.0F : flow.getBlockPos().getY() - lands.getY();
		}
	}

	@Override
	public void submit(FlowState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		// Only the falling stream is drawn here. The metal lying in a channel is back in the block model
		// for now: quads submitted inside this block's own volume do not appear, while the same quads
		// above it do, and the stream works precisely because most of it hangs below the block. That is
		// as far as this got — see the note in MeltFlowBlockEntity about what it was meant to become.
		if (!state.wet || !state.spout || state.drop <= 0.0F) {
			return;
		}
		int bright = brighter(state.colour, 1.08F);
		float scroll = state.time * FALL % 1.0F;
		float drop = state.drop;
		float bottom = -drop + 1.0F;
		float mid = 0.5F;
		collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(MELT, false), (p, buffer) -> {
			column(p, buffer, mid - THIN, mid, mid + THIN, mid, bottom, scroll, drop, bright);
			column(p, buffer, mid, mid - THIN, mid, mid + THIN, bottom, scroll, drop, bright);
		});
	}

	/** One upright ribbon of a falling stream, drawn both ways round. */
	private static void column(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
		float x0, float z0, float x1, float z1, float bottom, float scroll, float drop, int colour) {
		float v0 = scroll;
		float v1 = scroll + drop;
		put(pose, buffer, x0, bottom, z0, 0.0F, v1, colour, 245);
		put(pose, buffer, x1, bottom, z1, 1.0F, v1, colour, 245);
		put(pose, buffer, x1, SURFACE, z1, 1.0F, v0, colour, 245);
		put(pose, buffer, x0, SURFACE, z0, 0.0F, v0, colour, 245);
		put(pose, buffer, x0, SURFACE, z0, 0.0F, v0, colour, 245);
		put(pose, buffer, x1, SURFACE, z1, 1.0F, v0, colour, 245);
		put(pose, buffer, x1, bottom, z1, 1.0F, v1, colour, 245);
		put(pose, buffer, x0, bottom, z0, 0.0F, v1, colour, 245);
	}

	private static void put(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
		float x, float y, float z, float u, float v, int colour, int alpha) {
		buffer.addVertex(pose, x, y, z)
			.setColor((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF, alpha)
			.setUv(u, v)
			.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static int colourOf(@Nullable Item metal) {
		if (metal == null) {
			return 0xB8B8B8;
		}
		ForgeMaterial material = ForgeMaterial.fromInput(new ItemStack(metal));
		return material == null ? 0xB8B8B8 : material.color;
	}

	/** The one molten colour the whole mod uses, so a metal looks like itself in the pot, the channel and the mould. */
	private static int molten(int colour) {
		return dev.forja.material.ForgeMaterial.molten(colour);
	}

	private static int brighter(int colour, float by) {
		int r = Math.min(255, Math.round(((colour >> 16) & 0xFF) * by));
		int g = Math.min(255, Math.round(((colour >> 8) & 0xFF) * by));
		int b = Math.min(255, Math.round((colour & 0xFF) * by));
		return r << 16 | g << 8 | b;
	}
}
