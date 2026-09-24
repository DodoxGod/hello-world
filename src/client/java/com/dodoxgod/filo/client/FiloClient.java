package com.dodoxgod.filo.client;

import com.dodoxgod.filo.config.FiloConfig;
import com.dodoxgod.filo.network.DodgePayload;
import com.dodoxgod.filo.network.StaminaPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class FiloClient implements ClientModInitializer {
	private static KeyBinding dodgeKey;
	private static int dodgeCooldown;

	@Override
	public void onInitializeClient() {
		dodgeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.filo.dodge", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "category.filo"));

		ClientPlayNetworking.registerGlobalReceiver(StaminaPayload.ID,
				(payload, context) -> ClientStamina.update(payload.stamina(), payload.max()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientStamina.reset());

		ClientTickEvents.END_CLIENT_TICK.register(FiloClient::tick);
		HudRenderCallback.EVENT.register(StaminaHud::render);
	}

	private static void tick(MinecraftClient client) {
		if (dodgeCooldown > 0) dodgeCooldown--;
		ClientPlayerEntity player = client.player;
		while (dodgeKey.wasPressed()) {
			if (player != null) tryDodge(player);
		}
	}

	/** El movimiento lo aplica el cliente (así responde al instante); el servidor da la invulnerabilidad. */
	private static void tryDodge(ClientPlayerEntity player) {
		FiloConfig cfg = FiloConfig.get();
		if (!cfg.dodge.enabled || dodgeCooldown > 0 || !player.isOnGround() || player.isSpectator()) return;
		if (!player.isCreative() && ClientStamina.value() < cfg.stamina.dodgeCost) return;
		if (!ClientPlayNetworking.canSend(DodgePayload.ID)) return;

		float forward = player.input.movementForward;
		float sideways = player.input.movementSideways;
		if (forward == 0 && sideways == 0) forward = -1; // sin dirección: paso atrás

		double yaw = Math.toRadians(player.getYaw());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		Vec3d dir = new Vec3d(sideways * cos - forward * sin, 0.0, forward * cos + sideways * sin).normalize();
		player.setVelocity(dir.x * cfg.dodge.strength, cfg.dodge.lift, dir.z * cfg.dodge.strength);

		dodgeCooldown = cfg.dodge.cooldownTicks;
		ClientPlayNetworking.send(DodgePayload.INSTANCE);
	}
}
