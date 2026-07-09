package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.outerlayerplus.OuterLayerPlusFeature;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.OptionalInt;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    private List<PlayerInfo> nulltweaks$rowInfos = List.of();
    private int nulltweaks$faceIndex;
    private int nulltweaks$nameIndex;

    @Shadow
    private List<PlayerInfo> getPlayerInfos() {
        throw new AssertionError();
    }

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void nulltweaks$recolorTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        OuterLayerPlusFeature feature = OuterLayerPlusFeature.instance();
        if (feature == null || !feature.isNameRecolorActive()) {
            return;
        }

        OptionalInt color = feature.tabColor(info);
        if (color.isPresent()) {
            cir.setReturnValue(nulltweaks$recolor(cir.getReturnValue(), color.getAsInt()));
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void nulltweaks$beginTabRows(GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        nulltweaks$rowInfos = List.copyOf(getPlayerInfos());
        nulltweaks$faceIndex = 0;
        nulltweaks$nameIndex = 0;
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void nulltweaks$endTabRows(GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        nulltweaks$rowInfos = List.of();
        nulltweaks$faceIndex = 0;
        nulltweaks$nameIndex = 0;
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/PlayerFaceExtractor;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V"))
    private void nulltweaks$drawHeadRing(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size, boolean showHat, boolean upsideDown, int color) {
        PlayerFaceExtractor.extractRenderState(graphics, texture, x, y, size, showHat, upsideDown, color);

        PlayerInfo info = nulltweaks$nextFaceInfo();
        if (info == null) {
            return;
        }

        OuterLayerPlusFeature feature = OuterLayerPlusFeature.instance();
        if (feature == null) {
            return;
        }

        OptionalInt ringColor = feature.tabRingColor(info);
        if (ringColor.isPresent()) {
            graphics.outline(x, y, size, size, ringColor.getAsInt());
        }
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void nulltweaks$drawTabName(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        PlayerInfo info = nulltweaks$nextNameInfo();
        OuterLayerPlusFeature feature = OuterLayerPlusFeature.instance();
        if (info != null && feature != null && feature.isNameRecolorActive()) {
            OptionalInt tabColor = feature.tabColor(info);
            if (tabColor.isPresent()) {
                graphics.text(font, nulltweaks$recolor(text, tabColor.getAsInt()), x, y, ARGB.opaque(tabColor.getAsInt()));
                return;
            }
        }

        graphics.text(font, text, x, y, color);
    }

    private PlayerInfo nulltweaks$nextFaceInfo() {
        if (nulltweaks$faceIndex >= nulltweaks$rowInfos.size()) {
            return null;
        }

        return nulltweaks$rowInfos.get(nulltweaks$faceIndex++);
    }

    private PlayerInfo nulltweaks$nextNameInfo() {
        if (nulltweaks$nameIndex >= nulltweaks$rowInfos.size()) {
            return null;
        }

        return nulltweaks$rowInfos.get(nulltweaks$nameIndex++);
    }

    private static Component nulltweaks$recolor(Component component, int rgb) {
        return component.copy().withColor(rgb & 0xFFFFFF);
    }
}
