package site.zvolcan.fFAUtils.inventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.Sounds;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.playClick;
import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.text;

/**
 * Kit content editor built on raw Bukkit inventories - deliberately not a FastInv.
 * <p>
 * The player edits the kit with their OWN inventory. The chest opened on top only
 * carries the Save / Cancel / Reset controls. The player's real items are snapshotted
 * on open and restored on every exit path, so they can never be lost.
 * <p>
 * Serialisation order of {@link Kit#getContents()} is exactly
 * {@link PlayerInventory#getContents()}: slots 0-35 storage (0-8 hotbar, 9-35 main),
 * 36-39 armor (boots, leggings, chestplate, helmet) and 40 offhand. This is the same
 * layout {@code KitManager#applyKit} feeds back into {@code PlayerInventory#setContents},
 * so kits round-trip unchanged.
 */
public final class KitEditContentsInventory {

    private static final int CONTROL_SIZE = 9;
    private static final int SAVE_SLOT = 2;
    private static final int RESET_SLOT = 4;
    private static final int CANCEL_SLOT = 6;

    private static final String SAVE_SKIN_URL = "http://textures.minecraft.net/texture/925b8eed5c565bd440ec47c79c20d5cf370162b1d9b5dd3100ed6283fe01d6e";
    private static final String RESET_SKIN_URL = "http://textures.minecraft.net/texture/a89b93fd616ed3670ccf647a0f9380398c0d4615634f2deff46c6edbdc712885";
    private static final String CANCEL_SKIN_URL = "http://textures.minecraft.net/texture/68d40935279771adc63936ed9c8463abdf5c5ba78d2e86cb1ec10b4d1d225fb";

    /** Active editing sessions keyed by player UUID */
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private KitEditContentsInventory() {
    }

    /** Snapshots the player's inventory, loads the kit into it and opens the control chest */
    public static void open(@NotNull FFAUtils plugin, @NotNull KitManager kitManager, @NotNull Player player,
            @NotNull String kitName) {
        // A player already editing must not be snapshotted twice - that would capture kit items.
        if (SESSIONS.containsKey(player.getUniqueId())) {
            return;
        }

        ItemStack[] snapshot = deepCopy(player.getInventory().getContents());

        Inventory controls = Bukkit.createInventory(new ControlHolder(), CONTROL_SIZE, "Editing: " + kitName);
        controls.setItem(SAVE_SLOT, button(SAVE_SKIN_URL, "<green>Save</green>",
                "<gray>Store your current inventory as this kit</gray>"));
        controls.setItem(RESET_SLOT, button(RESET_SKIN_URL, "<aqua>Reset</aqua>",
                "<gray>Reload the saved kit, discarding edits</gray>"));
        controls.setItem(CANCEL_SLOT, button(CANCEL_SKIN_URL, "<red>Cancel</red>",
                "<gray>Discard changes and restore your items</gray>"));

        Session session = new Session(plugin, kitManager, kitName, snapshot);
        SESSIONS.put(player.getUniqueId(), session);

        loadKitInto(kitManager, player, kitName);
        player.openInventory(controls);
    }

    /** Restores every open session - used on plugin disable */
    public static void restoreAllSessions() {
        for (UUID uuid : new ArrayList<>(SESSIONS.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            Session session = SESSIONS.remove(uuid);
            if (player != null && session != null) {
                session.restore(player);
            }
        }
    }

    /** Removes and restores a session; a no-op when it was already ended (idempotent) */
    private static void endSession(@NotNull Player player) {
        Session session = SESSIONS.remove(player.getUniqueId());
        if (session != null) {
            session.restore(player);
        }
    }

    /** Wipes the player's inventory and loads the saved kit into it */
    private static void loadKitInto(@NotNull KitManager kitManager, @NotNull Player player, @NotNull String kitName) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);

        Kit kit = kitManager.getKit(kitName);
        if (kit != null) {
            kitManager.applyKit(player, kit);
        }
    }

    private static ItemStack[] deepCopy(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    /** Builds a player-head control button wearing the skin fetched from {@code textureUrl} */
    private static ItemStack button(String textureUrl, String name, String lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(textureUrl));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid skin URL: " + textureUrl, e);
        }
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);

        meta.displayName(text(name));
        List<net.kyori.adventure.text.Component> loreLines = new ArrayList<>();
        loreLines.add(text(lore));
        meta.lore(loreLines);
        item.setItemMeta(meta);
        return item;
    }

    /** Marks the control inventory so the listener can recognise it */
    private static final class ControlHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException("Control inventory is tracked by the open view");
        }
    }

    /** One player's editing state: the kit being edited plus their untouched real inventory */
    private static final class Session {

        private final FFAUtils plugin;
        private final KitManager kitManager;
        private final String kitName;
        private final ItemStack[] snapshot;

        private Session(FFAUtils plugin, KitManager kitManager, String kitName, ItemStack[] snapshot) {
            this.plugin = plugin;
            this.kitManager = kitManager;
            this.kitName = kitName;
            this.snapshot = snapshot;
        }

        private void restore(@NotNull Player player) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setArmorContents(null);
            inventory.setItemInOffHand(null);
            inventory.setContents(deepCopy(snapshot));
        }
    }

    /** Handles the control buttons and every exit path of an editing session */
    public static final class SessionListener implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof ControlHolder)) {
                return;
            }
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            Session session = SESSIONS.get(player.getUniqueId());
            if (session == null) {
                return;
            }

            Inventory clicked = event.getClickedInventory();
            if (clicked == null || !(clicked.getHolder() instanceof ControlHolder)) {
                // Clicks in the player's own inventory are the editing surface and stay allowed,
                // but shift-clicking would push items into the control chest.
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                }
                return;
            }

            event.setCancelled(true);

            switch (event.getSlot()) {
                case SAVE_SLOT -> save(session, player);
                case RESET_SLOT -> reset(session, player);
                case CANCEL_SLOT -> cancel(session, player);
                default -> {
                }
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (!(event.getInventory().getHolder() instanceof ControlHolder)) {
                return;
            }
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < CONTROL_SIZE) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            if (!(event.getInventory().getHolder() instanceof ControlHolder)) {
                return;
            }
            HumanEntity human = event.getPlayer();
            if (human instanceof Player player) {
                // No-op when a button already ended the session.
                endSession(player);
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            endSession(event.getPlayer());
        }

        private void save(Session session, Player player) {
            playClick(player);
            ItemStack[] contents = deepCopy(player.getInventory().getContents());
            session.kitManager.saveKit(session.kitName, new Kit(session.kitName, contents));

            // End the session before closing so the close event cannot restore twice.
            finish(session, player, Sounds.SUCCESS_SOUND,
                    "<green>Kit <white>" + session.kitName + "</white> saved.</green>");
        }

        private void cancel(Session session, Player player) {
            playClick(player);
            finish(session, player, Sounds.ERROR_SOUND, "<yellow>Kit editing cancelled.</yellow>");
        }

        private void reset(Session session, Player player) {
            playClick(player);
            loadKitInto(session.kitManager, player, session.kitName);
            session.plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                    "<gray>Reloaded the saved contents of <white>" + session.kitName + "</white>.</gray>");
        }

        /** Restores the snapshot, closes the control chest next tick and reports the outcome */
        private void finish(Session session, Player player, net.kyori.adventure.sound.Sound sound, String message) {
            SESSIONS.remove(player.getUniqueId());
            session.restore(player);
            session.plugin.getUtils().message(player, sound, message);
            Bukkit.getScheduler().runTask(session.plugin, () -> player.closeInventory());
        }
    }
}
