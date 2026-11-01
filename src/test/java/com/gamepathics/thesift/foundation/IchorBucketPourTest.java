package com.gamepathics.thesift.foundation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review P1: pouring an Ichor Bucket must never fall through to vanilla water. The pour
 * settles into a resonant cauldron with a full charge and an empty bucket swap.
 *
 * State is always read through explicitly constructed locations: MockBukkit caches
 * {@code Block#getLocation()} per block, so a handler that mutates a derived location would
 * otherwise shift the observed key.
 */
class IchorBucketPourTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private IchorContent content;
    private IchorState state;
    private IchorListener listener;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        plugin.getConfig().set("providers.strict", false);
        plugin.getConfig().set("content.ichor", true);
        plugin.getConfig().set("content.ichor_bucket", true);
        content = new IchorContent(plugin);
        state = new IchorState(plugin);
        listener = new IchorListener(plugin, content, state);
        world = server.addSimpleWorld("world");
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        state.shutdown();
        MockBukkit.unmock();
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    private PlayerBucketEmptyEvent pourEvent(Block clicked, org.bukkit.block.BlockFace face, ItemStack held) {
        player.getInventory().setItemInMainHand(held);
        // Paper 26.3 signature: (who, block, blockClicked, blockFace, bucket, item, hand);
        // the handler only reads getBlockClicked() and getBlockFace().
        return new PlayerBucketEmptyEvent(player, clicked, clicked, face, Material.WATER_BUCKET,
                held, org.bukkit.inventory.EquipmentSlot.HAND);
    }

    @Test
    void pouringIchorBucketPlacesCauldronWithFullChargeAndSwapsBucket() {
        Block clicked = world.getBlockAt(0, 64, 0);
        Block target = world.getBlockAt(0, 65, 0);
        ItemStack bucket = content.ichorBucket();

        PlayerBucketEmptyEvent event = pourEvent(clicked, org.bukkit.block.BlockFace.UP, bucket);
        listener.onIchorBucketPour(event);

        assertTrue(event.isCancelled(), "vanilla water placement must be cancelled");
        assertEquals(Material.WATER_CAULDRON, target.getType());
        assertEquals(3, state.level(at(0, 65, 0)));
        assertEquals(Material.BUCKET, player.getInventory().getItemInMainHand().getType(),
                "the Ichor bucket must become an empty bucket");
    }

    @Test
    void pouringOntoSolidGroundIsRefusedAndKeepsTheBucket() {
        Block clicked = world.getBlockAt(0, 64, 0);
        Block solid = world.getBlockAt(0, 65, 0);
        solid.setType(Material.STONE);
        ItemStack bucket = content.ichorBucket();

        PlayerBucketEmptyEvent event = pourEvent(clicked, org.bukkit.block.BlockFace.UP, bucket);
        listener.onIchorBucketPour(event);

        assertTrue(event.isCancelled());
        assertEquals(Material.STONE, solid.getType());
        assertEquals(0, state.level(at(0, 65, 0)));
        assertTrue(content.is(player.getInventory().getItemInMainHand(), IchorContent.ICHOR_BUCKET_ID),
                "a refused pour keeps the Ichor bucket");
    }

    @Test
    void vanillaWaterBucketIsNotIntercepted() {
        Block clicked = world.getBlockAt(0, 64, 0);
        Block target = world.getBlockAt(0, 65, 0);

        PlayerBucketEmptyEvent event = pourEvent(clicked, org.bukkit.block.BlockFace.UP,
                new ItemStack(Material.WATER_BUCKET));
        listener.onIchorBucketPour(event);

        assertFalse(event.isCancelled(), "a plain water bucket must keep vanilla behaviour");
        assertEquals(Material.AIR, target.getType());
    }

    @Test
    void breakingAPouredCauldronClearsItsCharge() {
        Block clicked = world.getBlockAt(4, 64, 4);
        Block target = world.getBlockAt(4, 65, 4);

        listener.onIchorBucketPour(pourEvent(clicked, org.bukkit.block.BlockFace.UP, content.ichorBucket()));
        assertEquals(3, state.level(at(4, 65, 4)));

        BlockBreakEvent breakEvent = new BlockBreakEvent(target, player);
        listener.onBlockBreak(breakEvent);
        assertEquals(0, state.level(at(4, 65, 4)),
                "breaking the cauldron must clear the stored charge");
    }

    @Test
    void cauldronFillAndBucketDrawStillWorkAfterPour() {
        Block clicked = world.getBlockAt(8, 64, 8);
        Block target = world.getBlockAt(8, 65, 8);
        listener.onIchorBucketPour(pourEvent(clicked, org.bukkit.block.BlockFace.UP, content.ichorBucket()));
        assertEquals(3, state.level(at(8, 65, 8)));

        // Draw the poured charge back into a fresh Ichor bucket via the cauldron handler.
        player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        PlayerInteractEvent draw = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                new ItemStack(Material.BUCKET), target, org.bukkit.block.BlockFace.UP,
                org.bukkit.inventory.EquipmentSlot.HAND);
        listener.onIchorBucketFill(draw);
        assertEquals(0, state.level(at(8, 65, 8)),
                "drawing a bucket drains bucket-cost charges from the cauldron");
        assertTrue(content.is(player.getInventory().getItemInMainHand(), IchorContent.ICHOR_BUCKET_ID)
                || player.getInventory().contains(content.ichorBucket().getType()),
                "drawing must hand back an Ichor bucket");
    }
}
