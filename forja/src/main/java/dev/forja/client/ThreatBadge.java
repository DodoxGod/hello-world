package dev.forja.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.forja.Forja;
import dev.forja.difficulty.Threat;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The rank over a dangerous monster's head.
 *
 * <p>"La única forma para identificar un veterano es viendo el nametag que tiene, añade algo que los
 * identifique de forma visual y bonita." A veteran, an elite and a champion now wear an insignia a
 * hand's breadth above their heads: one bronze chevron, two silver ones, two gold ones under a star
 * (tools/generate_assets.py, generate_threat_badges). One ladder, so a glance tells not only that
 * this one is special but how special, and the veteran's is the plainest rung of it: the smallest,
 * the dullest metal, a single stripe.
 *
 * <p>Why a badge over the head rather than the other ideas: it is the one thing that looks the same on
 * a zombie, a spider, a creeper and a slime, whatever the model; it reads from behind and from the
 * side; it is where the eye already goes for a name; and it costs one quad. A sash or scars would need
 * a layer per model and would vanish on half the bestiary; a particle trail reads as a status effect
 * and turns a horde into confetti; eyes are too small to see at fifteen blocks, which is exactly where
 * the choice to fight or not is made.
 *
 * <p>How it behaves:
 * <ul>
 * <li>It faces the camera and bobs gently, so it reads as a marker rather than as part of the mob.</li>
 * <li>It is lit by itself and drawn like a name, without the shading of a body, so its metal is the
 *     same at midnight and at noon, and it is ringed in near-black
 *     so it holds against a bright sky or sand.</li>
 * <li>It grows with distance (1.6 times by seventeen blocks, Andy's numbers), so at fifteen, where the choice to fight is
 *     made, it is still a clear mark on a 1080p screen rather than a speck, and fades out between 21 and 25 blocks, so a horde far off
 *     is not a field of stars.</li>
 * <li>When the mob's name shows (looked at, or a champion's), the name moves up over the badge instead
 *     of the two crossing.</li>
 * <li>It goes with the mob: not drawn while it is dying or invisible, culled with its body, and
 *     driven by {@link Threat#SHOWN}, which the server syncs from the tags every time the mob loads.</li>
 * </ul>
 *
 * <p>Nothing is allocated per mob: the quads for every tier and every step of fade are made once.
 */
public final class ThreatBadge {
	public static final RenderStateDataKey<Threat> KEY = RenderStateDataKey.create(() -> "forja:amenaza");

	private static final Identifier TEXTURE = Forja.id("textures/misc/insignias_amenaza.png");
	/** The sheet: three cells of 16 by 20 pixels, the insignia standing on the bottom row of each. */
	private static final float SHEET_W = 48.0F;
	private static final float SHEET_H = 20.0F;
	/** Pixel rows each insignia uses, counted from the bottom: the champion's star takes the extra four. */
	private static final int[] ROWS = {0, 16, 16, 20};

	/** Width in blocks up close: the veteran's is the smallest. */
	private static final float[] WIDTH = {0.0F, 0.32F, 0.36F, 0.40F};
	/** Space between the top of the head and the badge. */
	private static final float GAP = 0.14F;
	/** Growth with distance: from GROW_FROM blocks, up to GROW_MAX times the size GROW_SPAN blocks later. */
	private static final float GROW_FROM = 4.0F;
	private static final float GROW_SPAN = 13.0F;
	private static final float GROW_MAX = 1.6F;
	/** Full until FADE_FROM blocks, gone at FADE_TO. */
	private static final float FADE_FROM = 21.0F;
	private static final float FADE_TO = 25.0F;
	private static final int FADE_STEPS = 8;

	/** Where the name's lowest pixel sits over its attachment point (EntityRenderer: +0.5, 9 px at 0.025 down). */
	private static final float NAME_BOTTOM = 0.25F;

	private static RenderType type;
	private static Quad[][] quads;

	private ThreatBadge() {
	}

	/** What to draw over this one: nothing for a normal mob, or one that is dying. */
	public static Threat of(LivingEntity entity) {
		if (entity.isDeadOrDying() || entity.isRemoved()) {
			return Threat.NORMAL;
		}
		Threat threat = Threat.shown(entity);
		if (threat != Threat.NORMAL && entity == Minecraft.getInstance().getCameraEntity()) {
			return Threat.NORMAL;
		}
		return threat;
	}

	/** Size multiplier for the camera's distance. */
	static float grow(EntityRenderState state) {
		float distance = (float) Math.sqrt(state.distanceToCameraSq);
		return 1.0F + (GROW_MAX - 1.0F) * Mth.clamp((distance - GROW_FROM) / GROW_SPAN, 0.0F, 1.0F);
	}

	private static float width(EntityRenderState state, Threat threat) {
		return WIDTH[threat.ordinal()] * grow(state);
	}

	private static float bob(EntityRenderState state, float width) {
		return Mth.sin(state.ageInTicks * 0.09F) * 0.08F * width;
	}

	/** Height of the badge's top over the feet, bob left out so the name above it holds still. */
	static float top(EntityRenderState state, Threat threat) {
		float width = width(state, threat);
		return state.boundingBoxHeight + GAP + width * ROWS[threat.ordinal()] / 16.0F + 0.08F * width;
	}

	private static boolean shows(EntityRenderState state, Threat threat) {
		return threat != null && threat != Threat.NORMAL && !state.isInvisible
			&& state.distanceToCameraSq < FADE_TO * FADE_TO;
	}

	/** Called at the start of EntityRenderer.submit, with the pose at the mob's feet. */
	public static void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Threat threat = state.getData(KEY);
		if (!shows(state, threat)) {
			return;
		}
		float distance = (float) Math.sqrt(state.distanceToCameraSq);
		float fade = 1.0F - Mth.clamp((distance - FADE_FROM) / (FADE_TO - FADE_FROM), 0.0F, 1.0F);
		int step = Mth.clamp(Math.round(fade * FADE_STEPS), 0, FADE_STEPS);
		if (step == 0) {
			return;
		}
		if (quads == null) {
			build();
		}
		float width = width(state, threat);
		poseStack.pushPose();
		poseStack.translate(0.0F, state.boundingBoxHeight + GAP + bob(state, width), 0.0F);
		poseStack.mulPose(camera.orientation);
		poseStack.scale(width, width, width);
		collector.submitCustomGeometry(poseStack, type, quads[threat.ordinal()][step]);
		poseStack.popPose();
	}

	/**
	 * How far to lift the name so it clears the badge. Called inside EntityRenderer.submitNameDisplay,
	 * whose pose is pushed there and popped after, so the lift touches nothing else.
	 */
	public static float nameLift(EntityRenderState state) {
		Threat threat = state.getData(KEY);
		Vec3 attachment = state.nameTagAttachment;
		if (!shows(state, threat) || attachment == null) {
			return 0.0F;
		}
		return Math.max(0.0F, top(state, threat) + 0.04F - (float) attachment.y - NAME_BOTTOM);
	}

	private static void build() {
		// The name tag's own type: lit by itself and not shaded by the sun the way a body is, which turned
		// the gold to mustard at noon.
		type = RenderTypes.text(TEXTURE);
		Threat[] threats = Threat.values();
		quads = new Quad[threats.length][];
		for (Threat threat : threats) {
			int tier = threat.ordinal();
			quads[tier] = new Quad[FADE_STEPS + 1];
			if (ROWS[tier] == 0) {
				continue;
			}
			float u0 = (tier - 1) * 16.0F / SHEET_W;
			float u1 = tier * 16.0F / SHEET_W;
			float v0 = (SHEET_H - ROWS[tier]) / SHEET_H;
			for (int step = 1; step <= FADE_STEPS; step++) {
				quads[tier][step] = new Quad(u0, u1, v0, ROWS[tier] / 16.0F, Math.round(255.0F * step / FADE_STEPS));
			}
		}
	}

	/** One insignia, one alpha: a unit-wide quad standing on its bottom edge, drawn both ways round. */
	private record Quad(float u0, float u1, float v0, float height, int alpha) implements SubmitNodeCollector.CustomGeometryRenderer {
		@Override
		public void render(PoseStack.Pose pose, VertexConsumer buffer) {
			put(pose, buffer, -0.5F, 0.0F, this.u0, 1.0F);
			put(pose, buffer, 0.5F, 0.0F, this.u1, 1.0F);
			put(pose, buffer, 0.5F, this.height, this.u1, this.v0);
			put(pose, buffer, -0.5F, this.height, this.u0, this.v0);
			put(pose, buffer, -0.5F, this.height, this.u0, this.v0);
			put(pose, buffer, 0.5F, this.height, this.u1, this.v0);
			put(pose, buffer, 0.5F, 0.0F, this.u1, 1.0F);
			put(pose, buffer, -0.5F, 0.0F, this.u0, 1.0F);
		}

		private void put(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v) {
			buffer.addVertex(pose, x, y, 0.0F)
				.setColor(255, 255, 255, this.alpha)
				.setUv(u, v)
				.setLight(0xF000F0);
		}
	}
}
