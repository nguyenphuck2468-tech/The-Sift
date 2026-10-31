package com.gamepathics.thesift.foundation;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.Note;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

/** Lazy, structure-data-bounded Ancient City gate discovery and Sonorous block placement. */
final class AncientCityGateManager implements Listener {
    private static final int GATE_VERSION = 1;
    private static final int MAX_PIECE_VOLUME = 65_536;
    private final JavaPlugin plugin;
    private final ProviderReadiness readiness;
    private final SiftDimensionManager dimensions;
    private final ExternalBlockBridge blocks;
    private final SonorousBlockRegistry sonorous;
    private final ResonanceManager resonance;
    private final ExternalItemBridge items;
    private final AncientCityMelodyTracker melodies = new AncientCityMelodyTracker();
    private final NamespacedKey portalDisplayKey;
    private final NamespacedKey gateDataKey;
    private final NamespacedKey returnGateDataKey;
    private final Map<UUID, Long> searchedCells = new HashMap<>();
    private final Map<String, Gate> knownGates = new LinkedHashMap<>();
    private final Set<UUID> travelling = new HashSet<>();
    private final Set<UUID> transientBeams = new HashSet<>();
    private int taskId = -1;
    private int visualTaskId = -1;

    AncientCityGateManager(JavaPlugin plugin, ProviderReadiness readiness, SiftDimensionManager dimensions,
                           ResonanceManager resonance) {
        this.plugin = plugin;
        this.readiness = readiness;
        this.dimensions = dimensions;
        this.blocks = new ExternalBlockBridge(plugin);
        this.sonorous = new SonorousBlockRegistry(plugin);
        this.resonance = resonance;
        this.items = new ExternalItemBridge(plugin);
        this.gateDataKey = new NamespacedKey(plugin, "ancient_city_gate_v1");
        this.returnGateDataKey = new NamespacedKey(plugin, "ancient_city_return_gate_v1");
        this.portalDisplayKey = new NamespacedKey(plugin, "ancient_city_portal_display");
    }

