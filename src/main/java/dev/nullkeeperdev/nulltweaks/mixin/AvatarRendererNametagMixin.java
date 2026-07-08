package dev.nullkeeperdev.nulltweaks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nullkeeperdev.nulltweaks.feature.nametags.NametagTweaksFeature;
import dev.nullkeeperdev.nulltweaks.feature.nametags.NametagTweaksRenderContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererNametagMixin {
    private boolean nulltweaks$nametagActive;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void nulltweaks$formatNametag(Avatar entity, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        NametagTweaksFeature feature = NametagTweaksFeature.instance();
        if (feature == null || !feature.active() || state.nameTag == null) {
            return;
        }

        state.nameTag = feature.formatName(state.nameTag);
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void nulltweaks$pushNametagContext(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        NametagTweaksFeature feature = NametagTweaksFeature.instance();
        nulltweaks$nametagActive = feature != null && feature.active() && state.nameTag != null;
        NametagTweaksRenderContext.pushPlayerNametag(nulltweaks$nametagActive);
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
    private void nulltweaks$popNametagContext(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        NametagTweaksRenderContext.popPlayerNametag(nulltweaks$nametagActive);
        nulltweaks$nametagActive = false;
    }
}
