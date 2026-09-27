package dev.forja.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The name plate of a veteran, an elite or a champion.
 *
 * <p>"Las nametags hacen que se vea un poco feo/pobre, añádele diseño." Vanilla's white text on a grey
 * box said "Karn Rompehuesos" and nothing of the bronze chevron floating right under it. The ranked
 * now get a plaque that belongs with their badge ({@link ThreatBadge}): a dark plate with pointed ends,
 * edged in the metal of their rank and ringed in near-black so it holds against a bright sky or sand;
 * the name itself in that metal, its byname a little duller. The champion's is the rich one: a double
 * gold edge, a star on its crown and a stud at each point; the elite's has the studs; the veteran's is
 * just the plate, the plainest rung of the same ladder.
 *
 * <p>It only replaces the drawing. When the name shows is still vanilla's call (looked at, or always for
 * a champion), it shows through walls the way vanilla's does (the letters, faint, without the plate),
 * a sneaking mob keeps it to itself, and nobody else's name changes: players, items, unranked mobs
 * and anything with a line under its name (a scoreboard) keep vanilla's tag. It sits over the badge,
 * and grows with distance exactly as the badge does, so the two stay one piece at any range.
 *
 * <p>Nothing is made per frame: a plate (its coloured text, its width, its outline) is built once per
 * mob and name, kept while the name is the same object, and it draws its own geometry.
 */
public final class ThreatPlate {
	public static final RenderStateDataKey<Plate> KEY = RenderStateDataKey.create(() -> "forja:placa");

	/** Nametag pixels to blocks, as vanilla's. */
	private static final float PIXEL = 0.025F;
	/** Gap between the badge's top and the plate's lowest edge. */
	private static final float OVER_BADGE = 0.03F;
	/** The plate's box around the text, in pixels: the text runs 0 to 8. */
	private static final float TOP = -2.0F;
	private static final float BOTTOM = 9.0F;
	private static final float MID = (TOP + BOTTOM) / 2.0F;
	/** How far each pointed end reaches past the box: half its height, so the points are right angles. */
	private static final float POINT = (BOTTOM - TOP) / 2.0F;
	private static final float PAD = 3.5F;
	private static final float ROOT2 = (float) Math.sqrt(2.0);
	/**
	 * Depths behind the letters, which sit at 0 (negative is away from the camera here): the plate furthest,
	 * the dark backing of the star and the studs in front of it, and their metal in front of that.
	 */
	private static final float Z_PLATE = -0.03F;
	private static final float Z_UNDER = -0.02F;
	private static final float Z = -0.01F;

	private static final int LIGHT = 0xF000F0;
	private static final int FILL_TOP = 0xF02C2219;
	private static final int FILL_BOTTOM = 0xF00E0A07;
	private static final int RIM = 0xFF0B0806;

	/** Per threat: edge light, edge dark, name, byname. */
	private static final int[][] METAL = {
		{},
		{0xFFE0A064, 0xFF8A5226, 0xFFF2B274, 0xFFB08868},
		{0xFFF4F8FF, 0xFF8C95A4, 0xFFF2F6FC, 0xFFA4ADBB},
		{0xFFFFE48A, 0xFFB07A14, 0xFFFFD866, 0xFFCDAF68},
	};

	private static final Map<LivingEntity, Plate> PLATES = new WeakHashMap<>();
	private static RenderType plateType;

	private ThreatPlate() {
	}

	/**
	 * The plate for this mob's name as it shows now, or null to leave the name to vanilla. Called while the
	 * render state is extracted, after vanilla has decided whether the name shows at all.
	 */
	public static @Nullable Plate of(LivingEntity entity, EntityRenderState state, Threat threat) {
		Component name = entity.getCustomName();
		if (threat == null || threat == Threat.NORMAL || state.nameTag == null || state.scoreText != null || name == null) {
			return null;
		}
		Language language = Language.getInstance();
		Plate plate = PLATES.get(entity);
		if (plate == null || plate.source != name || plate.language != language || plate.threat != threat) {
			plate = new Plate(name, language, threat);
			PLATES.put(entity, plate);
		}
		return plate;
	}

