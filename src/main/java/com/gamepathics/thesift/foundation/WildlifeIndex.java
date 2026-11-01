package com.gamepathics.thesift.foundation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wildlife index extracted from SiftEcologyManager (Phase 4 step 1). Maps a species key (the
 * creature tag, or "type:&lt;VANILLA_TYPE&gt;" for untagged stand-ins) to the UUIDs currently
 * carrying it, so the periodic tick loops resolve a handful of entity lookups instead of
 * scanning every loaded entity.
 *
 * <p>Keying rule to remember: the add-to-world event fires while a freshly spawned creature's
 * PDC is still empty, so callers must {@link #refile} the UUID under the real tag once it is
 * written — the event never fires twice. Empty buckets are pruned eagerly to keep the map tight.
 */
final class WildlifeIndex {
    private final Map<String, Set<UUID>> buckets = new ConcurrentHashMap<>();

    void index(String key, UUID id) {
        if (key == null || id == null) return;
        buckets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    /** Removes a UUID from one bucket, pruning the bucket when it runs empty. */
    void unindex(String key, UUID id) {
        Set<UUID> bucket = buckets.get(key);
        if (bucket == null) return;
        bucket.remove(id);
        if (bucket.isEmpty()) buckets.remove(key);
    }

    /** Moves a UUID from one bucket to another (tag written after spawn). */
    void refile(String fromKey, String toKey, UUID id) {
        unindex(fromKey, id);
        if (toKey != null) index(toKey, id);
    }

    /** Drops a UUID from every bucket (entity gone without a known key). */
    void scrub(UUID id) {
        for (Map.Entry<String, Set<UUID>> entry : buckets.entrySet()) {
            if (entry.getValue().remove(id) && entry.getValue().isEmpty()) {
                buckets.remove(entry.getKey());
            }
        }
    }

    /** Snapshot of one bucket; empty when the species has no live entries. */
    List<UUID> bucket(String key) {
        return new ArrayList<>(buckets.getOrDefault(key, Set.of()));
    }

    Set<String> keys() {
        return new HashSet<>(buckets.keySet());
    }

    int size() {
        return buckets.values().stream().mapToInt(Set::size).sum();
    }

    void clear() {
        buckets.clear();
    }

    Map<String, Set<UUID>> snapshotForTests() {
        Map<String, Set<UUID>> copy = new HashMap<>();
        buckets.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return copy;
    }
}
