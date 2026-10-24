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

import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Early vanilla-backed approximation of The Sift's world generation and living content. */
public final class SiftEcologyManager implements Listener {
    private static final String DIMENSION = "thesift:the_sift";

    private final JavaPlugin plugin;
    private final ResonanceManager resonanceManager;
    private final EchoGolemManager echoGolemManager;
    private final RiftManager riftManager;
    private final NamespacedKey decoratedChunkKey;
    private final NamespacedKey creatureKey;
    private final SiftMobFactory mobFactory;
    private final BukkitTask tideTask;
    private long tideTick;

    public SiftEcologyManager(JavaPlugin plugin, ResonanceManager resonanceManager,
                              EchoGolemManager echoGolemManager, RiftManager riftManager) {
        this.plugin = plugin;
        this.resonanceManager = resonanceManager;
        this.echoGolemManager = echoGolemManager;
        this.riftManager = riftManager;
        this.decoratedChunkKey = new NamespacedKey(plugin, "sift_ecology_generated");
        this.creatureKey = new NamespacedKey(plugin, "sift_creature");
        this.mobFactory = new SiftMobFactory(plugin);
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
        if (!isSift(chunk.getWorld())
                || chunk.getPersistentDataContainer().has(decoratedChunkKey, PersistentDataType.BYTE)) {
            return;
        }

        // Mark first so a failed decoration never duplicates mobs/structures on reload.
        chunk.getPersistentDataContainer().set(decoratedChunkKey, PersistentDataType.BYTE, (byte) 1);
        long seed = chunk.getWorld().getSeed() ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L);
        Random random = new Random(seed);
        Biome biome = chunk.getBlock(8, Math.max(1, chunk.getWorld().getMinHeight()), 8).getBiome();
        String key = biome.getKey().toString().toLowerCase(Locale.ROOT);

