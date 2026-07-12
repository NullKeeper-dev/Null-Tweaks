package dev.nullkeeperdev.nulltweaks.feature.librarianscanner;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public interface LibrarianScannerRenderStateAccess {
    void nulltweaks$setLibrarianScannerLabels(List<Component> labels, Vec3 attachment);

    List<Component> nulltweaks$getLibrarianScannerLabels();

    Vec3 nulltweaks$getLibrarianScannerAttachment();
}
