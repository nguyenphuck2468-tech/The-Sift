package com.gamepathics.thesift.foundation;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Persistent world-level tags for blocks that own the Sift note interaction. */
final class SonorousBlockRegistry {
    private final NamespacedKey key;

    SonorousBlockRegistry(JavaPlugin plugin) {
        key = new NamespacedKey(plugin, "sonorous_deepslate_positions");
    }

    boolean contains(Block block) {
        String encoded = block.getWorld().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) return false;
        String position = encode(block);
        for (String value : encoded.split(";")) if (value.equals(position)) return true;
        return false;
    }

    void mark(Block block) {
        var container = block.getWorld().getPersistentDataContainer();
        String encoded = container.get(key, PersistentDataType.STRING);
        String position = encode(block);
        if (encoded == null || encoded.isBlank()) container.set(key, PersistentDataType.STRING, position);
        else if (!contains(block)) container.set(key, PersistentDataType.STRING, encoded + ";" + position);
    }

    private String encode(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
