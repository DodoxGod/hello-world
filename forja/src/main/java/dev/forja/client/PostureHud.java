package dev.forja.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The balance of whatever you are fighting, as a small bar under the crosshair: the mob you are looking
 * at, or the one you hit last. A hidden bar is a mechanic nobody finds, and knowing that two more blows
 * will stagger it is what makes pressing the attack a decision.
 *
 * <p>It fills amber and turns red close to breaking; while the mob is staggered it glows gold and runs
 * down with the time left to punish it.
 */
public final class PostureHud implements HudElement {
	private static final int WIDTH = 41;
	private static final int HEIGHT = 2;
	/** How long the last mob you hit keeps its bar when you look away, in ticks. */
	private static final double REMEMBER_TICKS = 60.0;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		LivingEntity target = target(minecraft, partial);
		if (target == null) {
			return;
		}
		float stagger = CombatAnims.staggerLeft(target.getId(), partial);
		float posture = CombatAnims.posture(target.getId(), partial);
		if (stagger < 0.0F && posture < 0.02F) {
			return;
		}
		int x = graphics.guiWidth() / 2 - WIDTH / 2;
		int y = graphics.guiHeight() / 2 + 9;
		float time = (Util.getMillis() % 60000L) / 1000.0F;
		HudBars.well(graphics, x, y, WIDTH, HEIGHT);
		if (stagger >= 0.0F) {
			int filled = Math.round(WIDTH * stagger);
			float pulse = 0.5F + 0.5F * Mth.sin(time * 12.0F);
			HudBars.fill(graphics, x, x + filled, y, HEIGHT, 0xFFD75E, time, x, WIDTH);
			HudBars.halo(graphics, x, y, WIDTH, HEIGHT, 0xFFD75E, 0.4F + 0.6F * pulse);
			return;
		}
		int filled = Math.round(WIDTH * Mth.clamp(posture, 0.0F, 1.0F));
		int colour = posture > 0.75F ? 0xE0533D : posture > 0.45F ? 0xE88A3C : 0xE8C547;
		// Filling from both ends towards the middle: when they meet, it breaks.
		int half = filled / 2;
		HudBars.fill(graphics, x, x + half, y, HEIGHT, colour, time, x, WIDTH);
		HudBars.fill(graphics, x + WIDTH - (filled - half), x + WIDTH, y, HEIGHT, colour, time, x, WIDTH);
		if (posture > 0.75F) {
			HudBars.halo(graphics, x, y, WIDTH, HEIGHT, colour, (posture - 0.75F) * 3.0F);
		}
	}

	private static LivingEntity target(Minecraft minecraft, float partial) {
		Entity looked = minecraft.crosshairPickEntity;
		if (looked instanceof LivingEntity living && living.isAlive() && CombatAnims.get(living.getId()) != null) {
			return living;
		}
		if (CombatAnims.ticksSinceLastHit(partial) > REMEMBER_TICKS) {
			return null;
		}
		Entity last = minecraft.level.getEntity(CombatAnims.lastHitEntity());
		return last instanceof LivingEntity living && living.isAlive() ? living : null;
	}
}
