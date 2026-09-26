package com.dodoxgod.filo.combat;

/** Tipo físico de un ataque. Decide qué material de armadura lo aguanta mejor. */
public enum DamageKind {
	/** Espadas, hachas, garras. */
	SLASH,
	/** Puños, mazas, explosiones, mobs que golpean. */
	BLUNT,
	/** Flechas, tridentes, picos, aguijones. */
	PIERCE,
	/** Cualquier otra cosa (fuego, magia...). Sin bonus ni penalización. */
	OTHER
}
