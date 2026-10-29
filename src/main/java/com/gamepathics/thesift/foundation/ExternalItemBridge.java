package com.gamepathics.thesift.foundation;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import com.gamepathics.thesift.foundation.api.SiftItemProvider;

import java.lang.reflect.Method;
import java.util.Locale;

/** Optional item-provider lookup. Reflection keeps third-party plugins optional. */
final class ExternalItemBridge {
    private final JavaPlugin plugin;

    ExternalItemBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    ItemStack create(String contentId) {
        String configured = plugin.getConfig().getString("integrations.items.provider", "auto")
                .toLowerCase(Locale.ROOT);
        if (!configured.equals("vanilla")) {
            ItemStack item = fromServices(contentId, configured);
            if (item != null) return item;
        }
        if (configured.equals("nexo") || (configured.equals("auto") && enabled("Nexo"))) {
            ItemStack item = fromNexo(contentId);
            if (item != null) return item;
            if (configured.equals("nexo")) return null;
        }
        if (configured.equals("itemsadder") || (configured.equals("auto") && enabled("ItemsAdder"))) {
            ItemStack item = fromItemsAdder(contentId);
            if (item != null) return item;
        }
        if (configured.equals("oraxen") || (configured.equals("auto") && enabled("Oraxen"))) {
            ItemStack item = fromOraxen(contentId);
            if (item != null) return item;
        }
        return null;
    }

    boolean isItem(String contentId, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        String configured = plugin.getConfig().getString("integrations.items.provider", "auto")
                .toLowerCase(Locale.ROOT);
        for (RegisteredServiceProvider<SiftItemProvider> registration : plugin.getServer()
                .getServicesManager().getRegistrations(SiftItemProvider.class)) {
            SiftItemProvider provider = registration.getProvider();
            if (provider == null || (!configured.equals("auto")
                    && !configured.equalsIgnoreCase(provider.getProviderId()))) continue;
            try {
                ItemStack expected = provider.createItem(contentId);
                if (expected != null && stack.isSimilar(expected)) return true;
            } catch (RuntimeException exception) {
                plugin.getLogger().fine("Sift item provider could not identify " + contentId + ": "
                        + exception.getMessage());
            }
        }
        if ((configured.equals("auto") || configured.equals("nexo")) && matchesNexo(contentId, stack)) return true;
        if ((configured.equals("auto") || configured.equals("itemsadder")) && matchesItemsAdder(contentId, stack)) return true;
        return (configured.equals("auto") || configured.equals("oraxen")) && matchesOraxen(contentId, stack);
    }

    private boolean matchesNexo(String id, ItemStack stack) {
        if (!enabled("Nexo")) return false;
        try {
            Class<?> api = Reflect.optionalClass("com.nexomc.nexo.api.NexoItems");
            if (api == null) return false;
            Method idFromItem = Reflect.optionalMethod(api, "idFromItem", ItemStack.class);
            if (idFromItem == null) return false;
            Object found = idFromItem.invoke(null, stack);
            return found instanceof String actual && matchesId(id, actual);
        } catch (ReflectiveOperationException | LinkageError exception) { return false; }
    }

    private boolean matchesItemsAdder(String id, ItemStack stack) {
        if (!enabled("ItemsAdder")) return false;
        try {
            Class<?> api = Reflect.optionalClass("dev.lone.itemsadder.api.CustomStack");
            if (api == null) return false;
            Method byItemStack = Reflect.optionalMethod(api, "byItemStack", ItemStack.class);
            if (byItemStack == null) return false;
            Object custom = byItemStack.invoke(null, stack);
            if (custom == null) return false;
            Method getNamespacedID = Reflect.optionalMethod(custom.getClass(), "getNamespacedID");
            if (getNamespacedID == null) return false;
            Object found = getNamespacedID.invoke(custom);
            return found instanceof String actual && matchesId(id, actual);
        } catch (ReflectiveOperationException | LinkageError exception) { return false; }
    }

    private boolean matchesOraxen(String id, ItemStack stack) {
        if (!enabled("Oraxen")) return false;
        try {
            Class<?> api = Reflect.optionalClass("io.th0rgal.oraxen.api.OraxenItems");
            if (api == null) return false;
            Method getIdByItem = Reflect.optionalMethod(api, "getIdByItem", ItemStack.class);
            if (getIdByItem == null) return false;
            Object found = getIdByItem.invoke(null, stack);
            return found instanceof String actual && matchesId(id, actual);
        } catch (ReflectiveOperationException | LinkageError exception) { return false; }
    }

