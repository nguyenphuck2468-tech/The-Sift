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
        if (configured.equals("auto") || configured.equals("itemsadder")) {
            LivingEntity itemsAdder = spawnItemsAdder(id, location);
            if (itemsAdder != null) return itemsAdder;
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

    private LivingEntity spawnItemsAdder(String id, Location location) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) return null;
        String entityId = plugin.getConfig().getString(
                "integrations.mobs.itemsadder.entities." + id, "thesift:" + id).trim();
        try {
            Class<?> customEntity = Reflect.optionalClass("dev.lone.itemsadder.api.CustomEntity");
            if (customEntity == null) return null;
            Method spawn = Reflect.optionalMethod(customEntity, "spawn", String.class, Location.class);
            if (spawn == null) return null;
            Method getEntity = Reflect.optionalMethod(customEntity, "getEntity");
            if (getEntity == null) return null;
            Object wrapper = spawn.invoke(null, entityId, location);
            if (wrapper == null) return null;
            Object entity = getEntity.invoke(wrapper);
            return entity instanceof LivingEntity living ? living : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("ItemsAdder custom entity unavailable for " + entityId + ": "
                    + exception.getMessage());
            return null;
        }
    }

    private LivingEntity spawnMythic(String id, Location location) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("MythicMobs")) return null;
        try {
            Class<?> mythicBukkit = Reflect.optionalClass("io.lumine.mythic.bukkit.MythicBukkit");
            Class<?> adapter = Reflect.optionalClass("io.lumine.mythic.bukkit.BukkitAdapter");
            if (mythicBukkit == null || adapter == null) return null;
            Method inst = Reflect.optionalMethod(mythicBukkit, "inst");
            Method adapt = Reflect.optionalMethod(adapter, "adapt", Location.class);
            if (inst == null || adapt == null) return null;
            Object api = inst.invoke(null);
            Method getMobManager = Reflect.optionalMethod(api.getClass(), "getMobManager");
            if (getMobManager == null) return null;
            Object manager = getMobManager.invoke(api);
            Method getMythicMob = Reflect.optionalMethod(manager.getClass(), "getMythicMob", String.class);
            if (getMythicMob == null) return null;
            Object result = getMythicMob.invoke(manager, id);
            Object mob = result instanceof Optional<?> optional ? optional.orElse(null) : result;
            if (mob == null) return null;

            Object mythicLocation = adapt.invoke(null, location);
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
            Method getAbstractEntity = Reflect.optionalMethod(activeMob.getClass(), "getEntity");
            if (getAbstractEntity == null) return null;
            Object abstractEntity = getAbstractEntity.invoke(activeMob);
            Method getBukkitEntity = Reflect.optionalMethod(abstractEntity.getClass(), "getBukkitEntity");
            if (getBukkitEntity == null) return null;
            Object bukkitEntity = getBukkitEntity.invoke(abstractEntity);
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
            Class<?> bossType = Reflect.optionalClass(
                    "com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity");
            if (bossType == null) return null;
            Method create = Reflect.optionalMethod(bossType, "createCustomBossEntity", String.class);
            Method spawn = Reflect.optionalMethod(bossType, "spawn", Location.class, int.class, boolean.class);
            Method getLivingEntity = Reflect.optionalMethod(bossType, "getLivingEntity");
            if (create == null || spawn == null || getLivingEntity == null) return null;
            Object boss = create.invoke(null, file);
            if (boss == null) return null;
            spawn.invoke(boss, location,
                    plugin.getConfig().getInt("integrations.mobs.elitemobs.level", 1), true);
            Object livingEntity = getLivingEntity.invoke(boss);
            return livingEntity instanceof LivingEntity entity ? entity : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("EliteMobs could not spawn configured Sift boss " + id + ": "
                    + exception.getMessage());
            return null;
        }
    }
}
