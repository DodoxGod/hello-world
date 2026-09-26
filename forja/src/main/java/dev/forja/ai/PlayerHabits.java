package dev.forja.ai;

import java.util.List;

import com.mojang.serialization.Codec;
import dev.forja.Forja;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * What the monsters have learned about a player, kept on the player (so every mob that fights them
 * shares it, and it survives a restart): how often they parry, dodge or just block a blow, which way
 * they dodge, how close they fight, how often they charge, and from all that a guess at how good they
 * are. Each is a moving average, so habits a player changes wear off.
 *
 * <pre>
 * a cada golpe que le llega (con atacante): r ← r + 0,05 · (x − r) para parada, esquiva y bloqueo (x = 1 si hizo eso, 0 si no)
 * a cada esquiva con un mob delante: lado ← lado + 0,1 · (s − lado), s = +1 a la derecha del mob, −1 a su izquierda
 * a cada golpe suyo cuerpo a cuerpo: distancia ← distancia + 0,05 · (d − distancia); carga ← carga + 0,05 · (cargado − carga)
 * nivel = clamp(1,2·parada + 1,0·esquiva + 0,4·bloqueo, 0, 1)
 * iniciales: parada 0,1; esquiva 0,1; bloqueo 0,2; lado 0; distancia 3; carga 0
 * </pre>
 */
public final class PlayerHabits {
	public static final int PARRY = 0;
	public static final int DODGE = 1;
	public static final int BLOCK = 2;
	public static final int SIDE = 3;
	public static final int DISTANCE = 4;
	public static final int CHARGE = 5;
	private static final int SIZE = 6;

	private static final float RATE_STEP = 0.05F;
	private static final float SIDE_STEP = 0.1F;
	private static final List<Float> INITIAL = List.of(0.1F, 0.1F, 0.2F, 0.0F, 3.0F, 0.0F);

	@SuppressWarnings("deprecation")
	public static final AttachmentType<List<Float>> VALUES = AttachmentRegistry.<List<Float>>builder()
		.initializer(() -> INITIAL)
		.persistent(Codec.FLOAT.listOf())
		.copyOnDeath()
		.buildAndRegister(Forja.id("habitos"));

	/** How a blow at the player ended. */
	public enum Outcome { PARRIED, DODGED, BLOCKED, TAKEN }

	private PlayerHabits() {
	}

	public static float get(Player player, int index) {
		if (player == null) {
			return INITIAL.get(index);
		}
		List<Float> values = player.getAttachedOrElse(VALUES, INITIAL);
		return index < values.size() ? values.get(index) : INITIAL.get(index);
	}

	private static void update(Player player, int index, float sample, float step) {
		List<Float> values = new java.util.ArrayList<>(player.getAttachedOrElse(VALUES, INITIAL));
		while (values.size() < SIZE) {
			values.add(INITIAL.get(values.size()));
		}
		float old = values.get(index);
		values.set(index, old + step * (sample - old));
		player.setAttached(VALUES, List.copyOf(values));
	}

	/** A blow reached (or was stopped by) the player. */
	public static void onBlow(Player player, Outcome outcome) {
		update(player, PARRY, outcome == Outcome.PARRIED ? 1.0F : 0.0F, RATE_STEP);
		update(player, DODGE, outcome == Outcome.DODGED ? 1.0F : 0.0F, RATE_STEP);
		update(player, BLOCK, outcome == Outcome.BLOCKED ? 1.0F : 0.0F, RATE_STEP);
	}

	/** The player dodged; {@code side} is +1 to the right of the mob facing them, −1 to its left. */
	public static void onDodge(Player player, float side) {
		update(player, SIDE, Mth.clamp(side, -1.0F, 1.0F), SIDE_STEP);
	}

	/** The player swung at something this far away, charged or not. */
	public static void onAttack(Player player, float distance, boolean charged) {
		update(player, DISTANCE, distance, RATE_STEP);
		update(player, CHARGE, charged ? 1.0F : 0.0F, RATE_STEP);
	}

	/** A guess at how good the player is, 0 (novice) to 1 (expert). */
	public static float skill(Player player) {
		return Mth.clamp(1.2F * get(player, PARRY) + get(player, DODGE) + 0.4F * get(player, BLOCK), 0.0F, 1.0F);
	}
}
