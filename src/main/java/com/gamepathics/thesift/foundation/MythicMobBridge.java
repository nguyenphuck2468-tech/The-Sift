package com.gamepathics.thesift.foundation;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import com.gamepathics.thesift.foundation.api.SiftMobProvider;

import java.lang.reflect.Method;
import java.util.Optional;

/** Uses configured MythicMobs definitions when available, with vanilla fallback. */
final class MythicMobBridge {
    private final JavaPlugin plugin;

    MythicMobBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    LivingEntity spawn(String id, Location location) {
        String configured = plugin.getConfig().getString("integrations.mobs.provider", "auto")
                .toLowerCase(java.util.Locale.ROOT);
        if (!configured.equals("vanilla") && !configured.equals("mythicmobs")) {
            for (RegisteredServiceProvider<SiftMobProvider> registration : plugin.getServer()
                    .getServicesManager().getRegistrations(SiftMobProvider.class)) {
                SiftMobProvider provider = registration.getProvider();
                if (provider == null) continue;
                String providerId = provider.getProviderId();
                if (!configured.equals("auto") && (providerId == null
                        || !providerId.equalsIgnoreCase(configured))) continue;
                try {
                    LivingEntity entity = provider.spawn(id, location);
                    if (entity != null) return entity;
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Custom mob provider '" + providerId
                            + "' failed for " + id + ": " + exception.getMessage());
                }
            }
        }
        if (configured.equals("auto") || configured.equals("mythicmobs")) {
            LivingEntity mythic = spawnMythic(id, location);
            if (mythic != null) return mythic;
        }
        if (configured.equals("auto") || configured.equals("elitemobs")) {
            return spawnEliteBoss(id, location);
        }
        return null;
    }

    private LivingEntity spawnMythic(String id, Location location) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("MythicMobs")) return null;
        try {
            Class<?> mythicBukkit = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object api = mythicBukkit.getMethod("inst").invoke(null);
            Object manager = api.getClass().getMethod("getMobManager").invoke(api);
            Object result = manager.getClass().getMethod("getMythicMob", String.class).invoke(manager, id);
            Object mob = result instanceof Optional<?> optional ? optional.orElse(null) : result;
            if (mob == null) return null;

            Class<?> adapter = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");
            Object mythicLocation = adapter.getMethod("adapt", Location.class).invoke(null, location);
            Method spawn = null;
            for (Method method : mob.getClass().getMethods()) {
                if (method.getName().equals("spawn") && method.getParameterCount() == 2
                        && method.getParameterTypes()[1] == double.class
                        && method.getParameterTypes()[0].isInstance(mythicLocation)) {
                    spawn = method;
                    break;
                }
            }
            if (spawn == null) return null;
            Object activeMob = spawn.invoke(mob, mythicLocation, 1.0);
            Object abstractEntity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
            Object bukkitEntity = abstractEntity.getClass().getMethod("getBukkitEntity").invoke(abstractEntity);
            return bukkitEntity instanceof LivingEntity entity ? entity : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("MythicMobs could not spawn " + id + "; using vanilla fallback: "
                    + exception.getMessage());
            return null;
        }
    }

    private LivingEntity spawnEliteBoss(String id, Location location) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("EliteMobs")) return null;
        String file = plugin.getConfig().getString("integrations.mobs.elitemobs.definitions." + id, "");
        if (file.isBlank()) return null;
        try {
            Class<?> bossType = Class.forName(
                    "com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity");
            Object boss = bossType.getMethod("createCustomBossEntity", String.class).invoke(null, file);
            if (boss == null) return null;
            bossType.getMethod("spawn", Location.class, int.class, boolean.class).invoke(boss, location,
                    plugin.getConfig().getInt("integrations.mobs.elitemobs.level", 1), true);
            Object livingEntity = bossType.getMethod("getLivingEntity").invoke(boss);
            return livingEntity instanceof LivingEntity entity ? entity : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("EliteMobs could not spawn configured Sift boss " + id + ": "
                    + exception.getMessage());
            return null;
        }
    }
}
