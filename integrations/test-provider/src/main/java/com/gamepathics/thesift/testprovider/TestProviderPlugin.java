package com.gamepathics.thesift.testprovider;

import com.gamepathics.thesift.foundation.api.SiftBlockProvider;
import com.gamepathics.thesift.foundation.api.SiftItemProvider;
import com.gamepathics.thesift.foundation.api.SiftMobProvider;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * Test provider (P9): registers all three Sift SPIs so the CI strict-boot gate can verify
 * that The Sift enables cleanly and unlocks the dimension when a real backend resolves
 * every mandatory ID. Deploy alongside The Sift on a Paper 26.3 server.
 *
 * <p>This is a TEST artifact, not production content: every item is a bare vanilla
 * material, every block placement is a no-op that returns true, every mob spawn is null.
 * It satisfies the readiness contract without shipping real custom content.
 */
public final class TestProviderPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getServicesManager().register(SiftItemProvider.class,
                new StubItemProvider(), this, org.bukkit.plugin.ServicePriority.Normal);
        getServer().getServicesManager().register(SiftBlockProvider.class,
                new StubBlockProvider(), this, org.bukkit.plugin.ServicePriority.Normal);
        getServer().getServicesManager().register(SiftMobProvider.class,
                new StubMobProvider(), this, org.bukkit.plugin.ServicePriority.Normal);
        getLogger().info("The Sift test provider registered all three SPIs (items, blocks, mobs).");
    }

    /** Declares support for every mandatory item ID; createItem returns a bare material stack. */
    private static final class StubItemProvider implements SiftItemProvider {
        @Override public String getProviderId() { return "test-provider"; }
        @Override public boolean supports(String contentId) { return true; }
        @Override public org.bukkit.inventory.ItemStack createItem(String contentId) {
            return new ItemStack(Material.PAPER);
        }
    }

    /** Declares support for every mandatory block ID; placement succeeds trivially. */
    private static final class StubBlockProvider implements SiftBlockProvider {
        @Override public String getProviderId() { return "test-provider"; }
        @Override public boolean supports(String contentId) { return true; }
        @Override public boolean placeBlock(String contentId, Location location) {
            return true;
        }
        @Override public boolean isBlock(String contentId, org.bukkit.block.Block block) { return false; }
    }

    /** Declares support for every mandatory mob ID; spawn is a no-op returning null. */
    private static final class StubMobProvider implements SiftMobProvider {
        @Override public String getProviderId() { return "test-provider"; }
        @Override public boolean supports(String contentId) { return true; }
        @Override public LivingEntity spawn(String contentId, Location location) { return null; }
    }
}
