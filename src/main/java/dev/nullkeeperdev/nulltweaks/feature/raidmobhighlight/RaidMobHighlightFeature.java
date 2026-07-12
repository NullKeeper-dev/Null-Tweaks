package dev.nullkeeperdev.nulltweaks.feature.raidmobhighlight;

import com.google.gson.JsonObject;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;

import java.awt.Color;

public final class RaidMobHighlightFeature extends Feature {
    private static final Color DEFAULT_LEADER_COLOR = new Color(0xFFB000);
    private static final Color DEFAULT_OTHER_RAID_MOB_COLOR = new Color(0xAA44FF);
    private static RaidMobHighlightFeature instance;

    private boolean pillagerLeaderGlow = true;
    private Color pillagerLeaderColor = DEFAULT_LEADER_COLOR;
    private boolean otherRaidMobs = true;
    private Color otherRaidMobColor = DEFAULT_OTHER_RAID_MOB_COLOR;
    private ClientLevel cachedLevel;
    private ItemStack cachedOminousBanner = ItemStack.EMPTY;

    public RaidMobHighlightFeature() {
        super("raid_mob_highlight", "Raid Mob Highlight", true);
        instance = this;
    }

    public static Integer outlineColorFor(LivingEntity entity) {
        RaidMobHighlightFeature feature = instance;
        if (feature == null || !feature.isEnabled()) {
            return null;
        }

        if (feature.pillagerLeaderGlow && feature.isPillagerLeader(entity)) {
            return ARGB.opaque(feature.pillagerLeaderColor.getRGB() & 0xFFFFFF);
        }

        if (feature.otherRaidMobs && feature.isOtherRaidMob(entity)) {
            return ARGB.opaque(feature.otherRaidMobColor.getRGB() & 0xFFFFFF);
        }

        return null;
    }

    public static boolean shouldAppearGlowing(Entity entity) {
        RaidMobHighlightFeature feature = instance;
        return feature != null
                && feature.isEnabled()
                && entity instanceof LivingEntity livingEntity
                && ((feature.pillagerLeaderGlow && feature.isPillagerLeader(livingEntity))
                || (feature.otherRaidMobs && feature.isOtherRaidMob(livingEntity)));
    }

    @Override
    public void buildConfig(OptionGroup.Builder builder) {
        builder.option(pillagerLeaderToggleOption())
                .option(pillagerLeaderColorOption())
                .option(otherRaidMobsToggleOption())
                .option(otherRaidMobColorOption());
    }

    @Override
    protected void loadSettings(JsonObject config) {
        pillagerLeaderGlow = NullTweaksConfig.getBoolean(config, "pillagerLeaderGlow", true);
        pillagerLeaderColor = parseColor(NullTweaksConfig.getString(config, "pillagerLeaderColor", colorString(DEFAULT_LEADER_COLOR)), DEFAULT_LEADER_COLOR);
        otherRaidMobs = NullTweaksConfig.getBoolean(config, "otherRaidMobs", true);
        otherRaidMobColor = parseColor(NullTweaksConfig.getString(config, "otherRaidMobColor", colorString(DEFAULT_OTHER_RAID_MOB_COLOR)), DEFAULT_OTHER_RAID_MOB_COLOR);
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("pillagerLeaderGlow", pillagerLeaderGlow);
        config.addProperty("pillagerLeaderColor", colorString(pillagerLeaderColor));
        config.addProperty("otherRaidMobs", otherRaidMobs);
        config.addProperty("otherRaidMobColor", colorString(otherRaidMobColor));
    }

    private boolean isPillagerLeader(LivingEntity entity) {
        if (!(entity instanceof Pillager)) {
            return false;
        }

        ItemStack headItem = entity.getItemBySlot(EquipmentSlot.HEAD);
        return !headItem.isEmpty() && ItemStack.isSameItemSameComponents(headItem, ominousBanner());
    }

    private boolean isOtherRaidMob(LivingEntity entity) {
        if (entity instanceof Ravager || entity instanceof Vindicator || entity instanceof Evoker || entity instanceof Vex) {
            return true;
        }

        return entity instanceof Pillager && !isPillagerLeader(entity);
    }

    private ItemStack ominousBanner() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return ItemStack.EMPTY;
        }

        if (cachedLevel != client.level || cachedOminousBanner.isEmpty()) {
            cachedLevel = client.level;
            HolderGetter<BannerPattern> bannerPatterns = client.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
            cachedOminousBanner = Raid.getOminousBannerInstance(bannerPatterns);
        }

        return cachedOminousBanner;
    }

    private Option<Boolean> pillagerLeaderToggleOption() {
        return booleanOption("Pillager Leader Glow", "Highlights Pillagers wearing an Ominous Banner.", true, this::pillagerLeaderGlow, this::setPillagerLeaderGlow);
    }

    private Option<Color> pillagerLeaderColorOption() {
        return colorOption("Pillager leader color", "Color for banner-carrying Pillagers.", DEFAULT_LEADER_COLOR, this::pillagerLeaderColor, this::setPillagerLeaderColor);
    }

    private Option<Boolean> otherRaidMobsToggleOption() {
        return booleanOption("Other Raid Mobs", "Highlights regular Pillagers, Ravagers, Vindicators, Evokers, and Vex.", true, this::otherRaidMobs, this::setOtherRaidMobs);
    }

    private Option<Color> otherRaidMobColorOption() {
        return colorOption("Other raid mob color", "Shared color for non-leader raid mobs.", DEFAULT_OTHER_RAID_MOB_COLOR, this::otherRaidMobColor, this::setOtherRaidMobColor);
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

    private boolean pillagerLeaderGlow() {
        return pillagerLeaderGlow;
    }

    private void setPillagerLeaderGlow(boolean enabled) {
        pillagerLeaderGlow = enabled;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Color pillagerLeaderColor() {
        return pillagerLeaderColor;
    }

    private void setPillagerLeaderColor(Color color) {
        pillagerLeaderColor = withoutAlpha(color);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean otherRaidMobs() {
        return otherRaidMobs;
    }

    private void setOtherRaidMobs(boolean enabled) {
        otherRaidMobs = enabled;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Color otherRaidMobColor() {
        return otherRaidMobColor;
    }

    private void setOtherRaidMobColor(Color color) {
        otherRaidMobColor = withoutAlpha(color);
        FeatureManager.INSTANCE.saveFeature(this);
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
}
