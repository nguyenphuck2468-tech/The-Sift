package com.gamepathics.thesift.foundation;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class TheSiftFoundationPlugin extends JavaPlugin {
    private final ContentRegistry contentRegistry = new ContentRegistry();
    private IchorContent ichorContent;
    private IchorState ichorState;
    private SiftiteContent siftiteContent;
    private ResonanceManager resonanceManager;
    private EchoGolemManager echoGolemManager;
    private RiftManager riftManager;
    private SiftDimensionManager siftDimensionManager;
    private SiftEcologyManager siftEcologyManager;
    private SiftAdvancementManager advancementManager;
    private ProviderReadiness providerReadiness;
    private ProviderApiProbe providerApiProbe;

    @Override
    public void onEnable() {
        registerCommand("sift", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                handleSiftCommand(source.getSender(), args);
            }

            @Override
            public String permission() {
                return "thesift.foundation.admin";
            }

            @Override
            public java.util.Collection<String> suggest(CommandSourceStack source, String[] args) {
                return commandSuggestions(args, getServer().getOnlinePlayers().stream()
                        .map(Player::getName).toList());
            }
        });
        saveDefaultConfig();
        if (!ConfigMigration.migrate(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        enforceDependencyMode();
        loadContentFlags();
        providerReadiness = new ProviderReadiness(this);
        providerApiProbe = new ProviderApiProbe(this);
        providerApiProbe.inspect();
        getServer().getPluginManager().registerEvents(new ProviderAccessGuard(this, providerReadiness), this);
        if (!providerReadiness.strict()) getLogger().warning(
                "providers.strict is disabled; vanilla fallback is enabled for development/CI.");
        if (contentRegistry.isEnabled(ContentId.ICHOR)) {
            ichorContent = new IchorContent(this);
            ichorState = new IchorState(this);
            ichorContent.registerRecipes();
            getServer().getPluginManager().registerEvents(new IchorListener(this, ichorContent, ichorState), this);
        }
        if (contentRegistry.isEnabled(ContentId.CHAROITE_ITEM)
                || contentRegistry.isEnabled(ContentId.CHAROITE_ORE)
                || contentRegistry.isEnabled(ContentId.SIFTITE_INGOT)
                || contentRegistry.isEnabled(ContentId.SIFTITE_ORE)
                || contentRegistry.isEnabled(ContentId.SOUL_BLOCK)) {
            siftiteContent = new SiftiteContent(this);
            siftiteContent.registerRecipes();
            getServer().getPluginManager().registerEvents(siftiteContent, this);
            getServer().getPluginManager().registerEvents(new SiftiteListener(this, siftiteContent), this);
            getServer().getPluginManager().registerEvents(new SiftiteRecoveryManager(this), this);
            getServer().getPluginManager().registerEvents(new CharoiteEnchantingListener(this), this);
        }
        if (contentRegistry.isEnabled(ContentId.SINGER) && contentRegistry.isEnabled(ContentId.RESONANCE)) {
            if (siftiteContent == null) siftiteContent = new SiftiteContent(this);
            resonanceManager = new ResonanceManager(this, siftiteContent, new SiftMobFactory(this));
            getServer().getPluginManager().registerEvents(resonanceManager, this);
            resonanceManager.start();
        }
        if (contentRegistry.isEnabled(ContentId.ECHO_GOLEM)) {
            if (siftiteContent == null) siftiteContent = new SiftiteContent(this);
            echoGolemManager = new EchoGolemManager(this, siftiteContent, new SiftMobFactory(this));
            getServer().getPluginManager().registerEvents(echoGolemManager, this);
            echoGolemManager.start();
        }
        if (contentRegistry.isEnabled(ContentId.RIFT)) {
            riftManager = new RiftManager(this, providerReadiness);
            getServer().getPluginManager().registerEvents(riftManager, this);
            getServer().getPluginManager().registerEvents(riftManager.ancientCityPortal(), this);
            if (resonanceManager != null) riftManager.ancientCityPortal().setSingerSpawner(resonanceManager::summonSinger);
            riftManager.start();
        }
        if (contentRegistry.isEnabled(ContentId.SIFT_DIMENSION)) {
            siftDimensionManager = new SiftDimensionManager(this, providerReadiness);
            siftEcologyManager = new SiftEcologyManager(this, resonanceManager, echoGolemManager,
                    riftManager, siftiteContent,
                    echoGolemManager != null ? echoGolemManager.mobFactory() : new SiftMobFactory(this));
            getServer().getPluginManager().registerEvents(siftEcologyManager, this);
            siftEcologyManager.populateLoadedSpawnChunks();
            advancementManager = new SiftAdvancementManager(this);
            getServer().getPluginManager().registerEvents(advancementManager, this);
        }
        if (contentRegistry.isEnabled(ContentId.SOUL_BLOCK)) {
            getServer().getPluginManager().registerEvents(new SoulBlockListener(this, siftiteContent), this);
        }
        registerContentPluginReloadHooks();
        // Keep a delayed initial refresh as a fallback for providers without a load event.
        getServer().getScheduler().runTaskLater(this, () -> {
            refreshContentRecipes();
            reportProviderReadiness();
        }, 200L);
        // Runs on the first tick, after every plugin (including ones that depend on The Sift) has enabled.
        getServer().getScheduler().runTask(this, this::verifyRequiredPlugins);
        getLogger().info("The Sift fan-made Paper 26.3 plugin enabled; not affiliated with Mojang or Microsoft.");
    }

    /** The Sift depends fully on provider plugins: vanilla fallback mode is not available. */
    private void enforceDependencyMode() {
        if (!getConfig().getBoolean("providers.strict", true)) {
            getLogger().warning("providers.strict: false is ignored; The Sift depends fully on provider plugins.");
        }
        getConfig().set("providers.strict", true);
    }

    private void verifyRequiredPlugins() {
        if (!isEnabled() || !getConfig().getBoolean("requirements.enabled", true)) return;
        java.util.List<String> missing = RequiredPlugins.missing(getConfig(), this::requirementPresent);
        if (missing.isEmpty()) return;
        getLogger().severe("The Sift is disabled because required plugins are missing:");
        for (String line : missing) getLogger().severe(" - " + line);
        getLogger().severe("Install them (or edit the 'requirements' section of config.yml) and restart the server.");
        getServer().getPluginManager().disablePlugin(this);
    }

    private boolean requirementPresent(String token) {
        if (token.regionMatches(true, 0, "service:", 0, 8)) {
            var services = getServer().getServicesManager();
            return switch (token.substring(8).toLowerCase(java.util.Locale.ROOT)) {
                case "item" -> !services.getRegistrations(com.gamepathics.thesift.foundation.api.SiftItemProvider.class).isEmpty();
                case "block" -> !services.getRegistrations(com.gamepathics.thesift.foundation.api.SiftBlockProvider.class).isEmpty();
                case "mob" -> !services.getRegistrations(com.gamepathics.thesift.foundation.api.SiftMobProvider.class).isEmpty();
                default -> false;
            };
        }
        return getServer().getPluginManager().isPluginEnabled(token);
    }

    private void registerContentPluginReloadHooks() {
        registerContentReloadHook("Nexo", "com.nexomc.nexo.api.events.NexoItemsLoadedEvent");
        registerContentReloadHook("ItemsAdder", "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent");
        registerContentReloadHook("Oraxen", "io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent");
    }

    private void registerContentReloadHook(String pluginName, String eventClassName) {
        if (!getServer().getPluginManager().isPluginEnabled(pluginName)) return;
        try {
            Class<? extends Event> eventType = Class.forName(eventClassName).asSubclass(Event.class);
            Listener listener = new Listener() {};
            getServer().getPluginManager().registerEvent(eventType, listener, EventPriority.MONITOR,
                    (ignored, event) -> getServer().getScheduler().runTask(this, () -> {
                        providerReadiness.invalidate();
                        refreshContentRecipes();
                        reportProviderReadiness();
                    }),
                    this, true);
        } catch (ClassNotFoundException | LinkageError exception) {
            getLogger().fine("Could not register " + pluginName + " content reload hook: "
                    + exception.getMessage());
        }
    }

    private void refreshContentRecipes() {
        if (ichorContent != null) ichorContent.registerRecipes();
        if (siftiteContent != null) siftiteContent.registerRecipes();
    }

    private void reportProviderReadiness() {
        providerReadiness.invalidate();
        if (providerReadiness.strict() && !providerReadiness.dimensionAllowed()) {
            getLogger().warning("The Sift dimension remains locked because required provider IDs are unresolved. "
                    + "Run /sift doctor for provider status and exact missing IDs. Existing world data is preserved.");
        } else if (providerReadiness.strict()) {
            getLogger().info("The Sift provider requirements are ready; the dimension can be loaded.");
        }
    }

    @Override
    public void onDisable() {
        var dimension = getServer().getWorld(SiftDimensionManager.DIMENSION_KEY);
        if (dimension != null) {
            var safeWorld = getServer().getWorlds().stream()
                    .filter(world -> !world.getKey().equals(SiftDimensionManager.DIMENSION_KEY))
                    .findFirst().orElse(null);
            if (safeWorld == null && !dimension.getPlayers().isEmpty()) {
                getLogger().severe("Cannot evacuate players from The Sift: no other world is loaded. The world will remain loaded.");
            } else if (safeWorld != null) {
                for (Player player : dimension.getPlayers()) player.teleport(safeWorld.getSpawnLocation());
            }
        }
        if (ichorState != null) {
            ichorState.shutdown();
        }
        if (resonanceManager != null) {
            resonanceManager.shutdown();
        }
        if (echoGolemManager != null) {
            echoGolemManager.shutdown();
        }
        if (riftManager != null) {
            riftManager.shutdown();
        }
        if (siftEcologyManager != null) {
            siftEcologyManager.shutdown();
        }
    }

    private boolean handleSiftCommand(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            if (!ConfigMigration.migrate(this)) {
                sender.sendMessage("The Sift config is newer than this build; reload was rejected.");
                return true;
            }
            enforceDependencyMode();
            loadContentFlags();
            providerReadiness.invalidate();
            refreshContentRecipes();
            verifyRequiredPlugins();
            reportProviderReadiness();
            sender.sendMessage("The Sift config was reloaded. Restart the server to apply content flag changes.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("doctor")) {
            sender.sendMessage("The Sift foundation: " + getDescription().getVersion());
            sender.sendMessage("Runtime target: Paper 26.3 / Paper API 26.3.build.218-stable");
            sender.sendMessage(contentPackStatus());
            for (Map.Entry<ContentId, Boolean> entry : contentRegistry.snapshot().entrySet()) {
                sender.sendMessage("- " + entry.getKey().key() + ": "
                        + (entry.getValue() ? "enabled" : "disabled"));
            }
            for (String line : providerReadiness.summary().split("\\n")) sender.sendMessage(line);
            for (String line : providerApiProbe.inspect()) sender.sendMessage("API: " + line);
            if (siftEcologyManager != null) sender.sendMessage("Worldgen provider placement failures: "
                    + siftEcologyManager.providerPlacementFailureSummary());
            for (String warning : providerWarnings()) sender.sendMessage("WARNING: " + warning);
            for (String conflict : pluginConflicts()) sender.sendMessage("ERROR: " + conflict);
            return true;
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("dimension")
                || args[0].equalsIgnoreCase("enter"))) {
            if (siftDimensionManager == null) {
                sender.sendMessage("The Sift dimension is disabled.");
            } else if (!providerReadiness.dimensionAllowed() && providerReadiness.strict()) {
                sender.sendMessage("The Sift dimension is locked because required provider IDs are missing. Run /sift doctor; see integrations/README.md for setup instructions.");
            } else if (sender instanceof Player player) {
                siftDimensionManager.enter(player);
            } else {
                if (siftDimensionManager.getOrCreateWorld() != null) {
                    sender.sendMessage("The Sift dimension is ready. Players can enter with /sift dimension.");
                } else {
                    sender.sendMessage("The Sift dimension could not be loaded; check the datapack and server log.");
                }
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("return")) {
            if (siftDimensionManager == null) {
                sender.sendMessage("The Sift dimension is disabled.");
            } else if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can return from The Sift.");
            } else {
                siftDimensionManager.returnToOverworld(player);
            }
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")
                && args[1].equalsIgnoreCase("ichor_bottle")) {
            if (ichorContent == null || !contentRegistry.isEnabled(ContentId.ICHOR_BOTTLE)) {
                sender.sendMessage("Ichor content is disabled.");
                return true;
            }
            Player target = getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }
            ItemStack bottle = ichorContent.ichorBottle();
            if (bottle == null) sender.sendMessage("Item ID 'ichor_bottle' is unresolved by the selected provider. Run /sift doctor.");
            else {
                giveOrDrop(target, bottle);
                sender.sendMessage("Gave Ichor Bottle to " + target.getName() + ".");
            }
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")
                && args[1].equalsIgnoreCase("rift_key")) {
            if (riftManager == null) {
                sender.sendMessage("Rift content is disabled.");
                return true;
            }
            Player target = getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("Player not found.");
            } else {
                ItemStack key = riftManager.createRiftKey();
                if (key == null) sender.sendMessage("Item ID 'rift_key' is unavailable in strict provider mode. Run /sift doctor.");
                else {
                    giveOrDrop(target, key);
                    sender.sendMessage("Gave Rift Key to " + target.getName() + ".");
                }
            }
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (siftiteContent == null) {
                sender.sendMessage("Charoite/Siftite content is disabled.");
                return true;
            }
            Player target = getServer().getPlayerExact(args[2]);
            ItemStack item = siftiteContent.forId(args[1].toLowerCase());
            if (target == null) {
                sender.sendMessage("Player not found.");
            } else if (item == null) {
                sender.sendMessage("Item ID '" + args[1].toLowerCase() + "' is unknown or unresolved by the selected provider. Run /sift doctor.");
            } else {
                giveOrDrop(target, item);
                sender.sendMessage("Gave " + args[1] + " to " + target.getName() + ".");
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("summon")
                && args[1].equalsIgnoreCase("singer")) {
            if (resonanceManager == null) {
                sender.sendMessage("Singer content is disabled.");
                return true;
            }
            Player target = args.length >= 3 ? getServer().getPlayerExact(args[2])
                    : sender instanceof Player player ? player : null;
            if (target == null) {
                sender.sendMessage("Usage: /sift summon singer <player>");
            } else {
                resonanceManager.summonSinger(target.getLocation());
                sender.sendMessage("Summoned a Singer near " + target.getName() + ".");
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("summon")
                && args[1].equalsIgnoreCase("echo_golem")) {
            if (echoGolemManager == null) {
                sender.sendMessage("Echo Golem content is disabled.");
                return true;
            }
            Player target = args.length >= 3 ? getServer().getPlayerExact(args[2])
                    : sender instanceof Player player ? player : null;
            if (target == null) {
                sender.sendMessage("Usage: /sift summon echo_golem <player>");
            } else {
                echoGolemManager.summonEchoGolem(target.getLocation());
                sender.sendMessage("Summoned an Echo Golem near " + target.getName() + ".");
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("summon")
                && args[1].equalsIgnoreCase("rift")) {
            if (riftManager == null) {
                sender.sendMessage("Rift content is disabled.");
                return true;
            }
            Player target = args.length >= 3 ? getServer().getPlayerExact(args[2])
                    : sender instanceof Player player ? player : null;
            if (target == null) {
                sender.sendMessage("Usage: /sift summon rift <player>");
            } else {
                if (riftManager.openRift(target.getLocation().add(0, 1, 0), target)) {
                    sender.sendMessage("Opened a Rift near " + target.getName() + ".");
                } else sender.sendMessage("Rift content is currently disabled.");
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("summon")) {
            if (siftEcologyManager == null) {
                sender.sendMessage("Sift creature content is disabled.");
                return true;
            }
            Player target = args.length >= 3 ? getServer().getPlayerExact(args[2])
                    : sender instanceof Player player ? player : null;
            if (target == null) {
                sender.sendMessage("Usage: /sift summon <species> [player]");
            } else if (siftEcologyManager.summonCreature(args[1], target.getLocation())) {
                sender.sendMessage("Summoned Sift creature " + args[1] + " near " + target.getName() + ".");
            } else {
                sender.sendMessage("Unknown Sift creature: " + args[1]);
            }
            return true;
        }
        sender.sendMessage("Usage: /sift <doctor|reload|dimension|return|give <item> <player>|summon <species> [player]>");
        return true;
    }

    private String contentPackStatus() {
        java.util.List<String> packs = getServer().getDatapackManager().getPacks().stream()
                .map(io.papermc.paper.datapack.DiscoveredDatapack::getName)
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains("thesift"))
                .sorted().toList();
        java.util.List<String> enabled = getServer().getDatapackManager().getEnabledPacks().stream()
                .map(io.papermc.paper.datapack.DiscoveredDatapack::getName)
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains("thesift"))
                .sorted().toList();
        String packUrl = getServer().getResourcePack();
        return "Datapack packs: " + (packs.isEmpty() ? "The Sift pack not discovered" : packs)
                + "; enabled: " + (enabled.isEmpty() ? "none" : enabled)
                + "; server resource pack: " + (packUrl == null || packUrl.isBlank() ? "not configured" : "configured")
                + "; required: " + getServer().isResourcePackRequired();
    }

    static java.util.List<String> commandSuggestions(String[] args, java.util.Collection<String> players) {
        java.util.List<String> candidates;
        if (args.length == 1) candidates = java.util.List.of("doctor", "reload", "dimension", "return", "give", "summon");
        else if (args.length == 2 && args[0].equalsIgnoreCase("give")) candidates = java.util.List.of(
                "ichor_bottle", "rift_key", "soul_block", "sonorous_deepslate", "siftslate", "charoite",
                "siftite_ingot", "siftite_nugget", "siftite_upgrade_smithing_template");
        else if (args.length == 2 && args[0].equalsIgnoreCase("summon")) candidates = java.util.List.of(
                "singer", "echo_golem", "rift", "blub", "dark_sniffer", "bulb", "loomer", "wisp",
                "violet_wisp", "koi", "hollow_bulb", "glowbell", "thornback", "glimmerwing",
                "the_sifter", "sifter", "frost_stalker", "sunspore", "drifter", "shardling",
                "prism_warden", "prismite", "lumen_tulip");
        else if (args.length == 3 && (args[0].equalsIgnoreCase("give")
                || args[0].equalsIgnoreCase("summon"))) candidates = java.util.List.copyOf(players);
        else return java.util.List.of();
        String prefix = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)).toList();
    }

    private java.util.List<String> providerWarnings() {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (!getServer().getPluginManager().isPluginEnabled("ModelEngine"))
            warnings.add("ModelEngine is absent; custom mob rendering will use provider defaults.");
        if (getConfig().getBoolean("providers.bedrock-enabled", false)) {
            if (!getServer().getPluginManager().isPluginEnabled("Geyser-Spigot")) warnings.add("Bedrock support is enabled but Geyser-Spigot is missing.");
            if (!getServer().getPluginManager().isPluginEnabled("floodgate")) warnings.add("Floodgate is missing; Bedrock access may require Java authentication.");
            if (!getServer().getPluginManager().isPluginEnabled("ViaVersion")) warnings.add("ViaVersion is missing; it is required when this Paper server is older than Geyser's emulated Java version.");
        }
        if (!getServer().getPluginManager().isPluginEnabled("ResourcePackManager")) warnings.add("ResourcePackManager is absent; only plugin detection is implemented.");
        return warnings;
    }

    private java.util.List<String> pluginConflicts() {
        java.util.List<String> conflicts = new java.util.ArrayList<>();
        for (org.bukkit.plugin.Plugin other : getServer().getPluginManager().getPlugins()) {
            if (other == this) continue;
            if (other.getName().equalsIgnoreCase("TheSift")) conflicts.add("Another plugin is also named TheSift: " + other.getName() + ". Remove one TheSift plugin.");
            if (other.getDescription().getCommands() != null && other.getDescription().getCommands().keySet().stream().anyMatch(key -> key.equalsIgnoreCase("sift")))
                conflicts.add("Another plugin declares /sift: " + other.getName() + ". Resolve the command conflict before using The Sift commands.");
        }
        return conflicts;
    }

    private void loadContentFlags() {
        for (ContentId id : ContentId.values()) {
            boolean enabledByDefault = switch (id) {
                case SIFT_DIMENSION, SOUL_BLOCK -> true;
                case ICHOR, ICHOR_BOTTLE, ICHOR_GLASS, ICHOR_GLASS_PANE ->
                        getConfig().getBoolean("ichor.enabled", true);
                case CHAROITE, CHAROITE_ITEM, CHAROITE_ORE ->
                        getConfig().getBoolean("charoite.enabled", true);
                case SIFTITE, SIFTITE_ORE, SIFTITE_INGOT, SIFTITE_NUGGET, SIFTITE_TEMPLATE, SIFTITE_SWORD,
                        SIFTITE_AXE, SIFTITE_PICKAXE, SIFTITE_SHOVEL, SIFTITE_HOE, SIFTITE_SPEAR,
                        SIFTITE_HELMET, SIFTITE_CHESTPLATE, SIFTITE_LEGGINGS, SIFTITE_BOOTS ->
                        getConfig().getBoolean("siftite.enabled", true);
                case SINGER, RESONANCE -> getConfig().getBoolean("resonance.enabled", true);
                case ECHO_GOLEM -> getConfig().getBoolean("echo-golem.enabled", true);
                case RIFT, RIFT_EVENTS -> getConfig().getBoolean("rift.enabled", true);
                default -> true;
            };
            contentRegistry.setEnabled(id,
                    getConfig().getBoolean("content." + id.key(), enabledByDefault));
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (item == null) return;
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    public ContentRegistry contentRegistry() {
        return contentRegistry;
    }
}
