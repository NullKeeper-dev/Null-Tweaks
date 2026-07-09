package dev.nullkeeperdev.nulltweaks.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nullkeeperdev.nulltweaks.NullTweaksClient;
import dev.nullkeeperdev.nulltweaks.config.screen.NullTweaksScreenBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class NullTweaksKeyMappings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(NullTweaksClient.MOD_ID, "null_tweaks"));

    private static final KeyMapping OPEN_CONFIG_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.nulltweaks.config.open",
            InputConstants.UNKNOWN.getType(),
            InputConstants.UNKNOWN.getValue(),
            CATEGORY));

    private static boolean hooksRegistered;

    private NullTweaksKeyMappings() {
    }

    public static void register() {
        if (hooksRegistered) {
            return;
        }

        hooksRegistered = true;
        ClientTickEvents.END_CLIENT_TICK.register(NullTweaksKeyMappings::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        while (OPEN_CONFIG_KEY.consumeClick()) {
            client.setScreenAndShow(NullTweaksScreenBuilder.build(null));
        }
    }
}
