package site.zvolcan.fFAUtils.managers;

import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Manages the list of command root labels that are blocked for players currently in combat.
 * Labels are loaded from {@code blocked-commands.yml} in the plugin data folder and
 * normalized to lowercase. Matching is case-insensitive.
 */
public final class BlockedCommandsManager {

    @Getter
    private static BlockedCommandsManager instance;

    private final FFAUtils plugin;
    private final Set<String> blockedCommands = new HashSet<>();

    public BlockedCommandsManager(@NotNull FFAUtils plugin) {
        instance = this;
        this.plugin = plugin;
    }

    /**
     * Loads all blocked command labels from {@code blocked-commands.yml} in the data folder.
     * If the file is missing or the {@code commands} key is absent, the blocked set is cleared
     * and {@link #isBlocked(String)} returns false for every label.
     */
    public void loadBlockedCommands() {
        blockedCommands.clear();

        File file = new File(plugin.getDataFolder(), "blocked-commands.yml");
        if (!file.exists()) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.contains("commands") && config.isList("commands")) {
                config.getStringList("commands").stream()
                        .filter(s -> s != null && !s.isEmpty())
                        .map(String::toLowerCase)
                        .forEach(blockedCommands::add);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load blocked-commands.yml", e);
        }
    }

    /**
     * Returns true if the given command label is in the blocked set.
     * The label is normalized to lowercase before lookup, so matching is case-insensitive.
     */
    public boolean isBlocked(@NotNull String label) {
        return blockedCommands.contains(label.toLowerCase());
    }

    /**
     * Returns an unmodifiable view of the currently blocked command labels.
     */
    @NotNull
    public Set<String> getBlockedCommands() {
        return Collections.unmodifiableSet(blockedCommands);
    }
}
