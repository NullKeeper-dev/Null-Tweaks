package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.nametags.NametagTweaksFeature;
import dev.nullkeeperdev.nulltweaks.feature.nametags.NametagTweaksRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
//? if >=26.2 {
@Mixin(net.minecraft.client.renderer.feature.NameTagFeatureRenderer.class)
//?} else {
/*@Mixin(targets = "net.minecraft.client.renderer.feature.NameTagFeatureRenderer$Storage")
*///?}
public abstract class NameTagStorageNametagMixin {
    //? if <26.2 {
    /*
    @ModifyArgs(method = "add", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"), require = 0)
    private void nulltweaks$scaleNametag(Args args) {
        NametagTweaksFeature feature = NametagTweaksFeature.instance();
        if (feature == null || !NametagTweaksRenderContext.active()) {
            return;
        }

        float scale = feature.renderScaleMultiplier();
        args.set(0, ((Float) args.get(0)) * scale);
        args.set(1, ((Float) args.get(1)) * scale);
        args.set(2, ((Float) args.get(2)) * scale);
    }

    @ModifyArg(
            method = "add",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;<init>(Lorg/joml/Matrix4fc;FFLnet/minecraft/network/chat/Component;IIID)V"),
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
            method = "add",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;<init>(Lorg/joml/Matrix4fc;FFLnet/minecraft/network/chat/Component;IIID)V"),
            index = 6,
            require = 0)
    private int nulltweaks$backgroundColor(int color) {
        NametagTweaksFeature feature = NametagTweaksFeature.instance();
        if (feature == null || !NametagTweaksRenderContext.active()) {
            return color;
        }

        return feature.backgroundColor();
    }
    *///?}
}
