package dev.forja.test;

/** Written by tools/castillo.py: where the castle's start piece stands in the plan's own coordinates. */
final class BastionLayout {
	static final int START_X = 76;
	static final int START_Y = -3;
	static final int START_Z = 86;
	/** The real castle's pieces: forja:bastion/p_i_j_k holds the plan from ORIGIN + 48 * (i, j, k). */
	static final int ORIGIN_X = -20;
	static final int ORIGIN_Y = -26;
	static final int ORIGIN_Z = -58;

	private BastionLayout() {
	}
}
