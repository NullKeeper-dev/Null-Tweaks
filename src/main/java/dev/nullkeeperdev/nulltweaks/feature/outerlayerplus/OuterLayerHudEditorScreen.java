package dev.nullkeeperdev.nulltweaks.feature.outerlayerplus;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class OuterLayerHudEditorScreen extends Screen {
    private final Screen parent;
    private final OuterLayerPlusFeature feature;
    private boolean dragging;
    private boolean resizing;
    private double dragOffsetX;
    private double dragOffsetY;

    public OuterLayerHudEditorScreen(Screen parent, OuterLayerPlusFeature feature) {
        super(Component.literal("OuterLayer+ HUD Editor"));
        this.parent = parent;
        this.feature = feature;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 102, height - 28, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> feature.resetPanelPlacement())
                .bounds(width / 2 + 2, height - 28, 100, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        extractTransparentBackground(graphics);
        List<OuterLayerPlusFeature.TrackedPlayer> players = feature.collectTrackedPlayers();
        feature.renderPanel(graphics, players, feature.panelX(), feature.panelY(), feature.panelScale(), true);

        Bounds bounds = currentBounds(players);
        int handle = 8;
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0xFFFFFFFF);
        graphics.fill(bounds.x() + bounds.width() - handle, bounds.y() + bounds.height() - handle, bounds.x() + bounds.width(), bounds.y() + bounds.height(), 0xAAFFFFFF);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            Bounds bounds = currentBounds(feature.collectTrackedPlayers());
            if (inResizeHandle(event.x(), event.y(), bounds)) {
                resizing = true;
                return true;
            }
            if (inBounds(event.x(), event.y(), bounds)) {
                dragging = true;
                dragOffsetX = event.x() - feature.panelX();
                dragOffsetY = event.y() - feature.panelY();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        Bounds bounds = currentBounds(feature.collectTrackedPlayers());
        if (dragging) {
            int x = clamp((int) Math.round(event.x() - dragOffsetX), 0, Math.max(0, width - bounds.width()));
            int y = clamp((int) Math.round(event.y() - dragOffsetY), 0, Math.max(0, height - bounds.height()));
            feature.setPanelPosition(x, y);
            return true;
        }
        if (resizing) {
            OuterLayerPlusFeature.PanelBounds baseBounds = feature.panelBounds(feature.collectTrackedPlayers(), true);
            double widthScale = (event.x() - feature.panelX()) / Math.max(1.0D, baseBounds.width());
            double heightScale = (event.y() - feature.panelY()) / Math.max(1.0D, baseBounds.height());
            feature.setPanelScale(Math.max(widthScale, heightScale));
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        resizing = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 && shouldCloseOnEsc()) {
            onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private Bounds currentBounds(List<OuterLayerPlusFeature.TrackedPlayer> players) {
        OuterLayerPlusFeature.PanelBounds baseBounds = feature.panelBounds(players, true);
        int scaledWidth = Math.max(1, (int) Math.ceil(baseBounds.width() * feature.panelScale()));
        int scaledHeight = Math.max(1, (int) Math.ceil(baseBounds.height() * feature.panelScale()));
        return new Bounds(feature.panelX(), feature.panelY(), scaledWidth, scaledHeight);
    }

    private static boolean inBounds(double mouseX, double mouseY, Bounds bounds) {
        return mouseX >= bounds.x()
                && mouseY >= bounds.y()
                && mouseX <= bounds.x() + bounds.width()
                && mouseY <= bounds.y() + bounds.height();
    }

    private static boolean inResizeHandle(double mouseX, double mouseY, Bounds bounds) {
        return mouseX >= bounds.x() + bounds.width() - 10
                && mouseY >= bounds.y() + bounds.height() - 10
                && mouseX <= bounds.x() + bounds.width()
                && mouseY <= bounds.y() + bounds.height();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Bounds(int x, int y, int width, int height) {
    }
}
