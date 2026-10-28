package com.gamepathics.thesift.foundation;

import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DarkSnifferDiggingGiftListenerRegistrationTest {
    private ServerMock server;
    private TheSiftFoundationPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(TheSiftFoundationPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void giftListenerIsRegisteredExactlyOnce() {
        List<RegisteredListener> listeners = Arrays.stream(server.getPluginManager().getRegisteredListeners(plugin))
                .filter(rl -> rl.getListener() instanceof DarkSnifferDiggingGiftListener)
                .toList();
        
        assertEquals(1, listeners.size(), "DarkSnifferDiggingGiftListener must be registered exactly once");
    }
}
