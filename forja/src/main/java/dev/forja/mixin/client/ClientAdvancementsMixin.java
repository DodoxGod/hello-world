package dev.forja.mixin.client;

import java.util.Map;

import dev.forja.ForjaPath;
import dev.forja.client.PathClient;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the guide read what the client knows of the player's advancements, and notices when the guide's
 * path moves on: the next step is worked out before each advancements packet and again after it.
 */
@Mixin(ClientAdvancements.class)
abstract class ClientAdvancementsMixin implements PathClient.Progress {
	@Shadow
	@Final
	private Map<AdvancementHolder, AdvancementProgress> progress;

	@Unique
	private ForjaPath.@Nullable Step forja$before;

	@Override
	public Map<AdvancementHolder, AdvancementProgress> forja$progress() {
		return this.progress;
	}

	@Inject(method = "update", at = @At("HEAD"))
	private void forja$noteBefore(ClientboundUpdateAdvancementsPacket packet, CallbackInfo info) {
		this.forja$before = ForjaPath.next(PathClient.done(this.progress)::contains);
	}

	@Inject(method = "update", at = @At("TAIL"))
	private void forja$noteAfter(ClientboundUpdateAdvancementsPacket packet, CallbackInfo info) {
		PathClient.updated(this.forja$before, ForjaPath.next(PathClient.done(this.progress)::contains), packet.shouldReset());
	}
}
