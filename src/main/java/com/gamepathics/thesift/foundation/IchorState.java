package com.gamepathics.thesift.foundation;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.scheduler.BukkitTask;

public final class IchorState {
    private static final String LEGACY_CAULDRONS_PATH = "runtime.ichor-cauldrons";
    private static final String LEGACY_BLOCKS_PATH = "runtime.ichor-blocks";

    private final JavaPlugin plugin;
    private final File storageFile;
    private final BukkitTask saveTask;
    private final Map<String, Integer> cauldrons = new HashMap<>();
    private final Map<String, String> blocks = new HashMap<>();
    private volatile boolean dirty = false;
    private long revision;

    public IchorState(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.storageFile = new File(plugin.getDataFolder(), "ichor-state.yml");
        load();
        // Schedule periodic saves to avoid saving on every single change
        this.saveTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::saveIfDirty, 20 * 60, 20 * 60);
    }

    /**
     * Moves Ichor data stored in older config.yml files into ichor-state.yml.
     * Existing state wins when the same location is present in both files.
     * The legacy config keys are removed only after the state file is safely
     * written and a backup of config.yml has been created.
     */
    static boolean migrateLegacyConfig(JavaPlugin plugin) {
        Objects.requireNonNull(plugin);
        return migrateLegacyConfig(plugin.getConfig(), plugin.getDataFolder(), plugin::saveConfig,
                plugin.getLogger());
    }

    static boolean migrateLegacyConfig(FileConfiguration source, File dataFolder,
                                       Runnable saveConfig, Logger logger) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(dataFolder);
        Objects.requireNonNull(saveConfig);
        Objects.requireNonNull(logger);
        boolean hasCauldrons = source.isConfigurationSection(LEGACY_CAULDRONS_PATH);
        boolean hasBlocks = source.isConfigurationSection(LEGACY_BLOCKS_PATH);
        if (!hasCauldrons && !hasBlocks) {
            return true;
        }

        File configFile = new File(dataFolder, "config.yml");
        File backupFile = nextConfigBackupFile(dataFolder);
        File stateFile = new File(dataFolder, "ichor-state.yml");
        File temporaryStateFile = new File(dataFolder, "ichor-state.yml.migrating");
        try {
            Files.createDirectories(dataFolder.toPath());
            if (configFile.exists()) {
                Files.copy(configFile.toPath(), backupFile.toPath());
            } else {
                source.save(backupFile);
            }

            FileConfiguration target = stateFile.exists()
                    ? YamlConfiguration.loadConfiguration(stateFile)
                    : new YamlConfiguration();
            mergeLegacySection(source, target, LEGACY_CAULDRONS_PATH, "cauldrons");
            mergeLegacySection(source, target, LEGACY_BLOCKS_PATH, "blocks");
            target.save(temporaryStateFile);
            try {
                Files.move(temporaryStateFile.toPath(), stateFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryStateFile.toPath(), stateFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | IllegalArgumentException exception) {
            logger.log(Level.SEVERE,
                    "Could not migrate legacy Ichor data; legacy config keys were retained.", exception);
            try {
                Files.deleteIfExists(temporaryStateFile.toPath());
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            return false;
        }

        source.set(LEGACY_CAULDRONS_PATH, null);
        source.set(LEGACY_BLOCKS_PATH, null);
        try {
            saveConfig.run();
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE,
                    "Ichor state was migrated, but legacy keys could not be removed from config.yml.", exception);
            return false;
        }
        logger.info("Migrated legacy Ichor state to ichor-state.yml; config backup: "
                + backupFile.getName());
        return true;
    }

    private static void mergeLegacySection(FileConfiguration source, FileConfiguration target,
                                           String sourcePath, String targetPath) {
        if (!source.isConfigurationSection(sourcePath)) {
            return;
        }
        for (String key : source.getConfigurationSection(sourcePath).getKeys(false)) {
            String targetKey = targetPath + "." + key;
            if (!target.contains(targetKey)) {
                target.set(targetKey, source.get(sourcePath + "." + key));
            }
        }
    }

    private static File nextConfigBackupFile(File dataFolder) {
        String baseName = "config.yml.pre-ichor-state-migration";
        File backupFile = new File(dataFolder, baseName + ".bak");
        int suffix = 1;
        while (backupFile.exists()) {
            backupFile = new File(dataFolder, baseName + "." + suffix++ + ".bak");
        }
        return backupFile;
    }

    public synchronized int level(Location location) {
        return cauldrons.getOrDefault(key(location), 0);
    }

    public synchronized void setLevel(Location location, int level) {
        String key = key(location);
        if (level <= 0) {
            cauldrons.remove(key);
        } else {
            cauldrons.put(key, level);
        }
        markDirty();
    }

    public synchronized void setBlock(Location location, String id) {
        blocks.put(key(location), id);
        markDirty();
    }

    public synchronized String block(Location location) {
        return blocks.get(key(location));
    }

    public synchronized void removeBlock(Location location) {
        blocks.remove(key(location));
        markDirty();
    }

    public void shutdown() {
        saveTask.cancel();
        // Synchronous write on the main thread is correct here: the async scheduler may already
        // be gone during onDisable, and this is the last chance to persist state.
        saveNow();
    }

    private void load() {
        if (!storageFile.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
        
        if (config.isConfigurationSection("cauldrons")) {
            for (String key : config.getConfigurationSection("cauldrons").getKeys(false)) {
                cauldrons.put(key, config.getInt("cauldrons." + key));
            }
        }
        if (config.isConfigurationSection("blocks")) {
            for (String key : config.getConfigurationSection("blocks").getKeys(false)) {
                blocks.put(key, config.getString("blocks." + key, ""));
            }
        }
        plugin.getLogger().info("Loaded Ichor state: " + cauldrons.size() + " cauldrons, " + blocks.size() + " blocks");
    }

    private void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    /**
     * Periodic async persistence. Snapshot AND file write share one monitor so a slow async
     * write can never land an older snapshot over a newer synchronous shutdown save (the
     * revision counter alone only guarded the dirty flag, not the file contents). The lock is
     * held briefly: main-thread mutators only append to small maps. The file is written to a
     * temp path and moved atomically so a crash mid-save cannot corrupt ichor-state.yml.
     */
    private void save() {
        final Map<String, Integer> cauldronSnapshot;
        final Map<String, String> blockSnapshot;
        final long savedRevision;
        synchronized (this) {
            if (!dirty && storageFile.exists()) return;
            cauldronSnapshot = new HashMap<>(cauldrons);
            blockSnapshot = new HashMap<>(blocks);
            savedRevision = revision;
            FileConfiguration config = new YamlConfiguration();
            config.set("cauldrons", cauldronSnapshot);
            config.set("blocks", blockSnapshot);
            try {
                Path target = storageFile.toPath();
                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);
                Path temp = Files.createTempFile(parent, "ichor-state", ".tmp");
                try {
                    Files.writeString(temp, config.saveToString());
                    try {
                        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException exception) {
                        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temp);
                }
                if (revision == savedRevision) dirty = false;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save Ichor state", e);
            }
        }
    }

    /** Synchronous variant used only from {@link #shutdown()} on the main thread. */
    private void saveNow() {
        save();
    }

    private synchronized void markDirty() {
        dirty = true;
        revision++;
    }

    private String key(Location location) {
        UUID worldUid = location.getWorld() != null ? location.getWorld().getUID() : new UUID(0, 0);
        return worldUid + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
