package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamFeature;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerFreecamMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void nulltweaks$freezeBeforeTick(CallbackInfo ci) {
        FreecamFeature.freezeLocalPlayer((LocalPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void nulltweaks$freezeAfterTick(CallbackInfo ci) {
        FreecamFeature.freezeLocalPlayer((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$cancelFreecamPositionPackets(CallbackInfo ci) {
        if (FreecamFeature.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "applyInput", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$cancelFreecamInputMovement(CallbackInfo ci) {
        if (FreecamFeature.isActive()) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0D;
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$cancelFreecamMovement(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (FreecamFeature.isActive()) {
            ci.cancel();
        }
    }
}
