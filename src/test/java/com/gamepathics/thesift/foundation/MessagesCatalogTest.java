package com.gamepathics.thesift.foundation;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.bukkit.configuration.file.YamlConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard for the bundled message catalogs. A syntax error in lang/vi.yml or lang/en.yml is
 * swallowed by Messages.loadBundle and silently degrades every in-game message to the hard
 * coded English default — this test makes the failure loud instead (a broken catalog once
 * shipped because of an accidental top-level fragment).
 */
class MessagesCatalogTest {

    /** Parses a bundled catalog; any YAML syntax error propagates and fails the test. */
    private static Map<String, String> load(String resource) throws Exception {
        try (InputStream stream = MessagesCatalogTest.class.getResourceAsStream(resource)) {
            assertTrue(stream != null, "missing bundled resource " + resource);
            YamlConfiguration config = new YamlConfiguration();
            config.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Map<String, String> values = new TreeMap<>();
            for (String key : config.getKeys(true)) {
                String value = config.getString(key);
                if (value != null) values.put(key, value);
            }
            return values;
        }
    }

    @Test
    void viCatalogParses() throws Exception {
        Map<String, String> vi = load("/lang/vi.yml");
        assertFalse(vi.isEmpty());
    }

    @Test
    void enCatalogParses() throws Exception {
        Map<String, String> en = load("/lang/en.yml");
        assertFalse(en.isEmpty());
    }

    @Test
    void viAndEnCatalogsCoverTheSameKeys() throws Exception {
        Set<String> vi = load("/lang/vi.yml").keySet();
        Set<String> en = load("/lang/en.yml").keySet();
        Set<String> missingInVi = new HashSet<>(en);
        missingInVi.removeAll(vi);
        Set<String> missingInEn = new HashSet<>(vi);
        missingInEn.removeAll(en);
        assertTrue(missingInVi.isEmpty(), "keys missing from vi.yml: " + missingInVi);
        assertTrue(missingInEn.isEmpty(), "keys missing from en.yml: " + missingInEn);
    }

    @Test
    void catalogsContainEveryRuntimeKey() throws Exception {
        Set<String> vi = load("/lang/vi.yml").keySet();
        for (String key : new String[]{
                "blub.tamed", "blub.failed", "blub.stay", "blub.follow",
                "spawn-eggs.disabled", "spawn-eggs.denied", "spawn-eggs.unknown",
                "echo-golem.bonded", "echo-golem.takes-soul", "echo-golem.returns-soul",
                "echo-golem.no-provider",
                "ichor.bottle-filled", "ichor.refill", "ichor.bucket-filled",
                "ichor.poured", "ichor.poured-blocked",
                "doctor.perm", "doctor.new-config",
                "tide.flowing", "tide.ebbing",
                "dimension.entered", "dimension.returned", "dimension.no-overworld",
                "world.first-entry",
                "gate.disabled", "gate.not-found", "gate.no-landing", "gate.silent",
                "gate.cannot-open", "gate.locked", "gate.gate-city", "gate.nearest",
                "gate.cannot-awaken", "gate.singer-failed", "gate.awakened", "gate.horn-first",
                "gate.opened", "gate.missing-notes", "gate.set-notes", "gate.no-return-site",
                "gate.return-frame-failed", "gate.returned", "gate.crossed",
                "sift.reload-done", "sift.datapack-usage", "sift.resourcepack-sent",
                "sift.resourcepack-no-provider", "sift.resourcepack-broadcast", "sift.version",
                "sift.runtime-target", "sift.dimension-disabled", "sift.dimension-locked",
                "sift.dimension-ready", "sift.dimension-load-failed", "sift.locate-console-only",
                "sift.return-console-only", "sift.ichor-disabled", "sift.rift-disabled",
                "sift.siftite-disabled", "sift.singer-disabled", "sift.echo-golem-disabled",
                "sift.creature-disabled", "sift.player-not-found", "sift.bottle-unresolved",
                "sift.key-unavailable", "sift.item-unresolved", "sift.gave", "sift.summon-usage",
                "sift.summon-usage-echo", "sift.summon-usage-rift", "sift.summon-usage-generic",
                "sift.summoned", "sift.unknown-creature", "sift.usage", "sift.locked",
                "sift.locked-no-world",
                "rift.dimension-unavailable", "rift.blocked-providers", "rift.blocked-world",
                "rift.tear-open", "rift.tearing-open", "rift.carries", "rift.open-failed",
                "sonorous.note", "sonorous.wrong-note", "sonorous.melody", "sonorous.awaken-first",
                "sonorous.no-frame", "sonorous.already-open", "sonorous.complete", "sonorous.closed",
                "soul.no-provider", "siftite.mini-rift-returned"}) {
            assertTrue(vi.contains(key), "vi.yml is missing runtime key " + key);
        }
    }

    @Test
    void aBrokenCatalogWouldFailParsingNotSilentlyFallBack() throws Exception {
        // Documents the failure mode this test class exists to catch: loadBundle swallows
        // the parse error, so a malformed catalog would otherwise degrade silently.
        YamlConfiguration config = new YamlConfiguration();
        org.junit.jupiter.api.Assertions.assertThrows(
                org.bukkit.configuration.InvalidConfigurationException.class, () ->
                        config.load(new java.io.StringReader(
                                "gate:\n  locked: \"ok\"\nbroken: \"x\"\n  indented: \"y\"\n")));
    }

    @Test
    void runtimeResolvesVietnameseCatalogThroughMessages() {
        ServerMock server = MockBukkit.mock();
        try {
            JavaPlugin plugin = MockBukkit.createMockPlugin("TheSift");
            plugin.getConfig().set("locale", "vi");
            Messages.init(plugin);
            assertEquals("Cổng Ancient City bị khóa vì provider chưa đủ. Chạy /sift doctor.",
                    Messages.get("gate.locked", "fallback-that-must-not-be-used"));
            assertEquals("Không có chỗ trống để trút Ichor.",
                    Messages.get("ichor.poured-blocked", "fallback-that-must-not-be-used"));
        } finally {
            MockBukkit.unmock();
        }
    }
}
