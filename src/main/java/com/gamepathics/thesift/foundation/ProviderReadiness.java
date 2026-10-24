package com.gamepathics.thesift.foundation;

import com.gamepathics.thesift.foundation.api.SiftBlockProvider;
import com.gamepathics.thesift.foundation.api.SiftItemProvider;
import com.gamepathics.thesift.foundation.api.SiftMobProvider;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only provider inventory used by strict dimension gating and /sift doctor. */
final class ProviderReadiness {
    private static final long CACHE_NANOS = 1_000_000_000L;
    private static final List<String> OPTIONAL = List.of("Nexo", "ItemsAdder", "Oraxen", "MythicMobs",
            "EliteMobs", "ModelEngine", "Geyser-Spigot", "floodgate", "ViaVersion", "ViaBackwards",
            "ViaRewind", "ResourcePackManager");

    private final JavaPlugin plugin;
    private final ExternalItemBridge items;
    private final ExternalBlockBridge blocks;
    private volatile long cacheExpiresAt;
    private volatile List<Backend> cachedItems = List.of();
    private volatile List<Backend> cachedBlocks = List.of();
    private volatile List<Backend> cachedMobs = List.of();

    ProviderReadiness(JavaPlugin plugin) {
        this.plugin = plugin;
        items = new ExternalItemBridge(plugin);
        blocks = new ExternalBlockBridge(plugin);
    }

    boolean strict() { return plugin.getConfig().getBoolean("providers.strict", true); }

    void invalidate() { cacheExpiresAt = 0L; }

    Map<String, String> pluginVersions() {
        Map<String, String> result = new LinkedHashMap<>();
        OPTIONAL.forEach(name -> {
            var p = plugin.getServer().getPluginManager().getPlugin(name);
            result.put(name, p == null ? "missing" : (p.isEnabled() ? p.getDescription().getVersion() : "disabled"));
        });
        return result;
    }

    List<Backend> itemBackends() {
        ensureCache(false);
        return cachedItems;
    }

    private List<Backend> scanItemBackends() {
        List<Backend> result = new ArrayList<>();
        String configured = configured("integrations.items.provider");
        for (String backend : List.of("SiftItemProvider", "Nexo", "ItemsAdder", "Oraxen")) {
            if (!(backend.equals("SiftItemProvider") ? serviceSelected(configured, SiftItemProvider.class) : selected(configured, backend))) continue;
            List<String> missing = ProviderContentIds.ITEM_IDS.stream().filter(id -> !items.resolves(backend, id)).toList();
            result.add(new Backend(backend, installed(backend), missing));
        }
        return result;
    }

    List<Backend> blockBackends() {
        ensureCache(false);
        return cachedBlocks;
    }

    private List<Backend> scanBlockBackends() {
        List<Backend> result = new ArrayList<>();
        String configured = configured("integrations.blocks.provider");
        for (String backend : List.of("SiftBlockProvider", "Nexo", "ItemsAdder", "Oraxen")) {
            if (!(backend.equals("SiftBlockProvider") ? serviceSelected(configured, SiftBlockProvider.class) : selected(configured, backend))) continue;
            List<String> missing = ProviderContentIds.BLOCK_IDS.stream().filter(id -> !blocks.resolves(backend, id)).toList();
            result.add(new Backend(backend, installed(backend), missing));
        }
        return result;
    }

    List<Backend> mobBackends() {
        ensureCache(false);
        return cachedMobs;
    }

    private List<Backend> scanMobBackends() {
        List<Backend> result = new ArrayList<>();
        String configured = configured("integrations.mobs.provider");
        for (String backend : List.of("SiftMobProvider", "ItemsAdder", "MythicMobs", "EliteMobs")) {
            if (!(backend.equals("SiftMobProvider") ? serviceSelected(configured, SiftMobProvider.class) : selected(configured, backend))) continue;
            boolean installed = isService(backend) ? hasService(backend) : plugin.getServer().getPluginManager().isPluginEnabled(backend);
            List<String> missing = ProviderContentIds.MOB_IDS.stream().filter(id -> !mobResolves(backend, id)).toList();
            result.add(new Backend(backend, installed, missing));
        }
        return result;
    }

