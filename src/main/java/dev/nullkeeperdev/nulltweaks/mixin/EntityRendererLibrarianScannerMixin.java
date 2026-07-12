package dev.nullkeeperdev.nulltweaks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nullkeeperdev.nulltweaks.feature.librarianscanner.LibrarianScannerRenderContext;
import dev.nullkeeperdev.nulltweaks.feature.librarianscanner.LibrarianScannerRenderStateAccess;
import dev.nullkeeperdev.nulltweaks.feature.librarianscanner.LibrarianTradeScannerFeature;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererLibrarianScannerMixin<T extends Entity, S extends EntityRenderState> {
    private boolean nulltweaks$librarianScannerActive;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void nulltweaks$attachLibrarianScannerLabels(T entity, S state, float tickDelta, CallbackInfo ci) {
        List<Component> labels = LibrarianTradeScannerFeature.labelsForEntity(entity.getId());
        Vec3 attachment = labels.isEmpty() ? null : entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(tickDelta));
        ((LibrarianScannerRenderStateAccess) state).nulltweaks$setLibrarianScannerLabels(labels, attachment);
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
    private void nulltweaks$submitLibrarianScannerLabels(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        LibrarianScannerRenderStateAccess access = (LibrarianScannerRenderStateAccess) state;
        List<Component> labels = access.nulltweaks$getLibrarianScannerLabels();
        Vec3 attachment = access.nulltweaks$getLibrarianScannerAttachment();
        if (labels.isEmpty() || attachment == null || state.distanceToCameraSq > 4096.0D) {
            return;
        }

        nulltweaks$librarianScannerActive = true;
        LibrarianScannerRenderContext.push(true);
        try {
            int offset = 0;
            if (state.nameTag != null) {
                offset += 10;
            }
            if (state.scoreText != null) {
                offset += 10;
            }

            for (int index = labels.size() - 1; index >= 0; index--) {
                //? if >=26.2 {
                submitNodeCollector.submitNameTag(poseStack, attachment, offset, labels.get(index), !state.isDiscrete, state.lightCoords, cameraRenderState);
                //?} else {
                /*submitNodeCollector.submitNameTag(poseStack, attachment, offset, labels.get(index), !state.isDiscrete, state.lightCoords, 0.0D, cameraRenderState);
                *///?}
                offset += 10;
            }
        } finally {
            LibrarianScannerRenderContext.pop(nulltweaks$librarianScannerActive);
            nulltweaks$librarianScannerActive = false;
        }
    }
}
