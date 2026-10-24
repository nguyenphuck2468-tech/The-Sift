package com.gamepathics.thesift.foundation.api;

import org.bukkit.Location;
import org.bukkit.block.Block;

/** Adapter contract for placing a provider-owned custom block during Sift world generation. */
public interface SiftBlockProvider {
    String getProviderId();

    /** Places the configured block at the location, returning true when placement succeeded. */
    boolean placeBlock(String contentId, Location location);

    /** Read-only readiness probe for the stable The Sift content ID, not a provider-specific alias. */
    default boolean supports(String contentId) { return false; }

    /** Return true when the block at the location is the configured provider block for this ID. */
    default boolean isBlock(String contentId, Block block) {
        return false;
    }
}
