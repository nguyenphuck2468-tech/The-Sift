package com.gamepathics.thesift.foundation;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Chunk-local persistent index of placed Sonorous Deepslate blocks. */
final class SonorousBlockRegistry {
    private static final Map<UUID, LegacyIndex> LEGACY_INDICES = new HashMap<>();

    private final NamespacedKey chunkKey;
    private final NamespacedKey legacyWorldKey;
    private final ExternalBlockBridge blockBridge;

    SonorousBlockRegistry(JavaPlugin plugin) {
        chunkKey = new NamespacedKey(plugin, "sonorous_deepslate_positions_v2");
        legacyWorldKey = new NamespacedKey(plugin, "sonorous_deepslate_positions");
        blockBridge = new ExternalBlockBridge(plugin);
    }

    boolean contains(Block block) {
        if (block == null) return false;
        Chunk chunk = block.getChunk();
        migrateLegacyChunk(chunk);
        return containsPosition(chunk.getPersistentDataContainer().get(chunkKey,
                PersistentDataType.INTEGER_ARRAY), block);
    }

    void mark(Block block) {
        if (block == null) return;
        Chunk chunk = block.getChunk();
        migrateLegacyChunk(chunk);
        var pdc = chunk.getPersistentDataContainer();
        int[] current = pdc.get(chunkKey, PersistentDataType.INTEGER_ARRAY);
        if (containsPosition(current, block)) return;
        int length = current == null ? 0 : current.length;
        int[] updated = Arrays.copyOf(current == null ? new int[0] : current, length + 3);
        updated[length] = block.getX() & 15;
        updated[length + 1] = block.getY();
        updated[length + 2] = block.getZ() & 15;
        pdc.set(chunkKey, PersistentDataType.INTEGER_ARRAY, updated);
    }

    void remove(Block block) {
        if (block == null) return;
        Chunk chunk = block.getChunk();
        migrateLegacyChunk(chunk);
        var pdc = chunk.getPersistentDataContainer();
        int[] current = pdc.get(chunkKey, PersistentDataType.INTEGER_ARRAY);
        if (current == null) return;
        int localX = block.getX() & 15;
        int localZ = block.getZ() & 15;
        List<Integer> remaining = new ArrayList<>(current.length);
        for (int i = 0; i + 2 < current.length; i += 3) {
            if (current[i] == localX && current[i + 1] == block.getY() && current[i + 2] == localZ) continue;
            remaining.add(current[i]);
            remaining.add(current[i + 1]);
            remaining.add(current[i + 2]);
        }
        if (remaining.isEmpty()) pdc.remove(chunkKey);
        else pdc.set(chunkKey, PersistentDataType.INTEGER_ARRAY,
                remaining.stream().mapToInt(Integer::intValue).toArray());
    }

    private boolean containsPosition(int[] positions, Block block) {
        if (positions == null) return false;
        int x = block.getX() & 15;
        int z = block.getZ() & 15;
        for (int i = 0; i + 2 < positions.length; i += 3) {
            if (positions[i] == x && positions[i + 1] == block.getY() && positions[i + 2] == z) return true;
        }
        return false;
    }

    /**
     * The old world-level index could contain every generated vanilla deepslate
     * as well as manually placed Siftslate. Convert only legacy entries that a
     * provider can still identify as a Sift block; ambiguous vanilla entries
     * are deliberately discarded to avoid turning ordinary deepslate into a portal.
     */
    private void migrateLegacyChunk(Chunk chunk) {
        World world = chunk.getWorld();
        UUID worldId = world.getUID();
        LegacyIndex index;
        synchronized (LEGACY_INDICES) {
            index = LEGACY_INDICES.computeIfAbsent(worldId, ignored -> readLegacy(world));
        }
        long chunkId = chunkId(chunk.getX(), chunk.getZ());
        List<LegacyPosition> positions;
        synchronized (index) {
            positions = index.chunks.remove(chunkId);
        }
        if (positions != null) {
            for (LegacyPosition position : positions) {
                Block oldBlock = world.getBlockAt(position.x, position.y, position.z);
                if (blockBridge.isBlock("sonorous_deepslate", oldBlock)
                        || blockBridge.isBlock("siftslate", oldBlock)) markWithoutMigration(oldBlock);
            }
        }
        boolean complete;
        synchronized (index) {
            complete = index.chunks.isEmpty();
        }
        if (complete) {
            world.getPersistentDataContainer().remove(legacyWorldKey);
            synchronized (LEGACY_INDICES) {
                LEGACY_INDICES.remove(worldId, index);
            }
        }
    }

    private void markWithoutMigration(Block block) {
        Chunk chunk = block.getChunk();
        var pdc = chunk.getPersistentDataContainer();
        int[] current = pdc.get(chunkKey, PersistentDataType.INTEGER_ARRAY);
        if (containsPosition(current, block)) return;
        int length = current == null ? 0 : current.length;
        int[] updated = Arrays.copyOf(current == null ? new int[0] : current, length + 3);
        updated[length] = block.getX() & 15;
        updated[length + 1] = block.getY();
        updated[length + 2] = block.getZ() & 15;
        pdc.set(chunkKey, PersistentDataType.INTEGER_ARRAY, updated);
    }

    private LegacyIndex readLegacy(World world) {
        String encoded = world.getPersistentDataContainer().get(legacyWorldKey, PersistentDataType.STRING);
        Map<Long, List<LegacyPosition>> chunks = new HashMap<>();
        if (encoded != null && !encoded.isBlank()) {
            for (String token : encoded.split(";")) {
                String[] coords = token.split(",", -1);
                if (coords.length != 3) continue;
                try {
                    int x = Integer.parseInt(coords[0]);
                    int y = Integer.parseInt(coords[1]);
                    int z = Integer.parseInt(coords[2]);
                    chunks.computeIfAbsent(chunkId(x >> 4, z >> 4), ignored -> new ArrayList<>())
                            .add(new LegacyPosition(x, y, z));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed legacy tokens; valid entries still migrate.
                }
            }
        }
        return new LegacyIndex(chunks);
    }

    private static long chunkId(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private record LegacyPosition(int x, int y, int z) {}
    private static final class LegacyIndex {
        private final Map<Long, List<LegacyPosition>> chunks;
        private LegacyIndex(Map<Long, List<LegacyPosition>> chunks) { this.chunks = chunks; }
    }
}
