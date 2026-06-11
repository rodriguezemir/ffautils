package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.MockUtil;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.CombatLogManager;
import site.zvolcan.fFAUtils.managers.DeathEventManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.managers.StatsManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.PlayerState;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for PlayerDeathListener killstreak logic.
 */
class PlayerDeathListenerTest {

    @Mock
    private DeathEventManager deathEventManager;
    @Mock
    private SpawnManager spawnManager;
    @Mock
    private CombatLogManager combatLogManager;
    @Mock
    private StatsManager statsManager;
    @Mock
    private PlayersManager playersManager;
    @Mock
    private Player victim;
    @Mock
    private Player killer;
    @Mock
    private PlayerDeathEvent event;

    private me.putindeer.api.util.PluginUtils utils;
    private PlayerDeathListener listener;
    private UUID victimUuid;
    private UUID killerUuid;
    private AutoCloseable mockCloseable;

    private static MockedStatic<MessagesManager> messagesMockScope;
    private static MessagesManager messagesMock;

    @BeforeAll
    static void setupMessagesMock() {
        messagesMockScope = mockStatic(MessagesManager.class, withSettings().lenient());
        messagesMock = mock(MessagesManager.class, withSettings().lenient());
        messagesMockScope.when(MessagesManager::getInstance).thenReturn(messagesMock);
        when(messagesMock.getMessage(eq("killstreak-lost"),
                eq("{player}"), anyString(),
                eq("{kills}"), anyString()))
                .thenAnswer(inv -> {
                    String player = inv.getArgument(2);
                    String kills = inv.getArgument(4);
                    return "{player} perdio una racha de {kills} kills."
                            .replace("{player}", player)
                            .replace("{kills}", kills);
                });
        when(messagesMock.getMessage(eq("killstreak-gained"),
                eq("{player}"), anyString(),
                eq("{kills}"), anyString()))
                .thenAnswer(inv -> {
                    String player = inv.getArgument(2);
                    String kills = inv.getArgument(4);
                    return "{player} consiguio una racha de {kills} kills."
                            .replace("{player}", player)
                            .replace("{kills}", kills);
                });
    }

    @AfterAll
    static void teardownMessagesMock() {
        if (messagesMockScope != null) {
            messagesMockScope.close();
            messagesMockScope = null;
            messagesMock = null;
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this);
        victimUuid = UUID.randomUUID();
        killerUuid = UUID.randomUUID();

        when(victim.getUniqueId()).thenReturn(victimUuid);
        when(killer.getUniqueId()).thenReturn(killerUuid);

        // PluginUtils is not mockable via @Mock in this environment,
        // so we create a mock manually with lenient settings
        try {
            Class<me.putindeer.api.util.PluginUtils> puClass =
                    (Class<me.putindeer.api.util.PluginUtils>)
                            Class.forName("me.putindeer.api.util.PluginUtils");
            utils = mock(puClass, withSettings().lenient().withoutAnnotations());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot load PluginUtils", e);
        }

        listener = new PlayerDeathListener(deathEventManager, spawnManager, combatLogManager, statsManager, playersManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockCloseable != null) {
            mockCloseable.close();
        }
    }

    // ─── Pure function: isMilestone ───────────────────────────────────────

    @Test
    void isMilestone_shouldReturnFalseForKillstreakZero() {
        assertFalse(PlayerDeathListener.isMilestone(0));
    }

    @Test
    void isMilestone_shouldReturnFalseForNonMilestoneValues() {
        assertFalse(PlayerDeathListener.isMilestone(1));
        assertFalse(PlayerDeathListener.isMilestone(2));
        assertFalse(PlayerDeathListener.isMilestone(3));
        assertFalse(PlayerDeathListener.isMilestone(4));
        assertFalse(PlayerDeathListener.isMilestone(6));
    }

    @Test
    void isMilestone_shouldReturnTrueAtEveryFifthKill() {
        assertTrue(PlayerDeathListener.isMilestone(5));
        assertTrue(PlayerDeathListener.isMilestone(10));
        assertTrue(PlayerDeathListener.isMilestone(15));
        assertTrue(PlayerDeathListener.isMilestone(20));
    }

    // ─── Victim path: loss broadcast ─────────────────────────────────────

