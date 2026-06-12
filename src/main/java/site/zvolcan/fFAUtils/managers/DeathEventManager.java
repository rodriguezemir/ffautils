package site.zvolcan.fFAUtils.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.zvolcan.fFAUtils.FFAUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Manages configurable death events for the FFAUtils plugin.
 * Events are selected randomly with equal probability upon player death.
 */
public class DeathEventManager {

    private final FFAUtils plugin;
    private final List<String> messages = new ArrayList<>();

    public DeathEventManager(@NotNull FFAUtils plugin) {
        this.plugin = plugin;
    }

    public boolean saveDeathEvent(@NotNull final String message) {
        messages.add(message);
        return true;
    }

    /**
     * Returns an immutable copy of all death messages
     * 
     * @return unmodifiable list of all messages
     */
    public List<String> getAllDeathEvents() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Loads all death events from death-messages.yml
     */
    public void loadAllDeathEvents() {
        messages.clear();

        File eventsFile = new File(plugin.getDataFolder(), "death-messages.yml");
        if (!eventsFile.exists()) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(eventsFile);
            if (config.contains("messages") && config.isList("messages")) {
                messages.addAll(config.getStringList("messages"));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load death-messages.yml", e);
        }
    }

    /**
     * Registers death events - loads all events from file
     */
    public void registerDeathMessages() {
        loadAllDeathEvents();
    }

    /**
     * Broadcasts the death event message to all players
     * 
     * @param killer the player killer
     * @param entity the player who died
     */
    public void broadcastDeathEvent(@NotNull Player entity, @Nullable final Player killer) {
        if (messages.isEmpty() || killer == null) {
            return;
        }

        String message = messages.get(ThreadLocalRandom.current().nextInt(messages.size()))
                .replace("{entity}", entity.getName());

        message = message.replace("{killer}", killer.getName())
                .replace("{health}",
                        String.format("%.2f", killer.getHealth()));

        plugin.getUtils().broadcast(false, message);
    }
}