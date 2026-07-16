package dev.nullkeeperdev.nulltweaks.feature;

import dev.nullkeeperdev.nulltweaks.NullTweaksClient;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FeatureManager {
    public static final FeatureManager INSTANCE = new FeatureManager();

    private final Map<String, Feature> features = new LinkedHashMap<>();
    private NullTweaksConfig config;
    private boolean hooksRegistered;

    private FeatureManager() {
    }

    public void register(Feature feature) {
        Feature previous = features.putIfAbsent(feature.id(), feature);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate feature id registered: " + feature.id());
        }
    }

    public Collection<Feature> getAll() {
        return List.copyOf(features.values());
    }

    public Optional<Feature> getById(String id) {
        return Optional.ofNullable(features.get(id));
    }

    public void initialize(NullTweaksConfig config) {
        this.config = config;

        for (Feature feature : features.values()) {
            feature.loadFromConfig(config.getFeatureConfig(feature.id()));
            feature.applyInitialState();
            writeFeature(feature);
        }

        config.save();
    }

    public void registerHooks() {
        if (hooksRegistered) {
            return;
        }

        hooksRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (Feature feature : features.values()) {
                if (feature.isActiveForHooks() && feature.listensForClientTicks()) {
                    try {
                        feature.onClientTick(client);
                    } catch (RuntimeException exception) {
                        disableForSession(feature, "client tick", exception);
                    }
                }
            }
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NullTweaksClient.MOD_ID, "features"), (guiGraphics, tickCounter) -> {
            for (Feature feature : features.values()) {
                if (feature.isActiveForHooks() && feature.listensForHudRender()) {
                    try {
                        feature.onHudRender(guiGraphics, tickCounter);
                    } catch (RuntimeException exception) {
                        disableForSession(feature, "HUD render", exception);
                    }
                }
            }
        });
        LevelRenderEvents.END_MAIN.register(context -> {
            for (Feature feature : features.values()) {
                if (feature.isActiveForHooks() && feature.listensForWorldRender()) {
                    try {
                        feature.onWorldRender(context);
                    } catch (RuntimeException exception) {
                        disableForSession(feature, "world render", exception);
                    }
                }
            }
        });
    }

    public void setEnabled(String id, boolean enabled) {
        Feature feature = getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown feature id: " + id));
        feature.setEnabled(enabled);
        saveFeature(feature);
    }

    public void saveFeature(Feature feature) {
        ensureInitialized();
        writeFeature(feature);
        config.save();
    }

    public void saveAll() {
        ensureInitialized();
        for (Feature feature : features.values()) {
            writeFeature(feature);
        }
        config.save();
    }

    private void writeFeature(Feature feature) {
        feature.saveToConfig(config.getFeatureConfig(feature.id()));
    }

    private void ensureInitialized() {
        if (config == null) {
            throw new IllegalStateException("FeatureManager has not been initialized");
        }
    }

    private static void disableForSession(Feature feature, String hook, RuntimeException exception) {
        feature.disableForSession();
        NullTweaksClient.LOGGER.error("Disabling feature {} for this session after failure in {}", feature.id(), hook, exception);
    }
}
