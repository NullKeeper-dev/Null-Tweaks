package dev.nullkeeperdev.nulltweaks;

import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamFeature;
import dev.nullkeeperdev.nulltweaks.feature.nametags.NametagTweaksFeature;
import dev.nullkeeperdev.nulltweaks.feature.outerlayerplus.OuterLayerPlusFeature;
import dev.nullkeeperdev.nulltweaks.input.NullTweaksKeyMappings;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NullTweaksClient implements ClientModInitializer {
    public static final String MOD_ID = "nulltweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger("Null Tweaks");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Null Tweaks initializing");

        NullTweaksKeyMappings.register();
        NullTweaksConfig config = NullTweaksConfig.load();
        FeatureManager featureManager = FeatureManager.INSTANCE;
        featureManager.register(new OuterLayerPlusFeature());
        featureManager.register(new NametagTweaksFeature());
        featureManager.register(new FreecamFeature());
        featureManager.initialize(config);
        featureManager.registerHooks();
    }
}
