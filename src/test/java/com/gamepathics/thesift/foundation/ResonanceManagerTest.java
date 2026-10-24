package com.gamepathics.thesift.foundation;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ResonanceManagerTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private ResonanceManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        plugin.getConfig().set("resonance.singer.pulse-seconds", 1);
        manager = new ResonanceManager(plugin);
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void singerPulseDealsConfiguredLowDamage() throws Exception {
        Player player = playerNearSinger();

        pulse();

        assertEquals(19.0, player.getHealth(), 0.01);
    }

    @Test
    void singerPulseAppliesConfiguredSlowness() throws Exception {
        Player player = playerNearSinger();
        plugin.getConfig().set("resonance.singer.slowness-seconds", 3);
        plugin.getConfig().set("resonance.singer.slowness-amplifier", 0);

        pulse();

        var effect = player.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
        assertNotNull(effect);
        assertEquals(0, effect.getAmplifier());
        assertTrue(effect.getDuration() > 0 && effect.getDuration() <= 60);
    }

    @Test
    void taggedCharoiteInPersonalInventoryImmunizesFromDamageAndSlowness() throws Exception {
        Player player = playerNearSinger();
        player.getInventory().setItem(0,
                new SiftiteContent(plugin).item(Material.AMETHYST_SHARD, "charoite", "Charoite"));

        pulse();

        assertEquals(20.0, player.getHealth(), 0.01);
        assertNull(player.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS));
    }

    @Test
    void untaggedAmethystShardDoesNotCountAsCharoite() throws Exception {
        Player player = playerNearSinger();
        player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.AMETHYST_SHARD));

        pulse();

        assertEquals(19.0, player.getHealth(), 0.01);
        assertNotNull(player.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS));
    }

    @Test
    void creativePlayersAreNotAffected() throws Exception {
        Player player = playerNearSinger();
        player.setGameMode(GameMode.CREATIVE);

        pulse();

        assertUnaffected(player);
    }

    @Test
    void spectatorPlayersAreNotAffected() throws Exception {
        Player player = playerNearSinger();
        player.setGameMode(GameMode.SPECTATOR);

        pulse();

        assertUnaffected(player);
    }

    @Test
    void creativePlayerDoesNotPreventNearbySurvivalPlayerFromBeingAffected() throws Exception {
        Player creative = server.addPlayer();
        creative.setGameMode(GameMode.CREATIVE);
        Player survival = server.addPlayer();
        Allay singer = creative.getWorld().spawn(creative.getLocation().clone().add(2, 0, 0), Allay.class);
        singer.getPersistentDataContainer().set(new NamespacedKey(plugin, "resonance_entity"),
                PersistentDataType.STRING, "singer");

        pulse();

        assertUnaffected(creative);
        assertEquals(19.0, survival.getHealth(), 0.01);
        assertNotNull(survival.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS));
    }

    @Test
    void attackCanBeDisabledWithoutDisablingCosmeticPulse() throws Exception {
        Player player = playerNearSinger();
        plugin.getConfig().set("resonance.singer.attack-enabled", false);

        pulse();

        assertUnaffected(player);
    }

    private Player playerNearSinger() {
        Player player = server.addPlayer();
        Location location = player.getLocation().clone().add(2, 0, 0);
        Allay singer = player.getWorld().spawn(location, Allay.class);
        singer.getPersistentDataContainer().set(new NamespacedKey(plugin, "resonance_entity"),
                PersistentDataType.STRING, "singer");
        return player;
    }

    private void pulse() throws Exception {
        Method pulse = ResonanceManager.class.getDeclaredMethod("pulse");
        pulse.setAccessible(true);
        pulse.invoke(manager);
    }

    private void assertUnaffected(Player player) {
        assertEquals(20.0, player.getHealth(), 0.01);
        assertNull(player.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS));
    }
}
