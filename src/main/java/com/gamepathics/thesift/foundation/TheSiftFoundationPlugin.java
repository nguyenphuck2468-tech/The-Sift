package com.gamepathics.thesift.foundation;

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

    @Override
    public void onEnable() {
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
        }
        if (contentRegistry.isEnabled(ContentId.SINGER)) {
            resonanceManager = new ResonanceManager(this);
            getServer().getPluginManager().registerEvents(resonanceManager, this);
            resonanceManager.start();
        }
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
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("sift")) {
            return false;
        }
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
        sender.sendMessage("Usage: /sift <doctor|reload|give|summon singer>");
        return true;
    }

    private void loadContentFlags() {
        for (ContentId id : ContentId.values()) {
            contentRegistry.setEnabled(id, getConfig().getBoolean("content." + id.key(), false));
        }
    }

    public ContentRegistry contentRegistry() {
        return contentRegistry;
    }
}
