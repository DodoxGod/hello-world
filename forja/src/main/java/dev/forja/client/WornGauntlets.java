package dev.forja.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.Forja;
import dev.forja.forge.ForgeType;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Forged gauntlets are worn, not held: whoever has a pair in hand wears one on each hand, drawn as plates
 * around the fist and the wrist of the arm itself, so they follow every punch and guard the arm makes.
 * The flat item is only for inventories.
 *
 * <p>Each gauntlet is built to the arm it goes on (a player's, a slim one, an armour stand's stick) and
 * tinted part by part the way the item's layers are: the mitt and its cuff by the first material, the
 * plates over the back of the hand and the knuckles by the second, the rivets by the third.
 */
public final class WornGauntlets {
	private static final Identifier SKIN = Forja.id("textures/entity/guanteletes_puestos.png");
	private static final float TEXTURE_W = 64.0F;
	private static final float TEXTURE_H = 32.0F;
	/** The colours the item model falls back on for each part when the stack has none: HANDLE, HEAD and EXTRA. */
	private static final int[] DEFAULT_COLOURS = {0xB8894F, 0xE4E4E4, 0xB8894F};
	/** Where each part's skin starts on the texture: leather, plate, rivet heads. */
	private static final int[][] SKIN_AT = {{0, 0}, {32, 0}, {0, 16}};

	/** An arm hanging straight down, by the box vanilla gives a player: used for an arm with no box of its own. */
	private static final float[] PLAIN_ARM = {-3.0F, -2.0F, -2.0F, 1.0F, 10.0F, 2.0F};

	/** The three parts of the gauntlet built for each arm it has been worn on, kept for as long as the arm is. */
	private static final Map<ModelPart, ModelPart[]> BUILT = new WeakHashMap<>();

	private WornGauntlets() {
	}

	/** Whether this is a pair of forged gauntlets. */
	public static boolean is(ItemStack stack) {
		ForgedParts parts = stack.isEmpty() ? null : stack.get(ModComponents.PARTS);
		return parts != null && parts.type() == ForgeType.GUANTELETES;
	}

	/**
	 * The gauntlets a hand wears: the pair in that hand, or else the pair in the other one (a pair dresses
	 * both hands), or empty.
	 */
	public static ItemStack on(ItemStack thisHand, ItemStack otherHand) {
		if (is(thisHand)) {
			return thisHand;
		}
		return is(otherHand) ? otherHand : ItemStack.EMPTY;
	}

