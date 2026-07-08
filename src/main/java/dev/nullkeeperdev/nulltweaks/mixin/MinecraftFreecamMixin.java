package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamFeature;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftFreecamMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$cancelFreecamAttack(CallbackInfoReturnable<Boolean> cir) {
        if (FreecamFeature.isActive()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$cancelFreecamUse(CallbackInfo ci) {
        if (FreecamFeature.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$cancelFreecamContinueAttack(boolean breaking, CallbackInfo ci) {
        if (FreecamFeature.isActive()) {
            ci.cancel();
        }
    }
}
