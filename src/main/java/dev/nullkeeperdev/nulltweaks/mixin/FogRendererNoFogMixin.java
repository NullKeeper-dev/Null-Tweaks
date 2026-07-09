package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.nofog.NoFogFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererNoFogMixin {
    private static final float NO_FOG_START = 1_000_000.0F;
    private static final float NO_FOG_END = 1_000_001.0F;

    @Redirect(
            method = {"computeFogColor", "setupFog"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;getFogType(Lnet/minecraft/client/Camera;)Lnet/minecraft/world/level/material/FogType;"))
    private FogType nulltweaks$useAdjustedFogType(FogRenderer renderer, Camera camera) {
        return NoFogFeature.fogTypeFor(camera);
    }

    @Redirect(
            method = "computeFogColor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/environment/FogEnvironment;isApplicable(Lnet/minecraft/world/level/material/FogType;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean nulltweaks$skipDisabledColorFog(FogEnvironment environment, FogType fogType, Entity entity) {
        return !NoFogFeature.shouldSkipColorEnvironment(environment) && environment.isApplicable(fogType, entity);
    }

    @Redirect(
            method = "setupFog",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/environment/FogEnvironment;isApplicable(Lnet/minecraft/world/level/material/FogType;Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean nulltweaks$skipDisabledSetupFog(FogEnvironment environment, FogType fogType, Entity entity) {
        return !NoFogFeature.shouldSkipSetupEnvironment(environment) && environment.isApplicable(fogType, entity);
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void nulltweaks$clearAtmosphericFog(Camera camera, int renderDistanceChunks, DeltaTracker deltaTracker, float viewDistance, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (!NoFogFeature.shouldDisableAtmosphericFog()) {
            return;
        }

        FogData data = cir.getReturnValue();
        data.renderDistanceStart = NO_FOG_START;
        data.renderDistanceEnd = NO_FOG_END;
        if (data.environmentalStart == 0.0F && data.environmentalEnd == 0.0F) {
            data.environmentalStart = NO_FOG_START;
            data.environmentalEnd = NO_FOG_END;
            data.skyEnd = NO_FOG_END;
            data.cloudEnd = NO_FOG_END;
        }
    }
}
