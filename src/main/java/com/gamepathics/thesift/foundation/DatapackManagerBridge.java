package com.gamepathics.thesift.foundation;

import io.papermc.paper.datapack.Datapack;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Runtime datapack management for the packs bundled inside the plugin jar.
 *
 * <p>The two source folders ({@code /datapack} for progression content and
 * {@code /sift_dimension_datapack} for worldgen) are discovered at bootstrap through the
 * Paper {@code DATAPACK_DISCOVERY} lifecycle event, but a running server can only toggle
 * filesystem directories via {@code /datapack enable|disable}. This bridge therefore stages
 * each bundled folder into {@code plugins/TheSift/provided/<name>} as a zip on first use, so
 * admins (and {@code /sift reload}) can enable, disable and re-apply the packs without a
 * restart.</p>
 */
public final class DatapackManagerBridge {
    // Folder names must match src/main/resources exactly: "datapack" is the
    // progression pack (advancements/loot tables), "sift_dimension_datapack"
    // carries the dimension/worldgen JSON. The previous "dimension"/"progression"
    // names never resolved, so staging silently did nothing.
    private static final List<String> BUNDLED_PACKS = List.of("sift_dimension_datapack", "datapack");

    private final JavaPlugin plugin;
    private final Path stageRoot;

    public DatapackManagerBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        this.stageRoot = plugin.getDataFolder().toPath().resolve("provided");
    }

    /** Extracts the bundled pack resources into the world datapack staging area. Idempotent. */
    public void stageBundledPacks() {
        for (String name : BUNDLED_PACKS) {
            URI root = resourceRoot(name);
            if (root == null) {
                plugin.getLogger().warning("Bundled datapack '" + name + "' not found in the plugin jar.");
                continue;
            }
            Path target = stageRoot.resolve(name + ".zip");
            try {
                Files.createDirectories(stageRoot);
                zipDirectory(Path.of(root), target);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not stage datapack '" + name + "': " + exception.getMessage());
            }
        }
    }

    /** Enables/disables a staged or discovered pack by name and refreshes the datapack manager. */
    public boolean setEnabled(String name, boolean enabled) {
        // Guard: disabling the dimension pack while the server runs can strand The Sift world
        // (missing dimension registration). The check tests whether the bundled dimension pack
        // itself is currently ENABLED - that is the real hazard. Enabling stays always-safe.
        if (!enabled && isDimensionPackName(name)) {
            var guardManager = plugin.getServer().getDatapackManager();
            boolean dimensionActive = findPack(guardManager.getPacks(), "sift_dimension_datapack") != null;
            if (dimensionActive) {
                plugin.getLogger().warning("Refusing to disable the dimension datapack ('" + name
                        + "') at runtime because it is currently enabled and The Sift world may"
                        + " depend on it. Unload/stop the server first, then edit the datapack"
                        + " configuration.");
                return false;
            }
        }
        var manager = plugin.getServer().getDatapackManager();
        Datapack pack = findPack(manager.getPacks(), name);
        if (pack == null) return false;
        pack.setEnabled(enabled);
        manager.refreshPacks();
        return true;
    }

    private static boolean isDimensionPackName(String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        return needle.equals("sift_dimension_datapack") || needle.equals("thesift:dimension")
                || needle.equals("dimension");
    }

    public String status() {
        var manager = plugin.getServer().getDatapackManager();
        StringBuilder builder = new StringBuilder("The Sift datapacks: ");
        List<Datapack> matching = manager.getPacks().stream()
                .filter(pack -> pack.getName().toLowerCase(Locale.ROOT).contains("thesift")
                        || pack.getName().toLowerCase(Locale.ROOT).contains("sift"))
                .toList();
        if (matching.isEmpty()) return "no The Sift datapack discovered yet (staged zips live in plugins/TheSift/provided)";
        boolean first = true;
        for (Datapack pack : matching) {
            if (!first) builder.append(", ");
            first = false;
            builder.append(pack.getName()).append('[').append(pack.isEnabled() ? "enabled" : "disabled").append(']');
        }
        return builder.toString();
    }

    public void report(CommandSender sender) {
        sender.sendMessage(status());
    }

    private static Datapack findPack(Collection<Datapack> packs, String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        // Exact match first: substring matching let setEnabled("datapack", ...) hit every pack
        // whose name contains "datapack", including other plugins' packs.
        for (Datapack pack : packs) {
            if (pack.getName().toLowerCase(Locale.ROOT).equals(needle)) return pack;
        }
        // Then namespace-qualified form ("thesift:<name>") and folder-name suffix "<name>.zip".
        for (Datapack pack : packs) {
            String lower = pack.getName().toLowerCase(Locale.ROOT);
            if (lower.equals("thesift:" + needle) || lower.equals(needle + ".zip")) return pack;
        }
        return null;
    }

    private URI resourceRoot(String folder) {
        String marker = "/" + folder + "/pack.mcmeta";
        var url = DatapackManagerBridge.class.getResource(marker);
        if (url == null) return null;
        try {
            String external = url.toString();
            String rootPart = external.endsWith(marker)
                    ? external.substring(0, external.length() - marker.length()) : external;
            // Inside a jar the root URL looks like "jar:file:/…!/datapack". Walk it with a
            // zip FileSystem instead of giving up: returning null here made staging a silent
            // no-op on production (jar) installs.
            if (rootPart.startsWith("jar:")) {
                String spec = rootPart.substring("jar:".length());
                int split = spec.indexOf("!/");
                String jarUri = split < 0 ? spec : spec.substring(0, split);
                String entryPath = split < 0 ? "" : spec.substring(split + 1);
                FileSystem fs = jarFilesystems.computeIfAbsent(URI.create(jarUri), uri -> {
                    try {
                        Map<String, String> env = new HashMap<>();
                        env.put("create", "false");
                        return FileSystems.newFileSystem(uri, env);
                    } catch (java.nio.file.FileSystemAlreadyExistsException alreadyOpen) {
                        // Another component (e.g. the resource-pack bridge or the plugin loader)
                        // already opened this jar; reuse that filesystem instead of silently
                        // returning null and turning staging into a no-op on production installs.
                        return FileSystems.getFileSystem(uri);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
                Path path = entryPath.isEmpty() ? fs.getPath("/") : fs.getPath(entryPath);
                return path.toUri();
            }
            return new URI(rootPart);
        } catch (Exception exception) {
            return null;
        }
    }

    /** Open jar filesystems kept alive so staged paths stay readable for the JVM lifetime. */
    private static final Map<URI, FileSystem> jarFilesystems = new java.util.concurrent.ConcurrentHashMap<>();

    private static void zipDirectory(Path base, Path output) throws IOException {
        Path temp = output.resolveSibling(output.getFileName() + ".tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp));
             var walk = Files.walk(base)) {
            for (Path entry : walk.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry(base.relativize(entry).toString().replace('\\', '/')));
                Files.copy(entry, zip);
                zip.closeEntry();
            }
        }
        Files.move(temp, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Reads a bundled resource fully (used by tests to verify pack contents ship in the jar). */
    public static byte[] readResource(String path) throws IOException {
        try (InputStream stream = DatapackManagerBridge.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource: " + path);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            stream.transferTo(buffer);
            return buffer.toByteArray();
        }
    }
}
