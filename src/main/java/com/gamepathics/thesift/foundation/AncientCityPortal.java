package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Ancient City portal, ported from the reference mod (PortalFrameScanner, SingerSummoner).
 *
 * <ul>
 *   <li>The frame is a rectangle (at least 3x3) of Reinforced Deepslate whose interior is air-like, found
 *       by the same anchor-walk algorithm as the mod, within 40 blocks of the player.</li>
 *   <li>Playing a Goat Horn starts a 3 second delay, then a Singer appears at the frame's bottom centre
 *       (never a second one within 2 blocks) and awakens eight Sonorous Deepslate blocks in front of it.</li>
 * </ul>
 */
final class AncientCityPortal implements Listener {
    private static final int MAX_SCAN = 23;
    private static final long SUMMON_DELAY_TICKS = 60L;

    /** A detected frame. {@code anchorY} is the bottom frame row; {@code bottomCenter} is inside the frame. */
    record Frame(World world, List<Block> interior, boolean alongX, Location bottomCenter, int anchorY) {
        BoundingBox gate() {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (Block block : interior) {
                minX = Math.min(minX, block.getX());
                minY = Math.min(minY, block.getY());
                minZ = Math.min(minZ, block.getZ());
                maxX = Math.max(maxX, block.getX());
                maxY = Math.max(maxY, block.getY());
                maxZ = Math.max(maxZ, block.getZ());
            }
            return new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        }

        /** +1 or -1: which side of the frame the given location is on. */
        int sideOf(Location location) {
            double offset = alongX ? location.getZ() - bottomCenter.getZ() : location.getX() - bottomCenter.getX();
            return offset >= 0 ? 1 : -1;
        }

        /** A safe standing spot a few blocks in front of the frame, on the given side. */
        Location arrival(int side) {
            double distance = 2.5 * side;
            return bottomCenter.clone().add(alongX ? 0 : distance, 0, alongX ? distance : 0);
        }
    }

    private final JavaPlugin plugin;
    private final SonorousBlockRegistry registry;
    private final ExternalBlockBridge blockBridge;
    private final NamespacedKey singerKey;
    private final Map<UUID, Long> hornScanCooldownUntil = new HashMap<>();
    private Function<Location, LivingEntity> singerSpawner;

    AncientCityPortal(JavaPlugin plugin) {
        this.plugin = plugin;
        this.registry = new SonorousBlockRegistry(plugin);
        this.blockBridge = new ExternalBlockBridge(plugin);
        this.singerKey = new NamespacedKey(plugin, "resonance_entity");
    }

    /** Wired by the plugin once the Singer manager exists (may stay null when the Singer is disabled). */
    void setSingerSpawner(Function<Location, LivingEntity> singerSpawner) {
        this.singerSpawner = singerSpawner;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("ancient-city.enabled", true)
                && plugin.getConfig().getBoolean("content.sonorous_deepslate", true);
    }

    private int scanRadius() {
        return Math.max(8, plugin.getConfig().getInt("ancient-city.scan-radius", 40));
    }

