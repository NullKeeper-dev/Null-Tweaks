/* SPDX-License-Identifier: GPL-3.0-only */
package dev.nullkeeperdev.nulltweaks.util;

import net.minecraft.client.Minecraft;

public final class ClientScreenState {
    private ClientScreenState() {
    }

    public static boolean hasOpenScreen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen() != null;
        //?} else {
        /*return client.screen != null;
        *///?}
    }
}
