package com.gamepathics.thesift.foundation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ContentRegistry {
    private final Map<ContentId, Boolean> enabled = new EnumMap<>(ContentId.class);

    public void setEnabled(ContentId id, boolean value) {
        enabled.put(Objects.requireNonNull(id), value);
    }

    public boolean isEnabled(ContentId id) {
        return enabled.getOrDefault(Objects.requireNonNull(id), false);
    }

    public Map<ContentId, Boolean> snapshot() {
        return Map.copyOf(enabled);
    }
}
