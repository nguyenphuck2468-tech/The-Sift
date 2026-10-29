package com.gamepathics.thesift.foundation;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/** Attaches user-supplied ModelEngine models to spawned Sift entities. */
final class ModelEngineBridge {
    private final JavaPlugin plugin;

    ModelEngineBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void attach(Entity entity, String creatureId) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ModelEngine")) return;
        String modelId = plugin.getConfig().getString(
                "integrations.mobs.modelengine.models." + creatureId, "").trim();
        if (modelId.isEmpty()) return;
        try {
            Class<?> api = Reflect.optionalClass("com.ticxo.modelengine.api.ModelEngineAPI");
            if (api == null) return;
            Method createModeledEntity = Reflect.optionalMethod(api, "createModeledEntity", Entity.class);
            Method createActiveModel = Reflect.optionalMethod(api, "createActiveModel", String.class);
            if (createModeledEntity == null || createActiveModel == null) return;
            Object modeled = createModeledEntity.invoke(null, entity);
            Object activeModel = createActiveModel.invoke(null, modelId);
            if (modeled == null || activeModel == null) return;
            Method addModel = null;
            for (Method method : modeled.getClass().getMethods()) {
                if (method.getName().equals("addModel") && method.getParameterCount() == 2
                        && method.getParameterTypes()[1] == boolean.class
                        && method.getParameterTypes()[0].isInstance(activeModel)) {
                    addModel = method;
                    break;
                }
            }
            if (addModel != null) addModel.invoke(modeled, activeModel, true);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("ModelEngine could not attach model for " + creatureId + ": "
                    + exception.getMessage());
        }
    }
}
