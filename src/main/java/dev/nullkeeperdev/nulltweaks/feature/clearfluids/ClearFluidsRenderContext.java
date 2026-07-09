package dev.nullkeeperdev.nulltweaks.feature.clearfluids;

public final class ClearFluidsRenderContext {
    private static final ThreadLocal<ClearFluidsFeature.RenderSettings> SETTINGS = new ThreadLocal<>();

    private ClearFluidsRenderContext() {
    }

    public static void set(ClearFluidsFeature.RenderSettings settings) {
        SETTINGS.set(settings);
    }

    public static ClearFluidsFeature.RenderSettings settings() {
        return SETTINGS.get();
    }

    public static void clear() {
        SETTINGS.remove();
    }
}
