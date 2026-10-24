package com.gamepathics.thesift.foundation;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Diagnostic reflection checks for optional APIs actually used by the bridges. */
final class ProviderApiProbe {
    private static final java.util.Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private final JavaPlugin plugin;

    ProviderApiProbe(JavaPlugin plugin) { this.plugin = plugin; }

    List<String> inspect() {
        List<String> result = new ArrayList<>();
        probe(result, "Nexo", "com.nexomc.nexo.api.NexoItems", "idFromItem", ItemStack.class);
        probe(result, "Nexo", "com.nexomc.nexo.api.NexoItems", "itemFromId", String.class);
        probe(result, "Nexo", "com.nexomc.nexo.api.NexoBlocks", "isCustomBlock", String.class);
        probe(result, "Nexo", "com.nexomc.nexo.api.NexoBlocks", "isCustomBlock", Block.class);
        probe(result, "Nexo", "com.nexomc.nexo.api.NexoBlocks", "customBlockMechanic", Block.class);
        probe(result, "ItemsAdder", "dev.lone.itemsadder.api.CustomStack", "byItemStack", ItemStack.class);
        probe(result, "ItemsAdder", "dev.lone.itemsadder.api.CustomStack", "getInstance", String.class);
        probe(result, "ItemsAdder", "dev.lone.itemsadder.api.CustomBlock", "isInRegistry", String.class);
        probe(result, "ItemsAdder", "dev.lone.itemsadder.api.CustomBlock", "byAlreadyPlaced", Block.class);
        probe(result, "ItemsAdder", "dev.lone.itemsadder.api.CustomBlock", "place", String.class,
                org.bukkit.Location.class);
        probe(result, "Oraxen", "io.th0rgal.oraxen.api.OraxenItems", "getIdByItem", ItemStack.class);
        probe(result, "Oraxen", "io.th0rgal.oraxen.api.OraxenItems", "getItemById", String.class);
        probe(result, "Oraxen", "io.th0rgal.oraxen.api.OraxenBlocks", "getBlockIDs");
        probe(result, "Oraxen", "io.th0rgal.oraxen.api.OraxenBlocks", "isOraxenBlock", Block.class);
        return result;
    }

    private void probe(List<String> result, String pluginName, String className, String methodName,
                       Class<?>... parameters) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(pluginName)) return;
        String signature = pluginName + ": " + className.substring(className.lastIndexOf('.') + 1)
                + "." + methodName + "(" + java.util.Arrays.stream(parameters)
                .map(Class::getSimpleName).collect(java.util.stream.Collectors.joining(",")) + ")";
        boolean found;
        try {
            Class.forName(className).getMethod(methodName, parameters);
            found = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            found = false;
            if (WARNED.add(signature)) plugin.getLogger().warning("Optional provider API method missing: "
                    + signature + "; related bridge calls will be unavailable. Check the provider version/API.");
        }
        result.add(signature + " = " + (found ? "found" : "MISSING"));
    }
}
