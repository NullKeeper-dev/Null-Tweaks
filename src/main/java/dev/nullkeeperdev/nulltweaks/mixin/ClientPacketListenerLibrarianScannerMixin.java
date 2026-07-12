package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.librarianscanner.LibrarianTradeScannerFeature;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerLibrarianScannerMixin {
    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$suppressLibrarianScannerMerchantScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (LibrarianTradeScannerFeature.shouldSuppressOpenScreen(packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMerchantOffers", at = @At("HEAD"), cancellable = true)
    private void nulltweaks$captureLibrarianScannerOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
        if (LibrarianTradeScannerFeature.handleMerchantOffers(packet)) {
            ci.cancel();
        }
    }
}
