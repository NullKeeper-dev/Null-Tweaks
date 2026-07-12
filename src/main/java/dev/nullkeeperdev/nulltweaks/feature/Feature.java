package dev.nullkeeperdev.nulltweaks.feature;

import com.google.gson.JsonObject;
import dev.isxander.yacl3.api.OptionGroup;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Locale;

public abstract class Feature {
    private final String id;
    private final String displayName;
    private final boolean defaultEnabled;
    private boolean enabled;

    protected Feature(String id, String displayName) {
        this(id, displayName, false);
    }

    protected Feature(String id, String displayName, boolean defaultEnabled) {
        this.id = validateId(id);
        this.displayName = displayName;
        this.defaultEnabled = defaultEnabled;
        this.enabled = defaultEnabled;
    }

    public final String id() {
        return id;
    }

    public final String displayName() {
        return displayName;
    }

    public final boolean defaultEnabled() {
        return defaultEnabled;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public boolean listensForClientTicks() {
        return false;
    }

    public void onClientTick(Minecraft client) {
    }

    public boolean listensForHudRender() {
        return false;
    }

    public void onHudRender(GuiGraphicsExtractor guiGraphics, DeltaTracker tickCounter) {
    }

    public boolean listensForWorldRender() {
        return false;
    }

    public void onWorldRender(LevelRenderContext context) {
    }

    public abstract void buildConfig(OptionGroup.Builder builder);

    protected void loadSettings(JsonObject config) {
    }

    protected void saveSettings(JsonObject config) {
    }

    final void loadFromConfig(JsonObject config) {
        enabled = dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig.getBoolean(config, "enabled", defaultEnabled);
        loadSettings(config);
    }

    final void saveToConfig(JsonObject config) {
        config.addProperty("enabled", enabled);
        saveSettings(config);
    }

    final void applyInitialState() {
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    private static String validateId(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!normalized.equals(id) || !id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Feature ids must be lowercase snake_case: " + id);
        }

        return id;
    }
}
