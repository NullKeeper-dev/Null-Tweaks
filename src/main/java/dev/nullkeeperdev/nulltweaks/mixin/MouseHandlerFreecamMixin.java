package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamFeature;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerFreecamMixin {
    @Redirect(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void nulltweaks$turnFreecamCamera(LocalPlayer player, double deltaX, double deltaY) {
        if (FreecamFeature.isActive()) {
            FreecamFeature.turnCamera(deltaX, deltaY);
        } else {
            player.turn(deltaX, deltaY);
        }
    }
}
