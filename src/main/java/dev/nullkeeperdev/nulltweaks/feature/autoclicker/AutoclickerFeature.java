package dev.nullkeeperdev.nulltweaks.feature.autoclicker;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.nullkeeperdev.nulltweaks.config.NullTweaksConfig;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import dev.nullkeeperdev.nulltweaks.feature.FeatureManager;
import dev.nullkeeperdev.nulltweaks.input.NullTweaksKeyMappings;
import dev.nullkeeperdev.nulltweaks.mixin.MinecraftAutoclickerAccessor;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class AutoclickerFeature extends Feature {
    private static final int MIN_INTERVAL_MS = 50;
    private static final int DEFAULT_INTERVAL_MS = 1000;
    private static final KeyMapping TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.nulltweaks.autoclicker.toggle",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            NullTweaksKeyMappings.CATEGORY));

    private int intervalMs = DEFAULT_INTERVAL_MS;
    private ClickType clickType = ClickType.LEFT;
    private boolean active;
    private long nextClickNanos;

    public AutoclickerFeature() {
        super("autoclicker", "Autoclicker");
    }

    @Override
    public void onDisable() {
        deactivate();
    }

    @Override
    public boolean listensForClientTicks() {
        return true;
    }

    @Override
    public void onClientTick(Minecraft client) {
        while (TOGGLE_KEY.consumeClick()) {
            if (active) {
                deactivate();
            } else {
                activate();
            }
        }

        if (!active || !canClick(client)) {
            return;
        }

        long now = System.nanoTime();
        if (now < nextClickNanos) {
            return;
        }

        performClick(client);
        long intervalNanos = intervalMs * 1_000_000L;
        nextClickNanos += intervalNanos;
        if (nextClickNanos <= now) {
            nextClickNanos = now + intervalNanos;
        }
    }

    @Override
    public boolean listensForHudRender() {
        return true;
    }

    @Override
    public void onHudRender(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        if (!active) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        String text = "Autoclicker: ON";
        int width = font.width(text) + 16;
        int x = client.getWindow().getGuiScaledWidth() / 2 - width / 2;
        int y = 8;
        fillRounded(graphics, x, y, width, 20, 3, 0xAA000000);
        graphics.text(font, text, x + 8, y + 6, 0xFF72F28A);
    }

    @Override
    public void buildConfig(ConfigCategory.Builder builder) {
        builder.group(OptionGroup.createBuilder()
                .name(Component.literal("Autoclicker"))
                .collapsed(false)
                .option(intervalOption())
                .option(clickTypeOption())
                .option(keybindButton())
                .build());
    }

    @Override
    protected void loadSettings(JsonObject config) {
        intervalMs = Math.max(MIN_INTERVAL_MS, NullTweaksConfig.getInt(config, "intervalMs", DEFAULT_INTERVAL_MS));
        clickType = ClickType.fromConfig(NullTweaksConfig.getString(config, "clickType", ClickType.LEFT.configValue()));
        active = false;
        nextClickNanos = 0L;
    }

    @Override
    protected void saveSettings(JsonObject config) {
        config.addProperty("intervalMs", intervalMs);
        config.addProperty("clickType", clickType.configValue());
    }

    private void activate() {
        active = true;
        nextClickNanos = System.nanoTime();
    }

    private void deactivate() {
        active = false;
        nextClickNanos = 0L;
    }

    private void performClick(Minecraft client) {
        MinecraftAutoclickerAccessor accessor = (MinecraftAutoclickerAccessor) client;
        if (clickType == ClickType.LEFT) {
            accessor.nulltweaks$setMissTime(0);
            accessor.nulltweaks$startAttack();
            return;
        }

        accessor.nulltweaks$startUseItem();
    }

    private static boolean canClick(Minecraft client) {
        return client != null
                && client.level != null
                && client.player != null
                && client.gameMode != null
                && !hasOpenScreenOrOverlay(client);
    }

    private static boolean hasOpenScreenOrOverlay(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen() != null || client.gui.overlay() != null;
        //?} else {
        /*return client.screen != null || client.getOverlay() != null;
        *///?}
    }

    private Option<Integer> intervalOption() {
        return Option.<Integer>createBuilder()
                .name(Component.literal("Interval"))
                .description(description("Fixed delay between simulated clicks, in milliseconds. Values below 50 are clamped."))
                .binding(DEFAULT_INTERVAL_MS, this::intervalMs, this::setIntervalMs)
                .controller(option -> IntegerFieldControllerBuilder.create(option)
                        .min(MIN_INTERVAL_MS)
                        .valueFormatter(value -> Component.literal(value + " ms")))
                .instant(true)
                .build();
    }

    private Option<ClickType> clickTypeOption() {
        return Option.<ClickType>createBuilder()
                .name(Component.literal("Click type"))
                .description(description("Chooses which mouse button is simulated while the toggle is active."))
                .binding(ClickType.LEFT, this::clickType, this::setClickType)
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(ClickType.class)
                        .valueFormatter(value -> Component.literal(value.displayName())))
                .instant(true)
                .build();
    }

    private ButtonOption keybindButton() {
        return ButtonOption.createBuilder()
                .name(Component.literal("Toggle keybind"))
                .description(description("Opens Minecraft's keybind screen so you can bind the Autoclicker on/off toggle."))
                .text(TOGGLE_KEY.getTranslatedKeyMessage())
                .action(screen -> Minecraft.getInstance().setScreenAndShow(new KeyBindsScreen(screen, Minecraft.getInstance().options)))
                .build();
    }

    private int intervalMs() {
        return intervalMs;
    }

    private void setIntervalMs(int value) {
        intervalMs = Math.max(MIN_INTERVAL_MS, value);
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private ClickType clickType() {
        return clickType;
    }

    private void setClickType(ClickType value) {
        clickType = value == null ? ClickType.LEFT : value;
        FeatureManager.INSTANCE.saveFeature(this);
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    private static void fillRounded(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        graphics.fill(x + radius, y, x + width - radius, y + height, color);
        graphics.fill(x, y + radius, x + width, y + height - radius, color);
        graphics.fill(x + 1, y + 1, x + radius, y + radius, color);
        graphics.fill(x + width - radius, y + 1, x + width - 1, y + radius, color);
        graphics.fill(x + 1, y + height - radius, x + radius, y + height - 1, color);
        graphics.fill(x + width - radius, y + height - radius, x + width - 1, y + height - 1, color);
    }

    public enum ClickType {
        LEFT("Left Click"),
        RIGHT("Right Click");

        private final String displayName;

        ClickType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        public String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        static ClickType fromConfig(String value) {
            for (ClickType clickType : values()) {
                if (clickType.configValue().equalsIgnoreCase(value)) {
                    return clickType;
                }
            }

            return LEFT;
        }
    }
}
