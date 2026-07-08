package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.outerlayerplus.OuterLayerPlusFeature;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void nulltweaks$addOuterLayerButton(CallbackInfo ci) {
        OuterLayerPlusFeature feature = OuterLayerPlusFeature.instance();
        if (feature == null || !feature.shouldShowPauseMenuButton() || !((PauseScreen) (Object) this).showsPauseMenu()) {
            return;
        }

        addRenderableWidget(Button.builder(Component.literal("OuterLayer+"), button -> minecraft.setScreenAndShow(feature.createHudEditorScreen(this)))
                .bounds(width - 114, 10, 104, 20)
                .build());
    }
}
