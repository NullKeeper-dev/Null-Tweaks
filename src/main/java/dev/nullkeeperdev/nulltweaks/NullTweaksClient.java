package dev.nullkeeperdev.nulltweaks;

import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import dev.nullkeeperdev.nulltweaks.feature.autoclicker.AutoclickerFeature;
import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamFeature;
import dev.nullkeeperdev.nulltweaks.feature.librarianscanner.LibrarianTradeScannerFeature;
import dev.nullkeeperdev.nulltweaks.feature.maxenchant.MaxEnchantIndicatorFeature;
import dev.nullkeeperdev.nulltweaks.feature.nametags.NametagTweaksFeature;
import dev.nullkeeperdev.nulltweaks.feature.nobobber.NoBobberFeature;
import dev.nullkeeperdev.nulltweaks.feature.nofog.NoFogFeature;
import dev.nullkeeperdev.nulltweaks.feature.outerlayerplus.OuterLayerPlusFeature;
import dev.nullkeeperdev.nulltweaks.feature.raidmobhighlight.RaidMobHighlightFeature;
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
        LibrarianTradeScannerFeature.registerCommands();
        NullTweaksConfig config = NullTweaksConfig.load();
        FeatureManager featureManager = FeatureManager.INSTANCE;
        featureManager.register(new OuterLayerPlusFeature());
        featureManager.register(new NametagTweaksFeature());
        featureManager.register(new FreecamFeature());
        featureManager.register(new NoBobberFeature());
        featureManager.register(new NoFogFeature());
        featureManager.register(new AutoclickerFeature());
        featureManager.register(new RaidMobHighlightFeature());
        featureManager.register(new LibrarianTradeScannerFeature());
        featureManager.register(new MaxEnchantIndicatorFeature());
        featureManager.initialize(config);
        featureManager.registerHooks();
    }
}
