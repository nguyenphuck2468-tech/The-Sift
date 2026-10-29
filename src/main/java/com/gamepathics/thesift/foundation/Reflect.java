package com.gamepathics.thesift.foundation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches resolved provider-plugin classes and methods. The provider bridges probe optional
 * plugin APIs from per-event call sites (block breaks, pistons, interactions); resolving
 * {@code Class.forName} and {@code getMethod} on every event dominates that cost once a
 * provider is installed, so lookups go through these caches instead.
 */
final class Reflect {
    private static final Map<String, Optional<Class<?>>> CLASSES = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private Reflect() { }

    /** Returns the plugin API class, or null when the plugin is not installed. */
    static Class<?> optionalClass(String name) {
        return CLASSES.computeIfAbsent(name, n -> {
            try {
                return Optional.of(Class.forName(n));
            } catch (ClassNotFoundException | LinkageError ignored) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Returns the public method, or null when the provider API does not declare it. */
    static Method optionalMethod(Class<?> owner, String name, Class<?>... params) {
        String key = owner.getName() + "#" + name + "#" + Arrays.toString(params);
        return METHODS.computeIfAbsent(key, ignored -> {
            try {
                return Optional.of(owner.getMethod(name, params));
            } catch (NoSuchMethodException ignored) {
                return Optional.<Method>empty();
            }
        }).orElse(null);
    }

    static void clearForTests() {
        CLASSES.clear();
        METHODS.clear();
    }
}
