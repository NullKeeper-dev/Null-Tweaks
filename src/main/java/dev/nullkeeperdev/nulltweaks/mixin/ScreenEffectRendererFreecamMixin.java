package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamFeature;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererFreecamMixin {
    @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
    private static void nulltweaks$skipBlockOverlayInFreecam(Player player, CallbackInfoReturnable<BlockState> cir) {
        if (FreecamFeature.isActive()) {
            cir.setReturnValue(null);
        }
    }
}
