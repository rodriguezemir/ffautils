package site.zvolcan.fFAUtils.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.objects.DeathEvent;

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

    private final FFAUtils plugin;
    private List<String> messages;

    public DeathEventManager(@NotNull FFAUtils plugin) {
        this.plugin = plugin;
    }

    public boolean saveDeathEvent(@NotNull final String message) {
        messages.add(message);
        return true;
    }

    /**
     * Returns an immutable copy of all death messages
     * @return unmodifiable list of all messages
     */
    public List<String> getAllDeathEvents() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Loads all death events from death-events.yml
     */
    public void loadAllDeathEvents() {
        messages.clear();

        File eventsFile = new File(plugin.getDataFolder(), "death-events.yml");
        if (!eventsFile.exists()) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(eventsFile);
            if (config.contains("messages") && config.isList("messages")) {
                this.messages = new ArrayList<>(config.getStringList("messages"));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load death-events.yml", e);
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
     * @param killer the player killer
     * @param entity the player who died
     */
    public void broadcastDeathEvent(@NotNull Player entity, @Nullable final Player killer) {
        String message =
                messages.get(ThreadLocalRandom.current().nextInt(messages.size()))
                        .replace("{entity}", entity.getName());

        if (killer != null) {
            message = message.replace("{killer}", killer.getName())
                    .replace("{health}",
                            String.format("%.2f", killer.getHealth())
                    );
        }

        plugin.getUtils().broadcast(message);
    }
}