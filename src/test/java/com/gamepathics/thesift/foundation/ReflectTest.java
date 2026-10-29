package com.gamepathics.thesift.foundation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReflectTest {
    @AfterEach
    void clearCache() {
        Reflect.clearForTests();
    }

    @Test
    void optionalClassResolvesKnownClassesAndReturnsNullForMissingOnes() {
        assertSame(String.class, Reflect.optionalClass("java.lang.String"));
        assertNull(Reflect.optionalClass("com.example.not.installed.PluginApi"));
        // A missing plugin stays cached: the second lookup does not retry Class.forName.
        assertNull(Reflect.optionalClass("com.example.not.installed.PluginApi"));
    }

    @Test
    void optionalMethodReturnsSameInstanceAcrossLookupsAndNullForMissing() throws Exception {
        Method first = Reflect.optionalMethod(String.class, "length");
        Method again = Reflect.optionalMethod(String.class, "length");
        assertSame(first, again);
        assertEquals(0, first.getParameterCount());

        assertNull(Reflect.optionalMethod(String.class, "noSuchMethodOnString"));
        assertNull(Reflect.optionalMethod(String.class, "valueOf", ReflectTest.class));
        assertSame(first, Reflect.optionalMethod(String.class, "length"));
    }
}