        decorateSurface(chunk, key, random);
        growBiomeLandmarks(chunk, key, random);
        if ((key.contains("overgrown") || key.contains("sift_wastes") || key.contains("siftslate"))
                && random.nextInt(18) == 0) generateAbandonedPortal(chunk, random);
        if ((key.contains("overgrown") || key.endsWith("carapace")) && random.nextInt(24) == 0) {
            generateIchorSpring(chunk, random);
        }
        if (random.nextInt(32) == 0) {
            if (key.contains("deep_dark")) generateSnifferCave(chunk, random);
            else if (key.contains("siftslate") || key.contains("wastes")) generateSoulCanyon(chunk, random);
            else if (key.contains("overgrown_peaks") || key.contains("ichor_snowy_peaks")) {
                generateCliffArch(chunk, random);
            } else if (key.contains("overgrown")) generateMonolith(chunk, random);
        }
        spawnSiftWildlife(chunk, key, random);
        if (key.endsWith("pink_meadows") && random.nextInt(22) == 0) {
            summonSinger(chunk, random);
        } else if (key.endsWith("crystal_spires") && random.nextInt(55) == 0) {
            summonEchoGolem(chunk, random);
        }
        if (key.endsWith("shattered_peaks") && random.nextInt(120) == 0) {
            openNaturalRift(chunk);
        }
        if (key.endsWith("bulb_forest") && random.nextInt(160) == 0) {
            spawnBoss(chunk, EntityType.WARDEN, "Prism Warden", "prism_warden", 180.0, 28.0);
        } else if (key.endsWith("shattered_peaks") && random.nextInt(120) == 0) {
            spawnBoss(chunk, EntityType.RAVAGER, "The Sifter", "the_sifter", 220.0, 24.0);
        }
    }

    private void decorateSurface(Chunk chunk, String biome, Random random) {
        World world = chunk.getWorld();
        for (int i = 0; i < 28; i++) {
            int x = chunk.getX() * 16 + random.nextInt(16);
            int z = chunk.getZ() * 16 + random.nextInt(16);
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 4) continue;
            Block ground = world.getBlockAt(x, y, z);
            Material surface = ground.getType();
            if (!surface.isSolid() || surface == Material.BEDROCK) continue;

            if (biome.endsWith("pink_meadows")) {
                if (surface == Material.GRASS_BLOCK && random.nextInt(4) == 0) {
                    ground.setType(random.nextBoolean() ? Material.MOSS_BLOCK : Material.DIRT, false);
                }
                placePlant(world, x, y + 1, z,
                        List.of(Material.PINK_PETALS, Material.PINK_TULIP, Material.AZURE_BLUET,
                                Material.LILAC, Material.CHERRY_SAPLING), random);
            } else if (biome.endsWith("bulb_forest")) {
                if (surface == Material.GRASS_BLOCK && random.nextInt(3) == 0) {
                    ground.setType(Material.MOSS_BLOCK, false);
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
                if (surface == Material.GRASS_BLOCK && random.nextBoolean()) ground.setType(Material.MOSS_BLOCK, false);
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
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(rocks[random.nextInt(rocks.length)], false);
                }
            }
        } else if (biome.endsWith("bulb_forest") || biome.contains("overgrown_forest")) {
            Material wood = random.nextBoolean() ? Material.MANGROVE_LOG : Material.DARK_OAK_LOG;
            Material leaves = random.nextBoolean() ? Material.MANGROVE_LEAVES : Material.AZALEA_LEAVES;
            int trunk = 4 + random.nextInt(4);
            for (int dy = 0; dy < trunk; dy++) world.getBlockAt(x, y + dy, z).setType(wood, false);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) < 4) {
                    world.getBlockAt(x + dx, y + trunk - 1, z + dz).setType(leaves, false);
                }
            }
            world.getBlockAt(x, y + trunk, z).setType(Material.SHROOMLIGHT, false);
        } else if (biome.endsWith("pink_meadows") || biome.contains("overgrown_clearing")) {
            Material wood = Material.CHERRY_LOG;
            for (int dy = 0; dy < 5; dy++) world.getBlockAt(x, y + dy, z).setType(wood, false);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) < 4) {
                    world.getBlockAt(x + dx, y + 4, z + dz).setType(Material.CHERRY_LEAVES, false);
                }
            }
        } else if (biome.endsWith("carapace") || biome.contains("sift_wastes")
                || biome.contains("sift_deep_dark")) {
            Material[] shell = {Material.TUFF_BRICKS, Material.PACKED_MUD, Material.PRISMARINE_BRICKS};
            int radius = 2 + random.nextInt(2);
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius + 1) {
                    world.getBlockAt(x + dx, y, z + dz).setType(shell[random.nextInt(shell.length)], false);
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
            container.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.AMETHYST_SHARD,
                    2 + random.nextInt(5)));
            if (random.nextBoolean()) container.getInventory().addItem(new org.bukkit.inventory.ItemStack(
                    Material.MUSIC_DISC_5));
            if (random.nextInt(100) < 40) container.getInventory().addItem(new org.bukkit.inventory.ItemStack(
                    Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            container.update(true, false);
        }
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
            if (dx * dx + dz * dz <= 2) world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.WATER, false);
        }
        world.getBlockAt(x, y + 1, z).setType(Material.WATER, false);
        world.spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, new Location(world, x + .5, y + 1, z + .5),
                12, 1, .2, 1, .02);
    }

    private void generateSoulCanyon(Chunk chunk, Random random) {
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
                        {"ALLAY", "Wisp"}, {"MUSHROOM_COW", "Sunspore"}};
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
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && resonanceManager != null && chunk.isLoaded()) {
                resonanceManager.summonSinger(new Location(chunk.getWorld(), x + .5, y, z + .5));
            }
        });
    }

    private void summonEchoGolem(Chunk chunk, Random random) {
        if (!plugin.getConfig().getBoolean("content.echo_golem", true)) return;
        int x = chunk.getX() * 16 + 8, z = chunk.getZ() * 16 + 8;
        int y = chunk.getWorld().getHighestBlockYAt(x, z) + 1;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && echoGolemManager != null && chunk.isLoaded()) {
                echoGolemManager.summonEchoGolem(new Location(chunk.getWorld(), x + .5, y, z + .5));
            }
        });
    }

    private void openNaturalRift(Chunk chunk) {
        if (riftManager == null || !plugin.getConfig().getBoolean("content.rift", true)) return;
        int x = chunk.getX() * 16 + 8, z = chunk.getZ() * 16 + 8;
        int y = chunk.getWorld().getHighestBlockYAt(x, z) + 1;
        Location location = new Location(chunk.getWorld(), x + .5, y, z + .5);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && riftManager != null && chunk.isLoaded()) {
                riftManager.openRift(location, null);
            }
        });
    }

    private void spawnBoss(Chunk chunk, EntityType type, String name, String id,
                           double health, double attackDamage) {
        int x = chunk.getX() * 16 + 8, z = chunk.getZ() * 16 + 8;
        int y = chunk.getWorld().getHighestBlockYAt(x, z) + 1;
        Location location = new Location(chunk.getWorld(), x + .5, y, z + .5);
        Entity entity = mobFactory.spawn(id, location, type);
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
            if (player.getLocation().getBlock().isLiquid()) {
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
        for (Entity entity : sift.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity.getType() != EntityType.SNIFFER) continue;
            String id = entity.getPersistentDataContainer().get(creatureKey, PersistentDataType.STRING);
            if (!"dark_sniffer".equals(id)) continue;
            for (Player target : living.getNearbyEntities(18, 10, 18).stream()
                    .filter(Player.class::isInstance).map(Player.class::cast).toList()) {
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
