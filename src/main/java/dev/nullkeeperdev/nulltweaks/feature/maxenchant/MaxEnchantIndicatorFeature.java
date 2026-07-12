package dev.nullkeeperdev.nulltweaks.feature.maxenchant;

import com.google.gson.JsonObject;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.nullkeeperdev.nulltweaks.NullTweaksClient;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;

import java.awt.Color;
import java.util.OptionalInt;

public final class MaxEnchantIndicatorFeature extends Feature {
    private static final Color DEFAULT_INDICATOR_COLOR = new Color(0x55D6FF);
    private static final double DEFAULT_CHROMA_SPEED = 1.0D;
    private static MaxEnchantIndicatorFeature instance;

    private Color indicatorColor = DEFAULT_INDICATOR_COLOR;
    private boolean chromaEnabled;
    private double chromaSpeed = DEFAULT_CHROMA_SPEED;
    private boolean runtimeDisabled;

    public MaxEnchantIndicatorFeature() {
        super("max_enchant_indicator", "Max Enchant Indicator", false);
        instance = this;
    }

    public static boolean isAtMaxLevel(Enchantment enchantment, int level) {
        return enchantment != null && level >= enchantment.getMaxLevel();
    }

    public static OptionalInt indicatorColorFor(Holder<Enchantment> enchantment, int level) {
        MaxEnchantIndicatorFeature feature = instance;
        if (feature == null || !feature.active() || enchantment == null) {
            return OptionalInt.empty();
        }

        try {
            if (!isAtMaxLevel(enchantment.value(), level)) {
                return OptionalInt.empty();
            }

            return OptionalInt.of(feature.currentIndicatorColor());
        } catch (RuntimeException exception) {
            feature.disableForSession("max-enchant holder color lookup", exception);
            return OptionalInt.empty();
        }
    }

    public static OptionalInt indicatorColorFor(Identifier enchantmentId, int level) {
        MaxEnchantIndicatorFeature feature = instance;
        if (feature == null || !feature.active() || enchantmentId == null) {
            return OptionalInt.empty();
        }

        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.level == null) {
                return OptionalInt.empty();
            }

            return client.level.registryAccess().lookup(Registries.ENCHANTMENT)
                    .flatMap(registry -> registry.getOptional(enchantmentId))
                    .filter(enchantment -> isAtMaxLevel(enchantment, level))
                    .map(ignored -> OptionalInt.of(feature.currentIndicatorColor()))
                    .orElseGet(OptionalInt::empty);
        } catch (RuntimeException exception) {
            feature.disableForSession("max-enchant id color lookup", exception);
            return OptionalInt.empty();
        }
    }

    public static Component applyIndicatorColor(Holder<Enchantment> enchantment, int level, Component component) {
        try {
            OptionalInt color = indicatorColorFor(enchantment, level);
            if (color.isEmpty()) {
                return component;
            }

            return component.copy().withColor(color.getAsInt());
        } catch (RuntimeException exception) {
            MaxEnchantIndicatorFeature feature = instance;
            if (feature != null) {
                feature.disableForSession("max-enchant tooltip coloring", exception);
            }
            return component;
        }
    }

    @Override
    public void onEnable() {
        runtimeDisabled = false;
    }

    @Override
    public void buildConfig(OptionGroup.Builder builder) {
        builder.option(indicatorColorOption())
                .option(chromaEnabledOption())
                .option(chromaSpeedOption());
    }

    @Override
    protected void loadSettings(JsonObject config) {
        indicatorColor = parseColor(NullTweaksConfig.getString(config, "indicatorColor", colorString(DEFAULT_INDICATOR_COLOR)), DEFAULT_INDICATOR_COLOR);
        chromaEnabled = NullTweaksConfig.getBoolean(config, "chromaEnabled", false);
        chromaSpeed = clampDouble(NullTweaksConfig.getDouble(config, "chromaSpeed", DEFAULT_CHROMA_SPEED), 0.1D, 5.0D);
        runtimeDisabled = false;
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("indicatorColor", colorString(indicatorColor));
        config.addProperty("chromaEnabled", chromaEnabled);
        config.addProperty("chromaSpeed", chromaSpeed);
    }

    private int currentIndicatorColor() {
        if (!chromaEnabled) {
            return indicatorColor.getRGB() & 0xFFFFFF;
        }

        double seconds = System.nanoTime() / 1_000_000_000.0D;
        float hue = (float) ((seconds * chromaSpeed / 4.0D) % 1.0D);
        return Color.HSBtoRGB(hue, 1.0F, 1.0F) & 0xFFFFFF;
    }

    private boolean active() {
        return isEnabled() && !runtimeDisabled;
    }

    private void disableForSession(String hook, RuntimeException exception) {
        runtimeDisabled = true;
        NullTweaksClient.LOGGER.error("Disabling feature {} for this session after failure in {}", id(), hook, exception);
    }

    private Option<Color> indicatorColorOption() {
        return Option.<Color>createBuilder()
                .name(Component.literal("Solid color"))
                .description(description("Color used for max-level enchantments when chroma is off."))
                .binding(DEFAULT_INDICATOR_COLOR, this::indicatorColor, this::setIndicatorColor)
                .controller(option -> ColorControllerBuilder.create(option).allowAlpha(false))
                .instant(true)
                .build();
    }

    private Option<Boolean> chromaEnabledOption() {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Enable chroma"))
                .description(description("Animates max-level enchantment indicators through the full hue spectrum."))
                .binding(false, this::chromaEnabled, this::setChromaEnabled)
                .controller(BooleanControllerBuilder::create)
                .instant(true)
                .build();
    }

    private Option<Double> chromaSpeedOption() {
        return Option.<Double>createBuilder()
                .name(Component.literal("Chroma speed"))
                .description(description("Controls how quickly chroma cycles through the hue spectrum."))
                .binding(DEFAULT_CHROMA_SPEED, this::chromaSpeed, this::setChromaSpeed)
                .controller(option -> DoubleSliderControllerBuilder.create(option)
                        .range(0.1D, 5.0D)
                        .step(0.1D)
                        .valueFormatter(value -> Component.literal(String.format("%.1fx", value))))
                .instant(true)
                .build();
    }

    private Color indicatorColor() {
        return indicatorColor;
    }

    private void setIndicatorColor(Color color) {
        indicatorColor = withoutAlpha(color);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean chromaEnabled() {
        return chromaEnabled;
    }

    private void setChromaEnabled(boolean enabled) {
        chromaEnabled = enabled;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private double chromaSpeed() {
        return chromaSpeed;
    }

    private void setChromaSpeed(double value) {
        chromaSpeed = clampDouble(value, 0.1D, 5.0D);
        FeatureManager.INSTANCE.saveFeature(this);
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
        return color == null ? DEFAULT_INDICATOR_COLOR : new Color(color.getRGB() & 0xFFFFFF);
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
