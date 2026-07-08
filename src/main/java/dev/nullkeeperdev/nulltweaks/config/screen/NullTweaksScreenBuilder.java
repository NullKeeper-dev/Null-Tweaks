package dev.nullkeeperdev.nulltweaks.config.screen;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NullTweaksScreenBuilder {
    private NullTweaksScreenBuilder() {
    }

    public static Screen build(Screen parent) {
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Null Tweaks"))
                .save(FeatureManager.INSTANCE::saveAll);

        for (Feature feature : FeatureManager.INSTANCE.getAll()) {
            ConfigCategory.Builder category = ConfigCategory.createBuilder()
                    .name(Component.literal(feature.displayName()));
            category.option(masterToggle(feature));
            feature.buildConfig(category);
            builder.category(category.build());
        }

        return builder.build().generateScreen(parent);
    }

    private static Option<Boolean> masterToggle(Feature feature) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Enabled"))
                .binding(feature.defaultEnabled(), feature::isEnabled, enabled -> FeatureManager.INSTANCE.setEnabled(feature.id(), enabled))
                .controller(BooleanControllerBuilder::create)
                .instant(true)
                .build();
    }
}
