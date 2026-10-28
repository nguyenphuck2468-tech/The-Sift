package com.gamepathics.thesift.foundation;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DarkSnifferDiggingGiftListenerRegistrationTest {
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resourcePackListenerDoesNotRegisterGiftListener() {
        // SiftResourcePackListener must NOT register DarkSnifferDiggingGiftListener
        new SiftResourcePackListener(plugin, new ResourcePackBridge(plugin));
        
        boolean found = false;
        for (RegisteredListener rl : HandlerList.getRegisteredListeners(plugin)) {
            if (rl.getListener() instanceof DarkSnifferDiggingGiftListener) {
                found = true;
                break;
            }
        }
        assertFalse(found, "SiftResourcePackListener must not register DarkSnifferDiggingGiftListener");
    }
}
