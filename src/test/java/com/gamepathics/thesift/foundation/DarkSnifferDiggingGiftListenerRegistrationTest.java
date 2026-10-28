package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkSnifferDiggingGiftListenerRegistrationTest {
    @Test
    void giftListenerIsRegisteredByPluginRatherThanResourcePackHandler() throws Exception {
        String pluginSource = Files.readString(Path.of(
                "src/main/java/com/gamepathics/thesift/foundation/TheSiftFoundationPlugin.java"));
        String resourcePackListenerSource = Files.readString(Path.of(
                "src/main/java/com/gamepathics/thesift/foundation/SiftResourcePackListener.java"));

        assertTrue(
                pluginSource.contains("registerEvents(new DarkSnifferDiggingGiftListener(this), this);"),
                "The plugin must register the Dark Sniffer gift listener during enable");
        assertFalse(
                resourcePackListenerSource.contains("new DarkSnifferDiggingGiftListener"),
                "Gift listener registration must not depend on resource-pack handling");
    }
}
