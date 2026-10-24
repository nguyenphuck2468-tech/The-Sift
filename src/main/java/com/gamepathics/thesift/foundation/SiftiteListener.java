package com.gamepathics.thesift.foundation;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public final class SiftiteListener implements Listener {
    private final SiftiteContent content;

    public SiftiteListener(SiftiteContent content) {
        this.content = content;
    }

    @EventHandler
    public void onOreBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.AMETHYST_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!tool.getType().name().endsWith("PICKAXE")) return;
        event.setDropItems(false);
        int amount = ThreadLocalRandom.current().nextInt(1, 3);
        ItemStack drop = content.item(Material.LAPIS_LAZULI, "charoite", "Charoite");
        drop.setAmount(amount);
        block.getWorld().dropItemNaturally(block.getLocation(), drop);
    }
}
