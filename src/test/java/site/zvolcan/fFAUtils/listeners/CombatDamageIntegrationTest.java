package site.zvolcan.fFAUtils.listeners;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.*;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying the full damage → combat logging flow.
 * <p>
 * When a player damages another player,
 * {@link PlayerConnectListener#onEntityDamageByEntity} must mark
 * <strong>both</strong> the damager and the damagee as in combat
 * via {@link CombatLogManager#setInCombat}.
 * <p>
 * The handler is invoked directly rather than via Bukkit event dispatch
 * because MockBukkit's {@code registerEvents} requires a real loaded
 * plugin, not a Mockito mock.
 */
class CombatDamageIntegrationTest {

    private ServerMock server;
    private CombatLogManager combatLogManager;
    private PlayerConnectListener listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        server = MockBukkit.mock();

        // ── Mock FFAUtils plugin ──────────────────────────────────────────
        FFAUtils mockPlugin = mock(FFAUtils.class, withSettings().lenient());

        // Real CombatLogManager — 200 ticks ≈ 10 seconds, plenty for the test
        combatLogManager = new CombatLogManager(mockPlugin, 200L);
        when(mockPlugin.getCombatLogManager()).thenReturn(combatLogManager);

        // Stub utility methods that MockBukkit internals or the listener
        // might call during event registration / propagation.
        when(mockPlugin.getName()).thenReturn("MockFFAUtils");
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("MockFFAUtils"));

        // PluginUtils — lenient, not exercised by our test path
        me.putindeer.api.util.PluginUtils utils;
        try {
            Class<me.putindeer.api.util.PluginUtils> puClass =
                    (Class<me.putindeer.api.util.PluginUtils>)
                            Class.forName("me.putindeer.api.util.PluginUtils");
            utils = mock(puClass, withSettings().lenient().withoutAnnotations());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot load PluginUtils", e);
        }
        when(mockPlugin.getUtils()).thenReturn(utils);

        // ── Create the listener (registered manually — MockBukkit's event
        // dispatch requires a real loaded plugin, not a Mockito mock).
        listener = new PlayerConnectListener(
                mockPlugin,
                mock(LobbyManager.class, withSettings().lenient()),
                mock(PlayersManager.class, withSettings().lenient()),
                mock(SpawnManager.class, withSettings().lenient()),
                mock(StatsManager.class, withSettings().lenient())
        );
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @SuppressWarnings("deprecation")
    void onEntityDamageByEntity_shouldMarkBothPlayersInCombat() {
        // Given
        Player player1 = server.addPlayer();
        Player player2 = server.addPlayer();

        // When — dispatch damage directly to the listener
        // The 5-param constructor is marked for removal in Paper API 1.21.11+
        // but still functional. Suppress the warning since this is the only way to
        // construct the event without DamageSource in integration tests.
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
                player1, player2,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).build(),
                5.0
        );
        listener.onEntityDamageByEntity(event);

        // Then — both participants must be in combat immediately
        assertTrue(combatLogManager.isInCombat(player1.getUniqueId()),
                "Damager should be marked in combat");
        assertTrue(combatLogManager.isInCombat(player2.getUniqueId()),
                "Damagee should be marked in combat");
    }
}
