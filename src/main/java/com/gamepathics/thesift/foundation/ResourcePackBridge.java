package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Bridges the bundled The Sift resource pack to clients through an external resource-pack
 * provider plugin (ResourcePackManager and similar) when one is installed, and falls back to
 * Bukkit's native player resource-pack API otherwise.
 *
 * <p>Integration with third-party plugins is done reflectively so this plugin never hard-depends
 * on a specific provider: we look up the provider plugin by name, resolve its public API from
 * the loaded jar (or its shaded relocation), and invoke it. Every failure path degrades to the
 * vanilla {@link Player#setResourcePack} API instead of breaking the server.</p>
 */
public final class ResourcePackBridge {
    /** Substrings matched (case-insensitive) against loaded plugin names to find a provider. */
    private static final List<String> PROVIDER_NAMES = List.of("resourcepackmanager", "multiverseresourcepacks");

    private final JavaPlugin plugin;
    private final Path localZip;
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile String cachedSha1;
    private volatile boolean detectedLogged;

    public ResourcePackBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.localZip = plugin.getDataFolder().toPath().resolve("pack").resolve("thesift-resource-pack.zip");
    }

    /** Materializes the bundled pack (src/main/resources/pack) into the plugin data folder as a zip. */
    public void prepare() {
        try {
            Files.createDirectories(localZip.getParent());
            if (!Files.exists(localZip)) buildZipFromResources();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not stage the bundled resource pack: " + exception.getMessage());
        }
    }

    /**
     * Sends the pack to every online player (join-time hook lives in the plugin bootstrap).
     * Prefers the detected provider plugin; falls back to the native Bukkit API.
     */
    public void broadcastToPlayers(CommandSender origin, boolean force) {
        prepareIfNeeded();
        Plugin provider = findProviderPlugin();
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendTo(player, provider, force);
        }
        if (origin != null) {
            origin.sendMessage("The Sift resource pack sent to " + Bukkit.getOnlinePlayers().size()
                    + " player(s) via " + (provider == null ? "native Bukkit API" : provider.getName()) + ".");
        }
    }

    public void sendTo(Player player, boolean force) {
        prepareIfNeeded();
        sendTo(player, findProviderPlugin(), force);
    }

    private void sendTo(Player player, Plugin provider, boolean force) {
        if (provider != null && tryProviderSend(provider, player, force)) return;
        tryNativeSend(player, force);
    }

    // ------------------------------------------------------------------ provider probing

    private Plugin findProviderPlugin() {
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            String name = candidate.getName().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            for (String wanted : PROVIDER_NAMES) {
                if (name.contains(wanted)) {
                    if (!detectedLogged) {
                        detectedLogged = true;
                        plugin.getLogger().info("Resource pack provider detected: " + candidate.getName()
                                + "; delegating pack delivery to it.");
                    }
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Reflective integration: look for common public entry points used by resource-pack manager
     * style plugins ({@code applyPacks(Player)}, {@code sendPack(Player, ...)}, or a singleton
     * {@code getInstance()}). If nothing matches, return false so the caller uses the native API.
     */
    private boolean tryProviderSend(Plugin provider, Player player, boolean force) {
        try {
            Object api = resolveApi(provider);
            if (api == null) return false;
            for (Method method : api.getClass().getMethods()) {
                String m = method.getName().toLowerCase(Locale.ROOT);
                if ((m.equals("applypacks") || m.equals("sendpack") || m.equals("sendresourcepack")
                        || m.equals("applypack")) && matchesPlayerSignature(method)) {
                    if (m.startsWith("send")) method.invoke(api, player, packUrl(), sha1(), plugin.getName(), force);
                    else method.invoke(api, player);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().fine("Provider " + provider.getName() + " could not deliver the pack ("
                    + exception.getClass().getSimpleName() + "); using native API.");
        }
        return false;
    }

    private boolean matchesPlayerSignature(Method method) {
        Class<?>[] types = method.getParameterTypes();
        return types.length >= 1 && types[0].isAssignableFrom(Player.class);
    }

    private Object resolveApi(Plugin provider) throws ReflectiveOperationException {
        for (Method method : provider.getClass().getMethods()) {
            if (method.getName().equals("getInstance") && method.getParameterCount() == 0
                    && method.getReturnType().isInstance(provider)) {
                return method.invoke(provider);
            }
        }
        return provider;
    }

    // ------------------------------------------------------------------ native fallback

    private void tryNativeSend(Player player, boolean force) {
        try {
            String hash = sha1();
            String prompt = plugin.getConfig().getString("resource-pack.prompt", "The Sift textures");
            if (force) {
                // Paper 26.x: setResourcePack(UUID key, url, byte[] hash, prompt, force).
                player.setResourcePack(java.util.UUID.nameUUIDFromBytes(
                                ("thesift:" + hash).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        packUrl(), hash.getBytes(java.nio.charset.StandardCharsets.UTF_8), prompt, true);
            } else {
                // Paper 26.x: addResourcePack(UUID id, url, byte[] hash, prompt, inSlot).
                player.addResourcePack(java.util.UUID.nameUUIDFromBytes(
                                ("thesift:addon").getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        packUrl(), hash.getBytes(java.nio.charset.StandardCharsets.UTF_8), prompt, true);
            }
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Native resource pack send failed for " + player.getName() + ": "
                    + exception.getMessage());
        }
    }

    private String title() {
        return plugin.getConfig().getString("resource-pack.title", "The Sift (fan-made)");
    }

    /**
     * Delivery URL for the staged zip. Paper only accepts http(s) URLs for player packs, so the
     * bridge checks {@code resource-pack.server-url} first; without it, players still receive the
     * pack through a detected provider plugin that reads local files.
     */
    private String packUrl() {
        String configured = plugin.getConfig().getString("resource-pack.server-url", "");
        if (configured != null && !configured.isBlank()) return configured.trim();
        return "file://" + localZip.toAbsolutePath();
    }

    // ------------------------------------------------------------------ hashing / staging

    public String sha1() {
        if (cachedSha1 != null) return cachedSha1;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream stream = Files.newInputStream(localZip)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) > 0) digest.update(buffer, 0, read);
            }
            cachedSha1 = HexFormat.of().formatHex(digest.digest());
            return cachedSha1;
        } catch (IOException | NoSuchAlgorithmException exception) {
            plugin.getLogger().warning("Could not hash the bundled resource pack: " + exception.getMessage());
            return "";
        }
    }

    private void prepareIfNeeded() {
        if (!Files.exists(localZip)) prepare();
    }

    /** Zips everything under the bundled {@code /pack} resource folder into the plugin data folder. */
    private void buildZipFromResources() throws IOException {
        var url = getClass().getResource("/pack/pack.mcmeta");
        if (url == null) throw new IOException("Bundled pack directory not found in the plugin jar");
        String external = url.toString();
        if (external.startsWith("jar:")) {
            // Running from a jar: copy every entry under pack/ out of the plugin jar itself.
            String jarPath = external.substring(0, external.indexOf("!/"));
            try (var zipSource = java.nio.file.FileSystems.newFileSystem(java.net.URI.create(jarPath), java.util.Map.of());
                 var zipStream = new java.util.zip.ZipOutputStream(Files.newOutputStream(localZip))) {
                Path packRoot = zipSource.getPath("pack");
                try (var walk = Files.walk(packRoot)) {
                    for (Path entry : walk.filter(Files::isRegularFile).toList()) {
                        String name = packRoot.relativize(entry).toString().replace('\\', '/');
                        zipStream.putNextEntry(new java.util.zip.ZipEntry(name));
                        Files.copy(entry, zipStream);
                        zipStream.closeEntry();
                    }
                }
            }
            return;
        }
        Path base = Path.of(url.getPath()).getParent(); // .../pack/pack.mcmeta -> .../pack
        try (var zipStream = new java.util.zip.ZipOutputStream(Files.newOutputStream(localZip));
             var walk = Files.walk(base)) {
            for (Path entry : walk.filter(Files::isRegularFile).toList()) {
                String name = base.relativize(entry).toString().replace('\\', '/');
                zipStream.putNextEntry(new java.util.zip.ZipEntry(name));
                Files.copy(entry, zipStream);
                zipStream.closeEntry();
            }
        }
    }

    /** Downloads a remote pack variant (e.g. CDN mirror) asynchronously; result cached in data folder. */
    public void fetchRemote(String url, Runnable onSuccess) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpResponse<byte[]> response = httpClient.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    Files.createDirectories(localZip.getParent());
                    Files.write(localZip, response.body());
                    cachedSha1 = null;
                    plugin.getServer().getScheduler().runTask(plugin, onSuccess);
                } else {
                    plugin.getLogger().warning("Remote pack download returned HTTP " + response.statusCode());
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Remote pack download failed: " + exception.getMessage());
            }
        });
    }
}