	/**
	 * Draws the plate in place of vanilla's tag. Called at the head of EntityRenderer.submitNameDisplay;
	 * true means it was drawn and vanilla's is skipped.
	 */
	public static boolean submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Plate plate = state.getData(KEY);
		if (plate == null || state.nameTag == null || state.scoreText != null) {
			return false;
		}
		if (plateType == null) {
			plateType = RenderTypes.textBackground();
		}
		float scale = PIXEL * ThreatBadge.grow(state);
		poseStack.pushPose();
		poseStack.translate(0.0F, ThreatBadge.top(state, plate.threat) + OVER_BADGE, 0.0F);
		poseStack.mulPose(camera.orientation);
		poseStack.scale(scale, -scale, scale);
		// Standing on its lowest edge: that is the point the badge was measured against.
		poseStack.translate(0.0F, -plate.lowest, 0.0F);
		collector.submitCustomGeometry(poseStack, plateType, plate);
		float x = -plate.width / 2.0F;
		collector.submitText(poseStack, x, 0.0F, plate.text, true, Font.DisplayMode.NORMAL, LIGHT, plate.byname, 0, 0);
		if (!state.isDiscrete) {
			// Through a wall, like vanilla's: the letters only, faint.
			collector.submitText(poseStack, x, 0.0F, plate.text, false, Font.DisplayMode.SEE_THROUGH, LIGHT,
				(plate.byname & 0xFFFFFF) | 0x80000000, 0, 0);
		}
		poseStack.popPose();
		return true;
	}

	/** One mob's plate: its text coloured by rank, and the outline of the plaque behind it. */
	public static final class Plate implements SubmitNodeCollector.CustomGeometryRenderer {
		final Component source;
		final Language language;
		final Threat threat;
		final FormattedCharSequence text;
		final int width;
		final int byname;
		/** Lowest pixel row of the whole plaque, rim included. */
		final float lowest;
		/** Quads, four corners each: x, y, z per corner, and a colour per corner. */
		private final float[] xyz;
		private final int[] colours;
		private int corners;

		Plate(Component source, Language language, Threat threat) {
			this.source = source;
			this.language = language;
			this.threat = threat;
			int[] metal = METAL[threat.ordinal()];
			this.byname = metal[3];
			this.text = colour(source, metal[2], metal[3]).getVisualOrderText();
			this.width = Minecraft.getInstance().font.width(this.text);
			boolean champion = threat == Threat.CAMPEON;
			int rings = champion ? 4 : 2;
			this.lowest = BOTTOM + rings;
			// Fill 3 quads, 6 per ring, the star 20, studs 4.
			int quads = 3 + 6 * rings + (champion ? 20 : 0) + (threat != Threat.VETERANO ? 4 : 0);
			this.xyz = new float[quads * 12];
			this.colours = new int[quads * 4];
			build(metal, champion);
		}

		/** "Karn Rompehuesos": the given name in the metal, the byname duller. Any other name all in the metal. */
		private static MutableComponent colour(Component name, int given, int byname) {
			if (name.getContents() instanceof TranslatableContents translatable && translatable.getKey().startsWith("name.forja.nombre.")
				&& translatable.getArgs().length == 2) {
				Object[] args = translatable.getArgs();
				return Component.translatable(translatable.getKey(), part(args[0]).withColor(given), part(args[1]).withColor(byname))
					.withColor(byname);
			}
			return name.copy().withColor(given);
		}

		private static MutableComponent part(Object arg) {
			return arg instanceof Component component ? component.copy() : Component.literal(String.valueOf(arg));
		}

		private void build(int[] metal, boolean champion) {
			float half = this.width / 2.0F + PAD;
			// The plate: a box with a point at each end, lighter at the top like a lit plaque.
			float[] fill = hexagon(half, 0.0F);
			this.quad(fill, 0, 1, 3, 4, FILL_TOP, FILL_TOP, FILL_BOTTOM, FILL_BOTTOM, Z_PLATE);
			this.quad(fill, 1, 2, 3, 3, FILL_TOP, mix(FILL_TOP, FILL_BOTTOM), FILL_BOTTOM, FILL_BOTTOM, Z_PLATE);
			this.quad(fill, 4, 5, 0, 0, FILL_BOTTOM, mix(FILL_TOP, FILL_BOTTOM), FILL_TOP, FILL_TOP, Z_PLATE);
			// Its edges, from the inside out: metal (bevelled: light above the middle, dark below), then for
			// the champion a dark line and a second metal edge, and last the near-black rim.
			int rings = champion ? 4 : 2;
			for (int ring = 0; ring < rings; ring++) {
				boolean rim = ring == rings - 1;
				boolean gap = champion && ring == 1;
				int light = rim || gap ? RIM : metal[0];
				int dark = rim || gap ? RIM : metal[1];
				this.ring(half, ring, ring + 1, light, dark);
			}
			if (threat != Threat.VETERANO) {
				// A stud at each point.
				float reach = half + POINT + rings * ROOT2 + 1.2F;
				this.diamond(reach, MID, 2.2F, RIM, Z_UNDER);
				this.diamond(-reach, MID, 2.2F, RIM, Z_UNDER);
				this.diamond(reach, MID, 1.2F, metal[0], Z);
				this.diamond(-reach, MID, 1.2F, metal[0], Z);
			}
			if (champion) {
				// A star on the crown of the plate, over a dark one a size larger so it reads on the sky.
				float cy = TOP - rings - 0.5F;
				this.star(0.0F, cy, 7.6F, 3.6F, RIM, RIM, Z_UNDER);
				this.star(0.0F, cy, 5.6F, 2.4F, metal[0], metal[1], Z);
			}
		}

		/** The plate's outline pushed out by d pixels: six corners, x and y each, from the top left round. */
		private static float[] hexagon(float half, float d) {
			float corner = half + d * (ROOT2 - 1.0F);
			float point = half + POINT + d * ROOT2;
			return new float[] {
				-corner, TOP - d, corner, TOP - d, point, MID, corner, BOTTOM + d, -corner, BOTTOM + d, -point, MID,
			};
		}

		private void ring(float half, float from, float to, int light, int dark) {
			float[] in = hexagon(half, from);
			float[] out = hexagon(half, to);
			for (int i = 0; i < 6; i++) {
				int j = (i + 1) % 6;
				this.put(in[i * 2], in[i * 2 + 1], Z_PLATE, shade(in[i * 2 + 1], light, dark));
				this.put(in[j * 2], in[j * 2 + 1], Z_PLATE, shade(in[j * 2 + 1], light, dark));
				this.put(out[j * 2], out[j * 2 + 1], Z_PLATE, shade(out[j * 2 + 1], light, dark));
				this.put(out[i * 2], out[i * 2 + 1], Z_PLATE, shade(out[i * 2 + 1], light, dark));
			}
		}

		private static int shade(float y, int light, int dark) {
			return y < MID - 0.01F ? light : y > MID + 0.01F ? dark : mix(light, dark);
		}

		private void diamond(float cx, float cy, float r, int colour, float z) {
			this.put(cx, cy - r, z, colour);
			this.put(cx + r, cy, z, colour);
			this.put(cx, cy + r, z, colour);
			this.put(cx - r, cy, z, colour);
		}

		/** A five-pointed star as ten slivers, each a quad with its last corner doubled. */
		private void star(float cx, float cy, float outer, float inner, int light, int dark, float z) {
			for (int i = 0; i < 10; i++) {
				double a0 = Math.PI * i / 5.0 - Math.PI / 2.0;
				double a1 = Math.PI * (i + 1) / 5.0 - Math.PI / 2.0;
				float r0 = i % 2 == 0 ? outer : inner;
				float r1 = i % 2 == 0 ? inner : outer;
				float x0 = cx + (float) Math.cos(a0) * r0;
				float y0 = cy + (float) Math.sin(a0) * r0;
				float x1 = cx + (float) Math.cos(a1) * r1;
				float y1 = cy + (float) Math.sin(a1) * r1;
				// Lit from the upper left: the slivers on that side light, the rest dark.
				int colour = Math.cos((a0 + a1) / 2.0 + Math.PI * 0.75) > 0.0 ? light : dark;
				this.put(cx, cy, z, mix(light, dark));
				this.put(x0, y0, z, colour);
				this.put(x1, y1, z, colour);
				this.put(x1, y1, z, colour);
			}
		}

		private void quad(float[] p, int a, int b, int c, int d, int ca, int cb, int cc, int cd, float z) {
			this.put(p[a * 2], p[a * 2 + 1], z, ca);
			this.put(p[b * 2], p[b * 2 + 1], z, cb);
			this.put(p[c * 2], p[c * 2 + 1], z, cc);
			this.put(p[d * 2], p[d * 2 + 1], z, cd);
		}

		private void put(float x, float y, float z, int colour) {
			int i = this.corners++;
			this.xyz[i * 3] = x;
			this.xyz[i * 3 + 1] = y;
			this.xyz[i * 3 + 2] = z;
			this.colours[i] = colour;
		}

		private static int mix(int a, int b) {
			int out = 0;
			for (int shift = 0; shift < 32; shift += 8) {
				out |= ((((a >>> shift) & 0xFF) + ((b >>> shift) & 0xFF)) / 2) << shift;
			}
			return out;
		}

		@Override
		public void render(PoseStack.Pose pose, VertexConsumer buffer) {
			// Each quad twice, once each way round, so it shows whichever way the pipeline culls.
			for (int q = 0; q < this.corners; q += 4) {
				for (int k = 0; k < 4; k++) {
					this.vertex(pose, buffer, q + k);
				}
				for (int k = 3; k >= 0; k--) {
					this.vertex(pose, buffer, q + k);
				}
			}
		}

		private void vertex(PoseStack.Pose pose, VertexConsumer buffer, int i) {
			buffer.addVertex(pose, this.xyz[i * 3], this.xyz[i * 3 + 1], this.xyz[i * 3 + 2]).setColor(this.colours[i]).setLight(LIGHT);
		}
	}
}
