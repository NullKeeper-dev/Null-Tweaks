package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LavaFogEnvironment.class)
public abstract class LavaFogEnvironmentFreecamMixin {
    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$useSpectatorLavaFog(FogData fogData, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!FreecamFeature.isActive()) {
            return;
        }

        fogData.environmentalStart = -8.0F;
        fogData.environmentalEnd = renderDistance * 0.5F;
        fogData.skyEnd = fogData.environmentalEnd;
        fogData.cloudEnd = fogData.environmentalEnd;
        ci.cancel();
    }
}
