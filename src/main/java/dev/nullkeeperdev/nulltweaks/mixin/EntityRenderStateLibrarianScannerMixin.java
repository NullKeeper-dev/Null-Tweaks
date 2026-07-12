package dev.nullkeeperdev.nulltweaks.mixin;

import dev.nullkeeperdev.nulltweaks.feature.librarianscanner.LibrarianScannerRenderStateAccess;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateLibrarianScannerMixin implements LibrarianScannerRenderStateAccess {
    @Unique
    private List<Component> nulltweaks$librarianScannerLabels = List.of();
    @Unique
    private Vec3 nulltweaks$librarianScannerAttachment;

    @Override
    public void nulltweaks$setLibrarianScannerLabels(List<Component> labels, Vec3 attachment) {
        nulltweaks$librarianScannerLabels = labels;
        nulltweaks$librarianScannerAttachment = attachment;
    }

    @Override
    public List<Component> nulltweaks$getLibrarianScannerLabels() {
        return nulltweaks$librarianScannerLabels;
    }

    @Override
    public Vec3 nulltweaks$getLibrarianScannerAttachment() {
        return nulltweaks$librarianScannerAttachment;
    }
}
