package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentIdTest {

    @Test
    void allIdsHaveUniqueKeys() {
        ContentId[] values = ContentId.values();
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertNotEquals(values[i].key(), values[j].key(),
                    "Duplicate key: " + values[i].key());
            }
        }
    }

    @Test
    void allKeysAreLowercase() {
        for (ContentId id : ContentId.values()) {
            assertEquals(id.key().toLowerCase(), id.key(),
                "Key must be lowercase: " + id.key());
        }
    }

    @Test
    void keyIsNotEmpty() {
        for (ContentId id : ContentId.values()) {
            assertFalse(id.key().isEmpty(), "Key must not be empty");
        }
    }

    @Test
    void expectedCoreIdsExist() {
        assertEquals("siftslate", ContentId.SIFTSLATE.key());
        assertEquals("charoite", ContentId.CHAROITE.key());
        assertEquals("siftite", ContentId.SIFTITE.key());
        assertEquals("ichor", ContentId.ICHOR.key());
        assertEquals("singer", ContentId.SINGER.key());
        assertEquals("echo_golem", ContentId.ECHO_GOLEM.key());
        assertEquals("rift", ContentId.RIFT.key());
    }

    @Test
    void siftiteArmorIdsExist() {
        assertEquals("siftite_helmet", ContentId.SIFTITE_HELMET.key());
        assertEquals("siftite_chestplate", ContentId.SIFTITE_CHESTPLATE.key());
        assertEquals("siftite_leggings", ContentId.SIFTITE_LEGGINGS.key());
        assertEquals("siftite_boots", ContentId.SIFTITE_BOOTS.key());
    }

    @Test
    void siftiteToolIdsExist() {
        assertEquals("siftite_sword", ContentId.SIFTITE_SWORD.key());
        assertEquals("siftite_axe", ContentId.SIFTITE_AXE.key());
        assertEquals("siftite_pickaxe", ContentId.SIFTITE_PICKAXE.key());
        assertEquals("siftite_shovel", ContentId.SIFTITE_SHOVEL.key());
        assertEquals("siftite_hoe", ContentId.SIFTITE_HOE.key());
        assertEquals("siftite_spear", ContentId.SIFTITE_SPEAR.key());
    }
}