package dev.forja.clase;

import dev.forja.Forja;
import dev.forja.registry.ModItems;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The class system over the wire: what the screens and keys ask for (choose, learn, use a skill), and what
 * the server tells the client that the synced attachment cannot (a toast, and the emblem opening the choice).
 * Everything asked is checked again here; the client only asks.
 */
public final class ClassNetwork {
	/** Client to server. */
	public record Action(byte kind, String arg) implements CustomPacketPayload {
		public static final byte CHOOSE = 0;
		public static final byte LEARN = 1;
		public static final byte SKILL = 2;
		public static final CustomPacketPayload.Type<Action> TYPE = new CustomPacketPayload.Type<>(Forja.id("clase_accion"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, Action::kind, ByteBufCodecs.STRING_UTF8, Action::arg, Action::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public enum Toast {
		/** A level reached. */
		LEVEL,
		/** A class taken or changed. */
		CHOSEN,
		/** The emblem was used: open the choice screen to change. */
		OPEN_CHANGE
	}

	/** Server to client. */
	public record Notice(byte kind, String clazz, int level) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Notice> TYPE = new CustomPacketPayload.Type<>(Forja.id("clase_aviso"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Notice> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, Notice::kind, ByteBufCodecs.STRING_UTF8, Notice::clazz, ByteBufCodecs.VAR_INT, Notice::level, Notice::new);

		public Toast toast() {
			return Toast.values()[Math.max(0, Math.min(Toast.values().length - 1, this.kind))];
		}

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private ClassNetwork() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Notice.TYPE, Notice.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	public static void toast(ServerPlayer player, Toast kind, PlayerClass clazz, int level) {
		if (ServerPlayNetworking.canSend(player, Notice.TYPE)) {
			ServerPlayNetworking.send(player, new Notice((byte) kind.ordinal(), clazz.id(), level));
		}
	}

	/** The emblem in hand opens the choice in its "change" form. */
	public static void openChange(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, Notice.TYPE)) {
			ServerPlayNetworking.send(player, new Notice((byte) Toast.OPEN_CHANGE.ordinal(), "", 0));
		}
	}

	static void handle(ServerPlayer player, Action action) {
		switch (action.kind()) {
			case Action.CHOOSE -> {
				PlayerClass chosen = PlayerClass.byId(action.arg());
				if (chosen != null) {
					tryChoose(player, chosen);
				}
			}
			case Action.LEARN -> {
				Talent talent = Talent.byId(action.arg());
				if (talent != null) {
					ClassProgress.unlock(player, talent);
				}
			}
			case Action.SKILL -> ClassSkills.use(player, "2".equals(action.arg()) ? 2 : 1, false);
			default -> {
			}
		}
	}

	/**
	 * The first class is free. After that the change costs an Emblema del olvido, taken from the hand or the
	 * bag (not in creative); without one, nothing happens and the player is told why.
	 */
	public static boolean tryChoose(ServerPlayer player, PlayerClass chosen) {
		if (ClassProgress.clazz(player) != null && !player.isCreative()) {
			ItemStack emblem = findEmblem(player);
			if (emblem.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("gui.forja.clase.falta_emblema"));
				return false;
			}
			emblem.shrink(1);
		}
		ClassProgress.choose(player, chosen);
		return true;
	}

	private static ItemStack findEmblem(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(ModItems.EMBLEMA_DEL_OLVIDO)) {
				return held;
			}
		}
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(ModItems.EMBLEMA_DEL_OLVIDO)) {
				return inventory.getItem(slot);
			}
		}
		return ItemStack.EMPTY;
	}
}
