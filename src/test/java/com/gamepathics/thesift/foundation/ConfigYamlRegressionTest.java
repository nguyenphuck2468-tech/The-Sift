package com.gamepathics.thesift.foundation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;



import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression for the duplicate-rift-keys bug: SnakeYAML lets a later duplicate key override the
 * earlier one, so the hardening defaults (0 / 0.0) were silently beaten by the legacy 3 / 0.3
 * pair further down the file. This test loads the SHIPPED config.yml exactly like Bukkit does.
 */
class ConfigYamlRegressionTest {
    @Test
    void shippedConfigYieldsTheHardeningRiftEconomyDefaults() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(readShippedConfig());
        assertEquals(0, config.getInt("rift.reward-drops"),
                "duplicate legacy keys must not resurrect the 3-item reward default");
        assertEquals(0.0, config.getDouble("rift.rift-key-chance"), 1e-9,
                "duplicate legacy keys must not resurrect the 0.3 key-chance default");
        assertEquals(30, config.getInt("rift.reward-cooldown-minutes"));
        assertEquals(false, config.getBoolean("content.prism_warden"));
        assertEquals(false, config.getBoolean("content.sift_standin_mobs"));
        assertEquals(false, config.getBoolean("content.sift_tide"));
        assertEquals(false, config.getBoolean("content.rift_walker_waves"));
        assertEquals(false, config.getBoolean("content.spire_tyrant"));
        assertEquals("vi", config.getString("locale"));
    }

    private String readShippedConfig() {
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            if (stream == null) {
                // test classpath exposes resources at the root: fall back to the working tree
                return java.nio.file.Files.readString(
                        java.nio.file.Path.of("src/main/resources/config.yml"));
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read the shipped config.yml", exception);
        }
    }

}
