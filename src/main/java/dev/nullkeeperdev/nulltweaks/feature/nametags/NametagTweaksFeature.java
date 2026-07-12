package dev.nullkeeperdev.nulltweaks.feature.nametags;

import com.google.gson.JsonObject;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.awt.Color;
import java.lang.reflect.Method;

public final class NametagTweaksFeature extends Feature {
    private static NametagTweaksFeature instance;
    private static boolean cameraGetterResolved;
    private static Method cameraGetter;

    private double scale = 1.0D;
    private int backgroundOpacity = 25;
    private Color textColor = new Color(0xFFFFFF);
    private boolean bold;
    private boolean zoomCompensation = true;

    public NametagTweaksFeature() {
        super("nametag_tweaks", "Nametag Tweaks", true);
        instance = this;
    }

    public static NametagTweaksFeature instance() {
        return instance;
    }

    @Override
    public void buildConfig(OptionGroup.Builder builder) {
        builder.option(scaleOption())
                .option(backgroundOpacityOption())
                .option(colorOption("Text color", "Overrides the full nametag text color.", new Color(0xFFFFFF), this::textColor, this::setTextColor))
                .option(booleanOption("Bold", "Renders every affected nametag with bold text.", false, this::bold, this::setBold))
                .option(booleanOption("Compensate nametag scale while zoomed", "Keeps nametags readable when the current FOV is lower than your normal FOV.", true, this::zoomCompensation, this::setZoomCompensation));
    }

    public boolean active() {
        return isEnabled();
    }

    public Component formatName(Component original) {
        MutableComponent formatted = Component.literal(original.getString());
        if (bold) {
            formatted.withStyle(style -> style.withBold(true));
        }
        return formatted;
    }

    public float renderScaleMultiplier() {
        double multiplier = scale;
        if (zoomCompensation) {
            multiplier *= zoomMultiplier();
        }
        return (float) clampDouble(multiplier, 0.1D, 8.0D);
    }

    public int textColorWithAlpha(int previousColor) {
        int alpha = (previousColor >>> 24) & 0xFF;
        if (alpha == 0) {
            alpha = 0xFF;
        }
        return (alpha << 24) | (textColor.getRGB() & 0xFFFFFF);
    }

    public int backgroundColor() {
        int alpha = (int) Math.round(backgroundOpacity * 255.0D / 100.0D);
        return (clampInt(alpha, 0, 255) << 24);
    }

    @Override
    protected void loadSettings(JsonObject config) {
        scale = clampDouble(NullTweaksConfig.getDouble(config, "scale", 1.0D), 0.5D, 2.0D);
        backgroundOpacity = clampInt(NullTweaksConfig.getInt(config, "backgroundOpacity", 25), 0, 100);
        textColor = parseColor(NullTweaksConfig.getString(config, "textColor", "#ffffff"), new Color(0xFFFFFF));
        bold = NullTweaksConfig.getBoolean(config, "bold", false);
        zoomCompensation = NullTweaksConfig.getBoolean(config, "zoomCompensation", true);
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("scale", scale);
        config.addProperty("backgroundOpacity", backgroundOpacity);
        config.addProperty("textColor", colorString(textColor));
        config.addProperty("bold", bold);
        config.addProperty("zoomCompensation", zoomCompensation);
    }

    private Option<Double> scaleOption() {
        return Option.<Double>createBuilder()
                .name(Component.literal("Scale"))
                .description(description("Multiplies vanilla nametag size without replacing vanilla distance falloff."))
                .binding(1.0D, this::scale, this::setScale)
                .controller(option -> DoubleSliderControllerBuilder.create(option)
                        .range(0.5D, 2.0D)
                        .step(0.05D)
                        .valueFormatter(value -> Component.literal(String.format("%.2fx", value))))
                .instant(true)
                .build();
    }

    private Option<Integer> backgroundOpacityOption() {
        return Option.<Integer>createBuilder()
                .name(Component.literal("Background opacity"))
                .description(description("Controls the dark rectangle behind nametag text from transparent to fully opaque."))
                .binding(25, this::backgroundOpacity, this::setBackgroundOpacity)
                .controller(option -> IntegerSliderControllerBuilder.create(option)
                        .range(0, 100)
                        .step(1)
                        .valueFormatter(value -> Component.literal(value + "%")))
                .instant(true)
                .build();
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

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private double scale() {
        return scale;
    }

    private void setScale(double value) {
        scale = clampDouble(value, 0.5D, 2.0D);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private int backgroundOpacity() {
        return backgroundOpacity;
    }

    private void setBackgroundOpacity(int value) {
        backgroundOpacity = clampInt(value, 0, 100);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Color textColor() {
        return textColor;
    }

    private void setTextColor(Color color) {
        textColor = withoutAlpha(color);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean bold() {
        return bold;
    }

    private void setBold(boolean enabled) {
        bold = enabled;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean zoomCompensation() {
        return zoomCompensation;
    }

    private void setZoomCompensation(boolean enabled) {
        zoomCompensation = enabled;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private double zoomMultiplier() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null || client.gameRenderer == null) {
            return 1.0D;
        }

        double baselineFov = client.options.fov().get();
        double currentFov = currentFov(client);
        if (baselineFov <= 0.0D || currentFov <= 0.0D || currentFov >= baselineFov) {
            return 1.0D;
        }

        return clampDouble(baselineFov / currentFov, 1.0D, 4.0D);
    }

    private static double currentFov(Minecraft client) {
        Camera camera = currentCamera(client);
        if (camera == null) {
            return client.options.fov().get();
        }

        return camera.getFov();
    }

    private static Camera currentCamera(Minecraft client) {
        if (!cameraGetterResolved) {
            cameraGetterResolved = true;
            cameraGetter = resolveCameraGetter(client.gameRenderer.getClass());
        }

        if (cameraGetter == null) {
            return null;
        }

        try {
            Object camera = cameraGetter.invoke(client.gameRenderer);
            if (camera instanceof Camera minecraftCamera) {
                return minecraftCamera;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }

        return null;
    }

    private static Method resolveCameraGetter(Class<?> gameRendererClass) {
        for (String methodName : new String[]{"mainCamera", "getMainCamera"}) {
            try {
                return gameRendererClass.getMethod(methodName);
            } catch (NoSuchMethodException ignored) {
            }
        }

        return null;
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

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
