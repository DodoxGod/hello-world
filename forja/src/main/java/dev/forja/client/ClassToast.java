package dev.forja.client;

import dev.forja.clase.ClassNetwork;
import dev.forja.clase.ClassProgress;
import dev.forja.clase.PlayerClass;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The toast of a class level (and of a class taken): the class's own mark, its name in its colour, and what
 * the level brought. Drawn on vanilla's advancement toast so it sits with the others in the corner.
 */
public class ClassToast implements Toast {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
	private static final long SHOWN_FOR = 5000L;

	private final PlayerClass clazz;
	private final Component title;
	private final Component text;
	private Toast.Visibility wanted = Toast.Visibility.SHOW;

	public ClassToast(PlayerClass clazz, ClassNetwork.Toast kind, int level) {
		this.clazz = clazz;
		if (kind == ClassNetwork.Toast.LEVEL) {
			this.title = Component.translatable("gui.forja.clase.toast.nivel", clazz.displayName(), level);
			this.text = level >= ClassProgress.MAX_LEVEL ? Component.translatable("gui.forja.clase.toast.maximo")
				: Component.translatable("gui.forja.clase.toast.punto", dev.forja.clase.ClassTree.levelPoints(level) - dev.forja.clase.ClassTree.levelPoints(level - 1), ClassProgress.key(ClassProgress.KEY_TREE));
		} else {
			this.title = clazz.displayName();
			this.text = Component.translatable("gui.forja.clase.toast.elegida", ClassProgress.key(ClassProgress.KEY_TREE));
		}
	}

	@Override
	public Toast.Visibility getWantedVisibility() {
		return this.wanted;
	}

	@Override
	public void update(ToastManager manager, long visibleMs) {
		this.wanted = visibleMs >= SHOWN_FOR * manager.getNotificationDisplayTimeMultiplier() ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long visibleMs) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, this.width(), this.height());
		g.item(this.clazz.icon(), 8, 8);
		g.text(font, this.title, 30, 7, 0xFF000000 | this.clazz.color, false);
		g.text(font, this.text, 30, 18, 0xFFFFFFFF, false);
	}
}
