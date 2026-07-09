package dev.nullkeeperdev.nulltweaks.feature.nofog;

import com.google.gson.JsonObject;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import dev.nullkeeperdev.nulltweaks.feature.clearfluids.ClearFluidsFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.FogType;

public final class NoFogFeature extends Feature {
    private static NoFogFeature instance;

    private boolean lavaFog;
    private boolean waterFog;
    private boolean powderSnowFog;
    private boolean blindnessFog;
    private boolean darknessFog;
    private boolean atmosphericFog;

    public NoFogFeature() {
        super("no_fog", "No Fog");
        instance = this;
    }

    public static FogType fogTypeFor(Camera camera) {
        FogType fogType = camera.getFluidInCamera();
        if (fogType == FogType.NONE) {
            return FogType.ATMOSPHERIC;
        }

        if (fogType == FogType.LAVA && (shouldDisableLavaFog() || ClearFluidsFeature.shouldClearLavaFog())) {
            return FogType.ATMOSPHERIC;
        }
        if (fogType == FogType.WATER && (shouldDisableWaterFog() || ClearFluidsFeature.shouldClearWaterFog())) {
            return FogType.ATMOSPHERIC;
        }
        if (fogType == FogType.POWDER_SNOW && shouldDisablePowderSnowFog()) {
            return FogType.ATMOSPHERIC;
        }

        return fogType;
    }

    public static boolean shouldSkipColorEnvironment(FogEnvironment environment) {
        return shouldDisableStatusEnvironment(environment);
    }

    public static boolean shouldSkipSetupEnvironment(FogEnvironment environment) {
        return shouldDisableStatusEnvironment(environment)
                || environment instanceof AtmosphericFogEnvironment && shouldDisableAtmosphericFog();
    }

    public static boolean shouldDisableAtmosphericFog() {
        NoFogFeature feature = instance;
        return feature != null && feature.isEnabled() && feature.atmosphericFog;
    }

    private static boolean shouldDisableLavaFog() {
        NoFogFeature feature = instance;
        return feature != null && feature.isEnabled() && feature.lavaFog;
    }

    private static boolean shouldDisableWaterFog() {
        NoFogFeature feature = instance;
        return feature != null && feature.isEnabled() && feature.waterFog;
    }

    private static boolean shouldDisablePowderSnowFog() {
        NoFogFeature feature = instance;
        return feature != null && feature.isEnabled() && feature.powderSnowFog;
    }

    private static boolean shouldDisableStatusEnvironment(FogEnvironment environment) {
        NoFogFeature feature = instance;
        if (feature == null || !feature.isEnabled()) {
            return false;
        }

        return environment instanceof BlindnessFogEnvironment && feature.blindnessFog
                || environment instanceof DarknessFogEnvironment && feature.darknessFog;
    }

    @Override
    public void buildConfig(ConfigCategory.Builder builder) {
        builder.group(OptionGroup.createBuilder()
                .name(Component.literal("Fog Types"))
                .collapsed(false)
                .option(booleanOption("Lava fog", "Disables the fog applied while the camera is inside lava.", this::lavaFog, this::setLavaFog))
                .option(booleanOption("Water fog", "Disables the fog applied while the camera is underwater.", this::waterFog, this::setWaterFog))
                .option(booleanOption("Powder snow fog", "Disables the fog applied while the camera is inside powder snow.", this::powderSnowFog, this::setPowderSnowFog))
                .option(booleanOption("Blindness fog", "Disables the visual fog from the Blindness status effect without removing the effect.", this::blindnessFog, this::setBlindnessFog))
                .option(booleanOption("Darkness fog", "Disables the visual fog from the Darkness status effect without removing the effect.", this::darknessFog, this::setDarknessFog))
                .option(booleanOption("Atmospheric/world fog", "Disables normal render-distance, weather, biome, and ambient world fog.", this::atmosphericFog, this::setAtmosphericFog))
                .build());
    }

    @Override
    protected void loadSettings(JsonObject config) {
        lavaFog = NullTweaksConfig.getBoolean(config, "lavaFog", false);
        waterFog = NullTweaksConfig.getBoolean(config, "waterFog", false);
        powderSnowFog = NullTweaksConfig.getBoolean(config, "powderSnowFog", false);
        blindnessFog = NullTweaksConfig.getBoolean(config, "blindnessFog", false);
        darknessFog = NullTweaksConfig.getBoolean(config, "darknessFog", false);
        atmosphericFog = NullTweaksConfig.getBoolean(config, "atmosphericFog", false);
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("lavaFog", lavaFog);
        config.addProperty("waterFog", waterFog);
        config.addProperty("powderSnowFog", powderSnowFog);
        config.addProperty("blindnessFog", blindnessFog);
        config.addProperty("darknessFog", darknessFog);
        config.addProperty("atmosphericFog", atmosphericFog);
    }

    private Option<Boolean> booleanOption(String name, String descriptionText, java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(description(descriptionText))
                .binding(false, getter::getAsBoolean, setter)
                .controller(BooleanControllerBuilder::create)
                .instant(true)
                .build();
    }

    private boolean lavaFog() {
        return lavaFog;
    }

    private void setLavaFog(boolean value) {
        lavaFog = value;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean waterFog() {
        return waterFog;
    }

    private void setWaterFog(boolean value) {
        waterFog = value;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean powderSnowFog() {
        return powderSnowFog;
    }

    private void setPowderSnowFog(boolean value) {
        powderSnowFog = value;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean blindnessFog() {
        return blindnessFog;
    }

    private void setBlindnessFog(boolean value) {
        blindnessFog = value;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean darknessFog() {
        return darknessFog;
    }

    private void setDarknessFog(boolean value) {
        darknessFog = value;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean atmosphericFog() {
        return atmosphericFog;
    }

    private void setAtmosphericFog(boolean value) {
        atmosphericFog = value;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }
}
