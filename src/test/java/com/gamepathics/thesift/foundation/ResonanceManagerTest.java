package com.gamepathics.thesift.foundation;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResonanceManagerTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void singerPulseDoesNotDamageNearbyPlayer() throws Exception {
        var plugin = MockBukkit.createMockPlugin("TheSift");
        Player player = server.addPlayer();
        Location singerLocation = player.getLocation().clone().add(2, 0, 0);
        ResonanceManager manager = new ResonanceManager(plugin);
        Allay singer = player.getWorld().spawn(singerLocation, Allay.class);
        singer.getPersistentDataContainer().set(new NamespacedKey(plugin, "resonance_entity"),
                PersistentDataType.STRING, "singer");
        double healthBefore = player.getHealth();

        Method pulse = ResonanceManager.class.getDeclaredMethod("pulse");
        pulse.setAccessible(true);
        for (int i = 0; i < 5; i++) pulse.invoke(manager);

        assertEquals(healthBefore, player.getHealth());
        manager.shutdown();
    }
}
