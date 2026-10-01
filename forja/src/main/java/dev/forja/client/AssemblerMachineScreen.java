package dev.forja.client;

import java.util.List;

import dev.forja.Forja;
import dev.forja.block.entity.AssemblerMachineBlockEntity;
import dev.forja.forge.ForgeType;
import dev.forja.item.CastingFrameItem;
import dev.forja.menu.AssemblerMachineMenu;
import dev.forja.menu.ForgeMenu;
import dev.forja.part.PartType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The assembler's screen.
 *
 * <p>The forge table's star, because that is what it is: the same five points in the same places, the
 * frame (if any) where the piece lies on the table, and the finished piece in the slot at the end of the
 * channel. What it will make is worked out here from the slots by the machine's own rule
 * (AssemblerMachineBlockEntity.plan), so the name at the top and the ghost in the output slot are what
 * will come out; what it is actually doing — working, cold, waiting on the output — is the server's word.
 * While it works the engraving of the star takes the colour of hot steel, as the table's does under a
 * smith's hammer, and the channel fills.
 */
public class AssemblerMachineScreen extends AbstractContainerScreen<AssemblerMachineMenu> {
	private static final Identifier TEXTURE = Forja.id("textures/gui/montadora.png");
	/** The forge table's lit star: same geometry, so the same picture, in the machine's own colour. */
	private static final Identifier STAR_LIT = Forja.id("textures/gui/estrella_viva.png");

	private static final int TEXT = 0xFF3B2A1A;
	private static final int MUTED = 0xFF6B6455;
	private static final int GOOD = 0xFF2E7D32;
	private static final int BAD = 0xFFA02020;

	/** The channel from the star to the output, matching textures/gui/montadora.png. */
	private static final int CHANNEL_X = 102;
	private static final int CHANNEL_Y = 54;
	private static final int CHANNEL_W = 40;

	/** The right-hand column: what it will make above the channel, heat and state below the output. */
	private static final int INFO_X = 104;
	private static final int INFO_W = 96;
	private static final int MAKES_Y = 19;
	private static final int STATE_Y = 71;

	/** The colour of the panel's stone, for the veil over a ghost. */
	private static final int PANEL = 0xC4BAA6;