    @Test
    void onDeath_whenVictimInArenaWithStreak_shouldBroadcastLossAndReset() {
        when(event.getPlayer()).thenReturn(victim);
        when(victim.getKiller()).thenReturn(null);
        when(victim.getName()).thenReturn("Steve");

        FFAPlayer victimFfa = new FFAPlayer(victimUuid);
        victimFfa.setState(PlayerState.IN_FFA);
        victimFfa.setKillstreak(7);
        when(playersManager.getFFAPlayer(victim)).thenReturn(victimFfa);

        try (var mockedFfa = mockStatic(FFAUtils.class)) {
            FFAUtils mockPlugin = mock(FFAUtils.class);
            mockedFfa.when(FFAUtils::getInstance).thenReturn(mockPlugin);
            when(mockPlugin.getUtils()).thenReturn(utils);

            listener.onDeath(event);

            String expectedMsg = "{player} perdio una racha de {kills} kills."
                    .replace("{player}", "Steve")
                    .replace("{kills}", "7");
            verify(utils).broadcast(false, expectedMsg);
            assertEquals(0, victimFfa.getKillstreak(),
                    "Victim killstreak must be reset to 0 after death");
        }
    }

    @Test
    void onDeath_whenVictimInArenaWithZeroStreak_shouldNotBroadcastLoss() {
        when(event.getPlayer()).thenReturn(victim);
        when(victim.getKiller()).thenReturn(null);

        FFAPlayer victimFfa = new FFAPlayer(victimUuid);
        victimFfa.setState(PlayerState.IN_FFA);
        victimFfa.setKillstreak(0);
        when(playersManager.getFFAPlayer(victim)).thenReturn(victimFfa);

        try (var mockedFfa = mockStatic(FFAUtils.class)) {
            FFAUtils mockPlugin = mock(FFAUtils.class);
            mockedFfa.when(FFAUtils::getInstance).thenReturn(mockPlugin);
            when(mockPlugin.getUtils()).thenReturn(utils);

            listener.onDeath(event);

            verify(utils, never()).broadcast(anyBoolean(), contains("perdio"));
        }
    }

    @Test
    void onDeath_whenVictimOutsideArenaWithStreak_shouldNotChangeKillstreak() {
        when(event.getPlayer()).thenReturn(victim);
        when(victim.getKiller()).thenReturn(null);

        FFAPlayer victimFfa = new FFAPlayer(victimUuid);
        victimFfa.setState(PlayerState.LOBBY);
        victimFfa.setKillstreak(5);
        when(playersManager.getFFAPlayer(victim)).thenReturn(victimFfa);

        try (var mockedFfa = mockStatic(FFAUtils.class)) {
            FFAUtils mockPlugin = mock(FFAUtils.class);
            mockedFfa.when(FFAUtils::getInstance).thenReturn(mockPlugin);
            when(mockPlugin.getUtils()).thenReturn(utils);

            listener.onDeath(event);

            verify(utils, never()).broadcast(anyBoolean(), anyString());
            assertEquals(5, victimFfa.getKillstreak(),
                    "Killstreak must NOT change when outside IN_FFA");
        }
    }

    // ─── Killer path: increment and milestone ───────────────────────────

    @Test
    void onDeath_whenKillerInArena_shouldIncrementKillstreak() {
        when(event.getPlayer()).thenReturn(victim);
        when(victim.getKiller()).thenReturn(killer);
        setupVictim(PlayerState.IN_FFA, 0);
        FFAPlayer killerFfa = setupKiller(PlayerState.IN_FFA, 3);

        try (var mockedFfa = mockStatic(FFAUtils.class)) {
            FFAUtils mockPlugin = mock(FFAUtils.class);
            mockedFfa.when(FFAUtils::getInstance).thenReturn(mockPlugin);
            when(mockPlugin.getUtils()).thenReturn(utils);

            listener.onDeath(event);

            assertEquals(4, killerFfa.getKillstreak(),
                    "Killer killstreak must increment by 1");
        }
    }

    @Test
    void onDeath_whenKillerReachesMilestone_shouldBroadcastMilestone() {
        when(event.getPlayer()).thenReturn(victim);
        when(victim.getKiller()).thenReturn(killer);
        when(victim.getName()).thenReturn("Victim");
        when(killer.getName()).thenReturn("Alex");
        setupVictim(PlayerState.IN_FFA, 0);
        setupKiller(PlayerState.IN_FFA, 4);

        try (var mockedFfa = mockStatic(FFAUtils.class)) {
            FFAUtils mockPlugin = mock(FFAUtils.class);
            mockedFfa.when(FFAUtils::getInstance).thenReturn(mockPlugin);
            when(mockPlugin.getUtils()).thenReturn(utils);

            listener.onDeath(event);

            String expectedMsg = "{player} consiguio una racha de {kills} kills."
                    .replace("{player}", "Alex")
                    .replace("{kills}", "5");
            verify(utils).broadcast(false, expectedMsg);
        }
    }

