package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T1.4: the strict dimension gate matrix (pure part of ProviderReadiness). */
class ProviderStrictnessTest {
    @Test
    void strictModeAllowsDimensionOnlyWhenBothProviderGroupsComplete() {
        assertTrue(ProviderReadiness.allowed(true, true, true));
        assertFalse(ProviderReadiness.allowed(true, true, false), "missing mob backend must lock the dimension");
        assertFalse(ProviderReadiness.allowed(true, false, true), "missing item/block backend must lock the dimension");
        assertFalse(ProviderReadiness.allowed(true, false, false));
    }

    @Test
    void fallbackModeNeverLocksTheDimension() {
        assertTrue(ProviderReadiness.allowed(false, false, false));
        assertTrue(ProviderReadiness.allowed(false, true, false));
        assertTrue(ProviderReadiness.allowed(false, false, true));
        assertTrue(ProviderReadiness.allowed(false, true, true));
    }
}
