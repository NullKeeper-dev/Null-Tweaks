package dev.nullkeeperdev.nulltweaks.mixin;

import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.nullkeeperdev.nulltweaks.config.screen.NullTweaksScreenBuilder;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(YACLScreen.class)
public abstract class YaclScreenCategoryMemoryMixin {
    @Shadow
    @Final
    public YetAnotherConfigLib config;

    @Shadow
    @Final
    public TabManager tabManager;

    @Inject(method = "onClose", at = @At("HEAD"))
    private void nulltweaks$rememberCategory(CallbackInfo ci) {
        YACLScreen screen = (YACLScreen) (Object) this;
        if (!NullTweaksScreenBuilder.isNullTweaksScreen(screen)) {
            return;
        }

        Tab currentTab = tabManager.getCurrentTab();
        if (currentTab != null) {
            NullTweaksScreenBuilder.rememberCategory(currentTab.getTabTitle());
        }
    }
}