    void start() {
        if (taskId != -1) return;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::scanNearbyPlayers, 40L, 40L).getTaskId();
        visualTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::renderFallbackPortalSurfaces, 10L, 10L).getTaskId();
    }

    void shutdown() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        if (visualTaskId != -1) Bukkit.getScheduler().cancelTask(visualTaskId);
        taskId = -1;
        visualTaskId = -1;
        for (UUID id : transientBeams) {
            var entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        transientBeams.clear();
        searchedCells.clear();
        knownGates.clear();
        travelling.clear();
        melodies.clear();
    }

    Optional<Location> locate(Player player) {
        if (readiness.strict() && !readiness.dimensionAllowed()) {
            player.sendMessage("Ancient City gates are locked because required providers are unresolved. Run /sift doctor.");
            return Optional.empty();
        }
        Optional<Gate> gate = findNearest(player.getLocation(), 32);
        if (gate.isEmpty()) {
            player.sendMessage("No generated Ancient City gate was found within 512 blocks.");
            return Optional.empty();
        }
        Location target = gate.orElseThrow().center();
        player.setCompassTarget(target);
        player.sendMessage("Nearest The Sift gate: " + target.getBlockX() + ", " + target.getBlockY()
                + ", " + target.getBlockZ() + ". Your compass now points to it.");
        return Optional.of(target);
    }

    private void scanNearbyPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (world.getKey().equals(SiftDimensionManager.DIMENSION_KEY)) {
                readReturnGate(world, player.getLocation()).ifPresent(gate -> knownGates.put(gate.key, gate));
                continue;
            }
            if (world.getEnvironment() != World.Environment.NORMAL) continue;
            int cellX = Math.floorDiv(player.getLocation().getBlockX(), 128);
            int cellZ = Math.floorDiv(player.getLocation().getBlockZ(), 128);
            long cell = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
            Long previous = searchedCells.put(player.getUniqueId(), cell);
            if (previous != null && previous == cell) continue;
            findNearest(player.getLocation(), 16);
        }
        melodies.expire(System.currentTimeMillis(), 60_000L);
    }

    private Optional<Gate> findNearest(Location origin, int radiusChunks) {
        World world = origin.getWorld();
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) return Optional.empty();
        StructureSearchResult result;
        try {
            result = world.locateNearestStructure(origin, Structure.ANCIENT_CITY, radiusChunks, false);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Ancient City lookup failed: " + exception.getMessage());
            return Optional.empty();
        }
        if (result == null || result.getLocation() == null) return Optional.empty();
        Location locate = result.getLocation();
        int centerChunkX = Math.floorDiv(locate.getBlockX(), 16);
        int centerChunkZ = Math.floorDiv(locate.getBlockZ(), 16);
        List<GeneratedStructure> structures = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            try {
                for (GeneratedStructure structure : world.getStructures(centerChunkX + dx, centerChunkZ + dz,
                        Structure.ANCIENT_CITY)) {
                    if (structures.stream().noneMatch(existing -> existing.getBoundingBox()
                            .equals(structure.getBoundingBox()))) structures.add(structure);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().fine("Ancient City structure lookup deferred: " + exception.getMessage());
            }
        }
        GeneratedStructure structure = structures.stream().min(Comparator.comparingDouble(candidate ->
                distanceSquaredXZ(candidate.getBoundingBox(), locate))).orElse(null);
        if (structure == null) return Optional.empty(); // A locate hint is not proof the city was generated.
        Optional<Gate> gate = readGate(world, locate);
        if (gate.isEmpty()) gate = generateForCity(world, locate, structure);
        gate.ifPresent(found -> knownGates.put(found.key(), found));
        return gate;
    }

    private Optional<Gate> generateForCity(World world, Location locate, GeneratedStructure structure) {
        if (readiness.strict() && !readiness.dimensionAllowed()) return Optional.empty();
        BoundingBox piece = structure.getPieces().stream().map(pieceData -> pieceData.getBoundingBox())
                .filter(bounds -> distanceSquaredXZ(bounds, locate) <= 32 * 32)
                .min(Comparator.comparingDouble((BoundingBox bounds) -> distanceSquaredXZ(bounds, locate))
                        .thenComparingDouble(AncientCityGateManager::boxVolume)).orElse(null);
        if (piece == null || !smallEnough(piece) || !allChunksLoaded(world, piece)) return Optional.empty();
        List<AncientCityFrame.BlockPos> reinforced = blocksInPiece(world, piece);
        Optional<AncientCityFrame> frameResult = AncientCityFrame.fromPiece(piece, reinforced);
        if (frameResult.isEmpty()) return Optional.empty();
        AncientCityFrame frame = frameResult.orElseThrow();
        int negativeOpen = openCellCount(world, frame.sonorousPositions(AncientCityFrame.Side.NEGATIVE));
        int positiveOpen = openCellCount(world, frame.sonorousPositions(AncientCityFrame.Side.POSITIVE));
        AncientCityFrame.Side side = AncientCityFrame.chooseSide(negativeOpen, positiveOpen,
                frame.minX(), frame.minZ());
        List<AncientCityFrame.BlockPos> positions = frame.sonorousPositions(side);
        if (openCellCount(world, positions) != 16) return Optional.empty();

        List<Block> placed = new ArrayList<>();
        for (AncientCityFrame.BlockPos position : positions) {
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            if (!placeSonorous(block)) {
                placed.forEach(placedBlock -> placedBlock.setType(Material.AIR, false));
                plugin.getLogger().warning("Ancient City gate placement failed at " + position
                        + "; partial Sonorous placement was rolled back.");
                return Optional.empty();
            }
            placed.add(block);
            sonorous.mark(block);
        }
        Gate gate = new Gate(gateKey(world, frame), world, frame, side, false, false, null);
        persist(gate);
        plugin.getLogger().info("Registered provider-backed Ancient City gate " + gate.key()
                + " with frame " + frame + "; strict=" + readiness.strict());
        return Optional.of(gate);
    }

    private boolean placeSonorous(Block block) {
        if (readiness.strict()) return blocks.place("sonorous_deepslate", block.getLocation());
        if (blocks.place("sonorous_deepslate", block.getLocation())) return true;
        block.setType(Material.DEEPSLATE, false);
        return true;
    }

    private int openCellCount(World world, List<AncientCityFrame.BlockPos> positions) {
        int count = 0;
        for (AncientCityFrame.BlockPos position : positions) {
            if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) return -1;
            if (world.getBlockAt(position.x(), position.y(), position.z()).isPassable()) count++;
            if (world.getBlockAt(position.x(), position.y() + 1, position.z()).isPassable()) count++;
        }
        return count;
    }

    private List<AncientCityFrame.BlockPos> blocksInPiece(World world, BoundingBox piece) {
        int minX = (int) Math.floor(piece.getMinX()), maxX = (int) Math.ceil(piece.getMaxX());
        int minY = Math.max(world.getMinHeight(), (int) Math.floor(piece.getMinY()));
        int maxY = Math.min(world.getMaxHeight() - 1, (int) Math.ceil(piece.getMaxY()));
        int minZ = (int) Math.floor(piece.getMinZ()), maxZ = (int) Math.ceil(piece.getMaxZ());
        List<AncientCityFrame.BlockPos> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++)
            for (int z = minZ; z <= maxZ; z++) {
                if (world.getBlockAt(x, y, z).getType() == Material.REINFORCED_DEEPSLATE)
                    positions.add(new AncientCityFrame.BlockPos(x, y, z));
            }
        return positions;
    }

    private boolean smallEnough(BoundingBox box) {
        double x = box.getMaxX() - box.getMinX(), y = box.getMaxY() - box.getMinY(),
                z = box.getMaxZ() - box.getMinZ();
        return x <= 48 && y <= 24 && z <= 48 && x * y * z <= MAX_PIECE_VOLUME;
    }

    private boolean allChunksLoaded(World world, BoundingBox box) {
        int minX = Math.floorDiv((int) Math.floor(box.getMinX()), 16);
        int maxX = Math.floorDiv((int) Math.ceil(box.getMaxX()), 16);
        int minZ = Math.floorDiv((int) Math.floor(box.getMinZ()), 16);
        int maxZ = Math.floorDiv((int) Math.ceil(box.getMaxZ()), 16);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (!world.isChunkLoaded(x, z)) return false;
        return true;
    }

    /** True when the block is one of a known gate's Sonorous positions, i.e. gate-owned. */
    boolean ownsSonorous(Block block) {
        for (Gate gate : knownGates.values()) {
            if (gate.world() != block.getWorld()) continue;
            for (AncientCityFrame.BlockPos pos : gate.frame().sonorousPositions(gate.side())) {
                if (pos.x() == block.getX() && pos.y() == block.getY() && pos.z() == block.getZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Optional<Gate> readGate(World world, Location locate) {
        // The record lives in the chunk holding the frame centre, while the lookup starts
        // from the structure-locate position; scan a 3x3 chunk neighbourhood so a restart
        // still finds gates whose centre chunk differs from the locate chunk.
        int cx = locate.getBlockX() >> 4, cz = locate.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded(cx + dx, cz + dz)) continue;
                int[] data = world.getChunkAt(cx + dx, cz + dz).getPersistentDataContainer()
                        .get(gateDataKey, PersistentDataType.INTEGER_ARRAY);
                if (data == null || data.length != 12 || data[0] != GATE_VERSION) continue;
                AncientCityFrame.Axis axis = data[7] == 0 ? AncientCityFrame.Axis.X : AncientCityFrame.Axis.Z;
                AncientCityFrame frame = new AncientCityFrame(data[1], data[2], data[3], data[4], data[5], data[6], axis);
                AncientCityFrame.Side side = data[8] == 0 ? AncientCityFrame.Side.NEGATIVE : AncientCityFrame.Side.POSITIVE;
                Integer returnMinY = data[11] == Integer.MIN_VALUE ? null : data[11];
                return Optional.of(new Gate(gateKey(world, frame), world, frame, side,
                        data[9] == 1, data[10] == 1, returnMinY));
            }
        }
        return Optional.empty();
    }

    private void persist(Gate gate) {
        AncientCityFrame frame = gate.frame();
        int centerX = (frame.minX() + frame.maxX()) >> 1;
        int centerZ = (frame.minZ() + frame.maxZ()) >> 1;
        var pdc = gate.world().getChunkAt(centerX >> 4, centerZ >> 4).getPersistentDataContainer();
        pdc.set(gateDataKey, PersistentDataType.INTEGER_ARRAY, new int[]{GATE_VERSION,
                frame.minX(), frame.minY(), frame.minZ(), frame.maxX(), frame.maxY(), frame.maxZ(),
                frame.longAxis() == AncientCityFrame.Axis.X ? 0 : 1,
                gate.side() == AncientCityFrame.Side.NEGATIVE ? 0 : 1,
                gate.open ? 1 : 0, gate.awakened ? 1 : 0, gate.returnMinY == null ? Integer.MIN_VALUE : gate.returnMinY});
    }

    private void persistReturn(Gate source, World sift, AncientCityFrame returnFrame) {
        int centerX = (returnFrame.minX() + returnFrame.maxX()) >> 1;
        int centerZ = (returnFrame.minZ() + returnFrame.maxZ()) >> 1;
        var pdc = sift.getChunkAt(centerX >> 4, centerZ >> 4).getPersistentDataContainer();
        AncientCityFrame frame = source.frame;
        pdc.set(returnGateDataKey, PersistentDataType.INTEGER_ARRAY, new int[]{GATE_VERSION,
                frame.minX(), frame.minY(), frame.minZ(), frame.maxX(), frame.maxY(), frame.maxZ(),
                frame.longAxis() == AncientCityFrame.Axis.X ? 0 : 1,
                source.side == AncientCityFrame.Side.NEGATIVE ? 0 : 1,
                returnFrame.minY()});
    }

    private Optional<Gate> readReturnGate(World world, Location location) {
        int cx = location.getBlockX() >> 4, cz = location.getBlockZ() >> 4;
        if (!world.isChunkLoaded(cx, cz)) return Optional.empty();
        int[] data = world.getChunkAt(cx, cz).getPersistentDataContainer()
                .get(returnGateDataKey, PersistentDataType.INTEGER_ARRAY);
        if (data == null || data.length != 10 || data[0] != GATE_VERSION) return Optional.empty();
        World overworld = Bukkit.getWorlds().stream().filter(candidate -> candidate.getEnvironment() == World.Environment.NORMAL)
                .findFirst().orElse(null);
        if (overworld == null) return Optional.empty();
        AncientCityFrame sourceFrame = new AncientCityFrame(data[1], data[2], data[3], data[4], data[5], data[6],
                data[7] == 0 ? AncientCityFrame.Axis.X : AncientCityFrame.Axis.Z);
        AncientCityFrame frame = new AncientCityFrame(sourceFrame.minX(), data[9], sourceFrame.minZ(),
                sourceFrame.maxX(), data[9] + 7, sourceFrame.maxZ(), sourceFrame.longAxis());
        AncientCityFrame.Side side = data[8] == 0 ? AncientCityFrame.Side.NEGATIVE : AncientCityFrame.Side.POSITIVE;
        Gate gate = new Gate(gateKey(world, frame) + ":return", world, frame, side, true, true, sourceFrame.minY());
        gate.pairedFrame = sourceFrame;
        gate.pairedWorld = overworld;
        return Optional.of(gate);
    }

    private String gateKey(World world, AncientCityFrame frame) {
        return world.getUID() + ":" + frame.minX() + ":" + frame.minY() + ":" + frame.minZ();
    }

    private Location center(AncientCityFrame frame, World world) {
        return new Location(world, (frame.minX() + frame.maxX() + 1) / 2.0,
                frame.minY() + 1.0, (frame.minZ() + frame.maxZ() + 1) / 2.0);
    }

    private static double distanceSquaredXZ(BoundingBox box, Location location) {
        double x = Math.max(box.getMinX(), Math.min(location.getX(), box.getMaxX()));
        double z = Math.max(box.getMinZ(), Math.min(location.getZ(), box.getMaxZ()));
        return (x - location.getX()) * (x - location.getX()) + (z - location.getZ()) * (z - location.getZ());
    }

    private static double boxVolume(BoundingBox box) {
        return (box.getMaxX() - box.getMinX()) * (box.getMaxY() - box.getMinY())
                * (box.getMaxZ() - box.getMinZ());
    }

    private static final class Gate {
        private final String key;
        private final World world;
        private final AncientCityFrame frame;
        private final AncientCityFrame.Side side;
        private boolean open;
        private boolean awakened;
        private Integer returnMinY;
        private UUID displayId;
        private AncientCityFrame pairedFrame;
        private World pairedWorld;

        private Gate(String key, World world, AncientCityFrame frame, AncientCityFrame.Side side,
                     boolean open, boolean awakened, Integer returnMinY) {
            this.key = key; this.world = world; this.frame = frame; this.side = side;
            this.open = open; this.awakened = awakened; this.returnMinY = returnMinY;
        }

        String key() { return key; }
        World world() { return world; }
        AncientCityFrame frame() { return frame; }
        AncientCityFrame.Side side() { return side; }
        boolean open() { return open; }
        boolean awakened() { return awakened; }
        Location center() {
            return new Location(world, (frame.minX() + frame.maxX() + 1) / 2.0,
                    frame.minY() + 1.0, (frame.minZ() + frame.maxZ() + 1) / 2.0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        if (travelling.contains(player.getUniqueId())) return;
        for (Gate gate : knownGates.values()) {
            if (!gate.open || gate.world != player.getWorld()
                    || !gate.frame.containsPortalOpening(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ())) continue;
            travel(player, gate);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGoatHorn(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null
                || event.getItem().getType() != Material.GOAT_HORN
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Gate gate = nearbyGate(event.getPlayer().getLocation(), 48);
        if (gate == null || gate.awakened) return;
        if (readiness.strict() && !readiness.dimensionAllowed()) {
            event.getPlayer().sendMessage("The Sift gate cannot awaken: required providers are unresolved. Run /sift doctor.");
            return;
        }
        if (resonance == null || resonance.summonSinger(gate.center()) == null) {
            event.getPlayer().sendMessage("The Singer could not appear. Check the required mob provider with /sift doctor.");
            plugin.getLogger().warning("Goat Horn gate activation failed because the Singer provider did not spawn singer.");
            return;
        }
        gate.awakened = true;
        persist(gate);
        event.getPlayer().sendMessage("The Singer's song awakens the Sonorous Deepslate.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onNoteBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.NOTE_BLOCK) return;
        Gate gate = gateForSonorous(placed.getRelative(org.bukkit.block.BlockFace.DOWN));
        if (gate != null && !gate.awakened) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Play a Goat Horn near the Ancient City gate to awaken its Sonorous Deepslate first.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortalNote(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.NOTE_BLOCK) return;
        Block noteBlock = event.getClickedBlock();
        Block base = noteBlock.getRelative(org.bukkit.block.BlockFace.DOWN);
        if (blocks.isProviderOwned(noteBlock) || !sonorous.contains(base)) return;
        Gate gate = gateForSonorous(base);
        if (gate == null) return;
        if (!gate.awakened) {
            event.getPlayer().sendActionBar("§cAwaken this city's Sonorous Deepslate with a Goat Horn first.");
            return;
        }
        if (!(noteBlock.getBlockData() instanceof NoteBlock data)) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            int next = (data.getNote().getId() + 1) % 8;
            data.setNote(new Note(next));
            noteBlock.setBlockData(data, false);
            event.getPlayer().sendActionBar("§bSift note: " + (next + 1));
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        event.setCancelled(true);
        int played = data.getNote().getId() + 1;
        long now = System.currentTimeMillis();
        long timeout = Math.max(1L, plugin.getConfig().getLong("rift.portal-song-timeout-seconds", 60)) * 1000L;
        AncientCityMelodyTracker.Result result = melodies.play(event.getPlayer().getUniqueId(), gate.key,
                played, noteBlock.getX() + ":" + noteBlock.getY() + ":" + noteBlock.getZ(), now, timeout);
        event.getPlayer().getWorld().spawnParticle(Particle.DUST,
                noteBlock.getLocation().add(0.5, 1.1, 0.5), 8, 0.35, 0.15, 0.35,
                new Particle.DustOptions(Color.fromRGB(SonorousPalette.noteRgb(played)), 1.1f));
        event.getPlayer().playSound(noteBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.8f,
                (float) Math.pow(2.0, (played - 1) / 12.0));
        spawnSonorousBeam(base, played);
        if (result == AncientCityMelodyTracker.Result.WRONG) {
            event.getPlayer().playSound(noteBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            event.getPlayer().sendActionBar("§cWrong note; the gate melody resets.");
        } else if (result == AncientCityMelodyTracker.Result.COMPLETE) {
            replayMelody(gate);
            if (openPortal(gate, event.getPlayer()))
                event.getPlayer().sendMessage("§bThe Ancient City portal opens into The Sift!");
        } else event.getPlayer().sendActionBar("§bAncient City melody: "
                + melodies.progress(event.getPlayer().getUniqueId(), gate.key) + "/8");
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrameHelp(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.REINFORCED_DEEPSLATE) return;
        Gate gate = gateForFrameBlock(event.getClickedBlock());
        if (gate == null || gate.open) return;
        Player player = event.getPlayer();
        if (readiness.strict() && !readiness.dimensionAllowed()) {
            player.sendMessage("This gate cannot open yet: required content and mob providers are unresolved. Run /sift doctor.");
        } else if (!gate.awakened) {
            player.sendMessage("This gate is silent. Play a Goat Horn near its Sonorous Deepslate to summon a Singer.");
        } else {
            List<AncientCityFrame.BlockPos> missing = gate.frame.sonorousPositions(gate.side).stream()
                    .filter(position -> gate.world.getBlockAt(position.x(), position.y() + 1, position.z()).getType()
                            != Material.NOTE_BLOCK).toList();
            if (!missing.isEmpty()) {
                player.sendMessage("Place Note Blocks on all 8 Sonorous Deepslate blocks; missing " + missing.size() + ".");
                for (AncientCityFrame.BlockPos position : missing)
                    gate.world.spawnParticle(Particle.DUST, position.x() + 0.5, position.y() + 1.2,
                            position.z() + 0.5, 4, 0.1, 0.1, 0.1,
                            new Particle.DustOptions(Color.fromRGB(0xE53935), 1.0f));
            } else player.sendMessage("Set the 8 note blocks, then play 1 → 3 → 7 → 6 → 5 → 2 → 4 → 8.");
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        melodies.removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (var entity : event.getChunk().getEntities())
            if (transientBeams.remove(entity.getUniqueId())) entity.remove();
    }

    private Gate nearbyGate(Location location, double range) {
        Gate cached = knownGates.values().stream().filter(gate -> gate.world.equals(location.getWorld()))
                .filter(gate -> gate.center().distanceSquared(location) <= range * range)
                .min(Comparator.comparingDouble(gate -> gate.center().distanceSquared(location))).orElse(null);
        if (cached != null) return cached;
        return findNearest(location, (int) Math.ceil(range / 16.0))
                .filter(gate -> gate.center().distanceSquared(location) <= range * range).orElse(null);
    }

    private Gate gateForSonorous(Block block) {
        if (block == null) return null;
        return knownGates.values().stream().filter(gate -> gate.world.equals(block.getWorld()))
                .filter(gate -> gate.frame.sonorousPositions(gate.side).stream().anyMatch(position ->
                        position.x() == block.getX() && position.y() == block.getY() && position.z() == block.getZ()))
                .findFirst().orElse(null);
    }

    private Gate gateForFrameBlock(Block block) {
        return knownGates.values().stream().filter(gate -> gate.world == block.getWorld())
                .filter(gate -> block.getX() >= gate.frame.minX() && block.getX() <= gate.frame.maxX()
                        && block.getY() >= gate.frame.minY() && block.getY() <= gate.frame.maxY()
                        && block.getZ() >= gate.frame.minZ() && block.getZ() <= gate.frame.maxZ())
                .findFirst().orElse(null);
    }

    private void replayMelody(Gate gate) {
        for (int note : AncientCityMelodyTracker.SONG) {
            for (AncientCityFrame.BlockPos position : gate.frame.sonorousPositions(gate.side)) {
                gate.world.spawnParticle(Particle.DUST, position.x() + 0.5, position.y() + 1.1,
                        position.z() + 0.5, 3, 0.1, 0.1, 0.1,
                        new Particle.DustOptions(Color.fromRGB(SonorousPalette.noteRgb(note)), 1.25f));
            }
        }
    }

    private void spawnSonorousBeam(Block base, int note) {
        ItemStack model = items.create("sonorous_deepslate");
        if (model != null) {
            Location location = base.getLocation().add(0.5, 1.0, 0.5);
            ItemDisplay display = base.getWorld().spawn(location, ItemDisplay.class, entity -> {
                entity.setItemStack(model);
                entity.setPersistent(false);
                entity.setGravity(false);
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setDisplayHeight(128.0f);
                entity.setDisplayWidth(0.5f);
                entity.setGlowColorOverride(Color.fromRGB(SonorousPalette.noteRgb(note)));
                entity.setTransformation(new Transformation(new Vector3f(0, 64.0f, 0), new Quaternionf(),
                        new Vector3f(0.3f, 128.0f, 0.3f), new Quaternionf()));
            });
            transientBeams.add(display.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                transientBeams.remove(display.getUniqueId());
                if (display.isValid()) display.remove();
            }, 100L);
            return;
        }
        if (readiness.strict()) {
            plugin.getLogger().severe("Cannot render Sonorous beam: provider item ID 'sonorous_deepslate' failed to resolve.");
            return;
        }
        Color color = Color.fromRGB(SonorousPalette.noteRgb(note));
        for (int step = 1; step <= 16; step++)
            base.getWorld().spawnParticle(Particle.DUST, base.getX() + 0.5, base.getY() + 1.0 + step * 0.5,
                    base.getZ() + 0.5, 2, 0.07, 0.07, 0.07, new Particle.DustOptions(color, 1.2f));
    }

    private void renderFallbackPortalSurfaces() {
        if (readiness.strict()) return;
        for (Gate gate : knownGates.values()) if (gate.open && gate.displayId == null) drawPortalParticles(gate);
    }

    private boolean openPortal(Gate gate, Player opener) {
        if (gate.open) return true;
        if (readiness.strict() && !readiness.dimensionAllowed()) {
            opener.sendMessage("The Sift portal is locked because provider requirements are unresolved. Run /sift doctor.");
            return false;
        }
        World sift = dimensions.getOrCreateWorld();
        if (sift == null) return false;
        Location returnLocation = findSafeLanding(sift, gate.center().getBlockX(), gate.center().getBlockZ(), 12);
        if (returnLocation == null) {
            opener.sendMessage("No safe 1:1 return-portal site is available at these X/Z coordinates in The Sift.");
            return false;
        }
        int returnMinY = returnLocation.getBlockY() - 1;
        AncientCityFrame returnFrame = new AncientCityFrame(gate.frame.minX(), returnMinY, gate.frame.minZ(),
                gate.frame.maxX(), returnMinY + 7, gate.frame.maxZ(), gate.frame.longAxis());
        if (!buildReturnFrame(sift, returnFrame)) {
            opener.sendMessage("The 1:1 return frame could not be built safely; no blocks were changed.");
            return false;
        }
        gate.open = true;
        gate.returnMinY = returnMinY;
        persist(gate);
        Gate returnGate = new Gate(gate.key + ":return", sift, returnFrame, gate.side, true, true, gate.returnMinY);
        returnGate.pairedFrame = gate.frame;
        returnGate.pairedWorld = gate.world;
        knownGates.put(returnGate.key, returnGate);
        persistReturn(gate, sift, returnFrame);
        spawnPortalSurface(gate);
        spawnPortalSurface(returnGate);
        return true;
    }

    private boolean buildReturnFrame(World world, AncientCityFrame frame) {
        loadFrameChunks(world, frame);
        List<Block> frameBlocks = frameBlocks(world, frame);
        if (frameBlocks.size() != 56) return false;
        for (Block block : frameBlocks) if (!block.isPassable() && block.getType() != Material.REINFORCED_DEEPSLATE)
            return false;
        boolean alongX = frame.longAxis() == AncientCityFrame.Axis.X;
        for (int offset = 1; offset < 21; offset++) {
            int x = alongX ? frame.minX() + offset : frame.minX();
            int z = alongX ? frame.minZ() : frame.minZ() + offset;
            Block floor = world.getBlockAt(x, frame.minY() - 1, z);
            if (!floor.getType().isSolid() || floor.isLiquid()) return false;
            for (int y = frame.minY() + 1; y < frame.maxY(); y++)
                if (!world.getBlockAt(x, y, z).isPassable()) return false;
        }
        for (Block block : frameBlocks) block.setType(Material.REINFORCED_DEEPSLATE, false);
        return true;
    }

    private void loadFrameChunks(World world, AncientCityFrame frame) {
        int minX = frame.longAxis() == AncientCityFrame.Axis.X ? frame.minX() : frame.minX();
        int maxX = frame.longAxis() == AncientCityFrame.Axis.X ? frame.maxX() : frame.minX();
        int minZ = frame.longAxis() == AncientCityFrame.Axis.X ? frame.minZ() : frame.minZ();
        int maxZ = frame.longAxis() == AncientCityFrame.Axis.X ? frame.minZ() : frame.maxZ();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
            world.getChunkAt(cx, cz);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            for (int cx = (x - 1) >> 4; cx <= (x + 1) >> 4; cx++)
                for (int cz = (z - 1) >> 4; cz <= (z + 1) >> 4; cz++) world.getChunkAt(cx, cz);
    }

    private List<Block> frameBlocks(World world, AncientCityFrame frame) {
        List<Block> result = new java.util.ArrayList<>();
        boolean alongX = frame.longAxis() == AncientCityFrame.Axis.X;
        for (int y = frame.minY(); y <= frame.maxY(); y++) for (int offset = 0; offset < 22; offset++) {
            boolean edge = y == frame.minY() || y == frame.maxY() || offset == 0 || offset == 21;
            if (!edge) continue;
            int x = alongX ? frame.minX() + offset : frame.minX();
            int z = alongX ? frame.minZ() : frame.minZ() + offset;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return List.of();
            result.add(world.getBlockAt(x, y, z));
        }
        return result;
    }

    private Location findSafeLanding(World world, int x, int z, int verticalRange) {
        int top = world.getHighestBlockYAt(x, z);
        for (int delta = 0; delta <= verticalRange; delta++) {
            int floorY = top + delta;
            if (floorY + 2 >= world.getMaxHeight()) break;
            Block floor = world.getBlockAt(x, floorY, z);
            Block feet = world.getBlockAt(x, floorY + 1, z);
            Block head = world.getBlockAt(x, floorY + 2, z);
            if (floor.getType().isSolid() && !floor.isLiquid() && feet.isPassable() && head.isPassable())
                return new Location(world, x + 0.5, floorY + 1.0, z + 0.5);
        }
        return null;
    }

    private void travel(Player player, Gate source) {
        if (readiness.strict() && !readiness.dimensionAllowed()) {
            player.sendMessage("This gate is locked because required providers are unresolved. Run /sift doctor.");
            plugin.getLogger().severe("Blocked gate travel because strict provider requirements are unresolved; saves were preserved.");
            return;
        }
        travelling.add(player.getUniqueId());
        Location destination = null;
        if (source.world.getKey().equals(SiftDimensionManager.DIMENSION_KEY)) {
            World overworld = source.pairedWorld;
            AncientCityFrame frame = source.pairedFrame;
            if (overworld != null && frame != null) destination = portalLanding(overworld, frame);
        } else {
            World sift = dimensions.getOrCreateWorld();
            if (sift != null && source.returnMinY != null) {
                AncientCityFrame frame = new AncientCityFrame(source.frame.minX(), source.returnMinY,
                        source.frame.minZ(), source.frame.maxX(), source.returnMinY + 7,
                        source.frame.maxZ(), source.frame.longAxis());
                destination = portalLanding(sift, frame);
                if (destination != null && knownGates.values().stream().noneMatch(gate ->
                        gate.world == sift && gate.frame.equals(frame))) {
                    Gate returnGate = new Gate(source.key + ":return", sift, frame, source.side,
                            true, true, source.frame.minY());
                    returnGate.pairedFrame = source.frame;
                    returnGate.pairedWorld = source.world;
                    knownGates.put(returnGate.key, returnGate);
                    persistReturn(source, sift, frame);
                    spawnPortalSurface(returnGate);
                }
            }
        }
        if (destination == null) {
            travelling.remove(player.getUniqueId());
            player.sendMessage("The portal could not find a safe 1:1 landing. No teleport was performed.");
            return;
        }
        if (!player.teleport(destination, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            travelling.remove(player.getUniqueId());
            return;
        }
        player.sendMessage(source.world.getKey().equals(SiftDimensionManager.DIMENSION_KEY)
                ? "You returned to the Overworld through the Ancient City gate."
                : "You crossed the Ancient City gate into The Sift.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> travelling.remove(player.getUniqueId()), 20L);
    }

    private Location portalLanding(World world, AncientCityFrame frame) {
        int x = (frame.minX() + frame.maxX()) >> 1;
        int z = (frame.minZ() + frame.maxZ()) >> 1;
        int feetY = frame.minY() + 1;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        Block feet = world.getBlockAt(x, feetY, z);
        Block head = world.getBlockAt(x, feetY + 1, z);
        Block floor = world.getBlockAt(x, feetY - 1, z);
        if (feet.isPassable() && head.isPassable() && floor.getType().isSolid() && !floor.isLiquid())
            return new Location(world, x + 0.5, feetY, z + 0.5);
        return findSafeLanding(world, x, z, 4);
    }

    private void spawnPortalSurface(Gate gate) {
        ItemStack model = items.create("rift_gate");
        if (model == null) {
            if (readiness.strict()) {
                plugin.getLogger().severe("Cannot render The Sift portal: provider item ID 'rift_gate' failed to resolve.");
                return;
            }
            drawPortalParticles(gate);
            return;
        }
        AncientCityFrame frame = gate.frame;
        Location location = frame.longAxis() == AncientCityFrame.Axis.X
                ? new Location(gate.world, (frame.minX() + frame.maxX() + 1) / 2.0,
                    (frame.minY() + frame.maxY() + 1) / 2.0, frame.minZ() + 0.5)
                : new Location(gate.world, frame.minX() + 0.5,
                    (frame.minY() + frame.maxY() + 1) / 2.0, (frame.minZ() + frame.maxZ() + 1) / 2.0);
        ItemDisplay display = gate.world.spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(model);
            entity.setPersistent(true);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.getPersistentDataContainer().set(portalDisplayKey, PersistentDataType.STRING, gate.key);
            Quaternionf rotation = frame.longAxis() == AncientCityFrame.Axis.X
                    ? new Quaternionf() : new Quaternionf().rotationY((float) (Math.PI / 2.0));
            entity.setTransformation(new Transformation(new Vector3f(), rotation,
                    new Vector3f(20.0f, 6.0f, 0.05f), new Quaternionf()));
            entity.setGlowColorOverride(Color.fromRGB(SonorousPalette.PORTAL_TEAL_RGB));
        });
        gate.displayId = display.getUniqueId();
    }

    private void drawPortalParticles(Gate gate) {
        AncientCityFrame frame = gate.frame;
        boolean alongX = frame.longAxis() == AncientCityFrame.Axis.X;
        for (int x = 0; x < 20; x++) for (int y = 0; y < 6; y++) {
            double px = alongX ? frame.minX() + 1.5 + x : frame.minX() + 0.5;
            double pz = alongX ? frame.minZ() + 0.5 : frame.minZ() + 1.5 + x;
            int color = (x + y) % 5 == 0 ? SonorousPalette.PORTAL_MIST_RGB : SonorousPalette.PORTAL_TEAL_RGB;
            gate.world.spawnParticle(Particle.DUST, px, frame.minY() + 1.5 + y, pz, 1,
                    0.02, 0.02, 0.02, new Particle.DustOptions(Color.fromRGB(color), 1.0f));
        }
    }
}
