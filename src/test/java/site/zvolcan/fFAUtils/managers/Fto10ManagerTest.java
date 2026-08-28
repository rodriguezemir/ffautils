package site.zvolcan.fFAUtils.managers;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.PlayerState;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Fto10ManagerTest {

    private ServerMock server;
    private FFAUtils plugin;
    private PlayersManager playersManager;
    private KitManager kitManager;
    private LobbyManager lobbyManager;
    private SpawnManager spawnManager;
    private Fto10Manager manager;
    private Player first;
    private Player second;
    private Player outsider;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(FFAUtils.class, withSettings().lenient());
        playersManager = mock(PlayersManager.class);
        kitManager = mock(KitManager.class);
        lobbyManager = mock(LobbyManager.class);
        spawnManager = mock(SpawnManager.class);
        manager = new Fto10Manager(plugin, playersManager, kitManager, lobbyManager);

        first = server.addPlayer();
        second = server.addPlayer();
        outsider = server.addPlayer();

        World world = server.getWorlds().get(0);
        Location duelSpawn = new Location(world, 10, 70, 10);
        Location lobbySpawn = new Location(world, 0, 70, 0);
        when(plugin.getSpawnManager()).thenReturn(spawnManager);
        when(spawnManager.getLobbySpawn()).thenReturn(lobbySpawn);

        FFAPlayer firstData = playerData(first, duelSpawn);
        FFAPlayer secondData = playerData(second, duelSpawn);
        when(playersManager.getFFAPlayer(first)).thenReturn(firstData);
        when(playersManager.getFFAPlayer(second)).thenReturn(secondData);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void accept_shouldStartMatchWithChallengersSpawnAndKit() {
        assertEquals(Fto10Manager.Result.SUCCESS, manager.invite(first, second));
        assertEquals(Fto10Manager.Result.SUCCESS, manager.accept(second, first));

        Fto10Manager.Match match = manager.getMatch(first.getUniqueId());
        assertNotNull(match);
        assertSame(match, manager.getMatch(second.getUniqueId()));
        assertEquals(0, match.score(first.getUniqueId()));
        assertEquals(0, match.score(second.getUniqueId()));
        assertEquals(PlayerState.IN_FFA, managerData(first).getState());
        assertEquals(PlayerState.IN_FFA, managerData(second).getState());
        verify(kitManager, times(2)).applyKit(any(Player.class), any(Kit.class));
    }

    @Test
    void damage_shouldOnlyAllowTheOtherFto10Player() {
        startMatch();

        EntityDamageByEntityEvent rivalDamage = damage(second, first);
        EntityDamageByEntityEvent outsiderDamage = damage(second, outsider);

        assertFalse(manager.shouldCancelDamage(second, rivalDamage));
        assertTrue(manager.shouldCancelDamage(second, outsiderDamage));
    }

    @Test
    void death_shouldAwardOneRoundAndWaitForRespawn() {
        startMatch();
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getPlayer()).thenReturn(first);
        when(death.getDrops()).thenReturn(new ArrayList<>());

        assertTrue(manager.handleDeath(death));

        Fto10Manager.Match match = manager.getMatch(first.getUniqueId());
        assertEquals(1, match.score(second.getUniqueId()));
        assertEquals(0, match.score(first.getUniqueId()));
        assertTrue(match.isAwaitingRespawn());
        verify(death).setKeepInventory(true);
    }

    @Test
    void leave_shouldAwardTenPointsToTheRemainingPlayerAndEndMatch() {
        startMatch();
        Fto10Manager.Match match = manager.getMatch(first.getUniqueId());

        assertEquals(Fto10Manager.Result.SUCCESS, manager.leave(first, second));

        assertEquals(10, match.score(second.getUniqueId()));
        assertFalse(manager.isInMatch(first.getUniqueId()));
        assertFalse(manager.isInMatch(second.getUniqueId()));
        assertEquals(PlayerState.LOBBY, managerData(first).getState());
        assertEquals(PlayerState.LOBBY, managerData(second).getState());
        verify(lobbyManager, times(2)).addLobbyItems(any(Player.class));
    }

    private void startMatch() {
        assertEquals(Fto10Manager.Result.SUCCESS, manager.invite(first, second));
        assertEquals(Fto10Manager.Result.SUCCESS, manager.accept(second, first));
    }

    private FFAPlayer playerData(Player player, Location spawn) {
        FFAPlayer data = new FFAPlayer(player.getUniqueId());
        data.setState(PlayerState.IN_FFA);
        data.setLastSpawn(spawn);
        data.setLastKit(new Kit("uhc", new org.bukkit.inventory.ItemStack[0]));
        return data;
    }

    private FFAPlayer managerData(Player player) {
        return playersManager.getFFAPlayer(player);
    }

    @SuppressWarnings("deprecation")
    private EntityDamageByEntityEvent damage(Player damaged, Player damager) {
        return new EntityDamageByEntityEvent(damager, damaged,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).build(), 1.0);
    }
}
