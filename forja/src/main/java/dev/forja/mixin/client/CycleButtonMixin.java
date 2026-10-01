package dev.forja.mixin.client;

import dev.forja.client.LadderClient;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A difficulty button attached to the ladder (LadderClient): its presses go one step along Pacífico → Fácil → Normal →
 * Difícil → Extremo, and HARD with the world's flag on reads "Extremo". Every other CycleButton is left as it is: the
 * hook is null on them.
 */
@Mixin(CycleButton.class)
abstract class CycleButtonMixin implements LadderClient.Attached {
	@Shadow
	@Final
	private Component name;

	@Shadow
	@Final
	private CycleButton.DisplayState displayState;

	@Unique
	private LadderClient.Hook forja$ladder;

	@Override
	public LadderClient.Hook forja$ladder() {
		return this.forja$ladder;
	}

	@Override
	public void forja$ladder(LadderClient.Hook hook) {
		this.forja$ladder = hook;
	}

	@Inject(method = "cycleValue", at = @At("HEAD"), cancellable = true)
	private void forja$ladderStep(int delta, CallbackInfo ci) {
		if (this.forja$ladder != null) {
			LadderClient.cycle((CycleButton<?>) (Object) this, this.forja$ladder, delta);
			ci.cancel();
		}
	}

	@Inject(method = "createLabelForValue", at = @At("RETURN"), cancellable = true)
	private void forja$extremoLabel(Object value, CallbackInfoReturnable<Component> cir) {
		if (this.forja$ladder != null && value == Difficulty.HARD && this.forja$ladder.extremo()) {
			cir.setReturnValue(this.displayState == CycleButton.DisplayState.VALUE ? LadderClient.EXTREMO
				: CommonComponents.optionNameValue(this.name, LadderClient.EXTREMO));
		}
	}
}
