package dev.forja.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.forja.Forja;
import dev.forja.forge.ForgeType;
import dev.forja.part.ForgedParts;
import dev.forja.registry.ModComponents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The grimorio in hand is a book, not a card: the enchanting table's book, bound in the tome's own
 * materials, held shut by its spine. When its holder strikes with it or reads a spell from it, it falls
 * open and the pages riffle, and a moment later it shuts again. The flat icon is for inventories.
 *
 * <p>The book is the vanilla one's shape (two covers on a spine, a block of pages each side and two
 * loose pages that turn) with a gem set in the front cover and a cap on each corner, drawn in four
 * passes so each takes its own tint the way the item's layers do: the covers by the TAPAS material,
 * the gem by the NUCLEO, the caps by the REMACHE; the pages and the leather of the spine stay as they are.
 */
public final class HeldTome {
	/** How far the tome in an entity's hand is open and how far its pages have turned, worked out while the entity is at hand. */
	public static final RenderStateDataKey<Reading> KEY = RenderStateDataKey.create(() -> "forja:grimorio");

	private static final Identifier TEXTURE = Forja.id("textures/entity/grimorio.png");
	/** The widest the covers go, in radians from shut, each side. */
	private static final float OPEN_ANGLE = 1.2F;
	/** Pages turned each tick while the book is open (two loose pages, so a cycle is two pages). */
	private static final float TURNS_PER_TICK = 0.11F;
	/** How long a book stays open after the blow or spell that opened it, then how long it takes to shut, in ticks. */
	private static final float LINGER_TICKS = 8.0F;
	private static final float CLOSE_TICKS = 6.0F;
	/** The share of a swing it takes to fall open. */
	private static final float OPENING_SHARE = 0.25F;
	/** The book's size against the vanilla one, which is a little large for a fist. */
	private static final float SCALE = 0.85F;
	/** Smaller again in first person, where it is a hand's breadth from the eye and would fill the screen open. */
	private static final float FIRST_PERSON_SCALE = 0.62F;

	/** The tint passes: which parts each one draws. */
	private static final int COVERS = 0;
	private static final int LEAVES = 1;
	private static final int GEM = 2;
	private static final int CAPS = 3;
	/** The item slot whose material tints each pass, or -1 for none. */
	private static final int[] SLOT = {1, -1, 0, 2};
	/** The colours the item model falls back on for those slots: HEAD, HANDLE and EXTRA. */
	private static final int[] DEFAULT_COLOURS = {0xE4E4E4, 0xB8894F, 0xB8894F};

	/** When each entity's tome was last seen open, in level ticks. */
	private static final Map<LivingEntity, double[]> LAST_OPEN = new WeakHashMap<>();

	private static @Nullable TomeModel model;

	/** How far a tome is open, 0 (shut) to 1, and how many pages have turned. */
	public record Reading(float open, float turned) {
		public static final Reading SHUT = new Reading(0.0F, 0.0F);
	}

	private HeldTome() {
	}

	/** Whether this is a forged tome. */
	public static boolean is(ItemStack stack) {
		ForgedParts parts = stack.isEmpty() ? null : stack.get(ModComponents.PARTS);
		return parts != null && parts.type() == ForgeType.GRIMORIO;
	}

