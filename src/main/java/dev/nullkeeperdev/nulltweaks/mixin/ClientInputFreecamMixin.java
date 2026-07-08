package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.freecam.FreecamClientInputAccess;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientInput.class)
public abstract class ClientInputFreecamMixin implements FreecamClientInputAccess {
    @Shadow
    public Input keyPresses;

    @Shadow
    protected Vec2 moveVector;

    @Unique
    @Override
    public void nulltweaks$clearFreecamMovement() {
        keyPresses = Input.EMPTY;
        moveVector = Vec2.ZERO;
    }
}
