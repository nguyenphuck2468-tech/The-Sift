package com.gamepathics.thesift.foundation;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player-facing message catalog. {@code locale: vi} (default) or {@code en} in config.yml
 * selects the bundled {@code lang/<locale>.yml}; a file named {@code messages.yml} in the
 * plugin data folder overrides any key, so server owners can retranslate or tweak every
 * string without touching the jar. Unknown keys fall back to English, then to the default
 * passed at the call site, so a missing translation can never break a flow.
 *
 * <p>Migrating a hardcoded string: add the key to both bundled lang files and replace the
 * literal with {@code messages.send(sender, "key", "English default")}.
 */
public final class Messages {
    private static final String PREFIX = "§d";

    private static volatile Messages instance;

    /** Idempotent: initialises (or re-initialises on /sift reload) the static catalog. */
    public static void init(JavaPlugin plugin) {
        instance = new Messages(plugin);
    }

    public static String get(String key, String englishDefault) {
        Messages current = instance;
        return current != null ? current.resolve(key, englishDefault) : englishDefault;
    }

    public static void send(CommandSender sender, String key, String englishDefault) {
        sender.sendMessage(PREFIX + get(key, englishDefault));
    }

    private final JavaPlugin plugin;
    private final String locale;
    private final Map<String, String> overrides = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> bundles = new ConcurrentHashMap<>();

    private Messages(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        String configured = plugin.getConfig().getString("locale", "vi").toLowerCase(java.util.Locale.ROOT);
        this.locale = configured.equals("en") ? "en" : "vi";
        bundles.put("vi", loadBundle("vi"));
        bundles.put("en", loadBundle("en"));
        FileConfiguration userFile = YamlConfiguration.loadConfiguration(
                plugin.getDataFolder().toPath().resolve("messages.yml").toFile());
        for (String key : userFile.getKeys(true)) {
            String value = userFile.getString(key);
            if (value != null) overrides.put(key, value);
        }
    }

    /** Resolves a key through user overrides, the configured locale, then English. */
    private String resolve(String key, String englishDefault) {
        String override = overrides.get(key);
        if (override != null) return override;
        String localized = bundles.getOrDefault(locale, Map.of()).get(key);
        if (localized != null) return localized;
        String english = bundles.getOrDefault("en", Map.of()).get(key);
        return english != null ? english : englishDefault;
    }

    private Map<String, String> loadBundle(String locale) {
        try (InputStream stream = plugin.getResource("lang/" + locale + ".yml")) {
            if (stream == null) return Map.of();
            FileConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, String> values = new ConcurrentHashMap<>();
            for (String key : config.getKeys(true)) {
                String value = config.getString(key);
                if (value != null) values.put(key, value);
            }
            return values;
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
