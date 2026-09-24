package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.Forja;
import dev.forja.block.entity.MeltTankBlockEntity;
import dev.forja.material.ForgeMaterial;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The metal inside a tank, drawn by hand so it can behave like lava rather than like a block.
 *
 * <p>A block model could only ever give a fixed texture in four steps. Drawn here it gets a level that
 * moves a pixel at a time, a surface that scrolls like lava, and three things that are the metal's own:
 * its <b>colour</b>, its <b>opacity</b> and its <b>shine</b>. The last two come off the colour rather
 * than out of a table — a pale metal is read as thin and glossy, a dark one as thick and dull — so every
 * metal the mod ever adds gets a look of its own for free, and gold never pours like obsidian.
 */
public class MeltTankRenderer implements BlockEntityRenderer<MeltTankBlockEntity, MeltTankRenderer.MeltState> {
	private static final Identifier MELT = Forja.id("textures/block/colada.png");

	/** How fast the surface scrolls, in texture widths a second. */
	private static final float FLOW = 0.28F;

	/** How fast the shine crosses the melt. */
	private static final float SHINE = 0.55F;

	public MeltTankRenderer(BlockEntityRendererProvider.Context context) {
	}

	/** Everything the melt needs to draw itself, pulled off the block entity once a frame. */
	public static class MeltState extends BlockEntityRenderState {
		public float fill;
		public int colour;
		public float alpha;
		public float shine;
		public float time;
	}

	@Override
	public MeltState createRenderState() {
		return new MeltState();
	}

	@Override
	public void extractRenderState(MeltTankBlockEntity tank, MeltState state, float partialTick, Vec3 camera,
		ModelFeatureRenderer.@org.jspecify.annotations.Nullable CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(tank, state, crumbling);
		Item metal = tank.metal();
		state.fill = metal == null ? 0.0F : Math.min(1.0F, tank.amount() / (float) MeltTankBlockEntity.CAPACITY);
		int base = colourOf(metal);
		state.colour = molten(base);
		float bright = luminance(state.colour);
		// A pale, shiny metal lets the light through and catches it; a dark one does neither.
		state.alpha = Math.max(0.78F, Math.min(0.98F, 0.98F - 0.22F * bright));
		state.shine = 0.3F + 0.6F * bright;
		state.time = (System.currentTimeMillis() % 100000L) / 1000.0F;
	}

	@Override
	public void submit(MeltState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.fill <= 0.0F) {
			return;
		}
		float top = 0.02F + state.fill * 0.94F;
		float low = 0.02F;
		float in = 0.055F;
		float out = 1.0F - in;
		float scroll = state.time * FLOW % 1.0F;
		int colour = state.colour;
		int alpha = Math.round(state.alpha * 255.0F);

		// The body. Translucent, unlit by the world and lit by itself, which is what makes it read as
		// something molten sitting in a dark workshop rather than as coloured glass.
		RenderType type = RenderTypes.entityTranslucentEmissive(MELT, false);
		collector.submitCustomGeometry(pose, type, (p, buffer) -> {
			// Sides, with the texture scrolling upward so the metal looks like it is turning over.
			quad(p, buffer, in, low, in, out, top, in, 0.0F, scroll, colour, alpha, 0.0F, 0.0F, -1.0F);
			quad(p, buffer, out, low, out, in, top, out, 0.0F, scroll, colour, alpha, 0.0F, 0.0F, 1.0F);
			quad(p, buffer, in, low, out, in, top, in, 0.0F, scroll, colour, alpha, -1.0F, 0.0F, 0.0F);
			quad(p, buffer, out, low, in, out, top, out, 0.0F, scroll, colour, alpha, 1.0F, 0.0F, 0.0F);
			// The surface, scrolling sideways at its own speed.
			float drift = state.time * FLOW * 0.6F % 1.0F;
			flat(p, buffer, in, top, in, out, out, drift, brighter(colour, 1.12F), alpha, 1.0F);
		});

