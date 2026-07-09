package dev.nullkeeperdev.nulltweaks.config.screen;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NullTweaksScreenBuilder {
    private static final String TITLE = "Null Tweaks";
    private static String lastCategoryName;

    private NullTweaksScreenBuilder() {
    }

    public static Screen build(Screen parent) {
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(Component.literal(TITLE))
                .save(FeatureManager.INSTANCE::saveAll)
                .screenInit(NullTweaksScreenBuilder::restoreLastCategory);

        for (Feature feature : FeatureManager.INSTANCE.getAll()) {
            ConfigCategory.Builder category = ConfigCategory.createBuilder()
                    .name(Component.literal(feature.displayName()));
            category.option(masterToggle(feature));
            feature.buildConfig(category);
            builder.category(category.build());
        }

        return builder.build().generateScreen(parent);
    }

    public static boolean isNullTweaksScreen(YACLScreen screen) {
        return screen != null && TITLE.equals(screen.config.title().getString());
    }

    public static void rememberCategory(Component categoryTitle) {
        lastCategoryName = categoryTitle == null ? null : categoryTitle.getString();
    }

    private static Option<Boolean> masterToggle(Feature feature) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal("Enabled"))
                .description(OptionDescription.of(Component.literal("Master switch for this feature. Turning it off stops its tick, HUD, and render hooks.")))
                .binding(feature.defaultEnabled(), feature::isEnabled, enabled -> FeatureManager.INSTANCE.setEnabled(feature.id(), enabled))
                .controller(BooleanControllerBuilder::create)
                .instant(true)
                .build();
    }

    private static void restoreLastCategory(YACLScreen screen) {
        if (lastCategoryName == null || screen.tabNavigationBar == null) {
            return;
        }

        for (Tab tab : screen.tabNavigationBar.getTabs()) {
            if (lastCategoryName.equals(tab.getTabTitle().getString())) {
                screen.tabManager.setCurrentTab(tab, false);
                return;
            }
        }
    }
}
