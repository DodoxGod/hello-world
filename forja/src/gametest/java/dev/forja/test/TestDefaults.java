package dev.forja.test;

import dev.forja.combat.CombatConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * What every test starts from, set once when the test server is up. The settings are the whole server's, and the
 * tests of a batch run side by side five blocks apart, so these used to be whatever the last test to touch them left:
 * <ul>
 *   <li>no squad hands its monsters to another player (iaRepartirObjetivos): every test's player is somebody else's
 *   "player about", and a squad with three more monsters than the one next door gave one of them to that test's
 *   player. A test that wants the sharing runs the squads itself with it on (TwoPlayerGameTests.squadUpdate);</li>
 *   <li>no veterans or elites by chance: most tests turn them off as they start, and one that gave the chances back
 *   when it ended (the boss's) put them back on for every test after it that had not.</li>
 * </ul>
 * The clock and the weather are the test environment's (data/forja-test/test_environment/estable.json).
 */
public final class TestDefaults implements ModInitializer {
	@Override
	public void onInitialize() {
		// The server tests only: the client test's pictures set up their own scenes.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (server instanceof net.minecraft.gametest.framework.GameTestServer) {
				apply();
			}
		});
	}

	static void apply() {
		CombatConfig cfg = CombatConfig.get();
		cfg.iaRepartirObjetivos = false;
		cfg.veteranChance = 0.0;
		cfg.eliteChance = 0.0;
	}
}
