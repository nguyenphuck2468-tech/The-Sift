package com.gamepathics.thesift.foundation;

import com.gamepathics.thesift.foundation.api.SiftBlockProvider;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Locale;

/** Optional custom-block placement integrations used by Sift world generation. */
final class ExternalBlockBridge {
    private final JavaPlugin plugin;

    ExternalBlockBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    boolean place(String contentId, Location location) {
        String provider = plugin.getConfig().getString("integrations.blocks.provider", "auto")
                .toLowerCase(Locale.ROOT);
        String externalId = plugin.getConfig().getString("integrations.blocks.definitions." + contentId, "")
                .trim();
        if (externalId.isEmpty() || provider.equals("vanilla")) return false;

        for (RegisteredServiceProvider<SiftBlockProvider> registration : plugin.getServer()
                .getServicesManager().getRegistrations(SiftBlockProvider.class)) {
            SiftBlockProvider adapter = registration.getProvider();
            if (adapter == null) continue;
            if (!provider.equals("auto") && !provider.equalsIgnoreCase(adapter.getProviderId())) continue;
            try {
                if (adapter.placeBlock(externalId, location.clone())) return true;
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Custom block provider '" + adapter.getProviderId()
                        + "' failed for " + contentId + ": " + exception.getMessage());
            }
        }
        if (provider.equals("auto") || provider.equals("nexo")) {
            if (placeReflectively("Nexo", "com.nexomc.nexo.api.NexoBlocks", externalId, location)) return true;
        }
        if (provider.equals("auto") || provider.equals("itemsadder")) {
            if (placeItemsAdder(externalId, location)) return true;
        }
        if (provider.equals("auto") || provider.equals("oraxen")) {
            if (placeReflectively("Oraxen", "io.th0rgal.oraxen.api.OraxenBlocks", externalId, location)) return true;
        }
        return false;
    }

    boolean isBlock(String contentId, Block block) {
        String provider = plugin.getConfig().getString("integrations.blocks.provider", "auto")
                .toLowerCase(Locale.ROOT);
        String externalId = plugin.getConfig().getString("integrations.blocks.definitions." + contentId, "")
                .trim();
        if (externalId.isEmpty() || provider.equals("vanilla")) return false;
        for (RegisteredServiceProvider<SiftBlockProvider> registration : plugin.getServer()
                .getServicesManager().getRegistrations(SiftBlockProvider.class)) {
            SiftBlockProvider adapter = registration.getProvider();
            if (adapter == null || (!provider.equals("auto")
                    && !provider.equalsIgnoreCase(adapter.getProviderId()))) continue;
            try {
                if (adapter.isBlock(externalId, block)) return true;
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Custom block provider '" + adapter.getProviderId()
                        + "' failed to identify " + contentId + ": " + exception.getMessage());
            }
        }
        if (provider.equals("auto") || provider.equals("nexo")) {
            if (reflectiveBlockCheck("Nexo", "com.nexomc.nexo.api.NexoBlocks", "isCustomBlock", block)) return true;
        }
        if (provider.equals("auto") || provider.equals("itemsadder")) {
            if (itemsAdderBlockCheck(block, externalId)) return true;
        }
        if (provider.equals("auto") || provider.equals("oraxen")) {
            if (reflectiveBlockCheck("Oraxen", "io.th0rgal.oraxen.api.OraxenBlocks", "isOraxenBlock", block)) return true;
        }
        return false;
    }

    private boolean reflectiveBlockCheck(String pluginName, String className, String methodName, Block block) {
        if (!enabled(pluginName)) return false;
        try {
            Object result = Class.forName(className).getMethod(methodName, Block.class).invoke(null, block);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine(pluginName + " block detection unavailable: " + exception.getMessage());
            return false;
        }
    }

    private boolean itemsAdderBlockCheck(Block block, String expectedId) {
        if (!enabled("ItemsAdder")) return false;
        try {
            Class<?> customBlock = Class.forName("dev.lone.itemsadder.api.CustomBlock");
            Object result = customBlock
                    .getMethod("byAlreadyPlaced", Block.class).invoke(null, block);
            if (result == null) return false;
            Object actualId = customBlock.getMethod("getNamespacedID").invoke(result);
            return actualId instanceof String id && id.equalsIgnoreCase(expectedId);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("ItemsAdder block detection unavailable: " + exception.getMessage());
            return false;
        }
    }

    private boolean placeItemsAdder(String id, Location location) {
        if (!enabled("ItemsAdder")) return false;
        try {
            Class<?> customBlock = Class.forName("dev.lone.itemsadder.api.CustomBlock");
            String itemId = id.contains(":") ? id : "thesift:" + id;
            Object result = customBlock.getMethod("place", String.class, Location.class)
                    .invoke(null, itemId, location.clone());
            return result != null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("ItemsAdder block unavailable for " + id + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean placeReflectively(String pluginName, String className, String id, Location location) {
        if (!enabled(pluginName)) return false;
        try {
            Class<?> api = Class.forName(className);
            Method place = api.getMethod("place", String.class, Location.class);
            Material before = location.getBlock().getType();
            Object result = place.invoke(null, id, location.clone());
            if (result instanceof Boolean success) return success;
            if (result != null) return true;
            Block block = location.getBlock();
            return block.getType() != before;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine(pluginName + " block unavailable for " + id + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean enabled(String name) {
        return plugin.getServer().getPluginManager().isPluginEnabled(name);
    }
}
