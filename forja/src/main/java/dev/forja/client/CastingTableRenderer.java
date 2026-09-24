package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.Forja;
import dev.forja.block.entity.CastingTableBlockEntity;
import dev.forja.material.ForgeMaterial;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What is happening on top of a casting table, which is the whole of its interface.
 *
 * <p>Three things are drawn here and nowhere else: the <b>frame</b> lying in the bed, the <b>stream</b>
 * of metal falling into it while the tanks are emptying, and the <b>pour</b> rising in the bed until it
 * is full. When the casting is done the finished tool lies there in place of the frame, so a row of
 * tables can be read at a glance from across the workshop without opening anything.
 */
public class CastingTableRenderer implements BlockEntityRenderer<CastingTableBlockEntity, CastingTableRenderer.TableState> {
	private static final Identifier MELT = Forja.id("textures/block/colada.png");

	/** Where the bed is, in block coordinates: the sunken square the top texture draws. */
	private static final float BED_FROM = 4.0F / 16.0F;
	private static final float BED_TO = 12.0F / 16.0F;

	/** How high the pour stands when the bed is full. */
	private static final float BED_DEEP = 1.5F / 16.0F;

	/** The share of the pour spent with metal still falling into the bed. */
	private static final float FALLING = 0.35F;

	private final ItemModelResolver items;

	public CastingTableRenderer(BlockEntityRendererProvider.Context context) {
		this.items = context.itemModelResolver();
	}

	/** Everything the table needs to draw itself, pulled off the block entity once a frame. */
	public static class TableState extends BlockEntityRenderState {
		public final ItemStackRenderState lying = new ItemStackRenderState();
		public boolean hasItem;
		public float fill;
		public float falling;
		public int colour;
		public float time;
	}

	@Override
	public TableState createRenderState() {
		return new TableState();
	}

	@Override
	public void extractRenderState(CastingTableBlockEntity table, TableState state, float partialTick, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(table, state, crumbling);
		// The finished tool takes the frame's place on the table, which is how a full table reads as done.
		ItemStack lying = table.result().isEmpty() ? table.frame() : table.result();
		state.hasItem = !lying.isEmpty();
		state.lying.clear();
		if (state.hasItem) {
			this.items.updateForTopItem(state.lying, lying, ItemDisplayContext.FIXED, table.getLevel(), null, 0);
		}
		// Off the world clock rather than off a synced counter, so it rises smoothly without the server
		// sending a packet a tick to say so.
		float progress = table.getLevel() == null ? table.progress()
			: table.progressAt(table.getLevel().getGameTime() + partialTick);
		// It falls in first and then stands: the bed is full by the time the stream stops.
		state.fill = table.metal() == null ? 0.0F : Math.min(1.0F, progress / FALLING);
		// A spout overhead is already drawing the fall, and far better than this can: it knows how far
		// the metal is dropping. Two streams in the same place read as one fat one.
		boolean fed = table.getLevel() != null
			&& table.getLevel().getBlockState(table.getBlockPos().above()).getBlock() instanceof dev.forja.block.MeltSpoutBlock;
		state.falling = table.metal() != null && progress < FALLING && !fed ? 1.0F : 0.0F;
		state.colour = molten(colourOf(table));
		state.time = (System.currentTimeMillis() % 100000L) / 1000.0F;
	}

	@Override
	public void submit(TableState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.hasItem) {
			pose.pushPose();
			// Lying flat in the bed, face up, a shade smaller than the bed so it never clips the rim. It
			// does not rise with the metal: the metal rises over it, and the shape glows through.
			pose.translate(0.5F, 1.004F, 0.5F);
			pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
			pose.scale(0.75F, 0.75F, 0.75F);
			state.lying.submit(pose, collector, state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
		if (state.fill <= 0.0F) {
			return;
		}

		float top = 1.0F + BED_DEEP * state.fill;
		float scroll = state.time * 0.28F % 1.0F;
		int colour = state.colour;
		// The pour sits in the bed: a shallow pool, lit by itself, the same metal the tanks were holding.
		collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(MELT, false), (p, buffer) -> {
			flat(p, buffer, BED_FROM, top, BED_FROM, BED_TO, BED_TO, scroll, brighter(colour, 1.12F), 235);
			// The little wall of it against the rim, so it reads as depth rather than as a decal.
			quad(p, buffer, BED_FROM, 1.0F, BED_FROM, BED_TO, top, BED_FROM, scroll, colour, 235);
			quad(p, buffer, BED_FROM, 1.0F, BED_TO, BED_TO, top, BED_TO, scroll, colour, 235);
			quad(p, buffer, BED_FROM, 1.0F, BED_FROM, BED_FROM, top, BED_TO, scroll, colour, 235);
			quad(p, buffer, BED_TO, 1.0F, BED_FROM, BED_TO, top, BED_TO, scroll, colour, 235);
		});

		if (state.falling > 0.0F) {
			// And the stream itself, falling in from above: two crossed faces, which is all a thin
			// column of anything ever needs to be. Narrow, or it reads as a plank stuck in the table.
			float thin = 1.0F / 16.0F;
			float mid = 0.5F;
			float from = mid - thin;
			float to = mid + thin;
			float high = 1.42F;
			float drop = state.time * 1.8F % 1.0F;
			collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(MELT, false), (p, buffer) -> {
				quad(p, buffer, from, top, mid, to, high, mid, drop, brighter(colour, 1.1F), 225);
				quad(p, buffer, mid, top, from, mid, high, to, drop, brighter(colour, 1.1F), 225);
			});
		}
	}

	/** One upright face, drawn both ways round so it reads from either side. */
	private static void quad(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
		float x0, float y0, float z0, float x1, float y1, float z1, float v, int colour, int alpha) {
		put(pose, buffer, x0, y0, z0, 0.0F, v + 1.0F, colour, alpha);
		put(pose, buffer, x1, y0, z1, 1.0F, v + 1.0F, colour, alpha);
		put(pose, buffer, x1, y1, z1, 1.0F, v, colour, alpha);
		put(pose, buffer, x0, y1, z0, 0.0F, v, colour, alpha);
		put(pose, buffer, x0, y1, z0, 0.0F, v, colour, alpha);
		put(pose, buffer, x1, y1, z1, 1.0F, v, colour, alpha);
		put(pose, buffer, x1, y0, z1, 1.0F, v + 1.0F, colour, alpha);
		put(pose, buffer, x0, y0, z0, 0.0F, v + 1.0F, colour, alpha);
	}

	/** The flat surface of the pour. */
	private static void flat(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer,
		float x0, float y, float z0, float x1, float z1, float drift, int colour, int alpha) {
		put(pose, buffer, x0, y, z0, drift, 0.0F, colour, alpha);
		put(pose, buffer, x0, y, z1, drift, 1.0F, colour, alpha);
		put(pose, buffer, x1, y, z1, drift + 1.0F, 1.0F, colour, alpha);
		put(pose, buffer, x1, y, z0, drift + 1.0F, 0.0F, colour, alpha);
		put(pose, buffer, x1, y, z0, drift + 1.0F, 0.0F, colour, alpha);
		put(pose, buffer, x1, y, z1, drift + 1.0F, 1.0F, colour, alpha);
		put(pose, buffer, x0, y, z1, drift, 1.0F, colour, alpha);
		put(pose, buffer, x0, y, z0, drift, 0.0F, colour, alpha);
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

	private static int colourOf(CastingTableBlockEntity table) {
		if (table.metal() == null) {
			return 0xB8B8B8;
		}
		ForgeMaterial material = ForgeMaterial.fromInput(new ItemStack(table.metal()));
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
