package com.gamepathics.thesift.foundation;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/** Routes all built-in Sift encounters through optional mob providers. */
final class SiftMobFactory {
    private final JavaPlugin plugin;
    private final MythicMobBridge providers;
    private final ModelEngineBridge modelEngine;

    SiftMobFactory(JavaPlugin plugin) {
        this.plugin = plugin;
        this.providers = new MythicMobBridge(plugin);
        this.modelEngine = new ModelEngineBridge(plugin);
    }

    LivingEntity spawn(String id, Location location, EntityType fallbackType) {
        Entity entity = providers.spawn(id, location);
        if (!(entity instanceof LivingEntity)) {
            entity = location.getWorld().spawnEntity(location, fallbackType);
        }
        modelEngine.attach(entity, id);
        return (LivingEntity) entity;
    }
}
