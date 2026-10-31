package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.Note;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class RiftManager implements Listener {
    public enum RiftState {
        DORMANT,
        OPENING,
        ACTIVE,
        CLOSING
    }

    private static final String RIFT_KEY_ID = "rift_key";

    private final JavaPlugin plugin;
    private AncientCityGateManager gateOwner;
    private final NamespacedKey riftKey;
    private final SiftMobFactory mobFactory;
    private final ExternalBlockBridge blockBridge;
    private final SonorousBlockRegistry sonorousBlocks;
    private final ProviderReadiness providerReadiness;
    private final Map<UUID, RiftInstance> rifts = new HashMap<>();
    private final Set<UUID> travelling = new HashSet<>();
    private BukkitTask updateTask;
    private final PortalSongTracker portalSongTracker = new PortalSongTracker();
    private final SiftDimensionManager dimensionManager;
    private long nextNaturalRiftAtMillis;
    private final AncientCityPortal ancientCity;
    private final SonorousConsoleTracker consoles = new SonorousConsoleTracker();
    private RiftInstance lastOpened;
    // Lazily shared content registry so repeated item lookups reuse one ExternalItemBridge/cache.
    private SiftiteContent sharedContent;

    /** Shared wiring: reuse the plugin-wide content registry and mob factory when provided. */
    public RiftManager(JavaPlugin plugin, ProviderReadiness providerReadiness, SiftiteContent sharedContent,
                       SiftMobFactory sharedMobFactory) {
        this.plugin = plugin;
        this.providerReadiness = providerReadiness;
        this.dimensionManager = new SiftDimensionManager(plugin, providerReadiness);
        this.riftKey = new NamespacedKey(plugin, "rift_key");
        // Prefer the injected plugin-wide factory; fall back to a local one for standalone use.
        this.mobFactory = sharedMobFactory != null ? sharedMobFactory : new SiftMobFactory(plugin);
        this.blockBridge = new ExternalBlockBridge(plugin);
        this.sonorousBlocks = new SonorousBlockRegistry(plugin);
        this.ancientCity = new AncientCityPortal(plugin);
        // Reuse the plugin-wide content registry when available so item caches stay consistent.
        this.sharedContent = sharedContent;
    }

    /** Wire the gate manager so gate-owned Sonorous blocks are left to it (package-private). */
    void setGateOwner(AncientCityGateManager gateOwner) {
        this.gateOwner = gateOwner;
    }

    public RiftManager(JavaPlugin plugin) {
        this(plugin, new ProviderReadiness(plugin), null, null);
    }

    RiftManager(JavaPlugin plugin, ProviderReadiness providerReadiness) {
        this(plugin, providerReadiness, null, null);
    }

    RiftManager(JavaPlugin plugin, ProviderReadiness providerReadiness, SiftiteContent sharedContent) {
        this(plugin, providerReadiness, sharedContent, null);
    }

    AncientCityPortal ancientCityPortal() {
        return ancientCity;
    }

    private SiftiteContent riftContent() {
        if (sharedContent == null) sharedContent = new SiftiteContent(plugin);
        return sharedContent;
    }

    public void start() {
        // Periodic rift state updates
        if (updateTask == null) updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateRifts, 20L, 20L);
    }

    public void shutdown() {
        if (updateTask != null) updateTask.cancel();
        rifts.values().forEach(RiftInstance::discard);
        rifts.clear();
        travelling.clear();
        for (Player player : Bukkit.getOnlinePlayers()) portalSongTracker.remove(player.getUniqueId());
    }

    public ItemStack createRiftKey() {
        ItemStack key = riftContent().item(Material.TRIAL_KEY, RIFT_KEY_ID, "Rift Key");
        if (key == null) return null;
        ItemMeta meta = key.getItemMeta();
        meta.setDisplayName("§5§lRift Key");
        meta.getPersistentDataContainer().set(riftKey, PersistentDataType.STRING, RIFT_KEY_ID);
        key.setItemMeta(meta);
        return key;
    }

    public boolean isRiftKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return RIFT_KEY_ID.equals(item.getItemMeta().getPersistentDataContainer()
                .get(riftKey, PersistentDataType.STRING));
    }

    public boolean openRift(Location location, Player opener) {
        return openRift(location, opener, false);
    }

    /** portal=true opens a long-lived passage (no mob waves, no rewards) as in the reference mod. */
    public boolean openRift(Location location, Player opener, boolean portal) {
        if (!plugin.getConfig().getBoolean("rift.enabled", true)
                || !plugin.getConfig().getBoolean("content.rift", true)
                || location == null || location.getWorld() == null) return false;
        if (providerReadiness.strict() && !providerReadiness.dimensionAllowed()
                && !location.getWorld().getKey().equals(SiftDimensionManager.DIMENSION_KEY)) {
            String message = "Rift blocked: required providers for The Sift are unavailable. Run /sift doctor.";
            plugin.getLogger().severe(message + " Existing dimension save was left untouched.");
            if (opener != null && opener.isOnline()) opener.sendMessage(message);
            return false;
        }
        World destinationWorld = location.getWorld().getKey().equals(SiftDimensionManager.DIMENSION_KEY)
                ? Bukkit.getWorlds().stream().filter(world -> world.getEnvironment() == World.Environment.NORMAL)
                    .findFirst().orElse(null)
                : dimensionManager.getOrCreateWorld();
        if (destinationWorld == null) {
            String message = "Rift blocked: its destination world could not be loaded. Check the server log and /sift doctor.";
            plugin.getLogger().severe(message);
            if (opener != null && opener.isOnline()) opener.sendMessage(message);
            return false;
        }
        UUID id = UUID.randomUUID();
        RiftInstance rift = new RiftInstance(id, location, opener, portal);
        rifts.put(id, rift);
        lastOpened = rift;
        rift.beginOpening();
        return true;
    }

    private void updateRifts() {
        long timeoutMillis = Math.max(1L, plugin.getConfig().getLong("rift.portal-song-timeout-seconds", 60)) * 1000L;
        portalSongTracker.expire(System.currentTimeMillis(), timeoutMillis);
        if (!plugin.getConfig().getBoolean("rift.enabled", true)) return;
        maybeOpenNaturalRift();

        rifts.entrySet().removeIf(entry -> {
            RiftInstance rift = entry.getValue();
            rift.tick();
            rift.transportNearbyEntities();
            if (rift.state == RiftState.DORMANT) {
                return true; // Remove dormant rifts
            }
            return false;
        });
    }

    /**
     * Mod parity: natural Rifts appear inside The Sift after several minutes and lead back to the
     * Overworld. Only runs while a player is in The Sift; each opens like a normal (temporary) Rift.
     */
    private void maybeOpenNaturalRift() {
        if (!plugin.getConfig().getBoolean("rift.natural.enabled", true)) return;
        World sift = Bukkit.getWorld(SiftDimensionManager.DIMENSION_KEY);
        if (sift == null || sift.getPlayers().isEmpty()) {
            nextNaturalRiftAtMillis = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        int minMinutes = Math.max(1, plugin.getConfig().getInt("rift.natural.min-minutes", 6));
        int maxMinutes = Math.max(minMinutes, plugin.getConfig().getInt("rift.natural.max-minutes", 14));
        if (nextNaturalRiftAtMillis == 0L) {
            nextNaturalRiftAtMillis = now + ThreadLocalRandom.current().nextLong(minMinutes, maxMinutes + 1L) * 60_000L;
            return;
        }
        if (now < nextNaturalRiftAtMillis) return;
        nextNaturalRiftAtMillis = 0L;
        int maxActive = Math.max(1, plugin.getConfig().getInt("rift.natural.max-active", 2));
        long active = rifts.values().stream()
                .filter(rift -> rift.center.getWorld() != null && rift.center.getWorld().equals(sift)
                        && rift.state != RiftState.DORMANT).count();
        if (active >= maxActive) return;
        java.util.List<Player> players = sift.getPlayers();
        Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double distance = ThreadLocalRandom.current().nextDouble(18, 36);
        int x = target.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = target.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        int y = sift.getHighestBlockYAt(x, z) + 1;
        if (openRift(new Location(sift, x + 0.5, y, z + 0.5), target)) {
            target.sendMessage("\u00a75A Rift tears open somewhere nearby...");
        }
    }

    /** Active rifts are two-way passages between the Overworld and The Sift. */
    @EventHandler
    public void onPlayerCrossRift(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        if (travelling.contains(player.getUniqueId())) return;
        for (RiftInstance rift : rifts.values()) {
            if (rift.state != RiftState.ACTIVE || rift.exitCenter == null) continue;
            if (rift.inGate(event.getTo())) {
                travel(player, rift.exitCenter);
                return;
            }
            if (rift.gate == null && nearPortal(event.getTo(), rift.center)) {
                travel(player, rift.exitCenter);
                return;
            }
            if (nearPortal(event.getTo(), rift.exitCenter)) {
                if (rift.gateArrival != null) travelTo(player, rift.gateArrival);
                else travel(player, rift.center);
                return;
            }
        }
    }

    private boolean nearPortal(Location player, Location portal) {
        return player.getWorld().equals(portal.getWorld())
                && player.distanceSquared(portal) <= 2.25;
    }

    private void travel(Entity entity, Location target) {
        // Place travelers outside the destination trigger radius so the paired
        // rifts do not immediately bounce them back and forth.
        travelTo(entity, target.clone().add(3.5, 1, 0.5));
    }

    private void travelTo(Entity entity, Location destination) {
        // Reference-mod parity: the rift carries boats/minecarts together with whoever is
        // riding them - teleport the vehicle, not a rider torn out of it.
        if (entity.isInsideVehicle() && entity.getVehicle() != null
                && !travelling.contains(entity.getVehicle().getUniqueId())) {
            travelTo(entity.getVehicle(), destination);
            return;
        }
        travelling.add(entity.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!entity.isValid() || entity.isDead()) { travelling.remove(entity.getUniqueId()); return; }
            // Passengers dismount on teleport; move them across and remount afterwards.
            java.util.List<Entity> riders = new java.util.ArrayList<>(entity.getPassengers());
            java.util.List<UUID> moved = new java.util.ArrayList<>();
            moved.add(entity.getUniqueId());
            org.bukkit.util.Vector momentum = entity instanceof org.bukkit.entity.Projectile projectile
                    ? projectile.getVelocity() : null;
            entity.teleport(destination);
            if (momentum != null) {
                // Teleport zeroes velocity; a flying projectile must keep its momentum.
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> { if (entity.isValid()) entity.setVelocity(momentum); }, 1L);
            }
            for (Entity rider : riders) {
                if (!rider.isValid() || rider.isDead()) continue;
                moved.add(rider.getUniqueId());
                travelling.add(rider.getUniqueId());
                rider.teleport(destination);
                entity.addPassenger(rider);
                if (rider instanceof Player player) {
                    player.sendMessage("§5The Rift carries you between worlds.");
                    SiftAdvancementGrant.grant(plugin, player, "rifter");
                }
            }
            if (entity instanceof Player player) {
                player.sendMessage("§5The Rift carries you between worlds.");
                SiftAdvancementGrant.grant(plugin, player, "rifter");
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                travelling.removeAll(moved);
            }, 20L);
        });
    }

    @EventHandler
    public void onRiftKeyUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isRiftKey(item)) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BEDROCK) return; // Rift opens on bedrock

        if (!openRift(block.getLocation().add(0, 1, 0), event.getPlayer())) return;
        event.setCancelled(true);
        if (item.getAmount() <= 1) event.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        else item.setAmount(item.getAmount() - 1);
    }

    /**
     * Sift portal interaction, ported from the reference mod: right-click cycles a Sonorous note 1-8,
     * left-click plays it. Each console (a connected group of Sonorous note blocks) keeps a sliding
     * window of its last eight notes; 1,3,7,6,5,2,4,8 ignites the Ancient City frame and the reverse
     * sequence closes it.
     */
    @EventHandler
    public void onPortalSong(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("content.rift", true)
                || !plugin.getConfig().getBoolean("rift.enabled", true)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.NOTE_BLOCK) return;
        Block block = event.getClickedBlock();
        // Gate-owned Sonorous note blocks belong to the Ancient City gate system; the rift
        // portal song must not cancel them away (its handler ran first by registration).
        if (gateOwner != null && gateOwner.ownsSonorous(block)) return;
        if (!canHandlePortalSong(block)) return;
        if (!(block.getBlockData() instanceof NoteBlock noteBlock)) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            int next = (noteBlock.getNote().getId() + 1) % 8;
            noteBlock.setNote(new Note(next));
            block.setBlockData(noteBlock, false);
            event.getPlayer().sendActionBar("§bSift note: " + (next + 1));
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        int played = noteBlock.getNote().getId() + 1;
        player.playSound(block.getLocation(), "minecraft:block.note_block.harp", 0.8f,
                (float) Math.pow(2.0, (played - 1) / 12.0));
        block.getWorld().spawnParticle(Particle.NOTE, block.getLocation().add(0.5, 1.2, 0.5), 1,
                0, 0, 0, (played - 1) / 24.0);
        java.util.List<Block> console = findConsole(block);
        Block anchor = console.stream()
                .min(java.util.Comparator.<Block>comparingInt(Block::getY)
                        .thenComparingInt(Block::getX).thenComparingInt(Block::getZ))
                .orElse(block);
        String key = block.getWorld().getName() + ":" + anchor.getX() + "," + anchor.getY() + "," + anchor.getZ();
        SonorousConsoleTracker.Match match = consoles.record(key, new SonorousConsoleTracker.Note(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), played));
        if (match == SonorousConsoleTracker.Match.OPEN) beginIgnition(player, block, consoles.lastMatch());
        else if (match == SonorousConsoleTracker.Match.CLOSE) closeGateNear(player, block);
    }

    /** Connected Sonorous note blocks (within 16 blocks of each other), like the mod's console group. */
    private java.util.List<Block> findConsole(Block origin) {
        java.util.Set<Block> found = new HashSet<>();
        java.util.ArrayDeque<Block> queue = new java.util.ArrayDeque<>();
        found.add(origin);
        queue.add(origin);
        World world = origin.getWorld();
        while (!queue.isEmpty() && found.size() < 64) {
            Block current = queue.poll();
            for (int dx = -16; dx <= 16; dx++) {
                for (int dy = -16; dy <= 16; dy++) {
                    for (int dz = -16; dz <= 16; dz++) {
                        int x = current.getX() + dx;
                        int y = current.getY() + dy;
                        int z = current.getZ() + dz;
                        if (!world.isChunkLoaded(x >> 4, z >> 4)
                                || world.getType(x, y, z) != Material.NOTE_BLOCK) continue;
                        Block candidate = world.getBlockAt(x, y, z);
                        if (canHandlePortalSong(candidate) && found.add(candidate)) queue.add(candidate);
                    }
                }
            }
        }
        return new java.util.ArrayList<>(found);
    }

    private boolean sequenceIntact(java.util.List<SonorousConsoleTracker.Note> notes) {
        for (SonorousConsoleTracker.Note note : notes) {
            World world = Bukkit.getWorld(note.world());
            if (world == null || !canHandlePortalSong(world.getBlockAt(note.x(), note.y(), note.z()))) return false;
        }
        return true;
    }

    /** Mod timeline: 0.5 s, colour burst; 1 s later the melody replays on its own; then the frame opens. */
    private void beginIgnition(Player player, Block played, java.util.List<SonorousConsoleTracker.Note> notes) {
        AncientCityPortal.Frame frame = ancientCity.findNearestFrame(played.getLocation());
        if (frame == null && plugin.getConfig().getBoolean("ancient-city.require-frame", true)) {
            player.sendActionBar("§7The song echoes, but no Reinforced Deepslate frame answers.");
            return;
        }
        if (frame != null && hasGateAt(frame.bottomCenter())) {
            player.sendActionBar("§7The portal is already open. Play the song backwards to close it.");
            return;
        }
        java.util.List<Location> sources = new java.util.ArrayList<>();
        for (SonorousConsoleTracker.Note note : notes) {
            sources.add(new Location(Bukkit.getWorld(note.world()), note.x() + 0.5, note.y() + 1.2, note.z() + 0.5));
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!sequenceIntact(notes)) return;
            for (Location source : sources) {
                source.getWorld().spawnParticle(Particle.END_ROD, source, 12, 0.3, 0.3, 0.3, 0.05);
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> autoplay(player, frame, played, notes, sources), 20L);
        }, 10L);
    }

    private void autoplay(Player player, AncientCityPortal.Frame frame, Block played,
                          java.util.List<SonorousConsoleTracker.Note> notes, java.util.List<Location> sources) {
        if (!sequenceIntact(notes)) return;
        for (int i = 0; i < notes.size(); i++) {
            final int step = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                SonorousConsoleTracker.Note note = notes.get(step);
                Location at = sources.get(step);
                at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f,
                        (float) Math.pow(2.0, (note.index() - 1) / 12.0));
                at.getWorld().spawnParticle(Particle.NOTE, at, 1, 0, 0, 0, (note.index() - 1) / 24.0);
                if (step == notes.size() - 1) openGate(player, frame, played, sources);
            }, i * 4L);
        }
    }

    private void openGate(Player player, AncientCityPortal.Frame frame, Block played, java.util.List<Location> sources) {
        Location at = frame != null ? frame.bottomCenter() : played.getLocation().add(0.5, 1, 0.5);
        if (!openRift(at, player, true)) {
            if (player.isOnline()) player.sendActionBar("§cThe Rift could not open; check provider readiness.");
            return;
        }
        RiftInstance rift = lastOpened;
        if (frame != null && rift != null) {
            rift.setGate(frame.gate(), frame.arrival(frame.sideOf(played.getLocation())));
            ancientCity.fireBeams(sources, at);
        }
        if (player.isOnline()) player.sendActionBar("§bThe Sonorous song is complete.");
    }

    private boolean hasGateAt(Location location) {
        for (RiftInstance rift : rifts.values()) {
            if (rift.gate != null && rift.state != RiftState.DORMANT && rift.center.getWorld() != null
                    && rift.center.getWorld().equals(location.getWorld())
                    && rift.center.distanceSquared(location) < 4.0) return true;
        }
        return false;
    }

    private void closeGateNear(Player player, Block block) {
        double radius = Math.max(8, plugin.getConfig().getInt("ancient-city.scan-radius", 40));
        for (RiftInstance rift : rifts.values()) {
            if (rift.gate == null || rift.state != RiftState.ACTIVE || rift.center.getWorld() == null
                    || !rift.center.getWorld().equals(block.getWorld())
                    || rift.center.distanceSquared(block.getLocation()) > radius * radius) continue;
            rift.beginClosing();
            player.sendActionBar("§5The portal folds back into the deepslate.");
            return;
        }
    }

    /** Mod advancement "Rift Resonance": play the Rift disc in a jukebox near an active Rift. */
    @EventHandler
    public void onRiftDisc(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.JUKEBOX) return;
        ItemStack held = event.getItem();
        if (held == null || !held.hasItemMeta()
                || !"rift_disc".equals(held.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "content"), PersistentDataType.STRING))) return;
        // Reference-mod parity: the disc goes into an EMPTY jukebox within 16 blocks of an
        // open rift (the plugin previously accepted 48 and never checked the jukebox).
        org.bukkit.block.Jukebox jukebox = (org.bukkit.block.Jukebox) event.getClickedBlock().getState();
        if (jukebox.getRecord() != null && !jukebox.getRecord().getType().isAir()) return;
        Location at = event.getClickedBlock().getLocation();
        for (RiftInstance rift : rifts.values()) {
            if (rift.state == RiftState.ACTIVE && rift.center.getWorld() != null
                    && rift.center.getWorld().equals(at.getWorld())
                    && rift.center.distanceSquared(at) <= 16.0 * 16.0) {
                SiftAdvancementGrant.grant(plugin, event.getPlayer(), "rift_resonance");
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        portalSongTracker.remove(event.getPlayer().getUniqueId());
    }

    boolean canHandlePortalSong(Block noteBlock) {
        if (!plugin.getConfig().getBoolean("content.sonorous_deepslate", true)) return false;
        if (noteBlock == null || noteBlock.getType() != Material.NOTE_BLOCK) return false;
        Block base = noteBlock.getRelative(org.bukkit.block.BlockFace.DOWN);
        return !blockBridge.isProviderOwned(noteBlock) && sonorousBlocks.contains(base);
    }

    public class RiftInstance {
        private final UUID id;
        private final Location center;
        private final Player opener;
        private final boolean portal;
        private Location exitCenter;
        private org.bukkit.util.BoundingBox gate;
        private Location gateArrival;
        private RiftState state = RiftState.DORMANT;
        private int stateTimer = 0;
        private int phase = 0;

        public RiftInstance(UUID id, Location center, Player opener) {
            this(id, center, opener, false);
        }

        public RiftInstance(UUID id, Location center, Player opener, boolean portal) {
            this.id = id;
            this.center = center;
            this.opener = opener;
            this.portal = portal;
        }

        void setGate(org.bukkit.util.BoundingBox gate, Location arrival) {
            this.gate = gate;
            this.gateArrival = arrival;
        }

        boolean inGate(Location location) {
            return gate != null && location.getWorld() != null && location.getWorld().equals(center.getWorld())
                    && gate.clone().expand(0.3).contains(location.getX(), location.getY() + 0.9, location.getZ());
        }

        public void beginOpening() {
            state = RiftState.OPENING;
            stateTimer = 0;
            phase = 0;
            center.getWorld().playSound(center, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.5f);
            center.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(0, 1, 0), 50, 2, 2, 2, 0.1);
            notifyOpener("§5The rift begins to tear open...");
            
        }

        private void becomeActive() {
            state = RiftState.ACTIVE;
            stateTimer = 0;
            phase = 1;
            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.7f);
            center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 5);
            center.getWorld().spawnParticle(Particle.DRAGON_BREATH, center.clone().add(0, 1, 0), 100, 3, 3, 3, 0.2);
            notifyOpener("§5§lTHE RIFT HAS OPENED! §r§5Something stirs within...");

            createExit();

            // Spawn initial mobs from the configured value.
            if (!portal) spawnRiftMobs(Math.max(0, plugin.getConfig().getInt("rift.initial-mobs", 3)));
        }

        private void createExit() {
            World sourceWorld = center.getWorld();
            World targetWorld = sourceWorld.getKey().equals(SiftDimensionManager.DIMENSION_KEY)
                    ? Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                        .findFirst().orElse(null)
                    : dimensionManager.getOrCreateWorld();
            if (targetWorld != null && targetWorld.getKey().equals(SiftDimensionManager.DIMENSION_KEY)) {
                if (providerReadiness.strict() && !providerReadiness.dimensionAllowed()) {
                    plugin.getLogger().severe("Blocked Rift destination into The Sift: required providers are unavailable. Run /sift doctor. Existing world data is preserved.");
                    return;
                }
            }
            if (targetWorld == null) return;
            int x = center.getBlockX();
            int z = center.getBlockZ();
            int y = Math.max(targetWorld.getMinHeight() + 2,
                    Math.min(targetWorld.getHighestBlockYAt(x, z) + 1, targetWorld.getMaxHeight() - 3));
            exitCenter = new Location(targetWorld, x, y, z);
            targetWorld.spawnParticle(Particle.REVERSE_PORTAL, exitCenter, 45, 1.2, 1.5, 1.2, 0.05);
            targetWorld.playSound(exitCenter, Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 0.8f);
        }

        private void pulseResonance() {
            center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.6f);
            center.getWorld().spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 1, 0), 1);
            for (Player p : center.getWorld().getPlayers()) {
                double range = plugin.getConfig().getDouble("rift.resonance-range", 20.0);
                if (p.getLocation().distanceSquared(center) <= range * range) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
                }
            }
        }

        private void spawnRiftMobs(int count) {
            for (int i = 0; i < count; i++) {
                Location spawnLoc = center.clone().add(
                    ThreadLocalRandom.current().nextDouble(-5, 5),
                    0,
                    ThreadLocalRandom.current().nextDouble(-5, 5)
                );
            spawnLoc = spawnLoc.getWorld().getHighestBlockAt(spawnLoc).getLocation().add(0.5, 1, 0.5);
                
                // Spawn Enderman-like or custom mob
                Entity entity = mobFactory.spawn("rift_walker", spawnLoc, EntityType.ENDERMAN);
                if (entity == null) continue;
                entity.setCustomName("§5Rift Walker");
                entity.setCustomNameVisible(true);
            }
        }

        private void beginClosing() {
            state = RiftState.CLOSING;
            stateTimer = 0;
            center.getWorld().playSound(center, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.8f);
            center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 1, 0), 100, 3, 3, 3, 0.2);
            notifyOpener("§5The rift begins to seal...");

        }

        private void close() {
            state = RiftState.DORMANT;
            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 0.5f);
            center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 3);
            center.getWorld().spawnParticle(Particle.SMOKE, center.clone().add(0, 1, 0), 50, 2, 2, 2, 0.1);
            notifyOpener("§5The rift has sealed. §7Rewards await the brave...");

            // Drop rewards
            if (!portal) dropRewards();
            if (exitCenter != null) {
                exitCenter.getWorld().spawnParticle(Particle.SMOKE, exitCenter, 30, 1, 1, 1, 0.03);
                exitCenter.getWorld().playSound(exitCenter, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 0.5f);
            }
        }

        private void dropRewards() {
            // Drop Siftite, Charoite, etc.
            int rewardCount = Math.max(0, plugin.getConfig().getInt("rift.reward-drops", 3));
            SiftiteContent content = riftContent();
            ItemStack reward = rewardCount == 0 ? null
                    : content.item(Material.NETHERITE_INGOT, "siftite_ingot", "Siftite Ingot");
            if (rewardCount > 0 && reward == null) {
                plugin.getLogger().warning("Skipped Rift Siftite rewards because item ID 'siftite_ingot' did not resolve.");
            } else for (int i = 0; i < rewardCount; i++) {
                Location dropLoc = center.clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 1,
                        ThreadLocalRandom.current().nextDouble(-2, 2));
                dropLoc.getWorld().dropItemNaturally(dropLoc, reward.clone());
            }
            // Rift Key fragment chance
            if (ThreadLocalRandom.current().nextDouble()
                    < plugin.getConfig().getDouble("rift.rift-key-chance", 0.3)) {
                ItemStack key = createRiftKey();
                if (key != null) center.getWorld().dropItemNaturally(center.clone().add(0, 1, 0), key);
            }
        }

        public void forceClose() {
            if (state != RiftState.DORMANT) {
                close();
            }
        }

        private void discard() {
            state = RiftState.DORMANT;
        }

        public void tick() {
            if (state == RiftState.OPENING) {
                stateTimer++;
                int seconds = Math.max(1, plugin.getConfig().getInt("rift.opening-duration-seconds", 5));
                if (stateTimer >= seconds) becomeActive();
                else center.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                        center.clone().add(0, 1, 0), 20 + stateTimer * 4, 1, 1, 1, 0.05);
            } else if (state == RiftState.ACTIVE) {
                stateTimer++;
                int duration = portal
                        ? Math.max(1, plugin.getConfig().getInt("rift.portal-active-seconds", 900))
                        : Math.max(1, plugin.getConfig().getInt("rift.active-duration-seconds", 300));
                int interval = Math.max(1, plugin.getConfig().getInt("rift.mob-spawn-interval-seconds", 60));
                int pulseInterval = Math.max(1,
                        plugin.getConfig().getInt("rift.resonance-pulse-interval-seconds", 60));
                if (gate == null && stateTimer >= duration) beginClosing();
                else {
                    if (!portal && stateTimer % interval == 0) {
                        spawnRiftMobs(Math.max(0, plugin.getConfig().getInt("rift.periodic-mobs", 1)));
                    }
                    if (!portal && stateTimer % pulseInterval == 0) pulseResonance();
                }
            } else if (state == RiftState.CLOSING) {
                stateTimer++;
                if (stateTimer >= Math.max(1, plugin.getConfig().getInt("rift.closing-duration-seconds", 5))) close();
                else center.getWorld().spawnParticle(Particle.PORTAL,
                        center.clone().add(0, 1, 0), 20, 2, 2, 2, 0.05);
            }
            // Passive ambient effects during active phase
            if (state == RiftState.ACTIVE && gate != null) {
                center.getWorld().spawnParticle(Particle.PORTAL, gate.getCenter().toLocation(center.getWorld()),
                        10, gate.getWidthX() / 2.0, gate.getHeight() / 2.0, gate.getWidthZ() / 2.0, 0.1);
            }
            if (state == RiftState.ACTIVE && ThreadLocalRandom.current().nextDouble() < 0.1) {
                center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0, 1, 0), 5, 1, 1, 1, 0.02);
            }
        }

        private void transportNearbyEntities() {
            if (state != RiftState.ACTIVE || exitCenter == null) return;
            if (gate != null) {
                for (Entity entity : center.getWorld().getNearbyEntities(gate.clone().expand(0.3))) {
                    if (entity.isDead() || travelling.contains(entity.getUniqueId())) continue;
                    travel(entity, exitCenter);
                }
                if (exitCenter.getWorld() != null) {
                    for (Entity entity : exitCenter.getWorld().getNearbyEntities(exitCenter, 1.4, 1.8, 1.4)) {
                        if (entity.isDead() || travelling.contains(entity.getUniqueId())) continue;
                        travelTo(entity, gateArrival != null ? gateArrival : center.clone().add(3.5, 1, 0.5));
                    }
                }
                return;
            }
            transportFrom(center, exitCenter);
            transportFrom(exitCenter, center);
        }

        private void transportFrom(Location from, Location to) {
            for (Entity entity : from.getWorld().getNearbyEntities(from, 1.4, 1.8, 1.4)) {
                if (entity.isDead() || travelling.contains(entity.getUniqueId())) continue;
                travel(entity, to);
            }
        }

        private void notifyOpener(String message) {
            if (opener != null && opener.isOnline()) opener.sendMessage(message);
        }
    }
}
