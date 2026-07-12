package dev.nullkeeperdev.nulltweaks.feature.outerlayerplus;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import dev.nullkeeperdev.nulltweaks.input.NullTweaksKeyMappings;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

public final class OuterLayerPlusFeature extends Feature {
    private static final KeyMapping OVERLAY_TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.nulltweaks.outerlayerplus.toggle_overlay",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            NullTweaksKeyMappings.CATEGORY));

    private static OuterLayerPlusFeature instance;

    private boolean overlayEnabled = true;
    private boolean distanceRingEnabled = true;
    private boolean nameRecolorEnabled;
    private int redThreshold = 30;
    private int yellowThreshold = 50;
    private Color redColor = new Color(0xFF0000);
    private Color yellowColor = new Color(0xFFD700);
    private Color greenColor = new Color(0x00FF00);
    private int panelX = 8;
    private int panelY = 8;
    private double panelScale = 1.0D;
    private int panelBackgroundOpacity = 75;

    public OuterLayerPlusFeature() {
        super("outer_layer_plus", "OuterLayer+", true);
        instance = this;
    }

    public static OuterLayerPlusFeature instance() {
        return instance;
    }

    @Override
    public boolean listensForClientTicks() {
        return true;
    }

    @Override
    public void onClientTick(Minecraft client) {
        while (OVERLAY_TOGGLE_KEY.consumeClick()) {
            setOverlayEnabled(!overlayEnabled);
        }
    }

    @Override
    public boolean listensForHudRender() {
        return true;
    }

    @Override
    public void onHudRender(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        if (!overlayEnabled) {
            return;
        }

        List<TrackedPlayer> players = collectTrackedPlayers();
        if (players.isEmpty()) {
            return;
        }

        renderPanel(graphics, players, panelX, panelY, panelScale);
    }

    @Override
    public void buildConfig(OptionGroup.Builder builder) {
        builder.option(sectionLabel("General"))
                .option(keybindButton())
                .option(sectionLabel("Overlay"))
                .option(booleanOption("Overlay panel", "Shows the draggable HUD list of currently loaded nearby players.", true, this::overlayEnabled, this::setOverlayEnabled))
                .option(percentageFieldOption("Background opacity", "Controls how dark the overlay panel background is.", 75, this::panelBackgroundOpacity, this::setPanelBackgroundOpacity))
                .option(ButtonOption.createBuilder()
                        .name(Component.literal("HUD position editor"))
                        .description(description("Opens the drag editor for the Overlay panel position and scale."))
                        .text(Component.literal("Open"))
                        .action(screen -> Minecraft.getInstance().setScreenAndShow(new OuterLayerHudEditorScreen(screen, this)))
                        .build())
                .option(sectionLabel("Tab List"))
                .option(booleanOption("Distance Ring", "Draws a colored outline around each player head in the vanilla tab list.", true, this::distanceRingEnabled, this::setDistanceRingEnabled))
                .option(booleanOption("Name Recolor", "Recolors tab-list player names by their distance tier.", false, this::nameRecolorEnabled, this::setNameRecolorEnabled))
                .option(sectionLabel("Distance"))
                .option(thresholdOption("Red threshold", "Players at or inside this X/Z distance use the red tier.", 30, this::redThreshold, this::setRedThreshold))
                .option(thresholdOption("Yellow threshold", "Players farther than red and at or inside this X/Z distance use the yellow tier.", 50, this::yellowThreshold, this::setYellowThreshold))
                .option(sectionLabel("Color Wheel"))
                .option(colorOption("Red tier", "Color used for players inside the red threshold.", new Color(0xFF0000), this::redColor, this::setRedColor))
                .option(colorOption("Yellow tier", "Color used for players between the red and yellow thresholds.", new Color(0xFFD700), this::yellowColor, this::setYellowColor))
                .option(colorOption("Green tier", "Color used for loaded players beyond the yellow threshold.", new Color(0x00FF00), this::greenColor, this::setGreenColor));
    }

    public boolean isDistanceRingActive() {
        return isEnabled() && distanceRingEnabled;
    }

