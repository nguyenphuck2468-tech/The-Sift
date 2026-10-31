package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.util.Vector;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Early vanilla-backed approximation of The Sift's world generation and living content. */
public final class SiftEcologyManager implements Listener {
    private static final String DIMENSION = "thesift:the_sift";
    private static final byte CHUNK_DECORATION_IN_PROGRESS = 1;
    private static final byte CHUNK_DECORATION_COMPLETE = 2;

    private final JavaPlugin plugin;
    private final ResonanceManager resonanceManager;
    private final EchoGolemManager echoGolemManager;
    private final RiftManager riftManager;
    private final NamespacedKey decoratedChunkKey;
    private final NamespacedKey decorationRetryAttemptsKey;
    private final NamespacedKey decorationRetryAtKey;
    private final NamespacedKey creatureKey;
    private final NamespacedKey blubOwnerKey;
    private final NamespacedKey blubStayKey;
    private final SpearThrowHandler spearThrowHandler;
    private final NamespacedKey ichorSnowballAtKey;
    private final NamespacedKey ichorContentKey;
    private final NamespacedKey snifferCorruptionStartKey;
    private final NamespacedKey snifferFleePathAtKey;
    private final NamespacedKey darkSnifferSonicAtKey;
    private final NamespacedKey darkSnifferSonicTargetKey;
    private final NamespacedKey darkSnifferCombatTargetKey;
    private final NamespacedKey darkSnifferHomeXKey;
    private final NamespacedKey darkSnifferHomeYKey;
    private final NamespacedKey darkSnifferHomeZKey;
    private final NamespacedKey darkSnifferIdlePathAtKey;
    private final NamespacedKey darkSnifferTargetRefreshKey;
    private final NamespacedKey darkSnifferPathRefreshKey;
    private final NamespacedKey darkSnifferAttackAtKey;
    private final NamespacedKey darkSnifferNoMeleeTicksKey;
    private final NamespacedKey darkSnifferSonicWindupKey;
    private final NamespacedKey darkSnifferWaveStartKey;
    private final NamespacedKey darkSnifferWaveOriginXKey;
    private final NamespacedKey darkSnifferWaveOriginYKey;
    private final NamespacedKey darkSnifferWaveOriginZKey;
    private final NamespacedKey darkSnifferWaveDirectionXKey;
    private final NamespacedKey darkSnifferWaveDirectionYKey;
    private final NamespacedKey darkSnifferWaveDirectionZKey;
    private final NamespacedKey darkSnifferWaveFlightTicksKey;
    private final NamespacedKey darkSnifferWaveIdKey;
    private final NamespacedKey darkSnifferWavePulledKey;
    private final NamespacedKey wardenPettingTargetKey;
    private final NamespacedKey wardenPettingSearchAtKey;
    private final NamespacedKey wardenPettingApproachAtKey;
    private final NamespacedKey wardenPettingStartKey;
    private final NamespacedKey wardenPettingHeartAtKey;
    private final NamespacedKey darkSnifferPettingWardenKey;
    /** Last herd anchor per world so spawned Sifters regroup instead of scattering (mod parity). */
    // Reference-mod parity (Sifter): herds of at most five around per-herd anchors.
    /** The five reference-mod mobs; never gated by the fan-made flag. */
    private static final java.util.Set<String> FAN_CORE_MOBS = java.util.Set.of(
            "singer", "echo_golem", "blub", "sifter", "dark_sniffer");

    private final java.util.Map<java.util.UUID, java.util.List<Location>> sifterHerdAnchors = new ConcurrentHashMap<>();
    private final SiftMobFactory mobFactory;
    private final ExternalBlockBridge blockBridge;
    private final SiftiteContent content;
    private final BukkitTask tideTask;
    private final BukkitTask darkSnifferCombatTask;
    private final AtomicLong providerPlacementFailures = new AtomicLong();
    private final AtomicLong deferredDecorationRetries = new AtomicLong();
    private final java.util.Map<String, AtomicLong> providerPlacementFailuresById = new ConcurrentHashMap<>();
    private volatile String lastProviderPlacementFailure = "none";
    private long tideTick;

    public SiftEcologyManager(JavaPlugin plugin, ResonanceManager resonanceManager,
                              EchoGolemManager echoGolemManager, RiftManager riftManager) {
        this(plugin, resonanceManager, echoGolemManager, riftManager, null, null);
    }

