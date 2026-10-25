package com.gamepathics.thesift.foundation;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mod parity (Mielon's The Sift): an Ancient City centre has a Reinforced Deepslate frame. Blowing a
 * Goat Horn there awakens eight Sonorous Deepslate blocks in front of it; playing the Singer's song
 * on Note Blocks above them opens the frame into The Sift (a long-lived portal Rift, 1:1 coordinates).
 *
 * The frame is detected by scanning loaded blocks for a connected Reinforced Deepslate cluster, so it
 * does not depend on any structure API.
 */
final class AncientCityPortal implements Listener {
    private static final int MAX_FRAME_BLOCKS = 600;

    /** Axis-aligned bounding box of the detected frame. */
    record Frame(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean alongX() { return (maxX - minX) >= (maxZ - minZ); }
        double centerX() { return (minX + maxX + 1) / 2.0; }
        double centerZ() { return (minZ + maxZ + 1) / 2.0; }
        Location portalLocation() { return new Location(world, centerX(), minY + 1.0, centerZ()); }
    }

    private final JavaPlugin plugin;
    private final SonorousBlockRegistry registry;
    private final ExternalBlockBridge blockBridge;
    private final Map<UUID, Long> hornScanCooldownUntil = new HashMap<>();

    AncientCityPortal(JavaPlugin plugin) {
        this.plugin = plugin;
        this.registry = new SonorousBlockRegistry(plugin);
        this.blockBridge = new ExternalBlockBridge(plugin);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("ancient-city.enabled", true)
                && plugin.getConfig().getBoolean("content.sonorous_deepslate", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGoatHorn(PlayerInteractEvent event) {
        if (!enabled() || event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GOAT_HORN) return;
        Player player = event.getPlayer();
        if (player.getLocation().getBlock().getBiome() != Biome.DEEP_DARK) return;
        long now = System.currentTimeMillis();
        Long until = hornScanCooldownUntil.get(player.getUniqueId());
        if (until != null && now < until) return;
        hornScanCooldownUntil.put(player.getUniqueId(), now + 5_000L);
        Frame frame = findFrame(player.getLocation());
        if (frame == null) return;
        int ready = awaken(player, frame);
        if (ready > 0) {
            player.sendActionBar("§5The Sonorous Deepslate awakens (" + ready + "/8)");
            SiftAdvancementGrant.grant(plugin, player, "singer_awake");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hornScanCooldownUntil.remove(event.getPlayer().getUniqueId());
    }

    /** Portal location of an Ancient City frame near the played Note Block, or null if none is found. */
    Location portalLocation(Block noteBlock) {
        if (!enabled() || noteBlock == null) return null;
        Frame frame = findFrame(noteBlock.getLocation());
        return frame == null ? null : frame.portalLocation();
    }

    /** Beams of light from every awakened Sonorous Deepslate near the played block to the frame. */
    void fireBeams(Block noteBlock, Location portal) {
        if (noteBlock == null || portal == null || portal.getWorld() == null) return;
        World world = noteBlock.getWorld();
        Block base = noteBlock.getRelative(BlockFace.DOWN);
        List<Location> sources = new ArrayList<>();
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                if (!world.isChunkLoaded((base.getX() + dx) >> 4, (base.getZ() + dz) >> 4)) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    Block candidate = world.getBlockAt(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (candidate.getType().isAir() || !registry.contains(candidate)) continue;
                    sources.add(candidate.getLocation().add(0.5, 1.4, 0.5));
                }
            }
        }
        Location target = portal.clone().add(0, 1.5, 0);
        new BukkitRunnable() {
            private int pulses;

            @Override
            public void run() {
                if (++pulses > 15) {
                    cancel();
                    return;
                }
                for (Location source : sources) {
                    int steps = 24;
                    double sx = (target.getX() - source.getX()) / steps;
                    double sy = (target.getY() - source.getY()) / steps;
                    double sz = (target.getZ() - source.getZ()) / steps;
                    for (int i = 0; i <= steps; i++) {
                        world.spawnParticle(Particle.END_ROD, source.getX() + sx * i, source.getY() + sy * i,
                                source.getZ() + sz * i, 1, 0, 0, 0, 0);
                    }
                }
                world.playSound(target, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.5f + pulses * 0.05f);
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /** Places (or confirms) up to eight Sonorous Deepslate blocks in front of the frame. */
    int awaken(Player player, Frame frame) {
        World world = frame.world();
        boolean alongX = frame.alongX();
        double cx = frame.centerX();
        double cz = frame.centerZ();
        double toward = alongX ? player.getLocation().getZ() - cz : player.getLocation().getX() - cx;
        int side = toward >= 0 ? 1 : -1;
        int distance = Math.max(3, plugin.getConfig().getInt("ancient-city.row-distance", 6));
        int ready = 0;
        for (int i = 0; i < 8; i++) {
            int offset = (i - 4) * 2 + 1; // -7, -5, -3, -1, 1, 3, 5, 7
            int x = (int) Math.floor(alongX ? cx + offset : cx + side * distance);
            int z = (int) Math.floor(alongX ? cz + side * distance : cz + offset);
            Block floor = findFloor(world, x, z, frame.minY());
            if (floor == null) continue;
            if (registry.contains(floor)) {
                ready++;
                continue;
            }
            if (placeSonorous(floor)) {
                ready++;
                world.spawnParticle(Particle.END_ROD, floor.getLocation().add(0.5, 1.2, 0.5), 6, 0.2, 0.2, 0.2, 0.01);
                world.playSound(floor.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.6f + i * 0.1f);
            }
        }
        return ready;
    }

    private boolean placeSonorous(Block block) {
        boolean placed = blockBridge.place("sonorous_deepslate", block.getLocation());
        if (!placed) {
            if (plugin.getConfig().getBoolean("providers.strict", true)) return false;
            block.setType(Material.DEEPSLATE, false);
        }
        registry.mark(block);
        return true;
    }

    private Block findFloor(World world, int x, int z, int frameMinY) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        for (int y = frameMinY + 3; y >= frameMinY - 6; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (!type.isSolid() || type == Material.REINFORCED_DEEPSLATE || type == Material.BEDROCK) continue;
            if (block.getRelative(BlockFace.UP).isPassable()
                    && block.getRelative(BlockFace.UP, 2).isPassable()) return block;
        }
        return null;
    }

    /**
     * Finds the nearest connected Reinforced Deepslate cluster (26-neighbour connectivity, because
     * portal-style frames only touch diagonally at the corners).
     */
    Frame findFrame(Location near) {
        World world = near.getWorld();
        if (world == null) return null;
        int radius = Math.max(8, plugin.getConfig().getInt("ancient-city.scan-radius", 24));
        int vertical = Math.max(6, plugin.getConfig().getInt("ancient-city.scan-vertical", 16));
        int minBlocks = Math.max(4, plugin.getConfig().getInt("ancient-city.min-frame-blocks", 10));
        int cx = near.getBlockX();
        int cy = near.getBlockY();
        int cz = near.getBlockZ();
        int lowY = Math.max(world.getMinHeight(), cy - vertical);
        int highY = Math.min(world.getMaxHeight() - 1, cy + vertical);
        Block start = null;
        double best = Double.MAX_VALUE;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = lowY; y <= highY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.REINFORCED_DEEPSLATE) continue;
                    double distance = near.distanceSquared(block.getLocation());
                    if (distance < best) {
                        best = distance;
                        start = block;
                    }
                }
            }
        }
        if (start == null) return null;

        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int minX = start.getX(), minY = start.getY(), minZ = start.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        while (!queue.isEmpty() && seen.size() <= MAX_FRAME_BLOCKS) {
            Block current = queue.poll();
            minX = Math.min(minX, current.getX());
            minY = Math.min(minY, current.getY());
            minZ = Math.min(minZ, current.getZ());
            maxX = Math.max(maxX, current.getX());
            maxY = Math.max(maxY, current.getY());
            maxZ = Math.max(maxZ, current.getZ());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        int nx = current.getX() + dx;
                        int ny = current.getY() + dy;
                        int nz = current.getZ() + dz;
                        if (ny < lowY || ny > highY || Math.abs(nx - cx) > radius + 8
                                || Math.abs(nz - cz) > radius + 8
                                || !world.isChunkLoaded(nx >> 4, nz >> 4)) continue;
                        Block neighbour = world.getBlockAt(nx, ny, nz);
                        if (neighbour.getType() == Material.REINFORCED_DEEPSLATE && seen.add(neighbour)) {
                            queue.add(neighbour);
                        }
                    }
                }
            }
        }
        if (seen.size() < minBlocks) return null;
        return new Frame(world, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
