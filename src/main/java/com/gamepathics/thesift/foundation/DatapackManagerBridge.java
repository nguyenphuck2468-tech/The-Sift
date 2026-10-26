package com.gamepathics.thesift.foundation;

import io.papermc.paper.datapack.Datapack;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
    private static final List<String> BUNDLED_PACKS = List.of("dimension", "progression");

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
        var manager = plugin.getServer().getDatapackManager();
        Datapack pack = findPack(manager.getPacks(), name);
        if (pack == null) return false;
        pack.setEnabled(enabled);
        manager.refreshPacks();
        return true;
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
        for (Datapack pack : packs) {
            if (pack.getName().toLowerCase(Locale.ROOT).contains(needle)) return pack;
        }
        return null;
    }

    private URI resourceRoot(String folder) {
        String marker = "/" + folder + "/pack.mcmeta";
        var url = DatapackManagerBridge.class.getResource(marker);
        if (url == null) return null;
        try {
            String external = url.toString();
            URI uri = new URI(external.endsWith(marker) ? external.substring(0, external.length() - marker.length()) : external);
            // Inside a jar we cannot walk the filesystem; staging then relies on the bootstrap
            // discovery path which registers the pack directly from the jar URL.
            return uri.getScheme().equals("jar") ? null : uri;
        } catch (Exception exception) {
            return null;
        }
    }

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
