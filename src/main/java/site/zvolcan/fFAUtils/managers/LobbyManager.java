package site.zvolcan.fFAUtils.managers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.Sounds;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.PlayerState;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class LobbyManager implements Listener {

    private final FFAUtils plugin;
    private final Map<Material, String> commands = new HashMap<>();
    private final Map<Integer, ItemStack> items = new HashMap<>();
    private final NamespacedKey respawnKey;
    private Material respawnItemMaterial;
    private String respawnItemName;
    private int respawnItemSlot = 4;
    private final Set<UUID> pendingRespawn = new HashSet<>();

    public LobbyManager(@NotNull FFAUtils plugin) {
        this.plugin = plugin;
        this.respawnKey = new NamespacedKey(plugin, "respawn_item");
        loadItemsSection();
    }

    public void addLobbyItems(@NotNull Player player) {
        if (plugin.getConfig().getBoolean("disable-lobby-items", false))
            return;
        player.getInventory().clear();
        loadPlayerItems(player);
        if (pendingRespawn.remove(player.getUniqueId())) {
            addRespawnItem(player);
        }
    }

    public void loadPlayerItems(@NotNull Player player) {
        for (int i : items.keySet()) {
            final ItemStack item = items.get(i);

            if (item == null)
                continue;
            player.getInventory().setItem(i, item);
        }

        addRespawnItem(player);
    }

    public void markForRespawn(@NotNull UUID playerId) {
        pendingRespawn.add(playerId);
    }

    public void clearPendingRespawn(@NotNull UUID playerId) {
        pendingRespawn.remove(playerId);
    }

    private void addRespawnItem(@NotNull Player player) {
        if (respawnItemMaterial == null)
            return;
        FFAPlayer ffaPlayer = plugin.getPlayersManager().getFFAPlayer(player);
        if (ffaPlayer.getLastKit() == null || ffaPlayer.getLastSpawn() == null)
            return;

        ItemStack item = plugin.getUtils().ib(respawnItemMaterial)
                .name(respawnItemName).build();
        item.editMeta(meta -> meta.getPersistentDataContainer().set(respawnKey, PersistentDataType.BYTE, (byte) 1));
        player.getInventory().setItem(respawnItemSlot, item);
    }

    private void loadItemsSection() {
        File itemsFile = new File(plugin.getDataFolder(), "spawn-lobby-items.yml");
        if (!itemsFile.exists()) {
            plugin.saveResource("spawn-lobby-items.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);
        if (!config.contains("items"))
            return;

        for (String key : Objects.requireNonNull(config.getConfigurationSection("items")).getKeys(false)) {
            try {
                Material mat = Material.getMaterial(config.getString("items." + key + ".material", "COMPASS"));
                if (mat == null)
                    continue;
                int slot = Integer.parseInt(key);
                final ItemStack item = plugin.getUtils().ib(mat)
                        .name(config.getString("items." + key + ".display-name")).build();

                items.put(slot, item);
                if (config.isString("items." + key + ".command")) {
                    commands.put(mat, config.getString("items." + key + ".command"));
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid slot key: " + key);
            }
        }

        if (config.contains("respawn-item")) {
            String matName = config.getString("respawn-item.material", "ARROW");
            respawnItemMaterial = Material.getMaterial(matName);
            respawnItemName = config.getString("respawn-item.display-name", "<green>Click to Respawn");
            respawnItemSlot = config.getInt("respawn-item.slot", 4);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        final ItemStack item = event.getItem();
        if (item == null)
            return;

        if (isRespawnItem(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            FFAPlayer ffaPlayer = plugin.getPlayersManager().getFFAPlayer(player);
            if (ffaPlayer.getLastKit() == null || ffaPlayer.getLastSpawn() == null)
                return;

            Kit kit = ffaPlayer.getLastKit();
            plugin.getKitManager().applyKit(player, kit);
            player.teleport(ffaPlayer.getLastSpawn());
            ffaPlayer.setState(PlayerState.IN_FFA);
            // plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
            // MessagesManager.getInstance().getMessage("respawn-applied"));
            return;
        }

        if (commands.containsKey(item.getType())) {
            event.getPlayer().performCommand(commands.get(item.getType()));
        }
    }

    private boolean isRespawnItem(@NotNull ItemStack item) {
        return respawnItemMaterial != null
                && item.getType() == respawnItemMaterial
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(respawnKey, PersistentDataType.BYTE);
    }
}