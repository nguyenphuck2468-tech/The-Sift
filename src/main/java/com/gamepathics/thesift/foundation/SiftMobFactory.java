package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Routes all built-in Sift encounters through optional mob providers. */
public final class SiftMobFactory {
    private final JavaPlugin plugin;
    private final MythicMobBridge providers;
    private final ModelEngineBridge modelEngine;

    /**
     * Plugin-typed constructor used by the managers. The provider bridges require a concrete
     * {@link JavaPlugin} (for their own command/config registration), so this stays separate
     * from the {@link #spawn} helper below, which only needs a scheduler owner.
     */
    public SiftMobFactory(JavaPlugin plugin) {
        this.plugin = plugin;
        this.providers = new MythicMobBridge(plugin);
        this.modelEngine = new ModelEngineBridge(plugin);
    }

    LivingEntity spawn(String id, Location location, EntityType fallbackType) {
        Entity entity = providers.spawn(id, location);
        if (!(entity instanceof LivingEntity)) {
            if (plugin.getConfig().getBoolean("providers.strict", true)) {
                plugin.getLogger().warning("No configured mob provider resolved '" + id + "'; strict mode skipped spawn.");
                return null;
            }
            entity = location.getWorld().spawnEntity(location, fallbackType);
        }
        modelEngine.attach(entity, id);
        return (LivingEntity) entity;
    }

    /**
     * Reference-mod parity: apply a mob's base attributes from {@code mobs.yml}-style config.
     * The mod ships per-mob defaults (Sifter 40 HP / speed 0.31 / dmg 10, Dark Sniffer 40 HP /
     * speed 0.1 / dmg 7 / knockback resist 0.75, Echo Golem 34 HP / speed 0.23, Blub 20 HP /
     * speed 0.3 / dmg 3); managers previously hard-coded only boss stats in strict mode, so
     * non-boss spawns silently kept their vanilla stand-in values. This helper centralises the
     * read/write so every spawn path can honour config. Unknown attribute names are ignored.
     */
    public static void applyAttributes(LivingEntity entity, java.util.Map<String, Double> stats) {
        if (entity == null || stats == null) return;
        for (java.util.Map.Entry<String, Double> entry : stats.entrySet()) {
            org.bukkit.attribute.Attribute attr = toAttribute(entry.getKey());
            if (attr == null || entity.getAttribute(attr) == null) continue;
            entity.getAttribute(attr).setBaseValue(entry.getValue());
            if (attr == org.bukkit.attribute.Attribute.MAX_HEALTH && entity.getHealth() > entry.getValue()) {
                entity.setHealth(entry.getValue());
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static org.bukkit.attribute.Attribute toAttribute(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(name.toLowerCase(java.util.Locale.ROOT));
            org.bukkit.attribute.Attribute attr = org.bukkit.Registry.ATTRIBUTE.get(key);
            if (attr != null) return attr;
        } catch (Throwable ignored) {
        }
        try {
            return org.bukkit.attribute.Attribute.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Convenience overload used by tests and callers that only tweak health. */
    public static void applyMaxHealth(LivingEntity entity, double health) {
        applyAttributes(entity, java.util.Map.of("MAX_HEALTH", health));
    }

    /** Scheduler-safe no-op hook kept for future async provider work; currently synchronous. */
    static void scheduleSync(Plugin owner, Runnable task) {
        Bukkit.getScheduler().runTask(owner, task);
    }
}

