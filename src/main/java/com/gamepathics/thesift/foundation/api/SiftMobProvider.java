package com.gamepathics.thesift.foundation.api;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/** Bukkit service contract for custom mob plugins used by The Sift. */
public interface SiftMobProvider {
    /** Stable provider key used by integrations.mobs.provider. */
    String getProviderId();

    /** Spawn a custom creature for a The Sift ID, or return null if unsupported. */
    LivingEntity spawn(String creatureId, Location location);

    /** Read-only readiness probe; do not spawn entities from this method. */
    default boolean supports(String creatureId) { return false; }
}