    boolean dimensionAllowed() {
        boolean content = itemBackends().stream().anyMatch(Backend::complete)
                && blockBackends().stream().anyMatch(Backend::complete);
        boolean mobs = mobBackends().stream().anyMatch(Backend::complete);
        return allowed(strict(), content, mobs);
    }

    static boolean allowed(boolean strict, boolean contentProvidersComplete, boolean mobProviderComplete) {
        return !strict || contentProvidersComplete && mobProviderComplete;
    }

    String summary() {
        ensureCache(true);
        StringBuilder out = new StringBuilder("Provider strict: ").append(strict())
                .append("; dimension allowed: ").append(dimensionAllowed());
        append(out, "Items", itemBackends(), ProviderContentIds.ITEM_IDS.size());
        append(out, "Blocks", blockBackends(), ProviderContentIds.BLOCK_IDS.size());
        append(out, "Mobs", mobBackends(), ProviderContentIds.MOB_IDS.size());
        pluginVersions().forEach((name, version) -> out.append("\n- ").append(name).append(": ").append(version));
        return out.toString();
    }

    private synchronized void ensureCache(boolean force) {
        long now = System.nanoTime();
        if (!force && now < cacheExpiresAt) return;
        cachedItems = scanItemBackends();
        cachedBlocks = scanBlockBackends();
        cachedMobs = scanMobBackends();
        cacheExpiresAt = System.nanoTime() + CACHE_NANOS;
    }

    private void append(StringBuilder out, String label, List<Backend> backends, int totalIds) {
        String path = switch (label) { case "Items" -> "integrations.items.provider";
            case "Blocks" -> "integrations.blocks.provider"; default -> "integrations.mobs.provider"; };
        out.append("\n").append(label).append(" provider selected: ").append(configured(path));
        for (Backend b : backends) {
            String state = !b.installed() ? "not installed"
                    : b.complete() ? "loaded/resolved by runtime ID probe"
                    : "present; unresolved (provider may still be loading or definitions are missing)";
            out.append("\n- ").append(b.name()).append(": ").append(state)
                    .append("; version ").append(pluginVersions().getOrDefault(b.name(), "registered service; version unavailable"))
                    .append("; resolved ").append(totalIds - b.missing().size()).append('/').append(totalIds)
                    .append("; missing IDs: ").append(b.missing().isEmpty() ? "none" : String.join(", ", b.missing()));
            if (!b.complete()) out.append("; fix: ").append(fixHint(b.name(), label));
        }
    }

    private String fixHint(String backend, String category) {
        return switch (backend) {
            case "Nexo" -> "install/enable Nexo; copy build/generated/provider-definitions/nexo/items.yml into plugins/Nexo/items/; keep integrations/nexo/block-variants.yml stable; reload Nexo or restart";
            case "ItemsAdder" -> category.equals("Mobs")
                    ? "install/enable ItemsAdder and define every missing custom entity; no generated entity definitions exist yet"
                    : "install/enable ItemsAdder; copy build/generated/provider-definitions/itemsadder/items.yml into plugins/ItemsAdder/contents/thesift/configs/; run /iazip or the installed version's documented reload";
            case "Oraxen" -> "install/enable Oraxen; copy build/generated/provider-definitions/oraxen/items.yml into plugins/Oraxen/items/; reload Oraxen or restart";
            case "MythicMobs" -> "install/enable MythicMobs; copy build/generated/provider-definitions/mythicmobs/TheSift.yml into plugins/MythicMobs/Mobs/; run /mm reload";
            case "EliteMobs" -> "install/enable EliteMobs and configure existing boss definition files; current integration cannot validate all required IDs";
            case "SiftItemProvider" -> "register a SiftItemProvider service whose provider ID matches integrations.items.provider and whose supports(id) returns true for every missing ID";
            case "SiftBlockProvider" -> "register a SiftBlockProvider service whose provider ID matches integrations.blocks.provider and whose supports(id) returns true for every missing ID";
            case "SiftMobProvider" -> "register a SiftMobProvider service whose provider ID matches integrations.mobs.provider and whose supports(id) returns true for every missing ID";
            default -> "install and enable " + backend + "; verify the generated definition path in integrations/README.md and reload that provider";
        };
    }

