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

    /** Shared Ichor state store for managers that need cauldron positions (package-visible). */
    IchorState ichorState() {
        return ichorState;
    }
    private SiftiteContent siftiteContent;
    private SiftMobFactory sharedMobFactory;
    private ResonanceManager resonanceManager;
    private EchoGolemManager echoGolemManager;
    private RiftManager riftManager;
    private SiftDimensionManager siftDimensionManager;
    private AncientCityGateManager ancientCityGateManager;
    private SiftEcologyManager siftEcologyManager;
    private SculkflowerManager sculkflowerManager;
    private SiftAdvancementManager advancementManager;
    private ProviderReadiness providerReadiness;
    private ProviderApiProbe providerApiProbe;
    private DatapackManagerBridge datapackBridge;
    private ResourcePackBridge resourcePackBridge;
    private BedrockBridge bedrockBridge;

    /** Bridge used by other managers (e.g. ore generation) to query bundled pack state. */
    public DatapackManagerBridge datapackBridge() {
        return datapackBridge;
    }

    @Override
    public void onEnable() {
        datapackBridge = new DatapackManagerBridge(this);
        resourcePackBridge = new ResourcePackBridge(this);
        bedrockBridge = new BedrockBridge(this);
        registerCommand("sift", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                handleSiftCommand(source.getSender(), args);
            }

            @Override
            public String permission() {
                return null;
            }

            @Override
            public java.util.Collection<String> suggest(CommandSourceStack source, String[] args) {
                return commandSuggestions(args, getServer().getOnlinePlayers().stream()
                        .map(Player::getName).toList(), TheSiftFoundationPlugin.this::speciesSuggestionEnabled);
            }
        });
        saveDefaultConfig();
        if (!ConfigMigration.migrate(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        enforceDependencyMode();
        Messages.init(this);
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
            // Created through the shared accessor so later managers reuse the same instance.
            siftiteContent = sharedContent();
            siftiteContent.registerRecipes();
            getServer().getPluginManager().registerEvents(siftiteContent, this);
            getServer().getPluginManager().registerEvents(new SiftiteListener(this, siftiteContent), this);
            getServer().getPluginManager().registerEvents(new SiftiteRecoveryManager(this), this);
            getServer().getPluginManager().registerEvents(new CharoiteEnchantingListener(this), this);
        }
        // One shared content registry + mob factory for every manager, created lazily so a
        // fully-disabled feature set does not pay the cost of an unused ExternalItemBridge.
        if (contentRegistry.isEnabled(ContentId.SINGER) && contentRegistry.isEnabled(ContentId.RESONANCE)) {
            resonanceManager = new ResonanceManager(this, sharedContent(), sharedMobFactory());
            getServer().getPluginManager().registerEvents(resonanceManager, this);
            resonanceManager.start();
        }
        if (contentRegistry.isEnabled(ContentId.ECHO_GOLEM)) {
            echoGolemManager = new EchoGolemManager(this, sharedContent(), sharedMobFactory());
            getServer().getPluginManager().registerEvents(echoGolemManager, this);
            echoGolemManager.start();
            if (resonanceManager != null) echoGolemManager.setSingerTest(resonanceManager::isSinger);
        }
        if (contentRegistry.isEnabled(ContentId.RIFT)) {
            riftManager = new RiftManager(this, providerReadiness, sharedContent(), sharedMobFactory());
            getServer().getPluginManager().registerEvents(riftManager, this);
            getServer().getPluginManager().registerEvents(riftManager.ancientCityPortal(), this);
            if (resonanceManager != null) riftManager.ancientCityPortal().setSingerSpawner(resonanceManager::summonSinger);
            riftManager.start();
        }
        if (contentRegistry.isEnabled(ContentId.SIFT_DIMENSION)) {
            siftDimensionManager = new SiftDimensionManager(this, providerReadiness);
            ancientCityGateManager = new AncientCityGateManager(this, providerReadiness, siftDimensionManager,
                    resonanceManager);
            getServer().getPluginManager().registerEvents(ancientCityGateManager, this);
            ancientCityGateManager.start();
            // Gate-owned Sonorous note blocks win over the rift portal song handler, and the
            // mod-port portal defers to the gate inside Ancient Cities (single portal flow).
            if (riftManager != null) {
                riftManager.setGateOwner(ancientCityGateManager);
                riftManager.ancientCityPortal().setGateOwner(ancientCityGateManager);
            }
            siftEcologyManager = new SiftEcologyManager(this, resonanceManager, echoGolemManager,
                    riftManager, sharedContent(), sharedMobFactory());
            getServer().getPluginManager().registerEvents(siftEcologyManager, this);
            siftEcologyManager.populateLoadedSpawnChunks();
            sculkflowerManager = new SculkflowerManager(this, sharedContent());
            getServer().getPluginManager().registerEvents(sculkflowerManager, this);
            advancementManager = new SiftAdvancementManager(this);
            getServer().getPluginManager().registerEvents(advancementManager, this);
        }
        if (contentRegistry.isEnabled(ContentId.SOUL_BLOCK)) {
            getServer().getPluginManager().registerEvents(new SoulBlockListener(this, sharedContent()), this);
        }
        getServer().getPluginManager().registerEvents(new DarkSnifferDiggingGiftListener(this), this);
        registerContentPluginReloadHooks();
        getServer().getPluginManager().registerEvents(new SiftResourcePackListener(this, resourcePackBridge), this);
        // Keep a delayed initial refresh as a fallback for providers without a load event.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (riftManager != null) riftManager.loadRifts();
            datapackBridge.stageBundledPacks();
            resourcePackBridge.prepare();
            refreshContentRecipes();
            reportProviderReadiness();
        }, 200L);
        // Runs on the first tick, after every plugin (including ones that depend on The Sift) has enabled.
        getServer().getScheduler().runTask(this, this::verifyRequiredPlugins);
        getLogger().info("The Sift fan-made Paper 26.3+ plugin enabled; not affiliated with Mojang or Microsoft.");
    }

    /** Lazily creates and returns the single shared {@link SiftiteContent} used by all managers. */
    private SiftiteContent sharedContent() {
        if (siftiteContent == null) siftiteContent = new SiftiteContent(this);
        return siftiteContent;
    }

    /** Lazily creates and returns the single shared {@link SiftMobFactory} used by all managers. */
    private SiftMobFactory sharedMobFactory() {
        if (sharedMobFactory == null) sharedMobFactory = new SiftMobFactory(this);
        return sharedMobFactory;
    }

    /** Shared content registry for tests/advanced integrations; never null after first use. */
    public SiftiteContent siftiteContent() {
        return sharedContent();
    }

    /** Shared mob factory for tests/advanced integrations. */
    public SiftMobFactory mobFactory() {
        return sharedMobFactory();
    }

    private void enforceDependencyMode() {
        // providers.strict is opt-in. The default is standalone install-and-play mode, where
        // missing providers are replaced with tagged vanilla stand-ins.
        if (!getConfig().getBoolean("providers.strict", false)) {
            getLogger().info("providers.strict: false - standalone install-and-play mode is active;"
                    + " missing provider content uses tagged vanilla stand-ins.");
        }
    }

    private void verifyRequiredPlugins() {
        if (!isEnabled() || !getConfig().getBoolean("requirements.enabled", true)) return;
        java.util.List<String> missing = RequiredPlugins.missing(getConfig(), this::requirementPresent);
        if (missing.isEmpty()) return;
        // Soft-dependency policy: paper-plugin.yml declares every provider dependency as
        // required:false and the README describes them as optional. We therefore warn loudly
        // instead of disabling the plugin mid-session (a /sift reload must never take the
        // plugin down). Set requirements.hard-stop: true to restore the old self-disable rule.
        if (getConfig().getBoolean("requirements.hard-stop", false)) {
            getLogger().severe("The Sift is disabled because required plugins are missing:");
            for (String line : missing) getLogger().severe(" - " + line);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().warning("Optional provider plugins are missing; some The Sift features stay"
                + " degraded until they are installed:");
        for (String line : missing) getLogger().warning(" - " + line);
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
        if (sculkflowerManager != null) sculkflowerManager.registerRecipes();
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
        if (riftManager != null) {
            riftManager.shutdown();
        }
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
            // Unload with save so a /reload can't hold a half-live dimension against a
            // closing plugin (the crash class the Skilleed plugin had to patch). The world
            // data is independent of this plugin; re-entry recreates/loads it on demand.
            if (safeWorld != null && getServer().unloadWorld(dimension, true)) {
                getLogger().info("The Sift dimension unloaded cleanly for plugin disable.");
            }
        }
        if (ichorContent != null) ichorContent.removeRecipes();
        if (siftiteContent != null) siftiteContent.removeRecipes();
        if (sculkflowerManager != null) sculkflowerManager.removeRecipes();
        if (ichorState != null) {
            ichorState.shutdown();
        }
        if (resonanceManager != null) {
            resonanceManager.shutdown();
        }
        if (echoGolemManager != null) {
            echoGolemManager.shutdown();
        }
        if (siftEcologyManager != null) {
            siftEcologyManager.shutdown();
        }
        if (ancientCityGateManager != null) ancientCityGateManager.shutdown();
    }

    private boolean handleSiftCommand(CommandSender sender, String[] args) {
        if (requiresAdminPermission(args) && !sender.hasPermission("thesift.foundation.admin")) {
            Messages.send(sender, "doctor.perm",
                    "You do not have permission to use this The Sift command.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            Messages.init(this);
            if (!ConfigMigration.migrate(this)) {
                Messages.send(sender, "doctor.new-config",
                        "The Sift config is newer than this build; reload was rejected.");
                return true;
            }
            enforceDependencyMode();
            loadContentFlags();
            providerReadiness.invalidate();
            refreshContentRecipes();
            verifyRequiredPlugins();
            reportProviderReadiness();
            datapackBridge.stageBundledPacks();
            resourcePackBridge.prepare();
            Messages.send(sender, "sift.reload-done",
                    "The Sift config was reloaded. Restart the server to apply content flag changes.");
            sender.sendMessage(datapackBridge.status());
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("datapack")) {
            if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
                datapackBridge.report(sender);
                return true;
            }
            if (args.length == 3) {
                boolean enable = args[1].equalsIgnoreCase("enable");
                if (!enable && !args[1].equalsIgnoreCase("disable")) {
                    Messages.send(sender, "sift.datapack-usage", "Usage: /sift datapack <list|enable|disable> [name]");
                    return true;
                }
                sender.sendMessage(datapackBridge.setEnabled(args[2], enable)
                        ? "Datapack '" + args[2] + "' " + (enable ? "enabled" : "disabled") + " and applied."
                        : "Datapack '" + args[2] + "' not discovered yet; run /datapack list or restart.");
                return true;
            }
            Messages.send(sender, "sift.datapack-usage", "Usage: /sift datapack <list|enable|disable> [name]");
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("resourcepack")) {
            boolean force = args.length >= 3 && args[2].equalsIgnoreCase("force");
            if (sender instanceof Player player) {
                resourcePackBridge.sendTo(player, force);
                sender.sendMessage(Messages.get("sift.resourcepack-sent",
                        "The Sift resource pack sent to you (%mode%).").replace("%mode%", force ? "forced" : "additive"));
            } else {
                // Broadcast only makes sense when delivery can actually happen.
                if (!resourcePackBridge.canDeliver()) {
                    Messages.send(sender, "sift.resourcepack-no-provider",
                            "No resource-pack provider plugin detected and resource-pack.server-url is empty; set an http(s) download URL or install a provider before broadcasting.");
                    return true;
                }
                resourcePackBridge.broadcastToPlayers(sender, force);
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("doctor")) {
            sender.sendMessage(Messages.get("sift.version", "The Sift foundation: %version%")
                    .replace("%version%", getPluginMeta().getVersion()));
            sender.sendMessage(Messages.get("sift.runtime-target", "Runtime server: %version%")
                    .replace("%version%", getServer().getBukkitVersion()));
            sender.sendMessage(contentPackStatus());
            for (Map.Entry<ContentId, Boolean> entry : contentRegistry.snapshot().entrySet()) {
                sender.sendMessage("- " + entry.getKey().key() + ": "
                        + (entry.getValue() ? "enabled" : "disabled"));
            }
            for (String line : providerReadiness.summary().split("\\n")) sender.sendMessage(line);
            for (String line : providerApiProbe.inspect()) sender.sendMessage("API: " + line);
            sender.sendMessage(bedrockStatus());
            if (siftEcologyManager != null) sender.sendMessage("Worldgen provider placement failures: "
                    + siftEcologyManager.providerPlacementFailureSummary());
            for (String warning : providerWarnings()) sender.sendMessage("WARNING: " + warning);
            for (String conflict : pluginConflicts()) sender.sendMessage("ERROR: " + conflict);
            return true;
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("dimension")
                || args[0].equalsIgnoreCase("enter"))) {
            if (siftDimensionManager == null) {
                Messages.send(sender, "sift.dimension-disabled", "The Sift dimension is disabled.");
            } else if (!providerReadiness.dimensionAllowed() && providerReadiness.strict()) {
                Messages.send(sender, "sift.dimension-locked", "The Sift dimension is locked because required provider IDs are missing. Run /sift doctor; see integrations/README.md for setup instructions.");
            } else if (sender instanceof Player player) {
                siftDimensionManager.enter(player);
            } else {
                if (siftDimensionManager.getOrCreateWorld() != null) {
                    Messages.send(sender, "sift.dimension-ready", "The Sift dimension is ready. Players can enter with /sift dimension.");
                } else {
                    Messages.send(sender, "sift.dimension-load-failed", "The Sift dimension could not be loaded; check the datapack and server log.");
                }
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("locate")) {
            if (!(sender instanceof Player player)) Messages.send(sender, "sift.locate-console-only", "Only players can locate an Ancient City gate.");
            else if (ancientCityGateManager == null) sender.sendMessage(Messages.get("gate.disabled", "Ancient City gates are disabled."));
            else ancientCityGateManager.locate(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("return")) {
            if (siftDimensionManager == null) {
                Messages.send(sender, "sift.dimension-disabled", "The Sift dimension is disabled.");
            } else if (!(sender instanceof Player player)) {
                Messages.send(sender, "sift.return-console-only", "Only players can return from The Sift.");
            } else {
                siftDimensionManager.returnToOverworld(player);
            }
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")
                && args[1].equalsIgnoreCase("ichor_bottle")) {
            if (ichorContent == null || !contentRegistry.isEnabled(ContentId.ICHOR_BOTTLE)) {
                Messages.send(sender, "sift.ichor-disabled", "Ichor content is disabled.");
                return true;
            }
            Player target = getServer().getPlayerExact(args[2]);
            if (target == null) {
                Messages.send(sender, "sift.player-not-found", "Player not found.");
                return true;
            }
            ItemStack bottle = ichorContent.ichorBottle();
            if (bottle == null) Messages.send(sender, "sift.bottle-unresolved", "Item ID 'ichor_bottle' is unresolved by the selected provider. Run /sift doctor.");
            else {
                giveOrDrop(target, bottle);
                sender.sendMessage(Messages.get("sift.gave", "Gave %item% to %target%.")
                        .replace("%item%", "Ichor Bottle").replace("%target%", target.getName()));
            }
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")
                && args[1].equalsIgnoreCase("rift_key")) {
            if (riftManager == null) {
                Messages.send(sender, "sift.rift-disabled", "Rift content is disabled.");
                return true;
            }
            Player target = getServer().getPlayerExact(args[2]);
            if (target == null) {
                Messages.send(sender, "sift.player-not-found", "Player not found.");
            } else {
                ItemStack key = riftManager.createRiftKey();
                if (key == null) Messages.send(sender, "sift.key-unavailable", "Item ID 'rift_key' is unavailable in strict provider mode. Run /sift doctor.");
                else {
                    giveOrDrop(target, key);
                    sender.sendMessage(Messages.get("sift.gave", "Gave %item% to %target%.")
                            .replace("%item%", "Rift Key").replace("%target%", target.getName()));
                }
            }
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (siftiteContent == null) {
                Messages.send(sender, "sift.siftite-disabled", "Charoite/Siftite content is disabled.");
                return true;
            }
            Player target = getServer().getPlayerExact(args[2]);
            ItemStack item = siftiteContent.forId(args[1].toLowerCase());
            if (target == null) {
                Messages.send(sender, "sift.player-not-found", "Player not found.");
            } else if (item == null) {
                sender.sendMessage(Messages.get("sift.item-unresolved", "Item ID '%item%' is unknown or unresolved by the selected provider. Run /sift doctor.")
                        .replace("%item%", args[1].toLowerCase()));
            } else {
                giveOrDrop(target, item);
                sender.sendMessage(Messages.get("sift.gave", "Gave %item% to %target%.")
                        .replace("%item%", args[1]).replace("%target%", target.getName()));
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("summon")
                && args[1].equalsIgnoreCase("singer")) {
            if (resonanceManager == null) {
                Messages.send(sender, "sift.singer-disabled", "Singer content is disabled.");
                return true;
            }
            Player target = args.length >= 3 ? getServer().getPlayerExact(args[2])
                    : sender instanceof Player player ? player : null;
            if (target == null) {
                Messages.send(sender, "sift.summon-usage", "Usage: /sift summon singer <player>");
            } else {
                resonanceManager.summonSinger(target.getLocation());
                sender.sendMessage(Messages.get("sift.summoned", "Summoned %mob% near %target%.")
                        .replace("%mob%", "a Singer").replace("%target%", target.getName()));
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("summon")
                && args[1].equalsIgnoreCase("echo_golem")) {
            if (echoGolemManager == null) {
                Messages.send(sender, "sift.echo-golem-disabled", "Echo Golem content is disabled.");
                return true;
            }
            Player target = args.length >= 3 ? getServer().getPlayerExact(args[2])
                    : sender instanceof Player player ? player : null;
            if (target == null) {
                Messages.send(sender, "sift.summon-usage-echo", "Usage: /sift summon echo_golem <player>");
            } else {
                echoGolemManager.summonEchoGolem(target.getLocation());
                sender.sendMessage(Messages.get("sift.summoned", "Summoned %mob% near %target%.")
                        .replace("%mob%", "an Echo Golem").replace("%target%", target.getName()));
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("summon")
                && args[1].equalsIgnoreCase("rift")) {
            if (riftManager == null) {
                Messages.send(sender, "sift.rift-disabled", "Rift content is disabled.");
                return true;
            }
            Player target = args.length >= 3 ? getServer().getPlayerExact(args[2])
                    : sender instanceof Player player ? player : null;
            if (target == null) {
                Messages.send(sender, "sift.summon-usage-rift", "Usage: /sift summon rift <player>");
            } else {
                if (riftManager.openRift(target.getLocation().add(0, 1, 0), target)) {
                    sender.sendMessage(Messages.get("sift.summoned", "Summoned %mob% near %target%.")
                        .replace("%mob%", "a Rift").replace("%target%", target.getName()));
                } else Messages.send(sender, "sift.rift-disabled", "Rift content is currently disabled.");
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("summon")) {
            if (siftEcologyManager == null) {
                Messages.send(sender, "sift.creature-disabled", "Sift creature content is disabled.");
                return true;
            }
            Player target = args.length >= 3 ? getServer().getPlayerExact(args[2])
                    : sender instanceof Player player ? player : null;
            if (target == null) {
                Messages.send(sender, "sift.summon-usage-generic", "Usage: /sift summon <species> [player]");
            } else if (siftEcologyManager.summonCreature(args[1], target.getLocation())) {
                sender.sendMessage(Messages.get("sift.summoned", "Summoned %mob% near %target%.")
                        .replace("%mob%", "Sift creature " + args[1]).replace("%target%", target.getName()));
            } else {
                sender.sendMessage(Messages.get("sift.unknown-creature", "Unknown Sift creature: %species%")
                        .replace("%species%", args[1]));
            }
            return true;
        }
        Messages.send(sender, "sift.usage", "Usage: /sift <doctor|reload|datapack <list|enable|disable>|resourcepack [force]|dimension|locate|return|give <item> <player>|summon <species> [player]>");
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

    /** Suggestion gate mirroring summonCreature's acceptance: core mobs always, fan mobs
     *  only when content.sift_standin_mobs is on, rifts follow content.rift. */
    private boolean speciesSuggestionEnabled(String species) {
        if (species.equals("rift")) return getConfig().getBoolean("content.rift", true);
        return SiftEcologyManager.FAN_CORE_MOBS.contains(species)
                || getConfig().getBoolean("content.sift_standin_mobs", false);
    }

    static java.util.List<String> commandSuggestions(String[] args, java.util.Collection<String> players,
                                                     java.util.function.Predicate<String> speciesEnabled) {
        java.util.List<String> candidates;
        if (args.length == 1) candidates = java.util.List.of("doctor", "reload", "datapack", "resourcepack", "dimension", "locate", "return", "give", "summon");
        else if (args.length == 2 && args[0].equalsIgnoreCase("datapack")) candidates = java.util.List.of("list", "enable", "disable");
        else if (args.length == 3 && args[0].equalsIgnoreCase("datapack") && !args[1].equalsIgnoreCase("list")) candidates = java.util.List.of("dimension", "progression");
        else if (args.length == 2 && args[0].equalsIgnoreCase("give")) candidates = java.util.List.of(
                "ichor_bottle", "ichor_bucket", "ichor_shard", "ichor_snowball", "rift_key", "soul_block",
                "sonorous_deepslate", "siftslate", "siftslate_bricks", "charoite",
                "overgrown_willow_log", "overgrown_willow_planks", "overgrown_willow_boat",
                "rift_disc", "wellspring_disc", "sift_rubble",
                "siftite_ingot", "siftite_nugget", "siftite_upgrade_smithing_template");
        else if (args.length == 2 && args[0].equalsIgnoreCase("summon")) candidates = java.util.List.of(
                "singer", "echo_golem", "rift", "blub", "dark_sniffer", "bulb", "loomer", "wisp",
                "violet_wisp", "koi", "hollow_bulb", "glowbell", "thornback", "glimmerwing",
                "the_sifter", "spire_tyrant", "sifter", "frost_stalker", "sunspore", "drifter", "shardling",
                "prism_warden", "prismite", "lumen_tulip").stream().filter(speciesEnabled).toList();
        else if (args.length == 3 && (args[0].equalsIgnoreCase("give")
                || args[0].equalsIgnoreCase("summon"))) candidates = java.util.List.copyOf(players);
        else return java.util.List.of();
        String prefix = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)).toList();
    }

    static boolean requiresAdminPermission(String[] args) {
        // Player-facing travel commands: the README documents /sift dimension and /sift return
        // as the way players enter and leave the dimension, and /sift locate is the compass
        // helper. Strict mode still gates actual dimension access through readiness.
        if (args.length == 0) return true;
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        return !(sub.equals("locate") || sub.equals("dimension") || sub.equals("enter") || sub.equals("return"));
    }

    /** Bedrock runtime support status for /sift doctor (Geyser/Floodgate level-2 tier). */
    private String bedrockStatus() {
        if (bedrockBridge == null || !getConfig().getBoolean("providers.bedrock-enabled", false)) {
            return "Bedrock: disabled (providers.bedrock-enabled=false); Geyser/Floodgate are not consulted.";
        }
        boolean geyser = getServer().getPluginManager().isPluginEnabled("Geyser-Spigot");
        boolean floodgate = getServer().getPluginManager().isPluginEnabled("floodgate");
        boolean via = getServer().getPluginManager().isPluginEnabled("ViaVersion");
        long bedrockOnline = bedrockBridge.bedrockPlayersOnline();
        return "Bedrock: Geyser " + (geyser ? "detected" : "MISSING")
                + ", Floodgate " + (floodgate ? "detected" : "missing")
                + ", ViaVersion " + (via ? "detected" : "missing")
                + "; Bedrock players online: " + bedrockOnline
                + ". Bedrock clients do not receive the Java resource pack and hear vanilla fallback sounds.";
    }

    /** Bedrock bridge access for managers (lazy-created for standalone/test use). */
    public BedrockBridge bedrockAccess() {
        if (bedrockBridge == null) bedrockBridge = new BedrockBridge(this);
        return bedrockBridge;
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
