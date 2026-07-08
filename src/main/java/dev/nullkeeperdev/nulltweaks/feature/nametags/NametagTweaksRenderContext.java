package dev.nullkeeperdev.nulltweaks.feature.nametags;

public final class NametagTweaksRenderContext {
    private static final ThreadLocal<Integer> PLAYER_NAMETAG_DEPTH = ThreadLocal.withInitial(() -> 0);

    private NametagTweaksRenderContext() {
    }

    public static void pushPlayerNametag(boolean active) {
        if (active) {
            PLAYER_NAMETAG_DEPTH.set(PLAYER_NAMETAG_DEPTH.get() + 1);
        }
    }

    public static void popPlayerNametag(boolean active) {
        if (!active) {
            return;
        }

        int depth = PLAYER_NAMETAG_DEPTH.get() - 1;
        if (depth <= 0) {
            PLAYER_NAMETAG_DEPTH.remove();
        } else {
            PLAYER_NAMETAG_DEPTH.set(depth);
        }
    }

    public static boolean active() {
        NametagTweaksFeature feature = NametagTweaksFeature.instance();
        return feature != null && feature.active() && PLAYER_NAMETAG_DEPTH.get() > 0;
    }
}
