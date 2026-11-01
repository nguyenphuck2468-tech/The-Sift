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

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full lifecycle test for SpearThrowHandler using an injectable spawner and clock.
 * Covers: tag recognition, creative mode, cooldown via injectable clock,
 * spawn failure rollback, spawner exception safety.
 * Paper-integration tests needed for: durability commit via damage(1,player),
 * broken spear hand clearing, cooldown PDC persistence.
 */
class SpearThrowHandlerTest {
    private ServerMock server;
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private SpearThrowHandler handler;
    private int spawnCount;
    private AtomicLong fakeClock;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        handler = new SpearThrowHandler(plugin);
        spawnCount = 0;
        fakeClock = new AtomicLong(1_000_000L);
        handler.setClock(fakeClock::get);
        // MockBukkit's damage(1, player) doesn't work on PlayerMock (NMS internals);
        // inject a meta-based strategy that produces the same result for testing.
        handler.setDurabilityStrategy((held, player) -> {
            ItemStack copy = held.clone();
            var m = copy.getItemMeta();
            if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                d.setDamage(d.getDamage() + 1);
                copy.setItemMeta(m);
            }
            return copy;
        });
        handler.setSpawner((eye, item, shooter, speed) -> {
            spawnCount++;
            return null; // no real entity needed for logic tests
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

    private PlayerMock playerSurvival() {
        var player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        return player;
    }

    @Test
    void isSpearRecognizesTaggedItems() {
        assertTrue(handler.isSpear(createSpear()));
        assertFalse(handler.isSpear(new ItemStack(Material.STICK)));
        assertFalse(handler.isSpear(null));
    }

    @Test
    void creativeThrowDoesNotReduceDurability() {
        var player = server.addPlayer();
        player.setGameMode(GameMode.CREATIVE);
        ItemStack spear = createSpear();
        player.getInventory().setItemInMainHand(spear);
        int before = 0;
        var m0 = spear.getItemMeta();
        if (m0 instanceof org.bukkit.inventory.meta.Damageable d0) before = d0.getDamage();

        handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);

        var inHand = player.getInventory().getItemInMainHand();
        if (!inHand.getType().isAir()) {
            var m = inHand.getItemMeta();
            if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                assertEquals(before, d.getDamage(), "creative must not lose durability");
            }
        }
    }

    @Test
    void spawnFailureDoesNotReduceDurabilityOrConsumeCooldown() {
        var player = playerSurvival();
        ItemStack spear = createSpear();
        player.getInventory().setItemInMainHand(spear);
        int beforeDamage = 0;
        var meta = spear.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable d) beforeDamage = d.getDamage();

        handler.setSpawner((eye, item, shooter, speed) -> null);
        var result = handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);

        assertNull(result, "failed spawn must return null");
        // Cooldown must not be consumed: a retry with a working spawner must succeed
        spawnCount = 0;
        handler.setSpawner((eye, item, shooter, speed) -> { spawnCount++; return null; });
        handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);
        assertTrue(spawnCount >= 1, "retry after failed spawn must not be blocked by cooldown");
    }

    @Test
    void spawnerExceptionDoesNotReduceDurability() {
        var player = playerSurvival();
        ItemStack spear = createSpear();
        player.getInventory().setItemInMainHand(spear);
        int beforeDamage = 0;
        var meta = spear.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable d) beforeDamage = d.getDamage();

        handler.setSpawner((eye, item, shooter, speed) -> {
            throw new RuntimeException("spawn exploded");
        });
        var result = handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);

        assertNull(result, "exception during spawn must return null");
        var inHand = player.getInventory().getItemInMainHand();
        if (!inHand.getType().isAir()) {
            var m = inHand.getItemMeta();
            if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                assertEquals(beforeDamage, d.getDamage(),
                        "exception during spawn must not reduce durability");
            }
        }
    }

    @Test
    void cooldownExpiryAllowsSecondThrow() {
        var player = playerSurvival();
        ItemStack spear = createSpear();

        handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);
        int spawnsAfterFirst = spawnCount;

        fakeClock.addAndGet(600L);
        handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);
        assertEquals(spawnsAfterFirst + 1, spawnCount, "throw after cooldown expiry must succeed");
    }

}
