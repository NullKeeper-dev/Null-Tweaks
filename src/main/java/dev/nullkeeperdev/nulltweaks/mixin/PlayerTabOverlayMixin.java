package dev.nullkeeperdev.nulltweaks.mixin;

import com.mojang.authlib.GameProfile;
import dev.nullkeeperdev.nulltweaks.feature.outerlayerplus.OuterLayerPlusFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.OptionalInt;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private Component header;

    @Shadow
    private List<PlayerInfo> getPlayerInfos() {
        throw new AssertionError();
    }

    @Shadow
    public abstract Component getNameForDisplay(PlayerInfo info);

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void nulltweaks$recolorTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        OuterLayerPlusFeature feature = OuterLayerPlusFeature.instance();
        if (feature == null || !feature.isNameRecolorActive()) {
            return;
        }

        OptionalInt color = feature.tabColor(info);
        if (color.isPresent()) {
            cir.setReturnValue(Component.literal(cir.getReturnValue().getString()).withColor(color.getAsInt()));
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void nulltweaks$drawTabHeadRings(GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        OuterLayerPlusFeature feature = OuterLayerPlusFeature.instance();
        if (feature == null || !feature.isDistanceRingActive()) {
            return;
        }

        List<PlayerInfo> players = getPlayerInfos();
        if (players.isEmpty() || minecraft.level == null || minecraft.player == null) {
            return;
        }

        int maxNameWidth = 0;
        int scoreWidth = 0;
        int spaceWidth = minecraft.font.width(" ");
        for (PlayerInfo info : players) {
            maxNameWidth = Math.max(maxNameWidth, minecraft.font.width(getNameForDisplay(info)));
            if (objective != null && objective.getRenderType() != ObjectiveCriteria.RenderType.HEARTS) {
                ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(info.getProfile()), objective);
                if (scoreInfo != null) {
                    Component score = scoreInfo.formatValue(objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT));
                    scoreWidth = Math.max(scoreWidth, spaceWidth + minecraft.font.width(score));
                }
            }
        }

        int playerCount = players.size();
        int rows = playerCount;
        int columns = 1;
        while (rows > 20) {
            columns++;
            rows = (playerCount + columns - 1) / columns;
        }

        int scoreColumnWidth = 0;
        if (objective != null) {
            scoreColumnWidth = objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS ? 90 : scoreWidth;
        }

        int columnWidth = Math.min(columns * (9 + maxNameWidth + scoreColumnWidth + 13), screenWidth - 50) / columns;
        int startX = screenWidth / 2 - (columnWidth * columns + (columns - 1) * 5) / 2;
        int y = 10;
        if (header != null) {
            List<FormattedCharSequence> headerLines = minecraft.font.split(header, screenWidth - 50);
            y += headerLines.size() * 9 + 1;
        }

        for (int index = 0; index < playerCount; index++) {
            PlayerInfo info = players.get(index);
            OptionalInt color = feature.tabRingColor(info);
            if (color.isEmpty()) {
                continue;
            }

            GameProfile profile = info.getProfile();
            if (minecraft.level.getPlayerByUUID(profile.id()) == null) {
                continue;
            }

            int column = index / rows;
            int row = index % rows;
            int x = startX + column * columnWidth + column * 5;
            int rowY = y + row * 9;
            graphics.outline(x - 1, rowY - 1, 10, 10, color.getAsInt());
        }
    }
}
