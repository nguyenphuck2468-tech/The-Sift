package com.gamepathics.thesift.foundation;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
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
        this.tideTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTide, 100L, 100L);
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

        if (biome.endsWith("crystal_spires")) {
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
        } else if (biome.endsWith("shattered_peaks")) {
            Material[] rocks = {Material.TUFF, Material.CALCITE, Material.ANDESITE, Material.COBBLED_DEEPSLATE};
            int radius = 2 + random.nextInt(3);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (random.nextInt(3) != 0) continue;
                    int dy = random.nextInt(3);
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(rocks[random.nextInt(rocks.length)], false);
                }
            }
        } else if (biome.endsWith("bulb_forest")) {
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
        } else if (biome.endsWith("pink_meadows")) {
            Material wood = Material.CHERRY_LOG;
            for (int dy = 0; dy < 5; dy++) world.getBlockAt(x, y + dy, z).setType(wood, false);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) < 4) {
                    world.getBlockAt(x + dx, y + 4, z + dz).setType(Material.CHERRY_LEAVES, false);
                }
            }
        } else if (biome.endsWith("carapace")) {
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
            } else if (biome.endsWith("pink_meadows")) {
                String[][] meadow = {{"RABBIT", "Loomer"}, {"BEE", "Glimmerwing"},
                        {"ALLAY", "Wisp"}, {"MUSHROOM_COW", "Sunspore"}};
                String[] mob = meadow[random.nextInt(meadow.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            } else if (biome.endsWith("bulb_forest")) {
                String[][] forest = {{"BAT", "Glowbell"}, {"ZOMBIE", "Hollow Bulb"},
                        {"VEX", "Violet Wisp"}, {"SILVERFISH", "Prismite"}};
                String[] mob = forest[random.nextInt(forest.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            } else if (biome.endsWith("crystal_spires")) {
                String[][] spires = {{"SILVERFISH", "Prismite"}, {"ENDERMITE", "Shardling"},
                        {"ALLAY", "Wisp"}};
                String[] mob = spires[random.nextInt(spires.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            } else {
                String[][] peaks = {{"STRAY", "Frost Stalker"}, {"GOAT", "Thornback"},
                        {"ENDERMAN", "Drifter"}};
                String[] mob = peaks[random.nextInt(peaks.length)];
                type = EntityType.valueOf(mob[0]); name = mob[1];
            }
            Entity entity = world.spawnEntity(new Location(world, x + .5, y, z + .5), type);
            entity.setPersistent(true);
            entity.setCustomName("§d" + name);
            entity.setCustomNameVisible(random.nextInt(4) == 0);
            entity.getPersistentDataContainer().set(creatureKey, PersistentDataType.STRING,
                    name.toLowerCase(Locale.ROOT).replace(' ', '_'));
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
        Entity entity = chunk.getWorld().spawnEntity(location, type);
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
}
