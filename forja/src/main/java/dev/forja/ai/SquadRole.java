package dev.forja.ai;

/** A mob's part in its squad, handed out by {@link Squad} by rules (never by a network). */
public enum SquadRole {
	/** Goes in and swings. */
	ATACANTE,
	/** Goes round behind the target while the others keep its attention. */
	FLANCO,
	/** Stays in front of the target, drawing its eye (and its blows) for the flanker. */
	DISTRACTOR,
	/** Shoots from behind the others. */
	COBERTURA,
	/** Waits on the ring for a turn. */
	RESERVA
}
