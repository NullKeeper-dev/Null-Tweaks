package dev.nullkeeperdev.nulltweaks.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAutoclickerAccessor {
    @Invoker("startAttack")
    boolean nulltweaks$startAttack();

    @Invoker("startUseItem")
    void nulltweaks$startUseItem();

    @Accessor("missTime")
    void nulltweaks$setMissTime(int missTime);
}