    /** Shared wiring: reuse the plugin-wide SiftiteContent/SiftMobFactory when available. */
    SiftEcologyManager(JavaPlugin plugin, ResonanceManager resonanceManager,
                       EchoGolemManager echoGolemManager, RiftManager riftManager,
                       SiftiteContent sharedContent, SiftMobFactory sharedMobFactory) {
        this.plugin = plugin;
        this.resonanceManager = resonanceManager;
        this.echoGolemManager = echoGolemManager;
        this.riftManager = riftManager;
        this.decoratedChunkKey = new NamespacedKey(plugin, "sift_ecology_generated");
        this.decorationRetryAttemptsKey = new NamespacedKey(plugin, "sift_ecology_retry_attempts");
        this.decorationRetryAtKey = new NamespacedKey(plugin, "sift_ecology_retry_at");
        this.creatureKey = new NamespacedKey(plugin, "sift_creature");
        this.blubOwnerKey = new NamespacedKey(plugin, "blub_owner");
        this.blubStayKey = new NamespacedKey(plugin, "blub_stay");
        this.spearThrowHandler = new SpearThrowHandler(plugin);
        this.ichorSnowballAtKey = new NamespacedKey(plugin, "ichor_snowball_last_throw");
        // Same namespace:name pair IchorContent uses for its content tag, so the snowball
        // handlers can read IchorContent-tagged items without an IchorContent reference.
        this.ichorContentKey = new NamespacedKey(plugin, "content");
        this.snifferCorruptionStartKey = new NamespacedKey(plugin, "sniffer_corruption_start");
        this.snifferFleePathAtKey = new NamespacedKey(plugin, "sniffer_flee_path_at");
        this.darkSnifferSonicAtKey = new NamespacedKey(plugin, "dark_sniffer_sonic_at");
        this.darkSnifferSonicTargetKey = new NamespacedKey(plugin, "dark_sniffer_sonic_target");
        this.darkSnifferCombatTargetKey = new NamespacedKey(plugin, "dark_sniffer_combat_target");
        this.darkSnifferHomeXKey = new NamespacedKey(plugin, "dark_sniffer_home_x");
        this.darkSnifferHomeYKey = new NamespacedKey(plugin, "dark_sniffer_home_y");
        this.darkSnifferHomeZKey = new NamespacedKey(plugin, "dark_sniffer_home_z");
        this.darkSnifferIdlePathAtKey = new NamespacedKey(plugin, "dark_sniffer_idle_path_at");
        this.darkSnifferTargetRefreshKey = new NamespacedKey(plugin, "dark_sniffer_target_refresh");
        this.darkSnifferPathRefreshKey = new NamespacedKey(plugin, "dark_sniffer_path_refresh");
        this.darkSnifferAttackAtKey = new NamespacedKey(plugin, "dark_sniffer_attack_at");
        this.darkSnifferNoMeleeTicksKey = new NamespacedKey(plugin, "dark_sniffer_no_melee_ticks");
        this.darkSnifferSonicWindupKey = new NamespacedKey(plugin, "dark_sniffer_sonic_windup");
        this.darkSnifferWaveStartKey = new NamespacedKey(plugin, "dark_sniffer_wave_start");
        this.darkSnifferWaveOriginXKey = new NamespacedKey(plugin, "dark_sniffer_wave_origin_x");
        this.darkSnifferWaveOriginYKey = new NamespacedKey(plugin, "dark_sniffer_wave_origin_y");
        this.darkSnifferWaveOriginZKey = new NamespacedKey(plugin, "dark_sniffer_wave_origin_z");
        this.darkSnifferWaveDirectionXKey = new NamespacedKey(plugin, "dark_sniffer_wave_direction_x");
        this.darkSnifferWaveDirectionYKey = new NamespacedKey(plugin, "dark_sniffer_wave_direction_y");
        this.darkSnifferWaveDirectionZKey = new NamespacedKey(plugin, "dark_sniffer_wave_direction_z");
        this.darkSnifferWaveFlightTicksKey = new NamespacedKey(plugin, "dark_sniffer_wave_flight_ticks");
        this.darkSnifferWaveIdKey = new NamespacedKey(plugin, "dark_sniffer_wave_id");
        this.darkSnifferWavePulledKey = new NamespacedKey(plugin, "dark_sniffer_wave_pulled");
        this.wardenPettingTargetKey = new NamespacedKey(plugin, "warden_petting_target");
        this.wardenPettingSearchAtKey = new NamespacedKey(plugin, "warden_petting_search_at");
        this.wardenPettingApproachAtKey = new NamespacedKey(plugin, "warden_petting_approach_at");
        this.wardenPettingStartKey = new NamespacedKey(plugin, "warden_petting_start");
        this.wardenPettingHeartAtKey = new NamespacedKey(plugin, "warden_petting_heart_at");
        this.darkSnifferPettingWardenKey = new NamespacedKey(plugin, "dark_sniffer_petting_warden");
        // Shared instances are injected by the plugin lifecycle; fall back to local ones only
        // for standalone/test construction.
        this.mobFactory = sharedMobFactory != null ? sharedMobFactory : new SiftMobFactory(plugin);
        this.blockBridge = new ExternalBlockBridge(plugin);
        this.content = sharedContent != null ? sharedContent : new SiftiteContent(plugin);
        this.tideTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.getConfig().getBoolean("content.sift_tide", false)) tickTide();
            tickIchorWeather();
            tickDarkSniffers();
            tickBlubs();
            tickSifterHerds();
            tickIchorCauldrons();
        }, 100L, 100L);
        this.darkSnifferCombatTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickDarkSnifferCombat();
            tickWardenPetting();
        }, 1L, 1L);
    }

    public void shutdown() {
        tideTask.cancel();
        darkSnifferCombatTask.cancel();
        sifterHerdAnchors.clear();
    }

    public void populateLoadedSpawnChunks() {
        World world = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (world != null) {
            for (Chunk chunk : world.getLoadedChunks()) populate(chunk);
        }
    }

    @EventHandler
    public void onNewSiftChunk(ChunkLoadEvent event) {
        populate(event.getChunk());
    }

    // Wildlife index -----------------------------------------------------------
    // The periodic tick loops used to call World#getEntities() (a full copy of every loaded
    // entity) on every run - the combat and petting loops even run each tick. This index maps
    // creature tag (or entity type for untagged stand-ins) to UUIDs, maintained by Paper's
    // add/remove-to-world events, so ticks resolve a handful of Bukkit.getEntity lookups.

    private final java.util.Map<String, java.util.Set<UUID>> wildlifeIndex = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> indexedWorlds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private String wildlifeKey(Entity entity) {
        if (entity instanceof Player) return null;
        String id = entity.getPersistentDataContainer().get(creatureKey, PersistentDataType.STRING);
        return id != null ? id : "type:" + entity.getType().name();
    }

    @EventHandler
    public void onEntityAddToWorld(com.destroystokyo.paper.event.entity.EntityAddToWorldEvent event) {
        String key = wildlifeKey(event.getEntity());
        if (key != null) wildlifeIndex.computeIfAbsent(key, k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldUnload(org.bukkit.event.world.WorldUnloadEvent event) {
        // Herd anchors hold Location references to the world; drop them on unload.
        sifterHerdAnchors.remove(event.getWorld().getUID());
        indexedWorlds.remove(event.getWorld().getUID());
    }

    @EventHandler
    public void onEntityRemoveFromWorld(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) {
        String key = wildlifeKey(event.getEntity());
        if (key != null) wildlifeIndex.computeIfAbsent(key, k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .remove(event.getEntity().getUniqueId());
    }

    /** One-time seed per world: entities loaded before this listener registered are not indexed. */
    private void ensureIndexed(World sift) {
        if (!indexedWorlds.add(sift.getUID())) return;
        for (Entity entity : sift.getEntities()) {
            String key = wildlifeKey(entity);
            if (key != null) wildlifeIndex.computeIfAbsent(key, k -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                    .add(entity.getUniqueId());
        }
    }

    /** Live tagged creatures of one species in the world, without a full entity scan. */
    private java.util.List<Entity> indexedMobs(World sift, String... keys) {
        ensureIndexed(sift);
        java.util.List<Entity> result = new java.util.ArrayList<>();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (String key : keys) {
            for (UUID uuid : java.util.List.copyOf(wildlifeIndex.getOrDefault(key, java.util.Set.of()))) {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity == null || entity.isDead() || !entity.isValid()
                        || entity.getWorld() != sift) {
                    java.util.Set<UUID> bucket = wildlifeIndex.get(key);
                    if (bucket != null) {
                        bucket.remove(uuid);
                        if (bucket.isEmpty()) wildlifeIndex.remove(key);
                    }
                    continue;
                }
                String current = wildlifeKey(entity);
                if (!key.equals(current)) {
                    // The creature tag is written right after factory spawn, so the
                    // add-to-world event indexed the fresh mob under its vanilla type.
                    // Re-file the UUID under the tag it carries now - the add event does
                    // not fire twice, so without this the species ticks never see it.
                    wildlifeIndex.getOrDefault(key, java.util.Set.of()).remove(uuid);
                    if (current != null && !current.startsWith("type:")) {
                        wildlifeIndex.computeIfAbsent(current,
                                k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(uuid);
                    }
                    continue;
                }
                if (seen.add(uuid)) result.add(entity);
            }
        }
        return result;
    }

    private void populate(Chunk chunk) {
        if (!isSift(chunk.getWorld())) return;
        if (ChunkRetryBackoff.isDeferred(chunk, decorationRetryAtKey, System.currentTimeMillis())) {
            deferredDecorationRetries.incrementAndGet();
            return;
        }
        // Skip the entity snapshot entirely for chunks that already finished decorating:
        // every reload of a decorated chunk otherwise pays a full chunk entity scan before
        // decorateChunkOnce rejects it.
        Byte decorated = chunk.getPersistentDataContainer().get(decoratedChunkKey, PersistentDataType.BYTE);
        if (decorated != null && decorated == CHUNK_DECORATION_COMPLETE) return;

        boolean measure = plugin.getConfig().getBoolean("debug.chunk-generation-metrics.enabled", false);
        long started = measure ? System.nanoTime() : 0L;
        Set<UUID> existingEntities = new HashSet<>();
        for (Entity entity : chunk.getEntities()) existingEntities.add(entity.getUniqueId());
        int[] generationOperations = {0};
        try {
            boolean completed = decorateChunkOnce(chunk, decoratedChunkKey,
                    () -> generationOperations[0] = decorateChunkContent(chunk));
            if (!completed) return;
            ChunkRetryBackoff.clear(chunk, decorationRetryAttemptsKey, decorationRetryAtKey);
        } catch (RuntimeException exception) {
            for (Entity entity : chunk.getEntities()) {
                if (!(entity instanceof Player) && !existingEntities.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
            long delay = ChunkRetryBackoff.recordFailure(chunk, decorationRetryAttemptsKey,
                    decorationRetryAtKey, System.currentTimeMillis());
            int attempts = ChunkRetryBackoff.attempts(chunk, decorationRetryAttemptsKey);
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Sift chunk decoration failed for " + chunk.getWorld().getName() + ":"
                            + chunk.getX() + ":" + chunk.getZ() + " (attempt " + attempts
                            + "); retry deferred for " + (delay / 1000) + "s. Successful earlier block placements "
                            + "are retained; the provider must be idempotent at a location.", exception);
            return;
        }

        if (measure) {
            double elapsedMillis = (System.nanoTime() - started) / 1_000_000.0;
            plugin.getLogger().info("Sift chunk " + chunk.getWorld().getName() + ":" + chunk.getX() + ":"
                    + chunk.getZ() + " generation=" + String.format(Locale.ROOT, "%.3f", elapsedMillis)
                    + "ms operation-attempts=" + generationOperations[0]);
        }
    }

    static boolean decorateChunkOnce(Chunk chunk, NamespacedKey markerKey, Runnable decoration) {
        Byte state = chunk.getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        if (state != null && state == CHUNK_DECORATION_COMPLETE) return false;
        chunk.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, CHUNK_DECORATION_IN_PROGRESS);
        try {
            decoration.run();
            chunk.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, CHUNK_DECORATION_COMPLETE);
            return true;
        } catch (RuntimeException exception) {
            chunk.getPersistentDataContainer().remove(markerKey);
            throw exception;
        }
    }

    private int decorateChunkContent(Chunk chunk) {
        int generationOperations = 0;
        long seed = chunk.getWorld().getSeed() ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L);
        Random random = new Random(seed);
        // Sample the surface biome, not the world's bottom Y. Modern 3D biome noise can
        // assign a cave biome at minHeight even when the surface belongs to an overgrown
        // forest or another Sift biome; using that sample misroutes all chunk decoration.
        int sampleX = chunk.getX() * 16 + 8;
        int sampleZ = chunk.getZ() * 16 + 8;
        int surfaceY = chunk.getWorld().getHighestBlockYAt(sampleX, sampleZ);
        surfaceY = Math.max(chunk.getWorld().getMinHeight(),
                Math.min(surfaceY, chunk.getWorld().getMaxHeight() - 1));
        Biome biome = chunk.getBlock(8, surfaceY, 8).getBiome();
        String key = biome.getKey().toString().toLowerCase(Locale.ROOT);

        // Strict mode prefers provider-owned content; the vanilla-safe landmarks below it
        // run in both modes, and provider-mapped placements fail-and-retry until resolved.
        if (plugin.getConfig().getBoolean("providers.strict", true)) {
            decorateStrictProviderContent(chunk, key, random);
            return 1;
        }

        decorateSurface(chunk, key, random);
        generationOperations += 28; // attempted surface decoration positions
        growBiomeLandmarks(chunk, key, random);
        generationOperations++;
        generateCharoiteOre(chunk, random);
        generationOperations++;
        generateSiftResources(chunk, random);
        generationOperations++;
        generateSiftiteOre(chunk, random);
        generationOperations++;
        if ((key.contains("overgrown") || key.contains("sift_wastes") || key.contains("siftslate"))
                && random.nextInt(18) == 0) {
            generateAbandonedPortal(chunk, random);
            generationOperations++;
        }
        if ((key.contains("overgrown") || (key.endsWith("carapace") || key.endsWith("sift_wastes"))) && random.nextInt(24) == 0) {
            generateIchorSpring(chunk, random);
            generationOperations++;
        }
        if (random.nextInt(32) == 0) {
            if (key.contains("deep_dark")) generateSnifferCave(chunk, random);
            else if (key.contains("siftslate") || key.contains("wastes")) generateSoulCanyon(chunk, random);
            else if (key.contains("overgrown_peaks") || key.contains("ichor_snowy_peaks")) {
                generateCliffArch(chunk, random);
            } else if (key.contains("overgrown")) generateMonolith(chunk, random);
        }
        if (random.nextInt(48) == 0) generateFossil(chunk, random);
        spawnSiftWildlife(chunk, key, random);
        generationOperations++;
        if ((key.endsWith("pink_meadows") || key.endsWith("overgrown_clearing")) && random.nextInt(22) == 0) {
            summonSinger(chunk, random);
            generationOperations++;
        } else if ((key.endsWith("crystal_spires") || key.endsWith("siftslate_slopes")) && random.nextInt(55) == 0) {
            summonEchoGolem(chunk, random);
            generationOperations++;
        }
        if ((key.endsWith("shattered_peaks") || key.endsWith("siftslate_peaks")) && plugin.getConfig().getBoolean("content.rift-events", true)
                && random.nextInt(120) == 0) {
            openNaturalRift(chunk);
            generationOperations++;
        }
        if ((key.endsWith("bulb_forest") || key.endsWith("overgrown_forest")) && random.nextInt(160) == 0) {
            spawnBoss(chunk, EntityType.WARDEN, "Prism Warden", "prism_warden", 180.0, 28.0);
            generationOperations++;
        } else if ((key.endsWith("shattered_peaks") || key.endsWith("siftslate_peaks")) && random.nextInt(120) == 0) {
            // Spire Tyrant variant: crimson-named Sifter boss in the peaks; killing it grants
            // the bundled "tyrant" advancement (see SiftAdvancementManager.onCreatureDeath).
            boolean tyrant = plugin.getConfig().getBoolean("content.spire_tyrant", false)
                    && random.nextBoolean();
            spawnBoss(chunk, EntityType.RAVAGER,
                    tyrant ? "Spire Tyrant" : "The Sifter",
                    tyrant ? "spire_tyrant" : "the_sifter", 220.0, 24.0);
            generationOperations++;
        }
        return generationOperations;
    }

    private void decorateStrictProviderContent(Chunk chunk, String biome, Random random) {
        decorateStrictSurface(chunk, biome, random);
        growStrictProviderLandmark(chunk, biome, random);
        generateCharoiteOre(chunk, random);
        generateSiftiteOre(chunk, random);
        // Landmarks the fallback branch generates were missing entirely under strict mode.
        // These are vanilla-safe (deepslate/tuff/soul sand structures) except the Ichor
        // spring and portal-ruin chest, whose provider-aware placements fail-and-retry by
        // design until a provider resolves - the same behaviour as any strict decoration.
        if ((biome.contains("overgrown") || biome.endsWith("carapace") || biome.endsWith("sift_wastes"))
                && random.nextInt(24) == 0) generateIchorSpring(chunk, random);
        if (biome.contains("overgrown") && random.nextInt(18) == 0) generateAbandonedPortal(chunk, random);
        if (random.nextInt(32) == 0) {
            if (biome.contains("deep_dark")) generateSnifferCave(chunk, random);
            else if (biome.contains("siftslate") || biome.contains("wastes")) generateSoulCanyon(chunk, random);
            else if (biome.contains("overgrown_peaks") || biome.contains("ichor_snowy_peaks")) {
                generateCliffArch(chunk, random);
            } else if (biome.contains("overgrown")) generateMonolith(chunk, random);
        }
        if (random.nextInt(48) == 0) generateFossil(chunk, random);
        spawnSiftWildlife(chunk, biome, random);
        if ((biome.endsWith("pink_meadows") || biome.endsWith("overgrown_clearing")) && random.nextInt(22) == 0) summonSinger(chunk, random);
        else if ((biome.endsWith("crystal_spires") || biome.endsWith("siftslate_slopes")) && random.nextInt(55) == 0) summonEchoGolem(chunk, random);
        if ((biome.endsWith("shattered_peaks") || biome.endsWith("siftslate_peaks")) && plugin.getConfig().getBoolean("content.rift-events", true)
                && random.nextInt(120) == 0) openNaturalRift(chunk);
        if (plugin.getConfig().getBoolean("content.prism_warden", false)
                && (biome.endsWith("bulb_forest") || biome.endsWith("overgrown_forest")) && random.nextInt(160) == 0)
            spawnBoss(chunk, EntityType.WARDEN, "Prism Warden", "prism_warden", 180.0, 28.0);
        else if ((biome.endsWith("shattered_peaks") || biome.endsWith("siftslate_peaks")) && random.nextInt(120) == 0) {
            boolean tyrant = plugin.getConfig().getBoolean("content.spire_tyrant", false)
                    && random.nextBoolean();
            spawnBoss(chunk, EntityType.RAVAGER,
                    tyrant ? "Spire Tyrant" : "The Sifter",
                    tyrant ? "spire_tyrant" : "the_sifter", 220.0, 24.0);
        }
    }

    private void decorateStrictSurface(Chunk chunk, String biome, Random random) {
        if (!((biome.endsWith("pink_meadows") || biome.endsWith("overgrown_clearing")) || (biome.endsWith("bulb_forest") || biome.endsWith("overgrown_forest"))
                || biome.contains("overgrown"))) return;
        World world = chunk.getWorld();
        for (int i = 0; i < 28; i++) {
            int x = chunk.getX() * 16 + 1 + random.nextInt(14);
            int z = chunk.getZ() * 16 + 1 + random.nextInt(14);
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 4) continue;
            Block ground = world.getBlockAt(x, y, z);
            if (ground.getType() == Material.GRASS_BLOCK)
                placeBlock(ground, "sift_grass", Material.MOSS_BLOCK);
        }
    }

    private void growStrictProviderLandmark(Chunk chunk, String biome, Random random) {
        if (random.nextInt(5) != 0) return;
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 3 + random.nextInt(10);
        int z = chunk.getZ() * 16 + 3 + random.nextInt(10);
        int y = world.getHighestBlockYAt(x, z) + 1;
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 12) return;
        boolean forest = (biome.endsWith("bulb_forest") || biome.endsWith("overgrown_forest")) || (biome.endsWith("pink_meadows") || biome.endsWith("overgrown_clearing"))
                || biome.contains("overgrown_forest") || biome.contains("overgrown_clearing");
        if (forest) {
            int height = 4 + random.nextInt(4);
            for (int dy = 0; dy < height; dy++)
                placeBlock(world.getBlockAt(x, y + dy, z), "sift_log", Material.CHERRY_LOG);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (Math.abs(dx) + Math.abs(dz) < 4)
                    placeBlock(world.getBlockAt(x + dx, y + height - 1, z + dz),
                            "sift_leaves", Material.CHERRY_LEAVES);
        } else {
            int radius = 2 + random.nextInt(3);
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++)
                if (random.nextInt(3) == 0)
                    placeBlock(world.getBlockAt(x + dx, y + random.nextInt(3), z + dz),
                            "siftstone", Material.TUFF);
        }
    }

    private void decorateSurface(Chunk chunk, String biome, Random random) {
        World world = chunk.getWorld();
        for (int i = 0; i < 28; i++) {
            int x = chunk.getX() * 16 + 1 + random.nextInt(14);
            int z = chunk.getZ() * 16 + 1 + random.nextInt(14);
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 4) continue;
            Block ground = world.getBlockAt(x, y, z);
            Material surface = ground.getType();
            if (!surface.isSolid() || surface == Material.BEDROCK) continue;

            if (biome.endsWith("pink_meadows")) {
                if (surface == Material.GRASS_BLOCK && random.nextInt(4) == 0) {
                    placeBlock(ground, "sift_grass", random.nextBoolean() ? Material.MOSS_BLOCK : Material.DIRT);
                }
                placePlant(world, x, y + 1, z,
                        List.of(Material.PINK_PETALS, Material.PINK_TULIP, Material.AZURE_BLUET,
                                Material.LILAC, Material.CHERRY_SAPLING), random);
            } else if (biome.endsWith("bulb_forest")) {
                if (surface == Material.GRASS_BLOCK && random.nextInt(3) == 0) {
                    placeBlock(ground, "sift_grass", Material.MOSS_BLOCK);
                }
                placePlant(world, x, y + 1, z,
                        List.of(Material.FERN, Material.LARGE_FERN, Material.GLOW_LICHEN,
                                Material.RED_MUSHROOM, Material.BROWN_MUSHROOM, Material.AZALEA), random);
            } else if (biome.endsWith("crystal_spires")) {
                if (surface == Material.GRASS_BLOCK || surface == Material.STONE) {
                    ground.setType(random.nextBoolean() ? Material.CALCITE : Material.TUFF, false);
                }
            } else if (biome.endsWith("shattered_peaks")) {
                if (surface == Material.GRASS_BLOCK) ground.setType(Material.TUFF, false);
            } else if (biome.endsWith("carapace")) {
                if (surface == Material.GRASS_BLOCK || surface == Material.DIRT) {
                    ground.setType(random.nextBoolean() ? Material.PACKED_MUD : Material.CALCITE, false);
                }
            } else if (biome.contains("overgrown")) {
                if (surface == Material.GRASS_BLOCK && random.nextBoolean()) {
                    placeBlock(ground, "sift_grass", Material.MOSS_BLOCK);
                }
                placePlant(world, x, y + 1, z,
                        List.of(Material.FERN, Material.LARGE_FERN, Material.GLOW_LICHEN,
                                Material.AZALEA, Material.MOSS_CARPET, Material.PINK_PETALS), random);
            } else if (biome.contains("siftslate") || biome.contains("wastes") || biome.contains("deep_dark")) {
                if (surface == Material.GRASS_BLOCK || surface == Material.STONE) {
                    ground.setType(random.nextBoolean() ? Material.TUFF : Material.DEEPSLATE, false);
                }
            }
        }
    }

    private void placePlant(World world, int x, int y, int z, List<Material> choices, Random random) {
        Block plant = world.getBlockAt(x, y, z);
        if (plant.getType() != Material.AIR) return;
        Material selected = choices.get(random.nextInt(choices.size()));
        if (selected == Material.PINK_PETALS) {
            plant.setType(selected, false);
        } else if (selected == Material.GLOW_LICHEN) {
            plant.setType(Material.GLOW_LICHEN, false);
        } else if (selected == Material.CHERRY_SAPLING || selected == Material.FERN
                || selected == Material.LARGE_FERN || selected == Material.RED_MUSHROOM
                || selected == Material.BROWN_MUSHROOM || selected == Material.AZALEA
                || selected == Material.PINK_TULIP || selected == Material.AZURE_BLUET
                || selected == Material.LILAC) {
            plant.setType(selected, false);
        }
    }

    private void growBiomeLandmarks(Chunk chunk, String biome, Random random) {
        if (random.nextInt(5) != 0) return;
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 3 + random.nextInt(10);
        int z = chunk.getZ() * 16 + 3 + random.nextInt(10);
        int y = world.getHighestBlockYAt(x, z) + 1;
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 12) return;

        if (biome.endsWith("crystal_spires") || biome.contains("siftslate_peaks")
                || biome.contains("overgrown_peaks") || biome.contains("ichor_snowy_peaks")) {
            int height = 4 + random.nextInt(9);
            for (int dy = 0; dy < height; dy++) {
                world.getBlockAt(x, y + dy, z).setType(Material.AMETHYST_BLOCK, false);
                if (dy > 1 && dy % 3 == 0) {
                    Material crystal = random.nextBoolean() ? Material.BUDDING_AMETHYST : Material.CALCITE;
                    world.getBlockAt(x + 1, y + dy, z).setType(crystal, false);
                    world.getBlockAt(x - 1, y + dy, z).setType(crystal, false);
                }
            }
            world.getBlockAt(x, y + height, z).setType(Material.AMETHYST_CLUSTER, false);
        } else if (biome.endsWith("shattered_peaks") || biome.contains("siftslate_slopes")
                || biome.contains("overgrown_slopes")) {
            Material[] rocks = {Material.TUFF, Material.CALCITE, Material.ANDESITE, Material.COBBLED_DEEPSLATE};
            int radius = 2 + random.nextInt(3);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (random.nextInt(3) != 0) continue;
                    int dy = random.nextInt(3);
                    placeBlock(world.getBlockAt(x + dx, y + dy, z + dz), "siftstone",
                            rocks[random.nextInt(rocks.length)]);
                }
            }
        } else if (biome.endsWith("bulb_forest") || biome.contains("overgrown_forest")) {
            Material wood = random.nextBoolean() ? Material.MANGROVE_LOG : Material.DARK_OAK_LOG;
            Material leaves = random.nextBoolean() ? Material.MANGROVE_LEAVES : Material.AZALEA_LEAVES;
            int trunk = 4 + random.nextInt(4);
            for (int dy = 0; dy < trunk; dy++) {
                placeBlock(world.getBlockAt(x, y + dy, z), "sift_log", wood);
            }
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) < 4) {
                    placeBlock(world.getBlockAt(x + dx, y + trunk - 1, z + dz), "sift_leaves", leaves);
                }
            }
            world.getBlockAt(x, y + trunk, z).setType(Material.SHROOMLIGHT, false);
        } else if (biome.endsWith("pink_meadows") || biome.contains("overgrown_clearing")) {
            Material wood = Material.CHERRY_LOG;
            for (int dy = 0; dy < 5; dy++) placeBlock(world.getBlockAt(x, y + dy, z), "sift_log", wood);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) < 4) {
                    placeBlock(world.getBlockAt(x + dx, y + 4, z + dz), "sift_leaves", Material.CHERRY_LEAVES);
                }
            }
        } else if (biome.endsWith("carapace") || biome.contains("sift_wastes")
                || biome.contains("sift_deep_dark")) {
            Material[] shell = {Material.TUFF_BRICKS, Material.PACKED_MUD, Material.PRISMARINE_BRICKS};
            int radius = 2 + random.nextInt(2);
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius + 1) {
                    placeBlock(world.getBlockAt(x + dx, y, z + dz), "siftstone_bricks",
                            shell[random.nextInt(shell.length)]);
                }
            }
            world.getBlockAt(x, y + 1, z).setType(Material.COPPER_BULB, false);
        }
    }

    private void generateAbandonedPortal(Chunk chunk, Random random) {
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 5 + random.nextInt(6);
        int z = chunk.getZ() * 16 + 5 + random.nextInt(6);
        int y = world.getHighestBlockYAt(x, z) + 1;
        Material[] frame = {Material.CRYING_OBSIDIAN, Material.OBSIDIAN, Material.CRYING_OBSIDIAN};
        for (int dy = 0; dy < 5; dy++) {
            for (int dx = 0; dx < 4; dx++) {
                if (dx != 0 && dx != 3 && dy != 0 && dy != 4) continue;
                Material block = frame[random.nextInt(frame.length)];
                if (random.nextInt(7) == 0) block = Material.AIR;
                world.getBlockAt(x + dx, y + dy, z).setType(block, false);
            }
        }
        world.getBlockAt(x + 5, y, z + 1).setType(Material.CHEST, false);
        Block chest = world.getBlockAt(x + 5, y, z + 1);
        if (chest.getState() instanceof Container container) {
            addIfPresent(container, content.item(Material.LAPIS_LAZULI, "charoite", "Charoite"));
            addIfPresent(container, content.forId("overgrown_willow"));
            if (random.nextBoolean()) addIfPresent(container, content.forId("rift_disc"));
            if (random.nextInt(100) < 40) addIfPresent(container, content.forId("siftite_upgrade_smithing_template"));
            if (random.nextInt(100) < 12) addIfPresent(container, content.forId("siftite_nugget"));
            if (random.nextBoolean()) container.getInventory().addItem(new org.bukkit.inventory.ItemStack(
                    Material.EMERALD, 1 + random.nextInt(3)));
            if (random.nextBoolean()) container.getInventory().addItem(new org.bukkit.inventory.ItemStack(
                    Material.DIAMOND, 1 + random.nextInt(2)));
            container.update(true, false);
        }
    }

    private static void addIfPresent(Container container, org.bukkit.inventory.ItemStack item) {
        if (item != null) container.getInventory().addItem(item);
    }

    private void generateIchorSpring(Chunk chunk, Random random) {
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 4 + random.nextInt(8);
        int z = chunk.getZ() * 16 + 4 + random.nextInt(8);
        int y = world.getHighestBlockYAt(x, z);
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) return;
        Material[] rim = {Material.CALCITE, Material.MOSS_BLOCK, Material.TUFF};
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (dx * dx + dz * dz > 5) continue;
            world.getBlockAt(x + dx, y, z + dz).setType(rim[random.nextInt(rim.length)], false);
        }
        // A single source block, not a 3x3 source pool: pools of sources spread infinitely
        // once broken, which turned every spring into an infinite ordinary-water farm (and,
        // before the weather fix, an infinite regeneration source). The mod's Ichor never
        // creates infinite sources.
        placeBlock(world.getBlockAt(x, y + 1, z), "ichor_water", Material.WATER);
        placeBlock(world.getBlockAt(x, y + 1, z), "ichor_water", Material.WATER);
        world.spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, new Location(world, x + .5, y + 1, z + .5),
                12, 1, .2, 1, .02);
    }

    private boolean placeBlock(Block block, String contentId, Material fallback) {
        boolean strict = plugin.getConfig().getBoolean("providers.strict", true);
        return placeBlock(block, contentId, fallback, strict,
                () -> strict && blockBridge.isBlock(contentId, block),
                () -> blockBridge.place(contentId, block.getLocation()), failure -> {
                    providerPlacementFailures.incrementAndGet();
                    providerPlacementFailuresById.computeIfAbsent(contentId, ignored -> new AtomicLong())
                            .incrementAndGet();
                    lastProviderPlacementFailure = failure;
                    plugin.getLogger().warning(failure);
                });
    }

    static boolean placeBlock(Block block, String contentId, Material fallback, boolean strict,
                              BooleanSupplier providerPlace, java.util.function.Consumer<String> onStrictFailure) {
        return placeBlock(block, contentId, fallback, strict, () -> false, providerPlace, onStrictFailure);
    }

    static boolean placeBlock(Block block, String contentId, Material fallback, boolean strict,
                              BooleanSupplier alreadyProviderBlock, BooleanSupplier providerPlace,
                              java.util.function.Consumer<String> onStrictFailure) {
        if (strict && alreadyProviderBlock.getAsBoolean()) return true;
        if (providerPlace.getAsBoolean()) return true;
        if (strict) {
            String failure = "Strict provider block placement failed for ID '" + contentId + "' at "
                    + block.getWorld().getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ()
                    + "; chunk decoration will retry after provider definitions are fixed.";
            onStrictFailure.accept(failure);
            throw new IllegalStateException(failure);
        }
        block.setType(fallback, false);
        return true;
    }

    long providerPlacementFailureCount() { return providerPlacementFailures.get(); }

    String providerPlacementFailureSummary() {
        if (providerPlacementFailuresById.isEmpty()) return "0 (deferred chunk retries: "
                + deferredDecorationRetries.get() + "; last: none)";
        String ids = providerPlacementFailuresById.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue().get())
                .collect(java.util.stream.Collectors.joining(", "));
        return providerPlacementFailures.get() + " (" + ids + "; deferred chunk retries: "
                + deferredDecorationRetries.get() + "; last: " + lastProviderPlacementFailure + ")";
    }

    /**
     * True when the bundled dimension datapack is enabled and its data-driven ore features are
     * therefore generating ores itself. The Java stand-in veins must not double-generate on top.
     * Fails closed (false = keep Java generation) if the pack cannot be queried yet.
     */
    private boolean dimensionOreFeaturesActive() {
        return plugin instanceof TheSiftFoundationPlugin foundation
                && foundation.datapackBridge() != null
                && foundation.datapackBridge().isDimensionPackEnabled();
    }

    private void generateCharoiteOre(Chunk chunk, Random random) {
        // Data-driven replacement: the dimension datapack's siftslate_charoite_ore placed feature
        // covers this when enabled; skip the Java stand-in so the two do not double-generate.
        if (dimensionOreFeaturesActive()) return;
        if (!plugin.getConfig().getBoolean("content.charoite_ore",
                plugin.getConfig().getBoolean("charoite.enabled", true))
                || !plugin.getConfig().getBoolean("content.charoite", true)
                || !plugin.getConfig().getBoolean("content.charoite_item", true)) return;
        if (random.nextInt(3) != 0) return;
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 3 + random.nextInt(10);
        int z = chunk.getZ() * 16 + 3 + random.nextInt(10);
        int y = Math.max(world.getMinHeight() + 8, 18 + random.nextInt(32));
        int size = 3 + random.nextInt(4);
        List<Material> hostRocks = List.of(Material.STONE, Material.DEEPSLATE, Material.TUFF,
                Material.ANDESITE, Material.CALCITE);
        for (int i = 0; i < size; i++) {
            Block ore = world.getBlockAt(x + random.nextInt(3) - 1,
                    y + random.nextInt(3) - 1, z + random.nextInt(3) - 1);
            if (!hostRocks.contains(ore.getType())) continue;
            placeBlock(ore, "charoite_ore", Material.AMETHYST_BLOCK);
            SiftiteListener.markGeneratedOre(plugin, ore);
        }
    }

    /**
     * Fan-made stand-in wildlife gate (plan: content.sift_standin_mobs, default FALSE). The
     * five core mod mobs (Singer, Echo Golem, Blub, Sifter, Dark Sniffer) are NOT affected;
     * the wildlife tables above mix them, so each table entry checks its own id here.
     */
    private boolean fanMobEnabled(String mobId) {
        return plugin.getConfig().getBoolean("content.sift_standin_mobs", false);
    }

    /** Ore veins required for ordinary survival progression in a biome set with no data-driven features. */
    private void generateSiftResources(Chunk chunk, Random random) {
        boolean dataDriven = dimensionOreFeaturesActive();
        // The dimension terrain spans Y 0..256 (the mod layout), not vanilla's -64..320, so the
        // vanilla-style ranges are shifted +64; unshifted negatives clamp to the floor and lose
        // most of the band (diamond would be squeezed into 1..8).
        generateOreVeins(chunk, random, Material.IRON_ORE, 2, 40, 120, 5);
        // The dimension datapack's siftslate_coal/diamond/emerald placed features cover these
        // three ores when enabled; skip only their Java stand-ins so iron/copper/redstone/lapis/
        // gold keep generating (the pack has no equivalent feature for those yet).
        if (!dataDriven) {
            generateOreVeins(chunk, random, Material.COAL_ORE, 2, 72, 176, 7);
        }
        generateOreVeins(chunk, random, Material.COPPER_ORE, 1, 48, 144, 5);
        generateOreVeins(chunk, random, Material.REDSTONE_ORE, 1, 8, 80, 5);
        generateOreVeins(chunk, random, Material.LAPIS_ORE, 1, 24, 112, 3);
        if (!dataDriven && random.nextInt(4) == 0) generateOreVeins(chunk, random, Material.DIAMOND_ORE, 1, 6, 72, 3);
        if (random.nextInt(8) == 0) generateOreVeins(chunk, random, Material.GOLD_ORE, 1, 16, 88, 4);
    }

    private void generateOreVeins(Chunk chunk, Random random, Material oreType,
                                  int veins, int minY, int maxY, int size) {
        World world = chunk.getWorld();
        int low = Math.max(world.getMinHeight() + 1, minY);
        int high = Math.min(world.getMaxHeight() - 2, maxY);
        if (high <= low) return;
        for (int vein = 0; vein < veins; vein++) {
            int x = chunk.getX() * 16 + random.nextInt(16);
            int y = low + random.nextInt(high - low + 1);
            int z = chunk.getZ() * 16 + random.nextInt(16);
            for (int block = 0; block < size; block++) {
                Block target = world.getBlockAt(x + random.nextInt(3) - 1,
                        y + random.nextInt(3) - 1, z + random.nextInt(3) - 1);
                if ((target.getX() >> 4) == chunk.getX() && (target.getZ() >> 4) == chunk.getZ()
                        && (target.getType() == Material.STONE || target.getType() == Material.DEEPSLATE
                        || target.getType() == Material.TUFF || target.getType() == Material.ANDESITE)) {
                    target.setType(target.getY() < 0
                            ? deepslateVariant(oreType) : oreType, false);
                }
            }
        }
    }

    private Material deepslateVariant(Material ore) {
        return switch (ore) {
            case IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
            case COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
            case COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
            case REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
            case LAPIS_ORE -> Material.DEEPSLATE_LAPIS_ORE;
            case DIAMOND_ORE -> Material.DEEPSLATE_DIAMOND_ORE;
            case GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
            default -> ore;
        };
    }

    private void generateSiftiteOre(Chunk chunk, Random random) {
        // Data-driven replacement: the dimension datapack's siftslate_siftite_ore placed feature
        // covers this when enabled; skip the Java stand-in so the two do not double-generate.
        if (dimensionOreFeaturesActive()) return;
        if (!plugin.getConfig().getBoolean("content.siftite_ore",
                plugin.getConfig().getBoolean("siftite.enabled", true))
                || !plugin.getConfig().getBoolean("content.siftite", true)
                || !plugin.getConfig().getBoolean("content.siftite_nugget", true)) return;
        if (random.nextInt(24) != 0) return;
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 1 + random.nextInt(14);
        int y = Math.max(world.getMinHeight() + 8, -24 + random.nextInt(40));
        int z = chunk.getZ() * 16 + 1 + random.nextInt(14);
        for (int i = 0; i < 3 + random.nextInt(3); i++) {
            Block ore = world.getBlockAt(x + random.nextInt(3) - 1,
                    y + random.nextInt(3) - 1, z + random.nextInt(3) - 1);
            if (ore.getType() != Material.STONE && ore.getType() != Material.DEEPSLATE
                    && ore.getType() != Material.TUFF) continue;
            placeBlock(ore, "siftite_ore", Material.ANCIENT_DEBRIS);
            SiftiteListener.markGeneratedSiftiteOre(plugin, ore);
        }
    }

    private void generateSoulCanyon(Chunk chunk, Random random) {
        boolean soulBlocksEnabled = plugin.getConfig().getBoolean("content.soul_block", true);
        World world = chunk.getWorld();
        int centerZ = chunk.getZ() * 16 + 8;
        for (int localX = 1; localX < 15; localX++) {
            int x = chunk.getX() * 16 + localX;
            int surfaceY = world.getHighestBlockYAt(x, centerZ);
            int width = 2 + (int) (Math.sin(localX * 0.72) * 1.5);
            for (int dz = -width; dz <= width; dz++) {
                for (int dy = 0; dy < 7; dy++) {
                    int y = surfaceY - dy;
                    Block block = world.getBlockAt(x, y, centerZ + dz);
                    if (block.getType() == Material.BEDROCK || block.getType() == Material.WATER) continue;
                    block.setType(dy == 6 ? Material.SOUL_SOIL : Material.AIR, false);
                }
            }
        }
        if (soulBlocksEnabled) {
            int soulX = chunk.getX() * 16 + 7;
            int soulY = world.getHighestBlockYAt(soulX, centerZ) - 6;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (random.nextInt(4) == 0) continue;
                    Block soul = world.getBlockAt(soulX + dx, soulY, centerZ + dz);
                    placeBlock(soul, "soul_block", Material.SOUL_SAND);
                    SoulBlockListener.markGenerated(plugin, soul);
                }
            }
        }
        world.spawnParticle(Particle.SOUL, new Location(world, chunk.getX() * 16 + 8.5,
                world.getHighestBlockYAt(chunk.getX() * 16 + 8, centerZ) - 3, centerZ + .5), 35, 5, 2, 1, .02);
    }

    private void generateCliffArch(Chunk chunk, Random random) {
        World world = chunk.getWorld();
        int centerX = chunk.getX() * 16 + 8;
        int z = chunk.getZ() * 16 + 8;
        int base = world.getHighestBlockYAt(centerX, z) + 1;
        Material stone = random.nextBoolean() ? Material.CALCITE : Material.TUFF;
        for (int side : new int[]{-4, 4}) {
            int height = 5 + random.nextInt(3);
            for (int y = 0; y < height; y++) world.getBlockAt(centerX + side, base + y, z).setType(stone, false);
        }
        for (int step = -4; step <= 4; step++) {
            int rise = 5 + 4 - Math.abs(step);
            world.getBlockAt(centerX + step, base + rise, z).setType(stone, false);
            if (Math.abs(step) < 3) world.getBlockAt(centerX + step, base + rise, z + 1).setType(stone, false);
        }
    }

    private void generateMonolith(Chunk chunk, Random random) {
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 8;
        int z = chunk.getZ() * 16 + 8;
        int y = world.getHighestBlockYAt(x, z) + 1;
        Material stone = random.nextBoolean() ? Material.POLISHED_DEEPSLATE : Material.TUFF_BRICKS;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) == 2 || Math.abs(dz) == 2) world.getBlockAt(x + dx, y, z + dz).setType(stone, false);
        }
        int height = 8 + random.nextInt(7);
        for (int dy = 1; dy < height; dy++) {
            world.getBlockAt(x, y + dy, z).setType(stone, false);
            if (dy % 4 == 0) world.getBlockAt(x + 1, y + dy, z).setType(Material.AMETHYST_BLOCK, false);
        }
        world.getBlockAt(x, y + height, z).setType(Material.SHROOMLIGHT, false);
    }

    private void generateSnifferCave(Chunk chunk, Random random) {
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 8;
        int z = chunk.getZ() * 16 + 8;
        int surface = world.getHighestBlockYAt(x, z);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz > 9) continue;
            for (int dy = 0; dy < 6; dy++) {
                int y = surface - dy;
                Block block = world.getBlockAt(x + dx, y, z + dz);
                if (block.getType() != Material.BEDROCK && block.getType() != Material.WATER) {
                    block.setType(dy == 5 ? Material.SCULK : Material.AIR, false);
                }
            }
        }
        for (int step = 0; step < 6; step++) {
            int y = surface - 5;
            Block tunnel = world.getBlockAt(x + 3 + step, y, z);
            if (tunnel.getType() != Material.BEDROCK && tunnel.getType() != Material.WATER) {
                tunnel.setType(Material.AIR, false);
                world.getBlockAt(x + 3 + step, y - 1, z).setType(Material.SCULK, false);
            }
        }
        if (world.getBlockAt(x, surface - 4, z).getType().isAir()) {
            Entity sniffer = mobFactory.spawn("dark_sniffer",
                    new Location(world, x + .5, surface - 4, z + .5), EntityType.SNIFFER);
            if (sniffer == null) return;
            sniffer.setCustomName("§5Dark Sniffer");
            sniffer.setCustomNameVisible(false);
            sniffer.setPersistent(true);
            sniffer.getPersistentDataContainer().set(creatureKey, PersistentDataType.STRING, "dark_sniffer");
        }
    }

    private void spawnSiftWildlife(Chunk chunk, String biome, Random random) {
        World world = chunk.getWorld();
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            // Reference-mod parity (Sifter): the mod spawns wild Sifter herds (especially in
            // the Overgrown Forest); keep it rare and gated behind content.sifter.
            if (biome.contains("overgrown_forest")
                    && plugin.getConfig().getBoolean("content.sifter", true)
                    && random.nextInt(40) == 0) {
                spawnSifterHerd(chunk, random);
                return;
            }
            // Honor the content.blub / content.sifter toggles from config.yml: species that are
            // disabled must not spawn in the wild. Previously these keys were display-only.
            if (!plugin.getConfig().getBoolean("content.blub", true)
                    && biome.endsWith("carapace")) continue;
            if (!plugin.getConfig().getBoolean("content.sifter", true)
                    && biome.contains("sift_wastes")) continue;
            int x = chunk.getX() * 16 + 2 + random.nextInt(12);
            int z = chunk.getZ() * 16 + 2 + random.nextInt(12);
            int y = world.getHighestBlockYAt(x, z) + 1;
            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 3) continue;
            EntityType type;
            String name;
            if (biome.endsWith("carapace")) {
                String[][] carapace = {{"TURTLE", "Thornback"}, {"RABBIT", "Blub"},
                        {"SILVERFISH", "Prismite"}, {"ALLAY", "Wisp"}};
                String[] mob = carapace[random.nextInt(carapace.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            } else if (biome.endsWith("pink_meadows") || biome.contains("overgrown_clearing")) {
                String[][] meadow = {{"RABBIT", "Loomer"}, {"BEE", "Glimmerwing"},
                        {"ALLAY", "Wisp"}, {"MOOSHROOM", "Sunspore"}};
                String[] mob = meadow[random.nextInt(meadow.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            } else if (biome.endsWith("bulb_forest") || biome.contains("overgrown_forest")) {
                String[][] forest = {{"BAT", "Glowbell"}, {"ZOMBIE", "Hollow Bulb"},
                        {"VEX", "Violet Wisp"}, {"SILVERFISH", "Prismite"}};
                String[] mob = forest[random.nextInt(forest.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            } else if (biome.endsWith("crystal_spires") || biome.contains("siftslate_peaks")) {
                String[][] spires = {{"SILVERFISH", "Prismite"}, {"ENDERMITE", "Shardling"},
                        {"ALLAY", "Wisp"}};
                String[] mob = spires[random.nextInt(spires.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            } else if (biome.contains("deep_dark")) {
                String[][] deep = {{"SNIFFER", "Dark Sniffer"}, {"WARDEN", "Prism Warden"},
                        {"ENDERMITE", "Shardling"}};
                String[] mob = deep[random.nextInt(deep.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            } else if (biome.contains("ichor_snowy") || biome.contains("siftslate_slopes")
                    || biome.contains("overgrown_slopes") || biome.contains("overgrown_peaks")) {
                String[][] cold = {{"STRAY", "Frost Stalker"}, {"GOAT", "Thornback"},
                        {"ENDERMAN", "Drifter"}};
                String[] mob = cold[random.nextInt(cold.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            } else if (biome.contains("sift_wastes")) {
                String[][] wastes = {{"HUSK", "Sift Wastes Stalker"}, {"ENDERMAN", "Drifter"},
                        {"SILVERFISH", "Prismite"}};
                String[] mob = wastes[random.nextInt(wastes.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            } else {
                String[][] peaks = {{"STRAY", "Frost Stalker"}, {"GOAT", "Thornback"},
                        {"ENDERMAN", "Drifter"}};
                String[] mob = peaks[random.nextInt(peaks.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            }
            String mobId = name.toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!FAN_CORE_MOBS.contains(mobId) && !fanMobEnabled(mobId)) continue;
            Location spawnAt = new Location(world, x + .5, y, z + .5);
            Entity entity = mobFactory.spawn(mobId, spawnAt, type);
            if (entity == null) continue;
            entity.setPersistent(true);
            entity.setCustomName("§d" + name);
            entity.setCustomNameVisible(random.nextInt(4) == 0);
            entity.getPersistentDataContainer().set(creatureKey, PersistentDataType.STRING, mobId);
            if (entity instanceof LivingEntity living && (type == EntityType.ZOMBIE || type == EntityType.VEX
                    || type == EntityType.SILVERFISH || type == EntityType.ENDERMITE)) {
                living.setRemoveWhenFarAway(false);
            }
        }
    }

    private void summonSinger(Chunk chunk, Random random) {
        if (!plugin.getConfig().getBoolean("content.singer", true)) return;
        int x = chunk.getX() * 16 + 8, z = chunk.getZ() * 16 + 8;
        int y = chunk.getWorld().getHighestBlockYAt(x, z) + 1;
        if (plugin.isEnabled() && resonanceManager != null && chunk.isLoaded()) {
            resonanceManager.summonSinger(new Location(chunk.getWorld(), x + .5, y, z + .5));
        }
    }

    private void summonEchoGolem(Chunk chunk, Random random) {
        if (!plugin.getConfig().getBoolean("content.echo_golem", true)) return;
        int x = chunk.getX() * 16 + 8, z = chunk.getZ() * 16 + 8;
        int y = chunk.getWorld().getHighestBlockYAt(x, z) + 1;
        if (plugin.isEnabled() && echoGolemManager != null && chunk.isLoaded()) {
            echoGolemManager.summonEchoGolem(new Location(chunk.getWorld(), x + .5, y, z + .5));
        }
    }

    private void openNaturalRift(Chunk chunk) {
        if (riftManager == null || !plugin.getConfig().getBoolean("content.rift", true)) return;
        int x = chunk.getX() * 16 + 8, z = chunk.getZ() * 16 + 8;
        int y = chunk.getWorld().getHighestBlockYAt(x, z) + 1;
        Location location = new Location(chunk.getWorld(), x + .5, y, z + .5);
        if (plugin.isEnabled() && riftManager != null && chunk.isLoaded()) {
            riftManager.openRift(location, null);
        }
    }

    private void spawnBoss(Chunk chunk, EntityType type, String name, String id,
                           double health, double attackDamage) {
        int x = chunk.getX() * 16 + 8, z = chunk.getZ() * 16 + 8;
        int y = chunk.getWorld().getHighestBlockYAt(x, z) + 1;
        Location location = new Location(chunk.getWorld(), x + .5, y, z + .5);
        Entity entity = mobFactory.spawn(id, location, type);
        if (entity == null) return;
        entity.setPersistent(true);
        entity.setCustomName("§5§l" + name);
        entity.setCustomNameVisible(true);
        entity.getPersistentDataContainer().set(creatureKey, PersistentDataType.STRING, id);
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            if (living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(health);
                living.setHealth(health);
            }
            if (living.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) != null) {
                living.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(attackDamage);
            }
        }
    }

    /**
     * Right-click in air with a Siftite Spear throws it as a projectile. This wires the
     * {@code siftite.spear-reach} config knob (previously only surfaced by /sift doctor) into
     * actual gameplay: the throw range is clamped to that value and the spear consumes one
     * durability point per throw.
     */
    @EventHandler
    public void onSpearThrow(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR) return;
        var hand = event.getHand();
        if (hand != org.bukkit.inventory.EquipmentSlot.HAND) return;
        var item = event.getItem();
        if (!spearThrowHandler.isSpear(item)) return;
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        spearThrowHandler.throwSpear(plugin, event.getPlayer(), item, hand);
    }

    /** A thrown spear lands exactly once: damage on entity hits, the copy drops where it lands. */
    @EventHandler
    public void onSpearHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Snowball projectile)) return;
        if (!"thrown_siftite_spear".equals(projectile.getPersistentDataContainer()
                .get(creatureKey, PersistentDataType.STRING))) return;
        // The copy drops on entity hits AND block hits (previously a block hit silently
        // deleted the spear), exactly once thanks to the handler's PDC guard.
        spearThrowHandler.dropThrownCopy(plugin, projectile);
        if (event.getHitEntity() instanceof LivingEntity target
                && projectile.getShooter() instanceof Player shooter) {
            spearThrowHandler.damageTarget(plugin, target, shooter);
        }
        projectile.remove();
    }

    /** Reference-mod item (ichor_snowball): a throwable that chills whatever it strikes. */
    @EventHandler
    public void onIchorSnowballThrow(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        var hand = event.getHand();
        if (hand != org.bukkit.inventory.EquipmentSlot.HAND) return;
        var item = event.getItem();
        if (item == null || item.getType() != Material.SNOWBALL
                || !"ichor_snowball".equals(item.getItemMeta().getPersistentDataContainer()
                        .get(ichorContentKey, PersistentDataType.STRING))) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        var pdc = player.getPersistentDataContainer();
        Long last = pdc.get(ichorSnowballAtKey, PersistentDataType.LONG);
        if (last != null && now - last < 500L) return;
        pdc.set(ichorSnowballAtKey, PersistentDataType.LONG, now);

        Location eye = player.getEyeLocation();
        eye.getWorld().spawn(eye, org.bukkit.entity.Snowball.class, spawned -> {
            spawned.setItem(item.clone());
            spawned.setShooter(player);
            spawned.setVelocity(eye.getDirection().normalize().multiply(1.5));
            spawned.getPersistentDataContainer().set(creatureKey, PersistentDataType.STRING,
                    "thrown_ichor_snowball");
        });
        // Vanilla consumes the snowball itself for a real snowball throw; this handler runs
        // before that for RIGHT_CLICK_AIR and alongside block placement for blocks, so the
        // throw is cancelled and exactly one snowball is consumed here.
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (item.getAmount() <= 1) player.getInventory().setItem(hand, null);
            else item.setAmount(item.getAmount() - 1);
        }
    }

    @EventHandler
    public void onIchorSnowballHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Snowball projectile)) return;
        if (!"thrown_ichor_snowball".equals(projectile.getPersistentDataContainer()
                .get(creatureKey, PersistentDataType.STRING))) return;
        // Chill whatever the snowball strikes; if a Warden is anywhere near the impact the
        // distraction worked, which is the reference mod's false_alarm advancement.
        if (event.getHitEntity() instanceof LivingEntity target) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, true, true, true));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    target.getLocation().add(0, 1, 0), 14, 0.3, 0.4, 0.3, 0.02);
        }
        boolean wardenNearby = !projectile.getWorld().getNearbyEntities(
                projectile.getLocation(), 16, 16, 16).stream()
                .filter(org.bukkit.entity.Warden.class::isInstance)
                .filter(w -> w.isValid() && !w.isDead()).toList().isEmpty();
        if (wardenNearby && projectile.getShooter() instanceof Player shooter) {
            SiftAdvancementGrant.grant(plugin, shooter, "false_alarm");
        }
    }

    @EventHandler
    public void onSiftBossDeath(EntityDeathEvent event) {
        String id = event.getEntity().getPersistentDataContainer().get(creatureKey, PersistentDataType.STRING);
        if ("prism_warden".equals(id) || "the_sifter".equals(id) || "spire_tyrant".equals(id)) {
            // content.item returns null in strict mode when no provider resolves the ID;
            // a null entry in the drop list would break the drop processing downstream.
            ItemStack heart = content.item(Material.ECHO_SHARD, "heart_of_the_sift", "Heart of the Sift");
            if (heart != null) event.getDrops().add(heart);
            ItemStack shard = content.item(Material.AMETHYST_SHARD, "sift_shard", "Sift Shard");
            if (shard != null) event.getDrops().add(shard);
            // Spire Fragment is a fan-made token for the bundled tyrant.json advancement icon.
            // The reference mod has no such item; admins can disable the whole variant with
            // content.spire_tyrant (default off), which also removes this drop.
            if (plugin.getConfig().getBoolean("content.spire_tyrant", false)) {
                int fragments = "spire_tyrant".equals(id) ? 2 : 1;
                for (int i = 0; i < fragments; i++) {
                    ItemStack fragment = content.item(Material.AMETHYST_SHARD,
                            "spire_fragment", "Spire Fragment");
                    if (fragment != null) event.getDrops().add(fragment);
                }
            }
        }
        // Reference-mod parity (food_chain): a tamed Blub that kills a Dark Snifter earns its
        // owner the advancement - the player taught the mob to fight for them.
        if ("dark_sniffer".equals(id) && event.getEntity().getKiller() == null
                && event.getEntity().getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) {
            String blubOwnerId = byEntity.getDamager().getPersistentDataContainer()
                    .get(blubOwnerKey, PersistentDataType.STRING);
            if (blubOwnerId != null) {
                try {
                    Player owner = Bukkit.getPlayer(UUID.fromString(blubOwnerId));
                    if (owner != null) SiftAdvancementGrant.grant(plugin, owner, "food_chain");
                } catch (IllegalArgumentException ignored) {
                    // Stale owner id.
                }
            }
        }
        // Reference-mod parity (Sifter): drops raw_sifter_meat, which smelts to cooked_sifter_meat.
        if ("sifter".equals(id)) {
            ItemStack raw = content.item(Material.BEEF, "raw_sifter_meat", "Raw Sifter Meat");
            if (raw != null) event.getDrops().add(raw);
        }
        // Blub tamed by the owner who killed it keeps nothing; wild/tamed Blub drops its own meat
        // so the food chain (Blub -> Dark Sniffer) reads correctly in survival.
        if ("blub".equals(id)) {
            ItemStack meat = content.item(Material.MUTTON, "blub_meat", "Blub Meat");
            if (meat != null) event.getDrops().add(meat);
        }
    }

    /** Smelt Raw Sifter Meat into Cooked Sifter Meat (mod parity for the food chain). */
    @EventHandler(ignoreCancelled = true)
    public void onSmeltSifterMeat(org.bukkit.event.inventory.FurnaceSmeltEvent event) {
        // When the registry owns real furnace recipes (recipes.enabled), let them resolve the
        // output; this listener is only the belt-and-braces path for strict-provider setups.
        if (plugin.getConfig().getBoolean("recipes.enabled", true)) return;
        ItemStack source = event.getSource();
        if (source.getType() == Material.BEEF && content.is(source, "raw_sifter_meat")) {
            ItemStack cooked = content.item(Material.COOKED_BEEF, "cooked_sifter_meat", "Cooked Sifter Meat");
            if (cooked != null) event.setResult(cooked);
        }
    }

    public boolean summonCreature(String species, Location location) {
        String id = species.toLowerCase(Locale.ROOT);
        if (id.equals("lumen_tulip")) {
            Block plant = location.getBlock();
            if (!plant.getType().isAir()) plant = plant.getRelative(0, 1, 0);
            plant.setType(Material.TORCHFLOWER, false);
            return true;
        }

        EntityType type;
        String display;
        switch (id) {
            case "blub" -> { type = EntityType.RABBIT; display = "Blub"; }
            case "dark_sniffer" -> { type = EntityType.SNIFFER; display = "Dark Sniffer"; }
            case "bulb" -> { type = EntityType.SLIME; display = "Bulb"; }
            case "loomer" -> { type = EntityType.RABBIT; display = "Loomer"; }
            case "wisp" -> { type = EntityType.ALLAY; display = "Wisp"; }
            case "violet_wisp" -> { type = EntityType.VEX; display = "Violet Wisp"; }
            case "koi" -> { type = EntityType.TROPICAL_FISH; display = "Koi"; }
            case "hollow_bulb" -> { type = EntityType.ZOMBIE; display = "Hollow Bulb"; }
            case "glowbell" -> { type = EntityType.BAT; display = "Glowbell"; }
            case "thornback" -> { type = EntityType.TURTLE; display = "Thornback"; }
            case "glimmerwing" -> { type = EntityType.BEE; display = "Glimmerwing"; }
            case "the_sifter" -> { type = EntityType.RAVAGER; display = "The Sifter"; }
            case "spire_tyrant" -> { type = EntityType.RAVAGER; display = "Spire Tyrant"; }
            case "sifter" -> { type = EntityType.RAVAGER; display = "Sifter"; }
            case "frost_stalker" -> { type = EntityType.STRAY; display = "Frost Stalker"; }
            case "sunspore" -> { type = EntityType.MOOSHROOM; display = "Sunspore"; }
            case "drifter" -> { type = EntityType.ENDERMAN; display = "Drifter"; }
            case "shardling" -> { type = EntityType.ENDERMITE; display = "Shardling"; }
            case "prism_warden" -> { type = EntityType.WARDEN; display = "Prism Warden"; }
            case "prismite" -> { type = EntityType.SILVERFISH; display = "Prismite"; }
            case "rift_walker" -> { type = EntityType.ENDERMAN; display = "Rift Walker"; }
            case "sift_wastes_stalker" -> { type = EntityType.WITCH; display = "Sift Wastes Stalker"; }
            default -> { return false; }
        }

        boolean boss = id.equals("the_sifter") || id.equals("prism_warden") || id.equals("spire_tyrant");
        // Mod parity (Sifter): herds cluster around a shared anchor instead of scattering.
        if ("sifter".equals(id)) {
            // Join the nearest herd anchor within 40 blocks while it has fewer than five
            // members; otherwise found a new herd. Anchor offsets snap to the surface so
            // herd members never spawn buried or in mid-air.
            Location anchor = null;
            java.util.List<Location> anchors = sifterHerdAnchors
                    .computeIfAbsent(location.getWorld().getUID(), w -> new java.util.ArrayList<>());
            for (Location candidate : anchors) {
                if (candidate.distanceSquared(location) <= 1600
                        && countNearby(location.getWorld(), candidate, 16) < 5) {
                    anchor = candidate;
                    break;
                }
            }
            if (anchor != null) {
                Vector offset = new Vector(java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 12 - 6,
                        0, java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 12 - 6);
                location = anchor.clone().add(offset);
            } else {
                anchor = location.clone();
                anchors.add(anchor);
            }
            location = location.clone();
            location.setY(location.getWorld().getHighestBlockYAt(location) + 1);
        }
        Entity entity = mobFactory.spawn(id, location, type);
        if (entity == null) return false;
        entity.setPersistent(true);
        entity.setCustomName((boss ? "§5§l" : "§d") + display);
        entity.setCustomNameVisible(true);
        entity.getPersistentDataContainer().set(creatureKey, PersistentDataType.STRING, id);
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            if (boss) {
                // Spire Tyrant mirrors the Sifter boss statline (220 HP / 24 dmg); Prism Warden keeps 180/28.
                boolean sifterLine = id.equals("the_sifter") || id.equals("spire_tyrant");
                double health = sifterLine ? 220.0 : 180.0;
                double damage = sifterLine ? 24.0 : 28.0;
                if (living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                    living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(health);
                    living.setHealth(health);
                }
                if (living.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) != null) {
                    living.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(damage);
                }
            } else {
                // Non-boss Sift mobs previously kept their vanilla stand-in stats; apply the
                // reference mod's per-mob defaults (config-overridable via mobs.<id>.* keys).
                SiftMobFactory.applyAttributes(living, modMobStats(id));
            }
        }
        return true;
    }

    /**
     * Per-mob base attributes mirroring the reference mod (docs/mod-gap-analysis.md §5).
     * Config paths {@code mobs.<id>.max-health / movement-speed / attack-damage / knockback-resist}
     * override each value so server owners can tune without recompiling.
     */
    private java.util.Map<String, Double> modMobStats(String id) {
        java.util.Map<String, Double> stats = new java.util.HashMap<>();
        switch (id) {
            case "sifter" -> {
                stats.put("MAX_HEALTH", plugin.getConfig().getDouble("mobs.sifter.max-health", 40.0));
                stats.put("MOVEMENT_SPEED", plugin.getConfig().getDouble("mobs.sifter.movement-speed", 0.31));
                stats.put("ATTACK_DAMAGE", plugin.getConfig().getDouble("mobs.sifter.attack-damage", 10.0));
            }
            case "dark_sniffer" -> {
                stats.put("MAX_HEALTH", plugin.getConfig().getDouble("mobs.dark_sniffer.max-health", 40.0));
                stats.put("MOVEMENT_SPEED", plugin.getConfig().getDouble("mobs.dark_sniffer.movement-speed", 0.1));
                stats.put("ATTACK_DAMAGE", plugin.getConfig().getDouble("mobs.dark_sniffer.attack-damage", 7.0));
                // Reference-mod parity: the Dark Snifter raises the ATTACK_KNOCKBACK attribute
                // (generic.knockback), not knockback resistance; apply both honouring config.
                Double attackKnockback = plugin.getConfig().getDouble("mobs.dark_sniffer.attack-knockback", 2.0);
                if (attackKnockback != null && attackKnockback > 0) stats.put("ATTACK_KNOCKBACK", attackKnockback);
                stats.put("KNOCKBACK_RESISTANCE",
                        plugin.getConfig().getDouble("mobs.dark_sniffer.knockback-resist", 0.75));
            }
            case "blub" -> {
                stats.put("MAX_HEALTH", plugin.getConfig().getDouble("mobs.blub.max-health", 20.0));
                stats.put("MOVEMENT_SPEED", plugin.getConfig().getDouble("mobs.blub.movement-speed", 0.3));
                stats.put("ATTACK_DAMAGE", plugin.getConfig().getDouble("mobs.blub.attack-damage", 3.0));
            }
            default -> {
                return java.util.Map.of();
            }
        }
        return stats;
    }

    /**
     * Reference-mod parity: Sifters travel in herds. Every herd member walks back toward the
     * world's last-known anchor when it strays too far, reproducing the mod's group movement
     * without a custom AI goal (which would need NMS). Runs on the existing ecology tick.
     */
    private void tickSifterHerds() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null) return;
        java.util.List<Location> anchors = sifterHerdAnchors.get(sift.getUID());
        if (anchors == null || anchors.isEmpty()) return;
        for (Entity entity : indexedMobs(sift, "sifter")) {
            if (!(entity instanceof Mob mob)) continue;
            Location nearest = null;
            double best = Double.MAX_VALUE;
            for (Location anchor : anchors) {
                double distance = mob.getLocation().distanceSquared(anchor);
                if (distance < best) {
                    best = distance;
                    nearest = anchor;
                }
            }
            if (nearest == null || best <= 900) continue;
            mob.getPathfinder().moveTo(nearest);
        }
        // Prune anchors whose herd has wandered off or died out, so the list stays bounded.
        anchors.removeIf(anchor -> countNearby(sift, anchor, 32) == 0);
    }

    private int countNearby(World world, Location center, double radius) {
        int count = 0;
        for (Entity entity : indexedMobs(world, "sifter")) {
            if (entity.getLocation().distanceSquared(center) <= radius * radius) count++;
        }
        return count;
    }

    /** Reference-mod parity (Sifter): rare wild herds (three to five) in Overgrown Forest. */
    private void spawnSifterHerd(Chunk chunk, Random random) {
        int x = chunk.getX() * 16 + 4 + random.nextInt(8);
        int z = chunk.getZ() * 16 + 4 + random.nextInt(8);
        int y = chunk.getWorld().getHighestBlockYAt(x, z) + 1;
        Location at = new Location(chunk.getWorld(), x + 0.5, y, z + 0.5);
        // Mod 1.1.0 rule: Sifters only spawn on natural terrain, never inside player builds.
        for (Block marker : java.util.List.of(
                chunk.getWorld().getBlockAt(x, y, z),
                chunk.getWorld().getBlockAt(x + 1, y, z),
                chunk.getWorld().getBlockAt(x, y, z + 1))) {
            Material type = marker.getType();
            if (type == Material.CHEST || type == Material.CRAFTING_TABLE || type == Material.FURNACE
                    || type == Material.TORCH || type == Material.WALL_TORCH || type.isAir()) {
                return;
            }
        }
        int herd = 3 + random.nextInt(3);
        for (int i = 0; i < herd; i++) {
            summonCreature("sifter", at);
        }
    }

    private boolean isSift(World world) {
        return world.getKey().toString().equals(DIMENSION);
    }

    private void tickTide() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null || sift.getPlayers().isEmpty()) return;
        int period = Math.max(30, plugin.getConfig().getInt("sift-world.tide-period-seconds", 120));
        tideTick += 5;
        boolean flow = (tideTick / period) % 2 == 0;
        for (Player player : sift.getPlayers()) {
            player.addPotionEffect(new PotionEffect(flow ? PotionEffectType.JUMP_BOOST : PotionEffectType.SLOWNESS,
                    110, flow ? 0 : 0, true, false, true));
            if (flow) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 110, 0, true, false, true));
            else player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 110, 0, true, false, true));
            player.getWorld().spawnParticle(flow ? Particle.END_ROD : Particle.REVERSE_PORTAL,
                    player.getLocation().add(0, 1, 0), 5, 1.3, 0.7, 1.3, 0.01);
            if (tideTick % 60 == 0) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        Messages.get(flow ? "tide.flowing" : "tide.ebbing",
                                flow ? "The Sift tide is flowing around you" : "The Sift tide is ebbing"),
                        flow ? net.kyori.adventure.text.format.NamedTextColor.AQUA
                                : net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f,
                        flow ? 1.5f : 0.7f);
            }
            for (Entity nearby : player.getNearbyEntities(12, 8, 12)) {
                if (nearby instanceof LivingEntity living && !(living instanceof Player)) {
                    living.addPotionEffect(new PotionEffect(flow ? PotionEffectType.JUMP_BOOST
                            : PotionEffectType.SLOWNESS, 110, 0, true, false, false));
                }
            }
        }
    }

    /**
     * Reference-mod parity: a pointed dripstone dripping above an Ichor cauldron slowly
     * refills it with Ichor. Runs on the 5s world tick; one level per drip.
     */
    private void tickIchorCauldrons() {
        if (!plugin.getConfig().getBoolean("ichor.cauldron.dripstone-fill", true)) return;
        IchorState state = ichorState();
        if (state == null) return;
        int maxLevel = Math.max(1, plugin.getConfig().getInt("ichor.cauldron.max-level", 3));
        for (Location at : state.cauldronLocations()) {
            if (!at.getChunk().isLoaded()) continue;
            if (state.level(at) >= maxLevel) continue;
            Block above = at.clone().add(0, 1, 0).getBlock();
            if (above.getType() != org.bukkit.Material.POINTED_DRIPSTONE) continue;
            state.setLevel(at, state.level(at) + 1);
            at.getWorld().spawnParticle(Particle.DRIPPING_DRIPSTONE_WATER,
                    at.clone().add(0.5, 1.1, 0.5), 6, 0.2, 0.15, 0.2, 0.01);
        }
    }

    /** Reference-mod 1.1.x: fossils buried in cave floors (bone blocks with a coal pocket). */
    private void generateFossil(Chunk chunk, Random random) {
        World world = chunk.getWorld();
        int x = chunk.getX() * 16 + 2 + random.nextInt(12);
        int z = chunk.getZ() * 16 + 2 + random.nextInt(12);
        int minHeight = world.getMinHeight() + 8;
        int maxHeight = world.getMaxHeight() - 16;
        for (int attempt = 0; attempt < 8; attempt++) {
            int y = minHeight + random.nextInt(Math.max(1, maxHeight - minHeight));
            Block floor = world.getBlockAt(x, y - 1, z);
            Block spot = world.getBlockAt(x, y, z);
            if (floor.getType() != Material.DEEPSLATE || !spot.getType().isAir()) continue;
            int segments = 2 + random.nextInt(3);
            for (int i = 0; i < segments; i++) {
                Block bone = world.getBlockAt(x + i % 2, y - 1, z + (i + 1) % 2);
                if (!bone.getType().isAir()) bone.setType(Material.BONE_BLOCK);
            }
            spot.setType(random.nextBoolean() ? Material.BONE_BLOCK : Material.COAL_ORE);
            return;
        }
    }

    private IchorState ichorState() {
        return plugin instanceof TheSiftFoundationPlugin foundation ? foundation.ichorState() : null;
    }

    /** Ichor rain/snow visual and contact-effect approximation for the reference mod weather. */
    private void tickIchorWeather() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null || sift.getPlayers().isEmpty()) return;
        int period = Math.max(180, plugin.getConfig().getInt("sift-world.ichor-weather-period-seconds", 900));
        long phase = (sift.getFullTime() / 20L) % period;
        boolean ichorStorm = phase > period * 2L / 3L && phase < period * 5L / 6L;
        if (sift.hasStorm() != ichorStorm) sift.setStorm(ichorStorm);
        sift.setThundering(false);
        if (!ichorStorm) return;

        org.bukkit.Particle.DustOptions ichor = new org.bukkit.Particle.DustOptions(Color.fromRGB(84, 226, 199), 1.2f);
        for (Player player : sift.getPlayers()) {
            Location at = player.getLocation();
            sift.spawnParticle(Particle.DUST, at.clone().add(0, 2, 0), 18,
                    2.5, 1.5, 2.5, ichor);
            // Effects only while the ichor storm is active - standing in ordinary (non-ichor)
            // water outside a storm must not be an infinite regeneration source. MOTION_BLOCKING
            // heightmap instead of light-from-sky, which also lit players under open eaves.
            boolean exposedToRain = sift.getHighestBlockYAt(at, HeightMap.MOTION_BLOCKING) <= at.getBlockY();
            boolean inWater = at.getBlock().getType() == Material.WATER
                    || at.getBlock().getType() == Material.BUBBLE_COLUMN;
            if (exposedToRain || inWater) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 0, true, false, true));
            }
            if (player.getLocation().getBlock().getBiome().getKey().toString().endsWith("ichor_snowy_peaks")) {
                int x = at.getBlockX(), z = at.getBlockZ();
                int y = sift.getHighestBlockYAt(x, z);
                Block top = sift.getBlockAt(x, y + 1, z);
                if (top.getType().isAir() || top.getType() == Material.SNOW) {
                    top.setType(Material.SNOW, false);
                    if (top.getBlockData() instanceof Snow snow && snow.getLayers() < 2) {
                        snow.setLayers(Math.min(2, snow.getLayers() + 1));
                        top.setBlockData(snow, false);
                    }
                }
            }
        }
    }

    /**
     * Paper approximation of the mod's Dark Sniffer combat loop. This runs every tick so the
     * 10-tick wind-up and 1.5-block/tick travelling wave do not become a single instant pull.
     * State is persisted on the entity, so a chunk unload safely interrupts/restarts combat.
     */
    private void tickDarkSnifferCombat() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null) return;
        long now = System.currentTimeMillis();
        for (Entity entity : indexedMobs(sift, "dark_sniffer", "type:SNIFFER")) {
            if (entity.getType() != EntityType.SNIFFER || !(entity instanceof Mob sniffer)) continue;
            var pdc = entity.getPersistentDataContainer();
            if (!"dark_sniffer".equals(pdc.get(creatureKey, PersistentDataType.STRING))) {
                // Reference behavior: ordinary Sniffers flee from a Dark Sniffer within 20 blocks.
                // Refresh the destination at most every 750 ms to avoid restarting pathfinding each tick.
                Entity threat = null;
                double nearestThreatDistance = 20.0 * 20.0;
                for (Entity nearby : entity.getNearbyEntities(20, 20, 20)) {
                    if (nearby.getType() != EntityType.SNIFFER
                            || !"dark_sniffer".equals(nearby.getPersistentDataContainer()
                            .get(creatureKey, PersistentDataType.STRING))) continue;
                    double distance = entity.getLocation().distanceSquared(nearby.getLocation());
                    if (distance < nearestThreatDistance) {
                        nearestThreatDistance = distance;
                        threat = nearby;
                    }
                }
                if (threat != null) {
                    Long lastFleePath = pdc.get(snifferFleePathAtKey, PersistentDataType.LONG);
                    if (lastFleePath == null || now - lastFleePath >= 750L || sniffer.getPathfinder().hasPath() == false) {
                        Vector away = entity.getLocation().toVector().subtract(threat.getLocation().toVector());
                        away.setY(0);
                        if (away.lengthSquared() < 0.0001) away = new Vector(1, 0, 0);
                        Location destination = entity.getLocation().clone().add(away.normalize().multiply(12));
                        if (sniffer.getPathfinder().moveTo(destination, 1.25)) {
                            pdc.set(snifferFleePathAtKey, PersistentDataType.LONG, now);
                        }
                    }
                } else if (pdc.has(snifferFleePathAtKey, PersistentDataType.LONG)) {
                    pdc.remove(snifferFleePathAtKey);
                }
                continue;
            }

            // Petting temporarily suppresses combat. Clear orphaned ownership if the
            // Warden unloaded, died, or abandoned this target before cleanup could run.
            String pettingWardenId = pdc.get(darkSnifferPettingWardenKey, PersistentDataType.STRING);
            if (pettingWardenId != null) {
                Entity pettingWarden = null;
                try {
                    pettingWarden = Bukkit.getEntity(UUID.fromString(pettingWardenId));
                } catch (IllegalArgumentException ignored) {
                    // Invalid persisted UUID: treat the petting claim as orphaned.
                }
                boolean stillPetting = pettingWarden instanceof org.bukkit.entity.Warden warden
                        && warden.isValid() && !warden.isDead()
                        && entity.getUniqueId().toString().equals(warden.getPersistentDataContainer()
                        .get(wardenPettingTargetKey, PersistentDataType.STRING));
                if (!stillPetting) {
                    pdc.remove(darkSnifferPettingWardenKey);
                } else {
                    clearDarkSnifferWave(pdc);
                    pdc.remove(darkSnifferCombatTargetKey);
                    pdc.remove(darkSnifferNoMeleeTicksKey);
                    sniffer.setTarget(null);
                    sniffer.getPathfinder().stopPathfinding();
                    continue;
                }
            }

            // The reference mob does not enter combat in Peaceful difficulty.
            // Cancel any persisted pursuit/wind-up as soon as the world switches to Peaceful.
            if (sift.getDifficulty() == Difficulty.PEACEFUL) {
                clearDarkSnifferWave(pdc);
                pdc.remove(darkSnifferCombatTargetKey);
                pdc.remove(darkSnifferNoMeleeTicksKey);
                sniffer.setTarget(null);
                sniffer.getPathfinder().stopPathfinding();
                continue;
            }

            String targetId = pdc.get(darkSnifferSonicTargetKey, PersistentDataType.STRING);
            Long windupAt = pdc.get(darkSnifferSonicWindupKey, PersistentDataType.LONG);
            Long waveStart = pdc.get(darkSnifferWaveStartKey, PersistentDataType.LONG);
            if (targetId != null && windupAt != null) {
                Player target;
                try {
                    target = Bukkit.getPlayer(UUID.fromString(targetId));
                } catch (IllegalArgumentException ignored) {
                    // Invalid persisted UUID: drop the stale sonic target and move on to the
                    // next sniffer in the batch instead of aborting the whole tick.
                    pdc.set(darkSnifferSonicTargetKey, PersistentDataType.STRING, "");
                    continue;
                }
                if (target == null || !target.isValid() || target.isDead()
                        || !target.getWorld().equals(sift)
                        || !sniffer.hasLineOfSight(target)
                        || entity.getLocation().distanceSquared(target.getLocation()) > 32.0 * 32.0) {
                    clearDarkSnifferWave(pdc);
                    continue;
                }
                if (waveStart == null) {
                    if (now - windupAt < 500L) {
                        sniffer.lookAt(target);
                        continue;
                    }
                    Vector origin = sniffer.getEyeLocation().toVector();
                    Vector targetPos = target.getEyeLocation().toVector();
                    Vector direct = targetPos.clone().subtract(origin);
                    double distance = direct.length();
                    if (distance < 0.001) {
                        clearDarkSnifferWave(pdc);
                        continue;
                    }
                    double leadTicks = Math.min(20.0, distance / 1.5);
                    Vector velocity = target.getVelocity();
                    Vector predicted = targetPos.clone().add(new Vector(
                            velocity.getX() * leadTicks, 0, velocity.getZ() * leadTicks));
                    Vector direction = predicted.subtract(origin);
                    if (direction.lengthSquared() < 0.000001) direction = direct;
                    double predictedDistance = direction.length();
                    direction.normalize();
                    int flightTicks = Math.max(1, (int) Math.ceil((predictedDistance + 7.5) / 1.5));
                    String waveId = entity.getUniqueId() + ":" + now;
                    pdc.set(darkSnifferWaveOriginXKey, PersistentDataType.DOUBLE, origin.getX());
                    pdc.set(darkSnifferWaveOriginYKey, PersistentDataType.DOUBLE, origin.getY());
                    pdc.set(darkSnifferWaveOriginZKey, PersistentDataType.DOUBLE, origin.getZ());
                    pdc.set(darkSnifferWaveDirectionXKey, PersistentDataType.DOUBLE, direction.getX());
                    pdc.set(darkSnifferWaveDirectionYKey, PersistentDataType.DOUBLE, direction.getY());
                    pdc.set(darkSnifferWaveDirectionZKey, PersistentDataType.DOUBLE, direction.getZ());
                    pdc.set(darkSnifferWaveFlightTicksKey, PersistentDataType.INTEGER, flightTicks);
                    pdc.set(darkSnifferWaveStartKey, PersistentDataType.LONG, now);
                    pdc.set(darkSnifferWaveIdKey, PersistentDataType.STRING, waveId);
                    pdc.set(darkSnifferSonicAtKey, PersistentDataType.LONG, now);
                    sniffer.getWorld().playSound(sniffer.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 0.7f);
                    continue;
                }
                int flightTicks = pdc.getOrDefault(darkSnifferWaveFlightTicksKey, PersistentDataType.INTEGER, 1);
                int age = (int) Math.max(0, (now - waveStart) / 50L);
                if (age >= flightTicks) {
                    clearDarkSnifferWave(pdc);
                    continue;
                }
                Vector origin = new Vector(
                        pdc.getOrDefault(darkSnifferWaveOriginXKey, PersistentDataType.DOUBLE, sniffer.getX()),
                        pdc.getOrDefault(darkSnifferWaveOriginYKey, PersistentDataType.DOUBLE, sniffer.getY() + 1),
                        pdc.getOrDefault(darkSnifferWaveOriginZKey, PersistentDataType.DOUBLE, sniffer.getZ()));
                Vector direction = new Vector(
                        pdc.getOrDefault(darkSnifferWaveDirectionXKey, PersistentDataType.DOUBLE, 0.0),
                        pdc.getOrDefault(darkSnifferWaveDirectionYKey, PersistentDataType.DOUBLE, 0.0),
                        pdc.getOrDefault(darkSnifferWaveDirectionZKey, PersistentDataType.DOUBLE, 1.0));
                Vector previous = origin.clone().add(direction.clone().multiply(1.5 * Math.max(0, age - 1)));
                Vector current = origin.clone().add(direction.clone().multiply(1.5 * age));
                String waveId = pdc.get(darkSnifferWaveIdKey, PersistentDataType.STRING);
                Location waveLoc = current.toLocation(sift);
                sift.spawnParticle(Particle.SONIC_BOOM, waveLoc, 1);
                sift.spawnParticle(Particle.SWEEP_ATTACK, waveLoc, 2, 0.15, 0.15, 0.15, 0);
                for (Entity candidate : sift.getNearbyEntities(waveLoc, 2, 2, 2)) {
                    if (!(candidate instanceof LivingEntity victim) || victim == entity || victim.isDead()) continue;
                    var victimPdc = victim.getPersistentDataContainer();
                    if (waveId != null && waveId.equals(victimPdc.get(darkSnifferWavePulledKey, PersistentDataType.STRING))) continue;
                    Vector center = victim.getLocation().toVector().add(
                            new Vector(0, victim.getHeight() * 0.5, 0));
                    if (distanceSquaredToSegment(center, previous, current) > 0.8 * 0.8) continue;
                    Vector pull = entity.getLocation().toVector().subtract(victim.getLocation().toVector()).multiply(0.3);
                    victim.setVelocity(victim.getVelocity().add(pull));
                    if (waveId != null) victimPdc.set(darkSnifferWavePulledKey, PersistentDataType.STRING, waveId);
                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.35f, 1.15f);
                }
                continue;
            }

            // Refresh the combat target periodically. Dark Sniffers hunt survival/adventure
            // players and ordinary Sniffers, but never other Dark Sniffers.
            String combatTargetId = pdc.get(darkSnifferCombatTargetKey, PersistentDataType.STRING);
            Long refreshAt = pdc.get(darkSnifferTargetRefreshKey, PersistentDataType.LONG);
            LivingEntity combatTarget = null;
            if (combatTargetId != null) {
                try {
                    Entity found = Bukkit.getEntity(UUID.fromString(combatTargetId));
                    if (found instanceof LivingEntity living && living.isValid() && !living.isDead()
                            && living.getWorld().equals(sift) && entity.getLocation().distanceSquared(living.getLocation()) <= 32.0 * 32.0
                            && isDarkSnifferCombatTarget(living, entity)) {
                        combatTarget = living;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid persisted UUID: clear it and acquire a fresh target below.
                }
            }
            if (combatTarget == null || refreshAt == null || now >= refreshAt) {
                LivingEntity nearest = null;
                double nearestDistanceSquared = 32.0 * 32.0;
                for (Entity nearby : sift.getNearbyEntities(entity.getLocation(), 32, 12, 32)) {
                    if (!(nearby instanceof LivingEntity living) || living == entity || living.isDead()
                            || !isDarkSnifferCombatTarget(living, entity)) continue;
                    double distanceSquared = entity.getLocation().distanceSquared(living.getLocation());
                    if (distanceSquared < nearestDistanceSquared) {
                        nearestDistanceSquared = distanceSquared;
                        nearest = living;
                    }
                }
                combatTarget = nearest;
                pdc.set(darkSnifferTargetRefreshKey, PersistentDataType.LONG, now + 500L);
                if (combatTarget == null) {
                    pdc.remove(darkSnifferCombatTargetKey);
                    pdc.remove(darkSnifferNoMeleeTicksKey);
                    sniffer.setTarget(null);
                    // Mirror the mod's home anchor and calm wandering rather than leaving
                    // idle Dark Sniffers frozen after their first spawn or combat encounter.
                    Location home;
                    Double homeX = pdc.get(darkSnifferHomeXKey, PersistentDataType.DOUBLE);
                    Double homeY = pdc.get(darkSnifferHomeYKey, PersistentDataType.DOUBLE);
                    Double homeZ = pdc.get(darkSnifferHomeZKey, PersistentDataType.DOUBLE);
                    if (homeX == null || homeY == null || homeZ == null) {
                        home = entity.getLocation();
                        pdc.set(darkSnifferHomeXKey, PersistentDataType.DOUBLE, home.getX());
                        pdc.set(darkSnifferHomeYKey, PersistentDataType.DOUBLE, home.getY());
                        pdc.set(darkSnifferHomeZKey, PersistentDataType.DOUBLE, home.getZ());
                    } else {
                        home = new Location(sift, homeX, homeY, homeZ);
                    }
                    double homeDistance = entity.getLocation().distanceSquared(home);
                    Long idlePathAt = pdc.get(darkSnifferIdlePathAtKey, PersistentDataType.LONG);
                    if (homeDistance > 10000.0) {
                        if (!sniffer.getPathfinder().hasPath()) {
                            sniffer.getPathfinder().moveTo(home, 0.1);
                            pdc.set(darkSnifferIdlePathAtKey, PersistentDataType.LONG, now + 1500L);
                        }
                    } else if (idlePathAt == null || now >= idlePathAt || !sniffer.getPathfinder().hasPath()) {
                        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(30) == 0) {
                            Location wander = entity.getLocation().clone().add(
                                    java.util.concurrent.ThreadLocalRandom.current().nextDouble(-12, 13), 0,
                                    java.util.concurrent.ThreadLocalRandom.current().nextDouble(-12, 13));
                            sniffer.getPathfinder().moveTo(wander, 0.1);
                        }
                        pdc.set(darkSnifferIdlePathAtKey, PersistentDataType.LONG, now + 1500L);
                    }
                    continue;
                }
                pdc.set(darkSnifferCombatTargetKey, PersistentDataType.STRING, combatTarget.getUniqueId().toString());
            }
            sniffer.setTarget(combatTarget);
            sniffer.lookAt(combatTarget);

            double targetDistanceSquared = entity.getLocation().distanceSquared(combatTarget.getLocation());
            int noMeleeTicks = pdc.getOrDefault(darkSnifferNoMeleeTicksKey, PersistentDataType.INTEGER, 0);
            if (targetDistanceSquared > 3.15 * 3.15) {
                noMeleeTicks = Math.min(8, noMeleeTicks + 1);
                pdc.set(darkSnifferNoMeleeTicksKey, PersistentDataType.INTEGER, noMeleeTicks);
                Long pathAt = pdc.get(darkSnifferPathRefreshKey, PersistentDataType.LONG);
                if (pathAt == null || now >= pathAt) {
                    sniffer.getPathfinder().moveTo(combatTarget, 1.2);
                    pdc.set(darkSnifferPathRefreshKey, PersistentDataType.LONG, now + 300L);
                }
            } else {
                pdc.set(darkSnifferNoMeleeTicksKey, PersistentDataType.INTEGER, 0);
                sniffer.getPathfinder().stopPathfinding();
                Long attackAt = pdc.get(darkSnifferAttackAtKey, PersistentDataType.LONG);
                if (attackAt == null || now - attackAt >= 1_200L) {
                    // Same config key the spawn stat line uses (mobs.dark_sniffer.attack-damage,
                    // underscored like every mobs.<id> key); the melee path must honour it too.
                    combatTarget.damage(plugin.getConfig().getDouble("mobs.dark_sniffer.attack-damage", 7.0), sniffer);
                    Vector knockback = combatTarget.getLocation().toVector()
                            .subtract(entity.getLocation().toVector());
                    knockback.setY(0);
                    if (knockback.lengthSquared() > 0.0001) {
                        knockback.normalize().multiply(0.75);
                        knockback.setY(0.25);
                        combatTarget.setVelocity(combatTarget.getVelocity().add(knockback));
                    }
                    pdc.set(darkSnifferAttackAtKey, PersistentDataType.LONG, now);
                    sniffer.getWorld().playSound(sniffer.getLocation(), Sound.ENTITY_SNIFFER_HURT, 0.8f, 0.75f);
                }
            }

            // Sonic Sniff is a ranged attack used only after the Dark Sniffer has failed
            // to reach melee range for eight ticks, matching the reference mob's combat loop.
            Long lastSonicAt = pdc.get(darkSnifferSonicAtKey, PersistentDataType.LONG);
            boolean sonicReady = lastSonicAt == null || now - lastSonicAt >= 5_000L;
            if (combatTarget instanceof Player player && targetDistanceSquared >= 25.0
                    && noMeleeTicks >= 8 && sonicReady && sniffer.hasLineOfSight(player)) {
                pdc.set(darkSnifferSonicTargetKey, PersistentDataType.STRING, player.getUniqueId().toString());
                pdc.set(darkSnifferSonicWindupKey, PersistentDataType.LONG, now);
                pdc.remove(darkSnifferWaveStartKey);
                sniffer.getPathfinder().stopPathfinding();
                sift.playSound(entity.getLocation(), Sound.ENTITY_SNIFFER_SNIFFING, 1.0f, 0.7f);
            }
        }
    }

    /**
     * Paper approximation of the reference mod's calm-Warden / Dark Sniffer bonding.
     * The Bukkit API does not expose the Warden's complete internal anger/vibration memories,
     * so we use its public target state as the calmness guard.
     */
    private void tickWardenPetting() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null) return;
        long now = System.currentTimeMillis();
        for (Entity entity : indexedMobs(sift, "type:WARDEN")) {
            if (!(entity instanceof org.bukkit.entity.Warden warden) || !warden.isValid() || warden.isDead()) continue;
            var pdc = warden.getPersistentDataContainer();
            String targetId = pdc.get(wardenPettingTargetKey, PersistentDataType.STRING);
            if (targetId != null) {
                Entity candidate = null;
                try {
                    candidate = Bukkit.getEntity(UUID.fromString(targetId));
                } catch (IllegalArgumentException ignored) {
                    // Invalid persisted UUID: abandon this petting attempt.
                }
                boolean calm = warden.getTarget() == null;
                if (!(candidate instanceof Mob darkSniffer) || !darkSniffer.isValid() || darkSniffer.isDead()
                        || !darkSniffer.getWorld().equals(sift)
                        || darkSniffer.getLocation().distanceSquared(warden.getLocation()) > 324.0
                        || !"dark_sniffer".equals(darkSniffer.getPersistentDataContainer()
                        .get(creatureKey, PersistentDataType.STRING)) || !calm) {
                    stopWardenPetting(warden, candidate, false, now);
                    continue;
                }

                var snifferPdc = darkSniffer.getPersistentDataContainer();
                snifferPdc.set(darkSnifferPettingWardenKey, PersistentDataType.STRING, warden.getUniqueId().toString());
                warden.lookAt(darkSniffer);
                if (darkSniffer.getLocation().distanceSquared(warden.getLocation()) > 12.25) {
                    Long approachAt = pdc.get(wardenPettingApproachAtKey, PersistentDataType.LONG);
                    if (approachAt == null) {
                        pdc.set(wardenPettingApproachAtKey, PersistentDataType.LONG, now);
                        approachAt = now;
                    }
                    if (now - approachAt > 12_000L) {
                        stopWardenPetting(warden, darkSniffer, false, now);
                    } else {
                        warden.getPathfinder().moveTo(darkSniffer, 0.55);
                    }
                } else {
                    warden.getPathfinder().stopPathfinding();
                    Long pettingAt = pdc.get(wardenPettingStartKey, PersistentDataType.LONG);
                    if (pettingAt == null) {
                        pdc.set(wardenPettingStartKey, PersistentDataType.LONG, now);
                        pettingAt = now;
                    }
                    Long heartAt = pdc.get(wardenPettingHeartAtKey, PersistentDataType.LONG);
                    if (heartAt == null || now - heartAt >= 1_000L) {
                        sift.spawnParticle(Particle.HEART,
                                darkSniffer.getLocation().add(0, 1.7, 0), 1, 0.18, 0.08, 0.18, 0);
                        pdc.set(wardenPettingHeartAtKey, PersistentDataType.LONG, now);
                    }
                    if (now - pettingAt >= 5_000L) stopWardenPetting(warden, darkSniffer, true, now);
                }
                continue;
            }

            Long searchAt = pdc.get(wardenPettingSearchAtKey, PersistentDataType.LONG);
            if (searchAt != null && now < searchAt) continue;
            if (warden.getTarget() != null) continue;
            // First search after 40–80 seconds; successful petting rests for two minutes.
            if (searchAt == null) {
                pdc.set(wardenPettingSearchAtKey, PersistentDataType.LONG,
                        now + java.util.concurrent.ThreadLocalRandom.current().nextLong(40_000L, 80_001L));
                continue;
            }
            Mob nearest = null;
            double nearestDistance = 18.0 * 18.0;
            for (Entity nearby : warden.getNearbyEntities(18, 10, 18)) {
                if (!(nearby instanceof Mob mob) || mob.getType() != EntityType.SNIFFER || mob.isDead()
                        || !"dark_sniffer".equals(mob.getPersistentDataContainer()
                        .get(creatureKey, PersistentDataType.STRING))) continue;
                var snifferPdc = mob.getPersistentDataContainer();
                if (snifferPdc.has(darkSnifferCombatTargetKey, PersistentDataType.STRING)
                        || snifferPdc.has(darkSnifferSonicWindupKey, PersistentDataType.LONG)
                        || snifferPdc.has(darkSnifferWaveStartKey, PersistentDataType.LONG)
                        || snifferPdc.has(darkSnifferPettingWardenKey, PersistentDataType.STRING)) continue;
                double distance = mob.getLocation().distanceSquared(warden.getLocation());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = mob;
                }
            }
            if (nearest == null) {
                pdc.set(wardenPettingSearchAtKey, PersistentDataType.LONG, now + 15_000L);
                continue;
            }
            pdc.set(wardenPettingTargetKey, PersistentDataType.STRING, nearest.getUniqueId().toString());
            pdc.set(wardenPettingApproachAtKey, PersistentDataType.LONG, now);
            pdc.remove(wardenPettingStartKey);
            pdc.remove(wardenPettingHeartAtKey);
            nearest.getPersistentDataContainer().set(darkSnifferPettingWardenKey,
                    PersistentDataType.STRING, warden.getUniqueId().toString());
        }
    }

    private void stopWardenPetting(org.bukkit.entity.Warden warden, Entity target, boolean completed, long now) {
        if (target != null) {
            var targetPdc = target.getPersistentDataContainer();
            String owner = targetPdc.get(darkSnifferPettingWardenKey, PersistentDataType.STRING);
            if (warden.getUniqueId().toString().equals(owner)) targetPdc.remove(darkSnifferPettingWardenKey);
            if (target instanceof Mob mob) mob.getPathfinder().stopPathfinding();
        }
        var pdc = warden.getPersistentDataContainer();
        pdc.remove(wardenPettingTargetKey);
        pdc.remove(wardenPettingApproachAtKey);
        pdc.remove(wardenPettingStartKey);
        pdc.remove(wardenPettingHeartAtKey);
        pdc.set(wardenPettingSearchAtKey, PersistentDataType.LONG,
                now + (completed ? 120_000L : 30_000L));
        warden.getPathfinder().stopPathfinding();
    }

    private boolean isDarkSnifferCombatTarget(LivingEntity target, Entity darkSniffer) {
        if (target instanceof Player player) {
            return player.isOnline() && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                    && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
        }
        return target.getType() == EntityType.SNIFFER && target != darkSniffer
                && !"dark_sniffer".equals(target.getPersistentDataContainer()
                .get(creatureKey, PersistentDataType.STRING));
    }

    private static double distanceSquaredToSegment(Vector point, Vector start, Vector end) {
        Vector segment = end.clone().subtract(start);
        double lengthSquared = segment.lengthSquared();
        if (lengthSquared < 1.0E-9) return point.distanceSquared(start);
        double t = Math.max(0, Math.min(1, point.clone().subtract(start).dot(segment) / lengthSquared));
        Vector closest = start.clone().add(segment.multiply(t));
        return point.distanceSquared(closest);
    }

    private void clearDarkSnifferWave(org.bukkit.persistence.PersistentDataContainer pdc) {
        pdc.remove(darkSnifferSonicTargetKey);
        pdc.remove(darkSnifferSonicWindupKey);
        pdc.remove(darkSnifferWaveStartKey);
        pdc.remove(darkSnifferWaveOriginXKey);
        pdc.remove(darkSnifferWaveOriginYKey);
        pdc.remove(darkSnifferWaveOriginZKey);
        pdc.remove(darkSnifferWaveDirectionXKey);
        pdc.remove(darkSnifferWaveDirectionYKey);
        pdc.remove(darkSnifferWaveDirectionZKey);
        pdc.remove(darkSnifferWaveFlightTicksKey);
        pdc.remove(darkSnifferWaveIdKey);
    }

    private void tickDarkSniffers() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null) return;
        // Use the world's persisted game-time clock, not wall-clock milliseconds:
        // infection should advance while the world ticks and must pause while the server is offline.
        long now = sift.getFullTime();
        // The source mod infects a Sniffer when it ENTERS its DIGGING state while
        // the block beneath the point 2.25 blocks ahead of its head is sculk.
        // Paper cannot inject into Sniffer.transitionTo(), so sample the state here;
        // once infection starts, the saved tick count persists even after digging ends.
        long infectionTicks = Math.max(1, plugin.getConfig().getInt("mobs.dark_sniffer.corruption-ticks", 600));
        java.util.List<Entity> conversions = new java.util.ArrayList<>();
        for (Entity entity : indexedMobs(sift, "dark_sniffer", "type:SNIFFER")) {
            if (!(entity instanceof org.bukkit.entity.Sniffer sniffer)) continue;
            var pdc = entity.getPersistentDataContainer();
            if (pdc.has(creatureKey, PersistentDataType.STRING)) continue; // already a Dark Sniffer
            Long infectionStartedAt = pdc.get(snifferCorruptionStartKey, PersistentDataType.LONG);
            // Older builds stored epoch milliseconds here; reset that legacy value safely.
            if (infectionStartedAt != null && infectionStartedAt > now + 1200L) {
                infectionStartedAt = null;
                pdc.remove(snifferCorruptionStartKey);
            }
            Location loc = entity.getLocation();
            if (infectionStartedAt == null) {
                if (sniffer.getState() != org.bukkit.entity.Sniffer.State.DIGGING) continue;
                Vector forward = loc.getDirection().setY(0).normalize();
                if (forward.lengthSquared() < 0.0001) continue;
                Location sniffedBlock = loc.clone().add(forward.multiply(2.25));
                Block sculkBelowSnout = sniffedBlock.getBlock().getRelative(0, -1, 0);
                if (sculkBelowSnout.getType() != Material.SCULK) continue;
                infectionStartedAt = now;
                pdc.set(snifferCorruptionStartKey, PersistentDataType.LONG, infectionStartedAt);
                sift.spawnParticle(Particle.WITCH, loc.clone().add(0, 1.5, 0), 6, 0.4, 0.3, 0.4, 0.1);
                continue;
            }
            // Infection is irreversible in the reference mod and is saved with the Sniffer.
            if (now - infectionStartedAt >= infectionTicks) conversions.add(entity);
        }
        // Convert after iteration so removal never disturbs the live entity list mid-scan.
        // Spawn first: if a provider refuses the replacement, keep the original Sniffer alive.
        for (Entity entity : conversions) {
            Location loc = entity.getLocation();
            if (!(entity instanceof LivingEntity original)) continue;
            double originalHealth = original.getHealth();
            java.util.Set<UUID> nearbyBefore = new java.util.HashSet<>();
            for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 4, 4, 4)) {
                nearbyBefore.add(nearby.getUniqueId());
            }
            if (!summonCreature("dark_sniffer", loc)) {
                plugin.getLogger().warning("Could not convert Sniffer to Dark Sniffer at "
                        + loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY()
                        + ":" + loc.getBlockZ() + "; keeping the original Sniffer.");
                continue;
            }
            // summonCreature is a boolean public API; identify only the replacement spawned
            // by this call so we can preserve the source mod's absolute-health conversion rule.
            LivingEntity replacement = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 4, 4, 4)) {
                if (!(nearby instanceof LivingEntity living) || nearbyBefore.contains(nearby.getUniqueId())) continue;
                if (!"dark_sniffer".equals(nearby.getPersistentDataContainer()
                        .get(creatureKey, PersistentDataType.STRING))) continue;
                double distance = nearby.getLocation().distanceSquared(loc);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    replacement = living;
                }
            }
            if (replacement == null) {
                plugin.getLogger().warning("Dark Sniffer spawned but could not be identified at "
                        + loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY()
                        + ":" + loc.getBlockZ() + "; keeping the original Sniffer to avoid data loss.");
                continue;
            }
            if (replacement.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                replacement.setHealth(Math.max(0.1, Math.min(originalHealth,
                        replacement.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue())));
            }
            replacement.setPersistent(original.isPersistent());
            replacement.setRemoveWhenFarAway(original.getRemoveWhenFarAway());
            if (original.getCustomName() != null) replacement.setCustomName(original.getCustomName());
            replacement.setCustomNameVisible(original.isCustomNameVisible());
            entity.getPersistentDataContainer().remove(snifferCorruptionStartKey);
            entity.remove();
            sift.playSound(loc, Sound.ENTITY_SNIFFER_DIGGING, 0.8f, 0.6f);
            sift.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, 1, 0), 20, 0.5, 0.6, 0.5, 0.05);
        }
    }

    /**
     * Reference-mod parity: Wardens never treat Dark Sniffers as valid targets.
     * EntityTargetLivingEntityEvent covers normal AI target selection and retaliation;
     * the Dark Sniffer is identified by our content tag rather than its vanilla entity type.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWardenTargetDarkSniffer(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Warden)) return;
        LivingEntity target = event.getTarget();
        if (target == null) return;
        if ("dark_sniffer".equals(target.getPersistentDataContainer()
                .get(creatureKey, PersistentDataType.STRING))) {
            event.setCancelled(true);
        }
    }

    /**
     * Reference-mod parity (Blub): right-clicking a wild Blub with a Soul Block tames it.
     * The tamed Blub follows its owner and defends them against hostile mobs. Sitting is not
     * implemented because a Rabbit has no leash/sit path without an item in hand; the mod's
     * "sit" state is a GeckoLib animation we cannot mirror on a vanilla stand-in.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlubInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity target = event.getRightClicked();
        if (!"blub".equals(target.getPersistentDataContainer().get(creatureKey, PersistentDataType.STRING))) {
            return;
        }
        Player player = event.getPlayer();
        var pdc = target.getPersistentDataContainer();
        String ownerId = pdc.get(blubOwnerKey, PersistentDataType.STRING);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (ownerId == null && content.is(hand, "soul_block")) {
            if (!plugin.getConfig().getBoolean("content.blub", true)) return;
            // Reference-mod parity: the Soul Block is always consumed and tames the Blub on
            // a 1-in-3 roll (mobs.blub.tame-chance), not on every attempt.
            consumeOne(player, hand);
            event.setCancelled(true);
            if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("mobs.blub.tame-chance", 1.0 / 3.0)) {
                Messages.send(player, "blub.failed", "The Blub sniffs the Soul Block and wanders off. Try again.");
                return;
            }
            pdc.set(blubOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            if (target instanceof LivingEntity living) living.setRemoveWhenFarAway(false);
            Messages.send(player, "blub.tamed", "The Blub accepts your Soul Block and follows you.");
            SiftAdvancementGrant.grant(plugin, player, "blub");
        } else if (ownerId != null && ownerId.equals(player.getUniqueId().toString())) {
            event.setCancelled(true);
            // Reference-mod parity (Blub): right-clicking your Blub with an empty hand
            // toggles stay/follow (a Rabbit cannot play the mod's sit animation, so staying
            // freezes the mob in place with its AI paused).
            if (hand.getType().isAir()) {
                boolean staying = pdc.has(blubStayKey, PersistentDataType.BYTE);
                if (staying) {
                    pdc.remove(blubStayKey);
                    if (target instanceof Mob mob) mob.setAI(true);
                    Messages.send(player, "blub.follow", "The Blub trots after you again.");
                } else {
                    pdc.set(blubStayKey, PersistentDataType.BYTE, (byte) 1);
                    if (target instanceof Mob mob) {
                        mob.setAI(false);
                        mob.getPathfinder().stopPathfinding();
                    }
                    Messages.send(player, "blub.stay", "The Blub settles down and stays.");
                }
                return;
            }
            // Already yours: a small heart response, mirroring the mod's bond feedback.
            target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 0.6, 0), 3);
        }
    }

    /**
     * Mod-parity spawn eggs: right-clicking a block with a tagged Sift spawn egg summons the
     * matching creature through the normal {@link #summonCreature} path (provider-first spawn,
     * config stats, PDC tagging). Vanilla stand-in eggs carry our content id in their PDC, so
     * this never interferes with real vanilla eggs.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onSpawnEggUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = event.getItem();
        if (hand == null || !hand.getType().name().endsWith("_SPAWN_EGG")) return;
        String species = content.idOf(hand);
        if (species == null) return;
        Player player = event.getPlayer();
        // Cancel first, always: the held item is a vanilla spawn egg, so an uncancelled event
        // would spawn the raw vanilla stand-in mob (Warden, Ravager, ...) with none of the
        // Sift limits - the permission check must not be the only gate.
        event.setCancelled(true);
        if (!plugin.getConfig().getBoolean("spawn-eggs.enabled", true)) {
            Messages.send(player, "spawn-eggs.disabled", "Sift spawn eggs are disabled by configuration.");
            return;
        }
        if (!"all".equals(plugin.getConfig().getString("spawn-eggs.permission-mode", "op"))
                && !player.isOp()) {
            Messages.send(player, "spawn-eggs.denied", "Sift spawn eggs require op in the current permission mode.");
            return;
        }
        Block clicked = event.getClickedBlock();
        Location at = clicked.getLocation().add(0.5, 1.0, 0.5);
        // Special mobs keep their dedicated managers so soul-event/charoite wiring applies.
        // The managers only exist while their content flags are enabled.
        boolean spawned = switch (species) {
            case "echo_golem" -> echoGolemManager != null && echoGolemManager.summonEchoGolem(at) != null;
            case "singer" -> resonanceManager != null && resonanceManager.summonSinger(at) != null;
            default -> summonCreature(species, at);
        };
        if (spawned) {
            consumeOne(player, hand);
        } else {
            player.sendMessage("§c" + Messages.get("spawn-eggs.unknown",
                    "Unknown Sift creature: %species%").replace("%species%", species));
        }
    }

    /** Follow + defend tick for tamed Blubs, batched per world instead of per player. */
    private void tickBlubs() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null) return;
        for (Entity entity : indexedMobs(sift, "blub")) {
            if (!(entity instanceof Mob blub)) continue;
            var pdc = blub.getPersistentDataContainer();
            if (!"blub".equals(pdc.get(creatureKey, PersistentDataType.STRING))) continue;
            String ownerId = pdc.get(blubOwnerKey, PersistentDataType.STRING);
            if (pdc.has(blubStayKey, PersistentDataType.BYTE)) continue; // staying put
            if (ownerId == null) {
                // Reference-mod parity (Blub): wild Blubs are drawn to a nearby player holding
                // a Soul Block - the same item that tames them.
                if (blub.getTarget() == null) {
                    for (Entity holder : blub.getNearbyEntities(10, 6, 10)) {
                        if (!(holder instanceof Player nearby)
                                || nearby.getGameMode() == GameMode.SPECTATOR) continue;
                        if (content.is(nearby.getInventory().getItemInMainHand(), "soul_block")
                                || content.is(nearby.getInventory().getItemInOffHand(), "soul_block")) {
                            blub.getPathfinder().moveTo(nearby.getLocation());
                            break;
                        }
                    }
                }
                continue;
            }
            Player owner;
            try {
                owner = Bukkit.getPlayer(UUID.fromString(ownerId));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (owner == null || !owner.isOnline() || owner.getWorld() != sift) continue;
            // Defend: target the most recent hostile attacker of the owner within range,
            // otherwise follow (teleport-lead like the mod's seated/following behaviour).
            boolean defending = false;
            if (blub.getTarget() == null) {
                for (Entity holder : sift.getNearbyEntities(owner.getLocation(), 8, 4, 8)) {
                    if (!(holder instanceof Mob hostile)) continue;
                    if (hostile.getTarget() == owner && hostile.getLocation()
                            .distanceSquared(blub.getLocation()) < 64) {
                        blub.setTarget(hostile);
                        defending = true;
                        break;
                    }
                }
            } else if (blub.getTarget() instanceof Mob hostile && hostile.getTarget() == owner) {
                defending = true;
            }
            double distance = blub.getLocation().distanceSquared(owner.getLocation());
            if (!defending && distance > 25) {
                if (distance > 900) {
                    Location anchor = owner.getLocation().add(0, 0.2, 0);
                    if (blub.teleport(anchor)) {
                        sift.spawnParticle(Particle.POOF, anchor, 4, 0.2, 0.2, 0.2, 0);
                    }
                } else {
                    // Walk toward the owner using Paper's Pathfinder API (no NMS needed).
                    blub.getPathfinder().moveTo(owner.getLocation());
                }
            }
        }
    }

    private void consumeOne(Player player, ItemStack hand) {
        // Vanilla behaviour: creative players keep their stack.
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }
}