    // ---- Goat Horn -> Singer + awakening ------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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
        Location origin = player.getLocation().clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> trySummon(player, origin), SUMMON_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hornScanCooldownUntil.remove(event.getPlayer().getUniqueId());
    }

    private void trySummon(Player player, Location origin) {
        // The summon fires 3 s after the horn; the player may have logged off by then.
        if (!player.isOnline()) return;
        if (!enabled() || origin.getWorld() == null) return;
        Frame frame = findNearestFrame(origin);
        if (frame == null) return;
        SiftAdvancementGrant.grant(plugin, player, "story/song_of_the_past");
        Location spawn = frame.bottomCenter();
        if (!hasSingerNear(spawn, 2.0) && singerSpawner != null) {
            LivingEntity singer = singerSpawner.apply(spawn);
            if (singer != null) {
                int side = frame.sideOf(player.getLocation());
                float yaw = frame.alongX() ? (side > 0 ? 0f : 180f) : (side > 0 ? -90f : 90f);
                singer.setRotation(yaw, 0f);
                spawn.getWorld().playSound(spawn, Sound.ENTITY_WARDEN_LISTENING, 1.0f, 0.8f);
            }
        }
        awakenSonorous(player, frame);
    }

    private boolean hasSingerNear(Location location, double radius) {
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if ("singer".equals(entity.getPersistentDataContainer().get(singerKey, PersistentDataType.STRING))) {
                return true;
            }
        }
        return false;
    }

    /** The Singer's song awakens the eight Sonorous Deepslate blocks, one every half second. */
    private void awakenSonorous(Player player, Frame frame) {
        World world = frame.world();
        int side = frame.sideOf(player.getLocation());
        int distance = Math.max(3, plugin.getConfig().getInt("ancient-city.row-distance", 6));
        double cx = frame.bottomCenter().getX();
        double cz = frame.bottomCenter().getZ();
        List<Block> floors = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int offset = (i - 4) * 2 + 1; // -7, -5, -3, -1, 1, 3, 5, 7
            int x = (int) Math.floor(frame.alongX() ? cx + offset : cx + side * distance);
            int z = (int) Math.floor(frame.alongX() ? cz + side * distance : cz + offset);
            Block floor = findFloor(world, x, z, frame.anchorY());
            if (floor != null) floors.add(floor);
        }
        new BukkitRunnable() {
            private int index;
            private int ready;

            @Override
            public void run() {
                if (index >= floors.size()) {
                    if (ready > 0) {
                        player.sendActionBar("§5The Sonorous Deepslate awakens (" + ready + "/8)");
                    }
                    cancel();
                    return;
                }
                Block floor = floors.get(index++);
                boolean already = registry.contains(floor);
                if (already || placeSonorous(floor)) {
                    ready++;
                    if (!already) {
                        world.spawnParticle(Particle.END_ROD, floor.getLocation().add(0.5, 1.2, 0.5), 6, 0.2, 0.2, 0.2, 0.01);
                        world.playSound(floor.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.6f + index * 0.1f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
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

    // ---- Beams ---------------------------------------------------------------------------------

    /** Beams of light from the played Sonorous blocks to the frame centre. */
    void fireBeams(List<Location> sources, Location portal) {
        if (sources.isEmpty() || portal == null || portal.getWorld() == null) return;
        World world = portal.getWorld();
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

    // ---- Frame detection (port of PortalFrameScanner) --------------------------------------------

    /**
     * Nearest valid frame within the scan radius of {@code origin}, or null.
     *
     * Performance notes (review fix): the bulk type sweep runs on {@link org.bukkit.ChunkSnapshot}s
     * instead of live block access, candidate anchors are deduplicated before the expensive
     * anchor-walk {@code scan()} so one frame costs at most two scans instead of one per border
     * block, and results are cached per chunk region for a few seconds so spamming Goat Horns is
     * nearly free. The cache stores nullable frames; a miss-negative is also remembered.
     */
    Frame findNearestFrame(Location origin) {
        World world = origin.getWorld();
        if (world == null) return null;
        int radius = scanRadius();
        String cacheKey = world.getUID() + ":" + (origin.getBlockX() >> 4) + ":" + (origin.getBlockZ() >> 4);
        FrameCacheEntry cached = frameCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresMillis > now) return cached.frame;
        Frame found = searchFrames(world, origin, radius);
        frameCache.put(cacheKey, new FrameCacheEntry(found, now + FRAME_CACHE_MILLIS));
        if (frameCache.size() > 64) frameCache.entrySet().removeIf(e -> e.getValue().expiresMillis <= now);
        return found;
    }

    private static final long FRAME_CACHE_MILLIS = 3_000L;
    private record FrameCacheEntry(Frame frame, long expiresMillis) {}
    /** A world-block position used to deduplicate frame anchors found by the snapshot sweep. */
    private record Pos(int x, int y, int z) {}
    private final Map<String, FrameCacheEntry> frameCache = new java.util.concurrent.ConcurrentHashMap<>();

    private Frame searchFrames(World world, Location origin, int radius) {
        double limit = (double) radius * radius;
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        // Pass 1: cheap snapshot sweep to collect distinct Reinforced Deepslate anchors.
        // Chunk#getBlockSnapshot()/ChunkSnapshot#getMinY() do not exist on the Paper API; the
        // supported call is Chunk#getChunkSnapshot(). Its getBlockType(x, y, z) takes a WORLD
        // height (it offsets by the section minimum internally), so we pass world Y directly —
        // iterating raw section indices would only ever cover a 16-layer sliver of the range.
        // Anchors are plain world coordinates; a record keeps them exact (no packing sign/order bugs).
        Set<Pos> anchors = new HashSet<>();
        int minY = Math.max(world.getMinHeight(), oy - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, oy + radius);
        for (int cx = (ox - radius) >> 4; cx <= (ox + radius) >> 4; cx++) {
            for (int cz = (oz - radius) >> 4; cz <= (oz + radius) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                org.bukkit.ChunkSnapshot snapshot = world.getChunkAt(cx, cz).getChunkSnapshot();
                int baseX = cx << 4, baseZ = cz << 4;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int dx = baseX + lx - ox, dz = baseZ + lz - oz;
                        double planar = (double) dx * dx + (double) dz * dz;
                        if (planar > limit) continue;
                        for (int y = minY; y <= maxY; y++) {
                            int dy = y - oy;
                            if (planar + (double) dy * dy > limit) continue;
                            if (snapshot.getBlockType(lx, y, lz) != Material.REINFORCED_DEEPSLATE) continue;
                            anchors.add(new Pos(baseX + lx, y, baseZ + lz));
                        }
                    }
                }
            }
        }
        // Pass 2: only deduplicated anchors get the full anchor-walk scan.
        Frame best = null;
        double bestDistance = Double.MAX_VALUE;
        Set<String> seen = new HashSet<>();
        for (Pos p : anchors) {
            Block block = world.getBlockAt(p.x(), p.y(), p.z());
            if (block.getType() != Material.REINFORCED_DEEPSLATE) continue; // stale snapshot guard
            Frame candidate = scan(block);
            if (candidate == null) continue;
            Location centre = candidate.bottomCenter();
            if (!seen.add(centre.getBlockX() + "," + centre.getBlockY() + "," + centre.getBlockZ()
                    + (candidate.alongX() ? "x" : "z"))) continue;
            double distance = centre.distanceSquared(origin);
            if (distance <= limit && distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    Frame scan(Block clicked) {
        Frame onX = scanAxis(clicked, true);
        return onX != null ? onX : scanAxis(clicked, false);
    }

    // Portal frames are anchored on Reinforced Deepslate only — the one block vanilla places
    // as an Ancient Portal frame piece. The datapack's minecraft:ancient_city_variants tag is
    // pack-facing (loot/advancement context) and deliberately NOT consulted here; widening
    // this predicate to deepslate tiles/cobbled would match ordinary Ancient City flooring.
    private static boolean isFrame(Block block) {
        return block.getType() == Material.REINFORCED_DEEPSLATE;
    }

    private static boolean airLikeInterior(Block block) {
        Material type = block.getType();
        return type.isAir() || type == Material.SCULK_VEIN || type == Material.GLOW_LICHEN
                || type == Material.SCULK_CATALYST;
    }

    private Frame scanAxis(Block clicked, boolean alongX) {
        BlockFace positive = alongX ? BlockFace.EAST : BlockFace.SOUTH;
        BlockFace negative = positive.getOppositeFace();
        Block anchor = clicked;
        int steps = 0;
        while (isFrame(anchor.getRelative(BlockFace.DOWN))) {
            anchor = anchor.getRelative(BlockFace.DOWN);
            if (++steps > MAX_SCAN) return null;
        }
        steps = 0;
        while (isFrame(anchor.getRelative(negative))) {
            anchor = anchor.getRelative(negative);
            if (++steps > MAX_SCAN) return null;
        }
        int width = 1;
        while (isFrame(anchor.getRelative(positive, width))) {
            if (++width > MAX_SCAN) return null;
        }
        int height = 1;
        while (isFrame(anchor.getRelative(BlockFace.UP, height))) {
            if (++height > MAX_SCAN) return null;
        }
        if (width < 3 || height < 3) return null;
        List<Block> interior = new ArrayList<>();
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                Block pos = anchor.getRelative(positive, w).getRelative(BlockFace.UP, h);
                boolean border = w == 0 || w == width - 1 || h == 0 || h == height - 1;
                if (border) {
                    if (!isFrame(pos)) return null;
                } else {
                    if (!airLikeInterior(pos)) return null;
                    interior.add(pos);
                }
            }
        }
        double middle = (width - 1) * 0.5;
        Location bottomCenter = new Location(anchor.getWorld(),
                anchor.getX() + 0.5 + positive.getModX() * middle,
                anchor.getY() + 1.0,
                anchor.getZ() + 0.5 + positive.getModZ() * middle);
        return new Frame(anchor.getWorld(), interior, alongX, bottomCenter, anchor.getY());
    }
}
