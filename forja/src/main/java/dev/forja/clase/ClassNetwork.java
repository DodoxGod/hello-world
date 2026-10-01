package dev.forja.clase;

import dev.forja.Forja;
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
		/** "Probar" then "Aplicar": several nodes at once, comma-separated, learned in that order. */
		public static final byte LEARN_PLAN = 3;
		/** The Vela del olvido: these nodes off the tree, comma-separated. */
		public static final byte FORGET = 4;
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
		OPEN_CHANGE,
		/** The Vela del olvido was used: open the tree to pick what to forget. */
		OPEN_FORGET
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

	/** The candle in hand opens the tree in its "forget" form. */
	public static void openForget(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, Notice.TYPE)) {
			ServerPlayNetworking.send(player, new Notice((byte) Toast.OPEN_FORGET.ordinal(), "", 0));
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
			case Action.LEARN -> ClassProgress.unlock(player, action.arg());
			case Action.LEARN_PLAN -> ClassProgress.unlockAll(player, split(action.arg()));
			case Action.FORGET -> forget(player, split(action.arg()));
			case Action.SKILL -> ClassSkills.use(player, "3".equals(action.arg()) ? 3 : "2".equals(action.arg()) ? 2 : 1, false);
			default -> {
			}
		}
	}

	/**
	 * The first class is free. After that the change costs a Medallón del olvido (or one of the old crafted
	 * Emblemas del olvido), taken from the hand or the bag (not in creative); without one, nothing happens and
	 * the player is told why. On your own class it only empties the tree (ClassProgress.choose).
	 */
	public static boolean tryChoose(ServerPlayer player, PlayerClass chosen) {
		// The classes open with their book: until it has been read, no class is taken (GuideBooks.CLASSES_READ).
		if (!dev.forja.GuideBooks.classesOpen(player)) {
			player.sendOverlayMessage(Component.translatable("gui.forja.libros.clases.cerradas"));
			return false;
		}
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

	private static java.util.List<String> split(String text) {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (String part : text.split(",")) {
			if (!part.isBlank() && out.size() < 200) {
				out.add(part.trim());
			}
		}
		return out;
	}

	/**
	 * The Vela del olvido: these nodes come off if together they are worth no more than a candle takes and what
	 * stays still hangs from the origin. One candle is spent (not in creative); without one, nothing happens.
	 */
	public static boolean forget(ServerPlayer player, java.util.List<String> ids) {
		int most = dev.forja.item.OblivionCandleItem.POINTS;
		if (!ClassProgress.canForget(ClassProgress.data(player), ids, most)) {
			player.sendOverlayMessage(Component.translatable("gui.forja.vela.no_puede", most));
			return false;
		}
		ItemStack candle = find(player, dev.forja.item.OblivionCandleItem.class);
		if (!player.isCreative()) {
			if (candle.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("gui.forja.vela.falta"));
				return false;
			}
			candle.shrink(1);
		}
		ClassProgress.forget(player, ids);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.CANDLE_EXTINGUISH,
			net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendOverlayMessage(Component.translatable("gui.forja.vela.hecho", ids.size()).withColor(0xFFF0C070));
		return true;
	}

	private static ItemStack find(ServerPlayer player, Class<?> kind) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (kind.isInstance(held.getItem())) {
				return held;
			}
		}
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (kind.isInstance(inventory.getItem(slot).getItem())) {
				return inventory.getItem(slot);
			}
		}
		return ItemStack.EMPTY;
	}

	private static ItemStack findEmblem(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.getItem() instanceof dev.forja.item.OblivionEmblemItem) {
				return held;
			}
		}
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).getItem() instanceof dev.forja.item.OblivionEmblemItem) {
				return inventory.getItem(slot);
			}
		}
		return ItemStack.EMPTY;
	}
}
