package dev.nullkeeperdev.nulltweaks.input;

import dev.nullkeeperdev.nulltweaks.NullTweaksClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class NullTweaksKeyMappings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(NullTweaksClient.MOD_ID, "null_tweaks"));

    private NullTweaksKeyMappings() {
    }
}
