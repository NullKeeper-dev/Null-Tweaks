package dev.nullkeeperdev.nulltweaks.feature.quarry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.util.List;

final class MissingBaritoneWarningScreen extends Screen {
    private static final URI BARITONE_REPO = URI.create("https://github.com/NullKeeper-dev/Null-Baritone");
    private static final Component TITLE = Component.literal("Quarry needs Null-Baritone");
    private static final FormattedText MESSAGE = Component.literal(
            "Quarry cannot be used because a compatible Baritone install was not found. Install Null-Baritone beside Null Tweaks, then reload the game.");

    private final Runnable dismissWarning;

    MissingBaritoneWarningScreen(Runnable dismissWarning) {
        super(TITLE);
        this.dismissWarning = dismissWarning;
    }

    @Override
    protected void init() {
        int buttonY = height / 2 + 46;
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(width / 2 - 154, buttonY, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Download"), button -> ConfirmLinkScreen.confirmLinkNow(this, BARITONE_REPO, true))
                .bounds(width / 2 - 48, buttonY, 96, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Don't show again"), button -> {
                    dismissWarning.run();
                    onClose();
                })
                .bounds(width / 2 + 58, buttonY, 116, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        extractTransparentBackground(graphics);
        int panelWidth = Math.min(380, width - 32);
        int panelX = (width - panelWidth) / 2;
        List<FormattedCharSequence> lines = font.split(MESSAGE, panelWidth - 32);
        int panelHeight = 96 + lines.size() * font.lineHeight;
        int panelY = Math.max(20, (height - panelHeight) / 2);

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE6101010);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xFF5C5C5C);
        graphics.centeredText(font, title, width / 2, panelY + 16, 0xFFFFFFFF);

        int textY = panelY + 40;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, panelX + 16, textY, 0xFFE0E0E0);
            textY += font.lineHeight;
        }

        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }
}
