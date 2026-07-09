package dev.nullkeeperdev.nulltweaks.feature.clearfluids;

import com.google.gson.JsonObject;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.awt.Color;

public final class ClearFluidsFeature extends Feature {
    private static ClearFluidsFeature instance;

    private boolean waterEnabled;
    private Color waterColor = Color.WHITE;
    private int waterOpacity = 100;
    private boolean lavaEnabled;
    private Color lavaColor = Color.WHITE;
    private int lavaOpacity = 100;

    public ClearFluidsFeature() {
        super("clear_fluids", "Clear Lava & Water");
        instance = this;
    }

    @Override
    public void onEnable() {
        refreshWorldRender();
    }

    @Override
    public void onDisable() {
        refreshWorldRender();
    }

    public static RenderSettings renderSettingsFor(FluidState fluidState) {
        ClearFluidsFeature feature = instance;
        if (feature == null || !feature.isEnabled() || fluidState == null) {
            return null;
        }

        FluidKind kind = kindFor(fluidState);
        if (kind == null) {
            return null;
        }

        return feature.settingsFor(kind);
    }

    public static boolean shouldClearWaterFog() {
        ClearFluidsFeature feature = instance;
        return feature != null && feature.isEnabled() && feature.waterEnabled && feature.waterOpacity < 100;
    }

    public static boolean shouldClearLavaFog() {
        ClearFluidsFeature feature = instance;
        return feature != null && feature.isEnabled() && feature.lavaEnabled && feature.lavaOpacity < 100;
    }

    @Override
    public void buildConfig(ConfigCategory.Builder builder) {
        builder.group(OptionGroup.createBuilder()
                .name(Component.literal("Water"))
                .collapsed(false)
                .option(booleanOption("Water", "Applies the Null Tweaks water tint and opacity override.", false, this::waterEnabled, this::setWaterEnabled))
                .option(colorOption("Water tint", "Color multiplied into rendered water.", Color.WHITE, this::waterColor, this::setWaterColor))
                .option(opacityOption("Water opacity", "Controls how transparent rendered water is.", this::waterOpacity, this::setWaterOpacity))
                .build());
        builder.group(OptionGroup.createBuilder()
                .name(Component.literal("Lava"))
                .collapsed(false)
                .option(booleanOption("Lava", "Applies the Null Tweaks lava tint and opacity override.", false, this::lavaEnabled, this::setLavaEnabled))
                .option(colorOption("Lava tint", "Color multiplied into rendered lava.", Color.WHITE, this::lavaColor, this::setLavaColor))
                .option(opacityOption("Lava opacity", "Controls how transparent rendered lava is.", this::lavaOpacity, this::setLavaOpacity))
                .build());
    }

    @Override
    protected void loadSettings(JsonObject config) {
        waterEnabled = NullTweaksConfig.getBoolean(config, "waterEnabled", false);
        waterColor = parseColor(NullTweaksConfig.getString(config, "waterColor", "#ffffff"), Color.WHITE);
        waterOpacity = clampOpacity(NullTweaksConfig.getInt(config, "waterOpacity", 100));
        lavaEnabled = NullTweaksConfig.getBoolean(config, "lavaEnabled", false);
        lavaColor = parseColor(NullTweaksConfig.getString(config, "lavaColor", "#ffffff"), Color.WHITE);
        lavaOpacity = clampOpacity(NullTweaksConfig.getInt(config, "lavaOpacity", 100));
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("waterEnabled", waterEnabled);
        config.addProperty("waterColor", colorString(waterColor));
        config.addProperty("waterOpacity", waterOpacity);
        config.addProperty("lavaEnabled", lavaEnabled);
        config.addProperty("lavaColor", colorString(lavaColor));
        config.addProperty("lavaOpacity", lavaOpacity);
    }

    private RenderSettings settingsFor(FluidKind kind) {
        return switch (kind) {
            case WATER -> waterEnabled ? new RenderSettings(waterColor, waterOpacity) : null;
            case LAVA -> lavaEnabled ? new RenderSettings(lavaColor, lavaOpacity) : null;
        };
    }

