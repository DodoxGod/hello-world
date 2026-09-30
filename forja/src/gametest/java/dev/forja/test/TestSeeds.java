package dev.forja.test;

import java.util.List;

import dev.forja.ai.MobAi;
import dev.forja.ai.MobMind;
import dev.forja.ai.Personality;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * The chance taken out of a mob, for a test that has to see the same fight every run: its own random numbers, its
 * brain's (which a network samples its decisions with) and its trait, all from one seed. Without it every run of a
 * test watched a different fight: a mob's random numbers start from the clock, its brain's from its UUID and the time.
 */
final class TestSeeds {
	private TestSeeds() {
	}

	static void seed(Mob mob, long seed) {
		mob.getRandom().setSeed(seed);
		MobMind mind = MobAi.mind(mob);
		if (mind != null) {
			mind.random.setSeed(seed * 0x9E3779B97F4A7C15L + 1L);
		}
		// The trait was rolled at spawn with the random numbers it had then: rolled again with these.
		for (String tag : List.copyOf(mob.entityTags())) {
			if (tag.startsWith(Personality.TRAIT_TAG)) {
				mob.removeTag(tag);
			}
		}
		if (mob.level() instanceof ServerLevel level) {
			Personality.roll(mob, level);
		}
	}
}
