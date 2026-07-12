package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.maxenchant.MaxEnchantIndicatorFeature;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEnchantments.class)
public abstract class ItemEnchantmentsMaxEnchantIndicatorMixin {
    @Redirect(
            method = "addToTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getFullname(Lnet/minecraft/core/Holder;I)Lnet/minecraft/network/chat/Component;", ordinal = 0))
    private Component nulltweaks$colorOrderedMaxEnchantTooltip(Holder<Enchantment> enchantment, int level) {
        return MaxEnchantIndicatorFeature.applyIndicatorColor(enchantment, level, Enchantment.getFullname(enchantment, level));
    }

    @Redirect(
            method = "addToTooltip",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/Enchantment;getFullname(Lnet/minecraft/core/Holder;I)Lnet/minecraft/network/chat/Component;", ordinal = 1))
    private Component nulltweaks$colorUnorderedMaxEnchantTooltip(Holder<Enchantment> enchantment, int level) {
        return MaxEnchantIndicatorFeature.applyIndicatorColor(enchantment, level, Enchantment.getFullname(enchantment, level));
    }
}
