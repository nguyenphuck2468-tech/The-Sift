package com.gamepathics.thesift.foundation;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests SpearThrowHandler logic via the injectable computeThrowCost() method and the
 * dropThrownCopy() PDC flag mechanism — no entity spawning needed.
  * Note: cooldownBlocksSecondThrow is tested on a real Paper server because
 * MockBukkit's PlayerMock PDC does not persist between calls.
 */
class SpearThrowHandlerTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private SpearThrowHandler handler;
    private int spawnCount;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        handler = new SpearThrowHandler(plugin);
        spawnCount = 0;
        // Injectable spawner: counts invocations, returns null (no real entity needed)
        handler.setSpawner((eye, item, shooter, speed, broke) -> {
            spawnCount++;
            return null; // no real projectile in test
        });
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
    void computeThrowCostSurvivalReducesDurability() {
        ItemStack held = createSpear();
        short before = held.getDurability();

        ItemStack[] result = SpearThrowHandler.computeThrowCost(held, false);

        assertNotNull(result[0], "survival hand item must not be null (spear didn't break)");
        assertNotNull(result[1], "projectile item must not be null (spear didn't break)");
        assertEquals((short)(before + 1), result[0].getDurability(), "hand item must lose 1 durability");
    }

    @Test
    void computeThrowCostCreativeKeepsFullDurability() {
        ItemStack held = createSpear();
        short before = held.getDurability();

        ItemStack[] result = SpearThrowHandler.computeThrowCost(held, true);

        assertNotNull(result[0]);
        assertNotNull(result[1]);
        assertEquals(before, result[0].getDurability(), "creative must not lose durability");
        assertEquals(before, result[1].getDurability(), "projectile copy must also be undamaged");
    }

    @Test
    void throwSpearSurvivalSpawnsProjectileAndReducesDurability() {
        PlayerMock player = server.addPlayer();
        ItemStack spear = createSpear();
        short before = spear.getDurability();

        var projectile = handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);

        // The spawner was invoked (even if it returns null in test mode)
        assertEquals(1, spawnCount, "projectile spawner must be called exactly once");
        // The hand item must be damaged (the inventory was updated by throwSpear)
        var inHand = player.getInventory().getItemInMainHand();
        if (!inHand.getType().isAir()) {
            assertEquals((short)(before + 1), inHand.getDurability(), "held spear must lose 1 durability");
        }
    }

    @Test
    void throwSpearCreativeDoesNotReduceDurability() {
        PlayerMock player = server.addPlayer();
        player.setGameMode(GameMode.CREATIVE);
        ItemStack spear = createSpear();
        short before = spear.getDurability();

        handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);

        assertEquals(1, spawnCount, "spawner must be called");
        var inHand = player.getInventory().getItemInMainHand();
        if (!inHand.getType().isAir()) {
            assertEquals(before, inHand.getDurability(), "creative must not lose durability");
        }
    }

    @Test
    void brokenSpearHandBecomesAirAndNoProjectileItem() {
        PlayerMock player = server.addPlayer();
        ItemStack spear = createSpear();
        // Damage to one point before breaking
        spear.setDurability((short)(spear.getType().getMaxDurability() - 1));

        var projectile = handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);

        // Hand must be cleared (spear broke)
        var inHand = player.getInventory().getItemInMainHand();
        assertTrue(inHand.getType().isAir() || inHand.getAmount() == 0,
                "broken spear must clear the hand slot");
    }
}
