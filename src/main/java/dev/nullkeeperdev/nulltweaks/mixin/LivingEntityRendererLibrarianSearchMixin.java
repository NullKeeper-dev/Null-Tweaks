package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.librarianscanner.LibrarianTradeScannerFeature;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererLibrarianSearchMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void nulltweaks$applyLibrarianSearchHighlightColor(LivingEntity entity, LivingEntityRenderState renderState, float tickDelta, CallbackInfo ci) {
        Integer outlineColor = LibrarianTradeScannerFeature.searchOutlineColorFor(entity);
        if (outlineColor != null) {
            renderState.outlineColor = outlineColor;
        }
    }
}
