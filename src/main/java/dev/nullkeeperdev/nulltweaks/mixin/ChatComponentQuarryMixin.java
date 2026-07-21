/* SPDX-License-Identifier: GPL-3.0-only */
package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.quarry.QuarryFeature;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentQuarryMixin {
    @Inject(method = {"addClientSystemMessage", "addServerSystemMessage"}, at = @At("HEAD"), cancellable = true)
    private void nulltweaks$hideBaritoneMessages(Component message, CallbackInfo ci) {
        if (QuarryFeature.shouldSuppressBaritoneMessage(message)) {
            ci.cancel();
        }
    }
}
