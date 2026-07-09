package dev.nullkeeperdev.nulltweaks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nullkeeperdev.nulltweaks.feature.nobobber.NoBobberFeature;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererNoBobberMixin {
    private static final Map<FishingHookRenderState, Boolean> HIDDEN_BOBBER_STATES = Collections.synchronizedMap(new WeakHashMap<>());

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/projectile/FishingHook;Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;F)V", at = @At("TAIL"))
    private void nulltweaks$rememberLocalPlayerBobber(FishingHook hook, FishingHookRenderState renderState, float tickDelta, CallbackInfo ci) {
        HIDDEN_BOBBER_STATES.put(renderState, NoBobberFeature.shouldHide(hook));
    }

    @Redirect(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/FishingHookRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V", ordinal = 0))
    private void nulltweaks$skipLocalPlayerHookSprite(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer renderer,
            FishingHookRenderState renderState,
            PoseStack originalPoseStack,
            SubmitNodeCollector originalCollector,
            CameraRenderState cameraRenderState) {
        if (!Boolean.TRUE.equals(HIDDEN_BOBBER_STATES.get(renderState))) {
            collector.submitCustomGeometry(poseStack, renderType, renderer);
        }
    }
}
