package dev.forja.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A vanilla button, drawn as a Forja one (client/ForjaUi): the guide's "Índice" and "Aceptar" were vanilla's grey
 * stone slabs under a leather book. Everything else — clicks, keys, focus, narration — is vanilla's Button.
 */
public class ForjaButton extends Button {
	private final int base;

	public ForjaButton(int x, int y, int width, int height, Component message, OnPress onPress, int base) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.base = base;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		ForjaUi.Look look = !this.active ? ForjaUi.Look.OFF : this.isHoveredOrFocused() ? ForjaUi.Look.HOVERED : ForjaUi.Look.READY;
		ForjaUi.button(g, Minecraft.getInstance().font, this.getMessage(), this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.base, look);
	}
}
