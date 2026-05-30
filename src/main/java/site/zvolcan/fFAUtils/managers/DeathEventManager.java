package site.zvolcan.fFAUtils.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.zvolcan.fFAUtils.objects.DeathEvent;
import site.zvolcan.fFAUtils.objects.EffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Manages configurable death events for the FFAUtils plugin.
 * Events are selected randomly with equal probability upon player death.
 */
public class DeathEventManager {

    private final JavaPlugin plugin;
    private final Map<String, DeathEvent> deathEvents = new HashMap<>();

    public DeathEventManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Saves a death event to memory and persists to death-events.yml
     * @param name the unique identifier for the event
     * @param event the DeathEvent to save
     * @return true if saved successfully, false if invalid input
     */
    public boolean saveDeathEvent(String name, DeathEvent event) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (event == null) {
            return false;
        }

        deathEvents.put(name, event);
        persistDeathEvents();
        return true;
    }

    /**
     * Retrieves a death event by name
     * @param name the event identifier
     * @return the DeathEvent or null if not found
     */
    @Nullable
    public DeathEvent getDeathEvent(String name) {
        return deathEvents.get(name);
    }

    /**
     * Returns an immutable copy of all death events
     * @return unmodifiable map of all events
     */
    public Map<String, DeathEvent> getAllDeathEvents() {
        return Collections.unmodifiableMap(new HashMap<>(deathEvents));
    }

    /**
     * Deletes a death event and persists the change
     * @param name the event identifier
     * @return true if deleted, false if not found or invalid name
     */
    public boolean deleteDeathEvent(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        boolean existed = deathEvents.containsKey(name);
        if (existed) {
            deathEvents.remove(name);
            persistDeathEvents();
        }
        return existed;
    }

    /**
     * Loads all death events from death-events.yml
     */
    public void loadAllDeathEvents() {
        deathEvents.clear();

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
            return;
        }

        File eventsFile = new File(dataFolder, "death-events.yml");
        if (!eventsFile.exists()) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(eventsFile);
            if (config.contains("events")) {
                for (String key : config.getConfigurationSection("events").getKeys(false)) {
                    String path = "events." + key;
                    String message = config.getString(path + ".message");
                    
                    if (message == null || message.isEmpty()) {
                        plugin.getLogger().log(Level.WARNING, 
                            "Skipping death event '" + key + "': missing message field");
                        continue;
                    }

                    boolean broadcast = config.getBoolean(path + ".broadcast", true);
                    String effectName = config.getString(path + ".effect", "NONE");
                    EffectType effect = EffectType.valueOf(effectName);

                    DeathEvent event = new DeathEvent(key, message, broadcast, effect);
                    deathEvents.put(key, event);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load death-events.yml", e);
        }
    }

    /**
     * Registers death events - loads all events from file
     */
    public void registerDeathEvents() {
        loadAllDeathEvents();
    }

    /**
     * Selects a random death event with equal probability
     * @return a random DeathEvent or null if no events configured
     */
    @Nullable
    public DeathEvent selectRandomEvent() {
        if (deathEvents.isEmpty()) {
            return null;
        }
        int index = ThreadLocalRandom.current().nextInt(deathEvents.size());
        return new ArrayList<>(deathEvents.values()).get(index);
    }

    /**
     * Executes a death event for the given player
     * @param player the player who died
     * @param event the death event to execute
     */
    public void executeDeathEvent(@Nullable Player player, @Nullable DeathEvent event) {
        if (player == null || event == null) {
            return;
        }
        broadcastDeathEvent(player, event);
    }

    /**
     * Broadcasts the death event message to all players
     * @param player the player who died
     * @param event the death event containing message and broadcast settings
     */
    private void broadcastDeathEvent(Player player, DeathEvent event) {
        String message = event.getMessage().replace("{player}", player.getName());
        
        if (event.isBroadcast()) {
            plugin.getServer().broadcastMessage(message);
        }
    }

    /**
     * Persists death events to death-events.yml
     */
    private void persistDeathEvents() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File eventsFile = new File(dataFolder, "death-events.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, DeathEvent> entry : deathEvents.entrySet()) {
            DeathEvent event = entry.getValue();
            String path = "events." + entry.getKey();
            config.set(path + ".message", event.getMessage());
            config.set(path + ".broadcast", event.isBroadcast());
            config.set(path + ".effect", event.getEffect().name());
        }

        try {
            config.save(eventsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save death-events.yml", e);
        }
    }
}