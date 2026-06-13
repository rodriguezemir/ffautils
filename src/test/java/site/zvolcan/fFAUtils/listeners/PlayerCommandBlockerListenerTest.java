package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.BlockedCommandsManager;
import site.zvolcan.fFAUtils.managers.CombatLogManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PlayerCommandBlockerListener}.
 * Covers the cancellation rules: combat state, bypass permission, blocked label,
 * case-insensitive matching, and null/empty message safety.
 */
class PlayerCommandBlockerListenerTest {

    @Mock
    private CombatLogManager combatLogManager;
    @Mock
    private BlockedCommandsManager blockedCommandsManager;
    @Mock
    private Player player;
    @Mock
    private PlayerCommandPreprocessEvent event;

    private me.putindeer.api.util.PluginUtils utils;
    private PlayerCommandBlockerListener listener;
    private FFAUtils mockPlugin;
    private UUID playerUuid;
    private AutoCloseable mockCloseable;
    private MockedStatic<FFAUtils> ffaStatic;
    private MockedStatic<MessagesManager> messagesStatic;
    private MessagesManager messagesMock;

    @BeforeEach
    void setUp() {
        mockCloseable = MockitoAnnotations.openMocks(this);
        playerUuid = UUID.randomUUID();

        when(player.getUniqueId()).thenReturn(playerUuid);

        // PluginUtils — not @Mock mockable in this environment
        try {
            Class<me.putindeer.api.util.PluginUtils> puClass =
                    (Class<me.putindeer.api.util.PluginUtils>)
                            Class.forName("me.putindeer.api.util.PluginUtils");
            utils = mock(puClass, withSettings().lenient().withoutAnnotations());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot load PluginUtils", e);
        }

        // Static mocks
        ffaStatic = mockStatic(FFAUtils.class, withSettings().lenient());
        mockPlugin = mock(FFAUtils.class, withSettings().lenient());
        ffaStatic.when(FFAUtils::getInstance).thenReturn(mockPlugin);
        when(mockPlugin.getUtils()).thenReturn(utils);
        when(mockPlugin.getCombatLogManager()).thenReturn(combatLogManager);
        when(mockPlugin.getBlockedCommandsManager()).thenReturn(blockedCommandsManager);

        messagesStatic = mockStatic(MessagesManager.class, withSettings().lenient());
        messagesMock = mock(MessagesManager.class, withSettings().lenient());
        messagesStatic.when(MessagesManager::getInstance).thenReturn(messagesMock);
        when(messagesMock.getMessage(eq("combat-command-blocked")))
                .thenReturn("<red>You cannot use this command while in combat.");

        // Default combat + permission state — overridden per test
        when(combatLogManager.isInCombat(any(UUID.class))).thenReturn(false);
        when(player.hasPermission(anyString())).thenReturn(false);
        when(blockedCommandsManager.isBlocked(anyString())).thenReturn(false);

        listener = new PlayerCommandBlockerListener(mockPlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (ffaStatic != null) ffaStatic.close();
        if (messagesStatic != null) messagesStatic.close();
        if (mockCloseable != null) mockCloseable.close();
    }

    // ─── Combat state gate ──────────────────────────────────────────────

    @Test
    void onPlayerCommand_whenNotInCombat_shouldNotCancel() {
        when(combatLogManager.isInCombat(playerUuid)).thenReturn(false);
        when(blockedCommandsManager.isBlocked("kit")).thenReturn(true);
        when(event.getMessage()).thenReturn("/kit");
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerCommand(event);

        verify(event, never()).setCancelled(true);
        verify(utils, never()).message(any(Player.class), anyBoolean(), anyString());
    }

    // ─── Blocked label gate ─────────────────────────────────────────────

    @Test
    void onPlayerCommand_whenInCombatAndBlocked_shouldCancelAndSendMessage() {
        when(combatLogManager.isInCombat(playerUuid)).thenReturn(true);
        when(blockedCommandsManager.isBlocked("kit")).thenReturn(true);
        when(event.getMessage()).thenReturn("/kit");
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerCommand(event);

        verify(event).setCancelled(true);
        verify(utils).message(eq(player), eq(false),
                eq("<red>You cannot use this command while in combat."));
    }

    @Test
    void onPlayerCommand_whenInCombatButNotBlocked_shouldNotCancel() {
        when(combatLogManager.isInCombat(playerUuid)).thenReturn(true);
        when(blockedCommandsManager.isBlocked("msg")).thenReturn(false);
        when(event.getMessage()).thenReturn("/msg friend hello");
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerCommand(event);

        verify(event, never()).setCancelled(true);
        verify(utils, never()).message(any(Player.class), anyBoolean(), anyString());
    }

    // ─── Bypass permission gate ─────────────────────────────────────────

    @Test
    void onPlayerCommand_whenPlayerHasBypassPermission_shouldNotCancel() {
        when(combatLogManager.isInCombat(playerUuid)).thenReturn(true);
        when(player.hasPermission("ffautils.bypass-combat-block")).thenReturn(true);
        when(blockedCommandsManager.isBlocked("kit")).thenReturn(true);
        when(event.getMessage()).thenReturn("/kit");
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerCommand(event);

        verify(event, never()).setCancelled(true);
        verify(utils, never()).message(any(Player.class), anyBoolean(), anyString());
    }

    @Test
    void onPlayerCommand_whenPlayerLacksBypassPermission_shouldCancel() {
        when(combatLogManager.isInCombat(playerUuid)).thenReturn(true);
        when(player.hasPermission("ffautils.bypass-combat-block")).thenReturn(false);
        when(blockedCommandsManager.isBlocked("kit")).thenReturn(true);
        when(event.getMessage()).thenReturn("/kit");
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerCommand(event);

        verify(event).setCancelled(true);
        verify(utils).message(eq(player), eq(false), anyString());
    }

    // ─── Case insensitivity ─────────────────────────────────────────────

    @Test
    void onPlayerCommand_whenMixedCaseLabel_shouldStillBlock() {
        when(combatLogManager.isInCombat(playerUuid)).thenReturn(true);
        when(event.getMessage()).thenReturn("/KIT");
        when(event.getPlayer()).thenReturn(player);

        // Listener should normalize the label to lowercase before checking.
        // We can't pre-stub isBlocked("KIT") — we verify the actual call
        // argument was lowercase.
        when(blockedCommandsManager.isBlocked(eq("kit"))).thenReturn(true);

        listener.onPlayerCommand(event);

        verify(event).setCancelled(true);
        verify(blockedCommandsManager).isBlocked("kit");
    }

    // ─── Null / empty message safety ────────────────────────────────────

    @Test
    void onPlayerCommand_whenMessageIsNull_shouldNotThrow() {
        when(event.getMessage()).thenReturn(null);
        when(event.getPlayer()).thenReturn(player);

        assertDoesNotThrow(() -> listener.onPlayerCommand(event));
        verify(event, never()).setCancelled(true);
    }

    @Test
    void onPlayerCommand_whenMessageIsEmpty_shouldNotThrow() {
        when(event.getMessage()).thenReturn("");
        when(event.getPlayer()).thenReturn(player);

        assertDoesNotThrow(() -> listener.onPlayerCommand(event));
        verify(event, never()).setCancelled(true);
    }

    // ─── Argument verification ──────────────────────────────────────────

    @Test
    void onPlayerCommand_whenBlocking_shouldQueryCombatAndBlockedWithLowercaseLabel() {
        when(combatLogManager.isInCombat(playerUuid)).thenReturn(true);
        when(blockedCommandsManager.isBlocked("spawn")).thenReturn(true);
        when(event.getMessage()).thenReturn("/Spawn arena1");
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerCommand(event);

        verify(combatLogManager).isInCombat(playerUuid);
        verify(blockedCommandsManager).isBlocked("spawn");
        verify(event).setCancelled(true);
    }
}
