package dev.nullkeeperdev.nulltweaks.feature.librarianscanner;

public final class LibrarianScannerRenderContext {
    private static int activeDepth;

    private LibrarianScannerRenderContext() {
    }

    public static void push(boolean active) {
        if (active) {
            activeDepth++;
        }
    }

    public static void pop(boolean active) {
        if (active && activeDepth > 0) {
            activeDepth--;
        }
    }

    public static boolean active() {
        return activeDepth > 0;
    }
}
