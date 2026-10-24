package com.gamepathics.thesift.foundation.api;

import org.bukkit.Location;

/** Adapter contract for placing a provider-owned custom block during Sift world generation. */
public interface SiftBlockProvider {
    String getProviderId();

    /** Places the configured block at the location, returning true when placement succeeded. */
    boolean placeBlock(String contentId, Location location);
}
