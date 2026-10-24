package com.gamepathics.thesift.foundation;

import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RiftBlockMetadataTest {
    @Test
    void noBlockBreakHandlerIsRegisteredWithoutAPlacedRiftBlockProducer() {
        for (Method method : RiftManager.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventHandler.class)) continue;
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(BlockBreakEvent.class.isAssignableFrom(parameter),
                        "RiftManager must not register metadata-based protection for blocks it never creates");
            }
        }
    }
}