	/**
	 * Draws a gauntlet over an arm. The pose stack is where the arm's parent is (the model's root in third
	 * person, the first-person hand's frame in first); the arm's own turn is applied here.
	 *
	 * @param left whether this is a left arm, so the plates go on the back of that hand
	 */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int outline, ModelPart arm, boolean left,
		ItemStack gauntlets) {
		ModelPart[] parts = BUILT.computeIfAbsent(arm, part -> build(part, left));
		CustomModelData colours = gauntlets.get(DataComponents.CUSTOM_MODEL_DATA);
		RenderType type = RenderTypes.entityCutout(SKIN);
		poseStack.pushPose();
		arm.translateAndRotate(poseStack);
		for (int slot = 0; slot < parts.length; slot++) {
			Integer colour = colours == null ? null : colours.getColor(slot);
			int tint = 0xFF000000 | (colour == null ? DEFAULT_COLOURS[slot] : colour);
			collector.submitModelPart(parts[slot], poseStack, type, light, OverlayTexture.NO_OVERLAY, null, tint, null, outline);
		}
		poseStack.popPose();
	}

	/**
	 * The gauntlet for one arm, in the arm's own space (y runs down the arm to the hand, -z is forward), as
	 * one part per material. It is sized off the arm's box: a mitt half a pixel proud of the hand, a wider
	 * cuff over the wrist, a plate over the back of the hand with a ridge along the knuckles, and rivets.
	 */
	private static ModelPart[] build(ModelPart arm, boolean left) {
		float[] box = armBox(arm);
		float cx = (box[0] + box[3]) / 2.0F;
		float cz = (box[2] + box[5]) / 2.0F;
		float hw = (box[3] - box[0]) / 2.0F;
		float hd = (box[5] - box[2]) / 2.0F;
		float end = box[4];
		// The back of the hand faces out from the body: -x on the right arm, +x on the left.
		float out = left ? 1.0F : -1.0F;

		List<ModelPart.Cube> leather = new ArrayList<>();
		// The cuff, flared over the wrist...
		leather.add(cube(0, cx - hw - 1.0F, end - 8.0F, cz - hd - 1.0F, cx + hw + 1.0F, end - 4.5F, cz + hd + 1.0F));
		// ...the mitt over the hand, a little past the end of it where the fingers curl...
		leather.add(cube(0, cx - hw - 0.5F, end - 4.5F, cz - hd - 0.5F, cx + hw + 0.5F, end + 0.5F, cz + hd + 0.5F));
		// ...and the thumb, folded across the front of the fist.
		leather.add(cube(0, cx - 0.9F, end - 3.6F, cz - hd - 1.2F, cx + 0.9F, end - 1.0F, cz - hd - 0.5F));

		List<ModelPart.Cube> plate = new ArrayList<>();
		float back = cx + out * (hw + 0.5F);
		// The plate over the back of the hand...
		plate.add(cube(1, back, end - 4.0F, cz - hd - 0.2F, back + out * 0.6F, end - 0.3F, cz + hd + 0.2F));
		// ...a band round the top of the cuff...
		plate.add(cube(1, cx - hw - 1.2F, end - 8.3F, cz - hd - 1.2F, cx + hw + 1.2F, end - 7.3F, cz + hd + 1.2F));
		// ...and the knuckles, three ridges across the end of the fist on the side of the back of the hand.
		float span = 2.0F * (hd + 0.5F);
		float gap = 0.35F;
		float each = (span - 2.0F * gap) / 3.0F;
		for (int i = 0; i < 3; i++) {
			float z0 = cz - hd - 0.5F + i * (each + gap);
			plate.add(cube(1, back + out * 0.4F, end + 0.5F, z0, cx - out * 0.3F, end + 1.25F, z0 + each));
		}

		List<ModelPart.Cube> rivets = new ArrayList<>();
		float plateFace = back + out * 0.6F;
		float cuffFace = cx + out * (hw + 1.0F);
		for (float side : new float[] {-1.0F, 1.0F}) {
			float z = cz + side * hd * 0.5F;
			rivets.add(cube(2, plateFace, end - 2.6F, z - 0.35F, plateFace + out * 0.35F, end - 1.9F, z + 0.35F));
			rivets.add(cube(2, cuffFace, end - 6.6F, z - 0.35F, cuffFace + out * 0.35F, end - 5.9F, z + 0.35F));
		}
		return new ModelPart[] {part(leather), part(plate), part(rivets)};
	}

	/** The arm's own box (min x, y, z, then max), or a plain player's arm if it has none. */
	private static float[] armBox(ModelPart arm) {
		float[] box = new float[6];
		boolean[] found = {false};
		arm.visit(new PoseStack(), (pose, path, index, cube) -> {
			if (!found[0] && path.isEmpty()) {
				box[0] = cube.minX;
				box[1] = cube.minY;
				box[2] = cube.minZ;
				box[3] = cube.maxX;
				box[4] = cube.maxY;
				box[5] = cube.maxZ;
				found[0] = true;
			}
		});
		return found[0] ? box : PLAIN_ARM.clone();
	}

	/** A box between two corners, given in either order, skinned from its part's corner of the texture. */
	private static ModelPart.Cube cube(int slot, float x0, float y0, float z0, float x1, float y1, float z1) {
		float minX = Math.min(x0, x1);
		float minY = Math.min(y0, y1);
		float minZ = Math.min(z0, z1);
		return new ModelPart.Cube(SKIN_AT[slot][0], SKIN_AT[slot][1], minX, minY, minZ,
			Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), 0.0F, 0.0F, 0.0F, false, TEXTURE_W, TEXTURE_H,
			EnumSet.allOf(Direction.class));
	}

	private static ModelPart part(List<ModelPart.Cube> cubes) {
		return new ModelPart(cubes, Map.of());
	}
}
