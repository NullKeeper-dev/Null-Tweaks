/* SPDX-License-Identifier: GPL-3.0-only */
package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.quarry.QuarryFeature;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeQuarryMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$enforceQuarryStartRules(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (QuarryFeature.shouldPreventBlockBreaking(pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$enforceQuarryContinueRules(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (QuarryFeature.shouldPreventBlockBreaking(pos)) {
            cir.setReturnValue(false);
        }
    }
}
