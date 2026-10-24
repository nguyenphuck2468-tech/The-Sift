package dev.lone.itemsadder.api;

import org.bukkit.block.Block;

/** Test fixture for the small reflective surface used from ItemsAdder's public API. */
public final class CustomBlock {
    private static String placedId;

    private final String namespacedId;

    private CustomBlock(String namespacedId) {
        this.namespacedId = namespacedId;
    }

    public static CustomBlock byAlreadyPlaced(Block ignored) {
        return placedId == null ? null : new CustomBlock(placedId);
    }

    public String getNamespacedID() {
        return namespacedId;
    }

    public static void setPlacedIdForTest(String id) {
        placedId = id;
    }
}