	/**
	 * How far open an entity's tome is right now. It opens with a swing of the hand that holds it (a blow,
	 * or a spell: casting swings the arm), while a monster reads a spell out of it (its warning), and while
	 * a blow with it is being charged; it stays open a moment after, then shuts.
	 */
	public static Reading reading(LivingEntity entity, float partialTick) {
		InteractionHand hand = is(entity.getMainHandItem()) ? InteractionHand.MAIN_HAND
			: is(entity.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
		if (hand == null) {
			return Reading.SHUT;
		}
		double now = CombatAnims.now(partialTick);
		double[] last = LAST_OPEN.computeIfAbsent(entity, e -> new double[] {-1.0E9});
		float open = 0.0F;

		InteractionHand swung = entity.swingingArm == null ? InteractionHand.MAIN_HAND : entity.swingingArm;
		float attack = entity.getAttackAnim(partialTick);
		if (swung == hand && entity.swinging && attack > 0.0F) {
			open = Math.max(open, Ease.outCubic(Math.min(1.0F, attack / OPENING_SHARE)));
		}
		CombatAnims.State anim = CombatAnims.get(entity.getId());
		if (anim != null) {
			// A monster reading its spell: the book opens as the warning starts and stays open through it.
			float warning = CombatAnims.progress(anim.telegraphAt, anim.telegraphTicks, now);
			if (warning >= 0.0F) {
				open = Math.max(open, Ease.outCubic(Math.min(1.0F, warning * 4.0F)));
			}
		}
		float charge = entity == Minecraft.getInstance().player ? CombatClient.localCharge(partialTick)
			: CombatAnims.charge(entity.getId(), partialTick);
		if (charge >= 0.0F) {
			open = Math.max(open, Ease.outCubic(Math.min(1.0F, charge * 3.0F)));
		}

		if (open >= 1.0F) {
			last[0] = now;
		} else {
			// Past whatever opened it: held open a moment, then shut.
			float since = (float) (now - last[0]);
			if (since < LINGER_TICKS) {
				open = 1.0F;
			} else if (since < LINGER_TICKS + CLOSE_TICKS) {
				open = Math.max(open, 1.0F - Ease.inOutSine((since - LINGER_TICKS) / CLOSE_TICKS));
			}
		}
		// The pages keep turning while it is open, so they are wherever time has got them to when it opens.
		return open <= 0.0F ? Reading.SHUT : new Reading(open, (float) (now * TURNS_PER_TICK % 1000.0));
	}

	/**
	 * Draws the tome held by its spine in a hand, in third person: the pose stack is at the hand as
	 * ArmedModel.translateToHand leaves it (the shoulder, turned with the arm). The spine runs through
	 * the fist front to back and the book hangs out of the bottom of it, covers to the sides, so it falls
	 * open to either side of the arm.
	 */
	public static void submitInHand(PoseStack poseStack, SubmitNodeCollector collector, int light, int outline, ItemStack tome,
		boolean left, @Nullable Reading reading) {
		poseStack.pushPose();
		// The middle of the fist, a pixel down the fingers: the spine lies across the palm.
		poseStack.translate((left ? 1.0F : -1.0F) / 16.0F, 9.0F / 16.0F, 0.0F);
		// The book's spine (its y) front to back along the fist, its width (x) on down the arm, its covers
		// (z) facing out to the sides, the front one away from the body.
		poseStack.mulPose(frame(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(0.0F, 0.0F, left ? -1.0F : 1.0F)));
		poseStack.scale(SCALE, SCALE, SCALE);
		submitBook(poseStack, collector, light, outline, tome, reading);
		poseStack.popPose();
	}

	/**
	 * Draws the tome in first person, where the held item would be: the pose stack is the hand's frame
	 * after the arm and the swing have been applied (x right, y up, z towards the eye). It is held up by
	 * the spine on the side of the hand, the front cover turned to the eye, and opens towards it.
	 */
	public static void submitFirstPerson(PoseStack poseStack, SubmitNodeCollector collector, int light, ItemStack tome, boolean left,
		@Nullable Reading reading) {
		int invert = left ? -1 : 1;
		poseStack.pushPose();
		poseStack.translate(invert * 0.07F, 0.2F, 0.02F);
		poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(invert * -28.0F));
		poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-12.0F));
		poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(invert * -8.0F));
		// Width towards the middle of the screen, spine upright (upside down in the left hand, so the
		// front cover still faces the eye), covers to and from the eye.
		poseStack.mulPose(frame(new Vector3f(-invert, 0.0F, 0.0F), new Vector3f(0.0F, invert, 0.0F)));
		poseStack.scale(FIRST_PERSON_SCALE, FIRST_PERSON_SCALE, FIRST_PERSON_SCALE);
		submitBook(poseStack, collector, light, 0, tome, reading);
		poseStack.popPose();
	}

	/** The turn that lays the book's width (x) along {@code width} and its spine (y) along {@code spine}. */
	private static Quaternionf frame(Vector3f width, Vector3f spine) {
		Vector3f covers = width.cross(spine, new Vector3f());
		return new Quaternionf().setFromNormalized(new Matrix3f(width, spine, covers));
	}

	/** The book in its own space, spine on the y axis, in its four tints. Model pixels are sixteenths. */
	private static void submitBook(PoseStack poseStack, SubmitNodeCollector collector, int light, int outline, ItemStack tome,
		@Nullable Reading reading) {
		if (model == null) {
			model = new TomeModel(TomeModel.createLayer().bakeRoot());
		}
		Reading at = reading == null ? Reading.SHUT : reading;
		float openness = at.open() * OPEN_ANGLE;
		float flip1 = Mth.clamp(Mth.frac(at.turned() + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
		float flip2 = Mth.clamp(Mth.frac(at.turned() + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
		CustomModelData colours = tome.get(DataComponents.CUSTOM_MODEL_DATA);
		for (int pass = COVERS; pass <= CAPS; pass++) {
			int tint = -1;
			if (SLOT[pass] >= 0) {
				Integer colour = colours == null ? null : colours.getColor(SLOT[pass]);
				tint = 0xFF000000 | (colour == null ? DEFAULT_COLOURS[SLOT[pass]] : colour);
			}
			collector.submitModel(model, new TomeModel.State(openness, flip1, flip2, pass), poseStack, model.renderType(TEXTURE),
				light, OverlayTexture.NO_OVERLAY, tint, null, outline, null);
		}
	}

	/**
	 * The book: the vanilla book's covers, spine and pages, plus the gem and the corner caps. Each draw
	 * shows only the parts of one tint pass, and opens the covers as far as its state says.
	 */
	static final class TomeModel extends net.minecraft.client.model.Model<TomeModel.State> {
		record State(float openness, float flip1, float flip2, int pass) {
		}

		private final ModelPart leftLid;
		private final ModelPart rightLid;
		private final ModelPart seam;
		private final ModelPart leftPages;
		private final ModelPart rightPages;
		private final ModelPart flipPage1;
		private final ModelPart flipPage2;
		private final ModelPart gem;
		private final ModelPart leftCaps;
		private final ModelPart rightCaps;

		TomeModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			this.leftLid = root.getChild("left_lid");
			this.rightLid = root.getChild("right_lid");
			this.seam = root.getChild("seam");
			this.leftPages = root.getChild("left_pages");
			this.rightPages = root.getChild("right_pages");
			this.flipPage1 = root.getChild("flip_page1");
			this.flipPage2 = root.getChild("flip_page2");
			this.gem = this.leftLid.getChild("gem");
			this.leftCaps = this.leftLid.getChild("caps");
			this.rightCaps = this.rightLid.getChild("caps");
		}

		static LayerDefinition createLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			// The vanilla book, part for part: the covers are a pixel either side of the spine and thin as paper.
			PartDefinition leftLid = root.addOrReplaceChild("left_lid",
				CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -5.0F, -0.005F, 6.0F, 10.0F, 0.005F), PartPose.offset(0.0F, 0.0F, -1.0F));
			PartDefinition rightLid = root.addOrReplaceChild("right_lid",
				CubeListBuilder.create().texOffs(16, 0).addBox(0.0F, -5.0F, -0.005F, 6.0F, 10.0F, 0.005F), PartPose.offset(0.0F, 0.0F, 1.0F));
			root.addOrReplaceChild("seam",
				CubeListBuilder.create().texOffs(12, 0).addBox(-1.0F, -5.0F, 0.0F, 2.0F, 10.0F, 0.005F), PartPose.rotation(0.0F, Mth.HALF_PI, 0.0F));
			root.addOrReplaceChild("left_pages", CubeListBuilder.create().texOffs(0, 10).addBox(0.0F, -4.0F, -0.99F, 5.0F, 8.0F, 1.0F), PartPose.ZERO);
			root.addOrReplaceChild("right_pages", CubeListBuilder.create().texOffs(12, 10).addBox(0.0F, -4.0F, -0.01F, 5.0F, 8.0F, 1.0F), PartPose.ZERO);
			CubeListBuilder page = CubeListBuilder.create().texOffs(24, 10).addBox(0.0F, -4.0F, 0.0F, 5.0F, 8.0F, 0.005F);
			root.addOrReplaceChild("flip_page1", page, PartPose.ZERO);
			root.addOrReplaceChild("flip_page2", page, PartPose.ZERO);
			// The cover's own outside is its +z face. The gem sits in the middle of the front cover...
			leftLid.addOrReplaceChild("gem", CubeListBuilder.create().texOffs(40, 20).addBox(-4.0F, -1.25F, 0.0F, 2.0F, 2.5F, 0.8F), PartPose.ZERO);
			// ...and a metal cap over each corner of both covers.
			leftLid.addOrReplaceChild("caps", caps(-6.0F), PartPose.ZERO);
			rightLid.addOrReplaceChild("caps", caps(0.0F), PartPose.ZERO);
			return LayerDefinition.create(mesh, 64, 32);
		}

		/** A cap on each corner of a cover running from {@code from} to six pixels past it. */
		private static CubeListBuilder caps(float from) {
			CubeListBuilder caps = CubeListBuilder.create().texOffs(48, 20);
			for (float x : new float[] {from, from + 4.8F}) {
				for (float y : new float[] {-5.0F, 3.8F}) {
					caps.addBox(x, y, 0.0F, 1.2F, 1.2F, 0.4F);
				}
			}
			return caps;
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float open = state.openness();
			this.leftLid.yRot = Mth.PI + open;
			this.rightLid.yRot = -open;
			this.leftPages.yRot = open;
			this.rightPages.yRot = -open;
			this.flipPage1.yRot = open - open * 2.0F * state.flip1();
			this.flipPage2.yRot = open - open * 2.0F * state.flip2();
			float out = Mth.sin(open);
			this.leftPages.x = out;
			this.rightPages.x = out;
			this.flipPage1.x = out;
			this.flipPage2.x = out;

			int pass = state.pass();
			this.leftLid.skipDraw = pass != COVERS;
			this.rightLid.skipDraw = pass != COVERS;
			this.seam.visible = pass == LEAVES;
			this.leftPages.visible = pass == LEAVES;
			this.rightPages.visible = pass == LEAVES;
			this.flipPage1.visible = pass == LEAVES;
			this.flipPage2.visible = pass == LEAVES;
			this.gem.visible = pass == GEM;
			this.leftCaps.visible = pass == CAPS;
			this.rightCaps.visible = pass == CAPS;
		}
	}
}
