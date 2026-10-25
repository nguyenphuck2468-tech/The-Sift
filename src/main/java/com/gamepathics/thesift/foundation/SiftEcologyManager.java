package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
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
    private final SiftMobFactory mobFactory;
    private final ExternalBlockBridge blockBridge;
    private final SiftiteContent content;
    private final BukkitTask tideTask;
    private final AtomicLong providerPlacementFailures = new AtomicLong();
    private final AtomicLong deferredDecorationRetries = new AtomicLong();
    private final java.util.Map<String, AtomicLong> providerPlacementFailuresById = new ConcurrentHashMap<>();
    private volatile String lastProviderPlacementFailure = "none";
    private long tideTick;

    public SiftEcologyManager(JavaPlugin plugin, ResonanceManager resonanceManager,
                              EchoGolemManager echoGolemManager, RiftManager riftManager) {
        this.plugin = plugin;
        this.resonanceManager = resonanceManager;
        this.echoGolemManager = echoGolemManager;
        this.riftManager = riftManager;
        this.decoratedChunkKey = new NamespacedKey(plugin, "sift_ecology_generated");
        this.decorationRetryAttemptsKey = new NamespacedKey(plugin, "sift_ecology_retry_attempts");
        this.decorationRetryAtKey = new NamespacedKey(plugin, "sift_ecology_retry_at");
        this.creatureKey = new NamespacedKey(plugin, "sift_creature");
        this.mobFactory = new SiftMobFactory(plugin);
        this.blockBridge = new ExternalBlockBridge(plugin);
        this.content = new SiftiteContent(plugin);
        this.tideTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tickTide();
            tickIchorWeather();
            tickDarkSniffers();
        }, 100L, 100L);
    }

    public void shutdown() {
        tideTask.cancel();
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

    private void populate(Chunk chunk) {
        if (!isSift(chunk.getWorld())) return;
        if (ChunkRetryBackoff.isDeferred(chunk, decorationRetryAtKey, System.currentTimeMillis())) {
            deferredDecorationRetries.incrementAndGet();
            return;
        }

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
        Biome biome = chunk.getBlock(8, Math.max(1, chunk.getWorld().getMinHeight()), 8).getBiome();
        String key = biome.getKey().toString().toLowerCase(Locale.ROOT);

        // In strict mode only provider-owned blocks and mobs are content. The
        // vanilla-backed decorative structures, plants, ores, portal frames,
        // chest loot and flowing-water stand-in below remain dev/CI behavior.
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
        if ((key.contains("overgrown") || key.endsWith("carapace")) && random.nextInt(24) == 0) {
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
        spawnSiftWildlife(chunk, key, random);
        generationOperations++;
        if (key.endsWith("pink_meadows") && random.nextInt(22) == 0) {
            summonSinger(chunk, random);
            generationOperations++;
        } else if (key.endsWith("crystal_spires") && random.nextInt(55) == 0) {
            summonEchoGolem(chunk, random);
            generationOperations++;
        }
        if (key.endsWith("shattered_peaks") && plugin.getConfig().getBoolean("content.rift-events", true)
                && random.nextInt(120) == 0) {
            openNaturalRift(chunk);
            generationOperations++;
        }
        if (key.endsWith("bulb_forest") && random.nextInt(160) == 0) {
            spawnBoss(chunk, EntityType.WARDEN, "Prism Warden", "prism_warden", 180.0, 28.0);
            generationOperations++;
        } else if (key.endsWith("shattered_peaks") && random.nextInt(120) == 0) {
            spawnBoss(chunk, EntityType.RAVAGER, "The Sifter", "the_sifter", 220.0, 24.0);
            generationOperations++;
        }
        return generationOperations;
    }

    private void decorateStrictProviderContent(Chunk chunk, String biome, Random random) {
        decorateStrictSurface(chunk, biome, random);
        growStrictProviderLandmark(chunk, biome, random);
        generateCharoiteOre(chunk, random);
        generateSiftiteOre(chunk, random);
        spawnSiftWildlife(chunk, biome, random);
        if (biome.endsWith("pink_meadows") && random.nextInt(22) == 0) summonSinger(chunk, random);
        else if (biome.endsWith("crystal_spires") && random.nextInt(55) == 0) summonEchoGolem(chunk, random);
        if (biome.endsWith("shattered_peaks") && plugin.getConfig().getBoolean("content.rift-events", true)
                && random.nextInt(120) == 0) openNaturalRift(chunk);
        if (biome.endsWith("bulb_forest") && random.nextInt(160) == 0)
            spawnBoss(chunk, EntityType.WARDEN, "Prism Warden", "prism_warden", 180.0, 28.0);
        else if (biome.endsWith("shattered_peaks") && random.nextInt(120) == 0)
            spawnBoss(chunk, EntityType.RAVAGER, "The Sifter", "the_sifter", 220.0, 24.0);
    }

    private void decorateStrictSurface(Chunk chunk, String biome, Random random) {
        if (!(biome.endsWith("pink_meadows") || biome.endsWith("bulb_forest")
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
        boolean forest = biome.endsWith("bulb_forest") || biome.endsWith("pink_meadows")
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
            if (dx * dx + dz * dz <= 2) {
                placeBlock(world.getBlockAt(x + dx, y + 1, z + dz), "ichor_water", Material.WATER);
            }
        }
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

    private void generateCharoiteOre(Chunk chunk, Random random) {
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

    /** Ore veins required for ordinary survival progression in a biome set with no data-driven features. */
    private void generateSiftResources(Chunk chunk, Random random) {
        generateOreVeins(chunk, random, Material.IRON_ORE, 2, -24, 56, 5);
        generateOreVeins(chunk, random, Material.COAL_ORE, 2, 8, 112, 7);
        generateOreVeins(chunk, random, Material.COPPER_ORE, 1, -16, 80, 5);
        generateOreVeins(chunk, random, Material.REDSTONE_ORE, 1, -56, 16, 5);
        generateOreVeins(chunk, random, Material.LAPIS_ORE, 1, -40, 48, 3);
        if (random.nextInt(4) == 0) generateOreVeins(chunk, random, Material.DIAMOND_ORE, 1, -58, 8, 3);
        if (random.nextInt(8) == 0) generateOreVeins(chunk, random, Material.GOLD_ORE, 1, -48, 24, 4);
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
            int x = chunk.getX() * 16 + 2 + random.nextInt(12);
            int z = chunk.getZ() * 16 + 2 + random.nextInt(12);
            int y = world.getHighestBlockYAt(x, z) + 1;
            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 3) continue;
            EntityType type;
            String name;
            if (biome.endsWith("carapace")) {
                String[][] carapace = {{"TURTLE", "Thornback"}, {"AXOLOTL", "Blub"},
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

    @EventHandler
    public void onSiftBossDeath(EntityDeathEvent event) {
        String id = event.getEntity().getPersistentDataContainer().get(creatureKey, PersistentDataType.STRING);
        if ("prism_warden".equals(id) || "the_sifter".equals(id)) {
            event.getDrops().add(new SiftiteContent(plugin).item(Material.ECHO_SHARD,
                    "heart_of_the_sift", "Heart of the Sift"));
            event.getDrops().add(new SiftiteContent(plugin).item(Material.AMETHYST_SHARD,
                    "sift_shard", "Sift Shard"));
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
            case "sifter" -> { type = EntityType.RAVAGER; display = "Sifter"; }
            case "frost_stalker" -> { type = EntityType.STRAY; display = "Frost Stalker"; }
            case "sunspore" -> { type = EntityType.MOOSHROOM; display = "Sunspore"; }
            case "drifter" -> { type = EntityType.ENDERMAN; display = "Drifter"; }
            case "shardling" -> { type = EntityType.ENDERMITE; display = "Shardling"; }
            case "prism_warden" -> { type = EntityType.WARDEN; display = "Prism Warden"; }
            case "prismite" -> { type = EntityType.SILVERFISH; display = "Prismite"; }
            default -> { return false; }
        }

        boolean boss = id.equals("the_sifter") || id.equals("prism_warden");
        Entity entity = mobFactory.spawn(id, location, type);
        if (entity == null) return false;
        entity.setPersistent(true);
        entity.setCustomName((boss ? "§5§l" : "§d") + display);
        entity.setCustomNameVisible(true);
        entity.getPersistentDataContainer().set(creatureKey, PersistentDataType.STRING, id);
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            if (boss) {
                double health = id.equals("the_sifter") ? 220.0 : 180.0;
                double damage = id.equals("the_sifter") ? 24.0 : 28.0;
                if (living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
                    living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(health);
                    living.setHealth(health);
                }
                if (living.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) != null) {
                    living.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(damage);
                }
            }
        }
        return true;
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
                player.sendActionBar(flow ? "§bThe Sift tide is flowing around you" : "§5The Sift tide is ebbing");
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

    /** Ichor rain/snow visual and contact-effect approximation for the reference mod weather. */
    private void tickIchorWeather() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null || sift.getPlayers().isEmpty()) return;
        for (Player player : sift.getPlayers()) {
            Material contact = player.getLocation().getBlock().getType();
            if (contact == Material.WATER || contact == Material.BUBBLE_COLUMN) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 0, true, false, true));
            }
        }
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
            if (player.getLocation().getBlock().getLightFromSky() > 0) {
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

    private void tickDarkSniffers() {
        World sift = Bukkit.getWorld(NamespacedKey.fromString(DIMENSION));
        if (sift == null) return;
        for (Player target : sift.getPlayers()) {
            for (Entity entity : target.getNearbyEntities(18, 10, 18)) {
                if (!(entity instanceof LivingEntity living) || entity.getType() != EntityType.SNIFFER) continue;
                String id = entity.getPersistentDataContainer().get(creatureKey, PersistentDataType.STRING);
                if (!"dark_sniffer".equals(id)) continue;
                Vector pull = living.getLocation().toVector().subtract(target.getLocation().toVector());
                if (pull.lengthSquared() < 0.01) continue;
                pull.normalize().multiply(0.28).setY(0.18);
                target.setVelocity(target.getVelocity().multiply(0.45).add(pull));
                target.playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.25f, 1.6f);
                sift.spawnParticle(Particle.SONIC_BOOM, target.getLocation().add(0, 1, 0), 1);
            }
        }
    }
}
