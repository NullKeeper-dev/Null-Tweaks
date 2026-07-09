package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraFreecamMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void nulltweaks$useFreecamTransform(DeltaTracker deltaTracker, CallbackInfo ci) {
        FreecamFeature.CameraState state = FreecamFeature.cameraState(deltaTracker.getGameTimeDeltaPartialTick(false));
        if (state == null) {
            return;
        }

        detached = true;
        setRotation(state.yRot(), state.xRot());
        setPosition(state.position());
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void nulltweaks$useFreecamRenderState(CameraRenderState renderState, float tickDelta, CallbackInfo ci) {
        FreecamFeature.CameraState state = FreecamFeature.cameraState(tickDelta);
        if (state == null) {
            return;
        }

        renderState.pos = state.position();
        renderState.blockPos = BlockPos.containing(state.position());
        renderState.xRot = state.xRot();
        renderState.yRot = state.yRot();
        nulltweaks$disableSmartCull(renderState);
    }

    //? if >=26.2 {
    private void nulltweaks$disableSmartCull(CameraRenderState renderState) {
        renderState.smartCull = false;
    }
    //?} else {
    /*private void nulltweaks$disableSmartCull(CameraRenderState renderState) {
    }
    *///?}
}
