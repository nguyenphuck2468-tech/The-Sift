package com.gamepathics.thesift.foundation;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URISyntaxException;

public final class TheSiftBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                event.registrar().discoverPack(
                        TheSiftBootstrap.class.getResource("/sift_dimension_datapack").toURI(), "dimension");
                event.registrar().discoverPack(
                        TheSiftBootstrap.class.getResource("/datapack").toURI(), "progression");
            } catch (IOException | URISyntaxException exception) {
                throw new IllegalStateException("Could not register The Sift datapack", exception);
            }
        });
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        return new TheSiftFoundationPlugin();
    }
}