		// And the shine: one bright band crossing the surface, which is the whole reason a pot of gold
		// does not look like a pot of iron with a different hue.
		float sweep = (state.time * SHINE % 1.6F) - 0.3F;
		if (sweep >= 0.0F && sweep <= 1.0F && state.shine > 0.0F) {
			float from = Math.max(in, sweep - 0.12F);
			float to = Math.min(out, sweep + 0.12F);
			if (to > from) {
				int glow = Math.round(state.shine * 150.0F);
				collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(MELT, false), (p, buffer) ->
					flat(p, buffer, from, top + 0.002F, in, to, out, 0.0F, 0xFFFFFF, glow, 1.0F));
			}
		}
	}

	/** One upright face of the melt. */
	private static void quad(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
		float x0, float y0, float z0, float x1, float y1, float z1,
		float u, float v, int colour, int alpha, float nx, float ny, float nz) {
		put(pose, buffer, x0, y0, z0, u, v + 1.0F, colour, alpha, nx, ny, nz);
		put(pose, buffer, x1, y0, z1, u + 1.0F, v + 1.0F, colour, alpha, nx, ny, nz);
		put(pose, buffer, x1, y1, z1, u + 1.0F, v, colour, alpha, nx, ny, nz);
		put(pose, buffer, x0, y1, z0, u, v, colour, alpha, nx, ny, nz);
		// The other way round as well: a melt is looked at from inside the glass as often as from out.
		put(pose, buffer, x0, y1, z0, u, v, colour, alpha, -nx, -ny, -nz);
		put(pose, buffer, x1, y1, z1, u + 1.0F, v, colour, alpha, -nx, -ny, -nz);
		put(pose, buffer, x1, y0, z1, u + 1.0F, v + 1.0F, colour, alpha, -nx, -ny, -nz);
		put(pose, buffer, x0, y0, z0, u, v + 1.0F, colour, alpha, -nx, -ny, -nz);
	}

	/** The flat top of the melt. */
	private static void flat(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
		float x0, float y, float z0, float x1, float z1, float drift, int colour, int alpha, float up) {
		put(pose, buffer, x0, y, z0, drift, 0.0F, colour, alpha, 0.0F, up, 0.0F);
		put(pose, buffer, x0, y, z1, drift, 1.0F, colour, alpha, 0.0F, up, 0.0F);
		put(pose, buffer, x1, y, z1, drift + 1.0F, 1.0F, colour, alpha, 0.0F, up, 0.0F);
		put(pose, buffer, x1, y, z0, drift + 1.0F, 0.0F, colour, alpha, 0.0F, up, 0.0F);
		put(pose, buffer, x1, y, z0, drift + 1.0F, 0.0F, colour, alpha, 0.0F, -up, 0.0F);
		put(pose, buffer, x1, y, z1, drift + 1.0F, 1.0F, colour, alpha, 0.0F, -up, 0.0F);
		put(pose, buffer, x0, y, z1, drift, 1.0F, colour, alpha, 0.0F, -up, 0.0F);
		put(pose, buffer, x0, y, z0, drift, 0.0F, colour, alpha, 0.0F, -up, 0.0F);
	}

	private static void put(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
		float x, float y, float z, float u, float v, int colour, int alpha, float nx, float ny, float nz) {
		buffer.addVertex(pose, x, y, z)
			.setColor((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF, alpha)
			.setUv(u, v)
			.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0)
			.setNormal(pose, nx, ny, nz);
	}

	/** The colour the mod already gives this metal, or plain iron for anything it does not know. */
	private static int colourOf(@org.jspecify.annotations.Nullable Item metal) {
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

	/** The same colour, hotter, for the surface of the pot. */
	private static int brighter(int colour, float by) {
		int r = Math.min(255, Math.round(((colour >> 16) & 0xFF) * by));
		int g = Math.min(255, Math.round(((colour >> 8) & 0xFF) * by));
		int b = Math.min(255, Math.round((colour & 0xFF) * by));
		return r << 16 | g << 8 | b;
	}

	private static float luminance(int colour) {
		return (0.2126F * ((colour >> 16) & 0xFF) + 0.7152F * ((colour >> 8) & 0xFF) + 0.0722F * (colour & 0xFF)) / 255.0F;
	}
}
