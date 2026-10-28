package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.bukkit.event.HandlerList;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkSnifferDiggingGiftListenerRegistrationTest {
    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registersGiftListenerWhenPluginEnables() {
        TheSiftFoundationPlugin plugin = MockBukkit.load(TheSiftFoundationPlugin.class);

        assertTrue(
                HandlerList.getRegisteredListeners(plugin).stream()
                        .anyMatch(registered -> registered.getListener() instanceof DarkSnifferDiggingGiftListener),
                "Dark Sniffer gift listener must be registered independently of resource-pack handling");
    }
}
