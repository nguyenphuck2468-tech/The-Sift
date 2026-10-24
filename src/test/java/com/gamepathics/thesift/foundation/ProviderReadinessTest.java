package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import com.gamepathics.thesift.foundation.api.SiftItemProvider;
import com.gamepathics.thesift.foundation.api.SiftBlockProvider;
import com.gamepathics.thesift.foundation.api.SiftMobProvider;

import static org.junit.jupiter.api.Assertions.*;

class ProviderReadinessTest {
    private ServerMock server;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("TheSift");
        plugin.getConfig().set("providers.strict", true);
        plugin.getConfig().set("integrations.items.provider", "fixture");
        plugin.getConfig().set("integrations.blocks.provider", "fixture");
        plugin.getConfig().set("integrations.mobs.provider", "fixture");
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void strictModeNeedsBothContentAndMobBackends() {
        assertTrue(ProviderReadiness.allowed(true, true, true));
        assertFalse(ProviderReadiness.allowed(true, false, true));
        assertFalse(ProviderReadiness.allowed(true, true, false));
        assertFalse(ProviderReadiness.allowed(true, false, false));
    }

    @Test
    void developmentModeRetainsFallbackBehavior() {
        assertTrue(ProviderReadiness.allowed(false, false, false));
    }

    @Test
    void strictRequiresResolvedIdsFromAllThreeBackendGroups() {
        assertFalse(new ProviderReadiness(plugin).dimensionAllowed());
        var services = server.getServicesManager();
        services.register(SiftItemProvider.class, new Items(), plugin, ServicePriority.Normal);
        services.register(SiftBlockProvider.class, new Blocks(), plugin, ServicePriority.Normal);
        services.register(SiftMobProvider.class, new Mobs(), plugin, ServicePriority.Normal);
        assertTrue(new ProviderReadiness(plugin).dimensionAllowed());
    }

    @Test
    void strictReportsMissingRequiredIdAndDisabledStrictAllowsFallback() {
        var services = server.getServicesManager();
        services.register(SiftItemProvider.class, new Items(), plugin, ServicePriority.Normal);
        services.register(SiftBlockProvider.class, new MissingSiftslateBlock(), plugin, ServicePriority.Normal);
        services.register(SiftMobProvider.class, new Mobs(), plugin, ServicePriority.Normal);

        ProviderReadiness readiness = new ProviderReadiness(plugin);
        assertFalse(readiness.dimensionAllowed());
        assertTrue(readiness.summary().contains("siftslate"));

        plugin.getConfig().set("providers.strict", false);
        assertTrue(readiness.dimensionAllowed());
    }

    private static final class Items implements SiftItemProvider {
        @Override public String getProviderId() { return "fixture"; }
        @Override public ItemStack createItem(String contentId) { return null; }
        @Override public boolean supports(String contentId) { return ProviderContentIds.ITEM_IDS.contains(contentId); }
    }
    private static final class Blocks implements SiftBlockProvider {
        @Override public String getProviderId() { return "fixture"; }
        @Override public boolean placeBlock(String contentId, Location location) { return false; }
        @Override public boolean supports(String contentId) { return ProviderContentIds.BLOCK_IDS.contains(contentId); }
    }
    private static final class MissingSiftslateBlock implements SiftBlockProvider {
        @Override public String getProviderId() { return "fixture"; }
        @Override public boolean placeBlock(String contentId, Location location) { return false; }
        @Override public boolean supports(String contentId) {
            return ProviderContentIds.BLOCK_IDS.contains(contentId) && !contentId.equals("siftslate");
        }
    }
    private static final class Mobs implements SiftMobProvider {
        @Override public String getProviderId() { return "fixture"; }
        @Override public LivingEntity spawn(String creatureId, Location location) { return null; }
        @Override public boolean supports(String creatureId) { return ProviderContentIds.MOB_IDS.contains(creatureId); }
    }
}