    public boolean isNameRecolorActive() {
        return isEnabled() && nameRecolorEnabled;
    }

    public OptionalInt tabColor(PlayerInfo info) {
        if (!isEnabled()) {
            return OptionalInt.empty();
        }

        AbstractClientPlayer player = playerFor(info.getProfile().id());
        if (player == null) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(tierFor(player).rgb());
    }

    public OptionalInt tabRingColor(PlayerInfo info) {
        if (!isEnabled()) {
            return OptionalInt.empty();
        }

        AbstractClientPlayer player = playerFor(info.getProfile().id());
        if (player == null) {
            return OptionalInt.empty();
        }

        if (!distanceRingEnabled) {
            return OptionalInt.of(0xFFFFFFFF);
        }

        return OptionalInt.of(ARGB.opaque(tierFor(player).rgb()));
    }

    public List<TrackedPlayer> collectTrackedPlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return List.of();
        }

        List<TrackedPlayer> players = new ArrayList<>();
        for (AbstractClientPlayer player : client.level.players()) {
            if (player == client.player || player.getUUID().equals(client.player.getUUID())) {
                continue;
            }

            double distance = xzDistance(client.player, player);
            players.add(new TrackedPlayer(player, player.getGameProfile().name(), distance, tierFor(distance)));
        }

        players.sort(Comparator.comparingDouble(TrackedPlayer::distance));
        return players;
    }

    public PanelBounds panelBounds(List<TrackedPlayer> players, boolean editorPreview) {
        List<TrackedPlayer> visiblePlayers = players;
        if (visiblePlayers.isEmpty() && editorPreview) {
            visiblePlayers = List.of(
                    TrackedPlayer.preview("NearbyPlayer", 18.0D, Tier.RED),
                    TrackedPlayer.preview("DistantPlayer", 44.0D, Tier.YELLOW));
        }

        if (visiblePlayers.isEmpty()) {
            return new PanelBounds(0, 0);
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        int width = 96;
        for (TrackedPlayer player : visiblePlayers) {
            width = Math.max(width, 4 + 12 + 5 + font.width(player.name()) + 4);
        }

        int height = 8 + visiblePlayers.size() * 16;
        return new PanelBounds(width, height);
    }

    public void renderPanel(GuiGraphicsExtractor graphics, List<TrackedPlayer> players, int x, int y, double scale) {
        renderPanel(graphics, players, x, y, scale, false);
    }

    public void renderPanel(GuiGraphicsExtractor graphics, List<TrackedPlayer> players, int x, int y, double scale, boolean editorPreview) {
        List<TrackedPlayer> visiblePlayers = players;
        if (visiblePlayers.isEmpty() && editorPreview) {
            visiblePlayers = List.of(
                    TrackedPlayer.preview("NearbyPlayer", 18.0D, Tier.RED),
                    TrackedPlayer.preview("DistantPlayer", 44.0D, Tier.YELLOW));
        }

        if (visiblePlayers.isEmpty()) {
            return;
        }

        PanelBounds bounds = panelBounds(visiblePlayers, editorPreview);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale((float) scale);
        fillRounded(graphics, 0, 0, bounds.width(), bounds.height(), 3, panelBackgroundColor());

        Minecraft client = Minecraft.getInstance();
        int rowY = 4;
        for (TrackedPlayer player : visiblePlayers) {
            int iconX = 4;
            int iconY = rowY + 2;
            int tierColor = ARGB.opaque(player.tier().rgb());
            if (player.player() != null) {
                PlayerFaceExtractor.extractRenderState(
                        graphics,
                        player.player().getSkin().body().texturePath(),
                        iconX,
                        iconY,
                        12,
                        true,
                        false,
                        -1);
            } else {
                graphics.fill(iconX, iconY, iconX + 12, iconY + 12, 0xFF555555);
            }

            if (isDistanceRingActive()) {
                graphics.outline(iconX - 1, iconY - 1, 14, 14, tierColor);
            }

            graphics.text(client.font, player.name(), 21, rowY + 4, 0xFFFFFFFF);
            rowY += 16;
        }
        pose.popMatrix();
    }

    public int panelX() {
        return panelX;
    }

    public int panelY() {
        return panelY;
    }

    public double panelScale() {
        return panelScale;
    }

    public void setPanelPosition(int x, int y) {
        panelX = Math.max(0, x);
        panelY = Math.max(0, y);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    public void setPanelScale(double scale) {
        panelScale = clampDouble(scale, 0.5D, 3.0D);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    public void resetPanelPlacement() {
        panelX = 8;
        panelY = 8;
        panelScale = 1.0D;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    @Override
    protected void loadSettings(JsonObject config) {
        overlayEnabled = NullTweaksConfig.getBoolean(config, "overlayEnabled", true);
        distanceRingEnabled = NullTweaksConfig.getBoolean(config, "distanceRingEnabled", true);
        nameRecolorEnabled = NullTweaksConfig.getBoolean(config, "nameRecolorEnabled", false);
        redThreshold = clampInt(NullTweaksConfig.getInt(config, "redThreshold", 30), 1, 512);
        yellowThreshold = clampInt(NullTweaksConfig.getInt(config, "yellowThreshold", 50), 1, 512);
        if (yellowThreshold < redThreshold) {
            yellowThreshold = redThreshold;
        }
        redColor = parseColor(NullTweaksConfig.getString(config, "redColor", "#ff0000"), new Color(0xFF0000));
        yellowColor = parseColor(NullTweaksConfig.getString(config, "yellowColor", "#ffd700"), new Color(0xFFD700));
        greenColor = parseColor(NullTweaksConfig.getString(config, "greenColor", "#00ff00"), new Color(0x00FF00));
        panelX = Math.max(0, NullTweaksConfig.getInt(config, "panelX", 8));
        panelY = Math.max(0, NullTweaksConfig.getInt(config, "panelY", 8));
        panelScale = clampDouble(NullTweaksConfig.getDouble(config, "panelScale", 1.0D), 0.5D, 3.0D);
        panelBackgroundOpacity = clampInt(NullTweaksConfig.getInt(config, "panelBackgroundOpacity", 75), 0, 100);
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("overlayEnabled", overlayEnabled);
        config.addProperty("distanceRingEnabled", distanceRingEnabled);
        config.addProperty("nameRecolorEnabled", nameRecolorEnabled);
        config.addProperty("redThreshold", redThreshold);
        config.addProperty("yellowThreshold", yellowThreshold);
        config.addProperty("redColor", colorString(redColor));
        config.addProperty("yellowColor", colorString(yellowColor));
        config.addProperty("greenColor", colorString(greenColor));
        config.addProperty("panelX", panelX);
        config.addProperty("panelY", panelY);
        config.addProperty("panelScale", panelScale);
        config.addProperty("panelBackgroundOpacity", panelBackgroundOpacity);
    }

    private ButtonOption keybindButton() {
        return ButtonOption.createBuilder()
                .name(Component.literal("Overlay toggle keybind"))
                .description(description("Opens Minecraft's keybind screen so you can bind the Overlay panel toggle."))
                .text(OVERLAY_TOGGLE_KEY.getTranslatedKeyMessage())
                .action(screen -> Minecraft.getInstance().setScreenAndShow(new KeyBindsScreen(screen, Minecraft.getInstance().options)))
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

    private Option<Integer> thresholdOption(String name, String descriptionText, int fallback, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        return Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(description(descriptionText))
                .binding(fallback, getter::getAsInt, value -> setter.accept(value))
                .controller(option -> IntegerSliderControllerBuilder.create(option)
                        .range(1, 256)
                        .step(1)
                        .valueFormatter(value -> Component.literal(value + " blocks")))
                .instant(true)
                .build();
    }

    private Option<Integer> percentageFieldOption(String name, String descriptionText, int fallback, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        return Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(description(descriptionText))
                .binding(fallback, getter::getAsInt, value -> setter.accept(value))
                .controller(option -> IntegerFieldControllerBuilder.create(option)
                        .range(0, 100)
                        .valueFormatter(value -> Component.literal(value + "%")))
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

    private static LabelOption sectionLabel(String name) {
        return LabelOption.create(Component.literal(name));
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private boolean overlayEnabled() {
        return overlayEnabled;
    }

    private void setOverlayEnabled(boolean enabled) {
        overlayEnabled = enabled;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean distanceRingEnabled() {
        return distanceRingEnabled;
    }

    private void setDistanceRingEnabled(boolean enabled) {
        distanceRingEnabled = enabled;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private boolean nameRecolorEnabled() {
        return nameRecolorEnabled;
    }

    private void setNameRecolorEnabled(boolean enabled) {
        nameRecolorEnabled = enabled;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private int panelBackgroundOpacity() {
        return panelBackgroundOpacity;
    }

    private void setPanelBackgroundOpacity(int value) {
        panelBackgroundOpacity = clampInt(value, 0, 100);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private int redThreshold() {
        return redThreshold;
    }

    private void setRedThreshold(int value) {
        redThreshold = clampInt(value, 1, 256);
        if (yellowThreshold < redThreshold) {
            yellowThreshold = redThreshold;
        }
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private int yellowThreshold() {
        return yellowThreshold;
    }

    private void setYellowThreshold(int value) {
        yellowThreshold = clampInt(value, 1, 256);
        if (redThreshold > yellowThreshold) {
            redThreshold = yellowThreshold;
        }
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Color redColor() {
        return redColor;
    }

    private void setRedColor(Color color) {
        redColor = withoutAlpha(color);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Color yellowColor() {
        return yellowColor;
    }

    private void setYellowColor(Color color) {
        yellowColor = withoutAlpha(color);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Color greenColor() {
        return greenColor;
    }

    private void setGreenColor(Color color) {
        greenColor = withoutAlpha(color);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private Tier tierFor(AbstractClientPlayer player) {
        return tierFor(xzDistance(Minecraft.getInstance().player, player));
    }

    private Tier tierFor(double distance) {
        if (distance <= redThreshold) {
            return Tier.RED;
        }
        if (distance <= yellowThreshold) {
            return Tier.YELLOW;
        }
        return Tier.GREEN;
    }

    private AbstractClientPlayer playerFor(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return null;
        }

        if (client.player.getUUID().equals(uuid)) {
            return null;
        }

        var player = client.level.getPlayerByUUID(uuid);
        if (player instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer;
        }

        return null;
    }

    private int rgb(Tier tier) {
        return switch (tier) {
            case RED -> redColor.getRGB() & 0xFFFFFF;
            case YELLOW -> yellowColor.getRGB() & 0xFFFFFF;
            case GREEN -> greenColor.getRGB() & 0xFFFFFF;
        };
    }

    private static double xzDistance(AbstractClientPlayer localPlayer, AbstractClientPlayer candidate) {
        if (localPlayer == null) {
            return Double.MAX_VALUE;
        }

        double dx = candidate.getX() - localPlayer.getX();
        double dz = candidate.getZ() - localPlayer.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        graphics.fill(x + radius, y, x + width - radius, y + height, color);
        graphics.fill(x, y + radius, x + width, y + height - radius, color);
        graphics.fill(x + 1, y + 1, x + radius, y + radius, color);
        graphics.fill(x + width - radius, y + 1, x + width - 1, y + radius, color);
        graphics.fill(x + 1, y + height - radius, x + radius, y + height - 1, color);
        graphics.fill(x + width - radius, y + height - radius, x + width - 1, y + height - 1, color);
    }

    private int panelBackgroundColor() {
        int alpha = Math.round(panelBackgroundOpacity * 255.0F / 100.0F);
        return (alpha << 24) | 0x000000;
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

    public enum Tier {
        RED,
        YELLOW,
        GREEN;

        public int rgb() {
            OuterLayerPlusFeature feature = OuterLayerPlusFeature.instance();
            if (feature == null) {
                return 0xFFFFFF;
            }
            return feature.rgb(this);
        }
    }

    public record TrackedPlayer(AbstractClientPlayer player, String name, double distance, Tier tier) {
        static TrackedPlayer preview(String name, double distance, Tier tier) {
            return new TrackedPlayer(null, name, distance, tier);
        }
    }

    public record PanelBounds(int width, int height) {
    }
}