    private static FluidKind kindFor(FluidState fluidState) {
        Fluid fluid = fluidState.getType();
        if (fluid.isSame(Fluids.WATER) || fluid.isSame(Fluids.FLOWING_WATER)) {
            return FluidKind.WATER;
        }
        if (fluid.isSame(Fluids.LAVA) || fluid.isSame(Fluids.FLOWING_LAVA)) {
            return FluidKind.LAVA;
        }

        return null;
    }

    private Option<Boolean> booleanOption(String name, String descriptionText, boolean fallback, java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(description(descriptionText))
                .binding(fallback, getter::getAsBoolean, setter)
                .controller(BooleanControllerBuilder::create)
                .instant(true)
                .build();
    }

    private Option<Color> colorOption(String name, String descriptionText, Color fallback, java.util.function.Supplier<Color> getter, java.util.function.Consumer<Color> setter) {
        return Option.<Color>createBuilder()
                .name(Component.literal(name))
                .description(description(descriptionText))
                .binding(fallback, getter, setter)
                .controller(option -> ColorControllerBuilder.create(option).allowAlpha(false))
                .instant(true)
                .build();
    }

    private Option<Integer> opacityOption(String name, String descriptionText, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        return Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(description(descriptionText))
                .binding(100, getter::getAsInt, value -> setter.accept(value))
                .controller(option -> IntegerSliderControllerBuilder.create(option)
                        .range(0, 100)
                        .step(1)
                        .valueFormatter(value -> Component.literal(value + "%")))
                .instant(true)
                .build();
    }

    private boolean waterEnabled() {
        return waterEnabled;
    }

    private void setWaterEnabled(boolean value) {
        waterEnabled = value;
        refreshWorldRender();
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Color waterColor() {
        return waterColor;
    }

    private void setWaterColor(Color value) {
        waterColor = withoutAlpha(value);
        refreshWorldRender();
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private int waterOpacity() {
        return waterOpacity;
    }

    private void setWaterOpacity(int value) {
        waterOpacity = clampOpacity(value);
        refreshWorldRender();
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean lavaEnabled() {
        return lavaEnabled;
    }

    private void setLavaEnabled(boolean value) {
        lavaEnabled = value;
        refreshWorldRender();
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Color lavaColor() {
        return lavaColor;
    }

    private void setLavaColor(Color value) {
        lavaColor = withoutAlpha(value);
        refreshWorldRender();
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private int lavaOpacity() {
        return lavaOpacity;
    }

    private void setLavaOpacity(int value) {
        lavaOpacity = clampOpacity(value);
        refreshWorldRender();
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private static void refreshWorldRender() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.levelRenderer == null) {
            return;
        }

        //? if >=26.2 {
        client.levelRenderer.resetLevelRenderData();
        //?} else {
        /*client.levelRenderer.allChanged();
        *///?}
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private static Color parseColor(String value, Color fallback) {
        String normalized = value.startsWith("#") ? value.substring(1) : value;
        try {
            return new Color(Integer.parseInt(normalized, 16) & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String colorString(Color color) {
        return String.format("#%06x", color.getRGB() & 0xFFFFFF);
    }

    private static Color withoutAlpha(Color color) {
        return new Color(color.getRGB() & 0xFFFFFF);
    }

    private static int clampOpacity(int value) {
        return Mth.clamp(value, 0, 100);
    }

    public enum FluidKind {
        WATER,
        LAVA
    }

    public record RenderSettings(Color tint, int opacity) {
        public int applyTo(int color) {
            int vanillaAlpha = color >>> 24;
            if (vanillaAlpha == 0 && (color & 0xFFFFFF) != 0) {
                vanillaAlpha = 255;
            }
            int alpha = (vanillaAlpha * clampOpacity(opacity) + 50) / 100;
            int red = ((color >>> 16) & 0xFF) * tint.getRed() / 255;
            int green = ((color >>> 8) & 0xFF) * tint.getGreen() / 255;
            int blue = (color & 0xFF) * tint.getBlue() / 255;
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
    }
}
