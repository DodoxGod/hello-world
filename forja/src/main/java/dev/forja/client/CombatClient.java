package dev.forja.client;

import dev.forja.combat.ChargePayload;
import dev.forja.combat.CombatRules;
import dev.forja.combat.DodgePayload;
import dev.forja.combat.Stamina;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** The client half of the combat overhaul: the dodge key, the stamina and posture bars, and the fight's poses. */
public final class CombatClient {
	private static int dodgeCooldown;
	/** Ticks the attack button has been held since it went down, and whether that became a charge. */
	private static int attackHeld;
	private static boolean charging;
	private static long chargeStart;

	private CombatClient() {
	}

	public static void register() {
		KeyMapping dodge = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.forja.esquivar",
			com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT, KeyMapping.Category.MOVEMENT));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (dodgeCooldown > 0) dodgeCooldown--;
			while (dodge.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					tryDodge(client.player);
				}
			}
			tickCharge(client);
		});
		CombatAnims.register();
		HudElementRegistry.attachElementAfter(VanillaHudElements.AIR_BAR, dev.forja.Forja.id("barra_estamina"), new StaminaHud());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, dev.forja.Forja.id("barra_postura"), new PostureHud());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, dev.forja.Forja.id("barra_carga"), new ChargeHud());
	}

	/**
	 * The charged blow: the attack button kept down after a swing, with a weapon that has a swing of its
	 * own, looking at no block. Past the delay the charge starts (and the server is told); letting go
	 * strikes. Anything that makes charging odd drops it without striking.
	 */
	private static void tickCharge(net.minecraft.client.Minecraft client) {
		CombatRules rules = CombatAnims.rules();
		LocalPlayer player = client.player;
		boolean down = client.options.keyAttack.isDown();
		boolean able = player != null && client.gui.screen() == null && rules.enabled() && rules.chargedAttack()
			&& !player.isSpectator() && !player.isUsingItem()
			&& dev.forja.combat.ChargedStrike.charges(player.getMainHandItem())
			&& (client.hitResult == null || client.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK);
		if (charging && (!able || !down)) {
			send(able ? ChargePayload.RELEASE : ChargePayload.CANCEL);
			charging = false;
		}
		if (!down || !able) {
			attackHeld = 0;
			return;
		}
		attackHeld++;
		if (!charging && attackHeld == Math.max(1, rules.chargeDelayTicks())) {
			charging = true;
			chargeStart = client.level.getGameTime();
			send(ChargePayload.START);
		}
	}

	private static void send(byte action) {
		if (ClientPlayNetworking.canSend(ChargePayload.TYPE)) {
			ClientPlayNetworking.send(new ChargePayload(action));
		}
	}

	/** How far the local player's charge has got, 0 to 1, or -1 when not charging. */
	public static float localCharge(float partialTick) {
		net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
		if (!charging || client.level == null) {
			return -1.0F;
		}
		float full = Math.max(1, CombatAnims.rules().chargeFullTicks());
		return Math.min(1.0F, (client.level.getGameTime() - chargeStart + partialTick) / full);
	}

	/**
	 * The client moves the player at once, so the dodge feels instant; the server grants the i-frames.
	 * It is judged by the server's rules (see {@link CombatRules}), so the two agree on when it is allowed.
	 */
	private static void tryDodge(LocalPlayer player) {
		CombatRules rules = CombatAnims.rules();
		if (!rules.enabled() || !rules.dodge() || dodgeCooldown > 0 || !player.onGround() || player.isSpectator()) return;
		if (!player.isCreative() && rules.stamina() && player.getAttachedOrElse(Stamina.VALUE, rules.staminaMax()) < rules.dodgeCost()) return;
		if (!ClientPlayNetworking.canSend(DodgePayload.TYPE)) return;

		Vec2 input = player.input.getMoveVector();
		float sideways = input.x;
		float forward = input.y;
		if (forward == 0 && sideways == 0) forward = -1; // no direction: a step back
		double yaw = Math.toRadians(player.getYRot());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		Vec3 dir = new Vec3(sideways * cos - forward * sin, 0.0, forward * cos + sideways * sin).normalize();
		player.setDeltaMovement(dir.x * rules.dodgeStrength(), rules.dodgeLift(), dir.z * rules.dodgeStrength());
		dodgeCooldown = rules.dodgeCooldownTicks();
		ClientPlayNetworking.send(new DodgePayload((float) dir.x, (float) dir.z));
	}
}
