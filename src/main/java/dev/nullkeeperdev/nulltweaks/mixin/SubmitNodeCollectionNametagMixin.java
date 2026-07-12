package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.nametags.NametagTweaksFeature;
import dev.nullkeeperdev.nulltweaks.feature.nametags.NametagTweaksRenderContext;
import dev.nullkeeperdev.nulltweaks.feature.librarianscanner.LibrarianScannerRenderContext;
import dev.nullkeeperdev.nulltweaks.feature.librarianscanner.LibrarianTradeScannerFeature;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionNametagMixin {
    @ModifyArgs(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"), require = 0)
    private void nulltweaks$scaleNametag(Args args) {
        NametagTweaksFeature feature = NametagTweaksFeature.instance();
        float scale = 1.0F;
        if (feature != null && NametagTweaksRenderContext.active()) {
            scale *= feature.renderScaleMultiplier();
        }
        if (LibrarianScannerRenderContext.active()) {
            scale *= LibrarianTradeScannerFeature.labelScale();
        }

        args.set(0, ((Float) args.get(0)) * scale);
        args.set(1, ((Float) args.get(1)) * scale);
        args.set(2, ((Float) args.get(2)) * scale);
    }

    @ModifyArg(
            method = "submitNameTag",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;<init>(Lorg/joml/Matrix4fc;FFLnet/minecraft/network/chat/Component;IIILnet/minecraft/client/gui/Font$DisplayMode;)V"),
            index = 5,
            require = 0)
    private int nulltweaks$textColor(int color) {
        NametagTweaksFeature feature = NametagTweaksFeature.instance();
        if (feature == null || !NametagTweaksRenderContext.active()) {
            return color;
        }

        return feature.textColorWithAlpha(color);
    }

    @ModifyArg(
            method = "submitNameTag",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;<init>(Lorg/joml/Matrix4fc;FFLnet/minecraft/network/chat/Component;IIILnet/minecraft/client/gui/Font$DisplayMode;)V"),
            index = 6,
            require = 0)
    private int nulltweaks$backgroundColor(int color) {
        NametagTweaksFeature feature = NametagTweaksFeature.instance();
        if (feature == null || !NametagTweaksRenderContext.active()) {
            return color;
        }

        return feature.backgroundColor();
    }
}
