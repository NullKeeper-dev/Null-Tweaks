package dev.nullkeeperdev.nulltweaks.feature.nobobber;

import dev.isxander.yacl3.api.OptionGroup;
import dev.nullkeeperdev.nulltweaks.feature.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;

public final class NoBobberFeature extends Feature {
    private static NoBobberFeature instance;

    public NoBobberFeature() {
        super("no_fishing_bobber", "No Fishing Bobber");
        instance = this;
    }

    public static boolean shouldHide(FishingHook hook) {
        NoBobberFeature feature = instance;
        if (feature == null || !feature.isEnabled() || hook == null) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        Player owner = hook.getPlayerOwner();
        return owner != null && owner.getUUID().equals(client.player.getUUID());
    }

    @Override
    public void buildConfig(OptionGroup.Builder builder) {
    }
}
