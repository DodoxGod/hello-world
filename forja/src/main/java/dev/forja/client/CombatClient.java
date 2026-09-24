package dev.forja.client;

import dev.forja.combat.CombatConfig;
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

/** The client half of the combat overhaul: the dodge key and the stamina bar. */
public final class CombatClient {
	private static int dodgeCooldown;

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
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.AIR_BAR, dev.forja.Forja.id("barra_estamina"), new StaminaHud());
	}

	/** The client moves the player at once, so the dodge feels instant; the server grants the i-frames. */
	private static void tryDodge(LocalPlayer player) {
		CombatConfig cfg = CombatConfig.get();
		if (!cfg.enabled || !cfg.dodge || dodgeCooldown > 0 || !player.onGround() || player.isSpectator()) return;
		if (!player.isCreative() && player.getAttachedOrElse(Stamina.VALUE, cfg.staminaMax) < cfg.dodgeCost) return;
		if (!ClientPlayNetworking.canSend(DodgePayload.TYPE)) return;

		Vec2 input = player.input.getMoveVector();
		float sideways = input.x;
		float forward = input.y;
		if (forward == 0 && sideways == 0) forward = -1; // no direction: a step back
		double yaw = Math.toRadians(player.getYRot());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		Vec3 dir = new Vec3(sideways * cos - forward * sin, 0.0, forward * cos + sideways * sin).normalize();
		player.setDeltaMovement(dir.x * cfg.dodgeStrength, cfg.dodgeLift, dir.z * cfg.dodgeStrength);
		dodgeCooldown = cfg.dodgeCooldownTicks;
		ClientPlayNetworking.send(DodgePayload.INSTANCE);
	}
}