	public AssemblerMachineScreen(AssemblerMachineMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 206, 196);
		this.inventoryLabelX = AssemblerMachineMenu.INVENTORY_X;
		this.inventoryLabelY = AssemblerMachineMenu.INVENTORY_Y - 11;
	}

	/** The last plan worked out, and what it was worked out from: redone only when the slots change. */
	private AssemblerMachineBlockEntity.Plan cached = AssemblerMachineBlockEntity.Plan.NONE;
	private List<ItemStack> cachedFrom = List.of();

	private AssemblerMachineBlockEntity.Plan plan() {
		List<ItemStack> now = new java.util.ArrayList<>(this.menu.points());
		now.add(this.menu.frame());
		boolean same = now.size() == this.cachedFrom.size();
		for (int i = 0; same && i < now.size(); i++) {
			same = ItemStack.matches(now.get(i), this.cachedFrom.get(i));
		}
		if (!same) {
			this.cachedFrom = now.stream().map(ItemStack::copy).toList();
			this.cached = AssemblerMachineBlockEntity.plan(this.menu.points(), this.menu.frame(), this.minecraft.level.registryAccess());
		}
		return this.cached;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		boolean working = this.menu.job() == AssemblerMachineBlockEntity.JOB_WORKING;
		if (working) {
			// Hot steel breathing in the engraving, the way the table's star takes fire under the hammer.
			double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0);
			int glow = (int) (120 + 110 * pulse) << 24 | 0xFFC27A;
			g.blit(RenderPipelines.GUI_TEXTURED, STAR_LIT, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256, glow);
		}
		float progress = this.menu.progress();
		ForjaUi.channel(g, x + CHANNEL_X, y + CHANNEL_Y, CHANNEL_W, 5, progress, 0xE0762A);
		// What will come out, as a ghost in the empty output slot: the piece itself, faded.
		AssemblerMachineBlockEntity.Plan plan = this.plan();
		if (plan.ready() && this.menu.output().isEmpty()) {
			this.ghost(g, plan.result(), x + AssemblerMachineMenu.OUTPUT_X, y + AssemblerMachineMenu.OUTPUT_Y, 150 - Math.round(90 * progress));
		}
		// And an empty centre says what goes there: a frame, faded out.
		if (this.menu.frame().isEmpty()) {
			this.ghost(g, CastingFrameItem.of(ForgeType.PICO), x + ForgeMenu.CENTER_X, y + ForgeMenu.CENTER_Y, 185);
		}
	}

	/** An item under a veil of the panel's own stone: there, but not yet. */
	private void ghost(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int veil) {
		g.item(stack, x, y);
		g.nextStratum();
		g.fill(x, y, x + 16, y + 16, Math.max(0, Math.min(255, veil)) << 24 | PANEL);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
		g.text(this.font, this.title, 8, 5, CrucibleScreen.ON_WOOD, true);

		AssemblerMachineBlockEntity.Plan plan = this.plan();
		// Above the channel: what the star will make, or what the frame asks for.
		Component makes;
		if (plan.ready()) {
			makes = Component.translatable("gui.forja.montadora.hara", plan.result().getHoverName());
		} else if (plan.type() != null && CastingFrameItem.typeOf(this.menu.frame()) != null) {
			makes = Component.translatable("gui.forja.montadora.marco", plan.type().displayName());
		} else {
			makes = Component.translatable("gui.forja.montadora.estrella");
		}
		CrucibleScreen.lines(g, this.font, makes, INFO_X, MAKES_Y, INFO_W, 4, plan.ready() ? TEXT : MUTED);

		// Under the output: the heat, then what it is doing about it.
		int work = this.menu.work();
		Component heat = work > 0
			? Component.translatable("gui.forja.montadora.calor", this.menu.heat().displayName(), String.format(java.util.Locale.ROOT, "%.1f", work / 20.0F))
			: Component.translatable("gui.forja.montadora.calor_frio", this.menu.heat().displayName());
		CrucibleScreen.lines(g, this.font, heat, INFO_X, STATE_Y, INFO_W, 2, work > 0 ? MUTED : BAD);
		// What it is doing goes right under the heat, however many lines that took: at a fixed seventeen pixels
		// down, a three-line answer ("it has everything but heat: a campfire under it or beside it") ran into
		// the top of the inventory.
		int heatLines = Math.min(2, this.font.split(heat, Math.round(INFO_W / 0.75F)).size());
		int job = this.menu.job();
		int colour = job == AssemblerMachineBlockEntity.JOB_WORKING ? GOOD
			: job == AssemblerMachineBlockEntity.JOB_EMPTY || job == AssemblerMachineBlockEntity.JOB_MISSING ? MUTED : BAD;
		CrucibleScreen.lines(g, this.font, this.doing(plan), INFO_X, STATE_Y + heatLines * 8 + 2, INFO_W, 3, colour);
	}

	/** What it is doing, in words: the server's state, the client's plan for the names in it. */
	private Component doing(AssemblerMachineBlockEntity.Plan plan) {
		switch (this.menu.job()) {
			case AssemblerMachineBlockEntity.JOB_WORKING:
				return Component.translatable("gui.forja.montadora.montando", Math.round(this.menu.progress() * 100.0F));
			case AssemblerMachineBlockEntity.JOB_COLD:
				return Component.translatable("gui.forja.montadora.frio");
			case AssemblerMachineBlockEntity.JOB_FULL:
				return Component.translatable("gui.forja.montadora.llena");
			case AssemblerMachineBlockEntity.JOB_NOTHING:
				return Component.translatable("gui.forja.montadora.nada");
			case AssemblerMachineBlockEntity.JOB_BEYOND:
				return Component.translatable("gui.forja.montadora.talabarteria");
			default:
				break;
		}
		List<PartType> missing = plan.missing();
		if (missing != null && !missing.isEmpty()) {
			MutableComponent names = Component.empty();
			for (int i = 0; i < missing.size(); i++) {
				if (i > 0) {
					names.append(", ");
				}
				names.append(missing.get(i).displayName());
			}
			return Component.translatable("gui.forja.montadora.faltan", names);
		}
		return Component.translatable("gui.forja.montadora.vacia");
	}
}
