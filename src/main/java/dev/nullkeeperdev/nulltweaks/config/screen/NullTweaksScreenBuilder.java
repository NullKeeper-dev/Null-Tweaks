package dev.nullkeeperdev.nulltweaks.config.screen;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
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
    private static final String[] VISUALS = {
            "max_enchant_indicator",
            "raid_mob_highlight",
            "nametag_tweaks",
            "no_fog",
            "no_fishing_bobber"
    };
    private static final String[] WORLD_INFO = {
            "outer_layer_plus",
            "librarian_trade_scanner"
    };
    private static final String[] MOVEMENT_AND_AUTOMATION = {
            "freecam",
            "autoclicker",
            "quarry"
    };
    private static String lastCategoryName;

    private NullTweaksScreenBuilder() {
    }

    public static Screen build(Screen parent) {
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(Component.literal(TITLE))
                .save(FeatureManager.INSTANCE::saveAll)
                .screenInit(NullTweaksScreenBuilder::restoreLastCategory);

        builder.category(groupedCategory("Visuals", VISUALS));
        builder.category(groupedCategory("World Info", WORLD_INFO));
        builder.category(groupedCategory("Movement & Automation", MOVEMENT_AND_AUTOMATION));

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

    private static ConfigCategory groupedCategory(String name, String[] featureIds) {
        ConfigCategory.Builder category = ConfigCategory.createBuilder()
                .name(Component.literal(name));

        for (String featureId : featureIds) {
            FeatureManager.INSTANCE.getById(featureId).ifPresent(feature -> {
                OptionGroup.Builder group = OptionGroup.createBuilder()
                        .name(Component.literal(feature.displayName()))
                        .description(OptionDescription.of(Component.literal(featureDescription(feature))))
                        .collapsed(true)
                        .option(masterToggle(feature));
                feature.buildConfig(group);
                category.group(group.build());
            });
        }

        return category.build();
    }

    private static String featureDescription(Feature feature) {
        return switch (feature.id()) {
            case "max_enchant_indicator" -> "Highlights enchantments that are at their maximum level with a custom color in tooltips, librarian trade labels, and the enchanting table.";
            case "raid_mob_highlight" -> "Glows raid-related mobs through walls so they are easier to find, including a distinct color for the banner-carrying Pillager leader.";
            case "nametag_tweaks" -> "Customize player nametag scale, background opacity, text color, and boldness, with optional zoom compensation.";
            case "no_fog" -> "Selectively disable specific types of fog rendering, including lava, water, powder snow, blindness, darkness, and atmospheric fog.";
            case "no_fishing_bobber" -> "Hides your own fishing bobber without affecting fishing mechanics.";
            case "outer_layer_plus" -> "Shows nearby players with distance-based color coding in a custom overlay and directly in the vanilla tab list.";
            case "librarian_trade_scanner" -> "Automatically discovers and displays librarians' enchanted book trades above their heads without opening their trading menu.";
            case "freecam" -> "Detach your camera to fly around and scout freely without moving your actual character.";
            case "autoclicker" -> "Automatically clicks at a fixed interval, useful for AFK grinding.";
            case "quarry" -> "Defines a boxed mining region and automates top-down block breaking through optional Baritone integration.";
            default -> feature.displayName();
        };
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
