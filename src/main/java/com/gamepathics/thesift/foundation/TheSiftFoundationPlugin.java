package com.gamepathics.thesift.foundation;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
        if (contentRegistry.isEnabled(ContentId.ICHOR)) {
            ichorContent = new IchorContent(this);
            ichorState = new IchorState(this);
            ichorContent.registerRecipes();
            getServer().getPluginManager().registerEvents(new IchorListener(this, ichorContent, ichorState), this);
        }
        if (contentRegistry.isEnabled(ContentId.CHAROITE_ITEM)
                || contentRegistry.isEnabled(ContentId.SIFTITE_INGOT)) {
            siftiteContent = new SiftiteContent(this);
            siftiteContent.registerRecipes();
            getServer().getPluginManager().registerEvents(new SiftiteListener(siftiteContent), this);
            getServer().getPluginManager().registerEvents(new SiftiteRecoveryManager(this), this);
            getServer().getPluginManager().registerEvents(new CharoiteEnchantingListener(this), this);
        }
        if (contentRegistry.isEnabled(ContentId.SINGER)) {
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
        }
        // Nexo finishes loading its item registry asynchronously; refresh outputs after startup.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (ichorContent != null) ichorContent.registerRecipes();
            if (siftiteContent != null) siftiteContent.registerRecipes();
        }, 200L);
        getLogger().info("The Sift foundation enabled for Minecraft "
                + getConfig().getString("compatibility.minecraft-target", "unknown") + ".");
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
            // Teardown existing modules
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
            getServer().getPluginManager().disablePlugin(this);
            // Re-enable with new config
            getServer().getPluginManager().enablePlugin(this);
            sender.sendMessage("The Sift foundation fully reloaded.");
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
            return true;
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("dimension")
                || args[0].equalsIgnoreCase("enter"))) {
            if (siftDimensionManager == null) {
                sender.sendMessage("The Sift dimension is disabled.");
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
            if (ichorContent == null) {
                sender.sendMessage("Ichor content is disabled.");
                return true;
            }
            Player target = getServer().getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }
            target.getInventory().addItem(ichorContent.ichorBottle());
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
                target.getInventory().addItem(riftManager.createRiftKey());
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
                target.getInventory().addItem(item);
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
                riftManager.openRift(target.getLocation().add(0, 1, 0), target);
                sender.sendMessage("Opened a Rift near " + target.getName() + ".");
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

    private void loadContentFlags() {
        for (ContentId id : ContentId.values()) {
            boolean enabledByDefault = id == ContentId.SIFT_DIMENSION;
            contentRegistry.setEnabled(id,
                    getConfig().getBoolean("content." + id.key(), enabledByDefault));
        }
    }

    public ContentRegistry contentRegistry() {
        return contentRegistry;
    }
}
