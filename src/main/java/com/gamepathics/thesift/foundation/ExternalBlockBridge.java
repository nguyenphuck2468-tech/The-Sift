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
        String externalId = configuredId(contentId, provider);
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

    boolean resolves(String backend, String contentId) {
        String externalId = configuredId(contentId, backend.toLowerCase(Locale.ROOT));
        try {
            if (backend.equals("SiftBlockProvider")) return plugin.getServer().getServicesManager()
                    .getRegistrations(SiftBlockProvider.class).stream().map(RegisteredServiceProvider::getProvider)
                    .anyMatch(p -> p != null && (plugin.getConfig().getString("integrations.blocks.provider", "auto").equalsIgnoreCase("auto")
                            || plugin.getConfig().getString("integrations.blocks.provider", "auto").equalsIgnoreCase(p.getProviderId()))
                            && p.supports(contentId));
            if (backend.equals("ItemsAdder")) {
                if (!enabled(backend)) return false;
                Class<?> type = Reflect.optionalClass("dev.lone.itemsadder.api.CustomBlock");
                if (type == null) return false;
                Method inRegistry = Reflect.optionalMethod(type, "isInRegistry", String.class);
                return inRegistry != null
                        && Boolean.TRUE.equals(inRegistry.invoke(null, externalId));
            }
            if (backend.equals("Nexo")) return nexoBlockDefinitionExists(externalId);
            if (backend.equals("Oraxen")) return oraxenBlockDefinitionExists(externalId);
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        return false;
    }

    private boolean nexoBlockDefinitionExists(String id) throws ReflectiveOperationException {
        if (!enabled("Nexo")) return false;
        Class<?> blocks = Reflect.optionalClass("com.nexomc.nexo.api.NexoBlocks");
        if (blocks == null) return false;
        Method isCustomBlock = Reflect.optionalMethod(blocks, "isCustomBlock", String.class);
        if (isCustomBlock == null) return false;
        if (Boolean.TRUE.equals(isCustomBlock.invoke(null, id))) return true;
        return !id.contains(":") && Boolean.TRUE.equals(isCustomBlock.invoke(null, "thesift:" + id));
    }

    private boolean oraxenBlockDefinitionExists(String id) throws ReflectiveOperationException {
        if (!enabled("Oraxen")) return false;
        Class<?> blocks = Reflect.optionalClass("io.th0rgal.oraxen.api.OraxenBlocks");
        if (blocks == null) return false;
        Method getBlockIDs = Reflect.optionalMethod(blocks, "getBlockIDs");
        if (getBlockIDs == null) return false;
        Object result = getBlockIDs.invoke(null);
        if (!(result instanceof Iterable<?> ids)) return false;
        String namespaced = id.contains(":") ? id : "thesift:" + id;
        for (Object value : ids) {
            if (value instanceof String found && (found.equalsIgnoreCase(id) || found.equalsIgnoreCase(namespaced))) return true;
        }
        return false;
    }

    boolean isBlock(String contentId, Block block) {
        String provider = plugin.getConfig().getString("integrations.blocks.provider", "auto")
                .toLowerCase(Locale.ROOT);
        String externalId = configuredId(contentId, provider);
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
            if (nexoBlockCheck(block, externalId)) return true;
        }
        if (provider.equals("auto") || provider.equals("itemsadder")) {
            if (itemsAdderBlockCheck(block, externalId)) return true;
        }
        if (provider.equals("auto") || provider.equals("oraxen")) {
            if (oraxenBlockCheck(block, externalId)) return true;
        }
        return false;
    }

    boolean isProviderOwned(Block block) {
        if (block == null) return false;
        if (reflectiveBlockCheck("Nexo", "com.nexomc.nexo.api.NexoBlocks", "isCustomBlock", block)
                || reflectiveBlockCheck("Oraxen", "io.th0rgal.oraxen.api.OraxenBlocks", "isOraxenBlock", block)
                || isAnyItemsAdderBlock(block)) return true;
        for (String contentId : ProviderContentIds.BLOCK_IDS) if (isBlock(contentId, block)) return true;
        var definitions = plugin.getConfig().getConfigurationSection("integrations.blocks.definitions");
        if (definitions != null) for (String contentId : definitions.getKeys(false))
            if (!ProviderContentIds.BLOCK_IDS.contains(contentId) && isBlock(contentId, block)) return true;
        return false;
    }

    private boolean isAnyItemsAdderBlock(Block block) {
        if (!enabled("ItemsAdder")) return false;
        try {
            Class<?> customBlock = Reflect.optionalClass("dev.lone.itemsadder.api.CustomBlock");
            if (customBlock == null) return false;
            Method byAlreadyPlaced = Reflect.optionalMethod(customBlock, "byAlreadyPlaced", Block.class);
            if (byAlreadyPlaced == null) return false;
            return byAlreadyPlaced.invoke(null, block) != null;
        } catch (ReflectiveOperationException | LinkageError exception) { return false; }
    }

    private boolean reflectiveBlockCheck(String pluginName, String className, String methodName, Block block) {
        if (!enabled(pluginName)) return false;
        try {
            Class<?> api = Reflect.optionalClass(className);
            if (api == null) return false;
            Method check = Reflect.optionalMethod(api, methodName, Block.class);
            if (check == null) return false;
            return Boolean.TRUE.equals(check.invoke(null, block));
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine(pluginName + " block detection unavailable: " + exception.getMessage());
            return false;
        }
    }

    private boolean nexoBlockCheck(Block block, String expectedId) {
        if (!enabled("Nexo")) return false;
        try {
            Class<?> api = Reflect.optionalClass("com.nexomc.nexo.api.NexoBlocks");
            if (api == null) return false;
            Method customBlockMechanic = Reflect.optionalMethod(api, "customBlockMechanic", Block.class);
            if (customBlockMechanic == null) return false;
            Object mechanic = customBlockMechanic.invoke(null, block);
            if (mechanic == null) return false;
            Method getItemID = Reflect.optionalMethod(mechanic.getClass(), "getItemID");
            if (getItemID == null) return false;
            Object found = getItemID.invoke(mechanic);
            return found instanceof String id && sameProviderId(expectedId, id);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("Nexo block ID lookup unavailable: " + exception.getMessage());
            return false;
        }
    }

    private boolean oraxenBlockCheck(Block block, String expectedId) {
        if (!enabled("Oraxen")) return false;
        try {
            Class<?> api = Reflect.optionalClass("io.th0rgal.oraxen.api.OraxenBlocks");
            if (api == null) return false;
            for (String methodName : new String[]{"getNoteBlockMechanic", "getStringMechanic",
                    "getChorusMechanic", "getShapedMechanic"}) {
                Method mechanicLookup = Reflect.optionalMethod(api, methodName, Block.class);
                if (mechanicLookup == null) continue;
                Object mechanic = mechanicLookup.invoke(null, block);
                if (mechanic == null) continue;
                for (String idMethod : new String[]{"getItemID", "getItemId"}) {
                    Method idLookup = Reflect.optionalMethod(mechanic.getClass(), idMethod);
                    if (idLookup == null) continue;
                    Object found = idLookup.invoke(mechanic);
                    if (found instanceof String id && sameProviderId(expectedId, id)) return true;
                }
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("Oraxen block ID lookup unavailable: " + exception.getMessage());
        }
        return false;
    }

    private boolean sameProviderId(String expected, String actual) {
        if (expected.contains(":")) return expected.equalsIgnoreCase(actual);
        return expected.equalsIgnoreCase(actual) || ("thesift:" + expected).equalsIgnoreCase(actual);
    }

    private boolean itemsAdderBlockCheck(Block block, String expectedId) {
        if (!enabled("ItemsAdder")) return false;
        try {
            Class<?> customBlock = Reflect.optionalClass("dev.lone.itemsadder.api.CustomBlock");
            if (customBlock == null) return false;
            Method byAlreadyPlaced = Reflect.optionalMethod(customBlock, "byAlreadyPlaced", Block.class);
            if (byAlreadyPlaced == null) return false;
            Object result = byAlreadyPlaced.invoke(null, block);
            if (result == null) return false;
            Method getNamespacedID = Reflect.optionalMethod(customBlock, "getNamespacedID");
            if (getNamespacedID == null) return false;
            Object actualId = getNamespacedID.invoke(result);
            String normalized = expectedId.contains(":") ? expectedId : "thesift:" + expectedId;
            return actualId instanceof String id && id.equalsIgnoreCase(normalized);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("ItemsAdder block detection unavailable: " + exception.getMessage());
            return false;
        }
    }

    private boolean placeItemsAdder(String id, Location location) {
        if (!enabled("ItemsAdder")) return false;
        try {
            Class<?> customBlock = Reflect.optionalClass("dev.lone.itemsadder.api.CustomBlock");
            if (customBlock == null) return false;
            Method place = Reflect.optionalMethod(customBlock, "place", String.class, Location.class);
            if (place == null) return false;
            String itemId = id.contains(":") ? id : "thesift:" + id;
            return place.invoke(null, itemId, location.clone()) != null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("ItemsAdder block unavailable for " + id + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean placeReflectively(String pluginName, String className, String id, Location location) {
        if (!enabled(pluginName)) return false;
        try {
            Class<?> api = Reflect.optionalClass(className);
            if (api == null) return false;
            Method place = Reflect.optionalMethod(api, "place", String.class, Location.class);
            if (place == null) return false;
            Material before = location.getBlock().getType();
            Object result = place.invoke(null, id, location.clone());
            if (result instanceof Boolean success) return success;
            if (result != null) return true;
            Block block = location.getBlock();
            return isBlock(id, block) || block.getType() != before;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine(pluginName + " block unavailable for " + id + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean enabled(String name) {
        return plugin.getServer().getPluginManager().isPluginEnabled(name);
    }

    private String configuredId(String contentId, String backend) {
        String configured = plugin.getConfig().getString("integrations.blocks.definitions." + contentId, "").trim();
        if (!configured.isEmpty()) return configured;
        return backend.equals("itemsadder") ? "thesift:" + contentId : contentId;
    }
}