    private boolean matchesId(String expected, String actual) {
        if (expected.contains(":")) return expected.equalsIgnoreCase(actual);
        return expected.equalsIgnoreCase(actual) || ("thesift:" + expected).equalsIgnoreCase(actual);
    }

    boolean resolves(String backend, String id) {
        try {
            return switch (backend) {
                case "SiftItemProvider" -> plugin.getServer().getServicesManager()
                        .getRegistrations(SiftItemProvider.class).stream().map(RegisteredServiceProvider::getProvider)
                        .anyMatch(p -> p != null && (plugin.getConfig().getString("integrations.items.provider", "auto").equalsIgnoreCase("auto")
                                || plugin.getConfig().getString("integrations.items.provider", "auto").equalsIgnoreCase(p.getProviderId()))
                                && p.supports(id));
                case "Nexo" -> fromNexo(id) != null;
                case "ItemsAdder" -> fromItemsAdder(id) != null;
                case "Oraxen" -> fromOraxen(id) != null;
                default -> false;
            };
        } catch (RuntimeException e) { return false; }
    }

    private ItemStack fromServices(String id, String configured) {
        for (RegisteredServiceProvider<SiftItemProvider> registration : plugin.getServer()
                .getServicesManager().getRegistrations(SiftItemProvider.class)) {
            SiftItemProvider provider = registration.getProvider();
            if (provider == null) continue;
            String providerId = provider.getProviderId();
            if (!configured.equals("auto") && (providerId == null
                    || !providerId.equalsIgnoreCase(configured))) continue;
            try {
                ItemStack item = provider.createItem(id);
                if (item != null) return item.clone();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Custom item provider '" + providerId
                        + "' failed for " + id + ": " + exception.getMessage());
            }
        }
        return null;
    }

    private boolean enabled(String name) {
        return plugin.getServer().getPluginManager().isPluginEnabled(name);
    }

    private ItemStack fromNexo(String id) {
        if (!enabled("Nexo")) return null;
        try {
            Class<?> api = Reflect.optionalClass("com.nexomc.nexo.api.NexoItems");
            if (api == null) return null;
            Method itemFromId = Reflect.optionalMethod(api, "itemFromId", String.class);
            if (itemFromId == null) return null;
            Object builder = itemFromId.invoke(null, id);
            if (builder == null) builder = itemFromId.invoke(null, "thesift:" + id);
            if (builder == null) return null;
            Method build = Reflect.optionalMethod(builder.getClass(), "build");
            if (build == null) return null;
            Object stack = build.invoke(builder);
            return stack instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("Nexo item unavailable for " + id + ": " + exception.getMessage());
            return null;
        }
    }

    private ItemStack fromItemsAdder(String id) {
        if (!enabled("ItemsAdder")) return null;
        try {
            Class<?> api = Reflect.optionalClass("dev.lone.itemsadder.api.CustomStack");
            if (api == null) return null;
            Method getInstance = Reflect.optionalMethod(api, "getInstance", String.class);
            if (getInstance == null) return null;
            Object customStack = getInstance.invoke(null, "thesift:" + id);
            if (customStack == null) return null;
            Method getItemStack = Reflect.optionalMethod(api, "getItemStack");
            if (getItemStack == null) return null;
            Object stack = getItemStack.invoke(customStack);
            return stack instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("ItemsAdder item unavailable for " + id + ": " + exception.getMessage());
            return null;
        }
    }

    private ItemStack fromOraxen(String id) {
        if (!enabled("Oraxen")) return null;
        try {
            Class<?> api = Reflect.optionalClass("io.th0rgal.oraxen.api.OraxenItems");
            if (api == null) return null;
            Method getItemById = Reflect.optionalMethod(api, "getItemById", String.class);
            if (getItemById == null) return null;
            Object builder = getItemById.invoke(null, id);
            if (builder == null) return null;
            Method build = Reflect.optionalMethod(builder.getClass(), "build");
            if (build == null) return null;
            Object stack = build.invoke(builder);
            return stack instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("Oraxen item unavailable for " + id + ": " + exception.getMessage());
            return null;
        }
    }
}
