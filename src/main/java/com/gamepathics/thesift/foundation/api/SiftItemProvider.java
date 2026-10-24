package com.gamepathics.thesift.foundation.api;

import org.bukkit.inventory.ItemStack;

/**
 * Bukkit service contract for custom-item plugins that The Sift does not
 * integrate directly. Register an implementation with ServicesManager.
 */
public interface SiftItemProvider {
    /** Stable provider key used by integrations.items.provider. */
    String getProviderId();

    /** Return the custom item for a The Sift content ID, or null if unsupported. */
    ItemStack createItem(String contentId);

    /** Read-only readiness probe; implementations should return false for IDs they do not own. */
    default boolean supports(String contentId) { return false; }
}
