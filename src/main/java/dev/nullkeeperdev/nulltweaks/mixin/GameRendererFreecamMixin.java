package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamFeature;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererFreecamMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$hideFreecamHand(CameraRenderState cameraRenderState, float tickDelta, Matrix4fc projectionMatrix, CallbackInfo ci) {
        if (!FreecamFeature.shouldRenderHand()) {
            ci.cancel();
        }
    }
}
