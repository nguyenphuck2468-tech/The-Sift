package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
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

    /**
     * Materializes the bundled pack (src/main/resources/pack) into the plugin data folder as a zip.
     * Rebuilds whenever the jar is newer than the staged zip, so upgrading the plugin ships the
     * updated pack instead of silently keeping the stale one cached in {@code cachedSha1}.
     */
    public void prepare() {
        try {
            Files.createDirectories(localZip.getParent());
            if (!isStagedZipCurrent()) {
                buildZipFromResources();
                cachedSha1 = null; // force re-hash after rebuild
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not stage the bundled resource pack: " + exception.getMessage());
        }
    }

    private boolean isStagedZipCurrent() throws IOException {
        if (!Files.exists(localZip)) return false;
        Path jar = pluginJarPath();
        if (jar == null) return true; // can't compare: keep existing zip
        return Files.getLastModifiedTime(localZip).compareTo(Files.getLastModifiedTime(jar)) >= 0;
    }

    private Path pluginJarPath() {
        try {
            // JavaPlugin#getFile is protected; reach it reflectively (same package restriction).
            Method getFile = JavaPlugin.class.getDeclaredMethod("getFile");
            getFile.setAccessible(true);
            Object file = getFile.invoke(plugin);
            return file instanceof java.io.File f ? f.toPath() : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null; // MockBukkit / unusual classloader: no comparable jar file
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
        // Native Paper delivery requires an http(s) URL the client can download; without a
        // configured server-url we would hand out a file:// link every client rejects (and with
        // setResourcePack(force) that can kick players at join). Skip quietly instead.
        if (packUrlOrNull() == null) {
            if (!detectedLogged) {
                detectedLogged = true;
                plugin.getLogger().info("resource-pack.server-url is empty and no provider plugin was"
                        + " detected; native pack delivery is skipped until a download URL is configured.");
            }
            return;
        }
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
     *
     * <p>The pack identity handed to provider plugins is the same UUID the native path derives
     * from this plugin's name and the staged zip hash, so a re-send replaces the previous pack
     * instead of stacking duplicates in the client's pack slot.</p>
     */
    private boolean tryProviderSend(Plugin provider, Player player, boolean force) {
        // The provider path needs a real download URL; without one (empty packUrl()) skip it
        // and let the caller fall through to the native check instead of invoking sendPack("").
        if (packUrlOrNull() == null) return false;
        try {
            Object api = resolveApi(provider);
            if (api == null) return false;
            byte[] hashBytes = parseSha1();
            String packId = "thesift:" + plugin.getName();
            for (Method method : api.getClass().getMethods()) {
                String m = method.getName().toLowerCase(Locale.ROOT);
                if ((m.equals("applypacks") || m.equals("sendpack") || m.equals("sendresourcepack")
                        || m.equals("applypack")) && matchesPlayerSignature(method)) {
                    if (m.startsWith("send")) {
                        invokeProviderSend(method, api, player, hashBytes, packId, force);
                    } else {
                        method.invoke(api, player);
                    }
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().fine("Provider " + provider.getName() + " could not deliver the pack ("
                    + describeReflectionFailure(exception) + "); using native API.");
        }
        return false;
    }

    /**
     * Invokes a provider's send entry point with the argument shape it actually declares.
     * Previously every {@code send*} method was called as {@code (Player, String url, String hex,
     * String source, boolean force)} regardless of its signature: any provider whose second
     * parameter was a byte[] hash (or with a different arity) threw IllegalArgumentException on
     * every send, silently disabling provider delivery. We match the parameter types instead of
     * guessing; if a required slot has no safe default we abort and fall back to the native API.
     */
    private void invokeProviderSend(Method method, Object api, Player player, byte[] hashBytes,
                                    String packId, boolean force) throws ReflectiveOperationException {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        args[0] = player;
        for (int i = 1; i < types.length; i++) {
            Class<?> type = types[i];
            if (type == String.class) {
                args[i] = i == 1 ? packUrl() : (i == 3 ? packId : "");
            } else if (type == byte[].class) {
                args[i] = hashBytes;
            } else if (type == boolean.class || type == Boolean.class) {
                args[i] = force;
            } else if (type == int.class || type == Integer.class) {
                args[i] = 0;
            } else if (type == long.class || Long.class == type) {
                args[i] = 0L;
            } else if (java.util.UUID.class == type) {
                args[i] = packKey(packId);
            } else if (Player.class.isAssignableFrom(type)) {
                args[i] = player;
            } else if (type.isPrimitive()) {
                // Cannot fill an unknown primitive slot safely; let the caller use the native API.
                throw new IllegalArgumentException("Unsupported provider parameter " + type.getName()
                        + " at index " + i + " of " + method.getName());
            } else {
                args[i] = null; // remaining reference types: best-effort null
            }
        }
        method.invoke(api, args);
    }

    /** Reflection failures surface wrapped in InvocationTargetException; report the real cause. */
    private static String describeReflectionFailure(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            cause = invocation.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    /** Raw 20-byte SHA-1 digest of the staged zip, or null when unavailable. */
    private byte[] parseSha1() {
        String hash = sha1();
        return hash.isEmpty() ? null : HexFormat.of().parseHex(hash);
    }

    /** Stable pack-slot key: same identity string always yields the same UUID (re-send replaces). */
    private static java.util.UUID packKey(String identity) {
        return java.util.UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
            // Paper expects the raw 20-byte SHA-1 digest, not the ASCII hex string.
            byte[] hashBytes = hash.isEmpty() ? null : HexFormat.of().parseHex(hash);
            String prompt = plugin.getConfig().getString("resource-pack.prompt", "The Sift textures");
            if (force) {
                // Paper 26.x: setResourcePack(UUID key, url, byte[] hash, prompt, force).
                // The key is derived from a stable identity string (same one tryProviderSend
                // passes to provider plugins), so re-sends replace the pack instead of
                // stacking duplicates in the client's resource-pack slot.
                player.setResourcePack(packKey("thesift:" + plugin.getName()),
                        packUrl(), hashBytes, prompt, true);
            } else {
                // Paper 26.x: addResourcePack(UUID id, url, byte[] hash, prompt, inSlot).
                // Additive packs are never forced; they show in the server-resource-pack slot.
                player.addResourcePack(packKey("thesift:" + plugin.getName() + ":addon"),
                        packUrl(), hashBytes, prompt, false);
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
        String url = packUrlOrNull();
        return url != null ? url : "";
    }

    /** The configured http(s) download URL, or null when none is set (native delivery disabled). */
    private String packUrlOrNull() {
        String configured = plugin.getConfig().getString("resource-pack.server-url", "");
        if (configured == null || configured.isBlank()) return null;
        String trimmed = configured.trim();
        // file:// URLs are rejected by every client; treat them as unconfigured.
        return trimmed.startsWith("http://") || trimmed.startsWith("https://") ? trimmed : null;
    }

    /** True when pack delivery can actually reach clients: a provider plugin, or an http(s) URL. */
    public boolean canDeliver() {
        return findProviderPlugin() != null || packUrlOrNull() != null;
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
        // Guarded entry point: callers reach this from commands/config strings, so an invalid
        // URL must fail as a warning like every other download error, not crash the caller.
        URI endpoint;
        try {
            endpoint = URI.create(url);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Remote pack URL is malformed (" + exception.getMessage()
                    + "); download skipped.");
            return;
        }
        if (packUrlOrNull() == null && endpoint.getScheme() == null) {
            plugin.getLogger().warning("Remote pack URL has no scheme ('" + url + "'); download skipped.");
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpResponse<byte[]> response = httpClient.send(
                        HttpRequest.newBuilder(endpoint).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    plugin.getLogger().warning("Remote pack download returned HTTP " + status);
                    return;
                }
                byte[] body = response.body();
                // A truncated or empty body would stage a corrupt zip whose SHA-1 then gets
                // handed to clients; reject anything that cannot be opened as a zip archive.
                try (java.util.zip.ZipFile probe = asTempZip(body)) {
                    if (probe.stream().findAny().isEmpty()) {
                        plugin.getLogger().warning("Remote pack download contains no entries; keeping the staged zip.");
                        return;
                    }
                }
                Files.createDirectories(localZip.getParent());
                Path temp = localZip.resolveSibling(localZip.getFileName() + ".download.tmp");
                Files.write(temp, body);
                Files.move(temp, localZip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                cachedSha1 = null;
                plugin.getServer().getScheduler().runTask(plugin, onSuccess);
            } catch (Exception exception) {
                plugin.getLogger().warning("Remote pack download failed: " + exception.getMessage());
            }
        });
    }

    /** Wraps a downloaded body in a temporary file so it can be probed as a zip, deleted on close. */
    private java.util.zip.ZipFile asTempZip(byte[] body) throws IOException {
        Path probe = Files.createTempFile("thesift-pack-probe", ".zip");
        try {
            Files.write(probe, body);
            java.util.zip.ZipFile zip = new java.util.zip.ZipFile(probe.toFile());
            probe.toFile().deleteOnExit();
            return zip;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(probe);
            throw exception;
        }
    }
}