    @Test
    void onDeath_whenKillerDoesNotReachMilestone_shouldNotBroadcastMilestone() {
        when(event.getPlayer()).thenReturn(victim);
        when(victim.getKiller()).thenReturn(killer);
        setupVictim(PlayerState.IN_FFA, 0);
        setupKiller(PlayerState.IN_FFA, 3);

        try (var mockedFfa = mockStatic(FFAUtils.class)) {
            FFAUtils mockPlugin = mock(FFAUtils.class);
            mockedFfa.when(FFAUtils::getInstance).thenReturn(mockPlugin);
            when(mockPlugin.getUtils()).thenReturn(utils);

            listener.onDeath(event);

            verify(utils, never()).broadcast(anyBoolean(), contains("consiguio"));
        }
    }

    @Test
    void onDeath_whenKillerOutsideArena_shouldNotModifyKillstreak() {
        when(event.getPlayer()).thenReturn(victim);
        when(victim.getKiller()).thenReturn(killer);
        setupVictim(PlayerState.IN_FFA, 0);
        FFAPlayer killerFfa = setupKiller(PlayerState.LOBBY, 10);

        try (var mockedFfa = mockStatic(FFAUtils.class)) {
            FFAUtils mockPlugin = mock(FFAUtils.class);
            mockedFfa.when(FFAUtils::getInstance).thenReturn(mockPlugin);
            when(mockPlugin.getUtils()).thenReturn(utils);

            listener.onDeath(event);

            assertEquals(10, killerFfa.getKillstreak(),
                    "Killstreak must NOT change when killer is not IN_FFA");
            verify(utils, never()).broadcast(anyBoolean(), anyString());
        }
    }

    @Test
    void onDeath_whenNoKiller_shouldNotCrash() {
        when(event.getPlayer()).thenReturn(victim);
        when(victim.getKiller()).thenReturn(null);

        FFAPlayer victimFfa = new FFAPlayer(victimUuid);
        victimFfa.setState(PlayerState.IN_FFA);
        victimFfa.setKillstreak(0);
        when(playersManager.getFFAPlayer(victim)).thenReturn(victimFfa);

        try (var mockedFfa = mockStatic(FFAUtils.class)) {
            FFAUtils mockPlugin = mock(FFAUtils.class);
            mockedFfa.when(FFAUtils::getInstance).thenReturn(mockPlugin);
            when(mockPlugin.getUtils()).thenReturn(utils);

            assertDoesNotThrow(() -> listener.onDeath(event),
                    "onDeath must not throw when there is no killer");
        }
    }

    @Test
    void onDeath_shouldPreserveExistingBehaviour() {
        when(event.getPlayer()).thenReturn(victim);
        when(victim.getKiller()).thenReturn(killer);
        setupVictim(PlayerState.IN_FFA, 0);
        setupKiller(PlayerState.IN_FFA, 0);

        try (var mockedFfa = mockStatic(FFAUtils.class)) {
            FFAUtils mockPlugin = mock(FFAUtils.class);
            mockedFfa.when(FFAUtils::getInstance).thenReturn(mockPlugin);
            when(mockPlugin.getUtils()).thenReturn(utils);

            listener.onDeath(event);

            verify(deathEventManager).broadcastDeathEvent(victim, killer);
            verify(combatLogManager).removeFromCombat(victimUuid);
            verify(combatLogManager).removeFromCombat(killerUuid);
            verify(statsManager).addDeath(victimUuid);
            verify(statsManager).addKill(killerUuid);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void setupVictim(PlayerState state, int killstreak) {
        FFAPlayer victimFfa = new FFAPlayer(victimUuid);
        victimFfa.setState(state);
        victimFfa.setKillstreak(killstreak);
        when(playersManager.getFFAPlayer(victim)).thenReturn(victimFfa);
    }

    private FFAPlayer setupKiller(PlayerState state, int killstreak) {
        FFAPlayer killerFfa = new FFAPlayer(killerUuid);
        killerFfa.setState(state);
        killerFfa.setKillstreak(killstreak);
        when(playersManager.getFFAPlayer(killer)).thenReturn(killerFfa);
        return killerFfa;
    }
}
