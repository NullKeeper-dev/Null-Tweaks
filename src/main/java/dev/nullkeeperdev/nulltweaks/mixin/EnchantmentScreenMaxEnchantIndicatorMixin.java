package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.maxenchant.MaxEnchantIndicatorFeature;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMaxEnchantIndicatorMixin {
    @Redirect(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getFullname(Lnet/minecraft/core/Holder;I)Lnet/minecraft/network/chat/Component;"))
    private Component nulltweaks$colorMaxEnchantPreview(Holder<Enchantment> enchantment, int level) {
        return MaxEnchantIndicatorFeature.applyIndicatorColor(enchantment, level, Enchantment.getFullname(enchantment, level));
    }
}
