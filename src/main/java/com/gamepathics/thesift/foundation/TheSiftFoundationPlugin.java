package com.gamepathics.thesift.foundation;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.Command;
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
        });
        saveDefaultConfig();
        if (!ConfigMigration.migrate(this)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        loadContentFlags();
        providerReadiness = new ProviderReadiness(this);
        if (!providerReadiness.strict()) {
            getLogger().warning("providers.strict is disabled; vanilla fallback is enabled for development/CI.");
        } else if (!providerReadiness.dimensionAllowed()) {
            getLogger().severe("The Sift dimension access is locked: required item, block, or mob provider IDs are unresolved. Run /sift doctor for the exact provider and ID list. Existing world data is preserved.");
        }
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
            resonanceManager = new ResonanceManager(this);
            getServer().getPluginManager().registerEvents(resonanceManager, this);
            resonanceManager.start();
        }
        if (contentRegistry.isEnabled(ContentId.ECHO_GOLEM)) {
            echoGolemManager = new EchoGolemManager(this);
            getServer().getPluginManager().registerEvents(echoGolemManager, this);
            echoGolemManager.start();
        }
        if (contentRegistry.isEnabled(ContentId.RIFT)) {
            riftManager = new RiftManager(this);
            getServer().getPluginManager().registerEvents(riftManager, this);
            riftManager.start();
        }
        if (contentRegistry.isEnabled(ContentId.SIFT_DIMENSION)) {
            siftDimensionManager = new SiftDimensionManager(this);
            siftEcologyManager = new SiftEcologyManager(this, resonanceManager, echoGolemManager, riftManager);
            getServer().getPluginManager().registerEvents(siftEcologyManager, this);
            siftEcologyManager.populateLoadedSpawnChunks();
            advancementManager = new SiftAdvancementManager(this);
            getServer().getPluginManager().registerEvents(advancementManager, this);
        }
        if (contentRegistry.isEnabled(ContentId.SOUL_BLOCK)) {
            getServer().getPluginManager().registerEvents(new SoulBlockListener(this), this);
        }
        registerContentPluginReloadHooks();
        // Keep a delayed initial refresh as a fallback for providers without a load event.
        getServer().getScheduler().runTaskLater(this, () -> {
            refreshContentRecipes();
        }, 200L);
        getLogger().info("The Sift foundation enabled for Minecraft "
                + getConfig().getString("compatibility.minecraft-target", "unknown") + ".");
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
                    (ignored, event) -> getServer().getScheduler().runTask(this, this::refreshContentRecipes),
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

    @Override
    public void onDisable() {
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("sift")) {
            return false;
        }
        return handleSiftCommand(sender, args);
    }

    private boolean handleSiftCommand(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            if (!ConfigMigration.migrate(this)) {
                sender.sendMessage("The Sift config is newer than this build; reload was rejected.");
                return true;
            }
            loadContentFlags();
            refreshContentRecipes();
            sender.sendMessage("The Sift config was reloaded. Restart the server to apply content flag changes.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("doctor")) {
            sender.sendMessage("The Sift foundation: " + getDescription().getVersion());
            sender.sendMessage("Minecraft target: "
                    + getConfig().getString("compatibility.minecraft-target", "unknown"));
            for (Map.Entry<ContentId, Boolean> entry : contentRegistry.snapshot().entrySet()) {
                sender.sendMessage("- " + entry.getKey().key() + ": "
                        + (entry.getValue() ? "enabled" : "disabled"));
            }
            for (String line : providerReadiness.summary().split("\\n")) sender.sendMessage(line);
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
            giveOrDrop(target, ichorContent.ichorBottle());
            sender.sendMessage("Gave Ichor Bottle to " + target.getName() + ".");
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
                giveOrDrop(target, riftManager.createRiftKey());
                sender.sendMessage("Gave Rift Key to " + target.getName() + ".");
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
                sender.sendMessage("Unknown Charoite/Siftite item.");
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
        sender.sendMessage("Usage: /sift <doctor|reload|dimension|return|give|summon singer|summon echo_golem|summon rift|give rift_key>");
        return true;
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
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    public ContentRegistry contentRegistry() {
        return contentRegistry;
    }
}
