package com.gamepathics.thesift.foundation;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for SpearThrowHandler: covers durability, creative mode, the
 * single-drop guarantee and the content tag recognition.
 */
class SpearThrowHandlerTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private SpearThrowHandler handler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        handler = new SpearThrowHandler(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack createSpear() {
        ItemStack spear = new ItemStack(Material.NETHERITE_SWORD);
        var meta = spear.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "content"), PersistentDataType.STRING, "siftite_spear");
        spear.setItemMeta(meta);
        return spear;
    }

    @Test
    void isSpearRecognizesTaggedItems() {
        assertTrue(handler.isSpear(createSpear()));
        assertFalse(handler.isSpear(new ItemStack(Material.STICK)));
        assertFalse(handler.isSpear(null));
    }

    @Test
    void throwReducesDurabilityByOne() {
        PlayerMock player = server.addPlayer();
        ItemStack spear = createSpear();
        short before = spear.getDurability();

        handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);

        assertEquals((short)(before + 1), spear.getDurability(), "throw must cost exactly 1 durability");
    }

    @Test
    void creativePlayerKeepsTheirStack() {
        PlayerMock player = server.addPlayer();
        player.setGameMode(GameMode.CREATIVE);
        ItemStack spear = createSpear();
        short before = spear.getDurability();

        handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);

        assertEquals(before, spear.getDurability(), "creative player must not lose durability");
    }

    @Test
    void cooldownBlocksImmediateSecondThrow() {
        PlayerMock player = server.addPlayer();
        ItemStack spear = createSpear();

        var first = handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);
        assertNotNull(first, "first throw should succeed");
        var second = handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);
        assertNull(second, "second throw within cooldown must be blocked");
    }

    @Test
    void dropThrownCopyDropsExactlyOnce() {
        PlayerMock player = server.addPlayer();
        ItemStack spear = createSpear();
        var projectile = handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);
        assertNotNull(projectile);

        // Simulate two consecutive drop attempts (entity hit + despawn race)
        boolean first = handler.dropThrownCopy(plugin, projectile);
        boolean second = handler.dropThrownCopy(plugin, projectile);

        assertTrue(first, "first drop must succeed");
        assertFalse(second, "second drop must be blocked by the PDC flag");
    }

    @Test
    void brokenSpearNeverDrops() {
        PlayerMock player = server.addPlayer();
        ItemStack spear = createSpear();
        // Damage to near-breaking
        spear.setDurability((short)(spear.getType().getMaxDurability() - 1));

        var projectile = handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);
        assertNotNull(projectile, "throw should succeed even if the spear breaks");

        // A broken spear has the dropped flag pre-set — the copy must not drop
        assertFalse(handler.dropThrownCopy(plugin, projectile),
                "broken spear must never drop its copy");
    }
}
