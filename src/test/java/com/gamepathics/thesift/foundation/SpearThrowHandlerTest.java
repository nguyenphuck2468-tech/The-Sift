package com.gamepathics.thesift.foundation;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Snowball;
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
 * Covers: tag recognition, the trident-style throw (hand empties, the projectile IS the
 * item — dupe regression), creative suppression, cooldown via injectable clock, spawn
 * failure rollback, spawner exception safety.
 * Paper-integration tests needed for: cooldown PDC persistence across restarts.
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
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack createSpear() {
        ItemStack spear = new ItemStack(Material.NETHERITE_SPEAR);
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

    /** A spawner backed by launchProjectile so a real projectile exists for state checks. */
    private Snowball throwWithRealProjectile(PlayerMock player, ItemStack spear) {
        handler.setSpawner((eye, item, shooter, speed) -> {
            spawnCount++;
            Snowball projectile = shooter.launchProjectile(Snowball.class);
            projectile.setItem(item);
            return projectile;
        });
        return handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);
    }

    @Test
    void isSpearRecognizesTaggedItems() {
        assertTrue(handler.isSpear(createSpear()));
        assertFalse(handler.isSpear(new ItemStack(Material.STICK)));
        assertFalse(handler.isSpear(null));
    }

    @Test
    void survivalThrowLeavesHandEmptyAndProjectileCarriesTheItem() {
        var player = playerSurvival();
        ItemStack spear = createSpear();
        player.getInventory().setItemInMainHand(spear);

        Snowball projectile = throwWithRealProjectile(player, spear);

        assertNotNull(projectile, "a successful throw returns the projectile");
        // Dupe regression (review P1): the hand must be EMPTY — the held item became the
        // projectile. The old design kept a damaged copy in hand while the projectile
        // dropped another one on hit, giving two spears per throw.
        assertTrue(player.getInventory().getItemInMainHand().getType().isAir(),
                "the thrown spear must leave the hand (trident model)");
        assertNotNull(projectile.getItem());
        assertFalse(projectile.getItem().getType().isAir(), "the projectile must carry the spear");
        assertEquals("siftite_spear", projectile.getItem().getItemMeta()
                .getPersistentDataContainer().get(new NamespacedKey(plugin, "content"),
                        PersistentDataType.STRING));
    }

    @Test
    void creativeThrowKeepsStackAndSuppressesDrop() {
        var player = server.addPlayer();
        player.setGameMode(GameMode.CREATIVE);
        ItemStack spear = createSpear();
        player.getInventory().setItemInMainHand(spear);

        Snowball projectile = throwWithRealProjectile(player, spear);

        assertNotNull(projectile);
        assertFalse(player.getInventory().getItemInMainHand().getType().isAir(),
                "creative keeps the stack");
        assertEquals((byte) 1, projectile.getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, "spear_copy_dropped"), PersistentDataType.BYTE),
                "creative throws must never drop a pickup-able copy");
        assertFalse(handler.dropThrownCopy(plugin, projectile),
                "dropThrownCopy must refuse a creative-thrown spear");
    }

    @Test
    void spawnFailureConsumesNeitherItemNorCooldown() {
        var player = playerSurvival();
        ItemStack spear = createSpear();
        player.getInventory().setItemInMainHand(spear);

        handler.setSpawner((eye, item, shooter, speed) -> null);
        var result = handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);

        assertNull(result, "failed spawn must return null");
        assertFalse(player.getInventory().getItemInMainHand().getType().isAir(),
                "a failed throw must not delete the spear");
        // Cooldown must not be consumed: a retry with a working spawner must succeed.
        spawnCount = 0;
        throwWithRealProjectile(player, spear);
        assertTrue(spawnCount >= 1, "retry after failed spawn must not be blocked by cooldown");
    }

    @Test
    void spawnerExceptionConsumesNeitherItemNorCooldown() {
        var player = playerSurvival();
        ItemStack spear = createSpear();
        player.getInventory().setItemInMainHand(spear);

        handler.setSpawner((eye, item, shooter, speed) -> {
            throw new RuntimeException("spawn exploded");
        });
        var result = handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);

        assertNull(result, "exception during spawn must return null");
        assertFalse(player.getInventory().getItemInMainHand().getType().isAir(),
                "an exploding spawn must not delete the spear");
        spawnCount = 0;
        throwWithRealProjectile(player, spear);
        assertTrue(spawnCount >= 1, "retry after an exception must not be blocked by cooldown");
    }

    @Test
    void cooldownExpiryAllowsSecondThrow() {
        var player = playerSurvival();
        ItemStack spear = createSpear();

        handler.setSpawner((eye, item, shooter, speed) -> {
            spawnCount++;
            return shooter.launchProjectile(Snowball.class);
        });
        handler.throwSpear(plugin, player, spear, org.bukkit.inventory.EquipmentSlot.HAND);
        int spawnsAfterFirst = spawnCount;

        fakeClock.addAndGet(600L);
        handler.throwSpear(plugin, player, createSpear(), org.bukkit.inventory.EquipmentSlot.HAND);
        assertEquals(spawnsAfterFirst + 1, spawnCount, "throw after cooldown expiry must succeed");
    }
}
