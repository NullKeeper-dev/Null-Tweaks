package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.librarianscanner.LibrarianTradeScannerFeature;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeLibrarianScannerMixin {
    @Inject(method = "interact", at = @At("HEAD"))
    private void nulltweaks$preemptLibrarianScannerForManualInteraction(Player player, Entity entity, EntityHitResult hitResult, InteractionHand hand, CallbackInfoReturnable<?> cir) {
        LibrarianTradeScannerFeature.handleManualInteraction(player, entity);
    }
}