    private boolean mobResolves(String backend, String id) {
        if (backend.equals("SiftMobProvider")) {
            return plugin.getServer().getServicesManager().getRegistrations(SiftMobProvider.class).stream()
                    .map(RegisteredServiceProvider::getProvider).anyMatch(p -> p != null
                            && (configured("integrations.mobs.provider").equals("auto")
                                || safe(p.getProviderId(), configured("integrations.mobs.provider")))
                            && p.supports(id));
        }
        if (backend.equals("ItemsAdder")) return itemsAdderEntityResolves(id);
        if (backend.equals("MythicMobs")) return invokeStatic("io.lumine.mythic.bukkit.MythicBukkit", "inst") != null
                && invokeMobRegistry(id);
        // The bridge can spawn a configured EliteMobs boss, but no verified side-effect-free
        // registry probe is available here. Do not treat a config string as a resolved ID.
        return false;
    }

    private boolean itemsAdderEntityResolves(String id) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) return false;
        String entityId = plugin.getConfig().getString(
                "integrations.mobs.itemsadder.entities." + id, "thesift:" + id).trim();
        try {
            Class<?> customEntity = Class.forName("dev.lone.itemsadder.api.CustomEntity");
            return Boolean.TRUE.equals(customEntity.getMethod("isInRegistry", String.class)
                    .invoke(null, entityId));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    private boolean invokeMobRegistry(String id) {
        try {
            Object api = invokeStatic("io.lumine.mythic.bukkit.MythicBukkit", "inst");
            Object manager = api.getClass().getMethod("getMobManager").invoke(api);
            Object found = manager.getClass().getMethod("getMythicMob", String.class).invoke(manager, id);
            return found != null && (!(found instanceof java.util.Optional<?> o) || o.isPresent());
        } catch (ReflectiveOperationException | LinkageError e) { return false; }
    }

    private Object invokeStatic(String className, String method) {
        try { return Class.forName(className).getMethod(method).invoke(null); }
        catch (ReflectiveOperationException | LinkageError e) { return null; }
    }

    private boolean hasService(String backend) {
        return switch (backend) {
            case "SiftItemProvider" -> plugin.getServer().getServicesManager().getRegistrations(SiftItemProvider.class).stream().anyMatch(r -> r.getProvider() != null);
            case "SiftBlockProvider" -> plugin.getServer().getServicesManager().getRegistrations(SiftBlockProvider.class).stream().anyMatch(r -> r.getProvider() != null);
            default -> plugin.getServer().getServicesManager().getRegistrations(SiftMobProvider.class).stream().anyMatch(r -> r.getProvider() != null);
        };
    }
    private boolean installed(String name) { return isService(name) ? hasService(name) : plugin.getServer().getPluginManager().isPluginEnabled(name); }
    private boolean isService(String name) { return name.startsWith("Sift"); }
    private boolean selected(String configured, String backend) {
        return configured.equals("auto") || configured.equals(backend.toLowerCase(Locale.ROOT));
    }
    private <T> boolean serviceSelected(String configured, Class<T> type) {
        if (configured.equals("auto")) return true;
        boolean registered = plugin.getServer().getServicesManager().getRegistrations(type).stream()
                .map(RegisteredServiceProvider::getProvider).anyMatch(provider -> provider != null
                        && configured.equalsIgnoreCase(serviceId(provider)));
        return registered || configured.equals(type.getSimpleName().toLowerCase(Locale.ROOT))
                || !List.of("vanilla", "nexo", "itemsadder", "oraxen", "mythicmobs", "elitemobs",
                "siftitemprovider", "siftblockprovider", "siftmobprovider").contains(configured);
    }
    private String serviceId(Object provider) {
        try { return String.valueOf(provider.getClass().getMethod("getProviderId").invoke(provider)); }
        catch (ReflectiveOperationException exception) { return ""; }
    }
    private String configured(String path) { return plugin.getConfig().getString(path, "auto").toLowerCase(Locale.ROOT); }
    private boolean safe(String value, String wanted) { return value != null && value.equalsIgnoreCase(wanted); }

    record Backend(String name, boolean installed, List<String> missing) {
        boolean complete() { return installed && missing.isEmpty(); }
    }
}
