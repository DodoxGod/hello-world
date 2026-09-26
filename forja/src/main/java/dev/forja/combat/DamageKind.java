package dev.forja.combat;

/** The physical kind of a blow: it decides which part of a piece of armor takes it best. */
public enum DamageKind {
	/** Blades, axes, claws: the plate turns them. */
	SLASH,
	/** Fists, maces, hammers, falls of rock, blasts: the lining soaks them. */
	BLUNT,
	/** Arrows, spears, picks, stings: only hard plate stops them. */
	PIERCE,
	/** Fire, magic and the rest: armor does its plain job. */
	OTHER
}
