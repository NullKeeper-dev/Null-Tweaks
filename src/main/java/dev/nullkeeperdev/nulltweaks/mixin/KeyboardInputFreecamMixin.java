package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamClientInputAccess;
import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamFeature;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputFreecamMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void nulltweaks$clearFreecamMovement(CallbackInfo ci) {
        if (FreecamFeature.shouldSuppressPlayerInput()) {
            ((FreecamClientInputAccess) this).nulltweaks$clearFreecamMovement();
        }
    }
}
