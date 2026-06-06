package site.zvolcan.fFAUtils.managers;

import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class MessagesManager {

    @Getter
    private static MessagesManager instance;

    private final FFAUtils plugin;
    private final Map<String, String> messages = new HashMap<>();

    public MessagesManager(@NotNull FFAUtils plugin) {
        instance = this;
        this.plugin = plugin;
    }

    public void loadMessages() {
        messages.clear();
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(messagesFile);
            for (String key : config.getKeys(false)) {
                if (config.isString(key)) {
                    messages.put(key, config.getString(key));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load messages.yml", e);
        }
    }

    public void registerMessages() {
        loadMessages();
    }

    @NotNull
    public String getMessage(@NotNull String key) {
        return messages.getOrDefault(key, "<red>Message not found: " + key);
    }

    @NotNull
    public String getMessage(@NotNull String key, String @NotNull... placeholders) {
        String message = getMessage(key);
        if (placeholders.length % 2 != 0) return message;
        for (int i = 0; i < placeholders.length; i += 2) {
            String placeholder = placeholders[i];
            String value = placeholders[i + 1];
            if (placeholder != null && value != null) {
                message = message.replace(placeholder, value);
            }
        }
        return message;
    }

    public void setMessage(@NotNull String key, @NotNull String value) {
        messages.put(key, value);
    }

    public void saveMessages() {
        try {
            File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
            YamlConfiguration config = new YamlConfiguration();
            for (Map.Entry<String, String> entry : messages.entrySet()) {
                config.set(entry.getKey(), entry.getValue());
            }
            config.save(messagesFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save messages.yml", e);
        }
    }
}
